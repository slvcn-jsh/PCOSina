# PCOSina App Observation and Improvement Action Plan

Report date: 2026-05-27  
Scope: latest local repository implementation only. This is an analysis and planning document. No production code was changed.

## 1. Executive Summary

PCOSina has a solid offline-first base: user profile data, plans, grocery data, progress logs, feedback queue, notification preferences, and several UI preferences are locally persisted, and parts of that state are also prepared for Firestore cloud sync. The app is not a thin online client. The main product-readiness issue is that several refined screens expose internal logic, ambiguous states, or incomplete UX around real behaviors that already exist in code.

The most serious issues to fix first are:

- Profile save feedback is not explicit enough. Multi-goal selection appears supported as a comma-delimited goal string, but save actions can feel broken because validation, disabled states, async local/cloud save, and navigation are not surfaced clearly.
- Meal status is inconsistent across Home, Plan, Recipe Details, and Progress. The Plan screen blocks skipped meals correctly, but Recipe Details can log a skipped planned meal and implicitly remove the skipped state.
- Pantry and grocery pricing are under-modeled. Pantry supports full, partial, and name-only coverage, but partial coverage does not reduce cost, name-only coverage is surfaced as "Pantry?", and unit compatibility is too unclear for users.
- Android and backend pricing logic are inconsistent. Garlic can become unrealistic in Android grocery totals because grocery aggregation sums individual clove segments while Android `PriceCatalog` prices each clove using a generic produce piece weight. Backend DB-backed estimation for "garlic 130g" is much lower and more realistic.
- Reminder notifications have real WorkManager scheduling and local logs, but the UI does not clearly explain that logs only appear after delivery, that master permission gates scheduling, or that some Settings switches are display-only.
- Offline plan generation failures can still show developer wording such as backend host, Render fallback, emulator internet, VPN, and Private DNS. The user-facing message should be simpler.

What should be fixed first:

1. Reproduce profile save, Generate New Week, meal logging status, notification test, and feedback queue outcomes with logs/screenshots.
2. Add user-visible save/loading/error states for profile editing.
3. Unify meal status rules across Home, Plan, Recipe Details, Progress, and history.
4. Replace developer-facing generation/network messages with simple copy.
5. Fix the Android grocery pricing unit bug for count units such as garlic cloves before deeper pantry redesign.

What should be removed or hidden:

- Visible "Plan rule contract" card, unless redesigned as a user-friendly "Why this plan fits you" section.
- "Primary-user plan" labels in repeated user-facing places.
- "Add ingredients to Grocery" from recipe/meal cards when ingredients already sync from the weekly plan.
- "Support Page" text below the Support header.
- "Phone permission: Allowed" as a prominent meal reminder message.

What should be redesigned:

- Pantry quantity and unit entry.
- Pantry coverage statuses and partial deduction.
- Weekly savings graph.
- Average macros explanation.
- Progress/check-in actionability.
- Support directory layout.
- Reminder settings hierarchy.
- Avatar/header layout and background image alignment controls.

What can stay as-is for now:

- Offline-first local persistence as the source of truth.
- Same-day-only progress/check-in editing policy, if the UI explains history is review-only.
- Check-ins and weekly review as concepts, because they already feed local summaries and can influence next-plan tags.
- Optional weight support, if copy is rewritten as wellness tracking rather than a required medical/weight-loss feature.

## 2. Readiness Scorecard

| Area | Current Readiness % | Severity | Main Issue | Likely Files | Recommended Direction |
|---|---:|---|---|---|---|
| Profile editing and onboarding | 68% | High | Save can feel silent or blocked; validation and date input need clearer UX | `UserProfileScreen.kt`, `UserViewModel.kt`, `UserPreferencesRepository.kt`, `GoalSelectionScreen.kt`, `GoalSemantics.kt` | Fix save states, validation, date picker |
| Meal plan screen | 72% | High | Internal labels, unclear Generate New Week lock, inconsistent meal status copy | `MealPlanRefinedScreen.kt`, `MealPlanViewModel.kt`, `MealLoggingPolicyUseCase.kt`, `RecipeDetailsScreen.kt` | Remove internal labels, clarify disabled policy, unify status |
| Grocery and pantry logic | 55% | High | Quantity/unit semantics unclear; partial pantry coverage does not deduct remaining cost | `GroceryRefinedScreen.kt`, `GroceryAggregation.kt`, `GroceryViewModel.kt`, `PantryEntry.kt` | Redesign data model and UX |
| Pricing logic | 45% | High | Android price conversion can overprice count units; backend and Android catalogs differ | `PriceCatalog.kt`, `GroceryAggregation.kt`, `backend/price_catalog.py`, `backend/database.py` | Fix unit pricing and centralize rules |
| Home screen UI | 75% | Medium | Goal card can grow too large and title text can clip | `DashboardRefinedScreen.kt`, `SharedAvatarHeader.kt` | Compact goal chips and responsive text |
| Progress screen | 58% | Medium | Cards are wordy, partially actionable, and charts/macros need explanation | `ProgressRefinedScreen.kt`, `ProgressSummaryUseCase.kt`, `WeightGoalGuardrailUseCase.kt` | Redesign card hierarchy and copy |
| Check-ins and weekly review | 62% | Medium | Useful data is collected, but effects on next plan are not obvious | `ProgressViewModel.kt`, `ProgressNextPlanAdjustmentUseCase.kt`, `PlannerProfilePreparationUseCase.kt`, `ReflectionStore.kt` | Clarify tracking vs tuning |
| Notifications/reminders | 50% | High | Real scheduler exists but UI does not explain delivery gates or empty logs | `SettingsScreen.kt`, `NotificationScheduler.kt`, `NotificationHelper.kt`, `NotificationScreen.kt`, `NotificationPreferences.kt` | Verify end-to-end and simplify UI |
| Support screen | 60% | Medium | Redundant header text, compressed directory, weak CTA purpose | `CommunityScreen.kt`, `AppNavHost.kt` | Remove duplicate copy, redesign support cards |
| Settings/account screen | 58% | High | Display-only toggles look clickable; goal text can compress; reminder copy is noisy | `SettingsScreen.kt`, `AuthRepository.kt`, `UserPreferencesRepository.kt` | Separate account, profile, reminders clearly |
| Avatar/assets/UI consistency | 65% | Medium | Header card text can clip; avatar transparency likely asset-level; no alignment config | `PcosinaAvatar.kt`, `PcosinaSharedChrome.kt`, drawable-nodpi assets | Verify assets, add alignment constants |
| Offline behavior | 72% | High | Local mode works, but generation failure wording and legacy-route risk need scan tests | `AppNavHost.kt`, `Routes.kt`, `MealPlanRepository.kt`, `old-files/legacy-ui-screens/*` | Keep offline, simplify failure messages |
| Feedback queue/sync behavior | 70% | Medium | Online feedback is queued first but user only sees queued wording | `CommunityScreen.kt`, `ProgressViewModel.kt`, `FeedbackRepository.kt`, `backend/main.py` | Keep queue-first, improve status copy |
| Backend/API/data consistency | 70% | High | Backend has richer pricing/admin paths, but mobile pricing can diverge | `backend/main.py`, `backend/price_catalog.py`, `PcosinaApiService.kt`, `MealPlanRepository.kt` | Align API contracts and client estimators |
| Overall app polish readiness | 62% | High | Strong implementation, but state clarity and unit realism are not production-polished | Cross-cutting | Phase fixes before visual polish |

