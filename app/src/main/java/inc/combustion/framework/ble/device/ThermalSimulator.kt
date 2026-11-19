package inc.combustion.framework.ble.device

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class ThermalSimulator(
    private val diffusivity: Double,
    private val thicknessMeters: Double = 0.04,
    private val numNodes: Int = 30,
    private val waterContentPercent: Double = 0.70,
    private val thermalConductivity: Double = 0.48,
    private val meatMaterial: MeatMaterials? = null,
    initialSurfaceMoisturePhi: Double = 0.85,
    initialTemp: Double = 20.0,
    initialProfile: DoubleArray? = null
) {
    companion object {
        private const val STEFAN_BOLTZMANN = 5.670374419e-8
        private const val SURFACE_EMISSIVITY = 0.80
        private const val CRITICAL_MOISTURE_FRACTION = 0.52
        private const val LATENT_HEAT_VAPORIZATION_J_PER_KG = 2.257e6
    }

    // Node 0 = Core (r = 0), Node numNodes - 1 = Surface (r = L)
    private val temps = DoubleArray(numNodes)
    private val nextTemps = DoubleArray(numNodes)

    // Pre-allocated Thomas Algorithm (TDMA) buffers to avoid GC allocations
    private val a = DoubleArray(numNodes)
    private val b = DoubleArray(numNodes)
    private val c = DoubleArray(numNodes)
    private val d = DoubleArray(numNodes)
    private val cPrime = DoubleArray(numNodes)
    private val dPrime = DoubleArray(numNodes)

    private var remainingSurfaceMoisture = waterContentPercent * initialSurfaceMoisturePhi.coerceIn(0.1, 1.0)
    private val dx = max(0.0005, thicknessMeters / max(1, numNodes - 1))

    init {
        if (initialProfile != null && initialProfile.size >= 2) {
            val srcMaxIndex = initialProfile.size - 1
            for (i in 0 until numNodes) {
                val pos = (i.toDouble() / (numNodes - 1)) * srcMaxIndex
                val idx = pos.toInt().coerceIn(0, srcMaxIndex - 1)
                val frac = pos - idx
                temps[i] = initialProfile[idx] * (1.0 - frac) + initialProfile[idx + 1] * frac
            }
        } else {
            for (i in 0 until numNodes) {
                temps[i] = initialTemp
            }
        }
    }

    fun step(
        ambientTemp: Double,
        heatTransferCoefficient: Double = 20.0,
        dtSeconds: Double = 1.0
    ): DoubleArray {
        val last = numNodes - 1
        val meanTempC = 0.5 * (temps[0] + temps[last])
        val choiSnapshot = meatMaterial?.evaluateAt(meanTempC)

        val kEffective = choiSnapshot?.kMeanWPerMK ?: thermalConductivity
        val rhoCp = if (choiSnapshot != null) {
            choiSnapshot.densityKgM3 * choiSnapshot.specificHeatJPerKgK
        } else {
            kEffective / max(1e-8, diffusivity)
        }
        val alphaEffective = if (choiSnapshot != null) {
            diffusivity.coerceIn(
                0.85 * choiSnapshot.alphaPerpM2PerS,
                1.15 * choiSnapshot.alphaParallelM2PerS
            )
        } else {
            diffusivity
        }

        val r = (alphaEffective * dtSeconds) / (2.0 * dx * dx)

        // 1. Center Core Node (i = 0, cylindrical symmetry dT/dr = 0)
        a[0] = 0.0
        b[0] = 1.0 + 4.0 * r
        c[0] = -4.0 * r
        d[0] = (1.0 - 4.0 * r) * temps[0] + 4.0 * r * temps[1]

        // 2. Interior Nodes (i = 1 until last, cylindrical radial coordinates)
        for (i in 1 until last) {
            val wMinus = 1.0 - (1.0 / (2.0 * i))
            val wPlus = 1.0 + (1.0 / (2.0 * i))
            a[i] = -r * wMinus
            b[i] = 1.0 + 2.0 * r
            c[i] = -r * wPlus
            d[i] = r * wMinus * temps[i - 1] + (1.0 - 2.0 * r) * temps[i] + r * wPlus * temps[i + 1]
        }

        // 3. Surface Node (i = last: Convection + Stefan-Boltzmann Radiation + Falling-Rate Evaporation)
        val surfTemp = temps[last]
        val surfK = surfTemp + 273.15
        val ambK = ambientTemp + 273.15

        // Linearized Stefan-Boltzmann radiative coefficient h_rad = eps * sigma * (T_s + T_amb) * (T_s^2 + T_amb^2)
        val hRad = SURFACE_EMISSIVITY * STEFAN_BOLTZMANN *
            (surfK + ambK) * (surfK * surfK + ambK * ambK)
        val hTotal = heatTransferCoefficient + hRad
        val bi = (hTotal * dx) / kEffective

        // Falling-rate crust drying regime: phi_w = 1.0 above M_crit, quadratic decay below M_crit
        val phiW = getSurfaceMoisturePhi()
        var effectiveBi = bi
        var evaporativeSink = 0.0

        if (waterContentPercent > 0.01 && surfTemp >= 58.0 && remainingSurfaceMoisture > 0.03 && ambientTemp > surfTemp) {
            // Tetens saturation vapor pressure driving mass flux during active cooking
            val pSatSurfKPa = 0.61078 * exp((17.27 * surfTemp.coerceAtMost(104.0)) / (surfTemp.coerceAtMost(104.0) + 237.3))
            val massFluxKgPerM2S = 4.8e-6 * heatTransferCoefficient * phiW * (pSatSurfKPa - 1.5).coerceAtLeast(0.0)
            val qEvapWPerM2 = LATENT_HEAT_VAPORIZATION_J_PER_KG * massFluxKgPerM2S

            // In the 68-84°C wet-bulb stall zone, latent heat absorbs a large share of convective input;
            // as remainingSurfaceMoisture drops below M_crit, phiW falls quadratically and the crust dries past 100°C.
            val wetBulbDamping = if (surfTemp in 66.0..84.0) (1.0 - 0.55 * phiW) else (1.0 - 0.25 * phiW)
            effectiveBi *= wetBulbDamping.coerceIn(0.32, 1.0)
            evaporativeSink = ((1.2 * qEvapWPerM2 * dtSeconds) / (rhoCp * dx)).coerceAtMost(1.4 * dtSeconds)

            val density = choiSnapshot?.densityKgM3 ?: 1050.0
            remainingSurfaceMoisture = max(
                0.03,
                remainingSurfaceMoisture - (massFluxKgPerM2S * dtSeconds) / (density * dx * 5.5)
            )
        }

        a[last] = -2.0 * r
        b[last] = 1.0 + 2.0 * r * (1.0 + effectiveBi)
        c[last] = 0.0
        d[last] = 2.0 * r * temps[last - 1] +
            (1.0 - 2.0 * r * (1.0 + effectiveBi)) * surfTemp +
            4.0 * r * effectiveBi * ambientTemp - evaporativeSink

        // 4. Thomas Algorithm (TDMA) O(N) Solver
        cPrime[0] = c[0] / b[0]
        dPrime[0] = d[0] / b[0]

        for (i in 1..last) {
            val denom = b[i] - a[i] * cPrime[i - 1]
            cPrime[i] = if (i < last) c[i] / denom else 0.0
            dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / denom
        }

        nextTemps[last] = dPrime[last]
        for (i in last - 1 downTo 0) {
            nextTemps[i] = dPrime[i] - cPrime[i] * nextTemps[i + 1]
        }

        for (i in 0..last) {
            temps[i] = nextTemps[i]
        }

        return temps
    }

    fun getCoreTemp(): Double = temps[0]

    fun getCurrentProfile(): DoubleArray = temps.copyOf()

    fun getSurfaceMoisturePhi(): Double {
        if (waterContentPercent <= 0.01) return 0.0
        return if (remainingSurfaceMoisture >= CRITICAL_MOISTURE_FRACTION) {
            1.0
        } else {
            (remainingSurfaceMoisture / CRITICAL_MOISTURE_FRACTION).pow(2.0).coerceIn(0.05, 1.0)
        }
    }
}
