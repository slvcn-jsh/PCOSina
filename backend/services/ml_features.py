from __future__ import annotations

from typing import Any, Dict, Iterable


STAGE1_LIGHTGBM_FEATURE_COLUMNS = [
    "model_score",
    "heuristic_score",
    "allergy_count",
    "budget_weekly_norm",
    "calorie_distance_score",
    "is_breakfast_candidate",
    "is_dinner_candidate",
    "is_lunch_candidate",
    "macro_distance_score",
    "max_cooking_time_minutes",
    "meals_per_day",
    "pantry_overlap_count",
    "profile_activity_lightly_active",
    "profile_goal_general_health",
    "profile_goal_weightloss",
    "reason_events_total",
    "recipe_calories",
    "recipe_carbs",
    "recipe_cost_est",
    "recipe_fats",
    "recipe_fiber",
    "recipe_minutes",
    "recipe_protein",
    "replace_reason_events_total",
    "replace_reason_tag_hist_cost_too_high",
    "replace_reason_tag_hist_forgot_or_missed",
    "replace_reason_tag_hist_ingredient_unavailable",
    "replace_reason_tag_hist_not_hungry",
    "replace_reason_tag_hist_prep_time_too_long",
    "replace_reason_tag_hist_repeat_fatigue",
    "replace_reason_tag_hist_restriction_conflict",
    "replace_reason_tag_hist_schedule_conflict",
    "replace_reason_tag_hist_skipped_by_user",
    "replace_reason_tag_hist_user_preference",
    "restriction_count",
    "skip_reason_events_total",
    "skip_reason_tag_hist_cost_too_high",
    "skip_reason_tag_hist_forgot_or_missed",
    "skip_reason_tag_hist_ingredient_unavailable",
    "skip_reason_tag_hist_not_hungry",
    "skip_reason_tag_hist_prep_time_too_long",
    "skip_reason_tag_hist_repeat_fatigue",
    "skip_reason_tag_hist_restriction_conflict",
    "skip_reason_tag_hist_schedule_conflict",
    "skip_reason_tag_hist_skipped_by_user",
    "skip_reason_tag_hist_user_preference",
]

REASON_FEEDBACK_FEATURE_COLUMNS = [
    name
    for name in STAGE1_LIGHTGBM_FEATURE_COLUMNS
    if name == "reason_events_total"
    or name.startswith("replace_reason_")
    or name.startswith("skip_reason_")
]

REASON_FEEDBACK_EVENT_NAMES = {
    "why_replaced_submitted",
    "why_skipped_submitted",
}

REASON_FEEDBACK_TAGS = sorted(
    {
        name.removeprefix("replace_reason_tag_hist_").removeprefix("skip_reason_tag_hist_")
        for name in REASON_FEEDBACK_FEATURE_COLUMNS
        if "_reason_tag_hist_" in name
    }
)


def zero_reason_feedback_features() -> Dict[str, float]:
    return {name: 0.0 for name in REASON_FEEDBACK_FEATURE_COLUMNS}


def reason_feedback_features_from_events(events: Iterable[Dict[str, Any]]) -> Dict[str, float]:
    features = zero_reason_feedback_features()
    for event in events or []:
        if not isinstance(event, dict):
            continue
        event_name = str(event.get("event_name") or event.get("eventName") or "").strip()
        if event_name not in REASON_FEEDBACK_EVENT_NAMES:
            continue
        features["reason_events_total"] += 1.0
        if event_name == "why_replaced_submitted":
            total_key = "replace_reason_events_total"
            tag_prefix = "replace_reason_tag_hist_"
        else:
            total_key = "skip_reason_events_total"
            tag_prefix = "skip_reason_tag_hist_"
        features[total_key] += 1.0

        raw_tags = event.get("reason_tags") or event.get("reasonTags") or []
        if isinstance(raw_tags, str):
            raw_tags = [raw_tags]
        elif not isinstance(raw_tags, list):
            raw_tags = []
        if not raw_tags:
            fallback_tag = event.get("reason_primary_tag") or event.get("reason_tag") or event.get("reasonTag")
            if fallback_tag:
                raw_tags = [fallback_tag]
        for tag in raw_tags:
            tag_value = str(tag or "").strip().lower().replace("-", "_").replace(" ", "_")
            if tag_value not in REASON_FEEDBACK_TAGS:
                continue
            features[f"{tag_prefix}{tag_value}"] += 1.0
    return features


def complete_stage1_feature_vector(
    features: Dict[str, float],
    *,
    feature_columns: Iterable[str] = STAGE1_LIGHTGBM_FEATURE_COLUMNS,
) -> Dict[str, float]:
    completed: Dict[str, float] = {}
    for name in feature_columns:
        try:
            completed[name] = float(features.get(name, 0.0))
        except Exception:
            completed[name] = 0.0
    return completed
