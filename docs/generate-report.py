"""Regenerate docs/project-report.pdf from the editable Markdown report."""

from __future__ import annotations

import html
import re
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import BaseDocTemplate, Frame, LongTable, PageBreak, PageTemplate, Paragraph, Spacer, Table, TableStyle

from report_diagrams import DIAGRAMS, export_svgs


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "project-report.md"
OUTPUT = ROOT / "project-report.pdf"
INK = colors.HexColor("#211d3e")
MUTED = colors.HexColor("#5d5973")
ACCENT = colors.HexColor("#6d4ed6")
GOLD = colors.HexColor("#f2b96a")


def inline(text: str) -> str:
    value = html.escape(text)
    value = re.sub(r"`([^`]+)`", r'<font color="#5337a8"><b>\1</b></font>', value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", value)
    return value


styles = {
    "title": ParagraphStyle("Title", fontName="Helvetica-Bold", fontSize=23, leading=28,
                            textColor=INK, spaceAfter=16, alignment=TA_LEFT),
    "meta": ParagraphStyle("Meta", fontName="Helvetica-Bold", fontSize=8.5, leading=12,
                           textColor=ACCENT, spaceAfter=19),
    "heading": ParagraphStyle("Heading", fontName="Helvetica-Bold", fontSize=15, leading=19,
                              textColor=INK, spaceBefore=12, spaceAfter=10, keepWithNext=True),
    "subheading": ParagraphStyle("Subheading", fontName="Helvetica-Bold", fontSize=11.5, leading=15,
                                 textColor=ACCENT, spaceBefore=9, spaceAfter=7, keepWithNext=True),
    "body": ParagraphStyle("Body", fontName="Helvetica", fontSize=9.6, leading=14.1,
                           textColor=INK, spaceAfter=10),
    "bullet": ParagraphStyle("Bullet", fontName="Helvetica", fontSize=9.3, leading=13.7,
                            textColor=INK, leftIndent=16, firstLineIndent=-11, spaceAfter=7),
    "table": ParagraphStyle("Table", fontName="Helvetica", fontSize=7.2, leading=9.5,
                           textColor=INK),
    "table_head": ParagraphStyle("TableHead", fontName="Helvetica-Bold", fontSize=7.3, leading=9.5,
                                textColor=colors.white),
    "team_kicker": ParagraphStyle("TeamKicker", fontName="Helvetica-Bold", fontSize=7.4, leading=10,
                                  textColor=ACCENT, spaceAfter=9),
    "team_name": ParagraphStyle("TeamName", fontName="Helvetica-Bold", fontSize=11.2, leading=14,
                                textColor=INK, spaceAfter=4),
    "team_id": ParagraphStyle("TeamId", fontName="Helvetica", fontSize=8.2, leading=11,
                              textColor=MUTED, spaceAfter=10),
    "team_scope": ParagraphStyle("TeamScope", fontName="Helvetica", fontSize=8.6, leading=12,
                                 textColor=INK),
}


def draw_page(canvas, doc):
    width, height = A4
    canvas.saveState()
    canvas.setFillColor(colors.HexColor("#f7f5fc"))
    canvas.rect(0, height - 25 * mm, width, 25 * mm, fill=1, stroke=0)
    canvas.setFillColor(ACCENT)
    canvas.rect(20 * mm, height - 20 * mm, 3 * mm, 11 * mm, fill=1, stroke=0)
    canvas.setFont("Helvetica-Bold", 9)
    canvas.setFillColor(INK)
    canvas.drawString(27 * mm, height - 14 * mm, "SERVICEFORCE")
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawRightString(width - 20 * mm, height - 14 * mm, "PROJECT REPORT  /  2026")
    canvas.setStrokeColor(colors.HexColor("#ded9ec"))
    canvas.line(20 * mm, 17 * mm, width - 20 * mm, 17 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.drawString(20 * mm, 12 * mm, "CS F213 / MAC F212  |  Intelligent IT Service Management")
    canvas.drawRightString(width - 20 * mm, 12 * mm, str(doc.page))
    canvas.restoreState()


def build():
    export_svgs(ROOT / "diagrams")
    doc = BaseDocTemplate(str(OUTPUT), pagesize=A4, leftMargin=20 * mm,
                          rightMargin=20 * mm, topMargin=33 * mm, bottomMargin=22 * mm,
                          title="ServiceForce | Project report",
                          subject="Current implementation, system design, features and evaluation")
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height,
                  leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
    doc.addPageTemplates(PageTemplate(id="report", frames=[frame], onPage=draw_page))
    story = []
    paragraphs: list[str] = []

    def flush():
        if paragraphs:
            story.append(Paragraph(inline(" ".join(paragraphs)), styles["body"]))
            paragraphs.clear()

    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    table_rows = []
    team_rows = []
    team_mode = False

    def flush_team():
        if not team_rows:
            return
        if len(team_rows) != 4 or any(len(row) != 4 for row in team_rows):
            raise ValueError("The team page needs exactly four rows with name, ID, area and scope")
        cards = []
        for name, student_id, area, scope in team_rows:
            content = [Paragraph(inline(area.upper()), styles["team_kicker"]),
                       Paragraph(inline(name), styles["team_name"]),
                       Paragraph(inline(student_id), styles["team_id"]),
                       Paragraph(inline(scope), styles["team_scope"])]
            card = Table([[content]], colWidths=[225], rowHeights=[126], hAlign="LEFT")
            card.setStyle(TableStyle([
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f7f5fc")),
                ("BOX", (0, 0), (-1, -1), .8, colors.HexColor("#dcd6eb")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 13),
                ("RIGHTPADDING", (0, 0), (-1, -1), 13),
                ("TOPPADDING", (0, 0), (-1, -1), 13),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 13),
            ]))
            cards.append(card)
        grid = Table([[cards[0], cards[1]], [cards[2], cards[3]]],
                     colWidths=[241, 241], rowHeights=[139, 139], hAlign="LEFT")
        grid.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (0, -1), 0),
            ("RIGHTPADDING", (0, 0), (0, -1), 8),
            ("LEFTPADDING", (1, 0), (1, -1), 8),
            ("RIGHTPADDING", (1, 0), (1, -1), 0),
            ("TOPPADDING", (0, 0), (-1, -1), 0),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
        ]))
        story.append(grid)
        story.append(Spacer(1, 6 * mm))
        team_rows.clear()

    def flush_table():
        if not table_rows:
            return
        rows = [[Paragraph(inline(cell), styles["table_head" if index == 0 else "table"])
                 for cell in row] for index, row in enumerate(table_rows)]
        table = LongTable(rows, colWidths=[31, 115, 164, 172], repeatRows=1,
                          hAlign="LEFT", splitByRow=1)
        table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), ACCENT),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f7f5fc")]),
            ("GRID", (0, 0), (-1, -1), .35, colors.HexColor("#dcd6eb")),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 5),
            ("RIGHTPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 6),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ]))
        story.append(table)
        table_rows.clear()

    for line in lines:
        line = line.strip()
        if team_mode and line.startswith("- "):
            team_rows.append([cell.strip() for cell in line[2:].split("|", 3)])
            continue
        if team_mode:
            flush_team()
            team_mode = False
        if line.startswith("|") and line.endswith("|"):
            flush()
            cells = [cell.strip() for cell in line[1:-1].split("|")]
            if not all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells):
                table_rows.append(cells)
            continue
        flush_table()
        if not line:
            flush()
        elif line.startswith("[[") and line.endswith("]]"):
            flush()
            name = line[2:-2]
            if name == "TEAM_CARDS":
                team_mode = True
            else:
                story.append(DIAGRAMS[name]())
                story.append(Spacer(1, 4 * mm))
        elif line == "---":
            flush()
            story.append(PageBreak())
        elif line.startswith("# "):
            flush()
            story.append(Paragraph(inline(line[2:]), styles["title"]))
        elif line.startswith("## "):
            flush()
            story.append(Paragraph(inline(line[3:]), styles["heading"]))
        elif line.startswith("### "):
            flush()
            story.append(Paragraph(inline(line[4:]), styles["subheading"]))
        elif line.startswith("- "):
            flush()
            story.append(Paragraph("<font color=\"#6d4ed6\"><b>\u2022</b></font>  " + inline(line[2:]), styles["bullet"]))
        elif line.startswith("CS F213 / MAC F212 |"):
            flush()
            story.append(Paragraph(inline(line), styles["meta"]))
        else:
            paragraphs.append(line)
    flush()
    flush_team()
    flush_table()
    story.append(Spacer(1, 2 * mm))
    doc.build(story)
    print(OUTPUT)


if __name__ == "__main__":
    build()
