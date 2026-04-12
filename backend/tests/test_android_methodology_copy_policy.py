from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMMUNITY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "CommunityScreen.kt"
METHODOLOGY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "IpoVisualizationScreen.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_community_screen_feels_product_ready_even_with_placeholder_content() -> None:
    source = _read(COMMUNITY_SCREEN)
    assert "title = \"Community\"" in source
    assert "Guides, meal tips, and simple support for your week" in source
    assert "Start here this week" in source
    assert "PCOS-friendly eating basics" in source
    assert "Community corner" in source
    assert "Coming soon" in source
    assert "Need a hand?" in source


def test_methodology_screen_is_kept_for_admin_side_only() -> None:
    source = _read(METHODOLOGY_SCREEN)
    assert "title = \"System Methodology\"" in source
    assert "subtitle = \"Admin-only planning pipeline\"" in source
    assert "PROFILE + PANTRY INPUTS" in source
    assert "DETERMINISTIC FILTERING" in source
    assert "DETERMINISTIC OPTIMIZATION" in source
    assert "EXPLAINABLE OUTPUTS" in source
    assert "ML can assist ranking candidates, but never overrides hard constraints" in source
    assert "This view is for internal review." in source
    assert "Wellness Decision Support" in source
    assert "does not diagnose conditions or replace clinical care" in source

    stale_copy = (
        "clinical markers are ingested",
        "mathematical global optimum",
        "Medical: Insulin Resistance, Symptoms",
    )
    for phrase in stale_copy:
        assert phrase not in source
