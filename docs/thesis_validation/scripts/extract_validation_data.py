#!/usr/bin/env python3
from __future__ import annotations

import ast
import csv
import importlib.util
import json
import math
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path
from statistics import mean
from textwrap import dedent


REPO_ROOT = Path(__file__).resolve().parents[3]
THESIS_DIR = REPO_ROOT / "docs" / "thesis_validation"
EXPORT_DIR = THESIS_DIR / "03_ACTUAL_SYSTEM_DATA_EXPORTS"
SCRIPTS_DIR = THESIS_DIR / "scripts"


REQUIRED_ROOT_FILES = [
    "README.md",
    "ARCHITECTURE.md",
    "TEST_PLAN.md",
    "ML_GUARDRAILS.md",
    "PRIVACY_AND_SECURITY.md",
    "RISK_REGISTER.md",
    "RELEASE_CHECKLIST.md",
    "render.yaml",
    "app/build.gradle.kts",
    "gradle/libs.versions.toml",
    "app/src/main/AndroidManifest.xml",
]

ANDROID_CORE_FILES = [
    "app/src/main/java/com/pcosina/app/data/model/UserProfile.kt",
    "app/src/main/java/com/pcosina/app/data/model/PantryEntry.kt",
    "app/src/main/java/com/pcosina/app/data/model/DailyLog.kt",
    "app/src/main/java/com/pcosina/app/data/model/MealCheckIn.kt",
    "app/src/main/java/com/pcosina/app/data/model/FeedbackEntry.kt",
    "app/src/main/java/com/pcosina/app/data/model/GrocerySource.kt",
    "app/src/main/java/com/pcosina/app/data/model/GrocerySnapshot.kt",
    "app/src/main/java/com/pcosina/app/data/model/DummyData.kt",
    "app/src/main/java/com/pcosina/app/data/model/PlanInstance.kt",
    "app/src/main/java/com/pcosina/app/data/model/MealPlan.kt",
    "app/src/main/java/com/pcosina/app/data/model/Recipe.kt",
    "app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt",
    "app/src/main/java/com/pcosina/app/domain/UnitConverter.kt",
    "app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt",
    "app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt",
    "app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt",
    "app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt",
    "app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt",
    "app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt",
    "app/src/main/java/com/pcosina/app/data/api/RecipeDetailDto.kt",
    "app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt",
    "app/src/main/java/com/pcosina/app/ui/GroceryViewModel.kt",
    "app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt",
    "app/src/main/java/com/pcosina/app/ui/UserViewModel.kt",
    "app/src/main/java/com/pcosina/app/ui/navigation/AppNavHost.kt",
]

BACKEND_CORE_FILES = [
    "backend/domain/models.py",
    "backend/services/meal_planner.py",
    "backend/services/plan_response_builder.py",
    "backend/services/ml_ranker.py",
    "backend/database.py",
    "backend/price_catalog.py",
    "backend/policy_config.py",
    "backend/main.py",
    "backend/worker_plan_jobs.py",
    "backend/recipes.json",
]

DOC_FILES = [
    "docs/roadmap/progress_ledger.md",
    "docs/roadmap/ml_progress_ledger.md",
]

ML_FILES = [
    "ml/offline_training/artifacts/model_v1/training_metrics.json",
    "ml/offline_training/artifacts/dataset_v1/dataset_manifest.json",
    "ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json",
]

BENCHMARK_FILES = [
    "benchmarks/README.md",
    "benchmarks/canonical_scenarios/comment5_canonical_scenarios.json",
    "benchmarks/runner/run_comment5_benchmark.py",
]

SAMPLE_PROFILE = {
    "age": 25,
    "heightCm": 160,
    "weightKg": 65,
    "activityLevel": "Lightly Active",
    "goal": "Weight Loss",
    "insulinResistanceLevel": "Moderate",
    "weeklyBudgetPhp": 1500,
    "householdSize": 1,
    "maxCookingTimeMinutes": 45,
    "pantryItems": ["egg", "rice", "tomato", "onion"],
    "allergies": [],
    "dietaryRestrictions": [],
    "symptoms": [],
    "displayName": "Sample User",
}


def rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def read_text(relative_path: str) -> str:
    return (REPO_ROOT / relative_path).read_text(encoding="utf-8")


def file_exists(relative_path: str) -> bool:
    return (REPO_ROOT / relative_path).exists()


def line_of(relative_path: str, needle: str) -> int | None:
    try:
        for idx, line in enumerate(read_text(relative_path).splitlines(), start=1):
            if needle in line:
                return idx
    except FileNotFoundError:
        return None
    return None


def cite(relative_path: str, needle: str | None = None) -> str:
    if not file_exists(relative_path):
        return f"{relative_path} (NOT FOUND IN CURRENT DEV BRANCH)"
    if needle:
        ln = line_of(relative_path, needle)
        if ln:
            return f"{relative_path}:{ln}"
    return relative_path


def run_git(*args: str) -> str:
    return (
        subprocess.check_output(["git", *args], cwd=REPO_ROOT, text=True)
        .strip()
    )


def load_module(name: str, relative_path: str):
    module_path = REPO_ROOT / relative_path
    sys.path.insert(0, str(module_path.parent))
    try:
        spec = importlib.util.spec_from_file_location(name, module_path)
        module = importlib.util.module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(module)
        return module
    finally:
        if sys.path and sys.path[0] == str(module_path.parent):
            sys.path.pop(0)


def model_rebuild_policy_module(module) -> None:
    for class_name in [
        "NutritionPolicy",
        "SnackRules",
        "PlanningPolicy",
        "Stage1Policy",
        "SolverPolicy",
        "SyncOfflinePolicy",
        "SrePolicy",
        "SecurityPolicy",
        "MilpWeights",
        "PlannerPolicyConfig",
        "PolicyCreateRequest",
        "PolicyActivateRequest",
        "PolicyRollbackRequest",
    ]:
        if hasattr(module, class_name):
            getattr(module, class_name).model_rebuild(_types_namespace=module.__dict__)


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def normalize_markdown(content: str) -> str:
    lines = []
    in_fence = False
    for raw_line in content.rstrip().splitlines():
        line = raw_line
        if line.strip().startswith("```"):
            line = line.lstrip()
            lines.append(line)
            in_fence = not in_fence
            continue
        if not in_fence and line.startswith("        "):
            line = line[8:]
        lines.append(line)
    return "\n".join(lines) + "\n"


def write_text(relative_path: str, content: str) -> None:
    out_path = REPO_ROOT / relative_path
    ensure_dir(out_path.parent)
    if out_path.suffix.lower() == ".md":
        out_path.write_text(normalize_markdown(content), encoding="utf-8")
    else:
        out_path.write_text(content.rstrip() + "\n", encoding="utf-8")


def write_json(relative_path: str, payload) -> None:
    out_path = REPO_ROOT / relative_path
    ensure_dir(out_path.parent)
    out_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_csv(relative_path: str, rows: list[dict], fieldnames: list[str]) -> None:
    out_path = REPO_ROOT / relative_path
    ensure_dir(out_path.parent)
    with out_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "") for field in fieldnames})


def markdown_table(headers: list[str], rows: list[list[str]]) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for row in rows:
        safe = [str(cell).replace("\n", "<br>") for cell in row]
        lines.append("| " + " | ".join(safe) + " |")
    return "\n".join(lines)


