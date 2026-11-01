"""The Skyblock's painted menu backgrounds: a day sky with drifting clouds and floating islands, instead of a chest
lined with glass panes. Used by render.py (same frame, tiling and font as the lobby menus).

Only static art lives here (memory rule: no dynamic text over pack art); every name, count and progress stays in item
names and tooltips. Slot geometry: chest slots at GUI (8 + 18c, 18 + 18r) -> CSS (21 + 54c, 51 + 54r), 48 px frames.
The main menu's button slots are layout.SB_MAIN (the plugin's MainMenu uses the same list).
"""
import random
import sys
import pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import layout  # noqa: E402

W = 528


def _grad(i, stops):
    return f'<linearGradient id="{i}" x1="0" y1="0" x2="0" y2="1">' + "".join(
        f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops) + "</linearGradient>"


def clouds(top, bottom, n, seed, opacity=.75):
    r = random.Random(seed)
    out = ""
    for _ in range(n):
        cx, cy = r.randint(-20, W + 20), r.randint(top, bottom)
        s = r.uniform(.7, 1.6)
        o = r.uniform(opacity * .5, opacity)
        out += "".join(f'<ellipse cx="{cx + dx * s:.0f}" cy="{cy + dy * s:.0f}" rx="{rx * s:.0f}" ry="{ry * s:.0f}" '
                       f'fill="#fff" fill-opacity="{o:.2f}"/>'
                       for dx, dy, rx, ry in ((0, 0, 30, 10), (-22, 4, 20, 8), (24, 3, 22, 8), (6, -7, 16, 8)))
    return out


def island(cx, cy, s, grass="#6bbf4e", rock="#7a5236", tree=True):
    """A small floating island: grass cap, rock underside, optional tree, a thread of waterfall."""
    out = (f'<path d="M{cx - 30 * s:.0f} {cy:.0f} H{cx + 30 * s:.0f} C{cx + 26 * s:.0f} {cy + 14 * s:.0f} '
           f'{cx + 10 * s:.0f} {cy + 30 * s:.0f} {cx:.0f} {cy + 40 * s:.0f} C{cx - 10 * s:.0f} {cy + 30 * s:.0f} '
           f'{cx - 26 * s:.0f} {cy + 14 * s:.0f} {cx - 30 * s:.0f} {cy:.0f}Z" fill="{rock}"/>'
           f'<rect x="{cx - 32 * s:.0f}" y="{cy - 5 * s:.0f}" width="{64 * s:.0f}" height="{8 * s:.0f}" rx="{4 * s:.0f}" fill="{grass}"/>'
           f'<rect x="{cx + 8 * s:.0f}" y="{cy + 3 * s:.0f}" width="{2 * s:.1f}" height="{30 * s:.0f}" fill="#bfe6ff" fill-opacity=".7"/>')
    if tree:
        out += (f'<rect x="{cx - 2 * s:.0f}" y="{cy - 20 * s:.0f}" width="{4 * s:.0f}" height="{16 * s:.0f}" fill="#5a3a20"/>'
                f'<circle cx="{cx:.0f}" cy="{cy - 24 * s:.0f}" r="{11 * s:.0f}" fill="#3f9e3f"/>'
                f'<circle cx="{cx - 4 * s:.0f}" cy="{cy - 28 * s:.0f}" r="{5 * s:.0f}" fill="#7fd36a"/>')
    return out


def sky(h, seed, dusk=False):
    stops = ([(0, "#2a3f8f"), (.45, "#5d8fd8"), (.75, "#f3b48a"), (1, "#f7d6b0")] if dusk
             else [(0, "#3a6cc8"), (.5, "#7fb6ee"), (.85, "#cfe6fb"), (1, "#eaf4ff")])
    return (f'<defs>{_grad("sbsky", stops)}'
            '<radialGradient id="sbsun"><stop offset="0" stop-color="#fff6d0" stop-opacity=".95"/>'
            '<stop offset="1" stop-color="#ffd27a" stop-opacity="0"/></radialGradient></defs>'
            f'<rect width="{W}" height="{h}" fill="url(#sbsky)"/>'
            f'<circle cx="452" cy="38" r="70" fill="url(#sbsun)"/><circle cx="452" cy="38" r="15" fill="#fffbe8"/>'
            + clouds(20, h - 40, 9, seed, .55))


