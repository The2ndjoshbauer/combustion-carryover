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
    // Stores T1..T8
    val temperaturesCelsius: SnapshotStateList<Double> = mutableStateListOf(
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    ),
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

    // *** V3 VARIABLES ***
    val suggestedPullTemp: MutableState<String> = mutableStateOf("---")
    // Stores the user's selection (Default to Beef)
    val selectedMeatType: MutableState<MeatType> = mutableStateOf(MeatType.BEEF)
    
    // The Physics Engine
    private val predictor = CarryoverPredictor()

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

        // Update Temps
        if(state.temperaturesCelsius != null) {
            val temps = state.temperaturesCelsius!!.values
            // Copy to our observable list
            for (i in 0..7) {
                if (i < temps.size) temperaturesCelsius[i] = temps[i]
            }
            // Display strings
            T1.value = f(temps[0]); T2.value = f(temps[1]); T3.value = f(temps[2]); T4.value = f(temps[3])
            T5.value = f(temps[4]); T6.value = f(temps[5]); T7.value = f(temps[6]); T8.value = f(temps[7])
        }

        // Helper for formatting
        fun fmt(v: Double?) = v?.let { String.format("%.1f", convertTemperature(it)) } ?: "---"

        instantRead.value = fmt(state.instantReadCelsius)
        coreTemperature.value = fmt(state.coreTemperatureCelsius)
        surfaceTemperature.value = fmt(state.surfaceTemperatureCelsius)
        ambientTemperature.value = fmt(state.ambientTemperatureCelsius)

        // Metadata updates
        uploadStatus.value = if(state.uploadState is ProbeUploadState.ProbeUploadInProgress) "Uploading..." else if (state.uploadState is ProbeUploadState.ProbeUploadComplete) "Complete" else "Connect"
        recordRange.value = if(state.connectionState == DeviceConnectionState.CONNECTED) "${state.minSequenceNumber} : ${state.maxSequenceNumber}" else ""
        connectionDescription.value = state.connectionState.toString()
        
        // Update Sensor Indices
        virtualCoreSensor.value = state.virtualSensors.virtualCoreSensor.toString()
        virtualSurfaceSensor.value = state.virtualSensors.virtualSurfaceSensor.toString()
        virtualAmbientSensor.value = state.virtualSensors.virtualAmbientSensor.toString()

        predictionState.value = state.predictionState?.toString() ?: ""
        predictionMode.value = state.predictionMode?.toString() ?: ""
        predictionType.value = state.predictionType?.toString() ?: ""
        rawSetPointTemperatureC.value = state.setPointTemperatureCelsius ?: DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS
        setPointTemperature.value = state.setPointTemperatureCelsius?.let { convertTemperature(it).roundToInt().toString() } ?: ""
        
        // *** V3 SIMULATION TRIGGER ***
        val currentCore = state.coreTemperatureCelsius
        val target = state.setPointTemperatureCelsius
        
        // We need valid indices (0-7) for Core and Surface to define the meat geometry
        // The library returns these in `state.virtualSensors`
        val coreIdx = state.virtualSensors.virtualCoreSensor.ordinal
        val surfIdx = state.virtualSensors.virtualSurfaceSensor.ordinal

        if (currentCore != null && target != null && coreIdx < 8 && surfIdx < 8) {
            val pullAtC = predictor.predictPeakTemp(
                targetTemp = target,
                temperatures = temperaturesCelsius, // Pass all 8 temps
                coreIndex = coreIdx.toInt(),
                surfaceIndex = surfIdx.toInt(),
                meatType = selectedMeatType.value // Use user selection
            )
            suggestedPullTemp.value = String.format("%.1f", convertTemperature(pullAtC))
        } else {
            suggestedPullTemp.value = "---"
        }
    }

    private fun f(v: Double) = String.format("%.1f", convertTemperature(v))
    private fun convertTemperature(temperature: Double) : Double { return convertTemperatureUnits(temperature) }
}