"""Import the reviewed PCOSina pricing audit workbook.

The final audit workbook stores human-reviewed market prices by canonical
ingredient. This script converts those rows into backend ingredient_price_rules
with explicit market-unit semantics, so the pricing engine uses the researched
price directly instead of applying the older category discount calibration.
"""

from __future__ import annotations

import argparse
import csv
import sys
import zipfile
from pathlib import Path
from typing import Any
from xml.etree import ElementTree as ET


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

import database  # noqa: E402
import price_catalog  # noqa: E402


DEFAULT_SEED_CSV = BACKEND_ROOT / "seed_data" / "reviewed_market_price_rules.csv"
AUDIT_SHEET_NAME = "Ingredient Audit"
XLSX_NS = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "rel": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
PRICE_RULE_COLUMNS = [
    "id",
    "keywords",
    "price_php",
    "price_min_php",
    "price_max_php",
    "category",
    "unit",
    "active",
    "source",
    "confidence",
    "effective",
    "pricing_basis",
    "zero_price",
    "market_source",
    "source_url",
    "review_status",
    "needs_manual_validation",
    "notes",
]
UNIT_ALIASES = {
    "l": "l",
    "liter": "l",
    "liters": "l",
    "litre": "l",
    "litres": "l",
    "kg": "kg",
    "kilogram": "kg",
    "kilograms": "kg",
    "pc": "piece",
    "pcs": "piece",
    "piece": "piece",
    "pieces": "piece",
    "pack": "pack",
    "packs": "pack",
    "packet": "pack",
    "packets": "pack",
    "bunch": "bunch",
    "bundle": "bunch",
    "head": "head",
    "can": "can",
    "tray": "tray",
}


def _column_index(cell_ref: str) -> int:
    letters = "".join(ch for ch in str(cell_ref or "A1") if ch.isalpha())
    value = 0
    for char in letters:
        value = value * 26 + ord(char.upper()) - 64
    return max(1, value)


def _xlsx_shared_strings(zip_file: zipfile.ZipFile) -> list[str]:
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    return ["".join(item.itertext()) for item in root.findall("main:si", XLSX_NS)]


def _xlsx_sheet_paths(zip_file: zipfile.ZipFile) -> dict[str, str]:
    workbook = ET.fromstring(zip_file.read("xl/workbook.xml"))
    rels = ET.fromstring(zip_file.read("xl/_rels/workbook.xml.rels"))
    relmap = {rel.attrib["Id"]: rel.attrib["Target"].lstrip("/") for rel in rels}
    paths: dict[str, str] = {}
    sheets = workbook.find("main:sheets", XLSX_NS)
    for sheet in sheets if sheets is not None else []:
        rel_id = sheet.attrib.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
        if not rel_id or rel_id not in relmap:
            continue
        target = relmap[rel_id]
        paths[str(sheet.attrib.get("name") or "")] = target if target.startswith("xl/") else f"xl/{target}"
    return paths


def read_xlsx_sheet(path: Path, sheet_name: str = AUDIT_SHEET_NAME) -> list[dict[str, str]]:
    with zipfile.ZipFile(path) as zip_file:
        shared = _xlsx_shared_strings(zip_file)
        sheet_paths = _xlsx_sheet_paths(zip_file)
        if sheet_name not in sheet_paths:
            raise ValueError(f"Workbook does not contain sheet {sheet_name!r}; found {', '.join(sheet_paths)}")
        root = ET.fromstring(zip_file.read(sheet_paths[sheet_name]))
        rows: list[list[str]] = []
        for row in root.findall("main:sheetData/main:row", XLSX_NS):
            cells: dict[int, str] = {}
            for cell in row.findall("main:c", XLSX_NS):
                cell_type = cell.attrib.get("t")
                value_node = cell.find("main:v", XLSX_NS)
                inline = cell.find("main:is", XLSX_NS)
                if cell_type == "s" and value_node is not None and value_node.text is not None:
                    value = shared[int(value_node.text)]
                elif cell_type == "inlineStr" and inline is not None:
                    value = "".join(inline.itertext())
                else:
                    value = value_node.text if value_node is not None and value_node.text is not None else ""
                cells[_column_index(cell.attrib.get("r", "A1"))] = str(value).strip()
            if cells:
                rows.append([cells.get(index, "") for index in range(1, max(cells) + 1)])
    if not rows:
        return []
    headers = rows[0]
    return [dict(zip(headers, row + [""] * (len(headers) - len(row)))) for row in rows[1:]]


