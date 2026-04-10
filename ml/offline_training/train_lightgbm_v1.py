#!/usr/bin/env python
"""Train LightGBM V1 for Stage 1 ranking support (non-authoritative)."""

from __future__ import annotations

import argparse
import csv
import json
import math
import subprocess
import time
from pathlib import Path
from typing import Any, Dict, Iterable, List

import pandas as pd

REQUEST_CONFUSION_FIELDS = [
    "request_id",
    "uid_hash",
    "cohorts",
    "budget_weekly_norm",
    "pantry_overlap_mean",
    "reason_events_total",
    "candidate_count",
    "positive_count",
    "negative_count",
    "positive_rate",
    "model_top1_label",
    "baseline_top1_label",
    "model_first_positive_rank",
    "baseline_first_positive_rank",
    "model_top10_true_positives",
    "model_top10_false_positives",
    "model_top10_false_negatives",
    "baseline_top10_true_positives",
    "baseline_top10_false_positives",
    "baseline_top10_false_negatives",
    "model_ndcg@10",
    "baseline_ndcg@10",
    "uplift_ndcg@10",
    "model_map@10",
    "baseline_map@10",
    "uplift_map@10",
]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Train PCOSINA LightGBM V1")
    parser.add_argument("--dataset-dir", required=True, help="Directory containing train.csv/val.csv/test.csv")
    parser.add_argument("--output-dir", default="ml/offline_training/artifacts/model_v1", help="Output artifact directory")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--n-estimators", type=int, default=400)
    parser.add_argument("--learning-rate", type=float, default=0.05)
    parser.add_argument("--num-leaves", type=int, default=31)
    parser.add_argument("--max-depth", type=int, default=-1)
    return parser


def _require_dependencies():
    try:
        import lightgbm as lgb  # noqa: F401
        from sklearn.metrics import log_loss, roc_auc_score  # noqa: F401
    except Exception as exc:
        raise RuntimeError(
            "Missing ML dependencies. Install: lightgbm and scikit-learn."
        ) from exc


def _read_split(path: Path) -> pd.DataFrame:
    if not path.exists():
        raise FileNotFoundError(f"Missing split file: {path}")
    df = pd.read_csv(path)
    if "selected_by_solver" not in df.columns:
        raise ValueError(f"Missing selected_by_solver label in {path}")
    if "request_id" not in df.columns:
        raise ValueError(f"Missing request_id in {path}")
    return df


def _feature_columns(df: pd.DataFrame) -> List[str]:
    blocked = {
        "selected_by_solver",
        "request_id",
        "uid_hash",
        "recipe_id",
        "meal_bucket",
        "generated_at_ms",
        "ranking_strategy",
        "model_version",
    }
    return [c for c in df.columns if c not in blocked]


def _safe_auc(y_true, y_prob):
    from sklearn.metrics import roc_auc_score

    if len(set(y_true)) < 2:
        return None
    return float(roc_auc_score(y_true, y_prob))


def _binary_metrics(y_true, y_prob) -> Dict[str, float | None]:
    from sklearn.metrics import log_loss

    return {
        "auc": _safe_auc(y_true, y_prob),
        "logloss": float(log_loss(y_true, y_prob, labels=[0, 1])),
    }


def _group_rows(df: pd.DataFrame) -> Dict[str, List[int]]:
    groups: Dict[str, List[int]] = {}
    for idx, rid in enumerate(df["request_id"].astype(str).tolist()):
        groups.setdefault(rid, []).append(idx)
    return groups


def _dcg(relevances: List[int], k: int) -> float:
    score = 0.0
    for i, rel in enumerate(relevances[:k], start=1):
        score += (2**int(rel) - 1) / math.log2(i + 1.0)
    return score


def _ndcg_at_k(labels: List[int], scores: List[float], request_ids: Iterable[str], k: int) -> float:
    frame = pd.DataFrame({"request_id": list(request_ids), "label": labels, "score": scores})
    values: List[float] = []
    for _rid, group in frame.groupby("request_id"):
        sorted_pred = group.sort_values("score", ascending=False)["label"].tolist()
        sorted_true = sorted(group["label"].tolist(), reverse=True)
        ideal = _dcg(sorted_true, k)
        if ideal <= 0:
            continue
        values.append(_dcg(sorted_pred, k) / ideal)
    return float(sum(values) / len(values)) if values else 0.0


