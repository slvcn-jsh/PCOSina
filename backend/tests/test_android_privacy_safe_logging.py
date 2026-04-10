from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def _read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def test_mobile_common_flow_logs_use_redacted_user_scope() -> None:
    helper = _read("app/src/main/java/com/pcosina/app/util/LogPrivacy.kt")
    assert "fun safeUserLogScope(userId: String?): String" in helper
    assert "MessageDigest.getInstance(\"SHA-256\")" in helper

    tracked_files = {
        "app/src/main/java/com/pcosina/app/ui/screens/MealPlanScreen.kt",
        "app/src/main/java/com/pcosina/app/ui/screens/GroceryListScreen.kt",
        "app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt",
        "app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt",
        "app/src/main/java/com/pcosina/app/notifications/NotificationScheduler.kt",
        "app/src/main/java/com/pcosina/app/ui/util/MealPlanNextActionDebugLog.kt",
    }
    combined = "\n".join(_read(path) for path in tracked_files)

    assert combined.count("safeUserLogScope(") >= len(tracked_files)

    raw_user_patterns = (
        "user=$userId",
        "user=$activeUserId",
        "user=$currentUserId",
        "user=${userViewModel.activeUserId}",
        "userId=$userId",
        "userId=${userId}",
    )
    for pattern in raw_user_patterns:
        assert pattern not in combined
