# PCOSINA SOP Evidence Extraction Dossier

Prepared on: 2026-05-24
Manuscript source: `C:\Users\salva\AndroidStudioProjects\PCOSINA2\docs\[Manuscript] Quadrant_Chapters_1-6_Revised_S2526T3 (1).docx`
SOP requirements source: `C:\Users\salva\AndroidStudioProjects\PCOSINA2\docs\defense\PCOSINA_SOP_Defense_Explanation_Report_2026-05-23.docx`

## Final Output Standard

This final version preserves the green defense-report organization in the DOCX output and adds the missing bridge that the manuscript alone does not provide: SOP-by-SOP how-process, inputs, outputs/evidence, metrics/results, statement extracts, visible figure evidence, table extracts, and gap notes.

Traceability note: `P####` references are DOCX paragraph indexes from this extraction pass.

## Current Manuscript Status

| Source Item | Extracted Count / Status | Meaning |
| --- | --- | --- |
| Manuscript paragraphs | 840 | Statement-level evidence anchors are available. |
| Manuscript tables | 51 | SOP-relevant tables are copied into the appendix. |
| Manuscript figures | 21 | SOP-relevant figures are extracted into the final assets folder and embedded in the DOCX. |

## Coverage Readout

| SOP | Evidence Status | Strongest Evidence | Gap / Boundary |
| --- | --- | --- | --- |
| 1 | Strong with wording cleanup | P0615-P0635; Tables 47 and 51 | Insulin-resistance wording still appears. |
| 2 | Mostly strong | P0273-P0276; Tables 19, 20, 33, 36, 41 | No final CTS/CIS result table found. |
| 3 | Proxy-supported | P0679-P0680; Table 49 | No actual food-waste measurement. |
| 4 | Strong for saved-data continuity | P0231, P0571, P1137; Table 50 | New optimized generation is backend-dependent. |
| 5 | Strong overall | Tables 25 and 33-51 | No visible standard deviation table; no CTS/CIS result values. |

## SOP 1: Weekly nutrition and weight-management meal planning

| Bucket | Extracted / Organized Answer |
| --- | --- |
| Revised SOP question | How can a weekly meal-planning system be designed to generate nutritionally adequate meal plans for Filipinos with PCOS while considering weight-management goals and dietary constraints? |
| Original manuscript SOP | P0179: How can a weekly meal-planning system be designed to generate nutritionally adequate meal plans for Filipinos with PCOS while considering dietary constraints related to weight management and insulin resistance? |
| Direct defense answer | PCOSina converts profile and goal data into planning targets, removes unsafe or incompatible recipes, and uses OR-Tools CP-SAT to assemble a complete 7-day, 21-slot plan while preserving hard constraints. |
| How PCOSina solves this | Two-stage workflow: deterministic candidate construction and safety filtering first; non-authoritative LightGBM ranking may order safe candidates; CP-SAT performs final weekly assignment and no-safe-plan handling. |
| Inputs required | Profile, anthropometrics, activity, wellness goal, dietary restrictions, allergies, explicit exclusions, budget, household scaling, pantry entries, cooking time, variety preference, recipe nutrition, prices, and policy values. |
| Outputs and evidence required | Seven-day plan with breakfast/lunch/dinner slots, nutrition summaries, cost estimates, pantry-overlap indicators, explanation data, grocery guidance, local saved artifacts, and no-safe-plan diagnostics when needed. |
| Metrics/results found | 100/100 final benchmark runs produced 21 meal slots; zero hard-rule violations; nutrition feasibility passed; final runtime average 257 ms, P95 313 ms, max 357 ms. |
| Gaps / boundaries | The manuscript still contains insulin-resistance wording in the original SOP/input descriptions. Use the revised SOP wording from the defense report.<br>The manuscript gives pass/fail computation evidence, but a detailed manual-vs-system formula worksheet should be ready if the panel asks. |

### Visible Figure Evidence

**Figure 1. Conceptual Framework**  
Source: manuscript P0244.

![Figure 1. Conceptual Framework](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_01.png)

**Figure 12. Two-Stage MILP-Based Meal Planning Algorithm**  
Source: manuscript P0662.

![Figure 12. Two-Stage MILP-Based Meal Planning Algorithm](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_12.png)

**Figure 20. Twenty-Profile Planner Runtime Optimization Progression**  
Source: manuscript P1110.

![Figure 20. Twenty-Profile Planner Runtime Optimization Progression](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_20.png)

### Manuscript Statement Extracts
| Source | Extracted statement |
| --- | --- |
| P0124 | This study developed PCOSina, an offline-first mobile meal-planning decision-support system designed for Filipino individuals diagnosed with PCOS. The system utilizes a constraint-based optimization framework with a two-stage planning approach. The first stage performs deterministic candidate filtering based on dietary preferences, restrictions, allergies, and pantry-aware signals, optionally supported by non-authoritative LightGBM-assisted candidate ranking. The second stage solves a MILP-formulated 0-1 weekly meal assignment model using OR-Tools CP-SAT to generate personalized weekly meal plans. The generated meal plans consider nutritional targets, budget considerations, dietary restrictions, Filipino food context, pantry overlap, and grocery guidance while maintaining practical household applicability. System evaluation using ISO/IEC 25010 software quality standards demonstrated high acceptability and system performance, while mathematical performance testing confirmed that the two-stage optimization architecture consistently delivers safe, feasible meal allocations within practical mobile execution times. PCOSina is evaluated as a wellness decision-support application and is not intended to diagnose, treat, or measure clinical outcomes related to PCOS. |
| P0204 | In PCOSina, weekly meal planning is treated as a constrained scheduling problem. Meals must be assigned across a fixed planning horizon of seven days, with each day containing defined meal slots such as breakfast, lunch, and dinner. These meal assignments must consider nutrition targets, dietary restrictions, allergies, food preferences, budget behavior, pantry overlap, and variety requirements. This aligns with scheduling theory, where tasks are assigned to time slots while respecting resource limitations and feasibility rules. |
| P0212 | In PCOSina, candidate recipes that survive the first-stage filtering process are encoded as binary recipe-slot assignment variables. The optimization model considers constraints and objective terms related to meal-slot validity, nutrition targets, allergies and dietary exclusions, repetition limits, variety behavior, budget limits, and pantry-aware planning signals. The formulation is solved in the remote optimizer service using OR-Tools CP-SAT, which is suitable for integer and constraint-based optimization problems. |
| P0222 | This staged design is based on algorithmic decomposition. Instead of passing the entire recipe database directly into the optimizer, the first stage reduces the candidate space through deterministic filtering, heuristic scoring, optional LightGBM-assisted ranking, and similarity pruning. This helps reduce solver burden and maintain practical response times while still preserving deterministic safety constraints. |
| P0259 | User planning inputs include profile information, dietary restrictions, allergies, food preferences, PCOS-related wellness goals, insulin-resistance-related settings, budget information, household scaling inputs, pantry items, and planning feedback. These inputs influence candidate filtering, ranking, optimization constraints, grocery guidance, and generated meal-plan outputs. |
| P0269 | The remote optimizer service first normalizes recipe data, ingredient tokens, user restrictions, allergies, pantry entries, and profile inputs. It labels recipe attributes, removes recipes that violate hard safety or dietary rules, estimates recipe cost, measures pantry overlap, applies heuristic scoring, and reduces near-duplicate candidates. The implemented LightGBM-assisted ranking component supports candidate prioritization within this stage, but it remains non-authoritative and cannot override deterministic hard constraints. |
| P0272 | After candidate construction, the system solves a MILP-style 0-1 weekly assignment formulation through OR-Tools CP-SAT. Candidate recipes are represented as binary assignment variables over meal slots. The model considers nutrition targets, meal-slot validity, allergies, dietary exclusions, repetition limits, budget behavior, variety requirements, and pantry-aware planning signals. The solver selects a weekly meal combination that best satisfies the defined constraints and objective terms. |
| P0277 | The output phase represents the system-generated results returned to the mobile application after processing. PCOSina generates a personalized 7-day Filipino-context meal plan that includes meal schedules, recipe details, nutritional information, explanation data, grocery guidance, and pantry-aware recommendations. The weekly plan is based on user profile inputs, dietary restrictions, allergies, preferences, budget behavior, pantry overlap, and PCOS-supportive nutrition principles. |
| P0655 | The meal-planning component of PCOSina implements a two-stage planning workflow in which the mobile application prepares, stores, and retrieves user data through local persistence, while the remote optimizer service performs computational planning for new optimized weekly meal plans. However, the generation of a new optimized weekly meal plan requires communication with the remote optimizer service, where the two-stage planning workflow is executed. |
| P0657 | PCOSina adopts this philosophy by employing a two-stage optimization process. In the first stage, the remote optimizer service constructs a preference-aware candidate pool by normalizing recipe data, labeling meal attributes, filtering infeasible options, ranking feasible recipes, and pruning near-duplicate or low-priority candidates. In the second stage, the same service solves a MILP-formulated 0-1 linear weekly assignment model through OR-Tools CP-SAT to select the combination of recipes that best satisfies nutritional targets, pantry-aware signals, budget considerations, and whole-week variety requirements. |
| P0659 | The planner accepts a structured planning request composed of anthropometric data, activity level, wellness goal, insulin-resistance-related setting, dietary restrictions, allergies, pantry entries, budget information, cooking-time limit, variety preference, and planning priority. Using these inputs together with recipe, nutrition, ingredient, price, and pantry-reference data, the system performs deterministic candidate filtering, LightGBM-assisted candidate ranking, and OR-Tools CP-SAT weekly assignment optimization to produce a seven-day meal plan, nutritional summaries, planning explanations, estimated cost information, pantry-overlap indicators, and grocery guidance. |
| P0667 | At runtime, the algorithm proceeds through a fixed planning sequence. The system first loads the user profile and normalizes profile inputs, pantry entries, allergy terms, dietary restrictions, and planning preferences. It then validates contradictory or unsupported input combinations, checks the planning horizon and meals-per-day settings, and computes calorie and macronutrient targets based on profile data and wellness goals. |
| P0669 | After input validation, the remote optimizer service executes the first-stage candidate construction process. Stage 1 normalizes recipe data, applies deterministic safety and suitability filters, estimates cost, measures pantry overlap, applies heuristic scoring, performs LightGBM-assisted candidate ranking, removes near-duplicate recipes, and caps the candidate pool to a solver-manageable size. LightGBM is used only as a non-authoritative ranking aid and cannot override allergy exclusions, dietary restrictions, explicit user exclusions, meal-slot eligibility, or other hard safety constraints. |
| P0671 | The reduced candidate pool is then passed to Stage 2. In this stage, the system builds meal-slot domains and solves a MILP-style 0-1 weekly assignment formulation through OR-Tools CP-SAT in the remote optimizer service. The accepted assignments are converted into day-level meal plans with breakfast, lunch, and dinner entries, nutritional summaries, estimated cost information, pantry-overlap indicators, explanation data, and grocery guidance. These generated outputs are returned to the mobile application and stored locally for offline-first access. |
| P0675 | When the primary CP-SAT optimization process cannot produce a complete or feasible weekly plan because of constraint conflicts, insufficient candidate coverage, timeout, or service interruption, the system applies an adaptive fallback strategy. Selected soft constraints may be progressively relaxed, such as variety preference, nutrition tolerance, budget warning band, or pantry-overlap preference. However, hard constraints such as allergies, dietary restrictions, explicit exclusions, and safety-related filters remain strictly enforced. If a safe complete plan still cannot be generated, the system provides constraint feedback or guidance rather than producing an unsafe recommendation. |
| P0819 | Once the remote optimizer service obtained a feasible or optimized solution, the accepted assignments were transformed into a structured seven-day meal plan containing breakfast, lunch, and dinner selections for each day. The resulting plan, nutritional summaries, pantry-overlap indicators, explanation data, estimated cost information, and grocery guidance were returned to the mobile client and stored locally for offline-first access. |
| P0821 | When no feasible complete plan could be produced under the supported settings, the system doesn’t force a partial or unsafe recommendation. Instead, it reported a structured no-safe-plan result and suggested adjustments to non-safety inputs such as budget limits, cooking-time limits, variety preferences, or overly restrictive exclusions before a new planning cycle is attempted. |
| P1141 | The system creates meal plans using a two-stage process. First, it automatically filters out recipes that conflict with the user's allergies, dietary restrictions, or planning limits. A machine learning model (LightGBM) then ranks the remaining safe options using profile and recipe signals. Finally, the main scheduling engine (Google OR-Tools CP-SAT) builds the seven-day plan with three meals per day. |
| P1143 | This scheduling engine strictly enforces hard rules, such as staying within the user's budget, blocking declared allergens and dietary restrictions, maintaining the weekly meal structure, and limiting recipe repetition. At the same time, it balances flexible goals by reducing nutritional gaps and prep time while considering pantry overlap as a planning-support signal. The machine learning layer helps rank safe candidates, but the CP-SAT planner remains the final authority for constraint enforcement. |
| P1145 | To evaluate the final planner across realistic user needs, the study used representative cases from the 20-profile benchmark and a separate no-safe-plan boundary test. These cases cover ordinary meal planning, budget limits, dietary restrictions, allergy-sensitive profiles, pantry-aware planning, and an intentionally impossible budget condition. |
| P1149 | These representative cases show that the planner was tested under ordinary use, budget-sensitive use, dietary restriction, allergy-sensitive use, pantry-aware use, and deliberate failure conditions. The no-safe-plan boundary test shows that the application reports infeasibility when a safe plan is impossible instead of forcing an unsafe recommendation. |
| P1153 | The table above shows that the final planner preserved the required safety boundary. In the final 20-profile benchmark, all 100 runs completed the 21-meal weekly structure, passed constraint validation, and recorded zero hard-rule violations. Nutrition feasibility also passed in all 100 runs, while sodium or sugar items were treated as advisory warnings when applicable. When the configured budget was intentionally impossible, the system returned structured no-safe-plan feedback instead of forcing an unsafe automatic recommendation. |
| P1156 | In this section, recovery refers to measured planner-latency recovery, not a separate recovery module. The first 20-profile local benchmark already generated valid plans, but it averaged 6,154 ms, with P95 at 12,514 ms and maximum runtime at 14,013 ms. After profile-aware solve ordering, first-complete-safe-plan stopping, a smaller safe search set, cached static recipe features, and the LightGBM-assisted Stage-1 path, the final benchmark completed 100/100 runs with an average runtime of 257 ms, P95 runtime of 313 ms, maximum runtime of 357 ms, and zero hard-rule violations. |
| P1191 | PCOSina generated weekly meal plans through a structured pipeline involving preference-aware filtering, dietary restriction enforcement, and CP-SAT-based optimization to ensure nutritional adequacy and compliance with user-defined constraints. System testing showed that the application consistently produced complete 21-slot weekly meal plans across all benchmark scenarios, including standard, budget-restricted, dietary-restricted, and pantry-aware conditions. Hard constraints such as dietary restrictions, budget limits, and meal structure requirements were consistently enforced, with no violations observed during testing. |
| P1193 | Questionnaire results further supported these findings, particularly under Functional Suitability where respondents rated the system with an overall weighted mean of 3.93 interpreted as “Agree.” In addition, clinical validation from the OB-GYN consultation confirmed that the system’s design direction aligns with PCOS weight management principles, particularly the emphasis on controlled carbohydrate intake, portion regulation, and weight-loss-oriented dietary planning. The expert also emphasized that reducing intake quantity and promoting balanced meals is appropriate for PCOS management, which aligns with the system’s optimization approach. |