def _ap_at_k(labels: List[int], scores: List[float], request_ids: Iterable[str], k: int) -> float:
    frame = pd.DataFrame({"request_id": list(request_ids), "label": labels, "score": scores})
    values: List[float] = []
    for _rid, group in frame.groupby("request_id"):
        g = group.sort_values("score", ascending=False).head(k)
        hits = 0
        precisions: List[float] = []
        for rank, rel in enumerate(g["label"].tolist(), start=1):
            if int(rel) == 1:
                hits += 1
                precisions.append(hits / rank)
        denom = max(1, int(group["label"].sum()))
        values.append(sum(precisions) / denom if precisions else 0.0)
    return float(sum(values) / len(values)) if values else 0.0


def _ranking_metrics(df: pd.DataFrame, score_col: str) -> Dict[str, float]:
    labels = df["selected_by_solver"].astype(int).tolist()
    scores = df[score_col].astype(float).tolist()
    request_ids = df["request_id"].astype(str).tolist()
    return {
        "ndcg@5": _ndcg_at_k(labels, scores, request_ids, 5),
        "ndcg@10": _ndcg_at_k(labels, scores, request_ids, 10),
        "map@5": _ap_at_k(labels, scores, request_ids, 5),
        "map@10": _ap_at_k(labels, scores, request_ids, 10),
    }


def _single_request_ndcg_at_k(labels: List[int], scores: List[float], k: int) -> float:
    ranked_labels = [int(label) for _, label in sorted(zip(scores, labels), key=lambda item: item[0], reverse=True)]
    ideal = _dcg(sorted([int(label) for label in labels], reverse=True), k)
    if ideal <= 0:
        return 0.0
    return float(_dcg(ranked_labels, k) / ideal)


def _single_request_ap_at_k(labels: List[int], scores: List[float], k: int) -> float:
    ranked_labels = [int(label) for _, label in sorted(zip(scores, labels), key=lambda item: item[0], reverse=True)]
    hits = 0
    precisions: List[float] = []
    for rank, rel in enumerate(ranked_labels[:k], start=1):
        if int(rel) == 1:
            hits += 1
            precisions.append(hits / rank)
    denom = max(1, sum(int(label) for label in labels))
    return float(sum(precisions) / denom) if precisions else 0.0


