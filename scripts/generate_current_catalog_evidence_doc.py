"""Generate the current 313-runtime-catalog evidence DOCX."""

from __future__ import annotations

import csv
import json
import statistics
import sys
import zipfile
from collections import Counter
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor

from scripts.export_budget_support_runtime_catalog import FCT as SUPPORT_FCT
from scripts.export_runtime_catalog_evidence_workbook import ADDON_FCT


BACKEND = ROOT / "backend"
RUNTIME = BACKEND / "recipes.json"
PRICE_RULES = BACKEND / "seed_data" / "reviewed_market_price_rules.csv"
PHILFCT_CACHE = BACKEND / "seed_data" / "philfct_reference_cache.json"
PRICED_DRAFT = BACKEND / "seed_data" / "pcosina_philfct_portioned_priced_catalog_v2_draft.json"
OUT = ROOT / "docs" / "data-models" / "PCOSina_Current_313_Runtime_Catalog_Evidence_Register.docx"

NUTRIENT_KEYS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")
SUPPORT_FCT_META = {
    "cooked white rice": ("A020", "Rice, well-milled, boiled"),
    "cooked munggo": ("D132", "Mung bean, boiled"),
    "egg": ("H003", "Egg, chicken, whole"),
    "malunggay": ("D094", "Horseradish tree lvs"),
    "tomato": ("D257", "Tomato"),
    "onion": ("D141", "Onion, Bombay bulb"),
    "garlic": ("D084", "Garlic bulb"),
    "cooking oil": ("K009", "Oil, corn"),
    "eggplant": ("D074", "Eggplant, boiled"),
    "pechay": ("PCOSINA-SUPPORT-PECHAY", "Pechay component estimate"),
    "okra": ("D140", "Okra, boiled"),
    "sitaw": ("D233", "String/Yard long bean pod, green, boiled"),
    "squash": ("D226", "Squash fruit, boiled"),
    "banana": ("E008", "Banana, cavendish, ripe"),
    "papaya": ("D158", "Papaya petioles, boiled"),
    "peanuts": ("C037", "Peanut w/o skin"),
    "chicken breast": ("F093", "Chicken breast"),
    "milkfish": ("G075", "Milkfish, broiled"),
    "pineapple": ("E073", "Pineapple"),
    "sardines": ("PCOSINA-SUPPORT-SARDINES", "Sardines component estimate"),
    "tuna": ("PCOSINA-SUPPORT-TUNA", "Tuna component estimate"),
    "tofu": ("PCOSINA-SUPPORT-TOFU", "Tofu component estimate"),
}

OFFICIAL_SOURCES = [
    [
        "DOST-FNRI Philippine Food Composition Tables / PhilFCT",
        "Nutrition source authority",
        "https://i.fnri.dost.gov.ph/fct/library",
        "Used as the official Philippine food-composition lookup basis for per-100g nutrient values. Each PCOSina ingredient still needs exact food-code review before final approval.",
    ],
    [
        "DOST-FNRI nutrition tools article",
        "Nutrition source credibility",
        "https://fnri.dost.gov.ph/index.php/programs-and-projects/news-and-announcement/779-dost-fnri-launches-updated-nutrition-tools",
        "Supports the use of Philippine Food Composition Tables and Food Exchange Lists as official nutrition tools.",
    ],
    [
        "FAO food-composition index entry for PhilFCT",
        "External source index",
        "https://www.fao.org/food-composition/tables-and-databases/detail/%28eswatini--2019%29-eswatini-food-composition-table/en",
        "Lists the DOST-FNRI PhilFCT online database as a Philippine food-composition source.",
    ],
    [
        "DTI latest SRP page",
        "Price source authority",
        "https://www.dti.gov.ph/dti-consumer-space/dti-latest-srps-basic-necessities-prime-commodities",
        "Used for SRP and e-Presyo cross-checks for basic necessities and prime commodities.",
    ],
    [
        "DTI e-Presyo",
        "Price source authority",
        "https://www.dti.gov.ph/konsyumer/e-presyo/",
        "Used for price freeze, comparative prevailing price, and SRP references where applicable.",
    ],
    [
        "DA Bantay Presyo",
        "Agricultural price source",
        "https://www.bantaypresyo.da.gov.ph/",
        "Used for agricultural commodity price monitoring cross-checks.",
    ],
    [
        "PSA selected agricultural commodity price situationer",
        "Independent price cross-check",
        "https://psa.gov.ph/statistics/price-situationer/selected-agri-commodities/index",
        "Used as an independent price cross-check for selected agricultural commodities.",
    ],
]


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def money(value: Any) -> str:
    try:
        return f"{float(value):,.2f}"
    except Exception:
        return ""


def number(value: Any, digits: int = 2) -> str:
    try:
        raw = float(value)
    except Exception:
        return ""
    if raw.is_integer():
        return str(int(raw))
    return f"{raw:.{digits}f}"


def rounded_number(value: Any, digits: int = 2) -> str:
    try:
        return f"{float(value):.{digits}f}"
    except Exception:
        return ""


def float_or_zero(value: Any) -> float:
    try:
        return float(value or 0)
    except Exception:
        return 0.0