### Tables and Figures
| Evidence Type | Sources |
| --- | --- |
| Tables | Table 4: Table 4. Clinical Governance Gates Used in PCOSina (P0583)<br>Table 7: Table 7. Adaptive Constraint Handling Sequence (P0679)<br>Table 8: Table 8. Trigger Conditions and Evidence Indicators (P0685)<br>Table 9: Table 9. Algorithm Selection Policy (P0689)<br>Table 10: Table 10. Standard BMI Categories (P0728)<br>Table 11: Table 11. Hard vs Soft Constraint Policy (P0741)<br>Table 12: Table 12. Role of LightGBM in the Planning Workflow (P0760)<br>Table 13: Table 13. Model Variables and Core Rules Matrix (P0770)<br>Table 14: Table 14. Variable-to-Constraint and Objective Mapping (P0774)<br>Table 15: Table 15. Sets, Parameters, and Decision Variables (P0776)<br>Table 16: Table 16. Objective Function Parameters, Penalty Terms, and Optimization Weights (P0784)<br>Table 17: Table 17. Core Constraints (P0786)<br>Table 18: Table 18. Hard vs Soft Constraint Taxonomy (P0789)<br>Table 21: Table 21. System Test Matrix (P0861)<br>Table 42: Table 42. PCOSina Implemented Workflow and Functional Coverage (P1104)<br>Table 45: Table 45. Summary of Functional Testing Results (P1136)<br>Table 46: Table 46. Representative Final Planner Evaluation Cases (P1147)<br>Table 47: Table 47. Final Planner Constraint and Safety Outcome Summary (P1151)<br>Table 50: Table 50. Offline-First and Performance Evaluation Summary (P1172)<br>Table 51: Table 51. Twenty-Profile Planner Optimization and Final Evidence Summary (P1176) |
| Figures | Figure 1: Figure 1. Conceptual Framework (P0244)<br>Figure 8: Figure 8. System Architecture (P0568)<br>Figure 12: Figure 12. Two-Stage MILP-Based Meal Planning Algorithm (P0662)<br>Figure 13: Figure 13. Adaptive fallback flowchart for weekly meal-plan generation (P0665)<br>Figure 14: Figure 14. Relaxation ladder used by adaptive fallback (P0677)<br>Figure 15: Figure 15. High-Level Pseudocode (P0814)<br>Figure 20: Figure 20. Twenty-Profile Planner Runtime Optimization Progression (P1110)<br>Figure 21: Figure 21. Scenario-Level Runtime and Nutrition Comparison (P1127) |

## SOP 2: Preferences, Filipino food practices, and dietary restrictions

| Bucket | Extracted / Organized Answer |
| --- | --- |
| Revised SOP question | How can user preferences, Filipino food practices, and dietary restrictions be effectively integrated into a personalized meal-planning algorithm for individuals with PCOS? |
| Original manuscript SOP | P0180: How can user preferences, cultural food practices, and dietary restrictions be effectively integrated into a personalized meal-planning algorithm for individuals with PCOS? |
| Direct defense answer | PCOSina treats allergies, restrictions, and explicit exclusions as hard filters, while Filipino food context, preferences, pantry overlap, budget, and cooking-time behavior shape ranking and optimization. |
| How PCOSina solves this | The system normalizes recipe data, ingredient tokens, pantry entries, allergy terms, and dietary restrictions; labels recipe attributes; removes hard-rule violations; ranks feasible recipes; and balances the full week through CP-SAT. |
| Inputs required | Dietary restrictions, allergies, explicit excluded foods, meal count, cooking-time limit, budget, pantry entries, planning priority, Filipino recipe data, nutrition values, ingredient tokens, and cultural familiarity indicators. |
| Outputs and evidence required | Personalized Filipino-context weekly meal plan, readable meal slots, restriction-compliant recipe choices, plan metadata, cultural relevance evidence, and no-safe-plan guidance when preferences become too restrictive. |
| Metrics/results found | Functional Suitability overall mean 3.93 Agree; Usability overall mean 4.06 Agree; Filipino food item scored 4.28 primary / 3.84 secondary; safety violations must remain zero. |
| Gaps / boundaries | The manuscript defines CTS/CIS formulas and protocol, but no final CTS/CIS result table was found.<br>Keep hard preferences separate from soft preferences in defense answers so cultural or ML ranking is never described as overriding safety. |

### Visible Figure Evidence

**Figure 1. Conceptual Framework**  
Source: manuscript P0244.

![Figure 1. Conceptual Framework](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_01.png)

**Figure 12. Two-Stage MILP-Based Meal Planning Algorithm**  
Source: manuscript P0662.

![Figure 12. Two-Stage MILP-Based Meal Planning Algorithm](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_12.png)

**Figure 16. ISO-Based Software Evaluation Model**  
Source: manuscript P0837.

![Figure 16. ISO-Based Software Evaluation Model](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_16.png)

### Manuscript Statement Extracts
| Source | Extracted statement |
| --- | --- |
| P0143 | Filipino individuals managing PCOS require meal plans that honor their cultural palate while emphasizing PCOS-friendly choices (e.g., higher fiber, lower sugar, and refined carbs) to support weight-management and insulin resistance-related nutrition goals. Despite the clear benefits of meal planning—it is associated with healthier diets and lower obesity rates—existing diet apps and programs have significant shortcomings. |
| P0229 | Modular design principles support the separation of system components into independent modules, such as user profiling, pantry management, recipe handling, candidate construction, LightGBM-assisted ranking, CP-SAT optimization, grocery guidance, and local data persistence. This separation improves maintainability, scalability, testability, and traceability. It also allows each component to be validated independently through unit tests, system tests, expert review, and software-quality evaluation. |
| P0231 | From a user-centered development perspective, PCOSina is designed to reduce the burden of manual meal planning. Personalization features, structured meal outputs, pantry-aware grocery guidance, and accessible mobile interfaces are intended to improve usability and support informed meal-planning decisions. |
| P0259 | User planning inputs include profile information, dietary restrictions, allergies, food preferences, PCOS-related wellness goals, insulin-resistance-related settings, budget information, household scaling inputs, pantry items, and planning feedback. These inputs influence candidate filtering, ranking, optimization constraints, grocery guidance, and generated meal-plan outputs. |
| P0269 | The remote optimizer service first normalizes recipe data, ingredient tokens, user restrictions, allergies, pantry entries, and profile inputs. It labels recipe attributes, removes recipes that violate hard safety or dietary rules, estimates recipe cost, measures pantry overlap, applies heuristic scoring, and reduces near-duplicate candidates. The implemented LightGBM-assisted ranking component supports candidate prioritization within this stage, but it remains non-authoritative and cannot override deterministic hard constraints. |
| P0272 | After candidate construction, the system solves a MILP-style 0-1 weekly assignment formulation through OR-Tools CP-SAT. Candidate recipes are represented as binary assignment variables over meal slots. The model considers nutrition targets, meal-slot validity, allergies, dietary exclusions, repetition limits, budget behavior, variety requirements, and pantry-aware planning signals. The solver selects a weekly meal combination that best satisfies the defined constraints and objective terms. |
| P0286 | Primary users evaluate usability, perceived usefulness, meal relevance, and pantry/grocery usefulness. Secondary users evaluate support value and practical meal-planning assistance. CS/IT professionals evaluate selected software quality characteristics. Health-related expert validators, such as registered nutritionist-dietitians and Obstetrician-Gynecologists, review safety boundaries, nutrition relevance, and non-diagnostic framing. |
| P0824 | System evaluation was conducted to assess the functional suitability, performance efficiency, compatibility, usability, reliability, security, maintainability, and portability of PCOSina. Since the system is intended for Filipino users diagnosed with PCOS, the evaluation also included a cultural relevance assessment. This assessment examined whether the generated meal plans contained familiar Filipino dishes and locally recognizable ingredients while still following user restrictions, allergy rules, nutrition targets, and system feasibility requirements. |
| P0826 | The cultural relevance evaluation was conducted using predefined test scenarios. For each scenario, the system generated meal plans, recorded the selected meals, and checked whether the dishes and ingredients were culturally appropriate for the intended Filipino context. Two indicators were used: the Cultural Taste Score (CTS), which measures dish familiarity, and the Cultural Ingredient Score (CIS), which measures ingredient familiarity. These indicators helped determine whether the generated plans were not only technically feasible but also practical and familiar for Filipino users. |
| P0960 | Cultural Title Share (CTS) |
| P0962 | Cultural Ingredient Share (CIS) |
| P0964 | Feasibility Rate (FR) |
| P0969 | Outputs are considered acceptable only when safety violations equal zero. |
| P1073 | The results show that both primary and secondary users positively evaluated the functional suitability of the PCOSina application, with overall weighted means interpreted as “Agree.” The highest ratings were observed in the inclusion of familiar Filipino food options and pantry-aware meal planning features. Overall, the findings indicate that the application provides meal-planning functionalities aligned with the dietary needs of users managing PCOS. |
| P1082 | The usability evaluation results indicate that respondents generally found the application easy to understand and navigate. Both primary and secondary users rated the indicators under the “Agree” interpretation, reflecting positive perceptions regarding the layout, navigation, and overall ease of use of the system. Overall, the findings suggest that the application is user-friendly for both technical and non-technical users. |
| P1097 | The evaluation results of the PCOSina application based on the ISO/IEC 25010 software quality standards are summarized in the table below. The data presents a comparative look between the assessments provided by the primary and secondary users across all eight quality characteristics. Overall, the findings indicate that both user groups perceived the application positively. This collective evaluation demonstrates that the application successfully achieved acceptable and dependable software quality. |
| P1196 | User preferences, cultural food practices, and dietary restrictions were integrated into the system through profile-based filtering, restriction-based exclusion, and recommendation ranking. Meals that violated allergies or dietary restrictions were automatically removed during candidate selection, while culturally familiar Filipino dishes were prioritized in the recommendation process. System testing showed that meal outputs changed depending on user profile configurations, confirming that personalization directly influences generated meal plans. |
| P1198 | Questionnaire results under Usability showed a weighted mean of 4.06 interpreted as “Agree,” indicating that respondents found the application easy to use and navigate. Functional Suitability results also reflected positive feedback regarding culturally relevant meal options. This is further supported by OB-GYN validation, where the expert confirmed that dietary control should prioritize portion size and balanced intake rather than strict food elimination, reinforcing the system’s approach of flexible, culturally aware, and constraint-based meal personalization. |

### Tables and Figures
| Evidence Type | Sources |
| --- | --- |
| Tables | Table 1: Table 1. Summary of Key Literature Related to the Study (P0440)<br>Table 2: Table 2. Summary of Key Studies Related to the Study (P0481)<br>Table 3: Table 3. Local Literature-to-Design Traceability Matrix (P0490)<br>Table 11: Table 11. Hard vs Soft Constraint Policy (P0741)<br>Table 12: Table 12. Role of LightGBM in the Planning Workflow (P0760)<br>Table 14: Table 14. Variable-to-Constraint and Objective Mapping (P0774)<br>Table 17: Table 17. Core Constraints (P0786)<br>Table 19: Table 19. Cultural Relevance Evaluation Protocol (P0828)<br>Table 20: Table 20. Acceptance Rule and Failure Handling (P0833)<br>Table 23: Table 23. Software-Effectiveness Dimensions and Metrics (P0955)<br>Table 33: Table 33. Functional Suitability of PCOSina (P1070)<br>Table 36: Table 36. Usability of PCOSina (P1080)<br>Table 41: Table 41. Summary of ISO/IEC 25010 Evaluation Results for the PCOSina Application (P1098)<br>Table 45: Table 45. Summary of Functional Testing Results (P1136)<br>Table 46: Table 46. Representative Final Planner Evaluation Cases (P1147)<br>Table 47: Table 47. Final Planner Constraint and Safety Outcome Summary (P1151)<br>Table 48: Table 48. Evaluation Summary of the Stage-1 ML Model (P1160) |
| Figures | Figure 1: Figure 1. Conceptual Framework (P0244)<br>Figure 12: Figure 12. Two-Stage MILP-Based Meal Planning Algorithm (P0662)<br>Figure 15: Figure 15. High-Level Pseudocode (P0814)<br>Figure 16: Figure 16. ISO-Based Software Evaluation Model (P0837)<br>Figure 18: Figure 18. Effectiveness dimensions, metrics, and output artifacts (P0944)<br>Figure 19: Figure 19. Effectiveness-evaluation flowchart (software-evaluation scope) (P0951) |

## SOP 3: Pantry-aware planning and grocery reduction support

| Bucket | Extracted / Organized Answer |
| --- | --- |
| Revised SOP question | How can pantry inventory data be integrated into meal planning to help promote reduced food waste and unnecessary grocery purchases while maintaining nutritional adequacy? |
| Original manuscript SOP | P0181: How can the integration of pantry inventory data into meal planning reduce food waste and unnecessary grocery purchases while maintaining nutritional adequacy? |
| Direct defense answer | PCOSina uses pantry entries as ranking and grocery-guidance signals. It can encourage ingredient reuse and reduce unnecessary purchases, but actual waste reduction depends on pantry accuracy and user adherence. |
| How PCOSina solves this | Pantry strings are normalized to tokens, compared with recipe ingredients, used to compute pantry overlap, and reflected in grocery deficit guidance. |
| Inputs required | User pantry entries, recipe ingredient lists, normalized pantry/ingredient tokens, quantities when available, budget caps, prices, and the generated meal plan. |
| Outputs and evidence required | Pantry-overlap indicators, grocery list grouping, pantry-covered flags, missing-item guidance, estimated prices, and no-safe-plan feedback for impossible budgets. |
| Metrics/results found | Scenario 4 recorded 38 pantry-overlap matches; PHP 1,000 budget scenario passed; PHP 20 impossible budget returned no-safe-plan; functional suitability pantry/purchase items scored positively. |
| Gaps / boundaries | The manuscript has proxy evidence for purchase reduction, but no measured pre/post household food-waste or purchase-log study.<br>Use 'helps promote' or 'supports' waste reduction. Avoid saying the system proved actual food-waste reduction. |

### Visible Figure Evidence

**Figure 1. Conceptual Framework**  
Source: manuscript P0244.

![Figure 1. Conceptual Framework](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_01.png)

**Figure 8. System Architecture**  
Source: manuscript P0568.

![Figure 8. System Architecture](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_08.png)

**Figure 21. Scenario-Level Runtime and Nutrition Comparison**  
Source: manuscript P1127.