def _request_level_stats(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return pd.DataFrame(columns=["request_id", "uid_hash", "budget_weekly_norm", "pantry_mean", "reason_events_total"])
    aggregations: Dict[str, tuple[str, str]] = {}
    if "uid_hash" in df.columns:
        aggregations["uid_hash"] = ("uid_hash", "first")
    if "budget_weekly_norm" in df.columns:
        aggregations["budget_weekly_norm"] = ("budget_weekly_norm", "first")
    if "pantry_overlap_count" in df.columns:
        aggregations["pantry_mean"] = ("pantry_overlap_count", "mean")
    if "reason_events_total" in df.columns:
        aggregations["reason_events_total"] = ("reason_events_total", "max")
    request_stats = df.groupby("request_id").agg(**aggregations).reset_index()
    if "uid_hash" not in request_stats.columns:
        request_stats["uid_hash"] = ""
    if "budget_weekly_norm" not in request_stats.columns:
        request_stats["budget_weekly_norm"] = math.nan
    if "pantry_mean" not in request_stats.columns:
        request_stats["pantry_mean"] = math.nan
    if "reason_events_total" not in request_stats.columns:
        request_stats["reason_events_total"] = 0.0
    return request_stats


def _cohort_definitions(global_request_stats: pd.DataFrame) -> Dict[str, Dict[str, Any]]:
    budget_series = global_request_stats["budget_weekly_norm"].dropna()
    budget_threshold = float(budget_series.quantile(1.0 / 3.0)) if not budget_series.empty else None
    return {
        "cold_start": {
            "description": "Requests with zero recorded reason-feedback events in the dataset snapshot.",
            "filters": {
                "reason_events_total_max": 0,
            },
        },
        "sparse_pantry": {
            "description": "Requests whose average pantry overlap per candidate is less than or equal to 1.0.",
            "filters": {
                "pantry_overlap_mean_lte": 1.0,
            },
        },
        "budget_constrained": {
            "description": "Requests whose normalized weekly budget falls in the lowest third of the dataset snapshot.",
            "filters": {
                "budget_weekly_norm_lte": budget_threshold,
                "threshold_source": "global_request_quantile_0.33",
            },
        },
    }


def _cohort_request_ids(
    request_stats: pd.DataFrame,
    cohort_definitions: Dict[str, Dict[str, Any]],
) -> Dict[str, List[str]]:
    request_ids: Dict[str, List[str]] = {}
    cold_mask = request_stats["reason_events_total"].fillna(0.0) <= 0.0
    request_ids["cold_start"] = request_stats.loc[cold_mask, "request_id"].astype(str).tolist()

    pantry_threshold = float(
        ((cohort_definitions.get("sparse_pantry") or {}).get("filters") or {}).get("pantry_overlap_mean_lte", 1.0)
    )
    pantry_mask = request_stats["pantry_mean"].fillna(math.inf) <= pantry_threshold
    request_ids["sparse_pantry"] = request_stats.loc[pantry_mask, "request_id"].astype(str).tolist()

    budget_threshold = ((cohort_definitions.get("budget_constrained") or {}).get("filters") or {}).get(
        "budget_weekly_norm_lte"
    )
    if budget_threshold is None:
        request_ids["budget_constrained"] = []
    else:
        budget_mask = request_stats["budget_weekly_norm"].fillna(math.inf) <= float(budget_threshold)
        request_ids["budget_constrained"] = request_stats.loc[budget_mask, "request_id"].astype(str).tolist()
    return request_ids


def _uplift_vs_baseline(
    model_metrics: Dict[str, float],
    baseline_metrics: Dict[str, float],
) -> Dict[str, float]:
    return {
        key: float(model_metrics[key] - baseline_metrics[key])
        for key in model_metrics.keys()
        if key in baseline_metrics
    }


def _cohort_metrics_for_split(
    df: pd.DataFrame,
    prediction_col: str,
    baseline_col: str,
    cohort_definitions: Dict[str, Dict[str, Any]],
) -> Dict[str, Dict[str, Any]]:
    request_stats = _request_level_stats(df)
    cohort_ids = _cohort_request_ids(request_stats, cohort_definitions)
    metrics: Dict[str, Dict[str, Any]] = {}
    for cohort_name, request_id_values in cohort_ids.items():
        subset = df[df["request_id"].astype(str).isin(request_id_values)].copy()
        row_count = int(len(subset))
        request_count = int(subset["request_id"].nunique()) if row_count else 0
        positive_rows = int(subset["selected_by_solver"].astype(int).sum()) if row_count else 0
        negative_rows = row_count - positive_rows
        model_rank_metrics = _ranking_metrics(subset, prediction_col) if row_count else {}
        baseline_rank_metrics = _ranking_metrics(subset, baseline_col) if row_count else {}
        metrics[cohort_name] = {
            "request_count": request_count,
            "row_count": row_count,
            "positive_rows": positive_rows,
            "negative_rows": negative_rows,
            "positive_rate": (float(positive_rows) / float(row_count)) if row_count else 0.0,
            "binary_metrics": (
                _binary_metrics(
                    subset["selected_by_solver"].astype(int).tolist(),
                    subset[prediction_col].astype(float).tolist(),
                )
                if row_count
                else None
            ),
            "ranking_metrics": model_rank_metrics if row_count else None,
            "baseline_ranking_metrics": baseline_rank_metrics if row_count else None,
            "uplift_vs_baseline": (
                _uplift_vs_baseline(model_rank_metrics, baseline_rank_metrics)
                if row_count
                else None
            ),
        }
    return metrics


def _first_positive_rank(ranked_labels: List[int]) -> int | None:
    for rank, rel in enumerate(ranked_labels, start=1):
        if int(rel) == 1:
            return rank
    return None


def _topk_confusion(ranked_labels: List[int], total_positives: int, k: int) -> Dict[str, int]:
    cutoff = min(int(k), len(ranked_labels))
    true_positives = sum(int(rel) for rel in ranked_labels[:cutoff])
    false_positives = cutoff - true_positives
    false_negatives = max(0, int(total_positives) - true_positives)
    return {
        "true_positives": int(true_positives),
        "false_positives": int(false_positives),
        "false_negatives": int(false_negatives),
    }


def _request_cohort_map(
    request_stats: pd.DataFrame,
    cohort_definitions: Dict[str, Dict[str, Any]],
) -> Dict[str, List[str]]:
    mapping: Dict[str, List[str]] = {}
    for cohort_name, request_ids in _cohort_request_ids(request_stats, cohort_definitions).items():
        for request_id in request_ids:
            mapping.setdefault(str(request_id), []).append(cohort_name)
    return {request_id: sorted(set(cohort_names)) for request_id, cohort_names in mapping.items()}


def _request_confusion_rows_for_split(
    df: pd.DataFrame,
    prediction_col: str,
    baseline_col: str,
    cohort_definitions: Dict[str, Dict[str, Any]],
    split_name: str,
) -> List[Dict[str, Any]]:
    request_stats = _request_level_stats(df)
    request_stats_map = {
        str(row["request_id"]): row for row in request_stats.to_dict(orient="records")
    }
    cohort_map = _request_cohort_map(request_stats, cohort_definitions)
    rows: List[Dict[str, Any]] = []
    for request_id, group in df.groupby("request_id"):
        request_key = str(request_id)
        labels = group["selected_by_solver"].astype(int).tolist()
        model_scores = group[prediction_col].astype(float).tolist()
        baseline_scores = group[baseline_col].astype(float).tolist()
        positive_count = int(sum(labels))
        candidate_count = int(len(labels))
        negative_count = int(candidate_count - positive_count)
        model_ranked_labels = (
            group.sort_values(prediction_col, ascending=False)["selected_by_solver"].astype(int).tolist()
        )
        baseline_ranked_labels = (
            group.sort_values(baseline_col, ascending=False)["selected_by_solver"].astype(int).tolist()
        )
        model_top10_confusion = _topk_confusion(model_ranked_labels, positive_count, 10)
        baseline_top10_confusion = _topk_confusion(baseline_ranked_labels, positive_count, 10)
        request_metric_model_ndcg = _single_request_ndcg_at_k(labels, model_scores, 10)
        request_metric_baseline_ndcg = _single_request_ndcg_at_k(labels, baseline_scores, 10)
        request_metric_model_map = _single_request_ap_at_k(labels, model_scores, 10)
        request_metric_baseline_map = _single_request_ap_at_k(labels, baseline_scores, 10)
        request_stats_row = request_stats_map.get(request_key) or {}
        rows.append(
            {
                "split": split_name,
                "request_id": request_key,
                "uid_hash": request_stats_row.get("uid_hash", ""),
                "cohorts": ";".join(cohort_map.get(request_key) or []),
                "budget_weekly_norm": request_stats_row.get("budget_weekly_norm"),
                "pantry_overlap_mean": request_stats_row.get("pantry_mean"),
                "reason_events_total": request_stats_row.get("reason_events_total"),
                "candidate_count": candidate_count,
                "positive_count": positive_count,
                "negative_count": negative_count,
                "positive_rate": (float(positive_count) / float(candidate_count)) if candidate_count else 0.0,
                "model_top1_label": model_ranked_labels[0] if model_ranked_labels else None,
                "baseline_top1_label": baseline_ranked_labels[0] if baseline_ranked_labels else None,
                "model_first_positive_rank": _first_positive_rank(model_ranked_labels),
                "baseline_first_positive_rank": _first_positive_rank(baseline_ranked_labels),
                "model_top10_true_positives": model_top10_confusion["true_positives"],
                "model_top10_false_positives": model_top10_confusion["false_positives"],
                "model_top10_false_negatives": model_top10_confusion["false_negatives"],
                "baseline_top10_true_positives": baseline_top10_confusion["true_positives"],
                "baseline_top10_false_positives": baseline_top10_confusion["false_positives"],
                "baseline_top10_false_negatives": baseline_top10_confusion["false_negatives"],
                "model_ndcg@10": request_metric_model_ndcg,
                "baseline_ndcg@10": request_metric_baseline_ndcg,
                "uplift_ndcg@10": float(request_metric_model_ndcg - request_metric_baseline_ndcg),
                "model_map@10": request_metric_model_map,
                "baseline_map@10": request_metric_baseline_map,
                "uplift_map@10": float(request_metric_model_map - request_metric_baseline_map),
            }
        )
    rows.sort(key=lambda item: (item["split"], item["uplift_ndcg@10"], item["uplift_map@10"], item["request_id"]))
    return rows


def _regressed_cohort_names(split_metrics: Dict[str, Dict[str, Any]]) -> List[str]:
    regressed: List[str] = []
    for cohort_name, cohort_metrics in split_metrics.items():
        uplift = cohort_metrics.get("uplift_vs_baseline") or {}
        ndcg_uplift = uplift.get("ndcg@10")
        map_uplift = uplift.get("map@10")
        if ndcg_uplift is None or map_uplift is None:
            continue
        if float(ndcg_uplift) < 0 or float(map_uplift) < 0:
            regressed.append(cohort_name)
    return sorted(regressed)


def _regressed_cohort_request_confusion_rows(
    request_rows: List[Dict[str, Any]],
    regressed_cohorts_by_split: Dict[str, List[str]],
) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    for row in request_rows:
        split_name = str(row.get("split") or "")
        regressed = set(regressed_cohorts_by_split.get(split_name) or [])
        cohorts = [value for value in str(row.get("cohorts") or "").split(";") if value]
        for cohort_name in sorted(regressed.intersection(cohorts)):
            exported = dict(row)
            exported["cohort"] = cohort_name
            rows.append(exported)
    rows.sort(
        key=lambda item: (
            str(item.get("split") or ""),
            str(item.get("cohort") or ""),
            float(item.get("uplift_ndcg@10") or 0.0),
            float(item.get("uplift_map@10") or 0.0),
            str(item.get("request_id") or ""),
        )
    )
    return rows


def _write_cohort_metrics_csv(
    path: Path,
    metrics_by_split: Dict[str, Dict[str, Dict[str, Any]]],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(
            [
                "split",
                "cohort",
                "request_count",
                "row_count",
                "positive_rows",
                "negative_rows",
                "positive_rate",
                "auc",
                "logloss",
                "ndcg@10",
                "map@10",
                "baseline_ndcg@10",
                "baseline_map@10",
                "uplift_ndcg@10",
                "uplift_map@10",
            ]
        )
        for split_name, split_metrics in metrics_by_split.items():
            for cohort_name, cohort_metrics in split_metrics.items():
                binary_metrics = cohort_metrics.get("binary_metrics") or {}
                ranking_metrics = cohort_metrics.get("ranking_metrics") or {}
                baseline_metrics = cohort_metrics.get("baseline_ranking_metrics") or {}
                uplift_metrics = cohort_metrics.get("uplift_vs_baseline") or {}
                writer.writerow(
                    [
                        split_name,
                        cohort_name,
                        cohort_metrics.get("request_count", 0),
                        cohort_metrics.get("row_count", 0),
                        cohort_metrics.get("positive_rows", 0),
                        cohort_metrics.get("negative_rows", 0),
                        cohort_metrics.get("positive_rate", 0.0),
                        binary_metrics.get("auc"),
                        binary_metrics.get("logloss"),
                        ranking_metrics.get("ndcg@10"),
                        ranking_metrics.get("map@10"),
                        baseline_metrics.get("ndcg@10"),
                        baseline_metrics.get("map@10"),
                        uplift_metrics.get("ndcg@10"),
                        uplift_metrics.get("map@10"),
                    ]
                )


def _write_request_confusion_csv(
    path: Path,
    rows: List[Dict[str, Any]],
    *,
    include_cohort_column: bool,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ["split"]
    if include_cohort_column:
        fieldnames.append("cohort")
    fieldnames.extend(REQUEST_CONFUSION_FIELDS)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field) for field in fieldnames})


