"""Slide: Use Case Diagram (full 16:9 slide PNG)"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from slide_template import *
from matplotlib.patches import Ellipse

fig, ax = new_slide()
slide_header(ax, 'USE CASE DIAGRAM', '유스케이스 다이어그램', page=9)
slide_footer(ax, page=9)

def t(x, y, s, fs=8, fw='normal', c=TEXT_D, ha='center', va='center'):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw, color=c, zorder=7)

def uc(cx, cy, w, h, label, fc, ec):
    ax.add_patch(Ellipse((cx, cy), width=w, height=h,
                          facecolor=fc, edgecolor=ec, linewidth=1.1, zorder=4))
    lines = label.split('\n')
    if len(lines) == 1:
        t(cx, cy, label, fs=7.2)
    else:
        t(cx, cy + h*0.18, lines[0], fs=7.2)
        t(cx, cy - h*0.22, lines[1], fs=6.5, c='#475569')

def actor(x, y, label, sub=''):
    c = plt.Circle((x, y + 0.58), 0.16, color='#64748B', fill=False, lw=1.5, zorder=5)
    ax.add_patch(c)
    ax.plot([x, x],         [y+0.42, y+0.12], color='#64748B', lw=1.5, zorder=5)
    ax.plot([x-0.22, x+0.22],[y+0.3, y+0.3],  color='#64748B', lw=1.5, zorder=5)
    ax.plot([x, x-0.2],    [y+0.12, y-0.15], color='#64748B', lw=1.5, zorder=5)
    ax.plot([x, x+0.2],    [y+0.12, y-0.15], color='#64748B', lw=1.5, zorder=5)
    t(x, y - 0.35, label, fs=8, fw='bold')
    if sub: t(x, y - 0.55, sub, fs=7, c=TEXT_L)

def conn(x1, y1, x2, y2, color='#94A3B8', ls='-', lw=0.9):
    ax.plot([x1, x2], [y1, y2], color=color, lw=lw, ls=ls, zorder=3)

# ── system boundary ───────────────────────────────────────────────────────────
fbox(ax, 2.5, 0.65, 11.0, 7.3, WHITE, '#334155', lw=2.2, z=1, r=0.1)
t(3.1, 7.68, '<<system>>', fs=7.5, c=TEXT_L, ha='left')
t(4.0, 7.68, 'Team Blackbox Platform', fs=9, fw='bold', c=TEXT_D, ha='left')

# ── 6 feature groups ─────────────────────────────────────────────────────────
G = [
    (2.65, 5.35, 5.4, 2.4,  '[Auth] 인증 / 프로필',   '#EEF2FF', '#6366F1', '#3730A3'),
    (8.3,  5.35, 5.05, 2.4, '[Proj] 프로젝트 관리',   '#ECFDF5', '#059669', '#065F46'),
    (2.65, 3.02, 5.4, 2.1,  '[Task] 칸반 보드',       '#FFF7ED', '#D97706', '#92400E'),
    (8.3,  3.02, 5.05, 2.1, '[Meet] 회의록',          '#F5F3FF', '#7C3AED', '#5B21B6'),
    (2.65, 0.75, 5.4, 2.05, '[Vault] Hash Vault',     '#FEF2F2', '#DC2626', '#991B1B'),
    (8.3,  0.75, 5.05, 2.05,'[Score] 기여도 / 분석',  '#ECFEFF', '#0891B2', '#164E63'),
]
for x, y, w, h, title, fc, ec, tc in G:
    fbox(ax, x, y, w, h, fc, ec, lw=1.3, z=2, r=0.06)
    fbox(ax, x, y + h - 0.42, w, 0.42, ec, ec, lw=0, z=3, r=0.06)
    t(x + w/2, y + h - 0.21, title, fs=8, fw='bold', c=WHITE)

# ── use cases ─────────────────────────────────────────────────────────────────
# Auth
for cx, cy, lb in [(3.6,6.7,'회원가입'),(5.5,6.7,'로그인'),(3.6,5.9,'프로필 수정'),(5.5,5.9,'비밀번호 변경')]:
    uc(cx, cy, 1.65, 0.48, lb, '#EEF2FF', '#6366F1')

# Project
for cx, cy, lb in [(9.1,6.9,'프로젝트 생성\n★팀장'),(10.9,6.9,'초대코드 참여'),(12.7,6.9,'멤버 관리\n★팀장'),
                   (9.1,5.95,'구글 캘린더 연동'),(10.9,5.95,'일정 조율'),(12.7,5.95,'프로젝트 삭제\n★팀장')]:
    fc = '#D1FAE5' if '★' in lb else '#ECFDF5'
    uc(cx, cy, 1.6, 0.48, lb, fc, '#059669')

# Kanban
for cx, cy, lb in [(3.5,4.6,'태스크 생성'),(5.4,4.6,'태스크 수정'),(3.5,3.9,'담당자 지정'),
                   (5.4,3.9,'상태 변경\nDONE'),(4.4,3.25,'Notion 동기화')]:
    uc(cx, cy, 1.6, 0.46, lb, '#FFF7ED', '#D97706')

# Meeting
for cx, cy, lb in [(9.0,4.6,'회의 생성\n★팀장'),(10.8,4.6,'체크인'),(12.7,4.6,'회의록 작성'),
                   (9.0,3.9,'AI 요약\nClaude'),(10.8,3.9,'액션아이템\n→태스크'),(12.7,3.9,'Notion 내보내기'),
                   (10.8,3.25,'AI 일정추천\nClaude+GCal')]:
    fc = '#EDE9FE' if 'Claude' in lb or 'Notion' in lb or '★' in lb else '#F5F3FF'
    uc(cx, cy, 1.6, 0.46, lb, fc, '#7C3AED')

# Vault
for cx, cy, lb in [(3.5,2.26,'파일 업로드'),(5.4,2.26,'파일 다운로드'),(3.5,1.58,'SHA-256 확인'),
                   (5.4,1.58,'변조 이력'),(4.4,1.0,'버전 타임라인')]:
    uc(cx, cy, 1.6, 0.44, lb, '#FEF2F2', '#DC2626')

# Analytics
for cx, cy, lb in [(9.0,2.2,'기여도 조회'),(10.8,2.2,'참여 여부 분석'),(12.7,2.2,'위험도 예측'),
                   (9.0,1.5,'재계산'),(10.8,1.5,'경보 확인'),(12.7,1.5,'가중치\n★팀장'),
                   (9.9,0.95,'PDF 리포트'),(11.8,0.95,'증거 패키지 ZIP\n★팀장')]:
    fc = '#CFFAFE' if '★' in lb else '#ECFEFF'
    uc(cx, cy, 1.6, 0.44, lb, fc, '#0891B2')

# ── actors ────────────────────────────────────────────────────────────────────
actor(1.5, 6.2, '팀원', '(MEMBER)')
actor(1.5, 3.5, '팀장', '(LEADER)')
actor(14.3, 6.5, 'Claude API', '(AI)')
actor(14.3, 4.8, 'Google', 'Calendar')
actor(14.3, 3.2, 'Notion API', '')

# ── connections ───────────────────────────────────────────────────────────────
conn(1.85, 6.75, 2.5, 6.5,  '#94A3B8', lw=1.0)
conn(1.85, 6.75, 2.5, 4.5,  '#94A3B8', lw=1.0)
conn(1.85, 6.75, 2.5, 2.0,  '#94A3B8', lw=1.0)
conn(1.85, 4.05, 2.5, 4.0,  '#059669', ls='--', lw=1.0)
conn(1.85, 4.05, 2.5, 2.0,  '#059669', ls='--', lw=1.0)
conn(13.95, 6.9, 13.35, 5.2, '#DC2626', ls='--', lw=1.0)
conn(13.95, 5.1, 13.35, 4.3, '#3B82F6', ls='--', lw=1.0)
conn(13.95, 3.5, 13.35, 4.3, '#8B5CF6', ls='--', lw=1.0)

# ── legend ────────────────────────────────────────────────────────────────────
for i, (fc, ec, lb) in enumerate([('#D1FAE5','#059669','★ 팀장 전용'),
                                    ('#EDE9FE','#7C3AED','AI / 외부 연동')]):
    lx = 2.7 + i * 3.5
    fbox(ax, lx, 0.62, 0.38, 0.28, fc, ec, lw=1.0, z=5, r=0.03)
    t(lx + 1.4, 0.76, lb, fs=8, fw='bold', c=TEXT_D, ha='center')

save_slide(fig, 'md/slide_usecase.png')
