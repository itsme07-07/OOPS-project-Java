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
from reportlab.platypus import BaseDocTemplate, Frame, Image, LongTable, PageBreak, PageTemplate, Paragraph, Preformatted, Spacer, Table, TableStyle

from report_diagrams import DIAGRAMS, export_svgs


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "project-report.md"
OUTPUT = ROOT / "project-report.pdf"
INK = colors.HexColor("#211d3e")
MUTED = colors.HexColor("#5d5973")
ACCENT = colors.HexColor("#7549df")
GOLD = colors.HexColor("#ffce72")
TEAL = colors.HexColor("#74e1d3")
DEEP = colors.HexColor("#17142c")
PALE = colors.HexColor("#f0ecfb")


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
    "cover_title": ParagraphStyle("CoverTitle", fontName="Helvetica-Bold", fontSize=29, leading=35,
                                  textColor=colors.white, spaceAfter=10),
    "cover_meta": ParagraphStyle("CoverMeta", fontName="Helvetica-Bold", fontSize=8.5, leading=12,
                                 textColor=TEAL, spaceAfter=18),
    "cover_heading": ParagraphStyle("CoverHeading", fontName="Helvetica-Bold", fontSize=16, leading=20,
                                    textColor=colors.white, spaceBefore=11, spaceAfter=11, keepWithNext=True),
    "cover_body": ParagraphStyle("CoverBody", fontName="Helvetica", fontSize=10, leading=15,
                                 textColor=colors.HexColor("#d4cfee"), spaceAfter=12),
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
                                  textColor=TEAL, spaceAfter=9),
    "team_name": ParagraphStyle("TeamName", fontName="Helvetica-Bold", fontSize=11.2, leading=14,
                                textColor=colors.white, spaceAfter=4),
    "team_id": ParagraphStyle("TeamId", fontName="Helvetica", fontSize=8.2, leading=11,
                              textColor=colors.HexColor("#c7bedf"), spaceAfter=10),
    "team_scope": ParagraphStyle("TeamScope", fontName="Helvetica", fontSize=8.6, leading=12,
                                 textColor=colors.HexColor("#ebe8f6")),
    "code": ParagraphStyle("Code", fontName="Courier", fontSize=7.1, leading=10,
                           textColor=INK),
    "caption": ParagraphStyle("Caption", fontName="Helvetica-Oblique", fontSize=8.3, leading=11.5,
                              textColor=MUTED, spaceAfter=9),
    "metric_number": ParagraphStyle("MetricNumber", fontName="Helvetica-Bold", fontSize=22, leading=26,
                                    textColor=GOLD, spaceAfter=4),
    "metric_label": ParagraphStyle("MetricLabel", fontName="Helvetica", fontSize=8.1, leading=11,
                                   textColor=colors.white),
    "cover_label": ParagraphStyle("CoverLabel", fontName="Helvetica-Bold", fontSize=8, leading=11,
                                   textColor=TEAL, spaceAfter=8),
    "cover_statement": ParagraphStyle("CoverStatement", fontName="Helvetica-Bold", fontSize=14, leading=19,
                                       textColor=colors.white, spaceAfter=9),
    "cover_small": ParagraphStyle("CoverSmall", fontName="Helvetica", fontSize=9.1, leading=13,
                                  textColor=colors.HexColor("#d4cfee")),
    "path_title": ParagraphStyle("PathTitle", fontName="Helvetica-Bold", fontSize=8.3, leading=11,
                                 textColor=INK, spaceAfter=5),
    "path_body": ParagraphStyle("PathBody", fontName="Helvetica", fontSize=7.5, leading=10,
                                textColor=MUTED),
}


