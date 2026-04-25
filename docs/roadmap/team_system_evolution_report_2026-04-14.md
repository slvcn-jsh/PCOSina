# PCOSina System Evolution Report

Date: 2026-04-14

## Purpose

This report summarizes how PCOSina evolved from the earlier Firebase App Distribution testing phase into the current system, what changed in product scope and backend architecture, why planner latency regressed during the ML transition, how that regression was fixed, and what remains before true production confidence.

This report is meant for internal team alignment. It focuses on actual repo evidence plus the latest live engineering validation carried out on 2026-04-14.

## Executive Summary

PCOSina is no longer the same system that was tested mainly through Firebase-distributed release APKs in early February.

The project has moved through three major transitions:

1. mobile-first thesis/release validation
2. production-hardening of the planner, async execution, sync, and release controls
3. ML-assisted ranking with strict deterministic solver authority and guarded rollout

The most important current conclusion is:

- the planner contract is still deterministic and MILP-authoritative
- ML is assistive only and remains guarded behind shadow/canary controls
- the major April latency regression was real, but it has now been reduced from roughly 188 to 208 seconds in failing live runs down to roughly 6.5 to 13.7 seconds in recent successful live runs

The system is materially more capable and more production-shaped than the earlier Firebase App Distribution era, but it is not fully production-complete yet. The remaining gaps are mainly operational evidence and rollout confidence, not core direction.

## Baseline: Early Firebase App Distribution Era

The older testing mode was centered around release APK generation and Firebase App Distribution.

Evidence:

- [docs/firebase-app-distribution.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/firebase-app-distribution.md)
- [scripts/release.ps1](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/scripts/release.ps1)
- [docs/thesis/pcosina_thesis.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/thesis/pcosina_thesis.md)

What that phase looked like:

- the app was packaged as release APKs and uploaded to Firebase App Distribution
- the thesis release record calls out Firebase App Distribution as the latest release path
- release 1.08.2 is documented there as a Firebase-distributed build for the `QUADRANT` tester group

What the system was then:

- deterministic meal planning without the later ML ranking pipeline
- simpler runtime behavior
- fewer planner-side diagnostics
- fewer user feedback loops
- less ops instrumentation
- less production rollout structure

Why the older version often felt faster:

- no stage-1 ML feature extraction and ranking pass
- no ML event taxonomy emission and candidate telemetry capture
- less async job orchestration complexity
- narrower planner observability footprint
- fewer post-plan product surfaces interacting with the result

That older phase was simpler, but also weaker in important ways:

- less diagnosable when plans failed
- less protected by rollout and canary controls
- less explicit about no-safe-plan behavior
- less robust in async planning and retry handling
- less mature around progress, feedback, history, and operator tooling

## System Growth Timeline

### 1. Foundation and Early Working App

Evidence from Git history:

- `88882ae` 2026-01-29 `baseline: initial working Android project`
- `336bcc8` 2026-01-31 `milestone:Updated theme, New login system, Working download list`

What changed:

- the app became a usable Android product shell
- foundational UI, login, and meal-planning interaction surfaces were established

### 2. Firebase Release and Mobile Product Expansion

Evidence:

- `a68889f` 2026-02-04 `release: 1.05`
- `65417fc` 2026-02-04 `fix: enable firebase/sentry deps and release signing`
- `198c713` 2026-02-04 `feat: real progress tracking, feedback queue, dashboard explanations`
- `404a70b` 2026-02-07 `feat: plan rationale, swap, pantry, reflections, onboarding guardrails`
- `95a4207` 2026-02-07 `feat: units, reflections, plan history, pricing, adherence`
- `693ed6c` 2026-02-09 `Implement week history insights and demo seeding`

What changed:

- real progress tracking was added
- local feedback queue behavior was introduced
- dashboard explanations became stronger
- pantry-aware planning and swap flows matured
- reflections, adherence, and plan history were added
- pricing and household-sensitive grocery behavior improved

Product impact:

- PCOSina moved from “planner demo” toward “meal planning plus follow-through”
- this is the phase where the app started becoming a full workflow, not only a generator

### 3. Planner Iteration and Reliability Hardening

Evidence:

- `c80fbfb` 2026-02-06 `Refactor backend planning logic into services`
- multiple 2026-02-07 commits around MILP pool caps, fallback behavior, debug summaries, and solver timing
- `78e96b8` 2026-02-09 `Fix async job persistence and mealsPerDay validation`
- `c3da0f6` 2026-02-13 `backend: tighten MILP constraints and add 36 quick Filipino recipes`
- `9eddc32` 2026-02-18 `feat(app): commit release readiness updates and guardrail fixes`

What changed:

- backend planning logic became more structured and service-based
- solver debugging and infeasibility tracing improved
- async execution and request validation improved
- recipe pool and solver constraints were tuned repeatedly
- release and guardrail work started to shift the project toward controlled deployment rather than ad hoc testing

This phase matters because it established the current product contract:

- deterministic stage-1 filtering
- deterministic OR-Tools CP-SAT optimization
- graceful failure handling

