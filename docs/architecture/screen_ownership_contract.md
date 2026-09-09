# PCOSina Screen Ownership Contract

Status: active implementation contract

PCOSina keeps each primary task in one predictable location. Shared state may be
summarized elsewhere, but editing controls and detailed explanations belong to
the owning screen.

| Screen | Owns | Does not own |
| --- | --- | --- |
| Home | Today's next meal, immediate actions, brief current-week state | Weekly renewal, detailed nutrition, settings management |
| Plan | Weekly schedule, selected-day estimates, meal replacement, renewal | Grocery purchasing and historical outcomes |
| Grocery | Purchase quantities, pantry coverage, price evidence, budget estimate | Logged nutrition and symptom trends |
| Progress | Completed behavior, actual spending, reflections, historical trends | Plan generation and grocery price editing |
| Support | Help, feedback submission, feedback delivery state | Profile configuration |
| Settings | Profile, planning preferences, reminders, privacy, account actions | Core daily tasks |

## Cross-Screen Authorities

- The selected plan and plan week come from the persisted plan domain state.
- Meal status uses one domain vocabulary: planned, due, completed, skipped,
  replaced, missed, and future.
- Backend grocery output is the authority for item identity and estimated total.
- Grocery rows must sum exactly to the authoritative estimate after purchase
  quantity repair.
- Progress shows estimated grocery cost separately from user-entered actual spend.
- Saved plans, recipes, grocery state, and progress history remain available
  offline. Generating a new optimized plan requires the backend.

## Distribution Boundary

Respondent testing uses the Firebase App Distribution `staging` APK and the
existing Firebase Authentication flow. Google Play publishing, Play signing,
Play-specific declarations, and Credential Manager migration are outside the
current thesis implementation scope.
