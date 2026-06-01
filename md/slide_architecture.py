"""Slide: Software Architecture (full 16:9 slide PNG)"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from slide_template import *

fig, ax = new_slide()
slide_header(ax, 'SOFTWARE ARCHITECTURE', '소프트웨어 아키텍처', page=8)
slide_footer(ax, page=8)

# ── helpers ───────────────────────────────────────────────────────────────────
def t(x, y, s, fs=9, fw='normal', c=TEXT_D, ha='center', va='center'):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw, color=c, zorder=6)

def arr_v(x, y1, y2, color='#64748B', lw=1.6):
    ax.annotate('', xy=(x, y2), xytext=(x, y1),
                arrowprops=dict(arrowstyle='-|>', color=color, lw=lw, mutation_scale=14), zorder=5)

def arr_h(x1, x2, y, color='#64748B', lw=1.4, label=''):
    ax.annotate('', xy=(x2, y), xytext=(x1, y),
                arrowprops=dict(arrowstyle='-|>', color=color, lw=lw, mutation_scale=14), zorder=5)
    if label:
        t((x1+x2)/2, y + 0.18, label, fs=7.5, c=color)

# ── browser ───────────────────────────────────────────────────────────────────
fbox(ax, 5.5, 7.3, 5.0, 0.62, '#DBEAFE', '#3B82F6', lw=2, z=4, r=0.07)
t(8.0, 7.62, 'Browser  (Chrome / Edge / Safari)', fs=11, fw='bold', c='#1E40AF')

arr_v(8.0, 7.3, 6.82, '#3B82F6', lw=1.8)
t(8.55, 7.06, 'REST API over HTTPS', fs=8, c='#3B82F6', ha='left')

# ── docker outer ──────────────────────────────────────────────────────────────
fbox(ax, 0.3, 1.0, 15.4, 5.7, GRAY1, BORDER, lw=1.8, z=1, r=0.08)
t(0.65, 6.45, '[Docker Compose]', fs=8.5, fw='bold', c=TEXT_L, ha='left')

# ── nginx ─────────────────────────────────────────────────────────────────────
fbox(ax, 0.6, 5.9, 14.8, 0.88, '#FEF3C7', '#D97706', lw=1.8, z=4, r=0.06)
t(8.0, 6.46, 'Nginx  ·  리버스 프록시  ·  :80 / :443', fs=11.5, fw='bold', c='#92400E')
t(8.0, 6.05, '/  →  frontend:3000               /api/*  →  backend:8080               /uploads/*  →  static files', fs=8.5, c='#78350F')

arr_v(3.5,  5.9, 5.42, '#D97706', lw=1.5)
arr_v(8.25, 5.9, 5.42, '#D97706', lw=1.5)

# ── frontend ──────────────────────────────────────────────────────────────────
fbox(ax, 0.6, 1.5, 5.8, 3.92, '#EFF6FF', '#3B82F6', lw=1.6, z=3, r=0.06)
fbox(ax, 0.6, 4.9, 5.8, 0.52, '#2563EB', '#1D4ED8', lw=0, z=4, r=0.06)
t(3.5, 5.16, 'Frontend  :3000', fs=11, fw='bold', c=WHITE)

fe = [('Next.js 16.2',    'App Router · TypeScript'),
      ('Tailwind CSS 3.x', 'shadcn/ui · 다크모드'),
      ('Zustand 5.0.3',   '전역 상태 · JWT'),
      ('@dnd-kit 6.3.1',  '칸반 드래그앤드롭'),
      ('Recharts 3.8.1',  '기여도 · 위험도 차트'),
      ('Axios 1.7.9',     'HTTP · 인터셉터')]
for i, (name, sub) in enumerate(fe):
    y = 4.55 - i * 0.49
    t(1.0,  y, name, fs=8.5, fw='bold', c='#1D4ED8', ha='left')
    t(6.1,  y, sub,  fs=8,   c='#3B82F6', ha='right')

# ── backend ───────────────────────────────────────────────────────────────────
fbox(ax, 7.0, 1.5, 6.2, 3.92, '#F0FDF4', '#059669', lw=1.6, z=3, r=0.06)
fbox(ax, 7.0, 4.9, 6.2, 0.52, '#047857', '#065F46', lw=0, z=4, r=0.06)
t(10.1, 5.16, 'Backend  :8080', fs=11, fw='bold', c=WHITE)

be = [('Spring Boot 3.3.5',   'Java 17 · REST API'),
      ('Spring Security',      'JWT Access 30min + Refresh 7d'),
      ('Spring JPA + Flyway',  'ORM · V1~V14 마이그레이션'),
      ('Claude API',           'AI 요약 · 액션아이템 · 일정추천'),
      ('Google Calendar API',  'OAuth2 · freeBusy · 이벤트 등록'),
      ('Notion API',           '회의록 · 칸반 자동 동기화')]
for i, (name, sub) in enumerate(be):
    y = 4.55 - i * 0.49
    t(7.4,  y, name, fs=8.5, fw='bold', c='#065F46', ha='left')
    t(12.9, y, sub,  fs=8,   c='#059669', ha='right')

# ── db + file storage ─────────────────────────────────────────────────────────
fbox(ax, 13.5, 3.3, 2.0, 2.1, '#FFF7ED', '#EA580C', lw=1.6, z=3, r=0.06)
fbox(ax, 13.5, 4.9, 2.0, 0.52, '#EA580C', '#C2410C', lw=0, z=4, r=0.06)
t(14.5, 5.16, 'PostgreSQL 16', fs=9, fw='bold', c=WHITE)
for i, s in enumerate(['Docker Volume', 'pgdata', '14 tables', 'Flyway V14']):
    t(14.5, 4.6 - i * 0.35, s, fs=8, c='#7C2D12')

arr_h(13.2, 13.5, 4.0, '#EA580C', lw=1.4, label='JDBC')

fbox(ax, 13.5, 1.5, 2.0, 1.5, '#FDF4FF', '#A855F7', lw=1.6, z=3, r=0.06)
fbox(ax, 13.5, 2.5, 2.0, 0.52, '#A855F7', '#7E22CE', lw=0, z=4, r=0.06)
t(14.5, 2.76, 'File Storage', fs=9, fw='bold', c=WHITE)
for i, s in enumerate(['Docker Volume', '/data/uploads', 'SHA-256 고정']):
    t(14.5, 2.2 - i * 0.35, s, fs=8, c='#581C87')

arr_h(13.2, 13.5, 2.2, '#A855F7', lw=1.4, label='R/W')

# ── external APIs ─────────────────────────────────────────────────────────────
ax.plot([0.6, 15.2], [1.38, 1.38], color=BORDER, lw=1.0, ls='--', zorder=3)
t(0.7, 1.18, '외부 서비스', fs=8, fw='bold', c=TEXT_L, ha='left')

ext = [(2.8, 'Claude API\n(Anthropic)', '#FEF2F2', '#DC2626', '#991B1B'),
       (7.1, 'Google Calendar API',     '#EFF6FF', '#3B82F6', '#1E40AF'),
       (11.4,'Notion API',              '#F5F3FF', '#8B5CF6', '#4C1D95')]
bx = 10.1   # backend center x

for api_x, label, fc, ec, tc in ext:
    fbox(ax, api_x - 2.2, 0.6, 4.4, 0.72, fc, ec, lw=1.3, z=3, r=0.05)
    lines = label.split('\n')
    if len(lines) == 2:
        t(api_x, 1.1, lines[0], fs=9, fw='bold', c=tc)
        t(api_x, 0.82, lines[1], fs=8, c=tc)
    else:
        t(api_x, 0.96, label, fs=9, fw='bold', c=tc)
    ax.annotate('', xy=(api_x, 1.32), xytext=(bx, 1.5),
                arrowprops=dict(arrowstyle='-|>', color=ec, lw=1.1,
                                connectionstyle='arc3,rad=0', shrinkA=3, shrinkB=3), zorder=4)

save_slide(fig, 'md/slide_architecture.png')