![Figure 21. Scenario-Level Runtime and Nutrition Comparison](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_21.png)

### Manuscript Statement Extracts
| Source | Extracted statement |
| --- | --- |
| P0147 | Second, current meal planners generally ignore the user’s pantry or food inventory. They recommend recipes without regard to what ingredients the user already has on hand, leading to unnecessary grocery expenses, extra market trips, and contributing to food waste when unused ingredients spoil. In contrast, public health guidance on meal planning emphasizes checking one’s pantry first and using available ingredients to minimize costs. Without inventory awareness, apps miss the opportunity to foster budget-friendly and sustainable consumption. |
| P0163 | Importantly, the planner incorporates pantry information as a pantry-aware planning signal by prioritizing recipes with stronger pantry overlap and generating grocery guidance for missing ingredients, prioritizing recipes that use ingredients already available at home. This pantry-aware design improves convenience and reduces grocery expenses while supporting waste reduction goals. Studies show that incorporating inventory constraints can reduce food purchasing costs by around 4% and lower food-waste environmental impact by up to 19%. PCOSina embodies this principle by algorithmically prioritizing recipes that make use of available pantry ingredients during meal plan generation, helping reduce additional grocery needs and potential food waste. |
| P0176 | Polycystic Ovary Syndrome (PCOS) is a common chronic hormonal disorder affecting individuals with ovaries, commonly diagnosed during the reproductive years, and is often accompanied by hormonal imbalances and metabolic complications. According to the Department of Science and Technology (DOST, 2025), up to 70% of cases go undiagnosed, making PCOS a significant societal and economic challenge that is frequently overlooked. The condition also affects mental well-being and can lead to serious long-term health complications if left unaddressed. Existing nutrition applications are generic, do not address PCOS-specific dietary needs, and often lack culturally relevant features such as Filipino food tracking, pantry management, and offline functionality. There is a need for a culturally adapted, evidence-based mobile application that integrates PCOS-specific nutrition targets with Filipino food composition data to support structured meal planning, encourage dietary planning consistency, and provide pantry-aware grocery guidance to minimize unnecessary purchases and household food waste. However, PCOSina is a decision-support tool and does not replace professional clinicians. Registered nutritionist-dietitians (RDs) and medical doctors (MDs) are integrated as expert validators to ensure safety constraints and requirement boundaries. |
| P0192 | 3. To integrate a digital pantry inventory system that prioritizes the use of available ingredients and generates efficient grocery recommendations to reduce food waste. |
| P0204 | In PCOSina, weekly meal planning is treated as a constrained scheduling problem. Meals must be assigned across a fixed planning horizon of seven days, with each day containing defined meal slots such as breakfast, lunch, and dinner. These meal assignments must consider nutrition targets, dietary restrictions, allergies, food preferences, budget behavior, pantry overlap, and variety requirements. This aligns with scheduling theory, where tasks are assigned to time slots while respecting resource limitations and feasibility rules. |
| P0259 | User planning inputs include profile information, dietary restrictions, allergies, food preferences, PCOS-related wellness goals, insulin-resistance-related settings, budget information, household scaling inputs, pantry items, and planning feedback. These inputs influence candidate filtering, ranking, optimization constraints, grocery guidance, and generated meal-plan outputs. |
| P0269 | The remote optimizer service first normalizes recipe data, ingredient tokens, user restrictions, allergies, pantry entries, and profile inputs. It labels recipe attributes, removes recipes that violate hard safety or dietary rules, estimates recipe cost, measures pantry overlap, applies heuristic scoring, and reduces near-duplicate candidates. The implemented LightGBM-assisted ranking component supports candidate prioritization within this stage, but it remains non-authoritative and cannot override deterministic hard constraints. |
| P0277 | The output phase represents the system-generated results returned to the mobile application after processing. PCOSina generates a personalized 7-day Filipino-context meal plan that includes meal schedules, recipe details, nutritional information, explanation data, grocery guidance, and pantry-aware recommendations. The weekly plan is based on user profile inputs, dietary restrictions, allergies, preferences, budget behavior, pantry overlap, and PCOS-supportive nutrition principles. |
| P0683 | Pantry overlap is treated as a scoring and planning signal, not as a final fallback relaxation level. This allowed the system to prioritize recipes that better match available pantry entries while still preserving safety and avoiding unsupported claims of full pantry-based feasibility. |
| P0707 | Pantry matching - Measures how well recipes match the user’s pantry entries and supports grocery guidance. |
| P0719 | Pantry information was used as a planning-support signal rather than as a complete guarantee that all ingredients are already available at home. The system checked pantry overlap using normalized pantry and ingredient terms: |
| P0720 | When ingredient quantity estimates were available, grocery gaps were estimated as: |
| P0721 | These pantry-related computations supported recipe prioritization and grocery guidance, but users remained responsible for verifying actual ingredient quantities and freshness. |
| P1163 | Pantry, grocery, and budget behaviors were evaluated to measure the practical usability of the system's core features. While the solver treats configured weekly budgets as strict hard constraints, pantry and grocery features operate on intelligent data tracking to assist user management. The table below summarizes the system's operational logic in these areas. |
| P1167 | The table above proves that the system successfully connects complex backend math with real-world features, making it easy for the user to plan their groceries and manage their budget based on what is already in their kitchen. |
| P1181 | The findings indicate that PCOSina achieved the main technical objectives of the study, including profile-based data collection, deterministic safety filtering, Stage 1 ranking support, CP-SAT weekly meal-plan optimization, pantry-aware grocery guidance, structured no-safe-plan handling, and offline-first continuity for saved information. |
| P1183 | The final-build local benchmark showed that the planner successfully generated complete 21-slot meal plans across realistic user profiles while preserving hard constraints. The evaluation was not based on one isolated case: it used 20 realistic planner cases, then repeated the final benchmark five times per case for 100 total runs. The final result reached an average runtime of 257 ms, a P95 runtime of 313 ms, a maximum runtime of 357 ms, and zero hard-constraint violations. In contrast, an intentionally infeasible budget produced a no-safe-plan result instead of generating an unsafe output, indicating that the defined system constraints were preserved during evaluation. |
| P1201 | The pantry management feature allowed the system to incorporate available household ingredients during meal-plan generation and grocery list creation. Meals that utilized existing pantry ingredients were prioritized, aiming to minimize unnecessary ingredient purchases and promote ingredient reuse across meal schedules. Grocery lists were generated based on the current pantry inventory, ensuring that only missing or insufficient ingredients were suggested for purchase while still maintaining alignment with nutritional targets. |
| P1203 | Functional Suitability results supported these findings, showing a positive evaluation of pantry-aware recommendations and grocery reduction features. This indicates that users recognized the system’s potential to optimize ingredient usage while maintaining meal planning quality. Overall, the integration of pantry inventory data supports the minimization of unnecessary grocery purchases and potentially helps lower household food waste without compromising nutritional adequacy. |
| P1218 | By incorporating user profiles, preferences, and Filipino recipe data, the personalization mechanism tailored weekly assignments directly to a user's body mass index (BMI) metrics and strict caloric boundaries. Furthermore, the digital pantry module reduced unnecessary grocery expenses and minimized food waste by processing available household ingredient records as algorithmic weights to encourage ingredient reuse. Technical boundaries from system logs show that this inventory feature operates as a pantry-aware guidance layout rather than a fully hard-enforced constraint across all optimization layers, preventing resource limitations from compromising a user's necessary macro-nutrient adequacy. |

### Tables and Figures
| Evidence Type | Sources |
| --- | --- |
| Tables | Table 3: Table 3. Local Literature-to-Design Traceability Matrix (P0490)<br>Table 11: Table 11. Hard vs Soft Constraint Policy (P0741)<br>Table 14: Table 14. Variable-to-Constraint and Objective Mapping (P0774)<br>Table 16: Table 16. Objective Function Parameters, Penalty Terms, and Optimization Weights (P0784)<br>Table 17: Table 17. Core Constraints (P0786)<br>Table 18: Table 18. Hard vs Soft Constraint Taxonomy (P0789)<br>Table 21: Table 21. System Test Matrix (P0861)<br>Table 31: Table 31. Frequency of Checking Pantry/Kitchen Inventory Before Buying Groceries of All Respondents (P1057)<br>Table 32: Table 32. Baseline Experience of Respondents Before Using PCOSina (P1064)<br>Table 33: Table 33. Functional Suitability of PCOSina (P1070)<br>Table 42: Table 42. PCOSina Implemented Workflow and Functional Coverage (P1104)<br>Table 45: Table 45. Summary of Functional Testing Results (P1136)<br>Table 46: Table 46. Representative Final Planner Evaluation Cases (P1147)<br>Table 47: Table 47. Final Planner Constraint and Safety Outcome Summary (P1151)<br>Table 49: Table 49. Pantry, Grocery, and Budget Evaluation Summary (P1165)<br>Table 50: Table 50. Offline-First and Performance Evaluation Summary (P1172)<br>Table 51: Table 51. Twenty-Profile Planner Optimization and Final Evidence Summary (P1176) |
| Figures | Figure 1: Figure 1. Conceptual Framework (P0244)<br>Figure 8: Figure 8. System Architecture (P0568)<br>Figure 12: Figure 12. Two-Stage MILP-Based Meal Planning Algorithm (P0662)<br>Figure 15: Figure 15. High-Level Pseudocode (P0814)<br>Figure 18: Figure 18. Effectiveness dimensions, metrics, and output artifacts (P0944)<br>Figure 20: Figure 20. Twenty-Profile Planner Runtime Optimization Progression (P1110)<br>Figure 21: Figure 21. Scenario-Level Runtime and Nutrition Comparison (P1127) |

## SOP 4: Offline-first mobile support

| Bucket | Extracted / Organized Answer |
| --- | --- |
| Revised SOP question | How can an offline-first mobile application be designed to support saved meal planning, saved recipe access, grocery/pantry management, and progress tracking in environments with limited or no internet connectivity? |
| Original manuscript SOP | P0182: How can an offline-first mobile application be designed to support meal planning, recipe access, and pantry management in environments with limited or no internet connectivity? |
| Direct defense answer | PCOSina keeps saved user-facing artifacts locally available while clearly bounding new optimized plan generation as backend-dependent. |
| How PCOSina solves this | The mobile client uses local persistence for profiles, saved plans, pantry records, grocery guides, progress records, and settings; remote optimization is used for new CP-SAT plan generation. |
| Inputs required | Local storage artifacts, saved profile, saved plan, pantry records, grocery guidance, progress logs, connectivity state, and backend optimizer availability. |
| Outputs and evidence required | Offline-readable saved artifacts, local-first continuity, clear online-dependency boundary for new generation, and reliability/portability survey evidence. |
| Metrics/results found | Portability overall mean 4.04 Agree; offline item 4.04 primary / 3.88 secondary; saved plans/pantry offline item 4.16 primary / 4.12 secondary; Reliability overall mean 4.00 Agree. |
| Gaps / boundaries | The manuscript supports saved-data continuity, not full offline generation of new optimized plans.<br>Screenshots/manual walkthrough evidence would strengthen SOP 4 if the panel asks for direct UI proof. |

### Visible Figure Evidence

**Figure 8. System Architecture**  
Source: manuscript P0568.

![Figure 8. System Architecture](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_08.png)

**Figure 9. Offline-First Planning Workflow**  
Source: manuscript P0588.

![Figure 9. Offline-First Planning Workflow](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_09.png)

**Figure 10. Offline and Online Feature Availability Map**  
Source: manuscript P0624.

![Figure 10. Offline and Online Feature Availability Map](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_10.png)