### 4. Production Hardening and Go-Live Readiness

Evidence:

- [docs/roadmap/progress_ledger.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/roadmap/progress_ledger.md)
- [docs/release/production_readiness_checklist.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/release/production_readiness_checklist.md)
- [docs/architecture/optimizer_queue_worker.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/architecture/optimizer_queue_worker.md)

Key status from the roadmap ledger:

- configuration externalization is done
- data and sync hardening is in progress
- optimizer service hardening is in progress
- go-live gates are in progress
- release and post-launch hardening are in progress

What changed:

- policy/config moved out of hardcoded behavior and into managed policy structures
- queue and worker scaffolding was introduced for async plan jobs
- diagnostics endpoints and rollout guardrails were added
- admin surfaces for content, ops, policy, and feedback were added
- privacy-safe logging and operator authentication received explicit test coverage

This is the point where the system stopped being just a mobile thesis build and started becoming an operable service.

### 5. ML Foundation and Guarded Rollout

Evidence:

- [docs/roadmap/ml_progress_ledger.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/roadmap/ml_progress_ledger.md)
- [docs/ml/model_card_lightgbm_v1.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/ml/model_card_lightgbm_v1.md)
- [docs/architecture/ml_telemetry_pipeline.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/architecture/ml_telemetry_pipeline.md)
- `1ffbdc3` 2026-04-09 `ml(runtime): deploy trained stage1 ranker artifacts`
- `472b080` 2026-04-09 `ml(runtime): refresh stage1 ranker artifacts`
- `d34d281` 2026-03-29 `docs(rollout): record production canary evidence`

What changed:

- event taxonomy and ML telemetry ingestion were added
- stage-1 candidate features and labels became dataset inputs
- offline dataset builder and LightGBM training pipeline were added
- canary guardrails and rollout checks were added
- production defaults were prepared for a 5% ML canary

Important product truth:

- ML is not the planner
- ML is a stage-1 assistive ranker only
- the authoritative planner is still OR-Tools CP-SAT

The ML progress ledger explicitly marks:

- training pipeline: done
- evaluation and baseline compare: done
- canary readiness: done
- shadow integration: still in progress until enough traffic-backed evidence exists

### 6. April 2026 Planner Latency Regression and Recovery

This is the most important recent engineering story because it affected perceived product quality directly.

#### What changed that caused the slowdown

As the system gained ML assistance and more telemetry, plan generation did more work before final solve:

- deterministic shortlist filtering
- candidate feature extraction
- optional stage-1 ML scoring
- telemetry capture
- then the same authoritative CP-SAT solve still ran

At the same time, generation was still running on a small Render web service, not on a dedicated planner worker.

That combination caused major latency regressions under live conditions.

#### What the live logs showed before the fix

Live Render validation on 2026-04-14 showed:

- a successful plan request taking about `207816 ms`
- another request failing after about `188249 ms`
- the true bottleneck was not CP-SAT alone
- a major part of the delay was inside stage-1 shortlist work

Crucially, the logs also showed:

- `ml_model_version: lightgbm_stage1_ranker_v1`
- `ranking_strategy: stage1_heuristic_shadow_only`

That proved:

- the trained model was loaded
- but ML was not even controlling ranking for those requests
- MILP/CP-SAT had not been replaced

#### Root causes identified

1. policy bootstrap race across multiple Gunicorn workers
2. stale async job reuse from app-side idempotency behavior
3. stage-1 runtime budget not effectively capping the whole solve path
4. per-recipe shadow ML scoring overhead inside shortlist
5. app telemetry duplication around `plan_generated`
6. progress route instability from corrupted local state and screen hydration paths

#### Fix sequence applied

Evidence from Git history:

- `13e7bd6` 2026-04-13 `fix(policy): serialize bootstrap version creation`
- `7bf554a` 2026-04-14 `fix(planner): cap solve path by wall-clock budget`
- `ad7d990` 2026-04-14 `perf(planner): batch stage1 shadow scoring`
- `a8b5ce0` 2026-04-14 `fix(app): stop reusing stale planner jobs`
- `afc4f2e` 2026-04-14 `fix(telemetry): avoid duplicate plan generated events`
- `671041b` 2026-04-13 `fix(progress): recover from corrupted local state`
- `a49d28e` 2026-04-14 `fix(progress): guard screen-side hydration effects`

What each fix did:

- policy bootstrap serialization removed a startup race that could crash Render workers
- wall-clock budgeting made planner time limits apply to the real solve path
- batched shadow scoring removed the worst shortlist bottleneck
- new per-attempt planner tokens stopped the app from reattaching to dead old jobs
- duplicate telemetry removal cleaned up analytics correctness
- progress crash hardening stabilized one of the app’s critical follow-through surfaces

#### Measured outcome after the fix

Live Render/device runs on 2026-04-14 then showed successful fresh plan generations of:

- `9803 ms`
- `13674 ms`
- `6890 ms`
- `6486 ms`
- `9593 ms`