## 3. Root Cause Map

### Unclear user-facing wording

- `MealPlanRefinedScreen.kt` exposes "Plan rule contract" and "Primary-user plan".
- `MealPlanRepository.kt` maps network failures to backend/Render/emulator/VPN wording.
- `ProgressRefinedScreen.kt` uses labels like "out of target" without explaining ranges or whether deviations are acceptable.
- `SettingsScreen.kt` shows permission and reminder internals too prominently.
- `CommunityScreen.kt` says online feedback is "queued" even when it is immediately attempted afterward.

### Incomplete wiring or click-handler uncertainty

- Generate New Week has a handler, but it is deliberately disabled until renewal eligibility is met.
- Settings profile summary switches use `onCheckedChange = null`, so they look like controls but are status indicators.
- Support "Make the week feel lighter" is clickable only through the inner "Let's Go!" pill and depends on `onOpenMealPlan`.

### Stale or legacy UI routes

- Current navigation imports refined screens only. `Routes.kt` does not define old dashboard/meal-plan route names.
- Legacy screen source still exists under `old-files/legacy-ui-screens/*`, and compatibility wrappers still exist in app source. The likely risk is stale compiled routes or old persisted route state, not active current navigation.

### Inconsistent state source

- Home opens Recipe Details directly from meal cards.
- Recipe Details determines if a recipe is in today's plan, but it does not treat skipped planned meals the same way the Plan screen does.
- Progress logs store completed and skipped IDs separately, while Home uses a combined "isLogged" boolean for completed or skipped.

### Weak pantry quantity model

- `PantryEntry.quantity` is free text and `expiryDate` is free text.
- Pantry matching can identify full, partial, and name-only matches, but the UI does not guide the user through unit compatibility.
- Partial pantry coverage is informational only in totals.

### Unrealistic price catalog and unit conversion

- Android `PriceCatalog` uses generic category piece weights for "piece" and "clove".
- `GroceryAggregation.kt` knows garlic clove is about 5g for aggregation, but cost estimation still sums each original clove segment using Android `PriceCatalog`, which treats each clove as a generic produce piece.
- Backend can use database price rules, but the Android client has its own static catalog and conversion logic.

### Unclear progress/check-in actionability

- Check-ins are meaningful locally, and `ProgressNextPlanAdjustmentUseCase.kt` can produce next-plan suggestions.
- `PlannerProfilePreparationUseCase.kt` can apply feedback tags like "Too expensive", "Too repetitive", and symptom/energy support.
- The UI does not clearly distinguish "tracking only" from "will affect next plan after you apply it".

### Reminder/notification uncertainty

- `NotificationScheduler.kt` schedules one-time WorkManager jobs and re-enqueues after firing.
- Delivery requires master toggle, Android permission, type-specific toggle, valid user session, quiet-hour pass, and last-fired interval.
- Empty notification history means no delivered notification was logged, not necessarily that scheduling is broken.

### Local/cloud persistence uncertainty

- Logout clears auth session and resets ViewModels, but does not delete per-user DataStore artifacts.
- Uninstall or clear app data wipes local data.
- Cloud restore exists for profile and several artifacts, but it is best-effort and depends on Firestore sync success.

### Layout responsiveness problems

- Header cards use fixed heights and one-line title constraints.
- Goal cards use full rows instead of compact chips.
- Progress macro rings and level circles use fixed widths that can wrap/compress.
- Support directory tiles are nested in tight rows.

## 4. Detailed Issue Analysis

### A. Profile Editing and Saving

Observation: Saving profile sometimes does not work after changing goals and selecting all three goals.

Current code behavior:

- `UserProfile.goal` is a single `String`, but `GoalSemantics.kt` explicitly parses comma-separated multiple goals and converts selected options back into a comma-delimited label/API string.
- `GoalSelectionScreen.kt` supports selecting any combination of Weight Loss, Symptom Management, and General Health.
- `UserViewModel.updateGoal()` saves the updated profile immediately.
- `UserProfileScreen.kt` only calls the primary action when `canProceed` is true. If disabled, the Save button cannot be clicked, and the current error message appears below the form rather than as a clear save failure.
- `UserViewModel.saveProfile()` and related update methods do not expose a `Saving`, `Saved`, or `Failed` state to the screen.
- `UserPreferencesRepository.updateProfile()` writes local DataStore first, then attempts cloud sync. Cloud failure is logged and not shown to the user.

Why it matters:

- The user cannot tell whether Save is disabled, validation failed, local save failed, cloud sync failed, or the UI simply did not respond.

Likely root cause:

- Not all-three-goals incompatibility. Multi-goal selection is supported in code.
- More likely: hidden validation blocker, disabled action, async save without visible status, or a navigation expectation mismatch.

Likely files/components:

- `app/src/main/java/com/pcosina/app/ui/screens/UserProfileScreen.kt`
- `app/src/main/java/com/pcosina/app/ui/screens/GoalSelectionScreen.kt`
- `app/src/main/java/com/pcosina/app/ui/UserViewModel.kt`
- `app/src/main/java/com/pcosina/app/data/model/UserProfile.kt`
- `app/src/main/java/com/pcosina/app/ui/util/GoalSemantics.kt`
- `app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt`

Recommended direction: Fix.

Solution options:

- Add explicit profile save UI state: "Saving...", "Saved on this device", "Saved locally. Cloud sync will retry", and "Fix these fields before saving".
- On disabled save, show the exact validation blocker near the button.
- Add a small success banner after edit-mode saves before returning to Settings.
- Replace manual target date with a date picker.
- Keep multi-goal selection, but show selected goals as chips.

What not to do:

- Do not remove multi-goal support unless backend/planner evidence shows it is unsafe.
- Do not make cloud sync mandatory for saving, because that would break offline-first behavior.

Verification test:

- In edit mode, select all three goals, change target date/weight/budget, tap Save, then reopen Settings and Profile. Confirm local state changed.
- Repeat offline and confirm "saved locally" copy.
- Add invalid target date and confirm Save shows a clear validation message.

### B. Plan Screen Cleanup and Behavior

Observation: Internal labels should be removed, add-to-grocery is redundant, today statuses are confusing, and Generate New Week appears broken.

Current code behavior:

- `MealPlanRefinedScreen.kt` renders `MealPlanContractCard` with title "Plan rule contract" and fields/classification/enforcement from backend `plannerContract`.
- The daily summary displays a "Primary-user plan" pill.
- Recipe Details has an "Add ingredients to Grocery" action even though Grocery already rebuilds from plan recipe sources.
- Generate New Week is disabled when `planRenewalEligible` is false. Eligibility requires no current plan, today after plan end, or final plan day with all final-day meals logged/skipped.
- When disabled, the card subtitle says renewal unlocks later, but the button itself looks like a broken inactive control.
- `MealLoggingPolicyUseCase.kt` only blocks non-today dates and meal sequence. It does not enforce a time-of-day lock.

Why it matters:

- Users read disabled controls as bugs unless the reason is next to the button.
- Internal contract language undermines polish and user trust.
- Redundant grocery actions create duplicate mental models.

Likely root cause:

- Developer/debug transparency and real policy controls are shown directly rather than converted to user-facing copy.

Likely files/components:

