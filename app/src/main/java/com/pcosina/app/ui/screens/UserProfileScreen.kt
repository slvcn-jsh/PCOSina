package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var age by rememberSaveable { mutableStateOf("25") }
    var weight by rememberSaveable { mutableStateOf("65") }
    var height by rememberSaveable { mutableStateOf("160") }

    val activityOptions = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active")
    var activityExpanded by rememberSaveable { mutableStateOf(false) }
    var activityLevel by rememberSaveable { mutableStateOf(activityOptions[1]) }

    val insulinOptions = listOf("None", "Mild", "Moderate", "Severe")
    var insulinExpanded by rememberSaveable { mutableStateOf(false) }
    var insulinLevel by rememberSaveable { mutableStateOf(insulinOptions[1]) }

    // Symptoms
    var symptomIrregularPeriods by rememberSaveable { mutableStateOf(false) }
    var symptomWeightGain by rememberSaveable { mutableStateOf(false) }
    var symptomAcne by rememberSaveable { mutableStateOf(false) }
    var symptomHairLoss by rememberSaveable { mutableStateOf(false) }

    // Comorbidities
    var comorbDiabetes by rememberSaveable { mutableStateOf(false) }
    var comorbPrediabetes by rememberSaveable { mutableStateOf(false) }
    var comorbHypertension by rememberSaveable { mutableStateOf(false) }
    var comorbNone by rememberSaveable { mutableStateOf(false) }

    // Dietary restrictions
    var lacto by rememberSaveable { mutableStateOf(false) }
    var vegetarian by rememberSaveable { mutableStateOf(false) }
    var pescatarian by rememberSaveable { mutableStateOf(false) }
    var noPork by rememberSaveable { mutableStateOf(false) }
    var noBeef by rememberSaveable { mutableStateOf(false) }

    var budget by rememberSaveable { mutableStateOf("2000") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientHeader(
            title = "Create Your Profile",
            subtitle = "Help us personalize your meal plan",
            containerHeight = 180,
        )

        // Personal Information
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Activity Level
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Activity Level",
                    style = MaterialTheme.typography.titleMedium,
                )
                ExposedDropdownMenuBox(
                    expanded = activityExpanded,
                    onExpandedChange = { activityExpanded = !activityExpanded },
                ) {
                    OutlinedTextField(
                        value = activityLevel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Activity Level") },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = activityExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = activityExpanded,
                        onDismissRequest = { activityExpanded = false },
                    ) {
                        activityOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    activityLevel = option
                                    activityExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // PCOS Details
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "PCOS Details",
                    style = MaterialTheme.typography.titleMedium,
                )

                ExposedDropdownMenuBox(
                    expanded = insulinExpanded,
                    onExpandedChange = { insulinExpanded = !insulinExpanded },
                ) {
                    OutlinedTextField(
                        value = insulinLevel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Insulin Resistance Level") },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = insulinExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = insulinExpanded,
                        onDismissRequest = { insulinExpanded = false },
                    ) {
                        insulinOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    insulinLevel = option
                                    insulinExpanded = false
                                },
                            )
                        }
                    }
                }

                Text(
                    text = "Symptoms",
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CheckboxRow("Irregular periods", symptomIrregularPeriods) { symptomIrregularPeriods = it }
                    CheckboxRow("Weight gain", symptomWeightGain) { symptomWeightGain = it }
                    CheckboxRow("Acne", symptomAcne) { symptomAcne = it }
                    CheckboxRow("Hair loss", symptomHairLoss) { symptomHairLoss = it }
                }

                Text(
                    text = "Comorbidities",
                    style = MaterialTheme.typography.labelLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CheckboxRow("Diabetes", comorbDiabetes) { comorbDiabetes = it }
                    CheckboxRow("Prediabetes", comorbPrediabetes) { comorbPrediabetes = it }
                    CheckboxRow("Hypertension", comorbHypertension) { comorbHypertension = it }
                    CheckboxRow("None", comorbNone) { comorbNone = it }
                }
            }
        }

        // Dietary Restrictions
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Dietary Restrictions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CheckboxRow("Lactose Intolerant", lacto) { lacto = it }
                    CheckboxRow("Vegetarian", vegetarian) { vegetarian = it }
                    CheckboxRow("Pescatarian", pescatarian) { pescatarian = it }
                    CheckboxRow("No Pork", noPork) { noPork = it }
                    CheckboxRow("No Beef", noBeef) { noBeef = it }
                }
            }
        }

        // Weekly Budget
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Weekly Budget",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Budget (PHP)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0ABF6A),
                                Color(0xFF2D9CDB),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save & Continue")
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
