import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import deploy_bootstrap


def test_deploy_bootstrap_runs_schema_and_seed_stages_once(monkeypatch):
    calls = []

    monkeypatch.setattr(deploy_bootstrap.database, "init_db", lambda: calls.append("application_schema"))
    monkeypatch.setattr(deploy_bootstrap.database, "seed_recipes", lambda: calls.append("recipe_seed"))
    monkeypatch.setattr(
        deploy_bootstrap.database,
        "seed_reviewed_price_rules",
        lambda: calls.append("reviewed_price_seed"),
    )
    monkeypatch.setattr(
        deploy_bootstrap.database,
        "seed_nutrition_corrections",
        lambda: calls.append("nutrition_correction_seed"),
    )
    monkeypatch.setattr(
        deploy_bootstrap.policy_store,
        "init_policy_store",
        lambda: calls.append("policy_schema"),
    )
    monkeypatch.setattr(
        deploy_bootstrap.policy_store,
        "ensure_default_policy",
        lambda actor: calls.append(("default_policy", actor)),
    )
    monkeypatch.setattr(
        deploy_bootstrap,
        "invalidate_price_rule_cache",
        lambda: calls.append("invalidate_price_cache"),
    )

    assert deploy_bootstrap.main() == 0
    assert calls == [
        "application_schema",
        "recipe_seed",
        "reviewed_price_seed",
        "invalidate_price_cache",
        "nutrition_correction_seed",
        "policy_schema",
        ("default_policy", "render-predeploy"),
    ]