- `MealPlanRefinedScreen.kt`
- `MealPlanViewModel.kt`
- `MealLoggingPolicyUseCase.kt`
- `RecipeDetailsScreen.kt`
- `GroceryViewModel.kt`
- `PcosinaApiService.kt`

Recommended direction: Fix and remove/rewrite.

Solution options:

- Hide "Plan rule contract" by default or replace it with "Why this plan fits you" using plain text.
- Remove repeated "Primary-user plan" pills from meal/day cards. Keep household/primary-user explanation only once in onboarding/settings if needed.
- Remove "Add ingredients to Grocery" from recipe details for planned meals.
- Change disabled Generate New Week copy to "Available after [date]" or "Finish or skip the final day meals to start a new week".
- Replace meal card states with: Today, Up next today, Ready to log, Logged, Skipped, Future day, Past day, Locked until earlier meal is handled.

What not to do:

- Do not allow unlimited regeneration without policy discussion. It may break weekly continuity, grocery estimates, and progress history.
- Do not remove hard-constraint transparency from backend data; just avoid exposing it as developer wording.

Verification test:

- With an active week, tap Generate New Week before eligible. Expected: disabled reason and unlock date.
- On final day after logging/skipping all meals, confirm Generate New Week enables.
- Open a recipe from a planned meal and confirm no redundant add-to-grocery action.

### C. Grocery Screen, Pantry Logic, Quantities, Expiry, and Pricing

Observation: Pantry list works but quantity, expiry, "Pantry?", partial deduction, and prices are confusing.

Current code behavior:

- `PantryEntry` stores `name`, free-text `quantity`, and free-text `expiryDate`.
- `GroceryAggregation.buildPantryCoverage()` returns `Full`, `Partial`, or `NameOnly`.
- Only `Full` pantry coverage auto-checks the grocery item. `NameOnly` becomes a "Pantry?" state because quantity is unknown.
- Partial coverage displays remaining quantity detail but does not reduce `totalEstimated`.
- `GroceryRefinedScreen.kt` calculates total cost by excluding checked/full pantry items only.
- Expired pantry entries are excluded from coverage and an ML event can be logged, but expiry is not otherwise made actionable.
- Category sections are shown prominently and can be collapsed/expanded.

Why it matters:

- Pantry guidance is a core promise. If users enter "1 piece garlic" and the system needs "100g garlic", the app cannot safely assume coverage.
- If users enter "80g garlic" and the list needs "100g", showing the full price and full grocery item overstates what to buy.

Likely root cause:

- Pantry is currently a light free-text helper, while users expect it to be a real inventory and deduction system.

Likely files/components:

- `GroceryRefinedScreen.kt`
- `GroceryAggregation.kt`
- `GroceryViewModel.kt`
- `GroceryRebuildUseCase.kt`
- `PantryEntry.kt`
- `UserPreferencesRepository.kt`
- `PriceCatalog.kt`

Recommended direction: Redesign.

Solution options:

- Replace quantity free text with amount plus unit dropdown.
- Use ingredient-specific default units: garlic can default to clove/head/g; eggs to pieces; oil to ml/tbsp; rice to kg/cup; meat to g/kg.
- Make expiry optional and hide it under "advanced" or "freshness reminder".
- Use pantry coverage states:
  - In pantry: enough quantity with compatible units.
  - Partly covered: compatible units but not enough quantity; show remaining amount and reduced estimate.
  - Confirm match: same ingredient name but missing/incompatible quantity.
  - Not in pantry: normal grocery item.
- Deduct pantry quantity and price only when units are compatible.
- Keep categories as a filter, not a dominant section, unless user testing shows they help.

What not to do:

- Do not auto-check "Pantry?" name-only items. That can hide items users still need to buy.
- Do not make expiry required for weekly planning.

Verification test:

- Add garlic with no quantity. Grocery garlic should show confirm match, not checked.
- Add garlic 80g when grocery needs 100g. Grocery should show 20g remaining and reduced price.
- Add garlic 1 piece when grocery needs 100g. Grocery should ask for confirmation or conversion.

### D. Home Screen UI

Observation: Goal cards are too large, do not match the daily tips card, and goal text can clip.

Current code behavior:

- `DashboardRefinedScreen.kt` shows goals and tips side by side when width allows.
- `RefinedGoalsCard` renders each goal as a full row/pill.
- `RefinedContentCard` title rows use one-line text with ellipsis and can compress around trailing actions.

Why it matters:

- Goal cards are always visible on Home, so their uneven sizing makes the app feel less refined.

Likely files/components:

- `DashboardRefinedScreen.kt`
- `PcosinaSharedChrome.kt`
- `GoalSemantics.kt`

Recommended direction: Redesign compactly.

Solution options:

- Render selected goals as chips or a compact summary: "3 goals selected".
- Keep goal detail available on tap or in Settings.
- Allow the title to wrap or remove trailing text from the goal card.
- Match visual density with the daily tip card.

What not to do:

- Do not enlarge the tips card just to match the goal card. Reduce the goal card instead.

Verification test:

- Test Home with 1, 2, and 3 goals on compact and large widths. No clipped "Your Goals" title and no oversized goal rows.

### E. Progress Screen and Weight Support

Observation: Progress cards are crowded, macro targets are unclear, weekly savings graph is weak, and history/editing expectations need clarity.

Current code behavior:

- `ProgressRefinedScreen.kt` has Today/Week modes and several cards: headline, weight support, weekly highlights, calendar, adherence, savings, average macros, plan feedback, next-plan adjustments, symptom trends, and bottom CTA.
- Weekly savings compares budget against actual spend if present, else against estimated weekly plan cost.
- Average macros uses plan averages and target values from backend explanation, then displays mini rings with "out of target".
- History calendar displays local logs, check-ins, reflections, and weekly record markers. It does not reopen exact past meal UIs for editing.
- Advanced weekly cards remember expansion preference through `progressAdvancedAnalyticsExpanded`.
- `WeightGoalGuardrailUseCase.kt` and weight support are optional, shown when weight-loss goal or weight target exists.

Why it matters:

- Users may interpret target deviations as "bad plan" rather than acceptable planning tolerance.
- Past-day history being read-only is probably correct, but the UI must say it clearly.

Likely root cause:

- Progress combines tracking, evaluation, planner tuning, and history in one dense screen without clear hierarchy.

Likely files/components:

- `ProgressRefinedScreen.kt`
- `ProgressViewModel.kt`
- `ProgressSummaryUseCase.kt`
- `ProgressNextPlanAdjustmentUseCase.kt`
- `PlannerProfilePreparationUseCase.kt`
- `WeightGoalGuardrailUseCase.kt`

Recommended direction: Redesign.

Solution options:

- Keep Today and Week modes if they reduce clutter; remove if the toggle label itself clips.
- Default advanced cards collapsed on fresh screen open, or persist only within a session.
- Explain macros as ranges/tolerances: "Target is a guide, not an exact requirement. The planner balances protein, carbs, fiber, budget, and variety."
- Redesign savings graph as grouped bars: Budget, Plan estimate, Actual spend.
- Make history calendar copy explicit: "Past days are for review. Edit today's logs only."
- Rework weight support copy into actions: "Your pace is above the safe weekly range. Use Nutrition Tight or adjust target date."

What not to do:

- Do not allow editing past exact meal entries without a deliberate data integrity design.
- Do not remove check-in data just because the current card copy is weak.

