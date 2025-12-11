package inc.combustion.example.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
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
            val minPull = (centerVal - 1.0).roundToInt()
            val maxPull = (centerVal + 1.0).roundToInt()
            zoneDisplay = "$minPull° – $maxPull°"
        } catch (e: Exception) {
            zoneDisplay = "---"
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GLIDE PATH ENGINE", style = MaterialTheme.typography.overline, color = Color.Gray, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${probeState.coreTemperature.value}°", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Bold)
                    Text("CURRENT", style = MaterialTheme.typography.caption)
                }
                Text("➜", fontSize = 24.sp, color = Combustion_Yellow)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(zoneDisplay, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold, color = Combustion_Red)
                    Text("PULL ZONE", style = MaterialTheme.typography.caption, color = Combustion_Red)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.DarkGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Material Physics:", style = MaterialTheme.typography.body2, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MeatButton("🥩", "Beef", probeState.selectedMeatType.value == MeatType.BEEF) { onMeatSelected(MeatType.BEEF) }
                MeatButton("🐷", "Pork", probeState.selectedMeatType.value == MeatType.PORK) { onMeatSelected(MeatType.PORK) }
                MeatButton("🍗", "Poultry", probeState.selectedMeatType.value == MeatType.POULTRY) { onMeatSelected(MeatType.POULTRY) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE), shape = RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = probeState.isWrapped.value,
                    onCheckedChange = { probeState.isWrapped.value = it },
                    colors = CheckboxDefaults.colors(checkedColor = Combustion_Red)
                )
                Text("Wrapped / Insulated (Foil)", style = MaterialTheme.typography.body2, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun MeatButton(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit) {
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