from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMMUNITY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "CommunityScreen.kt"
METHODOLOGY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "InternalMethodologyScreen.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_community_screen_feels_product_ready_even_with_placeholder_content() -> None:
    source = _read(COMMUNITY_SCREEN)
    assert "title = \"Support\"" in source
    assert "Clear help for planning, logging, and sending feedback." in source
    assert "Start with the next useful step." in source
    assert "Make the week feel lighter" in source
    assert "Need a hand?" in source
    assert "Send feedback now" in source


def test_methodology_screen_is_kept_for_admin_side_only() -> None:
    source = _read(METHODOLOGY_SCREEN)
    assert "fun InternalMethodologyScreen(" in source
    assert "title = \"Planning Methodology\"" in source
    assert "subtitle = \"Internal view of the deterministic planning pipeline.\"" in source
    assert "Profile + Pantry Inputs" in source
    assert "Deterministic Filtering" in source
    assert "Deterministic Optimization" in source
    assert "Explainable Outputs" in source
    assert "ML can assist ranking candidates, but never overrides hard constraints" in source
    assert "This screen is for internal review so regular users can stay on plan, grocery, progress, and support." in source
    assert "Pantry entries guide overlap scoring and quantity-aware grocery coverage" in source
    assert "pantry " + "feasibility" not in source
    assert "Decision support" in source
    assert "does not diagnose conditions or replace clinical care" in source

    stale_copy = (
        "clinical markers are ingested",
        "mathematical global optimum",
        "Medical profile markers are required",
    )
    for phrase in stale_copy:
        assert phrase not in source