Verification test:

- Open Progress with no logs, some logs, and a full week. Confirm cards are useful and not wordy.
- Verify macros explain why protein can be below target or carbs above target without implying medical failure.
- Confirm history calendar cannot mislead users into thinking past meal details are editable.

### F. Check-ins and Weekly Review

Observation: Check-ins collect energy, mood, cravings, hair loss, symptoms, spending, and notes, but users need to know why.

Current code behavior:

- `DailyLog` stores daily reflection, symptoms, weight, completed/skipped meals, and meal check-ins.
- `ProgressSummaryUseCase.kt` builds weekly adherence, trend summaries, and 4-week check-in history.
- `ProgressNextPlanAdjustmentUseCase.kt` can suggest next-plan tags from weight trend, cravings, energy, and symptom severity.
- `PlannerProfilePreparationUseCase.kt` applies plan feedback tags to planner profile: tighter nutrition, budget-first, quick prep, higher variety, symptom support.
- Weekly review stores notes and actual spending.

Why it matters:

- Users will stop entering check-ins if nothing visible happens.

Likely root cause:

- There is real plumbing from check-ins to recommendations, but the UI copy under-explains it.

Likely files/components:

- `DailyLog.kt`
- `MealCheckIn.kt`
- `ProgressViewModel.kt`
- `ProgressSummaryUseCase.kt`
- `ProgressNextPlanAdjustmentUseCase.kt`
- `PlannerProfilePreparationUseCase.kt`
- `ReflectionStore.kt`
- `UserPreferencesRepository.kt`

Recommended direction: Redesign/polish, not remove.

Solution options:

- Add a simple line: "Check-ins stay private on this device and can suggest next-plan adjustments when you apply them."
- Turn weekly highlights into action cards with one next step.
- Make plan feedback tags show exactly what they will change next time.
- Keep symptoms wellness-oriented, not diagnostic.

What not to do:

- Do not claim the app diagnoses PCOS symptom causes.
- Do not auto-apply symptom-driven plan changes without user approval.

Verification test:

- Submit three low-energy check-ins and confirm the next-plan adjustment card appears.
- Apply the suggestion and generate a plan. Confirm `PlannerProfilePreparationUseCase` applies the expected tag.

### G. Support Page

Observation: Support page has redundant text, compressed directory UI, and a vague "Make the week feel lighter" card.

Current code behavior:

- `CommunityScreen.kt` renders `SharedAvatarHeader` title "Support", then a separate "Support Page" text.
- It shows two `SupportVideoCard`s, a feedback card, `SupportDirectoryCard`, and `SupportFreshStartCard`.
- `SupportFreshStartCard` opens Meal Plan only through its inner button if `onOpenMealPlan` is provided.
- `SupportDirectoryCard` is informational only and uses compact two-column tiles.

Why it matters:

- Support should reduce friction. Redundant and compressed help content adds friction.

Likely files/components:

- `CommunityScreen.kt`
- `AppNavHost.kt`
- `MoreToolsScreen.kt`

Recommended direction: Remove and redesign.

Solution options:

- Remove the "Support Page" text.
- Decide whether App Directory is needed. If kept, turn it into tappable "Go to Home/Plan/Grocery/Progress" rows.
- Make "Make the week feel lighter" either a working shortcut to Meal Plan/Progress or remove it.

What not to do:

- Do not keep duplicate support cards that lead to the same screen without clear purpose.

Verification test:

- Tap every support CTA. Each must either navigate or clearly explain what it does.

### H. Settings and Account Screen

Observation: Reminder symptom/alert controls appear not working, goals compress, and notification status is confusing.

Current code behavior:

- Settings profile summary shows display-only toggles for Symptom Management, Reminders, and Alerts. They use `Switch(onCheckedChange = null)`.
- Main goal is shown as `primaryGoalLabel(profile.goal)`, which can be multiple comma-separated goals.
- Reminder settings update `NotificationPreferences` and call `NotificationScheduler.rescheduleAll`.
- "Saved meal times" is a repeated summary after editable rows.
- Notification permission copy is visible as "Phone permission: Allowed/Blocked".

Why it matters:

- Display-only switches are perceived as broken controls.
- Reminder setup needs to be testable and understandable.

Likely files/components:

- `SettingsScreen.kt`
- `NotificationPreferences.kt`
- `NotificationScheduler.kt`
- `NotificationHelper.kt`
- `NotificationScreen.kt`
- `UserViewModel.kt`
- `UserPreferencesRepository.kt`

Recommended direction: Fix and redesign.

Solution options:

- Convert display-only switches into status rows or make them real controls that navigate to the correct settings.
- Replace "Main goal" value with compact chips or "3 selected".
- Remove prominent "Phone permission allowed" copy from meal reminders; keep permission status under a diagnostics/detail row.
- Remove redundant "Saved meal times" if the editable rows already show saved times.
- Add a "Send test notification" debug/test action behind development/admin mode or QA build.

What not to do:

- Do not remove notification logs. They are useful evidence.
- Do not claim notifications are broken until a permission/channel/worker test is run.

Verification test:

- Enable master reminders, grant Android permission, set lunch one minute ahead, and confirm delivery/log entry.
- Disable master reminders and confirm scheduled work is canceled.
- Open Notification screen before and after delivery; confirm empty-state copy is accurate.

### I. General App Syncing and Meal Logging

Observation: Dinner/home card can log even if a planned meal was skipped or not synced consistently.

Current code behavior:

- Home meal cards open Recipe Details with recipe ID and meal label.
- `RecipeDetailsScreen.kt` checks if the recipe is in today's plan, but skipped meals still count as planned.
- `ProgressViewModel.markMealAsEaten()` removes matching skipped IDs when logging.
- Plan screen blocks logging skipped meals and says "Undo skip before logging it."
- Home uses a combined `isLogged` value for completed or skipped meals.

Why it matters:

- Meal state is a core trust area. A skipped meal should not silently become logged from another screen.

Likely root cause:

- No single shared meal status model across screens.

Likely files/components:

- `DashboardRefinedScreen.kt`
- `RecipeDetailsScreen.kt`
- `MealPlanRefinedScreen.kt`
- `ProgressViewModel.kt`
- `MealLoggingPolicyUseCase.kt`
- `TodayLogSnapshot` usage

Recommended direction: Critical fix.

Solution options:

- Introduce a shared UI/domain meal status: Planned, AvailableToday, LockedBySequence, Logged, Skipped, Future, Past.
- Recipe Details should respect skipped state and require explicit Undo Skip before Log.
- Home should not treat skipped as logged; it should show "Skipped" separately.

What not to do:

- Do not duplicate status rules per screen.
- Do not silently mutate skipped to logged.

Verification test:

- Skip dinner in Plan, open dinner from Home/Recipe Details, and attempt logging. Expected: blocked or explicit undo flow.

### J. Avatar, Assets, and UI Consistency

Observation: Avatar clothes look transparent, header text cuts off, support header sizing differs, and background image alignment is hard to tune.

Current code behavior:

- `PcosinaAvatar.kt` renders PNG avatar assets with `Image(..., contentScale = Fit)` and no alpha/tint.
- Therefore transparent clothing is likely in the PNG asset itself, unless a parent surface/background creates visual blending.
- `SharedAvatarHeader` uses fixed height, fixed left padding, one-line title, and clipped subtitle.
- Several screens use shared headers but with different compact flags and surrounding padding.
- Background/hero images are manually positioned per component.

