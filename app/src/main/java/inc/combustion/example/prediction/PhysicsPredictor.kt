package inc.combustion.example.prediction

import android.util.Log
import inc.combustion.framework.ble.device.MeatMaterials
import inc.combustion.framework.ble.device.ThermalSimulator
import kotlin.math.abs

/**
 * The V4 Brain.
 * Uses the Physics Engine to predict "Time to Done" (TTD).
 */
class PhysicsPredictor {

    companion object {
        private const val TAG = "PhysicsPredictor"
    }

    /**
     * PREDICTION ALGORITHM:
     * 1. Initialize a "Virtual Steak" with the user's selected meat settings.
     * 2. Set its starting temp to the *current* real probe temp.
     * 3. Run the simulation loop roughly 1000x faster than real time.
     * 4. Count how many seconds it takes to hit the target.
     *
     * @param currentCoreTemp The real T1 reading right now.
     * @param currentSurfaceTemp The real T8 reading right now.
     * @param targetTemp The user's goal (e.g., 96°C for brisket).
     * @param meatType The "Prior" (e.g., BRISKET, SALMON).
     */
    fun predictSecondsToDone(
        currentCoreTemp: Double,
        currentSurfaceTemp: Double,
        targetTemp: Double,
        meatType: MeatMaterials
    ): Long {
        
        // 1. Setup the Simulation (The "Digital Twin")
        // We use the diffusivity from the USDA Prior (MeatMaterials)
        // In the next update, we will "optimize" this value based on real data.
        val sim = ThermalSimulator(
            diffusivity = meatType.diffusivity,
            thicknessMeters = 0.04, // 4cm assumption (We will solve for this later)
            numNodes = 8,
            waterContentPercent = meatType.waterContentPercent // Enables Stall Logic
        )

        // 2. Pre-warm the simulator to the current state
        // (Simplification: We assume the whole steak is at the current gradient. 
        // A true PINN would fit the entire history curve here.)
        var simulatedCore = currentCoreTemp
        var secondsElapsed: Long = 0
        
        // Safety Break (Don't run forever if target is impossible)
        val maxSeconds = 24 * 60 * 60 // 24 Hours

        // 3. Fast-Forward Loop
        while (simulatedCore < targetTemp && secondsElapsed < maxSeconds) {
            
            // Step physics forward by 1 second
            // We assume the Oven Temp stays constant at the current surface temp (T8)
            val newTemps = sim.step(ambientTemp = currentSurfaceTemp, heatTransferCoefficient = 15.0)
            
            // T1 is the core (index 7 in our 8-node model)
            simulatedCore = newTemps[7]
            
            secondsElapsed++
        }

        Log.d(TAG, "V4 Prediction: ${secondsElapsed / 60} minutes to reach $targetTemp°C")
        
        return secondsElapsed
    }
}