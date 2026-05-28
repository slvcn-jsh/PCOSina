package com.pcosina.app.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.pcosina.app.ui.components.TokenizedFilterChip
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
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val ProfileMinAge = 18
private const val ProfileMaxAge = 60
private val ProfileOnboardingCoral = Color(0xFFEF6F7D)
private val ProfileOnboardingSilhouette = Color(0xFFFFC8CF)

private fun hasSavedProfileToken(values: List<String>, vararg aliases: String): Boolean {
    val normalizedAliases = aliases.map { it.lowercase(Locale.ENGLISH) }.toSet()
    return values.any { value -> value.trim().lowercase(Locale.ENGLISH) in normalizedAliases }
}

private fun parseDelimitedProfileItems(text: String): List<String> =
    text.split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ENGLISH) }

private fun Modifier.clearProfileFocusOnUserDrag(onDragStart: () -> Unit): Modifier =
    pointerInput(onDragStart) {
        awaitPointerEventScope {
            var gestureActive = false
            var clearedForGesture = false
            var accumulatedDrag = Offset.Zero

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedChanges = event.changes.filter { it.pressed }

                if (pressedChanges.isEmpty()) {
                    gestureActive = false
                    clearedForGesture = false
                    accumulatedDrag = Offset.Zero
                    continue
                }

                if (!gestureActive) {
                    gestureActive = true
                    clearedForGesture = false
                    accumulatedDrag = Offset.Zero
                }

                accumulatedDrag += pressedChanges.fold(Offset.Zero) { total, change ->
                    total + change.positionChange()
                }

                if (!clearedForGesture && kotlin.math.abs(accumulatedDrag.y) > viewConfiguration.touchSlop) {
                    onDragStart()
                    clearedForGesture = true
                }
            }
        }
    }

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
    var targetWeight by rememberSaveable {
        val value = profile.targetWeightKg?.let {
            if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.kgToLb(it).toString()
            else it.toString()
        }.orEmpty()
        mutableStateOf(value)
    }
    var targetDate by rememberSaveable { mutableStateOf(profile.targetDate.orEmpty()) }
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
    var pantryText by rememberSaveable { mutableStateOf(profile.pantryItems.joinToString(", ")) }
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
    val profileContentBottomPadding = if (imeVisible) 12.dp else 8.dp
    val profileScrollState = rememberScrollState()
    val clearProfileTextFocus = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            Unit
        }
    }
    val clearFocusOnProfileScroll = remember(clearProfileTextFocus) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 0.5f) {
                    clearProfileTextFocus()
                }
                return Offset.Zero
            }
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
        val savedTargetWeightKg = profile.targetWeightKg
        if (targetWeight.isBlank() && savedTargetWeightKg != null) {
            targetWeight = if (weightUnit == UnitConverter.WEIGHT_LB) {
                UnitConverter.kgToLb(savedTargetWeightKg).toString()
            } else {
                savedTargetWeightKg.toString()
            }
        }
        if (targetDate.isBlank() && !profile.targetDate.isNullOrBlank()) {
            targetDate = profile.targetDate.orEmpty()
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
            val currentTargetKg = if (lastWeightUnit == UnitConverter.WEIGHT_LB) {
                targetWeight.toIntOrNull()?.let { UnitConverter.lbToKg(it) }
            } else {
                targetWeight.toIntOrNull()
            }
            targetWeight = if (weightUnit == UnitConverter.WEIGHT_LB) {
                currentTargetKg?.let { UnitConverter.kgToLb(it).toString() } ?: ""
            } else {
                currentTargetKg?.toString() ?: ""
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
    val targetWeightInputValue = targetWeight.toIntOrNull()
    val targetWeightValueKg = targetWeightInputValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val targetDateText = targetDate.trim()
    val targetDateValue = targetDateText.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    val targetDateInvalidFormat = targetDateText.isNotBlank() && targetDateValue == null
    val targetDateInPast = targetDateValue?.isBefore(LocalDate.now()) == true
    val targetWeightInvalid = targetWeight.isNotBlank() && targetWeightInputValue == null
    val targetWeightOutOfRange = targetWeightValueKg != null && targetWeightValueKg !in 35..180
    val targetSupportValid = when {
        targetWeight.isBlank() && targetDateText.isBlank() -> true
        targetWeightInvalid || targetWeightOutOfRange -> false
        targetWeightValueKg == null -> false
        targetDateInvalidFormat || targetDateInPast -> false
        else -> true
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
        heightValueCm != null && heightValueCm in 120..200 &&
        targetSupportValid

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
    val currentStepLabel = when (currentStep) {
        1 -> "Personal details"
        2 -> "Symptoms"
        3 -> "Preferences & budget"
        else -> "Profile"
    }
    val stepOneBlockerMessage = when {
        isProfileLoading -> "Loading profile. Please wait..."
        !isEditMode && displayName.isBlank() -> "Add a display name to continue."
        age.isBlank() || ageValue == null -> "Enter age as a whole number."
        ageValue?.let { it !in ProfileMinAge..ProfileMaxAge } == true -> "Age must stay between 18 and 60."
        weight.isBlank() || weightInputValue == null -> "Enter weight as a whole number."
        weightValueKg == null || (weightValueKg !in 35..180) -> "Weight must stay between 35 and 180 kg equivalent."
        targetWeightInvalid -> "Enter target weight as a whole number or leave it blank."
        targetWeightOutOfRange -> "Target weight must stay between 35 and 180 kg equivalent."
        targetDateInvalidFormat -> "Use target date format YYYY-MM-DD or leave it blank."
        targetDateInPast -> "Target date must be today or later."
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
    val profileStatusSummary = when (currentStep) {
        1 -> if (stepOneValid) {
            "Personal details are ready. Nutrition targets can now be estimated accurately."
        } else {
            stepOneBlockerMessage
        }
        2 -> if (stepTwoValid) {
            "Wellness details are ready. Symptoms can guide deterministic planning nudges."
        } else {
            stepTwoBlockerMessage
        }
        3 -> if (stepThreeValid) {
            "Planning rules are ready. Hard constraints and soft preferences are set."
        } else {
            stepThreeBlockerMessage
        }
        else -> ""
    }
    val profileStorageSummary = if (isEditMode) {
        "Saved locally and reused for future plans, grocery guidance, and progress screens."
    } else {
        "Saved locally. Goals, weekly planning, and grocery setup unlock after this profile."
    }
    val primaryActionLabel = when (currentStep) {
        1 -> "Save identity"
        2 -> "Save wellness"
        3 -> if (isEditMode) "Save profile" else "Complete profile"
        else -> "Next"
    }
    val currentStepRequiredTotal = when (currentStep) {
        1 -> if (isEditMode) 5 else 6
        2 -> 1
        3 -> 3
        else -> 1
    }
    val currentStepReadyCount = when (currentStep) {
        1 -> listOf(
            isEditMode || displayName.isNotBlank(),
            ageValue != null && ageValue in ProfileMinAge..ProfileMaxAge,
            weightValueKg != null && weightValueKg in 35..180,
            heightValueCm != null && heightValueCm in 120..200,
            targetSupportValid,
            activityLevel.isNotBlank()
        ).count { it }
        2 -> 1
        3 -> listOf(
            maxCookingValue != null && maxCookingValue in 10..240,
            budget.isBlank() || budgetValue != null,
            stepThreeConflict == null
        ).count { it }
        else -> 0
    }.coerceAtMost(currentStepRequiredTotal)
    val currentStepProgress = currentStepReadyCount / currentStepRequiredTotal.toFloat()
    val currentStepProgressLabel = "$currentStepReadyCount of $currentStepRequiredTotal ready"

    fun persistStepData(step: Int, markComplete: Boolean) {
        if (!isEditMode && displayName.isNotBlank()) {
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
        val safeTargetWeight = targetWeightValueKg?.takeIf { it in 35..180 }
        val safeTargetDate = targetDateValue?.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val safeWeeklyWeightChangeGoal = if (safeWeight != null && safeTargetWeight != null && targetDateValue != null) {
            val weeks = ChronoUnit.DAYS.between(LocalDate.now(), targetDateValue).toFloat() / 7f
            if (weeks > 0f) (safeTargetWeight - safeWeight) / weeks else null
        } else {
            null
        }
        userViewModel.updateWeightSupport(
            targetWeightKg = safeTargetWeight,
            targetDate = safeTargetDate,
            weeklyWeightChangeGoalKg = safeWeeklyWeightChangeGoal
        )
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
            val pantryItems = parseDelimitedProfileItems(pantryText)
            userViewModel.updatePantryItems(pantryItems)
            val allergyItems = parseDelimitedProfileItems(allergiesText)
            userViewModel.updateAllergies(allergyItems)
        }
        userViewModel.setProfileCompleted(markComplete || isEditMode)
    }
    fun handleStepBack() {
        if (currentStep > 1) {
            persistStepData(currentStep, markComplete = false)
            currentStep--
        }
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
                    subtitle = "Share a bit about your journey and what you like to eat so every recipe can fit your week.",
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
                    onBack = ::handleStepBack,
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
                .clearProfileFocusOnUserDrag(clearProfileTextFocus)
                .nestedScroll(clearFocusOnProfileScroll)
                .background(if (showProfileShell) ProfileOnboardingCoral else PcosinaSurface),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = if (showProfileShell) {
                    RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
                } else {
                    RoundedCornerShape(0.dp)
                },
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
                            .padding(horizontal = 16.dp, vertical = if (showProfileShell) 22.dp else 10.dp)
                            .padding(bottom = profileContentBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!inputMode) {
                            ProfileStepProgressPanel(
                                currentStep = currentStep,
                                currentStepLabel = currentStepLabel,
                                progress = currentStepProgress,
                                progressLabel = currentStepProgressLabel,
                                statusSummary = profileStatusSummary,
                                storageSummary = profileStorageSummary,
                                isReady = canProceed,
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
                                    targetWeight = targetWeight,
                                    onTargetWeight = { targetWeight = it },
                                    targetDate = targetDate,
                                    onTargetDate = { targetDate = it },
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
                                    pantryText = pantryText,
                                    onPantryText = { pantryText = it },
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
                                onBack = ::handleStepBack,
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
            .height(214.dp)
            .background(ProfileOnboardingCoral)
            .statusBarsPadding()
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)
    ) {
        ProfileHeaderSilhouette(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = 8.dp)
                .width(130.dp)
                .height(150.dp)
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp)
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
                .padding(end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    letterSpacing = 0.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
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
                .size(118.dp)
                .clip(CircleShape)
                .background(ProfileOnboardingSilhouette.copy(alpha = 0.16f))
        )
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = ProfileOnboardingSilhouette.copy(alpha = 0.48f),
            modifier = Modifier.size(92.dp)
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
                    text = "Planning goal",
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
    currentStepLabel: String,
    progress: Float,
    progressLabel: String,
    statusSummary: String,
    storageSummary: String,
    isReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.primary.copy(alpha = if (isReady) 0.88f else 0.72f),
                contentColor = Color.White
            ) {
                Text(
                    text = "Step $currentStep of 3",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = currentStepLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = colorScheme.primary,
            trackColor = colorScheme.primary.copy(alpha = 0.14f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isReady) "Ready" else "In progress",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = statusSummary,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.82f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = storageSummary,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, PcosinaBlushBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun OnboardingProgress(currentStep: Int, color: Color) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.10f),
                contentColor = color
            ) {
                Text(
                    text = "Profile step $currentStep of 3",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Text(
                text = when (currentStep) {
                    1 -> "Personal"
                    2 -> "Symptoms"
                    else -> "Preferences"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurfaceVariant
            )
        }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) { i ->
                val step = i + 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (step <= currentStep) color else colorScheme.surfaceVariant)
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
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val defaultHelperCopy = when (currentStep) {
        1 -> "Start with your identity, height, weight, and activity so targets stay realistic."
        2 -> "Add symptoms and health markers that should influence your weekly plan."
        else -> "Finish the hard food rules, pantry, and budget settings before saving."
    }
    val helperCopy = when {
        isSaving -> "Saving profile on this device..."
        !isNextEnabled && !disabledReason.isNullOrBlank() -> disabledReason
        !statusMessage.isNullOrBlank() -> statusMessage
        else -> defaultHelperCopy
    }
    val helperColor = when {
        isSaving || !statusMessage.isNullOrBlank() -> primaryColor.copy(alpha = 0.82f)
        !isNextEnabled && !disabledReason.isNullOrBlank() -> colorScheme.error
        else -> PcosinaDeepRose.copy(alpha = 0.78f)
    }
    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        border = BorderStroke(
            width = 1.dp,
            color = PcosinaBlushBorder
        ),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 8.dp)
        ) {
            if (!compact || isSaving || !isNextEnabled || !statusMessage.isNullOrBlank()) {
                Text(
                    text = helperCopy,
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = helperColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        Text("Back", color = colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(92.dp))
                }

                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 132.dp)
                        .testTag(if (currentStep < 3) "profile_next_step_cta" else "profile_complete_cta"),
                    enabled = isNextEnabled
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
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
    targetWeight: String,
    onTargetWeight: (String) -> Unit,
    targetDate: String,
    onTargetDate: (String) -> Unit,
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
    showName: Boolean
) {
    val context = LocalContext.current
    val options = listOf("Sedentary", "Lightly Active", "Moderately Active", "Very Active")
    var expanded by remember { mutableStateOf(false) }
    val ageValue = age.toIntOrNull()
    val weightValue = weight.toIntOrNull()
    val weightKg = weightValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val targetWeightValue = targetWeight.toIntOrNull()
    val targetWeightKg = targetWeightValue?.let {
        if (weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
    }
    val targetDateText = targetDate.trim()
    val targetDateValue = targetDateText.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
    val targetDateDisplay = targetDateValue
        ?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
        .orEmpty()
    fun openTargetDatePicker() {
        val initialDate = targetDateValue ?: LocalDate.now().plusMonths(3)
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                onTargetDate(selected.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth,
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }
    val ageInvalidFormat = age.isNotBlank() && ageValue == null
    val weightInvalidFormat = weight.isNotBlank() && weightValue == null
    val targetWeightInvalidFormat = targetWeight.isNotBlank() && targetWeightValue == null
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
    val ageOutOfRange = ageValue != null && (ageValue < ProfileMinAge || ageValue > ProfileMaxAge)
    val weightOutOfRange = weightKg != null && (weightKg < 35 || weightKg > 180)
    val targetWeightOutOfRange = targetWeightKg != null && (targetWeightKg < 35 || targetWeightKg > 180)
    val targetDateInvalidFormat = targetDateText.isNotBlank() && targetDateValue == null
    val targetDateInPast = targetDateValue?.isBefore(LocalDate.now()) == true
    val heightOutOfRange = heightCmValue != null && (heightCmValue < 120 || heightCmValue > 200)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val compactUnitChipWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 68.dp, medium = 88.dp)
    val heightUnitChipWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 72.dp, medium = 92.dp)

    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        if (showName) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Display name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                    .testTag("profile_step1_name_input"),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
        }
        OutlinedTextField(
            value = age,
            onValueChange = onAge,
            label = { Text("Age (years)") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                .testTag("profile_step1_age_input"),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = ageInvalidFormat || ageOutOfRange,
            supportingText = if (ageInvalidFormat || ageOutOfRange) {
                {
                    Text(
                        when {
                            ageInvalidFormat -> "Enter a whole number."
                            else -> "Age must be 18–60."
                        }
                    )
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
        Text(
            text = "Valid Range: 18 - 60 years old",
            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (ageOutOfRange || weightOutOfRange) {
            Text(
                text = "Tip: keep age 18–60 and weight 35–180 for accurate targets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (heightUnit == UnitConverter.HEIGHT_FT_IN) {
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                label = { Text(if (weightUnit == UnitConverter.WEIGHT_LB) "Weight (lb)" else "Weight (kg)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                    .testTag("profile_step1_weight_input"),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = weightInvalidFormat || weightOutOfRange,
                supportingText = if (weightInvalidFormat || weightOutOfRange) {
                    {
                        Text(
                            when {
                                weightInvalidFormat -> "Enter a whole number."
                                else -> "35–180 kg equivalent only."
                            }
                        )
                    }
                } else null,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = heightFt,
                    onValueChange = onHeightFt,
                    label = { Text("Height (ft)") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_ft_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightFtInvalidFormat || heightOutOfRange,
                    supportingText = if (heightFtInvalidFormat || heightOutOfRange) {
                        {
                            Text(
                                when {
                                    heightFtInvalidFormat -> "Enter feet as a whole number."
                                    else -> "Total height must stay 120–200 cm."
                                }
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
                OutlinedTextField(
                    value = heightIn,
                    onValueChange = onHeightIn,
                    label = { Text("Height (in)") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_in_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightInInvalidFormat || heightInOutOfRange || heightOutOfRange,
                    supportingText = if (heightInInvalidFormat || heightInOutOfRange || heightOutOfRange) {
                        {
                            Text(
                                when {
                                    heightInInvalidFormat -> "Enter inches as a whole number."
                                    heightInOutOfRange -> "Inches must be 0–11."
                                    else -> "Total height must stay 120–200 cm."
                                }
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeight,
                    label = { Text(if (weightUnit == UnitConverter.WEIGHT_LB) "Weight (lb)" else "Weight (kg)") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_weight_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = weightInvalidFormat || weightOutOfRange,
                    supportingText = if (weightInvalidFormat || weightOutOfRange) {
                        {
                            Text(
                                when {
                                    weightInvalidFormat -> "Enter a whole number."
                                    else -> "35–180 kg equivalent only."
                                }
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = onHeightCm,
                    label = { Text("Height (cm)") },
                    modifier = Modifier
                        .weight(1f)
                        .keepFocusedProfileFieldVisible(onTextInputFocusChange)
                        .testTag("profile_step1_height_cm_input"),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightCmInvalidFormat || heightOutOfRange,
                    supportingText = if (heightCmInvalidFormat || heightOutOfRange) {
                        {
                            Text(
                                when {
                                    heightCmInvalidFormat -> "Enter height in centimeters."
                                    else -> "Height must be 120–200 cm."
                                }
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenizedFilterChip(
                    selected = weightUnit == UnitConverter.WEIGHT_KG,
                    onClick = { onWeightUnit(UnitConverter.WEIGHT_KG) },
                    text = "kg",
                    labelMaxWidth = compactUnitChipWidth
                )
                TokenizedFilterChip(
                    selected = weightUnit == UnitConverter.WEIGHT_LB,
                    onClick = { onWeightUnit(UnitConverter.WEIGHT_LB) },
                    text = "lb",
                    labelMaxWidth = compactUnitChipWidth
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenizedFilterChip(
                    selected = heightUnit == UnitConverter.HEIGHT_CM,
                    onClick = { onHeightUnit(UnitConverter.HEIGHT_CM) },
                    text = "cm",
                    labelMaxWidth = heightUnitChipWidth
                )
                TokenizedFilterChip(
                    selected = heightUnit == UnitConverter.HEIGHT_FT_IN,
                    onClick = { onHeightUnit(UnitConverter.HEIGHT_FT_IN) },
                    text = "ft/in",
                    labelMaxWidth = heightUnitChipWidth
                )
            }
        }
        Text(
            text = "Valid Range: 35 - 180 kg and 120 - 200 cm",
            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (heightOutOfRange) {
            Text(
                text = "Tip: keep height between 120 and 200 cm.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = "Optional weight support",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = PcosinaDeepRose
        )
        OutlinedTextField(
            value = targetWeight,
            onValueChange = onTargetWeight,
            label = { Text(if (weightUnit == UnitConverter.WEIGHT_LB) "Target weight (lb, optional)" else "Target weight (kg, optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_step1_target_weight_input"),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = targetWeightInvalidFormat || targetWeightOutOfRange,
            supportingText = if (targetWeightInvalidFormat || targetWeightOutOfRange) {
                {
                    Text(
                        when {
                            targetWeightInvalidFormat -> "Enter a whole number or leave blank."
                            else -> "35–180 kg equivalent only."
                        }
                    )
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = ::openTargetDatePicker),
            ) {
                OutlinedTextField(
                    value = targetDateDisplay,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Target date (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_step1_target_date_input"),
                    shape = MaterialTheme.shapes.medium,
                    isError = targetDateInvalidFormat || targetDateInPast,
                    supportingText = if (targetDateInvalidFormat || targetDateInPast) {
                        {
                            Text(
                                when {
                                    targetDateInvalidFormat -> "Pick a date from the calendar."
                                    else -> "Choose today or a future date."
                                }
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = color,
                    )
                )
            }
            OutlinedButton(
                onClick = ::openTargetDatePicker,
                modifier = Modifier.height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (targetDateDisplay.isBlank()) "Pick" else "Change")
            }
        }
        if (targetDateDisplay.isNotBlank()) {
            TextButton(onClick = { onTargetDate("") }) {
                Text("Clear target date")
            }
        }
        val targetPaceText = if (weightKg != null && targetWeightKg != null && targetDateValue != null && !targetDateInPast) {
            val days = ChronoUnit.DAYS.between(LocalDate.now(), targetDateValue)
            val weeklyPace = if (days > 0) (targetWeightKg - weightKg).toFloat() / (days.toFloat() / 7f) else 0f
            "Estimated target pace: ${String.format(Locale.ENGLISH, "%.2f", kotlin.math.abs(weeklyPace))} kg/week."
        } else {
            "Target fields are optional. They only support progress review and do not guarantee weight change."
        }
        Text(
            text = targetPaceText,
            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = activity, onValueChange = {}, readOnly = true, label = { Text("Activity Level") }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, shape = MaterialTheme.shapes.medium, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(focusedBorderColor = color))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onActivity(opt); expanded = false }) }
            }
        }
        val activityHint = when (activity) {
            "Sedentary" -> "Little to no exercise; lowers calorie targets."
            "Lightly Active" -> "Light activity 1–3 days/week; slightly raises calorie targets."
            "Moderately Active" -> "Moderate activity 3–5 days/week; raises calorie targets further."
            "Very Active" -> "Hard exercise 6–7 days/week; highest calorie target adjustment."
            else -> "Choose the closest match for a typical week. This changes calorie targets."
        }
        Text(
            text = activityHint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        SectionTitle("Wellness Details")
        Text(
            text = "These are optional wellness signals and can be changed later without redoing your whole profile.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f)
        )
        Text("Symptoms (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.22f) else Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) color.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = if (selected) color else Color.Transparent,
                border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outline)
            ) {}
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
    onTextInputFocusChange: (Boolean) -> Unit = {},
    color: Color
) {
    val varietyOptions = listOf("Low", "Balanced", "High")
    var varietyExpanded by remember { mutableStateOf(false) }
    val priorityOptions = listOf("Budget First", "Balanced", "Variety First", "Nutrition Tight")
    val restrictionOptions = listOf(
        "Lactose Intolerant" to (r1 to onR1),
        "Vegetarian" to (r2 to onR2),
        "Pescatarian" to (r3 to onR3),
        "Exclude Pork" to (r4 to onR4),
        "Exclude Beef" to (r5 to onR5)
    )
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
    val allergyTokens = parseDelimitedProfileItems(allergiesText)
        .map { it.lowercase(Locale.getDefault()) }
        .toMutableList()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val priorityChipMaxWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 104.dp, medium = 136.dp)
    val allergyChipMaxWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 92.dp, medium = 124.dp)
    val restrictionChipMaxWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 112.dp, medium = 144.dp)
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)) {
        SectionTitle("Preferences & Budget")
        Text(
            text = "Planning rules are ready. Hard constraints and soft preferences are set.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f)
        )

        SectionTitle("Dietary Restrictions")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            restrictionOptions.forEach { (label, state) ->
                val selected = state.first
                val toggle = state.second
                TokenizedFilterChip(
                    selected = selected,
                    onClick = { toggle(!selected) },
                    text = label,
                    labelMaxWidth = restrictionChipMaxWidth,
                    modifier = Modifier.heightIn(min = UiChipTokens.MinTouchHeight)
                )
            }
        }
        if (r2 && r3) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.error,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
            ) {
                Text(
                    text = "Vegetarian and Pescatarian cannot both be active.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        SectionTitle("Allergies")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commonAllergens.forEach { (label, token) ->
                val selected = allergyTokens.contains(token)
                TokenizedFilterChip(
                    selected = selected,
                    onClick = {
                        val updated = if (selected) {
                            allergyTokens.filterNot { it == token }
                        } else {
                            allergyTokens + token
                        }
                        onAllergiesText(updated.distinct().joinToString(", "))
                    },
                    text = label,
                    labelMaxWidth = allergyChipMaxWidth,
                    modifier = Modifier.heightIn(min = UiChipTokens.MinTouchHeight)
                )
            }
        }
        OutlinedTextField(
            value = allergiesText,
            onValueChange = onAllergiesText,
            label = { Text("Other allergies") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )

        SectionTitle("Weekly Planning")
        OutlinedTextField(
            value = budget,
            onValueChange = onBudget,
            label = { Text("Budget (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            prefix = { Text("₱ ") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
        Text(
            text = "Nutrition, ingredients, and shopping totals are planned for the primary user only.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = maxCookingTime,
            onValueChange = onMaxCookingTime,
            label = { Text("Max cooking time (minutes)") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange),
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
                label = { Text("Variety preference") },
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

        SectionTitle("Pantry Preference")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            priorityOptions.forEach { opt ->
                TokenizedFilterChip(
                    selected = planningPriority == opt,
                    onClick = { onPlanningPriority(opt) },
                    text = opt,
                    labelMaxWidth = priorityChipMaxWidth,
                    modifier = Modifier.heightIn(min = UiChipTokens.MinTouchHeight)
                )
            }
        }
        OutlinedTextField(
            value = pantryText,
            onValueChange = onPantryText,
            label = { Text("Pantry items (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .keepFocusedProfileFieldVisible(onTextInputFocusChange),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color)
        )
        Text(
            text = "Finish the hard food rules, pantry, and budget settings before saving.",
            style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
            color = PcosinaDeepRose.copy(alpha = 0.72f)
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = PcosinaBlushStrong.copy(alpha = 0.82f),
            contentColor = Color.White
        ) {}
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
