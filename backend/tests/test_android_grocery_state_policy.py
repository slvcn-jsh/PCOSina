from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GROCERY_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "GroceryViewModel.kt"
MEAL_PLAN_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "MealPlanViewModel.kt"
USER_PREFS_REPOSITORY = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "data" / "repository" / "UserPreferencesRepository.kt"
USER_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "UserViewModel.kt"
GROCERY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "GroceryRefinedScreen.kt"
PROGRESS_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "ProgressRefinedScreen.kt"
GROCERY_AGGREGATION = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "domain" / "GroceryAggregation.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_grocery_view_model_falls_back_when_saved_active_plan_is_stale():
    source = _read(GROCERY_VIEW_MODEL)
    assert "savedActive?.let" in source
    assert "?: snapshots.lastOrNull()" in source
    assert "groceryLocalRepository.saveActivePlanId(userId, resolvedActiveId)" in source


def test_grocery_view_model_persists_active_plan_clears():
    source = _read(GROCERY_VIEW_MODEL)
    assert "groceryLocalRepository.saveActivePlanId(currentUserId, planId)" in source
    assert "if (planId == null)" in source


def test_clear_grocery_snapshots_also_clears_active_plan_id():
    source = _read(USER_PREFS_REPOSITORY)
    assert "preferences.remove(Keys.activePlanId(userId))" in source


def test_grocery_item_aggregation_and_manual_edits_use_normalized_name_keys():
    grocery_source = _read(GROCERY_VIEW_MODEL)
    meal_plan_source = _read(MEAL_PLAN_VIEW_MODEL)
    assert "private fun normalizedItemKey(name: String)" in grocery_source
    assert "normalizedItemKey(it.name) == normalizedKey" in grocery_source
    assert "normalizedItemKey(it.name) != normalizedKey" in grocery_source
    assert "groupBy { normalizeGroceryItemName(it.name) }" in meal_plan_source
    assert "private fun normalizeGroceryItemName(raw: String)" in meal_plan_source


def test_pantry_editing_and_matching_use_normalized_name_keys():
    user_source = _read(USER_VIEW_MODEL)
    grocery_screen_source = _read(GROCERY_SCREEN)
    grocery_aggregation_source = _read(GROCERY_AGGREGATION)
    assert "private fun normalizePantryNameKey(raw: String)" in user_source
    assert "_pantryEntries.value.associateBy { normalizePantryNameKey(it.name) }" in user_source
    assert ".distinctBy { normalizePantryNameKey(it.name) }" in user_source
    assert "buildPantryCoverage(groupedEntries, effectivePantryEntries, today)" in grocery_screen_source
    assert ".filterValues { it.autoCovered }" in grocery_screen_source
    assert "PantryCoverageStatus.Partial" in grocery_screen_source
    assert "PantryCoverageStatus.NameOnly" in grocery_screen_source
    assert "fun groceryNamesMatch(left: String, right: String): Boolean" in grocery_aggregation_source
    assert "fun buildPantryCoverage(" in grocery_aggregation_source
    assert "enum class PantryCoverageStatus" in grocery_aggregation_source
    assert "PantryCoverageStatus.Full" in grocery_aggregation_source
    assert "PantryCoverageStatus.Partial" in grocery_aggregation_source
    assert "PantryCoverageStatus.NameOnly" in grocery_aggregation_source
    assert "private fun refinedPantryKey(raw: String)" in grocery_screen_source
    assert "private fun refinedPantryMatches(pantryName: String, groceryName: String): Boolean" in grocery_screen_source
    assert "return groceryNamesMatch(pantryName, groceryName)" in grocery_screen_source


def test_android_budget_display_reconciles_pantry_deductions_with_backend_grocery_authority():
    meal_plan_source = _read(MEAL_PLAN_VIEW_MODEL)
    grocery_screen_source = _read(GROCERY_SCREEN)
    progress_screen_source = _read(PROGRESS_SCREEN)

    assert "response.groceryOutput" in meal_plan_source
    assert "response.explanation.copy(estimatedWeeklyCost = authoritativeEstimate)" in meal_plan_source
    assert "val activeGroceryOutput = activePlanResponse?.groceryOutput" in grocery_screen_source
    assert "val trustBackendPricing = shouldTrustBackendGroceryPricing(" in grocery_screen_source
    assert "if (trustBackendPricing)" in grocery_screen_source
    assert "trustBackendPrices = trustBackendPricing" in grocery_screen_source
    assert "correctedAuthoritativeGroceryEstimate(" in grocery_screen_source
    assert "alignGroceryEstimateWithAuthority(" in grocery_screen_source
    assert "localAmountPhp = localShoppingEstimate" in grocery_screen_source
    assert "if (groupedEntries.isEmpty())" in grocery_screen_source
    assert "currentPlan\n            ?.groceryOutput\n            ?.estimatedTotalPhp" in progress_screen_source
