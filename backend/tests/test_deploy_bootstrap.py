import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import deploy_bootstrap


def test_deploy_bootstrap_runs_schema_and_seed_stages_once(monkeypatch):
    calls = []

    def seed_recipes(**kwargs):
        calls.append(("recipe_seed", kwargs))

    monkeypatch.setattr(deploy_bootstrap.database, "init_db", lambda: calls.append("application_schema"))
    monkeypatch.setattr(deploy_bootstrap.database, "seed_recipes", seed_recipes)
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
        ("recipe_seed", {"force_reseed": True, "deactivate_missing_seed": True}),
        "reviewed_price_seed",
        "invalidate_price_cache",
        "nutrition_correction_seed",
        "policy_schema",
        ("default_policy", "render-predeploy"),
    ]


def test_render_web_does_not_repeat_predeploy_bootstrap_on_process_start():
    blueprint = (ROOT.parent / "render.yaml").read_text(encoding="utf-8")
    web_service = blueprint.split("  - type: web", 1)[1].split("  - type: worker", 1)[0]

    assert "preDeployCommand: python deploy_bootstrap.py" in web_service
    assert "- key: PCOSINA_BOOTSTRAP_ON_STARTUP\n        value: \"false\"" in web_service
    assert "- key: PCOSINA_FORCE_RESEED\n        value: \"false\"" in web_service
