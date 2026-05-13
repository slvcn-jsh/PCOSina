import time
import csv
import json
import os
import math
from statistics import median
from domain.models import GeneratePlanRequest, UserProfile
from services.meal_planner import solve_meal_plan
import database

PROFILES = [
    UserProfile(displayName="Base", dietaryRestrictions=[]),
    UserProfile(displayName="Veg", dietaryRestrictions=["Vegetarian"]),
    UserProfile(displayName="Pesc", dietaryRestrictions=["Pescatarian"]),
    UserProfile(displayName="NoPork", dietaryRestrictions=["No Pork"]),
    UserProfile(displayName="NoBeef", dietaryRestrictions=["No Beef"]),
    UserProfile(displayName="Lactose", dietaryRestrictions=["Lactose Intolerant"]),
    UserProfile(displayName="VegLactose", dietaryRestrictions=["Vegetarian", "Lactose Intolerant"]),
    UserProfile(displayName="BudgetLow", dietaryRestrictions=["No Pork"], budgetWeekly=800),
    UserProfile(displayName="Pantry", dietaryRestrictions=["No Beef"], pantryItems=["chicken", "egg", "garlic", "tomato"]),
]

DEFAULT_WEIGHT_SETS = [
    {"repeat_weight": 5, "group_weight": 2, "diversity_weight": 1, "pantry_weight": 1},
    {"repeat_weight": 3, "group_weight": 1, "diversity_weight": 2, "pantry_weight": 1},
    {"repeat_weight": 6, "group_weight": 3, "diversity_weight": 2, "pantry_weight": 1},
]

def load_weight_sets():
    if os.path.exists("weights_grid.json"):
        try:
            with open("weights_grid.json", "r") as f:
                data = json.load(f)
            if isinstance(data, list) and data:
                return data
        except Exception:
            pass
    if os.getenv("PCOSINA_WEIGHT_GRID", "").lower() == "true":
        return generate_weight_grid()
    return DEFAULT_WEIGHT_SETS

def generate_weight_grid():
    repeat = [3, 5, 7]
    group = [1, 2, 3]
    diversity = [1, 2]
    pantry = [1, 2]
    grid = []
    for r in repeat:
        for g in group:
            for d in diversity:
                for p in pantry:
                    grid.append({
                        "repeat_weight": r,
                        "group_weight": g,
                        "diversity_weight": d,
                        "pantry_weight": p,
                    })
    return grid

def save_env_weights(best_set):
    env_path = ".env"
    line = f'PCOSINA_MILP_WEIGHTS={json.dumps(best_set)}'
    if not os.path.exists(env_path):
        with open(env_path, "w") as f:
            f.write(line + "\n")
        print("Saved .env with PCOSINA_MILP_WEIGHTS")
        return
    with open(env_path, "r") as f:
        lines = f.readlines()
    found = False
    for i, l in enumerate(lines):
        if l.startswith("PCOSINA_MILP_WEIGHTS="):
            lines[i] = line + "\n"
            found = True
            break
    if not found:
        lines.append(line + "\n")
    with open(env_path, "w") as f:
        f.writelines(lines)
    print("Updated .env with PCOSINA_MILP_WEIGHTS")

