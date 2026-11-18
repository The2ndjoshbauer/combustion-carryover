/*
 * Project: Combustion Inc. Android Example
 * File: Dialogs.kt
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun TemperatureSelectionDialog(
    title: String,
    buttonText: String,
    onButtonClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    unitsString: String,
    initialValue: Int,
    minValue: Int,
    maxValue: Int
) {
    val safeMin = minOf(minValue, maxValue)
    val safeMax = maxOf(minValue, maxValue)
    var selectedNumber by remember(initialValue, safeMin, safeMax) {
        mutableStateOf(initialValue.coerceIn(safeMin, safeMax))
    }

    StandardDialog(
        title = title,
        buttonText = buttonText,
        onButtonClick = {
            onButtonClick(selectedNumber)
        },
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$selectedNumber° $unitsString",
                color = MaterialTheme.colors.onPrimary,
                style = MaterialTheme.typography.h5,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1.0f))
                NumberPicker(
                    initialValue = selectedNumber,
                    minValue = safeMin,
                    maxValue = safeMax,
                    onValueChange = {
                        selectedNumber = it.coerceIn(safeMin, safeMax)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colors.onPrimary,
                    style = MaterialTheme.typography.h6,
                    text = unitsString
                )
                Spacer(modifier = Modifier.weight(1.0f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(-5, -1, 1, 5).forEach { delta ->
                    OutlinedButton(
                        onClick = {
                            selectedNumber = (selectedNumber + delta).coerceIn(safeMin, safeMax)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSecondary),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text(
                            text = if (delta > 0) "+$delta°" else "$delta°",
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleSelectDialog(
    title: String,
    optionsList: List<String>,
    defaultSelected: Int,
    submitButtonText: String,
    onSubmitButtonClick: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(defaultSelected) }

    StandardDialog(
        title = title,
        buttonText = submitButtonText,
        onButtonClick = {
            onSubmitButtonClick(selectedOption)
        },
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(optionsList) {
                RadioButtonRow(
                    it,
                    optionsList[selectedOption]
                ) { selectedValue ->
                    selectedOption = optionsList.indexOf(selectedValue)
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    details: String,
    onYesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismiss() }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = title,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colors.onPrimary,
                    style = MaterialTheme.typography.subtitle1,
                    textAlign = TextAlign.Center
                )
                Divider(
                    modifier = Modifier
                        .padding(top = 8.dp)
                )
                Text(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 36.dp, bottom = 36.dp),
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    text = details
                )
                Row {
                    Button(
                        onClick = {
                            onYesClick()
                            onDismiss()
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSecondary),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colors.onPrimary,
                            disabledContentColor = MaterialTheme.colors.onSecondary
                        ),
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text(text = "Yes")
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Button(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, MaterialTheme.colors.onSecondary),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colors.onPrimary,
                            disabledContentColor = MaterialTheme.colors.onSecondary
                        ),
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text(text = "No")
                    }
                }
            }
        }
    }
}

@Composable
fun StandardDialog(
    title: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest.invoke() }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = title,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colors.onPrimary,
                    style = MaterialTheme.typography.subtitle1,
                    textAlign = TextAlign.Center
                )
                Divider(
                    modifier = Modifier
                        .padding(top = 8.dp)
                )
                content()
                DialogButton(
                    label = buttonText,
                    enabled = true,
                    onClick = {
                        onButtonClick()
                        onDismissRequest.invoke()
                    },
                )
            }
        }
    }
}

@Composable
fun DialogButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        Button(
            onClick = onClick,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSecondary),
            colors = ButtonDefaults.buttonColors(
                contentColor = MaterialTheme.colors.onPrimary,
                disabledContentColor = MaterialTheme.colors.onSecondary
            ),
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
        ) {
            Text(text = label)
        }
    }
}
