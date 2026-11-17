#!/usr/bin/env python3
"""The ValoBlock tactical map art (stdlib only, deterministic): the shared window chrome, one plan image per map, one zoomed image per
zone, and the three small item sprites the menu lays over the plan. Called by valoblock_ui.py (it writes the font that lists them).

The plans come from arena-plugin/src/main/resources/valoblock/maps/<id>.json (the hand-tuned source of truth, not the design plans).
Geometry (cells to pixels) lives in vb_layout.py and is mirrored by TacGrid.java; the art carries colour and shapes only, no letters:
orange = Seedbearer side, light blue = Sealer side, magenta = a site (one white dot = site A, two = site B).

  overview  3 px per cell, 90 x 90 px = the 5 x 5 middle slots of the chest; a zone is 6 x 6 cells = one slot
  zone      15 px per cell over the 6 x 6 cells of the zone; the fine grid of 5 x 5 slots lies over it
"""
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import valoblock_items as vi  # noqa: E402
import vb_layout as vb  # noqa: E402

MAPS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/maps"
CLEAR = (0, 0, 0, 0)
N = vb.TAC_GRID
PX = vb.TAC_PLAN_PX


def col(hex6, a=255):
    r, g, b = vi.rgb(hex6.lstrip("#"))
    return (r, g, b, a)


FLOOR, FLOOR2, WALL, WALL_HI, WALL_LO = col("2b303c"), col("262a35"), col("8d95a8"), col("aab2c4"), col("636b7d")
HIGH, HIGH_HI = col("4a5573"), col("7d89b0")
SITE, SITE_HI = col("a63fc0"), col("e6a0f5")
ATTACK, DEFEND = col("c9782e"), col("3f86bd")
FEATURE, CRATE, FOLIAGE, WATER, WATER_HI = col("d9b23c"), col("7a5a32"), col("2f6a3d"), col("1f3f73"), col("2d5896")
GRID = col("0b0d12", 120)
DOT, ORB = col("ffffff"), col("ffd36a")


def load(mid):
    return json.loads((MAPS / f"{mid}.json").read_text())


def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def rect(img, x, y, w, h, c):
    for yy in range(max(0, y), min(len(img), y + h)):
        for xx in range(max(0, x), min(len(img[0]), x + w)):
            img[yy][xx] = c


def cell_pixel(ch, ox, oy, ppc, cx, cy):
    """The colour of one pixel (offset ox, oy inside a cell drawn ppc px wide)."""
    edge = ppc >= 8 and (ox in (0, ppc - 1) or oy in (0, ppc - 1))
    if ch == "#":
        if ppc < 8:
            return WALL_HI if (ox == 0 or oy == 0) else WALL if ox < ppc - 1 and oy < ppc - 1 else WALL_LO
        return WALL_HI if (ox == 0 or oy == 0) else WALL_LO if (ox == ppc - 1 or oy == ppc - 1) else WALL
    if ch == "w":
        return WATER_HI if ((ox + 2 * oy + cx * 3 + cy) % 7 == 0 and ppc >= 8) else WATER
    if ch in "AF":
        base = SITE if ch == "A" else FEATURE
        return SITE_HI if edge and ch == "A" else base if ch == "A" or not edge else col("f5de8a")
    if ch == "R":
        return ATTACK
    if ch == "B":
        return DEFEND
    if ch == "h":
        return HIGH_HI if edge or ppc < 8 and (ox == 0 or oy == 0) else HIGH
    if ch == "c":
        return CRATE if not edge else col("a07c48")
    if ch == "g":
        return FOLIAGE if (ox + oy + cx + cy) % 3 else col("3f8a50")
    return FLOOR if (cx + cy) % 2 == 0 else FLOOR2


def dotted_grid(img):
    """The zone / fine slot boundaries (every 18 px), dotted so the art stays readable."""
    for k in range(1, N):
        for t in range(0, PX, 2):
            img[t][k * 18] = GRID
            img[k * 18][t] = GRID


def sites_of(j):
    """[(id, cx, cz, (x0, z0, x1, z1))] in cells: the plan position of each site box (blocks / cell size)."""
    cell = j["cell"]
    out = []
    for s in j["sites"]:
        x0, z0, x1, z1 = s["min"][0] / cell, s["min"][2] / cell, s["max"][0] / cell, s["max"][2] / cell
        out.append((s["id"], (x0 + x1) / 2, (z0 + z1) / 2, (x0, z0, x1, z1)))
    return out


