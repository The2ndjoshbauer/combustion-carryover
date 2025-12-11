/*
 * Project: Combustion Inc. Android Example
 * File: DetailsScreen.kt
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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import inc.combustion.example.AppState
import inc.combustion.example.components.*
import inc.combustion.framework.service.*
import java.util.*
import kotlin.math.roundToInt

data class DetailsScreenState(
    val serialNumber: String,
    val probeState: ProbeState,
    val probeData: SnapshotStateList<LoggedProbeDataPoint> = mutableStateListOf(),
    val probeDataStartTimestamp: MutableState<Date>,
    val measurementCardIsExpanded: MutableState<Boolean>,
    val plotCardIsExpanded: MutableState<Boolean>,
    val detailsCardIsExpanded: MutableState<Boolean>,
    val instantReadCardIsExpanded: MutableState<Boolean>,
    val temperaturesCardIsExpanded: MutableState<Boolean>,
    val predictionsCardIsExpanded: MutableState<Boolean>,
    val onGetTargetTemperatureC: () -> Double,
    val onConnectClick: () -> Unit,
    val onSetProbeColorClick: (ProbeColor) -> Unit,
    val onSetProbeIDClick: (ProbeID) -> Unit,
    val onShareClick: () -> Unit,
    val onSetRemovalPredictionClick: (Int) -> Unit,
    val onCancelPredictionClick: () -> Unit
)

@Composable
fun DetailsScreen(appState: AppState, serialNumber: String?) {
    val unitsConversion: (Double) -> Double = { celsius -> appState.toPreferredTemperatureUnits(celsius) }
    val viewModel : DetailsViewModel = viewModel(factory = DetailsViewModel.Factory(DeviceManager.instance, serialNumber ?: "?", unitsConversion))

    val screenState = DetailsScreenState(
        serialNumber = viewModel.serialNumber,
        probeState =  viewModel.probeState,
        probeData = viewModel.probeData,
        onGetTargetTemperatureC = { viewModel.predictionTargetTemperatureC },
        probeDataStartTimestamp = viewModel.probeDataStartTimestamp,
        measurementCardIsExpanded = appState.showMeasurements,
        plotCardIsExpanded = appState.showPlot,
        detailsCardIsExpanded = appState.showDetails,
        instantReadCardIsExpanded = appState.showInstantRead,
        temperaturesCardIsExpanded = appState.showTemperatures,
        predictionsCardIsExpanded = appState.showPrediction,
        onConnectClick =  { viewModel.toggleConnection() },
        onSetProbeColorClick = { color -> viewModel.setProbeColor(color) },
        onSetProbeIDClick = { id -> viewModel.setProbeID(id) },
        onShareClick = { val (fileName, fileData) = viewModel.getShareData(); appState.onShareTextData(fileName, fileData) },
        onSetRemovalPredictionClick = { viewModel.setRemovalPrediction(appState.fromPreferredTemperatureUnits(it.toDouble())) },
        onCancelPredictionClick = { viewModel.cancelPrediction() }
    )

    DetailsContent(appState = appState, screenState = screenState)
}

@Composable
fun DetailsContent(appState: AppState, screenState: DetailsScreenState) {
    var showProbeColorDialog by remember { mutableStateOf(false) }
    var showProbeIDDialog by remember { mutableStateOf(false) }
    var showCancelPredictionDialog by remember { mutableStateOf(false) }
    var showEnterSetpointDialog by remember { mutableStateOf(false) }

    // --- DIALOG FIX: Convert between Index (Int) and Enum (Type) ---
    
    if (showProbeColorDialog) {
        val colors = ProbeColor.values()
        // Find the index of the current color (default to 0 if not found)
        val currentIndex = colors.indexOfFirst { it.toString() == screenState.probeState.color.value }.let { if (it == -1) 0 else it }
        
        SingleSelectDialog(
            title = "Select Probe Color",
            optionsList = colors.map { it.toString() },
            defaultSelected = currentIndex, // Passing Int
            submitButtonText = "OK",
            onSubmitButtonClick = { index ->
                // Converting Int back to Enum
                if (index in colors.indices) {
                    screenState.onSetProbeColorClick(colors[index])
                }
                showProbeColorDialog = false
            },
            onDismissRequest = { showProbeColorDialog = false }
        )
    }

    if (showProbeIDDialog) {
        val ids = ProbeID.values()
        // Find the index of the current ID
        val currentIndex = ids.indexOfFirst { it.toString() == screenState.probeState.id.value }.let { if (it == -1) 0 else it }

        SingleSelectDialog(
            title = "Select Probe ID",
            optionsList = ids.map { it.toString() },
            defaultSelected = currentIndex, // Passing Int
            submitButtonText = "OK",
            onSubmitButtonClick = { index ->
                 // Converting Int back to Enum
                if (index in ids.indices) {
                    screenState.onSetProbeIDClick(ids[index])
                }
                showProbeIDDialog = false
            },
            onDismissRequest = { showProbeIDDialog = false }
        )
    }
    // -------------------------------------------------------------

    if (showCancelPredictionDialog) {
        ConfirmationDialog(
            title = "Cancel Prediction?",
            details = "Are you sure you want to cancel the current prediction?",
            onYesClick = {
                screenState.onCancelPredictionClick()
                showCancelPredictionDialog = false
            },
            onDismiss = { showCancelPredictionDialog = false }
        )
    }

    if (showEnterSetpointDialog) {
        TemperatureSelectionDialog(
            title = "Target Temperature", buttonText = "Set",
            onButtonClick = screenState.onSetRemovalPredictionClick,
            onDismissRequest = { showEnterSetpointDialog = false },
            unitsString = if(appState.units.value == AppState.Units.FAHRENHEIT) "Fahrenheit" else "Celsius",
            initialValue = appState.toPreferredTemperatureUnits(screenState.onGetTargetTemperatureC()).roundToInt(),
            minValue = appState.toPreferredTemperatureUnits(DetailsViewModel.MINIMUM_PREDICTION_SETPOINT_CELSIUS).toInt(),
            maxValue = appState.toPreferredTemperatureUnits(DetailsViewModel.MAXIMUM_PREDICTION_SETPOINT_CELSIUS).roundToInt()
        )
    }

    AppScaffold(
        title = screenState.serialNumber,
        navigationIcon = { BackIconButton(onClick = { appState.navigateBack() }) },
        actionIcons = {
            ShareIconButton(enable = screenState.probeData.size > 0, onClick = { screenState.onShareClick() })
            ConnectionStateButton(probeState = screenState.probeState, onClick = screenState.onConnectClick)
        },
        appState = appState
    ) {
        if (!appState.isScanning.value || !appState.bluetoothIsOn.value) {
            AppProgressIndicator(reason = appState.noDevicesReasonString)
        } else {
            LazyColumn {
                item { TemperaturesCard(probeState = screenState.probeState, cardIsExpanded = screenState.temperaturesCardIsExpanded) }

                // --- V3.5 GLIDE PATH DASHBOARD ---
                item {
                    CookingDashboard(
                        probeState = screenState.probeState,
                        onMeatSelected = { type -> screenState.probeState.selectedMeatType.value = type }
                    )
                }
                // --------------------------------

                item { PredictionsCard(probeState = screenState.probeState, cardIsExpanded = screenState.predictionsCardIsExpanded, onSetPredictionClick = { showEnterSetpointDialog = true }, onCancelPredictionClick = { showCancelPredictionDialog = true }) }
                item { InstantReadCard(probeState = screenState.probeState, cardIsExpanded = screenState.instantReadCardIsExpanded) }
                item { PlotCard(plotData = screenState.probeData, probeState = screenState.probeState, cardIsExpanded = screenState.plotCardIsExpanded, plotDataStartTimestamp = screenState.probeDataStartTimestamp) }
                item { MeasurementsCard(probeState = screenState.probeState, cardIsExpanded = screenState.measurementCardIsExpanded) }
                item { DetailsCard(probeState = screenState.probeState, cardIsExpanded = screenState.detailsCardIsExpanded, onSetProbeColorClick = { showProbeColorDialog = true }, onSetProbeIDClick = { showProbeIDDialog = true }) }
            }
        }
    }
}