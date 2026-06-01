"""Team Blackbox — Use Case Diagram"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Ellipse

plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

FW, FH = 26, 20
fig, ax = plt.subplots(figsize=(FW, FH), dpi=150)
fig.patch.set_facecolor('#F8FAFC')
ax.set_xlim(0, FW)
ax.set_ylim(0, FH)
ax.axis('off')

# ─── helpers ──────────────────────────────────────────────────────────────────
def fbox(x, y, w, h, fc, ec, lw=1.5, z=2, pad=0.12):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=f'round,pad={pad}',
                  facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z))

def txt(x, y, s, fs=10, fw='normal', c='#1E293B', ha='center', va='center'):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw, color=c, zorder=7)

def usecase(cx, cy, w, h, label, fc='#F8FAFC', ec='#64748B'):
    """Draw an ellipse use-case"""
    ell = Ellipse((cx, cy), width=w, height=h,
                  facecolor=fc, edgecolor=ec, linewidth=1.3, zorder=4)
    ax.add_patch(ell)
    lines = label.split('\n')
    if len(lines) == 1:
        txt(cx, cy, label, fs=8.5, c='#1E293B')
    else:
        txt(cx, cy + 0.18, lines[0], fs=8.5, c='#1E293B')
        txt(cx, cy - 0.22, lines[1], fs=7.5, c='#475569')

def actor(x, y, label, sub=''):
    """Draw stick figure actor"""
    # head
    head = plt.Circle((x, y + 1.15), 0.32, color='#475569', fill=True, zorder=5)
    ax.add_patch(head)
    head2 = plt.Circle((x, y + 1.15), 0.32, color='white', fill=True, zorder=5)
    ax.add_patch(head2)
    head3 = plt.Circle((x, y + 1.15), 0.30, color='#64748B', fill=False, linewidth=1.8, zorder=6)
    ax.add_patch(head3)
    # body
    ax.plot([x, x], [y + 0.83, y + 0.1], color='#64748B', lw=1.8, zorder=5)
    # arms
    ax.plot([x - 0.45, x + 0.45], [y + 0.6, y + 0.6], color='#64748B', lw=1.8, zorder=5)
    # legs
    ax.plot([x, x - 0.4], [y + 0.1, y - 0.35], color='#64748B', lw=1.8, zorder=5)
    ax.plot([x, x + 0.4], [y + 0.1, y - 0.35], color='#64748B', lw=1.8, zorder=5)
    # label
    txt(x, y - 0.65, label, fs=10, fw='bold', c='#1E293B')
    if sub:
        txt(x, y - 1.0, sub, fs=8.5, c='#64748B')

def conn(x1, y1, x2, y2, color='#94A3B8', ls='-', lw=1.0):
    ax.plot([x1, x2], [y1, y2], color=color, lw=lw, ls=ls, zorder=3)

# ─── Title ────────────────────────────────────────────────────────────────────
txt(13, 19.4, 'Team Blackbox  —  Use Case Diagram', fs=17, fw='bold', c='#0F172A')

# ─── System boundary ──────────────────────────────────────────────────────────
fbox(3.8, 0.8, 18.4, 17.8, '#FFFFFF', '#334155', lw=2.5, z=1, pad=0.2)
txt(5.0, 18.25, '<<system>>', fs=9, c='#64748B', ha='left')
txt(7.5, 18.25, 'Team Blackbox Platform', fs=12, fw='bold', c='#0F172A', ha='left')

# ─── Feature group boxes ──────────────────────────────────────────────────────
groups = [
    # (x, y, w, h, title, fc, ec, tc)
    (4.2,  14.8, 8.2, 3.5, '[Auth] 인증 / 프로필',    '#EEF2FF', '#6366F1', '#3730A3'),
    (12.8, 14.8, 9.0, 3.5, '[Proj] 프로젝트 관리',    '#ECFDF5', '#059669', '#065F46'),
    (4.2,  9.8,  8.2, 4.6, '[Task] 칸반 보드',        '#FFF7ED', '#D97706', '#92400E'),
    (12.8, 9.8,  9.0, 4.6, '[Meet] 회의록',           '#F5F3FF', '#7C3AED', '#5B21B6'),
    (4.2,  4.5,  8.2, 4.9, '[Vault] Hash Vault',       '#FEF2F2', '#DC2626', '#991B1B'),
    (12.8, 4.5,  9.0, 4.9, '[Score] 기여도 / 분석',    '#ECFEFF', '#0891B2', '#164E63'),
]
for x, y, w, h, title, fc, ec, tc in groups:
    fbox(x, y, w, h, fc, ec, lw=1.5, z=2)
    fbox(x, y + h - 0.7, w, 0.7, ec, ec, lw=0, z=3)
    txt(x + w/2, y + h - 0.35, title, fs=10, fw='bold', c='white')

# ─── Use Cases per group ──────────────────────────────────────────────────────

# 인증 / 프로필 (x=4.2-12.4, y=14.8-18.3)
auth_uc = [
    (5.6,  16.8, '회원가입'),
    (8.3,  16.8, '로그인'),
    (5.6,  15.6, '프로필 수정'),
    (8.3,  15.6, '비밀번호 변경'),
]
for cx, cy, label in auth_uc:
    usecase(cx, cy, 2.6, 0.7, label, '#EEF2FF', '#6366F1')

# 프로젝트 관리 (x=12.8-21.8, y=14.8-18.3)
proj_uc = [
    (14.5, 17.2, '프로젝트 생성\n★ 팀장 전용'),
    (17.3, 17.2, '초대코드 참여'),
    (20.1, 17.2, '멤버 관리\n★ 팀장 전용'),
    (14.5, 15.7, 'Google Calendar\n연동'),
    (17.3, 15.7, '프로젝트 삭제\n★ 팀장 전용'),
    (20.1, 15.7, '일정 조율 페이지'),
]
for cx, cy, label in proj_uc:
    fc = '#D1FAE5' if '팀장' in label else '#ECFDF5'
    ec = '#059669' if '팀장' in label else '#6EE7B7'
    usecase(cx, cy, 2.6, 0.75, label, fc, ec)

# 칸반 보드 (x=4.2-12.4, y=9.8-14.4)
kanban_uc = [
    (5.5,  13.2, '태스크 생성'),
    (8.4,  13.2, '태스크 수정'),
    (5.5,  12.1, '담당자 지정'),
    (8.4,  12.1, '우선순위 설정'),
    (5.5,  11.0, '상태 변경\nDONE 처리'),
    (8.4,  11.0, 'Notion\n태스크 동기화'),
    (7.0,  10.0, '프로젝트 간\n이동 드롭다운'),
]
for cx, cy, label in kanban_uc:
    usecase(cx, cy, 2.6, 0.75, label, '#FFF7ED', '#D97706')

# 회의록 (x=12.8-21.8, y=9.8-14.4)
meeting_uc = [
    (14.5, 13.5, '회의 생성\n★ 팀장 전용'),
    (17.3, 13.5, '체크인'),
    (20.1, 13.5, '참석자 관리'),
    (14.5, 12.2, '회의록 작성'),
    (17.3, 12.2, 'AI 요약\n★ Claude API'),
    (20.1, 12.2, 'Notion 내보내기\n★ Notion API'),
    (17.3, 11.0, '액션아이템\n→ 태스크 생성'),
    (20.1, 11.0, '체크인 코드\n재생성'),
    (14.5, 11.0, 'AI 일정 추천\n★ Claude + GCal'),
]
for cx, cy, label in meeting_uc:
    fc = '#EDE9FE' if '✨' in label or '팀장' in label else '#F5F3FF'
    usecase(cx, cy, 2.6, 0.75, label, fc, '#7C3AED')

# Hash Vault (x=4.2-12.4, y=4.5-9.4)
vault_uc = [
    (5.5,  8.3, '파일 업로드'),
    (8.4,  8.3, '파일 다운로드'),
    (5.5,  7.2, 'SHA-256\n해시 확인'),
    (8.4,  7.2, '변조 이력\n확인'),
    (5.5,  6.1, '버전 이력\n타임라인'),
    (8.4,  6.1, '변조 감지\n알림'),
    (7.0,  5.1, '파일 변경 내역\n비교'),
]
for cx, cy, label in vault_uc:
    usecase(cx, cy, 2.6, 0.75, label, '#FEF2F2', '#DC2626')

# 기여도 / 분석 (x=12.8-21.8, y=4.5-9.4)
analytics_uc = [
    (14.5, 8.5, '기여도 점수\n조회'),
    (17.3, 8.5, '참여 여부\n분석'),
    (20.1, 8.5, '마감 위험도\n예측'),
    (14.5, 7.3, '기여도\n재계산'),
    (17.3, 7.3, '경보 확인\n(FREE_RIDE 등)'),
    (20.1, 7.3, '가중치 설정\n★ 팀장 전용'),
    (14.5, 6.1, 'PDF 리포트\n발급'),
    (17.3, 6.1, '증거 패키지\nZIP ★ 팀장'),
    (20.1, 6.1, 'Notion 내보내기'),
]
for cx, cy, label in analytics_uc:
    usecase(cx, cy, 2.6, 0.75, label, '#ECFEFF', '#0891B2')

# ─── Actors ───────────────────────────────────────────────────────────────────
# 팀원 (left)
actor(1.8, 14.5, '팀원', '(MEMBER)')
# 팀장 (left)
actor(1.8, 9.2, '팀장', '(LEADER)')

# External actors (right)
actor(24.2, 15.5, 'Claude API', '(Anthropic AI)')
actor(24.2, 12.0, 'Google', 'Calendar API')
actor(24.2, 8.5,  'Notion API', '')

# ─── Actor → System boundary lines ────────────────────────────────────────────
# 팀원 → system
conn(2.4, 15.5, 3.8, 15.5, '#94A3B8', lw=1.2)
conn(2.4, 15.5, 3.8, 12.5, '#94A3B8', lw=1.2)
conn(2.4, 15.5, 3.8, 7.5,  '#94A3B8', lw=1.2)
conn(2.4, 15.5, 3.8, 6.0,  '#94A3B8', lw=1.2)

# 팀장 → system
conn(2.4, 10.2, 3.8, 10.2, '#059669', lw=1.2, ls='--')
conn(2.4, 10.2, 4.2, 7.8,  '#059669', lw=1.2, ls='--')

# External → system
conn(23.6, 16.0, 22.2, 14.5, '#DC2626', lw=1.2, ls='--')  # Claude → 회의록 AI
conn(23.6, 16.0, 22.2, 11.0, '#DC2626', lw=1.2, ls='--')  # Claude → 칸반
conn(23.6, 12.5, 22.2, 12.5, '#3B82F6', lw=1.2, ls='--')  # GCal → 프로젝트
conn(23.6,  9.0, 22.2, 11.0, '#8B5CF6', lw=1.2, ls='--')  # Notion → 회의록
conn(23.6,  9.0, 22.2,  7.0, '#8B5CF6', lw=1.2, ls='--')  # Notion → 기여도

# ─── Legend ───────────────────────────────────────────────────────────────────
legend_data = [
    ('#EDE9FE', '#7C3AED', '★  팀장 전용 기능'),
    ('#D1FAE5', '#059669', '★  AI / 외부 API 연동'),
]
for i, (fc, ec, label) in enumerate(legend_data):
    lx, ly = 4.5 + i * 6.5, 1.55
    fbox(lx, ly - 0.25, 0.55, 0.5, fc, ec, lw=1.2, z=5)
    txt(lx + 3.2, ly + 0.0, label, fs=9.5, fw='bold', c='#1E293B', ha='center')

plt.tight_layout(pad=0.4)
plt.savefig('md/usecase.png', dpi=150, bbox_inches='tight',
            facecolor=fig.get_facecolor())
print("Saved: md/usecase.png")
plt.close()
