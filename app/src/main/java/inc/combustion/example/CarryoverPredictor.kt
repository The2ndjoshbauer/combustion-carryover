package inc.combustion.example

import inc.combustion.framework.ble.device.ChoiOkosProperties
import inc.combustion.framework.ble.device.MeatMaterials
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class MeatType(
    val displayName: String,
    val shortLabel: String,
    val icon: String,
    val diffusivity: Double,
    val thermalConductivity: Double,
    val waterFraction: Double,
    val proteinFraction: Double,
    val fatFraction: Double,
    val ashFraction: Double = 0.01,
    val targetSalmonellaLog10: Double = 6.5,
    val refD54_4Minutes: Double = 13.0,
    val zValueC: Double = 6.0
) {
    BEEF(
        displayName = "Lean Beef (6% Fat)",
        shortLabel = "Lean Beef",
        icon = "🥩",
        diffusivity = 1.32e-7,
        thermalConductivity = 0.49,
        waterFraction = 0.72,
        proteinFraction = 0.21,
        fatFraction = 0.06,
        targetSalmonellaLog10 = 6.5,
        refD54_4Minutes = 13.0,
        zValueC = 6.0
    ),
    MARBLED_BEEF(
        displayName = "Marbled Beef (23% Fat)",
        shortLabel = "Marbled",
        icon = "🐄",
        diffusivity = 1.15e-7,
        thermalConductivity = 0.41,
        waterFraction = 0.58,
        proteinFraction = 0.18,
        fatFraction = 0.23,
        targetSalmonellaLog10 = 6.5,
        refD54_4Minutes = 13.0,
        zValueC = 6.0
    ),
    PORK(
        displayName = "Pork (6% Fat)",
        shortLabel = "Pork",
        icon = "🐷",
        diffusivity = 1.31e-7,
        thermalConductivity = 0.48,
        waterFraction = 0.72,
        proteinFraction = 0.21,
        fatFraction = 0.06,
        targetSalmonellaLog10 = 6.5,
        refD54_4Minutes = 13.0,
        zValueC = 6.0
    ),
    POULTRY(
        displayName = "Poultry (2% Fat)",
        shortLabel = "Poultry",
        icon = "🍗",
        diffusivity = 1.38e-7,
        thermalConductivity = 0.51,
        waterFraction = 0.74,
        proteinFraction = 0.23,
        fatFraction = 0.02,
        targetSalmonellaLog10 = 7.0,
        refD54_4Minutes = 15.0,
        zValueC = 6.1
    ),
    FISH(
        displayName = "Salmon (14% Fat)",
        shortLabel = "Salmon",
        icon = "🐟",
        diffusivity = 1.21e-7,
        thermalConductivity = 0.45,
        waterFraction = 0.65,
        proteinFraction = 0.20,
        fatFraction = 0.14,
        targetSalmonellaLog10 = 6.5,
        refD54_4Minutes = 10.0,
        zValueC = 5.8
    ),
    OTHER(
        displayName = "Other (Average)",
        shortLabel = "Other",
        icon = "🍖",
        diffusivity = 1.30e-7,
        thermalConductivity = 0.48,
        waterFraction = 0.70,
        proteinFraction = 0.20,
        fatFraction = 0.09,
        targetSalmonellaLog10 = 6.5,
        refD54_4Minutes = 13.0,
        zValueC = 6.0
    );

    fun evaluateProperties(tempC: Double): ChoiOkosProperties.ThermophysicalSnapshot {
        return ChoiOkosProperties.evaluate(
            tempC = tempC,
            waterFraction = waterFraction,
            proteinFraction = proteinFraction,
            fatFraction = fatFraction,
            ashFraction = ashFraction
        )
    }

    fun toMeatMaterial(): MeatMaterials {
        return when (this) {
            BEEF -> MeatMaterials.BEEF_STEAK
            MARBLED_BEEF -> MeatMaterials.BEEF_BRISKET
            PORK -> MeatMaterials.PORK_LOIN
            POULTRY -> MeatMaterials.POULTRY
            FISH -> MeatMaterials.SALMON
            OTHER -> MeatMaterials.OTHER
        }
    }
}

/**
 * Table 2 Resting Environment Boundary Presets from the carryover physics research paper.
 * Models free-stream natural convection, surface radiation emissivity, evaporative mass-transfer
 * impedance, local air pocket temperature, and bottom contact conduction symmetry.
 */
