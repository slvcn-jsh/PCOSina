import sys
from pathlib import Path
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

import database
import price_catalog
from scripts.import_dti_srp_price_rules import import_rows


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_price_catalog_credibility"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"price_catalog_credibility_{uuid4().hex}.db"


def test_dti_srp_import_drives_explainable_price_estimates():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    price_catalog.invalidate_override_cache()

    csv_dir = Path(__file__).resolve().parent / ".tmp_price_catalog_credibility"
    csv_dir.mkdir(parents=True, exist_ok=True)
    csv_path = csv_dir / f"srp_{uuid4().hex}.csv"
    csv_path.write_text(
        "keywords,price_php,category,unit,effective,source,confidence,notes\n"
        '"garlic,bawang",150,Produce,kg,2026-05,DTI SRP,high,unit test baseline\n',
        encoding="utf-8",
    )

    imported = import_rows(csv_path)
    price_catalog.invalidate_override_cache()
    estimate = price_catalog.estimate_price_explained("bawang", "1 kg", month_index=5)

    assert imported[0]["pricePhp"] == 150
    assert estimate.source == "dti_srp"
    assert estimate.source_label == "DTI SRP baseline (2026-05)"
    assert estimate.confidence == "medium"
    assert estimate.price_php > 0
    assert estimate.matched_keywords == ["garlic", "bawang"]


def test_price_estimate_applies_default_seasonality_and_tingi_factor():
    database.DATABASE_URL = ""
    database.DB_NAME = str(_temp_db_path())
    database.init_db()
    price_catalog.invalidate_override_cache()

    rainy = price_catalog.estimate_price_explained("tomato", "1 kg", month_index=8)
    summer = price_catalog.estimate_price_explained("tomato", "1 kg", month_index=3)
    tingi = price_catalog.estimate_price_explained("tomato", "2 pieces", month_index=3)

    assert rainy.market_multiplier > summer.market_multiplier
    assert rainy.price_php > summer.price_php
    assert tingi.tingi_multiplier > 1.0
