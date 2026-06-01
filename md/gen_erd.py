"""
Team Blackbox ERD — presentation-quality PNG
Color-coded by domain, large font, clear relationships
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm

# Use Malgun Gothic for Korean support
plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch
import matplotlib.patheffects as pe

# ── colour palette ─────────────────────────────────────────────────────────────
C = {
    'auth':      {'h': '#3730A3', 'b': '#EEF2FF', 'e': '#6366F1'},   # indigo
    'project':   {'h': '#065F46', 'b': '#ECFDF5', 'e': '#059669'},   # green
    'task':      {'h': '#92400E', 'b': '#FFF7ED', 'e': '#D97706'},   # amber
    'meeting':   {'h': '#5B21B6', 'b': '#F5F3FF', 'e': '#7C3AED'},   # violet
    'vault':     {'h': '#991B1B', 'b': '#FEF2F2', 'e': '#DC2626'},   # red
    'analytics': {'h': '#164E63', 'b': '#ECFEFF', 'e': '#0891B2'},   # cyan
}

EW   = 4.6   # entity box width
RH   = 0.30  # row height
HH   = 0.46  # header height

# ── entity definitions ─────────────────────────────────────────────────────────
# (name, color_key, [(col_name, type, key)])
#   key: 'PK' | 'FK' | 'UK' | ''
ENTITIES = {
  'users': ('users', 'auth', [
      ('id',           'uuid',  'PK'),
      ('email',        'str',   'UK'),
      ('name',         'str',   ''),
      ('password_hash','str',   ''),
      ('role',         'str',   ''),  # STUDENT|PROFESSOR|TA
      ('avatar_url',   'str',   ''),
      ('created_at',   'ts',    ''),
      ('updated_at',   'ts',    ''),
  ]),
  'google_calendar_tokens': ('google_calendar_tokens', 'auth', [
      ('id',           'uuid',  'PK'),
      ('user_id',      'uuid',  'FK'),
      ('access_token', 'text',  ''),
      ('refresh_token','text',  ''),
      ('token_expiry', 'ts',    ''),
      ('updated_at',   'ts',    ''),
  ]),
  'projects': ('projects', 'project', [
      ('id',          'uuid', 'PK'),
      ('name',        'str',  ''),
      ('course_name', 'str',  ''),
      ('semester',    'str',  ''),
      ('start_date',  'date', ''),
      ('end_date',    'date', ''),
      ('invite_code', 'str',  'UK'),
      ('created_by',  'uuid', 'FK'),
      ('created_at',  'ts',   ''),
      ('updated_at',  'ts',   ''),
  ]),
  'project_members': ('project_members', 'project', [
      ('id',                  'uuid', 'PK'),
      ('project_id',          'uuid', 'FK'),
      ('user_id',             'uuid', 'FK'),
      ('role',                'str',  ''),  # LEADER|MEMBER|OBSERVER
      ('consent_platform',    'bool', ''),
      ('consent_github',      'bool', ''),
      ('consent_drive',       'bool', ''),
      ('consent_ai_analysis', 'bool', ''),
      ('joined_at',           'ts',   ''),
  ]),
  'weight_configs': ('weight_configs', 'project', [
      ('id',             'uuid', 'PK'),
      ('project_id',     'uuid', 'FK'),
      ('professor_id',   'uuid', 'FK'),
      ('weight_git',     'dec',  ''),
      ('weight_doc',     'dec',  ''),
      ('weight_meeting', 'dec',  ''),
      ('weight_task',    'dec',  ''),
      ('updated_at',     'ts',   ''),
  ]),
  'tasks': ('tasks', 'task', [
      ('id',          'uuid', 'PK'),
      ('project_id',  'uuid', 'FK'),
      ('title',       'str',  ''),
      ('status',      'str',  ''),  # TODO|IN_PROGRESS|DONE
      ('priority',    'str',  ''),  # LOW|MEDIUM|HIGH|URGENT
      ('tag',         'str',  ''),
      ('due_date',    'date', ''),
      ('completed_at','ts',   ''),
      ('created_by',  'uuid', 'FK'),
      ('created_at',  'ts',   ''),
  ]),
  'task_assignees': ('task_assignees', 'task', [
      ('task_id',     'uuid', 'PK'),
      ('user_id',     'uuid', 'FK'),
      ('assigned_at', 'ts',   ''),
  ]),
  'meetings': ('meetings', 'meeting', [
      ('id',              'uuid', 'PK'),
      ('project_id',      'uuid', 'FK'),
      ('title',           'str',  ''),
      ('meeting_date',    'ts',   ''),
      ('notes',           'text', ''),
      ('decisions',       'text', ''),
      ('checkin_code',    'str',  'UK'),
      ('ai_summary',      'text', ''),
      ('notion_page_id',  'str',  ''),
      ('created_by',      'uuid', 'FK'),
      ('created_at',      'ts',   ''),
  ]),
  'meeting_attendees': ('meeting_attendees', 'meeting', [
      ('meeting_id',     'uuid', 'PK'),
      ('user_id',        'uuid', 'FK'),
      ('checked_in',     'bool', ''),
      ('checked_at',     'ts',   ''),
      ('checked_in_date','date', ''),
  ]),
  'file_vault': ('file_vault', 'vault', [
      ('id',           'uuid', 'PK'),
      ('project_id',   'uuid', 'FK'),
      ('uploader_id',  'uuid', 'FK'),
      ('file_name',    'str',  ''),
      ('file_hash',    'str',  ''),
      ('file_size',    'int',  ''),
      ('storage_path', 'text', ''),
      ('is_immutable', 'bool', ''),
      ('version',      'int',  ''),
  ]),
  'tamper_detection_log': ('tamper_detection_log', 'vault', [
      ('id',            'uuid', 'PK'),
      ('vault_id',      'uuid', 'FK'),
      ('original_hash', 'str',  ''),
      ('new_hash',      'str',  ''),
      ('detector_type', 'str',  ''),
      ('status',        'str',  ''),  # FLAGGED|REVIEWED|DISMISSED
      ('detected_at',   'ts',   ''),
  ]),
  'activity_logs': ('activity_logs', 'analytics', [
      ('id',          'uuid', 'PK'),
      ('project_id',  'uuid', 'FK'),
      ('user_id',     'uuid', 'FK'),
      ('source',      'str',  ''),   # PLATFORM|GITHUB|...
      ('action_type', 'str',  ''),
      ('metadata',    'json', ''),
      ('trust_level', 'dec',  ''),
      ('occurred_at', 'ts',   ''),
  ]),
  'contribution_scores': ('contribution_scores', 'analytics', [
      ('id',                   'uuid', 'PK'),
      ('project_id',           'uuid', 'FK'),
      ('user_id',              'uuid', 'FK'),
      ('total_score',          'dec',  ''),
      ('task_participated',    'bool', ''),
      ('meeting_participated', 'bool', ''),
      ('file_participated',    'bool', ''),
      ('action_participated',  'bool', ''),
      ('participation_level',  'str',  ''),  # FULL|PARTIAL|NONE
      ('calculated_at',        'ts',   ''),
  ]),
  'alerts': ('alerts', 'analytics', [
      ('id',            'uuid', 'PK'),
      ('project_id',    'uuid', 'FK'),
      ('user_id',       'uuid', 'FK'),  # nullable
      ('alert_type',    'str',  ''),    # FREE_RIDE|DROPOUT|OVERLOAD|...
      ('severity',      'str',  ''),    # LOW|MEDIUM|HIGH|CRITICAL
      ('message',       'text', ''),
      ('is_read',       'bool', ''),
      ('resolved_at',   'ts',   ''),
  ]),
}

# ── layout positions (x_left, y_top) ──────────────────────────────────────────
#
#   users(col2)          google_calendar_tokens(col5)
#   project_members(0)   projects(col2)   weight_configs(col5)
#   tasks(col0)  meetings(col1)  activity_logs(col3)  alerts(col5)
#   task_assign  mtg_atnd        contrib_scores
#   file_vault
#   tamper_log
#

def entity_height(key):
    _, _, cols = ENTITIES[key]
    return HH + len(cols) * RH

R1 = 26.8
R2 = R1 - max(entity_height('users'), entity_height('google_calendar_tokens')) - 0.9
R3 = R2 - max(entity_height('project_members'), entity_height('projects'), entity_height('weight_configs')) - 0.9
R4 = R3 - max(entity_height('tasks'), entity_height('meetings'),
              entity_height('activity_logs'), entity_height('alerts')) - 0.8
R5 = R4 - max(entity_height('task_assignees'), entity_height('meeting_attendees'),
              entity_height('contribution_scores')) - 0.8
R6 = R5 - entity_height('file_vault') - 0.8
R7 = R6 - entity_height('tamper_detection_log') - 0.5

C0, C1, C2, C3, C4, C5 = 0.4, 6.0, 11.6, 17.2, 22.8, 28.4

POS = {
    'users':                  (C2, R1),
    'google_calendar_tokens': (C5, R1),
    'project_members':        (C0, R2),
    'projects':               (C2, R2),
    'weight_configs':         (C5, R2),
    'tasks':                  (C0, R3),
    'meetings':               (C1, R3),
    'activity_logs':          (C3, R3),
    'alerts':                 (C5, R3),
    'task_assignees':         (C0, R4),
    'meeting_attendees':      (C1, R4),
    'contribution_scores':    (C3, R4),
    'file_vault':             (C0, R5),
    'tamper_detection_log':   (C0, R6),
}

# ── relationship arrows ──────────────────────────────────────────────────────
# (from_entity, to_entity, label, line_color)
RELATIONS = [
    # users → many
    ('users', 'projects',             'creates',  '#6366F1'),
    ('users', 'project_members',      'joins',    '#6366F1'),
    ('users', 'google_calendar_tokens','token',   '#6366F1'),
    ('users', 'tasks',                'creates',  '#6366F1'),
    ('users', 'task_assignees',       'assigned', '#6366F1'),
    ('users', 'meetings',             'creates',  '#6366F1'),
    ('users', 'meeting_attendees',    'attends',  '#6366F1'),
    ('users', 'file_vault',           'uploads',  '#6366F1'),
    ('users', 'activity_logs',        'logs',     '#6366F1'),
    ('users', 'contribution_scores',  'scored',   '#6366F1'),
    ('users', 'alerts',               'targets',  '#6366F1'),
    ('users', 'weight_configs',       'prof sets','#6366F1'),
    # projects → many
    ('projects', 'project_members',   'has',      '#059669'),
    ('projects', 'tasks',             'contains', '#059669'),
    ('projects', 'meetings',          'holds',    '#059669'),
    ('projects', 'file_vault',        'stores',   '#059669'),
    ('projects', 'activity_logs',     'logs',     '#059669'),
    ('projects', 'contribution_scores','scores',  '#059669'),
    ('projects', 'alerts',            'generates','#059669'),
    ('projects', 'weight_configs',    'configures','#059669'),
    # task
    ('tasks', 'task_assignees',       '1:N',      '#D97706'),
    # meeting
    ('meetings', 'meeting_attendees', '1:N',      '#7C3AED'),
    # vault
    ('file_vault', 'tamper_detection_log', '1:N', '#DC2626'),
]

# ── helpers ────────────────────────────────────────────────────────────────────
def box_center(key):
    x, y = POS[key]
    h = entity_height(key)
    return (x + EW/2, y - h/2)

def box_edge(key, side):
    """Return (x,y) of midpoint of a box edge: top/bottom/left/right"""
    x, y = POS[key]
    h = entity_height(key)
    cx = x + EW/2
    cy = y - h/2
    if side == 'top':    return (cx, y)
    if side == 'bottom': return (cx, y - h)
    if side == 'left':   return (x, cy)
    if side == 'right':  return (x + EW, cy)

def draw_entity(ax, key):
    name, ckey, cols = ENTITIES[key]
    col = C[ckey]
    x, y = POS[key]
    h = entity_height(key)

    # shadow
    shadow = FancyBboxPatch((x+0.07, y-h-0.07), EW, h,
                             boxstyle="round,pad=0.05",
                             facecolor='#00000018', edgecolor='none', zorder=1)
    ax.add_patch(shadow)

    # body
    body = FancyBboxPatch((x, y-h), EW, h,
                           boxstyle="round,pad=0.05",
                           facecolor=col['b'], edgecolor=col['e'],
                           linewidth=1.8, zorder=2)
    ax.add_patch(body)

    # header
    hdr = FancyBboxPatch((x, y-HH), EW, HH,
                          boxstyle="round,pad=0.05",
                          facecolor=col['h'], edgecolor=col['e'],
                          linewidth=1.8, zorder=3)
    ax.add_patch(hdr)

    ax.text(x + EW/2, y - HH/2, name,
            ha='center', va='center',
            fontsize=10, fontweight='bold', color='white', zorder=4,
            fontfamily='monospace')

    # rows
    for i, (cname, ctype, ktype) in enumerate(cols):
        ry = y - HH - (i + 0.5) * RH
        # divider
        if i > 0:
            ax.plot([x+0.12, x+EW-0.12],
                    [y-HH - i*RH]*2,
                    color=col['e'], alpha=0.25, lw=0.6, zorder=3)

        # key badge
        if ktype == 'PK':
            ax.text(x+0.18, ry, 'PK', ha='left', va='center',
                    fontsize=6.5, color='#B45309', fontweight='bold',
                    bbox=dict(boxstyle='round,pad=0.15', fc='#FEF3C7',
                              ec='#D97706', lw=0.7), zorder=4)
            name_x = x + 0.72
        elif ktype == 'FK':
            ax.text(x+0.18, ry, 'FK', ha='left', va='center',
                    fontsize=6.5, color='#4338CA', fontweight='bold',
                    bbox=dict(boxstyle='round,pad=0.15', fc='#EEF2FF',
                              ec='#6366F1', lw=0.7), zorder=4)
            name_x = x + 0.72
        elif ktype == 'UK':
            ax.text(x+0.18, ry, 'UK', ha='left', va='center',
                    fontsize=6.5, color='#065F46', fontweight='bold',
                    bbox=dict(boxstyle='round,pad=0.15', fc='#D1FAE5',
                              ec='#059669', lw=0.7), zorder=4)
            name_x = x + 0.72
        else:
            name_x = x + 0.22

        ax.text(name_x, ry, cname,
                ha='left', va='center',
                fontsize=8.5, color='#1F2937',
                fontweight='bold' if ktype == 'PK' else 'normal',
                fontfamily='monospace', zorder=4)

        ax.text(x+EW-0.15, ry, ctype,
                ha='right', va='center',
                fontsize=7.5, color='#6B7280', style='italic', zorder=4)


def draw_relation(ax, frm, to, lbl, color):
    fx, fy = box_center(frm)
    tx, ty = box_center(to)

    # pick best edge pair to minimise crossing
    fx0, fy0 = POS[frm][0], POS[frm][1]
    tx0, ty0 = POS[to][0], POS[to][1]
    fh = entity_height(frm)
    th = entity_height(to)

    if tx0 > fx0 + EW + 0.5:
        p1 = (fx0+EW, fy); p2 = (tx0, ty)
    elif tx0 + EW < fx0 - 0.5:
        p1 = (fx0, fy); p2 = (tx0+EW, ty)
    elif ty0 < fy0 - fh - 0.3:
        p1 = (fx, fy0-fh); p2 = (tx, ty0)
    else:
        p1 = (fx, fy0); p2 = (tx, ty0-th)

    ax.annotate('', xy=p2, xytext=p1,
                arrowprops=dict(
                    arrowstyle='-|>',
                    color=color,
                    lw=1.1,
                    connectionstyle='arc3,rad=0.0',
                    shrinkA=3, shrinkB=3,
                ),
                zorder=1)


# ── draw ──────────────────────────────────────────────────────────────────────
FW, FH = 34, 29
fig, ax = plt.subplots(figsize=(FW, FH), dpi=150)
fig.patch.set_facecolor('#F1F5F9')
ax.set_xlim(0, FW)
ax.set_ylim(R7 - 1, R1 + 1)
ax.axis('off')

# background grid hint
for gx in [C0-0.2, C1-0.2, C2-0.2, C3-0.2, C4-0.2, C5-0.2]:
    ax.axvline(gx, color='#CBD5E1', lw=0.4, alpha=0.4, zorder=0)

# relations first (under entities)
for frm, to, lbl, col in RELATIONS:
    draw_relation(ax, frm, to, lbl, col)

# entities
for key in ENTITIES:
    draw_entity(ax, key)

# ── legend ────────────────────────────────────────────────────────────────────
legend_items = [
    ('인증 / 사용자',  C['auth']['h']),
    ('프로젝트 관리', C['project']['h']),
    ('태스크',        C['task']['h']),
    ('회의록',        C['meeting']['h']),
    ('Hash Vault',    C['vault']['h']),
    ('분석 / 경보',   C['analytics']['h']),
]
legend_x = C5 + EW + 0.3
legend_y = R2
for i, (label, color) in enumerate(legend_items):
    lx = legend_x
    ly = legend_y - i * 0.6
    rect = FancyBboxPatch((lx, ly-0.22), 0.5, 0.38,
                           boxstyle='round,pad=0.04',
                           facecolor=color, edgecolor='none', zorder=5)
    ax.add_patch(rect)
    ax.text(lx + 0.62, ly-0.03, label,
            va='center', fontsize=9, color='#1E293B', zorder=5)

# key badge legend
badge_y = legend_y - len(legend_items) * 0.6 - 0.5
for i, (badge, desc, fc, ec, tc) in enumerate([
    ('PK', 'Primary Key',  '#FEF3C7','#D97706','#B45309'),
    ('FK', 'Foreign Key',  '#EEF2FF','#6366F1','#4338CA'),
    ('UK', 'Unique Key',   '#D1FAE5','#059669','#065F46'),
]):
    ax.text(legend_x+0.25, badge_y - i*0.5, badge,
            ha='center', va='center', fontsize=7, color=tc, fontweight='bold',
            bbox=dict(boxstyle='round,pad=0.2', fc=fc, ec=ec, lw=0.8), zorder=5)
    ax.text(legend_x+0.62, badge_y - i*0.5 - 0.02, desc,
            va='center', fontsize=9, color='#1E293B', zorder=5)

# title
ax.text(FW/2, R1 + 0.6,
        'Team Blackbox — Entity Relationship Diagram',
        ha='center', va='bottom',
        fontsize=18, fontweight='bold', color='#0F172A',
        zorder=5)
ax.text(FW/2, R1 + 0.2,
        '14 tables  ·  PostgreSQL 16  ·  Spring Boot JPA',
        ha='center', va='bottom',
        fontsize=11, color='#475569', zorder=5)

plt.tight_layout(pad=0.5)
plt.savefig('md/erd_final.png', dpi=150, bbox_inches='tight',
            facecolor=fig.get_facecolor())
print("Saved: md/erd_final.png")
plt.close()
