package inc.combustion.framework.ble.device

import kotlin.math.max
import kotlin.math.min

class ThermalSimulator(
    private val diffusivity: Double,
    private val thicknessMeters: Double = 0.04,
    private val numNodes: Int = 8,
    private val waterContentPercent: Double = 0.70,
    initialTemp: Double = 20.0
) {
    private var temps = DoubleArray(numNodes) { initialTemp }
    private var remainingSurfaceMoisture = waterContentPercent
    private val dx = (thicknessMeters / 2.0) / max(1, numNodes - 1)
    private val dt = 1.0
    private val thermalConductivity = 0.48

    fun step(ambientTemp: Double, heatTransferCoefficient: Double = 15.0): DoubleArray {
        val nextTemps = temps.copyOf()
        val fo = min(0.25, (diffusivity * dt) / (dx * dx))
        val bi = (heatTransferCoefficient * dx) / thermalConductivity

        // Surface node (index 0) with convection and evaporative stall cooling
        var surfaceFlux = 2.0 * fo * (temps[1] - temps[0] + bi * (ambientTemp - temps[0]))
        if (temps[0] >= 68.0 && remainingSurfaceMoisture > 0.05 && surfaceFlux > 0.0) {
            val stallFactor = if (temps[0] in 68.0..79.0) 0.25 else 0.65
            surfaceFlux *= stallFactor
            remainingSurfaceMoisture = max(0.0, remainingSurfaceMoisture - 0.00004)
        }
        nextTemps[0] = temps[0] + surfaceFlux

        // Interior nodes (1 to numNodes - 2)
        for (i in 1 until numNodes - 1) {
            nextTemps[i] = temps[i] + fo * (temps[i - 1] - 2.0 * temps[i] + temps[i + 1])
        }

        // Core node (index 7) insulated symmetry boundary
        val last = numNodes - 1
        nextTemps[last] = temps[last] + 2.0 * fo * (temps[last - 1] - temps[last])

        temps = nextTemps
        return temps
    }
}
