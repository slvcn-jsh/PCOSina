import csv
import shutil
import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = BACKEND_ROOT.parent
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
if str(REPO_ROOT / "scripts") not in sys.path:
    sys.path.insert(0, str(REPO_ROOT / "scripts"))

from policy_config import default_policy  # noqa: E402
import generate_config_migration_report as report  # noqa: E402


def _get_value(payload: dict, dotted: str):
    current = payload
    for part in dotted.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def test_required_policy_keys_exist_in_default_payload():
    payload = default_policy().to_runtime_dict()
    missing = [k for k in report.REQUIRED_KEYS if _get_value(payload, k) is None]
    assert missing == []


def test_migration_report_rows_cover_required_keys():
    rows = report._build_rows()
    seen = {row["new_config_key"] for row in rows}
    missing = [k for k in report.REQUIRED_KEYS if k not in seen]
    assert missing == []


def test_generated_csv_has_expected_columns_and_required_keys():
    base = REPO_ROOT / "tmp" / "pytest_registry_report"
    shutil.rmtree(base, ignore_errors=True)
    base.mkdir(parents=True, exist_ok=True)

    csv_out = base / "config_migration_report.csv"
    md_out = base / "config_migration_report.md"
    rows = report._build_rows()
    report._write_csv(rows, csv_out)
    report._write_md(rows, md_out)

    assert csv_out.exists()
    assert md_out.exists()

    with csv_out.open(newline="", encoding="utf-8") as fp:
        reader = csv.DictReader(fp)
        assert reader.fieldnames == [
            "old_location",
            "new_config_key",
            "default",
            "allowed_range",
            "safety_criticality",
            "override_permissions",
            "notes",
        ]
        keys = {row["new_config_key"] for row in reader}

    missing = [k for k in report.REQUIRED_KEYS if k not in keys]
    assert missing == []