enum class RestingEnvironment(
    val displayName: String,
    val shortLabel: String,
    val hConvectiveWPerM2K: Double,
    val emissivity: Double,
    val evapMultiplier: Double,
    val ambientTempC: Double,
    val bottomFluxFactor: Double,
    val isInsulated: Boolean
) {
    WIRE_RACK(
        displayName = "Open Wire Rack (360° Air)",
        shortLabel = "Wire Rack",
        hConvectiveWPerM2K = 11.5,
        emissivity = 0.82,
        evapMultiplier = 1.00,
        ambientTempC = 23.0,
        bottomFluxFactor = 1.00,
        isInsulated = false
    ),
    WOOD_BOARD(
        displayName = "Wood Cutting Board",
        shortLabel = "Wood Board",
        hConvectiveWPerM2K = 8.5,
        emissivity = 0.78,
        evapMultiplier = 0.75,
        ambientTempC = 23.0,
        bottomFluxFactor = 0.45,
        isInsulated = false
    ),
    BUTCHER_PAPER(
        displayName = "Butcher Paper / Loose Tent",
        shortLabel = "Paper / Tent",
        hConvectiveWPerM2K = 5.0,
        emissivity = 0.35,
        evapMultiplier = 0.25,
        ambientTempC = 32.0,
        bottomFluxFactor = 0.35,
        isInsulated = true
    ),
    TIGHT_FOIL(
        displayName = "Tight Aluminum Foil Wrap",
        shortLabel = "Tight Foil",
        hConvectiveWPerM2K = 2.5,
        emissivity = 0.08,
        evapMultiplier = 0.02,
        ambientTempC = 38.0,
        bottomFluxFactor = 0.25,
        isInsulated = true
    ),
    CAMBRO_HOLD(
        displayName = "Insulated Cambro / Cooler",
        shortLabel = "Cambro Hold",
        hConvectiveWPerM2K = 1.6,
        emissivity = 0.15,
        evapMultiplier = 0.05,
        ambientTempC = 58.0,
        bottomFluxFactor = 0.20,
        isInsulated = true
    )
}

data class CarryoverPredictionResult(
    val peakCoreTempC: Double,
    val timeToPeakSeconds: Double,
    val pullTempC: Double,
    val restingCoreTrajectoryC: DoubleArray,
    val dtStepSeconds: Double
)

class CarryoverPredictor {

    companion object {
        private const val NUM_NODES = 30
        private const val SENSOR_SPACING_METERS = 0.006
        private const val STEFAN_BOLTZMANN = 5.670374419e-8
        private const val LATENT_HEAT_VAPORIZATION_J_PER_KG = 2.257e6
        private const val CRITICAL_MOISTURE_FRACTION = 0.52
    }

    private val grid = DoubleArray(NUM_NODES)
    private val nextGrid = DoubleArray(NUM_NODES)
    private val a = DoubleArray(NUM_NODES)
    private val b = DoubleArray(NUM_NODES)
    private val c = DoubleArray(NUM_NODES)
    private val d = DoubleArray(NUM_NODES)
    private val cPrime = DoubleArray(NUM_NODES)
    private val dPrime = DoubleArray(NUM_NODES)

    /**
     * Solves the Inverse Heat Transfer Problem (IHTP) online by estimating effective thermal
     * diffusivity (alpha_eff) from interior sensor curvature (d2T/dx2) and rate of change (dT/dt),
     * constrained by the Choi & Okos parallel/perpendicular muscle-fiber anisotropy envelope.
     */
    fun estimateEffectiveDiffusivity(
        currentTemps: List<Double>,
        prevTemps: DoubleArray?,
        dtSeconds: Double,
        coreIndex: Int,
        surfaceIndex: Int,
        baseDiffusivity: Double,
        meatType: MeatType = MeatType.BEEF
    ): Double {
        val meanInteriorTempC = if (currentTemps.isNotEmpty()) {
            val cIdx = coreIndex.coerceIn(0, currentTemps.lastIndex)
            val sIdx = surfaceIndex.coerceIn(cIdx, currentTemps.lastIndex)
            0.5 * (currentTemps[cIdx] + currentTemps[sIdx])
        } else {
            55.0
        }

        val props = meatType.evaluateProperties(meanInteriorTempC)
        val minAnisotropyBound = 0.92 * props.alphaPerpM2PerS
        val maxAnisotropyBound = 1.08 * props.alphaParallelM2PerS

        if (prevTemps == null || dtSeconds <= 0.5 || surfaceIndex - coreIndex < 2) {
            return baseDiffusivity.coerceIn(minAnisotropyBound, maxAnisotropyBound)
        }

        val dx2 = SENSOR_SPACING_METERS * SENSOR_SPACING_METERS
        var sumAlpha = 0.0
        var validNodes = 0

        for (i in (coreIndex + 1) until surfaceIndex.coerceAtMost(7)) {
            val dTdt = (currentTemps[i] - prevTemps[i]) / dtSeconds
            // Radial cylindrical Laplacian: d2T/dr2 + (1/r)(dT/dr)
            val rIdx = max(1, i - coreIndex)
            val d2Tdr2 = currentTemps[i - 1] - 2.0 * currentTemps[i] + currentTemps[i + 1]
            val dTdr = 0.5 * (currentTemps[i + 1] - currentTemps[i - 1]) / rIdx
            val radialLaplacian = d2Tdr2 + dTdr

            if (abs(radialLaplacian) > 0.20 && dTdt * radialLaplacian > 0.0) {
                val rawAlpha = (dTdt * dx2) / radialLaplacian
                sumAlpha += rawAlpha.coerceIn(minAnisotropyBound, maxAnisotropyBound)
                validNodes++
            }
        }

        return if (validNodes > 0) {
            (sumAlpha / validNodes).coerceIn(minAnisotropyBound, maxAnisotropyBound)
        } else {
            props.alphaMeanM2PerS.coerceIn(minAnisotropyBound, maxAnisotropyBound)
        }
    }