Why it matters:

- Avatar/header is a repeated brand element. If it clips or looks transparent, every screen feels less trustworthy.

Likely files/components:

- `PcosinaAvatar.kt`
- `PcosinaSharedChrome.kt`
- `CommunityScreen.kt`
- `DashboardRefinedScreen.kt`
- `MealPlanRefinedScreen.kt`
- `ProgressRefinedScreen.kt`
- `GroceryRefinedScreen.kt`
- `app/src/main/res/drawable-nodpi/avatar_*.png`
- `app/src/main/res/drawable-nodpi/pcosina_*background*.png`

Recommended direction: Verify asset, then polish.

Solution options:

- Inspect PNG alpha channel and replace/fix avatar assets if clothing pixels are transparent.
- Increase header height or allow subtitle/title wrapping where needed.
- Add per-screen image placement constants: `contentScale`, `alignment`, `xOffset`, `yOffset`, and size.
- Keep transparent background for assets, but not transparent subject/clothing pixels.

What not to do:

- Do not tint avatar PNGs globally. It can hide the real asset problem.

Verification test:

- Render avatar PNGs on dark and light backgrounds.
- Screenshot Support, Progress, Grocery, and Dashboard headers on compact width.

### K. Feedback Queue and Sync Behavior

Observation: Online feedback says "queued for sending" instead of "sent".

Current code behavior:

- `CommunityScreen.kt` always calls `onFeedback(trimmedFeedback, isOnline)`, clears text, then shows "Feedback queued for sending" if online.
- `AppNavHost.kt` maps feedback to `progressViewModel.queueFeedback(message)` then `trySendQueuedFeedback(isOnline)`.
- `ProgressViewModel.trySendQueuedFeedback()` attempts to send queued entries and removes successful entries.
- `FeedbackRepository.kt` posts to `/feedback`.
- `backend/main.py` has a `/feedback` endpoint with rate limiting, App Check/schema dependencies, and database persistence.

Why it matters:

- Queue-first is good offline-first design, but online users expect a success or failure signal.

Likely files/components:

- `CommunityScreen.kt`
- `AppNavHost.kt`
- `ProgressViewModel.kt`
- `FeedbackRepository.kt`
- `backend/main.py`
- `backend/database.py`

Recommended direction: Keep queue-first, improve status.

Solution options:

- Show "Sending..." immediately when online.
- After queue drains, show "Feedback sent".
- If still queued after a short delay, show "Saved and will sync shortly".
- Offline: "Saved offline. It will send when you are back online."
- Failed: "Could not send. Saved for retry."

What not to do:

- Do not bypass the local queue for online sends; it protects against flaky network and app restarts.

Verification test:

- Send feedback online and confirm backend row appears and queue clears.
- Send offline and confirm queue persists, then comes online and drains.

### L. Onboarding and Profile Setup Polish

Observation: Onboarding/profile scroll overextends; weight management may be optional; target date should use a date picker.

Current code behavior:

- `UserProfileScreen.kt` uses a full-height vertical scroll column plus bottom bar with `imePadding()` and `navigationBarsPadding()`.
- `keyboardScrollPadding` adds 112dp when IME is visible and 24dp otherwise.
- Focused fields call `bringIntoView()` multiple times.
- Target date is a text field in `YYYY-MM-DD` format.
- Weight target fields are optional.

Why it matters:

- Manual date entry and over-scroll make profile setup feel unfinished.

Likely files/components:

- `UserProfileScreen.kt`
- `WeightGoalGuardrailUseCase.kt`
- `UnitConverter.kt`

Recommended direction: Polish.

Solution options:

- Replace target date text input with a date picker.
- Normalize bottom padding so final section has enough space but does not overextend.
- Keep weight management optional; only show weight support when goal/target makes it relevant.

What not to do:

- Do not force target weight/date for non-weight-loss goals.

Verification test:

- Complete onboarding with keyboard open on smallest supported device size. Ensure final section and Save button are reachable without excessive blank scroll.

### M. Offline Behavior and Legacy UI Risk

Observation: Offline mostly works, but a legacy UI appeared once; generation failure messages mention internal backend details.

Current code behavior:

- Current `AppNavHost.kt` imports refined screens, not old-files legacy screens.
- `Routes.kt` only defines refined route names.
- `old-files/legacy-ui-screens/*` still exist outside current app source.
- `LegacyScreenCompatibility.kt` and `ProgressLegacySupport.kt` still exist in app source as compatibility wrappers/helpers.
- `MealPlanRepository.mapGeneratePlanException()` can show DNS/backend host, Render fallback, emulator internet, VPN, and Private DNS wording.

Why it matters:

- A user-facing offline failure should be simple: "Internet connection is needed to generate a new plan."
- Any reachable legacy UI damages consistency.

Likely root cause:

- Active route registration appears refined, so legacy appearance may be stale build, old back stack, deep link, or compatibility wrapper path. Needs reproduction.

Likely files/components:

- `AppNavHost.kt`
- `Routes.kt`
- `LegacyScreenCompatibility.kt`
- `ProgressLegacySupport.kt`
- `old-files/legacy-ui-screens/*`
- `MealPlanRepository.kt`
- `MealPlanViewModel.kt`

Recommended direction: Study and fix wording.

Solution options:

- Replace network generation errors with user-first copy and optional diagnostics hidden behind "Details".
- Confirm legacy files are not compiled or routed. Remove compatibility wrappers only after confirming no test depends on them.
- Clear old navigation state during QA reinstall tests.

What not to do:

- Do not delete old-files blindly during audit; user asked for no changes.
- Do not remove offline cached plan reading.

Verification test:

- Install fresh, generate plan, go offline, open every tab and meal detail. Confirm no old "at glance" UI.
- Offline Generate Plan should show simple internet-required copy.

### N. Account Persistence After Logout, Uninstall, Reinstall, and Login

Observation: Need to know what survives logout/reinstall/login.

Current code behavior:

- `AuthRepository.logout()` signs out Firebase/Google and clears auth session only.
- Per-user DataStore/profile/artifact data is not deleted on logout.
- `AppNavHost.kt` resets ViewModels on logout, then reloads user-specific data on login.
- `UserPreferencesRepository.syncProfileWithCloud()` attempts local/remote merge for profile and artifact domains: pantry, plan, grocery, feedback, progress UI, and notification preferences.
- Local app data is wiped by uninstall or clear app data.
- Cloud restore depends on successful Firestore sync and same Firebase UID.

Expected survival matrix:

| Scenario | Expected current behavior |
|---|---|
| App close/reopen | Local DataStore and secure artifact files should survive |
| Logout/login same account on same install | Local per-user data should survive because logout does not clear it |
| Offline restart | Local saved data should survive |
| Clear app data | Local data wiped; only cloud restore can bring data back |
| Uninstall/reinstall | Local data wiped; only cloud restore can bring data back |
| New device login | Only cloud-synced profile/artifacts can restore |

Recommended direction: Clarify and verify.

What not to do:

- Do not promise uninstall restore unless cloud sync is verified for the exact data type.
- Do not make cloud required for local use.

Verification test:

- Run the matrix above with a test account and record which profile, plan, pantry, grocery, progress, notification, and feedback values return.