def run(weight_set=None, write_files=True):
    database.init_db()
    database.seed_recipes()
    recipes = database.get_all_recipes()
    times = []
    rows = []
    feasible = 0
    total = 0
    quality_rows = []
    for profile in PROFILES:
        for trial in range(5):
            req = GeneratePlanRequest(profile=profile, days=7)
            start = time.perf_counter()
            result, msg = solve_meal_plan(req, recipes, weight_set=weight_set)
            elapsed = time.perf_counter() - start
            times.append(elapsed)
            total += 1
            if result:
                feasible += 1
                # Quality metrics: macro deviation and budget deviation (approx)
                day_targets = []
                w, h, a = (profile.weightKg if profile.weightKg > 0 else 65, profile.heightCm if profile.heightCm > 0 else 160, profile.age if profile.age > 0 else 25)
                bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
                target = int(bmr * 1.375)
                if "Weight Loss" in profile.goal: target -= 500
                target = max(1200, target)
                target_protein = int((target * 0.25) / 4)
                target_carbs = int((target * 0.40) / 4)
                target_fats = int((target * 0.35) / 9)
                for _ in range(len(result)):
                    day_targets.append((target, target_protein, target_carbs, target_fats))
                macro_dev = 0
                pro_dev = 0
                carb_dev = 0
                fat_dev = 0
                unique_recipes = set()
                unique_ingredients = set()
                total_cost = 0
                for day, t in zip(result, day_targets):
                    tcal, tpro, tcarb, tfat = t
                    cals = 0
                    pro = 0
                    carb = 0
                    fat = 0
                    for m in day.meals:
                        recipe = next((r for r in recipes if r["id"] == m.recipeId), None)
                        if recipe:
                            cals += int(recipe.get("calories", 0))
                            pro += int(recipe.get("proteinGrams", 0))
                            carb += int(recipe.get("carbsGrams", 0))
                            fat += int(recipe.get("fatsGrams", 0))
                            total_cost += int(recipe.get("ingredients", []) and (len(recipe.get("ingredients", [])) * 8 + (recipe.get("calories", 0) * 0.4)) or 0)
                            unique_recipes.add(recipe["id"])
                            for ing in recipe.get("ingredients", []):
                                name = ing.get("name") if isinstance(ing, dict) else str(ing)
                                if name:
                                    unique_ingredients.add(name.lower())
                    d_pro = abs(pro - tpro)
                    d_carb = abs(carb - tcarb)
                    d_fat = abs(fat - tfat)
                    pro_dev += d_pro
                    carb_dev += d_carb
                    fat_dev += d_fat
                    macro_dev += abs(cals - tcal) + d_pro + d_carb + d_fat
                budget_weekly = None
                if profile.budgetWeekly and profile.budgetWeekly > 0:
                    budget_weekly = float(profile.budgetWeekly)
                elif profile.budgetMonthly and profile.budgetMonthly > 0:
                    budget_weekly = float(profile.budgetMonthly) / 4.33
                budget_dev = abs(total_cost - budget_weekly) if budget_weekly else 0
                # Normalize quality (lower deviation = higher score)
                max_macro = max(1, len(result) * 4 * 400)
                macro_score = max(0, 100 - (macro_dev / max_macro) * 100)
                budget_score = 100
                if budget_weekly:
                    budget_score = max(0, 100 - (budget_dev / max(1, budget_weekly)) * 100)
                variety_score = min(100, (len(unique_recipes) / max(1, len(result) * 3)) * 100)
                ingredient_diversity = min(100, (len(unique_ingredients) / max(1, len(result) * 3 * 4)) * 100)
                quality_index = round((macro_score * 0.4) + (budget_score * 0.2) + (variety_score * 0.2) + (ingredient_diversity * 0.1) + (float(100 - (macro_dev / max(1, len(result) * 4 * 400)) * 100) * 0.1), 2)
                quality_rows.append({
                    "profile": profile.displayName,
                    "trial": trial,
                    "macro_dev": round(macro_dev, 2),
                    "budget_dev": round(budget_dev, 2),
                    "macro_score": round(macro_score, 2),
                    "budget_score": round(budget_score, 2),
                    "variety_score": round(variety_score, 2),
                    "ingredient_diversity": round(ingredient_diversity, 2),
                    "protein_dev": round(pro_dev, 2),
                    "carb_dev": round(carb_dev, 2),
                    "fat_dev": round(fat_dev, 2),
                    "daily_compliance": round(100 - (macro_dev / max(1, len(result) * 4 * 400)) * 100, 2),
                    "quality_index": quality_index,
                })
            rows.append({
                "profile": profile.displayName,
                "trial": trial,
                "feasible": bool(result),
                "seconds": round(elapsed, 4),
            })
    feasible_rate = (feasible / total) * 100 if total else 0
    p95 = None
    if len(times) >= 10:
        sorted_times = sorted(times)
        p95 = sorted_times[int(len(sorted_times) * 0.95) - 1]
    if write_files:
        print(f"Feasible: {feasible}/{total} ({feasible_rate:.1f}%)")
        print(f"Median solve: {median(times):.2f}s")
        print(f"Max solve: {max(times):.2f}s")
        if p95 is not None:
            print(f"P95 solve: {p95:.2f}s")
        with open("benchmark_results.csv", "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["profile", "trial", "feasible", "seconds"])
            writer.writeheader()
            writer.writerows(rows)
        print("Saved benchmark_results.csv")
        if quality_rows:
            with open("benchmark_quality.csv", "w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=["profile", "trial", "macro_dev", "budget_dev", "macro_score", "budget_score", "variety_score", "ingredient_diversity", "protein_dev", "carb_dev", "fat_dev", "daily_compliance", "quality_index"])
                writer.writeheader()
                writer.writerows(quality_rows)
            print("Saved benchmark_quality.csv")
            by_profile = {}
            for row in quality_rows:
                name = row["profile"]
            by_profile.setdefault(name, {"macro": [], "budget": [], "variety": [], "ing": [], "daily": [], "qindex": []})
            by_profile[name]["macro"].append(row["macro_score"])
            by_profile[name]["budget"].append(row["budget_score"])
            by_profile[name]["variety"].append(row["variety_score"])
            by_profile[name]["ing"].append(row["ingredient_diversity"])
            by_profile[name]["daily"].append(row["daily_compliance"])
            by_profile[name]["qindex"].append(row["quality_index"])
            with open("benchmark_quality_summary.csv", "w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=["profile", "macro_score_avg", "macro_score_sd", "budget_score_avg", "budget_score_sd", "variety_score_avg", "variety_score_sd", "ingredient_diversity_avg", "ingredient_diversity_sd", "daily_compliance_avg", "daily_compliance_sd", "quality_index_avg", "quality_index_sd"])
                writer.writeheader()
                for name, data in by_profile.items():
                    def _sd(vals):
                        if len(vals) < 2:
                            return 0
                        m = sum(vals) / len(vals)
                        return (sum((v - m) ** 2 for v in vals) / (len(vals) - 1)) ** 0.5
                    writer.writerow({
                        "profile": name,
                        "macro_score_avg": round(sum(data["macro"]) / max(1, len(data["macro"])), 2),
                        "macro_score_sd": round(_sd(data["macro"]), 2),
                        "budget_score_avg": round(sum(data["budget"]) / max(1, len(data["budget"])), 2),
                        "budget_score_sd": round(_sd(data["budget"]), 2),
                        "variety_score_avg": round(sum(data["variety"]) / max(1, len(data["variety"])), 2),
                        "variety_score_sd": round(_sd(data["variety"]), 2),
                        "ingredient_diversity_avg": round(sum(data["ing"]) / max(1, len(data["ing"])), 2),
                        "ingredient_diversity_sd": round(_sd(data["ing"]), 2),
                        "daily_compliance_avg": round(sum(data["daily"]) / max(1, len(data["daily"])), 2),
                        "daily_compliance_sd": round(_sd(data["daily"]), 2),
                        "quality_index_avg": round(sum(data["qindex"]) / max(1, len(data["qindex"])), 2),
                        "quality_index_sd": round(_sd(data["qindex"]), 2),
                    })
            print("Saved benchmark_quality_summary.csv")
        with open("benchmark_summary.csv", "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["feasible_rate", "median_seconds", "max_seconds", "p95_seconds"])
            writer.writeheader()
            writer.writerow({
                "feasible_rate": round(feasible_rate, 2),
                "median_seconds": round(median(times), 4),
                "max_seconds": round(max(times), 4),
                "p95_seconds": round(p95, 4) if p95 is not None else "",
            })
        print("Saved benchmark_summary.csv")
    per_profile = {}
    profile_meta = {}
    for row in rows:
        name = row["profile"]
        per_profile.setdefault(name, {"total": 0, "feasible": 0, "times": []})
        per_profile[name]["total"] += 1
        if row["feasible"]:
            per_profile[name]["feasible"] += 1
        per_profile[name]["times"].append(row["seconds"])
    for p in PROFILES:
        profile_meta[p.displayName] = {
            "restrictions": p.dietaryRestrictions,
            "budgetWeekly": getattr(p, "budgetWeekly", None),
            "budgetMonthly": getattr(p, "budgetMonthly", None),
        }
    if write_files:
        with open("benchmark_profiles.csv", "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["profile", "feasible_rate", "median_seconds", "max_seconds"])
            writer.writeheader()
            for name, data in per_profile.items():
                rate = (data["feasible"] / data["total"]) * 100 if data["total"] else 0
                writer.writerow({
                    "profile": name,
                    "feasible_rate": round(rate, 2),
                    "median_seconds": round(median(data["times"]), 4),
                    "max_seconds": round(max(data["times"]), 4),
                })
        print("Saved benchmark_profiles.csv")
    weak_profiles = []
    for name, data in per_profile.items():
        rate = (data["feasible"] / data["total"]) * 100 if data["total"] else 0
        if rate < 90:
            weak_profiles.append((name, rate))
    if write_files and weak_profiles:
        weak_profiles.sort(key=lambda x: x[1])
        print("Low feasibility profiles (<90%):")
        for name, rate in weak_profiles:
            meta = profile_meta.get(name, {})
            restrictions = meta.get("restrictions") or []
            budget = meta.get("budgetWeekly") or meta.get("budgetMonthly")
            suggestion = "Increase macro tolerance or expand shortlist size."
            if budget:
                suggestion = "Relax budget by 10–20% or allow higher cost penalty."
            if "Vegetarian" in restrictions and "Lactose Intolerant" in restrictions:
                suggestion = "Relax macro bounds or allow repeats up to 4/week."
            print(f"- {name}: {rate:.1f}% | {restrictions} | Suggestion: {suggestion}")

    return {
        "feasible_rate": feasible_rate,
        "median_seconds": median(times),
        "max_seconds": max(times),
        "p95_seconds": p95 if p95 is not None else 0,
        "per_profile": per_profile,
        "times": times,
    }

if __name__ == "__main__":
    best = None
    best_set = None
    rankings = []
    per_profile_best = {}
    for ws in load_weight_sets():
        print(f"\n== Tuning weights {ws} ==")
        result = run(weight_set=ws, write_files=False)
        score = result["feasible_rate"] - (result["median_seconds"] * 5)
        rankings.append({
            "weights": ws,
            "feasible_rate": result["feasible_rate"],
            "median_seconds": result["median_seconds"],
            "score": score,
        })
        for name, data in result["per_profile"].items():
            rate = (data["feasible"] / data["total"]) * 100 if data["total"] else 0
            med = median(data["times"]) if data["times"] else 0
            current = per_profile_best.get(name)
            if current is None or (rate > current["feasible_rate"]) or (rate == current["feasible_rate"] and med < current["median_seconds"]):
                per_profile_best[name] = {
                    "weights": ws,
                    "feasible_rate": rate,
                    "median_seconds": med,
                }
        if best is None or score > best:
            best = score
            best_set = ws
    print(f"\nBest weight set: {best_set}")
    if best_set:
        with open("best_weights.json", "w") as f:
            json.dump(best_set, f)
        print("Saved best_weights.json (copy into PCOSINA_MILP_WEIGHTS env)")
        save_env_weights(best_set)
        best_result = run(weight_set=best_set, write_files=True)
    rankings.sort(key=lambda x: x["score"], reverse=True)
    with open("weights_rankings.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["weights", "feasible_rate", "median_seconds", "score"])
        writer.writeheader()
        for r in rankings:
            writer.writerow({
                "weights": json.dumps(r["weights"]),
                "feasible_rate": round(r["feasible_rate"], 2),
                "median_seconds": round(r["median_seconds"], 4),
                "score": round(r["score"], 4),
            })
    print("Saved weights_rankings.csv")
    with open("weights_best_by_profile.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["profile", "weights", "feasible_rate", "median_seconds"])
        writer.writeheader()
        for name, data in per_profile_best.items():
            writer.writerow({
                "profile": name,
                "weights": json.dumps(data["weights"]),
                "feasible_rate": round(data["feasible_rate"], 2),
                "median_seconds": round(data["median_seconds"], 4),
            })
    print("Saved weights_best_by_profile.csv")
    if best_set and best_result:
        times = best_result.get("times", [])
        if times:
            mean = sum(times) / len(times)
            var = sum((t - mean) ** 2 for t in times) / max(1, len(times) - 1)
            sd = math.sqrt(var)
            ci = 1.96 * (sd / math.sqrt(len(times)))
            with open("benchmark_confidence.csv", "w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=["mean_seconds", "sd_seconds", "ci95_seconds", "n"])
                writer.writeheader()
                writer.writerow({
                    "mean_seconds": round(mean, 4),
                    "sd_seconds": round(sd, 4),
                    "ci95_seconds": round(ci, 4),
                    "n": len(times),
                })
            print("Saved benchmark_confidence.csv")
    # Correlation between feasibility and quality (approx by profile)
    try:
        with open("benchmark_quality_summary.csv", "r") as f:
            qrows = list(csv.DictReader(f))
        with open("benchmark_profiles.csv", "r") as f:
            prows = list(csv.DictReader(f))
        qmap = {r["profile"]: r for r in qrows}
        profmap = {r["profile"]: r for r in prows}
        pairs = []
        for name, q in qmap.items():
            p = profmap.get(name)
            if not p:
                continue
            try:
                feas = float(p["feasible_rate"])
                qual = float(q["macro_score_avg"])
                pairs.append((feas, qual))
            except Exception:
                pass
        if len(pairs) >= 2:
            xs = [a for a, _ in pairs]
            ys = [b for _, b in pairs]
            mx = sum(xs) / len(xs)
            my = sum(ys) / len(ys)
            num = sum((x - mx) * (y - my) for x, y in pairs)
            denx = sum((x - mx) ** 2 for x in xs)
            deny = sum((y - my) ** 2 for y in ys)
            corr = num / ((denx * deny) ** 0.5) if denx > 0 and deny > 0 else 0
            with open("benchmark_correlation.csv", "w", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=["metric_x", "metric_y", "pearson_r"])
                writer.writeheader()
                writer.writerow({"metric_x": "feasible_rate", "metric_y": "macro_score_avg", "pearson_r": round(corr, 4)})
            print("Saved benchmark_correlation.csv")
    except Exception:
        pass
