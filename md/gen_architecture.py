"""Team Blackbox — Software Architecture diagram"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

FW, FH = 24, 17
fig, ax = plt.subplots(figsize=(FW, FH), dpi=150)
fig.patch.set_facecolor('#F8FAFC')
ax.set_xlim(0, FW)
ax.set_ylim(0, FH)
ax.axis('off')

def fbox(x, y, w, h, fc, ec, lw=1.8, z=2, pad=0.12):
    ax.add_patch(FancyBboxPatch((x, y), w, h,
                  boxstyle=f'round,pad={pad}',
                  facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z))

def txt(x, y, s, fs=10, fw='normal', c='#1E293B', ha='center', va='center'):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw, color=c, zorder=6)

def arr_v(x, y1, y2, color='#64748B', lw=1.8, label=''):
    ax.annotate('', xy=(x, y2), xytext=(x, y1),
                arrowprops=dict(arrowstyle='-|>', color=color, lw=lw, mutation_scale=16), zorder=5)
    if label:
        txt(x + 0.25, (y1+y2)/2, label, fs=8, c=color, ha='left')

def arr_h(x1, x2, y, color='#64748B', lw=1.8, label=''):
    ax.annotate('', xy=(x2, y), xytext=(x1, y),
                arrowprops=dict(arrowstyle='-|>', color=color, lw=lw, mutation_scale=16), zorder=5)
    if label:
        txt((x1+x2)/2, y+0.2, label, fs=8, c=color)

# ─── Title ────────────────────────────────────────────────────────────────────
txt(12, 16.5, 'Team Blackbox  —  Software Architecture', fs=17, fw='bold', c='#0F172A')

# ─── Browser ─────────────────────────────────────────────────────────────────
fbox(7.0, 14.7, 10.0, 1.3, '#DBEAFE', '#3B82F6', lw=2.2, z=4)
txt(12.0, 15.35, 'Browser  (Chrome / Edge / Safari)', fs=13, fw='bold', c='#1E40AF')
arr_v(12.0, 14.7, 13.9, '#3B82F6', lw=2.2, label='REST API over HTTPS')

# ─── Docker Compose outer boundary ───────────────────────────────────────────
fbox(0.3, 1.2, 23.4, 12.5, '#F1F5F9', '#94A3B8', lw=2, z=1, pad=0.2)
txt(1.2, 13.45, '[Docker] Docker Compose', fs=11, fw='bold', c='#64748B', ha='left')

# ─── Nginx ───────────────────────────────────────────────────────────────────
fbox(0.8, 12.2, 22.4, 1.5, '#FEF3C7', '#D97706', lw=2, z=4)
txt(12.0, 13.1, 'Nginx  ·  리버스 프록시  ·  :80 / :443', fs=13, fw='bold', c='#92400E')
txt(12.0, 12.55,
    '/              →  frontend:3000          '
    '/api/*       →  backend:8080          '
    '/uploads/*  →  static files',
    fs=9, c='#78350F')

# Nginx → containers
arr_v(4.3,  12.2, 11.5, '#D97706', lw=1.8)
arr_v(12.0, 12.2, 11.5, '#D97706', lw=1.8)

# ─── Frontend ────────────────────────────────────────────────────────────────
fbox(0.8, 4.8, 7.0, 6.7, '#EFF6FF', '#3B82F6', lw=1.8, z=3)
fbox(0.8, 10.6, 7.0, 0.9, '#2563EB', '#1D4ED8', lw=0, z=4)
txt(4.3, 11.05, 'Frontend  :3000', fs=12, fw='bold', c='white')

fe = [
    ('Next.js 14',       'App Router · TypeScript'),
    ('Tailwind CSS',     'shadcn/ui 컴포넌트'),
    ('Zustand',          '전역 상태 관리'),
    ('@dnd-kit',         '칸반 드래그앤드롭'),
    ('Recharts',         '기여도 · 위험도 차트'),
    ('Axios',            'JWT 인터셉터'),
]
for i, (name, sub) in enumerate(fe):
    y = 10.0 - i * 0.85
    txt(1.5, y, name, fs=9, fw='bold', c='#1D4ED8', ha='left')
    txt(5.5, y, sub, fs=8.5, c='#3B82F6', ha='right')

# ─── Backend ─────────────────────────────────────────────────────────────────
fbox(8.5, 4.8, 7.5, 6.7, '#F0FDF4', '#059669', lw=1.8, z=3)
fbox(8.5, 10.6, 7.5, 0.9, '#047857', '#065F46', lw=0, z=4)
txt(12.25, 11.05, 'Backend  :8080', fs=12, fw='bold', c='white')

be = [
    ('Spring Boot 3.x',     'Java 17  ·  JTE 21'),
    ('Spring Security',      'JWT Access 30min + Refresh 7d'),
    ('Spring JPA',           'Flyway V1~V14 마이그레이션'),
    ('Claude API',           'AI 요약 · 액션아이템 · 일정추천'),
    ('Google Calendar API',  'OAuth2 · freeBusy · 이벤트 등록'),
    ('Notion API',           '회의록 · 칸반 자동 동기화'),
]
for i, (name, sub) in enumerate(be):
    y = 10.0 - i * 0.85
    txt(9.2, y, name, fs=9, fw='bold', c='#065F46', ha='left')
    txt(15.7, y, sub, fs=8.5, c='#059669', ha='right')

# ─── PostgreSQL ───────────────────────────────────────────────────────────────
fbox(17.0, 8.2, 6.0, 3.3, '#FFF7ED', '#EA580C', lw=1.8, z=3)
fbox(17.0, 10.6, 6.0, 0.9, '#EA580C', '#C2410C', lw=0, z=4)
txt(20.0, 11.05, 'PostgreSQL 16', fs=12, fw='bold', c='white')
db_info = ['Docker Volume  pgdata', '14 tables · Flyway 마이그레이션',
           'UUID PK · JSONB · Triggers']
for i, s in enumerate(db_info):
    txt(20.0, 9.95 - i * 0.58, s, fs=9, c='#7C2D12')

# JDBC arrow
arr_h(16.0, 17.0, 9.6, '#EA580C', lw=1.8, label='JDBC')

# ─── File Storage ─────────────────────────────────────────────────────────────
fbox(17.0, 4.8, 6.0, 2.9, '#FDF4FF', '#A855F7', lw=1.8, z=3)
fbox(17.0, 6.8, 6.0, 0.9, '#A855F7', '#7E22CE', lw=0, z=4)
txt(20.0, 7.25, 'File Storage', fs=12, fw='bold', c='white')
fs_info = ['Docker Volume  uploads', '/data/uploads/{projectId}/',
           'SHA-256 해시 고정 · INSERT 전용']
for i, s in enumerate(fs_info):
    txt(20.0, 6.35 - i * 0.58, s, fs=9, c='#581C87')

arr_h(16.0, 17.0, 6.2, '#A855F7', lw=1.8, label='R/W')

# ─── External APIs (bottom) ───────────────────────────────────────────────────
ax.plot([0.6, 23.4], [4.4, 4.4], color='#CBD5E1', lw=1.2, ls='--', zorder=2)
txt(1.2, 4.15, '외부 서비스 연동', fs=9.5, fw='bold', c='#94A3B8', ha='left')

ext = [
    (4.1,  'Claude API\n(Anthropic)',        '#FEF2F2', '#DC2626', '#991B1B',  9.0),
    (12.0, 'Google Calendar API',            '#EFF6FF', '#3B82F6', '#1E40AF', 11.0),
    (19.5, 'Notion API',                     '#F5F3FF', '#8B5CF6', '#4C1D95',  7.5),
]

# backend bottom center → fan to each API
bx_bottom = 12.25  # backend center x
by_bottom = 4.8    # backend y bottom

for api_x, label, fc, ec, tc, box_w in ext:
    fbox(api_x - box_w/2, 1.4, box_w, 2.4, fc, ec, lw=1.5, z=3)
    lines = label.split('\n')
    if len(lines) == 2:
        txt(api_x, 2.95, lines[0], fs=10.5, fw='bold', c=tc)
        txt(api_x, 2.45, lines[1], fs=9, c=tc)
    else:
        txt(api_x, 2.7, label, fs=10.5, fw='bold', c=tc)

    # connector line from backend bottom to API top
    ax.annotate('', xy=(api_x, 3.8), xytext=(bx_bottom, by_bottom),
                arrowprops=dict(
                    arrowstyle='-|>', color=ec, lw=1.3,
                    connectionstyle='arc3,rad=0.0',
                    shrinkA=3, shrinkB=3,
                ), zorder=4)

plt.tight_layout(pad=0.4)
plt.savefig('md/architecture.png', dpi=150, bbox_inches='tight',
            facecolor=fig.get_facecolor())
print("Saved: md/architecture.png")
plt.close()
