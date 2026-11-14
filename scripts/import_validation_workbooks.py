"""Import PCOSina validation workbook data into the runtime backend database.

This importer intentionally uses the existing backend write APIs:
- recipe nutrition values become recipe_nutrition_corrections
- pricing audit rows become ingredient_price_rules

The XLSX parser uses only the Python standard library so the script can run in
the current project environment without installing openpyxl.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any
from xml.etree import ElementTree as ET
from zipfile import ZipFile


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
DEFAULT_NUTRITION_WORKBOOK = (
    REPO_ROOT
    / "docs"
    / "thesis_validation"
    / "03_ACTUAL_SYSTEM_DATA_EXPORTS"
    / "PCOSina_Latest_Dataset_Validation_Workbook_2026-05-24.xlsx"
)
FALLBACK_NUTRITION_WORKBOOK = REPO_ROOT / "app" / "PCOSina_Latest_Dataset_Validation_Workbook.xlsx"
DEFAULT_PRICING_WORKBOOK = REPO_ROOT / "app" / "pcosina_pricing_audit_final_updated.xlsx"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
import price_catalog  # noqa: E402

if not database.DATABASE_URL and database.DB_NAME == "pcosina.db":
    database.DB_NAME = str(BACKEND_ROOT / "pcosina.db")


SPREADSHEET_NS = {
    "a": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
PACKAGE_NS = {"rel": "http://schemas.openxmlformats.org/package/2006/relationships"}


def _column_index(cell_ref: str) -> int:
    letters = "".join(ch for ch in str(cell_ref or "") if ch.isalpha())
    index = 0
    for ch in letters:
        index = index * 26 + (ord(ch.upper()) - 64)
    return max(0, index - 1)


def _read_xlsx_sheet(path: Path, sheet_name: str) -> list[dict[str, str]]:
    with ZipFile(path) as archive:
        shared_strings: list[str] = []
        if "xl/sharedStrings.xml" in archive.namelist():
            shared_root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            for item in shared_root.findall("a:si", SPREADSHEET_NS):
                text = "".join(
                    node.text or ""
                    for node in item.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")
                )
                shared_strings.append(text)

        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        rels = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        relmap = {
            rel.attrib["Id"]: rel.attrib["Target"]
            for rel in rels.findall("rel:Relationship", PACKAGE_NS)
        }
        target = None
        for sheet in workbook.findall("a:sheets/a:sheet", SPREADSHEET_NS):
            if sheet.attrib.get("name") == sheet_name:
                target = relmap[sheet.attrib["{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]]
                break
        if not target:
            raise ValueError(f"Sheet not found: {sheet_name}")

        sheet_path = "xl/" + target.lstrip("/") if not target.startswith("xl/") else target
        root = ET.fromstring(archive.read(sheet_path))
        rows: list[list[str | None]] = []
        for row in root.findall(".//a:sheetData/a:row", SPREADSHEET_NS):
            values: list[str | None] = []
            for cell in row.findall("a:c", SPREADSHEET_NS):
                index = _column_index(cell.attrib.get("r", "A1"))
                while len(values) <= index:
                    values.append(None)

                cell_type = cell.attrib.get("t")
                value_node = cell.find("a:v", SPREADSHEET_NS)
                inline_node = cell.find("a:is", SPREADSHEET_NS)
                value: str | None = None
                if cell_type == "s" and value_node is not None and value_node.text is not None:
                    value = shared_strings[int(value_node.text)]
                elif cell_type == "inlineStr" and inline_node is not None:
                    value = "".join(
                        node.text or ""
                        for node in inline_node.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")
                    )
                elif value_node is not None:
                    value = value_node.text
                values[index] = value
            if any(str(value or "").strip() for value in values):
                rows.append(values)

    if not rows:
        return []
    headers = [str(value or "").strip() for value in rows[0]]
    records: list[dict[str, str]] = []
    for row in rows[1:]:
        record = {
            header: str(row[index]).strip()
            for index, header in enumerate(headers)
            if header and index < len(row) and row[index] is not None and str(row[index]).strip()
        }
        if record:
            records.append(record)
    return records


def _optional_int(value: Any) -> int | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        return int(round(float(text)))
    except Exception:
        return None


def _positive_int(value: Any) -> int | None:
    parsed = _optional_int(value)
    if parsed is None or parsed <= 0:
        return None
    return parsed


def _csv_keywords(raw: str) -> list[str]:
    return [part.strip().lower() for part in str(raw or "").split(",") if part.strip()]


def _rule_id(raw: str) -> str:
    value = str(raw or "").strip().lower()
    value = re.sub(r"[^a-z0-9_]+", "_", value)
    return re.sub(r"_+", "_", value).strip("_") or "price_rule"


def _existing_default(*paths: Path) -> Path:
    for path in paths:
        if path.exists():
            return path
    return paths[0]


def import_nutrition_workbook(path: Path, *, dry_run: bool = False) -> dict[str, int]:
    rows = _read_xlsx_sheet(path, "Recipe Catalog")
    saved = 0
    skipped = 0
    for row in rows:
        recipe_id = str(row.get("id") or "").strip()
        if not recipe_id:
            skipped += 1
            continue
        payload = {
            "calories": _optional_int(row.get("calories")),
            "proteinGrams": _optional_int(row.get("proteinGrams")),
            "carbsGrams": _optional_int(row.get("carbsGrams")),
            "fatsGrams": _optional_int(row.get("fatsGrams")),
            "fiberGrams": _optional_int(row.get("fiberGrams")),
            "sodiumMg": _optional_int(row.get("sodiumMg")),
            "sugarGrams": _optional_int(row.get("sugarGrams")),
            "active": True,
            "notes": "; ".join(
                part
                for part in [
                    f"source={row.get('nutritionDataSource') or 'dataset_validation_workbook'}",
                    f"confidence={row.get('nutritionConfidence') or 'medium'}",
                    f"review_status={row.get('nutritionReviewStatus') or 'pending_review'}",
                    f"workbook={path.name}",
                    f"workbook_correction_id={row.get('nutritionCorrectionId') or ''}",
                    f"source_version={row.get('sourceVersion') or ''}",
                    f"notes={row.get('nutritionNotes') or ''}",
                ]
                if not part.endswith("=")
            ),
        }
        if payload["calories"] is None and payload["proteinGrams"] is None and payload["carbsGrams"] is None:
            skipped += 1
            continue
        if not dry_run:
            database.upsert_nutrition_correction(recipe_id, payload)
        saved += 1
    return {"nutrition_rows": len(rows), "nutrition_imported": saved, "nutrition_skipped": skipped}


def import_pricing_workbook(path: Path, *, dry_run: bool = False) -> dict[str, int]:
    rows = _read_xlsx_sheet(path, "Ingredient Audit")
    saved = 0
    skipped = 0
    for row in rows:
        canonical_key = str(row.get("canonical_key") or "").strip()
        keywords = _csv_keywords(row.get("suggested_keywords") or canonical_key.replace("_", " "))
        price = _positive_int(row.get("real_price_php")) or _positive_int(row.get("current_rule_price_php"))
        if not canonical_key or not keywords or price is None:
            skipped += 1
            continue
        unit = str(row.get("real_unit") or row.get("suggested_unit") or "").strip() or None
        source = str(row.get("market_source") or row.get("current_source") or "pricing_audit_workbook").strip()
        confidence = str(row.get("current_confidence") or "medium").strip().lower()
        if row.get("real_price_php"):
            confidence = "high" if row.get("source_url") or row.get("market_source") else confidence
        notes = "; ".join(
            part
            for part in [
                f"source={source}",
                f"confidence={confidence}",
                f"effective={row.get('survey_date') or ''}",
                f"workbook={path.name}",
                f"source_url={row.get('source_url') or ''}",
                f"reviewer_notes={row.get('reviewer_notes') or ''}",
            ]
            if not part.endswith("=")
        )
        payload = {
            "id": _rule_id(canonical_key),
            "keywords": keywords,
            "pricePhp": price,
            "priceMinPhp": _positive_int(row.get("price_min_php")),
            "priceMaxPhp": _positive_int(row.get("price_max_php")),
            "category": str(row.get("category") or "Others").strip() or "Others",
            "unit": unit,
            "active": True,
            "notes": notes,
        }
        if not dry_run:
            database.upsert_price_rule(payload)
        saved += 1
    if not dry_run:
        price_catalog.invalidate_override_cache()
    return {"price_rows": len(rows), "price_imported": saved, "price_skipped": skipped}


def main() -> int:
    parser = argparse.ArgumentParser(description="Import PCOSina nutrition/pricing validation workbooks.")
    parser.add_argument(
        "--nutrition-workbook",
        type=Path,
        default=_existing_default(DEFAULT_NUTRITION_WORKBOOK, FALLBACK_NUTRITION_WORKBOOK),
    )
    parser.add_argument(
        "--pricing-workbook",
        type=Path,
        default=DEFAULT_PRICING_WORKBOOK,
    )
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--skip-nutrition", action="store_true")
    parser.add_argument("--skip-pricing", action="store_true")
    args = parser.parse_args()

    database.init_db()
    database.seed_recipes()
    result: dict[str, int] = {}
    if not args.skip_nutrition:
        result.update(import_nutrition_workbook(args.nutrition_workbook, dry_run=args.dry_run))
    if not args.skip_pricing:
        result.update(import_pricing_workbook(args.pricing_workbook, dry_run=args.dry_run))
    action = "Validated" if args.dry_run else "Imported"
    print(f"{action} validation workbook data:")
    for key, value in result.items():
        print(f"- {key}: {value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