def _git_commit() -> str | None:
    try:
        out = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        return out or None
    except Exception:
        return None


def _write_feature_importance(path: Path, columns: List[str], split_importance: List[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.writer(fp)
        writer.writerow(["feature", "importance_split"])
        for name, value in sorted(zip(columns, split_importance), key=lambda item: item[1], reverse=True):
            writer.writerow([name, value])


def _validate_non_empty(df: pd.DataFrame, name: str) -> None:
    if df.empty:
        raise RuntimeError(f"{name} split is empty")
    labels = set(df["selected_by_solver"].astype(int).tolist())
    if labels != {0, 1}:
        raise RuntimeError(f"{name} split must contain both classes 0 and 1; got {sorted(labels)}")


def main() -> int:
    args = build_parser().parse_args()
    _require_dependencies()

    import lightgbm as lgb

    dataset_dir = Path(args.dataset_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    train_df = _read_split(dataset_dir / "train.csv")
    val_df = _read_split(dataset_dir / "val.csv")
    test_df = _read_split(dataset_dir / "test.csv")
    dataset_full_path = dataset_dir / "dataset_full.csv"

    _validate_non_empty(train_df, "train")
    _validate_non_empty(val_df, "val")
    _validate_non_empty(test_df, "test")

    feature_cols = _feature_columns(train_df)
    if not feature_cols:
        raise RuntimeError("No feature columns found")

    X_train = train_df[feature_cols].fillna(0.0)
    y_train = train_df["selected_by_solver"].astype(int)
    X_val = val_df[feature_cols].fillna(0.0)
    y_val = val_df["selected_by_solver"].astype(int)
    X_test = test_df[feature_cols].fillna(0.0)
    y_test = test_df["selected_by_solver"].astype(int)

    model = lgb.LGBMClassifier(
        objective="binary",
        n_estimators=int(args.n_estimators),
        learning_rate=float(args.learning_rate),
        num_leaves=int(args.num_leaves),
        max_depth=int(args.max_depth),
        subsample=0.9,
        colsample_bytree=0.9,
        random_state=int(args.seed),
    )
    model.fit(X_train, y_train)

    pred_val = model.predict_proba(X_val)[:, 1]
    pred_test = model.predict_proba(X_test)[:, 1]

    val_metrics = _binary_metrics(y_val.tolist(), pred_val.tolist())
    test_metrics = _binary_metrics(y_test.tolist(), pred_test.tolist())
    val_rank_metrics = _ranking_metrics(val_df.assign(model_score_pred=pred_val), "model_score_pred")
    test_rank_metrics = _ranking_metrics(test_df.assign(model_score_pred=pred_test), "model_score_pred")

    baseline_col = "heuristic_score" if "heuristic_score" in val_df.columns else feature_cols[0]
    val_baseline_rank = _ranking_metrics(val_df, baseline_col)
    test_baseline_rank = _ranking_metrics(test_df, baseline_col)
    global_df = pd.read_csv(dataset_full_path) if dataset_full_path.exists() else pd.concat(
        [train_df, val_df, test_df],
        ignore_index=True,
    )
    cohort_definitions = _cohort_definitions(_request_level_stats(global_df))
    val_eval_df = val_df.assign(model_score_pred=pred_val)
    test_eval_df = test_df.assign(model_score_pred=pred_test)
    val_cohort_metrics = _cohort_metrics_for_split(
        val_eval_df,
        prediction_col="model_score_pred",
        baseline_col=baseline_col,
        cohort_definitions=cohort_definitions,
    )
    test_cohort_metrics = _cohort_metrics_for_split(
        test_eval_df,
        prediction_col="model_score_pred",
        baseline_col=baseline_col,
        cohort_definitions=cohort_definitions,
    )
    val_regressed_cohorts = _regressed_cohort_names(val_cohort_metrics)
    test_regressed_cohorts = _regressed_cohort_names(test_cohort_metrics)

    model_path = output_dir / "lightgbm_v1_model.txt"
    model.booster_.save_model(str(model_path))

    feature_importance_path = output_dir / "feature_importance.csv"
    _write_feature_importance(
        feature_importance_path,
        feature_cols,
        model.booster_.feature_importance(importance_type="split").tolist(),
    )
    cohort_metrics_path = output_dir / "cohort_metrics.csv"
    _write_cohort_metrics_csv(
        cohort_metrics_path,
        {
            "val": val_cohort_metrics,
            "test": test_cohort_metrics,
        },
    )
    request_confusion_rows = _request_confusion_rows_for_split(
        val_eval_df,
        prediction_col="model_score_pred",
        baseline_col=baseline_col,
        cohort_definitions=cohort_definitions,
        split_name="val",
    )
    request_confusion_rows.extend(
        _request_confusion_rows_for_split(
            test_eval_df,
            prediction_col="model_score_pred",
            baseline_col=baseline_col,
            cohort_definitions=cohort_definitions,
            split_name="test",
        )
    )
    request_confusion_path = output_dir / "request_confusion.csv"
    _write_request_confusion_csv(
        request_confusion_path,
        request_confusion_rows,
        include_cohort_column=False,
    )
    regressed_request_confusion_path = output_dir / "cohort_regression_request_confusion.csv"
    _write_request_confusion_csv(
        regressed_request_confusion_path,
        _regressed_cohort_request_confusion_rows(
            request_confusion_rows,
            {
                "val": val_regressed_cohorts,
                "test": test_regressed_cohorts,
            },
        ),
        include_cohort_column=True,
    )

    dataset_manifest_path = dataset_dir / "dataset_manifest.json"
    dataset_manifest = {}
    if dataset_manifest_path.exists():
        dataset_manifest = json.loads(dataset_manifest_path.read_text(encoding="utf-8"))

    metrics = {
        "model_name": "lightgbm_stage1_ranker_v1",
        "seed": int(args.seed),
        "trained_at_ms": int(time.time() * 1000),
        "git_commit": _git_commit(),
        "feature_columns": feature_cols,
        "feature_count": len(feature_cols),
        "dataset_manifest": dataset_manifest,
        "splits": {
            "train_rows": int(len(train_df)),
            "val_rows": int(len(val_df)),
            "test_rows": int(len(test_df)),
        },
        "val_binary_metrics": val_metrics,
        "test_binary_metrics": test_metrics,
        "val_ranking_metrics": val_rank_metrics,
        "test_ranking_metrics": test_rank_metrics,
        "baseline_column": baseline_col,
        "val_baseline_ranking_metrics": val_baseline_rank,
        "test_baseline_ranking_metrics": test_baseline_rank,
        "artifact_files": {
            "model": model_path.name,
            "feature_importance_csv": feature_importance_path.name,
            "cohort_metrics_csv": cohort_metrics_path.name,
            "request_confusion_csv": request_confusion_path.name,
            "cohort_regression_request_confusion_csv": regressed_request_confusion_path.name,
        },
        "cohort_definitions": cohort_definitions,
        "val_cohort_metrics": val_cohort_metrics,
        "test_cohort_metrics": test_cohort_metrics,
        "val_regressed_cohorts": val_regressed_cohorts,
        "test_regressed_cohorts": test_regressed_cohorts,
    }

    (output_dir / "training_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    (output_dir / "training_config_snapshot.json").write_text(
        json.dumps(
            {
                "seed": int(args.seed),
                "n_estimators": int(args.n_estimators),
                "learning_rate": float(args.learning_rate),
                "num_leaves": int(args.num_leaves),
                "max_depth": int(args.max_depth),
            },
            indent=2,
        ),
        encoding="utf-8",
    )

    print(f"Saved model: {model_path}")
    print(f"Saved metrics: {output_dir / 'training_metrics.json'}")
    print(f"Saved cohort metrics: {cohort_metrics_path}")
    print(f"Saved request confusion: {request_confusion_path}")
    print(f"Saved regressed cohort request confusion: {regressed_request_confusion_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
