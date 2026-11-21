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
from reportlab.platypus import BaseDocTemplate, Frame, PageBreak, PageTemplate, Paragraph, Spacer


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
    "body": ParagraphStyle("Body", fontName="Helvetica", fontSize=9.6, leading=14.1,
                           textColor=INK, spaceAfter=10),
    "bullet": ParagraphStyle("Bullet", fontName="Helvetica", fontSize=9.3, leading=13.7,
                            textColor=INK, leftIndent=16, firstLineIndent=-11, spaceAfter=7),
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
    for line in lines:
        line = line.strip()
        if not line:
            flush()
        elif line == "---":
            flush()
            story.append(PageBreak())
        elif line.startswith("# "):
            flush()
            story.append(Paragraph(inline(line[2:]), styles["title"]))
        elif line.startswith("## "):
            flush()
            story.append(Paragraph(inline(line[3:]), styles["heading"]))
        elif line.startswith("- "):
            flush()
            story.append(Paragraph("<font color=\"#6d4ed6\"><b>\u2022</b></font>  " + inline(line[2:]), styles["bullet"]))
        elif line.startswith("CS F213 / MAC F212 |"):
            flush()
            story.append(Paragraph(inline(line), styles["meta"]))
        else:
            paragraphs.append(line)
    flush()
    story.append(Spacer(1, 2 * mm))
    doc.build(story)
    print(OUTPUT)


if __name__ == "__main__":
    build()
