/*
 * Project: Combustion Inc. Android Example
 * File: DetailsViewModel.kt
 * Author: https://github.com/miwright2
 *
 * MIT License
 *
 * Copyright (c) 2022. Combustion Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package inc.combustion.example.details

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import inc.combustion.example.BuildConfig
import inc.combustion.example.LOG_TAG
import inc.combustion.example.components.ProbeState
import inc.combustion.framework.service.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

class DetailsViewModel (
    val serialNumber: String,
    private val deviceManager: DeviceManager,
    temperatureUnitsConversion: (Double) -> Double
) : ViewModel() {

    private var collectJob: Job? = null
    private var pendingTargetTemperatureC: Double? = null

    val probeState = ProbeState(serialNumber, temperatureUnitsConversion)
    val probeData = mutableStateListOf<LoggedProbeDataPoint>()
    val probeDataStartTimestamp = mutableStateOf(Date())

    val predictionTargetTemperatureC: Double
        get() = probeState.getEffectiveTargetTempC()

    companion object {
        const val MINIMUM_PREDICTION_SETPOINT_CELSIUS = DeviceManager.MINIMUM_PREDICTION_SETPOINT_CELSIUS
        const val MAXIMUM_PREDICTION_SETPOINT_CELSIUS = DeviceManager.MAXIMUM_PREDICTION_SETPOINT_CELSIUS
    }

    init {
        updateProbeState(null)
        viewModelScope.launch {
            deviceManager.probeFlow(serialNumber)?.collect { it ->
                val recordCount = deviceManager.recordsDownloaded(serialNumber)

                updateProbeState(it)

                // If user set a target while probe was advertising/connecting, send it once connected
                if (it.connectionState == DeviceConnectionState.CONNECTED) {
                    pendingTargetTemperatureC?.let { pendingC ->
                        pendingTargetTemperatureC = null
                        deviceManager.setRemovalPrediction(serialNumber, pendingC) { success ->
                            if (!success) {
                                Log.e(LOG_TAG, "Failed to send queued removal prediction $pendingC ($serialNumber)")
                            }
                        }
                    }
                }

                if (it.uploadState is ProbeUploadState.ProbeUploadComplete) {
                    if (collectJob == null) {
                        probeData.clear()
                        probeDataStartTimestamp.value = deviceManager.logStartTimestampForDevice(serialNumber)

                        val flow = deviceManager.createLogFlowForDevice(serialNumber)
                        collectJob = viewModelScope.launch {
                            flow.collect { log ->
                                probeData.add(log)
                            }
                        }
                    }
                } else {
                    if (it.uploadState !is ProbeUploadState.ProbeUploadInProgress) {
                        collectJob?.cancel()
                        collectJob = null
                    }

                    if (recordCount > probeData.count()) {
                        val logs = deviceManager.exportLogsForDevice(serialNumber)
                        if (!logs.isNullOrEmpty()) {
                            probeDataStartTimestamp.value = deviceManager.logStartTimestampForDevice(serialNumber)
                            probeData.clear()
                            probeData.addAll(logs)
                        }
                    }
                }
            }
        }
    }

    class Factory(
        private val deviceManager: DeviceManager,
        private val serialNumber: String,
        private val temperatureUnitsConversion: (Double) -> Double
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DetailsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DetailsViewModel(serialNumber, deviceManager, temperatureUnitsConversion) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /**
     * ViewModel processing of Bluetooth Button click from the Card composable on the DevicesScreen
     *
     * @see ProbeState
     */
    fun toggleConnection() {
        when (probeState.connectionState.value) {
            ProbeState.ConnectionState.ADVERTISING_CONNECTABLE -> {
                deviceManager.connect(serialNumber)
            }
            ProbeState.ConnectionState.CONNECTED -> {
                deviceManager.disconnect(serialNumber)
            }
            // Otherwise, we cannot change the Connect State of the device.
            else -> {
                Log.d(
                    LOG_TAG,
                    "No toggle action $serialNumber is ${probeState.connectionState.value}"
                )
            }
        }
    }

    /**
     * ViewModel processing a color choice for a probe
     *
     * @param color new color value for probe
     *
     * @see ProbeState
     * @see ProbeColor
     */
    fun setProbeColor(color: ProbeColor) {
        deviceManager.setProbeColor(serialNumber, color) {
            if(!it) {
                Log.e(LOG_TAG, "Failed to set probe color ($serialNumber)")
            }
        }
    }

    /**
     * ViewModel processing a id choice for a probe
     *
     * @param id new id value for probe
     *
     * @see ProbeState
     * @see ProbeID
     */
    fun setProbeID(id: ProbeID) {
        deviceManager.setProbeID(serialNumber, id) {
            if(!it) {
                Log.e(LOG_TAG, "Failed to set probe ID ($serialNumber)")
            }
        }
    }

    /**
     * Gets the probe data as a shareable CSV file enriched with Glide Path & Crank-Nicolson metrics.
     *
     * @return first: suggested file name, second: csv data.
     */
    fun getShareData(): Pair<String, String> {
        val appVersionName = "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"
        val probe = deviceManager.probe(probeState.serialNumber)
        val logs = deviceManager.exportLogsForDevice(probeState.serialNumber)?.takeIf { it.isNotEmpty() }
            ?: probeData.toList()

        val csvVersion = 4
        val sb = StringBuilder()
        val serial = probe?.serialNumber ?: probeState.serialNumber
        val firmwareVersion = probe?.fwVersion?.toString() ?: probeState.firmwareVersion.value ?: "UNKNOWN"
        val hardwareVersion = probe?.hwRevision ?: probeState.hardwareRevision.value ?: "UNKNOWN"
        val samplePeriod = probe?.sessionInfo?.samplePeriod ?: 0u
        val frameworkVersion = "Android ${inc.combustion.framework.Combustion.FRAMEWORK_VERSION_NAME} ${inc.combustion.framework.Combustion.FRAMEWORK_BUILD_TYPE}"

        val now = java.time.LocalDateTime.now()
        val headerDateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val createdDateTime = now.format(headerDateTimeFormatter)

        val meatType = probeState.selectedMeatType.value
        val restingEnv = probeState.selectedRestingEnvironment.value
        val isWrapped = restingEnv.isInsulated
        val currentAlpha = probeState.effectiveDiffusivityState.value
        val effectiveTargetC = probeState.getEffectiveTargetTempC()

        // Header metadata (standard + Glide Path V5 physics parameters)
        sb.appendLine("Combustion Inc. Probe Data")
        sb.appendLine("App: $appVersionName")
        sb.appendLine("CSV version: $csvVersion")
        sb.appendLine("Probe S/N: $serial")
        sb.appendLine("Probe FW version: $firmwareVersion")
        sb.appendLine("Probe HW revision: $hardwareVersion")
        sb.appendLine("Framework: $frameworkVersion")
        sb.appendLine("Sample Period: $samplePeriod")
        sb.appendLine("Created: $createdDateTime")
        sb.appendLine("Glide Path Meat Type: ${meatType.name} (${meatType.displayName})")
        sb.appendLine("Resting Environment Preset: ${restingEnv.name} (${restingEnv.displayName})")
        sb.appendLine("Wrapped / Insulated: $isWrapped")
        sb.appendLine(String.format(Locale.US, "Calibrated Effective Diffusivity (m2/s): %.4e", currentAlpha))
        sb.appendLine(String.format(Locale.US, "Surface Moisture Factor (phi_w): %.3f", probeState.surfaceMoisturePhiState.value))
        sb.appendLine("Current Suggested Pull Temp: ${probeState.suggestedPullTemp.value}")
        sb.appendLine("Current Time To Peak: ${probeState.timeToPeakMinutes.value}")
        sb.appendLine(
            String.format(
                Locale.US,
                "USDA FSIS Salmonella Log10 Kill (Live -> Projected Post-Rest): %.2f -> %.2f",
                probeState.livePasteurizationLog10.value,
                probeState.projectedRestPasteurizationLog10.value
            )
        )
        sb.appendLine(String.format(Locale.US, "Arrhenius Collagen Conversion (%%): %.2f", probeState.collagenConversionPct.value))
        sb.appendLine()

        // Column headers (Standard 23 + 6 V4 Glide Path columns + 7 V5 Physics columns)
        sb.appendLine(
            "Timestamp,SessionID,SequenceNumber,T1,T2,T3,T4,T5,T6,T7,T8," +
                "VirtualCoreTemperature,VirtualSurfaceTemperature,VirtualAmbientTemperature," +
                "EstimatedCoreTemperature,PredictionSetPoint,VirtualCoreSensor,VirtualSurfaceSensor," +
                "VirtualAmbientSensor,PredictionState,PredictionMode,PredictionType,PredictionValueSeconds," +
                "MeatType,IsWrapped,EffectiveDiffusivity_m2s,PredictedRestingPeakCoreC,PredictedCarryoverRiseC,SuggestedPullTempC," +
                "RestingEnvironment,TimeToPeakMin,SurfaceMoisturePhi,PasteurizationLiveLog10,PasteurizationProjectedRestLog10,CollagenConversionPct,FlipOrLidEvent"
        )

        val predictor = inc.combustion.example.CarryoverPredictor()
        val sensorSpacingMeters = 0.006

        if (logs.isNotEmpty()) {
            val startTime = logs.first().timestamp.time
            var runningAlpha = meatType.diffusivity
            var runningPhi = 0.85
            var runningLiveLog10 = 0.0
            var runningComeUpMin = 0.0
            var runningIntactCollagen = 1.0
            var prevTemps: DoubleArray? = null
            var prevTimestampMs = startTime

            logs.forEach { dataPoint ->
                val timestamp = dataPoint.timestamp.time
                val elapsed = (timestamp - startTime) / 1000.0f
                val dtSec = ((timestamp - prevTimestampMs) / 1000.0).coerceAtLeast(1.0)
                val rawVals = dataPoint.temperatures.values

                val coreIdx = dataPoint.virtualCoreSensor.ordinal.coerceIn(0, 6)
                val surfIdx = (dataPoint.virtualSurfaceSensor.ordinal + 3).coerceIn(coreIdx + 1, 7)

                val estimatedAlpha = predictor.estimateEffectiveDiffusivity(
                    currentTemps = rawVals,
                    prevTemps = prevTemps,
                    dtSeconds = dtSec,
                    coreIndex = coreIdx,
                    surfaceIndex = surfIdx,
                    baseDiffusivity = meatType.diffusivity,
                    meatType = meatType
                )
                runningAlpha = 0.25 * estimatedAlpha + 0.75 * runningAlpha

                val coreTempC = dataPoint.virtualCoreTemperature
                val surfTempC = dataPoint.virtualSurfaceTemperature
                val ambTempC = dataPoint.virtualAmbientTemperature
                val subSurfTempC = rawVals.getOrElse((surfIdx - 1).coerceAtLeast(0)) { surfTempC }

                runningPhi = predictor.estimateSurfaceMoisturePhi(
                    surfaceTempC = surfTempC,
                    subSurfaceTempC = subSurfTempC,
                    ambientTempC = ambTempC,
                    previousPhi = runningPhi,
                    dtSeconds = dtSec,
                    isWrapped = isWrapped
                )

                if (coreTempC in 10.0..54.44) {
                    runningComeUpMin += dtSec / 60.0
                }
                val slowComeUpPenalty = runningComeUpMin > 120.0
                runningLiveLog10 = (runningLiveLog10 +
                    inc.combustion.example.components.ThermalDoseIntegrator.salmonellaLog10RatePerSecond(
                        tempC = coreTempC,
                        meatType = meatType,
                        slowComeUpAdapted = slowComeUpPenalty
                    ) * dtSec).coerceAtMost(15.0)

                val meanMeatTempC = 0.6 * coreTempC + 0.4 * surfTempC
                val kColl = inc.combustion.example.components.ThermalDoseIntegrator.collagenHydrolysisRatePerSecond(meanMeatTempC)
                if (kColl > 0.0) {
                    runningIntactCollagen *= kotlin.math.exp(-kColl * dtSec)
                }
                val rowCollagenPct = ((1.0 - runningIntactCollagen) * 100.0).coerceIn(0.0, 100.0)

                prevTemps = DoubleArray(8) { idx -> rawVals.getOrElse(idx) { 0.0 } }
                prevTimestampMs = timestamp

                val spanCount = surfIdx - coreIdx + 1
                val activeProfile = DoubleArray(spanCount) { idx -> rawVals.getOrElse(coreIdx + idx) { 0.0 } }
                val radiusMeters = (surfIdx - coreIdx) * sensorSpacingMeters
                val rowTargetC = if (dataPoint.predictionSetPointTemperature > 0.0) {
                    dataPoint.predictionSetPointTemperature
                } else {
                    effectiveTargetC
                }

                val restingResult = predictor.predictRestingTrajectory(
                    profile = activeProfile,
                    radiusMeters = radiusMeters,
                    alpha = runningAlpha,
                    meatType = meatType,
                    restingEnvironment = restingEnv,
                    surfaceMoisturePhi = runningPhi,
                    targetTempC = rowTargetC
                )
                val peakCoreC = restingResult.peakCoreTempC
                val carryoverRiseC = (peakCoreC - coreTempC).coerceAtLeast(0.0)
                val suggestedPullC = restingResult.pullTempC
                val timeToPeakMin = restingResult.timeToPeakSeconds / 60.0
                val restLog10Add = inc.combustion.example.components.ThermalDoseIntegrator.integrateTrajectorySalmonellaLog10(
                    trajectoryC = restingResult.restingCoreTrajectoryC,
                    dtSeconds = restingResult.dtStepSeconds,
                    meatType = meatType,
                    slowComeUpAdapted = slowComeUpPenalty
                )
                val projectedRestLog10 = (runningLiveLog10 + restLog10Add).coerceAtMost(15.0)

                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%.3f,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s,%s,%s,%d,%s,%b,%.4e,%.2f,%.2f,%.2f,%s,%.2f,%.3f,%.2f,%.2f,%.2f,%s",
                        elapsed,
                        dataPoint.sessionId.toString(),
                        dataPoint.sequenceNumber.toString(),
                        rawVals[0], rawVals[1], rawVals[2], rawVals[3],
                        rawVals[4], rawVals[5], rawVals[6], rawVals[7],
                        dataPoint.virtualCoreTemperature,
                        dataPoint.virtualSurfaceTemperature,
                        dataPoint.virtualAmbientTemperature,
                        dataPoint.estimatedCoreTemperature,
                        dataPoint.predictionSetPointTemperature,
                        dataPoint.virtualCoreSensor.toString(),
                        dataPoint.virtualSurfaceSensor.toString(),
                        dataPoint.virtualAmbientSensor.toString(),
                        dataPoint.predictionState.toString(),
                        dataPoint.predictionMode.toString(),
                        dataPoint.predictionType.toString(),
                        dataPoint.predictionValueSeconds.toInt(),
                        meatType.name,
                        isWrapped,
                        runningAlpha,
                        peakCoreC,
                        carryoverRiseC,
                        suggestedPullC,
                        restingEnv.name,
                        timeToPeakMin,
                        runningPhi,
                        runningLiveLog10,
                        projectedRestLog10,
                        rowCollagenPct,
                        probeState.lastFlipOrLidEvent.value
                    )
                )
            }
        } else {
            // Live snapshot fallback when historical logs have not finished downloading
            val temps = probeState.temperaturesCelsius.toList()
            val coreIdx = probeState.coreIndexState.value.coerceIn(0, 6)
            val surfIdx = probeState.surfaceIndexState.value.coerceIn(coreIdx + 1, 7)
            val spanCount = surfIdx - coreIdx + 1
            val activeProfile = DoubleArray(spanCount) { idx -> temps.getOrElse(coreIdx + idx) { 0.0 } }
            val radiusMeters = (surfIdx - coreIdx) * sensorSpacingMeters

            val restingResult = predictor.predictRestingTrajectory(
                profile = activeProfile,
                radiusMeters = radiusMeters,
                alpha = currentAlpha,
                meatType = meatType,
                restingEnvironment = restingEnv,
                surfaceMoisturePhi = probeState.surfaceMoisturePhiState.value,
                targetTempC = effectiveTargetC
            )
            val peakCoreC = restingResult.peakCoreTempC
            val coreTempC = probeState.rawCoreTemperatureC.value
            val carryoverRiseC = (peakCoreC - coreTempC).coerceAtLeast(0.0)
            val suggestedPullC = restingResult.pullTempC
            val timeToPeakMin = restingResult.timeToPeakSeconds / 60.0

            sb.appendLine(
                String.format(
                    Locale.US,
                    "%.3f,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s,%s,%s,%s,%d,%s,%b,%.4e,%.2f,%.2f,%.2f,%s,%.2f,%.3f,%.2f,%.2f,%.2f,%s",
                    0.0f,
                    probe?.sessionInfo?.sessionID?.toString() ?: "LIVE",
                    probe?.maxSequenceNumber?.toString() ?: "0",
                    temps.getOrElse(0) { 0.0 }, temps.getOrElse(1) { 0.0 }, temps.getOrElse(2) { 0.0 }, temps.getOrElse(3) { 0.0 },
                    temps.getOrElse(4) { 0.0 }, temps.getOrElse(5) { 0.0 }, temps.getOrElse(6) { 0.0 }, temps.getOrElse(7) { 0.0 },
                    coreTempC,
                    probeState.rawSurfaceTemperatureC.value,
                    probeState.rawAmbientTemperatureC.value,
                    probe?.estimatedCoreCelsius ?: coreTempC,
                    effectiveTargetC,
                    probeState.virtualCoreSensor.value.ifEmpty { "T1" },
                    probeState.virtualSurfaceSensor.value.ifEmpty { "T4" },
                    probeState.virtualAmbientSensor.value.ifEmpty { "T8" },
                    probeState.predictionState.value.ifEmpty { "NONE" },
                    probeState.predictionMode.value.ifEmpty { "NONE" },
                    probeState.predictionType.value.ifEmpty { "NONE" },
                    probe?.predictionSeconds?.toInt() ?: 0,
                    meatType.name,
                    isWrapped,
                    currentAlpha,
                    peakCoreC,
                    carryoverRiseC,
                    suggestedPullC,
                    restingEnv.name,
                    timeToPeakMin,
                    probeState.surfaceMoisturePhiState.value,
                    probeState.livePasteurizationLog10.value,
                    probeState.projectedRestPasteurizationLog10.value,
                    probeState.collagenConversionPct.value,
                    probeState.lastFlipOrLidEvent.value
                )
            )
        }

        val fileDateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val recommendedFileName = "ProbeData_${serial}_${now.format(fileDateTimeFormatter)}.csv"

        return recommendedFileName to sb.toString()
    }

    /**
     * Sets the prediction mode to removal with the input setpoint.
     *
     * @param removalTemperatureC The target removal temperature in Celsius.
     */
    fun setRemovalPrediction(removalTemperatureC: Double) {
        probeState.applyUserTargetTemperatureC(removalTemperatureC)

        when (probeState.connectionState.value) {
            ProbeState.ConnectionState.CONNECTED -> {
                deviceManager.setRemovalPrediction(serialNumber, removalTemperatureC) {
                    if (!it) {
                        Log.e(LOG_TAG, "Failed to set removal prediction to $removalTemperatureC ($serialNumber)")
                    }
                }
            }
            ProbeState.ConnectionState.ADVERTISING_CONNECTABLE -> {
                pendingTargetTemperatureC = removalTemperatureC
                deviceManager.connect(serialNumber)
            }
            else -> {
                pendingTargetTemperatureC = removalTemperatureC
                deviceManager.setRemovalPrediction(serialNumber, removalTemperatureC) {
                    if (!it) {
                        Log.d(LOG_TAG, "Target $removalTemperatureC saved locally; awaiting probe connection ($serialNumber)")
                    }
                }
            }
        }
    }

    /**
     * Cancels the prediction if one is active.
     */
    fun cancelPrediction() {
        pendingTargetTemperatureC = null
        probeState.clearUserTargetTemperature()
        deviceManager.cancelPrediction(serialNumber) {
            if (!it) {
                Log.e(LOG_TAG, "Failed to cancel prediction ($serialNumber)")
            }
        }
    }

    /**
     * Updates the current value of the ProbeState with what is represented by the framework.
     *
     * @param probe Probe data object from the service.  If null, attempts to get the current
     * value from the probe.
     */
    private fun updateProbeState(probe: Probe?) {
        val recordCount = deviceManager.recordsDownloaded(serialNumber)

        (probe ?: deviceManager.probe(serialNumber))?.let {
            probeState.updateProbeState(it, recordCount)
        }
    }
}