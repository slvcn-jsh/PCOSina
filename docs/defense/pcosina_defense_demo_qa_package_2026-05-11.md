# PCOSINA Defense Demo and Q&A Package

Date prepared: 2026-05-11  
Source branch: `ui/layout-refine`  
Commit: `d7c2842ac987bea1eef531ea969d30e3aa10ad8b`  

Use this file as the presenter/demo support document. The full implementation audit is in `docs/system-audit/pcosina_lead_developer_system_responsibility_package_2026-05-11.md`.

## 10-15 Minute Demo Flow

| Step | What to Show | What to Say | What Not to Say |
|---|---|---|---|
| 1 | Open PCOSINA login screen | "The primary sign-in path is Continue with Google. Email access remains for emulator/testing and support." | Do not say email access has been removed. |
| 2 | Sign in with prepared Google account | "Authentication is handled through Firebase and Google sign-in." | Do not use untested credentials during defense. |
| 3 | Profile setup/edit | "The profile captures personal, medical-context, preference, budget, household, and pantry inputs." | Do not say the app diagnoses PCOS. |
| 4 | Goal selection | "Goals guide the meal planning priorities." | Do not claim guaranteed health outcomes. |
| 5 | Generate meal plan | "The Android app sends the profile to the backend optimizer, then saves the plan locally." | Do not claim meal generation is fully offline. |
| 6 | View Today's Plan and weekly plan | "Saved plans can be viewed again after generation." | Do not claim every recipe detail is offline after restart. |
| 7 | Open recipe details | "Recipe details show ingredients, steps, and nutrition values from backend recipe data." | Do not claim nutrition data is clinically certified unless validation is complete. |
| 8 | Open meal swap | "Meal swaps request backend alternatives and save the replacement locally." | Do not say swaps work without internet. |
| 9 | Open grocery list | "The grocery list is derived from planned recipe ingredients for the primary-user plan." | Do not claim exact market prices. |
| 10 | Mark item bought/in pantry | "The UI supports bought and pantry status during shopping." | Do not overclaim durable purchased tracking until verified. |
| 11 | Progress/check-in | "Progress entries and check-ins are stored locally." | Do not call it clinical monitoring. |
| 12 | Explain meal logging rule | "Logging is today-only and sequence-based, not based on exact clock time." | Do not say the system detects real eating time. |
| 13 | Settings/reminders | "Reminders are local Android notifications with quiet-hour controls." | Do not guarantee delivery on every Android device. |
| 14 | Offline-first point | Turn off internet after saved data exists and show saved profile/plan/grocery/logs | "Offline-first means saved working data remains available locally." | Do not generate a new plan offline. |
| 15 | Limitations | End with scope statement | "PCOSINA is a decision-support prototype, not a medical device." | Do not overpromise clinical impact. |

## Screenshot Checklist

- Login screen with Continue with Google.
- Email access panel, only for testing/support explanation.
- Profile setup Step 1, Step 2, and Step 3.
- Goal selection.
- Dashboard/Home.
- Meal plan empty state.
- Meal plan loading state.
- Generated 7-day meal plan.
- Today's Plan.
- Recipe details.
- Meal swap dialog.
- Grocery list.
- Bought item and In Pantry status.
- Pantry add/list dialog.
- Progress screen.
- Today's check-in.
- Meal check-in.
- Weekly review.
- Settings screen.
- Reminder settings.
- Offline demo with saved plan visible.
- GitHub branch and commit.
- APK/build artifact.
- Signed validation forms after completion.

## Defense Q&A

| Question | Answer |
|---|---|
| What is PCOSINA? | PCOSINA is an Android wellness decision-support app for Filipino PCOS-aware meal planning, grocery support, reminders, and progress tracking. |
| Is it a medical app? | It is health-related wellness support, but it is not a medical device. |
| Does it diagnose PCOS? | No. It does not diagnose PCOS or any condition. |
| Does it treat PCOS? | No. It supports meal planning decisions and habit tracking only. |
| What is offline-first here? | Saved profile, plan, pantry, grocery, logs, reflections, and reminder preferences are stored locally and can be viewed after login/data setup. |
| What needs internet? | Google/Firebase auth, cloud sync/restore, new plan generation, recipe details, meal swaps, and feedback submission. |
| Why Google Auth? | Google Auth gives secure identity verification and account continuity through Firebase. |
| Why is email still present? | Email access remains for emulator testing, support, and operator/testing workflows. The user-facing primary path is Continue with Google. |
| How does the algorithm work? | The backend filters unsafe or unsuitable recipe candidates, optionally scores them, then uses OR-Tools CP-SAT constrained optimization to select the weekly plan. |
| Where is the two-stage approach? | Stage 1 is `shortlist_candidates`; Stage 2 is `solve_meal_plan` with CP-SAT in `backend/services/meal_planner.py`. |
| Is MILP actually implemented? | The backend implements constrained optimization using OR-Tools CP-SAT. It is backend-side, not Android-side. |
| How does pantry affect the grocery list? | Pantry entries are matched against grocery items and can mark items as covered by pantry. |
| How does pantry affect planning? | Pantry entries are also used as planning signals/rewards in backend optimization. |
| How does meal logging work? | Meal completion is local, today-only, and sequence-based for planned meals. |
| Can users log meals for past days? | No. The policy blocks past-day and future-day logging. |
| Is meal logging time-based? | No. It is date-based and sequence-based, not exact clock-time based. |
| Can family or spouse access the account? | No dedicated family/shared account feature was found in the Android audit. |
| How was the system validated? | Manual test cases are completed separately; expert, domain, and statistician validation materials are prepared for execution and signatures. |
| Why ISO 25010? | ISO 25010 is a recognized framework for software product quality evaluation. |
| What did CS/IT experts validate? | Technical correctness, feature behavior, architecture, data handling, offline-first behavior, algorithm feasibility, and error handling. |
| What did the statistician validate? | Computation samples, formula outputs, and statistical treatment of survey/validation data. |
| What did nutrition/OB-GYN validate? | Meal plan appropriateness, nutrition presentation, PCOS-safe wording, and health limitations. |
| What are the system limitations? | Android-only, not a medical device, not fully offline, recipe/nutrition data dependent, and no proof of long-term clinical outcomes. |
| What should future researchers improve? | Local fallback generation, larger validated recipe dataset, durable grocery purchased tracking, health professional review, and longitudinal outcome evaluation. |

## Safe Closing Statement

"PCOSINA focuses on decision support. The app helps users organize profile-based meal planning, grocery preparation, reminders, and progress tracking. It does not diagnose or treat PCOS. Its planning logic is implemented as backend constrained optimization, while the Android app stores and displays the resulting plan and related user data locally."
