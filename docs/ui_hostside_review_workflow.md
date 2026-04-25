# Host-Side UI Review Workflow

This project now includes a Compose Preview gallery for no-device visual review in Android Studio.

## Why use it

- Review UI changes without a physical phone or emulator.
- Catch layout issues before APK distribution.
- Review compact width, dark mode, and large-font states from the workstation.

## Current preview targets

- Dashboard status stack
- Dashboard long-text / truncation state
- Guided journey / onboarding flow
- Login
- Login error state
- Sign-up state
- Sign-up loading state
- Sign-up email-format validation state
- Sign-up validation error
- Sign-up verification-sent state
- Account recovery reset-sent state
- Account recovery verification state
- User profile setup
- Meal plan overview
- Meal plan no-safe-plan fallback
- Grocery first-run empty state
- Grocery search/filter empty state
- Grocery pantry inventory flow
- Grocery filter-reset recovery state
- Grocery budget warning state
- Grocery monthly budget mode
- Grocery share-success state
- Grocery share-failure recovery
- Grocery checked-item flow
- Grocery checked pantry-category flow
- Recipe loading state
- Recipe error state
- Recipe success / header state
- Recipe locked-logging state
- Recipe not-in-today-plan recovery
- Recipe meal check-in dialog
- Recipe add-to-grocery success
- Recipe admin rationale copy
- Support & guides screen
- Help & tools hub
- Planning methodology admin reference
- Settings overview / top summary
- Settings admin-tools state
- Settings notification-success state
- Settings notification-permission state
- Settings quiet-hours scheduling state
- Settings meal-reminder success state
- Settings weekly-reset success state
- Settings reminder no-sign-in validation state
- Settings test-notification success state
- Settings reminder diagnostics state
- Progress summary
- Progress empty state

These live under `app/src/debug/java/com/pcosina/app/ui/preview/`.

## How to use it

1. Open `HostSideVisualShowcasePreviews.kt` in Android Studio.
2. Switch the Preview panel to `Design` or `Split`.
3. Refresh previews after UI edits.
4. Review the compact, dark, and large-font variants before building an APK.
5. Use the host previews to tune spacing, hierarchy, copy, and empty/loading messaging before asking someone with a phone to validate interaction details.

## Reports

No external device is required for this workflow.

## Next expansion

- Add preview coverage for sign-up password-mismatch and verification-resend edge states if auth copy changes further.
- Add preview coverage for settings weekly-reset no-sign-in and test-notification no-sign-in validation if reminder UX changes further.
- Add host-side screenshot baselines once dependency resolution is available in the environment.