### Manuscript Statement Extracts
| Source | Extracted statement |
| --- | --- |
| P0149 | Third, most nutrition apps assume constant internet connectivity for downloading meal plans or syncing data. This is a critical limitation in the Philippines, where only 48.8% of households had internet access at home as of 2024 (up from 17.7% in 2019). Over half of Filipino homes remain offline or rely on intermittent connections, largely due to cost barriers—58.3% of offline households cite high subscription fees. An app that fails to work offline will likely exclude or frustrate a substantial portion of users, especially in rural and lower-income areas. |
| P0227 | Offline-first design asserts that applications should preserve access to essential user data and previously generated outputs even without continuous internet connectivity. In PCOSina, saved user profiles, pantry records, generated meal plans, grocery guidance, and progress-related records are maintained locally so that users can continue accessing core information under limited-connectivity conditions. |
| P0253 | Knowledge requirements consist of PCOS-supportive nutrition principles, Filipino dietary guidelines, GI/GL awareness, pantry-aware planning concepts, and optimization theory. Operations Research concepts are used to formulate the weekly meal-planning problem through binary decision variables, constraints, and objective terms. Software engineering concepts such as offline-first architecture, modular design, local data handling, and ethical decision-support boundaries guide the technical design of the system. Privacy and safety principles, including user consent, data minimization, and non-diagnostic limitations, also form part of the knowledge foundation. |
| P0264 | The software development process includes standard phases such as planning, analysis and design, development, testing, refinement, and deployment. Planning defines the study scope, target users, functional requirements, constraints, and evaluation criteria. Analysis and design focus on user flow, system architecture, data handling, offline-first continuity, and the separation of mobile, backend, optimization, and evaluation components. Development implements the mobile onboarding flow, profile management, pantry management, local persistence, plan request preparation, remote optimization, grocery guidance, feedback handling, and optional synchronization. Testing and refinement address system defects, computation correctness, usability issues, constraint behavior, and performance concerns. Deployment supports prototype evaluation through APK testing and optional Firebase-supported distribution. |
| P0279 | The generated weekly plan and related data are returned to the mobile client and stored locally to support offline-first functionality. This allows users to access previously generated meal plans, grocery lists, pantry-related suggestions, and progress information even without continuous internet connectivity. |
| P0290 | The framework also clarifies the system boundaries. PCOSina is designed as an offline-first wellness decision-support tool and is not intended for diagnosis, medical treatment, or clinical decision-making. Its contribution lies in demonstrating how software engineering principles and context-aware design can be applied to support practical, Filipino-based dietary planning for individuals managing PCOS. |
| P0371 | PCOS (Polycystic Ovary Syndrome) - A common endocrine disorder affecting individuals with ovaries, commonly diagnosed during reproductive years, and often associated with hormonal imbalance, insulin resistance, and increased risk of metabolic complications. |
| P0372 | PCOS-Supportive Diet - A dietary approach that emphasizes balanced nutrition, lower-glycemic food choices, adequate fiber, protein awareness, and portion control to support PCOS-related nutrition goals. It is not a medical prescription or treatment plan. |
| P0567 | The architecture diagram illustrates the interaction between the Android mobile application, local persistence layer, remote optimizer service, reference recipe and nutrition data, optional cloud synchronization services, and evaluation components. PCOSina follows an offline-first/local-first design in which saved user profiles, pantry records, generated meal plans, grocery guidance, and progress-related data remain accessible locally. Optional synchronization is supplementary and may support backup, recovery, authentication, or distribution when enabled. New optimized weekly plan generation is performed through the remote optimizer service. |
| P0590 | PCOSina follows an offline-first workflow in which the mobile client first retrieves locally stored data, including the user profile, pantry records, previously generated meal plans, grocery lists, and progress records. These locally persisted records remain accessible and editable when network connectivity is unavailable. When the user requests a new optimized weekly meal plan, the system may require access to the backend planner, where hard constraints such as allergies, ingredient exclusions, pantry feasibility, budget limits, nutritional bounds, meal-slot requirements, and variety rules are validated before optimization. If a feasible plan is identified, the resulting plan is returned to the mobile client and saved locally for subsequent offline access. If no feasible plan satisfies the required constraints, the system returns infeasibility guidance rather than producing an unsafe or incomplete recommendation. |
| P0626 | The offline-first design distinguishes between locally available functions, backend-dependent functions, and optional online services. Local functions include viewing and updating saved profiles, pantry records, previously generated meal plans, grocery guidance, and progress records, since these data are stored on the device. Backend-dependent functions primarily involve generating a new optimized weekly plan, because final constraint validation and optimization are performed by the backend planner. Optional online services, such as cloud synchronization, telemetry, and feedback submission, support backup, recovery, monitoring, and system improvement but are not required for accessing saved local data. Therefore, PCOSina’s offline-first design does not imply that all operations are available without connectivity; rather, it ensures that saved user data and previously accepted planning outputs remain usable offline. |
| P0655 | The meal-planning component of PCOSina implements a two-stage planning workflow in which the mobile application prepares, stores, and retrieves user data through local persistence, while the remote optimizer service performs computational planning for new optimized weekly meal plans. However, the generation of a new optimized weekly meal plan requires communication with the remote optimizer service, where the two-stage planning workflow is executed. |
| P0671 | The reduced candidate pool is then passed to Stage 2. In this stage, the system builds meal-slot domains and solves a MILP-style 0-1 weekly assignment formulation through OR-Tools CP-SAT in the remote optimizer service. The accepted assignments are converted into day-level meal plans with breakfast, lunch, and dinner entries, nutritional summaries, estimated cost information, pantry-overlap indicators, explanation data, and grocery guidance. These generated outputs are returned to the mobile application and stored locally for offline-first access. |
| P0819 | Once the remote optimizer service obtained a feasible or optimized solution, the accepted assignments were transformed into a structured seven-day meal plan containing breakfast, lunch, and dinner selections for each day. The resulting plan, nutritional summaries, pantry-overlap indicators, explanation data, estimated cost information, and grocery guidance were returned to the mobile client and stored locally for offline-first access. |
| P1094 | The portability evaluation results show that both primary and secondary users positively assessed the application’s adaptability across Android devices and usage conditions. Respondents agreed that the application installs properly, remains accessible offline, and adapts well to different devices and screen sizes. Overall, the findings indicate good portability and accessibility across supported platforms. |
| P1170 | Offline-first behavior was evaluated in terms of continuity rather than claiming that every function is fully offline. PCOSina stores important user-facing information locally so that saved plans, pantry data, grocery guidance, and progress records can remain accessible after they have been created. However, new optimized meal-plan generation may still depend on backend availability because the current planner uses the backend CP-SAT service and optional worker or queue execution path. |
| P1174 | The table above demonstrates that the system supports saved-data continuity through local caching, allowing users to access active profiles, meal plans, pantry records, grocery guides, and progress records after those records have been created. For performance evaluation, the study used average runtime, P95 runtime, and maximum runtime. P95 means that 95 percent of the completed runs finished at or below the reported time. This metric was included so the evaluation would show consistency, not only the best or average case. |
| P1206 | PCOSina was designed using an offline-first approach where essential user data such as profiles, meal plans, pantry records, and progress logs are stored locally. This enables users to access previously generated meal plans and pantry information even without internet connectivity. Evaluation results under Portability and Reliability both showed positive ratings, indicating that users can consistently access stored data across different usage conditions. |
| P1208 | However, system analysis shows that new optimized meal-plan generation may still rely on backend computation due to the CP-SAT optimization engine. Despite this limitation, offline access to saved outputs ensures continuity of use in low-connectivity environments. This design aligns with practical usability requirements in real-world settings where internet access may not always be available. |
| P1220 | The implementation confirmed the reliability of an offline-first mobile architecture within the local connectivity landscape. By leveraging local data persistence, the system guarantees continuous user access to active profile records, generated meal schedules, grocery tracking lists, and clinical progress logs in environments with intermittent or zero internet connection. However, performance testing clarifies that the synchronization framework remains backend-dependent for executing the mathematical optimization routines required to generate entirely new meal plan layouts. |

### Tables and Figures
| Evidence Type | Sources |
| --- | --- |
| Tables | Table 3: Table 3. Local Literature-to-Design Traceability Matrix (P0490)<br>Table 6: Table 6. Software Specifications (P0640)<br>Table 8: Table 8. Trigger Conditions and Evidence Indicators (P0685)<br>Table 9: Table 9. Algorithm Selection Policy (P0689)<br>Table 21: Table 21. System Test Matrix (P0861)<br>Table 37: Table 37. Reliability of PCOSina (P1083)<br>Table 40: Table 40. Portability of PCOSina (P1092)<br>Table 41: Table 41. Summary of ISO/IEC 25010 Evaluation Results for the PCOSina Application (P1098)<br>Table 42: Table 42. PCOSina Implemented Workflow and Functional Coverage (P1104)<br>Table 45: Table 45. Summary of Functional Testing Results (P1136)<br>Table 50: Table 50. Offline-First and Performance Evaluation Summary (P1172)<br>Table 51: Table 51. Twenty-Profile Planner Optimization and Final Evidence Summary (P1176) |
| Figures | Figure 4: Figure 4. Activity Diagram (P0546)<br>Figure 5: Figure 5. Sequence Diagram (P0553)<br>Figure 8: Figure 8. System Architecture (P0568)<br>Figure 9: Figure 9. Offline-First Planning Workflow (P0588)<br>Figure 10: Figure 10. Offline and Online Feature Availability Map (P0624)<br>Figure 16: Figure 16. ISO-Based Software Evaluation Model (P0837)<br>Figure 18: Figure 18. Effectiveness dimensions, metrics, and output artifacts (P0944)<br>Figure 19: Figure 19. Effectiveness-evaluation flowchart (software-evaluation scope) (P0951) |

## SOP 5: Evaluation of nutrition quality, usability, cultural relevance, and efficiency

| Bucket | Extracted / Organized Answer |
| --- | --- |
| Revised SOP question | How does the proposed system perform in terms of nutritional quality, usability, cultural relevance, and computational efficiency based on algorithmic, expert, and user-centered evaluation metrics? |
| Original manuscript SOP | P0183: How does the proposed system perform in terms of nutritional quality, usability, cultural relevance, and computational efficiency based on algorithmic and user-centered evaluation metrics? |
| Direct defense answer | PCOSina separates evaluation into algorithmic evidence, functional testing, ML ranking evidence, expert validation, and ISO/IEC 25010 user-centered results. |
| How PCOSina solves this | The study uses Cronbach alpha for instrument reliability, weighted means for ISO/IEC 25010 results, functional tests, benchmark scenarios, ML ranking metrics, pantry/offline summaries, and runtime progression. |
| Inputs required | 50 respondents, primary/secondary user surveys, expert validators, functional test matrix, 20-profile benchmark, planner logs, ML dataset, and cultural relevance protocol. |
| Outputs and evidence required | Cronbach alpha table, demographic/baseline tables, ISO/IEC 25010 tables, functional testing table, planner benchmark tables, ML metrics, pantry/offline results, and final evidence summary. |
| Metrics/results found | Cronbach alpha all above 0.700; overall ISO mean 4.02 Agree; Usability 4.06 Agree; Functional Suitability 3.93 Agree; Security 4.24 Strongly Agree; 100/100 final runs; average runtime 257 ms; P95 313 ms; max 357 ms; zero hard-rule violations; ML NDCG@10 0.9211. |
| Gaps / boundaries | Standard deviation values requested by the SOP report are not visible in the manuscript result tables.<br>Cultural relevance has protocol/formulas and survey support, but final CTS/CIS values were not found.<br>Cronbach alpha proves questionnaire consistency only; it must be paired with the algorithmic and survey results already extracted here. |

### Visible Figure Evidence

**Figure 16. ISO-Based Software Evaluation Model**  
Source: manuscript P0837.

![Figure 16. ISO-Based Software Evaluation Model](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_16.png)

**Figure 18. Effectiveness dimensions, metrics, and output artifacts**  
Source: manuscript P0944.

![Figure 18. Effectiveness dimensions, metrics, and output artifacts](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_18.png)

**Figure 20. Twenty-Profile Planner Runtime Optimization Progression**  
Source: manuscript P1110.

![Figure 20. Twenty-Profile Planner Runtime Optimization Progression](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_20.png)

**Figure 21. Scenario-Level Runtime and Nutrition Comparison**  
Source: manuscript P1127.

![Figure 21. Scenario-Level Runtime and Nutrition Comparison](docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets/figure_21.png)

