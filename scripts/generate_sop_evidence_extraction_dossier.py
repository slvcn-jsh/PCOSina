from __future__ import annotations

import re
import shutil
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from docx.table import Table
from docx.text.paragraph import Paragraph


ROOT = Path(__file__).resolve().parents[1]
MANUSCRIPT = ROOT / "docs" / "[Manuscript] Quadrant_Chapters_1-6_Revised_S2526T3 (1).docx"
SOP_REPORT = ROOT / "docs" / "defense" / "PCOSINA_SOP_Defense_Explanation_Report_2026-05-23.docx"
OUT_DOCX = ROOT / "docs" / "defense" / "PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.docx"
OUT_MD = ROOT / "docs" / "defense" / "PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.md"
ASSET_DIR = ROOT / "docs" / "defense" / "PCOSINA_SOP_Evidence_Extraction_Dossier_FINAL_2026-05-24.assets"


@dataclass(frozen=True)
class SourceParagraph:
    index: int
    style: str
    text: str


@dataclass(frozen=True)
class SourceTable:
    number: int
    title: str
    paragraph_index: int
    object_index: int
    rows: list[list[str]]


@dataclass(frozen=True)
class SourceFigure:
    number: int
    title: str
    paragraph_index: int
    image_path: Path | None


@dataclass(frozen=True)
class SopSection:
    number: int
    title: str
    revised_question: str
    original_question_ref: str
    direct_answer: str
    how: str
    inputs: str
    outputs: str
    metrics: str
    statements: list[int]
    tables: list[int]
    figures: list[int]
    gaps: list[str]


def clean_text(text: str) -> str:
    return " ".join(text.split())


def iter_blocks(document: Document):
    for child in document.element.body.iterchildren():
        if child.tag == qn("w:p"):
            yield Paragraph(child, document)
        elif child.tag == qn("w:tbl"):
            yield Table(child, document)


def extract_sources(document: Document) -> tuple[dict[int, SourceParagraph], dict[int, SourceTable], dict[int, SourceFigure]]:
    paragraphs: dict[int, SourceParagraph] = {}
    tables: dict[int, SourceTable] = {}
    figures: dict[int, SourceFigure] = {}
    embedded_images: list[tuple[int, str, bytes]] = []

    if ASSET_DIR.exists():
        shutil.rmtree(ASSET_DIR)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)

    paragraph_index = -1
    table_object_index = 0
    last_table_caption: tuple[int, str, int] | None = None
    previous_figure_caption_index = -1

    for block in iter_blocks(document):
        if isinstance(block, Paragraph):
            paragraph_index += 1
            text = clean_text(block.text)
            if text:
                paragraphs[paragraph_index] = SourceParagraph(paragraph_index, block.style.name, text)

            for rid in block._element.xpath(".//a:blip/@r:embed"):
                part = document.part.related_parts[rid]
                suffix = Path(str(part.partname)).suffix or ".png"
                embedded_images.append((paragraph_index, suffix, part.blob))

            table_match = re.match(r"Table\s+(\d+)\.\s*(.+)", text)
            if table_match:
                last_table_caption = (
                    int(table_match.group(1)),
                    f"Table {table_match.group(1)}. {table_match.group(2)}",
                    paragraph_index,
                )

            figure_match = re.match(r"Figure\s+(\d+)\.\s*(.+)", text)
            if figure_match:
                number = int(figure_match.group(1))
                nearest_image = find_figure_image(number, previous_figure_caption_index, paragraph_index, embedded_images)
                figures[number] = SourceFigure(
                    number=number,
                    title=f"Figure {figure_match.group(1)}. {figure_match.group(2)}",
                    paragraph_index=paragraph_index,
                    image_path=nearest_image,
                )
                previous_figure_caption_index = paragraph_index
        else:
            table_object_index += 1
            if last_table_caption and last_table_caption[0] not in tables:
                number, title, paragraph_index_for_caption = last_table_caption
                rows = [[clean_text(cell.text) for cell in row.cells] for row in block.rows]
                tables[number] = SourceTable(
                    number=number,
                    title=title,
                    paragraph_index=paragraph_index_for_caption,
                    object_index=table_object_index,
                    rows=rows,
                )

    return paragraphs, tables, figures


def find_figure_image(
    number: int,
    previous_figure_caption_index: int,
    paragraph_index: int,
    images: list[tuple[int, str, bytes]],
) -> Path | None:
    if not images:
        return None
    candidates = [
        image for image in images
        if previous_figure_caption_index < image[0] <= paragraph_index
    ]
    if candidates:
        _, suffix, blob = sorted(candidates, key=lambda item: item[0])[-1]
        path = ASSET_DIR / f"figure_{number:02d}{suffix}"
        path.write_bytes(blob)
        return path
    return None


