# ML Shadow Readiness Checklist

- [ ] Event taxonomy includes all required event names.
- [ ] Backend validates and persists ML events (`/ml/events` + planner events).
- [ ] Stage1 candidate feature table receives labeled rows.
- [ ] Dataset build script succeeds end-to-end.
- [ ] Dataset readiness gates pass (`rows >= 500`, `positive_rate >= 1%`).
- [ ] Training dependencies installed and verified.
- [ ] LightGBM training completes and artifacts are stored.
- [ ] Metrics report generated and reviewed.
- [ ] Model loading path configured in backend.
- [ ] Shadow mode enabled with no ranking authority transfer.
- [ ] Fallback path validated when model is missing/unavailable.
- [ ] Observability captures model version, fallback rate, and missing features.
