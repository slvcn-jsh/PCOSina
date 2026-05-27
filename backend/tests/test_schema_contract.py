import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import schema_contract


def test_schema_version_matches():
    path = Path(__file__).parents[1] / "schema" / "pcosina_contract.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    assert data.get("schemaVersion") == schema_contract.SCHEMA_VERSION


def test_feedback_request_contract_caps_message_length():
    path = Path(__file__).parents[1] / "schema" / "pcosina_contract.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    message = data["properties"]["FeedbackRequest"]["properties"]["message"]

    assert message["maxLength"] == 2000


def test_plan_explanation_contract_includes_extended_diagnostics_fields():
    path = Path(__file__).parents[1] / "schema" / "pcosina_contract.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    properties = data["definitions"]["PlanExplanation"]["properties"]

    for key in [
        "candidatePoolSize",
        "budgetHardCapApplied",
        "plannerContract",
        "profileRuleEffects",
        "candidateExclusionSummary",
        "selectionReasonsByRecipeId",
        "selectionReasonCounts",
        "fiberMinTarget",
        "sugarMaxTarget",
        "goalStrategy",
        "solverStatus",
        "retryAttemptsUsed",
        "phaseTimingsMs",
        "solverBudget",
        "solvePairDiagnostics",
    ]:
        assert key in properties

    profile_rule_effects = properties["profileRuleEffects"]["properties"]
    for key in ["hardFilters", "softDrivers", "shoppingFactors", "advisoryLimits", "trackingOnly"]:
        assert key in profile_rule_effects

    planner_contract = properties["plannerContract"]["items"]["properties"]
    for key in ["field", "classification", "enforcement", "active"]:
        assert key in planner_contract


def test_user_profile_contract_includes_goal_support_tracking_fields():
    path = Path(__file__).parents[1] / "schema" / "pcosina_contract.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    properties = data["definitions"]["UserProfile"]["properties"]

    for key in ["targetWeightKg", "targetDate", "weeklyWeightChangeGoalKg"]:
        assert key in properties