    /**
     * Estimates the falling-rate surface moisture availability factor phi_w in [0.05, 1.0]
     * from surface temperature history and interior-to-surface heat flux balance.
     */
    fun estimateSurfaceMoisturePhi(
        surfaceTempC: Double,
        subSurfaceTempC: Double,
        ambientTempC: Double,
        previousPhi: Double,
        dtSeconds: Double,
        isWrapped: Boolean
    ): Double {
        if (isWrapped) {
            return (previousPhi + 0.01 * dtSeconds).coerceIn(0.65, 1.0)
        }
        if (surfaceTempC < 62.0) {
            return previousPhi.coerceIn(0.35, 1.0)
        }

        // Falling-rate crust dehydration accelerates when surface climbs past the 68-85°C evaporative plateau
        val dryingDrive = ((surfaceTempC - 65.0).coerceAtLeast(0.0) / 45.0) *
            ((ambientTempC - surfaceTempC).coerceAtLeast(10.0) / 120.0)
        val rawDecay = 0.00018 * dryingDrive * dtSeconds.coerceIn(0.5, 15.0)

        // If surface temperature exceeds 96°C, crust is clearly in the falling-rate dry regime
        val tempCeilingPhi = when {
            surfaceTempC >= 105.0 -> 0.14
            surfaceTempC >= 95.0 -> 0.28
            surfaceTempC >= 85.0 -> 0.48
            else -> 1.0
        }

        val updated = min(previousPhi - rawDecay, tempCeilingPhi)
        return updated.coerceIn(0.08, 1.0)
    }

