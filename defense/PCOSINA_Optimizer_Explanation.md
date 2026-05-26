# PCOSINA Meal-Plan Optimizer Explanation

## A) Non-Technical Panel Explanation
PCOSINA's optimizer works like a smart planner that balances real-world rules at once. It first removes meals that violate dietary restrictions, then chooses a seven-day schedule using mathematical optimization. The optimizer tries to match calorie and macro targets, reduce repetition, enforce the user's configured budget cap, and prioritize pantry use. It is not a machine-learning model; it is a transparent rule-and-constraint system. When constraints are too tight, the system returns structured no-safe-plan guidance instead of forcing a partial or unsafe plan.

## B) Technical Undergraduate Level Explanation
### Mathematical framing
The weekly plan is a constrained scheduling and optimization problem. Each meal slot (day x meal) must select exactly one recipe, while the plan must approximate nutrition targets, minimize repetition, and remain budget-aware.

### Variable definitions
Let D be the number of days (default 7), M the number of meals per day (3), and S = D x M total slots. Let R be the candidate recipes after filtering.

Decision variable:
- x[s,i] in {0,1} equals 1 if recipe i is chosen for slot s.

Auxiliary variables track:
- daily calorie deviation
- macro deviations (protein, carbs, fats)
- repeat penalties
- protein group penalties
- diversity coverage

### Penalties and rewards
The objective minimizes calorie deviation and macro deviations, plus penalties for repeated recipes and overused protein groups. Weekly budget is enforced as a hard cap when configured, while cost can be minimized directly for budget-priority profiles. Pantry matches and vegetable diversity provide rewards that reduce the objective.

### Tolerance relaxation strategy
Instead of fixed macro bounds, the solver tries tolerance levels in sequence (e.g., 0.2, 0.3, 0.4). For each tolerance, it tries increasing repeat limits (e.g., 2, 3, 4, 10). This progressive relaxation improves feasibility without abandoning nutrition goals.

### Repeat, group, diversity, pantry, budget mechanisms
- Repeat control: weekly caps and penalties for extra repeats.
- Protein group diversity: penalizes overuse of a protein category.
- Ingredient diversity: tracks coverage of vegetable tokens.
- Pantry matching: rewards recipes that share ingredients with pantry items.
- Budget: hard estimated weekly cost cap when the user provides a budget.

### Fallback behavior when infeasible
If no feasible solution exists within time limits, the backend returns a structured `no-safe-plan` response with reason codes, diagnostics, and safe adjustment guidance. The retained greedy helper is not a production-authoritative fallback path.

## C) Pseudocode + Equation-Style Summary
### Pseudocode
```
INPUT: profile, recipes
if restrictions conflict -> infeasible
candidates = filter_by_constraints_and_pantry(profile, recipes)
candidates = shortlist_by_cost_and_score(candidates)
for tol in [0.2, 0.3, 0.4]:
  set macro bounds = target ± tol
  for max_per_week in [2, 3, 4, 10]:
    build CP-SAT model with constraints
    solve with time limit
    if feasible -> return plan + explanation
return no-safe-plan guidance and diagnostics
```

### Equation-style summary
Objective (minimize): total calorie deviation + macro deviations + repeat penalties + group penalties + optional cost-priority term + diversity slack - pantry rewards - diversity rewards.

Constraints: one recipe per slot; no immediate repeats; max repeats per week; macro bounds with tolerance; hard weekly budget cap when configured; minimum ingredient diversity via slack.

## Why this is valid for PCOS meal planning
PCOS management emphasizes structured eating patterns, balanced macros, and controlled energy intake rather than ad hoc meal choices. The two-stage MILP optimizer enforces these requirements while respecting user preferences, pantry availability, and affordability. The approach is transparent, explainable, and aligns with the thesis' non-ML decision-support framework, making it appropriate for PCOS-focused meal planning.
