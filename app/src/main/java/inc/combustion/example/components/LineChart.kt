/*
 * Project: Combustion Inc. Android Example
 * File: LineChart.kt
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

package inc.combustion.example.components

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import inc.combustion.example.databinding.LineChartBinding
import inc.combustion.framework.service.LoggedProbeDataPoint
import java.util.*
import kotlin.math.max

private data class SensorPlotPoint(
    val timestampMs: Long,
    val temperaturesC: DoubleArray
)

@Composable
fun MeasurementsLineChart(
    probeState: ProbeState,
    plotData: SnapshotStateList<LoggedProbeDataPoint>,
    plotDataStartTimestamp: MutableState<Date>,
    noDataComposable: @Composable () -> Unit,
    primaryColor: Int = MaterialTheme.colors.primary.toArgb(),
    onPrimaryColor: Int = MaterialTheme.colors.onPrimary.toArgb(),
) {
    val historyVersion = probeState.historyVersion.value
    val liveSamples = probeState.glidePathHistory.toList()
    val loggedSnapshot = plotData.toList()
    val currentTemps = probeState.temperaturesCelsius.toList()
    val hasAnyValidTemp = currentTemps.any { it > 0.0 } || probeState.rawCoreTemperatureC.value > 0.0

    val mergedPoints = remember(loggedSnapshot.size, liveSamples.size, historyVersion, hasAnyValidTemp) {
        val points = ArrayList<SensorPlotPoint>()
        if (loggedSnapshot.isNotEmpty()) {
            val step = max(1, loggedSnapshot.size / 250)
            var idx = 0
            while (idx < loggedSnapshot.size) {
                val dp = loggedSnapshot[idx]
                val vals = dp.temperatures.values
                points.add(
                    SensorPlotPoint(
                        timestampMs = dp.timestamp.time,
                        temperaturesC = DoubleArray(8) { i -> vals.getOrElse(i) { 0.0 } }
                    )
                )
                idx += step
            }
            val lastDp = loggedSnapshot.last()
            if (points.lastOrNull()?.timestampMs != lastDp.timestamp.time) {
                val vals = lastDp.temperatures.values
                points.add(
                    SensorPlotPoint(
                        timestampMs = lastDp.timestamp.time,
                        temperaturesC = DoubleArray(8) { i -> vals.getOrElse(i) { 0.0 } }
                    )
                )
            }
            val lastLogTime = lastDp.timestamp.time
            liveSamples.filter { it.timestampMs > lastLogTime }.forEach { sample ->
                points.add(
                    SensorPlotPoint(
                        timestampMs = sample.timestampMs,
                        temperaturesC = sample.temperaturesC
                    )
                )
            }
        } else if (liveSamples.isNotEmpty()) {
            liveSamples.forEach { sample ->
                points.add(
                    SensorPlotPoint(
                        timestampMs = sample.timestampMs,
                        temperaturesC = sample.temperaturesC
                    )
                )
            }
        } else if (hasAnyValidTemp) {
            points.add(
                SensorPlotPoint(
                    timestampMs = System.currentTimeMillis(),
                    temperaturesC = DoubleArray(8) { i -> currentTemps.getOrElse(i) { 0.0 } }
                )
            )
        }
        points
    }

    if (mergedPoints.isEmpty()) {
        noDataComposable()
    } else {
        AndroidViewBinding(
            factory = { inflater, parent, attachToParent ->
                LineChartBinding.inflate(inflater, parent, attachToParent).apply {
                    InitializeChart(lineChart, primaryColor, onPrimaryColor)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(top = 8.dp),
            update = {
                if (lineChart.data == null || lineChart.data.dataSetCount < 8) {
                    InitializeChart(lineChart, primaryColor, onPrimaryColor)
                }

                for (index in 0..7) {
                    (lineChart.data.getDataSetByIndex(index) as? LineDataSet)?.clear()
                }

                val startTimeMs = if (loggedSnapshot.isNotEmpty()) {
                    minOf(plotDataStartTimestamp.value.time, mergedPoints.first().timestampMs)
                } else {
                    mergedPoints.first().timestampMs
                }

                var lastX = -1f
                mergedPoints.forEach { pt ->
                    val rawMinutes = (pt.timestampMs - startTimeMs) / 60000.0f
                    val xVal = if (lastX >= 0f && rawMinutes <= lastX) {
                        lastX + 0.02f
                    } else {
                        rawMinutes
                    }
                    lastX = xVal

                    for (sensorIdx in 0..7) {
                        val tempDisplay = probeState.toDisplayTemperature(pt.temperaturesC[sensorIdx]).toFloat()
                        lineChart.data.addEntry(Entry(xVal, tempDisplay), sensorIdx)
                    }
                }

                // If only a single point exists so far, add a second point at +0.1 min so all 8 sensor lines render visibly
                if (mergedPoints.size == 1) {
                    val pt = mergedPoints.first()
                    for (sensorIdx in 0..7) {
                        val tempDisplay = probeState.toDisplayTemperature(pt.temperaturesC[sensorIdx]).toFloat()
                        lineChart.data.addEntry(Entry(0.1f, tempDisplay), sensorIdx)
                    }
                }

                lineChart.data.notifyDataChanged()
                lineChart.notifyDataSetChanged()
                lineChart.invalidate()
            }
        )
    }
}

fun InitializeChart(
    chart: LineChart,
    primaryColor: Int,
    onPrimaryColor: Int
) {
    val lineColors = listOf(
        Color(0xFF2979FF).toArgb(),
        Color(0xFF00E5FF).toArgb(),
        Color(0xFF00E676).toArgb(),
        Color(0xFFFFEA00).toArgb(),
        Color(0xFFE040FB).toArgb(),
        Color(0xFFFF5252).toArgb(),
        Color(0xFFFF9100).toArgb(),
        Color(0xFFFF4081).toArgb()
    )

    chart.description.isEnabled = true
    chart.description.text = "Elapsed Time (min)"
    chart.description.textColor = onPrimaryColor

    chart.setTouchEnabled(true)
    chart.isDragEnabled = true
    chart.setScaleEnabled(true)
    chart.setDrawGridBackground(false)
    chart.setGridBackgroundColor(primaryColor)
    chart.setPinchZoom(true)
    chart.setBackgroundColor(Color.Transparent.toArgb())

    val data = LineData()
    data.setValueTextColor(onPrimaryColor)
    data.isHighlightEnabled = false
    chart.data = data

    chart.legend.form = Legend.LegendForm.LINE
    chart.legend.typeface = Typeface.SANS_SERIF
    chart.legend.textColor = onPrimaryColor
    chart.legend.isWordWrapEnabled = true

    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.typeface = Typeface.SANS_SERIF
    chart.xAxis.textColor = onPrimaryColor
    chart.xAxis.setAvoidFirstLastClipping(true)
    chart.xAxis.isEnabled = true
    chart.xAxis.granularity = 0.1f

    chart.axisLeft.typeface = Typeface.SANS_SERIF
    chart.axisLeft.textColor = onPrimaryColor
    chart.axisLeft.setDrawGridLines(true)

    chart.axisRight.isEnabled = false

    for (index in 0..7) {
        val set = LineDataSet(null, "T${index + 1}").apply {
            axisDependency = YAxis.AxisDependency.LEFT
            color = lineColors[index]
            lineWidth = 2.0f
            fillAlpha = 65
            fillColor = lineColors[index]
            valueTextColor = onPrimaryColor
            setDrawCircles(false)
            setDrawValues(false)
            setDrawHighlightIndicators(false)
            setDrawVerticalHighlightIndicator(false)
        }
        data.addDataSet(set)
    }
}