    /**
     * Runs a temperature-dependent Crank-Nicolson TDMA resting simulation with coupled
     * convective + Stefan-Boltzmann radiative + Lewis-analogy falling-rate evaporative boundary losses.
     * Integrates until the core temperature reaches its maximum (dT_core/dt <= 0) and returns
     * both Peak Core Temp, Time-to-Peak (t_peak), Pull Temp, and the full resting trajectory.
     */
    fun predictRestingTrajectory(
        profile: DoubleArray,
        radiusMeters: Double,
        alpha: Double,
        meatType: MeatType = MeatType.BEEF,
        restingEnvironment: RestingEnvironment = RestingEnvironment.WOOD_BOARD,
        surfaceMoisturePhi: Double = 0.75,
        targetTempC: Double = 57.2
    ): CarryoverPredictionResult {
        if (profile.size < 2) {
            val single = profile.firstOrNull() ?: 0.0
            return CarryoverPredictionResult(
                peakCoreTempC = single,
                timeToPeakSeconds = 0.0,
                pullTempC = targetTempC,
                restingCoreTrajectoryC = doubleArrayOf(single),
                dtStepSeconds = 2.0
            )
        }

        val srcMax = profile.size - 1
        for (i in 0 until NUM_NODES) {
            val pos = (i.toDouble() / (NUM_NODES - 1)) * srcMax
            val idx = pos.toInt().coerceIn(0, srcMax - 1)
            val frac = pos - idx
            grid[i] = profile[idx] * (1.0 - frac) + profile[idx + 1] * frac
        }

        val dx = max(0.0005, radiusMeters / (NUM_NODES - 1))
        val dt = 2.0
        val last = NUM_NODES - 1
        val maxSteps = 1200 // Up to 40 minutes of resting simulation

        var peakCoreTemp = grid[0]
        var timeToPeakSec = 0.0
        var localMoistureFraction = meatType.waterFraction * surfaceMoisturePhi.coerceIn(0.08, 1.0)
        val trajectory = DoubleArray(maxSteps + 1)
        trajectory[0] = grid[0]
        var recordedSteps = 1

        val env = restingEnvironment
        val ambientK = env.ambientTempC + 273.15
        // Asymmetry correction for bottom board/pan contact reducing effective exposed perimeter
        val symmetrySurfaceScale = 0.55 + 0.45 * env.bottomFluxFactor

        for (step in 1..maxSteps) {
            val meanTempC = 0.5 * (grid[0] + grid[last])
            val choiProps = meatType.evaluateProperties(meanTempC)
            val kThermal = choiProps.kMeanWPerMK
            val rhoCp = choiProps.densityKgM3 * choiProps.specificHeatJPerKgK
            val effectiveAlpha = alpha.coerceIn(
                0.85 * choiProps.alphaPerpM2PerS,
                1.15 * choiProps.alphaParallelM2PerS
            )

            val r = (effectiveAlpha * dt) / (2.0 * dx * dx)

            // 1. Center Core Node (i = 0)
            a[0] = 0.0
            b[0] = 1.0 + 4.0 * r
            c[0] = -4.0 * r
            d[0] = (1.0 - 4.0 * r) * grid[0] + 4.0 * r * grid[1]

            // 2. Interior Radial Nodes
            for (i in 1 until last) {
                val wMinus = 1.0 - (1.0 / (2.0 * i))
                val wPlus = 1.0 + (1.0 / (2.0 * i))
                a[i] = -r * wMinus
                b[i] = 1.0 + 2.0 * r
                c[i] = -r * wPlus
                d[i] = r * wMinus * grid[i - 1] + (1.0 - 2.0 * r) * grid[i] + r * wPlus * grid[i + 1]
            }

            // 3. Coupled Convective + Radiative + Falling-Rate Evaporative Surface Boundary
            val surfTempC = grid[last]
            val surfK = surfTempC + 273.15

            // Linearized Stefan-Boltzmann radiative heat transfer coefficient h_rad
            val hRad = env.emissivity * STEFAN_BOLTZMANN *
                (surfK + ambientK) * (surfK * surfK + ambientK * ambientK)
            val hTotal = (env.hConvectiveWPerM2K + hRad) * symmetrySurfaceScale
            val bi = (hTotal * dx) / kThermal

            // Falling-rate moisture availability phi_w = (M_s / M_crit)^2 below critical moisture
            val phiW = if (localMoistureFraction >= CRITICAL_MOISTURE_FRACTION) {
                1.0
            } else {
                (localMoistureFraction / CRITICAL_MOISTURE_FRACTION).pow(2.0).coerceIn(0.0, 1.0)
            }

            // Lewis-analogy vapor pressure Deficit evaporative cooling flux [W/m^2]
            val evapCoolingTempDrop = if (surfTempC > env.ambientTempC + 5.0 && env.evapMultiplier > 0.0) {
                // Tetens saturation vapor pressure difference approximation (kPa)
                val pSatSurfKPa = 0.61078 * exp((17.27 * surfTempC) / (surfTempC + 237.3))
                val pSatAmbKPa = 0.61078 * exp((17.27 * env.ambientTempC) / (env.ambientTempC + 237.3))
                val rhAmbient = if (env.isInsulated) 0.88 else 0.45
                val vaporDeficitKPa = (pSatSurfKPa - rhAmbient * pSatAmbKPa).coerceAtLeast(0.0)

                // Mass transfer coefficient from Lewis relation h_m = h_c / (rho_air * cp_air * Le^(2/3))
                val massFluxKgPerM2S = 6.2e-6 * env.hConvectiveWPerM2K *
                    env.evapMultiplier * phiW * vaporDeficitKPa * symmetrySurfaceScale
                val qEvapWPerM2 = LATENT_HEAT_VAPORIZATION_J_PER_KG * massFluxKgPerM2S

                // Update surface moisture inventory
                localMoistureFraction = max(0.04, localMoistureFraction - (massFluxKgPerM2S * dt) / (choiProps.densityKgM3 * dx * 4.0))

                // Convert surface heat flux sink into nodal temperature decrement over dt
                ((2.0 * qEvapWPerM2 * dt) / (rhoCp * dx)).coerceAtMost(1.8)
            } else {
                0.0
            }

            a[last] = -2.0 * r
            b[last] = 1.0 + 2.0 * r * (1.0 + bi)
            c[last] = 0.0
            d[last] = 2.0 * r * grid[last - 1] +
                (1.0 - 2.0 * r * (1.0 + bi)) * surfTempC +
                4.0 * r * bi * env.ambientTempC - evapCoolingTempDrop

            // 4. Thomas Algorithm (TDMA)
            cPrime[0] = c[0] / b[0]
            dPrime[0] = d[0] / b[0]
            for (i in 1..last) {
                val denom = b[i] - a[i] * cPrime[i - 1]
                cPrime[i] = if (i < last) c[i] / denom else 0.0
                dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / denom
            }

            nextGrid[last] = dPrime[last]
            for (i in last - 1 downTo 0) {
                nextGrid[i] = dPrime[i] - cPrime[i] * nextGrid[i + 1]
            }

            var maxGridTemp = nextGrid[0]
            for (i in 0..last) {
                grid[i] = nextGrid[i]
                if (grid[i] > maxGridTemp) maxGridTemp = grid[i]
            }

            val simulatedCore = grid[0]
            trajectory[step] = simulatedCore
            recordedSteps = step + 1

            if (simulatedCore > peakCoreTemp + 1e-4) {
                peakCoreTemp = simulatedCore
                timeToPeakSec = step * dt
            } else if (maxGridTemp <= peakCoreTemp || (step > 30 && simulatedCore < peakCoreTemp - 0.04)) {
                break
            }
        }

        val initialCoreC = profile.first()
        val predictedRise = (peakCoreTemp - initialCoreC).coerceAtLeast(0.0)
        val pullTempC = targetTempC - predictedRise

        return CarryoverPredictionResult(
            peakCoreTempC = peakCoreTemp,
            timeToPeakSeconds = timeToPeakSec,
            pullTempC = pullTempC,
            restingCoreTrajectoryC = trajectory.copyOf(recordedSteps),
            dtStepSeconds = dt
        )
    }