def shade_cell(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_margins(cell, top=80, start=80, bottom=80, end=80) -> None:
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    tc_mar = tc_pr.first_child_found_in("w:tcMar")
    if tc_mar is None:
        tc_mar = OxmlElement("w:tcMar")
        tc_pr.append(tc_mar)
    for margin, value in {"top": top, "start": start, "bottom": bottom, "end": end}.items():
        node = tc_mar.find(qn(f"w:{margin}"))
        if node is None:
            node = OxmlElement(f"w:{margin}")
            tc_mar.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_repeat_table_header(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tbl_header = OxmlElement("w:tblHeader")
    tbl_header.set(qn("w:val"), "true")
    tr_pr.append(tbl_header)


def set_cant_split(row) -> None:
    tr_pr = row._tr.get_or_add_trPr()
    tr_pr.append(OxmlElement("w:cantSplit"))


def add_heading(document: Document, text: str, level: int = 1) -> None:
    p = document.add_heading(text, level=level)
    for run in p.runs:
        run.font.name = "Calibri"
        run.font.color.rgb = RGBColor(31, 78, 55)


def add_para(document: Document, text: str, size: float = 9.5, bold: bool = False, italic: bool = False) -> None:
    p = document.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.05
    r = p.add_run(text)
    r.font.name = "Calibri"
    r.font.size = Pt(size)
    r.bold = bold
    r.italic = italic


def add_bullets(document: Document, items: list[str]) -> None:
    for item in items:
        p = document.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(2)
        r = p.add_run(item)
        r.font.name = "Calibri"
        r.font.size = Pt(9)


def add_table(document: Document, headers: list[str], rows: list[list[str]], font_size: float = 7.7) -> None:
    table = document.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    try:
        table.style = "Table Grid"
    except Exception:
        pass

    header_row = table.rows[0]
    set_repeat_table_header(header_row)
    for index, header in enumerate(headers):
        cell = header_row.cells[index]
        cell.text = ""
        p = cell.paragraphs[0]
        r = p.add_run(header)
        r.bold = True
        r.font.size = Pt(font_size)
        shade_cell(cell, "D9EAD3")
        set_cell_margins(cell)
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER

    for row in rows:
        cells = table.add_row().cells
        set_cant_split(table.rows[-1])
        for index, value in enumerate(row):
            cells[index].text = ""
            p = cells[index].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(value)
            r.font.size = Pt(font_size)
            set_cell_margins(cells[index])
            cells[index].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.TOP

    document.add_paragraph()


def add_callout(document: Document, title: str, body: str, fill: str = "EAF4EF") -> None:
    table = document.add_table(rows=1, cols=1)
    try:
        table.style = "Table Grid"
    except Exception:
        pass
    cell = table.cell(0, 0)
    shade_cell(cell, fill)
    set_cell_margins(cell, top=130, start=150, bottom=130, end=150)
    p = cell.paragraphs[0]
    r = p.add_run(title)
    r.bold = True
    r.font.size = Pt(10)
    r.font.color.rgb = RGBColor(31, 78, 55)
    p2 = cell.add_paragraph()
    p2.paragraph_format.space_after = Pt(0)
    r2 = p2.add_run(body)
    r2.font.size = Pt(9)
    document.add_paragraph()


def add_source_figure(document: Document, figure: SourceFigure, width_inches: float = 6.9) -> bool:
    add_para(document, f"{figure.title} | Source: manuscript P{figure.paragraph_index:04d}.", size=8.5, bold=True)
    if figure.image_path and figure.image_path.exists():
        try:
            paragraph = document.add_paragraph()
            paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = paragraph.add_run()
            run.add_picture(str(figure.image_path), width=Inches(width_inches))
            return True
        except Exception:
            add_para(document, f"Image file exists but could not be embedded: {figure.image_path}", size=8.5)
            return False
    add_para(document, "No embedded image was mapped to this caption during extraction.", size=8.5)
    return False


def get_pseudocode_text() -> str:
    return (
        "Start plan generation; load and validate user profile; return validation guidance when required inputs are missing "
        "or conflicting; run Stage 1 candidate construction; return no-safe-plan guidance when candidate coverage is insufficient; "
        "iterate through supported nutrition tolerance and recipe repeat-limit settings; build and solve the CP-SAT weekly assignment "
        "model within the configured time budget; return an optimized weekly meal plan when a complete feasible 21-slot plan is found; "
        "otherwise return a structured no-safe-plan response with reason codes, diagnostics, and suggested non-safety adjustments."
    )


def key_figures_for_sop(section: SopSection) -> list[int]:
    return {
        1: [1, 12, 20],
        2: [1, 12, 16],
        3: [1, 8, 21],
        4: [8, 9, 10],
        5: [16, 18, 20, 21],
    }.get(section.number, section.figures[:3])


def configure_document(document: Document) -> None:
    section = document.sections[0]
    section.orientation = WD_ORIENT.LANDSCAPE
    section.page_width, section.page_height = section.page_height, section.page_width
    section.top_margin = Inches(0.45)
    section.bottom_margin = Inches(0.45)
    section.left_margin = Inches(0.50)
    section.right_margin = Inches(0.50)

    styles = document.styles
    styles["Normal"].font.name = "Calibri"
    styles["Normal"].font.size = Pt(9.5)
    for style_name in ["Heading 1", "Heading 2", "Heading 3"]:
        styles[style_name].font.name = "Calibri"
        styles[style_name].font.color.rgb = RGBColor(31, 78, 55)
    styles["Heading 1"].font.size = Pt(15)
    styles["Heading 2"].font.size = Pt(12)
    styles["Heading 3"].font.size = Pt(10.5)


def get_sop_sections() -> list[SopSection]:
    return [
        SopSection(
            number=1,
            title="Weekly nutrition and weight-management meal planning",
            revised_question=(
                "How can a weekly meal-planning system be designed to generate nutritionally adequate meal plans "
                "for Filipinos with PCOS while considering weight-management goals and dietary constraints?"
            ),
            original_question_ref="P0179",
            direct_answer=(
                "PCOSina converts profile and goal data into planning targets, removes unsafe or incompatible recipes, "
                "and uses OR-Tools CP-SAT to assemble a complete 7-day, 21-slot plan while preserving hard constraints."
            ),
            how=(
                "Two-stage workflow: deterministic candidate construction and safety filtering first; non-authoritative "
                "LightGBM ranking may order safe candidates; CP-SAT performs final weekly assignment and no-safe-plan handling."
            ),
            inputs=(
                "Profile, anthropometrics, activity, wellness goal, dietary restrictions, allergies, explicit exclusions, "
                "budget, household scaling, pantry entries, cooking time, variety preference, recipe nutrition, prices, and policy values."
            ),
            outputs=(
                "Seven-day plan with breakfast/lunch/dinner slots, nutrition summaries, cost estimates, pantry-overlap indicators, "
                "explanation data, grocery guidance, local saved artifacts, and no-safe-plan diagnostics when needed."
            ),
            metrics=(
                "100/100 final benchmark runs produced 21 meal slots; zero hard-rule violations; nutrition feasibility passed; "
                "final runtime average 257 ms, P95 313 ms, max 357 ms."
            ),
            statements=[124, 204, 212, 222, 259, 269, 272, 277, 655, 657, 659, 667, 669, 671, 675, 819, 821, 1141, 1143, 1145, 1149, 1153, 1156, 1191, 1193],
            tables=[4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 42, 45, 46, 47, 50, 51],
            figures=[1, 8, 12, 13, 14, 15, 20, 21],
            gaps=[
                "The manuscript still contains insulin-resistance wording in the original SOP/input descriptions. Use the revised SOP wording from the defense report.",
                "The manuscript gives pass/fail computation evidence, but a detailed manual-vs-system formula worksheet should be ready if the panel asks.",
            ],
        ),
        SopSection(
            number=2,
            title="Preferences, Filipino food practices, and dietary restrictions",
            revised_question=(
                "How can user preferences, Filipino food practices, and dietary restrictions be effectively integrated "
                "into a personalized meal-planning algorithm for individuals with PCOS?"
            ),
            original_question_ref="P0180",
            direct_answer=(
                "PCOSina treats allergies, restrictions, and explicit exclusions as hard filters, while Filipino food context, "
                "preferences, pantry overlap, budget, and cooking-time behavior shape ranking and optimization."
            ),
            how=(
                "The system normalizes recipe data, ingredient tokens, pantry entries, allergy terms, and dietary restrictions; "
                "labels recipe attributes; removes hard-rule violations; ranks feasible recipes; and balances the full week through CP-SAT."
            ),
            inputs=(
                "Dietary restrictions, allergies, explicit excluded foods, meal count, cooking-time limit, budget, pantry entries, "
                "planning priority, Filipino recipe data, nutrition values, ingredient tokens, and cultural familiarity indicators."
            ),
            outputs=(
                "Personalized Filipino-context weekly meal plan, readable meal slots, restriction-compliant recipe choices, plan metadata, "
                "cultural relevance evidence, and no-safe-plan guidance when preferences become too restrictive."
            ),
            metrics=(
                "Functional Suitability overall mean 3.93 Agree; Usability overall mean 4.06 Agree; Filipino food item scored "
                "4.28 primary / 3.84 secondary; safety violations must remain zero."
            ),
            statements=[143, 166, 168, 229, 231, 259, 269, 272, 286, 824, 826, 960, 962, 964, 966, 969, 1073, 1082, 1097, 1196, 1198],
            tables=[1, 2, 3, 11, 12, 14, 17, 19, 20, 23, 33, 36, 41, 45, 46, 47, 48],
            figures=[1, 12, 15, 16, 18, 19],
            gaps=[
                "The manuscript defines CTS/CIS formulas and protocol, but no final CTS/CIS result table was found.",
                "Keep hard preferences separate from soft preferences in defense answers so cultural or ML ranking is never described as overriding safety.",
            ],
        ),
        SopSection(
            number=3,
            title="Pantry-aware planning and grocery reduction support",
            revised_question=(
                "How can pantry inventory data be integrated into meal planning to help promote reduced food waste "
                "and unnecessary grocery purchases while maintaining nutritional adequacy?"
            ),
            original_question_ref="P0181",
            direct_answer=(
                "PCOSina uses pantry entries as ranking and grocery-guidance signals. It can encourage ingredient reuse and reduce unnecessary purchases, "
                "but actual waste reduction depends on pantry accuracy and user adherence."
            ),
            how=(
                "Pantry strings are normalized to tokens, compared with recipe ingredients, used to compute pantry overlap, and reflected in grocery deficit guidance."
            ),
            inputs=(
                "User pantry entries, recipe ingredient lists, normalized pantry/ingredient tokens, quantities when available, budget caps, prices, and the generated meal plan."
            ),
            outputs=(
                "Pantry-overlap indicators, grocery list grouping, pantry-covered flags, missing-item guidance, estimated prices, and no-safe-plan feedback for impossible budgets."
            ),
            metrics=(
                "Scenario 4 recorded 38 pantry-overlap matches; PHP 1,000 budget scenario passed; PHP 20 impossible budget returned no-safe-plan; "
                "functional suitability pantry/purchase items scored positively."
            ),
            statements=[147, 163, 176, 192, 204, 259, 269, 277, 683, 707, 719, 720, 721, 1163, 1167, 1181, 1183, 1201, 1203, 1218],
            tables=[3, 11, 14, 16, 17, 18, 21, 31, 32, 33, 42, 45, 46, 47, 49, 50, 51],
            figures=[1, 8, 12, 15, 18, 20, 21],
            gaps=[
                "The manuscript has proxy evidence for purchase reduction, but no measured pre/post household food-waste or purchase-log study.",
                "Use 'helps promote' or 'supports' waste reduction. Avoid saying the system proved actual food-waste reduction.",
            ],
        ),
        SopSection(
            number=4,
            title="Offline-first mobile support",
            revised_question=(
                "How can an offline-first mobile application be designed to support saved meal planning, saved recipe access, "
                "grocery/pantry management, and progress tracking in environments with limited or no internet connectivity?"
            ),
            original_question_ref="P0182",
            direct_answer=(
                "PCOSina keeps saved user-facing artifacts locally available while clearly bounding new optimized plan generation as backend-dependent."
            ),
            how=(
                "The mobile client uses local persistence for profiles, saved plans, pantry records, grocery guides, progress records, and settings; "
                "remote optimization is used for new CP-SAT plan generation."
            ),
            inputs=(
                "Local storage artifacts, saved profile, saved plan, pantry records, grocery guidance, progress logs, connectivity state, and backend optimizer availability."
            ),
            outputs=(
                "Offline-readable saved artifacts, local-first continuity, clear online-dependency boundary for new generation, and reliability/portability survey evidence."
            ),
            metrics=(
                "Portability overall mean 4.04 Agree; offline item 4.04 primary / 3.88 secondary; saved plans/pantry offline item 4.16 primary / 4.12 secondary; "
                "Reliability overall mean 4.00 Agree."
            ),
            statements=[149, 226, 227, 253, 264, 279, 290, 348, 371, 372, 567, 590, 626, 655, 671, 819, 850, 1094, 1170, 1174, 1206, 1208, 1220],
            tables=[3, 6, 8, 9, 21, 37, 40, 41, 42, 45, 50, 51],
            figures=[4, 5, 8, 9, 10, 16, 18, 19],
            gaps=[
                "The manuscript supports saved-data continuity, not full offline generation of new optimized plans.",
                "Screenshots/manual walkthrough evidence would strengthen SOP 4 if the panel asks for direct UI proof.",
            ],
        ),
        SopSection(
            number=5,
            title="Evaluation of nutrition quality, usability, cultural relevance, and efficiency",
            revised_question=(
                "How does the proposed system perform in terms of nutritional quality, usability, cultural relevance, and computational efficiency "
                "based on algorithmic, expert, and user-centered evaluation metrics?"
            ),
            original_question_ref="P0183",
            direct_answer=(
                "PCOSina separates evaluation into algorithmic evidence, functional testing, ML ranking evidence, expert validation, and ISO/IEC 25010 user-centered results."
            ),
            how=(
                "The study uses Cronbach alpha for instrument reliability, weighted means for ISO/IEC 25010 results, functional tests, benchmark scenarios, ML ranking metrics, "
                "pantry/offline summaries, and runtime progression."
            ),
            inputs=(
                "50 respondents, primary/secondary user surveys, expert validators, functional test matrix, 20-profile benchmark, planner logs, ML dataset, and cultural relevance protocol."
            ),
            outputs=(
                "Cronbach alpha table, demographic/baseline tables, ISO/IEC 25010 tables, functional testing table, planner benchmark tables, ML metrics, pantry/offline results, and final evidence summary."
            ),
            metrics=(
                "Cronbach alpha all above 0.700; overall ISO mean 4.02 Agree; Usability 4.06 Agree; Functional Suitability 3.93 Agree; Security 4.24 Strongly Agree; "
                "100/100 final runs; average runtime 257 ms; P95 313 ms; max 357 ms; zero hard-rule violations; ML NDCG@10 0.9211."
            ),
            statements=[916, 920, 941, 946, 953, 960, 962, 964, 966, 969, 979, 1019, 1021, 1025, 1031, 1069, 1073, 1082, 1094, 1097, 1138, 1141, 1143, 1153, 1156, 1158, 1163, 1170, 1174, 1178, 1181, 1183, 1211, 1213, 1222, 1241, 1253],
            tables=[19, 20, 21, 22, 23, 24, 25, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51],
            figures=[16, 18, 19, 20, 21],
            gaps=[
                "Standard deviation values requested by the SOP report are not visible in the manuscript result tables.",
                "Cultural relevance has protocol/formulas and survey support, but final CTS/CIS values were not found.",
                "Cronbach alpha proves questionnaire consistency only; it must be paired with the algorithmic and survey results already extracted here.",
            ],
        ),
    ]


def build_docx(
    paragraphs: dict[int, SourceParagraph],
    tables: dict[int, SourceTable],
    figures: dict[int, SourceFigure],
    sections: list[SopSection],
) -> None:
    doc = Document()
    configure_document(doc)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("PCOSINA SOP Evidence Extraction Dossier")
    run.bold = True
    run.font.size = Pt(25)
    run.font.color.rgb = RGBColor(31, 78, 55)

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = subtitle.add_run("Final defense-ready document with manuscript data, tables, figures, claim boundaries, and SOP traceability")
    r.italic = True
    r.font.size = Pt(11)

    add_callout(
        doc,
        "Final Output Standard",
        "This document preserves the green defense-report template and adds the missing bridge that the manuscript alone does not provide: "
        "each SOP is converted into how-process, required inputs, outputs/evidence, metrics/results, statement extracts, visible figures, extracted tables, and gap notes.",
        fill="EAF4EF",
    )

    add_table(
        doc,
        ["Field", "Value"],
        [
            ["Prepared on", date.today().isoformat()],
            ["Manuscript source", str(MANUSCRIPT)],
            ["SOP requirements source", str(SOP_REPORT)],
            ["Final deliverable", str(OUT_DOCX)],
            ["Deliverable purpose", "One defense-ready extraction document covering how, inputs, outputs/evidence, metrics/results, figures, tables, statements, and gaps per SOP."],
            ["Traceability note", "P#### references are DOCX paragraph indexes from the current manuscript extraction; table and figure captions can be found by Ctrl+F if paragraph indexes shift."],
        ],
    )

    add_callout(
        doc,
        "Defense-Safe Position",
        "PCOSina is a wellness decision-support application, not a diagnosis engine, treatment engine, or medical device. "
        "The planning contract is deterministic filtering plus deterministic CP-SAT optimization. ML is assistive only and cannot override hard constraints.",
    )

    add_heading(doc, "Current Manuscript Status", 1)
    add_table(
        doc,
        ["Source Item", "Extracted Count / Status", "Meaning for This Final Document"],
        [
            ["Manuscript paragraphs", str(len(paragraphs)), "The evidence extraction scanned the manuscript body and keeps statement-level source anchors."],
            ["Manuscript tables", str(len(tables)), "SOP-relevant source tables are copied into the appendix and cross-referenced inside each SOP."],
            ["Manuscript figures", str(len(figures)), "SOP-relevant figures are embedded visibly in the SOP sections and again in the figure appendix."],
            ["Final evidence style", "Green template retained", "The output uses the defense report's green headings, callouts, shaded tables, and structured outline."],
        ],
    )

    add_heading(doc, "What This Final Version Adds", 1)
    add_table(
        doc,
        ["Added Layer", "Why It Was Added"],
        [
            ["SOP-to-evidence bridge", "The manuscript contains evidence, but it is scattered across Chapters 1, 3, 4, 5, and appendices. This document groups it by defense question."],
            ["Inline figure panels", "Figures are now visible in the SOP sections instead of being left only as references or hidden source assets."],
            ["Claim-boundary notes", "Defense-sensitive areas are marked clearly: insulin-resistance wording, pantry/waste claims, offline-generation limits, CTS/CIS gaps, and standard deviation gaps."],
            ["Extracted table appendix", "The numerical evidence is preserved in one place so the panel can check the source data without searching the full manuscript."],
        ],
    )

    add_heading(doc, "Requirements From SOP Report", 1)
    add_table(
        doc,
        ["SOP", "Revised Statement Used for Defense", "Main Defense Boundary"],
        [
            ["1", sections[0].revised_question, "Remove insulin-resistance claiming; defend measurable nutrition adequacy, weight management, and constraints."],
            ["2", sections[1].revised_question, "Hard restrictions stay non-negotiable; cultural fit and preferences are ranking/optimization signals."],
            ["3", sections[2].revised_question, "Use 'helps promote' reduced waste/purchases; pantry data is not proof of actual household waste reduction."],
            ["4", sections[3].revised_question, "Defend saved-data offline continuity; do not claim full offline generation unless implemented locally."],
            ["5", sections[4].revised_question, "Use algorithmic, expert, and user-centered metrics; Cronbach alpha is survey reliability only."],
        ],
    )

    add_heading(doc, "Master SOP Evidence Matrix", 1)
    add_table(
        doc,
        ["SOP", "Evidence Status", "Strongest Manuscript Evidence", "Main Gap / Boundary"],
        [
            ["1", "Strong with wording cleanup", "P0615-P0635 algorithm, Table 47 safety, Table 51 final benchmark", "Original manuscript still says insulin resistance."],
            ["2", "Mostly strong", "P0273-P0276 personalization, Tables 19-20 cultural protocol, Tables 33/36/41 survey", "No final CTS/CIS result table found."],
            ["3", "Proxy-supported", "P0679-P0680 pantry/grocery formulas, Table 49 pantry/budget behavior", "No actual food-waste reduction measurement."],
            ["4", "Strong for saved-data continuity", "P0231, P0571, P1137, Table 50", "New optimized generation remains backend-dependent."],
            ["5", "Strong overall", "Tables 25, 33-51; P1123 final runtime; P0988 evaluation scope", "Standard deviation and CTS/CIS values not visible."],
        ],
    )

    for section in sections:
        add_sop_section(doc, section, paragraphs, tables, figures)

    add_heading(doc, "Gap and Cleanup Board", 1)
    gap_rows = []
    for section in sections:
        for gap in section.gaps:
            gap_rows.append([f"SOP {section.number}", gap, "Resolve in manuscript wording, appendices, or defense notes before final panel questioning."])
    add_table(doc, ["SOP", "Gap / Boundary", "Action"], gap_rows)

    add_heading(doc, "Extracted Manuscript Figure Appendix", 1)
    figure_numbers = sorted({number for section in sections for number in section.figures})
    for number in figure_numbers:
        figure = figures.get(number)
        if not figure:
            if number == 15:
                add_heading(doc, "Figure 15. High-Level Pseudocode", 2)
                add_para(doc, "Source: manuscript P0814. The pseudocode is extracted as table-like text in the manuscript rather than a normal embedded image.", size=8.5, italic=True)
                add_para(doc, get_pseudocode_text(), size=8)
            continue
        add_heading(doc, figure.title, 2)
        if figure.number == 15 and not figure.image_path:
            add_para(doc, "Source: manuscript P0814. The pseudocode is extracted as table-like text in the manuscript rather than a normal embedded image.", size=8.5, italic=True)
            add_para(doc, get_pseudocode_text(), size=8)
        else:
            add_source_figure(doc, figure, width_inches=8.6)

    add_heading(doc, "Extracted Manuscript Table Appendix", 1)
    appendix_table_numbers = sorted({number for section in sections for number in section.tables if number in tables})
    for table_number in appendix_table_numbers:
        source_table = tables[table_number]
        add_heading(doc, f"{source_table.title}", 2)
        add_para(doc, f"Source: manuscript P{source_table.paragraph_index:04d}; table object {source_table.object_index}.", size=8.5, italic=True)
        if not source_table.rows:
            add_para(doc, "No rows extracted.", size=8.5)
            continue
        headers = [value or f"Column {idx + 1}" for idx, value in enumerate(source_table.rows[0])]
        add_table(doc, headers, source_table.rows[1:], font_size=6.8)

    doc.save(OUT_DOCX)


def add_sop_section(
    doc: Document,
    section: SopSection,
    paragraphs: dict[int, SourceParagraph],
    tables: dict[int, SourceTable],
    figures: dict[int, SourceFigure],
) -> None:
    add_heading(doc, f"SOP {section.number}: {section.title}", 1)
    add_callout(doc, "Direct Defense Answer", section.direct_answer, fill="F2F8F4")
    add_table(
        doc,
        ["Bucket", "Extracted / Organized Answer"],
        [
            ["Revised SOP question", section.revised_question],
            ["Original manuscript SOP", f"{section.original_question_ref}: {paragraphs[int(section.original_question_ref[1:])].text}"],
            ["How PCOSina solves this", section.how],
            ["Inputs required", section.inputs],
            ["Outputs and evidence required", section.outputs],
            ["Metrics/results found", section.metrics],
        ],
        font_size=7.5,
    )

    add_heading(doc, f"SOP {section.number} Visible Figure Evidence", 2)
    figure_note_rows = []
    for number in key_figures_for_sop(section):
        figure = figures.get(number)
        if figure:
            figure_note_rows.append([f"Figure {number}", figure.title, f"Manuscript P{figure.paragraph_index:04d}"])
        elif number == 13:
            figure_note_rows.append(["Figure 13", "High-Level Pseudocode", "Manuscript P0774"])
    if figure_note_rows:
        add_table(doc, ["Figure", "Caption / Evidence Role", "Source"], figure_note_rows, font_size=7.2)
    for number in key_figures_for_sop(section):
        figure = figures.get(number)
        if figure:
            if figure.number == 15 and not figure.image_path:
                add_para(doc, "Figure 15. High-Level Pseudocode | Source: manuscript P0814.", size=8.5, bold=True)
                add_para(doc, get_pseudocode_text(), size=8)
            else:
                add_source_figure(doc, figure, width_inches=6.6)
        elif number == 15:
            add_para(doc, "Figure 15. High-Level Pseudocode | Source: manuscript P0814.", size=8.5, bold=True)
            add_para(doc, get_pseudocode_text(), size=8)

    add_heading(doc, f"SOP {section.number} Manuscript Statement Extracts", 2)
    statement_rows = []
    for paragraph_index in section.statements:
        paragraph = paragraphs.get(paragraph_index)
        if paragraph:
            statement_rows.append([f"P{paragraph.index:04d}", paragraph.text])
    add_table(doc, ["Source", "Extracted statement"], statement_rows, font_size=6.9)

    add_heading(doc, f"SOP {section.number} Table and Figure Evidence Map", 2)
    table_refs = [f"Table {number}: {tables[number].title} (P{tables[number].paragraph_index:04d})" for number in section.tables if number in tables]
    figure_refs = []
    for number in section.figures:
        figure = figures.get(number)
        if figure:
            figure_refs.append(f"Figure {number}: {figure.title} (P{figure.paragraph_index:04d})")
        elif number == 13:
            figure_refs.append("Figure 13: High-Level Pseudocode (P0774; extracted as table-like pseudocode)")
    add_table(
        doc,
        ["Evidence Type", "Sources"],
        [
            ["Tables", "\n".join(table_refs)],
            ["Figures", "\n".join(figure_refs)],
            ["Gaps / claim boundaries", "\n".join(section.gaps)],
        ],
        font_size=7.2,
    )


def markdown_table(headers: list[str], rows: list[list[str]]) -> str:
    def esc(value: str) -> str:
        return value.replace("|", "\\|").replace("\n", "<br>")

    lines = ["| " + " | ".join(esc(h) for h in headers) + " |"]
    lines.append("| " + " | ".join("---" for _ in headers) + " |")
    for row in rows:
        padded = row + [""] * (len(headers) - len(row))
        lines.append("| " + " | ".join(esc(value) for value in padded[: len(headers)]) + " |")
    return "\n".join(lines)


def build_markdown(
    paragraphs: dict[int, SourceParagraph],
    tables: dict[int, SourceTable],
    figures: dict[int, SourceFigure],
    sections: list[SopSection],
) -> None:
    lines: list[str] = []
    lines.append("# PCOSINA SOP Evidence Extraction Dossier")
    lines.append("")
    lines.append(f"Prepared on: {date.today().isoformat()}")
    lines.append(f"Manuscript source: `{MANUSCRIPT}`")
    lines.append(f"SOP requirements source: `{SOP_REPORT}`")
    lines.append("")
    lines.append("## Final Output Standard")
    lines.append("")
    lines.append(
        "This final version preserves the green defense-report organization in the DOCX output and adds the missing bridge "
        "that the manuscript alone does not provide: SOP-by-SOP how-process, inputs, outputs/evidence, metrics/results, "
        "statement extracts, visible figure evidence, table extracts, and gap notes."
    )
    lines.append("")
    lines.append("Traceability note: `P####` references are DOCX paragraph indexes from this extraction pass.")
    lines.append("")
    lines.append("## Current Manuscript Status")
    lines.append("")
    lines.append(markdown_table(
        ["Source Item", "Extracted Count / Status", "Meaning"],
        [
            ["Manuscript paragraphs", str(len(paragraphs)), "Statement-level evidence anchors are available."],
            ["Manuscript tables", str(len(tables)), "SOP-relevant tables are copied into the appendix."],
            ["Manuscript figures", str(len(figures)), "SOP-relevant figures are extracted into the final assets folder and embedded in the DOCX."],
        ],
    ))
    lines.append("")
    lines.append("## Coverage Readout")
    lines.append("")
    lines.append(
        markdown_table(
            ["SOP", "Evidence Status", "Strongest Evidence", "Gap / Boundary"],
            [
                ["1", "Strong with wording cleanup", "P0615-P0635; Tables 47 and 51", "Insulin-resistance wording still appears."],
                ["2", "Mostly strong", "P0273-P0276; Tables 19, 20, 33, 36, 41", "No final CTS/CIS result table found."],
                ["3", "Proxy-supported", "P0679-P0680; Table 49", "No actual food-waste measurement."],
                ["4", "Strong for saved-data continuity", "P0231, P0571, P1137; Table 50", "New optimized generation is backend-dependent."],
                ["5", "Strong overall", "Tables 25 and 33-51", "No visible standard deviation table; no CTS/CIS result values."],
            ],
        )
    )
    lines.append("")

    for section in sections:
        lines.append(f"## SOP {section.number}: {section.title}")
        lines.append("")
        lines.append(markdown_table(
            ["Bucket", "Extracted / Organized Answer"],
            [
                ["Revised SOP question", section.revised_question],
                ["Original manuscript SOP", f"{section.original_question_ref}: {paragraphs[int(section.original_question_ref[1:])].text}"],
                ["Direct defense answer", section.direct_answer],
                ["How PCOSina solves this", section.how],
                ["Inputs required", section.inputs],
                ["Outputs and evidence required", section.outputs],
                ["Metrics/results found", section.metrics],
                ["Gaps / boundaries", "<br>".join(section.gaps)],
            ],
        ))
        lines.append("")
        lines.append("### Visible Figure Evidence")
        lines.append("")
        for number in key_figures_for_sop(section):
            figure = figures.get(number)
            if figure and figure.image_path:
                rel = figure.image_path.relative_to(ROOT).as_posix()
                lines.append(f"**{figure.title}**  ")
                lines.append(f"Source: manuscript P{figure.paragraph_index:04d}.")
                lines.append("")
                lines.append(f"![{figure.title}]({rel})")
                lines.append("")
            elif figure and figure.number == 15:
                lines.append("**Figure 15. High-Level Pseudocode**  ")
                lines.append("Source: manuscript P0814. Extracted as table-like pseudocode text in the source manuscript.")
                lines.append("")
                lines.append(get_pseudocode_text())
                lines.append("")
            elif number == 15:
                lines.append("**Figure 15. High-Level Pseudocode**  ")
                lines.append("Source: manuscript P0814. Extracted as table-like pseudocode text in the source manuscript.")
                lines.append("")
                lines.append(get_pseudocode_text())
                lines.append("")
        lines.append("### Manuscript Statement Extracts")
        statement_rows = []
        for paragraph_index in section.statements:
            paragraph = paragraphs.get(paragraph_index)
            if paragraph:
                statement_rows.append([f"P{paragraph.index:04d}", paragraph.text])
        lines.append(markdown_table(["Source", "Extracted statement"], statement_rows))
        lines.append("")
        lines.append("### Tables and Figures")
        table_refs = [f"Table {number}: {tables[number].title} (P{tables[number].paragraph_index:04d})" for number in section.tables if number in tables]
        figure_refs = []
        for number in section.figures:
            figure = figures.get(number)
            if figure:
                figure_refs.append(f"Figure {number}: {figure.title} (P{figure.paragraph_index:04d})")
            elif number == 13:
                figure_refs.append("Figure 13: High-Level Pseudocode (P0774)")
        lines.append(markdown_table(["Evidence Type", "Sources"], [["Tables", "<br>".join(table_refs)], ["Figures", "<br>".join(figure_refs)]]))
        lines.append("")

    lines.append("## Extracted Table Appendix")
    for table_number in sorted({number for section in sections for number in section.tables if number in tables}):
        table = tables[table_number]
        lines.append("")
        lines.append(f"### {table.title}")
        lines.append(f"Source: manuscript P{table.paragraph_index:04d}; table object {table.object_index}.")
        if table.rows:
            headers = [value or f"Column {idx + 1}" for idx, value in enumerate(table.rows[0])]
            lines.append(markdown_table(headers, table.rows[1:]))

    lines.append("")
    lines.append("## Extracted Figure Appendix")
    for number in sorted({number for section in sections for number in section.figures}):
        figure = figures.get(number)
        if figure:
            if figure.image_path:
                image_ref = figure.image_path.relative_to(ROOT)
                lines.append(f"- {figure.title} - P{figure.paragraph_index:04d}; extracted image: `{image_ref}`")
            elif figure.number == 15:
                lines.append("- Figure 15. High-Level Pseudocode - P0814; extracted as table-like pseudocode text in the source manuscript.")
            else:
                lines.append(f"- {figure.title} - P{figure.paragraph_index:04d}; no embedded image object found in the manuscript.")
        elif number == 15:
            lines.append("- Figure 15. High-Level Pseudocode - P0814; extracted as table-like pseudocode.")

    OUT_MD.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    manuscript = Document(MANUSCRIPT)
    paragraphs, tables, figures = extract_sources(manuscript)
    sections = get_sop_sections()
    build_docx(paragraphs, tables, figures, sections)
    build_markdown(paragraphs, tables, figures, sections)
    print(f"Wrote {OUT_DOCX}")
    print(f"Wrote {OUT_MD}")


if __name__ == "__main__":
    main()
