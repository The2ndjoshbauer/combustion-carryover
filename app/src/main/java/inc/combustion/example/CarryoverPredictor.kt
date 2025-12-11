package inc.combustion.example

import android.util.Log

enum class MeatType(val displayName: String, val diffusivity: Double) {
    // Diffusivity values (alpha) from Research Table 1
    // Units: 10^-7 m^2/s.
    BEEF("Beef (Steak)", 1.35e-7),
    PORK("Pork (Loin)", 1.33e-7),
    POULTRY("Chicken/Turkey", 1.42e-7),
    FISH("Fish (Salmon)", 1.30e-7),
    OTHER("Other (Average)", 1.35e-7)
}

class CarryoverPredictor {

    /**
     * V3.5 PHYSICS ENGINE (Finite Difference Simulation)
     * Simulates the "Resting" phase to find the peak temperature.
     * * @param isWrapped If true, assumes the meat is insulated (foil/cooler), preventing heat loss.
     */
    fun predictPeakTemp(
        targetTemp: Double,
        temperatures: List<Double>, // All 8 sensors
        coreIndex: Int,            // Which sensor is T_Core
        surfaceIndex: Int,         // Which sensor is T_Surface
        meatType: MeatType,
        isWrapped: Boolean         // <--- NEW PARAMETER
    ): Double {

        // 1. SETUP THE GRID
        // We only care about the meat from Core to Surface.
        // If Core is T1 and Surface is T6, we have 6 nodes.
        if (coreIndex < 0 || surfaceIndex > 7 || coreIndex >= surfaceIndex) {
            return targetTemp // Invalid geometry
        }
        
        // Extract just the meat temperatures for our simulation grid
        // Copy them to a mutable list so we can simulate "future" changes
        var grid = ArrayList<Double>()
        for (i in coreIndex..surfaceIndex) {
            grid.add(temperatures[i])
        }

        // 2. SIMULATION PARAMETERS
        val alpha = meatType.diffusivity // Thermal Diffusivity
        val dx = 0.006 // Distance between sensors (~6mm)
        val dt = 1.0   // Time step (1 second)
        
        // The "Stability Criterion" for the simulation (Fourier Number)
        // Fo = alpha * dt / dx^2
        val Fo = (alpha * dt) / (dx * dx)

        // Safety: If Fo > 0.5, the simulation becomes unstable (math explodes).
        if (Fo > 0.5) return targetTemp 

        // 3. RUN THE SIMULATION LOOP (The "Crystal Ball")
        // We simulate up to 20 minutes (1200 seconds) into the future
        var simulatedCoreTemp = grid[0]
        var peakCoreTemp = simulatedCoreTemp
        
        // We assume "Resting Conditions":
        val ambientAirTemp = 25.0 
        
        // BOUNDARY CONDITION LOGIC:
        // If Wrapped (Foil/Cooler): Heat cannot escape. Cooling rate is 0.0 (Insulated).
        // If Unwrapped (Air): Heat escapes via natural convection. Cooling rate is 0.005.
        val surfaceCoolingRate = if (isWrapped) 0.0 else 0.005 

        for (timeStep in 1..1200) {
            val newGrid = ArrayList<Double>(grid)

            // A. Update Interior Nodes (Heat Diffusion)
            // Temperature change depends on neighbors (T_left, T_right)
            for (i in 1 until grid.size - 1) {
                val T_current = grid[i]
                val T_left = grid[i - 1]
                val T_right = grid[i + 1]
                
                // The Heat Equation: dT/dt = alpha * d^2T/dx^2
                val diffusion = Fo * (T_left - 2 * T_current + T_right)
                newGrid[i] = T_current + diffusion
            }

            // B. Update Core Node (Symmetry Condition)
            // Heat can't flow "past" the center, so T_left is effectively T_right
            val T_core = grid[0]
            val T_next = grid[1]
            newGrid[0] = T_core + (Fo * 2 * (T_next - T_core))

            // C. Update Surface Node (Boundary Condition)
            // Surface loses heat to the air
            val T_surface = grid[grid.size - 1]
            val T_prev = grid[grid.size - 2]
            
            // Diffusion from inside + Cooling to outside
            val internalFlux = Fo * (T_prev - T_surface)
            val coolingFlux = -surfaceCoolingRate * (T_surface - ambientAirTemp)
            
            newGrid[grid.size - 1] = T_surface + internalFlux + coolingFlux

            // D. Check for Peak
            grid = newGrid
            simulatedCoreTemp = grid[0]

            if (simulatedCoreTemp > peakCoreTemp) {
                peakCoreTemp = simulatedCoreTemp
            } else {
                // If core temp drops for 10 seconds straight, we found the peak.
                // (Simplified here to just breaking on drop for speed)
                if (simulatedCoreTemp < peakCoreTemp - 0.05) {
                    break
                }
            }
        }

        // 4. CALCULATE PULL TEMP
        // If the simulation says "It will rise 5 degrees", we pull 5 degrees early.
        val predictedRise = peakCoreTemp - temperatures[coreIndex]
        
        // Safety: Only positive rise
        val validRise = if (predictedRise > 0) predictedRise else 0.0
        
        return targetTemp - validRise
    }
}