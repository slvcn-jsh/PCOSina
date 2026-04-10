#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def _apply_scenario(metrics: Dict[str, Any], scenario: str) -> Dict[str, Any]:
    out = dict(metrics)
    if scenario in {"hard", "all"}:
        out["hard_violation_rate"] = 0.03
    if scenario in {"latency", "all"}:
        out["latency_p95_ms"] = 12000
    if scenario in {"api", "all"}:
        out["api_error_rate"] = 0.03
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate synthetic canary-breach metrics for rollback drills.")
    parser.add_argument(
        "--input",
        default="benchmarks/reports/go_live_metrics.example.json",
        help="Source metrics JSON path.",
    )
    parser.add_argument(
        "--output",
        default="benchmarks/reports/go_live_metrics.breach.json",
        help="Output metrics JSON path.",
    )
    parser.add_argument(
        "--scenario",
        choices=["hard", "latency", "api", "all"],
        default="all",
        help="Which threshold category to force into breach.",
    )
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        print(f"missing input metrics file: {input_path}")
        return 2

    metrics = _load_json(input_path)
    simulated = _apply_scenario(metrics=metrics, scenario=args.scenario)
    simulated["simulation_note"] = f"synthetic breach scenario={args.scenario}"
    output_path = Path(args.output)
    _write_json(output_path, simulated)
    print(f"SIMULATED METRICS WRITTEN -> {output_path}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
