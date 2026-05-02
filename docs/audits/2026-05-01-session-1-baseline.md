# Session 1 Baseline - 2026-05-01

## Purpose
Session 1 is limited to workspace hygiene and baseline verification before functional fixes continue on `ui/layout-refine`.

## Approved Product Decisions
- Bottom-nav/help label stays `Support`.
- Grocery and pantry tools stay locked until the user has a plan.
- Demo seeding and fallback plan/history paths should be removed later, not kept as part of the app path.
- Internal methodology and admin tools should move behind real operator authorization instead of local user-facing toggles.
- Progress should prefer actual weekly spend, fall back to estimated cost, and label both clearly.
- The main displayed calorie target should align with backend solver targets in the final product. If exact alignment is not ready yet, treat the Android value as a temporary estimate and label it clearly.
- UI copy should stay simple and user-facing, not thesis-style.
- App Check debug-token flow is acceptable for internal development.

## Hygiene Scope
- Ignore local Codex temp artifacts.
- Ignore local Gradle scratch artifacts that are not part of the repo.
- Ignore extracted design folders and local zip drops under `app/`.
- Ignore backend temp pytest working folders outside the tracked test tree.

## Known WIP Boundary
- The branch already contains substantial uncommitted UI and runtime work before Session 1 starts.
- Session 1 does not change app logic, backend logic, Firebase config, or planner behavior.
- Functional fixes continue in later sessions after the workspace boundary is safer.