### Manuscript Statement Extracts
| Source | Extracted statement |
| --- | --- |
| P0916 | Final evaluation data were analyzed using weighted mean and Cronbach’s Alpha to verify the internal consistency of the ISO/IEC 25010 survey instrument. |
| P0920 | Descriptive statistical methods were used to analyze the evaluation results and user feedback gathered during the beta testing phase. These methods included frequency distribution, percentage analysis, and weighted means to objectively assess the application based on the product quality characteristics specified by the ISO/IEC 25010 model: Functional Suitability, Performance Efficiency, Compatibility, Usability, Reliability, Security, Maintainability, and Portability. |
| P0941 | Table 22. Likert Scale for ISO/IEC 25010 Product Quality |
| P0946 | This diagram illustrates the comprehensive evaluation framework structured to assess the effectiveness and system quality of the PCOSina application. The model mapped out eight distinct evaluation components starting with the ISO IEC 25010 system quality dimensions and survey instrument reliability through Cronbach Alpha. It covered the technical verification blocks including planner correctness, nutrition accuracy, generation performance behavior, offline resilience testing, and security privacy checks. Finally, it integrated the user and expert feedback mechanisms to consolidate all software evaluation evidence. This structured breakdown mapped the precise measures and technical outputs that served as the foundational basis for the data analysis and interpretations detailed in Chapter 4. |
| P0953 | This flowchart illustrates the sequence followed during the system evaluation phase of the PCOSina application. Clinical symptom changes and biological outcomes were excluded from the study scope, as the evaluation focused solely on software quality and numerical correctness. The process began by defining the evaluation scope and collecting data through user surveys, alpha and beta testing, and professional reviews. The gathered data were then used to compute the evaluation metrics, producing analytical results. |
| P0960 | Cultural Title Share (CTS) |
| P0962 | Cultural Ingredient Share (CIS) |
| P0964 | Feasibility Rate (FR) |
| P0969 | Outputs are considered acceptable only when safety violations equal zero. |
| P0979 | The collected responses were analyzed using Microsoft Excel Data Analysis ToolPak. The variance components required for the computation of Cronbach’s Alpha were obtained from the output of the ANOVA: Two-Factor Without Replication in Excel. These values were then used to manually compute the Cronbach’s Alpha coefficient for each subscale. A Cronbach’s Alpha value closer to 1 indicates higher internal consistency and greater reliability of the instrument. The interpretation of Cronbach’s Alpha values in this study is based on established reliability thresholds, where higher values indicate stronger internal consistency of the instrument (George & Mallery, 2003). |
| P1019 | This chapter presents the analysis and interpretation of the data gathered during the study. The findings are based on system development, testing, and evaluation processes conducted on the PCOSina application. Before interpreting the actual scores, all collected survey data underwent a reliability test using Cronbach's Alpha to make sure that the research instruments are accurate and consistent. All system outputs, computed statistics, and survey results were reviewed and checked for precision by a statistician. |
| P1021 | The chapter is organized according to the specific objectives and Statement of the Problem (SOP) of the study. The first section presents the questionnaire validation and the demographic profile of the respondents. The next sections discuss the evaluation results from both primary and secondary users, showing the weighted mean interpretations based on the ISO/IEC 25010 software quality standards. These findings are further explained alongside the actual system metrics and related literature. The application was evaluated by users, IT/CS experts, and healthcare professionals in obstetrics and gynecology, nutrition, and dietetics. PCOSina works as a wellness decision-support tool and does not diagnose PCOS, prescribe treatment, or replace professional medical consultation. |
| P1025 | Cronbach's alpha is a measure of how well the items in a scale measure the same underlying construct (Cronbach, 1951). A high Cronbach's alpha indicates that the items on the scale are measuring the same underlying construct, and that the scores on the scale are consistent (Tavakol & Dennick, 2011). In this study, Cronbach's alpha was calculated for each of the software quality subscales across the separate surveys deployed for both the primary and secondary users to check the internal consistency of the pilot-tested survey instrument. This ensures that the questionnaire items under each software quality subscale are reliable for formal data collection. |
| P1031 | Cronbach’s alpha results for the eight subscales of the survey indicate good to excellent internal consistency across all measures. These findings demonstrate that the questionnaire is a trustworthy tool for assessing the PCOSina application's software quality features. Because all computed coefficients surpassed the standard minimum threshold of 0.700, the data gathered from both primary and secondary users is consistent for further analysis. |
| P1069 | An evaluation of the software was performed through the use of survey forms based on the ISO/IEC 25010 standards. Before letting the respondents answer the survey forms, the purpose of the application was explained to them and they were given a user manual showing how the mobile application works. The survey forms consisted of five questions for each of the eight categories: Functional Suitability, Performance Efficiency, Compatibility, Usability, Reliability, Security, Maintainability, and Portability, with a total of 40 questions. The results were calculated using Likert scale equations, where 1 means strongly disagree and 5 means strongly agree to compute the weighted means. |
| P1073 | The results show that both primary and secondary users positively evaluated the functional suitability of the PCOSina application, with overall weighted means interpreted as “Agree.” The highest ratings were observed in the inclusion of familiar Filipino food options and pantry-aware meal planning features. Overall, the findings indicate that the application provides meal-planning functionalities aligned with the dietary needs of users managing PCOS. |
| P1082 | The usability evaluation results indicate that respondents generally found the application easy to understand and navigate. Both primary and secondary users rated the indicators under the “Agree” interpretation, reflecting positive perceptions regarding the layout, navigation, and overall ease of use of the system. Overall, the findings suggest that the application is user-friendly for both technical and non-technical users. |
| P1094 | The portability evaluation results show that both primary and secondary users positively assessed the application’s adaptability across Android devices and usage conditions. Respondents agreed that the application installs properly, remains accessible offline, and adapts well to different devices and screen sizes. Overall, the findings indicate good portability and accessibility across supported platforms. |
| P1097 | The evaluation results of the PCOSina application based on the ISO/IEC 25010 software quality standards are summarized in the table below. The data presents a comparative look between the assessments provided by the primary and secondary users across all eight quality characteristics. Overall, the findings indicate that both user groups perceived the application positively. This collective evaluation demonstrates that the application successfully achieved acceptable and dependable software quality. |
| P1138 | To evaluate the core software architecture of PCOSina, the system underwent functional testing across representative operational scenarios. All 34 validation cases passed within their defined scope, showing that the implemented calculators, filtering rules, planning contracts, saved-data behavior, and API/schema checks operated according to the system requirements used in this study. |
| P1141 | The system creates meal plans using a two-stage process. First, it automatically filters out recipes that conflict with the user's allergies, dietary restrictions, or planning limits. A machine learning model (LightGBM) then ranks the remaining safe options using profile and recipe signals. Finally, the main scheduling engine (Google OR-Tools CP-SAT) builds the seven-day plan with three meals per day. |
| P1143 | This scheduling engine strictly enforces hard rules, such as staying within the user's budget, blocking declared allergens and dietary restrictions, maintaining the weekly meal structure, and limiting recipe repetition. At the same time, it balances flexible goals by reducing nutritional gaps and prep time while considering pantry overlap as a planning-support signal. The machine learning layer helps rank safe candidates, but the CP-SAT planner remains the final authority for constraint enforcement. |
| P1153 | The table above shows that the final planner preserved the required safety boundary. In the final 20-profile benchmark, all 100 runs completed the 21-meal weekly structure, passed constraint validation, and recorded zero hard-rule violations. Nutrition feasibility also passed in all 100 runs, while sodium or sugar items were treated as advisory warnings when applicable. When the configured budget was intentionally impossible, the system returned structured no-safe-plan feedback instead of forcing an unsafe automatic recommendation. |
| P1156 | In this section, recovery refers to measured planner-latency recovery, not a separate recovery module. The first 20-profile local benchmark already generated valid plans, but it averaged 6,154 ms, with P95 at 12,514 ms and maximum runtime at 14,013 ms. After profile-aware solve ordering, first-complete-safe-plan stopping, a smaller safe search set, cached static recipe features, and the LightGBM-assisted Stage-1 path, the final benchmark completed 100/100 runs with an average runtime of 257 ms, P95 runtime of 313 ms, maximum runtime of 357 ms, and zero hard-rule violations. |
| P1158 | The machine learning layer ranks and shortlists relevant meal choices after deterministic safety filtering has already been applied. This pre-sorted shortlist helps the CP-SAT optimizer reach feasible plans faster by reducing candidate-search pressure. However, critical rules such as allergen filtering, dietary restrictions, budget limits, meal-slot structure, repetition rules, and nutrition checks remain controlled by deterministic filtering and CP-SAT optimization. The machine learning layer improves candidate ordering, but it never overrides the mathematical planner. The table below details the data profile and accuracy metrics used to evaluate the Stage-1 machine learning layer. |
| P1163 | Pantry, grocery, and budget behaviors were evaluated to measure the practical usability of the system's core features. While the solver treats configured weekly budgets as strict hard constraints, pantry and grocery features operate on intelligent data tracking to assist user management. The table below summarizes the system's operational logic in these areas. |
| P1170 | Offline-first behavior was evaluated in terms of continuity rather than claiming that every function is fully offline. PCOSina stores important user-facing information locally so that saved plans, pantry data, grocery guidance, and progress records can remain accessible after they have been created. However, new optimized meal-plan generation may still depend on backend availability because the current planner uses the backend CP-SAT service and optional worker or queue execution path. |
| P1174 | The table above demonstrates that the system supports saved-data continuity through local caching, allowing users to access active profiles, meal plans, pantry records, grocery guides, and progress records after those records have been created. For performance evaluation, the study used average runtime, P95 runtime, and maximum runtime. P95 means that 95 percent of the completed runs finished at or below the reported time. This metric was included so the evaluation would show consistency, not only the best or average case. |
| P1178 | The table above presents the step-by-step performance improvement of the planner, from the first slow but working benchmark to the final optimized result. The same 20 realistic profiles were used so the improvement could be compared fairly, and the final evidence repeated each profile five times for 100 total runs. The important result is that the planner became faster without changing what counts as an acceptable plan. A meal plan was still accepted only after the system checked allergies and restrictions, budget limits, cooking-time limits, repetition rules, nutrition checks, and the required seven-day, three-meal structure. The LightGBM model was active in the final benchmark, but it only helped order safe choices; it did not decide whether unsafe choices could pass. |
| P1181 | The findings indicate that PCOSina achieved the main technical objectives of the study, including profile-based data collection, deterministic safety filtering, Stage 1 ranking support, CP-SAT weekly meal-plan optimization, pantry-aware grocery guidance, structured no-safe-plan handling, and offline-first continuity for saved information. |
| P1183 | The final-build local benchmark showed that the planner successfully generated complete 21-slot meal plans across realistic user profiles while preserving hard constraints. The evaluation was not based on one isolated case: it used 20 realistic planner cases, then repeated the final benchmark five times per case for 100 total runs. The final result reached an average runtime of 257 ms, a P95 runtime of 313 ms, a maximum runtime of 357 ms, and zero hard-constraint violations. In contrast, an intentionally infeasible budget produced a no-safe-plan result instead of generating an unsafe output, indicating that the defined system constraints were preserved during evaluation. |
| P1211 | The system demonstrated strong performance across nutritional quality, usability, cultural relevance, and computational efficiency based on both algorithmic and user-centered evaluations. System testing showed that PCOSina produced more accurate calorie and macronutrient outputs compared to the baseline while maintaining zero hard-constraint violations across all scenarios. Although the baseline was faster in simple cases, it produced lower diversity and degraded under stress scenarios, while PCOSina remained stable. |
| P1213 | User evaluation results also confirmed overall system effectiveness, with all ISO/IEC 25010 categories rated as “Agree,” except Security which was rated “Strongly Agree.” In addition, OB-GYN validation supported the system’s nutritional direction, particularly its focus on portion control, reduced carbohydrate intake, and weight-management-oriented planning. Overall, the findings indicate that PCOSina performs effectively as a decision-support system that balances nutritional accuracy, usability, and computational efficiency. |
| P1222 | Finally, user and expert evaluations validated the overall technical quality and health-related safety boundaries of the application. Survey feedback gathered via the ISO/IEC 25010 framework confirmed high system acceptance among the 50 respondents, highlighted by a weighted mean score of 4.06 for Usability and 3.93 for Functional Suitability. Medical validation from an Obstetrician-Gynecologist and a Registered Nutritionist-Dietitian verified that the application's emphasis on portion regulations, weight management assistance, and balanced nutrient distributions directly aligns with general evidence-based guidelines for PCOS care. Ultimately, the findings indicate that the application satisfies its benchmarks as a non-diagnostic, preference-aware wellness decision-support tool designed to assist users in dietary management without replacing clinical consultation, treatment, or medical prescription. |
| P1241 | The respondents evaluated the application based on the ISO/IEC 25010 software quality characteristics. The data showed positive evaluations across all categories, notably obtaining a weighted mean score of 4.06 (interpreted as “Agree”) for Usability and 3.93 (interpreted as “Agree”) for Functional Suitability. |
| P1253 | Algorithmic benchmarks and user-centered metrics confirmed high technical, nutritional, and operational acceptance. The successful evaluations from the 50 end-users, combined with professional verifications from CS/IT experts, an OB-GYN, and a Registered Nutritionist-Dietitian, confirm that the system satisfied the defined optimization constraints in the tested cases and provides evidence-based dietary guidance for digital PCOS care. |

### Tables and Figures
| Evidence Type | Sources |
| --- | --- |
| Tables | Table 19: Table 19. Cultural Relevance Evaluation Protocol (P0828)<br>Table 20: Table 20. Acceptance Rule and Failure Handling (P0833)<br>Table 21: Table 21. System Test Matrix (P0861)<br>Table 22: Table 22. Likert Scale for ISO/IEC 25010 Product Quality (P0941)<br>Table 23: Table 23. Software-Effectiveness Dimensions and Metrics (P0955)<br>Table 24: Table 24. Interpretation of Cronbach’s Alpha Coefficient (P0981)<br>Table 25: Table 25. Cronbach’s Alpha Results for Primary and Secondary User Surveys (P1029)<br>Table 31: Table 31. Frequency of Checking Pantry/Kitchen Inventory Before Buying Groceries of All Respondents (P1057)<br>Table 32: Table 32. Baseline Experience of Respondents Before Using PCOSina (P1064)<br>Table 33: Table 33. Functional Suitability of PCOSina (P1070)<br>Table 34: Table 34. Performance Efficiency of PCOSina (P1074)<br>Table 35: Table 35. Compatibility of PCOSina (P1077)<br>Table 36: Table 36. Usability of PCOSina (P1080)<br>Table 37: Table 37. Reliability of PCOSina (P1083)<br>Table 38: Table 38. Security of PCOSina (P1086)<br>Table 39: Table 39. Maintainability of PCOSina (P1089)<br>Table 40: Table 40. Portability of PCOSina (P1092)<br>Table 41: Table 41. Summary of ISO/IEC 25010 Evaluation Results for the PCOSina Application (P1098)<br>Table 42: Table 42. PCOSina Implemented Workflow and Functional Coverage (P1104)<br>Table 43: Table 43. Baseline Algorithm Runtime, Feasibility, and Diversity Comparison (P1117)<br>Table 44: Table 44. Baseline Algorithm Nutritional Quality and Safety Comparison (P1122)<br>Table 45: Table 45. Summary of Functional Testing Results (P1136)<br>Table 46: Table 46. Representative Final Planner Evaluation Cases (P1147)<br>Table 47: Table 47. Final Planner Constraint and Safety Outcome Summary (P1151)<br>Table 48: Table 48. Evaluation Summary of the Stage-1 ML Model (P1160)<br>Table 49: Table 49. Pantry, Grocery, and Budget Evaluation Summary (P1165)<br>Table 50: Table 50. Offline-First and Performance Evaluation Summary (P1172)<br>Table 51: Table 51. Twenty-Profile Planner Optimization and Final Evidence Summary (P1176) |
| Figures | Figure 16: Figure 16. ISO-Based Software Evaluation Model (P0837)<br>Figure 18: Figure 18. Effectiveness dimensions, metrics, and output artifacts (P0944)<br>Figure 19: Figure 19. Effectiveness-evaluation flowchart (software-evaluation scope) (P0951)<br>Figure 20: Figure 20. Twenty-Profile Planner Runtime Optimization Progression (P1110)<br>Figure 21: Figure 21. Scenario-Level Runtime and Nutrition Comparison (P1127) |

## Extracted Table Appendix

### Table 1. Summary of Key Literature Related to the Study
Source: manuscript P0440; table object 1.
| Author(s) & Year | Focus | Methodology/ Approach | Key Findings | Relevance to Current Study |
| --- | --- | --- | --- | --- |
| Barrea et al. (2022) | Dietary strategies for PCOS | Clinical review | Low-GI and Mediterranean diets improve metabolic outcomes in PCOS | Provides nutritional standards PCOSina must operationalize |
| De Guzman et al. (2022) | PCOS dietary challenges among Filipinas | Qualitative study | Economic constraints and cultural food habits hinder diet adherence | Supports need for accessible, practical meal planning systems |
| Teede et al., 2022 | Nutritional standards and lifestyle interventions for PCOS | Evidence-based guideline review / clinical recommendations | Lifestyle and diet modification, including structured meal planning, are essential for insulin resistance, weight management, and metabolic control in PCOS | Provide a guideline for the foundation of developing a rule-based meal planning algorithm, ensuring that the system provides meal plans aligned with recommended macronutrient composition, low-glycemic index foods, and other dietary considerations for women with PCOS. |
| Zeevi et al., 2022 | Personalization in meal recommendation systems | Literature review / review of personalized nutrition interventions | User-centric, individualized dietary strategies improve engagement and health outcomes compared to generic advice | Create predefined rules and decision trees to personalize meals based on preferences, restrictions, and PCOS guidelines |
| Angeles-Agdeppa et al. (2023) | Diet quality of Filipinos (Phil-HEI) | Secondary analysis of national nutrition survey data | Filipino diets show gaps in vegetable intake and high refined carbohydrate consumption | Provides empirical basis for improving diet quality through structured meal planning |
| Coleman & Bignell, 2023 | Nutrition knowledge gaps in women with PCOS | Undergraduate honors thesis / survey-based assessment | Many women with PCOS lack access to dietitian advice and have limited knowledge about diet’s role in managing their condition | Justifies rule-based diet guidance, give step-by-step meal suggestions, addressing knowledge gaps |
| Gonzales et al. (2023) | Philippine mobile health apps | App appraisal using Mobile Application Rating Scale | Apps focus on general health, lack individualized nutrition features | Identifies gap in condition-specific nutrition apps in the Philippines |
| Banal et al. (2024) | Accuracy of MyFitnessPal for Filipinos | Nutrient comparison analysis | Poor agreement with Philippine Food Composition Tables | Highlights limitations of foreign apps for Filipino users |
| Calimlim et al. (2025) | Lived experiences of Filipino women with PCOS | Qualitative narrative video analysis | Filipino women experience confusion and gaps in PCOS dietary guidance | Justifies technology-based, culturally sensitive PCOS self-management tools |
| Kumar et al., 2025 | AI-based personalized meal recommendation systems | Experimental / AI-based system implementation | AI systems can generate personalized meal plans based on health profiles, preferences, metabolic conditions, and lifestyle; improves long-term engagement | Provides a conceptual basis for personalization, allowing the system to generate tailored meal plans for women with PCOS based on predefined rules and user inputs. |

