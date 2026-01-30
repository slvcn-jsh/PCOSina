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
import androidx.compose.runtime.collectAsState
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
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userViewModel: UserViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()

    var age by rememberSaveable { mutableStateOf(profile.age.toString()) }
    var weight by rememberSaveable { mutableStateOf(profile.weightKg.toString()) }
    var height by rememberSaveable { mutableStateOf(profile.heightCm.toString()) }

    val activityOptions = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active")
    var activityExpanded by rememberSaveable { mutableStateOf(false) }
    var activityLevel by rememberSaveable { mutableStateOf(profile.activityLevel) }

    val insulinOptions = listOf("None", "Mild", "Moderate", "Severe")
    var insulinExpanded by rememberSaveable { mutableStateOf(false) }
    var insulinLevel by rememberSaveable { mutableStateOf(profile.insulinResistanceLevel) }

    // Symptoms
    var symptomIrregularPeriods by rememberSaveable { mutableStateOf(profile.symptoms.contains("Irregular periods")) }
    var symptomWeightGain by rememberSaveable { mutableStateOf(profile.symptoms.contains("Weight gain")) }
    var symptomAcne by rememberSaveable { mutableStateOf(profile.symptoms.contains("Acne")) }
    var symptomHairLoss by rememberSaveable { mutableStateOf(profile.symptoms.contains("Hair loss")) }

    // Comorbidities
    var comorbDiabetes by rememberSaveable { mutableStateOf(profile.comorbidities.contains("Diabetes")) }
    var comorbPrediabetes by rememberSaveable { mutableStateOf(profile.comorbidities.contains("Prediabetes")) }
    var comorbHypertension by rememberSaveable { mutableStateOf(profile.comorbidities.contains("Hypertension")) }
    var comorbNone by rememberSaveable { mutableStateOf(profile.comorbidities.isEmpty()) }

    // Dietary restrictions
    var lacto by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Lactose Intolerant")) }
    var vegetarian by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Vegetarian")) }
    var pescatarian by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Pescatarian")) }
    var noPork by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("No Pork")) }
    var noBeef by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("No Beef")) }

    var budget by rememberSaveable { mutableStateOf(profile.weeklyBudgetPhp.toString()) }

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
            onClick = {
                // Save to ViewModel
                userViewModel.updatePersonalDetails(
                    age = age.toIntOrNull() ?: 25,
                    weight = weight.toIntOrNull() ?: 65,
                    height = height.toIntOrNull() ?: 160,
                    activity = activityLevel
                )
                
                val symptoms = mutableListOf<String>()
                if (symptomIrregularPeriods) symptoms.add("Irregular periods")
                if (symptomWeightGain) symptoms.add("Weight gain")
                if (symptomAcne) symptoms.add("Acne")
                if (symptomHairLoss) symptoms.add("Hair loss")
                
                val comorbidities = mutableListOf<String>()
                if (comorbDiabetes) comorbidities.add("Diabetes")
                if (comorbPrediabetes) comorbidities.add("Prediabetes")
                if (comorbHypertension) comorbidities.add("Hypertension")
                
                userViewModel.updatePcosDetails(insulinLevel, symptoms, comorbidities)
                
                val restrictions = mutableListOf<String>()
                if (lacto) restrictions.add("Lactose Intolerant")
                if (vegetarian) restrictions.add("Vegetarian")
                if (pescatarian) restrictions.add("Pescatarian")
                if (noPork) restrictions.add("No Pork")
                if (noBeef) restrictions.add("No Beef")
                
                userViewModel.updateDietaryRestrictions(restrictions)
                userViewModel.updateBudget(budget.toIntOrNull() ?: 2000)
                
                onNext()
            },
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
