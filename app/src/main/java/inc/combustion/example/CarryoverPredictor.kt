package inc.combustion.example

import kotlin.math.abs
import kotlin.math.max

enum class MeatType(
    val displayName: String,
    val diffusivity: Double,
    val thermalConductivity: Double
) {
    BEEF("Beef (Steak)", 1.35e-7, 0.49),
    PORK("Pork (Loin)", 1.33e-7, 0.48),
    POULTRY("Chicken/Turkey", 1.42e-7, 0.51),
    FISH("Fish (Salmon)", 1.30e-7, 0.46),
    OTHER("Other (Average)", 1.35e-7, 0.48)
}

class CarryoverPredictor {

    companion object {
        private const val NUM_NODES = 30
        private const val SENSOR_SPACING_METERS = 0.006
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
     * diffusivity (alpha_eff) from interior sensor curvature (d2T/dx2) and rate of change (dT/dt).
     */
    fun estimateEffectiveDiffusivity(
        currentTemps: List<Double>,
        prevTemps: DoubleArray?,
        dtSeconds: Double,
        coreIndex: Int,
        surfaceIndex: Int,
        baseDiffusivity: Double
    ): Double {
        if (prevTemps == null || dtSeconds <= 0.5 || surfaceIndex - coreIndex < 2) {
            return baseDiffusivity
        }

        val dx2 = SENSOR_SPACING_METERS * SENSOR_SPACING_METERS
        var sumAlpha = 0.0
        var validNodes = 0

        for (i in (coreIndex + 1) until surfaceIndex.coerceAtMost(7)) {
            val dTdt = (currentTemps[i] - prevTemps[i]) / dtSeconds
            val laplacian = currentTemps[i - 1] - 2.0 * currentTemps[i] + currentTemps[i + 1]

            if (abs(laplacian) > 0.25 && dTdt * laplacian > 0.0) {
                val rawAlpha = (dTdt * dx2) / laplacian
                val minBound = baseDiffusivity * 0.75
                val maxBound = baseDiffusivity * 1.25
                sumAlpha += rawAlpha.coerceIn(minBound, maxBound)
                validNodes++
            }
        }

        return if (validNodes > 0) sumAlpha / validNodes else baseDiffusivity
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
        isWrapped: Boolean
    ): Double {
        if (profile.size < 2) return profile.firstOrNull() ?: 0.0

        val srcMax = profile.size - 1
        for (i in 0 until NUM_NODES) {
            val pos = (i.toDouble() / (NUM_NODES - 1)) * srcMax
            val idx = pos.toInt().coerceIn(0, srcMax - 1)
            val frac = pos - idx
            grid[i] = profile[idx] * (1.0 - frac) + profile[idx + 1] * frac
        }

        val dx = max(0.0005, radiusMeters / (NUM_NODES - 1))
        val dt = 2.0
        val r = (alpha * dt) / (2.0 * dx * dx)

        // Resting convective coefficient h_c (Table 2: Countertop natural convection vs. foil wrap)
        val hc = if (isWrapped) 2.5 else 10.0
        val bi = (hc * dx) / thermalConductivity
        val ambientAirTemp = 23.0
        val last = NUM_NODES - 1

        var peakCoreTemp = grid[0]

        for (step in 1..900) {
            // Center Core Node (i = 0)
            a[0] = 0.0
            b[0] = 1.0 + 4.0 * r
            c[0] = -4.0 * r
            d[0] = (1.0 - 4.0 * r) * grid[0] + 4.0 * r * grid[1]

            // Interior Radial Nodes
            for (i in 1 until last) {
                val wMinus = 1.0 - (1.0 / (2.0 * i))
                val wPlus = 1.0 + (1.0 / (2.0 * i))
                a[i] = -r * wMinus
                b[i] = 1.0 + 2.0 * r
                c[i] = -r * wPlus
                d[i] = r * wMinus * grid[i - 1] + (1.0 - 2.0 * r) * grid[i] + r * wPlus * grid[i + 1]
            }

            // Surface Node with Convective + Unwrapped Evaporative Flash-Cooling
            val surfTemp = grid[last]
            val evapLoss = if (!isWrapped && surfTemp > 70.0) {
                0.0025 * (surfTemp - 70.0) * dt
            } else {
                0.0
            }

            a[last] = -2.0 * r
            b[last] = 1.0 + 2.0 * r * (1.0 + bi)
            c[last] = 0.0
            d[last] = 2.0 * r * grid[last - 1] +
                (1.0 - 2.0 * r * (1.0 + bi)) * surfTemp +
                4.0 * r * bi * ambientAirTemp - evapLoss

            // Thomas Algorithm (TDMA)
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
            if (simulatedCore > peakCoreTemp) {
                peakCoreTemp = simulatedCore
            } else if (maxGridTemp <= peakCoreTemp || (step > 45 && simulatedCore < peakCoreTemp - 0.05)) {
                break
            }
        }

        return peakCoreTemp
    }

    fun predictPeakTemp(
        targetTemp: Double,
        temperatures: List<Double>,
        coreIndex: Int,
        surfaceIndex: Int,
        meatType: MeatType,
        isWrapped: Boolean,
        customAlpha: Double? = null
    ): Double {
        if (coreIndex < 0 || surfaceIndex > 7 || coreIndex >= surfaceIndex) {
            return targetTemp
        }

        val spanCount = surfaceIndex - coreIndex + 1
        val activeProfile = DoubleArray(spanCount) { idx -> temperatures[coreIndex + idx] }
        val radiusMeters = (surfaceIndex - coreIndex) * SENSOR_SPACING_METERS
        val alpha = customAlpha ?: meatType.diffusivity

        val peakCoreTemp = predictPeakCoreTemp(
            profile = activeProfile,
            radiusMeters = radiusMeters,
            alpha = alpha,
            thermalConductivity = meatType.thermalConductivity,
            isWrapped = isWrapped
        )

        val predictedRise = (peakCoreTemp - temperatures[coreIndex]).coerceAtLeast(0.0)
        return targetTemp - predictedRise
    }
}
