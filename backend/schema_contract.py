import json
import os
from typing import Any, Dict

SCHEMA_VERSION = "1.2.0"
_SCHEMA_FILENAME = "pcosina_contract.json"
_SCHEMA_PATH = os.path.join(os.path.dirname(__file__), "schema", _SCHEMA_FILENAME)


def load_schema_contract() -> Dict[str, Any]:
    with open(_SCHEMA_PATH, "r", encoding="utf-8") as f:
        return json.load(f)