That is the clearest current evidence that the planner has recovered from the April regression.

## Before vs Now

| Dimension | Earlier Firebase App Distribution Era | Current System |
|---|---|---|
| Mobile testing mode | Release APKs distributed to tester groups through Firebase | Direct debug installs plus release automation, ADB helpers, CI release builds, Render-connected validation |
| Planner architecture | Deterministic backend with fewer diagnostics and less async rigor | Deterministic stage-1 filtering plus deterministic CP-SAT, with async jobs, diagnostics, and operational guardrails |
| ML | Not present in runtime | LightGBM stage-1 ranking in shadow/canary form only |
| ML authority | Not applicable | Explicitly non-authoritative; solver remains authoritative |
| Feedback | Basic feedback/admin functionality emerged during Feb | Local feedback queue, reason-normalization pipeline, retraining inputs, admin tooling, telemetry-backed feedback loop |
| Progress | Present but narrower | Much deeper today: adherence, reflections, journals, weekly insights, spending, export, locked-flow semantics, crash hardening |
| History | Early plan output orientation | week history, plan history, reflection continuity, demo seeding |
| Community | Not central in early release record | Product-facing Community surface exists, but it is still a curated/early support surface rather than a fully interactive social platform |
| Operations | Limited release-era visibility | policy store, canary guardrails, rollout scripts, ops console, diagnostics, worker queue scaffolding |
| Safety contract | Less explicit | formal no-safe-plan and production contract docs, policy versioning, privacy-safe logging, app-check enforcement tests |
| Planner performance | Simpler and often faster due to lower pre-solver overhead | More complex, briefly regressed badly, now recovered through targeted optimization and runtime fixes |

## What Was Added That Increased Product Value

The current system is meaningfully more complete than the earlier build because it now includes:

- progress tracking as a first-class workflow, not just plan display
- reflections and adherence tracking
- pantry-aware planning and grocery scaling
- swap support and plan rationale support
- week history and analytics
- feedback queue and retry behavior
- admin feedback, content, policy, and ops tooling
- rollout and canary evidence paths
- ML telemetry, datasets, evaluation, and guarded rollout
- stronger release and privacy posture

This is why the current system is heavier than the earlier version. It is solving a broader problem set.

## What We Solved Recently

The most important recent wins are:

- recovered planner runtime from multi-minute failures to single-digit and low-teen second live runs
- preserved deterministic solver authority while introducing ML assistance
- eliminated stale-job reuse on the phone
- stabilized the progress screen against corrupted local state
- strengthened allergy handling, hard budget handling, symptom-aware planning, and profile conflict checks in the current working branch

## Current Production Readiness

The project is closer to production than it was in the Firebase-only testing phase, but it is not yet “done.”

Best current reading from the ledgers:

- production-shaped architecture: yes
- deterministic planner contract: yes
- ML canary-ready rollout system: yes
- live ML widening approved: not yet
- sync hardening complete: not yet
- sustained production load evidence archived: not yet
- connected Android/device evidence fully archived: not yet

Main remaining blockers from the roadmap ledgers:

- connected Android/device evidence
- staged multi-device sync validation
- sustained production load or Redis throughput evidence
- first traffic-backed live ML canary/shadow snapshot before widening beyond 5%

## Honest Assessment of “How Close Are We?”

PCOSina is no longer in a prototype-only state.

It is best described now as:

- feature-rich
- architecturally intentional
- operationally maturing
- planner-correct in its core contract
- not yet fully production-certified

A practical internal framing is:

- product direction: strong
- core planner contract: strong
- release discipline: much stronger than before
- ops and rollout maturity: real, but still gathering final evidence
- remaining work: mostly validation, guardrails, and last-mile production proof rather than “start over” architecture work

## Team Takeaway

The system did not simply become more complicated for its own sake.

Compared with the earlier Firebase App Distribution era, PCOSina now has:

- a clearer deterministic planning contract
- better failure handling
- richer user follow-through features
- operational tooling for real deployment
- a guarded ML layer that assists without taking authority
- evidence that major live performance regressions can be diagnosed and fixed systematically

The strongest message to the team is:

PCOSina is moving in the right direction, and recent work has converted several serious risks into controlled engineering problems with documented fixes and measurable outcomes.

## Source Evidence

- [README.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/README.md)
- [docs/firebase-app-distribution.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/firebase-app-distribution.md)
- [docs/thesis/pcosina_thesis.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/thesis/pcosina_thesis.md)
- [docs/roadmap/progress_ledger.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/roadmap/progress_ledger.md)
- [docs/roadmap/ml_progress_ledger.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/roadmap/ml_progress_ledger.md)
- [docs/architecture/optimizer_queue_worker.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/architecture/optimizer_queue_worker.md)
- [docs/ml/model_card_lightgbm_v1.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/ml/model_card_lightgbm_v1.md)
- [docs/architecture/ml_telemetry_pipeline.md](/C:/Users/salva/AndroidStudioProjects/PCOSINA2/docs/architecture/ml_telemetry_pipeline.md)