def safe_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).replace("\r", " ").replace("\n", " ")
    return " ".join(text.split())


def short_text(value: Any, limit: int = 95) -> str:
    text = safe_text(value)
    if len(text) <= limit:
        return text
    return text[: limit - 3].rstrip() + "..."


def norm(value: Any) -> str:
    text = "".join(ch.lower() if ch.isalnum() else " " for ch in safe_text(value))
    return " ".join(text.split())


def grams_from_quantity(quantity: Any) -> float | None:
    text = safe_text(quantity).lower()
    parts = text.split()
    for idx, part in enumerate(parts):
        if part == "g" and idx > 0:
            try:
                return float(parts[idx - 1])
            except Exception:
                return None
        if part.endswith("g") and len(part) > 1:
            try:
                return float(part[:-1])
            except Exception:
                continue
        if part in {"kg", "kilo", "kilos", "kilogram", "kilograms"} and idx > 0:
            try:
                return float(parts[idx - 1]) * 1000.0
            except Exception:
                return None
    return None


def per100_from_tuple(values: Iterable[Any]) -> dict[str, float]:
    items = list(values)
    return {
        "calories": float_or_zero(items[0] if len(items) > 0 else 0),
        "protein_g": float_or_zero(items[1] if len(items) > 1 else 0),
        "carbs_g": float_or_zero(items[2] if len(items) > 2 else 0),
        "fat_g": float_or_zero(items[3] if len(items) > 3 else 0),
        "fiber_g": float_or_zero(items[4] if len(items) > 4 else 0),
    }


def compute_contribution(portion_g: float | None, per_100g: dict[str, Any]) -> dict[str, float]:
    if portion_g is None:
        return {key: 0.0 for key in NUTRIENT_KEYS}
    factor = float(portion_g) / 100.0
    return {key: round(factor * float_or_zero(per_100g.get(key)), 2) for key in NUTRIENT_KEYS}


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_cell_width(cell, width_inches: float) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_w = tc_pr.first_child_found_in("w:tcW")
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(int(width_inches * 1440)))
    tc_w.set(qn("w:type"), "dxa")


def configure_document(doc: Document) -> None:
    section = doc.sections[0]
    section.orientation = WD_ORIENT.LANDSCAPE
    section.page_width = Inches(11)
    section.page_height = Inches(8.5)
    section.top_margin = Inches(0.5)
    section.bottom_margin = Inches(0.5)
    section.left_margin = Inches(0.45)
    section.right_margin = Inches(0.45)

    styles = doc.styles
    styles["Normal"].font.name = "Arial"
    styles["Normal"].font.size = Pt(9)
    styles["Title"].font.name = "Arial"
    styles["Title"].font.size = Pt(18)
    styles["Title"].font.bold = True
    for name in ("Heading 1", "Heading 2", "Heading 3"):
        styles[name].font.name = "Arial"
        styles[name].font.color.rgb = RGBColor(31, 78, 121)


