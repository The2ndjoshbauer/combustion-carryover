package inc.combustion.example

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

        // Stability limit for 1D cylindrical radial finite-difference method
        if (Fo > 0.25) return targetTemp

        var simulatedCoreTemp = grid[0]
        var peakCoreTemp = simulatedCoreTemp
        val ambientAirTemp = 25.0

        // Reduced cooling rate when wrapped in foil/butcher paper vs. uncovered
        val surfaceCoolingRate = if (isWrapped) 0.0012 else 0.004

        for (timeStep in 1..1200) {
            val newGrid = ArrayList<Double>(grid)

            // Interior nodes (1D cylindrical radial coordinates)
            for (i in 1 until grid.size - 1) {
                val radialWeightPlus = 1.0 + (1.0 / (2.0 * i))
                val radialWeightMinus = 1.0 - (1.0 / (2.0 * i))
                val diffusion = Fo * (
                    radialWeightMinus * grid[i - 1] - 2.0 * grid[i] + radialWeightPlus * grid[i + 1]
                )
                newGrid[i] = grid[i] + diffusion
            }

            // Center core node (r = 0 symmetry boundary)
            newGrid[0] = grid[0] + (4.0 * Fo * (grid[1] - grid[0]))

            // Outer surface node with ambient convective cooling
            val surfaceTemp = grid[grid.size - 1]
            val prevTemp = grid[grid.size - 2]
            val internalFlux = Fo * (prevTemp - surfaceTemp)
            val coolingFlux = -surfaceCoolingRate * (surfaceTemp - ambientAirTemp)

            newGrid[grid.size - 1] = surfaceTemp + internalFlux + coolingFlux

            grid = newGrid
            simulatedCoreTemp = grid[0]

            val maxGridTemp = grid.maxOrNull() ?: simulatedCoreTemp
            if (simulatedCoreTemp > peakCoreTemp) {
                peakCoreTemp = simulatedCoreTemp
            } else if (maxGridTemp <= peakCoreTemp || (timeStep > 90 && simulatedCoreTemp < peakCoreTemp - 0.05)) {
                break
            }
        }

        val predictedRise = peakCoreTemp - temperatures[coreIndex]
        val validRise = if (predictedRise > 0) predictedRise else 0.0

        return targetTemp - validRise
    }
}
