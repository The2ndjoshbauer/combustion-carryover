package inc.combustion.example.details

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import inc.combustion.example.MeatType
import inc.combustion.example.R
import inc.combustion.example.components.GlidePathSample
import inc.combustion.example.components.ProbeState
import inc.combustion.example.prediction.PhysicsPredictor
import inc.combustion.example.theme.Combustion_Red
import inc.combustion.example.theme.Combustion_Yellow
import inc.combustion.framework.ble.device.MeatMaterials
import inc.combustion.framework.service.LoggedProbeDataPoint
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CookingDashboard(
    probeState: ProbeState,
    plotData: List<LoggedProbeDataPoint> = emptyList(),
    recordCount: Int = 0,
    onSetTargetTempClick: () -> Unit = {},
    onExportCsvClick: () -> Unit = {},
    onMeatSelected: (MeatType) -> Unit
) {
    val predictedString = probeState.suggestedPullTemp.value
    var zoneDisplay = "---"

    if (predictedString != "---") {
        try {
            val centerVal = predictedString.toDouble()
            if (centerVal > 5.0) {
                val minPull = (centerVal - 1.0).roundToInt()
                val maxPull = (centerVal + 1.0).roundToInt()
                zoneDisplay = "$minPull° – $maxPull°"
            }
        } catch (e: Exception) {
            zoneDisplay = "---"
        }
    }

    val v4Material = when (probeState.selectedMeatType.value) {
        MeatType.PORK -> MeatMaterials.PORK_SHOULDER
        MeatType.POULTRY -> MeatMaterials.POULTRY
        MeatType.FISH -> MeatMaterials.SALMON
        else -> MeatMaterials.BEEF_BRISKET
    }

    val effectiveTargetC = probeState.getEffectiveTargetTempC()
    val displayTargetTemp = probeState.toDisplayTemperature(effectiveTargetC).roundToInt()

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GLIDE PATH ENGINE",
                style = MaterialTheme.typography.overline,
                color = Color.Gray,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${probeState.coreTemperature.value}°",
                        style = MaterialTheme.typography.h3,
                        fontWeight = FontWeight.Bold
                    )
                    Text("CURRENT", style = MaterialTheme.typography.caption)
                }

                Text(
                    text = "➜",
                    fontSize = 24.sp,
                    color = Combustion_Yellow
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = zoneDisplay,
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.Bold,
                        color = Combustion_Red
                    )
                    Text("PULL ZONE", style = MaterialTheme.typography.caption, color = Combustion_Red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF23272D), shape = RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARGET DONENESS",
                        style = MaterialTheme.typography.overline,
                        color = Color(0xFFB0B8C1)
                    )
                    Text(
                        text = "${displayTargetTemp}°",
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Button(
                    onClick = onSetTargetTempClick,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Combustion_Red,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("set_target_temp_button")
                ) {
                    Text(
                        text = "Set Target Temp",
                        style = MaterialTheme.typography.button,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.DarkGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            V4PredictionCard(
                temperaturesCelsius = probeState.temperaturesCelsius.toList(),
                coreIndex = probeState.coreIndexState.value,
                surfaceIndex = probeState.surfaceIndexState.value,
                currentCoreTempC = probeState.rawCoreTemperatureC.value,
                ambientTempC = probeState.rawAmbientTemperatureC.value,
                targetTempC = effectiveTargetC,
                meatType = v4Material,
                isWrapped = probeState.isWrapped.value,
                effectiveDiffusivity = probeState.effectiveDiffusivityState.value
            )

            Spacer(modifier = Modifier.height(12.dp))

            GlidePathRealTimeChart(
                probeState = probeState,
                plotData = plotData,
                targetTempC = effectiveTargetC
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Material Physics:", style = MaterialTheme.typography.body2, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeatButton("🥩", "Beef", probeState.selectedMeatType.value == MeatType.BEEF) {
                    onMeatSelected(MeatType.BEEF)
                    probeState.effectiveDiffusivityState.value = MeatType.BEEF.diffusivity
                    probeState.recalculateCarryover()
                }
                MeatButton("🐷", "Pork", probeState.selectedMeatType.value == MeatType.PORK) {
                    onMeatSelected(MeatType.PORK)
                    probeState.effectiveDiffusivityState.value = MeatType.PORK.diffusivity
                    probeState.recalculateCarryover()
                }
                MeatButton("🍗", "Poultry", probeState.selectedMeatType.value == MeatType.POULTRY) {
                    onMeatSelected(MeatType.POULTRY)
                    probeState.effectiveDiffusivityState.value = MeatType.POULTRY.diffusivity
                    probeState.recalculateCarryover()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = probeState.isWrapped.value,
                    onCheckedChange = {
                        probeState.isWrapped.value = it
                        probeState.recalculateCarryover()
                    },
                    colors = CheckboxDefaults.colors(checkedColor = Combustion_Red)
                )
                Text(
                    text = "Wrapped / Insulated (Foil)",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val exportLabel = if (recordCount > 0) {
                "Export CSV (Glide Path + $recordCount Logs)"
            } else {
                "Export Live Snapshot CSV (Glide Path)"
            }

            Button(
                onClick = onExportCsvClick,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Combustion_Yellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("export_csv_button")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_share_24),
                    contentDescription = "Export CSV",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = exportLabel,
                    style = MaterialTheme.typography.button,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun GlidePathRealTimeChart(
    probeState: ProbeState,
    plotData: List<LoggedProbeDataPoint>,
    targetTempC: Double
) {
    val historyVersion = probeState.historyVersion.value
    val selectedMeat = probeState.selectedMeatType.value
    val isWrapped = probeState.isWrapped.value
    val liveSamples = probeState.glidePathHistory.toList()
    val loggedCount = plotData.size

    val samplesToPlot: List<GlidePathSample> = remember(
        loggedCount,
        liveSamples.size,
        historyVersion,
        selectedMeat,
        isWrapped,
        targetTempC
    ) {
        if (plotData.isNotEmpty()) {
            val step = max(1, plotData.size / 120)
            val sampledLogs = ArrayList<GlidePathSample>((plotData.size / step) + 2)
            var idx = 0
            while (idx < plotData.size) {
                val dp = plotData[idx]
                val coreIdx = dp.virtualCoreSensor.ordinal.coerceIn(0, 6)
                val surfIdx = (dp.virtualSurfaceSensor.ordinal + 3).coerceIn(coreIdx + 1, 7)
                val rawVals = dp.temperatures.values
                sampledLogs.add(
                    GlidePathSample(
                        timestampMs = dp.timestamp.time,
                        temperaturesC = DoubleArray(8) { i -> rawVals.getOrElse(i) { 0.0 } },
                        coreIndex = coreIdx,
                        surfaceIndex = surfIdx,
                        coreTempC = dp.virtualCoreTemperature,
                        surfaceTempC = dp.virtualSurfaceTemperature,
                        effectiveAlpha = probeState.effectiveDiffusivityState.value
                    )
                )
                idx += step
            }
            val lastDp = plotData.last()
            if (sampledLogs.lastOrNull()?.timestampMs != lastDp.timestamp.time) {
                val coreIdx = lastDp.virtualCoreSensor.ordinal.coerceIn(0, 6)
                val surfIdx = (lastDp.virtualSurfaceSensor.ordinal + 3).coerceIn(coreIdx + 1, 7)
                val rawVals = lastDp.temperatures.values
                sampledLogs.add(
                    GlidePathSample(
                        timestampMs = lastDp.timestamp.time,
                        temperaturesC = DoubleArray(8) { i -> rawVals.getOrElse(i) { 0.0 } },
                        coreIndex = coreIdx,
                        surfaceIndex = surfIdx,
                        coreTempC = lastDp.virtualCoreTemperature,
                        surfaceTempC = lastDp.virtualSurfaceTemperature,
                        effectiveAlpha = probeState.effectiveDiffusivityState.value
                    )
                )
            }
            sampledLogs
        } else {
            liveSamples
        }
    }

    Card(
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color(0xFF141619),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glide_path_realtime_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME GLIDE PATH TELEMETRY",
                    style = MaterialTheme.typography.overline,
                    color = Color(0xFFB0B8C1),
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (samplesToPlot.isNotEmpty()) "${samplesToPlot.size} pts" else "Live",
                    style = MaterialTheme.typography.caption,
                    color = Combustion_Yellow
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ChartLegendChip(color = Color(0xFF00E5FF), label = "Core")
                ChartLegendChip(color = Color(0xFFFF9100), label = "Surface")
                ChartLegendChip(color = Combustion_Yellow, label = "Resting Peak")
                ChartLegendChip(color = Combustion_Red, label = "Pull Target")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (samplesToPlot.isEmpty() && probeState.rawCoreTemperatureC.value <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Waiting for live probe temperature telemetry...",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }
            } else {
                AndroidView(
                    factory = { context ->
                        LineChart(context).apply {
                            configureGlidePathChart(this)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("glide_path_line_chart"),
                    update = { chart ->
                        updateGlidePathChartData(
                            chart = chart,
                            samples = samplesToPlot,
                            probeState = probeState,
                            targetTempC = targetTempC
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ChartLegendChip(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFFD5DCE2)
        )
    }
}

private fun configureGlidePathChart(chart: LineChart) {
    val axisTextColor = Color(0xFFCFD8DC).toArgb()
    val gridColor = Color(0xFF2A2E35).toArgb()

    chart.description.isEnabled = true
    chart.description.text = "Elapsed Time (min)"
    chart.description.textColor = axisTextColor
    chart.description.textSize = 10f

    chart.setTouchEnabled(true)
    chart.isDragEnabled = true
    chart.setScaleEnabled(true)
    chart.setPinchZoom(true)
    chart.setDrawGridBackground(false)
    chart.setBackgroundColor(Color.Transparent.toArgb())

    // Legend is rendered cleanly in Compose header above the chart
    chart.legend.isEnabled = false

    chart.xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        typeface = Typeface.SANS_SERIF
        textColor = axisTextColor
        setDrawGridLines(true)
        this.gridColor = gridColor
        setAvoidFirstLastClipping(true)
        granularity = 0.1f
    }

    chart.axisLeft.apply {
        typeface = Typeface.SANS_SERIF
        textColor = axisTextColor
        setDrawGridLines(true)
        this.gridColor = gridColor
    }

    chart.axisRight.isEnabled = false
}

private fun updateGlidePathChartData(
    chart: LineChart,
    samples: List<GlidePathSample>,
    probeState: ProbeState,
    targetTempC: Double
) {
    val activeSamples = if (samples.isNotEmpty()) {
        samples
    } else {
        val now = System.currentTimeMillis()
        listOf(
            GlidePathSample(
                timestampMs = now,
                temperaturesC = DoubleArray(8) { probeState.temperaturesCelsius.getOrElse(it) { 0.0 } },
                coreIndex = probeState.coreIndexState.value,
                surfaceIndex = probeState.surfaceIndexState.value,
                coreTempC = probeState.rawCoreTemperatureC.value,
                surfaceTempC = probeState.rawSurfaceTemperatureC.value,
                effectiveAlpha = probeState.effectiveDiffusivityState.value
            )
        )
    }

    val coreEntries = ArrayList<Entry>(activeSamples.size + 1)
    val surfaceEntries = ArrayList<Entry>(activeSamples.size + 1)
    val peakEntries = ArrayList<Entry>(activeSamples.size + 1)
    val pullTargetEntries = ArrayList<Entry>(activeSamples.size + 1)

    val startTimeMs = activeSamples.first().timestampMs

    activeSamples.forEachIndexed { index, sample ->
        val rawElapsedMin = ((sample.timestampMs - startTimeMs) / 60000.0f)
        val xVal = if (index > 0 && rawElapsedMin <= coreEntries.last().x) {
            coreEntries.last().x + 0.02f
        } else {
            rawElapsedMin
        }

        val (peakCoreC, suggestedPullC) = probeState.computeSampleCarryover(
            profileTempsC = sample.temperaturesC,
            coreIdx = sample.coreIndex,
            surfIdx = sample.surfaceIndex,
            coreTempC = sample.coreTempC,
            targetTempC = targetTempC,
            alpha = sample.effectiveAlpha
        )

        val coreDisplay = probeState.toDisplayTemperature(sample.coreTempC).toFloat()
        val surfDisplay = probeState.toDisplayTemperature(sample.surfaceTempC).toFloat()
        val peakDisplay = probeState.toDisplayTemperature(peakCoreC).toFloat()
        val pullDisplay = probeState.toDisplayTemperature(suggestedPullC).toFloat()

        coreEntries.add(Entry(xVal, coreDisplay))
        surfaceEntries.add(Entry(xVal, surfDisplay))
        peakEntries.add(Entry(xVal, peakDisplay))
        pullTargetEntries.add(Entry(xVal, pullDisplay))
    }

    // If only 1 data point has arrived so far, add a second point at +0.1 min so all 4 lines render visibly
    if (coreEntries.size == 1) {
        coreEntries.add(Entry(0.1f, coreEntries[0].y))
        surfaceEntries.add(Entry(0.1f, surfaceEntries[0].y))
        peakEntries.add(Entry(0.1f, peakEntries[0].y))
        pullTargetEntries.add(Entry(0.1f, pullTargetEntries[0].y))
    }

    val coreColor = Color(0xFF00E5FF).toArgb()
    val surfaceColor = Color(0xFFFF9100).toArgb()
    val peakColor = Combustion_Yellow.toArgb()
    val pullColor = Combustion_Red.toArgb()
    val valueTextColor = Color.White.toArgb()

    val coreDataSet = LineDataSet(coreEntries, "Core").apply {
        axisDependency = YAxis.AxisDependency.LEFT
        color = coreColor
        lineWidth = 2.4f
        setDrawCircles(false)
        setDrawValues(false)
        setDrawHighlightIndicators(false)
        this.valueTextColor = valueTextColor
    }

    val surfaceDataSet = LineDataSet(surfaceEntries, "Surface").apply {
        axisDependency = YAxis.AxisDependency.LEFT
        color = surfaceColor
        lineWidth = 1.8f
        setDrawCircles(false)
        setDrawValues(false)
        setDrawHighlightIndicators(false)
        this.valueTextColor = valueTextColor
    }

    val peakDataSet = LineDataSet(peakEntries, "Resting Peak").apply {
        axisDependency = YAxis.AxisDependency.LEFT
        color = peakColor
        lineWidth = 2.6f
        setDrawCircles(false)
        setDrawValues(false)
        setDrawHighlightIndicators(false)
        setDrawFilled(true)
        fillColor = peakColor
        fillAlpha = 30
        this.valueTextColor = valueTextColor
    }

    val pullDataSet = LineDataSet(pullTargetEntries, "Pull Target").apply {
        axisDependency = YAxis.AxisDependency.LEFT
        color = pullColor
        lineWidth = 2.2f
        enableDashedLine(14f, 8f, 0f)
        setDrawCircles(false)
        setDrawValues(false)
        setDrawHighlightIndicators(false)
        this.valueTextColor = valueTextColor
    }

    val targetDisplay = probeState.toDisplayTemperature(targetTempC).toFloat()
    chart.axisLeft.removeAllLimitLines()
    val targetLimitLine = LimitLine(targetDisplay, "Target ${targetDisplay.roundToInt()}°").apply {
        lineWidth = 1.2f
        lineColor = Color(0xFF90A4AE).toArgb()
        enableDashedLine(8f, 6f, 0f)
        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        textSize = 9f
        textColor = Color(0xFFCFD8DC).toArgb()
    }
    chart.axisLeft.addLimitLine(targetLimitLine)

    val lineData = LineData(coreDataSet, surfaceDataSet, peakDataSet, pullDataSet)
    lineData.isHighlightEnabled = false
    chart.data = lineData
    chart.data.notifyDataChanged()
    chart.notifyDataSetChanged()
    chart.invalidate()
}

@Composable
fun MeatButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) Combustion_Yellow else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.LightGray
    val borderColor = if (isSelected) Combustion_Yellow else Color.Gray

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(40.dp)
    ) {
        Text("$icon $label", color = textColor, fontSize = 12.sp)
    }
}

@Composable
fun V4PredictionCard(
    temperaturesCelsius: List<Double>,
    coreIndex: Int,
    surfaceIndex: Int,
    currentCoreTempC: Double,
    ambientTempC: Double,
    targetTempC: Double,
    meatType: MeatMaterials,
    isWrapped: Boolean,
    effectiveDiffusivity: Double
) {
    val predictor = remember { PhysicsPredictor() }

    var predictionText by remember { mutableStateOf("Waiting for data...") }
    var textColor by remember { mutableStateOf(Color.DarkGray) }

    LaunchedEffect(
        currentCoreTempC,
        ambientTempC,
        targetTempC,
        meatType,
        isWrapped,
        coreIndex,
        surfaceIndex
    ) {
        if (currentCoreTempC > 20.0 && ambientTempC > 20.0) {
            predictionText = "Simulating Crank-Nicolson..."

            val seconds = predictor.predictSecondsToPull(
                temperaturesCelsius = temperaturesCelsius,
                coreIndex = coreIndex,
                surfaceIndex = surfaceIndex,
                ambientTempC = ambientTempC,
                targetTempC = targetTempC,
                meatType = meatType,
                isWrapped = isWrapped,
                customDiffusivity = effectiveDiffusivity
            )

            when {
                seconds == -1L -> {
                    predictionText = "Stall / Ambient Below Target"
                    textColor = Color.Red
                }
                seconds == 0L -> {
                    predictionText = "PULL NOW (In Pull Zone)"
                    textColor = Combustion_Red
                }
                else -> {
                    val hours = seconds / 3600
                    val minutes = (seconds % 3600) / 60
                    predictionText = "Time to Pull: ${hours}h ${minutes}m"
                    textColor = Color(0xFF006400)
                }
            }
        } else {
            predictionText = "Probe Cold / Disconnected"
            textColor = Color.DarkGray
        }
    }

    Card(
        elevation = 2.dp,
        backgroundColor = Color(0xFFF5F5F5),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "V4 CRANK-NICOLSON ORACLE (${meatType.displayName})",
                style = MaterialTheme.typography.overline,
                color = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = predictionText,
                style = MaterialTheme.typography.h6,
                color = textColor
            )
            Text(
                text = String.format("Calibrated α: %.2f × 10⁻⁷ m²/s", effectiveDiffusivity * 1e7),
                style = MaterialTheme.typography.caption,
                color = Color.DarkGray
            )
        }
    }
}