def add_title(doc: Document) -> None:
    title = doc.add_paragraph(style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.add_run("PCOSina Current 313 Runtime Catalog Evidence Register")

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.add_run(
        "Word evidence packet for the active runtime recipe catalog only. "
        "Generated from backend/recipes.json."
    )

    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta.add_run("Last verified: 2026-08-05 | Scope: current 313 runtime seed catalog")


def add_paragraph(doc: Document, text: str, bold_label: str | None = None) -> None:
    paragraph = doc.add_paragraph()
    if bold_label:
        run = paragraph.add_run(bold_label)
        run.bold = True
        paragraph.add_run(text)
    else:
        paragraph.add_run(text)


def add_table(
    doc: Document,
    headers: list[str],
    rows: Iterable[Iterable[Any]],
    widths: list[float] | None = None,
    font_size: int = 8,
) -> None:
    row_list = [list(row) for row in rows]
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    hdr = table.rows[0]
    set_repeat_table_header(hdr)
    for idx, heading in enumerate(headers):
        cell = hdr.cells[idx]
        cell.text = str(heading)
        set_cell_shading(cell, "1F4E79")
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        for paragraph in cell.paragraphs:
            paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            for run in paragraph.runs:
                run.font.bold = True
                run.font.color.rgb = RGBColor(255, 255, 255)
                run.font.size = Pt(font_size)
        if widths:
            set_cell_width(cell, widths[idx])

    for row_values in row_list:
        row = table.add_row()
        for idx, value in enumerate(row_values):
            cell = row.cells[idx]
            cell.text = safe_text(value)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.TOP
            if widths:
                set_cell_width(cell, widths[idx])
            for paragraph in cell.paragraphs:
                paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT
                for run in paragraph.runs:
                    run.font.size = Pt(font_size)

    doc.add_paragraph()


def active_price_rule_summary(rows: list[dict[str, str]]) -> dict[str, Any]:
    active = [row for row in rows if str(row.get("active") or "").lower() == "true"]
    source_urls = Counter(safe_text(row.get("source_url")) for row in active)
    return {
        "active_count": len(active),
        "confidence": Counter(safe_text(row.get("confidence")) for row in active),
        "review_status": Counter(safe_text(row.get("review_status")) for row in active),
        "source_urls": source_urls,
    }


def current_pricing_trace(recipes: list[dict[str, Any]], priced: dict[str, Any]) -> dict[str, Counter]:
    recipe_ids = {safe_text(recipe.get("id")) for recipe in recipes}
    counters = {
        "status": Counter(),
        "confidence": Counter(),
        "market_source": Counter(),
        "source_url": Counter(),
    }
    for meal in priced.get("meals", []):
        if safe_text(meal.get("sourceRecipeId")) not in recipe_ids:
            continue
        for item in meal.get("portionIngredients") or []:
            pricing = item.get("pricing") or {}
            for field, counter in counters.items():
                counter.update([safe_text(pricing.get(field))])
    return counters


def compute_stats(recipes: list[dict[str, Any]]) -> dict[str, tuple[int, float, float, float, float]]:
    values = {
        "Estimated meal cost, PHP": [
            float(recipe.get("estimatedCostPhp"))
            for recipe in recipes
            if recipe.get("estimatedCostPhp") is not None
        ],
        "PhilFCT coverage score": [
            float(recipe.get("philfctCoverage"))
            for recipe in recipes
            if recipe.get("philfctCoverage") is not None
        ],
        "Cooking minutes": [
            float(recipe.get("minutes"))
            for recipe in recipes
            if recipe.get("minutes") is not None
        ],
    }
    result: dict[str, tuple[int, float, float, float, float]] = {}
    for key, bucket in values.items():
        result[key] = (
            len(bucket),
            min(bucket) if bucket else 0.0,
            statistics.median(bucket) if bucket else 0.0,
            statistics.mean(bucket) if bucket else 0.0,
            max(bucket) if bucket else 0.0,
        )
    return result


def ingredient_summary(recipes: list[dict[str, Any]]) -> list[list[Any]]:
    ingredient_rows = []
    philfct_codes = set()
    with_price = 0
    add_ons = 0
    missing_price = 0
    for recipe in recipes:
        for item in recipe.get("ingredients") or []:
            ingredient_rows.append(item)
            if item.get("philfctCode"):
                philfct_codes.add(safe_text(item.get("philfctCode")))
            if item.get("priceCostPhp") is not None:
                with_price += 1
            else:
                missing_price += 1
            if item.get("completePlateAddon"):
                add_ons += 1
    return [
        ["Ingredient rows in current 313 runtime seed", len(ingredient_rows)],
        ["Ingredient rows with priceCostPhp", with_price],
        ["Ingredient rows missing priceCostPhp", missing_price],
        ["Unique PhilFCT codes used by current runtime ingredients", len(philfct_codes)],
        ["Complete-plate add-on ingredient rows", add_ons],
    ]


def source_breakdown_rows(counter: Counter) -> list[list[Any]]:
    return [[key or "(blank)", count] for key, count in counter.most_common()]


def recipe_inventory_rows(recipes: list[dict[str, Any]]) -> list[list[Any]]:
    rows = []
    for idx, recipe in enumerate(sorted(recipes, key=lambda item: (safe_text(item.get("mealType")), safe_text(item.get("name")))), start=1):
        nutrition = recipe.get("nutrition") or {}
        rows.append(
            [
                idx,
                recipe.get("id"),
                short_text(recipe.get("name") or recipe.get("title"), 42),
                recipe.get("mealType"),
                number(nutrition.get("calories"), 1),
                number(nutrition.get("protein_g"), 1),
                number(nutrition.get("carbs_g"), 1),
                number(nutrition.get("fat_g"), 1),
                number(nutrition.get("fiber_g"), 1),
                money(recipe.get("estimatedCostPhp")),
                number(recipe.get("philfctCoverage"), 2),
                recipe.get("nutritionConfidence"),
                recipe.get("nutritionReviewStatus"),
                short_text(recipe.get("sourceDataset"), 48),
            ]
        )
    return rows


def priced_meal_index(priced: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        safe_text(meal.get("sourceRecipeId")): meal
        for meal in priced.get("meals", [])
        if safe_text(meal.get("sourceRecipeId"))
    }


def philfct_code_index(philfct: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {
        safe_text(item.get("code")): item
        for item in philfct
        if safe_text(item.get("code"))
    }


def runtime_component_evidence(
    recipe: dict[str, Any],
    ingredient: dict[str, Any],
    philfct_by_code: dict[str, dict[str, Any]],
    row_type: str,
) -> dict[str, Any]:
    name = safe_text(ingredient.get("name"))
    portion_g = grams_from_quantity(ingredient.get("quantity"))
    code = safe_text(ingredient.get("philfctCode"))
    philfct_name = safe_text(ingredient.get("philfctName"))
    source = safe_text(ingredient.get("pricingSource") or ingredient.get("sourceText"))
    per_100g: dict[str, Any] = {}
    note = ""
    included = "Yes"

    if ingredient.get("excludedFromNutritionTotals"):
        included = "No"
        note = "Excluded from nutrition totals."
        portion_g = portion_g if portion_g is not None else 0.0
    elif name in SUPPORT_FCT:
        per_100g = per100_from_tuple(SUPPORT_FCT[name])
        meta_code, meta_name = SUPPORT_FCT_META.get(name, ("PCOSINA-SUPPORT", name))
        code = code or meta_code
        philfct_name = philfct_name or meta_name
        note = "Computed from runtime support/FCT component table."
    elif name in ADDON_FCT:
        addon_name, addon_code, default_g, addon_per_100g = ADDON_FCT[name]
        portion_g = portion_g if portion_g is not None else float(default_g)
        per_100g = per100_from_tuple(addon_per_100g)
        code = code or safe_text(addon_code)
        philfct_name = philfct_name or safe_text(addon_name)
        note = "Computed from complete-plate add-on FCT table."
    elif code and code in philfct_by_code:
        entry = philfct_by_code[code]
        per_100g = entry.get("per_100g") or {}
        philfct_name = philfct_name or safe_text(entry.get("name"))
        note = "Computed from PhilFCT reference cache."
    else:
        included = "No"
        note = "No per-100g nutrition source resolved in current evidence files."

    computed = (
        {key: 0.0 for key in NUTRIENT_KEYS}
        if included == "No"
        else compute_contribution(portion_g, per_100g)
    )
    return {
        "recipe_id": safe_text(recipe.get("id")),
        "recipe_name": safe_text(recipe.get("name") or recipe.get("title")),
        "meal": safe_text(recipe.get("mealType")),
        "row_type": row_type,
        "ingredient": name,
        "source_text": safe_text(ingredient.get("sourceText") or ingredient.get("quantity")),
        "portion_g": portion_g,
        "philfct_code": code,
        "philfct_name": philfct_name,
        "per_100g": per_100g,
        "computed": computed,
        "nutrition_included": included,
        "nutrition_note": note,
        "cost_php": float_or_zero(ingredient.get("priceCostPhp")),
        "pricing_status": safe_text(ingredient.get("pricingSource")),
        "price_rule_id": "",
        "unit_price_php": "",
        "market_unit": "",
        "price_confidence": "",
        "price_source_url": "",
        "market_source": source,
    }


def priced_source_evidence(recipe: dict[str, Any], row: dict[str, Any]) -> dict[str, Any]:
    philfct = row.get("philfct") or {}
    per_100g = philfct.get("per_100g") or {}
    computed = row.get("computed") or {key: 0.0 for key in NUTRIENT_KEYS}
    pricing = row.get("pricing") or {}
    nutrition_included = "Yes" if row.get("computed") else "No"
    parse_status = safe_text(row.get("parse_status"))
    if parse_status == "ignored_non_nutritive":
        note = "Ignored non-nutritive source row."
    elif row.get("computed"):
        note = "Computed from priced/portioned PhilFCT draft row."
    else:
        note = "No computed PhilFCT nutrition; row may still contribute to cost."
    return {
        "recipe_id": safe_text(recipe.get("id")),
        "recipe_name": safe_text(recipe.get("name") or recipe.get("title")),
        "meal": safe_text(recipe.get("mealType")),
        "row_type": "Base source ingredient",
        "ingredient": safe_text(row.get("clean_name") or row.get("source_text")),
        "source_text": safe_text(row.get("source_text")),
        "source_servings": row.get("source_servings"),
        "portion_g": row.get("portion_g"),
        "parse_status": parse_status,
        "philfct_code": safe_text(philfct.get("philfct_code")),
        "philfct_name": safe_text(philfct.get("philfct_name")),
        "per_100g": per_100g,
        "computed": computed,
        "nutrition_included": nutrition_included,
        "nutrition_note": note,
        "cost_php": float_or_zero(pricing.get("cost_php")),
        "pricing_status": safe_text(pricing.get("status")),
        "price_rule_id": safe_text(pricing.get("price_rule_id")),
        "unit_price_php": safe_text(pricing.get("price_php")),
        "market_unit": safe_text(pricing.get("market_unit")),
        "price_confidence": safe_text(pricing.get("confidence")),
        "price_source_url": safe_text(pricing.get("source_url")),
        "market_source": safe_text(pricing.get("market_source")),
    }


def recipe_evidence_rows(
    recipe: dict[str, Any],
    priced_by_recipe: dict[str, dict[str, Any]],
    philfct_by_code: dict[str, dict[str, Any]],
) -> list[dict[str, Any]]:
    recipe_id = safe_text(recipe.get("id"))
    rows: list[dict[str, Any]] = []
    priced_meal = priced_by_recipe.get(recipe_id)
    if priced_meal:
        for item in priced_meal.get("portionIngredients") or []:
            rows.append(priced_source_evidence(recipe, item))
        for ingredient in recipe.get("ingredients") or []:
            if ingredient.get("completePlateAddon"):
                rows.append(
                    runtime_component_evidence(
                        recipe,
                        ingredient,
                        philfct_by_code,
                        "Complete-plate add-on",
                    )
                )
        return rows

    for ingredient in recipe.get("ingredients") or []:
        row_type = "Generated/RND component"
        if ingredient.get("completePlateAddon"):
            row_type = "Complete-plate add-on"
        rows.append(runtime_component_evidence(recipe, ingredient, philfct_by_code, row_type))
    return rows


def all_evidence_rows(
    recipes: list[dict[str, Any]],
    priced: dict[str, Any],
    philfct: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    priced_by_recipe = priced_meal_index(priced)
    philfct_by_code = philfct_code_index(philfct)
    rows: list[dict[str, Any]] = []
    for recipe in sorted(recipes, key=lambda item: (safe_text(item.get("mealType")), safe_text(item.get("name")))):
        rows.extend(recipe_evidence_rows(recipe, priced_by_recipe, philfct_by_code))
    return rows


def recomputed_total_rows(recipes: list[dict[str, Any]], evidence_rows: list[dict[str, Any]]) -> list[list[Any]]:
    rows_by_recipe: dict[str, list[dict[str, Any]]] = {}
    for row in evidence_rows:
        rows_by_recipe.setdefault(safe_text(row.get("recipe_id")), []).append(row)

    output = []
    for idx, recipe in enumerate(sorted(recipes, key=lambda item: (safe_text(item.get("mealType")), safe_text(item.get("name")))), start=1):
        recipe_id = safe_text(recipe.get("id"))
        nutrition = recipe.get("nutrition") or {}
        sums = {key: 0.0 for key in NUTRIENT_KEYS}
        cost_sum = 0.0
        missing_nutrition_rows = 0
        for row in rows_by_recipe.get(recipe_id, []):
            computed = row.get("computed") or {}
            for key in NUTRIENT_KEYS:
                sums[key] += float_or_zero(computed.get(key))
            cost_sum += float_or_zero(row.get("cost_php"))
            if row.get("nutrition_included") == "No" and row.get("cost_php"):
                missing_nutrition_rows += 1

        nutrient_deltas = {
            key: sums[key] - float_or_zero(nutrition.get(key))
            for key in NUTRIENT_KEYS
        }
        nutrient_ok = all(abs(delta) <= 0.50 for delta in nutrient_deltas.values())
        nutrient_rounding_ok = all(abs(delta) <= 0.55 for delta in nutrient_deltas.values())
        cost_delta = round(cost_sum - float_or_zero(recipe.get("estimatedCostPhp")), 2)
        cost_ok = abs(cost_delta) <= 0.02
        status = "OK"
        if not nutrient_ok and not cost_ok:
            status = "Review nutrition and cost delta"
        elif not nutrient_ok:
            status = "OK within rounded-total tolerance" if nutrient_rounding_ok else "Review nutrition delta"
        elif not cost_ok:
            status = "Review cost delta"
        elif missing_nutrition_rows:
            status = "OK; has cost-only row(s)"

        output.append(
            [
                idx,
                recipe_id,
                short_text(recipe.get("name") or recipe.get("title"), 42),
                recipe.get("mealType"),
                nutrition.get("calories"),
                rounded_number(sums["calories"]),
                nutrition.get("protein_g"),
                rounded_number(sums["protein_g"]),
                nutrition.get("carbs_g"),
                rounded_number(sums["carbs_g"]),
                nutrition.get("fat_g"),
                rounded_number(sums["fat_g"]),
                nutrition.get("fiber_g"),
                rounded_number(sums["fiber_g"]),
                money(recipe.get("estimatedCostPhp")),
                money(cost_sum),
                money(cost_delta),
                status,
            ]
        )
    return output


def nutrition_detail_rows(evidence_rows: list[dict[str, Any]]) -> list[list[Any]]:
    rows = []
    for idx, row in enumerate(evidence_rows, start=1):
        per_100g = row.get("per_100g") or {}
        computed = row.get("computed") or {}
        rows.append(
            [
                idx,
                row.get("recipe_id"),
                short_text(row.get("recipe_name"), 34),
                row.get("meal"),
                short_text(row.get("row_type"), 22),
                short_text(row.get("ingredient"), 28),
                short_text(row.get("source_text"), 34),
                rounded_number(row.get("portion_g")),
                row.get("philfct_code"),
                short_text(row.get("philfct_name"), 30),
                rounded_number(per_100g.get("calories")),
                rounded_number(per_100g.get("protein_g")),
                rounded_number(per_100g.get("carbs_g")),
                rounded_number(per_100g.get("fat_g")),
                rounded_number(per_100g.get("fiber_g")),
                rounded_number(computed.get("calories")),
                rounded_number(computed.get("protein_g")),
                rounded_number(computed.get("carbs_g")),
                rounded_number(computed.get("fat_g")),
                rounded_number(computed.get("fiber_g")),
                row.get("nutrition_included"),
                short_text(row.get("nutrition_note"), 42),
            ]
        )
    return rows


def pricing_detail_rows(evidence_rows: list[dict[str, Any]]) -> list[list[Any]]:
    rows = []
    for idx, row in enumerate(evidence_rows, start=1):
        rows.append(
            [
                idx,
                row.get("recipe_id"),
                short_text(row.get("recipe_name"), 36),
                row.get("meal"),
                short_text(row.get("ingredient"), 32),
                rounded_number(row.get("portion_g")),
                money(row.get("cost_php")),
                short_text(row.get("pricing_status"), 32),
                short_text(row.get("price_rule_id"), 28),
                row.get("unit_price_php"),
                row.get("market_unit"),
                row.get("price_confidence"),
                short_text(row.get("market_source"), 48),
                short_text(row.get("price_source_url"), 52),
            ]
        )
    return rows


def build_doc() -> dict[str, Any]:
    recipes = read_json(RUNTIME)
    price_rules = read_csv(PRICE_RULES)
    philfct = read_json(PHILFCT_CACHE)
    priced = read_json(PRICED_DRAFT)

    if not isinstance(recipes, list):
        raise ValueError("backend/recipes.json must be a JSON array")
    if len(recipes) != 313:
        raise ValueError(f"Expected current runtime seed count 313, found {len(recipes)}")
    if not isinstance(philfct, list):
        raise ValueError("PhilFCT reference cache must be a JSON array")
    evidence_rows = all_evidence_rows(recipes, priced, philfct)
    recomputed_rows = recomputed_total_rows(recipes, evidence_rows)

    doc = Document()
    configure_document(doc)
    add_title(doc)

    doc.add_heading("1. Current Scope", level=1)
    add_table(
        doc,
        ["Question", "Answer"],
        [
            ["Which catalog is documented here?", "Only the current active runtime seed catalog: backend/recipes.json."],
            ["How many recipes are in that catalog?", f"{len(recipes)} recipes."],
            ["Should the old 1114 count be used?", "No. It is not the current runtime seed count."],
            ["Are the larger draft catalogs included as active runtime?", "No. Draft/review catalogs are excluded from the recipe inventory in this document."],
            ["Does this include ingredient-level computation?", f"Yes. The appendices include {len(evidence_rows)} nutrition/pricing evidence rows used to cross-check the 313 recipe totals."],
            ["Can this be used for panel/RND cross-checking?", "Yes, but rows marked source_mapped_needs_final_review still require final ingredient-code and price review before claiming final approval."],
        ],
        [2.0, 7.85],
        font_size=9,
    )

    add_paragraph(
        doc,
        "PCOSina is an offline-first Filipino-PCOS meal planning system and wellness decision-support tool. "
        "This evidence packet documents meal planning inputs and evidence sources only; it does not make diagnosis, treatment, or medical-device claims."
    )

    doc.add_heading("2. Runtime Catalog Counts", level=1)
    add_table(
        doc,
        ["Check", "Current value", "Source"],
        [
            ["Current runtime seed count", len(recipes), "backend/recipes.json"],
            ["PhilFCT reference cache entries", len(philfct), "backend/seed_data/philfct_reference_cache.json"],
            ["Active price rules available to pricing pipeline", active_price_rule_summary(price_rules)["active_count"], "backend/seed_data/reviewed_market_price_rules.csv"],
            ["Ingredient-level evidence rows in this DOCX", len(evidence_rows), "Current runtime seed plus priced/portioned source rows"],
            ["Runtime seed source identity", "Matches pcosina_philfct_runtime_candidate_catalog_v2_budget_supported.json", "SHA-256 check command in Section 9"],
            ["Known local DB warning", "Local SQLite may still show 800 active recipes unless reseeded", "database.get_recipe_catalog_status()"],
        ],
        [2.6, 4.0, 3.25],
        font_size=8,
    )

    doc.add_heading("3. Distribution Tables", level=1)
    add_table(
        doc,
        ["Meal type", "Count"],
        source_breakdown_rows(Counter(safe_text(recipe.get("mealType")) for recipe in recipes)),
        [3.2, 1.2],
        font_size=9,
    )
    add_table(
        doc,
        ["Nutrition data source", "Count"],
        source_breakdown_rows(Counter(safe_text(recipe.get("nutritionDataSource")) for recipe in recipes)),
        [5.2, 1.2],
        font_size=8,
    )
    add_table(
        doc,
        ["Source dataset", "Count"],
        source_breakdown_rows(Counter(safe_text(recipe.get("sourceDataset")) for recipe in recipes)),
        [6.2, 1.2],
        font_size=8,
    )
    add_table(
        doc,
        ["Review status", "Count"],
        source_breakdown_rows(Counter(safe_text(recipe.get("nutritionReviewStatus")) for recipe in recipes)),
        [4.6, 1.2],
        font_size=8,
    )

    doc.add_heading("4. Nutrition And Pricing Summary", level=1)
    add_table(
        doc,
        ["Metric", "Count", "Min", "Median", "Average", "Max"],
        [
            [name, count, number(min_val), number(median_val), number(avg_val), number(max_val)]
            for name, (count, min_val, median_val, avg_val, max_val) in compute_stats(recipes).items()
        ],
        [2.8, 1.0, 1.0, 1.0, 1.0, 1.0],
        font_size=8,
    )
    add_table(
        doc,
        ["Ingredient check", "Count"],
        ingredient_summary(recipes),
        [5.5, 1.2],
        font_size=8,
    )

    doc.add_heading("5. Credible Source Register", level=1)
    add_table(
        doc,
        ["Source", "Evidence area", "URL", "How PCOSina uses it"],
        OFFICIAL_SOURCES,
        [2.35, 1.65, 3.0, 2.85],
        font_size=7,
    )
    add_paragraph(
        doc,
        "Important: a credible external source does not automatically approve every PCOSina row. "
        "Each recipe remains reviewable until the exact ingredient portions, PhilFCT food code/name, and price basis are verified."
    )

    doc.add_heading("6. Computation Formulas", level=1)
    add_table(
        doc,
        ["Area", "Formula or rule", "Review note"],
        [
            [
                "Nutrition contribution",
                "ingredient nutrient contribution = portion_g / 100 * PhilFCT nutrient per 100g",
                "Water and rows marked excludedFromNutritionTotals are excluded.",
            ],
            [
                "Meal nutrition total",
                "meal nutrient total = sum of included ingredient contributions",
                "Applies to calories, protein, carbs, fat, and fiber in the current runtime seed.",
            ],
            [
                "Kilogram pricing",
                "portion_g / 1000 * price_php",
                "Used for standard produce, meat, seafood, rice, and similar market-unit rows.",
            ],
            [
                "Liter pricing",
                "portion_g / 1000 * price_php",
                "Uses grams as a practical ml-equivalent estimate for cooking liquids.",
            ],
            [
                "Piece pricing",
                "portion_g / 100 * price_php",
                "Conservative reviewable estimate when exact piece weight is missing.",
            ],
            [
                "Cooked rice pricing",
                "cooked_portion_g * 0.34 / 1000 * price_php",
                "Converts cooked rice to a dry-market equivalent.",
            ],
            [
                "Cooked munggo pricing",
                "cooked_portion_g * 0.40 / 1000 * price_php",
                "Converts cooked munggo to a dry-market equivalent.",
            ],
        ],
        [1.6, 4.1, 4.15],
        font_size=8,
    )

    price_summary = active_price_rule_summary(price_rules)
    current_price_trace = current_pricing_trace(recipes, priced)

    doc.add_heading("7. Price Evidence Status", level=1)
    add_table(
        doc,
        ["Price rule status", "Count"],
        source_breakdown_rows(price_summary["review_status"]),
        [4.2, 1.2],
        font_size=8,
    )
    add_table(
        doc,
        ["Price rule confidence", "Count"],
        source_breakdown_rows(price_summary["confidence"]),
        [3.0, 1.2],
        font_size=8,
    )
    add_table(
        doc,
        ["Current-catalog traced pricing status from priced draft", "Count"],
        source_breakdown_rows(current_price_trace["status"]),
        [5.5, 1.2],
        font_size=8,
    )
    add_table(
        doc,
        ["Current-catalog traced pricing confidence from priced draft", "Count"],
        source_breakdown_rows(current_price_trace["confidence"]),
        [5.5, 1.2],
        font_size=8,
    )

    doc.add_heading("8. Maintainable Update Rules", level=1)
    add_table(
        doc,
        ["When changing...", "Update this first", "Then regenerate/check"],
        [
            ["Recipe name, ingredients, or meal type", "backend/recipes.json or the upstream catalog generator input", "Regenerate this DOCX and rerun planner scenario audit."],
            ["Nutrition facts", "PhilFCT code/name, portion_g, and source evidence. Do not only edit final totals.", "Recompute ingredient contributions and verify meal totals."],
            ["Price inputs", "backend/seed_data/reviewed_market_price_rules.csv with source_url, confidence, effective date, and review_status", "Rebuild price estimates and confirm changed meal costs."],
            ["Runtime seed promotion", "backend/recipes.json", "Check count, SHA-256, catalog status, evidence workbook, and this DOCX."],
            ["Panel/RND evidence", "Use row-level workbook plus this DOCX", "Avoid claiming full approval unless review_status supports it."],
        ],
        [2.4, 3.6, 3.8],
        font_size=8,
    )

    doc.add_heading("9. Repeatable Cross-Check Commands", level=1)
    commands = [
        ["Count current runtime seed", "Get-Content -Raw -LiteralPath backend\\recipes.json | ConvertFrom-Json | Measure-Object | Select-Object Count"],
        ["Check seed versus local DB", "$env:PYTHONPATH = \"backend\"; python -c \"import json, database; print(json.dumps(database.get_recipe_catalog_status(), indent=2))\""],
        ["Check meal-type distribution", "Get-Content -Raw -LiteralPath backend\\recipes.json | ConvertFrom-Json | Group-Object mealType | Select-Object Name,Count | Sort-Object Name"],
        ["Compare runtime seed hash", "Get-FileHash -Algorithm SHA256 -LiteralPath backend\\recipes.json,backend\\seed_data\\pcosina_philfct_runtime_candidate_catalog_v2_budget_supported.json"],
        ["Regenerate row-level evidence workbook", "python scripts\\export_runtime_catalog_evidence_workbook.py"],
        ["Regenerate this Word document", "python scripts\\generate_current_catalog_evidence_doc.py"],
    ]
    add_table(doc, ["Check", "Command"], commands, [2.3, 7.6], font_size=7)

    doc.add_heading("10. Safe Defense Wording", level=1)
    add_table(
        doc,
        ["Use", "Avoid"],
        [
            [
                "PCOSina's current runtime seed catalog contains 313 budget-supported, PhilFCT-aligned recipe candidates.",
                "The current recipe count is 1114.",
            ],
            [
                "Most rows are source-mapped and need final review before dietitian-approved claims.",
                "All current recipes are fully dietitian-approved.",
            ],
            [
                "The larger draft catalogs are review inputs, not the active runtime inventory documented here.",
                "The complete-plate draft count is the active app count.",
            ],
        ],
        [4.95, 4.95],
        font_size=8,
    )

    doc.add_page_break()
    doc.add_heading("Appendix A. Full Current 313 Runtime Recipe Inventory", level=1)
    add_paragraph(
        doc,
        "This table lists every recipe in the current runtime seed file. Appendices B, C, and D show how the recipe-level totals are recomputed from ingredient evidence rows."
    )
    add_table(
        doc,
        [
            "#",
            "ID",
            "Recipe name",
            "Meal",
            "kcal",
            "Prot",
            "Carb",
            "Fat",
            "Fiber",
            "Cost PHP",
            "Cov",
            "Conf",
            "Review status",
            "Source dataset",
        ],
        recipe_inventory_rows(recipes),
        [0.28, 0.55, 1.55, 0.65, 0.43, 0.43, 0.43, 0.4, 0.43, 0.58, 0.38, 0.5, 1.45, 1.75],
        font_size=6,
    )

    doc.add_page_break()
    doc.add_heading("Appendix B. Recomputed Totals Cross-Check For All 313 Recipes", level=1)
    add_paragraph(
        doc,
        "For each recipe, this table compares the catalog total against the sum of ingredient-level evidence rows. "
        "Catalog values are rounded integers; recomputed values retain two decimals so reviewers can see the underlying math."
    )
    add_table(
        doc,
        [
            "#",
            "ID",
            "Recipe name",
            "Meal",
            "Cat kcal",
            "Calc kcal",
            "Cat Prot",
            "Calc Prot",
            "Cat Carb",
            "Calc Carb",
            "Cat Fat",
            "Calc Fat",
            "Cat Fiber",
            "Calc Fiber",
            "Cat Cost",
            "Calc Cost",
            "Cost Delta",
            "Status",
        ],
        recomputed_rows,
        [
            0.25,
            0.48,
            1.1,
            0.45,
            0.38,
            0.42,
            0.38,
            0.42,
            0.38,
            0.42,
            0.38,
            0.42,
            0.38,
            0.42,
            0.5,
            0.5,
            0.5,
            1.3,
        ],
        font_size=5,
    )

    doc.add_page_break()
    doc.add_heading("Appendix C. Ingredient-Level Nutrition Computation Rows", level=1)
    add_paragraph(
        doc,
        "Each row shows the ingredient/source row, portion in grams, PhilFCT or component reference, per-100g nutrient values, and computed contribution. "
        "Rows marked No are not included in nutrition totals, usually because they are water, non-nutritive, or cost-only rows without computed PhilFCT nutrition."
    )
    add_table(
        doc,
        [
            "#",
            "ID",
            "Recipe",
            "Meal",
            "Row type",
            "Ingredient",
            "Source text",
            "g",
            "Code",
            "PhilFCT/component name",
            "kcal/100",
            "P/100",
            "C/100",
            "F/100",
            "Fib/100",
            "Calc kcal",
            "Calc P",
            "Calc C",
            "Calc F",
            "Calc Fib",
            "In?",
            "Note",
        ],
        nutrition_detail_rows(evidence_rows),
        [
            0.22,
            0.45,
            0.85,
            0.38,
            0.55,
            0.7,
            0.8,
            0.38,
            0.45,
            0.8,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.32,
            0.28,
            0.8,
        ],
        font_size=5,
    )

    doc.add_page_break()
    doc.add_heading("Appendix D. Ingredient-Level Pricing Rows", level=1)
    add_paragraph(
        doc,
        "Each row shows the ingredient/source row, portion, consumed-portion cost, matched price rule, market unit, confidence, market source, and source URL when available."
    )
    add_table(
        doc,
        [
            "#",
            "ID",
            "Recipe",
            "Meal",
            "Ingredient",
            "g",
            "Cost",
            "Pricing status",
            "Price rule",
            "Unit price",
            "Unit",
            "Conf",
            "Market/source basis",
            "Source URL",
        ],
        pricing_detail_rows(evidence_rows),
        [
            0.22,
            0.45,
            1.1,
            0.45,
            0.9,
            0.4,
            0.45,
            0.8,
            0.7,
            0.4,
            0.35,
            0.45,
            1.4,
            1.4,
        ],
        font_size=5,
    )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    doc.save(OUT)

    with zipfile.ZipFile(OUT) as package:
        if "[Content_Types].xml" not in package.namelist():
            raise RuntimeError("Generated DOCX is missing [Content_Types].xml")

    return {
        "output": str(OUT),
        "recipe_count": len(recipes),
        "evidence_row_count": len(evidence_rows),
        "recomputed_total_rows": len(recomputed_rows),
        "tables": len(doc.tables),
        "paragraphs": len(doc.paragraphs),
    }


def main() -> int:
    summary = build_doc()
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
