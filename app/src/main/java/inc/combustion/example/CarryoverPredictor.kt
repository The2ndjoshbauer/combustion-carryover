package inc.combustion.example

import android.util.Log
import java.util.Date

class CarryoverPredictor {

    // MEMORY: We need to remember the past to calculate speed
    private var previousCoreTemp: Double? = null
    private var previousTime: Long? = null
    
    // SMOOTHING: Temperature jumps around. We calculate average speed over time.
    // This value stores the calculated "Degrees Per Minute"
    private var currentRateOfRise: Double = 0.0

    /**
     * V2 HYBRID ENGINE (Physics + Velocity)
     * Calculates pull temp based on thermal momentum AND speed.
     */
    fun calculatePullTemp(targetFinalTemp: Double, currentCore: Double, currentSurface: Double): Double {
        
        // 1. SAFETY CHECKS
        if (currentCore.isNaN() || currentSurface.isNaN() || currentCore < 0 || currentSurface > 1000) {
            return targetFinalTemp
        }

        val currentTime = System.currentTimeMillis()

        // 2. CALCULATE VELOCITY (Rate of Rise)
        // We only update the speed calculation every 5 seconds to avoid jitter
        if (previousCoreTemp != null && previousTime != null) {
            val timeDiffSeconds = (currentTime - previousTime!!) / 1000.0
            
            if (timeDiffSeconds >= 5.0) {
                val tempDiff = currentCore - previousCoreTemp!!
                
                // Convert to "Degrees Per Minute"
                // Example: Rose 0.1 degrees in 6 seconds = 1.0 degree/minute
                val instantRate = (tempDiff / timeDiffSeconds) * 60.0
                
                // Smoothing: Don't just take the new number, blend it with the old one (80% old, 20% new)
                // This prevents one bad sensor reading from ruining the math.
                currentRateOfRise = (currentRateOfRise * 0.8) + (instantRate * 0.2)
                
                // Update memory
                previousCoreTemp = currentCore
                previousTime = currentTime
            }
        } else {
            // First run, just Initialize
            previousCoreTemp = currentCore
            previousTime = currentTime
        }

        // 3. THE V2 FORMULA
        // ------------------------------------------
        
        // Part A: The Gradient (How much hotter is the outside?)
        val heatGradient = currentSurface - currentCore
        // If outside is cooler than inside, carryover is zero.
        if (heatGradient <= 0) return targetFinalTemp

        // Part B: The Momentum (How fast are we moving?)
        // If we are rising fast (high heat), carryover is bigger.
        // If we are stalling (low heat), carryover is smaller.
        
        // FACTOR 1: Gradient Impact (10% of the difference travels in)
        val gradientRise = heatGradient * 0.10
        
        // FACTOR 2: Velocity Impact (For every 1 degree/min of speed, add 2 degrees of carryover)
        // Note: We clamp this so it can't be negative
        val velocityRise = if (currentRateOfRise > 0) (currentRateOfRise * 2.0) else 0.0

        // Combine them
        val totalPredictedRise = gradientRise + velocityRise

        return targetFinalTemp - totalPredictedRise
    }
}