    /**
     * Runs a Crank-Nicolson TDMA resting simulation from an arbitrary internal temperature profile
     * and returns the peak resting core temperature.
     */
    fun predictPeakCoreTemp(
        profile: DoubleArray,
        radiusMeters: Double,
        alpha: Double,
        thermalConductivity: Double,
        isWrapped: Boolean,
        meatType: MeatType = MeatType.BEEF,
        restingEnvironment: RestingEnvironment? = null,
        surfaceMoisturePhi: Double = 0.75
    ): Double {
        val env = restingEnvironment ?: if (isWrapped) {
            RestingEnvironment.TIGHT_FOIL
        } else {
            RestingEnvironment.WOOD_BOARD
        }
        return predictRestingTrajectory(
            profile = profile,
            radiusMeters = radiusMeters,
            alpha = alpha,
            meatType = meatType,
            restingEnvironment = env,
            surfaceMoisturePhi = surfaceMoisturePhi
        ).peakCoreTempC
    }

    fun predictPeakTemp(
        targetTemp: Double,
        temperatures: List<Double>,
        coreIndex: Int,
        surfaceIndex: Int,
        meatType: MeatType,
        isWrapped: Boolean,
        customAlpha: Double? = null,
        restingEnvironment: RestingEnvironment? = null,
        surfaceMoisturePhi: Double = 0.75
    ): Double {
        if (coreIndex < 0 || surfaceIndex > 7 || coreIndex >= surfaceIndex) {
            return targetTemp
        }

        val spanCount = surfaceIndex - coreIndex + 1
        val activeProfile = DoubleArray(spanCount) { idx -> temperatures[coreIndex + idx] }
        val radiusMeters = (surfaceIndex - coreIndex) * SENSOR_SPACING_METERS
        val alpha = customAlpha ?: meatType.diffusivity
        val env = restingEnvironment ?: if (isWrapped) {
            RestingEnvironment.TIGHT_FOIL
        } else {
            RestingEnvironment.WOOD_BOARD
        }

        return predictRestingTrajectory(
            profile = activeProfile,
            radiusMeters = radiusMeters,
            alpha = alpha,
            meatType = meatType,
            restingEnvironment = env,
            surfaceMoisturePhi = surfaceMoisturePhi,
            targetTempC = targetTemp
        ).pullTempC
    }
}
