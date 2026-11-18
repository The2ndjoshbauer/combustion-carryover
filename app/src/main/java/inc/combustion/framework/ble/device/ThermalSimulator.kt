package inc.combustion.framework.ble.device

import kotlin.math.max

class ThermalSimulator(
    private val diffusivity: Double,
    private val thicknessMeters: Double = 0.04,
    private val numNodes: Int = 30,
    private val waterContentPercent: Double = 0.70,
    private val thermalConductivity: Double = 0.48,
    initialTemp: Double = 20.0,
    initialProfile: DoubleArray? = null
) {
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

    private var remainingSurfaceMoisture = waterContentPercent
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
        val r = (diffusivity * dtSeconds) / (2.0 * dx * dx)
        val bi = (heatTransferCoefficient * dx) / thermalConductivity
        val last = numNodes - 1

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

        // 3. Surface Node (i = last, convection + latent heat evaporative stall)
        val surfTemp = temps[last]
        var effectiveBi = bi
        var evaporativeSink = 0.0

        if (surfTemp >= 68.0 && remainingSurfaceMoisture > 0.05 && ambientTemp > surfTemp) {
            val stallFactor = if (surfTemp in 68.0..82.0) 0.28 else 0.60
            effectiveBi *= stallFactor
            evaporativeSink = 0.02 * (surfTemp - 65.0).coerceAtLeast(0.0) * dtSeconds * 0.05
            remainingSurfaceMoisture = max(0.0, remainingSurfaceMoisture - 0.00003 * dtSeconds)
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
}