def overlay(img, j, x0c, y0c, ppc):
    """Site boxes, the dots that tell A from B, the spawn points and the ult orbs, in the view whose top-left is (x0c, y0c) cells."""
    cell = j["cell"]

    def px(xc, zc):
        return round((xc - x0c) * ppc), round((zc - y0c) * ppc)

    for sid, cx, cz, (a, b, c, d) in sites_of(j):
        if ppc >= 8:
            ax, ay = px(a, b)
            bx, by = px(c, d)
            for x in range(ax, bx):
                for y in (ay, by - 1):
                    if 0 <= x < PX and 0 <= y < PX:
                        img[y][x] = SITE_HI
            for y in range(ay, by):
                for x in (ax, bx - 1):
                    if 0 <= x < PX and 0 <= y < PX:
                        img[y][x] = SITE_HI
        mx, my = px(cx, cz)
        dots = 1 if sid == "A" else 2
        size = 2 if ppc < 8 else 4
        for k in range(dots):
            rect(img, mx - dots * size // 2 - (dots - 1) + k * (size + 2), my - size // 2, size, size, DOT)
    if ppc >= 8:
        for side, colr in (("SEEDBEARERS", ATTACK), ("SEALERS", DEFEND)):
            for p in j["spawns"][side]:
                x, y = px(p[0] / cell, p[2] / cell)
                rect(img, x - 1, y - 1, 3, 3, DOT)
                rect(img, x, y, 1, 1, colr)
    for p in j["orbs"]:
        x, y = px(p[0] / cell, p[2] / cell)
        k = 1 if ppc < 8 else 3
        for d in range(-k, k + 1):
            for yy, xx in ((y + d, x + k - abs(d)), (y + d, x - k + abs(d))):
                if 0 <= xx < PX and 0 <= yy < PX:
                    img[yy][xx] = ORB


def paint(j, x0c, y0c, ppc):
    plan = j["plan"]
    img = canvas(PX, PX, FLOOR)
    for y in range(PX):
        for x in range(PX):
            cx, cy = x0c + x // ppc, y0c + y // ppc
            img[y][x] = cell_pixel(plan[cy][cx], x % ppc, y % ppc, ppc, cx, cy)
    dotted_grid(img)
    overlay(img, j, x0c, y0c, ppc)
    return img


def overview(j):
    return paint(j, 0, 0, PX // len(j["plan"]))


def zone_cells(j):
    """Cells per zone side (the plan is square, N zones across)."""
    return len(j["plan"]) // N


def zone(j, zc, zr):
    z = zone_cells(j)
    return paint(j, zc * z, zr * z, PX // z)


# ------------------------------------------------------------------ chrome (the window the plan lies in)

BG, EDGE, SLOT = col("12151d"), col("2a2f3c"), col("232733")


def frame(img, x, y, w, h, fill, border, t=1):
    rect(img, x, y, w, h, border)
    rect(img, x + t, y + t, w - 2 * t, h - 2 * t, fill)


def chrome():
    """The 176 x 222 window: dark plate, title strip, the plan frame, the teammate slots, the three control slots, the player's inventory."""
    import valoblock_ui as ui  # the same base as the other v2 menus
    img = ui.window_base()
    frame(img, vb.TAC_PLAN_X - 1, vb.TAC_PLAN_Y - 1, PX + 2, PX + 2, col("07080b"), col("3a3f4c"))
    for r in range(4):
        ui.slot_frame(img, 0, r, 1, 1, col("1e2230"), col("3a3f4c"))   # the teammates
    ui.slot_frame(img, 0, 5, 1, 1, col("2a2a36"), col("9aa3b5"))       # back
    ui.slot_frame(img, 4, 5, 1, 1, col("1f3557"), col("7fc8e8"))       # the ability and its charges
    ui.slot_frame(img, 8, 5, 1, 1, col("4a1f26"), col("e8636f"))       # cancel
    return img


# ------------------------------------------------------------------ item sprites

def sprite_dim():
    """Laid over a slot that is out of reach: a dark, see-through square."""
    return canvas(16, 16, col("05060a", 170))


def sprite_mark():
    """A smoke the player already placed: a pale ring with a dot."""
    img = canvas(16, 16)
    for a in range(16):
        for b in range(16):
            d = (a - 7.5) ** 2 + (b - 7.5) ** 2
            if 30 <= d <= 49:
                img[b][a] = col("d8d4c0")
            elif d <= 4:
                img[b][a] = col("d8d4c0")
    return img


def sprite_pick():
    """A spot already picked (a Miasma Wall end, a Storm Call spot): a bright cross."""
    img = canvas(16, 16)
    for k in range(3, 13):
        img[k][k] = img[k][15 - k] = col("ffd36a")
        img[k][k + 1 if k < 15 else k] = col("ffd36a")
    return img


SPRITES = {"tac_dim": sprite_dim, "tac_mark": sprite_mark, "tac_pick": sprite_pick}


def write_png(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(vi.png(rows))


def providers(ui):
    """Writes the chrome tiles, the plan images and the sprites; returns the font providers (space glyphs excluded)."""
    import layout
    out = ui.cut_background("vb_tacmap", vb.TAC_CHROME_BASE, chrome())
    for mi, mid in enumerate(vb.TAC_MAPS):
        j = load(mid)
        files = [(vb.TAC_PLAN_BASE + mi, f"vb_tac_{mid}", overview(j))]
        files += [(vb.TAC_ZONE_BASE + mi * N * N + k, f"vb_tac_{mid}_z{k}", zone(j, k % N, k // N)) for k in range(N * N)]
        for code, name, img in files:
            write_png(ui.TEXTURES / "tac" / f"{name}.png", img)
            out.append({"type": "bitmap", "file": f"vampyuyu:font/tac/{name}.png", "ascent": layout.ASCENT - vb.TAC_PLAN_Y, "height": PX,
                        "chars": [chr(code)]})
    return out


def items(ui):
    n = 0
    for name, make in SPRITES.items():
        write_png(ui.NS / f"textures/item/valoblock/{name}.png", make())
        ui.item(name, {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/valoblock/{name}"}})
        n += 1
    return n