def read_audit_rows(path: Path, sheet_name: str = AUDIT_SHEET_NAME) -> list[dict[str, str]]:
    if path.suffix.lower() == ".xlsx":
        return read_xlsx_sheet(path, sheet_name=sheet_name)
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def _clean(value: Any) -> str:
    return str(value or "").strip()


def _safe_notes_value(value: Any) -> str:
    return _clean(value).replace(";", ",").replace("\n", " ")[:600]


def _float(value: Any) -> float | None:
    text = _clean(value).replace(",", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _price_int(value: Any) -> int:
    parsed = _float(value)
    if parsed is None:
        return 0
    return int(round(parsed))


def _unit(value: Any, fallback: Any = "") -> str:
    raw = _clean(value or fallback).lower()
    return UNIT_ALIASES.get(raw, raw)


def _confidence(value: Any) -> str:
    raw = _clean(value).lower()
    if raw.startswith("high"):
        return "high"
    if raw.startswith("low"):
        return "low"
    return "medium"


def _bool_text(value: Any) -> str:
    return "true" if _clean(value).lower() in {"1", "true", "yes", "y", "on"} else "false"


def _keywords(row: dict[str, str]) -> list[str]:
    candidates: list[str] = []
    for column in ("suggested_keywords", "display_name", "canonical_key"):
        raw = _clean(row.get(column))
        if column == "suggested_keywords":
            candidates.extend(part.strip().lower() for part in raw.split(",") if part.strip())
        elif raw:
            candidates.append(raw.replace("_", " ").lower())
    result: list[str] = []
    for keyword in candidates:
        if keyword and keyword not in result:
            result.append(keyword)
    return result[:12]


def _rule_notes(row: dict[str, str], *, zero_price: bool, confidence: str) -> str:
    chunks = [
        "source=reviewed_market",
        f"confidence={confidence}",
        f"effective={_safe_notes_value(row.get('survey_date'))}",
        "pricing_basis=market_unit",
        "category_multiplier=none",
    ]
    if zero_price:
        chunks.append("zero_price=true")
    optional = {
        "market_source": row.get("market_source"),
        "source_url": row.get("source_url"),
        "review_status": row.get("implementation_ready_status"),
        "needs_manual_validation": row.get("needs_manual_validation"),
        "unit_note": row.get("unit_conversion_note"),
        "special_case": row.get("special_case_pricing_note"),
        "reviewer_notes": row.get("reviewer_notes"),
    }
    for key, value in optional.items():
        cleaned = _safe_notes_value(value)
        if cleaned:
            chunks.append(f"{key}={cleaned}")
    return "; ".join(chunks)[:2000]


def reviewed_price_rule_rows(
    audit_rows: list[dict[str, str]],
    *,
    include_needs_validation: bool = True,
    include_zero_price: bool = True,
) -> list[dict[str, str]]:
    rules: list[dict[str, str]] = []
    for row in audit_rows:
        canonical_key = _clean(row.get("canonical_key"))
        keywords = _keywords(row)
        if not canonical_key or not keywords:
            continue
        needs_validation = _clean(row.get("needs_manual_validation")).lower() in {"yes", "true", "1", "y"}
        if needs_validation and not include_needs_validation:
            continue
        real_price = _price_int(row.get("real_price_php"))
        zero_price = real_price <= 0
        if zero_price and not include_zero_price:
            continue
        confidence = _confidence(row.get("pricing_confidence_final"))
        price_php = max(1, real_price)
        price_min = max(1, _price_int(row.get("price_min_php"))) if _price_int(row.get("price_min_php")) > 0 else ""
        price_max = max(1, _price_int(row.get("price_max_php"))) if _price_int(row.get("price_max_php")) > 0 else ""
        rules.append(
            {
                "id": f"reviewed_{canonical_key}",
                "keywords": ", ".join(keywords),
                "price_php": str(price_php),
                "price_min_php": str(price_min),
                "price_max_php": str(price_max),
                "category": _clean(row.get("category")) or "Others",
                "unit": _unit(row.get("real_unit"), row.get("suggested_unit")),
                "active": "true",
                "source": "reviewed_market",
                "confidence": confidence,
                "effective": _clean(row.get("survey_date")),
                "pricing_basis": "market_unit",
                "zero_price": "true" if zero_price else "false",
                "market_source": _clean(row.get("market_source")),
                "source_url": _clean(row.get("source_url")),
                "review_status": _clean(row.get("implementation_ready_status")),
                "needs_manual_validation": "true" if needs_validation else "false",
                "notes": _rule_notes(row, zero_price=zero_price, confidence=confidence),
            }
        )
    return rules


def write_price_rule_csv(rows: list[dict[str, str]], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=PRICE_RULE_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def import_price_rule_rows(rows: list[dict[str, str]], *, dry_run: bool = False) -> list[dict[str, Any]]:
    database.init_db()
    saved: list[dict[str, Any]] = []
    for row in rows:
        payload = {
            "id": row["id"],
            "keywords": [part.strip().lower() for part in row["keywords"].split(",") if part.strip()],
            "pricePhp": max(1, _price_int(row.get("price_php"))),
            "priceMinPhp": _price_int(row.get("price_min_php")) or None,
            "priceMaxPhp": _price_int(row.get("price_max_php")) or None,
            "category": row.get("category") or "Others",
            "unit": row.get("unit") or None,
            "active": _bool_text(row.get("active")) == "true",
            "notes": row.get("notes"),
        }
        saved.append(payload if dry_run else database.upsert_price_rule(payload))
    if not dry_run:
        price_catalog.invalidate_override_cache()
    return saved


def main() -> int:
    parser = argparse.ArgumentParser(description="Import reviewed PCOSina market-unit pricing audit.")
    parser.add_argument("audit_path", type=Path, help="Reviewed pricing audit .xlsx or .csv")
    parser.add_argument("--sheet", default=AUDIT_SHEET_NAME)
    parser.add_argument("--export-csv", type=Path, default=None, help="Write normalized backend seed CSV.")
    parser.add_argument("--dry-run", action="store_true", help="Validate without database writes.")
    parser.add_argument("--skip-needs-validation", action="store_true")
    parser.add_argument("--skip-zero-price", action="store_true")
    args = parser.parse_args()

    audit_rows = read_audit_rows(args.audit_path, sheet_name=args.sheet)
    rule_rows = reviewed_price_rule_rows(
        audit_rows,
        include_needs_validation=not args.skip_needs_validation,
        include_zero_price=not args.skip_zero_price,
    )
    if args.export_csv:
        write_price_rule_csv(rule_rows, args.export_csv)
    saved = import_price_rule_rows(rule_rows, dry_run=args.dry_run)
    action = "Validated" if args.dry_run else "Imported"
    print(f"{action} {len(saved)} reviewed market price rule(s).")
    if args.export_csv:
        print(f"Wrote normalized seed CSV: {args.export_csv}")
    for row in saved[:10]:
        print(f"- {row.get('id')}: {', '.join(row.get('keywords') or [])} = PHP {row.get('pricePhp')} / {row.get('unit') or 'item'}")
    if len(saved) > 10:
        print(f"... {len(saved) - 10} more")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
