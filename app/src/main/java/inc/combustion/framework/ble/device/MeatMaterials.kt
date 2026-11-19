package inc.combustion.framework.ble.device

import kotlin.math.max

/**
 * Choi & Okos (1986) temperature- and composition-dependent thermophysical correlations
 * for unfrozen meat (0°C <= T <= 150°C), combined with parallel/perpendicular muscle-fiber
 * thermal conductivity anisotropy bounds.
 */
object ChoiOkosProperties {

    data class ThermophysicalSnapshot(
        val densityKgM3: Double,
        val specificHeatJPerKgK: Double,
        val kParallelWPerMK: Double,
        val kPerpWPerMK: Double,
        val kMeanWPerMK: Double,
        val alphaParallelM2PerS: Double,
        val alphaPerpM2PerS: Double,
        val alphaMeanM2PerS: Double
    )

    fun evaluate(
        tempC: Double,
        waterFraction: Double,
        proteinFraction: Double,
        fatFraction: Double,
        ashFraction: Double = max(0.01, 1.0 - (waterFraction + proteinFraction + fatFraction))
    ): ThermophysicalSnapshot {
        val t = tempC.coerceIn(0.0, 140.0)
        val t2 = t * t

        // Constituent densities rho_i(T) [kg/m^3]
        val rhoWater = 997.18 + 3.1439e-3 * t - 3.7574e-3 * t2
        val rhoProtein = 1329.9 - 0.5184 * t
        val rhoFat = 925.59 - 0.41757 * t
        val rhoAsh = 2423.8 - 0.28063 * t

        // Constituent specific heats c_p,i(T) [J/(kg*K)]
        val cpWater = 4176.2 - 0.090864 * t + 5.4731e-3 * t2
        val cpProtein = 2008.2 + 1.2089 * t - 1.3129e-3 * t2
        val cpFat = 1984.2 + 1.4733 * t - 4.8008e-3 * t2
        val cpAsh = 1092.6 + 1.8896 * t - 3.6817e-3 * t2

        // Constituent thermal conductivities k_i(T) [W/(m*K)]
        val kWater = 0.57109 + 1.7625e-3 * t - 6.7036e-6 * t2
        val kProtein = 0.17881 + 1.1958e-3 * t - 2.7178e-6 * t2
        val kFat = 0.18071 - 2.7604e-4 * t - 1.7749e-7 * t2
        val kAsh = 0.32962 + 1.4011e-3 * t - 2.9069e-6 * t2

        // Normalize mass fractions
        val sumX = max(1e-6, waterFraction + proteinFraction + fatFraction + ashFraction)
        val xW = waterFraction / sumX
        val xP = proteinFraction / sumX
        val xF = fatFraction / sumX
        val xA = ashFraction / sumX

        // Mixture specific heat c_p(T)
        val cpMix = xW * cpWater + xP * cpProtein + xF * cpFat + xA * cpAsh

        // Mixture density rho(T) via specific volume additivity
        val invRho = (xW / rhoWater) + (xP / rhoProtein) + (xF / rhoFat) + (xA / rhoAsh)
        val rhoMix = 1.0 / max(1e-6, invRho)

        // Volume fractions phi_i = (x_i / rho_i) * rhoMix
        val phiW = (xW / rhoWater) * rhoMix
        val phiP = (xP / rhoProtein) * rhoMix
        val phiF = (xF / rhoFat) * rhoMix
        val phiA = (xA / rhoAsh) * rhoMix

        // Parallel to muscle fibers (Upper Wiener bound): k_parallel = sum(phi_i * k_i)
        val kParallel = phiW * kWater + phiP * kProtein + phiF * kFat + phiA * kAsh

        // Perpendicular to muscle fibers (Lower Wiener / Series bound): 1/k_perp = sum(phi_i / k_i)
        val invKPerp = (phiW / kWater) + (phiP / kProtein) + (phiF / kFat) + (phiA / kAsh)
        val kPerp = 1.0 / max(1e-6, invKPerp)

        // Effective isotropic mean k (weighted toward parallel myofibrillar transport)
        val kMean = 0.55 * kParallel + 0.45 * kPerp

        val heatCapacityVolumetric = max(1e3, rhoMix * cpMix)
        val alphaParallel = kParallel / heatCapacityVolumetric
        val alphaPerp = kPerp / heatCapacityVolumetric
        val alphaMean = kMean / heatCapacityVolumetric

        return ThermophysicalSnapshot(
            densityKgM3 = rhoMix,
            specificHeatJPerKgK = cpMix,
            kParallelWPerMK = kParallel,
            kPerpWPerMK = kPerp,
            kMeanWPerMK = kMean,
            alphaParallelM2PerS = alphaParallel,
            alphaPerpM2PerS = alphaPerp,
            alphaMeanM2PerS = alphaMean
        )
    }
}

enum class MeatMaterials(
    val displayName: String,
    val diffusivity: Double,
    val waterContentPercent: Double,
    val thermalConductivity: Double,
    val proteinMassFraction: Double,
    val fatMassFraction: Double,
    val ashMassFraction: Double = 0.01
) {
    // Table 1 compositions from research paper
    BEEF_STEAK("Lean Beef (6% Fat)", 1.32e-7, 0.72, 0.49, 0.21, 0.06, 0.01),
    BEEF_BRISKET("Marbled Beef (23% Fat)", 1.15e-7, 0.58, 0.41, 0.18, 0.23, 0.01),
    PORK_SHOULDER("Pork Shoulder", 1.26e-7, 0.68, 0.46, 0.20, 0.11, 0.01),
    PORK_LOIN("Pork Loin (6% Fat)", 1.31e-7, 0.72, 0.48, 0.21, 0.06, 0.01),
    POULTRY("Poultry Breast (2% Fat)", 1.38e-7, 0.74, 0.51, 0.23, 0.02, 0.01),
    SALMON("Salmon Fillet (14% Fat)", 1.21e-7, 0.65, 0.45, 0.20, 0.14, 0.01),
    OTHER("Other / Average", 1.30e-7, 0.70, 0.48, 0.20, 0.09, 0.01);

    fun evaluateAt(tempC: Double): ChoiOkosProperties.ThermophysicalSnapshot {
        return ChoiOkosProperties.evaluate(
            tempC = tempC,
            waterFraction = waterContentPercent,
            proteinFraction = proteinMassFraction,
            fatFraction = fatMassFraction,
            ashFraction = ashMassFraction
        )
    }
}
