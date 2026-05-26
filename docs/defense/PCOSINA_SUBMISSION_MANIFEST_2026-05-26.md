# PCOSina Submission Manifest

Status date: 2026-05-26

This manifest identifies the current cleaned artifacts for defense/compliance submission after removal of the legacy medical-severity and profile serving-count planning inputs.

## Current Authoritative Artifacts

- `benchmarks/canonical_scenarios/planner_realistic_profiles_20.json`
- `benchmarks/reports/planner_realistic_profiles.clean_20260526.json`
- `benchmarks/reports/planner_realistic_profiles.clean_20260526.csv`
- `docs/thesis_validation/00_EVIDENCE_INDEX.md`
- `docs/thesis_validation/01_SYSTEM_DATA_DICTIONARY.csv`
- `docs/thesis_validation/02_FORMULAS_AND_COMPUTATIONS.md`
- `docs/thesis_validation/04_DECISION_TREES.md`
- `docs/thesis_validation/05_VALIDATION_TEST_CASES.csv`
- `docs/thesis_validation/06_SAMPLE_MANUAL_COMPUTATION.md`
- `docs/thesis_validation/07_CHAPTER_4_TABLES_READY.md`
- `docs/thesis_validation/08_STATISTICIAN_PACKET.md`
- `docs/thesis_validation/14_SYSTEM_COMPUTATIONS_AND_FORMULAS_CENTRALIZED.md`
- `docs/thesis_validation/14_SYSTEM_COMPUTATIONS_AND_FORMULAS_CENTRALIZED.docx`
- `docs/thesis_validation/14_SYSTEM_COMPUTATIONS_AND_FORMULAS_CENTRALIZED_FINAL.docx`
- `docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.md`
- `docs/defense/PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.docx`
- `docs/defense/PCOSINA_Final_System_Status_And_SOP5_Action_Report_2026-05-25.docx`

## Archive-Only / Do Not Submit As Current Truth

- `benchmarks/reports/defense_audit.*`
- `benchmarks/reports/defense_smoke_*.json`
- `benchmarks/reports/defense_smoke_*.csv`
- `benchmarks/reports/planner_realistic_profiles.local*`
- `docs/CHAPTER 4 PCOSINA MANUSCRIPT.backup-*`
- `docs/CHAPTER 4 PCOSINA MANUSCRIPT.section-only-before-restore.docx`
- `docs/defense/PCOSINA_Insulin_Removal_System_Impact_Audit_2026-05-23.docx`
- `docs/defense/PCOSINA_Manuscript_SOP_Coverage_Audit_2026-05-23.docx`
- `docs/defense/PCOSINA_SOP_Defense_Explanation_Report_2026-05-23.docx`
- `output/doc/*`

## Verification Run

- Backend focused tests: `87 passed`
- Android unit tests: `BUILD SUCCESSFUL` for `testDebugUnitTest`
- Fresh benchmark: `100/100` pass, average `233 ms`, P95 `317 ms`, max `378 ms`
- JSON validation: cleaned benchmark fixture, fresh benchmark JSON report, and backend schema all valid
- Current tracked source/evidence scan: only the roadmap removal note remains, which is historical status text

## Submission Boundary

Use the current artifacts above for system evidence. Treat older benchmark reports, backups, generated drafts, and historical audit reports as archive material unless they are separately refreshed.
