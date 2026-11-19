package inc.combustion.example.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import inc.combustion.example.CarryoverPredictor
import inc.combustion.example.MeatType
import inc.combustion.example.RestingEnvironment
import inc.combustion.framework.service.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

data class GlidePathSample(
    val timestampMs: Long,
    val temperaturesC: DoubleArray,
    val coreIndex: Int,
    val surfaceIndex: Int,
    val coreTempC: Double,
    val surfaceTempC: Double,
    val effectiveAlpha: Double
)

object ThermalDoseIntegrator {
    private const val GAS_CONSTANT_R = 8.314462618 // J/(mol*K)
    private const val COLLAGEN_EA_J_PER_MOL = 156500.0 // 156.5 kJ/mol per research paper
    // Calibrated so k_coll(80°C / 353.15K) = 1.15e-4 s^-1 (~35% per hour at 80°C, ~85% at 92°C)
    private const val COLLAGEN_REF_TEMP_K = 353.15
    private const val COLLAGEN_REF_RATE_PER_SEC = 1.15e-4

    /**
     * Computes instantaneous USDA FSIS Appendix A Salmonella log10 reduction rate (log10 / second)
     * at a given temperature in Celsius, incorporating the 2025 AMIF slow come-up adaptation factor.
     */
    fun salmonellaLog10RatePerSecond(
        tempC: Double,
        meatType: MeatType,
        slowComeUpAdapted: Boolean = false
    ): Double {
        if (tempC < 52.0) return 0.0
        val baseD54_4Min = meatType.refD54_4Minutes
        // 2025 AMIF slow come-up (>120 min between 10°C and 54.4°C) increases D(54.4°C) by 1.8x (13.0 -> 23.4 min)
        val effectiveDRefSeconds = (if (slowComeUpAdapted) baseD54_4Min * 1.8 else baseD54_4Min) * 60.0
        val exponent = (tempC - 54.44) / meatType.zValueC
        return (10.0.pow(exponent)) / effectiveDRefSeconds
    }

    /**
     * Integrates a resting core temperature trajectory [°C] spaced by dtSeconds to predict
     * the additional USDA FSIS Salmonella log10 lethality accumulated during the carryover rest.
     */
    fun integrateTrajectorySalmonellaLog10(
        trajectoryC: DoubleArray,
        dtSeconds: Double,
        meatType: MeatType,
        slowComeUpAdapted: Boolean = false
    ): Double {
        if (trajectoryC.isEmpty() || dtSeconds <= 0.0) return 0.0
        var sumLog10 = 0.0
        for (i in trajectoryC.indices) {
            sumLog10 += salmonellaLog10RatePerSecond(trajectoryC[i], meatType, slowComeUpAdapted) * dtSeconds
        }
        return sumLog10
    }

    /**
     * Computes the 1st-order Arrhenius collagen-to-gelatin hydrolysis rate constant k_coll(T) [s^-1].
     */
    fun collagenHydrolysisRatePerSecond(tempC: Double): Double {
        if (tempC < 55.0) return 0.0
        val tempK = tempC + 273.15
        val exponent = -(COLLAGEN_EA_J_PER_MOL / GAS_CONSTANT_R) * ((1.0 / tempK) - (1.0 / COLLAGEN_REF_TEMP_K))
        return COLLAGEN_REF_RATE_PER_SEC * exp(exponent)
    }
}

