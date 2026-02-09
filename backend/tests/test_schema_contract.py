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
