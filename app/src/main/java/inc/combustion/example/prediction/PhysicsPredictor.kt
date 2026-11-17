package inc.combustion.example.prediction

import android.util.Log
import inc.combustion.framework.ble.device.MeatMaterials
import inc.combustion.framework.ble.device.ThermalSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The V4 Brain.
 * Uses the Physics Engine to predict "Time to Done" (TTD).
 */
class PhysicsPredictor {

    companion object {
        private const val TAG = "PhysicsPredictor"
    }

    suspend fun predictSecondsToDone(
        currentCoreTemp: Double,
        currentSurfaceTemp: Double,
        targetTemp: Double,
        meatType: MeatMaterials
    ): Long = withContext(Dispatchers.Default) {

        if (currentSurfaceTemp <= targetTemp && currentCoreTemp < targetTemp) {
            return@withContext -1L
        }

        val sim = ThermalSimulator(
            diffusivity = meatType.diffusivity,
            thicknessMeters = 0.04,
            numNodes = 8,
            waterContentPercent = meatType.waterContentPercent
        )

        var simulatedCore = currentCoreTemp
        var secondsElapsed = 0L
        val maxSeconds = 24L * 60L * 60L

        // Pre-warm the simulator until virtual core reaches current real core temp
        var warmupSteps = 0
        while (sim.step(ambientTemp = currentSurfaceTemp, heatTransferCoefficient = 15.0)[7] < currentCoreTemp) {
            if (++warmupSteps > maxSeconds) return@withContext -1L
        }

        // Fast-forward loop
        while (simulatedCore < targetTemp && secondsElapsed < maxSeconds) {
            val newTemps = sim.step(ambientTemp = currentSurfaceTemp, heatTransferCoefficient = 15.0)
            simulatedCore = newTemps[7]
            secondsElapsed++
        }

        if (secondsElapsed >= maxSeconds) {
            return@withContext -1L
        }

        Log.d(TAG, "V4 Prediction: ${secondsElapsed / 60} minutes to reach $targetTemp°C")
        secondsElapsed
    }
}
