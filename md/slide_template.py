"""
Shared slide template helpers for Team Blackbox presentation.
Import this in each slide script.
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle

plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

# ── design tokens ─────────────────────────────────────────────────────────────
NAVY   = '#1A3A4A'
TEAL   = '#0D9488'
WHITE  = '#FFFFFF'
GRAY1  = '#F8FAFC'
GRAY2  = '#F1F5F9'
BORDER = '#CBD5E1'
TEXT_D = '#0F172A'
TEXT_M = '#334155'
TEXT_L = '#64748B'

# Slide canvas: 16:9, 3840×2160 @ 240 DPI  →  crisp on any projector
SW, SH = 16, 9   # data units (inches)
DPI = 240

def new_slide():
    fig, ax = plt.subplots(figsize=(SW, SH), dpi=DPI)
    fig.patch.set_facecolor(WHITE)
    ax.set_xlim(0, SW)
    ax.set_ylim(0, SH)
    ax.axis('off')
    return fig, ax

def slide_header(ax, tag, title, page, total=14):
    """Teal tag line + big title + top accent bar."""
    # accent bar
    ax.add_patch(Rectangle((0.45, 8.42), 3.5, 0.045,
                             facecolor=TEAL, edgecolor='none', zorder=4))
    ax.text(0.45, 8.62, tag,   ha='left', va='center',
            fontsize=8, fontweight='bold', color=TEAL, zorder=5)
    ax.text(0.45, 8.08, title, ha='left', va='center',
            fontsize=20, fontweight='bold', color=TEXT_D, zorder=5)

def slide_footer(ax, page, total=14):
    """Dark navy footer bar."""
    ax.add_patch(Rectangle((0, 0), SW, 0.52,
                             facecolor=NAVY, edgecolor='none', zorder=3))
    ax.text(0.45, 0.26, 'Team Blackbox  ·  손에 손잡고',
            ha='left', va='center', fontsize=8.5, color='#94A3B8', zorder=5)
    ax.text(SW - 0.45, 0.26, f'{page}  /  {total}',
            ha='right', va='center', fontsize=8.5, color='#94A3B8', zorder=5)

def save_slide(fig, path):
    plt.tight_layout(pad=0)
    plt.savefig(path, dpi=DPI, bbox_inches='tight', facecolor='white')
    print(f'Saved: {path}')
    plt.close()

def fbox(ax, x, y, w, h, fc, ec='none', lw=1.0, z=2, r=0.0):
    ax.add_patch(FancyBboxPatch((x, y), w, h,
        boxstyle=f'round,pad={r}' if r else 'square,pad=0',
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z))

def rect(ax, x, y, w, h, fc, ec='none', lw=0.8, z=2):
    ax.add_patch(Rectangle((x, y), w, h,
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=z))
