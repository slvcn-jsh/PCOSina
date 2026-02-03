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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userViewModel: UserViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    var currentStep by rememberSaveable { mutableStateOf(1) }

    // State Persistence with safe defaults
    var displayName by rememberSaveable { mutableStateOf(profile.displayName) }
    var age by rememberSaveable { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var weight by rememberSaveable { mutableStateOf(if (profile.weightKg > 0) profile.weightKg.toString() else "") }
    var height by rememberSaveable { mutableStateOf(if (profile.heightCm > 0) profile.heightCm.toString() else "") }
    var activityLevel by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var insulinLevel by rememberSaveable { mutableStateOf(profile.insulinResistanceLevel) }
    
    var symptomIrregularPeriods by rememberSaveable { mutableStateOf(profile.symptoms.contains("Irregular periods")) }
    var symptomWeightGain by rememberSaveable { mutableStateOf(profile.symptoms.contains("Weight gain")) }
    var symptomAcne by rememberSaveable { mutableStateOf(profile.symptoms.contains("Acne")) }
    var symptomHairLoss by rememberSaveable { mutableStateOf(profile.symptoms.contains("Hair loss")) }
    
    var lacto by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Lactose Intolerant")) }
    var vegetarian by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Vegetarian") || profile.dietaryRestrictions.contains("Vegetarian Only")) }
    var pescatarian by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("Pescatarian")) }
    var noPork by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("No Pork")) }
    var noBeef by rememberSaveable { mutableStateOf(profile.dietaryRestrictions.contains("No Beef")) }
    var budget by rememberSaveable { mutableStateOf(if (profile.weeklyBudgetPhp > 0) profile.weeklyBudgetPhp.toString() else "2000") }

    val primaryColor = Color(0xFFFC6B7D)
    val secondaryColor = Color(0xFF8C3A45)

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color(0xFFFFF9F9))) {
                GradientHeader(
                    title = "Profile Setup",
                    subtitle = "Step $currentStep of 3",
                    containerHeight = 140
                )
                OnboardingProgress(currentStep, primaryColor)
            }
        },
        bottomBar = {
            BottomActionRow(
                currentStep = currentStep,
                primaryColor = primaryColor,
                onBack = { if (currentStep > 1) currentStep-- },
                onNext = {
                    if (currentStep < 3) {
                        currentStep++
                    } else {
                        // FINAL SAVE TO VIEWMODEL
                        userViewModel.updateProfileName(displayName)
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
                        userViewModel.updatePcosDetails(insulinLevel, symptoms, emptyList())
                        
                        val restrictions = mutableListOf<String>()
                        if (lacto) restrictions.add("Lactose Intolerant")
                        if (vegetarian) restrictions.add("Vegetarian")
                        if (pescatarian) restrictions.add("Pescatarian")
                        if (noPork) restrictions.add("No Pork")
                        if (noBeef) restrictions.add("No Beef")
                        userViewModel.updateDietaryRestrictions(restrictions)
                        userViewModel.updateBudget(budget.toIntOrNull() ?: 2000)
                        
                        onNext()
                    }
                }
            )
        },
        containerColor = Color(0xFFFFF9F9)
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
                        1 -> StepOneIdentity(displayName, {displayName=it}, age, {age=it}, weight, {weight=it}, height, {height=it}, activityLevel, {activityLevel=it}, primaryColor)
                        2 -> StepTwoMedical(insulinLevel, {insulinLevel=it}, symptomIrregularPeriods, {symptomIrregularPeriods=it}, symptomWeightGain, {symptomWeightGain=it}, symptomAcne, {symptomAcne=it}, symptomHairLoss, {symptomHairLoss=it}, primaryColor)
                        3 -> StepThreeDiet(lacto, {lacto=it}, vegetarian, {vegetarian=it}, pescatarian, {pescatarian=it}, noPork, {noPork=it}, noBeef, {noBeef=it}, budget, {budget=it}, primaryColor)
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
                    .background(if (step <= currentStep) color else Color.LightGray.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
fun BottomActionRow(currentStep: Int, primaryColor: Color, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 1) {
            TextButton(onClick = onBack) {
                Text("Back", color = Color.Gray)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        
        Button(
            onClick = onNext,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            modifier = Modifier.height(52.dp).width(140.dp)
        ) {
            Text(if (currentStep < 3) "Next" else "Complete", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepOneIdentity(name: String, onName: (String) -> Unit, age: String, onAge: (String) -> Unit, weight: String, onWeight: (String) -> Unit, height: String, onHeight: (String) -> Unit, activity: String, onActivity: (String) -> Unit, color: Color) {
    val options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Personal Details")
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = onAge,
                label = { Text("Age (years)") },
                supportingText = { Text("Used to estimate calorie needs.") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                label = { Text("Weight (kg)") },
                supportingText = { Text("Used for nutrition targets.") },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
        }
        OutlinedTextField(
            value = height,
            onValueChange = onHeight,
            label = { Text("Height (cm)") },
            supportingText = { Text("Used to estimate calorie needs.") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
        
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = activity, onValueChange = {}, readOnly = true, label = { Text("Activity Level") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, shape = MaterialTheme.shapes.medium, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onActivity(opt); expanded = false }) }
            }
        }
        Text(
            text = "Choose the closest match for a typical week.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
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
        Text("Select active symptoms (optional):", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
        Text(
            text = "Helps tailor recommendations. Leave blank if unsure.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        CheckboxRow("Irregular periods", s1, color, onS1)
        CheckboxRow("Weight gain", s2, color, onS2)
        CheckboxRow("Acne", s3, color, onS3)
        CheckboxRow("Hair loss", s4, color, onS4)
    }
}

@Composable
fun StepThreeDiet(r1: Boolean, onR1: (Boolean) -> Unit, r2: Boolean, onR2: (Boolean) -> Unit, r3: Boolean, onR3: (Boolean) -> Unit, r4: Boolean, onR4: (Boolean) -> Unit, r5: Boolean, onR5: (Boolean) -> Unit, budget: String, onBudget: (String) -> Unit, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Preferences & Budget")
        Text(
            text = "Select only what applies to you.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
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
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = Color(0xFF8C3A45))
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickableNoRipple { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = accentColor))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (checked) Color.Black else Color.DarkGray)
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
