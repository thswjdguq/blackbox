"""Team Blackbox — Tech Stack slide (presentation-quality PNG)"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle

plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

# Slide: 16:9, 1600×900 px equivalent
FW, FH = 16, 9
fig, ax = plt.subplots(figsize=(FW, FH), dpi=180)
fig.patch.set_facecolor('white')
ax.set_xlim(0, FW)
ax.set_ylim(0, FH)
ax.axis('off')

# ── colour tokens ─────────────────────────────────────────────────────────────
NAVY   = '#1A3A4A'
TEAL   = '#0D9488'
TEAL_L = '#CCFBF1'
WHITE  = '#FFFFFF'
GRAY1  = '#F8FAFC'   # row odd bg
GRAY2  = '#F1F5F9'   # row even bg
BORDER = '#CBD5E1'
TEXT_D = '#0F172A'
TEXT_M = '#334155'
TEXT_L = '#64748B'

def fbox(x, y, w, h, fc, ec='none', lw=1.0, z=2, r=0.0):
    ax.add_patch(FancyBboxPatch((x, y), w, h,
        boxstyle=f'round,pad={r}' if r else 'square,pad=0',
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z,
        clip_on=False))

def rect(x, y, w, h, fc, ec='none', lw=0.8, z=2):
    ax.add_patch(Rectangle((x, y), w, h,
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z, clip_on=False))

def txt(x, y, s, fs=9, fw='normal', c=TEXT_D, ha='left', va='center', z=5):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw,
            color=c, zorder=z, clip_on=False)

# ── SLIDE BORDER ──────────────────────────────────────────────────────────────
fbox(0, 0, FW, FH, WHITE, BORDER, lw=0.5, z=0)

# ── HEADER ────────────────────────────────────────────────────────────────────
# teal top accent bar
rect(0.5, 8.45, 4.0, 0.04, TEAL, z=4)

txt(0.5, 8.62, 'TECH STACK', fs=8, fw='bold', c=TEAL, ha='left')
txt(0.5, 8.1, '적용 기술  ·  라이브러리  ·  구현 환경',
    fs=18, fw='bold', c=TEXT_D, ha='left')

# ── TABLE HELPER ──────────────────────────────────────────────────────────────
def draw_table(ox, oy, title, rows, col1_w=2.5, col2_w=4.0):
    """
    ox, oy  = top-left origin
    rows    = [(library, description), ...]
    """
    RH = 0.41   # row height
    TH = 0.42   # header height
    TW = col1_w + col2_w

    # section label
    txt(ox, oy + 0.22, title, fs=9.5, fw='bold', c=TEAL, ha='left')
    # underline
    rect(ox, oy + 0.05, TW, 0.025, TEAL, z=4)

    oy -= 0.05   # small gap

    # ── column header ──────────────────────────────────────────────────────────
    rect(ox, oy - TH, TW, TH, NAVY, z=3)
    txt(ox + col1_w/2, oy - TH/2, '라이브러리', fs=9, fw='bold',
        c=WHITE, ha='center', z=5)
    txt(ox + col1_w + col2_w/2, oy - TH/2, '담당 기능', fs=9, fw='bold',
        c=WHITE, ha='center', z=5)
    # vertical divider in header
    rect(ox + col1_w, oy - TH, 0.008, TH, WHITE, z=4)

    # ── rows ──────────────────────────────────────────────────────────────────
    y = oy - TH
    for i, (lib, desc) in enumerate(rows):
        bg = GRAY1 if i % 2 == 0 else GRAY2
        rect(ox, y - RH, TW, RH, bg, BORDER, lw=0.5, z=2)
        # vertical divider
        rect(ox + col1_w, y - RH, 0.008, RH, BORDER, z=3)

        txt(ox + 0.18, y - RH/2, lib,  fs=9,   fw='bold', c=NAVY,   ha='left')
        txt(ox + col1_w + 0.18, y - RH/2, desc, fs=9, fw='normal', c=TEXT_M, ha='left')
        y -= RH

    # outer border
    rect(ox, y, TW, oy - y, 'none', BORDER, lw=1.0, z=4)

# ── BACKEND TABLE ─────────────────────────────────────────────────────────────
be_rows = [
    ('Spring Boot 3.3.5',    'REST API 서버'),
    ('Spring Security',      'JWT 인증 · BCrypt'),
    ('Spring Data JPA',      'ORM · Repository 패턴'),
    ('Flyway',               'V1~V14 DB 마이그레이션'),
    ('OpenPDF 1.3.30',       'SHA-256 무결성 PDF 생성'),
    ('Spring WebFlux',       'Claude · Notion · Google 연동'),
]
draw_table(0.5, 7.55, 'BACKEND', be_rows, col1_w=2.5, col2_w=4.0)

# ── FRONTEND TABLE ────────────────────────────────────────────────────────────
fe_rows = [
    ('Next.js 16.2',     'App Router · SSR · TypeScript'),
    ('Zustand 5.0.3',    '전역 상태 관리 · JWT 저장'),
    ('Axios 1.7.9',      'HTTP 클라이언트 · 인터셉터'),
    ('@dnd-kit 6.3.1',   '칸반 드래그앤드롭'),
    ('Recharts 3.8.1',   '기여도 · 위험도 레이더 차트'),
    ('Tailwind CSS 3.x', '디자인 시스템 · 다크모드'),
]
draw_table(8.2, 7.55, 'FRONTEND', fe_rows, col1_w=2.7, col2_w=4.6)

# ── DEVELOPMENT ENVIRONMENT ───────────────────────────────────────────────────
txt(0.5, 2.62, 'DEVELOPMENT ENVIRONMENT', fs=9.5, fw='bold', c=TEAL, ha='left')
rect(0.5, 2.44, 8.0, 0.025, TEAL, z=4)

env_cards = [
    ('IDE',      'IntelliJ  ·  VS Code'),
    ('빌드',     'Gradle 8  ·  npm'),
    ('버전 관리', 'Git  ·  GitHub'),
    ('컨테이너', 'Docker Compose'),
    ('DB 관리',  'Flyway  ·  DBeaver'),
    ('협업',     'Notion  ·  Discord'),
]

CARD_W = 2.42
CARD_H = 0.95
cx = 0.5
for label, val in env_cards:
    fbox(cx, 1.38, CARD_W, CARD_H, GRAY1, BORDER, lw=0.9, z=2, r=0.05)
    # left accent bar
    rect(cx, 1.38, 0.06, CARD_H, TEAL, z=4)
    txt(cx + 0.22, 1.38 + CARD_H * 0.67, label, fs=8.5, fw='bold', c=NAVY, ha='left')
    txt(cx + 0.22, 1.38 + CARD_H * 0.30, val,   fs=8.5, fw='normal', c=TEXT_M, ha='left')
    cx += CARD_W + 0.1

# ── FOOTER ────────────────────────────────────────────────────────────────────
rect(0, 0, FW, 0.55, NAVY, z=3)
txt(0.5,  0.27, 'Team Blackbox  ·  손에 손잡고', fs=8.5, c='#94A3B8', ha='left')
txt(FW-0.5, 0.27, '7  /  14', fs=8.5, c='#94A3B8', ha='right')

plt.tight_layout(pad=0)
plt.savefig('md/techstack.png', dpi=180, bbox_inches='tight',
            facecolor='white')
print("Saved: md/techstack.png")
plt.close()
