from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GROCERY_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "GroceryViewModel.kt"
MEAL_PLAN_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "MealPlanViewModel.kt"
USER_PREFS_REPOSITORY = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "data" / "repository" / "UserPreferencesRepository.kt"
USER_VIEW_MODEL = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "UserViewModel.kt"
GROCERY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "GroceryRefinedScreen.kt"


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
    assert "private fun normalizePantryNameKey(raw: String)" in user_source
    assert "_pantryEntries.value.associateBy { normalizePantryNameKey(it.name) }" in user_source
    assert ".distinctBy { normalizePantryNameKey(it.name) }" in user_source
    assert "effectivePantryEntries.map { refinedPantryKey(it.name) }" in grocery_screen_source
    assert "pantryTokens.any { token -> refinedPantryMatches(token, item.name) }" in grocery_screen_source
    assert "private fun refinedPantryKey(raw: String)" in grocery_screen_source
    assert "private fun refinedPantryTokens(raw: String): Set<String>" in grocery_screen_source
    assert "private fun refinedPantryMatches(pantryName: String, groceryName: String): Boolean" in grocery_screen_source
    assert "if (pantryKey == groceryKey) return true" in grocery_screen_source
    assert "if (pantryTokens.size <= 1 || groceryTokens.size <= 1) return false" in grocery_screen_source
    assert "pantryTokens.containsAll(groceryTokens) || groceryTokens.containsAll(pantryTokens)" in grocery_screen_source
