# Chapter 5 And 6 Writing Guide

## Chapter 5

Summary of computational validation findings:
This section should report the manual-computation validation of the actual implemented formulas. Present BMI, BMR, TDEE, calorie target, macro target, pantry match, and price-estimation checks in tabular form. State how many computations matched the expected system result exactly or within acceptable floating-point tolerance. Do not claim clinical validation; keep the discussion limited to correctness of implemented computations.

Summary of software evaluation findings:
This section should summarize ISO 25010 questionnaire results after respondents have answered them. Discuss functional suitability, performance efficiency, compatibility/offline support, usability, reliability, and security/privacy only using collected respondent ratings. If maintainability is evaluated, separate IT-expert results from general-user results.

Summary of expert validation findings:
This section should summarize the nutrition/health expert review of the implemented meal-planning criteria, nutrition explanation, allergy/restriction safety, grocery usefulness, and Filipino meal relevance. Use actual expert comments and ratings only after they have been collected.

Interpretation of ISO 25010 ratings:
Interpret the weighted means using the approved interpretation scale from the statistician. Do not write final rating labels until the actual response data exists. At this stage, the manuscript can state that the tables are ready to receive respondent results.

Discussion of system strengths:
Focus on strengths that are directly supported by the codebase and validation evidence, such as offline-first local persistence, deterministic rule-based safety filtering, CP-SAT-based authoritative optimization, structured no-safe-plan guidance, and bundled Filipino recipe data.

Discussion of system limitations:
Explicitly discuss the current implementation gaps that affect thesis claims. These include the lack of a hard pantry-feasibility constraint, Android/backend price-catalog differences, Android/backend calorie-preview rounding differences, and the fact that local execution of OR-Tools-backed planner code may require an environment where OR-Tools can load correctly.

## Chapter 6

Summary:
The chapter summary should restate that PCOSina was validated as a wellness decision-support system using actual implemented formulas, actual rule-based planner logic, actual bundled datasets, actual tests, and planned respondent/expert evaluation instruments. Avoid any sentence that implies medical diagnosis, treatment prescription, or clinical effectiveness.

Conclusions per objective:
Write one conclusion per thesis objective. For computational objectives, conclude based on the manual-vs-system validation tables. For software-quality objectives, conclude only after ISO 25010 data has been collected. For expert-appropriateness objectives, conclude only after expert validation responses have been obtained.

Recommendations:
Recommendations should prioritize evidence gaps that matter for the defense: parity tests between Android preview and backend planner output, additional formula unit tests, expert validation completion, statistician review of instruments, and thesis wording corrections where implementation differs from expected terminology.

Future work:
Future work may include unifying price estimation across Android and backend, adding a true hard pantry-feasibility mode if desired, improving local execution portability for OR-Tools environments, expanding nutrition data coverage such as sodium and sugar in Android DTOs, and extending validation with larger respondent and expert samples.

Limitations:
State that respondent data, expert ratings, and finalized statistical analysis are not present in the repository at the time this evidence pack was generated. Also state that any production-like runtime metrics or live database override data were not treated as bundled source-of-truth evidence unless they existed directly in the repository.
