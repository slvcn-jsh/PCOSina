from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MORE_TOOLS_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "MoreToolsScreen.kt"
METHODOLOGY_SCREEN = ROOT / "app" / "src" / "main" / "java" / "com" / "pcosina" / "app" / "ui" / "screens" / "IpoVisualizationScreen.kt"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_more_tools_screen_explains_offline_first_trust_contract() -> None:
    source = _read(MORE_TOOLS_SCREEN)
    assert "subtitle = \"Methodology, trust, and support\"" in source
    assert "profile, pantry, hard constraints, and weekly optimization" in source
    assert "Offline-First Trust" in source
    assert "Your device stays the primary source of truth." in source
    assert "Hard rules such as allergies, exclusions, pantry feasibility, budget, and nutrition limits stay authoritative." in source


def test_methodology_screen_matches_local_first_deterministic_pipeline() -> None:
    source = _read(METHODOLOGY_SCREEN)
    assert "subtitle = \"Local-First Planning Pipeline\"" in source
    assert "PROFILE + PANTRY INPUTS" in source
    assert "DETERMINISTIC FILTERING" in source
    assert "DETERMINISTIC OPTIMIZATION" in source
    assert "EXPLAINABLE OUTPUTS" in source
    assert "ML can assist ranking candidates, but never overrides hard constraints" in source
    assert "Wellness Decision Support" in source
    assert "does not diagnose conditions or replace clinical care" in source

    stale_copy = (
        "clinical markers are ingested",
        "mathematical global optimum",
        "Medical: Insulin Resistance, Symptoms",
    )
    for phrase in stale_copy:
        assert phrase not in source
