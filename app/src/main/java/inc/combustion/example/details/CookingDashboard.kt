package inc.combustion.example.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import inc.combustion.example.MeatType
import inc.combustion.example.components.ProbeState
import inc.combustion.example.theme.Combustion_Red
import inc.combustion.example.theme.Combustion_Yellow
import kotlin.math.roundToInt
import inc.combustion.example.prediction.PhysicsPredictor
import inc.combustion.framework.ble.device.MeatMaterials

@Composable
fun CookingDashboard(
    probeState: ProbeState,
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

    val effectiveTargetC = if (probeState.setPointTemperature.value.isNotEmpty()) {
        probeState.rawSetPointTemperatureC.value
    } else {
        when (probeState.selectedMeatType.value) {
            MeatType.POULTRY -> 68.3
            else -> 96.0
        }
    }

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

            Spacer(modifier = Modifier.height(24.dp))
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
        }
    }
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
