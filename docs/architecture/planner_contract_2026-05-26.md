# PCOSina Planner Contract

System source of truth added on 2026-05-26.

The runtime planner now returns `explanation.plannerContract` with one row per major input or output constraint. Android maps the same contract into `PlannerPlanExplanation.plannerContract` and displays the hard, soft, advisory, and tracking counts in the Meal Plan screen.

| Classification | Meaning | Current examples |
| --- | --- | --- |
| hard | Cannot be violated when active. Failure returns no-safe-plan or removes candidates before solve. | allergies, supported dietary restrictions, weekly budget cap, max cooking time, daily calorie/macro/fiber bounds |
| soft | Steers scoring, retry order, or objective weights but does not replace hard safety filters. | goal, symptoms, variety preference, planning priority, pantry overlap |
| advisory | Reported or minimized as an overage/penalty, but not a full infeasibility gate. | sodium and sugar overage targets |
| tracking | Stored for continuity or context, not used as solver hard constraints. | display name, comorbidities, optional target weight/date/weekly pace |

Important boundary: backend planning remains pantry-aware, not hard pantry-feasible. Android Grocery now evaluates saved pantry quantities after a plan is generated and separates full coverage, partial coverage, and name-only matches. Sodium and sugar are Android-displayable recipe fields now, but the solver still treats their daily limits as advisory overage penalties. Optional weight target fields support local progress review and opt-in next-plan steering; they are not solver hard constraints and do not prove weight change.
