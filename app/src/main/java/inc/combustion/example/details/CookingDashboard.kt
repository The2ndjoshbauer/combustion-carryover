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

@Composable
fun CookingDashboard(
    probeState: ProbeState,
    onMeatSelected: (MeatType) -> Unit
) {
    // 1. Calculate the Zone (V3.5 Logic)
    val predictedString = probeState.suggestedPullTemp.value
    var zoneDisplay = "---"
    
    // Only calculate if we have a real number (not "---")
    if (predictedString != "---") {
        try {
            val centerVal = predictedString.toDouble()
            // Create a 3-degree window (e.g., 129 - 131)
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

            // MEAT SELECTOR BUTTONS
            Text("Material Physics:", style = MaterialTheme.typography.body2, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))