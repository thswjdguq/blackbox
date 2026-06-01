"""Slide: ERD (full 16:9 slide PNG)"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from slide_template import *

fig, ax = new_slide()
slide_header(ax, 'DATABASE DESIGN', 'ERD  ·  Entity Relationship Diagram', page=10)
slide_footer(ax, page=10)

def t(x, y, s, fs=8, fw='normal', c=TEXT_D, ha='center', va='center', **kwargs):
    ax.text(x, y, s, ha=ha, va=va, fontsize=fs, fontweight=fw, color=c, zorder=7, **kwargs)

EW   = 2.55   # entity width
RH   = 0.215  # row height
HH   = 0.30   # header height

COLORS = {
    'auth':      {'h': '#3730A3', 'b': '#EEF2FF', 'e': '#6366F1'},
    'project':   {'h': '#065F46', 'b': '#ECFDF5', 'e': '#059669'},
    'task':      {'h': '#92400E', 'b': '#FFF7ED', 'e': '#D97706'},
    'meeting':   {'h': '#5B21B6', 'b': '#F5F3FF', 'e': '#7C3AED'},
    'vault':     {'h': '#991B1B', 'b': '#FEF2F2', 'e': '#DC2626'},
    'analytics': {'h': '#164E63', 'b': '#ECFEFF', 'e': '#0891B2'},
}

def entity(x, y, name, cols, ck):
    c = COLORS[ck]
    h = HH + len(cols) * RH
    fbox(ax, x+0.04, y-h-0.04, EW, h, '#0000001A', 'none', z=1)   # shadow
    fbox(ax, x, y-h, EW, h, c['b'], c['e'], lw=1.3, z=2, r=0.04)
    fbox(ax, x, y-HH, EW, HH, c['h'], c['e'], lw=0, z=3, r=0.04)
    t(x+EW/2, y-HH/2, name, fs=7.2, fw='bold', c=WHITE)
    for i, (cn, ct, kt) in enumerate(cols):
        ry = y - HH - (i+0.5)*RH
        if i > 0:
            ax.plot([x+0.06, x+EW-0.06], [y-HH-i*RH]*2,
                    color=c['e'], alpha=0.25, lw=0.5, zorder=3)
        if kt == 'PK':
            t(x+0.14, ry, 'PK', fs=5.5, fw='bold', c='#B45309',
              ha='left',
              bbox=dict(boxstyle='round,pad=0.1', fc='#FEF3C7', ec='#D97706', lw=0.5))
            nx = x+0.55
        elif kt == 'FK':
            t(x+0.14, ry, 'FK', fs=5.5, fw='bold', c='#4338CA',
              ha='left',
              bbox=dict(boxstyle='round,pad=0.1', fc='#EEF2FF', ec='#6366F1', lw=0.5))
            nx = x+0.55
        elif kt == 'UK':
            t(x+0.14, ry, 'UK', fs=5.5, fw='bold', c='#065F46',
              ha='left',
              bbox=dict(boxstyle='round,pad=0.1', fc='#D1FAE5', ec='#059669', lw=0.5))
            nx = x+0.55
        else:
            nx = x+0.14
        t(nx, ry, cn, fs=7, fw='bold' if kt=='PK' else 'normal',
          c=TEXT_D, ha='left')
        t(x+EW-0.08, ry, ct, fs=6.5, c=TEXT_L, ha='right', fw='normal')
    return {'cx': x+EW/2, 'cy': y-h/2, 'top': y, 'bot': y-h,
            'lx': x, 'rx': x+EW}

def rel(e1, e2, color='#94A3B8'):
    x1, y1 = e1['cx'], e1['cy']
    x2, y2 = e2['cx'], e2['cy']
    if e2['lx'] > e1['rx'] + 0.2:
        p1 = (e1['rx'], y1); p2 = (e2['lx'], y2)
    elif e1['lx'] > e2['rx'] + 0.2:
        p1 = (e1['lx'], y1); p2 = (e2['rx'], y2)
    elif e2['top'] < e1['bot'] - 0.1:
        p1 = (x1, e1['bot']); p2 = (x2, e2['top'])
    else:
        p1 = (x1, e1['top']); p2 = (x2, e2['bot'])
    ax.annotate('', xy=p2, xytext=p1,
                arrowprops=dict(arrowstyle='-|>', color=color, lw=0.9,
                                mutation_scale=10, shrinkA=3, shrinkB=3), zorder=1)

# ── column definitions (trimmed for slide readability) ────────────────────────
E = {
 'users':        ('users','auth',    [('id','uuid','PK'),('email','str','UK'),('name','str',''),('password_hash','str',''),('role','str',''),('created_at','ts','')]),
 'gct':          ('google_cal_tokens','auth',[('id','uuid','PK'),('user_id','uuid','FK'),('access_token','text',''),('refresh_token','text',''),('token_expiry','ts','')]),
 'projects':     ('projects','project',[('id','uuid','PK'),('name','str',''),('invite_code','str','UK'),('created_by','uuid','FK'),('start_date','date',''),('end_date','date','')]),
 'proj_members': ('project_members','project',[('id','uuid','PK'),('project_id','uuid','FK'),('user_id','uuid','FK'),('role','str',''),('consent_platform','bool','')]),
 'weight':       ('weight_configs','project',[('id','uuid','PK'),('project_id','uuid','FK'),('professor_id','uuid','FK'),('weight_git','dec',''),('weight_task','dec','')]),
 'tasks':        ('tasks','task',    [('id','uuid','PK'),('project_id','uuid','FK'),('title','str',''),('status','str',''),('priority','str',''),('created_by','uuid','FK')]),
 'task_asgn':    ('task_assignees','task',[('task_id','uuid','PK'),('user_id','uuid','FK'),('assigned_at','ts','')]),
 'meetings':     ('meetings','meeting',[('id','uuid','PK'),('project_id','uuid','FK'),('title','str',''),('checkin_code','str','UK'),('ai_summary','text',''),('created_by','uuid','FK')]),
 'mtg_atnd':     ('meeting_attendees','meeting',[('meeting_id','uuid','PK'),('user_id','uuid','FK'),('checked_in','bool',''),('checked_in_date','date','')]),
 'vault':        ('file_vault','vault',[('id','uuid','PK'),('project_id','uuid','FK'),('uploader_id','uuid','FK'),('file_hash','str',''),('file_size','int',''),('version','int','')]),
 'tamper':       ('tamper_detection','vault',[('id','uuid','PK'),('vault_id','uuid','FK'),('original_hash','str',''),('new_hash','str',''),('status','str','')]),
 'activity':     ('activity_logs','analytics',[('id','uuid','PK'),('project_id','uuid','FK'),('user_id','uuid','FK'),('action_type','str',''),('trust_level','dec','')]),
 'scores':       ('contribution_scores','analytics',[('id','uuid','PK'),('project_id','uuid','FK'),('user_id','uuid','FK'),('participation_level','str',''),('calculated_at','ts','')]),
 'alerts':       ('alerts','analytics',[('id','uuid','PK'),('project_id','uuid','FK'),('user_id','uuid','FK'),('alert_type','str',''),('resolved_at','ts','')]),
}

def eh(k):
    _, _, cols = E[k]; return HH + len(cols)*RH

# ── layout (x, y=top) ─────────────────────────────────────────────────────────
C0,C1,C2,C3,C4,C5 = 0.35, 3.15, 5.95, 8.75, 11.55, 14.35

R1 = 7.55
R2 = R1 - max(eh('users'), eh('gct')) - 0.35
R3 = R2 - max(eh('projects'), eh('proj_members'), eh('weight')) - 0.35
R4 = R3 - max(eh('tasks'), eh('meetings'), eh('activity'), eh('alerts')) - 0.30
R5 = R4 - max(eh('task_asgn'), eh('mtg_atnd'), eh('scores')) - 0.30
R6 = R5 - max(eh('vault'), eh('tamper')) - 0.30

POS = {
    'users': (C2, R1), 'gct': (C5, R1),
    'proj_members': (C0, R2), 'projects': (C2, R2), 'weight': (C5, R2),
    'tasks': (C0, R3), 'meetings': (C2, R3), 'activity': (C4, R3), 'alerts': (C5, R3),
    'task_asgn': (C0, R4), 'mtg_atnd': (C2, R4), 'scores': (C4, R4),
    'vault': (C0, R5), 'tamper': (C0, R6),
}

boxes = {}
for key, (name, ck, cols) in E.items():
    x, y = POS[key]
    boxes[key] = entity(x, y, name, cols, ck)

# ── relationships ─────────────────────────────────────────────────────────────
REL = [
    ('users','projects','#6366F1'), ('users','proj_members','#6366F1'),
    ('users','gct','#6366F1'),      ('users','tasks','#6366F1'),
    ('users','task_asgn','#6366F1'),('users','meetings','#6366F1'),
    ('users','mtg_atnd','#6366F1'), ('users','vault','#6366F1'),
    ('users','activity','#6366F1'), ('users','scores','#6366F1'),
    ('users','alerts','#6366F1'),   ('users','weight','#6366F1'),
    ('projects','proj_members','#059669'), ('projects','tasks','#059669'),
    ('projects','meetings','#059669'),     ('projects','vault','#059669'),
    ('projects','activity','#059669'),     ('projects','scores','#059669'),
    ('projects','alerts','#059669'),       ('projects','weight','#059669'),
    ('tasks','task_asgn','#D97706'),       ('meetings','mtg_atnd','#7C3AED'),
    ('vault','tamper','#DC2626'),
]
for a, b, c in REL:
    rel(boxes[a], boxes[b], c)

# ── colour legend ──────────────────────────────────────────────────────────────
lg = [('인증/사용자','#3730A3'),('프로젝트','#065F46'),('태스크','#92400E'),
      ('회의록','#5B21B6'),('Hash Vault','#991B1B'),('기여도/경보','#164E63')]
lx = C5 + EW + 0.15
ly = R2
for i, (lb, hc) in enumerate(lg):
    fbox(ax, lx, ly-i*0.38, 0.28, 0.28, hc, 'none', z=5)
    t(lx+0.36, ly-i*0.38+0.14, lb, fs=7, c=TEXT_D, ha='left')

save_slide(fig, 'md/slide_erd.png')
