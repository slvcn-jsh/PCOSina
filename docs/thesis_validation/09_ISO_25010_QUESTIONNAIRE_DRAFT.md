# ISO 25010 Questionnaire Draft

Scale:
- 5 - Strongly Agree
- 4 - Agree
- 3 - Neutral
- 2 - Disagree
- 1 - Strongly Disagree

## A. Functional Suitability

1. The system generates a meal plan based on the user’s profile.
2. The system considers allergies and dietary restrictions when generating a meal plan.
3. The system provides a grocery list based on the generated meal plan.
4. The system provides understandable feedback when a safe meal plan cannot be generated.
5. The system shows useful nutrition-related information for meal planning.

## B. Performance Efficiency

1. The system responds within an acceptable time when generating a meal plan.
2. The system remains responsive while loading saved plans and grocery data.
3. The system updates plan or grocery information without unnecessary delay.

## C. Compatibility / Offline Support

1. The system can still show saved meal-planning information when connectivity is limited.
2. The system can still show saved grocery information when connectivity is limited.
3. The system’s local-first behavior supports continuous use even when the backend is unavailable.

## D. Usability

1. The system is easy to navigate.
2. The system’s buttons and next actions are clear.
3. The profile input steps are understandable.
4. The meal plan output is easy to read.
5. The grocery list is easy to understand.

## E. Reliability

1. The system behaves consistently when the same valid inputs are used.
2. The system handles incomplete or conflicting profiles clearly.
3. The system preserves saved plans when they are available locally.
4. The system provides clear guidance instead of failing silently when no safe plan can be produced.

## F. Security / Privacy

1. The system appears to protect profile-related information during normal use.
2. The system does not show unnecessary personal information in normal screens.
3. The system’s handling of saved reflections or progress data appears appropriately private for a wellness application.

## G. Maintainability For IT Experts Only

1. The implemented system shows clear separation between UI, data, and planner logic.
2. The system’s deterministic fallback behavior is clear and appropriate.
3. The code structure supports future maintenance and validation.
4. The planner and API contract appear traceable enough for debugging and support.

## H. Overall Satisfaction

1. Overall, the system is useful as a wellness decision-support application.
2. Overall, I would recommend improvements to this system rather than a complete redesign.
3. Overall, the current implementation is understandable enough for thesis demonstration and evaluation.
