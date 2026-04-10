# PCOSINA Defense Flashcards (Printable)

Card 01
Q: What is PCOSINA in one sentence?
A: An offline-first, Filipino-focused meal planning app for PCOS using a two-stage MILP optimizer.

Card 02
Q: What core problem does it address?
A: Generic apps lack PCOS-specific, culturally relevant, pantry-aware, and offline planning.

Card 03
Q: Why MILP instead of ML?
A: MILP guarantees constraint feasibility and explainability without training data.

Card 04
Q: What is the two-stage algorithm?
A: Filter recipes by constraints, then optimize weekly selection with MILP.

Card 05
Q: What inputs drive the plan?
A: Age, height, weight, activity level, goal, restrictions, pantry items, budget.

Card 06
Q: Where is user data stored?
A: Locally via DataStore and encrypted preferences on the device.

Card 07
Q: Is plan generation offline?
A: Not currently; plan generation requires the backend.

Card 08
Q: What endpoints are used?
A: /generate-plan, /recipe/{id}, /recipes/summary, /health, /feedback.

Card 09
Q: What happens if the plan is infeasible?
A: Backend returns 422; user relaxes constraints.

Card 10
Q: How is variety enforced?
A: Max repeats, no consecutive repeats, protein-group penalty, veggie diversity reward.

Card 11
Q: How is pantry used?
A: Recipes with pantry matches are rewarded; grocery list uses consolidated ingredients.

Card 12
Q: What is offline-first in reality?
A: Saved plans and logs are available offline; new plans need internet.

Card 13
Q: What is the evaluation framework?
A: ISO/IEC 25010 with alpha/beta testing.

Card 14
Q: What statistics are used?
A: Frequency, percentage, weighted mean; Cronbach’s alpha for reliability.

Card 15
Q: Is PCOSINA a medical device?
A: No; it is a decision-support tool for wellness guidance.

Card 16
Q: What makes it culturally relevant?
A: Filipino recipes and local dietary guidelines (Pinggang Pinoy).

Card 17
Q: Where is the backend deployed?
A: Render (release base URL points to Render).

Card 18
Q: How is authentication handled?
A: Firebase Auth on client; backend verifies ID tokens.

Card 19
Q: What are the main screens?
A: Splash, Login, SignUp, Onboarding, Profile, Goal, Dashboard, MealPlan, Grocery, Progress, Settings.

Card 20
Q: What are the system limitations?
A: Data completeness, variety limits, online plan generation dependency.

Card 21 (Trick)
Q: Chapter 1 says MILP runs on device. Does it?
A: In this implementation, it runs on the backend; offline solver is future work.
Tip: Separate thesis intent vs current code.

Card 22 (Trick)
Q: Why does the code use CP-SAT if the thesis says MILP?
A: CP-SAT solves integer optimization problems; formulation is MILP-style.
Tip: Say “MILP-style formulation solved with CP-SAT.”

Card 23 (Trick)
Q: Are nutrition values fully accurate?
A: Not always; missing values may be estimated.
Tip: Acknowledge limitation and planned dataset validation.

Card 24 (Trick)
Q: Offline-first means full offline planning, right?
A: Not in the current implementation; generation is online.
Tip: Emphasize offline access to saved data.

Card 25 (Trick)
Q: Is the optimizer medical advice?
A: No; it provides decision support only.
Tip: Repeat the boundary statement.

Card 26 (Trick)
Q: Why not store profiles on the server?
A: Privacy-first design and offline resilience.
Tip: Position as a conscious design choice.

Card 27 (Trick)
Q: Could the admin feedback token leak?
A: If passed in query params, yes; header-only is better.
Tip: Propose mitigation.

Card 28 (Trick)
Q: What if the backend is down?
A: Saved plans still viewable, but new plans cannot be generated.
Tip: Explain offline fallback behavior.

Card 29 (Trick)
Q: Does the system guarantee weight loss?
A: No; it does not claim clinical outcomes.
Tip: Emphasize wellness guidance only.

Card 30 (Trick)
Q: Why not a recommendation model?
A: Recommendations can’t enforce constraints like MILP can.
Tip: Use “feasibility + explainability” keywords.
