from __future__ import annotations

import argparse
import csv
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Dict, List, Tuple


ROOT = Path(__file__).resolve().parent
MATRIX_CSV = ROOT / "comment7_architecture_decision_matrix.csv"
OUT_TRIALS = ROOT / "comment7_architecture_sensitivity_trials_seed42.csv"
OUT_SUMMARY = ROOT / "comment7_architecture_sensitivity_recomputed_seed42.csv"


def read_matrix(path: Path) -> Tuple[List[str], List[str], Dict[Tuple[str, str], float]]:
    with path.open("r", encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    criteria = sorted({r["criterion"] for r in rows})
    architectures = sorted({r["architecture"] for r in rows})
    ratings = {(r["architecture"], r["criterion"]): float(r["rating"]) for r in rows}
    return criteria, architectures, ratings


def sample_weights(criteria: List[str], rng: random.Random) -> Dict[str, float]:
    raw = [rng.random() for _ in criteria]
    total = sum(raw)
    if total <= 0:
        return {c: 1.0 / len(criteria) for c in criteria}
    return {c: w / total for c, w in zip(criteria, raw)}


def architecture_scores(
    criteria: List[str],
    architectures: List[str],
    ratings: Dict[Tuple[str, str], float],
    weights: Dict[str, float],
) -> Dict[str, float]:
    out: Dict[str, float] = {}
    for a in architectures:
        out[a] = sum(weights[c] * ratings[(a, c)] for c in criteria)
    return out


def run_trials(
    criteria: List[str],
    architectures: List[str],
    ratings: Dict[Tuple[str, str], float],
    trials: int,
    seed: int,
) -> Tuple[List[Dict[str, str]], Dict[str, float]]:
    rng = random.Random(seed)
    win_counter = Counter()
    trial_rows: List[Dict[str, str]] = []

    for t in range(1, trials + 1):
        w = sample_weights(criteria, rng)
        scores = architecture_scores(criteria, architectures, ratings, w)
        # deterministic tie-break by architecture name for reproducibility
        winner = sorted(scores.items(), key=lambda kv: (-kv[1], kv[0]))[0][0]
        win_counter[winner] += 1

        row: Dict[str, str] = {
            "trial": str(t),
            "winner": winner,
        }
        for c in criteria:
            row[f"w_{c}"] = f"{w[c]:.10f}"
        for a in architectures:
            row[f"score_{a}"] = f"{scores[a]:.10f}"
        trial_rows.append(row)

    win_rate = {a: win_counter[a] / float(trials) for a in architectures}
    return trial_rows, win_rate


def write_trials(path: Path, rows: List[Dict[str, str]]) -> None:
    if not rows:
        return
    fieldnames = list(rows[0].keys())
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        w.writerows(rows)


def write_summary(path: Path, win_rate: Dict[str, float]) -> None:
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["architecture", "win_rate_random_weights"])
        w.writeheader()
        for a in sorted(win_rate.keys()):
            w.writerow(
                {
                    "architecture": a,
                    "win_rate_random_weights": f"{win_rate[a]:.8f}",
                }
            )


def main() -> None:
    parser = argparse.ArgumentParser(description="Recompute comment7 random-weight sensitivity with reproducible seed.")
    parser.add_argument("--trials", type=int, default=50000, help="Number of random-weight trials.")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducibility.")
    parser.add_argument("--matrix", type=Path, default=MATRIX_CSV, help="Path to decision matrix CSV.")
    parser.add_argument("--out_trials", type=Path, default=OUT_TRIALS, help="Output trial-level CSV.")
    parser.add_argument("--out_summary", type=Path, default=OUT_SUMMARY, help="Output summary CSV.")
    args = parser.parse_args()

    criteria, architectures, ratings = read_matrix(args.matrix)
    trial_rows, win_rate = run_trials(
        criteria=criteria,
        architectures=architectures,
        ratings=ratings,
        trials=args.trials,
        seed=args.seed,
    )
    write_trials(args.out_trials, trial_rows)
    write_summary(args.out_summary, win_rate)

    print(f"matrix={args.matrix}")
    print(f"trials={args.trials}")
    print(f"seed={args.seed}")
    print(f"out_trials={args.out_trials}")
    print(f"out_summary={args.out_summary}")
    for a in sorted(win_rate.keys()):
        print(f"{a}: {win_rate[a]:.8f}")

if __name__ == "__main__":
    main()
