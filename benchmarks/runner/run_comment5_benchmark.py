#!/usr/bin/env python
"""Reproducible Comment #5 benchmark runner (phase-1 skeleton).

This runner freezes benchmark metadata, deterministic seed, scenario set, and output
manifest so future batches can plug in full algorithm execution without changing
report contracts.
"""

from __future__ import annotations

import argparse
import json
import shutil
import time
from pathlib import Path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="PCOSINA Comment #5 benchmark runner (skeleton)")
    parser.add_argument("--repo-root", default=".", help="Repository root path")
    parser.add_argument("--recipes-path", default="backend/recipes.json", help="Recipes JSON path")
    parser.add_argument(
        "--scenarios-path",
        default="benchmarks/canonical_scenarios/comment5_canonical_scenarios.json",
        help="Scenario definition path",
    )
    parser.add_argument("--output-dir", default="benchmarks/reports", help="Report output directory")
    parser.add_argument("--seed", type=int, default=2026, help="Deterministic seed")
    parser.add_argument("--use-existing-artifacts", action="store_true", help="Copy existing backend comment5 artifacts")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    root = Path(args.repo_root).resolve()
    recipes_path = (root / args.recipes_path).resolve()
    scenarios_path = (root / args.scenarios_path).resolve()
    output_dir = (root / args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    if not recipes_path.exists():
        raise FileNotFoundError(f"Missing recipes file: {recipes_path}")
    if not scenarios_path.exists():
        raise FileNotFoundError(f"Missing scenarios file: {scenarios_path}")

    scenarios = json.loads(scenarios_path.read_text(encoding="utf-8"))
    run_id = f"comment5-{int(time.time())}"
    manifest = {
        "runId": run_id,
        "seed": args.seed,
        "recipesPath": str(recipes_path),
        "scenarioPath": str(scenarios_path),
        "scenarioCount": len(scenarios.get("scenarios", [])),
        "algorithms": [
            "cp_sat_two_stage_authoritative",
            "greedy_baseline_reference",
            "generative_proxy_reference",
        ],
        "status": "skeleton_ready",
        "notes": [
            "Phase-1 skeleton locks deterministic metadata and artifact contract.",
            "Full execution runner will be added in phase-2 with raw trial generation."
        ]
    }

    copied = []
    if args.use_existing_artifacts:
        artifact_names = [
            "comment5_algorithm_tradeoff_records.csv",
            "comment5_algorithm_tradeoff_summary.csv",
            "comment5_algorithm_tradeoff_report.json",
            "comment5_algorithm_tradeoff_vs_cpsat.csv",
            "comment5_complexity_by_scenario.csv",
            "comment5_complexity_summary.json",
        ]
        for name in artifact_names:
            src = root / "backend" / name
            if src.exists():
                dst = output_dir / name
                shutil.copy2(src, dst)
                copied.append(str(dst))

    manifest["copiedArtifacts"] = copied
    manifest_path = output_dir / "comment5_benchmark_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Wrote manifest: {manifest_path}")
    if copied:
        print(f"Copied {len(copied)} artifact(s) into {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
