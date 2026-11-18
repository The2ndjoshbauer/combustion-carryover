package inc.combustion.example.prediction

import android.util.Log
import inc.combustion.example.CarryoverPredictor
import inc.combustion.framework.ble.device.MeatMaterials
import inc.combustion.framework.ble.device.ThermalSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The V4 Oracle Brain.
 * Couples the Crank-Nicolson active cooking simulator with the resting CarryoverPredictor
 * to predict "Time to Pull" rather than overcooking to target on the heat.
 */
class PhysicsPredictor {

    companion object {
        private const val TAG = "PhysicsPredictor"
        private const val SENSOR_SPACING_METERS = 0.006
    }

    private val carryoverOracle = CarryoverPredictor()

    suspend fun predictSecondsToPull(
        temperaturesCelsius: List<Double>,
        coreIndex: Int,
        surfaceIndex: Int,
        ambientTempC: Double,
        targetTempC: Double,
        meatType: MeatMaterials,
        isWrapped: Boolean = false,
        customDiffusivity: Double? = null
    ): Long = withContext(Dispatchers.Default) {

        val validCoreIdx = coreIndex.coerceIn(0, 6)
        val validSurfIdx = surfaceIndex.coerceIn(validCoreIdx + 1, 7)

        val currentCoreC = temperaturesCelsius.getOrElse(validCoreIdx) { 0.0 }
        val currentSurfC = temperaturesCelsius.getOrElse(validSurfIdx) { 0.0 }
        val drivingAmbientC = max(ambientTempC, currentSurfC)

        // If neither the ambient environment nor current surface can reach target, return error
        if (drivingAmbientC <= targetTempC && currentCoreC < targetTempC) {
            return@withContext -1L
        }

        val activeProfile = DoubleArray(validSurfIdx - validCoreIdx + 1) { i ->
            temperaturesCelsius[validCoreIdx + i]
        }

        val radiusMeters = max(0.012, (validSurfIdx - validCoreIdx) * SENSOR_SPACING_METERS)
        val alpha = customDiffusivity ?: meatType.diffusivity

        // Check if meat has already reached the Pull Zone right now
        val initialRestingPeak = carryoverOracle.predictPeakCoreTemp(
            profile = activeProfile,
            radiusMeters = radiusMeters,
            alpha = alpha,
            thermalConductivity = meatType.thermalConductivity,
            isWrapped = isWrapped
        )
        if (initialRestingPeak >= targetTempC) {
            return@withContext 0L
        }

        val sim = ThermalSimulator(
            diffusivity = alpha,
            thicknessMeters = radiusMeters,
            numNodes = 30,
            waterContentPercent = if (isWrapped) 0.0 else meatType.waterContentPercent,
            thermalConductivity = meatType.thermalConductivity,
            initialProfile = activeProfile
        )

        var secondsElapsed = 0L
        val dtStep = 5L
        val maxSeconds = 24L * 60L * 60L

        // Estimate convective heat transfer coefficient h_c based on cooking regime (Table 2)
        val hcActive = when {
            drivingAmbientC > 230.0 -> 45.0
            drivingAmbientC > 150.0 -> 22.0
            else -> 15.0
        }

        while (secondsElapsed < maxSeconds) {
            sim.step(
                ambientTemp = drivingAmbientC,
                heatTransferCoefficient = hcActive,
                dtSeconds = dtStep.toDouble()
            )
            secondsElapsed += dtStep

            // Every 15 simulated seconds, check the Oracle resting carryover peak
            if (secondsElapsed % 15L == 0L || sim.getCoreTemp() >= targetTempC) {
                val projectedPeak = carryoverOracle.predictPeakCoreTemp(
                    profile = sim.getCurrentProfile(),
                    radiusMeters = radiusMeters,
                    alpha = alpha,
                    thermalConductivity = meatType.thermalConductivity,
                    isWrapped = isWrapped
                )
                if (projectedPeak >= targetTempC) {
                    break
                }
            }
        }

        if (secondsElapsed >= maxSeconds) {
            return@withContext -1L
        }

        Log.d(TAG, "V4 Oracle Time to Pull: ${secondsElapsed / 60} min for target $targetTempC°C")
        secondsElapsed
    }
}