def slot(x, y, tone="#ffffff", op=.22, glow=False):
    """A soft cloud-glass frame under one slot (48x48 at its CSS top-left)."""
    out = (f'<rect x="{x}" y="{y}" width="48" height="48" rx="12" fill="#0c2a5a" fill-opacity="{op}" '
           f'stroke="{tone}" stroke-opacity=".75" stroke-width="2"/>'
           f'<rect x="{x + 4}" y="{y + 4}" width="40" height="14" rx="7" fill="#fff" fill-opacity=".12"/>')
    if glow:
        out = f'<circle cx="{x + 24}" cy="{y + 24}" r="40" fill="#fff6c8" fill-opacity=".28"/>' + out
    return out


def chest_slots(rows, cols=range(9), **kw):
    return "".join(slot(24 + 54 * c, 54 + 54 * r, **kw) for r in range(rows) for c in cols)


def ground(top, bottom, seed):
    """The lower window: a big island top with grass, under the player's inventory."""
    r = random.Random(seed)
    out = (f'<path d="M0 {top + 18} C120 {top - 4} 400 {top - 4} {W} {top + 18} V{bottom} H0z" fill="#4f9a3c"/>'
           f'<path d="M0 {top + 30} C140 {top + 14} 390 {top + 14} {W} {top + 30} V{bottom} H0z" fill="#8a5a3a"/>')
    for _ in range(24):
        x, y = r.randint(0, W), r.randint(top + 40, bottom - 10)
        out += f'<rect x="{x}" y="{y}" width="{r.randint(6, 14)}" height="{r.randint(4, 8)}" fill="#6e4528"/>'
    return out


def inventory(top_rows_y, hotbar_y):
    frames = "".join(slot(24 + 54 * c, top_rows_y + 54 * r, "#ffe9b0", .18) for r in range(3) for c in range(9))
    return frames + "".join(slot(24 + 54 * c, hotbar_y, "#ffe9b0", .24) for c in range(9))


def panel54(frame, title):
    """Any 6-row Skyblock menu: six rows of cloud-glass slots over the sky, the island ground below."""
    h = 666
    svg = (sky(h, 11) + island(70, 300, .9) + island(470, 250, .7, tree=False) + chest_slots(6)
           + ground(396, h, 3) + inventory(420, 594))
    return W, h, frame(W, h, svg, title())


def panel27(frame, title):
    """Any 3-row Skyblock menu."""
    h = 504
    svg = (sky(h, 12) + island(470, 200, .6, tree=False) + chest_slots(3) + ground(230, h, 4) + inventory(252, 426))
    return W, h, frame(W, h, svg, title())


def main_menu(frame, title):
    """The Skyblock menu: the buttons are islands of a constellation around the player's own island."""
    h = 666
    pads = layout.SB_MAIN
    centre = layout.SB_MAIN_CENTRE

    def xy(index):
        r, c = divmod(index, 9)
        return 24 + 54 * c + 24, 54 + 54 * r + 24

    cx, cy = xy(centre)
    lines = "".join(f'<line x1="{cx}" y1="{cy}" x2="{x}" y2="{y}" stroke="#fff" stroke-opacity=".45" '
                    f'stroke-width="2" stroke-dasharray="2 7" stroke-linecap="round"/>'
                    for x, y in (xy(i) for i in pads if i != centre))
    stars = "".join(f'<path d="M{x} {y - 34}l3 8 8 3-8 3-3 8-3-8-8-3 8-3z" fill="#fff" fill-opacity=".8"/>'
                    for x, y in (xy(i) for i in pads if i != centre))
    frames = "".join(slot(xy(i)[0] - 24, xy(i)[1] - 24, "#ffffff" if i != centre else "#ffe9a0", .25, i == centre)
                     for i in pads)
    svg = (sky(h, 21, dusk=True) + lines + island(cx, cy + 22, 1.15, tree=False) + stars + frames
           + ground(396, h, 5) + inventory(420, 594))
    return W, h, frame(W, h, svg, title())


def forge(frame, title):
    """The Forge: recipes on rows 1-3 around a glowing hearth, the job slots on the last row."""
    h = 666
    hearth = ('<radialGradient id="sbember"><stop offset="0" stop-color="#ffd27a" stop-opacity=".9"/>'
              '<stop offset="1" stop-color="#ff6a2c" stop-opacity="0"/></radialGradient>'
              '<ellipse cx="264" cy="390" rx="260" ry="70" fill="url(#sbember)"/>')
    svg = (sky(h, 31, dusk=True) + hearth + slot(24 + 54 * 4, 54, "#ffe9a0", .3, True)
           + "".join(slot(24 + 54 * c, 54 + 54 * r, "#ffd9a8", .25) for r in (1, 2, 3) for c in range(9))
           + "".join(slot(24 + 54 * c, 54 + 54 * 5, "#ff9a6a", .3) for c in range(1, 8))
           + ground(396, h, 6) + inventory(420, 594))
    return W, h, frame(W, h, svg, title())
