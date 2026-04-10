# PCOSINA Final Defense Speaker Notes

## Slide 1: Title Slide
Introduce the project title and team. State the value proposition: a localized, offline-first meal planner for Filipinos with PCOS using optimization. Remind panel this is decision-support, not treatment.

## Slide 2: Background and Motivation
Briefly explain PCOS management relies on diet and lifestyle. Highlight Filipino diet patterns and the pain points in current apps: lack of personalization, pantry awareness, and offline access. Set up why PCOSina is needed.

## Slide 3: Theoretical and Conceptual Framework
Connect theory to design: optimization theory and MILP formalize meal selection. Offline-first and modular design ensure usability. Mention rule-based AI for explainable filtering. Walk through IPO: inputs, process, outputs, evaluation loop.

## Slide 4: Problem Statement
Summarize the problem: culturally aligned PCOS meal planning is missing, tools ignore real constraints, and connectivity limits access. These gaps drive our research questions and system design.

## Slide 5: Objectives
State the general objective, then the specific objectives: two-stage MILP algorithm, personalization, pantry integration, offline-first architecture, and evaluation using quality metrics.

## Slide 6: Scope and Significance
Clarify scope: Filipino recipes, 7-day plans, decision support only. Not a medical device. Emphasize significance for users, dietitian validators, and researchers.

## Slide 7: RRL/RRS Synthesis
Synthesize RRL/RRS: literature supports personalized, structured nutrition; localization and offline access are weak; existing tools are generic and lack constraints. Optimization is a suitable method to address feasibility and personalization.

## Slide 8: Methodology and Architecture
Explain methodology and architecture: developmental and descriptive, built with hybrid Agile-Iterative Scrum. Architecture includes mobile app, local storage, optimization module, and recipe data. Flow from onboarding to plan, grocery, tracking. Cue demo: login, profile, generate plan.

## Slide 9: Algorithm and Development
Explain the two-stage algorithm: filtering removes infeasible recipes using preferences and pantry; MILP selects the weekly plan optimizing nutrition, variety, budget, and pantry use. Show simplified objective and constraints. Cue demo: week navigation.

## Slide 10: Data and Constraints
Describe data and constraints: local recipe and nutrition database for offline use, preprocessing for ingredients/pantry, constraints for diet rules, budget, variety, macros. Mention limitations: dataset size and nutrition data quality. Cue demo: recipe details, add to grocery.

## Slide 11: Evaluation and Respondents
Evaluation approach: ISO/IEC 25010 criteria (functional suitability, usability, reliability, performance efficiency). Respondents are PCOS users and interested participants via purposive sampling. Use surveys, user testing, and dietitian consultation. Stats: frequency, percentage, weighted mean. Reliability via Cronbach's alpha. Accuracy testing with AE/PE. Cue demo: grocery consolidation.

## Slide 12: Results and Key Findings
Present key findings placeholders since Chapter 4 results are not provided. Note where to insert mean scores, top criterion, performance times, and satisfaction results. Cue demo: progress tracking.

## Slide 13: Closing
Close with what we built, why it matters, and next steps. Reiterate decision-support framing. Invite questions.

## Common Panel Questions and Suggested Answers
- **Q:** Why MILP instead of machine learning?
  **A:** Our problem is constraint-heavy and explainable. MILP guarantees feasibility with nutrition, budget, and variety constraints, while ML would require training data we do not have and would reduce transparency.
- **Q:** How does offline-first work if optimization is heavy?
  **A:** Core features and data are stored locally; the app remains usable without internet for viewing plans and groceries. Optimization can run when connectivity is available and results are saved for offline use.
- **Q:** How do you ensure plans are culturally relevant?
  **A:** We use Filipino recipes and align nutrition guidance with local dietary practices, so recommended meals reflect familiar foods and portioning.
- **Q:** What happens when constraints are too strict?
  **A:** The system reports infeasibility and suggests adjustments, and a heuristic fallback can be used to still provide a plan.
- **Q:** How is nutritional accuracy validated?
  **A:** We perform computation precision testing using absolute and percent error versus manual calculations and dietitian reference values.
- **Q:** What ISO/IEC 25010 criteria were used?
  **A:** Functional suitability, usability, reliability, and performance efficiency, based on user and expert evaluation.
- **Q:** Is this a medical treatment app?
  **A:** No. It is a decision-support and wellness tool that helps users plan meals; it does not replace professional medical advice.
- **Q:** What are the next steps?
  **A:** Expand the recipe dataset, refine personalization factors, and conduct longer-term adherence and outcome studies.