procee# PCOSina Design Handoff Implementation Plan

This plan translates the exported Figma/Canva handoff into safe Android UI work.

## Current state

- The repo is on `ui/layout-refine`.
- The active UI is Jetpack Compose under `app/src/main/java/com/pcosina/app/ui`.
- The app already has real ViewModel, repository, planner, grocery, progress, profile, notification, and settings data flows.
- The exported handoff assets exist locally under `design-assets/PCOSINA UI & SVG FILES/`.
- No stale `DashboardScreen.kt`, `MealPlanScreen.kt`, `GroceryListScreen.kt`, `ProgressScreen.kt`, or active `PolishedDashboardScreen.kt` files were found under `app/src/main/java`.
- `old-files/legacy-ui-screens/PolishedDashboardScreen.kt` is archive/reference only.

## Hard boundaries

- Do not use full-screen PNG files as actual Android screens.
- Do not use full-screen SVG files as actual Android screens.
- Rebuild UI in Compose.
- Preserve existing ViewModel and repository data flow.
- Do not hardcode real user data, meals, grocery counts, pantry items, budget values, progress values, calories, or avatar choices.
- Do not change backend, solver, planner, database, sync, or ML logic for this design task.
- Do not commit or push until explicitly requested.

## Active Android screen mapping

| Design area | Active Android files |
|---|---|
| Splash | `SplashScreen.kt` |
| Login / sign up / terms | `LoginScreen.kt`, `SignUpScreen.kt` |
| Personal profile / medical profile / preferences / budget | `UserProfileScreen.kt` |
| Goal selection | `GoalSelectionScreen.kt` |
| Home | `DashboardRefinedScreen.kt` |
| Plan / swap / meal check-in | `MealPlanRefinedScreen.kt`, `MealCheckInDialog.kt`, `RecipeDetailsScreen.kt` |
| Grocery / pantry / filters | `GroceryRefinedScreen.kt`, `GroceryViewModel.kt` |
| Progress | `ProgressRefinedScreen.kt`, `ProgressViewModel.kt` |
| Support | `CommunityScreen.kt` |
| Settings / avatar / reminders | `SettingsScreen.kt`, `PcosinaAvatar.kt`, `UserViewModel.kt` |
| Navigation | `AppNavHost.kt`, `Routes.kt`, `BottomNavBar.kt` |
| Theme and reusable UI | `Color.kt`, `Theme.kt`, `UiSpacingTokens.kt`, `UiMotionTokens.kt`, `UiChipTokens.kt`, `components/` |

## Screen-by-screen gap analysis

### Login, splash, and terms

- References: Splash Screen, Login Screen, Terms of Service 1, Terms of Service 2.
- Main files: `SplashScreen.kt`, `LoginScreen.kt`, `SignUpScreen.kt`.
- Main gaps: visual logo/heart treatment, exact splash composition, auth panel layout, and terms flow matching.
- Data to preserve: auth state, email/password state, Google sign-in path if still supported, legal acceptance behavior.
- Risk: moderate because auth routing must remain reliable.

### Profile and onboarding

- References: Personal Profile, Medical Profile, Preferences and Budget 1/2, Goal Setting Screen.
- Main files: `UserProfileScreen.kt`, `GoalSelectionScreen.kt`.
- Main gaps: step layout, field grouping, visual rhythm, bottom actions, and sequence fidelity.
- Data to preserve: age, height, weight, activity, symptoms, restrictions, allergies, budget, pantry, goal, hard constraints.
- Risk: high because persisted profile data drives planner constraints.

### Home

- References: Home Screen, Home Screen 1, WM/PSM/GH notification variants.
- Main file: `DashboardRefinedScreen.kt`.
- Main gaps: compactness, notification states, goals and daily tips hierarchy, meal card readability, icon matching.
- Data to preserve: current plan, profile, week progress, daily meals, goal state.
- Risk: moderate because it reads from many real sources but should not change domain behavior.

### Plan

- References: Plan Screen, Plan Screen alternate, Swap UI, Meal Check-In UI 1/2.
- Main files: `MealPlanRefinedScreen.kt`, `MealCheckInDialog.kt`, `RecipeDetailsScreen.kt`.
- Main gaps: plan card layout, swap modal, check-in modal, locked day/meal wording, recipe navigation polish.
- Data to preserve: plan generation, MILP-driven result handling, swap options, meal check-ins, grocery sync.
- Risk: high because this is the core meal planning flow.

### Grocery and pantry

- References: Grocery Screen, Grocery Meal Card UI, Select Filter UI, Pantry List Page UI, Popup Assets.
- Main file: `GroceryRefinedScreen.kt`.
- Main gaps: filter popup styling, pantry list styling, add pantry item dialog, expandable category clarity, grocery action icons.
- Data to preserve: generated grocery list, pantry entries, checked items, filters, estimated cost, budget state.
- Risk: moderate to high because grocery output must stay tied to real planner data.

