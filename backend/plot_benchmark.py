import csv
from statistics import mean, median

def bar(value, max_value, width=30):
    if max_value <= 0:
        return ""
    filled = int((value / max_value) * width)
    return "#" * filled + "-" * (width - filled)

def save_svg(profiles, out_path="benchmark_charts.svg"):
    width = 900
    height = 40 + len(profiles) * 40
    max_rate = max(float(p["feasible_rate"]) for p in profiles) if profiles else 100
    max_med = max(float(p["median_seconds"]) for p in profiles) if profiles else 1
    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    svg.append('<style>text{font-family:Arial,sans-serif;font-size:12px;}</style>')
    y = 20
    svg.append(f'<text x="10" y="{y}">Feasibility (%)</text>')
    y += 20
    for p in profiles:
        rate = float(p["feasible_rate"])
        bar_w = int((rate / max_rate) * 300) if max_rate > 0 else 0
        svg.append(f'<text x="10" y="{y+12}">{p["profile"]}</text>')
        svg.append(f'<rect x="120" y="{y}" width="{bar_w}" height="14" fill="#4CAF50"/>')
        svg.append(f'<text x="{120+bar_w+6}" y="{y+12}">{rate:.1f}%</text>')
        y += 24
    y += 20
    svg.append(f'<text x="10" y="{y}">Median Time (s)</text>')
    y += 20
    for p in profiles:
        med = float(p["median_seconds"])
        bar_w = int((med / max_med) * 300) if max_med > 0 else 0
        svg.append(f'<text x="10" y="{y+12}">{p["profile"]}</text>')
        svg.append(f'<rect x="120" y="{y}" width="{bar_w}" height="14" fill="#2196F3"/>')
        svg.append(f'<text x="{120+bar_w+6}" y="{y+12}">{med:.2f}s</text>')
        y += 24
    svg.append("</svg>")
    with open(out_path, "w") as f:
        f.write("\n".join(svg))
    print(f"Saved {out_path}")

def save_quality_svg(quality_by_profile, out_path="benchmark_quality.svg"):
    if not quality_by_profile:
        return
    width = 900
    height = 40 + len(quality_by_profile) * 60
    svg = []
    svg.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    svg.append('<style>text{font-family:Arial,sans-serif;font-size:12px;}</style>')
    y = 20
    svg.append(f'<text x="10" y="{y}">Quality Scores (Avg)</text>')
    y += 20
    for name, data in quality_by_profile.items():
        if len(data) == 11:
            macro, budget, variety, ingred, daily, macro_sd, budget_sd, variety_sd, ingred_sd, daily_sd, qindex = data
        elif len(data) == 10:
            macro, budget, variety, ingred, daily, macro_sd, budget_sd, variety_sd, ingred_sd, daily_sd = data
            qindex = 0
        elif len(data) == 5:
            macro, budget, variety, ingred, daily = data
            macro_sd = budget_sd = variety_sd = ingred_sd = daily_sd = 0
            qindex = 0
        else:
            macro, budget, variety, ingred = data
            daily = 0
            macro_sd = budget_sd = variety_sd = ingred_sd = daily_sd = 0
            qindex = 0
        svg.append(f'<text x="10" y="{y+12}">{name}</text>')
        svg.append(f'<rect x="120" y="{y}" width="{int(macro*3)}" height="10" fill="#4CAF50"/>')
        svg.append(f'<rect x="120" y="{y+12}" width="{int(budget*3)}" height="10" fill="#2196F3"/>')
        svg.append(f'<rect x="120" y="{y+24}" width="{int(variety*3)}" height="10" fill="#FFC107"/>')
        svg.append(f'<rect x="120" y="{y+36}" width="{int(ingred*3)}" height="10" fill="#9C27B0"/>')
        svg.append(f'<rect x="120" y="{y+48}" width="{int(daily*3)}" height="10" fill="#FF5722"/>')
        if qindex:
            svg.append(f'<rect x="120" y="{y+60}" width="{int(qindex*3)}" height="10" fill="#607D8B"/>')
        # Error bars (SD)
        def err_bar(val, sd, y_pos):
            x = 120 + int(val * 3)
            left = x - int(sd * 3)
            right = x + int(sd * 3)
            svg.append(f'<line x1="{left}" y1="{y_pos+5}" x2="{right}" y2="{y_pos+5}" stroke="#333" stroke-width="1"/>')
        err_bar(macro, macro_sd, y)
        err_bar(budget, budget_sd, y+12)
        err_bar(variety, variety_sd, y+24)
        err_bar(ingred, ingred_sd, y+36)
        err_bar(daily, daily_sd, y+48)
        svg.append(f'<text x="430" y="{y+10}">macro {macro:.1f}</text>')
        svg.append(f'<text x="430" y="{y+22}">budget {budget:.1f}</text>')
        svg.append(f'<text x="430" y="{y+34}">variety {variety:.1f}</text>')
        svg.append(f'<text x="430" y="{y+46}">ingred {ingred:.1f}</text>')
        svg.append(f'<text x="430" y="{y+58}">daily {daily:.1f}</text>')
        if qindex:
            svg.append(f'<text x="430" y="{y+70}">qindex {qindex:.1f}</text>')
            y += 72
        else:
            y += 60
    svg.append("</svg>")
    with open(out_path, "w") as f:
        f.write("\n".join(svg))
    print(f"Saved {out_path}")