### Table 2. Summary of Key Studies Related to the Study
Source: manuscript P0481; table object 2.
| Author(s) & Year | Focus | Methodology/ Approach | Key Findings | Relevance to Current Study |
| --- | --- | --- | --- | --- |
| De Leon, et al. (2022) | Development of a low-cost meal plan for COVID-19 immunity among Filipino adults | Used MILP with 391 food variables and 31 constraints. Data was gathered via 24-hour food recall and FFQs from 120 respondents in NCR. | Found that most respondents lacked Vitamins A, C, D, E, Zinc, and Protein. The model created 7-day plans costing 68.75 pesos to 82.57 pesos daily. | Proves that MILP can solve for specific nutrient deficiencies in the Philippines. This supports the use of MILP for PCOS-specific nutrient targets. |
| Guevarra et al. (2022) | Comparison of optimization techniques for student meal plans. | Applied Linear Programming (LP) and Integer Linear Programming (ILP) to minimize costs while meeting caloric targets. | Found that while LP is cheaper theoretically, ILP is more practical because it yields whole-food portions rather than decimals. | Supports the choice of MILP/ILP for your Stage 1 optimization to ensure meal plans are realistic for Filipino users. |
| Africa (2023) | Development of food-based recommendations (FBRs) for Filipino schoolchildren. | Used Linear Programming (LP) to identify "problem nutrients" (Calcium and Vitamin C) and simulate diet scenarios using Optifood software. | Found that even with optimized local food servings (e.g., dark green leafy vegetables and eggs), some nutrients remain difficult to meet, requiring specific intervention. | Validates the use of Linear Programming to solve local nutritional gaps. It provides a baseline for identifying "problem nutrients" which can be applied to PCOS-specific targets. |
| Agrawal et al. (2025) | Algorithmic techniques in personalized nutrition systems | Review of algorithmic approaches for meal planning including ML, DL, and NLP for dietary recommendations | Identified methods to turn user data into actionable meal plans; highlighted challenges in interpretability, constraint handling, and robustness | Provides insights on algorithmic design and decision-making for personalized meal planning systems |
| Bharadwaj & Sandeep (2025) | System architecture for automated meal planning | Web-based modular system with user input, back-end processing, and food database | Structured data management enabled personalized meal plan generation; manual data entry remains a limitation | Shows how computational architecture supports algorithmic meal planning |
| Guo et al. (2025) | AI-based dietary guidance systems | Systematic review of empirical and design studies | Highlighted gaps in algorithmic implementation, input handling, and alignment with user-specific needs | Provides design insights for mathematical/logical handling of inputs and dietary rules |
| Kaçar et al. (2025) | Evaluation of generative chatbots for dietary plans | Comparative study of ChatGPT 4.0, Gemini, Microsoft Copilot for personalized calorie-controlled diets | ChatGPT 4.0 had highest accuracy for caloric compliance, all models produced acceptable meal quality | Highlights importance of underlying algorithm type and computational accuracy in meal planning systems |
| Kopitar et al. (2025) | Constraint representation in meal planning | Used decomposition of compound ingredients to improve nutrient calculations | Decomposition improved accuracy in enforcing caloric, macronutrient, and exclusion constraints | Supports computational methods for constraint handling in diet planning |
| Mohbat & Zaki (2025) | Semantic constraint handling in personalized meal planning | Knowledge graph and LLM-based system (KERL) to encode dietary rules | Enabled enforcement of hard and soft constraints dynamically, improved flexibility and accuracy | Provides a computational framework for dynamic and mathematically verifiable constraint handling |
| Saad et al. (2025) | Modular architecture in real-time nutrition assistants | Client-server system with modules for food image analysis and recommendation | Modular design allowed dynamic meal suggestions; real-time processing feasible | Supports use of modular computational design in meal planning systems |
| Starke et al. (2025) | Recommendation algorithms in automated meal planning | Systematic review of collaborative filtering and similarity-based methods in food recommender systems | Traditional methods limited diversity, need algorithms that consider long-term dietary behavior | Supports development of mathematical models for recommendation logic and user adherence |
| Ter et al. (2025) | Variational Autoencoder (VAE) in personalized meal planning | Deep learning model using latent space representation of meals to generate personalized meal plans | VAEs allowed recommendations with sparse data and improved personalization; NDCG used as evaluation metric | Demonstrates computational modeling techniques for diet personalization and ranking of recommendations |
| Yasukawa & Scholer (2025) | Structured corpus for automated meal planning | Developed healthy meal plan corpus for algorithm training and testing | Corpus enabled generation of nutritionally adequate menus; supports crowdsourcing of menu combinations | Shows importance of structured datasets for algorithmic meal planning systems |

### Table 3. Local Literature-to-Design Traceability Matrix
Source: manuscript P0490; table object 3.
| Local Source | Key Local Insight | Direct Impact on PCOSina |
| --- | --- | --- |
| Paluyo et al. (2023), BMJ Innovations, rural Philippines digital health | Local health systems require practical delivery under uneven access conditions. | Supports offline-first continuity and deferred network operations in app workflow. |
| DOST-FNRI (2025), Filipino protein source pattern | Common intake patterns are context-specific and must be reflected in diet planning assumptions. | Informs Filipino-oriented dataset curation and cultural ingredient realism. |
| Angeles-Agdeppa et al. (2023), Phil-HEI dietary evaluation | Filipino dietary quality can be assessed through structured nutrient-oriented indicators. | Supports nutrient-target framing and transparent nutrition summary reporting. |
| Bustamante et al. (2022), Manila PCOS awareness among students | Awareness and lifestyle practices are uneven even in educated groups. | Supports user guidance emphasis and clarity-first UX messaging. |
| Gonzales et al. (2023), PH mobile-app quality mapping | Local app quality studies emphasize structured quality dimensions. | Supports ISO/IEC 25010-based software effectiveness evaluation. |

### Table 4. Clinical Governance Gates Used in PCOSina
Source: manuscript P0583; table object 4.
| Governance Gate | Purpose | Description |
| --- | --- | --- |
| Requirements Gate | Defines what the system is allowed to do | Confirms that PCOSina requirements are limited to PCOS-related meal planning, profile setup, pantry management, grocery guidance, and progress tracking without diagnostic or treatment claims. |
| Constraint Gate | Protects safety rules before planning | Checks that hard constraints such as allergies, excluded ingredients, dietary restrictions, budget limits, nutrition bounds, meal-slot rules, and repetition limits are enforced before a plan is accepted. |
| Output Communication Gate | Controls how results are explained to users | Ensures that meal plans, nutrition details, grocery guidance, and no-safe-plan messages are written clearly, safely, and without implying that the app replaces medical or nutrition consultation. |
| Evaluation Gate | Confirms that the system was tested and reviewed | Verifies that functional tests, expert review, and software quality checks are used before the system outputs are presented as valid evaluation results. |

### Table 6. Software Specifications
Source: manuscript P0640; table object 6.
| Software | Description | Version | Purpose | Platform |
| --- | --- | --- | --- | --- |
| Android Studio | IDE | Latest | App development | Windows/macOS |
| Kotlin | Mobile Programming Language | Project-compatible version | Android Application Logic | Android |
| Jetpack Compose | Android UI toolkit | Project-compatible version | Declarative mobile user interface | Android |
| Android DataStore / Encrypted Shared Preferences / Local Artifacts | Local persistence technologies | Built-in / library-supported | Offline-first storage of saved profiles, pantry records, generated plans, settings, and progress-related data | Android |
| Firebase | Optional cloud service | Optional | Authentication, distribution, backup, or supplementary synchronization when enabled | Cloud |
| FastAPI | Backend web framework | Project-compatible version | API layer for plan-generation requests and optimizer service endpoints | Backend |
| PostgreSQL | Relational Database | Project-compatible version | Backend data storage and persistent server-side records | Backend / Cloud |
| OR-Tools CP-SAT | Optimization Solver | Project-compatible version | Solving the MILP-style 0-1 weekly assignment formulation | Backend |
| LightGBM | Machine-learning ranking library | Project-compatible version | Assistive candidate ranking during Stage 1 candidate construction | Backend |
| Git / GitHub | Version control and repository platform | Latest available | Collaboration, version tracking, issue tracking, and implementation traceability | Web/Desktop |
| SQLite | Database | Built-in | Offline storage | Mobile |
| Draw.io | Diagram Tool | Web-based | System modeling | Web |
| Python / Java | Backend programming language | Project-compatible | FastAPI backend, optimizer service, CP-SAT planning logic, LightGBM-assisted ranking, backend utilities | Backend |

### Table 7. Adaptive Constraint Handling Sequence
Source: manuscript P0679; table object 7.
| Level | Relaxation Rule | Stop/Continue Rule |
| --- | --- | --- |
| L1 | Use the strictest configured weekly recipe repeat limit and base nutrition tolerance. | Stop if the system forms a complete 21-slot weekly plan; otherwise, continue. |
| L2 | Widen the nutrition tolerance band based on the configured daily and weekly tolerance policy. | Stop if the system forms a complete 21-slot weekly plan; otherwise, continue. |
| L3 | Try the next configured recipe repeat limit, allowing more weekly reuse while still preventing adjacent duplicate meals. | Stop if the system forms a complete 21-slot weekly plan; otherwise, continue through the remaining supported attempts. |
| L4 | If all supported attempts fail or the planner times out, return structured no-safe-plan guidance. | Do not force a partial or unsafe recommendation; return diagnostics, blocker information, and suggested non-safety adjustments. |

### Table 8. Trigger Conditions and Evidence Indicators
Source: manuscript P0685; table object 8.
| Trigger Condition | Operational Indicator | Evidence Signal | Fallback Action |
| --- | --- | --- | --- |
| Infeasible CP-SAT result | All configured tolerance and repeat-limit attempts fail to produce a complete feasible plan | Solver diagnostics show attempted tolerance and repeat-limit combinations without a feasible solution. | Return a structured no-safe-plan response with reason codes and guidance. |
| Timeout | Planner exceeds the configured service time budget during filtering, pricing, model building, or solving. | Runtime telemetry records phase timings, solver budget metadata, or the timeout stage. | Return no-safe-plan guidance with retry suggestions and safe non-safety adjustments. |
| Insufficient candidate coverage | Too few safe candidates remain after Stage 1 filtering and shortlist construction. | Candidate diagnostics show low candidate count or exclusion summaries after hard filtering. | Return no-safe-plan guidance and suggest adjusting non-safety inputs such as budget, cooking time, or variety preference. |
| Profile conflict | User profile contains conflicting restrictions or invalid planning inputs. | Validation logic returns a conflict reason before optimization begins. | Return validation guidance before solver execution. |
| Service/network interruption | Remote optimizer request or queued plan polling cannot complete. | The mobile app cannot complete a new optimization request but still has saved local artifacts. | Preserve access to previously generated local plans, grocery guidance, and progress data; provide retry guidance when possible. |

### Table 9. Algorithm Selection Policy
Source: manuscript P0689; table object 9.
| Condition | Selected Algorithm | Label | Disclosure |
| --- | --- | --- | --- |
| Primary CP-SAT planning succeeds within the configured limits | Two-stage planning workflow using deterministic filtering,LightGBM-assisted ranking, and OR-Tools CP-SAT optimization | Optimized Plan (Feasible) | No fallback warning |
| CP-SAT planning is infeasible after supported retry attempts | Structured fallback handling | No Safe Plan Generated | Show reason codes, blocker summary, and suggested non-safety adjustments. |
| Planner timeout or remote service issue occurs | Safe failure handling and retry guidance | Planning Unavailable / Retry Needed | Inform the user that a new optimized plan could not be completed and suggest retrying later. |
| Profile contains invalid or conflicting inputs | Input validation guidance before optimization | Profile Needs Adjustment | Show which required or conflicting inputs must be corrected. |
| Saved plan already exists but new generation cannot complete | Offline-first saved-plan access | Saved Plan Available | Allow the user to view previously generated plans, grocery guidance, and progress data. |

### Table 10. Standard BMI Categories
Source: manuscript P0728; table object 15.
| BMI Range | Classification |
| --- | --- |
| Less than 18.5 | Underweight |
| 18.5 - 24.9 | Normal Weight |
| 25.0 - 29.9 | Overweight |
| 30.0 and above | Obese |

### Table 11. Hard vs Soft Constraint Policy
Source: manuscript P0741; table object 16.
| Constraint Class | Examples | Fallback Policy |
| --- | --- | --- |
| Hard (invariant) | Allergies, explicit exclusions, strict forbidden ingredients, invalid meal-slot pairings, profile validation rules. | Never relaxed; any candidate violating hard rules is rejected. |
| Soft (adaptive) | Nutrition tolerance, variety preference, and configured recipe repeat-limit attempts | Adjusted only within supported retry settings. |
| Policy-dependent | Pantry-overlap threshold, budget behavior, candidate-pool limits, and preparation-time preference | Applied based on planner configuration and system policy; not relaxed in a way that violates safety rules. |

### Table 12. Role of LightGBM in the Planning Workflow
Source: manuscript P0760; table object 17.
| Aspect | Description |
| --- | --- |
| Main purpose | Assist candidate recipe ranking |
| Input features | Nutrition values, cost estimate, cooking time, pantry overlap, restrictions, meal-slot suitability |
| Output | Bounded ranking score or ranking boost |
| Authority level | Non-authoritative |
| Cannot override | Allergies, dietary restrictions, explicit exclusions, hard safety rules, meal-slot eligibility, final CP-SAT assignment |
| Fallback behavior | Deterministic heuristic ranking is used if LightGBM is unavailable or not ready |
| Final meal-plan generator | OR-Tools CP-SAT, not LightGBM |

### Table 13. Model Variables and Core Rules Matrix
Source: manuscript P0770; table object 20.
| Symbol / Term | Meaning |
| --- | --- |
| x(r,s) | Binary variable showing whether recipe r is assigned to meal slot s. |
| y(r) | Binary variable showing whether recipe r appears at least once in the weekly plan. |
| Hard rules | Safety rules that cannot be overridden, such as allergies and explicit exclusions. |

### Table 14. Variable-to-Constraint and Objective Mapping
Source: manuscript P0774; table object 21.
| Variable Group | Primary Functions | Where It Affects Planning |
| --- | --- | --- |
| Anthropometric and lifestyle variables, such as age, weight, height, activity level, and wellness goal | Target derivation and personalization | Calorie and macronutrient target computation |
| Safety variables, such as allergies and dietary restrictions | Hard filtering and exclusion | Stage 1 filtering and Stage 2 admissibility rules |
| Economic variables, such as weekly budget | Budget control | Budget constraint, cost checking, and budget-related guidance |
| Practical variables, such as cooking-time limit, variety preference, and planning priority | Feasibility and adherence support | Candidate filtering, ranking, repetition control, and objective weighting |
| PCOS-context variables, such as insulin-resistance-related setting and symptoms | Personalization and reporting context | Target-selection context and non-diagnostic explanation outputs |
| Pantry variables, such as pantry entries | Pantry-aware planning support | Pantry-overlap scoring, candidate prioritization, and grocery guidance |

### Table 15. Sets, Parameters, and Decision Variables
Source: manuscript P0776; table object 22.
| Symbol | Meaning | Type |
| --- | --- | --- |
| R | Candidate recipes after Stage 1 filtering | Set |
| S | Weekly meal slots (D x M = 7 x 3 = 21) | Set |
| K | Nutrition dimensions, such as calories, protein, carbohydrates, and fat | Set |
| I | Ingredient set used for pantry and grocery guidance | Set |
| x_(r,s) | 1 if recipe r assigned to slot s, else 0 | Binary decision variable |
| y_r | 1 if recipe r is used at least once during the week, otherwise 0 | Binary helper variable |
| u_k, v_k | Under-target and over-target nutrition deviation for nutrition dimension k | Non-negative deviation variables |
| g_i | Estimated grocery gap for ingredient i, when ingredient quantity estimates are available | Non-negative guidance variable |

### Table 16. Objective Function Parameters, Penalty Terms, and Optimization Weights
Source: manuscript P0784; table object 24.
| Symbol / Term | Meaning |
| --- | --- |
| Z | Overall objective value minimized by the optimizer. |
| u(k), v(k) | Under-target and over-target nutrition deviation terms for nutrition dimension k. |
| g(i) | Estimated grocery gap for ingredient i, when quantity estimates are available. |
| λ1 to λ6 | Planner weights that control the importance of each objective term. |
| PantryReward | Reward term for recipes that better match pantry entries. |
| DiversityReward | Reward term for broader meal variety across the weekly plan. |