### Progress

- References: Progress Screen WH UI, Progress Screen UI 1/2/3.
- Main files: `ProgressRefinedScreen.kt`, `ProgressViewModel.kt`.
- Main gaps: bulky card density, weekly highlights layout, macro chart styling, savings/adherence hierarchy.
- Data to preserve: weekly savings, adherence, meals done, average macros, BMI/health metrics if shown, chart calculations.
- Risk: high because calculated values must remain real.

### Support

- Reference: Support Screen UI.
- Main file: `CommunityScreen.kt`.
- Main gaps: final spacing/icon matching, How to Use/Tips/Feedback/App Directory sections, future demo video button treatment.
- Data to preserve: feedback interaction and navigation callbacks.
- Risk: low compared with planner/profile/progress.

### Settings, avatar, reminders, and notifications

- References: Settings Profile, Avatar, Reminder, Reminder Time variants, Notification UI.
- Main files: `SettingsScreen.kt`, `PcosinaAvatar.kt`, `UserViewModel.kt`.
- Main gaps: tab/section fidelity, avatar grid/state, reminder cards/time picker, notification preferences screen state.
- Data to preserve: profile data, avatar ID, notification preferences, reminder scheduling settings, admin/debug gating.
- Risk: moderate because settings touch persistence and permissions.

## Recommended implementation order

### Session 1: asset manifest and design handoff docs

- Files: `docs/design_handoff/README.md`, `asset_manifest.md`, `figma_frame_index.md`, `implementation_plan.md`.
- No Kotlin changes.
- No resource imports.
- Success criteria: docs clearly separate screen references from importable assets and define the phased plan.

### Session 2: Support screen polish

- Likely files: `CommunityScreen.kt`, optional support icon drawables after approved import.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: support screen matches the reference more closely without old "At a glance" density.
- Data criteria: feedback still works.
- Manual check: open Support tab/screen and test feedback action.

### Session 3: Settings and avatar polish

- Likely files: `SettingsScreen.kt`, `PcosinaAvatar.kt`, optional settings/avatar drawables.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: settings profile/avatar/reminder layouts match selected Figma variants.
- Data criteria: avatar and notification preferences persist.
- Manual check: change avatar, reopen settings, verify selected avatar remains.

### Session 4: Grocery, pantry, and filter UI

- Likely files: `GroceryRefinedScreen.kt`, optional grocery drawables.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: pantry, filter, grocery cards, and category sections match handoff.
- Data criteria: pantry entries and grocery calculations remain real.
- Manual check: add pantry item, filter grocery list, check/uncheck item.

### Session 5: Home spacing and readability

- Likely files: `DashboardRefinedScreen.kt`, `BottomNavBar.kt`, optional home/nav drawables.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: goals, tips, meal cards, and notification variants read clearly.
- Data criteria: dashboard uses current profile and meal plan data.
- Manual check: open dashboard with and without generated plan.

### Session 6: Progress dashboard and charts

- Likely files: `ProgressRefinedScreen.kt`, progress chart/components, optional progress drawables.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: selected progress variant is compact and readable.
- Data criteria: weekly savings, adherence, macros, meals done, and charts remain calculated.
- Manual check: inspect progress with seeded or existing logs.

### Session 7: Profile and onboarding

- Likely files: `UserProfileScreen.kt`, `GoalSelectionScreen.kt`.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: personal, medical, preferences/budget, and goal steps match the Figma sequence.
- Data criteria: all profile values persist and hard constraints remain explicit.
- Manual check: complete onboarding and verify planner receives the saved profile.

### Session 8: Login, splash, and terms

- Likely files: `SplashScreen.kt`, `LoginScreen.kt`, `SignUpScreen.kt`, optional auth assets.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: splash, login, heart logo, and terms states match the handoff.
- Data criteria: auth flow remains functional.
- Manual check: cold start, login, sign up, and terms interaction.

### Session 9: final consistency and QA pass

- Likely files: small fixes across UI screens, components, theme, and resources.
- Build command: `.\gradlew.bat :app:assembleDebug`.
- UI criteria: whole app uses one visual language with correct icons and no crowded cards.
- Data criteria: no fake data or broken flows.
- Manual check: run through splash, login, onboarding, home, plan, grocery, progress, support, settings.

## Decisions needed before implementation

- Which Progress screen variant is final?
- Should Terms of Service remain a dialog or become dedicated screens?
- Which Notification UI state is final?
- Are Grocery popup/filter designs final?
- Should avatar selection appear in onboarding, settings, or both?
- Which exported icons are final and which are placeholders?
- Should all full-screen SVGs stay in documentation/reference only?
- Should existing rasterized nav icons be replaced by VectorDrawable XML imports?

## Figma MCP use during later implementation

For each implementation session, fetch the exact Figma node with Figma MCP before editing Compose code. Use the local PNGs as visual references, then translate the structure into Compose using existing PCOSina state and theme patterns.
