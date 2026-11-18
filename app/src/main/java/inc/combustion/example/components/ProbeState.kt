package inc.combustion.example.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import inc.combustion.framework.service.*
import inc.combustion.example.MeatType
import inc.combustion.example.CarryoverPredictor
import kotlin.math.roundToInt

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
    val isWrapped: MutableState<Boolean> = mutableStateOf(false)

    private val predictor = CarryoverPredictor()
    private var activeSetPointC: Double? = null
    private var lastCoreIndex: Int = 0
    private var lastSurfaceIndex: Int = 3
    private var hasValidTemps: Boolean = false
    private var filterInitialized: Boolean = false
    private var previousFilteredTempsC: DoubleArray? = null
    private var lastUpdateTimestampMs: Long = 0L
    val isUploading = mutableStateOf(false)

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
            val dtSec = if (lastUpdateTimestampMs > 0L) (nowMs - lastUpdateTimestampMs) / 1000.0 else 5.0
            val snapshotPrev = if (filterInitialized) DoubleArray(8) { temperaturesCelsius[it] } else null

            val ambC = state.ambientTemperatureCelsius ?: rawTemps.getOrElse(7) { 0.0 }
            val surfC = state.surfaceTemperatureCelsius ?: rawTemps.getOrElse(lastSurfaceIndex) { 0.0 }

            // Stem-effect bias correction when high ambient heat conducts down the steel shaft
            val stemBiasC = if (ambC - surfC > 80.0) ((ambC - surfC - 80.0) * 0.015).coerceAtMost(1.8) else 0.0

            for (i in 0..7) {
                if (i < rawTemps.size) {
                    val corrected = if (i == lastSurfaceIndex - 1 && i > lastCoreIndex) {
                        rawTemps[i] - stemBiasC
                    } else {
                        rawTemps[i]
                    }
                    // 1D Kalman / Exponential Moving Average smoothing (alpha = 0.35)
                    temperaturesCelsius[i] = if (!filterInitialized) {
                        corrected
                    } else {
                        0.35 * corrected + 0.65 * temperaturesCelsius[i]
                    }
                }
            }
            filterInitialized = true
            lastUpdateTimestampMs = nowMs

            // Online Inverse Heat Transfer Problem (IHTP) diffusivity update
            val estimatedAlpha = predictor.estimateEffectiveDiffusivity(
                currentTemps = temperaturesCelsius,
                prevTemps = snapshotPrev ?: previousFilteredTempsC,
                dtSeconds = dtSec.coerceAtLeast(1.0),
                coreIndex = lastCoreIndex,
                surfaceIndex = lastSurfaceIndex,
                baseDiffusivity = selectedMeatType.value.diffusivity
            )
            effectiveDiffusivityState.value = 0.25 * estimatedAlpha + 0.75 * effectiveDiffusivityState.value
            previousFilteredTempsC = snapshotPrev

            T1.value = f(temperaturesCelsius[0]); T2.value = f(temperaturesCelsius[1])
            T3.value = f(temperaturesCelsius[2]); T4.value = f(temperaturesCelsius[3])
            T5.value = f(temperaturesCelsius[4]); T6.value = f(temperaturesCelsius[5])
            T7.value = f(temperaturesCelsius[6]); T8.value = f(temperaturesCelsius[7])
        }

        fun fmt(v: Double?) = v?.let { String.format("%.1f", convertTemperature(it)) } ?: "---"

        rawCoreTemperatureC.value = state.coreTemperatureCelsius ?: 0.0
        rawSurfaceTemperatureC.value = state.surfaceTemperatureCelsius ?: 0.0
        rawAmbientTemperatureC.value = state.ambientTemperatureCelsius ?: 0.0

        instantRead.value = fmt(state.instantReadCelsius)
        coreTemperature.value = fmt(state.coreTemperatureCelsius)
        surfaceTemperature.value = fmt(state.surfaceTemperatureCelsius)
        ambientTemperature.value = fmt(state.ambientTemperatureCelsius)
        uploadStatus.value = if(state.uploadState is ProbeUploadState.ProbeUploadInProgress) "Uploading..." else if (state.uploadState is ProbeUploadState.ProbeUploadComplete) "Complete" else "Connect"
        recordRange.value = if(state.connectionState == DeviceConnectionState.CONNECTED) "${state.minSequenceNumber} : ${state.maxSequenceNumber}" else ""
        connectionDescription.value = state.connectionState.toString()
        virtualCoreSensor.value = state.virtualSensors.virtualCoreSensor.toString()
        virtualSurfaceSensor.value = state.virtualSensors.virtualSurfaceSensor.toString()
        virtualAmbientSensor.value = state.virtualSensors.virtualAmbientSensor.toString()

        predictionState.value = state.predictionState?.toString() ?: ""
        predictionMode.value = state.predictionMode?.toString() ?: ""
        predictionType.value = state.predictionType?.toString() ?: ""
        rawSetPointTemperatureC.value = state.setPointTemperatureCelsius ?: DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS
        setPointTemperature.value = state.setPointTemperatureCelsius?.let { convertTemperature(it).roundToInt().toString() } ?: ""

        activeSetPointC = state.setPointTemperatureCelsius
        hasValidTemps = (state.coreTemperatureCelsius != null && state.temperaturesCelsius != null)

        recalculateCarryover()
    }

    fun recalculateCarryover() {
        val target = activeSetPointC
        if (hasValidTemps && target != null && lastCoreIndex in 0..7 && lastSurfaceIndex in 0..7 && lastCoreIndex < lastSurfaceIndex) {
            val pullAtC = predictor.predictPeakTemp(
                targetTemp = target,
                temperatures = temperaturesCelsius,
                coreIndex = lastCoreIndex,
                surfaceIndex = lastSurfaceIndex,
                meatType = selectedMeatType.value,
                isWrapped = isWrapped.value,
                customAlpha = effectiveDiffusivityState.value
            )
            suggestedPullTemp.value = String.format("%.1f", convertTemperature(pullAtC))
        } else {
            suggestedPullTemp.value = "---"
        }
    }

    private fun f(v: Double) = String.format("%.1f", convertTemperature(v))
    private fun convertTemperature(temperature: Double) : Double { return convertTemperatureUnits(temperature) }
}