### O. Pricing and Market Realism

Observation: Garlic and meat prices can be unrealistic. Weekly costs can exceed PHP 3,000.

Current code behavior:

- Android `PriceCatalog.kt` contains static rules and generic category piece weights.
- `GroceryAggregation.kt` has ingredient-specific count-unit weights for aggregation, including garlic clove/piece/head.
- But cost estimation sums original quantity segments with Android `PriceCatalog.estimatePriceDetail(displayName, segment)`, so many cloves can be priced as many generic produce pieces.
- Backend `backend/price_catalog.py` has similar static rules plus optional database price-rule overrides.
- Local DB price rules observed include garlic around PHP 140/kg and meat rules such as chicken, pork, beef, fish, and shrimp.
- Backend direct estimate for "garlic 130g" is reasonable, while Android aggregation can make clove-based grocery totals high.
- Backend recipe cost clamps and buffers per recipe, while mobile grocery estimates are client-side and not guaranteed to match backend weekly cost.

Why it matters:

- Price trust is central to Filipino market usefulness. If garlic or weekly meat cost looks absurd, users may distrust the whole plan.

Likely files/components:

- `PriceCatalog.kt`
- `GroceryAggregation.kt`
- `GroceryRebuildUseCase.kt`
- `backend/price_catalog.py`
- `backend/database.py`
- `backend/services/meal_planner.py`
- `backend/recipes.json`

Recommended direction: Critical pricing correction, then catalog realism pass.

Solution options:

- Estimate grocery cost from aggregated normalized quantity, not by summing every original count segment.
- Share unit conversion tables between backend and Android, or generate Android price/unit data from backend seed.
- Add ingredient-specific count weights to Android `PriceCatalog`, not only `GroceryAggregation`.
- Add price confidence labels: exact, estimated, needs review.
- Add market-friendly units and PH ranges: garlic clove/head/kg, onion piece/kg, egg piece/tray, rice kg, chicken/pork/beef/fish kg, oil ml/liter.
- Add tests for garlic 1 clove, 1 head, 130g, 80g vs 100g pantry, and weekly meat-heavy plans.

What not to do:

- Do not simply cap weekly totals without fixing unit math.
- Do not hide prices if they are wrong; correct the estimator.

Verification test:

- Add a plan with many garlic cloves and confirm grocery display aggregates to grams/head/clove and cost stays realistic.
- Compare Android grocery estimate to backend estimate for the same ingredient list.

## 5. Feature-by-Feature Decision Table

| Feature | Decision | Reason |
|---|---|---|
| Plan rule contract | Hide or redesign | Current wording is internal; convert to "Why this plan fits you" if kept |
| Primary user plan label | Remove repeated labels | Useful concept, but repeated label is confusing |
| Add ingredients to grocery | Remove from planned recipe cards | Grocery already syncs from plan sources |
| Generate new week card | Keep, clarify disabled policy | Button is policy-locked, not broken |
| Pantry quantity | Redesign | Free text is too ambiguous for deduction |
| Pantry expiry | Hide under optional advanced | Useful for freshness but too heavy as primary input |
| Grocery categories | Needs study first | Could help scanning, but currently adds clutter |
| Pantry question-mark status | Redesign | Should say "Confirm pantry match" |
| Weekly savings graph | Redesign | Current line chart is visually weak for budget/plan/actual comparison |
| Average macros card | Keep, rewrite | Useful, but target copy must explain tolerance |
| Scan feedback card / plan feedback | Redesign | Tags affect next plan, but wording is too detached |
| Today check-in | Keep and clarify | Data supports trends and possible next-plan suggestions |
| Weekly review | Keep and improve | Spending and feedback tags can guide planning |
| Weekly highlights | Redesign | Current recap needs one clear action per highlight |
| Make week feel lighter | Remove or make full-card CTA | Current purpose is vague |
| Add directory / App Directory | Needs study first | Keep only if tappable or clearly useful |
| Reminder toggles | Keep, clarify real vs display-only | Some are status indicators, not controls |
| Save meal times | Remove/rewrite | Redundant after editable time rows |
| Keep-going nudges | Keep after testing | Real engagement worker exists, but copy needs clearer purpose |
| Notification screen | Keep | It is the delivery log/status page; empty state needs explanation |
| History calendar | Keep, clarify read-only | Useful for review without corrupting past meal state |
| Legacy offline meal screen | Remove route access after verification | Current nav does not route to old-files, but compatibility risk must be tested |

## 6. Pantry, Grocery, and Pricing Deep Study Plan

Current logic summary:

- Meal plan recipe sources feed Grocery through `GroceryViewModel.setPlanSources()`.
- `GroceryAggregation.kt` canonicalizes ingredient names, aggregates quantities, estimates category/cost, and compares against pantry entries.
- Pantry coverage has three states: full, partial, and name-only.
- Only full coverage auto-checks and removes cost from total.
- Pricing is client-side for grocery and backend-side for plan estimates.

Known weaknesses:

- Pantry quantity and expiry are free text.
- Partial coverage does not reduce remaining grocery quantity or cost.
- Name-only coverage creates ambiguous "Pantry?" UI.
- Unit compatibility is hidden from users.
- Android and backend price/unit behavior diverge.
- Count units such as clove/piece can become unrealistic.

Worst-case scenarios:

- User enters "garlic 1 piece"; grocery needs "100g garlic"; app incorrectly treats or prices it.
- User enters "80g garlic"; grocery needs "100g"; app still shows full cost and full item.
- User enters "1 pack chicken"; grocery needs "500g chicken"; app cannot compare.
- User enters expired pantry item; app excludes it from coverage but user does not understand why.
- User sees weekly grocery total above PHP 3,000 and assumes plan is unusable.

Recommended future data model:

- `PantryEntry(name, amount: Double?, unit: String?, expiryDate: LocalDate?, confidence, notes)`
- `GroceryNeed(name, requiredAmount, requiredUnit, normalizedBaseAmount, baseUnit, estimatedCost, priceConfidence)`
- `PantryCoverage(required, covered, remaining, unitCompatible, status)`

Unit handling proposal:

- Store canonical base units for matching: grams for solids, ml for liquids, count for discrete items.
- Support display units: g, kg, ml, l, piece, clove, head, cup, tbsp, tsp, pack, can, bunch.
- Maintain ingredient-specific conversions for common Filipino market items.
- Ask for confirmation when conversion is unknown.

Price correction proposal:

- Price aggregated normalized quantities, not original segments.
- Move price/unit tables to shared generated data or one backend source.
- Add min/max realistic PH ranges by ingredient.
- Flag low-confidence estimates rather than pretending exactness.

Deduction logic proposal:

- Full compatible coverage: auto-mark as in pantry and subtract full cost.
- Partial compatible coverage: show remaining amount and subtract proportional covered cost.
- Name-only coverage: require user confirmation and do not subtract cost.
- Incompatible units: show "Need unit" action.

UX proposal:

- Add pantry item with fields: item, amount, unit, optional expiry.
- Show unit hint per ingredient.
- Replace "Pantry?" with "Confirm pantry match".
- Add "Use pantry first" summary explaining what was deducted.

Testing matrix:

| Case | Pantry input | Grocery need | Expected |
|---|---|---|---|
| Name only | garlic | 100g garlic | Confirm match, no deduction |
| Partial grams | garlic 80g | 100g garlic | 20g remaining, partial deduction |
| Full grams | garlic 120g | 100g garlic | In pantry, full deduction |
| Count compatible | garlic 20 cloves | 100g garlic | Full or partial using clove conversion |
| Count incompatible | chicken 1 pack | 500g chicken | Needs confirmation |
| Expired | milk 1L expired | 250ml milk | Excluded with freshness warning |

## 7. Progress and Check-in Deep Study Plan

Purpose by card:

- Headline card: quick adherence and BMI/summary snapshot.
- Weight support: optional goal/pace guardrail.
- Weekly highlights: recap budget, balance, energy, and consistency.
- Calendar: review local history, not edit past meals.
- Weekly adherence: planned vs completed meals.
- Weekly savings: budget vs estimate/actual spend.
- Average macros: plan averages vs backend targets.
- Plan feedback: manually saved tags for future plan tuning.
- Next-plan adjustments: suggestions derived from local check-ins.
- Symptom trends: local check-in trend summary.
- Bottom CTA: daily check-in and weekly review entry point.

Data used:

- Planned meals from current/saved plan.
- Completed/skipped meals from local `DailyLog`.
- Daily reflection and symptom fields from local logs.
- Weekly spend/journal from local/secure reflection storage.
- Plan metrics from recipe details and backend explanation.
- Plan feedback tags from local preferences and cloud artifact sync.

Whether it affects next plan:

- Plan feedback tags can affect next plan through `PlannerProfilePreparationUseCase.kt`.
- Next-plan adjustment suggestions can add feedback tags after user taps Apply.
- Check-ins do not directly change a plan unless they generate/apply tags.

How to make clearer:

- Label tracking-only cards as "For review".
- Label tuning cards as "Can affect next plan after you apply".
- Add one action sentence per weekly highlight.
- Explain macro targets as guide ranges, not exact pass/fail.

Cards to collapse by default:

- Weekly adherence details.
- Weekly savings details.
- Average macros details.
- Symptom trends details.

Cards to remove or redesign:

- Standalone plan feedback card should merge with next-plan adjustments.
- Weekly savings graph should become a grouped comparison.
- Weekly highlights should become shorter action cards.

## 8. Reminder and Notification Deep Study Plan

Current scheduling logic:

- `SettingsScreen.kt` updates `NotificationPreferences` and calls `NotificationScheduler.rescheduleAll()`.
- `NotificationScheduler.kt` schedules one-time WorkManager jobs for breakfast, lunch, dinner, weekly reset, and engagement checks.
- Workers dispatch notifications, write local notification logs, and re-enqueue themselves.
- `NotificationHelper.kt` creates channels and checks Android notification permission.

Why notifications may not appear yet:

- Master reminders are off by default.
- Android 13+ notification permission may not be granted.
- App notifications may be blocked in system settings.
- WorkManager delay has not elapsed.
- Quiet hours can block dispatch.
- Last-fired interval can block duplicate reminders.
- User session validation can block delivery.
- Type-specific toggle may be off.

Required test steps:

1. Enable master reminders.
2. Grant Android POST_NOTIFICATIONS permission.
3. Confirm app notifications are allowed in system settings.
4. Set a meal reminder 1-2 minutes ahead.
5. Keep device awake enough for WorkManager test.
6. Confirm notification appears.
7. Open Notification screen and confirm a log entry.
8. Disable master reminders and confirm work is canceled.

UI recommendations:

- Replace prominent permission technical copy with "Notifications are ready" or "Turn on phone notifications".
- Add "Last delivered" and "Next scheduled" rows.
- Hide WorkManager diagnostic details under QA/developer mode.
- Convert display-only switches to status rows.

## 9. Offline and Sync Deep Study Plan

What works offline:

- Local profile, saved plan, grocery list, pantry entries, progress logs, feedback queue, notification preferences, and plan history should load from local storage.
- Feedback can be queued offline.
- Grocery can review synced plan ingredients offline.

What does not work offline:

- New plan generation requires backend access.
- Recipe swap options require internet.
- Cloud restore/sync requires internet.
- Feedback submission to backend requires internet.

Generation failure wording:

- Current lower-level messages can mention backend host, DNS, local debug backend, Render fallback, emulator internet, VPN, and Private DNS.
- User-facing copy should be: "Internet connection is needed to generate a new plan. You can still view saved plans and groceries offline."

Local persistence vs cloud restore:

- Local persistence survives app close and same-install logout/login.
- Uninstall and clear app data wipe local persistence.
- Cloud restore can restore only what was successfully synced to Firestore for the same UID.

Legacy UI route scan:

- Current active route map is refined.
- Legacy source under `old-files` is not imported into current `AppNavHost`.
- Compatibility wrappers still need route/back-stack QA.

## 10. Phased Action Plan

### Phase 0 - Confirm Current Logic and Reproduce Bugs

Tasks:

- Reproduce profile save issue with all three goals.
- Reproduce disabled Generate New Week.
- Reproduce skipped meal logging from Home/Recipe Details.
- Test online/offline feedback queue.
- Test notification delivery with near-future reminder.
- Test offline navigation for legacy UI.
- Capture garlic pricing examples from current app data.

Files likely inspected:

- `UserProfileScreen.kt`, `GoalSelectionScreen.kt`, `UserViewModel.kt`
- `MealPlanRefinedScreen.kt`, `RecipeDetailsScreen.kt`, `ProgressViewModel.kt`
- `GroceryRefinedScreen.kt`, `GroceryAggregation.kt`, `PriceCatalog.kt`
- `SettingsScreen.kt`, `NotificationScheduler.kt`
- `AppNavHost.kt`, `Routes.kt`

Dependency: none.  
Risk: no code risk, but requires device/emulator evidence.  
Expected result: confirmed bug list with screenshots/logs.  
Test method: manual QA plus targeted unit tests where available.

### Phase 1 - Critical Bug Fixes

Tasks:

- Add profile save state and validation feedback.
- Unify skipped/logged meal behavior between Plan and Recipe Details.
- Replace developer network generation messages with simple user copy.
- Fix Android grocery price estimation for count-unit aggregation, especially garlic.
- Make Generate New Week disabled state self-explanatory.

Dependency: Phase 0 reproduction.  
Risk: medium; touches core flows.  
Expected result: core app use no longer feels broken or misleading.  
Test method: manual flows plus focused unit tests for status/pricing.

### Phase 2 - Remove Redundant or Confusing UI

Tasks:

- Hide/remove Plan rule contract.
- Remove repeated Primary-user plan label.
- Remove planned recipe add-to-grocery action.
- Remove Support Page duplicate title.
- Remove/rewrite saved meal times and phone-permission clutter.

Dependency: Phase 1 for status clarity.  
Risk: low to medium; UI copy changes.  
Expected result: cleaner app with less internal wording.  
Test method: screenshot review across screens.

### Phase 3 - Pantry, Grocery, and Pricing Redesign

Tasks:

- Introduce structured amount/unit pantry model.
- Add unit dropdown and ingredient unit hints.
- Implement partial deduction for compatible units.
- Add confidence states for uncertain matches.
- Align Android/backend price catalog behavior.

Dependency: Phase 1 pricing correction.  
Risk: high; data model and persistence migration.  
Expected result: grocery list becomes trustworthy and market-realistic.  
Test method: unit conversion tests, pantry deduction tests, price snapshot tests.

