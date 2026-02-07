package com.pcosina.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.domain.UnitConverter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userViewModel: UserViewModel,
    onNext: () -> Unit,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val isProfileLoading by userViewModel.isProfileLoading.collectAsState()
    var currentStep by rememberSaveable { mutableStateOf(1) }

    // State Persistence with safe defaults
    var displayName by rememberSaveable { mutableStateOf(profile.displayName) }
    var age by rememberSaveable { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var weightUnit by rememberSaveable { mutableStateOf(profile.weightUnit) }
    var heightUnit by rememberSaveable { mutableStateOf(profile.heightUnit) }
    var lastWeightUnit by rememberSaveable { mutableStateOf(profile.weightUnit) }
    var lastHeightUnit by rememberSaveable { mutableStateOf(profile.heightUnit) }
    var weight by rememberSaveable {
        val value = if (profile.weightKg > 0) {
            if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.kgToLb(profile.weightKg).toString()
            else profile.weightKg.toString()
        } else ""
        mutableStateOf(value)
    }
    var heightCmInput by rememberSaveable { mutableStateOf(if (profile.heightCm > 0) profile.heightCm.toString() else "") }
    var heightFtInput by rememberSaveable { mutableStateOf("") }
    var heightInInput by rememberSaveable { mutableStateOf("") }
    var activityLevel by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var insulinLevel by rememberSaveable { mutableStateOf("None") } // Default to a safe value

    var symptomIrregularPeriods by rememberSaveable { mutableStateOf(false) }
    var symptomWeightGain by rememberSaveable { mutableStateOf(false) }
    var symptomAcne by rememberSaveable { mutableStateOf(false) }
    var symptomHairLoss by rememberSaveable { mutableStateOf(false) }

    var lacto by rememberSaveable { mutableStateOf(false) }
    var vegetarian by rememberSaveable { mutableStateOf(false) }
    var pescatarian by rememberSaveable { mutableStateOf(false) }
    var noPork by rememberSaveable { mutableStateOf(false) }
    var noBeef by rememberSaveable { mutableStateOf(false) }
    var budget by rememberSaveable { mutableStateOf("2000") }
    var pantryText by rememberSaveable { mutableStateOf(profile.pantryItems.joinToString(", ")) }

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(profile) {
        if (displayName.isBlank() && profile.displayName.isNotBlank()) {
            displayName = profile.displayName
        }
        if (age.isBlank() && profile.age > 0) {
            age = profile.age.toString()
        }
        if (weight.isBlank() && profile.weightKg > 0) {
            weight = if (weightUnit == UnitConverter.WEIGHT_LB) {
                UnitConverter.kgToLb(profile.weightKg).toString()
            } else {
                profile.weightKg.toString()
            }
        }
        if (heightCmInput.isBlank() && profile.heightCm > 0) {
            heightCmInput = profile.heightCm.toString()
        }
        if (profile.heightCm > 0 && (heightFtInput.isBlank() && heightInInput.isBlank())) {
            val (ft, inch) = UnitConverter.cmToFeetInches(profile.heightCm)
            heightFtInput = if (ft > 0) ft.toString() else ""
            heightInInput = if (inch > 0) inch.toString() else "0"
        }
        if (activityLevel.isBlank()) {
            activityLevel = profile.activityLevel
        }
        if (pantryText.isBlank() && profile.pantryItems.isNotEmpty()) {
            pantryText = profile.pantryItems.joinToString(", ")
        }
    }

    LaunchedEffect(weightUnit) {
        if (lastWeightUnit != weightUnit) {
            val currentKg = if (lastWeightUnit == UnitConverter.WEIGHT_LB) {
                weight.toIntOrNull()?.let { UnitConverter.lbToKg(it) }
            } else {
                weight.toIntOrNull()
            }
            weight = if (weightUnit == UnitConverter.WEIGHT_LB) {
                currentKg?.let { UnitConverter.kgToLb(it).toString() } ?: ""
            } else {
                currentKg?.toString() ?: ""
            }
            lastWeightUnit = weightUnit
        }
    }

    LaunchedEffect(heightUnit) {
        if (lastHeightUnit != heightUnit) {
            if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
                val cm = heightCmInput.toIntOrNull() ?: 0
                val (ft, inch) = UnitConverter.cmToFeetInches(cm)
                heightFtInput = if (ft > 0) ft.toString() else ""
                heightInInput = inch.toString()
            } else {
                val ft = heightFtInput.toIntOrNull() ?: 0
                val inch = heightInInput.toIntOrNull() ?: 0
                heightCmInput = UnitConverter.feetInchesToCm(ft, inch).toString()
            }
            lastHeightUnit = heightUnit
        }
    }

    val ageValue = age.toIntOrNull()
    val weightInputValue = weight.toIntOrNull()
    val weightValueKg = weightInputValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val heightValueCm = if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
        val ft = heightFtInput.toIntOrNull()
        val inch = heightInInput.toIntOrNull()
        if (ft != null && inch != null) UnitConverter.feetInchesToCm(ft, inch) else null
    } else {
        heightCmInput.toIntOrNull()
    }
    val budgetValue = budget.toIntOrNull()

    val stepOneValid = (isEditMode || displayName.isNotBlank()) &&
        ageValue != null && ageValue in 13..60 &&
        weightValueKg != null && weightValueKg in 35..180 &&
        heightValueCm != null && heightValueCm in 120..200

    val stepTwoValid = insulinLevel.isNotBlank()

    val stepThreeValid = budgetValue != null && budgetValue in 0..20000

    val canProceed = !isProfileLoading && when (currentStep) {
        1 -> stepOneValid
        2 -> stepTwoValid
        3 -> stepThreeValid
        else -> false
    }

    fun persistStepData(step: Int, markComplete: Boolean) {
        if (!isEditMode && displayName.isNotBlank()) {
            userViewModel.updateProfileName(displayName)
        }
        val safeAge = age.toIntOrNull()?.coerceIn(13, 60)
        val safeWeight = weightValueKg?.coerceIn(35, 180)
        val safeHeight = heightValueCm?.coerceIn(120, 200)
        userViewModel.updateUnitPreferences(heightUnit, weightUnit)
        if (safeAge != null && safeWeight != null && safeHeight != null) {
            userViewModel.updatePersonalDetails(
                age = safeAge,
                weight = safeWeight,
                height = safeHeight,
                activity = activityLevel
            )
        }
        if (step >= 2 && insulinLevel.isNotBlank()) {
            val symptoms = mutableListOf<String>()
            if (symptomIrregularPeriods) symptoms.add("Irregular periods")
            if (symptomWeightGain) symptoms.add("Weight gain")
            if (symptomAcne) symptoms.add("Acne")
            if (symptomHairLoss) symptoms.add("Hair loss")
            userViewModel.updatePcosDetails(insulinLevel, symptoms, emptyList())
        }
        if (step >= 3) {
            val restrictions = mutableListOf<String>()
            if (lacto) restrictions.add("Lactose Intolerant")
            if (vegetarian) restrictions.add("Vegetarian")
            if (pescatarian) restrictions.add("Pescatarian")
            if (noPork) restrictions.add("No Pork")
            if (noBeef) restrictions.add("No Beef")
            userViewModel.updateDietaryRestrictions(restrictions)
            val budgetSafe = budgetValue?.coerceIn(0, 20000)
            if (budgetSafe != null) {
                userViewModel.updateBudget(budgetSafe)
            }
            val pantryItems = pantryText.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            userViewModel.updatePantryItems(pantryItems)
        }
        userViewModel.setProfileCompleted(markComplete || isEditMode)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(colorScheme.background)) {
                GradientHeader(
                    title = if (isEditMode) "Update Health Data" else "Profile Setup",
                    subtitle = "Step $currentStep of 3",
                    containerHeight = 140
                )
                OnboardingProgress(currentStep, colorScheme.primary)
            }
        },
        bottomBar = {
            BottomActionRow(
                currentStep = currentStep,
                primaryColor = colorScheme.primary,
                isNextEnabled = canProceed,
                onBack = {
                    if (currentStep > 1) {
                        persistStepData(currentStep, markComplete = false)
                        currentStep--
                    }
                },
                onNext = {
                    if (!canProceed) return@BottomActionRow
                    if (currentStep < 3) {
                        persistStepData(currentStep, markComplete = false)
                        currentStep++
                    } else {
                        persistStepData(currentStep, markComplete = true)
                        onNext()
                    }
                }
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "stepAnimation"
                ) { step ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        when (step) {
                            1 -> StepOneIdentity(
                                name = displayName,
                                onName = { displayName = it },
                                age = age,
                                onAge = { age = it },
                                weight = weight,
                                onWeight = { weight = it },
                                weightUnit = weightUnit,
                                onWeightUnit = { weightUnit = it },
                                heightUnit = heightUnit,
                                onHeightUnit = { heightUnit = it },
                                heightCm = heightCmInput,
                                onHeightCm = { heightCmInput = it },
                                heightFt = heightFtInput,
                                onHeightFt = { heightFtInput = it },
                                heightIn = heightInInput,
                                onHeightIn = { heightInInput = it },
                                activity = activityLevel,
                                onActivity = { activityLevel = it },
                                color = colorScheme.primary,
                                showName = !isEditMode
                            )
                            2 -> StepTwoMedical(insulinLevel, {insulinLevel=it}, symptomIrregularPeriods, {symptomIrregularPeriods=it}, symptomWeightGain, {symptomWeightGain=it}, symptomAcne, {symptomAcne=it}, symptomHairLoss, {symptomHairLoss=it}, colorScheme.primary)
                            3 -> StepThreeDiet(
                                lacto, {lacto=it},
                                vegetarian, {vegetarian=it},
                                pescatarian, {pescatarian=it},
                                noPork, {noPork=it},
                                noBeef, {noBeef=it},
                                budget, {budget=it},
                                pantryText, {pantryText=it},
                                colorScheme.primary
                            )
                        }

                        if (!canProceed) {
                            Text(
                                text = when (currentStep) {
                                    1 -> if (isProfileLoading) "Loading profile. Please wait..." else "Please complete all required fields with valid values."
                                    2 -> "Please select your insulin resistance level."
                                    3 -> "Please enter a valid weekly budget."
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
}

@Composable
fun OnboardingProgress(currentStep: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) { i ->
            val step = i + 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (step <= currentStep) color else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
fun BottomActionRow(
    currentStep: Int,
    primaryColor: Color,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 1) {
            TextButton(onClick = onBack) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        Button(
            onClick = onNext,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            modifier = Modifier.height(52.dp).width(140.dp),
            enabled = isNextEnabled
        ) {
            Text(if (currentStep < 3) "Next" else "Complete", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepOneIdentity(
    name: String,
    onName: (String) -> Unit,
    age: String,
    onAge: (String) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    weightUnit: String,
    onWeightUnit: (String) -> Unit,
    heightUnit: String,
    onHeightUnit: (String) -> Unit,
    heightCm: String,
    onHeightCm: (String) -> Unit,
    heightFt: String,
    onHeightFt: (String) -> Unit,
    heightIn: String,
    onHeightIn: (String) -> Unit,
    activity: String,
    onActivity: (String) -> Unit,
    color: Color,
    showName: Boolean
) {
    val options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active")
    var expanded by remember { mutableStateOf(false) }
    val ageValue = age.toIntOrNull()
    val weightValue = weight.toIntOrNull()
    val weightKg = weightValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val heightCmValue = if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
        val ft = heightFt.toIntOrNull()
        val inch = heightIn.toIntOrNull()
        if (ft != null && inch != null) UnitConverter.feetInchesToCm(ft, inch) else null
    } else {
        heightCm.toIntOrNull()
    }
    val ageOutOfRange = ageValue != null && (ageValue < 13 || ageValue > 60)
    val weightOutOfRange = weightKg != null && (weightKg < 35 || weightKg > 180)
    val heightOutOfRange = heightCmValue != null && (heightCmValue < 120 || heightCmValue > 200)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Personal Details")
        if (showName) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Display name") },
                supportingText = { Text("Shown on your dashboard and plan.") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = onAge,
                label = { Text("Age (years)") },
                supportingText = { Text("Used to estimate calorie needs (13–60).") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                label = { Text(if (weightUnit == UnitConverter.WEIGHT_LB) "Weight (lb)" else "Weight (kg)") },
                supportingText = { Text("Used for nutrition targets.") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = weightUnit == UnitConverter.WEIGHT_KG,
                onClick = { onWeightUnit(UnitConverter.WEIGHT_KG) },
                label = { Text("kg") }
            )
            FilterChip(
                selected = weightUnit == UnitConverter.WEIGHT_LB,
                onClick = { onWeightUnit(UnitConverter.WEIGHT_LB) },
                label = { Text("lb") }
            )
        }
        if (ageOutOfRange || weightOutOfRange) {
            Text(
                text = "Tip: keep age 13–60 and weight 35–180 for accurate targets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heightFt,
                    onValueChange = onHeightFt,
                    label = { Text("Height (ft)") },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
                OutlinedTextField(
                    value = heightIn,
                    onValueChange = onHeightIn,
                    label = { Text("Height (in)") },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
            }
        } else {
            OutlinedTextField(
                value = heightCm,
                onValueChange = onHeightCm,
                label = { Text("Height (cm)") },
                supportingText = { Text("Used to estimate calorie needs (120–200).") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = heightUnit == UnitConverter.HEIGHT_CM,
                onClick = { onHeightUnit(UnitConverter.HEIGHT_CM) },
                label = { Text("cm") }
            )
            FilterChip(
                selected = heightUnit == UnitConverter.HEIGHT_FT_IN,
                onClick = { onHeightUnit(UnitConverter.HEIGHT_FT_IN) },
                label = { Text("ft/in") }
            )
        }
        if (heightOutOfRange) {
            Text(
                text = "Tip: keep height 120–200 cm for accurate targets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = activity, onValueChange = {}, readOnly = true, label = { Text("Activity Level") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, shape = MaterialTheme.shapes.medium, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onActivity(opt); expanded = false }) }
            }
        }
        Text(
            text = "Choose the closest match for a typical week.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepTwoMedical(insulin: String, onInsulin: (String) -> Unit, s1: Boolean, onS1: (Boolean) -> Unit, s2: Boolean, onS2: (Boolean) -> Unit, s3: Boolean, onS3: (Boolean) -> Unit, s4: Boolean, onS4: (Boolean) -> Unit, color: Color) {
    val options = listOf("None", "Mild", "Moderate", "Severe")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Medical Profile")
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = insulin, onValueChange = {}, readOnly = true, label = { Text("Insulin Resistance") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, shape = MaterialTheme.shapes.medium, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onInsulin(opt); expanded = false }) }
            }
        }
        Text("Select active symptoms (optional):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Helps tailor recommendations. Leave blank if unsure.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CheckboxRow("Irregular periods", s1, color, onS1)
        CheckboxRow("Weight gain", s2, color, onS2)
        CheckboxRow("Acne", s3, color, onS3)
        CheckboxRow("Hair loss", s4, color, onS4)
    }
}

@Composable
fun StepThreeDiet(
    r1: Boolean,
    onR1: (Boolean) -> Unit,
    r2: Boolean,
    onR2: (Boolean) -> Unit,
    r3: Boolean,
    onR3: (Boolean) -> Unit,
    r4: Boolean,
    onR4: (Boolean) -> Unit,
    r5: Boolean,
    onR5: (Boolean) -> Unit,
    budget: String,
    onBudget: (String) -> Unit,
    pantryText: String,
    onPantryText: (String) -> Unit,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Preferences & Budget")
        Text(
            text = "Select only what applies to you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CheckboxRow("Lactose Intolerant", r1, color, onR1)
        CheckboxRow("Vegetarian", r2, color, onR2)
        CheckboxRow("Pescatarian", r3, color, onR3)
        CheckboxRow("Exclude Pork", r4, color, onR4)
        CheckboxRow("Exclude Beef", r5, color, onR5)

        OutlinedTextField(
            value = budget,
            onValueChange = onBudget,
            label = { Text("Weekly Budget (PHP)") },
            supportingText = { Text("Optional. Used for ingredient suggestions.") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            prefix = { Text("₱ ") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )

        OutlinedTextField(
            value = pantryText,
            onValueChange = onPantryText,
            label = { Text("Pantry items (comma-separated)") },
            supportingText = { Text("Example: eggs, oats, tuna") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickableNoRipple { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = accentColor))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.composed {
        this.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        )
    }
