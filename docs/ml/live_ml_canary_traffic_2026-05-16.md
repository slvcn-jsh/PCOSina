# Live ML Canary Traffic Evidence - 2026-05-16

Source: production backend log excerpt supplied during release verification.

Privacy handling: raw IP addresses, full user hashes, and full request identifiers are intentionally omitted from this evidence note.

## Observation
- Timestamp: 2026-05-16 10:23:20 +08:00
- Event: `stage1_candidates_scored`
- Request: `53f194b9...`
- Model version: `lightgbm_stage1_ranker_v1`
- `ml_score_enabled`: `true`
- `ranking_strategy`: `stage1_ml_canary_plus_heuristic`
- `ranker_ready`: `true`
- `ml_candidate_count`: `120`
- `candidate_count_pre`: `94`
- `candidate_count_post`: `94`
- `stage1_ml_score`: `4 ms`
- Solver status: `OPTIMAL`
- Plan status: `success`

## Interpretation
This confirms that a live backend request applied LightGBM scoring to Stage 1 ranking under the canary strategy. The request also emitted and persisted ML telemetry through the existing planner event path.

This does not mean broad ML rollout is complete. The correct rollout state is live canary observed, with CP-SAT/MILP still retaining final authority over hard constraints and plan feasibility.

## Follow-up
- Keep the canary cohort under guardrail monitoring before widening.
- Archive aggregate canary counts, fallback rate, and cohort drift evidence from production telemetry.
- Retrain only after new telemetry or feature/label changes are available.
