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
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults

// --- V4 IMPORTS ---
import inc.combustion.example.prediction.PhysicsPredictor
import inc.combustion.framework.ble.device.MeatMaterials

@Composable
fun CookingDashboard(
    probeState: ProbeState,
    onMeatSelected: (MeatType) -> Unit
) {
    // 1. Calculate the Zone (V3.5 Logic)
    val predictedString = probeState.suggestedPullTemp.value
    var zoneDisplay = "---"

    // Logic to hide Ghost Zone: Only show if valid number AND > 5 degrees
    if (predictedString != "---") {
        try {
            val centerVal = predictedString.toDouble()
            // Safety check: Don't show a zone for 0.0 target
            if (centerVal > 5.0) {
                val minPull = (centerVal - 1.0).roundToInt()
                val maxPull = (centerVal + 1.0).roundToInt()
                zoneDisplay = "$minPull° – $maxPull°"
            }
        } catch (e: Exception) {
            zoneDisplay = "---"
        }
    }

    // --- V4 MAPPING LOGIC ---
    // Convert the UI "MeatType" (Simple) to Physics "MeatMaterials" (Complex)
    val v4Material = when (probeState.selectedMeatType.value) {
        MeatType.PORK -> MeatMaterials.PORK_SHOULDER
        MeatType.POULTRY -> MeatMaterials.POULTRY
        else -> MeatMaterials.BEEF_BRISKET // Default Beef to Brisket to test "The Stall"
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
            // HEADER
            Text(
                text = "GLIDE PATH ENGINE",
                style = MaterialTheme.typography.overline,
                color = Color.Gray,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // MAIN DATA ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: CURRENT TEMP
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${probeState.coreTemperature.value}°",
                        style = MaterialTheme.typography.h3,
                        fontWeight = FontWeight.Bold
                    )
                    Text("CURRENT", style = MaterialTheme.typography.caption)
                }

                // ARROW INDICATOR
                Text(
                    text = "➜",
                    fontSize = 24.sp,
                    color = Combustion_Yellow
                )

                // RIGHT: PULL ZONE
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

            // --- V4 PREDICTION DISPLAY ---
            // Inserted here: Shows the physics simulation result
            V4PredictionCard(
                currentCoreTemp = try { probeState.coreTemperature.value.toDouble() } catch(e:Exception) { 0.0 },
                currentSurfaceTemp = try { probeState.surfaceTemperature.value.toDouble() } catch(e:Exception) { 0.0 },
                meatType = v4Material
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            // -----------------------------

            // MEAT SELECTOR BUTTONS
            Text("Material Physics:", style = MaterialTheme.typography.body2, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeatButton("🥩", "Beef", probeState.selectedMeatType.value == MeatType.BEEF) { onMeatSelected(MeatType.BEEF) }
                MeatButton("🐷", "Pork", probeState.selectedMeatType.value == MeatType.PORK) { onMeatSelected(MeatType.PORK) }
                MeatButton("🍗", "Poultry", probeState.selectedMeatType.value == MeatType.POULTRY) { onMeatSelected(MeatType.POULTRY) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // INSULATION TOGGLE
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = probeState.isWrapped.value,
                    onCheckedChange = { probeState.isWrapped.value = it },
                    colors = CheckboxDefaults.colors(checkedColor = Combustion_Red)
                )
                Text(
                    text = "Wrapped / Insulated (Foil)",
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
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

// --- V4 PREDICTION COMPONENT ---
@Composable
fun V4PredictionCard(
    currentCoreTemp: Double,
    currentSurfaceTemp: Double,
    meatType: MeatMaterials
) {
    // 1. Initialize the Predictor (persist across recompositions)
    val predictor = remember { PhysicsPredictor() }
    
    // 2. State for the display text
    var predictionText by remember { mutableStateOf("Waiting for data...") }
    var textColor by remember { mutableStateOf(Color.Gray) }

    // 3. Run Prediction when temps change
    LaunchedEffect(currentCoreTemp, currentSurfaceTemp, meatType) {
        if (currentCoreTemp > 20 && currentSurfaceTemp > 20) {
            
            predictionText = "Simulating..."
            
            // Run calculation on background thread
            val seconds = predictor.predictSecondsToDone(
                currentCoreTemp = currentCoreTemp,
                currentSurfaceTemp = currentSurfaceTemp,
                targetTemp = 96.0, // Hardcoded 205°F for Brisket Test
                meatType = meatType
            )

            if (seconds == -1L) {
                predictionText = "Stall/Heat Error"
                textColor = Color.Red
            } else {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                predictionText = "Time to Done: ${hours}h ${minutes}m"
                textColor = Color(0xFF006400) // Dark Green
            }
        } else {
            predictionText = "Probe Cold / Disconnected"
        }
    }

    // 4. UI Layout
    Card(
        elevation = 2.dp,
        backgroundColor = Color(0xFFF5F5F5), // Light grey background to distinguish it
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "V4 PHYSICS ENGINE (${meatType.displayName})",
                style = MaterialTheme.typography.overline,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = predictionText,
                style = MaterialTheme.typography.h6,
                color = textColor
            )
            if (meatType.waterContentPercent > 0.4) {
                Text(
                    text = "*Simulating Stall (Evaporative Cooling)",
                    style = MaterialTheme.typography.caption,
                    color = Color.LightGray
                )
            }
        }
    }
}