### Table 17. Core Constraints
Source: manuscript P0786; table object 25.
| Constraint Family | Equation Form | Purpose |
| --- | --- | --- |
| Slot assignment completeness | For each meal slot s, exactly one allowed recipe is assigned. | Ensures each breakfast, lunch, and dinner slot has one meal. |
| Recipe-use linking | If recipe r is assigned to any slot, y_r marks the recipe as used. | Supports repetition and variety control. |
| Restriction and allergy safety | X_r,s = 0 for recipes violating allergies, restrictions, exclusions, or slot eligibility. | Prevents prohibited recipes from appearing in the final plan. |
| Nutrition range with deviation | Nutrition totals are compared against lower and upper target ranges using deviation variables. | Keeps the plan near calorie and macronutrient targets while measuring deviation. |
| Budget control | Total estimated cost must stay within the weekly budget when a hard budget is configured. | Supports budget-aware planning and prevents undisclosed budget violations. |
| Pantry and grocery guidance | Pantry overlap is rewarded, and grocery gaps may be estimated when ingredient quantity data are available. | Supports pantry-aware planning and grocery guidance. |
| Variety/repetition limit | The number of times the same recipe appears is limited by repeat settings. | Avoids excessive repetition across the weekly plan. |

### Table 18. Hard vs Soft Constraint Taxonomy
Source: manuscript P0789; table object 26.
| Constraint | Class | Relaxation Policy |
| --- | --- | --- |
| Allergy and explicit restriction rules | Hard | Never relaxed in primary or fallback handling. |
| Meal-slot validity and one-meal-per-slot requirement | Hard | Never relaxed; required for a valid weekly plan. |
| Adjacent duplicate prevention | Hard | Preserved to avoid immediate meal repetition. |
| Weekly budget cap, when configured as hard | Hard / policy-dependent | Enforced as a budget ceiling when enabled. |
| Nutrition target band | Soft / bounded | Managed through tolerance ranges and deviation penalties. |
| Variety and repetition preference | Soft / policy-dependent | Adjusted only within supported retry settings. |
| Pantry-overlap preference | Soft / policy-dependent | Used as a reward, scoring signal, or shortlist threshold; not treated as a full pantry-availability guarantee. |
| Preparation-time preference | Policy-dependent | May affect filtering, scoring, or candidate ranking depending on planner configuration. |

### Table 19. Cultural Relevance Evaluation Protocol
Source: manuscript P0828; table object 28.
| Protocol Step | Method | Why It Matters |
| --- | --- | --- |
| P1 Scenario execution | Run predefined user scenarios and record the selected meals per scenario. | Ensures that cultural relevance is evaluated across multiple cases, not only one sample meal plan. |
| P2 Metric computation | Compute the Cultural Taste Score (CTS) and Cultural Ingredient Score (CIS) for each scenario. | Separates dish familiarity from ingredient familiarity. |
| P3 Safety checking | Check that generated plans have no hard safety violations, such as allergy or dietary restriction conflicts. | Ensures that cultural relevance does not override user safety. |
| P4 Result summary | Summarize the cultural relevance scores and review the results against the generated meal-plan data. | Ensures that the reported cultural relevance results are consistent with the actual system outputs. |

### Table 20. Acceptance Rule and Failure Handling
Source: manuscript P0833; table object 29.
| Condition | Decision | Required Action |
| --- | --- | --- |
| Safety violations are found in any scenario | Fail | Reject the output set for cultural acceptance and investigate allergy, restriction, or constraint enforcement. |
| Mean CTS or Mean CIS falls below the required threshold | Conditional fail | Review recipe dataset coverage, cultural tags, and filtering rules. |
| Reported scores are inconsistent with the generated meal-plan records | Audit fail | Recheck the source records, recompute the metrics, and correct the report tables if needed. |
| All safety and cultural relevance conditions pass | Accept | Report cultural relevance as software-evaluated evidence, not as a clinical or dietary outcome claim. |

### Table 21. System Test Matrix
Source: manuscript P0861; table object 30.
| Test Case | Input Condition | Expected Output |
| --- | --- | --- |
| TC1 - Feasible CP-SAT plan generation | Complete profile, normal budget, adequate recipe coverage, and moderate restrictions | Optimized weekly meal plan is returned without fallback or warning label. |
| TC2 - Infeasible planning scenario | Tight candidate pool, highly restrictive preferences, or conflicting planning conditions | System returns structured no-safe-plan guidance with reason codes, blocker summary, and suggested non-safety adjustments. |
| TC3 - Timeout or service limit | Planner exceeds configured service time budget during filtering, model building, or solving | System returns timeout or retry guidance without producing an unsafe or partial recommendation. |
| TC4 - Hard constraint safety | Allergy-restricted or restriction-sensitive profile with conflicting recipes in the source pool | No returned plan contains hard-forbidden ingredients or restriction-violating recipes. |
| TC5 - Profile validation conflict | Missing required fields, invalid values, or contradictory restrictions | System blocks plan generation and displays validation guidance before optimization begins. |
| TC6 - Offline-first saved-data access | Device has no active connection but has previously generated plans or saved local data | Users can still access saved plans, grocery guidance, pantry records, and progress-related data. |
| TC7 - Pantry-aware grocery guidance | User pantry entries partially match selected recipe ingredients | The system displays pantry-overlap indicators and grocery guidance for ingredients that may need to be purchased. |
| TC8 - LightGBM unavailable or not ready | ML ranking model is unavailable, stale, or disabled | The system continues using deterministic heuristic ranking and proceeds without allowing ML to affect hard constraints. |

### Table 22. Likert Scale for ISO/IEC 25010 Product Quality
Source: manuscript P0941; table object 31.
| Scale | Mean Range | Interpretation | Degree of Agreement |
| --- | --- | --- | --- |
| 5 | 4.21 - 5.00 | Excellent | Strongly Agree |
| 4 | 3.41 - 4.20 | Very Good | Agree |
| 3 | 2.61 - 3.40 | Good | Neutral |
| 2 | 1.81 - 2.60 | Fair | Disagree |
| 1 | 1.00 - 1.80 | Poor | Strongly Disagree |

### Table 23. Software-Effectiveness Dimensions and Metrics
Source: manuscript P0955; table object 32.
| Effectiveness Dimension | Evaluation Metrics | Out of Scope |
| --- | --- | --- |
| Software quality | ISO/IEC 25010 selected characteristics and respondent ratings | Clinical symptom change |
| Instrument reliability | Cronbach alpha for questionnaire consistency | Clinical diagnostic validity |
| Numeric correctness | AE and PE against manual references | Biological outcome effectiveness |
| Planner behavior | Runtime, feasibility, compliance deviation, repetition, pantry utilization | Therapeutic efficacy claims |
| Cultural relevance | Cultural Taste Score (CTS), Cultural Ingredient Score (CIS), and scenario-based cultural relevance review | Long-term dietary adherence or clinical nutrition outcome |

### Table 24. Interpretation of Cronbach’s Alpha Coefficient
Source: manuscript P0981; table object 34.
| Cronbach’s Alpha Value | Interpretation |
| --- | --- |
| ≥ 0.90 | Excellent |
| 0.80 - 0.89 | Good |
| 0.70 - 0.79 | Acceptable |
| 0.60 - 0.69 | Questionable |
| 0.50 - 0.59 | Poor |
| < 0.50 | Unacceptable |

### Table 25. Cronbach’s Alpha Results for Primary and Secondary User Surveys
Source: manuscript P1029; table object 35.
| Evaluation Subscale (Category) | No. of Items | Primary Users Alpha (α) | Remarks (Primary) | Secondary Users Alpha (α) | Remarks (Secondary) |
| --- | --- | --- | --- | --- | --- |
| Functional Suitability | 5 | 0.866 | Good | 0.927 | Excellent |
| Performance Efficiency | 5 | 0.858 | Good | 0.962 | Excellent |
| Compatibility | 5 | 0.922 | Excellent | 0.841 | Good |
| Usability | 5 | 0.916 | Excellent | 0.869 | Good |
| Reliability | 5 | 0.825 | Good | 0.886 | Good |
| Security | 5 | 0.879 | Good | 0.948 | Excellent |
| Maintainability | 5 | 0.972 | Excellent | 0.933 | Excellent |
| Portability | 5 | 0.908 | Excellent | 0.870 | Good |

### Table 31. Frequency of Checking Pantry/Kitchen Inventory Before Buying Groceries of All Respondents
Source: manuscript P1057; table object 41.
| Category | Frequency |
| --- | --- |
| Always | 26 |
| Often | 5 |
| Sometimes | 15 |
| Rarely | 2 |
| Never | 2 |
| Total | 50 |

### Table 32. Baseline Experience of Respondents Before Using PCOSina
Source: manuscript P1064; table object 42.
| Category | Mean | Interpretation |
| --- | --- | --- |
| Meal Planning Difficulty | 3.88 | Agree |
| Budget–Diet Balancing | 3.74 | Agree |
| Pantry / Inventory Management | 3.84 | Agree |
| Resource Utilization Efficiency | 3.66 | Agree |
| Meal Variety Limitation | 3.42 | Agree |

### Table 33. Functional Suitability of PCOSina
Source: manuscript P1070; table object 43.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app provides meal plans that are suitable for individuals managing PCOS. | 3.84 | 3.68 |
| The meal suggestions help support a healthy diet for my condition/appropriate for PCOS users. | 3.96 | 3.80 |
| The app includes Filipino food options that are familiar to me. | 4.28 | 3.84 |
| The meal plans consider the ingredients I already have at home. | 4.04 | 3.72 |
| The app helps me reduce unnecessary food purchases by using available ingredients. | 4.24 | 3.88 |
| AVERAGE | 4.07 | 3.78 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 3.93 | 3.93 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 34. Performance Efficiency of PCOSina
Source: manuscript P1074; table object 44.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app generates my weekly meal plan within a reasonable time (around 1 minute or less). | 3.56 | 3.84 |
| The app responds quickly when I use its features. | 3.80 | 3.88 |
| The app does not lag when I input or select ingredients. | 4.04 | 4.08 |
| The app runs smoothly even when generating full meal plans. | 4.28 | 4.24 |
| The app does not slow down my phone during use. | 4.28 | 4.12 |
| AVERAGE | 3.99 | 4.03 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 4.01 | 4.01 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 35. Compatibility of PCOSina
Source: manuscript P1077; table object 45.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app works properly on my Android device. | 4.04 | 4.00 |
| The app runs well without affecting other apps on my phone. | 4.08 | 4.04 |
| The app correctly displays my meal plans and pantry data. | 4.12 | 3.96 |
| The app works both online and offline without problems. | 3.92 | 4.08 |
| The app functions properly regardless of device conditions. | 4.04 | 4.04 |
| AVERAGE | 4.04 | 4.02 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 4.03 | 4.03 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 36. Usability of PCOSina
Source: manuscript P1080; table object 46.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app is easy to understand even for first-time users. | 4.16 | 4.24 |
| The layout and design are clear and simple. | 4.00 | 4.04 |
| I can easily navigate through the app’s features. | 3.88 | 4.16 |
| The instructions in the app are easy to follow. | 4.00 | 4.24 |
| The app is user-friendly for people without technical background. | 3.88 | 4.04 |
| AVERAGE | 3.98 | 4.14 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 4.06 | 4.06 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 37. Reliability of PCOSina
Source: manuscript P1083; table object 47.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app works without frequently crashing or freezing. | 3.68 | 4.24 |
| The app continues to function properly every time I use it. | 3.84 | 4.20 |
| The app does not stop unexpectedly during use. | 3.48 | 4.16 |
| My data remains safe even if the app suddenly closes. | 3.96 | 4.40 |
| The app consistently generates correct and usable meal plans. | 3.84 | 4.20 |
| AVERAGE | 3.76 | 4.24 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 4.00 | 4.00 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 38. Security of PCOSina
Source: manuscript P1086; table object 48.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| My personal health information is kept private in the app. | 4.12 | 4.52 |
| Only I can access my account and data. | 4.00 | 4.44 |
| The app protects my information from unauthorized access. | 3.88 | 4.52 |
| My stored data is secure within the app. | 4.12 | 4.48 |
| The app ensures my personal records are not shared with others. | 3.84 | 4.48 |
| AVERAGE | 3.99 | 4.49 |
| INTERPRETATION | Agree | Strongly Agree |
| TOTAL AVERAGE | 4.24 | 4.24 |
| OVERALL INTERPRETATION | Strongly Agree | Strongly Agree |

### Table 39. Maintainability of PCOSina
Source: manuscript P1089; table object 49.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app continues to improve through updates. | 4.16 | 3.64 |
| Updates do not break existing features. | 4.16 | 3.52 |
| The app can support new features in the future. | 4.20 | 3.44 |
| The app remains stable after updates or improvements. | 4.32 | 3.56 |
| The app is consistently maintained and improved. | 4.28 | 3.52 |
| AVERAGE | 4.22 | 3.54 |
| INTERPRETATION | Strongly Agree | Agree |
| TOTAL AVERAGE | 3.88 | 3.88 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 40. Portability of PCOSina
Source: manuscript P1092; table object 50.
| Questions | Primary Users | Secondary Users |
| --- | --- | --- |
| The app works well on different Android phones. | 4.00 | 4.08 |
| The app installs and runs without issues. | 4.04 | 4.04 |
| I can still use the app even without an internet connection. | 4.04 | 3.88 |
| My saved meal plans and pantry data remain accessible offline. | 4.16 | 4.12 |
| The app adapts well to different screen sizes and device versions. | 4.00 | 4.04 |
| AVERAGE | 4.05 | 4.03 |
| INTERPRETATION | Agree | Agree |
| TOTAL AVERAGE | 4.04 | 4.04 |
| OVERALL INTERPRETATION | Agree | Agree |

### Table 41. Summary of ISO/IEC 25010 Evaluation Results for the PCOSina Application
Source: manuscript P1098; table object 51.
| Category | Primary User Average Mean | Secondary User Average Mean | Overall Mean | Verbal Interpretation |
| --- | --- | --- | --- | --- |
| Functional Suitability | 4.07 | 3.78 | 3.93 | Agree |
| Performance Efficiency | 3.99 | 4.03 | 4.01 | Agree |
| Compatibility | 4.04 | 4.02 | 4.03 | Agree |
| Usability | 3.98 | 4.14 | 4.06 | Agree |
| Reliability | 3.76 | 4.24 | 4.00 | Agree |
| Security | 3.99 | 4.49 | 4.24 | Strongly Agree |
| Maintainability | 4.22 | 3.54 | 3.88 | Agree |
| Portability | 4.05 | 4.03 | 4.04 | Agree |
| OVERALL TOTAL | 4.01 | 4.03 | 4.02 | Agree |