def run():
    profiles = []
    try:
        with open("benchmark_profiles.csv", "r", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                profiles.append(row)
    except FileNotFoundError:
        print("benchmark_profiles.csv not found. Run benchmark_milp.py first.")
        return

    times = []
    try:
        with open("benchmark_results.csv", "r", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    times.append(float(row["seconds"]))
                except Exception:
                    pass
    except FileNotFoundError:
        print("benchmark_results.csv not found. Run benchmark_milp.py first.")
        return

    quality = []
    try:
        with open("benchmark_quality.csv", "r", newline="") as f:
            reader = csv.DictReader(f)
            for row in reader:
                quality.append(row)
    except FileNotFoundError:
        quality = []

    if not times:
        print("No timing data found.")
        return

    print("\n== Overall Timing ==")
    print(f"Mean: {mean(times):.2f}s | Median: {median(times):.2f}s | Max: {max(times):.2f}s")

    print("\n== Per-Profile Feasibility ==")
    max_rate = max(float(p["feasible_rate"]) for p in profiles) if profiles else 100
    for p in profiles:
        rate = float(p["feasible_rate"])
        print(f"{p['profile']:<15} {rate:>6.1f}% | {bar(rate, max_rate)}")

    print("\n== Per-Profile Median Time ==")
    max_med = max(float(p["median_seconds"]) for p in profiles) if profiles else 1
    for p in profiles:
        med = float(p["median_seconds"])
        print(f"{p['profile']:<15} {med:>6.2f}s | {bar(med, max_med)}")
    save_svg(profiles)

    if quality:
        print("\n== Quality Scores (Avg) ==")
        by_profile = {}
        for row in quality:
            name = row["profile"]
            by_profile.setdefault(name, {"macro": [], "budget": [], "variety": [], "ing": [], "daily": [], "qindex": []})
            by_profile[name]["macro"].append(float(row["macro_score"]))
            by_profile[name]["budget"].append(float(row["budget_score"]))
            by_profile[name]["variety"].append(float(row["variety_score"]))
            by_profile[name]["ing"].append(float(row["ingredient_diversity"]))
            by_profile[name]["daily"].append(float(row["daily_compliance"]))
            by_profile[name]["qindex"].append(float(row["quality_index"]))
        svg_input = {}
        for name, data in by_profile.items():
            m = sum(data["macro"]) / max(1, len(data["macro"]))
            b = sum(data["budget"]) / max(1, len(data["budget"]))
            v = sum(data["variety"]) / max(1, len(data["variety"]))
            i = sum(data["ing"]) / max(1, len(data["ing"]))
            d = sum(data["daily"]) / max(1, len(data["daily"]))
            q = sum(data["qindex"]) / max(1, len(data["qindex"]))
            def _sd(vals):
                if len(vals) < 2:
                    return 0
                mv = sum(vals) / len(vals)
                return (sum((x - mv) ** 2 for x in vals) / (len(vals) - 1)) ** 0.5
            msd = _sd(data["macro"])
            bsd = _sd(data["budget"])
            vsd = _sd(data["variety"])
            isd = _sd(data["ing"])
            dsd = _sd(data["daily"])
            print(f"{name:<15} macro={m:.1f} budget={b:.1f} variety={v:.1f} ingred={i:.1f} daily={d:.1f} qindex={q:.1f}")
            svg_input[name] = (m, b, v, i, d, msd, bsd, vsd, isd, dsd, q)
        save_quality_svg(svg_input)

if __name__ == "__main__":
    run()
