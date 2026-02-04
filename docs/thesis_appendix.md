# PCOSINA Thesis Appendix: Auth, Onboarding, and Data Integrity

**Scope**
This appendix documents the end-to-end auth/onboarding/data flow analysis, formal behavioral specifications, and engineering fixes for PCOSINA. It is written to be demo-ready and thesis-defensible.

## A) Diagnosis (Root Causes + Locations)
1. Validation gaps in auth
   - Root cause: Email format and password complexity not enforced before Firebase calls.
   - Components: AuthViewModel.onLogin, AuthViewModel.onSignUp.
   - Files: app/src/main/java/com/pcosina/app/ui/AuthViewModel.kt.
2. Onboarding resets after relogin
   - Root cause: Routing decisions made before profile load completes; stale or default profile used.
   - Components: AppNavHost login route; UserViewModel load timing.
   - Files: app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt.
3. Cross-account plan and grocery leakage
   - Root cause: Email-keyed DataStore and in-memory caches not cleared on logout.
   - Components: UserPreferencesRepository keys; MealPlanViewModel; GroceryViewModel.
   - Files: app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt, app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt, app/src/main/java/com/pcosina/app/ui/GroceryViewModel.kt.
4. Identity drift risk
   - Root cause: Email is mutable; storage keyed by email breaks when email changes.
   - Components: UserPreferencesRepository key strategy.
   - Files: app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt.

## B) Correct Behavior Spec (Formal Rules)
1. Signup/Login validation
   - Email required and valid format.
   - Password required, length >= 8, at least 1 letter, 1 number, 1 special character.
   - Confirm password must match.
   - Invalid input must not call Firebase; show inline error.
2. onboardingComplete/profileCompleted persistence
   - Profile completion is per-account and stored by UID.
   - Routing must wait for profile load before deciding destination.
3. Logout behavior
   - Must sign out Firebase.
   - Must clear session and reset in-memory caches.
   - Must not delete user data unless explicitly requested.
4. Per-account isolation
   - All persisted data is UID-keyed.
   - No read/write when UID missing.
   - Reset in-memory caches on UID change.

## C) Fix Plan (Ordered, Minimal Risk)
1. Enforce auth validation rules in AuthViewModel.
2. Reset UserViewModel, MealPlanViewModel, GroceryViewModel on logout.
3. Route to Splash after login and wait for profile load to decide Dashboard vs Onboarding.
4. Switch DataStore keys from email to UID.
5. Migrate legacy email-keyed data to UID once.
6. Invalidate in-memory caches on UID change.

## D) Improvements & Enhancements
### Demo polish (high-impact, low-effort)
1. Inline field validation and helpful hints for onboarding.
2. Numeric input masks for age, weight, height, budget.
3. Disable Next until required fields are valid.
4. Loading overlay during profile restoration after login.
5. Offline error banner for plan generation.
6. Show plan timestamp on plan screen.
7. Explain why each profile field is required.
8. Distinct errors for auth vs network failures.

### Thesis edge (explainability and evidence)
1. Explainability panel: show macro targets and constraint rationale.
2. Record solver stats (time, deviation from targets).
3. Plan provenance hash for reproducibility.
4. Re-generate with same seed for deterministic evaluation.
5. A/B switch between heuristic and MILP for benchmarking.

### Future-scale upgrades
1. Server-side per-UID plan history and cross-device sync.
2. Roles: user, nutritionist, admin.
3. Consent-based analytics and privacy-safe telemetry.

## E) Edge Cases (Expected Behavior)
1. Empty email -> block with error.
2. Invalid email format -> block with error.
3. Password missing special character -> block with error.
4. Password missing number -> block with error.
5. Confirm password mismatch -> block with error.
6. Logout during profile load -> clear caches and return to Login.
7. Relogin same user -> Dashboard without onboarding reset.
8. Login different user -> onboarding required, no prior data.
9. UID missing -> no storage read/write.
10. Email changes in Firebase -> data still intact via UID.
11. Offline plan generation -> show error, keep cached plan.
12. Corrupt cached plan JSON -> fallback to Idle and regenerate.

## F) Acceptance Criteria
1. Invalid auth inputs never reach Firebase.
2. Onboarding Next disabled until required fields valid.
3. Logout clears in-memory state immediately.
4. Login routing waits for profile load and uses profileCompleted.
5. No cross-account leakage of profile, plan, or grocery data.
6. Legacy data migrates once to UID.
7. Offline/timeout handled gracefully.

## G) Manual Test Script
1. Signup with invalid email -> error.
2. Signup with weak password -> error.
3. Signup with valid creds -> onboarding.
4. Leave age empty -> Next disabled.
5. Fill valid values -> Next enabled.
6. Complete onboarding -> Dashboard.
7. Generate plan -> appears and persists.
8. Logout -> Login and caches cleared.
9. Login same user -> Dashboard without onboarding.
10. Login different user -> onboarding required, no prior data.
11. Offline plan generation -> error banner, cached plan remains.
12. Check logcat for migration once if legacy data exists.