### Phase 4 - Progress, Check-in, and Actionability Redesign

Tasks:

- Rewrite progress cards into tracking vs action sections.
- Collapse advanced cards by default.
- Redesign savings graph.
- Clarify macros and target tolerances.
- Merge plan feedback and next-plan adjustments.

Dependency: stable meal logging state.  
Risk: medium; lots of UI and copy.  
Expected result: Progress explains what happened and what to do next.  
Test method: seeded logs/screenshot scenarios.

### Phase 5 - Reminder and Notification Verification

Tasks:

- Add QA test route/action if appropriate.
- Verify WorkManager scheduling, channels, permission states, and logs.
- Rewrite empty notification state and settings copy.
- Convert display-only switches to status rows.

Dependency: device/emulator notification access.  
Risk: medium; Android version differences.  
Expected result: reminders are testable and user-clear.  
Test method: near-future reminder delivery test and log verification.

### Phase 6 - Offline, Sync, and Restore Clarification

Tasks:

- Document app close/logout/uninstall/new-device behavior.
- Add user-facing restore expectation copy if needed.
- Verify Firestore artifact restore for profile, plan, pantry, grocery, progress, notification prefs.
- Simplify offline generation failure.

Dependency: test account and network on/off testing.  
Risk: medium; sync is sensitive.  
Expected result: no false promises about uninstall/new-device restore.  
Test method: persistence matrix with screenshots.

### Phase 7 - Visual Polish and Consistency

Tasks:

- Compact Home goals.
- Fix SharedAvatarHeader clipping.
- Verify/fix avatar PNG alpha.
- Add per-screen background image alignment constants.
- Normalize support, grocery, progress, avatar header card sizing.

Dependency: content cleanup phases.  
Risk: low to medium.  
Expected result: app feels cohesive across screen families.  
Test method: screenshot review on compact and large devices.

### Phase 8 - Regression Testing and Final QA

Tasks:

- Run build/tests.
- Run manual QA checklist.
- Verify offline saved data.
- Verify safety constraints still hold.
- Verify no internal/developer wording remains in common user paths.

Dependency: all previous phases.  
Risk: low if phased well.  
Expected result: release-candidate behavior.  
Test method: automated tests plus real-device smoke test.

## 11. No-Code Verification Checklist

| Screen | Action | Expected Behavior | Evidence to Capture |
|---|---|---|---|
| Profile edit | Select all three goals and save | Profile saves locally; Settings shows selected goals | Before/after screenshots |
| Profile edit | Enter invalid target date | Save blocked with clear message near button | Screenshot |
| Meal Plan | Tap Generate New Week before eligible | Disabled reason or unlock date shown | Screenshot |
| Meal Plan | Final day all meals logged/skipped | Generate New Week becomes enabled | Screenshot |
| Meal Plan | Skip dinner, then try logging from Recipe Details | Skipped state is respected; no silent log | Screen recording |
| Grocery | Add pantry garlic no quantity | Grocery shows confirm match, not auto-checked | Screenshot |
| Grocery | Add pantry garlic 80g, need 100g | App should show current behavior before fix; capture mismatch | Screenshot |
| Grocery | Review garlic total price | Capture quantity and estimate | Screenshot |
| Progress | Open Today and Week modes | No clipped toggle text; cards understandable | Screenshots |
| Progress | Select a past calendar day | UI says history/review only | Screenshot |
| Progress | Add low-energy check-ins | See whether next-plan adjustment appears | Screenshot |
| Support | Tap every card/CTA | Each navigates or explains purpose | Screen recording |
| Settings | Tap display-only switches | Confirm whether they are noninteractive | Screen recording |
| Settings | Set meal reminder 1 minute ahead | Notification delivers and log appears | Notification screenshot |
| Notifications | Open before any delivery | Empty state explains no delivery yet | Screenshot |
| Feedback | Send online | Queue drains or status remains queued; backend row if available | Screenshot/log |
| Feedback | Send offline then reconnect | Feedback remains saved and sends later | Screenshot/log |
| Offline | Open all tabs and meal detail | No legacy UI appears | Screen recording |
| Offline | Generate new plan | Simple internet-required message | Screenshot |
| Persistence | Logout/login same account | Local data returns | Checklist |
| Persistence | Uninstall/reinstall same account | Only cloud-synced data returns | Checklist |

## 12. Questions Before Implementation

- Should PCOSina support multiple goals long-term, or choose one primary goal plus secondary goals?
- What exact policy should unlock Generate New Week: after 7 days, after final-day meals handled, manual override, or both?
- Should users be allowed to undo a skipped meal and log it later on the same day?
- Should past meal logs be editable at all, or should past days remain review-only?
- Which pantry units are required for MVP: g, kg, ml, l, piece, clove, head, cup, tbsp, tsp, pack, can?
- Should expiry date be hidden, optional, or used for warnings in the current release?
- What should happen when pantry unit conversion is uncertain?
- Should grocery cost deduct partial pantry quantities immediately, or only after user confirms coverage?
- What PH market source or maintained catalog should define price realism?
- Should Android pricing be generated from backend price catalog data?
- Should weekly plan cost represent one user only or household shopping cost?
- What is the maximum acceptable weekly grocery estimate for demo/initial market context?
- Should "Too expensive" remain a manual feedback tag if weekly review already records actual spend?
- Which check-in symptoms are necessary for wellness tracking, and which are too medical or too noisy?
- Should check-ins affect next planning automatically, or only after Apply?
- What reminder types are necessary for MVP: meals, weekly planning, inactivity, streak, grocery sync, plan ready?
- Should notification history show scheduled reminders, delivered reminders, or both?
- What should survive logout, uninstall, clear data, and new-device login?
- Is cloud restore a product promise or best-effort backup?
- Should Support App Directory stay if bottom navigation already exists?
- Are avatar assets final, or should transparent clothing be corrected in source PNGs?
- Should background image alignment be managed by per-screen constants that designers/developers can tune manually?

## 13. Recommended First Implementation Prompt

Use this prompt after reviewing this report:

```text
You are working in C:\Users\salva\AndroidStudioProjects\PCOSINA2.

Follow AGENTS.md. Implement Phase 0 and Phase 1 only from docs/product_audit/PCOSINA_APP_OBSERVATION_ACTION_PLAN.md.

Do not redesign pantry UI yet. Do not remove large UI sections beyond the Phase 1 fixes. Do not change backend planner constraints. Preserve offline-first behavior and deterministic planner rules.

Tasks:
1. Reproduce and document current behavior for profile save, Generate New Week, skipped meal logging from Recipe Details, online/offline feedback queue, notification delivery, offline generation message, and garlic grocery pricing.
2. Add clear profile edit save/loading/success/error feedback without requiring cloud sync for local save.
3. Make Generate New Week disabled policy visible with unlock date or final-day requirement.
4. Unify meal logging so skipped planned meals cannot be silently logged from Recipe Details/Home without explicit undo.
5. Replace developer-facing offline/backend generation error copy with simple user-facing wording.
6. Fix the Android grocery price estimation bug for aggregated count units such as garlic cloves so 130g garlic is priced from 130g, not from many generic produce pieces.
7. Add focused tests for the changed logic where practical, and run the smallest relevant build/test commands.

At the end, report changed files, tests run, and any remaining Phase 2+ items.
```
