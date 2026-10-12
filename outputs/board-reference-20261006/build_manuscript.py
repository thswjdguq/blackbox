from pathlib import Path
import re
from html import escape
from reportlab.pdfgen.canvas import Canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, KeepTogether
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT = Path(__file__).resolve().parent
pdfmetrics.registerFont(TTFont('Malgun', 'C:/Windows/Fonts/malgun.ttf'))
pdfmetrics.registerFont(TTFont('MalgunBold', 'C:/Windows/Fonts/malgunbd.ttf'))
pdfmetrics.registerFontFamily('Malgun', normal='Malgun', bold='MalgunBold', italic='Malgun', boldItalic='MalgunBold')

styles = {
    'title': ParagraphStyle('title', fontName='MalgunBold', fontSize=20, leading=28, textColor=colors.HexColor('#152A35'), spaceAfter=14, wordWrap='CJK'),
    'board': ParagraphStyle('board', fontName='MalgunBold', fontSize=17, leading=24, spaceAfter=14, wordWrap='CJK'),
    'heading': ParagraphStyle('heading', fontName='MalgunBold', fontSize=11.2, leading=17, spaceBefore=13, spaceAfter=6, keepWithNext=True, wordWrap='CJK'),
    'body': ParagraphStyle('body', fontName='Malgun', fontSize=10.3, leading=16.4, spaceAfter=6, wordWrap='CJK'),
    'bullet': ParagraphStyle('bullet', fontName='Malgun', fontSize=10.3, leading=16.4, spaceAfter=5, leftIndent=10, firstLineIndent=-10, wordWrap='CJK'),
    'label': ParagraphStyle('label', fontName='MalgunBold', fontSize=10.3, leading=16.4, spaceAfter=3, keepWithNext=True, wordWrap='CJK'),
}

def inline(s):
    s = escape(s)
    return re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', s)

lines = (ROOT / 'Team_Blackbox_전시보드_원고.md').read_text(encoding='utf-8').splitlines()
story = []
board_count = 0
for line in lines:
    line = line.strip()
    if not line:
        continue
    if line.startswith('# '):
        story.append(Paragraph(inline(line[2:]), styles['title']))
    elif line.startswith('## '):
        board_count += 1
        if board_count > 1:
            story.append(PageBreak())
        story.append(Paragraph(inline(line[3:]), styles['board']))
    elif line.startswith('### '):
        story.append(Paragraph(inline(line[4:]), styles['heading']))
    elif line.startswith('- '):
        story.append(Paragraph('• ' + inline(line[2:]), styles['bullet']))
    elif re.fullmatch(r'\*\*[^*]+\*\*', line):
        story.append(Paragraph(inline(line), styles['label']))
    else:
        story.append(Paragraph(inline(line), styles['body']))

def footer(c, doc):
    c.saveState()
    c.setFont('Malgun', 8)
    c.setFillColor(colors.HexColor('#68717A'))
    c.drawString(46, 25, 'Team Blackbox  |  전시 보드 원고  |  2026.10.06')
    c.drawRightString(A4[0]-46, 25, str(doc.page))
    c.restoreState()

out = ROOT / 'Team_Blackbox_전시보드_원고.pdf'
doc = SimpleDocTemplate(str(out), pagesize=A4, rightMargin=46, leftMargin=46, topMargin=40, bottomMargin=44,
    title='Team Blackbox 전시 보드 원고', author='Team Blackbox')
doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(out)