def draw_page(canvas, doc):
    width, height = A4
    canvas.saveState()
    if doc.page == 1:
        canvas.setFillColor(DEEP)
        canvas.rect(0, 0, width, height, fill=1, stroke=0)
        canvas.setStrokeColor(colors.HexColor("#2f2752"))
        canvas.setLineWidth(28)
        canvas.circle(width + 22 * mm, 11 * mm, 77 * mm, fill=0, stroke=1)
        canvas.setLineWidth(1.2)
        canvas.setStrokeColor(colors.HexColor("#4b3875"))
        canvas.circle(width + 22 * mm, 11 * mm, 57 * mm, fill=0, stroke=1)
        canvas.setFillColor(TEAL)
        canvas.circle(width - 28 * mm, height - 20 * mm, 2.1 * mm, fill=1, stroke=0)
        canvas.setFillColor(GOLD)
        canvas.circle(width - 20 * mm, height - 20 * mm, 2.1 * mm, fill=1, stroke=0)
    else:
        canvas.setFillColor(colors.HexColor("#fcfbff"))
        canvas.rect(0, 0, width, height, fill=1, stroke=0)
        canvas.setFillColor(DEEP)
        canvas.rect(0, height - 25 * mm, width, 25 * mm, fill=1, stroke=0)
    canvas.setFillColor(GOLD)
    canvas.roundRect(20 * mm, height - 20 * mm, 3 * mm, 11 * mm, 1.5 * mm, fill=1, stroke=0)
    canvas.setFont("Helvetica-Bold", 9)
    canvas.setFillColor(colors.white)
    canvas.drawString(27 * mm, height - 14 * mm, "SERVICEFORCE")
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#c9bff0"))
    canvas.drawRightString(width - 20 * mm, height - 14 * mm, "OBJECT-ORIENTED PROJECT REPORT")
    canvas.setStrokeColor(colors.HexColor("#4b426a") if doc.page == 1 else colors.HexColor("#d9d4e8"))
    canvas.line(20 * mm, 17 * mm, width - 20 * mm, 17 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#c9bff0") if doc.page == 1 else MUTED)
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
    cover_mode = True

    def flush():
        if paragraphs:
            story.append(Paragraph(inline(" ".join(paragraphs)), styles["cover_body" if cover_mode else "body"]))
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
        card_colors = ["#272446", "#173b43", "#322451", "#39304d"]
        for index, (name, student_id, area, scope) in enumerate(team_rows):
            content = [Paragraph(inline(area.upper()), styles["team_kicker"]),
                       Paragraph(inline(name), styles["team_name"]),
                       Paragraph(inline(student_id), styles["team_id"]),
                       Paragraph(inline(scope), styles["team_scope"])]
            card = Table([[content]], colWidths=[225], rowHeights=[126], hAlign="LEFT")
            card.setStyle(TableStyle([
                ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor(card_colors[index])),
                ("BOX", (0, 0), (-1, -1), .8, colors.HexColor("#594b83")),
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
        header = table_rows[0][0]
        widths = ([90, 100, 292] if len(table_rows[0]) == 3 and header == "Stage"
                  else [145, 92, 245] if len(table_rows[0]) == 3 and header == "Question"
                  else [96, 195, 191] if len(table_rows[0]) == 3 and header == "OOP concept"
                  else [178, 93, 211] if len(table_rows[0]) == 3 and header == "Route"
                  else [145, 290, 47] if len(table_rows[0]) == 3
                  else [31, 115, 164, 172])
        table = LongTable(rows, colWidths=widths, repeatRows=1,
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
        story.append(Spacer(1, 3 * mm))
        table_rows.clear()

    code_lines = None
    screens = {
        "SCREEN_EMPLOYEE": "incident-employee.png",
        "SCREEN_TECHNICIAN": "incident-technician.png",
        "SCREEN_ADMIN": "incident-admin.png",
    }
    for raw_line in lines:
        line = raw_line.strip()
        if line.startswith("```"):
            flush()
            if code_lines is None:
                code_lines = []
            else:
                block = Preformatted("\n".join(code_lines), styles["code"])
                box = Table([[block]], colWidths=[doc.width], hAlign="LEFT")
                box.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f7f5fc")),
                    ("BOX", (0, 0), (-1, -1), .5, colors.HexColor("#dcd6eb")),
                    ("LEFTPADDING", (0, 0), (-1, -1), 10),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                    ("TOPPADDING", (0, 0), (-1, -1), 9),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
                ]))
                story.append(box)
                story.append(Spacer(1, 3 * mm))
                code_lines = None
            continue
        if code_lines is not None:
            code_lines.append(raw_line.rstrip())
            continue
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
            elif name == "COVER_SUMMARY":
                panel = Table([[[
                    Paragraph("THE SERVICEFORCE IDEA", styles["cover_label"]),
                    Paragraph("One incident. One shared record. A clear path from report to verified resolution.", styles["cover_statement"]),
                    Paragraph("Three roles  /  two routing strategies  /  two escalation deadlines  /  one auditable timeline", styles["cover_small"]),
                ]]], colWidths=[doc.width], hAlign="LEFT")
                panel.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#292347")),
                    ("LINEBEFORE", (0, 0), (0, 0), 5, GOLD),
                    ("LEFTPADDING", (0, 0), (-1, -1), 18),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 18),
                    ("TOPPADDING", (0, 0), (-1, -1), 16),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 12),
                ]))
                story.append(panel)
            elif name == "AT_A_GLANCE":
                metrics = [("3", "ROLE WORKSPACES"), ("54", "EXECUTED TESTS"),
                           ("2", "ROUTING STRATEGIES"), ("28", "DOCUMENTED CASES")]
                cells = [[Paragraph(value, styles["metric_number"]), Paragraph(label, styles["metric_label"])]
                         for value, label in metrics]
                strip = Table([cells], colWidths=[doc.width / 4] * 4, hAlign="LEFT")
                strip.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, -1), DEEP),
                    ("LINEBELOW", (0, 0), (-1, -1), 2, TEAL),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 12),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                    ("TOPPADDING", (0, 0), (-1, -1), 12),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 12),
                ]))
                story.append(strip)
                story.append(Spacer(1, 5 * mm))
            elif name == "INCIDENT_PATH":
                steps = [("01  REPORT", "Employee describes the issue"),
                         ("02  ROUTE", "Priority, SLA and lead"),
                         ("03  INVESTIGATE", "Team shares work and evidence"),
                         ("04  ESCALATE", "Deadlines trigger action"),
                         ("05  VERIFY", "Reporter confirms the fix")]
                cells = [[Paragraph(title, styles["path_title"]), Paragraph(body, styles["path_body"])]
                         for title, body in steps]
                path = Table([cells], colWidths=[doc.width / 5] * 5, hAlign="LEFT")
                path.setStyle(TableStyle([
                    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#faf8fe")),
                    ("LINEBELOW", (0, 0), (-1, -1), 2, ACCENT),
                    ("LINEBEFORE", (1, 0), (-1, 0), .5, colors.HexColor("#dcd6eb")),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 8),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                    ("TOPPADDING", (0, 0), (-1, -1), 10),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 12),
                ]))
                story.append(path)
                story.append(Spacer(1, 4 * mm))
            elif name in screens:
                path = ROOT / "screenshots" / screens[name]
                picture = Image(str(path), width=doc.width, height=doc.width * 900 / 1440)
                story.append(picture)
                story.append(Spacer(1, 3 * mm))
            else:
                story.append(DIAGRAMS[name]())
                story.append(Spacer(1, 4 * mm))
        elif line == "---":
            flush()
            story.append(PageBreak())
            cover_mode = False
        elif line.startswith("# "):
            flush()
            story.append(Paragraph(inline(line[2:]), styles["cover_title" if cover_mode else "title"]))
        elif line.startswith("## "):
            flush()
            story.append(Paragraph(inline(line[3:]), styles["cover_heading" if cover_mode else "heading"]))
        elif line.startswith("### "):
            flush()
            story.append(Paragraph(inline(line[4:]), styles["subheading"]))
        elif line.startswith("Figure "):
            flush()
            story.append(Paragraph(inline(line), styles["caption"]))
        elif line.startswith("- "):
            flush()
            story.append(Paragraph("<font color=\"#6d4ed6\"><b>\u2022</b></font>  " + inline(line[2:]), styles["bullet"]))
        elif line.startswith("CS F213 / MAC F212 |"):
            flush()
            story.append(Paragraph(inline(line), styles["cover_meta" if cover_mode else "meta"]))
        else:
            paragraphs.append(line)
    flush()
    if code_lines is not None:
        raise ValueError("Unclosed code block in project report")
    flush_team()
    flush_table()
    story.append(Spacer(1, 2 * mm))
    doc.build(story)
    print(OUTPUT)


if __name__ == "__main__":
    build()