### Table 42. PCOSina Implemented Workflow and Functional Coverage
Source: manuscript P1104; table object 52.
| Module / Screen | Role in the User Workflow |
| --- | --- |
| Splash | Initial branding, offline-first framing, and startup feedback. |
| Login | Existing user sign-in and local account handoff. |
| Sign Up | Account creation, validation, and verification guidance. |
| Onboarding | First-time orientation and expectation setting. |
| Profile Setup Step 1 | Identity, anthropometrics, and health-profile entry. |
| Profile Setup Step 2 | Dietary restrictions, symptom inputs, and medical planning constraints. |
| Profile Setup Step 3 | Household size, pantry behavior, and planning preferences. |
| Goal Selection | The user chooses weight loss, symptom management, or general health focus. |
| Dashboard | Status-first weekly landing page with next-step guidance. |
| Meal Plan | Weekly plan generation, day browsing, and meal swap flow. |
| Grocery List | Pantry-aware grocery generation with category grouping and sharing. |
| Progress | Meal logging, reflection capture, and weekly/daily progress tracking. |
| Recipe Details | Recipe rationale, ingredients, instructions, and meal logging handoff. |
| Settings | Reminder controls, profile summary, and maintenance tools. |

### Table 43. Baseline Algorithm Runtime, Feasibility, and Diversity Comparison
Source: manuscript P1117; table object 53.
| Scenario | Test condition | Baseline time | PCOSina time | Completion result | Variety result | Main finding |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Standard profile | 0.29 s | 1.91 s | Both completed | Baseline 0.00; PCOSina 3.00 | PCOSina used more time to produce a less repetitive weekly plan. |
| S2 | Budget and No Pork | 0.22 s | 1.17 s | Both completed | Baseline 0.00; PCOSina 3.00 | PCOSina preserved the budget and restriction while improving variety. |
| S3 | Vegetarian and lactose-intolerant | 0.14 s | 3.94 s | Both completed | Baseline 0.00; PCOSina 3.19 | The more restrictive profile required more planning work but produced the strongest variety score. |
| S4 | Pantry-aware and No Beef | 0.29 s | 2.47 s | Both completed | Baseline 0.00; PCOSina 3.00 | PCOSina considered pantry information while keeping weekly variety. |

### Table 44. Baseline Algorithm Nutritional Quality and Safety Comparison
Source: manuscript P1122; table object 54.
| Scenario | Baseline calorie gap | PCOSina calorie gap | Baseline macro gap | PCOSina macro gap | Baseline hard-rule issues | PCOSina hard-rule issues |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | 531.57 kcal | 530.14 kcal | 33.27% | 33.10% | 0 | 0 |
| S2 | 540.43 kcal | 539.00 kcal | 33.27% | 33.02% | 0 | 0 |
| S3 | 605.43 kcal | 601.86 kcal | 36.31% | 36.28% | 0 | 0 |
| S4 | 565.71 kcal | 557.14 kcal | 33.97% | 33.10% | 0 | 0 |

### Table 45. Summary of Functional Testing Results
Source: manuscript P1136; table object 55.
| Test Category | Feature Tested | Pass/Fail | Result | Interpretation |
| --- | --- | --- | --- | --- |
| Health computation | BMI, BMR, TDEE, calorie target, and macro target checks | Pass | Unit and manual checks confirmed the expected computation paths. | Numerical outputs were traceable to the defined formulas, with acceptable rounding differences documented where applicable. |
| Safety filtering | Allergy and dietary restriction exclusion, including custom ingredient allergies such as chicken | Pass | Allergen and restriction behavior was tested. | Declared unsafe ingredients are filtered before planning. |
| Planner constraints | Budget cap, household scaling, cooking time, and repetition rules | Pass | Constraint behavior was tested across representative cases. | The planner preserved the two-stage planning behavior and did not relax hard constraints. |
| Planner contract | Seven-day, three-meal plan structure | Pass | Plan structure was checked against the expected 21-slot weekly format. | The planning horizon and meal-slot assumptions were explicitly enforced. |
| Pantry/grocery aggregation | Grocery list merging, estimated pricing, and pantry-overlap support | Pass | Selected-plan ingredients were consolidated and pantry-covered items were identified for grocery guidance. | Pantry data supports ranking and grocery organization, while final safety and feasibility remain controlled by planner constraints. |
| No-safe-plan handling | Response when constraints prevent a safe complete plan | Pass | Infeasible cases returned structured no-safe-plan behavior instead of unsafe fallback output. | The system reports infeasibility when constraints cannot be satisfied. |
| Offline reliability | Availability of saved information without continuous connectivity | Pass | Saved-data access was reviewed under offline-first conditions. | Existing profiles, meal plans, pantry entries, grocery guides, and progress records remain accessible after being saved. |
| Input and API validation | Profile completion, invalid inputs, and schema handling | Pass | Input-validation and schema behavior were tested. | Invalid or incomplete requests were handled before or during planning. |

### Table 46. Representative Final Planner Evaluation Cases
Source: manuscript P1147; table object 56.
| Evaluation Case | Description | Main Constraint Tested |
| --- | --- | --- |
| Standard weekly plan | Default realistic user profile from the 20-profile benchmark. | Complete seven-day, 21-meal generation |
| Budget and restriction case | Budget-sensitive and No Pork profiles from the final benchmark. | Budget handling and restriction filtering |
| Dietary-restricted case | Vegetarian and lactose-intolerant profiles from the final benchmark. | Restrictive candidate-pool handling |
| Allergy-sensitive cases | Gluten, shellfish, egg, peanut, and tree-nut allergy profiles. | Allergy filtering before plan generation |
| Pantry-aware case | Pantry-heavy profile with available household ingredients. | Pantry overlap for ranking and grocery guidance |
| No-safe-plan boundary | Intentionally impossible PHP 20 weekly budget from contract testing. | Safe infeasibility reporting instead of unsafe fallback output |

### Table 47. Final Planner Constraint and Safety Outcome Summary
Source: manuscript P1151; table object 57.
| Evaluation Area | Evidence Used | Final Result | Safety Result | Nutrition / Practical Result | Interpretation |
| --- | --- | --- | --- | --- | --- |
| Weekly plan structure | Final 20-profile benchmark, 100 total runs | 100/100 runs produced 21 meal slots | 0 hard-rule violations | Nutrition feasibility passed in 100/100 runs | The planner consistently produced complete weekly plans under valid profiles. |
| Standard profile | T01 Default new user, 5 repeated runs | 5/5 passed; average 247.8 ms | 0 hard-rule violations | Nutrition feasibility passed | Ordinary plan generation worked under the final optimized planner. |
| Budget and restriction profiles | Budget-sensitive, No Pork, and low-budget valid cases | Representative budget/restriction cases passed | 0 hard-rule violations | Budget validation remained active | The planner preserved configured budget and restriction rules. |
| Allergy-sensitive profiles | Gluten, shellfish, egg, peanut, and tree-nut allergy cases | All allergy-profile runs passed | 0 hard-rule violations | Unsafe allergy matches were blocked before planning | Declared allergy conflicts were not allowed into the final meal plan. |
| Pantry-aware profile | T19 Pantry-heavy user, 5 repeated runs | 5/5 passed; average 301.8 ms | 0 hard-rule violations | Pantry data supported ranking and grocery guidance | Pantry information improved planning support without replacing safety checks. |
| No-safe-plan boundary | PHP 20 infeasible weekly budget contract test | Returned no-safe-plan | Unsafe fallback avoided | Not applicable because no safe plan exists | The system reports infeasibility when user limits cannot be satisfied. |

### Table 48. Evaluation Summary of the Stage-1 ML Model
Source: manuscript P1160; table object 58.
| Metric | Observed Value | Interpretation |
| --- | --- | --- |
| Dataset version | ml-dataset-v1-1775742965 | Offline dataset snapshot used for the current Stage-1 ranking model. |
| Rows / positive rate | 74,112 rows / 0.0627 | Shows the size of the candidate dataset and the selected-candidate rate used for training and testing. |
| Unique requests / users | 360 / 60 | Shows request-level and user-level coverage in the training evidence. |
| Train / val / test split | 60,319 / 7,350 / 6,443 | The request-level split reduces leakage between model training and final testing. |
| Feature count | 46 | Represents the profile, recipe, pantry, budget, and feedback variables used for candidate ranking. |
| Test AUC | 0.9373 | Indicates that selected and non-selected recipe candidates were generally separable. |
| Test log loss | 0.1024 | Shows low prediction error for the model probabilities on the test split. |
| Test NDCG@10 | 0.9211 | Shows that relevant selected candidates usually appeared near the top of the ranked list. |
| Baseline NDCG@10 | 0.8838 | Reference top-10 ranking quality using the deterministic heuristic score. |
| Test MAP@10 | 0.6779 | Shows precision-oriented ranking quality within the top 10 candidates. |
| Baseline MAP@10 | 0.6275 | Reference top-10 precision before learned ranking was applied. |

### Table 49. Pantry, Grocery, and Budget Evaluation Summary
Source: manuscript P1165; table object 59.
| Computation Area | Observed System Behavior | Interpretation |
| --- | --- | --- |
| Pantry-Overlap Behavior | Scenario 4 recorded 38 pantry-overlap matches (e.g., chicken, egg, garlic, tomato) to influence ranking. | Optimizes recipe ranking and prioritizes meals using on-hand ingredients. |
| Grocery Deficit & Guidance | The grocery module consolidates recipe requirements and automatically flags pantry-covered items. | Streamlines shopping lists by separating available kitchen items from needed groceries. |
| Price Computation | Estimates costs using structured baseline pricing (e.g., egg at PHP 7 per piece). | Provides a functional spending baseline for weekly budgetary planning. |
| Budget Enforcement | Scenario 2 successfully generated a plan within the PHP 1,000 cap; Scenario 5 blocked generation at an impossible PHP 20 cap. | Ensures hard economic limits are never bypassed or relaxed. |

### Table 50. Offline-First and Performance Evaluation Summary
Source: manuscript P1172; table object 60.
| Evaluation Area | System Logic & Evidence | Practical Interpretation |
| --- | --- | --- |
| Saved data continuity | Profile, meal plans, pantry data, grocery guides, and progress records are cached through local persistence. | Users can access and manage already saved information offline after it has been created. |
| New plan generation | Optimized generation uses the backend CP-SAT planning service and optional worker or queue path. | Establishes a hybrid architecture where optimization requires active backend execution. |
| Observed final-build runtime | Final real-LightGBM benchmark completed 100/100 production-shaped local runs with average 257 ms, P95 313 ms, max 357 ms, and zero hard-rule violations. | Demonstrates sub-second execution efficiency under standard local benchmark conditions while preserving hard-rule validation. |
| Initial planner benchmark | The first 20-profile local benchmark averaged 6,154 ms, with P95 at 12,514 ms and maximum runtime at 14,013 ms. | Serves as the starting benchmark used to quantify the final optimization gains. |
| Performance interpretation | The final version uses a smaller safe recipe pool, better attempt ordering, cached recipe information, and faster solver settings while keeping the same hard-rule checks. | Post-optimization metrics show that planner processing time was substantially reduced for the tested path. |

### Table 51. Twenty-Profile Planner Optimization and Final Evidence Summary
Source: manuscript P1176; table object 61.
| Evaluation Step | Measured Result | Improvement Made or Evidence Used | Meaning for the Study |
| --- | --- | --- | --- |
| Benchmark test set | 20 realistic planner profiles were tested. The final evidence repeated each profile 5 times, giving 100 total runs. | Stored benchmark profiles and final benchmark report. | The evaluation covers common cases such as ordinary planning, budget limits, allergies, dietary restrictions, pantry use, and no-budget planning. |
| Starting benchmark | All 20 profiles produced valid results, but the average time was 6.154 seconds. Slower runs reached 12.514 seconds at P95 and 14.013 seconds at maximum. | First local 20-profile benchmark. | The planner was correct, but still too slow for a smooth meal-planning experience. |
| Better first attempts | All 20 profiles still passed. Average time improved to 4.669 seconds, P95 to 6.679 seconds, and maximum to 7.612 seconds. | The planner tried the settings most likely to work earlier. | This reduced waiting time without changing the safety rules. |
| Return first complete safe plan | All 100 repeated runs passed. Average time improved to 970 ms, P95 to 2.770 seconds, and maximum to 3.328 seconds. | When one valid plan was enough, the planner stopped after finding the first complete safe weekly plan. | This prioritized fast and safe meal-plan generation for interactive use. |
| Actual ML-assisted run | All 100 runs passed with the real LightGBM model active. Average time was 1.004 seconds, P95 was 3.043 seconds, maximum was 3.493 seconds, and there were 0 hard-rule violations. | The actual Stage-1 ranking model was required during the benchmark. | The ML-assisted route improved ordering but did not bypass the planner's safety checks. |
| Smaller safe search set | All 100 runs passed. Average time improved to 531 ms, P95 to 953 ms, maximum to 1.260 seconds, and there were 0 hard-rule violations. | The planner used profile-aware ordering and a smaller set of safe recipe candidates. | The system became consistently fast under the local benchmark while keeping the same safety checks. |
| Final optimized planner | All 100 final runs passed. Average time was 257 ms (0.257 seconds), P95 was 313 ms, maximum was 357 ms, and there were 0 hard-rule violations. | Final local benchmark with the current LightGBM-assisted planner path active. | This is the final performance result for the current system version. |
| Safe-failure case | Under an impossible PHP 20 weekly budget, the boundary case returned no-safe-plan. | Final boundary-case evidence and no-safe-plan tests. | The system refuses to create an unsafe or impossible meal plan when the user's limits cannot be satisfied. |

## Extracted Figure Appendix
- Figure 1. Conceptual Framework - P0244; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_01.png`
- Figure 4. Activity Diagram - P0546; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_04.png`
- Figure 5. Sequence Diagram - P0553; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_05.png`
- Figure 8. System Architecture - P0568; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_08.png`
- Figure 9. Offline-First Planning Workflow - P0588; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_09.png`
- Figure 10. Offline and Online Feature Availability Map - P0624; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_10.png`
- Figure 12. Two-Stage MILP-Based Meal Planning Algorithm - P0662; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_12.png`
- Figure 13. Adaptive fallback flowchart for weekly meal-plan generation - P0665; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_13.png`
- Figure 14. Relaxation ladder used by adaptive fallback - P0677; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_14.png`
- Figure 15. High-Level Pseudocode - P0814; extracted as table-like pseudocode text in the source manuscript.
- Figure 16. ISO-Based Software Evaluation Model - P0837; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_16.png`
- Figure 18. Effectiveness dimensions, metrics, and output artifacts - P0944; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_18.png`
- Figure 19. Effectiveness-evaluation flowchart (software-evaluation scope) - P0951; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_19.png`
- Figure 20. Twenty-Profile Planner Runtime Optimization Progression - P1110; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_20.png`
- Figure 21. Scenario-Level Runtime and Nutrition Comparison - P1127; extracted image: `docs\defense\PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets\figure_21.png`