data class ProbeState(
    val serialNumber: String,
    private val convertTemperatureUnits: (Double) -> Double,
    val macAddress: MutableState<String> = mutableStateOf("TBD"),
    val firmwareVersion: MutableState<String?> = mutableStateOf(null),
    val hardwareRevision: MutableState<String?> = mutableStateOf(null),
    val modelInformation: MutableState<ModelInformation?> = mutableStateOf(null),
    val rssi: MutableState<Int> = mutableStateOf(0),
    val temperaturesCelsius: SnapshotStateList<Double> = mutableStateListOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    val connectionState: MutableState<ConnectionState> = mutableStateOf(ConnectionState.OUT_OF_RANGE),
    val uploadStatus: MutableState<String> = mutableStateOf(""),
    val recordsDownloaded: MutableState<Int> = mutableStateOf(0),
    val recordRange: MutableState<String> = mutableStateOf(""),
    val color: MutableState<String> = mutableStateOf(""),
    val id: MutableState<String> = mutableStateOf(""),
    val batteryStatus: MutableState<String> = mutableStateOf(""),
    val instantRead: MutableState<String> = mutableStateOf(""),
    val connectionDescription: MutableState<String> = mutableStateOf(""),
    val samplePeriod: MutableState<String> = mutableStateOf("0.0"),
    val virtualCoreSensor: MutableState<String> = mutableStateOf(""),
    val virtualSurfaceSensor: MutableState<String> = mutableStateOf(""),
    val virtualAmbientSensor: MutableState<String> = mutableStateOf(""),
    val coreTemperature: MutableState<String> = mutableStateOf(""),
    val surfaceTemperature: MutableState<String> = mutableStateOf(""),
    val ambientTemperature: MutableState<String> = mutableStateOf(""),
    val rawCoreTemperatureC: MutableState<Double> = mutableStateOf(0.0),
    val rawSurfaceTemperatureC: MutableState<Double> = mutableStateOf(0.0),
    val rawAmbientTemperatureC: MutableState<Double> = mutableStateOf(0.0),
    val coreIndexState: MutableState<Int> = mutableStateOf(0),
    val surfaceIndexState: MutableState<Int> = mutableStateOf(3),
    val effectiveDiffusivityState: MutableState<Double> = mutableStateOf(MeatType.BEEF.diffusivity),
    val surfaceMoisturePhiState: MutableState<Double> = mutableStateOf(0.85),
    val timeToPeakMinutes: MutableState<String> = mutableStateOf("---"),
    val rawTimeToPeakMinutes: MutableState<Double> = mutableStateOf(0.0),
    val predictedRestingPeakTemp: MutableState<String> = mutableStateOf("---"),
    val rawPredictedRestingPeakC: MutableState<Double> = mutableStateOf(0.0),
    val livePasteurizationLog10: MutableState<Double> = mutableStateOf(0.0),
    val projectedRestPasteurizationLog10: MutableState<Double> = mutableStateOf(0.0),
    val comeUpDurationMinutes: MutableState<Double> = mutableStateOf(0.0),
    val slowComeUpPenaltyActive: MutableState<Boolean> = mutableStateOf(false),
    val collagenConversionPct: MutableState<Double> = mutableStateOf(0.0),
    val lastFlipOrLidEvent: MutableState<String> = mutableStateOf("Steady Heat"),
    val predictionState: MutableState<String> = mutableStateOf(""),
    val predictionMode: MutableState<String> = mutableStateOf(""),
    val predictionType: MutableState<String> = mutableStateOf(""),
    val setPointTemperature: MutableState<String> = mutableStateOf(""),
    val rawSetPointTemperatureC: MutableState<Double> = mutableStateOf(DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS),
    val heatStartTemperature: MutableState<String> = mutableStateOf(""),
    val percentThroughCook: MutableState<String> = mutableStateOf(""),
    val prediction: MutableState<String> = mutableStateOf(""),
    val estimateCore: MutableState<String> = mutableStateOf(""),
    val predictionIsStale: MutableState<Boolean> = mutableStateOf(true)
) {
    enum class ConnectionState {
        OUT_OF_RANGE, ADVERTISING_CONNECTABLE, ADVERTISING_NOT_CONNECTABLE, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, NO_ROUTE;
        companion object {
            fun fromDeviceConnectionState(state: DeviceConnectionState) : ConnectionState {
                return when(state) {
                    DeviceConnectionState.OUT_OF_RANGE -> OUT_OF_RANGE
                    DeviceConnectionState.ADVERTISING_CONNECTABLE -> ADVERTISING_CONNECTABLE
                    DeviceConnectionState.ADVERTISING_NOT_CONNECTABLE -> ADVERTISING_NOT_CONNECTABLE
                    DeviceConnectionState.CONNECTING -> CONNECTING
                    DeviceConnectionState.CONNECTED -> CONNECTED
                    DeviceConnectionState.DISCONNECTING -> DISCONNECTING
                    DeviceConnectionState.DISCONNECTED -> DISCONNECTED
                    DeviceConnectionState.NO_ROUTE -> NO_ROUTE
                }
            }
        }
    }

    val T1 : MutableState<String> = mutableStateOf("")
    val T2 : MutableState<String> = mutableStateOf("")
    val T3 : MutableState<String> = mutableStateOf("")
    val T4 : MutableState<String> = mutableStateOf("")
    val T5 : MutableState<String> = mutableStateOf("")
    val T6 : MutableState<String> = mutableStateOf("")
    val T7 : MutableState<String> = mutableStateOf("")
    val T8 : MutableState<String> = mutableStateOf("")

    val suggestedPullTemp: MutableState<String> = mutableStateOf("---")
    val selectedMeatType: MutableState<MeatType> = mutableStateOf(MeatType.BEEF)
    val selectedRestingEnvironment: MutableState<RestingEnvironment> = mutableStateOf(RestingEnvironment.WOOD_BOARD)
    val isWrapped: MutableState<Boolean> = mutableStateOf(false)
    val glidePathHistory: SnapshotStateList<GlidePathSample> = mutableStateListOf()
    val historyVersion: MutableState<Int> = mutableStateOf(0)

    private val predictor = CarryoverPredictor()
    private var activeSetPointC: Double? = null
    private var userTargetTemperatureC: Double? = null
    private var lastCoreIndex: Int = 0
    private var lastSurfaceIndex: Int = 3
    private var hasValidTemps: Boolean = false
    private var filterInitialized: Boolean = false
    private var previousRawMeasTempsC: DoubleArray? = null
    private var previousFilteredTempsC: DoubleArray? = null
    private var previousSurfaceDerivativeCPerS: Double = 0.0
    private var intactCollagenFraction: Double = 1.0
    private var lastUpdateTimestampMs: Long = 0L
    private var lastHistorySampleTimestampMs: Long = 0L
    val isUploading = mutableStateOf(false)

    companion object {
        // First-order thermistor + stainless sheath thermal lag time constant (tau = 4.5s)
        private const val PROBE_THERMAL_LAG_TAU_SECONDS = 4.5
    }

    fun selectRestingEnvironment(environment: RestingEnvironment) {
        selectedRestingEnvironment.value = environment
        isWrapped.value = environment.isInsulated
        recalculateCarryover()
    }

    fun applyUserTargetTemperatureC(targetC: Double) {
        val clampedC = targetC.coerceIn(
            DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS,
            DeviceManager.MAXIMUM_PREDICTION_SETPOINT_CELSIUS
        )
        userTargetTemperatureC = clampedC
        activeSetPointC = clampedC
        rawSetPointTemperatureC.value = clampedC
        setPointTemperature.value = convertTemperature(clampedC).roundToInt().toString()
        if (predictionMode.value.isEmpty() || predictionMode.value == ProbePredictionMode.NONE.toString()) {
            predictionMode.value = ProbePredictionMode.TIME_TO_REMOVAL.toString()
        }
        recalculateCarryover()
    }

    fun clearUserTargetTemperature() {
        userTargetTemperatureC = null
        activeSetPointC = null
        setPointTemperature.value = ""
        predictionMode.value = ProbePredictionMode.NONE.toString()
        recalculateCarryover()
    }

    fun updateProbeState(state: Probe, downloads: Int) {
        macAddress.value = state.mac
        firmwareVersion.value = state.fwVersion?.toString() ?: ""
        hardwareRevision.value = state.hwRevision
        modelInformation.value = state.modelInformation
        connectionState.value = ConnectionState.fromDeviceConnectionState(state.connectionState)
        rssi.value = state.rssi
        recordsDownloaded.value = downloads
        color.value = state.color.toString()
        id.value = state.id.toString()
        isUploading.value = (state.uploadState is ProbeUploadState.ProbeUploadInProgress)
        predictionIsStale.value = state.statusNotificationsStale

        samplePeriod.value = if(state.sessionInfo != null) String.format("%d ms", state.sessionInfo?.let { it.samplePeriod.toLong() } ) else ""
        batteryStatus.value = when(state.batteryStatus) { ProbeBatteryStatus.LOW_BATTERY -> "Low"; ProbeBatteryStatus.OK -> "Good" }

        lastCoreIndex = state.virtualSensors.virtualCoreSensor.ordinal
        lastSurfaceIndex = (state.virtualSensors.virtualSurfaceSensor.ordinal + 3).coerceIn(0, 7)
        coreIndexState.value = lastCoreIndex
        surfaceIndexState.value = lastSurfaceIndex

        if(state.temperaturesCelsius != null) {
            val rawTemps = state.temperaturesCelsius!!.values
            val nowMs = System.currentTimeMillis()
            val dtSec = if (lastUpdateTimestampMs > 0L) {
                ((nowMs - lastUpdateTimestampMs) / 1000.0).coerceIn(0.5, 30.0)
            } else {
                5.0
            }
            val snapshotPrev = if (filterInitialized) DoubleArray(8) { temperaturesCelsius[it] } else null
            val rawPrev = previousRawMeasTempsC

            val ambC = state.ambientTemperatureCelsius ?: rawTemps.getOrElse(7) { 0.0 }
            val surfC = state.surfaceTemperatureCelsius ?: rawTemps.getOrElse(lastSurfaceIndex) { 0.0 }

            // Module 3: Axial stem-effect bias correction when high ambient heat conducts down the steel shaft
            val stemBiasC = if (ambC - surfC > 75.0) {
                ((ambC - surfC - 75.0) * 0.016).coerceAtMost(2.0)
            } else {
                0.0
            }

            for (i in 0..7) {
                if (i < rawTemps.size) {
                    val stemCorrected = when {
                        i == lastSurfaceIndex - 1 && i > lastCoreIndex -> rawTemps[i] - stemBiasC
                        i == lastSurfaceIndex - 2 && i > lastCoreIndex -> rawTemps[i] - (0.35 * stemBiasC)
                        else -> rawTemps[i]
                    }

                    // Module 3: First-order probe thermal lag compensation T_true = T_meas + tau * (dT_meas / dt)
                    val deLagged = if (rawPrev != null && dtSec >= 0.5) {
                        val dTMeasDt = (stemCorrected - rawPrev[i]) / dtSec
                        val lagLeadTerm = (PROBE_THERMAL_LAG_TAU_SECONDS * dTMeasDt).coerceIn(-1.2, 1.2)
                        stemCorrected + lagLeadTerm
                    } else {
                        stemCorrected
                    }

                    // 1D Kalman / Exponential Moving Average smoothing (alpha = 0.35)
                    temperaturesCelsius[i] = if (!filterInitialized) {
                        deLagged
                    } else {
                        0.35 * deLagged + 0.65 * temperaturesCelsius[i]
                    }
                }
            }
            previousRawMeasTempsC = DoubleArray(8) { idx -> rawTemps.getOrElse(idx) { 0.0 } }
            filterInitialized = true
            lastUpdateTimestampMs = nowMs

            // Module 3: Flip / Lid Opening Detection from outer-sensor derivative sign inversions
            if (snapshotPrev != null && dtSec >= 0.5) {
                val sIdx = lastSurfaceIndex.coerceIn(1, 7)
                val surfDeriv = (temperaturesCelsius[sIdx] - snapshotPrev[sIdx]) / dtSec
                val ambDeriv = (temperaturesCelsius[7] - snapshotPrev[7]) / dtSec

                when {
                    // Sudden surface cooling while ambient stays hot or rebounds -> meat flipped
                    previousSurfaceDerivativeCPerS > 0.02 && surfDeriv < -0.25 && abs(ambDeriv) < abs(surfDeriv) * 1.8 -> {
                        lastFlipOrLidEvent.value = "Flip Detected"
                    }
                    // Sudden ambient drop -> grill/oven lid opened
                    ambDeriv < -0.45 -> {
                        lastFlipOrLidEvent.value = "Lid Opened"
                    }
                    abs(surfDeriv) < 0.12 && abs(ambDeriv) < 0.20 -> {
                        if (lastFlipOrLidEvent.value != "Steady Heat") {
                            lastFlipOrLidEvent.value = "Steady Heat"
                        }
                    }
                }
                previousSurfaceDerivativeCPerS = 0.4 * surfDeriv + 0.6 * previousSurfaceDerivativeCPerS
            }

            // Module 1 & 3: Online Inverse Heat Transfer Problem (IHTP) diffusivity update bounded by Choi & Okos anisotropy
            val estimatedAlpha = predictor.estimateEffectiveDiffusivity(
                currentTemps = temperaturesCelsius,
                prevTemps = snapshotPrev ?: previousFilteredTempsC,
                dtSeconds = dtSec.coerceAtLeast(1.0),
                coreIndex = lastCoreIndex,
                surfaceIndex = lastSurfaceIndex,
                baseDiffusivity = selectedMeatType.value.diffusivity,
                meatType = selectedMeatType.value
            )
            effectiveDiffusivityState.value = 0.25 * estimatedAlpha + 0.75 * effectiveDiffusivityState.value

            // Module 2 & 3: Falling-rate surface moisture availability factor phi_w update
            val subSurfC = temperaturesCelsius.getOrElse((lastSurfaceIndex - 1).coerceAtLeast(0)) { surfC }
            surfaceMoisturePhiState.value = predictor.estimateSurfaceMoisturePhi(
                surfaceTempC = surfC,
                subSurfaceTempC = subSurfC,
                ambientTempC = ambC,
                previousPhi = surfaceMoisturePhiState.value,
                dtSeconds = dtSec,
                isWrapped = selectedRestingEnvironment.value.isInsulated
            )
            previousFilteredTempsC = snapshotPrev

            // Module 4: Real-time USDA FSIS Pasteurization & Arrhenius Collagen Hydrolysis Integration
            val coreC = state.coreTemperatureCelsius ?: temperaturesCelsius[lastCoreIndex.coerceIn(0, 7)]
            val surfaceC = state.surfaceTemperatureCelsius ?: temperaturesCelsius[lastSurfaceIndex.coerceIn(0, 7)]

            // 2025 AMIF Slow Come-Up Guardrail: Track dwell time between 10.0°C (50°F) and 54.44°C (130°F)
            if (coreC in 10.0..54.44) {
                comeUpDurationMinutes.value = comeUpDurationMinutes.value + (dtSec / 60.0)
                if (comeUpDurationMinutes.value > 120.0) {
                    slowComeUpPenaltyActive.value = true
                }
            }

            // Accumulate live USDA FSIS Salmonella log10 reduction
            val log10Step = ThermalDoseIntegrator.salmonellaLog10RatePerSecond(
                tempC = coreC,
                meatType = selectedMeatType.value,
                slowComeUpAdapted = slowComeUpPenaltyActive.value
            ) * dtSec
            livePasteurizationLog10.value = (livePasteurizationLog10.value + log10Step).coerceAtMost(15.0)

            // Accumulate 1st-order Arrhenius collagen-to-gelatin conversion using mean muscle temperature
            val meanMeatTempC = 0.6 * coreC + 0.4 * surfaceC
            val kColl = ThermalDoseIntegrator.collagenHydrolysisRatePerSecond(meanMeatTempC)
            if (kColl > 0.0) {
                intactCollagenFraction *= exp(-kColl * dtSec)
                collagenConversionPct.value = ((1.0 - intactCollagenFraction) * 100.0).coerceIn(0.0, 100.0)
            }

            T1.value = f(temperaturesCelsius[0]); T2.value = f(temperaturesCelsius[1])
            T3.value = f(temperaturesCelsius[2]); T4.value = f(temperaturesCelsius[3])
            T5.value = f(temperaturesCelsius[4]); T6.value = f(temperaturesCelsius[5])
            T7.value = f(temperaturesCelsius[6]); T8.value = f(temperaturesCelsius[7])

            if (coreC > 0.0 && (nowMs - lastHistorySampleTimestampMs >= 900L || glidePathHistory.isEmpty())) {
                lastHistorySampleTimestampMs = nowMs
                if (glidePathHistory.size >= 360) {
                    glidePathHistory.removeAt(0)
                }
                glidePathHistory.add(
                    GlidePathSample(
                        timestampMs = nowMs,
                        temperaturesC = DoubleArray(8) { temperaturesCelsius[it] },
                        coreIndex = lastCoreIndex.coerceIn(0, 6),
                        surfaceIndex = lastSurfaceIndex.coerceIn((lastCoreIndex + 1).coerceAtMost(7), 7),
                        coreTempC = coreC,
                        surfaceTempC = surfaceC,
                        effectiveAlpha = effectiveDiffusivityState.value
                    )
                )
            }
        }

        fun fmt(v: Double?) = v?.let { String.format("%.1f", convertTemperature(it)) } ?: "---"

        rawCoreTemperatureC.value = state.coreTemperatureCelsius ?: 0.0
        rawSurfaceTemperatureC.value = state.surfaceTemperatureCelsius ?: 0.0
        rawAmbientTemperatureC.value = state.ambientTemperatureCelsius ?: 0.0

        instantRead.value = fmt(state.instantReadCelsius)
        coreTemperature.value = fmt(state.coreTemperatureCelsius)
        surfaceTemperature.value = fmt(state.surfaceTemperatureCelsius)
        ambientTemperature.value = fmt(state.ambientTemperatureCelsius)
        heatStartTemperature.value = fmt(state.heatStartTemperatureCelsius)
        estimateCore.value = fmt(state.estimatedCoreCelsius)
        percentThroughCook.value = state.predictionPercent?.let { "${it.roundToInt()}%" } ?: "---"
        prediction.value = state.predictionSeconds?.let { secs ->
            val mins = secs.toLong() / 60
            val remSecs = secs.toLong() % 60
            String.format("%02d:%02d", mins, remSecs)
        } ?: "---"

        uploadStatus.value = if(state.uploadState is ProbeUploadState.ProbeUploadInProgress) "Uploading..." else if (state.uploadState is ProbeUploadState.ProbeUploadComplete) "Complete" else "Connect"
        recordRange.value = if(state.connectionState == DeviceConnectionState.CONNECTED) "${state.minSequenceNumber} : ${state.maxSequenceNumber}" else ""
        connectionDescription.value = state.connectionState.toString()
        virtualCoreSensor.value = state.virtualSensors.virtualCoreSensor.toString()
        virtualSurfaceSensor.value = state.virtualSensors.virtualSurfaceSensor.toString()
        virtualAmbientSensor.value = state.virtualSensors.virtualAmbientSensor.toString()

        predictionState.value = state.predictionState?.toString() ?: ""
        val hwSetPointC = state.setPointTemperatureCelsius?.takeIf {
            it >= DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS &&
                state.predictionMode != null &&
                state.predictionMode != ProbePredictionMode.NONE
        }
        val effectiveSetPointC = userTargetTemperatureC ?: hwSetPointC
        activeSetPointC = effectiveSetPointC

        if (effectiveSetPointC != null) {
            rawSetPointTemperatureC.value = effectiveSetPointC
            setPointTemperature.value = convertTemperature(effectiveSetPointC).roundToInt().toString()
            predictionMode.value = if (state.predictionMode != null && state.predictionMode != ProbePredictionMode.NONE) {
                state.predictionMode.toString()
            } else {
                ProbePredictionMode.TIME_TO_REMOVAL.toString()
            }
        } else {
            rawSetPointTemperatureC.value = state.setPointTemperatureCelsius ?: DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS
            setPointTemperature.value = ""
            predictionMode.value = state.predictionMode?.toString() ?: ""
        }
        predictionType.value = state.predictionType?.toString() ?: ""

        hasValidTemps = (state.coreTemperatureCelsius != null || temperaturesCelsius.any { it > 0.0 })

        recalculateCarryover()
    }

    fun getEffectiveTargetTempC(): Double {
        return userTargetTemperatureC ?: activeSetPointC ?: if (setPointTemperature.value.isNotEmpty()) {
            rawSetPointTemperatureC.value
        } else {
            when (selectedMeatType.value) {
                MeatType.POULTRY -> 68.3
                MeatType.FISH -> 57.0
                MeatType.MARBLED_BEEF -> 94.0
                else -> 57.2
            }
        }
    }

    fun computeSampleCarryover(
        profileTempsC: DoubleArray,
        coreIdx: Int,
        surfIdx: Int,
        coreTempC: Double,
        targetTempC: Double,
        alpha: Double? = null
    ): Pair<Double, Double> {
        val validCore = coreIdx.coerceIn(0, 6)
        val validSurf = surfIdx.coerceIn(validCore + 1, 7)
        val spanCount = validSurf - validCore + 1
        val activeProfile = DoubleArray(spanCount) { idx ->
            profileTempsC.getOrElse(validCore + idx) { coreTempC }
        }
        val radiusMeters = (validSurf - validCore) * 0.006
        val effectiveAlpha = alpha ?: effectiveDiffusivityState.value

        val result = predictor.predictRestingTrajectory(
            profile = activeProfile,
            radiusMeters = radiusMeters,
            alpha = effectiveAlpha,
            meatType = selectedMeatType.value,
            restingEnvironment = selectedRestingEnvironment.value,
            surfaceMoisturePhi = surfaceMoisturePhiState.value,
            targetTempC = targetTempC
        )
        return result.peakCoreTempC to result.pullTempC
    }

    fun toDisplayTemperature(temperatureC: Double): Double = convertTemperature(temperatureC)

    fun recalculateCarryover() {
        val target = getEffectiveTargetTempC()
        if (hasValidTemps && lastCoreIndex in 0..7 && lastSurfaceIndex in 0..7 && lastCoreIndex < lastSurfaceIndex) {
            val spanCount = lastSurfaceIndex - lastCoreIndex + 1
            val activeProfile = DoubleArray(spanCount) { idx -> temperaturesCelsius[lastCoreIndex + idx] }
            val radiusMeters = (lastSurfaceIndex - lastCoreIndex) * 0.006

            val result = predictor.predictRestingTrajectory(
                profile = activeProfile,
                radiusMeters = radiusMeters,
                alpha = effectiveDiffusivityState.value,
                meatType = selectedMeatType.value,
                restingEnvironment = selectedRestingEnvironment.value,
                surfaceMoisturePhi = surfaceMoisturePhiState.value,
                targetTempC = target
            )

            suggestedPullTemp.value = String.format("%.1f", convertTemperature(result.pullTempC))
            rawPredictedRestingPeakC.value = result.peakCoreTempC
            predictedRestingPeakTemp.value = String.format("%.1f", convertTemperature(result.peakCoreTempC))

            val tPeakMin = result.timeToPeakSeconds / 60.0
            rawTimeToPeakMinutes.value = tPeakMin
            timeToPeakMinutes.value = String.format("%.1f min", tPeakMin)

            // Module 4: Integrate projected post-pull carryover rest trajectory for USDA FSIS Salmonella log10 kill
            val restLog10Add = ThermalDoseIntegrator.integrateTrajectorySalmonellaLog10(
                trajectoryC = result.restingCoreTrajectoryC,
                dtSeconds = result.dtStepSeconds,
                meatType = selectedMeatType.value,
                slowComeUpAdapted = slowComeUpPenaltyActive.value
            )
            projectedRestPasteurizationLog10.value = (livePasteurizationLog10.value + restLog10Add).coerceAtMost(15.0)
        } else {
            suggestedPullTemp.value = "---"
            timeToPeakMinutes.value = "---"
            predictedRestingPeakTemp.value = "---"
            projectedRestPasteurizationLog10.value = livePasteurizationLog10.value
        }
        historyVersion.value = historyVersion.value + 1
    }

    private fun f(v: Double) = String.format("%.1f", convertTemperature(v))
    private fun convertTemperature(temperature: Double) : Double { return convertTemperatureUnits(temperature) }
}
