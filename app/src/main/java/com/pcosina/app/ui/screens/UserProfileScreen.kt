package com.pcosina.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.theme.PcosinaBlushBorder
import com.pcosina.app.ui.theme.PcosinaBlushStrong
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.profileConstraintConflictMessage
import com.pcosina.app.ui.util.primaryGoalLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ProfileMinAge = 18
private const val ProfileMaxAge = 60
private val ProfileOnboardingCoral = Color(0xFFEF6F7D)
private val ProfileOnboardingSilhouette = Color(0xFFFFC8CF)
private val ProfileFieldShape = RoundedCornerShape(4.dp)
private val CommonAllergyTokens = setOf(
    "dairy",
    "egg",
    "peanut",
    "nuts",
    "soy",
    "gluten",
    "fish",
    "shellfish"
)

private fun hasSavedProfileToken(values: List<String>, vararg aliases: String): Boolean {
    val normalizedAliases = aliases.map { it.lowercase(Locale.ENGLISH) }.toSet()
    return values.any { value -> value.trim().lowercase(Locale.ENGLISH) in normalizedAliases }
}

private fun parseDelimitedProfileItems(text: String): List<String> =
    text.split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ENGLISH) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun UserProfileScreen(
    userViewModel: UserViewModel,
    onNext: () -> Unit,
    isEditMode: Boolean = false,
    onEditGoals: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val isProfileLoading by userViewModel.isProfileLoading.collectAsState()
    var currentStep by rememberSaveable { mutableStateOf(1) }
    val profileSaveScope = rememberCoroutineScope()
    var profileSaveInProgress by rememberSaveable { mutableStateOf(false) }
    var profileActionMessage by rememberSaveable { mutableStateOf<String?>(null) }

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

    val savedSymptomKey = profile.symptoms.joinToString("|")
    var symptomIrregularPeriods by rememberSaveable(savedSymptomKey) {
        mutableStateOf(hasSavedProfileToken(profile.symptoms, "Irregular periods"))
    }
    var symptomWeightGain by rememberSaveable(savedSymptomKey) {
        mutableStateOf(hasSavedProfileToken(profile.symptoms, "Weight gain"))
    }
    var symptomAcne by rememberSaveable(savedSymptomKey) {
        mutableStateOf(hasSavedProfileToken(profile.symptoms, "Acne"))
    }
    var symptomHairLoss by rememberSaveable(savedSymptomKey) {
        mutableStateOf(hasSavedProfileToken(profile.symptoms, "Hair loss"))
    }

    val savedRestrictionKey = profile.dietaryRestrictions.joinToString("|")
    var lacto by rememberSaveable(savedRestrictionKey) {
        mutableStateOf(hasSavedProfileToken(profile.dietaryRestrictions, "Lactose Intolerant"))
    }
    var vegetarian by rememberSaveable(savedRestrictionKey) {
        mutableStateOf(hasSavedProfileToken(profile.dietaryRestrictions, "Vegetarian"))
    }
    var pescatarian by rememberSaveable(savedRestrictionKey) {
        mutableStateOf(hasSavedProfileToken(profile.dietaryRestrictions, "Pescatarian"))
    }
    var noPork by rememberSaveable(savedRestrictionKey) {
        mutableStateOf(hasSavedProfileToken(profile.dietaryRestrictions, "No Pork", "Exclude Pork"))
    }
    var noBeef by rememberSaveable(savedRestrictionKey) {
        mutableStateOf(hasSavedProfileToken(profile.dietaryRestrictions, "No Beef", "Exclude Beef"))
    }
    var budget by rememberSaveable {
        mutableStateOf(if (profile.weeklyBudgetPhp > 0) profile.weeklyBudgetPhp.toString() else "")
    }
    var allergiesText by rememberSaveable { mutableStateOf(profile.allergies.joinToString(", ")) }
    var maxCookingTime by rememberSaveable { mutableStateOf(profile.maxCookingTimeMinutes.toString()) }
    var varietyPref by rememberSaveable { mutableStateOf(profile.varietyPreference) }
    var planningPriority by rememberSaveable { mutableStateOf(profile.planningPriority) }

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val inputMode = imeVisible
    val profileContentBottomPadding = if (imeVisible) 220.dp else 8.dp
    val profileScrollState = rememberScrollState()
    val clearProfileTextFocus = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            Unit
        }
    }
    LaunchedEffect(currentStep) {
        clearProfileTextFocus()
        profileScrollState.scrollTo(0)
    }

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
    val stepThreeConflict = profileConstraintConflictMessage(
        vegetarian = vegetarian,
        pescatarian = pescatarian,
        planningPriority = planningPriority,
        varietyPreference = varietyPref,
        allergiesText = allergiesText,
        budgetPhp = budgetValue,
    )
    val stepOneValid = (isEditMode || displayName.isNotBlank()) &&
        ageValue != null && ageValue in ProfileMinAge..ProfileMaxAge &&
        weightValueKg != null && weightValueKg in 35..180 &&
        heightValueCm != null && heightValueCm in 120..200

    val stepTwoValid = true

    val stepThreeValid = (budgetValue == null || budgetValue in 1..20000) &&
        maxCookingValue != null && maxCookingValue in 10..240 &&
        stepThreeConflict == null

    val canProceed = !isProfileLoading && when (currentStep) {
        1 -> stepOneValid
        2 -> stepTwoValid
        3 -> stepThreeValid
        else -> false
    }
    val stepOneBlockerMessage = when {
        isProfileLoading -> "Loading profile. Please wait..."
        !isEditMode && displayName.isBlank() -> "Add a display name to continue."
        age.isBlank() || ageValue == null -> "Enter age as a whole number."
        ageValue?.let { it !in ProfileMinAge..ProfileMaxAge } == true -> "Age must stay between 18 and 60."
        weight.isBlank() || weightInputValue == null -> "Enter weight as a whole number."
        weightValueKg == null || (weightValueKg !in 35..180) -> "Weight must stay between 35 and 180 kg equivalent."
        heightValueCm == null -> "Enter a valid height before continuing."
        heightValueCm?.let { it !in 120..200 } == true -> "Height must stay between 120 and 200 cm."
        activityLevel.isBlank() -> "Choose your typical activity level."
        else -> "Fix highlighted fields to continue."
    }
    val stepTwoBlockerMessage = "Symptoms are optional. Continue if none apply."
    val stepThreeBlockerMessage = when {
        stepThreeConflict != null -> stepThreeConflict
        maxCookingTime.isBlank() || maxCookingValue == null -> "Enter max cooking time in minutes."
        maxCookingValue?.let { it !in 10..240 } == true -> "Max cooking time must stay between 10 and 240 minutes."
        else -> "Set max cooking time (10–240). Budget is optional unless Budget First is selected."
    }
    val currentStepBlockerMessage = when (currentStep) {
        1 -> stepOneBlockerMessage
        2 -> stepTwoBlockerMessage
        3 -> stepThreeBlockerMessage
        else -> "Fix highlighted fields to continue."
    }
    val primaryActionLabel = when (currentStep) {
        1 -> "Continue"
        2 -> "Continue"
        3 -> if (isEditMode) "Save profile" else "Complete profile"
        else -> "Next"
    }
    fun persistStepData(step: Int, markComplete: Boolean) {
        if (displayName.isNotBlank()) {
            userViewModel.updateProfileName(displayName)
        }
        val safeAge = age.toIntOrNull()?.coerceIn(ProfileMinAge, ProfileMaxAge)
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
        if (step >= 2) {
            val symptoms = mutableListOf<String>()
            if (symptomIrregularPeriods) symptoms.add("Irregular periods")
            if (symptomWeightGain) symptoms.add("Weight gain")
            if (symptomAcne) symptoms.add("Acne")
            if (symptomHairLoss) symptoms.add("Hair loss")
            userViewModel.updatePcosDetails(symptoms, emptyList())
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
            val allergyItems = parseDelimitedProfileItems(allergiesText)
                .filter { it.lowercase(Locale.ENGLISH) in CommonAllergyTokens }
            userViewModel.updateAllergies(allergyItems)
        }
        userViewModel.setProfileCompleted(markComplete || isEditMode)
    }
    fun handleStepAction() {
        if (!canProceed) {
            profileActionMessage = currentStepBlockerMessage
            return
        }
        profileActionMessage = null
        if (currentStep < 3) {
            persistStepData(currentStep, markComplete = false)
            profileActionMessage = "Saved this step on this device."
            currentStep++
        } else {
            profileSaveInProgress = true
            profileActionMessage = "Saving profile on this device..."
            persistStepData(currentStep, markComplete = true)
            profileSaveScope.launch {
                delay(450)
                profileSaveInProgress = false
                profileActionMessage = "Profile saved on this device. Sync will retry when online."
                onNext()
            }
        }
    }

    val showProfileShell = !inputMode

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!inputMode) {
                ProfileHeader(
                    title = if (isEditMode) "PROFILE SETTINGS" else "PROFILE ONBOARDING",
                    subtitle = if (isEditMode) {
                        "Update the details PCOSina uses for meals, groceries, and reminders."
                    } else {
                        "Let's start with a few details about you to personalize your experience."
                    },
                    onBack = {
                        if (currentStep > 1) {
                            persistStepData(currentStep, markComplete = false)
                            currentStep--
                        } else {
                            onBack?.invoke()
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!inputMode) {
                BottomActionRow(
                    currentStep = currentStep,
                    primaryLabel = if (profileSaveInProgress) "Saving..." else primaryActionLabel,
                    primaryColor = colorScheme.primary,
                    isNextEnabled = canProceed && !profileSaveInProgress,
                    compact = false,
                    disabledReason = if (!canProceed) currentStepBlockerMessage else null,
                    statusMessage = profileActionMessage,
                    isSaving = profileSaveInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    onNext = ::handleStepAction
                )
            }
        },
        containerColor = if (showProfileShell) ProfileOnboardingCoral else PcosinaSurface
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .background(if (showProfileShell) ProfileOnboardingCoral else PcosinaSurface),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = if (showProfileShell) Color.White else PcosinaSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(profileScrollState)
                            .padding(horizontal = if (showProfileShell) 28.dp else 16.dp, vertical = if (showProfileShell) 14.dp else 10.dp)
                            .padding(bottom = profileContentBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!inputMode) {
                            ProfileStepProgressPanel(
                                currentStep = currentStep,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_status_center_card")
                            )
                        }
                        if (!inputMode && isEditMode && onEditGoals != null) {
                            EditProfileGoalEntryCard(
                                currentGoal = primaryGoalLabel(profile.goal),
                                onClick = {
                                    persistStepData(currentStep, markComplete = false)
                                    onEditGoals()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ProfileSectionCard(
                            modifier = Modifier.fillMaxWidth(),
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
                                    showName = true,
                                )
                                2 -> StepTwoMedical(
                                    s1 = symptomIrregularPeriods,
                                    onS1 = { symptomIrregularPeriods = it },
                                    s2 = symptomWeightGain,
                                    onS2 = { symptomWeightGain = it },
                                    s3 = symptomAcne,
                                    onS3 = { symptomAcne = it },
                                    s4 = symptomHairLoss,
                                    onS4 = { symptomHairLoss = it },
                                    color = colorScheme.primary
                                )
                                3 -> StepThreeDiet(
                                    r1 = lacto,
                                    onR1 = { lacto = it },
                                    r2 = vegetarian,
                                    onR2 = { vegetarian = it },
                                    r3 = pescatarian,
                                    onR3 = { pescatarian = it },
                                    r4 = noPork,
                                    onR4 = { noPork = it },
                                    r5 = noBeef,
                                    onR5 = { noBeef = it },
                                    budget = budget,
                                    onBudget = { budget = sanitizeBudgetInput(it) },
                                    maxCookingTime = maxCookingTime,
                                    onMaxCookingTime = { maxCookingTime = it },
                                    varietyPreference = varietyPref,
                                    onVarietyPreference = { varietyPref = it },
                                    planningPriority = planningPriority,
                                    onPlanningPriority = { planningPriority = it },
                                    allergiesText = allergiesText,
                                    onAllergiesText = { allergiesText = it },
                                    color = colorScheme.primary
                                )
                            }
                        }

                        if (!canProceed && !inputMode) {
                            Text(
                                text = when (currentStep) {
                                    1 -> stepOneBlockerMessage
                                    2 -> stepTwoBlockerMessage
                                    3 -> stepThreeBlockerMessage
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (inputMode) {
                            BottomActionRow(
                                currentStep = currentStep,
                                primaryLabel = if (profileSaveInProgress) "Saving..." else primaryActionLabel,
                                primaryColor = colorScheme.primary,
                                isNextEnabled = canProceed && !profileSaveInProgress,
                                compact = true,
                                disabledReason = if (!canProceed) currentStepBlockerMessage else null,
                                statusMessage = profileActionMessage,
                                isSaving = profileSaveInProgress,
                                modifier = Modifier.fillMaxWidth(),
                                onNext = ::handleStepAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(194.dp)
            .background(ProfileOnboardingCoral)
            .statusBarsPadding()
            .padding(start = 28.dp, top = 14.dp, end = 24.dp, bottom = 17.dp)
    ) {
        ProfileHeaderSilhouette(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = 0.dp)
                .width(126.dp)
                .height(132.dp)
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ProfileOnboardingCoral
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(end = 70.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 0.sp
                ),
                color = Color.White.copy(alpha = 0.94f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileHeaderSilhouette(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(ProfileOnboardingSilhouette.copy(alpha = 0.16f))
        )
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = ProfileOnboardingSilhouette.copy(alpha = 0.48f),
            modifier = Modifier.size(82.dp)
        )
    }
}

@Composable
private fun EditProfileGoalEntryCard(
    currentGoal: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, PcosinaBlushBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Meal planning goal",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                Text(
                    text = currentGoal.ifBlank { "Choose the goal that should guide your plan." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(999.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Reselect")
            }
        }
    }
}

@Composable
private fun ProfileStepProgressPanel(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    OnboardingProgress(
        currentStep = currentStep,
        color = ProfileOnboardingCoral,
        modifier = modifier,
    )
}

@Composable
private fun ProfileSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun OnboardingProgress(
    currentStep: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val step = i + 1
                Box(contentAlignment = Alignment.Center) {
                    if (step == currentStep) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.18f)),
                        )
                    }
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = if (step <= currentStep) color else Color.White,
                        border = BorderStroke(1.5.dp, if (step <= currentStep) color else colorScheme.outlineVariant),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = step.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (step <= currentStep) Color.White else colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (i < 2) {
                    Box(
                    modifier = Modifier
                        .weight(1f)
                            .height(1.dp)
                            .background(if (step < currentStep) color else colorScheme.outlineVariant),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Personal Details", "Symptoms", "Preferences").forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (index + 1 == currentStep) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (index + 1 <= currentStep) color else colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BottomActionRow(
    currentStep: Int,
    primaryLabel: String,
    primaryColor: Color,
    isNextEnabled: Boolean,
    compact: Boolean = false,
    disabledReason: String? = null,
    statusMessage: String? = null,
    isSaving: Boolean = false,
    onBack: () -> Unit = {},
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(0.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
        ) {
            if (!isNextEnabled && !disabledReason.isNullOrBlank()) {
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(if (currentStep < 3) "profile_next_step_cta" else "profile_complete_cta"),
                enabled = isNextEnabled,
            ) {
                Text(primaryLabel, fontWeight = FontWeight.SemiBold)
            }
            if (!compact) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = when {
                            isSaving -> "Saving your progress on this device..."
                            !statusMessage.isNullOrBlank() -> statusMessage
                            else -> "Your progress is saved automatically on this device."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.keepFocusedProfileFieldVisible(
    onFocusChange: (Boolean) -> Unit = {},
): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var bringIntoViewJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            bringIntoViewJob?.cancel()
        }
    }
    return bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { focusState ->
            onFocusChange(focusState.isFocused)
            bringIntoViewJob?.cancel()
            if (focusState.isFocused) {
                bringIntoViewJob = scope.launch {
                    delay(120)
                    bringIntoViewRequester.bringIntoView()
                    delay(220)
                    bringIntoViewRequester.bringIntoView()
                    delay(320)
                    bringIntoViewRequester.bringIntoView()
                }
            } else {
                bringIntoViewJob = null
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
    onTextInputFocusChange: (Boolean) -> Unit = {},
    color: Color,
    showName: Boolean,
) {
    val ageValue = age.toIntOrNull()
    val weightValue = weight.toIntOrNull()
    val weightKg = weightValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val heightCmValue = if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
        val feet = heightFt.toIntOrNull()
        val inches = heightIn.toIntOrNull()
        if (feet != null && inches != null && inches in 0..11) {
            UnitConverter.feetInchesToCm(feet, inches)
        } else {
            null
        }
    } else {
        heightCm.toIntOrNull()
    }
    val ageError = age.isNotBlank() && (ageValue == null || ageValue !in ProfileMinAge..ProfileMaxAge)
    val weightError = weight.isNotBlank() && (weightKg == null || weightKg !in 35..180)
    val heightHasInput = if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
        heightFt.isNotBlank() || heightIn.isNotBlank()
    } else {
        heightCm.isNotBlank()
    }
    val heightError = heightHasInput && (heightCmValue == null || heightCmValue !in 120..200)
    val activityOptions = listOf(
        "Sedentary" to "Sedentary",
        "Lightly Active (1-3 days per week)" to "Lightly Active",
        "Moderately Active (3-5 days per week)" to "Moderately Active",
        "Very Active (6-7 days per week)" to "Very Active",
    )

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (showName) {
            RequiredProfileLabel("Display Name")
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                placeholder = { Text("Enter your display name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                    .testTag("profile_step1_name_input"),
                shape = ProfileFieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
            )
        }

        RequiredProfileLabel("Age")
        OutlinedTextField(
            value = age,
            onValueChange = onAge,
            placeholder = { Text("Enter your age") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                .testTag("profile_step1_age_input"),
            shape = ProfileFieldShape,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = ageError,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
        )
        ProfileRangeHint(if (ageError) "Age must be 18-60." else "18-60 years old", ageError)

        RequiredProfileLabel("Weight")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                placeholder = { Text("Enter your weight") },
                modifier = Modifier
                    .weight(1f)
                    .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                    .testTag("profile_step1_weight_input"),
                shape = ProfileFieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weightError,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
            )
            ProfileUnitDropdown(
                value = weightUnit,
                options = listOf("kg" to UnitConverter.WEIGHT_KG, "lb" to UnitConverter.WEIGHT_LB),
                onSelected = onWeightUnit,
                color = color,
                modifier = Modifier.width(90.dp),
            )
        }
        ProfileRangeHint(if (weightError) "Weight must be 35-180 kg equivalent." else "35-180 kg", weightError)

        RequiredProfileLabel("Height")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
                OutlinedTextField(
                    value = heightFt,
                    onValueChange = onHeightFt,
                    placeholder = { Text("ft") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_ft_input"),
                    shape = ProfileFieldShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightError,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
                )
                OutlinedTextField(
                    value = heightIn,
                    onValueChange = onHeightIn,
                    placeholder = { Text("in") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_in_input"),
                    shape = ProfileFieldShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightError,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
                )
            } else {
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = onHeightCm,
                    placeholder = { Text("Enter your height") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_cm_input"),
                    shape = ProfileFieldShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightError,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
                )
            }
            ProfileUnitDropdown(
                value = heightUnit,
                options = listOf("cm" to UnitConverter.HEIGHT_CM, "ft/in" to UnitConverter.HEIGHT_FT_IN),
                onSelected = onHeightUnit,
                color = color,
                modifier = Modifier.width(90.dp),
            )
        }
        ProfileRangeHint(if (heightError) "Height must be 120-200 cm equivalent." else "120-200 cm", heightError)

        RequiredProfileLabel("Activity Level")
        ProfileUnitDropdown(
            value = activity,
            options = activityOptions,
            onSelected = onActivity,
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        ProfileRangeHint("Used to estimate your daily energy needs.", false)
    }
}

@Composable
private fun RequiredProfileLabel(text: String, required: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        if (required) {
            Text(text = "*", color = ProfileOnboardingCoral, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun ProfileRangeHint(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileUnitDropdown(
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = options.firstOrNull { it.second == value }?.first ?: value
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ProfileFieldShape,
            singleLine = true,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, storedValue) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(storedValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun StepTwoMedical(s1: Boolean, onS1: (Boolean) -> Unit, s2: Boolean, onS2: (Boolean) -> Unit, s3: Boolean, onS3: (Boolean) -> Unit, s4: Boolean, onS4: (Boolean) -> Unit, color: Color) {
    val symptomOptions = listOf(
        "Irregular periods" to (s1 to onS1),
        "Weight gain" to (s2 to onS2),
        "Acne" to (s3 to onS3),
        "Hair loss" to (s4 to onS4)
    )

    val noneSelected = symptomOptions.none { it.second.first }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Which symptoms do you experience?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select all that apply, or choose ‘None of the above.’",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            symptomOptions.forEach { (label, state) ->
                val selected = state.first
                val toggle = state.second
                OnboardingChoiceRow(
                    label = label,
                    selected = selected,
                    onClick = { toggle(!selected) },
                    color = color
                )
            }
            OnboardingChoiceRow(
                label = "None of the above",
                selected = noneSelected,
                onClick = {
                    onS1(false)
                    onS2(false)
                    onS3(false)
                    onS4(false)
                },
                color = color,
            )
        }
    }
}

@Composable
private fun OnboardingChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = if (selected) color.copy(alpha = 0.16f) else Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = if (selected) color else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outline)
            ) {
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OnboardingTokenChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    labelMaxWidth: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            stateDescription = if (selected) "Selected" else "Not selected"
        },
        shape = RoundedCornerShape(4.dp),
        color = if (selected) color.copy(alpha = 0.16f) else Color.White,
        border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .heightIn(min = UiChipTokens.MinTouchHeight)
                .widthIn(max = labelMaxWidth)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    allergiesText: String,
    onAllergiesText: (String) -> Unit,
    onTextInputFocusChange: (Boolean) -> Unit = {},
    color: Color,
) {
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val screenWidthDp = with(LocalDensity.current) { windowWidthPx.toDp().value.roundToInt() }
    val restrictionLabelWidth = UiChipTokens.widthByClass(
        screenWidthDp = screenWidthDp,
        compact = 132.dp,
        medium = 164.dp,
    )
    val allergyLabelWidth = UiChipTokens.widthByClass(
        screenWidthDp = screenWidthDp,
        compact = 106.dp,
        medium = 142.dp,
    )
    val restrictions = listOf(
        "Lactose Intolerant" to (r1 to onR1),
        "Vegetarian" to (r2 to onR2),
        "Pescatarian" to (r3 to onR3),
        "No Pork" to (r4 to onR4),
        "No Beef" to (r5 to onR5),
    )
    val allergens = listOf(
        "Dairy" to "dairy",
        "Eggs" to "egg",
        "Peanuts" to "peanut",
        "Tree Nuts" to "nuts",
        "Soy" to "soy",
        "Gluten/Wheat" to "gluten",
        "Fish" to "fish",
        "Shellfish" to "shellfish",
    )
    val selectedAllergens = parseDelimitedProfileItems(allergiesText)
        .map { it.lowercase(Locale.ENGLISH) }
        .filter { it in CommonAllergyTokens }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Preferences & Budget",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select all that apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Dietary Restrictions")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            restrictions.forEach { (label, state) ->
                OnboardingTokenChip(
                    selected = state.first,
                    text = label,
                    onClick = { state.second(!state.first) },
                    labelMaxWidth = restrictionLabelWidth,
                    color = color,
                )
            }
        }
        if (r2 && r3) {
            Text(
                text = "Choose Vegetarian or Pescatarian, not both.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SectionTitle("Allergies")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            allergens.forEach { (label, token) ->
                val selected = token in selectedAllergens
                OnboardingTokenChip(
                    selected = selected,
                    text = label,
                    onClick = {
                        val updated = if (selected) {
                            selectedAllergens.filterNot { it == token }
                        } else {
                            selectedAllergens + token
                        }
                        onAllergiesText(updated.distinct().joinToString(", "))
                    },
                    labelMaxWidth = allergyLabelWidth,
                    color = color,
                )
            }
        }

        SectionTitle("Weekly Planning")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val budgetRequired = planningPriority == "Budget First"
                RequiredProfileLabel(
                    text = if (budgetRequired) "Budget" else "Budget (Optional)",
                    required = budgetRequired,
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = onBudget,
                    placeholder = { Text("₱") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange),
                    shape = ProfileFieldShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = budgetRequired && budget.isBlank(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RequiredProfileLabel("Max Cooking Time")
                OutlinedTextField(
                    value = maxCookingTime,
                    onValueChange = onMaxCookingTime,
                    placeholder = { Text("e.g., 45") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange),
                    shape = ProfileFieldShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = maxCookingTime.toIntOrNull()?.let { it !in 10..240 } == true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color),
                )
                ProfileRangeHint("minutes", false)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RequiredProfileLabel("Variety Preference")
                ProfileUnitDropdown(
                    value = varietyPreference,
                    options = listOf(
                        "Low Variety" to "Low",
                        "Balanced" to "Balanced",
                        "High Variety" to "High",
                    ),
                    onSelected = onVarietyPreference,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RequiredProfileLabel("Meal Plan Priority")
                ProfileUnitDropdown(
                    value = planningPriority,
                    options = listOf(
                        "Budget First" to "Budget First",
                        "Balanced" to "Balanced",
                        "Variety First" to "Variety First",
                        "Nutrition Tight" to "Nutrition Tight",
                    ),
                    onSelected = onPlanningPriority,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = CircleShape,
            color = ProfileOnboardingCoral,
            contentColor = Color.White
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (checked) "Selected" else "Not selected" },
        shape = MaterialTheme.shapes.large,
        color = if (checked) {
            accentColor.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (checked) {
                accentColor.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = accentColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (checked) {
                        accentColor.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
                    },
                    contentColor = if (checked) {
                        accentColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = if (checked) "Included in your planning profile" else "Tap to include",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
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
