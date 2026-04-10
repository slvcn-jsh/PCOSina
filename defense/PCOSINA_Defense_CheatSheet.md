# PCOSINA Defense-Ready Cheat Sheet (1 Page)

**Two-Sentence Definition (Memorize)**
PCOSINA is an offline-first, Filipino-focused meal planning app for individuals with PCOS that uses a two-stage, pantry-constrained MILP optimization approach to generate weekly meal plans.
It is a decision-support tool for wellness guidance, not a medical treatment system.

**Problem Statement (One Line)**
Existing nutrition apps are generic and lack PCOS-specific, culturally relevant, pantry-aware, and offline-capable meal planning for Filipinos.
Source: Chapter 1 Sec 1.2

**General Objective**
Develop and evaluate PCOSINA, an offline-first, preference-aware weekly meal planner using a two-stage, pantry-constrained optimization approach.
Source: Chapter 1 Sec 1.3

**Specific Objectives (5)**
1. Two-stage filtering + MILP for weekly plan selection.
2. Personalization using restrictions, preferences, PCOS goals.
3. Pantry integration + grocery efficiency.
4. Offline-first architecture for core functions.
5. Evaluate nutritional adequacy, usability, cultural relevance, pantry use, efficiency.
Source: Chapter 1 Sec 1.3

**Scope and Delimitations (Key Points)**
Filipino recipes and local dietary guidelines (Pinggang Pinoy and Philippine references).
7-day plan with breakfast, lunch, dinner.
Prototype focus on algorithm and offline capability.
Not a medical device and no clinical outcomes claimed.
Source: Chapter 1 Sec 1.7

**Frameworks to Mention**
Theoretical: Optimization and scheduling theory, MILP, offline-first SE principles, rule-based intelligent systems (non-ML).
Conceptual: IPO with evaluation feedback loop for iterative improvement.
Source: Chapter 1 Sec 1.4 to 1.5

**Methodology**
Developmental and descriptive research design.
Hybrid Agile-Iterative with Scrum practices.
Source: Chapter 3 Sec 3.1 to 3.4

**Algorithm (One Line)**
Stage 1 filters recipes by constraints and pantry; Stage 2 uses MILP to optimize weekly plans by nutrition, cost, and variety.
Source: Chapter 1 Sec 1.4 and Chapter 3 Sec 3.4; Repo: backend/services/meal_planner.py

**Why MILP vs ML (One Line)**
MILP provides explainable feasibility under explicit constraints and runs offline without training data.
Source: Chapter 1 Sec 1.4.4

**Real App Features (Repo-Verified)**
Auth: email-password and Google sign-in with Firebase.
Screens: Splash, Login, SignUp, Onboarding, Profile, Goal, Dashboard, Meal Plan, Grocery, Progress, Settings.
Meal planning via backend /generate-plan; recipe details via /recipe/{id}.
Grocery consolidation and category estimation.
Progress tracking: adherence, weight, reflections, weekly journal, feedback queue.
Repo: app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt and ui/screens/*.kt

**Offline-First Truth Statement**
Core data is stored locally (DataStore + encrypted prefs) for offline viewing and tracking; generating a new plan still requires backend connectivity.
Repo: UserPreferencesRepository.kt, ReflectionStore.kt, MealPlanRepository.kt

**Evaluation Framework**
ISO/IEC 25010: functional suitability, usability, reliability, performance efficiency.
Alpha and beta testing plus validator feedback.
Stats: frequency, percentage, weighted mean, Cronbach's alpha.
Computation precision: Absolute Error and Percent Error for numeric outputs.
Source: Chapter 3 Sec 3.5 to 3.9

**Respondents and Sampling**
Respondents: individuals with PCOS and selected users interested in dietary management.
Sampling: purposive.
Source: Chapter 3 Sec 3.8
[INSERT respondent count] source needed: Chapter 3 results section
[INSERT Cronbach's alpha value] source needed: Chapter 3 results section
[INSERT ISO/IEC 25010 weighted mean scores] source needed: Chapter 3 results section

**Dataset Statement (Say Carefully)**
Thesis scope: Filipino recipes from credible sources such as DOST-FNRI and Filipino cooking blogs; nutrition aligned with Philippine guidelines.
Repo reality: recipes stored in backend/recipes.json seeded into SQLite; missing nutrition uses placeholder estimates.
Sources: Chapter 1 Sec 1.7; Repo: backend/recipes.json and backend/database.py

**Limitations (Say Once)**
Variety limited by recipe pool size.
Nutrition values may be estimates.
Offline-first means updates require manual sync.
Source: Chapter 1 Sec 1.7.4

**Closing Line (Memorize)**
We built a constraint-aware, offline-first, culturally localized meal planning system for Filipino PCOS users; it is practical, explainable, and ready for broader evaluation and dataset expansion.
