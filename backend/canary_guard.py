from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Tuple


@dataclass(frozen=True)
class MetricRule:
    key: str
    comparator: str  # "lte" | "gte"


DEFAULT_ROLLBACK_RULES: Tuple[MetricRule, ...] = (
    MetricRule(key="hard_violation_rate", comparator="lte"),
    MetricRule(key="latency_p95_ms", comparator="lte"),
    MetricRule(key="api_error_rate", comparator="lte"),
)

DEFAULT_ROLLBACK_THRESHOLDS: Dict[str, float] = {
    "hard_violation_rate": 0.0,
    "latency_p95_ms": 7000.0,
    "api_error_rate": 0.01,
}


def extract_rollback_thresholds(policy_payload: Dict[str, Any]) -> Dict[str, float]:
    sre = policy_payload.get("sre") if isinstance(policy_payload, dict) else {}
    trigger_map = sre.get("rollback_trigger_thresholds") if isinstance(sre, dict) else {}
    if not isinstance(trigger_map, dict):
        return dict(DEFAULT_ROLLBACK_THRESHOLDS)
    out: Dict[str, float] = {}
    for key, raw in trigger_map.items():
        try:
            out[str(key)] = float(raw)
        except Exception:
            continue
    for key, threshold in DEFAULT_ROLLBACK_THRESHOLDS.items():
        out.setdefault(key, threshold)
    return out


def evaluate_canary_metrics(
    metrics: Dict[str, Any],
    thresholds: Dict[str, float],
    rules: Tuple[MetricRule, ...] = DEFAULT_ROLLBACK_RULES,
) -> List[Dict[str, Any]]:
    breaches: List[Dict[str, Any]] = []
    for rule in rules:
        if rule.key not in thresholds:
            continue
        threshold = float(thresholds[rule.key])
        if rule.key not in metrics:
            breaches.append(
                {
                    "metric": rule.key,
                    "reason": "missing_metric",
                    "threshold": threshold,
                    "observed": None,
                }
            )
            continue
        try:
            observed = float(metrics[rule.key])
        except Exception:
            breaches.append(
                {
                    "metric": rule.key,
                    "reason": "non_numeric_metric",
                    "threshold": threshold,
                    "observed": metrics.get(rule.key),
                }
            )
            continue

        if rule.comparator == "lte":
            ok = observed <= threshold
        elif rule.comparator == "gte":
            ok = observed >= threshold
        else:
            ok = True

        if not ok:
            breaches.append(
                {
                    "metric": rule.key,
                    "reason": "threshold_exceeded",
                    "threshold": threshold,
                    "observed": observed,
                    "comparator": rule.comparator,
                }
            )
    return breaches
