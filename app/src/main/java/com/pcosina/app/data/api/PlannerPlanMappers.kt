package com.pcosina.app.data.api

import com.pcosina.app.data.model.PlannerDayPlan
import com.pcosina.app.data.model.PlannerContractItem
import com.pcosina.app.data.model.PlannerGroceryOutput
import com.pcosina.app.data.model.PlannerGroceryOutputItem
import com.pcosina.app.data.model.PlannerPlanExplanation
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.data.model.PlannerPlannedMeal
import com.pcosina.app.data.model.PlannerTimestamps as PlannerTimestampsModel

fun PlannedMealDto.toPlannerPlannedMeal(): PlannerPlannedMeal =
    PlannerPlannedMeal(
        mealLabel = mealLabel,
        recipeId = recipeId,
        title = title,
    )

fun DayPlanDto.toPlannerDayPlan(): PlannerDayPlan =
    PlannerDayPlan(
        dayLabel = dayLabel,
        meals = meals.map { it.toPlannerPlannedMeal() },
        totalCalories = totalCalories,
    )

fun PlannerContractItemDto.toPlannerContractItem(): PlannerContractItem =
    PlannerContractItem(
        field = field,
        classification = classification,
        enforcement = enforcement,
        active = active,
    )

fun PlanExplanation.toPlannerPlanExplanation(): PlannerPlanExplanation =
    PlannerPlanExplanation(
        confidenceScore = confidenceScore,
        targetCalories = targetCalories,
        avgCalories = avgCalories,
        avgCaloriesDeviation = avgCaloriesDeviation,
        targetProtein = targetProtein,
        avgProtein = avgProtein,
        targetCarbs = targetCarbs,
        avgCarbs = avgCarbs,
        targetFats = targetFats,
        avgFats = avgFats,
        toleranceUsed = toleranceUsed,
        maxPerWeek = maxPerWeek,
        pantryMatches = pantryMatches,
        uniqueVegTokens = uniqueVegTokens,
        budgetWeekly = budgetWeekly,
        estimatedWeeklyCost = estimatedWeeklyCost,
        restrictionCount = restrictionCount,
        budgetHardCapApplied = budgetHardCapApplied,
        goalValue = goalValue,
        symptomSelections = symptomSelections,
        profileRuleEffects = profileRuleEffects,
        symptomStrategy = symptomStrategy,
        candidateExclusionSummary = candidateExclusionSummary,
        selectionReasonsByRecipeId = selectionReasonsByRecipeId,
        selectionReasonCounts = selectionReasonCounts,
        plannerContract = plannerContract.map { it.toPlannerContractItem() },
        fiberMinTarget = fiberMinTarget,
        sugarMaxTarget = sugarMaxTarget,
        goalStrategy = goalStrategy,
    )

fun GroceryOutputItemDto.toPlannerGroceryOutputItem(): PlannerGroceryOutputItem =
    PlannerGroceryOutputItem(
        key = key,
        name = name,
        quantity = quantity,
        requiredQuantity = requiredQuantity,
        purchaseQuantity = purchaseQuantity,
        purchaseMode = purchaseMode,
        estimatedCostPhp = estimatedCostPhp,
        unitPricePhp = unitPricePhp,
        priceUnit = priceUnit,
        category = category,
        source = source,
        sourceLabel = sourceLabel,
        confidence = confidence,
        originalNames = originalNames,
    )

fun GroceryOutputDto.toPlannerGroceryOutput(): PlannerGroceryOutput =
    PlannerGroceryOutput(
        authority = authority,
        budgetAuthority = budgetAuthority,
        pricingAuthority = pricingAuthority,
        pricingCatalogVersion = pricingCatalogVersion,
        pricingReferenceDate = pricingReferenceDate,
        pricingReferenceLocation = pricingReferenceLocation,
        pricingBasis = pricingBasis,
        pricingSourceCounts = pricingSourceCounts,
        estimatedTotalPhp = estimatedTotalPhp,
        finalGroceryEstimatePhp = finalGroceryEstimatePhp,
        weeklyBudgetPhp = weeklyBudgetPhp,
        userBudgetPhp = userBudgetPhp,
        withinBudget = withinBudget,
        budgetDeltaPhp = budgetDeltaPhp,
        budgetGapPhp = budgetGapPhp,
        displayedEstimateSource = displayedEstimateSource,
        itemCount = itemCount,
        selectedMealCount = selectedMealCount,
        plannerMealEstimatePhp = plannerMealEstimatePhp,
        solverBudgetEstimatePhp = solverBudgetEstimatePhp,
        roughMealBudgetCapPhp = roughMealBudgetCapPhp,
        items = items.map { it.toPlannerGroceryOutputItem() },
    )

fun PlannerTimestamps?.toPlannerTimestampsModel(): PlannerTimestampsModel? =
    this?.let {
        PlannerTimestampsModel(
            requestedAtMs = it.requestedAtMs,
            completedAtMs = it.completedAtMs,
        )
    }

fun GeneratePlanResponse.toPlannerPlanResponse(): PlannerPlanResponse =
    PlannerPlanResponse(
        weekLabel = weekLabel,
        days = days.map { it.toPlannerDayPlan() },
        status = status,
        message = message,
        explanation = explanation?.toPlannerPlanExplanation(),
        requestId = requestId,
        planId = planId,
        groceryOutput = groceryOutput?.toPlannerGroceryOutput(),
        policyVersion = policyVersion,
        machineReasonCodes = machineReasonCodes,
        humanGuidance = humanGuidance,
        suggestedRelaxations = suggestedRelaxations,
        diagnosticsReference = diagnosticsReference,
        timestamps = timestamps.toPlannerTimestampsModel(),
    )
