package inc.combustion.example

import android.util.Log

enum class MeatType(val displayName: String, val diffusivity: Double) {
    BEEF("Beef (Steak)", 1.35e-7),
    PORK("Pork (Loin)", 1.33e-7),
    POULTRY("Chicken/Turkey", 1.42e-7),
    FISH("Fish (Salmon)", 1.30e-7),
    OTHER("Other (Average)", 1.35e-7)
}

class CarryoverPredictor {

    fun predictPeakTemp(
        targetTemp: Double,
        temperatures: List<Double>,
        coreIndex: Int,
        surfaceIndex: Int,
        meatType: MeatType,
        isWrapped: Boolean 
    ): Double {

        if (coreIndex < 0 || surfaceIndex > 7 || coreIndex >= surfaceIndex) {
            return targetTemp 
        }
        
        var grid = ArrayList<Double>()
        for (i in coreIndex..surfaceIndex) {
            grid.add(temperatures[i])
        }

        val alpha = meatType.diffusivity 
        val dx = 0.006 
        val dt = 1.0   
        val Fo = (alpha * dt) / (dx * dx)

        if (Fo > 0.5) return targetTemp 

        var simulatedCoreTemp = grid[0]
        var peakCoreTemp = simulatedCoreTemp
        val ambientAirTemp = 25.0 
        
        val surfaceCoolingRate = if (isWrapped) 0.0 else 0.005 

        for (timeStep in 1..1200) {
            val newGrid = ArrayList<Double>(grid)

            for (i in 1 until grid.size - 1) {
                val diffusion = Fo * (grid[i - 1] - 2 * grid[i] + grid[i + 1])
                newGrid[i] = grid[i] + diffusion
            }

            newGrid[0] = grid[0] + (Fo * 2 * (grid[1] - grid[0]))

            val T_surface = grid[grid.size - 1]
            val T_prev = grid[grid.size - 2]
            val internalFlux = Fo * (T_prev - T_surface)
            val coolingFlux = -surfaceCoolingRate * (T_surface - ambientAirTemp)
            
            newGrid[grid.size - 1] = T_surface + internalFlux + coolingFlux

            grid = newGrid
            simulatedCoreTemp = grid[0]

            if (simulatedCoreTemp > peakCoreTemp) {
                peakCoreTemp = simulatedCoreTemp
            } else {
                if (simulatedCoreTemp < peakCoreTemp - 0.05) break
            }
        }

        val predictedRise = peakCoreTemp - temperatures[coreIndex]
        val validRise = if (predictedRise > 0) predictedRise else 0.0
        
        return targetTemp - validRise
    }
}