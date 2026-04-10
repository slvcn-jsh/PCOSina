# ML Canary Go/No-Go Checklist

## Go criteria
- [ ] Shadow metrics stable for at least one evaluation window.
- [ ] No hard-rule regression on authoritative planner path.
- [ ] Fallback rate acceptable and trending stable.
- [ ] p95 latency remains within SLO.
- [ ] Baseline-vs-model ranking uplift is non-negative on target cohorts.
- [ ] Rollback switch tested in staging.

## No-Go triggers
- [ ] Any hard constraint violation attributable to ML path.
- [ ] Significant quality regression vs heuristic baseline.
- [ ] p95 latency regression above configured threshold.
- [ ] Model load/inference instability causing elevated fallback rates.

## Canary ramp policy
1. Start at 5% cohort.
2. Hold and review metrics.
3. Increase in controlled steps only if all gates pass.
4. Roll back immediately on any no-go trigger.
