package com.pcosina.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import kotlin.math.roundToInt
import java.util.Locale

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
    var budget by rememberSaveable {
        mutableStateOf(if (profile.weeklyBudgetPhp > 0) profile.weeklyBudgetPhp.toString() else "")
    }
    var pantryText by rememberSaveable { mutableStateOf(profile.pantryItems.joinToString(", ")) }
    var allergiesText by rememberSaveable { mutableStateOf(profile.allergies.joinToString(", ")) }
    var maxCookingTime by rememberSaveable { mutableStateOf(profile.maxCookingTimeMinutes.toString()) }
    var varietyPref by rememberSaveable { mutableStateOf(profile.varietyPreference) }
    var planningPriority by rememberSaveable { mutableStateOf(profile.planningPriority) }

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
        if (insulinLevel == "None" && profile.insulinResistanceLevel.isNotBlank()) {
            insulinLevel = profile.insulinResistanceLevel
        }
        if (pantryText.isBlank() && profile.pantryItems.isNotEmpty()) {
            pantryText = profile.pantryItems.joinToString(", ")
        }
        if (allergiesText.isBlank() && profile.allergies.isNotEmpty()) {
            allergiesText = profile.allergies.joinToString(", ")
        }
        if (budget.isBlank() && profile.weeklyBudgetPhp > 0) {
            budget = profile.weeklyBudgetPhp.toString()
        }
        if (varietyPref.isBlank()) {
            varietyPref = profile.varietyPreference
        }
        if (planningPriority.isBlank()) {
            planningPriority = profile.planningPriority
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
    val budgetValue = parseBudgetInput(budget)
    val maxCookingValue = maxCookingTime.toIntOrNull()

    val stepOneValid = (isEditMode || displayName.isNotBlank()) &&
        ageValue != null && ageValue in 13..60 &&
        weightValueKg != null && weightValueKg in 35..180 &&
        heightValueCm != null && heightValueCm in 120..200

    val stepTwoValid = insulinLevel.isNotBlank()

    val stepThreeValid = (budgetValue == null || budgetValue in 1..20000) &&
        maxCookingValue != null && maxCookingValue in 10..240

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
            val budgetSafe = budgetValue?.coerceIn(1, 20000)
            if (budgetSafe != null) userViewModel.updateBudget(budgetSafe) else userViewModel.updateBudget(0)
            val maxCookSafe = maxCookingValue?.coerceIn(10, 240) ?: 45
            userViewModel.updateCookingPreferences(maxCookSafe, varietyPref.ifBlank { "Balanced" })
            userViewModel.updatePlanningPriority(planningPriority.ifBlank { "Balanced" })
            val pantryItems = pantryText.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            userViewModel.updatePantryItems(pantryItems)
            val allergyItems = allergiesText.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            userViewModel.updateAllergies(allergyItems)
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
                        slideInHorizontally(animationSpec = tween(UiMotionTokens.ProfileStepSlideMs)) { it } +
                            fadeIn(animationSpec = tween(UiMotionTokens.ProfileStepFadeMs)) togetherWith
                            slideOutHorizontally(animationSpec = tween(UiMotionTokens.ProfileStepSlideMs)) { -it } +
                            fadeOut(animationSpec = tween(UiMotionTokens.ProfileStepFadeMs))
                    } else {
                        slideInHorizontally(animationSpec = tween(UiMotionTokens.ProfileStepSlideMs)) { -it } +
                            fadeIn(animationSpec = tween(UiMotionTokens.ProfileStepFadeMs)) togetherWith
                            slideOutHorizontally(animationSpec = tween(UiMotionTokens.ProfileStepSlideMs)) { it } +
                            fadeOut(animationSpec = tween(UiMotionTokens.ProfileStepFadeMs))
                    }
                },
                label = "stepAnimation"
                ) { step ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
                    ) {
                        if (step == 1 && !isEditMode) {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth().testTag("profile_first_win_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "First win in ~60–90 seconds",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Finish this profile -> Select goals -> Generate your first weekly plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                                budget, { budget = sanitizeBudgetInput(it) },
                                maxCookingTime, { maxCookingTime = it },
                                varietyPref, { varietyPref = it },
                                planningPriority, { planningPriority = it },
                                pantryText, {pantryText=it},
                                allergiesText, { allergiesText = it },
                                colorScheme.primary
                            )
                        }

                        if (!canProceed) {
                            Text(
                                text = when (currentStep) {
                                    1 -> if (isProfileLoading) "Loading profile. Please wait..." else "Fix highlighted fields to continue."
                                    2 -> "Please select your insulin resistance level."
                                    3 -> "Set max cooking time (10–240). Budget is optional."
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = UiSpacingTokens.CardContentGap),
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
        modifier = Modifier.fillMaxWidth().padding(UiSpacingTokens.CardContentPadding),
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
            modifier = Modifier
                .height(52.dp)
                .width(140.dp)
                .testTag(if (currentStep < 3) "profile_next_step_cta" else "profile_complete_cta"),
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
    val ageInvalidFormat = age.isNotBlank() && ageValue == null
    val weightInvalidFormat = weight.isNotBlank() && weightValue == null
    val heightCmValue = if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
        val ft = heightFt.toIntOrNull()
        val inch = heightIn.toIntOrNull()
        if (ft != null && inch != null) UnitConverter.feetInchesToCm(ft, inch) else null
    } else {
        heightCm.toIntOrNull()
    }
    val heightCmInvalidFormat = heightUnit == UnitConverter.HEIGHT_CM &&
        heightCm.isNotBlank() &&
        heightCmValue == null
    val heightFtInvalidFormat = heightUnit == UnitConverter.HEIGHT_FT_IN &&
        heightFt.isNotBlank() &&
        heightFt.toIntOrNull() == null
    val heightInInvalidFormat = heightUnit == UnitConverter.HEIGHT_FT_IN &&
        heightIn.isNotBlank() &&
        heightIn.toIntOrNull() == null
    val heightInOutOfRange = heightUnit == UnitConverter.HEIGHT_FT_IN &&
        (heightIn.toIntOrNull()?.let { it !in 0..11 } == true)
    val ageOutOfRange = ageValue != null && (ageValue < 13 || ageValue > 60)
    val weightOutOfRange = weightKg != null && (weightKg < 35 || weightKg > 180)
    val heightOutOfRange = heightCmValue != null && (heightCmValue < 120 || heightCmValue > 200)

    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        SectionTitle("Personal Details")
        Text(
            text = "Required to personalize your meal targets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showName) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth().testTag("profile_step1_name_input"),
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
                modifier = Modifier.weight(1f).testTag("profile_step1_age_input"),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = ageInvalidFormat || ageOutOfRange,
                supportingText = {
                    val helper = when {
                        ageInvalidFormat -> "Enter a whole number."
                        ageOutOfRange -> "Age must be 13–60."
                        else -> "Required: 13–60."
                    }
                    Text(helper)
                },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                label = { Text(if (weightUnit == UnitConverter.WEIGHT_LB) "Weight (lb)" else "Weight (kg)") },
                modifier = Modifier.weight(1f).testTag("profile_step1_weight_input"),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weightInvalidFormat || weightOutOfRange,
                supportingText = {
                    val helper = when {
                        weightInvalidFormat -> "Enter a whole number."
                        weightOutOfRange -> "Allowed range: 35–180 kg equivalent."
                        else -> "Required: 35–180 kg equivalent."
                    }
                    Text(helper)
                },
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
        Text(
            text = "Valid ranges: age 13–60 • weight 35–180 kg • height 120–200 cm.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
                    modifier = Modifier.weight(1f).testTag("profile_step1_height_ft_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightFtInvalidFormat || heightOutOfRange,
                    supportingText = {
                        val helper = when {
                            heightFtInvalidFormat -> "Enter feet as a whole number."
                            heightOutOfRange -> "Total height must stay 120–200 cm."
                            else -> "Example: 5"
                        }
                        Text(helper)
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
                OutlinedTextField(
                    value = heightIn,
                    onValueChange = onHeightIn,
                    label = { Text("Height (in)") },
                    modifier = Modifier.weight(1f).testTag("profile_step1_height_in_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightInInvalidFormat || heightInOutOfRange || heightOutOfRange,
                    supportingText = {
                        val helper = when {
                            heightInInvalidFormat -> "Enter inches as a whole number."
                            heightInOutOfRange -> "Inches must be 0–11."
                            heightOutOfRange -> "Total height must stay 120–200 cm."
                            else -> "Range: 0–11"
                        }
                        Text(helper)
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
            }
        } else {
            OutlinedTextField(
                value = heightCm,
                onValueChange = onHeightCm,
                label = { Text("Height (cm)") },
                modifier = Modifier.fillMaxWidth().testTag("profile_step1_height_cm_input"),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = heightCmInvalidFormat || heightOutOfRange,
                supportingText = {
                    val helper = when {
                        heightCmInvalidFormat -> "Enter height in centimeters."
                        heightOutOfRange -> "Height must be 120–200 cm."
                        else -> "Required: 120–200 cm."
                    }
                    Text(helper)
                },
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
                text = "Tip: keep height between 120 and 200 cm.",
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
        val activityHint = when (activity) {
            "Sedentary" -> "Little to no exercise; mostly seated work."
            "Lightly Active" -> "Light activity 1–3 days/week."
            "Moderately Active" -> "Moderate activity 3–5 days/week."
            "Very Active" -> "Hard exercise 6–7 days/week."
            else -> "Choose the closest match for a typical week."
        }
        Text(
            text = activityHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepTwoMedical(insulin: String, onInsulin: (String) -> Unit, s1: Boolean, onS1: (Boolean) -> Unit, s2: Boolean, onS2: (Boolean) -> Unit, s3: Boolean, onS3: (Boolean) -> Unit, s4: Boolean, onS4: (Boolean) -> Unit, color: Color) {
    val options = listOf("None", "Mild", "Moderate", "Severe")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        SectionTitle("Medical Profile")
        Text(
            text = "Select what applies today. You can update this anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = insulin, onValueChange = {}, readOnly = true, label = { Text("Insulin Resistance") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, shape = MaterialTheme.shapes.medium, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onInsulin(opt); expanded = false }) }
            }
        }
        Text("Symptoms (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CheckboxRow("Irregular periods", s1, color, onS1)
        CheckboxRow("Weight gain", s2, color, onS2)
        CheckboxRow("Acne", s3, color, onS3)
        CheckboxRow("Hair loss", s4, color, onS4)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    maxCookingTime: String,
    onMaxCookingTime: (String) -> Unit,
    varietyPreference: String,
    onVarietyPreference: (String) -> Unit,
    planningPriority: String,
    onPlanningPriority: (String) -> Unit,
    pantryText: String,
    onPantryText: (String) -> Unit,
    allergiesText: String,
    onAllergiesText: (String) -> Unit,
    color: Color
) {
    val varietyOptions = listOf("Low", "Balanced", "High")
    var varietyExpanded by remember { mutableStateOf(false) }
    val priorityOptions = listOf("Budget First", "Balanced", "Variety First", "Nutrition Tight")
    val commonAllergens = listOf(
        "Dairy" to "dairy",
        "Eggs" to "egg",
        "Peanuts" to "peanut",
        "Tree Nuts" to "nuts",
        "Soy" to "soy",
        "Gluten/Wheat" to "gluten",
        "Fish" to "fish",
        "Shellfish" to "shellfish"
    )
    val allergyTokens = allergiesText.split(",")
        .map { it.trim().lowercase(Locale.getDefault()) }
        .filter { it.isNotBlank() }
        .toMutableList()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val priorityChipMaxWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 104.dp, medium = 136.dp)
    val allergyChipMaxWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 92.dp, medium = 124.dp)
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        SectionTitle("Preferences & Budget")
        Text(
            text = "Optional preferences to make plans easier to follow.",
            style = MaterialTheme.typography.labelMedium,
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
            label = { Text("Weekly Budget (optional)") },
            supportingText = { Text("Optional. Used for weekly cost estimate.") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            prefix = { Text("₱ ") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )

        OutlinedTextField(
            value = maxCookingTime,
            onValueChange = onMaxCookingTime,
            label = { Text("Max Cooking Time (minutes)") },
            supportingText = { Text("Required: 10–240") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )

        ExposedDropdownMenuBox(
            expanded = varietyExpanded,
            onExpandedChange = { varietyExpanded = !varietyExpanded }
        ) {
            OutlinedTextField(
                value = varietyPreference,
                onValueChange = {},
                readOnly = true,
                label = { Text("Variety Preference") },
                supportingText = { Text("Controls repeat limits and diversity.") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = varietyExpanded) },
                shape = MaterialTheme.shapes.medium,
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color)
            )
            ExposedDropdownMenu(expanded = varietyExpanded, onDismissRequest = { varietyExpanded = false }) {
                varietyOptions.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onVarietyPreference(opt)
                            varietyExpanded = false
                        }
                    )
                }
            }
        }

        Text(
            text = "Planning Priority",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Pick what to favor when trade-offs happen.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            priorityOptions.forEach { opt ->
                FilterChip(
                    selected = planningPriority == opt,
                    onClick = { onPlanningPriority(opt) },
                    modifier = Modifier.heightIn(min = UiChipTokens.MinTouchHeight),
                    label = {
                        Text(
                            text = opt,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = priorityChipMaxWidth)
                        )
                    }
                )
            }
        }

        Text(
            text = "Allergies",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonAllergens.forEach { (label, token) ->
                val selected = allergyTokens.contains(token)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            allergyTokens.filterNot { it == token }
                        } else {
                            allergyTokens + token
                        }
                        onAllergiesText(updated.distinct().joinToString(", "))
                    },
                    modifier = Modifier.heightIn(min = UiChipTokens.MinTouchHeight),
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = allergyChipMaxWidth)
                        )
                    }
                )
            }
        }
        OutlinedTextField(
            value = pantryText,
            onValueChange = onPantryText,
            label = { Text("Pantry items (optional)") },
            supportingText = { Text("Example: eggs, oats, tuna.") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )

        OutlinedTextField(
            value = allergiesText,
            onValueChange = onAllergiesText,
            label = { Text("Allergies (comma-separated)") },
            supportingText = { Text("Example: peanuts, dairy, shellfish.") },
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

private fun sanitizeBudgetInput(text: String): String {
    return text.filter { it.isDigit() || it == ',' || it == '.' }
}

private fun parseBudgetInput(text: String): Int? {
    val cleaned = text.replace(",", "").trim()
    if (cleaned.isBlank()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (value <= 0) return null
    return value.roundToInt().coerceAtMost(20000)
}