def normalize_token(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", text.strip().lower()).strip("_")


def titleize_name(field_name: str) -> str:
    pieces = re.sub(r"([a-z])([A-Z])", r"\1 \2", field_name).replace("_", " ")
    return pieces.strip().title()


def load_json(relative_path: str):
    return json.loads(read_text(relative_path))


def list_relative_files(relative_dir: str, pattern: str) -> list[str]:
    base = REPO_ROOT / relative_dir
    if not base.exists():
        return []
    return sorted(rel(path) for path in base.rglob(pattern) if path.is_file())


def python_tests_inventory(relative_dir: str) -> list[dict]:
    rows = []
    for relative_path in list_relative_files(relative_dir, "test_*.py"):
        text = read_text(relative_path)
        for match in re.finditer(r"^def (test_[A-Za-z0-9_]+)\(", text, re.MULTILINE):
            rows.append(
                {
                    "suite": "backend",
                    "test_name": match.group(1),
                    "source_file": relative_path,
                    "line_number": text[: match.start()].count("\n") + 1,
                    "test_type": "pytest",
                }
            )
    return rows


def kotlin_tests_inventory(relative_dir: str, suite_type: str) -> list[dict]:
    rows = []
    for relative_path in list_relative_files(relative_dir, "*.kt"):
        text = read_text(relative_path)
        for match in re.finditer(r"@Test\s+fun\s+([A-Za-z0-9_]+)\s*\(", text, re.MULTILINE):
            rows.append(
                {
                    "suite": suite_type,
                    "test_name": match.group(1),
                    "source_file": relative_path,
                    "line_number": text[: match.start()].count("\n") + 1,
                    "test_type": "kotlin_test",
                }
            )
    return rows


def parse_kotlin_data_classes(relative_path: str) -> list[dict]:
    text = read_text(relative_path)
    classes = []
    for match in re.finditer(r"data class\s+([A-Za-z0-9_]+)\s*\(", text):
        class_name = match.group(1)
        start = match.end()
        depth = 1
        idx = start
        while idx < len(text) and depth > 0:
            if text[idx] == "(":
                depth += 1
            elif text[idx] == ")":
                depth -= 1
            idx += 1
        params_blob = text[start : idx - 1]
        class_line = text[: match.start()].count("\n") + 1
        fields = []
        for raw_line in params_blob.splitlines():
            line = raw_line.strip().rstrip(",")
            if not line.startswith(("val ", "var ")):
                continue
            field_match = re.match(r"(?:val|var)\s+([A-Za-z0-9_]+)\s*:\s*([^=]+?)(?:\s*=\s*(.+))?$", line)
            if not field_match:
                continue
            name = field_match.group(1).strip()
            data_type = field_match.group(2).strip()
            default_value = (field_match.group(3) or "").strip()
            field_line = line_of(relative_path, f"{name}:")
            fields.append(
                {
                    "class_name": class_name,
                    "field_name": name,
                    "data_type": data_type,
                    "default_value": default_value,
                    "line_number": field_line or class_line,
                    "required_or_optional": "optional" if ("?" in data_type or default_value) else "required",
                }
            )
        classes.append({"class_name": class_name, "line_number": class_line, "fields": fields})
    return classes


def parse_python_model_fields(relative_path: str, class_names: list[str]) -> list[dict]:
    text = read_text(relative_path)
    rows = []
    class_pattern = re.compile(r"^class\s+([A-Za-z0-9_]+)\([A-Za-z0-9_, ]+\):", re.MULTILINE)
    matches = list(class_pattern.finditer(text))
    boundaries = []
    for idx, match in enumerate(matches):
        start = match.start()
        end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
        boundaries.append((match.group(1), start, end))

    for class_name, start, end in boundaries:
        if class_name not in class_names:
            continue
        block = text[start:end]
        class_line = text[:start].count("\n") + 1
        for line in block.splitlines()[1:]:
            if not line.startswith("    "):
                break
            stripped = line.strip()
            if not stripped or stripped.startswith("@"):
                continue
            field_match = re.match(r"([A-Za-z0-9_]+)\s*:\s*([^=]+?)(?:\s*=\s*(.+))?$", stripped)
            if not field_match:
                continue
            field_name = field_match.group(1).strip()
            if field_name in {"model_config"}:
                continue
            data_type = field_match.group(2).strip()
            default_value = (field_match.group(3) or "").strip()
            validation_bits = []
            for key in ["ge", "gt", "le", "lt", "default"]:
                match_val = re.search(rf"{key}\s*=\s*([^,\)]+)", default_value)
                if match_val and key != "default":
                    validation_bits.append(f"{key}={match_val.group(1).strip()}")
            if "default_factory" in default_value:
                default_text = "default_factory"
            elif default_value:
                default_text = default_value
            else:
                default_text = ""
            field_line = line_of(relative_path, f"{field_name}:")
            rows.append(
                {
                    "class_name": class_name,
                    "field_name": field_name,
                    "data_type": data_type,
                    "default_value": default_text,
                    "validation_rule": "; ".join(validation_bits),
                    "line_number": field_line or class_line,
                    "required_or_optional": "optional"
                    if ("Optional[" in data_type or "None" in default_text or default_text)
                    else "required",
                }
            )
    return rows


def parse_meal_planner_literals() -> dict:
    relative_path = "backend/services/meal_planner.py"
    tree = ast.parse(read_text(relative_path), filename=relative_path)
    literals = {}
    for node in tree.body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id in {
                    "ING_SYNONYMS",
                    "ALLERGEN_SYNONYMS",
                    "FISH_FAMILY_TOKENS",
                    "SHELLFISH_FAMILY_TOKENS",
                    "DAIRY_FAMILY_TOKENS",
                    "EGG_FAMILY_TOKENS",
                    "NUTS_FAMILY_TOKENS",
                    "PEANUT_FAMILY_TOKENS",
                    "SOY_FAMILY_TOKENS",
                    "GLUTEN_FAMILY_TOKENS",
                    "SYMPTOM_ALIASES",
                }:
                    literals[target.id] = ast.literal_eval(node.value)
    literals["ALLERGEN_FAMILY_TOKENS"] = {
        "fish": sorted(literals.get("FISH_FAMILY_TOKENS", [])),
        "shellfish": sorted(literals.get("SHELLFISH_FAMILY_TOKENS", [])),
        "dairy": sorted(literals.get("DAIRY_FAMILY_TOKENS", [])),
        "egg": sorted(literals.get("EGG_FAMILY_TOKENS", [])),
        "nuts": sorted(literals.get("NUTS_FAMILY_TOKENS", [])),
        "peanut": sorted(literals.get("PEANUT_FAMILY_TOKENS", [])),
        "soy": sorted(literals.get("SOY_FAMILY_TOKENS", [])),
        "gluten": sorted(literals.get("GLUTEN_FAMILY_TOKENS", [])),
        "wheat": sorted(literals.get("GLUTEN_FAMILY_TOKENS", [])),
    }
    return literals


def parse_android_price_rules() -> list[dict]:
    relative_path = "app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt"
    text = read_text(relative_path)
    pattern = re.compile(
        r'PriceRule\(\s*listOf\((.*?)\)\s*,\s*([0-9.]+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)',
        re.DOTALL,
    )
    rows = []
    for match in pattern.finditer(text):
        keywords_blob, price, category, unit = match.groups()
        keywords = re.findall(r'"([^"]+)"', keywords_blob)
        source_line = text[: match.start()].count("\n") + 1
        for keyword in keywords:
            rows.append(
                {
                    "keyword": keyword,
                    "normalized_keyword_if_any": normalize_token(keyword),
                    "price_php": float(price),
                    "category": category,
                    "unit": unit,
                    "source_file": f"{relative_path}:{source_line}",
                    "notes": "Android grocery price rule",
                }
            )
    return rows


def collect_inspected_files() -> list[dict]:
    files = []

    def add(relative_path: str, purpose: str, trust: str, flags: dict | None = None, notes: str = ""):
        flags = flags or {}
        files.append(
            {
                "file": relative_path,
                "purpose": purpose,
                "user_input_data": flags.get("user_input_data", "N"),
                "formula_computation": flags.get("formula_computation", "N"),
                "rule_logic": flags.get("rule_logic", "N"),
                "optimization_logic": flags.get("optimization_logic", "N"),
                "stored_system_data": flags.get("stored_system_data", "N"),
                "validation_test_logic": flags.get("validation_test_logic", "N"),
                "ui_output_logic": flags.get("ui_output_logic", "N"),
                "trust_level": trust,
                "notes": notes,
            }
        )

    manual_overrides = {
        "README.md": (
            "Product overview and setup commands.",
            "MEDIUM",
            {"rule_logic": "Y", "optimization_logic": "Y", "validation_test_logic": "Y"},
        ),
        "ARCHITECTURE.md": (
            "System architecture and planner-stage description.",
            "MEDIUM",
            {"rule_logic": "Y", "optimization_logic": "Y", "stored_system_data": "Y"},
        ),
        "TEST_PLAN.md": (
            "Repo-defined validation plan and commands.",
            "MEDIUM",
            {"validation_test_logic": "Y"},
        ),
        "ML_GUARDRAILS.md": (
            "ML safety contract and deterministic fallback rules.",
            "MEDIUM",
            {"rule_logic": "Y", "optimization_logic": "Y"},
        ),
        "PRIVACY_AND_SECURITY.md": (
            "Privacy posture, local-first storage, and security notes.",
            "MEDIUM",
            {"stored_system_data": "Y", "rule_logic": "Y"},
        ),
        "RISK_REGISTER.md": (
            "Known risks and unresolved evidence gaps.",
            "LOW",
            {"validation_test_logic": "Y"},
        ),
        "RELEASE_CHECKLIST.md": (
            "Release evidence checklist and pre-release checks.",
            "LOW",
            {"validation_test_logic": "Y"},
        ),
        "render.yaml": (
            "Dev deployment blueprint and service topology.",
            "HIGH",
            {"rule_logic": "Y", "stored_system_data": "Y"},
        ),
        "app/build.gradle.kts": (
            "Android build config, schema version, and release endpoints.",
            "HIGH",
            {"rule_logic": "Y", "ui_output_logic": "Y"},
        ),
        "gradle/libs.versions.toml": (
            "Android dependency version catalog.",
            "HIGH",
            {},
        ),
        "app/src/main/AndroidManifest.xml": (
            "Android permissions and app manifest settings.",
            "HIGH",
            {"ui_output_logic": "Y", "rule_logic": "Y"},
        ),
        "backend/recipes.json": (
            "Bundled recipe inventory used by database seeding.",
            "HIGH",
            {"stored_system_data": "Y", "formula_computation": "Y"},
        ),
        "ml/offline_training/artifacts/model_v1/training_metrics.json": (
            "Offline ML artifact metrics for Stage 1 ranker evidence.",
            "HIGH",
            {"validation_test_logic": "Y", "formula_computation": "Y"},
        ),
        "ml/offline_training/artifacts/dataset_v1/dataset_manifest.json": (
            "Offline ML dataset manifest and row counts.",
            "HIGH",
            {"stored_system_data": "Y"},
        ),
        "ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json": (
            "Offline reason-feedback summary for ML rollout evidence.",
            "HIGH",
            {"stored_system_data": "Y", "validation_test_logic": "Y"},
        ),
    }

    for relative_path in REQUIRED_ROOT_FILES + ANDROID_CORE_FILES + BACKEND_CORE_FILES + DOC_FILES + ML_FILES + BENCHMARK_FILES:
        if relative_path in manual_overrides:
            purpose, trust, flags = manual_overrides[relative_path]
            add(relative_path, purpose, trust, flags)
            continue
        if not file_exists(relative_path):
            add(relative_path, "Expected source not found in current dev branch.", "MISSING")
            continue
        name = Path(relative_path).name
        flags = {}
        trust = "HIGH" if relative_path.endswith((".kt", ".py", ".json", ".yaml", ".yml")) else "MEDIUM"
        purpose = name
        if "/data/model/" in relative_path or "domain/models.py" in relative_path:
            purpose = "Data model definitions."
            flags = {"user_input_data": "Y", "stored_system_data": "Y", "ui_output_logic": "Y"}
        elif "HealthMetrics.kt" in relative_path or "UnitConverter.kt" in relative_path:
            purpose = "Health and unit computations."
            flags = {"formula_computation": "Y", "user_input_data": "Y", "ui_output_logic": "Y"}
        elif "PriceCatalog.kt" in relative_path or "price_catalog.py" in relative_path:
            purpose = "Price rules and price estimation logic."
            flags = {"formula_computation": "Y", "rule_logic": "Y", "stored_system_data": "Y"}
        elif "GroceryAggregation.kt" in relative_path:
            purpose = "Grocery aggregation and cost estimation."
            flags = {"formula_computation": "Y", "rule_logic": "Y", "ui_output_logic": "Y"}
        elif "/data/repository/" in relative_path:
            purpose = "Persistence and API orchestration."
            flags = {"user_input_data": "Y", "rule_logic": "Y", "stored_system_data": "Y", "ui_output_logic": "Y"}
        elif "/data/api/" in relative_path:
            purpose = "API DTOs and Retrofit endpoint contract."
            flags = {"user_input_data": "Y", "stored_system_data": "Y", "ui_output_logic": "Y"}
        elif "/ui/" in relative_path:
            purpose = "Android UI and view-model behavior."
            flags = {"user_input_data": "Y", "rule_logic": "Y", "ui_output_logic": "Y"}
        elif "meal_planner.py" in relative_path:
            purpose = "Backend planner scoring, filtering, and CP-SAT optimization."
            flags = {"formula_computation": "Y", "rule_logic": "Y", "optimization_logic": "Y", "stored_system_data": "Y"}
        elif "plan_response_builder.py" in relative_path:
            purpose = "Structured no-safe-plan response builder."
            flags = {"rule_logic": "Y", "ui_output_logic": "Y"}
        elif "ml_ranker.py" in relative_path:
            purpose = "Optional ML ranking adapter and fallback handling."
            flags = {"formula_computation": "Y", "rule_logic": "Y"}
        elif "database.py" in relative_path:
            purpose = "Recipe seeding, database access, and normalization."
            flags = {"formula_computation": "Y", "stored_system_data": "Y", "rule_logic": "Y"}
        elif "policy_config.py" in relative_path:
            purpose = "Authoritative planner policy schema and defaults."
            flags = {"rule_logic": "Y", "optimization_logic": "Y", "stored_system_data": "Y"}
        elif "main.py" in relative_path or "worker_plan_jobs.py" in relative_path:
            purpose = "Backend API/runtime orchestration."
            flags = {"rule_logic": "Y", "optimization_logic": "Y", "ui_output_logic": "Y"}
        elif relative_path.startswith("docs/"):
            purpose = "Supporting roadmap documentation."
            trust = "LOW"
        elif relative_path.startswith("benchmarks/"):
            purpose = "Benchmark or canary support artifact."
            flags = {"validation_test_logic": "Y"}
        add(relative_path, purpose, trust, flags)

    for relative_path in list_relative_files("app/src/main/java/com/pcosina/app/ui/screens", "*.kt"):
        add(
            relative_path,
            "Compose screen and output flow.",
            "HIGH",
            {"user_input_data": "Y", "rule_logic": "Y", "ui_output_logic": "Y"},
        )

    for relative_path in list_relative_files("backend/tests", "test_*.py"):
        add(
            relative_path,
            "Backend automated test coverage.",
            "HIGH",
            {"validation_test_logic": "Y", "rule_logic": "Y", "optimization_logic": "Y"},
        )

    for relative_path in list_relative_files(".github/workflows", "*.yml") + list_relative_files(".github/workflows", "*.yaml"):
        add(
            relative_path,
            "CI/CD and operational validation workflow.",
            "HIGH",
            {"validation_test_logic": "Y", "rule_logic": "Y"},
        )

    android_unit_tests = list_relative_files("app/src/test/java", "*.kt")
    android_ui_tests = list_relative_files("app/src/androidTest/java", "*.kt")
    for relative_path in android_unit_tests + android_ui_tests:
        add(
            relative_path,
            "Android automated test coverage.",
            "HIGH",
            {"validation_test_logic": "Y", "ui_output_logic": "Y", "rule_logic": "Y"},
        )

    benchmark_dir = REPO_ROOT / "benchmarks"
    if not benchmark_dir.exists():
        add("benchmarks/", "Benchmark directory expected by task but not found.", "MISSING")

    return files


def recipe_inventory(database_module) -> list[dict]:
    recipes_raw = load_json("backend/recipes.json")
    medians = database_module._compute_nutrition_medians(recipes_raw)
    rows = []
    for recipe in recipes_raw:
        nutrition = recipe.get("nutrition", {}) or {}
        calories, protein, carbs, fats, fiber = database_module._normalize_nutrition(nutrition, medians)
        inferred_tags = database_module._infer_tags(recipe)
        row = {
            "recipe_id": recipe.get("id", ""),
            "title": recipe.get("name", ""),
            "meal_type": recipe.get("mealType", "Universal"),
            "calories": calories,
            "protein_grams": protein,
            "carbs_grams": carbs,
            "fats_grams": fats,
            "fiber_grams": fiber,
            "sodium_mg_if_any": nutrition.get("sodium_mg", ""),
            "sugar_grams_if_any": nutrition.get("sugar_g", ""),
            "minutes": recipe.get("minutes", 25),
            "tags": ", ".join(inferred_tags),
            "ingredient_count": len(recipe.get("ingredients", [])),
            "ingredients": " | ".join(
                f"{item.get('name', '')} ({item.get('quantity', '')})" for item in recipe.get("ingredients", [])
            ),
            "source_file_or_seed_function": "backend/recipes.json + backend/database.py::seed_recipes",
            "notes": "Static export from bundled recipe dataset; runtime nutrition corrections depend on live DB state.",
        }
        rows.append(row)
    return rows


def build_recipe_inventory_json(recipe_rows: list[dict]) -> list[dict]:
    payload = []
    for row in recipe_rows:
        payload.append(
            {
                "recipe_id": row["recipe_id"],
                "title": row["title"],
                "meal_type": row["meal_type"],
                "nutrition": {
                    "calories": row["calories"],
                    "protein_grams": row["protein_grams"],
                    "carbs_grams": row["carbs_grams"],
                    "fats_grams": row["fats_grams"],
                    "fiber_grams": row["fiber_grams"],
                    "sodium_mg_if_any": row["sodium_mg_if_any"],
                    "sugar_grams_if_any": row["sugar_grams_if_any"],
                },
                "minutes": row["minutes"],
                "tags": [tag.strip() for tag in row["tags"].split(",") if tag.strip()],
                "ingredient_count": row["ingredient_count"],
                "ingredients": row["ingredients"].split(" | ") if row["ingredients"] else [],
                "source_file_or_seed_function": row["source_file_or_seed_function"],
                "notes": row["notes"],
            }
        )
    return payload


def policy_rows(policy_module) -> list[dict]:
    model_rebuild_policy_module(policy_module)
    default_policy = policy_module.default_policy().model_dump()
    production_policy = policy_module.default_policy().resolve_for_environment("production")
    rows = []

    def flatten(prefix: str, value, source_file: str, default_or_environment_specific: str):
        if isinstance(value, dict):
            for key, inner in value.items():
                flatten(f"{prefix}.{key}" if prefix else key, inner, source_file, default_or_environment_specific)
        elif isinstance(value, list):
            rows.append(
                {
                    "policy_path": prefix,
                    "value": json.dumps(value),
                    "source_file": source_file,
                    "default_or_environment_specific": default_or_environment_specific,
                    "affects_planner_yes_no": "yes",
                    "notes": "",
                }
            )
        else:
            rows.append(
                {
                    "policy_path": prefix,
                    "value": value,
                    "source_file": source_file,
                    "default_or_environment_specific": default_or_environment_specific,
                    "affects_planner_yes_no": "yes",
                    "notes": "",
                }
            )

    flatten("", default_policy, cite("backend/policy_config.py", "def default_policy"), "default")
    flatten("", production_policy, cite("backend/policy_config.py", 'return {'), "production_runtime")
    return rows


def constraint_rows(citations: dict) -> list[dict]:
    return [
        {
            "constraint_name": "Valid profile required before planner execution",
            "stage": "precheck",
            "constraint_type": "hard",
            "source_file": citations["validate_profile"],
            "source_function": "validate_profile",
            "notes": "Rejects invalid household size, conflicting restrictions, missing budget for budget priority, and invalid max cooking time.",
        },
        {
            "constraint_name": "Planning horizon days must equal active policy",
            "stage": "precheck",
            "constraint_type": "hard",
            "source_file": citations["solve_meal_plan"],
            "source_function": "solve_meal_plan",
            "notes": "Current default policy expects 7 days.",
        },
        {
            "constraint_name": "Meals per day must equal active policy",
            "stage": "precheck",
            "constraint_type": "hard",
            "source_file": citations["solve_meal_plan"],
            "source_function": "solve_meal_plan",
            "notes": "Current default policy expects 3 meals per day.",
        },
        {
            "constraint_name": "Allergy exposure exclusion",
            "stage": "stage1",
            "constraint_type": "hard",
            "source_file": citations["restriction_failure_reasons"],
            "source_function": "restriction_failure_reasons",
            "notes": "Recipes exposing normalized allergen families are removed before optimization.",
        },
        {
            "constraint_name": "Dietary restriction exclusion",
            "stage": "stage1",
            "constraint_type": "hard",
            "source_file": citations["restriction_failure_reasons"],
            "source_function": "restriction_failure_reasons",
            "notes": "No Pork, No Beef, Vegetarian, Pescatarian, and Lactose Intolerant have explicit exclusion rules.",
        },
        {
            "constraint_name": "Max cooking time screen-out",
            "stage": "stage1",
            "constraint_type": "hard",
            "source_file": citations["shortlist_candidates"],
            "source_function": "shortlist_candidates",
            "notes": "Recipes beyond the profile's max cooking time are excluded from the shortlist.",
        },
        {
            "constraint_name": "Exactly one recipe per meal slot",
            "stage": "stage2",
            "constraint_type": "hard",
            "source_file": citations["solve_meal_plan"],
            "source_function": "solve_meal_plan",
            "notes": "The CP-SAT model chooses one allowed recipe per breakfast, lunch, and dinner slot.",
        },
        {
            "constraint_name": "Meal-slot compatibility",
            "stage": "stage2",
            "constraint_type": "hard",
            "source_file": citations["infer_allowed_meals"],
            "source_function": "infer_allowed_meals",
            "notes": "Recipes are only assignable to allowed meal labels.",
        },
        {
            "constraint_name": "No adjacent identical recipe",
            "stage": "stage2",
            "constraint_type": "hard",
            "source_file": citations["solve_meal_plan"],
            "source_function": "solve_meal_plan",
            "notes": "Back-to-back repetition is blocked.",
        },
        {
            "constraint_name": "Per-attempt max recipe reuse cap",
            "stage": "stage2",
            "constraint_type": "hard",
            "source_file": citations["adjust_max_per_week"],
            "source_function": "adjust_max_per_week + solve_meal_plan",
            "notes": "The solver retries with broader repeat caps if earlier attempts are infeasible.",
        },
        {
            "constraint_name": "Weekly budget hard cap when budget exists",
            "stage": "stage2",
            "constraint_type": "hard",
            "source_file": citations["resolve_budget_weekly"],
            "source_function": "resolve_budget_weekly + solve_meal_plan",
            "notes": "Cost optimization is only activated for budget priority, but the weekly budget cap itself is hard whenever a budget is resolved.",
        },
        {
            "constraint_name": "Daily calorie target deviation penalty",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["nutrition_policy"],
            "source_function": "NutritionPolicy + solve_meal_plan",
            "notes": "Deviation is penalized within tolerance bands.",
        },
        {
            "constraint_name": "Daily macro target deviation penalty",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["macro_ratios"],
            "source_function": "macro_ratios + solve_meal_plan",
            "notes": "Protein, carbohydrate, and fat deviations are penalized from daily targets.",
        },
        {
            "constraint_name": "Fiber shortfall penalty",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["symptom_adjustments"],
            "source_function": "symptom_adjustments + solve_meal_plan",
            "notes": "Fiber targets are raised for some goals/symptoms and shortfall is penalized.",
        },
        {
            "constraint_name": "Sodium overage penalty",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["nutrition_policy"],
            "source_function": "NutritionPolicy + solve_meal_plan",
            "notes": "Penalty only works when sodium values exist in recipe data or corrections.",
        },
        {
            "constraint_name": "Sugar overage penalty",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["nutrition_policy"],
            "source_function": "NutritionPolicy + solve_meal_plan",
            "notes": "Sugar ceiling can be tightened by symptom adjustments.",
        },
        {
            "constraint_name": "Vegetable diversity reward/minimum slack",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["planning_policy"],
            "source_function": "PlanningPolicy + solve_meal_plan",
            "notes": "Encourages diverse vegetable tokens across the week.",
        },
        {
            "constraint_name": "Pantry reward/minimum slack",
            "stage": "stage2",
            "constraint_type": "soft",
            "source_file": citations["stage1_policy"],
            "source_function": "Stage1Policy + solve_meal_plan",
            "notes": "Pantry is rewarded, but NOT enforced as a hard feasibility constraint in current code.",
        },
    ]


def objective_rows(citations: dict) -> list[dict]:
    return [
        {
            "objective_component": "Daily calorie deviation",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Absolute daily calorie error from jittered target.",
        },
        {
            "objective_component": "Daily protein deviation",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Absolute protein deviation from target grams.",
        },
        {
            "objective_component": "Daily carbohydrate deviation",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Absolute carbohydrate deviation from target grams.",
        },
        {
            "objective_component": "Daily fat deviation",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Absolute fat deviation from target grams.",
        },
        {
            "objective_component": "Fiber slack",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Penalty when fiber target is missed.",
        },
        {
            "objective_component": "Sodium overage",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Penalty when daily sodium exceeds the policy ceiling.",
        },
        {
            "objective_component": "Sugar overage",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Penalty when daily sugar exceeds the policy ceiling.",
        },
        {
            "objective_component": "Weekly cost",
            "direction": "minimize when budget priority is active",
            "hard_or_soft": "soft",
            "source_file": citations["should_optimize_cost"],
            "function": "_should_optimize_cost + solve_meal_plan",
            "notes": "Hard budget cap may still apply even when cost is not in the soft objective.",
        },
        {
            "objective_component": "Preparation time burden",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Longer preparation time increases penalty.",
        },
        {
            "objective_component": "Recipe repetition overage",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Works with hard per-attempt repetition caps.",
        },
        {
            "objective_component": "Protein group concentration",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Discourages over-concentration in one protein group.",
        },
        {
            "objective_component": "Acceptance/diversity penalties",
            "direction": "minimize",
            "hard_or_soft": "soft",
            "source_file": citations["planning_policy"],
            "function": "PlanningPolicy + solve_meal_plan",
            "notes": "Uses planning weights from policy config.",
        },
        {
            "objective_component": "Pantry reward",
            "direction": "maximize (implemented as subtracting reward)",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Rewards pantry overlap but does not enforce full pantry feasibility.",
        },
        {
            "objective_component": "Vegetable diversity reward",
            "direction": "maximize (implemented as subtracting reward)",
            "hard_or_soft": "soft",
            "source_file": citations["solve_meal_plan"],
            "function": "solve_meal_plan",
            "notes": "Rewards unique vegetable token coverage.",
        },
    ]


def discover_api_endpoints() -> list[dict]:
    rows = []
    android_file = "app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt"
    android_text = read_text(android_file)
    for method in ["GET", "POST", "PUT", "DELETE"]:
        pattern = re.compile(rf'@{method}\("([^"]+)"\)\s+suspend fun\s+([A-Za-z0-9_]+)\(')
        for match in pattern.finditer(android_text):
            line = android_text[: match.start()].count("\n") + 1
            rows.append(
                {
                    "method": method,
                    "path": "/" + match.group(1).lstrip("/"),
                    "surface": "Android client",
                    "source_file": f"{android_file}:{line}",
                    "function_or_decorator": match.group(2),
                    "notes": "",
                }
            )

    backend_file = "backend/main.py"
    backend_text = read_text(backend_file)
    for method in ["get", "post", "put", "delete", "patch"]:
        pattern = re.compile(rf'@app\.{method}\("([^"]+)"(?:,|\))')
        for match in pattern.finditer(backend_text):
            line = backend_text[: match.start()].count("\n") + 1
            rows.append(
                {
                    "method": method.upper(),
                    "path": match.group(1),
                    "surface": "Backend server",
                    "source_file": f"{backend_file}:{line}",
                    "function_or_decorator": f"@app.{method}",
                    "notes": "",
                }
            )
    return rows


def stored_local_artifacts_rows() -> list[dict]:
    return [
        {
            "artifact_name": "user_prefs DataStore",
            "storage_mechanism": "Preferences DataStore",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", "private val Context.userPrefsDataStore"),
            "key_or_domain": "user_prefs",
            "sync_behavior": "local-first; selected fields may sync to Firestore",
            "used_for": "Profile, preferences, local flags, active-plan metadata.",
            "notes": "Not Room/SQLite on Android.",
        },
        {
            "artifact_name": "pantry_entries",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"pantry_entries"'),
            "key_or_domain": "artifact_pantry_entries_<userId>",
            "sync_behavior": "local-only",
            "used_for": "Serialized pantry entries.",
            "notes": "",
        },
        {
            "artifact_name": "last_plan_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"last_plan_json"'),
            "key_or_domain": "artifact_last_plan_json_<userId>",
            "sync_behavior": "local-only cache",
            "used_for": "Most recent saved plan response.",
            "notes": "",
        },
        {
            "artifact_name": "plan_history_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"plan_history_json"'),
            "key_or_domain": "artifact_plan_history_json_<userId>",
            "sync_behavior": "local-only cache",
            "used_for": "Saved plan history list.",
            "notes": "",
        },
        {
            "artifact_name": "grocery_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"grocery_json"'),
            "key_or_domain": "artifact_grocery_json_<userId>",
            "sync_behavior": "local-only cache",
            "used_for": "Current grocery list.",
            "notes": "",
        },
        {
            "artifact_name": "grocery_sources_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"grocery_sources_json"'),
            "key_or_domain": "artifact_grocery_sources_json_<userId>",
            "sync_behavior": "local-only cache",
            "used_for": "Recipe source references for grocery items.",
            "notes": "",
        },
        {
            "artifact_name": "grocery_snapshots_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"grocery_snapshots_json"'),
            "key_or_domain": "artifact_grocery_snapshots_json_<userId>",
            "sync_behavior": "local-only cache",
            "used_for": "Saved grocery snapshots by plan.",
            "notes": "",
        },
        {
            "artifact_name": "feedback_queue_json",
            "storage_mechanism": "ReflectionStore encrypted artifact",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", '"feedback_queue_json"'),
            "key_or_domain": "artifact_feedback_queue_json_<userId>",
            "sync_behavior": "local-only until retry succeeds",
            "used_for": "Queued feedback submissions when offline or request fails.",
            "notes": "",
        },
        {
            "artifact_name": "daily_logs_<userId>",
            "storage_mechanism": "EncryptedSharedPreferences via ReflectionStore",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt", 'daily_logs_'),
            "key_or_domain": "daily_logs_<userId>",
            "sync_behavior": "local-only",
            "used_for": "Daily progress logs and meal check-ins.",
            "notes": "",
        },
        {
            "artifact_name": "weekly_journal_<userId>_<weekStart>",
            "storage_mechanism": "EncryptedSharedPreferences via ReflectionStore",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt", 'weekly_journal_'),
            "key_or_domain": "weekly_journal_<userId>_<weekStart>",
            "sync_behavior": "local-only",
            "used_for": "Weekly journal reflections.",
            "notes": "",
        },
        {
            "artifact_name": "weekly_spend_<userId>_<weekStart>",
            "storage_mechanism": "EncryptedSharedPreferences via ReflectionStore",
            "source_file": cite("app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt", 'weekly_spend_'),
            "key_or_domain": "weekly_spend_<userId>_<weekStart>",
            "sync_behavior": "local-only",
            "used_for": "Weekly spending notes.",
            "notes": "",
        },
    ]


def build_context() -> dict:
    git_branch = run_git("branch", "--show-current")
    git_sha = run_git("rev-parse", "HEAD")
    generated_at = datetime.now().astimezone().isoformat()

    warnings = []
    solver_import_warning = ""
    try:
        load_module("meal_planner_probe", "backend/services/meal_planner.py")
    except Exception as exc:
        solver_import_warning = f"{type(exc).__name__}: {exc}"
        warnings.append(
            "Local execution of backend/services/meal_planner.py is blocked in this environment; full CP-SAT plan generation was not executed."
        )

    policy_module = load_module("policy_config_validation", "backend/policy_config.py")
    price_catalog_module = load_module("price_catalog_validation", "backend/price_catalog.py")
    database_module = load_module("database_validation", "backend/database.py")
    model_rebuild_policy_module(policy_module)

    citations = {
        "android_bmi": cite("app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt", "fun bmi("),
        "android_bmi_category": cite("app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt", "fun bmiCategory("),
        "android_activity_multiplier": cite("app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt", "fun activityMultiplier("),
        "android_bmr": cite("app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt", "fun bmrMifflinStJeorFemale("),
        "android_target": cite("app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt", "fun targetCaloriesPerDay("),
        "unit_converter": cite("app/src/main/java/com/pcosina/app/domain/UnitConverter.kt", "object UnitConverter"),
        "android_price_catalog": cite("app/src/main/java/com/pcosina/app/domain/PriceCatalog.kt", "object PriceCatalog"),
        "android_grocery_aggregation": cite("app/src/main/java/com/pcosina/app/domain/GroceryAggregation.kt", "fun buildGroceryListEntries"),
        "android_grocery_screen_pantry": cite("app/src/main/java/com/pcosina/app/ui/screens/GroceryRefinedScreen.kt", "private fun refinedPantryMatches"),
        "android_user_profile": cite("app/src/main/java/com/pcosina/app/data/model/UserProfile.kt", "data class UserProfile"),
        "android_pantry_entry": cite("app/src/main/java/com/pcosina/app/data/model/PantryEntry.kt", "data class PantryEntry"),
        "user_prefs_repo": cite("app/src/main/java/com/pcosina/app/data/repository/UserPreferencesRepository.kt", "class UserPreferencesRepository"),
        "reflection_store": cite("app/src/main/java/com/pcosina/app/data/repository/ReflectionStore.kt", "class ReflectionStore"),
        "meal_plan_repo": cite("app/src/main/java/com/pcosina/app/data/repository/MealPlanRepository.kt", "class MealPlanRepository"),
        "meal_plan_vm": cite("app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt", "class MealPlanViewModel"),
        "progress_vm": cite("app/src/main/java/com/pcosina/app/ui/ProgressViewModel.kt", "class ProgressViewModel"),
        "backend_user_profile": cite("backend/domain/models.py", "class UserProfile(BaseModel):"),
        "macro_ratios": cite("backend/services/meal_planner.py", "def macro_ratios("),
        "activity_multiplier_backend": cite("backend/services/meal_planner.py", "def activity_multiplier("),
        "adjust_max_per_week": cite("backend/services/meal_planner.py", "def adjust_max_per_week("),
        "resolve_budget_weekly": cite("backend/services/meal_planner.py", "def resolve_budget_weekly("),
        "normalize_ingredients": cite("backend/services/meal_planner.py", "def normalize_ingredients("),
        "normalize_pantry": cite("backend/services/meal_planner.py", "def normalize_pantry("),
        "normalize_allergies": cite("backend/services/meal_planner.py", "def normalize_allergies("),
        "derive_allergen_exposures": cite("backend/services/meal_planner.py", "def derive_allergen_exposures("),
        "symptom_adjustments": cite("backend/services/meal_planner.py", "def symptom_adjustments("),
        "stage1_recipe_adjustments": cite("backend/services/meal_planner.py", "def stage1_recipe_adjustments("),
        "infer_allowed_meals": cite("backend/services/meal_planner.py", "def infer_allowed_meals("),
        "estimate_cost": cite("backend/services/meal_planner.py", "def estimate_cost("),
        "base_score": cite("backend/services/meal_planner.py", "def _base_score("),
        "shadow_ml_score": cite("backend/services/meal_planner.py", "def _shadow_ml_score("),
        "apply_stage1_scoring": cite("backend/services/meal_planner.py", "def _apply_stage1_scoring("),
        "restriction_failure_reasons": cite("backend/services/meal_planner.py", "def restriction_failure_reasons("),
        "validate_profile": cite("backend/services/meal_planner.py", "def validate_profile("),
        "planning_policy": cite("backend/policy_config.py", "class PlanningPolicy(BaseModel):"),
        "stage1_policy": cite("backend/policy_config.py", "class Stage1Policy(BaseModel):"),
        "nutrition_policy": cite("backend/policy_config.py", "class NutritionPolicy(BaseModel):"),
        "shortlist_candidates": cite("backend/services/meal_planner.py", "def shortlist_candidates("),
        "should_optimize_cost": cite("backend/services/meal_planner.py", "def _should_optimize_cost("),
        "solve_meal_plan": cite("backend/services/meal_planner.py", "def solve_meal_plan("),
        "build_no_safe_plan_response": cite("backend/services/plan_response_builder.py", "def build_no_safe_plan_response("),
        "reason_codes_from_message": cite("backend/services/plan_response_builder.py", "def _reason_codes_from_message("),
        "freshen_cached_plan_response": cite("backend/services/plan_response_builder.py", "def freshen_cached_plan_response("),
        "backend_price_catalog": cite("backend/price_catalog.py", "def estimate_price_detail("),
        "backend_recipe_cost": cite("backend/price_catalog.py", "def estimate_recipe_cost("),
        "database_seed_recipes": cite("backend/database.py", "def seed_recipes("),
        "database_get_all_recipes": cite("backend/database.py", "def get_all_recipes("),
        "backend_main_generate_plan": cite("backend/main.py", '@app.post("/generate-plan"'),
        "backend_main_async": cite("backend/main.py", '@app.post("/generate-plan-async"'),
        "backend_main_swap": cite("backend/main.py", '@app.post("/recipes/swap-options"'),
        "worker_plan_jobs": cite("backend/worker_plan_jobs.py", "def"),
    }

    meal_literals = parse_meal_planner_literals()
    android_price_rules = parse_android_price_rules()
    backend_price_rules = []
    for rule in getattr(price_catalog_module, "_RULES", []):
        keywords = getattr(rule, "keywords", [])
        price_php = getattr(rule, "price_php", "")
        category = getattr(rule, "category", "")
        unit = getattr(rule, "unit", "")
        for keyword in keywords:
            backend_price_rules.append(
                {
                    "keyword": keyword,
                    "normalized_keyword_if_any": normalize_token(keyword),
                    "price_php": price_php,
                    "category": category,
                    "unit": unit,
                    "source_file": cite("backend/price_catalog.py", "_RULES = ["),
                    "notes": "Backend base code rule. Runtime DB overrides are not bundled in the repo snapshot.",
                }
            )

    recipes = recipe_inventory(database_module)
    recipe_lookup = {row["recipe_id"]: row for row in recipes}
    sample_recipe_row = recipe_lookup["ph_qk_052"]
    recipes_raw = load_json("backend/recipes.json")
    sample_recipe_raw = next(recipe for recipe in recipes_raw if recipe.get("id") == "ph_qk_052")

    sample_height_m = SAMPLE_PROFILE["heightCm"] / 100.0
    sample_bmi = SAMPLE_PROFILE["weightKg"] / (sample_height_m * sample_height_m)
    sample_bmi_category = "Underweight" if sample_bmi < 18.5 else "Normal" if sample_bmi < 25 else "Overweight" if sample_bmi < 30 else "Obese"
    sample_bmr = (10 * SAMPLE_PROFILE["weightKg"]) + (6.25 * SAMPLE_PROFILE["heightCm"]) - (5 * SAMPLE_PROFILE["age"]) - 161
    android_multiplier = 1.375
    sample_tdee_android = round(sample_bmr * android_multiplier)
    sample_target_android = sample_tdee_android - 500
    sample_tdee_backend = int(sample_bmr * android_multiplier)
    sample_target_backend = max(1200, min(3200, sample_tdee_backend - 500))
    protein_ratio, carb_ratio, fat_ratio = (0.28, 0.35, 0.37)
    sample_protein_backend = max(55, min(220, int(sample_target_backend * protein_ratio / 4)))
    sample_carbs_backend = max(120, min(420, int(sample_target_backend * carb_ratio / 4)))
    sample_fats_backend = max(35, min(140, int(sample_target_backend * fat_ratio / 9)))

    normalized_pantry = []
    for item in SAMPLE_PROFILE["pantryItems"]:
        token = normalize_token(item)
        normalized_pantry.append(meal_literals["ING_SYNONYMS"].get(token, token))
    normalized_pantry = sorted(set(normalized_pantry))
    sample_ingredient_tokens = []
    for ingredient in sample_recipe_raw.get("ingredients", []):
        for part in re.split(r"[^A-Za-z0-9]+", ingredient.get("name", "")):
            token = normalize_token(part)
            if token:
                sample_ingredient_tokens.append(meal_literals["ING_SYNONYMS"].get(token, token))
    sample_ingredient_tokens = sorted(set(sample_ingredient_tokens))
    sample_pantry_match = len(set(normalized_pantry) & set(sample_ingredient_tokens))

    sample_recipe_cost_backend = price_catalog_module.estimate_recipe_cost(sample_recipe_raw.get("ingredients", []))
    sample_stage1_boost = 0.75
    sample_base_score = (
        (sample_recipe_row["protein_grams"] * 2.0)
        - (sample_recipe_cost_backend * 0.05)
        - (abs(sample_recipe_row["calories"] - 500) * 0.15)
        + (sample_pantry_match * 1.5)
        + sample_stage1_boost
    )
    sample_shadow_macro = min(1.0, (sample_recipe_row["protein_grams"] / 45.0) * 0.5 + (sample_recipe_row["fiber_grams"] / 15.0) * 0.5)
    sample_shadow_calorie = 1.0 - min(1.0, abs(sample_recipe_row["calories"] - 500) / 500.0)
    sample_shadow_prep = 1.0 - min(1.0, sample_recipe_row["minutes"] / 90.0)
    sample_shadow_pantry = min(1.0, sample_pantry_match / 5.0)
    sample_shadow_budget = 1.0 - min(1.0, sample_recipe_cost_backend / (SAMPLE_PROFILE["weeklyBudgetPhp"] / 21.0))
    sample_shadow_score = (
        (0.30 * sample_shadow_macro)
        + (0.25 * sample_shadow_calorie)
        + (0.15 * sample_shadow_prep)
        + (0.20 * sample_shadow_pantry)
        + (0.10 * sample_shadow_budget)
    )
    sample_stage1_boost_with_shadow = sample_stage1_boost + (min(0.30, sample_shadow_score) * 0.15 * 10.0)
    sample_base_score_with_shadow = (
        (sample_recipe_row["protein_grams"] * 2.0)
        - (sample_recipe_cost_backend * 0.05)
        - (abs(sample_recipe_row["calories"] - 500) * 0.15)
        + (sample_pantry_match * 1.5)
        + sample_stage1_boost_with_shadow
    )

    sample_egg_price_rule = next(rule for rule in android_price_rules if rule["keyword"] == "egg")
    sample_egg_price = round(max(5.0, sample_egg_price_rule["price_php"] * 2))

    backend_tests = python_tests_inventory("backend/tests")
    android_unit_tests = kotlin_tests_inventory("app/src/test/java", "android_unit")
    android_ui_tests = kotlin_tests_inventory("app/src/androidTest/java", "android_instrumented")

    dataset_summary = {
        "recipe_count": len(recipes),
        "meal_type_counts": Counter(row["meal_type"] for row in recipes),
        "average_calories": round(mean(row["calories"] for row in recipes), 2) if recipes else 0,
        "average_protein": round(mean(row["protein_grams"] for row in recipes), 2) if recipes else 0,
        "average_minutes": round(mean(row["minutes"] for row in recipes if row["minutes"] != ""), 2) if recipes else 0,
        "min_calories": min(row["calories"] for row in recipes) if recipes else 0,
        "max_calories": max(row["calories"] for row in recipes) if recipes else 0,
        "avg_ingredient_count": round(mean(row["ingredient_count"] for row in recipes), 2) if recipes else 0,
    }

    return {
        "git_branch": git_branch,
        "git_sha": git_sha,
        "generated_at": generated_at,
        "warnings": warnings,
        "solver_import_warning": solver_import_warning,
        "policy_module": policy_module,
        "price_catalog_module": price_catalog_module,
        "database_module": database_module,
        "citations": citations,
        "meal_literals": meal_literals,
        "android_price_rules": android_price_rules,
        "backend_price_rules": backend_price_rules,
        "recipes": recipes,
        "sample_recipe_row": sample_recipe_row,
        "sample_recipe_raw": sample_recipe_raw,
        "sample_bmi": sample_bmi,
        "sample_bmi_category": sample_bmi_category,
        "sample_bmr": sample_bmr,
        "sample_tdee_android": sample_tdee_android,
        "sample_target_android": sample_target_android,
        "sample_tdee_backend": sample_tdee_backend,
        "sample_target_backend": sample_target_backend,
        "sample_protein_backend": sample_protein_backend,
        "sample_carbs_backend": sample_carbs_backend,
        "sample_fats_backend": sample_fats_backend,
        "normalized_pantry": normalized_pantry,
        "sample_ingredient_tokens": sample_ingredient_tokens,
        "sample_pantry_match": sample_pantry_match,
        "sample_recipe_cost_backend": sample_recipe_cost_backend,
        "sample_base_score": sample_base_score,
        "sample_shadow_score": sample_shadow_score,
        "sample_stage1_boost_with_shadow": sample_stage1_boost_with_shadow,
        "sample_base_score_with_shadow": sample_base_score_with_shadow,
        "sample_egg_price": sample_egg_price,
        "backend_tests": backend_tests,
        "android_unit_tests": android_unit_tests,
        "android_ui_tests": android_ui_tests,
        "dataset_summary": dataset_summary,
        "inspected_files": collect_inspected_files(),
        "api_endpoints": discover_api_endpoints(),
    }


def build_data_dictionary_rows(ctx: dict) -> list[dict]:
    rows = []

    kotlin_sources = [
        "app/src/main/java/com/pcosina/app/data/model/UserProfile.kt",
        "app/src/main/java/com/pcosina/app/data/model/PantryEntry.kt",
        "app/src/main/java/com/pcosina/app/data/model/DailyLog.kt",
        "app/src/main/java/com/pcosina/app/data/model/MealCheckIn.kt",
        "app/src/main/java/com/pcosina/app/data/model/FeedbackEntry.kt",
        "app/src/main/java/com/pcosina/app/data/model/GrocerySource.kt",
        "app/src/main/java/com/pcosina/app/data/model/GrocerySnapshot.kt",
        "app/src/main/java/com/pcosina/app/data/model/PlanInstance.kt",
        "app/src/main/java/com/pcosina/app/data/model/MealPlan.kt",
        "app/src/main/java/com/pcosina/app/data/model/Recipe.kt",
        "app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt",
        "app/src/main/java/com/pcosina/app/data/api/RecipeDetailDto.kt",
    ]
    python_model_rows = parse_python_model_fields(
        "backend/domain/models.py",
        [
            "UserProfile",
            "Ingredient",
            "RecipeDetail",
            "RecipeSummary",
            "PlanExplanation",
            "GeneratePlanRequest",
            "SwapOptionsRequest",
            "PlannedMeal",
            "DayPlan",
            "GeneratePlanResponse",
            "FeedbackRequest",
            "MlClientEventRequest",
        ],
    )

    manual_field_notes = {
        ("UserProfile", "activityLevel"): ("Sedentary, Lightly Active, Moderately Active, Very Active", "Used by BMI/BMR/TDEE preview and backend target calories."),
        ("UserProfile", "goal"): ("Weight Loss, Symptom Management, General Health", "Used by calorie target and planner strategy."),
        ("UserProfile", "insulinResistanceLevel"): ("Mild, Moderate, Severe", "Used by backend macro ratio mapping."),
        ("UserProfile", "householdSize"): ("1..6", "Used to scale recipe cost and grocery quantities."),
        ("UserProfile", "maxCookingTimeMinutes"): ("10..240 on backend validation", "Used to exclude long recipes."),
        ("UserProfile", "dietaryRestrictions"): ("Recognized examples: Vegetarian, Pescatarian, Lactose Intolerant, No Pork, No Beef, No Eggs, No Dairy, No Seafood, No Fish", "Used for restriction filtering and profile conflict checks."),
        ("UserProfile", "allergies"): ("Normalized families include fish, shellfish, dairy, egg, nuts, peanut, soy, gluten, wheat", "Used for allergen-family exclusion."),
        ("GeneratePlanRequest", "days"): ("Current policy requires 7", "Planner rejects unsupported day counts."),
        ("GeneratePlanRequest", "mealsPerDay"): ("Current policy requires 3", "Planner rejects unsupported meal-slot counts."),
        ("GeneratePlanResponse", "status"): ("success, no-safe-plan", "Determines plan or guidance response behavior."),
        ("PlanExplanation", "toleranceUsed"): ("Derived from policy tolerance sequence", "Explains which tolerance band the solver used."),
    }

    class_layer_overrides = {
        "UserProfile": "Android",
        "PantryEntry": "Android",
        "DailyLog": "Android",
        "MealCheckIn": "Android",
        "FeedbackEntry": "Android",
        "GrocerySource": "Android",
        "GrocerySnapshot": "Android",
        "PlanInstance": "Android",
        "MealPlan": "Android",
        "DayPlan": "Android",
        "PlannedMeal": "Android",
        "Recipe": "Android",
        "IngredientDto": "Android",
        "RecipeDetailDto": "Android",
        "GeneratePlanRequest": "Android",
        "SwapOptionsRequestDto": "Android",
        "GeneratePlanResponse": "Android",
        "PlannerTimestamps": "Android",
        "PlanExplanation": "Android",
        "HealthResponse": "Android",
        "RecipeSummaryDto": "Android",
        "MlClientEventRequestDto": "Android",
        "MlClientEventResponseDto": "Android",
        "GeneratePlanAsyncResponse": "Android",
        "PlanJobDto": "Android",
    }

    for source in kotlin_sources:
        for parsed in parse_kotlin_data_classes(source):
            class_name = parsed["class_name"]
            layer = class_layer_overrides.get(class_name, "Android")
            for field in parsed["fields"]:
                note_tuple = manual_field_notes.get((class_name, field["field_name"]), ("", ""))
                rows.append(
                    {
                        "field_name": field["field_name"],
                        "display_name_if_any": titleize_name(field["field_name"]),
                        "layer": layer,
                        "source_file": f"{source}:{field['line_number']}",
                        "class_or_function": class_name,
                        "data_type": field["data_type"],
                        "default_value": field["default_value"],
                        "allowed_values_if_known": note_tuple[0],
                        "required_or_optional": field["required_or_optional"],
                        "validation_rule_if_known": "",
                        "used_for": note_tuple[1] or f"{class_name} model field.",
                        "affects_output_yes_no": "yes",
                        "output_affected": "Planner request, UI display, persistence, or diagnostics.",
                        "notes": "",
                    }
                )

    for field in python_model_rows:
        class_name = field["class_name"]
        layer = "Backend"
        if class_name in {"PlannedMeal", "DayPlan", "GeneratePlanResponse"}:
            layer = "Backend-only"
        if class_name == "UserProfile":
            layer = "Backend"
        note_tuple = manual_field_notes.get((class_name, field["field_name"]), ("", ""))
        rows.append(
            {
                "field_name": field["field_name"],
                "display_name_if_any": titleize_name(field["field_name"]),
                "layer": layer,
                "source_file": f"backend/domain/models.py:{field['line_number']}",
                "class_or_function": class_name,
                "data_type": field["data_type"],
                "default_value": field["default_value"],
                "allowed_values_if_known": note_tuple[0],
                "required_or_optional": field["required_or_optional"],
                "validation_rule_if_known": field["validation_rule"],
                "used_for": note_tuple[1] or f"{class_name} backend schema field.",
                "affects_output_yes_no": "yes",
                "output_affected": "Backend validation, planner execution, response serialization, or diagnostics.",
                "notes": "",
            }
        )

    model_rebuild_policy_module(ctx["policy_module"])
    default_policy = ctx["policy_module"].default_policy().model_dump()

    def flatten_policy(prefix: str, value):
        if isinstance(value, dict):
            for key, inner in value.items():
                flatten_policy(f"{prefix}.{key}" if prefix else key, inner)
        else:
            rows.append(
                {
                    "field_name": prefix,
                    "display_name_if_any": titleize_name(prefix.split(".")[-1]),
                    "layer": "Backend-only",
                    "source_file": cite("backend/policy_config.py", "class PlannerPolicyConfig"),
                    "class_or_function": "PlannerPolicyConfig runtime tree",
                    "data_type": type(value).__name__,
                    "default_value": json.dumps(value) if isinstance(value, (list, dict)) else value,
                    "allowed_values_if_known": "",
                    "required_or_optional": "required",
                    "validation_rule_if_known": "",
                    "used_for": "Planner policy value used by backend selection or optimization.",
                    "affects_output_yes_no": "yes",
                    "output_affected": "Candidate shaping, CP-SAT optimization, offline guarantees, or diagnostics.",
                    "notes": "Flattened from default runtime policy.",
                }
            )

    flatten_policy("", default_policy)

    return rows


def write_exports(ctx: dict) -> None:
    ensure_dir(EXPORT_DIR)

    recipe_fields = [
        "recipe_id",
        "title",
        "meal_type",
        "calories",
        "protein_grams",
        "carbs_grams",
        "fats_grams",
        "fiber_grams",
        "sodium_mg_if_any",
        "sugar_grams_if_any",
        "minutes",
        "tags",
        "ingredient_count",
        "ingredients",
        "source_file_or_seed_function",
        "notes",
    ]
    write_csv("docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/recipe_inventory.csv", ctx["recipes"], recipe_fields)
    write_json("docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/recipe_inventory.json", build_recipe_inventory_json(ctx["recipes"]))

    ingredient_rows = [
        {
            "raw_token": key,
            "normalized_token": value,
            "source_file": cite("backend/services/meal_planner.py", "ING_SYNONYMS = {"),
            "notes": "",
        }
        for key, value in sorted(ctx["meal_literals"]["ING_SYNONYMS"].items())
    ]
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/ingredient_synonyms.csv",
        ingredient_rows,
        ["raw_token", "normalized_token", "source_file", "notes"],
    )

    allergen_rows = [
        {
            "raw_allergy": key,
            "normalized_allergy": value,
            "source_file": cite("backend/services/meal_planner.py", "ALLERGEN_SYNONYMS = {"),
            "notes": "",
        }
        for key, value in sorted(ctx["meal_literals"]["ALLERGEN_SYNONYMS"].items())
    ]
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/allergen_synonyms.csv",
        allergen_rows,
        ["raw_allergy", "normalized_allergy", "source_file", "notes"],
    )

    family_rows = []
    for family, tokens in sorted(ctx["meal_literals"]["ALLERGEN_FAMILY_TOKENS"].items()):
        for token in sorted(tokens):
            family_rows.append(
                {
                    "family": family,
                    "token": token,
                    "source_file": cite("backend/services/meal_planner.py", "ALLERGEN_FAMILY_TOKENS = {"),
                    "notes": "",
                }
            )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/allergen_family_tokens.csv",
        family_rows,
        ["family", "token", "source_file", "notes"],
    )

    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/price_catalog_rules_android.csv",
        ctx["android_price_rules"],
        ["keyword", "normalized_keyword_if_any", "price_php", "category", "unit", "source_file", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/price_catalog_rules_backend.csv",
        ctx["backend_price_rules"],
        ["keyword", "normalized_keyword_if_any", "price_php", "category", "unit", "source_file", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/policy_values.csv",
        policy_rows(ctx["policy_module"]),
        ["policy_path", "value", "source_file", "default_or_environment_specific", "affects_planner_yes_no", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/planner_constraints.csv",
        constraint_rows(ctx["citations"]),
        ["constraint_name", "stage", "constraint_type", "source_file", "source_function", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/optimization_objective_summary.csv",
        objective_rows(ctx["citations"]),
        ["objective_component", "direction", "hard_or_soft", "source_file", "function", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/backend_test_inventory.csv",
        ctx["backend_tests"],
        ["suite", "test_name", "source_file", "line_number", "test_type"],
    )
    android_test_rows = ctx["android_unit_tests"] + ctx["android_ui_tests"]
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/android_test_inventory.csv",
        android_test_rows,
        ["suite", "test_name", "source_file", "line_number", "test_type"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/api_endpoints.csv",
        ctx["api_endpoints"],
        ["method", "path", "surface", "source_file", "function_or_decorator", "notes"],
    )
    write_csv(
        "docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/stored_local_artifacts.csv",
        stored_local_artifacts_rows(),
        ["artifact_name", "storage_mechanism", "source_file", "key_or_domain", "sync_behavior", "used_for", "notes"],
    )


def write_evidence_index(ctx: dict) -> None:
    grouped = defaultdict(list)
    for row in ctx["inspected_files"]:
        top = "root"
        if row["file"].startswith("app/"):
            top = "app"
        elif row["file"].startswith("backend/"):
            top = "backend"
        elif row["file"].startswith("docs/"):
            top = "docs"
        elif row["file"].startswith("ml/"):
            top = "ml"
        elif row["file"].startswith("benchmarks/"):
            top = "benchmarks"
        elif row["file"].startswith(".github/"):
            top = ".github"
        grouped[top].append(row)

    sections = []
    for group in ["root", "app", "backend", "docs", "ml", "benchmarks", ".github"]:
        rows = grouped.get(group, [])
        if not rows:
            continue
        headers = [
            "File",
            "Purpose",
            "Input",
            "Formula",
            "Rule",
            "Optimize",
            "Stored Data",
            "Tests",
            "UI",
            "Trust",
            "Notes",
        ]
        table_rows = [
            [
                row["file"],
                row["purpose"],
                row["user_input_data"],
                row["formula_computation"],
                row["rule_logic"],
                row["optimization_logic"],
                row["stored_system_data"],
                row["validation_test_logic"],
                row["ui_output_logic"],
                row["trust_level"],
                row["notes"],
            ]
            for row in sorted(rows, key=lambda item: item["file"])
        ]
        sections.append(f"## {group}\n\n{markdown_table(headers, table_rows)}")

    content = dedent(
        f"""
        # Chapter 4-6 Validation Evidence Index

        This evidence pack was generated from the `dev` branch only, as requested. No `main`-branch logic was used as the authoritative system description.

        ## Generation Metadata

        - Current branch: `{ctx["git_branch"]}`
        - Current commit SHA: `{ctx["git_sha"]}`
        - Date/time generated: `{ctx["generated_at"]}`
        - Generator script: `docs/thesis_validation/scripts/extract_validation_data.py`
        - Total inspected source entries listed below: `{len(ctx["inspected_files"])}` 

        ## Trust Levels

        - `HIGH`: directly implemented in code, tests, or bundled datasets.
        - `MEDIUM`: described in docs and supported by code.
        - `LOW`: described in docs but not directly demonstrated in current code.
        - `MISSING`: expected by the task but not found in the current `dev` branch.

        ## Environment Notes

        - Local plan generation using `backend/services/meal_planner.py` was **not executed** in this environment because importing OR-Tools-backed planner code is blocked here: `{ctx["solver_import_warning"] or "NOT APPLICABLE"}`.
        - Where code execution was blocked, the pack uses static parsing of actual source files and actual bundled datasets instead of guessed outputs.

        {chr(10).join(sections)}
        """
    ).strip()
    write_text("docs/thesis_validation/00_EVIDENCE_INDEX.md", content)


def write_data_dictionary(ctx: dict) -> None:
    rows = build_data_dictionary_rows(ctx)
    fieldnames = [
        "field_name",
        "display_name_if_any",
        "layer",
        "source_file",
        "class_or_function",
        "data_type",
        "default_value",
        "allowed_values_if_known",
        "required_or_optional",
        "validation_rule_if_known",
        "used_for",
        "affects_output_yes_no",
        "output_affected",
        "notes",
    ]
    write_csv("docs/thesis_validation/01_SYSTEM_DATA_DICTIONARY.csv", rows, fieldnames)


def write_formulas(ctx: dict) -> None:
    sample_recipe = ctx["sample_recipe_row"]
    content = dedent(
        f"""
        # Actual Formulas And Computations

        This file separates what is directly implemented from what is recommended for thesis validation but not yet implemented in the current `dev` branch.

        ## ACTUALLY IMPLEMENTED IN SYSTEM

        ### 1. BMI
        - Purpose: Compute body mass index for Android-side profile feedback.
        - Actual formula or pseudocode: `bmi = weightKg / ((heightCm / 100.0) ^ 2)`; if `heightCm <= 0` or `weightKg <= 0`, return `0.0`.
        - Input variables: `weightKg`, `heightCm`
        - Output variables: `bmi`
        - Units: kilograms, centimeters, kg/m^2
        - Source file path: `{ctx["citations"]["android_bmi"]}` (`HealthMetrics.bmi`)
        - Example manual computation using sample profile: `65 / (1.60^2) = 25.390625`
        - Implemented in: Android
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | weightKg | heightCm | manual_bmi | app_bmi | match_yes_no`

        ### 2. BMI Category Thresholds
        - Purpose: Convert the numeric BMI into a human-readable category in Android UI.
        - Actual formula or pseudocode:
          - `<= 0 -> "—"`
          - `< 18.5 -> Underweight`
          - `< 25 -> Normal`
          - `< 30 -> Overweight`
          - `else -> Obese`
        - Input variables: `bmi`
        - Output variables: `bmiCategory`
        - Units: category label
        - Source file path: `{ctx["citations"]["android_bmi_category"]}` (`HealthMetrics.bmiCategory`)
        - Example manual computation using sample profile: `25.390625 -> Overweight`
        - Implemented in: Android
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | manual_bmi | expected_category | app_category | match_yes_no`

        ### 3. BMR (Mifflin-St Jeor, female constant)
        - Purpose: Estimate baseline energy needs for calorie-target preview in Android and authoritative planning in backend.
        - Actual formula or pseudocode:
          - Android: `bmr = (10 * weightKg) + (6.25 * heightCm) - (5 * age) - 161`; if invalid values are provided, Android falls back to `weight=60`, `height=155`, `age=25`.
          - Backend: same core formula, but inside planner startup it falls back to `weight=65`, `height=160`, `age=25` when inputs are invalid.
        - Input variables: `weightKg`, `heightCm`, `age`
        - Output variables: `bmr`
        - Units: kcal/day
        - Source file path: Android `{ctx["citations"]["android_bmr"]}`, backend `{ctx["citations"]["solve_meal_plan"]}`
        - Example manual computation using sample profile: `(10*65) + (6.25*160) - (5*25) - 161 = 1364`
        - Implemented in: Android and backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | age | weightKg | heightCm | manual_bmr | android_bmr | backend_bmr | match_yes_no`

        ### 4. Activity Multiplier Mapping
        - Purpose: Convert activity level text into the multiplier used for TDEE.
        - Actual formula or pseudocode:
          - `Sedentary -> 1.2`
          - `Lightly Active -> 1.375`
          - `Moderately Active -> 1.55`
          - `Very Active -> 1.725`
          - default/unrecognized -> `1.375`
        - Input variables: `activityLevel`
        - Output variables: `activityMultiplier`
        - Units: multiplier
        - Source file path: Android `{ctx["citations"]["android_activity_multiplier"]}`, backend `{ctx["citations"]["activity_multiplier_backend"]}`
        - Example manual computation using sample profile: `Lightly Active -> 1.375`
        - Implemented in: Android and backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `activity_level | expected_multiplier | system_multiplier | match_yes_no`

        ### 5. TDEE
        - Purpose: Derive total daily energy expenditure from BMR and activity.
        - Actual formula or pseudocode:
          - Android: `tdee = round(bmr * activityMultiplier)`
          - Backend: `tdee = int(bmr * activityMultiplier)`
        - Input variables: `bmr`, `activityMultiplier`
        - Output variables: `tdee`
        - Units: kcal/day
        - Source file path: Android `{ctx["citations"]["android_target"]}`, backend `{ctx["citations"]["solve_meal_plan"]}`
        - Example manual computation using sample profile:
          - Android preview: `round(1364 * 1.375) = {ctx["sample_tdee_android"]}`
          - Backend planner: `int(1364 * 1.375) = {ctx["sample_tdee_backend"]}`
        - Implemented in: Android and backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | manual_tdee_android | manual_tdee_backend | app_tdee | backend_tdee | match_yes_no`

        ### 6. Daily Calorie Target
        - Purpose: Produce the calorie target used in Android preview and backend planning.
        - Actual formula or pseudocode:
          - Android:
            - `target = round(bmr * multiplier) + adjustment`
            - `Weight Loss -> -500`
            - `Symptom Management -> 0`
            - `General Health -> 0`
          - Backend:
            - `target = int(bmr * multiplier)`
            - `Weight Loss -> target -= 500`
            - `target += symptom_state["calorieTargetDelta"]`
            - `target = clamp(target, nutrition.calorie_min, nutrition.calorie_max)`
        - Input variables: `bmr`, `activityLevel`, `goal`, backend `symptom_state`
        - Output variables: `targetCalories`
        - Units: kcal/day
        - Source file path: Android `{ctx["citations"]["android_target"]}`, backend `{ctx["citations"]["solve_meal_plan"]}`
        - Example manual computation using sample profile:
          - Android preview: `{ctx["sample_tdee_android"]} - 500 = {ctx["sample_target_android"]}`
          - Backend authoritative target: `{ctx["sample_tdee_backend"]} - 500 = {ctx["sample_target_backend"]}`
        - Implemented in: Android and backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | goal | manual_target_android | manual_target_backend | app_target | backend_target | match_yes_no`

        ### 7. Goal-Based Calorie Adjustment
        - Purpose: Modify daily calories based on the stated goal.
        - Actual formula or pseudocode:
          - Android explicit branches: `Weight Loss = -500`, `Symptom Management = 0`, `General Health = 0`
          - Backend explicit branch: `Weight Loss = -500`; symptom-related deltas are applied through `symptom_adjustments()`
        - Input variables: `goal`, `symptoms`
        - Output variables: calorie delta
        - Units: kcal/day
        - Source file path: Android `{ctx["citations"]["android_target"]}`, backend `{ctx["citations"]["symptom_adjustments"]}`
        - Example manual computation using sample profile: `Weight Loss -> -500`, no additional symptom delta because no symptoms were supplied.
        - Implemented in: Android and backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `goal | symptoms | expected_delta | system_delta | match_yes_no`

        ### 8. Macro Ratio Mapping By Insulin Resistance
        - Purpose: Set backend daily macro ratios before gram conversion.
        - Actual formula or pseudocode:
          - `Severe -> (protein=0.30, carbs=0.30, fats=0.40)`
          - `Moderate -> (protein=0.28, carbs=0.35, fats=0.37)`
          - default (`Mild` and unrecognized) -> `(0.25, 0.40, 0.35)`
        - Input variables: `insulinResistanceLevel`
        - Output variables: `proteinRatio`, `carbRatio`, `fatRatio`
        - Units: ratio
        - Source file path: `{ctx["citations"]["macro_ratios"]}` (`macro_ratios`)
        - Example manual computation using sample profile: `Moderate -> (0.28, 0.35, 0.37)`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | insulinResistanceLevel | expected_ratios | backend_ratios | match_yes_no`

        ### 9. Macro Gram Conversion
        - Purpose: Convert the backend calorie target into daily protein, carbohydrate, and fat gram targets.
        - Actual formula or pseudocode:
          - `proteinGrams = clamp(int(targetCalories * proteinRatio / 4), protein_min, protein_max)`
          - `carbsGrams = clamp(int(targetCalories * carbRatio / 4), carb_min, carb_max)`
          - `fatsGrams = clamp(int(targetCalories * fatRatio / 9), fat_min, fat_max)`
          - then add any symptom-based deltas before the clamp is finalized.
        - Input variables: `targetCalories`, `macroRatios`, policy min/max, symptom deltas
        - Output variables: `targetProtein`, `targetCarbs`, `targetFats`
        - Units: grams/day
        - Source file path: `{ctx["citations"]["solve_meal_plan"]}` (`solve_meal_plan`)
        - Example manual computation using sample profile:
          - Protein: `int(1375 * 0.28 / 4) = {ctx["sample_protein_backend"]}`
          - Carbs: `int(1375 * 0.35 / 4) = 120` then clamp stays `120`
          - Fats: `int(1375 * 0.37 / 9) = {ctx["sample_fats_backend"]}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | targetCalories | manual_protein | manual_carbs | manual_fats | backend_targets | match_yes_no`

        ### 10. Unit Conversions
        - Purpose: Convert weight and height units for Android data entry.
        - Actual formula or pseudocode:
          - `kgToLb = kg * 2.20462`
          - `lbToKg = lb / 2.20462`
          - `cmToFeetInches`: convert cm to inches using `2.54`, split into feet and inches, round inches, roll over when inches reaches 12
          - `feetInchesToCm = (feet * 12 + inches) * 2.54`
        - Input variables: `kg`, `lb`, `cm`, `feet`, `inches`
        - Output variables: converted weight or height
        - Units: kg, lb, cm, ft/in
        - Source file path: `{ctx["citations"]["unit_converter"]}` (`UnitConverter`)
        - Example manual computation using sample profile: `160 cm -> about 5 ft 3 in`
        - Implemented in: Android
        - Manual validation recommended: Yes
        - Suggested validation table format: `input_value | source_unit | expected_value | app_value | match_yes_no`

        ### 11. Pantry Normalization
        - Purpose: Normalize pantry strings to tokens for backend Stage 1 matching.
        - Actual formula or pseudocode:
          - lowercase input
          - split on non-alphanumeric separators
          - normalize token format
          - map through `ING_SYNONYMS`
          - de-duplicate
        - Input variables: `pantryItems`
        - Output variables: normalized pantry token set
        - Units: normalized tokens
        - Source file path: `{ctx["citations"]["normalize_pantry"]}` (`normalize_pantry`)
        - Example manual computation using sample profile: `egg, rice, tomato, onion -> {", ".join(ctx["normalized_pantry"])}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `raw_pantry_item | expected_token | backend_token | match_yes_no`

        ### 12. Pantry Match Scoring
        - Purpose: Count backend token overlap between a recipe and pantry tokens.
        - Actual formula or pseudocode:
          - `pantryMatch = len(normalized_recipe_tokens intersect normalized_pantry_tokens)`
          - Stage 1 base score adds `pantryMatch * 1.5`
          - CP-SAT objective later adds pantry reward and pantry-minimum slack penalties
        - Input variables: normalized pantry tokens, normalized recipe tokens
        - Output variables: `pantryMatch`, score contribution
        - Units: token count and weighted score
        - Source file path: `{ctx["citations"]["shortlist_candidates"]}` and `{ctx["citations"]["base_score"]}`
        - Example manual computation using sample recipe `{sample_recipe["recipe_id"]} {sample_recipe["title"]}`:
          - Pantry tokens: `{", ".join(ctx["normalized_pantry"])}`
          - Normalized ingredient tokens include: `{", ".join(ctx["sample_ingredient_tokens"])}`
          - Overlap count: `{ctx["sample_pantry_match"]}`
          - Base-score pantry contribution: `{ctx["sample_pantry_match"]} * 1.5 = {ctx["sample_pantry_match"] * 1.5:.2f}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `recipe_id | pantry_tokens | recipe_tokens | overlap_count | expected_score_bonus | backend_value`

        ### 13. Allergy Normalization
        - Purpose: Convert free-text allergy entries into normalized families.
        - Actual formula or pseudocode:
          - lowercase input
          - normalize token
          - map through `ALLERGEN_SYNONYMS`
          - return unique normalized family set
        - Input variables: `allergies`
        - Output variables: normalized allergies
        - Units: normalized family labels
        - Source file path: `{ctx["citations"]["normalize_allergies"]}` (`normalize_allergies`)
        - Example manual computation using sample profile: no allergies supplied, so normalized set is empty.
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `raw_allergy | expected_family | backend_family | match_yes_no`

        ### 14. Allergen Family Matching
        - Purpose: Detect whether recipe ingredient tokens expose an allergen family.
        - Actual formula or pseudocode:
          - normalize recipe ingredient tokens
          - check whether any token belongs to `ALLERGEN_FAMILY_TOKENS[family]`
          - mark the family as exposed if a token matches
        - Input variables: recipe ingredient tokens, allergen-family token map
        - Output variables: detected exposure families
        - Units: family labels
        - Source file path: `{ctx["citations"]["derive_allergen_exposures"]}` (`derive_allergen_exposures`)
        - Example manual computation: token `bangus` maps to fish-family exposure; token `shrimp` maps to shellfish-family exposure.
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `ingredient_token | expected_family | backend_family | match_yes_no`

        ### 15. Dietary Restriction Filtering
        - Purpose: Exclude recipes that violate recognized diet restrictions.
        - Actual formula or pseudocode:
          - `No Pork` excludes recipes with normalized `pork` token
          - `No Beef` excludes recipes with normalized `beef` token
          - `Vegetarian` excludes `contains_meat` or `contains_seafood`
          - `Pescatarian` excludes `contains_meat`
          - `Lactose Intolerant` excludes `contains_dairy`
        - Input variables: `dietaryRestrictions`, recipe tags, normalized ingredient tokens
        - Output variables: keep/exclude decision and failure reasons
        - Units: rule decision
        - Source file path: `{ctx["citations"]["restriction_failure_reasons"]}` (`restriction_failure_reasons`)
        - Example manual computation using sample profile: no dietary restrictions supplied, so the sample recipe is not excluded by this path.
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `restriction | recipe_id | expected_keep_or_exclude | backend_result | reason`

        ### 16. Symptom-Based Adjustments
        - Purpose: Modify nutrition targets and Stage 1 bonuses for specific goals or symptom tags.
        - Actual formula or pseudocode:
          - `Symptom Management -> fiber +4, sugar -8, carbs -10, stage1 steady-carb/high-fiber bonuses`
          - `Weight Loss -> stage1 lower-calorie bonus +0.75, high-protein bonus +0.5`
          - `Weight Gain -> calorie +120, fiber +2, stage1 energy-density boosts`
          - `Irregular Periods -> fiber +2`
          - `Acne -> sugar -6, dairy penalty +2.0, steady-carb bonus +0.5`
          - `Hair Loss -> protein +8, high-protein bonus +1.25`
        - Input variables: `goal`, normalized `symptoms`, recipe nutrition/tags
        - Output variables: `calorieTargetDelta`, macro deltas, fiber/sugar adjustments, Stage 1 bonus flags
        - Units: kcal/day, grams/day, score bonuses
        - Source file path: `{ctx["citations"]["symptom_adjustments"]}` and `{ctx["citations"]["stage1_recipe_adjustments"]}`
        - Example manual computation using sample profile: because no symptom tags were supplied, symptom-specific deltas remain zero; only the weight-loss lower-calorie recipe bonus can apply to eligible recipes.
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `goal | symptoms | expected_delta_or_bonus | backend_value | match_yes_no`

        ### 17. Recipe Cost Estimation
        - Purpose: Estimate recipe-level cost for backend screening and budget handling.
        - Actual formula or pseudocode:
          - `estimate_recipe_cost(ingredients)` sums per-ingredient `estimate_price_detail`
          - scale total by `0.75`
          - clamp to `30..450`
          - `meal_planner.estimate_cost()` multiplies by household size
        - Input variables: ingredients, price rules, household size
        - Output variables: estimated recipe cost
        - Units: Philippine pesos
        - Source file path: `{ctx["citations"]["backend_recipe_cost"]}` and `{ctx["citations"]["estimate_cost"]}`
        - Example manual computation using sample recipe `{sample_recipe["recipe_id"]}`: backend estimated cost = `{ctx["sample_recipe_cost_backend"]}` PHP for household size `1`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `recipe_id | ingredient_list | manual_cost | backend_cost | household_size | match_yes_no`

        ### 18. Price Rule Matching
        - Purpose: Match grocery or ingredient names against code-defined price rules.
        - Actual formula or pseudocode:
          - Android and backend both scan rule keywords in normalized text
          - when a rule matches, unit/quantity factor is applied to the rule price
          - when no exact rule matches, category-based fallback logic is used
        - Input variables: ingredient/grocery name, quantity text, price rules
        - Output variables: matched rule, unit, estimated price
        - Units: Philippine pesos
        - Source file path: Android `{ctx["citations"]["android_price_catalog"]}`, backend `{ctx["citations"]["backend_price_catalog"]}`
        - Example manual computation using Android grocery estimate: `egg` rule is `PHP 7 per piece`, so `2 pieces -> PHP {ctx["sample_egg_price"]}`
        - Implemented in: Android and backend, but with **different** code rule tables and fallback maps
        - Manual validation recommended: Yes
        - Suggested validation table format: `item_name | quantity | matched_rule | manual_price | system_price | android_or_backend`

        ### 19. Grocery Item Aggregation
        - Purpose: Combine ingredient strings into a saved grocery list and estimate cost in Android.
        - Actual formula or pseudocode:
          - normalize display key
          - scale quantity text by household size
          - merge matching segments
          - sum estimated prices for segments using `PriceCatalog.estimatePriceDetail`
        - Input variables: ingredient strings, household size, saved plan sources
        - Output variables: grocery list entries with quantity display and estimated price
        - Units: text quantities and Philippine pesos
        - Source file path: `{ctx["citations"]["android_grocery_aggregation"]}` (`buildGroceryListEntries`)
        - Example manual computation using one item: `egg (2 pieces)` becomes one grocery entry with estimated price `PHP {ctx["sample_egg_price"]}`
        - Implemented in: Android
        - Manual validation recommended: Yes
        - Suggested validation table format: `source_ingredients | normalized_key | expected_quantity | expected_price | app_output`

        ### 20. Daily Total Calorie Calculation
        - Purpose: Summarize current meal-plan calories in Android after load or swap.
        - Actual formula or pseudocode: sum `recipe.calories` for all planned meals that have recipe details, together with the equivalent macro sums.
        - Input variables: selected recipes with detail data
        - Output variables: calories, protein, carbs, fats, fiber totals
        - Units: kcal/day or plan totals; grams for macros/fiber
        - Source file path: `{cite("app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt", "private fun calculateMetrics")}` (`MealPlanViewModel.calculateMetrics`)
        - Example manual computation: if the current plan only contained `{sample_recipe["recipe_id"]}`, the meal would contribute `{sample_recipe["calories"]}` kcal, `{sample_recipe["protein_grams"]}` g protein, `{sample_recipe["carbs_grams"]}` g carbs, `{sample_recipe["fats_grams"]}` g fats, `{sample_recipe["fiber_grams"]}` g fiber.
        - Implemented in: Android
        - Manual validation recommended: Yes
        - Suggested validation table format: `plan_id | recipe_ids | manual_totals | app_totals | match_yes_no`

        ### 21. Stage 1 Base Score
        - Purpose: Produce the deterministic pre-optimization score used to shortlist candidates.
        - Actual formula or pseudocode:
          - `_base_score = (protein * 2.0) - (cost_est * 0.05) - (abs(calories - 500) * 0.15) + (pantryMatch * 1.5) + stage1Boost`
        - Input variables: recipe protein, cost estimate, calories, pantryMatch, stage1Boost
        - Output variables: `_base_score`
        - Units: weighted score
        - Source file path: `{ctx["citations"]["base_score"]}` (`_base_score`)
        - Example manual computation using `{sample_recipe["recipe_id"]}`:
          - `(17 * 2.0) - ({ctx["sample_recipe_cost_backend"]} * 0.05) - (|390 - 500| * 0.15) + ({ctx["sample_pantry_match"]} * 1.5) + 0.75`
          - `= {ctx["sample_base_score"]:.3f}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `recipe_id | manual_base_score | backend_base_score | match_yes_no`

        ### 22. Shadow ML Score
        - Purpose: Produce deterministic fallback ranking features when no live ML score is applied.
        - Actual formula or pseudocode:
          - `macro = min(1, (protein/45)*0.5 + (fiber/15)*0.5)`
          - `calorie = 1 - min(1, abs(calories - 500)/500)`
          - `prep = 1 - min(1, minutes/90)`
          - `pantry = min(1, pantryMatch/5)`
          - `budget = 1 - min(1, recipeCost / (budget/21))` when budget exists
          - `shadow = 0.30*macro + 0.25*calorie + 0.15*prep + 0.20*pantry + 0.10*budget`
        - Input variables: recipe nutrition, prep time, pantry match, budget, cost
        - Output variables: `shadowMlScore`
        - Units: 0..1 score
        - Source file path: `{ctx["citations"]["shadow_ml_score"]}` (`_shadow_ml_score`)
        - Example manual computation using `{sample_recipe["recipe_id"]}`: shadow score ≈ `{ctx["sample_shadow_score"]:.4f}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `recipe_id | manual_shadow_score | backend_shadow_score | match_yes_no`

        ### 23. Final Stage 1 Boost After Shadow/ML Weighting
        - Purpose: Apply bounded ML or shadow score influence without replacing hard filters.
        - Actual formula or pseudocode:
          - `effectiveBoost = preservedBoost + (boundedMlScore * effectiveWeight * 10.0) - prepPenalty`
          - where `boundedMlScore` is capped by `ML_score_cap`
        - Input variables: preserved boost, ML or shadow score, weight, cap, prep penalty
        - Output variables: updated `_stage1_boost`
        - Units: weighted score
        - Source file path: `{ctx["citations"]["apply_stage1_scoring"]}` (`_apply_stage1_scoring`)
        - Example manual computation using `{sample_recipe["recipe_id"]}`: updated boost with shadow contribution ≈ `{ctx["sample_stage1_boost_with_shadow"]:.3f}`, giving a revised base score ≈ `{ctx["sample_base_score_with_shadow"]:.3f}`
        - Implemented in: Backend
        - Manual validation recommended: Yes
        - Suggested validation table format: `recipe_id | preserved_boost | shadow_or_ml_score | manual_final_boost | backend_final_boost`

        ### 24. Stage 2 Optimization Constraints
        - Purpose: Build the authoritative solver model for weekly meal planning.
        - Actual formula or pseudocode:
          - define boolean variable `x[slot, recipe]`
          - enforce exactly one recipe per slot
          - forbid disallowed meal-slot assignments
          - forbid adjacent identical recipe reuse
          - cap recipe reuse by current repeat limit
          - enforce weekly budget cap when budget exists
          - add soft deviation variables for daily calories, macros, fiber, sodium, sugar, meal distribution, pantry, and diversity
          - minimize weighted objective across those deviations
        - Input variables: shortlisted recipes, targets, policy config, budget, pantry, restrictions
        - Output variables: selected recipe assignments and explanation payload
        - Units: plan assignments and diagnostics
        - Source file path: `{ctx["citations"]["solve_meal_plan"]}` (`solve_meal_plan`)
        - Example manual computation using sample profile: the model would build `7 days * 3 meals = 21` meal slots, use the backend target of `{ctx["sample_target_backend"]}` kcal/day, and enforce the weekly budget cap of `PHP 1500`.
        - Implemented in: Backend
        - Manual validation recommended: Yes, by tracing one solved case and one no-safe-plan case
        - Suggested validation table format: `profile_id | candidate_count | hard_constraints_checked | soft_objective_terms | solver_result_status`

        ### 25. No-Safe-Plan Decision Logic
        - Purpose: Return a structured failure response when no safe plan can be produced.
        - Actual formula or pseudocode:
          - if profile validation fails -> return structured `no-safe-plan`
          - if Stage 1 yields too few safe candidates -> return structured `no-safe-plan`
          - if CP-SAT proves infeasible or times out -> return structured `no-safe-plan`
          - builder attaches `machineReasonCodes`, `humanGuidance`, `suggestedRelaxations`, diagnostics, solver metadata, and timestamps
        - Input variables: validation error, diagnostics, profile, solver status
        - Output variables: `GeneratePlanResponse` with `status="no-safe-plan"`
        - Units: response contract
        - Source file path: `{ctx["citations"]["build_no_safe_plan_response"]}` and `{ctx["citations"]["reason_codes_from_message"]}`
        - Example manual computation using sample profile: not triggered by the sample profile itself, but it would trigger if the budget or restrictions made the candidate set or solver model infeasible.
        - Implemented in: Backend and Android consumer path
        - Manual validation recommended: Yes
        - Suggested validation table format: `profile_id | failure_condition | expected_reason_codes | backend_response_status | guidance_present_yes_no`

        ## RECOMMENDED FOR THESIS VALIDATION BUT NOT YET IMPLEMENTED

        ### A. Unified single-source grocery price engine
        - Status: NOT FOUND IN CURRENT DEV BRANCH
        - Evidence: Android uses `{ctx["citations"]["android_price_catalog"]}` while backend uses `{ctx["citations"]["backend_price_catalog"]}` with separate rule tables and fallback maps.

        ### B. Hard pantry-feasibility constraint
        - Status: NOT FOUND IN CURRENT DEV BRANCH
        - Evidence: pantry is a Stage 1 score/reward path and a Stage 2 soft reward/slack path, not a hard all-ingredients-in-pantry rule in current planner code.

        ### C. Android-side macro target computation and display parity with backend planner
        - Status: NOT FOUND IN CURRENT DEV BRANCH
        - Evidence: Android `HealthMetrics` computes calorie preview only; macro ratio and gram targets are computed in backend planner code.
        """
    ).strip()
    write_text("docs/thesis_validation/02_FORMULAS_AND_COMPUTATIONS.md", content)


def write_decision_trees(ctx: dict) -> None:
    content = dedent(
        f"""
        # Rule-Based Decision Trees

        These are decision-flow diagrams based on actual code paths. They are **not** ML decision trees.

        ## A. Profile Validation Decision Tree

        ```mermaid
        flowchart TD
            A[Generate plan request received] --> B{{Android profile has age, height, weight, activity, goal?}}
            B -- No --> C[Client rejects request before backend call]
            B -- Yes --> D[Backend validate_profile()]
            D --> E{{householdSize 1..6 and maxCookingTime 10..240?}}
            E -- No --> F[Return no-safe-plan or validation error]
            E -- Yes --> G{{conflicting restrictions or priorities?}}
            G -- Yes --> F
            G -- No --> H[Proceed to Stage 1 candidate shaping]
        ```

        - Source files used: `{ctx["citations"]["meal_plan_repo"]}`, `{ctx["citations"]["validate_profile"]}`
        - Input variables: age, heightCm, weightKg, activityLevel, goal, householdSize, maxCookingTimeMinutes, dietaryRestrictions, allergies, planningPriority, weeklyBudgetPhp
        - Output variables: valid request or failure message
        - Validation approach: manual invalid-profile cases plus backend unit tests for conflicting restrictions
        - What to show during demo: incomplete profile handling and one conflicting-profile example

        ## B. Health Computation Decision Tree

        ```mermaid
        flowchart TD
            A[Profile values entered] --> B[Android BMI and BMR calculations]
            B --> C[Android activity multiplier lookup]
            C --> D[Android calorie target preview]
            D --> E{{Generate plan requested?}}
            E -- No --> F[Show preview only]
            E -- Yes --> G[Backend recomputes BMR, TDEE, target]
            G --> H[Backend derives macro targets and tolerances]
        ```

        - Source files used: `{ctx["citations"]["android_bmi"]}`, `{ctx["citations"]["android_target"]}`, `{ctx["citations"]["macro_ratios"]}`, `{ctx["citations"]["solve_meal_plan"]}`
        - Input variables: age, heightCm, weightKg, activityLevel, goal, insulinResistanceLevel, symptoms
        - Output variables: BMI, BMI category, calorie target preview, backend macro targets
        - Validation approach: manual step-by-step math with one shared sample profile
        - What to show during demo: Android preview values and note backend authoritative recalculation

        ## C. Recipe Filtering Decision Tree

        ```mermaid
        flowchart TD
            A[All recipes loaded] --> B[Normalize ingredient tokens and tags]
            B --> C{{Allergy exposure found?}}
            C -- Yes --> D[Exclude recipe]
            C -- No --> E{{Restriction rule fails?}}
            E -- Yes --> D
            E -- No --> F{{Minutes exceed max cooking time?}}
            F -- Yes --> D
            F -- No --> G[Compute cost, pantry match, Stage 1 boosts]
            G --> H[Send recipe into shortlist buckets]
        ```

        - Source files used: `{ctx["citations"]["normalize_ingredients"]}`, `{ctx["citations"]["restriction_failure_reasons"]}`, `{ctx["citations"]["shortlist_candidates"]}`
        - Input variables: recipe ingredients, tags, allergies, dietaryRestrictions, maxCookingTimeMinutes, householdSize, budget
        - Output variables: kept/excluded recipe and shortlist diagnostics
        - Validation approach: allergy/restriction test cases using actual recipes from `backend/recipes.json`
        - What to show during demo: one excluded recipe and one kept recipe

        ## D. Allergy And Dietary Restriction Decision Tree

        ```mermaid
        flowchart TD
            A[User allergy and restriction strings] --> B[Normalize allergies]
            B --> C[Derive recipe allergen exposures]
            C --> D{{Normalized allergy intersects exposures?}}
            D -- Yes --> E[Exclude recipe]
            D -- No --> F{{Restriction-specific rule fails?}}
            F -- Yes --> E
            F -- No --> G[Recipe remains eligible]
        ```

        - Source files used: `{ctx["citations"]["normalize_allergies"]}`, `{ctx["citations"]["derive_allergen_exposures"]}`, `{ctx["citations"]["restriction_failure_reasons"]}`
        - Input variables: allergies, dietaryRestrictions, recipe tokens, recipe tags
        - Output variables: exclusion reason or pass-through
        - Validation approach: fish/shellfish/dairy/egg/no-pork cases
        - What to show during demo: fish allergy blocking `bangus`, shellfish allergy blocking `shrimp`

        ## E. Pantry Matching Decision Tree

        ```mermaid
        flowchart TD
            A[Pantry items entered] --> B[Backend normalize_pantry()]
            B --> C[Normalize recipe ingredient tokens]
            C --> D[Count token overlap]
            D --> E[Add Stage 1 pantry score bonus]
            E --> F[Carry pantry reward/slack into Stage 2 objective]
        ```

        - Source files used: `{ctx["citations"]["normalize_pantry"]}`, `{ctx["citations"]["base_score"]}`, `{ctx["citations"]["solve_meal_plan"]}`
        - Input variables: pantryItems, recipe tokens, stage1 policy
        - Output variables: pantryMatch count and score contribution
        - Validation approach: manual token-overlap table using sample pantry and sample recipe
        - What to show during demo: one recipe with high overlap and explain that pantry is rewarded, not hard-enforced

        ## F. Budget/Cost Decision Tree

        ```mermaid
        flowchart TD
            A[Budget fields received] --> B[resolve_budget_weekly()]
            B --> C{{Budget value exists?}}
            C -- No --> D[No weekly hard cap]
            C -- Yes --> E[Estimate recipe costs]
            E --> F[Filter shortlist for budget-aware keep ratio]
            F --> G[Apply weekly budget hard cap in Stage 2]
            G --> H{{Planning priority contains budget?}}
            H -- Yes --> I[Add cost term to objective]
            H -- No --> J[Skip soft cost objective]
        ```

        - Source files used: `{ctx["citations"]["resolve_budget_weekly"]}`, `{ctx["citations"]["estimate_cost"]}`, `{ctx["citations"]["should_optimize_cost"]}`, `{ctx["citations"]["solve_meal_plan"]}`
        - Input variables: weeklyBudgetPhp, budgetWeekly, budgetMonthly, householdSize, planningPriority
        - Output variables: resolved weekly budget, cost-aware shortlist behavior, budget hard-cap enforcement
        - Validation approach: compare budget and non-budget-priority runs/tests
        - What to show during demo: budget priority versus balanced priority behavior

        ## G. Meal Plan Optimization Decision Tree

        ```mermaid
        flowchart TD
            A[Validated profile and shortlist] --> B[Build daily targets and macro targets]
            B --> C[Construct CP-SAT slot variables]
            C --> D[Add hard constraints]
            D --> E[Add soft deviation variables]
            E --> F[Minimize weighted objective]
            F --> G{{Solver finds feasible plan?}}
            G -- Yes --> H[Return success response with explanation]
            G -- No --> I[Return no-safe-plan response]
        ```

        - Source files used: `{ctx["citations"]["solve_meal_plan"]}`, `{ctx["citations"]["build_no_safe_plan_response"]}`
        - Input variables: shortlisted recipes, targets, policy, budget, pantry, restrictions
        - Output variables: `GeneratePlanResponse`
        - Validation approach: planner contract tests plus manual reading of constraints/objective
        - What to show during demo: solver metadata fields and explanation payload

        ## H. No-Safe-Plan Decision Tree

        ```mermaid
        flowchart TD
            A[Failure occurs] --> B{{Profile conflict?}}
            B -- Yes --> C[Map to reason codes and guidance]
            B -- No --> D{{No safe candidates or solver infeasible/time-out?}}
            D -- Yes --> C
            D -- No --> E[Unknown infeasibility code]
            C --> F[Build no-safe-plan response]
            F --> G[Android shows guidance and cached-plan continuity if available]
        ```

        - Source files used: `{ctx["citations"]["reason_codes_from_message"]}`, `{ctx["citations"]["build_no_safe_plan_response"]}`, `{cite("app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt", "private fun presentNoSafePlan")}`
        - Input variables: message text, diagnostics, profile, cached plan availability
        - Output variables: structured no-safe-plan response and Android presentation state
        - Validation approach: backend no-safe-plan contract tests plus UI tests
        - What to show during demo: reason codes, guidance text, and cached-plan fallback

        ## I. Grocery List Generation Decision Tree

        ```mermaid
        flowchart TD
            A[Saved meal plan exists] --> B[Collect meal-source ingredients]
            B --> C[Aggregate grocery entries]
            C --> D[Estimate item prices]
            D --> E[Load pantry names]
            E --> F[UI marks covered items by pantry-name matching]
            F --> G[Display grocery list and snapshots]
        ```

        - Source files used: `{ctx["citations"]["android_grocery_aggregation"]}`, `{ctx["citations"]["android_grocery_screen_pantry"]}`, `{ctx["citations"]["user_prefs_repo"]}`
        - Input variables: recipe ingredient strings, household size, pantry entries, checked state
        - Output variables: grocery items, prices, pantry-covered state, saved snapshots
        - Validation approach: manual grocery rebuild check using one sample recipe
        - What to show during demo: generated grocery items and pantry-covered behavior

        ## J. Offline/Local Cache Decision Tree

        ```mermaid
        flowchart TD
            A[App needs profile, plan, grocery, or progress state] --> B[Read local DataStore/ReflectionStore first]
            B --> C{{Local artifact exists?}}
            C -- Yes --> D[Use local data immediately]
            C -- No --> E[Fall back to empty/default state]
            D --> F{{Optional cloud sync available?}}
            F -- Yes --> G[Update local state from cloud-safe path]
            F -- No --> H[Continue offline-first behavior]
        ```

        - Source files used: `{ctx["citations"]["user_prefs_repo"]}`, `{ctx["citations"]["reflection_store"]}`, `{ctx["citations"]["progress_vm"]}`, `{ctx["citations"]["meal_plan_vm"]}`
        - Input variables: user ID, local artifact keys, cloud sync availability
        - Output variables: loaded local state, optional sync update
        - Validation approach: manual airplane-mode/load-from-cache demo
        - What to show during demo: saved plan still appears without backend response

        ## K. Plan History And Active Plan Decision Tree

        ```mermaid
        flowchart TD
            A[Plan response received or loaded] --> B[Persist last plan]
            B --> C[Append to plan history]
            C --> D[Load active plan on next app visit]
            D --> E{{Swap or feedback updates plan?}}
            E -- Yes --> F[Persist updated plan and recalculate metrics]
            E -- No --> G[Keep current active plan]
        ```

        - Source files used: `{ctx["citations"]["user_prefs_repo"]}`, `{ctx["citations"]["meal_plan_vm"]}`
        - Input variables: plan response, plan history, active user ID
        - Output variables: active plan, plan history, metrics
        - Validation approach: save/load/swap walkthrough plus repository persistence tests where present
        - What to show during demo: reopening the app and seeing the saved plan

        ## L. Swap Meal Decision Tree

        ```mermaid
        flowchart TD
            A[User taps swap meal] --> B[Request swap options from backend]
            B --> C{{Options available?}}
            C -- No --> D[Show no-swap or no-safe feedback]
            C -- Yes --> E[User selects replacement recipe]
            E --> F[Replace local slot]
            F --> G[Recalculate metrics]
            G --> H[Persist updated active plan]
        ```

        - Source files used: `{ctx["citations"]["meal_plan_repo"]}`, `{ctx["citations"]["meal_plan_vm"]}`, `{ctx["citations"]["backend_main_swap"]}`
        - Input variables: current recipe ID, mealLabel, activeRecipeIds, profile
        - Output variables: swap option list or updated plan
        - Validation approach: swap-path UI test plus backend candidate restrictions
        - What to show during demo: swap one breakfast and observe metric update

        ## M. Progress Tracking Decision Tree

        ```mermaid
        flowchart TD
            A[User opens progress screen] --> B[Load per-user logs and weekly reflections]
            B --> C{{Selected date is today?}}
            C -- No --> D[Block editing]
            C -- Yes --> E[Allow meal check-in, weight, notes, and feedback]
            E --> F[Persist locally in ReflectionStore/DataStore]
            F --> G{{Feedback upload fails?}}
            G -- Yes --> H[Queue feedback for retry]
            G -- No --> I[Mark feedback synced]
        ```

        - Source files used: `{ctx["citations"]["progress_vm"]}`, `{ctx["citations"]["reflection_store"]}`
        - Input variables: selected date, meal check-ins, reflections, feedback payload
        - Output variables: saved log state, queued feedback state
        - Validation approach: same-day logging test and offline feedback retry check
        - What to show during demo: today-only logging lock and local persistence after reopening the app
        """
    ).strip()
    write_text("docs/thesis_validation/04_DECISION_TREES.md", content)


def write_validation_cases(ctx: dict) -> None:
    cases = [
        ("TC-BMI-001", "Health Computation", "BMI normal calculation", "weight=60,height=165", "22.04 BMI, Normal", "Android BMI returns about 22.04 and category Normal", "HealthMetrics.bmi + bmiCategory", ctx["citations"]["android_bmi"], "HealthMetricsTest.bmi_isComputedCorrectly", "unit", "implemented", ""),
        ("TC-BMI-002", "Health Computation", "BMI invalid height/weight", "weight=0,height=160", "0.0 BMI and category —", "Android BMI returns 0.0 and category dash", "HealthMetrics.bmi + bmiCategory", ctx["citations"]["android_bmi"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-BMI-003", "Health Computation", "BMI category underweight", "weight=45,height=165", "16.53 BMI -> Underweight", "Android category branch returns Underweight", "HealthMetrics.bmiCategory", ctx["citations"]["android_bmi_category"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-BMI-004", "Health Computation", "BMI category overweight", "weight=75,height=165", "27.55 BMI -> Overweight", "Android category branch returns Overweight", "HealthMetrics.bmiCategory", ctx["citations"]["android_bmi_category"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-BMI-005", "Health Computation", "BMI category obese", "weight=90,height=165", "33.06 BMI -> Obese", "Android category branch returns Obese", "HealthMetrics.bmiCategory", ctx["citations"]["android_bmi_category"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-BMR-001", "Health Computation", "BMR calculation", "age=25,height=160,weight=65", "1364", "Android and backend compute 1364 before target adjustment", "Mifflin-St Jeor implementation", ctx["citations"]["android_bmr"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", "Backend uses same core formula inside solve_meal_plan."),
        ("TC-TDEE-001", "Health Computation", "TDEE Sedentary", "age=25,height=160,weight=65,activity=Sedentary", "Android round(1364*1.2)=1637; backend int(...)=1636", "Multiplier mapping works for Sedentary", "activityMultiplier + target calories", ctx["citations"]["android_activity_multiplier"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-TDEE-002", "Health Computation", "TDEE Lightly Active", "age=25,height=160,weight=65,activity=Lightly Active", f"Android {ctx['sample_tdee_android']}; backend {ctx['sample_tdee_backend']}", "Multiplier mapping works for Lightly Active", "activityMultiplier + target calories", ctx["citations"]["android_activity_multiplier"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-TDEE-003", "Health Computation", "TDEE Moderately Active", "age=25,height=160,weight=65,activity=Moderately Active", "Android round(1364*1.55)=2114; backend int(...)=2114", "Multiplier mapping works for Moderately Active", "activityMultiplier + target calories", ctx["citations"]["android_activity_multiplier"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-TDEE-004", "Health Computation", "TDEE Very Active", "age=25,height=160,weight=65,activity=Very Active", "Android round(1364*1.725)=2353; backend int(...)=2352", "Multiplier mapping works for Very Active", "activityMultiplier + target calories", ctx["citations"]["android_activity_multiplier"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-CAL-001", "Health Computation", "Daily calorie target for Weight Loss", "sample profile", f"Android {ctx['sample_target_android']}; backend {ctx['sample_target_backend']}", "Weight-loss deficit is applied", "HealthMetrics.targetCaloriesPerDay + solve_meal_plan", ctx["citations"]["android_target"], "HealthMetricsTest.targetCalories_weightLoss_usesDeficit", "unit", "implemented", ""),
        ("TC-CAL-002", "Health Computation", "Daily calorie target for Symptom Management", "same profile goal=Symptom Management", "No -500 deficit; backend symptom adjustments may further change target if symptoms exist", "Calorie adjustment branch stays neutral before symptom deltas", "goal adjustment branches", ctx["citations"]["android_target"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-CAL-003", "Health Computation", "Daily calorie target for General Health", "same profile goal=General Health", "No -500 deficit", "Neutral goal branch is used", "goal adjustment branches", ctx["citations"]["android_target"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-MACRO-001", "Health Computation", "Macro ratio for Mild", "insulinResistanceLevel=Mild", "(0.25,0.40,0.35)", "Backend macro_ratios returns default tuple", "macro_ratios", ctx["citations"]["macro_ratios"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-MACRO-002", "Health Computation", "Macro ratio for Moderate", "insulinResistanceLevel=Moderate", "(0.28,0.35,0.37)", "Backend macro_ratios returns moderate tuple", "macro_ratios", ctx["citations"]["macro_ratios"], "test_macro_ratios_moderate", "unit", "implemented", ""),
        ("TC-MACRO-003", "Health Computation", "Macro ratio for Severe", "insulinResistanceLevel=Severe", "(0.30,0.30,0.40)", "Backend macro_ratios returns severe tuple", "macro_ratios", ctx["citations"]["macro_ratios"], "test_macro_ratios_severe", "unit", "implemented", ""),
        ("TC-ALLERGY-001", "Safety Filter", "Fish allergy excludes bangus/tilapia/galunggong/salmon/tuna", "allergies=['fish']", "Recipes containing those descendant tokens are excluded", "Allergen family mapping", ctx["citations"]["derive_allergen_exposures"], "test_allergy_filter_blocks_descendant_ingredients", "unit", "implemented", ""),
        ("TC-ALLERGY-002", "Safety Filter", "Shellfish allergy excludes shrimp/crab", "allergies=['shellfish']", "Recipes with shrimp/crab family tokens are excluded", "Allergen family mapping", ctx["citations"]["derive_allergen_exposures"], "test_allergy_filter_blocks_descendant_ingredients", "unit", "implemented", ""),
        ("TC-ALLERGY-003", "Safety Filter", "Dairy allergy excludes milk/cheese/yogurt", "allergies=['dairy']", "Recipes with dairy-family tokens are excluded", "Allergen family mapping", ctx["citations"]["derive_allergen_exposures"], "test_allergy_filter_blocks_descendant_ingredients", "unit", "implemented", ""),
        ("TC-ALLERGY-004", "Safety Filter", "Egg allergy excludes egg recipes", "allergies=['egg']", "Recipes with egg-family tokens are excluded", "Allergen family mapping", ctx["citations"]["derive_allergen_exposures"], "test_allergy_filter_blocks_descendant_ingredients", "unit", "implemented", ""),
        ("TC-RESTR-001", "Safety Filter", "No Pork restriction excludes pork/baboy/liempo", "dietaryRestrictions=['No Pork']", "Recipes with normalized pork token are excluded", "restriction_failure_reasons", ctx["citations"]["restriction_failure_reasons"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", "Rule exists in production code, but no dedicated test was found."),
        ("TC-PANTRY-001", "Planner Scoring", "Pantry match count", f"sample pantry vs {ctx['sample_recipe_row']['recipe_id']}", f"Overlap count {ctx['sample_pantry_match']}", "Backend pantryMatch equals token overlap count", "normalize_pantry + _base_score", ctx["citations"]["base_score"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-BUDGET-001", "Planner Constraint", "Budget constraint", "weeklyBudgetPhp set lower than feasible cost", "No solution if all candidate combinations exceed budget", "Budget hard cap", ctx["citations"]["solve_meal_plan"], "test_solve_meal_plan_enforces_budget_as_hard_cap", "unit", "implemented", ""),
        ("TC-HOUSEHOLD-001", "Planner Constraint", "Household size scaling", "householdSize=1 vs 3", "Recipe cost and grocery quantity estimates increase monotonically", "estimate_cost + grocery scaling", ctx["citations"]["estimate_cost"], "test_shortlist_candidates_scales_cost_estimates_for_households; test_estimate_cost_scales_monotonically_with_household_size", "unit", "implemented", ""),
        ("TC-COOK-001", "Planner Constraint", "Max cooking time", "maxCookingTimeMinutes=20", "Recipes beyond 20 minutes are excluded in shortlist", "shortlist_candidates", ctx["citations"]["shortlist_candidates"], "NOT FOUND IN CURRENT DEV BRANCH", "unit", "recommended", ""),
        ("TC-REPEAT-001", "Planner Constraint", "Recipe repetition limit", "default policy repeat limits", "Per-attempt max-per-week cap is enforced", "adjust_max_per_week + solve_meal_plan", ctx["citations"]["adjust_max_per_week"] if "adjust_max_per_week" in ctx["citations"] else ctx["citations"]["solve_meal_plan"], "test_build_swap_candidates_blocks_current_recipe_and_repetition_overflow; test_build_swap_candidates_allows_repeats_up_to_policy_limit", "unit", "implemented", ""),
        ("TC-STRUCT-001", "Planner Contract", "7-day plan structure", "days=7", "Plan request is accepted; unsupported day counts are rejected", "solve_meal_plan precheck", ctx["citations"]["solve_meal_plan"], "NOT FOUND IN CURRENT DEV BRANCH", "integration", "recommended", ""),
        ("TC-STRUCT-002", "Planner Contract", "3 meals per day", "mealsPerDay=3", "Plan request is accepted; unsupported meal count is rejected", "solve_meal_plan precheck", ctx["citations"]["solve_meal_plan"], "NOT FOUND IN CURRENT DEV BRANCH", "integration", "recommended", ""),
        ("TC-NOSAFE-001", "Failure Handling", "No-safe-plan scenario", "budget or restriction makes solution infeasible", "Response status=no-safe-plan with reason codes and guidance", "build_no_safe_plan_response", ctx["citations"]["build_no_safe_plan_response"], "test_solve_meal_plan_returns_no_safe_plan_when_budget_makes_model_infeasible; test_no_safe_plan_response_contract", "integration", "implemented", ""),
        ("TC-GROCERY-001", "Output Generation", "Grocery list ingredient aggregation", "ingredients from sample plan", "Matching grocery names merge and prices sum", "buildGroceryListEntries", ctx["citations"]["android_grocery_aggregation"], "GroceryAggregationTest", "unit", "implemented", "Android test file contains aggregation coverage."),
        ("TC-SWAP-001", "Output Generation", "Meal swap safety", "swap candidate request with activeRecipeIds", "Current recipe, repeat overflow, restriction conflicts, and budget overflow are blocked", "build_swap_candidates", ctx["citations"]["backend_main_swap"], "test_build_swap_candidates_blocks_current_recipe_and_repetition_overflow; test_build_swap_candidates_respects_budget_and_restrictions", "unit", "implemented", ""),
        ("TC-OFFLINE-001", "Offline Reliability", "Offline saved plan availability", "saved local plan exists while backend unavailable", "Android loads saved plan from local artifact", "UserPreferencesRepository + MealPlanViewModel", ctx["citations"]["meal_plan_vm"], "NOT FOUND IN CURRENT DEV BRANCH", "manual", "recommended", ""),
        ("TC-PROFILE-001", "Input Validation", "Profile incomplete handling", "age/height/weight missing", "Android blocks generate-plan action", "validateGeneratePlanProfile", ctx["citations"]["meal_plan_repo"], "ProfileStepOneValidationUiTest", "ui", "implemented", ""),
        ("TC-SCHEMA-001", "API Contract", "API schema version handling", "mismatched schema header", "Client surfaces schema-mismatch error path; backend tests schema contract", "schema version header handling", ctx["citations"]["meal_plan_repo"], "test_schema_contract", "integration", "implemented", ""),
    ]
    rows = [
        {
            "test_case_id": case[0],
            "category": case[1],
            "purpose": case[2],
            "input_profile_or_data": case[3],
            "expected_manual_result": case[4],
            "expected_system_behavior": case[5],
            "source_formula_or_rule": case[6],
            "source_file": case[7],
            "actual_existing_test_if_any": case[8],
            "recommended_test_type": case[9],
            "status": case[10],
            "notes": case[11] if len(case) > 11 else "",
        }
        for case in cases
    ]
    write_csv(
        "docs/thesis_validation/05_VALIDATION_TEST_CASES.csv",
        rows,
        [
            "test_case_id",
            "category",
            "purpose",
            "input_profile_or_data",
            "expected_manual_result",
            "expected_system_behavior",
            "source_formula_or_rule",
            "source_file",
            "actual_existing_test_if_any",
            "recommended_test_type",
            "status",
            "notes",
        ],
    )


def write_sample_manual_computation(ctx: dict) -> None:
    sample_recipe = ctx["sample_recipe_row"]
    content = dedent(
        f"""
        # Sample Manual Computation

        ## Sample Profile

        - age: 25
        - height: 160 cm
        - weight: 65 kg
        - activityLevel: Lightly Active
        - goal: Weight Loss
        - insulinResistanceLevel: Moderate
        - weeklyBudgetPhp: 1500
        - householdSize: 1
        - maxCookingTimeMinutes: 45
        - pantryItems: egg, rice, tomato, onion
        - allergies: none
        - dietaryRestrictions: none

        ## 1. BMI

        - Formula source: `{ctx["citations"]["android_bmi"]}`
        - Formula: `BMI = weightKg / ((heightCm / 100)^2)`
        - Computation:
          - `heightM = 160 / 100 = 1.60`
          - `BMI = 65 / (1.60^2)`
          - `BMI = 65 / 2.56`
          - `BMI = {ctx["sample_bmi"]:.6f}`

        ## 2. BMI Category

        - Threshold source: `{ctx["citations"]["android_bmi_category"]}`
        - `{ctx["sample_bmi"]:.6f}` is `< 30` but `>= 25`, so the category is **{ctx["sample_bmi_category"]}**.

        ## 3. BMR

        - Formula source: Android `{ctx["citations"]["android_bmr"]}`, backend `{ctx["citations"]["solve_meal_plan"]}`
        - `BMR = (10 * 65) + (6.25 * 160) - (5 * 25) - 161`
        - `BMR = 650 + 1000 - 125 - 161`
        - `BMR = {ctx["sample_bmr"]}`

        ## 4. TDEE

        - Activity multiplier source: `{ctx["citations"]["android_activity_multiplier"]}`
        - `Lightly Active -> 1.375`
        - Android preview TDEE: `round(1364 * 1.375) = {ctx["sample_tdee_android"]}`
        - Backend planner TDEE: `int(1364 * 1.375) = {ctx["sample_tdee_backend"]}`

        ## 5. Daily Calorie Target

        - Goal source: `{ctx["citations"]["android_target"]}`
        - Weight Loss uses `-500 kcal/day`
        - Android preview target: `{ctx["sample_tdee_android"]} - 500 = {ctx["sample_target_android"]}`
        - Backend planner target: `{ctx["sample_tdee_backend"]} - 500 = {ctx["sample_target_backend"]}`
        - Thesis note: the backend target is the authoritative planner target; Android preview differs by 1 kcal here because Android uses `round()` while backend uses `int()`.

        ## 6. Macro Target

        - Source: `{ctx["citations"]["macro_ratios"]}` and `{ctx["citations"]["solve_meal_plan"]}`
        - Moderate insulin resistance ratios: protein `0.28`, carbs `0.35`, fats `0.37`
        - Protein target: `int(1375 * 0.28 / 4) = {ctx["sample_protein_backend"]} g`
        - Carbohydrate target: `int(1375 * 0.35 / 4) = {ctx["sample_carbs_backend"]} g`
        - Fat target: `int(1375 * 0.37 / 9) = {ctx["sample_fats_backend"]} g`
        - These macro targets are implemented in the backend planner, not in Android `HealthMetrics`.

        ## 7. Pantry Match For One Actual Recipe

        - Actual recipe used: `{sample_recipe["recipe_id"]}` `{sample_recipe["title"]}` from `backend/recipes.json`
        - Recipe nutrition: `{sample_recipe["calories"]} kcal`, `{sample_recipe["protein_grams"]} g protein`, `{sample_recipe["carbs_grams"]} g carbs`, `{sample_recipe["fats_grams"]} g fat`, `{sample_recipe["fiber_grams"]} g fiber`
        - Recipe ingredients:
          - talong roasted (1 medium)
          - egg (2 pieces)
          - onion chopped (2 tbsp)
          - tomato chopped (1/3 cup)
          - white rice cooked (2/3 cup)
        - Backend pantry normalization source: `{ctx["citations"]["normalize_pantry"]}`
        - Normalized pantry tokens: `{", ".join(ctx["normalized_pantry"])}`
        - Normalized recipe tokens include: `{", ".join(ctx["sample_ingredient_tokens"])}`
        - Overlap count: `{ctx["sample_pantry_match"]}`
        - Result: the backend planner would record a pantry match count of `{ctx["sample_pantry_match"]}` for this recipe.

        ## 8. Grocery Missing-Items Determination For The Same Recipe

        - Source: `{ctx["citations"]["android_grocery_screen_pantry"]}`
        - Important actual behavior: the Android grocery screen uses pantry-name matching, not the backend token-overlap algorithm.
        - With pantry entries entered exactly as `egg`, `rice`, `tomato`, and `onion`:
          - `egg` can exact-match a grocery item named `egg`
          - `rice` does **not** automatically exact-match `white rice cooked`
          - `tomato` does **not** automatically exact-match `tomato chopped`
          - `onion` does **not** automatically exact-match `onion chopped`
          - `talong roasted` remains uncovered
        - Therefore the UI may still show `white rice cooked`, `tomato chopped`, `onion chopped`, and `talong roasted` as still needing purchase unless the pantry entry names are more specific or the user manually checks them off.

        ## 9. Estimated Price For One Grocery Item

        - Source: `{ctx["citations"]["android_price_catalog"]}`
        - Android price rule: `egg -> PHP 7 per piece`
        - Example quantity: `2 pieces`
        - Estimated price: `7 * 2 = PHP {ctx["sample_egg_price"]}`

        ## 10. How The Planner Treats This Profile In Stage 1

        - Source: `{ctx["citations"]["shortlist_candidates"]}`, `{ctx["citations"]["base_score"]}`, `{ctx["citations"]["shadow_ml_score"]}`
        - The profile passes validation because it has valid age, height, weight, activity, goal, household size, cooking-time limit, and no conflicting restrictions.
        - Stage 1 removes recipes that violate allergies/restrictions or exceed 45 minutes.
        - For sample recipe `{sample_recipe["recipe_id"]}`, the backend estimated cost is `PHP {ctx["sample_recipe_cost_backend"]}`.
        - Deterministic base score:
          - `(protein * 2.0) - (cost * 0.05) - (abs(calories - 500) * 0.15) + (pantryMatch * 1.5) + stage1Boost`
          - `= {ctx["sample_base_score"]:.3f}`
        - Deterministic shadow ranking score for the same recipe: `{ctx["sample_shadow_score"]:.4f}`
        - After shadow-score weighting, the effective boost becomes about `{ctx["sample_stage1_boost_with_shadow"]:.3f}` and the revised base score becomes about `{ctx["sample_base_score_with_shadow"]:.3f}`.
        - ML remains assistive only here. Hard filters still run before any ranking effect.

        ## 11. How The Planner Treats This Profile In Stage 2

        - Source: `{ctx["citations"]["solve_meal_plan"]}`
        - The solver would create `21` meal slots (`7 days * 3 meals/day`).
        - It would use the backend daily calorie target of `{ctx["sample_target_backend"]}` kcal/day.
        - It would use macro targets of `{ctx["sample_protein_backend"]} g protein`, `{ctx["sample_carbs_backend"]} g carbs`, and `{ctx["sample_fats_backend"]} g fat`.
        - It would enforce hard constraints such as:
          - exactly one recipe per meal slot
          - no adjacent identical recipe
          - recipe-repeat cap for the current solve attempt
          - weekly budget cap of `PHP 1500`
        - It would then minimize soft deviations such as calorie error, macro error, fiber shortfall, sugar overage, repetition pressure, and preparation burden.

        ## 12. Actual Generated Output

        - Full local generation was **not executed** in this environment because importing `backend/services/meal_planner.py` is blocked here: `{ctx["solver_import_warning"] or "NOT APPLICABLE"}`
        - The actual response shape is implemented in:
          - backend `{cite("backend/domain/models.py", "class GeneratePlanResponse(BaseModel):")}`
          - Android `{cite("app/src/main/java/com/pcosina/app/data/api/PcosinaApiService.kt", "data class GeneratePlanResponse(")}`
        - Actual response fields include:
          - `weekLabel`
          - `days`
          - `status`
          - `message`
          - `explanation`
          - `requestId`
          - `planId`
          - `policyVersion`
          - `machineReasonCodes`
          - `humanGuidance`
          - `suggestedRelaxations`
          - `diagnosticsReference`
          - `timestamps`
        - Because execution was blocked, this chapter pack uses the real response contract and real recipe dataset as static evidence instead of fabricating a solver output.
        """
    ).strip()
    write_text("docs/thesis_validation/06_SAMPLE_MANUAL_COMPUTATION.md", content)


def write_chapter_tables(ctx: dict) -> None:
    ds = ctx["dataset_summary"]
    backend_test_count = len(ctx["backend_tests"])
    backend_test_files = len({row["source_file"] for row in ctx["backend_tests"]})
    android_test_rows = ctx["android_unit_tests"] + ctx["android_ui_tests"]
    android_test_count = len(android_test_rows)
    android_test_files = len({row["source_file"] for row in android_test_rows})
    meal_type_table = markdown_table(
        ["Meal Type", "Recipe Count"],
        [[meal_type, count] for meal_type, count in sorted(ds["meal_type_counts"].items())],
    )
    content = dedent(
        f"""
        # Chapter 4 Tables Ready

        ## Table 1: System Input Variables

        {markdown_table(
            ["Variable", "Actual Source", "Used For"],
            [
                ["age, heightCm, weightKg", ctx["citations"]["android_user_profile"], "BMI/BMR/TDEE and planner validation"],
                ["activityLevel", ctx["citations"]["android_user_profile"], "TDEE multiplier and calorie target"],
                ["goal", ctx["citations"]["android_user_profile"], "Calorie adjustment and planner strategy"],
                ["insulinResistanceLevel", ctx["citations"]["backend_user_profile"], "Backend macro ratio mapping"],
                ["allergies, dietaryRestrictions", ctx["citations"]["backend_user_profile"], "Safety filtering"],
                ["weeklyBudgetPhp / budgetWeekly / budgetMonthly", ctx["citations"]["resolve_budget_weekly"], "Budget cap and cost objective"],
                ["householdSize", ctx["citations"]["backend_user_profile"], "Cost scaling and grocery scaling"],
                ["maxCookingTimeMinutes", ctx["citations"]["backend_user_profile"], "Recipe filtering"],
                ["pantryItems", ctx["citations"]["android_user_profile"], "Pantry overlap scoring and grocery UI coverage"],
            ],
        )}

        ## Table 2: Health Computations Used by PCOSina

        {markdown_table(
            ["Computation", "Actual Formula Summary", "Source"],
            [
                ["BMI", "weight / (height_m^2)", ctx["citations"]["android_bmi"]],
                ["BMI category", "Underweight <18.5, Normal <25, Overweight <30, else Obese", ctx["citations"]["android_bmi_category"]],
                ["BMR", "(10*w) + (6.25*h) - (5*a) - 161", ctx["citations"]["android_bmr"]],
                ["TDEE", "BMR * activity multiplier", ctx["citations"]["android_target"]],
                ["Backend calorie target", "int(BMR*multiplier) +/- goal/symptom deltas, then clamp", ctx["citations"]["solve_meal_plan"]],
            ],
        )}

        ## Table 3: Activity Level Multipliers

        {markdown_table(
            ["Activity Level", "Multiplier", "Source"],
            [
                ["Sedentary", "1.2", ctx["citations"]["android_activity_multiplier"]],
                ["Lightly Active", "1.375", ctx["citations"]["android_activity_multiplier"]],
                ["Moderately Active", "1.55", ctx["citations"]["android_activity_multiplier"]],
                ["Very Active", "1.725", ctx["citations"]["android_activity_multiplier"]],
            ],
        )}

        ## Table 4: Goal-Based Calorie Adjustments

        {markdown_table(
            ["Goal", "Android Adjustment", "Backend Adjustment", "Source"],
            [
                ["Weight Loss", "-500", "-500", ctx["citations"]["android_target"]],
                ["Symptom Management", "0", "0 before symptom deltas", ctx["citations"]["symptom_adjustments"]],
                ["General Health", "0", "0 before symptom deltas", ctx["citations"]["android_target"]],
            ],
        )}

        ## Table 5: Insulin Resistance Macro Ratios

        {markdown_table(
            ["Insulin Resistance Level", "Protein", "Carbs", "Fats", "Source"],
            [
                ["Mild/default", "0.25", "0.40", "0.35", ctx["citations"]["macro_ratios"]],
                ["Moderate", "0.28", "0.35", "0.37", ctx["citations"]["macro_ratios"]],
                ["Severe", "0.30", "0.30", "0.40", ctx["citations"]["macro_ratios"]],
            ],
        )}

        ## Table 6: Sample Manual Computation

        {markdown_table(
            ["Metric", "Manual Result", "Source"],
            [
                ["BMI", f"{ctx['sample_bmi']:.6f}", ctx["citations"]["android_bmi"]],
                ["BMI Category", ctx["sample_bmi_category"], ctx["citations"]["android_bmi_category"]],
                ["BMR", ctx["sample_bmr"], ctx["citations"]["android_bmr"]],
                ["Android TDEE", ctx["sample_tdee_android"], ctx["citations"]["android_target"]],
                ["Backend TDEE", ctx["sample_tdee_backend"], ctx["citations"]["solve_meal_plan"]],
                ["Android calorie target", ctx["sample_target_android"], ctx["citations"]["android_target"]],
                ["Backend calorie target", ctx["sample_target_backend"], ctx["citations"]["solve_meal_plan"]],
                ["Backend protein target", f"{ctx['sample_protein_backend']} g", ctx["citations"]["solve_meal_plan"]],
                ["Backend carb target", f"{ctx['sample_carbs_backend']} g", ctx["citations"]["solve_meal_plan"]],
                ["Backend fat target", f"{ctx['sample_fats_backend']} g", ctx["citations"]["solve_meal_plan"]],
            ],
        )}

        ## Table 7: Manual vs System Output Validation

        {markdown_table(
            ["Metric", "Manual Result", "System Path", "Expected Match Rule"],
            [
                ["BMI", f"{ctx['sample_bmi']:.6f}", "Android HealthMetrics.bmi", "Exact or floating-point-equivalent match"],
                ["BMR", ctx["sample_bmr"], "Android and backend BMR formula", "Exact integer match"],
                ["TDEE", f"Android {ctx['sample_tdee_android']} / backend {ctx['sample_tdee_backend']}", "Different rounding rules", "Match the code path used"],
                ["Calorie target", f"Android {ctx['sample_target_android']} / backend {ctx['sample_target_backend']}", "Different rounding rules", "Match the code path used"],
                ["Macro targets", f"{ctx['sample_protein_backend']} / {ctx['sample_carbs_backend']} / {ctx['sample_fats_backend']}", "Backend only", "Exact integer match"],
            ],
        )}

        ## Table 8: Recipe Filtering Rules

        {markdown_table(
            ["Rule", "Actual Behavior", "Source"],
            [
                ["Allergy filter", "Exclude recipes with descendant allergen-family tokens", ctx["citations"]["derive_allergen_exposures"]],
                ["Diet restrictions", "Exclude recipes by No Pork/No Beef/Vegetarian/Pescatarian/Lactose Intolerant rules", ctx["citations"]["restriction_failure_reasons"]],
                ["Cooking time", "Exclude recipes beyond maxCookingTimeMinutes", ctx["citations"]["shortlist_candidates"]],
                ["Profile conflicts", "Reject conflicting restriction combinations before solving", ctx["citations"]["validate_profile"]],
            ],
        )}

        ## Table 9: Pantry Matching Rules

        {markdown_table(
            ["Layer", "Actual Rule", "Source"],
            [
                ["Backend planner", "Token overlap count influences Stage 1 score and Stage 2 soft reward", ctx["citations"]["base_score"]],
                ["Android grocery UI", "Pantry coverage uses pantry-name matching, not backend token-overlap count", ctx["citations"]["android_grocery_screen_pantry"]],
            ],
        )}

        ## Table 10: Grocery Price Estimation Rules

        {markdown_table(
            ["Layer", "Actual Rule", "Source"],
            [
                ["Android", "PriceCatalog rule scan + quantity factor + minimum PHP 5", ctx["citations"]["android_price_catalog"]],
                ["Backend", "estimate_price_detail + recipe total * 0.75, clamped 30..450", ctx["citations"]["backend_recipe_cost"]],
                ["Gap note", "Android and backend price catalogs are not identical", ctx["citations"]["backend_price_catalog"]],
            ],
        )}

        ## Table 11: Optimization Constraints

        {markdown_table(
            ["Constraint", "Hard/Soft", "Source"],
            [
                ["Exactly one recipe per meal slot", "Hard", ctx["citations"]["solve_meal_plan"]],
                ["No adjacent identical recipe", "Hard", ctx["citations"]["solve_meal_plan"]],
                ["Per-attempt reuse cap", "Hard", ctx["citations"]["solve_meal_plan"]],
                ["Weekly budget cap when budget exists", "Hard", ctx["citations"]["solve_meal_plan"]],
                ["Daily calorie deviation", "Soft", ctx["citations"]["solve_meal_plan"]],
                ["Daily macro deviation", "Soft", ctx["citations"]["solve_meal_plan"]],
                ["Fiber, sodium, sugar penalties", "Soft", ctx["citations"]["solve_meal_plan"]],
                ["Pantry and vegetable diversity rewards", "Soft", ctx["citations"]["solve_meal_plan"]],
            ],
        )}

        ## Table 12: No-Safe-Plan Conditions

        {markdown_table(
            ["Condition", "Actual Response Path", "Source"],
            [
                ["Conflicting profile/restrictions", "Return no-safe-plan with mapped reason codes", ctx["citations"]["build_no_safe_plan_response"]],
                ["No safe candidates after Stage 1", "Return no-safe-plan with diagnostics", ctx["citations"]["build_no_safe_plan_response"]],
                ["Solver infeasible/time-out", "Return no-safe-plan with solver metadata", ctx["citations"]["build_no_safe_plan_response"]],
            ],
        )}

        ## Table 13: Actual Recipe Dataset Summary

        - Total bundled recipes found in `backend/recipes.json`: `{ds["recipe_count"]}`
        - Average calories: `{ds["average_calories"]}`
        - Average protein: `{ds["average_protein"]}`
        - Average minutes: `{ds["average_minutes"]}`
        - Minimum calories: `{ds["min_calories"]}`
        - Maximum calories: `{ds["max_calories"]}`
        - Average ingredient count: `{ds["avg_ingredient_count"]}`

        {meal_type_table}

        ## Table 14: Actual Backend Test Coverage

        {markdown_table(
            ["Metric", "Actual Inventory Result"],
            [
                ["Backend test files discovered", backend_test_files],
                ["Backend test functions discovered", backend_test_count],
                ["Planner-specific files observed", "test_meal_planner.py, test_no_safe_plan_contract.py, test_planner_response_contract.py, test_worker_plan_jobs.py, test_policy_config.py, test_schema_contract.py"],
                ["Coverage percentage", "NOT FOUND IN CURRENT DEV BRANCH"],
            ],
        )}

        ## Table 15: Actual Android Test Coverage

        {markdown_table(
            ["Metric", "Actual Inventory Result"],
            [
                ["Android test files discovered", android_test_files],
                ["Android test methods discovered", android_test_count],
                ["Unit/instrumented split", f"{len(ctx['android_unit_tests'])} unit tests, {len(ctx['android_ui_tests'])} instrumented tests"],
                ["Coverage percentage", "NOT FOUND IN CURRENT DEV BRANCH"],
            ],
        )}

        ## Table 16: ISO 25010 Evaluation Instrument Mapping

        {markdown_table(
            ["ISO 25010 Area", "Actual System Feature To Evaluate"],
            [
                ["Functional suitability", "Profile-based meal plan generation, restrictions, grocery output, no-safe-plan handling"],
                ["Performance efficiency", "Plan-generation wait time and UI responsiveness"],
                ["Compatibility / offline support", "Saved plan, grocery, and progress availability without network"],
                ["Usability", "Navigation clarity, profile wizard, progress screen, grocery list readability"],
                ["Reliability", "No-safe-plan handling, cached-plan continuity, feedback queue retry"],
                ["Security / privacy", "Authentication path, privacy-safe logging, local encrypted reflection storage"],
                ["Maintainability (IT experts only)", "Separation of UI, repository, policy, planner, and ML guardrails"],
            ],
        )}

        ## Table 17: Expert Validation Instrument Mapping

        {markdown_table(
            ["Expert Area", "Actual Feature To Review"],
            [
                ["Nutrition appropriateness", "Calorie target explanation and backend macro targets"],
                ["PCOS relevance", "Goal/symptom criteria and Filipino meal dataset"],
                ["Allergy/restriction safety", "Allergen-family filtering and restriction exclusions"],
                ["Meal plan clarity", "Explainability fields and user-facing messages"],
                ["Grocery usefulness", "Aggregated ingredient list and estimated pricing"],
            ],
        )}

        ## Table 18: Recommended Statistical Treatment

        {markdown_table(
            ["Data Type", "Recommended Treatment", "Status"],
            [
                ["Computational validation pass/fail", "Frequency and percentage", "Recommended only"],
                ["ISO 25010 Likert responses", "Weighted mean, standard deviation, overall mean", "Recommended only"],
                ["Questionnaire reliability", "Cronbach's alpha if required", "Recommended only"],
                ["Expert instrument validity", "Content validity index if required", "Recommended only"],
                ["Multiple-expert agreement", "Inter-rater agreement if needed", "Recommended only"],
            ],
        )}
        """
    ).strip()
    write_text("docs/thesis_validation/07_CHAPTER_4_TABLES_READY.md", content)


def write_statistician_packet(ctx: dict) -> None:
    content = dedent(
        """
        # Statistician Consultation Packet

        ## Study Title

        Validation of PCOSina: An Offline-First Filipino-PCOS Meal Planning System

        ## System Purpose

        PCOSina is a wellness decision-support system for Filipino-PCOS meal planning. It is not a diagnosis engine or medical device. The current implementation combines deterministic rule-based filtering with deterministic OR-Tools CP-SAT optimization, with optional ML used only as an assistive ranking path.

        ## What Data The System Collects

        - Profile inputs such as age, height, weight, activity level, goal, insulin resistance level, allergies, dietary restrictions, budget, household size, pantry items, and cooking-time preference
        - Plan outputs and explanation metadata
        - Grocery snapshots and item sources
        - Progress logs, meal check-ins, reflections, and feedback queue entries when the user uses progress features

        ## What Outputs The System Generates

        - Weekly meal plan response
        - Meal-level recipe assignments
        - Plan explanation and diagnostics
        - Grocery list with estimated prices
        - No-safe-plan guidance when a safe plan cannot be produced
        - Saved local plan/history artifacts and progress summaries

        ## What Computations Are Validated Manually

        - BMI
        - BMI category
        - BMR
        - TDEE
        - Daily calorie target
        - Backend macro ratio and macro gram target
        - Pantry overlap count
        - Grocery price example

        ## What Outputs Are Evaluated By Users

        - Whether the system generates a plan from their profile
        - Whether the grocery list is understandable and useful
        - Whether saved plans remain accessible
        - Whether no-safe-plan feedback is clear
        - Whether the interface is easy to navigate

        ## What Outputs Are Evaluated By IT Experts

        - Functional suitability of actual implemented features
        - Reliability of offline-first behavior and failure handling
        - Performance efficiency and response handling
        - Security/privacy posture visible in the implemented system
        - Maintainability and architectural separation

        ## What Outputs Are Evaluated By Nutrition/Health Experts

        - Nutrition computation presentation
        - Calorie-target explanation
        - Macro-target explanation
        - PCOS relevance of rule set and meal criteria
        - Safety of allergy/restriction filtering
        - Practical usefulness of meal and grocery outputs

        ## Proposed Respondent Groups

        - End users or representative student evaluators for usability and functional suitability
        - IT experts for software-quality evaluation
        - Nutrition or health experts for wellness-appropriateness review

        ## Proposed Instruments

        - ISO 25010-based user/IT questionnaire adapted to actual implemented features
        - Nutrition/health expert validation form
        - Manual computation validation worksheet
        - Performance and technical observation sheet

        ## Proposed Likert Scale

        - 5 - Strongly Agree
        - 4 - Agree
        - 3 - Neutral
        - 2 - Disagree
        - 1 - Strongly Disagree

        ## Proposed Statistical Treatment

        These are recommendations only. This repository does not contain completed respondent datasets.

        - frequency
        - percentage
        - weighted mean
        - standard deviation
        - overall mean
        - Cronbach’s alpha if questionnaire reliability is required
        - content validity index if expert validation of instrument is required
        - inter-rater agreement if multiple experts rate meal quality

        ## Proposed Interpretation Scale

        This can be finalized with the statistician, but the common 5-point interpretation pattern is:

        - 4.21 to 5.00: Strongly Agree / Very High
        - 3.41 to 4.20: Agree / High
        - 2.61 to 3.40: Neutral / Moderate
        - 1.81 to 2.60: Disagree / Low
        - 1.00 to 1.80: Strongly Disagree / Very Low

        ## Required Distinction For Consultation

        A. Computational validation data:
           - manual formula results vs system results
           - pass/fail accuracy checks

        B. Software evaluation data:
           - ISO 25010 Likert ratings
           - functionality, reliability, usability, efficiency, security/privacy if applicable

        C. Expert validation data:
           - nutrition appropriateness
           - PCOS relevance
           - allergy/restriction safety
           - meal plan clarity
           - grocery usefulness

        D. System performance/technical data:
           - generation success/failure
           - response time if measurable
           - no-safe-plan cases
           - offline cache behavior

        ## Questions To Ask The Statistician

        - Is weighted mean sufficient for the planned ISO 25010 questionnaire, or should another summary measure be emphasized?
        - Should manual-computation validation be reported as frequency/percentage of matches, exact-difference tables, or both?
        - Is Cronbach’s alpha required for the questionnaire before deployment to respondents?
        - If multiple nutrition experts rate the same meal plans, should inter-rater agreement be computed?
        - What sample size is acceptable for user respondents, IT experts, and nutrition experts in the thesis context?
        - Should expert-form content validity be established before the main validation run?

        ## Tables/Statistical Worksheets Needed

        - Manual computation worksheet: manual result vs system result vs difference vs match flag
        - ISO 25010 response matrix per respondent
        - Expert validation response matrix per expert
        - Frequency table for pass/fail computational checks
        - Weighted mean and standard deviation table by ISO 25010 category
        - Overall summary table by respondent group
        """
    ).strip()
    write_text("docs/thesis_validation/08_STATISTICIAN_PACKET.md", content)


def write_iso_questionnaire() -> None:
    content = dedent(
        """
        # ISO 25010 Questionnaire Draft

        Scale:
        - 5 - Strongly Agree
        - 4 - Agree
        - 3 - Neutral
        - 2 - Disagree
        - 1 - Strongly Disagree

        ## A. Functional Suitability

        1. The system generates a meal plan based on the user’s profile.
        2. The system considers allergies and dietary restrictions when generating a meal plan.
        3. The system provides a grocery list based on the generated meal plan.
        4. The system provides understandable feedback when a safe meal plan cannot be generated.
        5. The system shows useful nutrition-related information for meal planning.

        ## B. Performance Efficiency

        1. The system responds within an acceptable time when generating a meal plan.
        2. The system remains responsive while loading saved plans and grocery data.
        3. The system updates plan or grocery information without unnecessary delay.

        ## C. Compatibility / Offline Support

        1. The system can still show saved meal-planning information when connectivity is limited.
        2. The system can still show saved grocery information when connectivity is limited.
        3. The system’s local-first behavior supports continuous use even when the backend is unavailable.

        ## D. Usability

        1. The system is easy to navigate.
        2. The system’s buttons and next actions are clear.
        3. The profile input steps are understandable.
        4. The meal plan output is easy to read.
        5. The grocery list is easy to understand.

        ## E. Reliability

        1. The system behaves consistently when the same valid inputs are used.
        2. The system handles incomplete or conflicting profiles clearly.
        3. The system preserves saved plans when they are available locally.
        4. The system provides clear guidance instead of failing silently when no safe plan can be produced.

        ## F. Security / Privacy

        1. The system appears to protect profile-related information during normal use.
        2. The system does not show unnecessary personal information in normal screens.
        3. The system’s handling of saved reflections or progress data appears appropriately private for a wellness application.

        ## G. Maintainability For IT Experts Only

        1. The implemented system shows clear separation between UI, data, and planner logic.
        2. The system’s deterministic fallback behavior is clear and appropriate.
        3. The code structure supports future maintenance and validation.
        4. The planner and API contract appear traceable enough for debugging and support.

        ## H. Overall Satisfaction

        1. Overall, the system is useful as a wellness decision-support application.
        2. Overall, I would recommend improvements to this system rather than a complete redesign.
        3. Overall, the current implementation is understandable enough for thesis demonstration and evaluation.
        """
    ).strip()
    write_text("docs/thesis_validation/09_ISO_25010_QUESTIONNAIRE_DRAFT.md", content)


def write_nutrition_form() -> None:
    content = dedent(
        """
        # Nutrition Expert Validation Form

        Disclaimer:
        PCOSina is a wellness decision-support system and not a diagnostic tool or medical device.

        Scale:
        - 5 - Strongly Agree
        - 4 - Agree
        - 3 - Neutral
        - 2 - Disagree
        - 1 - Strongly Disagree

        ## Rating Sections

        1. Accuracy of nutrition computation presentation
        2. Appropriateness of calorie target explanation
        3. Appropriateness of macro target explanation
        4. PCOS relevance of meal-planning criteria
        5. Safety of allergy and restriction filtering
        6. Clarity of no-safe-plan guidance
        7. Practicality of grocery list
        8. Filipino meal relevance
        9. Budget/pantry usefulness
        10. Limitations and recommendations

        ## Open-Ended Questions

        1. Are the meal recommendations appropriate as wellness decision support?
        2. Are the nutrition explanations understandable?
        3. What warnings/disclaimers should be added?
        4. What improvements are needed before clinical/medical use?
        """
    ).strip()
    write_text("docs/thesis_validation/10_NUTRITION_EXPERT_VALIDATION_FORM.md", content)


def write_chapter_guide() -> None:
    content = dedent(
        """
        # Chapter 5 And 6 Writing Guide

        ## Chapter 5

        Summary of computational validation findings:
        This section should report the manual-computation validation of the actual implemented formulas. Present BMI, BMR, TDEE, calorie target, macro target, pantry match, and price-estimation checks in tabular form. State how many computations matched the expected system result exactly or within acceptable floating-point tolerance. Do not claim clinical validation; keep the discussion limited to correctness of implemented computations.

        Summary of software evaluation findings:
        This section should summarize ISO 25010 questionnaire results after respondents have answered them. Discuss functional suitability, performance efficiency, compatibility/offline support, usability, reliability, and security/privacy only using collected respondent ratings. If maintainability is evaluated, separate IT-expert results from general-user results.

        Summary of expert validation findings:
        This section should summarize the nutrition/health expert review of the implemented meal-planning criteria, nutrition explanation, allergy/restriction safety, grocery usefulness, and Filipino meal relevance. Use actual expert comments and ratings only after they have been collected.

        Interpretation of ISO 25010 ratings:
        Interpret the weighted means using the approved interpretation scale from the statistician. Do not write final rating labels until the actual response data exists. At this stage, the manuscript can state that the tables are ready to receive respondent results.

        Discussion of system strengths:
        Focus on strengths that are directly supported by the codebase and validation evidence, such as offline-first local persistence, deterministic rule-based safety filtering, CP-SAT-based authoritative optimization, structured no-safe-plan guidance, and bundled Filipino recipe data.

        Discussion of system limitations:
        Explicitly discuss the current implementation gaps that affect thesis claims. These include the lack of a hard pantry-feasibility constraint, Android/backend price-catalog differences, Android/backend calorie-preview rounding differences, and the fact that local execution of OR-Tools-backed planner code may require an environment where OR-Tools can load correctly.

        ## Chapter 6

        Summary:
        The chapter summary should restate that PCOSina was validated as a wellness decision-support system using actual implemented formulas, actual rule-based planner logic, actual bundled datasets, actual tests, and planned respondent/expert evaluation instruments. Avoid any sentence that implies medical diagnosis, treatment prescription, or clinical effectiveness.

        Conclusions per objective:
        Write one conclusion per thesis objective. For computational objectives, conclude based on the manual-vs-system validation tables. For software-quality objectives, conclude only after ISO 25010 data has been collected. For expert-appropriateness objectives, conclude only after expert validation responses have been obtained.

        Recommendations:
        Recommendations should prioritize evidence gaps that matter for the defense: parity tests between Android preview and backend planner output, additional formula unit tests, expert validation completion, statistician review of instruments, and thesis wording corrections where implementation differs from expected terminology.

        Future work:
        Future work may include unifying price estimation across Android and backend, adding a true hard pantry-feasibility mode if desired, improving local execution portability for OR-Tools environments, expanding nutrition data coverage such as sodium and sugar in Android DTOs, and extending validation with larger respondent and expert samples.

        Limitations:
        State that respondent data, expert ratings, and finalized statistical analysis are not present in the repository at the time this evidence pack was generated. Also state that any production-like runtime metrics or live database override data were not treated as bundled source-of-truth evidence unless they existed directly in the repository.
        """
    ).strip()
    write_text("docs/thesis_validation/11_CHAPTER_5_6_WRITING_GUIDE.md", content)


def write_gaps(ctx: dict) -> None:
    rows = [
        ["GAP-001", "Thesis wording must not claim Android local persistence is Room/SQLite. Actual Android persistence uses Preferences DataStore plus encrypted ReflectionStore artifacts.", f"{ctx['citations']['user_prefs_repo']}; {ctx['citations']['reflection_store']}; {cite('ARCHITECTURE.md', 'DataStore')}", "high", "Revise Chapter 3/4 storage wording to DataStore + encrypted local artifacts on Android.", "Chapters 3, 4, 6"],
        ["GAP-002", "Thesis wording must not describe the authoritative planner as pure MILP. The implemented solver is OR-Tools CP-SAT, even though policy naming still includes `MilpWeights`.", f"{ctx['citations']['solve_meal_plan']}; {ctx['citations']['backend_main_generate_plan']}; {cite('backend/policy_config.py', 'class MilpWeights(BaseModel):')}", "high", "Revise thesis wording to deterministic rule filtering + OR-Tools CP-SAT optimization.", "Chapters 1, 3, 4, 6"],
        ["GAP-003", "Hard pantry-feasibility enforcement is NOT implemented in the current planner. Pantry is rewarded, not hard-constrained.", f"{ctx['citations']['base_score']}; {ctx['citations']['solve_meal_plan']}; {ctx['citations']['stage1_policy']}", "high", "Do not claim pantry feasibility as a hard planner constraint unless the code is changed later.", "Chapters 1, 4, 5, 6"],
        ["GAP-004", "Android and backend price catalogs are separate implementations and may produce different estimates.", f"{ctx['citations']['android_price_catalog']}; {ctx['citations']['backend_price_catalog']}", "high", "State clearly which price engine is used for which output and avoid claiming a single unified cost formula.", "Chapters 4, 5, 6"],
        ["GAP-005", "Android calorie preview and backend authoritative target can differ by 1 kcal because Android uses `round()` and backend uses `int()`.", f"{ctx['citations']['android_target']}; {ctx['citations']['solve_meal_plan']}", "medium", "Document Android preview vs backend authoritative target in validation tables.", "Chapters 4, 5"],
        ["GAP-006", "Android does not implement macro-target computation in `HealthMetrics`; macro ratios and macro grams are backend-only.", f"{ctx['citations']['android_target']}; {ctx['citations']['solve_meal_plan']}", "medium", "Avoid claiming Android independently computes displayed macro targets unless you add that feature later.", "Chapters 4, 5, 6"],
        ["GAP-007", "Android `RecipeDetailDto` does not include sodium or sugar fields even though backend recipe model supports them.", f"{cite('app/src/main/java/com/pcosina/app/data/api/RecipeDetailDto.kt', 'data class RecipeDetailDto(')}; {cite('backend/domain/models.py', 'class RecipeDetail(BaseModel):')}", "medium", "State that sodium/sugar planning penalties exist in backend logic, but Android DTO parity is incomplete.", "Chapters 4, 6"],
        ["GAP-008", "Full local execution of the planner was not possible in this environment because OR-Tools-backed import is blocked by application-control policy.", f"{cite('docs/thesis_validation/00_EVIDENCE_INDEX.md')}; {ctx['citations']['solve_meal_plan']}", "medium", "Use static source-backed evidence plus backend tests; run full local planner validation on a machine where OR-Tools loads correctly.", "Chapters 4, 5"],
        ["GAP-009", "Coverage percentages for backend and Android tests are NOT FOUND IN CURRENT DEV BRANCH. Only test inventories were available.", f"{cite('docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/backend_test_inventory.csv')}; {cite('docs/thesis_validation/03_ACTUAL_SYSTEM_DATA_EXPORTS/android_test_inventory.csv')}", "medium", "Do not report code-coverage percentages unless a coverage report is generated separately.", "Chapters 4, 5"],
        ["GAP-010", "Dedicated Android parity tests for BMR/TDEE/macros were not found.", f"{cite('app/src/test/java/com/pcosina/app')} ; {cite('app/src/main/java/com/pcosina/app/domain/HealthMetrics.kt', 'object HealthMetrics')}", "medium", "Add unit tests covering BMR, all activity multipliers, BMI category boundaries, and Android/backend parity notes.", "Chapters 4, 6"],
        ["GAP-011", "Dedicated unit tests for the refined grocery pantry-name matching edge case were not found.", f"{ctx['citations']['android_grocery_screen_pantry']}", "medium", "Add tests for single-token pantry entries versus multi-token grocery names.", "Chapters 4, 6"],
        ["GAP-012", "The feedback auto-tuning path in MealPlanViewModel is counterintuitive: `Too expensive` lowers the budget and `Too hard to cook` lowers max cooking time.", f"{cite('app/src/main/java/com/pcosina/app/ui/MealPlanViewModel.kt', 'private fun applyFeedbackTuning')}", "medium", "Review or document this behavior before presenting it as user-beneficial adaptation.", "Chapters 5, 6"],
        ["GAP-013", "Respondent ISO 25010 data is not present in the repository.", cite("docs/thesis_validation/09_ISO_25010_QUESTIONNAIRE_DRAFT.md"), "high", "Collect respondent data before writing final evaluation results.", "Chapter 5"],
        ["GAP-014", "Nutrition expert validation data is not present in the repository.", cite("docs/thesis_validation/10_NUTRITION_EXPERT_VALIDATION_FORM.md"), "high", "Conduct expert validation and record actual ratings/comments.", "Chapter 5"],
        ["GAP-015", "Statistician-reviewed instrument and statistical-treatment approval are not present in the repository.", cite("docs/thesis_validation/08_STATISTICIAN_PACKET.md"), "high", "Consult the statistician and finalize the analysis plan before defense.", "Chapters 5, 6"],
        ["GAP-016", "Final demo screenshots or defense-ready runtime artifacts are not bundled in this evidence pack.", cite("docs/thesis_validation/13_GENERATED_ARTIFACTS_SUMMARY.md"), "low", "Capture screenshots of profile input, successful plan, no-safe-plan, grocery list, and progress screen.", "Chapters 4, 5"],
        ["GAP-017", "Any thesis claim that PCOSina diagnoses PCOS or provides clinical treatment guidance would be unsupported.", f"{cite('README.md', 'wellness decision-support')}; {cite('PRIVACY_AND_SECURITY.md', 'not a medical device')}", "high", "Keep all thesis wording within wellness decision-support scope.", "Chapters 1, 5, 6"],
        ["GAP-018", "Generic `No Seafood` or `No Fish` recipe exclusion rules were not found outside profile-conflict validation logic.", f"{ctx['citations']['validate_profile']}; {ctx['citations']['restriction_failure_reasons']}", "medium", "Do not claim a full recipe-level `No Seafood` or `No Fish` filter unless you implement and test it.", "Chapters 4, 6"],
    ]
    content = "# Implementation Gaps And Action Items\n\n" + markdown_table(
        ["gap_id", "description", "evidence/source file", "severity", "recommended action", "chapter affected"],
        rows,
    )
    write_text("docs/thesis_validation/12_IMPLEMENTATION_GAPS_AND_ACTION_ITEMS.md", content)


def write_artifacts_summary() -> None:
    rows = [
        ["00_EVIDENCE_INDEX.md", "Master index of inspected sources and trust levels.", "Use to justify every evidence source cited in Chapter 4.", "Use as audit trail for what evidence supports findings.", "Use to explain scope limits of conclusions.", "Use as consultation handout index.", "Use as defense navigation sheet."],
        ["01_SYSTEM_DATA_DICTIONARY.csv", "Actual field inventory across Android, backend, API, and policy models.", "Convert to input-variable appendix and data-field tables.", "Supports discussion of validated inputs/outputs.", "Supports conclusion statements about implemented scope.", "Helps statistician understand variable structure.", "Useful when answering panel questions about inputs and outputs."],
        ["02_FORMULAS_AND_COMPUTATIONS.md", "Actual implemented formulas and pseudocode.", "Core Chapter 4 computation evidence.", "Supports computational-validation discussion.", "Supports conclusion on formula correctness.", "Helps statistician separate computational validation from survey data.", "Useful for manual-computation defense."],
        ["03_ACTUAL_SYSTEM_DATA_EXPORTS/", "CSV/JSON exports of recipes, rules, policies, tests, endpoints, and local artifacts.", "Use as source tables and appendices.", "Supports detailed evidence citations.", "Supports evidence-based conclusions.", "Provides raw material for worksheets.", "Useful for showing the system uses actual repo data."],
        ["04_DECISION_TREES.md", "Rule-based decision-flow diagrams.", "Use for Chapter 4 process diagrams.", "Supports explanation of behavior during evaluation.", "Supports recommendations about missing branches or gaps.", "Useful for consultation on what should be measured.", "Useful for explaining system flows during demo."],
        ["05_VALIDATION_TEST_CASES.csv", "Thesis validation case inventory mapped to actual rules and tests.", "Use as manual/unit/integration validation matrix.", "Supports Chapter 5 findings organization.", "Supports recommendations on missing tests.", "Helps statistician define pass/fail worksheets.", "Useful when asked how the system was validated."],
        ["06_SAMPLE_MANUAL_COMPUTATION.md", "Worked example using one realistic sample profile.", "Can be pasted directly into Chapter 4 manual validation section.", "Supports findings narrative on computational correctness.", "Supports conclusion about implemented math paths.", "Useful for explaining numeric validation scope.", "Ideal for live walk-through with panelists."],
        ["07_CHAPTER_4_TABLES_READY.md", "Chapter 4-ready tables based on actual repo evidence.", "Direct source for Chapter 4 tables.", "Supports structured discussion of results later.", "Provides summary tables for conclusions.", "Useful for showing proposed data tables.", "Good reference for fast defense answers."],
        ["08_STATISTICIAN_PACKET.md", "Consultation-ready statistical planning brief.", "Not a Chapter 4 table, but supports methodology validation.", "Defines how Chapter 5 should separate data types.", "Supports Chapter 6 recommendations.", "Primary file for statistician meeting.", "Useful to explain why survey results are not yet claimed."],
        ["09_ISO_25010_QUESTIONNAIRE_DRAFT.md", "Actual-feature-aligned software evaluation questionnaire.", "Can be attached as Chapter 4 or appendix instrument.", "Used once software-evaluation data is collected.", "Supports recommendations on future validation.", "Helps statistician review instrument design.", "Useful to show panel the planned survey instrument."],
        ["10_NUTRITION_EXPERT_VALIDATION_FORM.md", "Nutrition/health expert review instrument.", "Can be attached as Chapter 4 or appendix instrument.", "Used once expert data is collected.", "Supports recommendations on expert validation.", "Helps statistician review expert-rating structure.", "Useful to show clinical-scope safeguards."],
        ["11_CHAPTER_5_6_WRITING_GUIDE.md", "Draft-ready writing guidance without fabricated values.", "Indirect support only.", "Primary drafting guide for Chapter 5.", "Primary drafting guide for Chapter 6.", "Clarifies which sections need actual collected data.", "Useful when preparing defense script."],
        ["12_IMPLEMENTATION_GAPS_AND_ACTION_ITEMS.md", "Thesis-relevant implementation gaps and claim risks.", "Use to avoid unsupported statements in Chapter 4.", "Use in Chapter 5 limitations.", "Use in Chapter 6 recommendations and future work.", "Highlights what is still missing before analysis.", "Useful for transparent defense responses."],
        ["13_GENERATED_ARTIFACTS_SUMMARY.md", "How each generated file should be used.", "Quick reference for Chapter 4 assembly.", "Quick reference for Chapter 5 assembly.", "Quick reference for Chapter 6 assembly.", "Quick reference for consultations.", "Quick reference before defense."],
        ["scripts/extract_validation_data.py", "Safe local generator for this pack.", "Rebuilds the evidence pack from current dev sources.", "Can be rerun after code changes before evaluation.", "Can be rerun before final conclusions.", "Lets statistician confirm raw source extraction.", "Useful for reproducibility questions."],
        ["scripts/run_validation_checks.md", "Commands to rerun tests and extraction.", "Use to document validation procedure.", "Use when repeating validation before Chapter 5 writing.", "Use before final summary updates.", "Useful in consultation follow-up.", "Useful when panel asks how to reproduce evidence."],
    ]
    write_text(
        "docs/thesis_validation/13_GENERATED_ARTIFACTS_SUMMARY.md",
        "# Generated Artifacts Summary\n\n" + markdown_table(
            ["file name", "purpose", "how to use in Chapter 4", "how to use in Chapter 5", "how to use in Chapter 6", "how to use in statistician consultation", "how to use in demo/defense"],
            rows,
        ),
    )


def write_run_validation_checks(ctx: dict) -> None:
    content = dedent(
        f"""
        # Run Validation Checks

        Run from repository root on the `dev` branch.

        ## 1. Regenerate This Evidence Pack

        ```powershell
        python docs/thesis_validation/scripts/extract_validation_data.py
        ```

        ## 2. Backend Tests Most Relevant To Planner And Validation

        ```powershell
        python -m pytest backend/tests/test_policy_config.py -q
        python -m pytest backend/tests/test_schema_contract.py -q
        python -m pytest backend/tests/test_planner_response_contract.py -q
        python -m pytest backend/tests/test_no_safe_plan_contract.py -q
        python -m pytest backend/tests/test_worker_plan_jobs.py -q
        python -m pytest backend/tests/test_meal_planner.py -q
        ```

        ## 3. Android Unit And Instrumented Tests

        ```powershell
        .\\gradlew testDebugUnitTest
        .\\gradlew connectedDebugAndroidTest
        ```

        ## 4. Current Environment Warning

        In the environment used to generate this pack, importing `backend/services/meal_planner.py` was blocked:

        ```text
        {ctx["solver_import_warning"] or "No import warning recorded."}
        ```

        If that same application-control block exists on the validation machine, planner tests that import OR-Tools-backed code may fail before test execution. In that case, run the full planner tests on a machine where OR-Tools can load correctly.
        """
    ).strip()
    write_text("docs/thesis_validation/scripts/run_validation_checks.md", content)


def main() -> None:
    ensure_dir(THESIS_DIR)
    ensure_dir(EXPORT_DIR)
    ensure_dir(SCRIPTS_DIR)

    ctx = build_context()

    write_exports(ctx)
    write_evidence_index(ctx)
    write_data_dictionary(ctx)
    write_formulas(ctx)
    write_decision_trees(ctx)
    write_validation_cases(ctx)
    write_sample_manual_computation(ctx)
    write_chapter_tables(ctx)
    write_statistician_packet(ctx)
    write_iso_questionnaire()
    write_nutrition_form()
    write_chapter_guide()
    write_gaps(ctx)
    write_artifacts_summary()
    write_run_validation_checks(ctx)

    print("Generated thesis validation evidence pack in docs/thesis_validation/")
    for warning in ctx["warnings"]:
        print(f"WARNING: {warning}")


if __name__ == "__main__":
    main()
