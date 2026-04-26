# Chapter 4-6 Validation Evidence Index

This evidence pack was generated from the `dev` branch only, as requested. No `main`-branch logic was used as the authoritative system description.

## Generation Metadata

- Current branch: `dev`
- Current commit SHA: `a094a0b234facd0b1a14e56d2773136db48b49c2`
- Date/time generated: `2026-04-26T15:28:20.971176+08:00`
- Generator script: `docs/thesis_validation/scripts/extract_validation_data.py`
- Total inspected source entries listed below: `211` 

## Trust Levels

- `HIGH`: directly implemented in code, tests, or bundled datasets.
- `MEDIUM`: described in docs and supported by code.
- `LOW`: described in docs but not directly demonstrated in current code.
- `MISSING`: expected by the task but not found in the current `dev` branch.

## Environment Notes

- Local plan generation using `backend/services/meal_planner.py` was **not executed** in this environment because importing OR-Tools-backed planner code is blocked here: `OSError: [WinError 4551] An Application Control policy has blocked this file`.
- Where code execution was blocked, the pack uses static parsing of actual source files and actual bundled datasets instead of guessed outputs.

## root

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ARCHITECTURE.md | System architecture and planner-stage description. | N | N | Y | Y | Y | N | N | MEDIUM |  |
| ML_GUARDRAILS.md | ML safety contract and deterministic fallback rules. | N | N | Y | Y | N | N | N | MEDIUM |  |
| PRIVACY_AND_SECURITY.md | Privacy posture, local-first storage, and security notes. | N | N | Y | N | Y | N | N | MEDIUM |  |
| README.md | Product overview and setup commands. | N | N | Y | Y | N | Y | N | MEDIUM |  |
| RELEASE_CHECKLIST.md | Release evidence checklist and pre-release checks. | N | N | N | N | N | Y | N | LOW |  |
| RISK_REGISTER.md | Known risks and unresolved evidence gaps. | N | N | N | N | N | Y | N | LOW |  |
| TEST_PLAN.md | Repo-defined validation plan and commands. | N | N | N | N | N | Y | N | MEDIUM |  |
| gradle/libs.versions.toml | Android dependency version catalog. | N | N | N | N | N | N | N | HIGH |  |
| render.yaml | Dev deployment blueprint and service topology. | N | N | Y | N | Y | N | N | HIGH |  |
## app

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| app/build.gradle.kts | Android build config, schema version, and release endpoints. | N | N | Y | N | N | N | Y | HIGH |  |
| app/src/androidTest/java/com/example/pcosina/ExampleInstrumentedTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/AppLaunchTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/CompactWidthVisualRegressionTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/DashboardWeekCloseoutUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/DuplicateMealSlotUiFlowTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/FirstWinFlowUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/GroceryFeedbackSemanticsUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/LiveTelemetryLoopInstrumentedTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/MealPlanNoSafePlanUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/MealPlanSwapBannerUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProfileStepOneValidationUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProgressDateLockUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProgressFeedbackFlowUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProgressLoggingPolicyInstrumentedTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProgressPerUserPersistenceUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/ProgressRetryBannerUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/RecipeDetailsNextCtaRouteUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/TestViewModelBindings.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/androidTest/java/com/pcosina/app/TraversalAndCtaBannerUiTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/main/AndroidManifest.xml | Android permissions and app manifest settings. | N | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt | API DTOs and Retrofit endpoint contract. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/api/RecipeDetailDto.kt | API DTOs and Retrofit endpoint contract. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/DailyLog.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/DummyData.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/FeedbackEntry.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/GrocerySnapshot.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/GrocerySource.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/MealCheckIn.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/MealPlan.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/PantryEntry.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/PlanInstance.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/Recipe.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/model/UserProfile.kt | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt | Persistence and API orchestration. | Y | N | Y | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt | Persistence and API orchestration. | Y | N | Y | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt | Persistence and API orchestration. | Y | N | Y | N | Y | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt | Grocery aggregation and cost estimation. | N | Y | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt | Health and unit computations. | Y | Y | N | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt | Price rules and price estimation logic. | N | Y | Y | N | Y | N | N | HIGH |  |
| app/src/main/java/com/pcosina/app/domain/UnitConverter.kt | Health and unit computations. | Y | Y | N | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/GroceryViewModel.kt | Android UI and view-model behavior. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt | Android UI and view-model behavior. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt | Android UI and view-model behavior. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/UserViewModel.kt | Android UI and view-model behavior. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt | Android UI and view-model behavior. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/CommunityScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/DashboardScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/GoalSelectionScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/GroceryListScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/IpoVisualizationScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/LoginScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/MealPlanScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/MoreToolsScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/ProgressScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/RecipeDetailsScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/SettingsScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/SignUpScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/SplashScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/main/java/com/pcosina/app/ui/screens/UserProfileScreen.kt | Compose screen and output flow. | Y | N | Y | N | N | N | Y | HIGH |  |
| app/src/test/java/com/example/pcosina/ExampleUnitTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/AdbPreflightRemediationPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/AndroidEnvPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/AppCheckPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ArtifactSyncPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/AuthMessageMappingTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/CiUxCriticalFlowPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/CiVisualEnvironmentPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/CiVisualRegressionPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/CiVisualStrictSetCompletenessPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ConnectedTestRunnerPreflightPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ConnectivityObserverPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DashboardConnectivityPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DashboardDisclosureTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DashboardPolicyUsageTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DashboardPrimaryActionPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DashboardTodayTimelineTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/DebugBaseUrlPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/FeedbackConsistencyPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/FirstWinDeterminismPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/FrameTimingProbeTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GoalSemanticsTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GoogleServicesReleaseGuardPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GroceryAccessibilityPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GroceryActionFeedbackPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GroceryAggregationTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GroceryListDataIsolationPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GuidedJourneyRegressionPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/GuidedProfileRoutePolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/HealthMetricsTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ImpactMetricFormatterTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/LoginRecoveryPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ManifestSecurityPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/MealCheckInSupportTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/MealPlanDayNavigationPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/MealPlanDaySelectionPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NavigationUsagePolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NotificationGoalPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NotificationGuardrailPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NotificationLifecycleReschedulePolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NotificationReliabilityPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/NotificationSchedulerPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/PlanAnchoringPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/PlanHistoryClearPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/PlanRationaleTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/PlannerPollingPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProfileConstraintSemanticsTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressDataSanitizationTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressDateParsingTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressFeedbackPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressModePolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressRecoveryPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressToastReplacementPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ProgressTraversalPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/RecipeDetailsSlotLoggingPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/RecipeRouteContextPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/ReleaseSigningPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/RoutesClassificationTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/SecureArtifactStoragePolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/SettingsNotificationPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/SwapAndGroceryRegressionPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/TodayLogSnapshotTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/UiGuardrailPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/UnitConverterTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/VisualBaselineAssetIntegrityPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/VisualBaselineChecksumPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/VisualBaselineManifestRefreshPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
| app/src/test/java/com/pcosina/app/VisualBaselineRefreshPreflightPolicyTest.kt | Android automated test coverage. | N | N | Y | N | N | Y | Y | HIGH |  |
## backend

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| backend/database.py | Recipe seeding, database access, and normalization. | N | Y | Y | N | Y | N | N | HIGH |  |
| backend/domain/models.py | Data model definitions. | Y | N | N | N | Y | N | Y | HIGH |  |
| backend/main.py | Backend API/runtime orchestration. | N | N | Y | Y | N | N | Y | HIGH |  |
| backend/policy_config.py | Authoritative planner policy schema and defaults. | N | N | Y | Y | Y | N | N | HIGH |  |
| backend/price_catalog.py | Price rules and price estimation logic. | N | Y | Y | N | Y | N | N | HIGH |  |
| backend/recipes.json | Bundled recipe inventory used by database seeding. | N | Y | N | N | Y | N | N | HIGH |  |
| backend/services/meal_planner.py | Backend planner scoring, filtering, and CP-SAT optimization. | N | Y | Y | Y | Y | N | N | HIGH |  |
| backend/services/ml_ranker.py | Optional ML ranking adapter and fallback handling. | N | Y | Y | N | N | N | N | HIGH |  |
| backend/services/plan_response_builder.py | Structured no-safe-plan response builder. | N | N | Y | N | N | N | Y | HIGH |  |
| backend/tests/test_admin_content_console_ui.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_feedback_security.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_nutrition_correction_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_operator_auth_policy.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_ops_console_ui.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_policy_console_ui.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_price_rule_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_admin_recipe_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_android_grocery_state_policy.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_android_methodology_copy_policy.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_android_navigation_help_access_policy.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_android_privacy_safe_logging.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_android_tooling_scripts.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_app_check_enforcement.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_async_job_ownership.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_async_queue_contract.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_broker_queue_mode.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_canary_guard.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_canary_guard_alert.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ci_ops_scripts.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_lightgbm_training.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_meal_planner.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ml_dataset_builder.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ml_events.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ml_ranker.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ml_readiness_script.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ml_rollout_prep_script.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_no_safe_plan_contract.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_admin_session_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_canary_webhooks.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_operator_access_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_plan_job_admin_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_plan_job_diagnostics.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_support_case_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_support_case_export_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_user_cloud_profile_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_ops_user_support_bundle_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_plan_job_diagnostics.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_planner_response_contract.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_policy_admin_api.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_policy_config.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_policy_registry.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_policy_store.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_rate_limit_backend.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_reason_feedback_dataset_builder.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_reason_normalizer.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_reason_telemetry_generator.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_runtime_readiness.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_schema_contract.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_schema_migrations.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_sync_recovery_e2e.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_sync_replay_pack.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/tests/test_worker_plan_jobs.py | Backend automated test coverage. | N | N | Y | Y | N | Y | N | HIGH |  |
| backend/worker_plan_jobs.py | Backend API/runtime orchestration. | N | N | Y | Y | N | N | Y | HIGH |  |
## docs

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| docs/roadmap/ml_progress_ledger.md | Supporting roadmap documentation. | N | N | N | N | N | N | N | LOW |  |
| docs/roadmap/progress_ledger.md | Supporting roadmap documentation. | N | N | N | N | N | N | N | LOW |  |
## ml

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ml/offline_training/artifacts/dataset_v1/dataset_manifest.json | Offline ML dataset manifest and row counts. | N | N | N | N | Y | N | N | HIGH |  |
| ml/offline_training/artifacts/model_v1/training_metrics.json | Offline ML artifact metrics for Stage 1 ranker evidence. | N | Y | N | N | N | Y | N | HIGH |  |
| ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json | Offline reason-feedback summary for ML rollout evidence. | N | N | N | N | Y | Y | N | HIGH |  |
## benchmarks

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| benchmarks/README.md | Benchmark or canary support artifact. | N | N | N | N | N | Y | N | MEDIUM |  |
| benchmarks/canonical_scenarios/comment5_canonical_scenarios.json | Benchmark or canary support artifact. | N | N | N | N | N | Y | N | HIGH |  |
| benchmarks/runner/run_comment5_benchmark.py | Benchmark or canary support artifact. | N | N | N | N | N | Y | N | HIGH |  |
## .github

| File | Purpose | Input | Formula | Rule | Optimize | Stored Data | Tests | UI | Trust | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| .github/workflows/canary-drill.yml | CI/CD and operational validation workflow. | N | N | Y | N | N | Y | N | HIGH |  |
| .github/workflows/ci.yml | CI/CD and operational validation workflow. | N | N | Y | N | N | Y | N | HIGH |  |
| .github/workflows/go-live-gates.yml | CI/CD and operational validation workflow. | N | N | Y | N | N | Y | N | HIGH |  |
