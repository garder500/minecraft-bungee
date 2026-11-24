#!/usr/bin/env python3
"""The ValoBlock tactical map art (stdlib only, deterministic): the window chrome, one plan image per map, one zoomed image per
zone, and the small item sprites the menu lays over the plan. Called by valoblock_ui.py (it writes the font that lists them).

The plans come from arena-plugin/src/main/resources/valoblock/maps/<id>.json (the hand-tuned source of truth, not the design plans).
Geometry (cells to pixels) lives in vb_layout.py and is mirrored by TacGrid.java; the art carries colour and shapes only, no letters.

A plan is a clean floor plan, not a block spreadsheet: a dark background, a light floor wherever a player can stand (the characters
TacGrid.walkable accepts), a crisp pale outline where a wall meets it, soft colour fills for the sites (magenta; a diamond with one
pip = site A, two = site B) and the two spawns (orange = Seedbearers, blue = Sealers), and no grid: the slot under the pointer is
what the client highlights. Every pixel is classified from the plan, so the same painter draws any size without smearing:

  overview  3 px per cell, 90 x 90 px = the 5 x 5 middle slots of the chest; a zone is 6 x 6 cells = one slot
  zone      15 px per cell over the 6 x 6 cells of the zone; the fine grid of 5 x 5 slots lies over it
  vote      the whole plan in the thumbnail of valoblock_vote.py (about 1.5 px per cell)
"""
import json
import math
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


VOID, WALL_FILL, RIM = col("0a0d14"), col("131824"), col("e4e9f4")
FLOOR, FLOOR_SHADE = col("8a94ab"), col("6c7690")
HIGH, HIGH_RIM = col("a7b1c8"), col("cdd5e6")
SITE, SITE_HI = col("c94fe0"), col("f0b4fb")
ATTACK, DEFEND = col("e8873a"), col("4a9be0")
FEATURE, FEATURE_HI = col("e0b93c"), col("fbe89a")
CRATE, CRATE_HI = col("6e4f2a"), col("b08a52")
FOLIAGE, FOLIAGE_HI = col("1d4a2c"), col("3f8a50")
WATER, WATER_HI, SHORE = col("0f1b33"), col("1c3158"), col("3d62a0")
DOT, ORB, INK = col("ffffff"), col("ffd36a"), col("05060a")
WALKABLE = ".RBAFh"    # = TacGrid.walkable: where a click lands


def load(mid):
    return json.loads((MAPS / f"{mid}.json").read_text())


def canvas(w, h, fill=CLEAR):
    return [[fill] * w for _ in range(h)]


def rect(img, x, y, w, h, c):
    for yy in range(max(0, y), min(len(img), y + h)):
        for xx in range(max(0, x), min(len(img[0]), x + w)):
            img[yy][xx] = c


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def sites_of(j):
    """[(id, cx, cz, (x0, z0, x1, z1))] in cells: the plan position of each site box (blocks / cell size)."""
    cell = j["cell"]
    out = []
    for s in j["sites"]:
        x0, z0, x1, z1 = s["min"][0] / cell, s["min"][2] / cell, s["max"][0] / cell, s["max"][2] / cell
        out.append((s["id"], (x0 + x1) / 2, (z0 + z1) / 2, (x0, z0, x1, z1)))
    return out


def diamond(img, x, y, r, c, edge=INK):
    """A filled diamond of radius r with a dark outline."""
    for dy in range(-r - 1, r + 2):
        for dx in range(-r - 1, r + 2):
            d = abs(dx) + abs(dy)
            if d <= r + 1 and 0 <= y + dy < len(img) and 0 <= x + dx < len(img[0]):
                img[y + dy][x + dx] = c if d <= r else edge


def paint(j, x0c, y0c, cells, size):
    """The square view of `cells` plan cells from (x0c, y0c), `size` px wide. Each pixel takes the plan character under its centre."""
    plan = j["plan"]
    rows, cols = len(plan), len(plan[0])
    ppc = size / cells
    t = max(1, round(ppc / 7))                      # the outline: 1 px on the overview, 2 px zoomed in

    def char(x, y):
        cx, cy = math.floor(x0c + (x + 0.5) / ppc), math.floor(y0c + (y + 0.5) / ppc)
        return plan[cy][cx] if 0 <= cy < rows and 0 <= cx < cols else "#"

    def near(x, y, test, reach):
        return any(test(char(x + dx, y + dy)) for dx, dy in ((reach, 0), (-reach, 0), (0, reach), (0, -reach)))

    def walk(ch):
        return ch in WALKABLE

    boxes = [b for _, _, _, b in sites_of(j)]
    spawn = {}                                      # the box of each spawn (its cells are a ring round plain floor): tinted whole
    for mark in "RB":
        cells_ = [(cx, cy) for cy, row in enumerate(plan) for cx, o in enumerate(row) if o == mark]
        if cells_:
            spawn[mark] = (min(c[0] for c in cells_), min(c[1] for c in cells_), max(c[0] for c in cells_) + 1, max(c[1] for c in cells_) + 1)

    def spawn_at(px, py):
        return next((m for m, (a, b, c2, d) in spawn.items() if a <= px < c2 and b <= py < d), None)

    img = canvas(size, size, VOID)
    for y in range(size):
        for x in range(size):
            ch = char(x, y)
            if walk(ch):
                c = HIGH if ch == "h" else FLOOR
                px, py = x0c + (x + 0.5) / ppc, y0c + (y + 0.5) / ppc
                if ch == "A" or any(a <= px < c2 and b <= py < d for a, b, c2, d in boxes):
                    c = mix(c, SITE, 0.42 if ch == "A" else 0.2)
                elif spawn_at(px, py) and ch != "h":
                    c = mix(c, ATTACK if spawn_at(px, py) == "R" else DEFEND, 0.45)
                elif ch == "F":
                    c = FEATURE_HI if any(near(x, y, lambda o: o != "F", k) for k in range(1, t + 1)) else FEATURE
                if ch == "h" and any(near(x, y, lambda o: o != "h", k) for k in range(1, t + 1)):
                    c = HIGH_RIM                    # high ground: a raised slab with a bright lip
                elif ch != "h":
                    up, left = char(x, y - t), char(x - t, y)
                    if not walk(up) or not walk(left) or (up == "h") or (left == "h"):
                        c = mix(c, FLOOR_SHADE, 0.6)    # the shadow a wall or a slab casts on the floor below and right of it
                img[y][x] = c
            elif ch == "w":
                wave = (x + 3 * y) % 11 == 0 and (7 * x + 3 * y) % 4 < 2 and ppc >= 2    # from the pixel only: no cell rhythm
                c = WATER_HI if wave else WATER
                if any(near(x, y, walk, k) for k in range(1, t + 1)):
                    c = SHORE
                img[y][x] = c
            elif ch == "g":
                c = FOLIAGE_HI if any(near(x, y, lambda o: o != "g", k) for k in range(1, t + 1)) else FOLIAGE
                img[y][x] = c
            elif ch == "c":
                c = CRATE_HI if any(near(x, y, lambda o: o != "c", k) for k in range(1, t + 1)) else CRATE
                img[y][x] = c
            else:                                   # a wall: dark, with the crisp pale outline where the floor starts
                edge = any(near(x, y, lambda o: o != "#" and o != "w", k) for k in range(1, t + 1))
                img[y][x] = RIM if edge else WALL_FILL if any(near(x, y, lambda o: o != "#", k) for k in range(t + 1, 3 * t + 1)) else VOID
    # markers: the site diamonds (one pip = A, two = B), the ult orbs, and zoomed in the spawn points
    cell = j["cell"]

    def at(xc, zc):
        return round((xc - x0c) * ppc), round((zc - y0c) * ppc)

    big = ppc >= 8
    for sid, cx, cz, _ in sites_of(j):
        mx, my = at(cx, cz)
        r = 7 if big else 4 if ppc >= 2.5 else 3
        diamond(img, mx, my, r, SITE_HI)
        diamond(img, mx, my, r - 1, SITE, SITE)
        dots, k = (1 if sid == "A" else 2), (2 if ppc >= 2.5 else 1)
        gap = k + 1
        for i in range(dots):
            rect(img, mx - (dots * k + (dots - 1) * (gap - k)) // 2 + i * gap, my - k // 2, k, k, DOT)
    for p in j["orbs"]:
        x, y = at(p[0] / cell, p[2] / cell)
        diamond(img, x, y, 3 if big else 1, ORB)
    if big:
        for side, colr in (("SEEDBEARERS", ATTACK), ("SEALERS", DEFEND)):
            for p in j["spawns"][side]:
                x, y = at(p[0] / cell, p[2] / cell)
                diamond(img, x, y, 2, DOT)
                rect(img, x, y, 1, 1, colr)
    return img


def overview(j, size=PX):
    return paint(j, 0, 0, len(j["plan"]), size)


def zone_cells(j):
    """Cells per zone side (the plan is square, N zones across)."""
    return len(j["plan"]) // N


def zone(j, zc, zr):
    z = zone_cells(j)
    return paint(j, zc * z, zr * z, z, PX)


# ------------------------------------------------------------------ chrome (the window the plan lies in)

def chrome():
    """The 176 x 222 window (the frame system of valoblock_menukit.py): the plan's frame with corner brackets, the teammates' rail,
    and the three controls: back (a steel-rimmed well), the ability and its charges (a lit well), cancel (a red button)."""
    import valoblock_menukit as kit
    import valoblock_ui as ui
    accent = kit.ACCENT_TAC
    img = ui.window_base(accent)
    x, y, w = vb.TAC_PLAN_X - 1, vb.TAC_PLAN_Y - 1, PX + 2          # a 1 px ring right round the plan
    kit.bevel(img, x, y, w, w, kit.mix(accent, kit.BG_BOTTOM, 0.6), kit.mix(accent, kit.BG_BOTTOM, 0.75))
    kit.brackets(img, x, y, w, w, accent, 9)
    kit.rect(img, vb.TAC_PLAN_X, vb.TAC_PLAN_Y, PX, PX, VOID)
    kit.tray(img, *ui.slot_box(0, 0, 1, 4))
    for r in range(4):
        ui.slot_well(img, 0, r, fill=kit.col("121622"))             # the teammates
    for c, r0 in ((1, 0), (7, 0), (8, 0)):                          # the margins: quiet angled bars, brightest at the top
        bx, by, bw, bh = ui.slot_box(c, r0, 1, 5)
        if c == 7:
            bx += 3
        kit.slashes(img, bx + 3, by + 2, bw - 6, 20, accent, 6, 3, (0.35, 0.35))
    ui.slot_well(img, 0, 5, fill=kit.col("161b28"), light=kit.STEEL)   # back: a well, the slot is empty when there is nothing to undo
    ui.slot_well(img, 4, 5, glow=accent)                            # the ability and its charges
    bx, by, bw, bh = ui.slot_box(4, 5)
    kit.brackets(img, bx - 2, by - 2, bw + 4, bh + 4, accent, 5)
    kit.raised(img, *ui.slot_box(8, 5), kit.col("a23a48"))          # cancel
    return img


# ------------------------------------------------------------------ item sprites

def sprite_dim():
    """Laid over a slot that is out of reach: a dark, see-through square."""
    return canvas(16, 16, col("05060a", 170))


def sprite_far():
    """Laid over a plan cell that is out of reach: thin dark diagonal hatching, so the drawing under it stays readable."""
    img = canvas(16, 16)
    for a in range(16):
        for b in range(16):
            if (a + b) % 4 == 0:
                img[b][a] = col("05060a", 150)
    return img


def sprite_mark():
    """A smoke the player already placed (and a ballot of the map vote): a pale ring with a dot, outlined so it holds on the light floor."""
    import valoblock_menukit as kit
    return kit.glyph(kit.union(kit.ring(8, 8, 6.2, 3.6), kit.disc(8, 8, 1.6)), col("f2efe0"), 16)


def sprite_pick():
    """A spot already picked (a Miasma Wall end, a Storm Call spot): a gold cross."""
    import valoblock_menukit as kit
    return kit.cross_glyph(col("ffd36a"))


def sprite_back():
    import valoblock_menukit as kit
    return kit.arrow_glyph()


def sprite_cancel():
    import valoblock_menukit as kit
    return kit.cross_glyph(col("ffe1e4"))


SPRITES = {"tac_dim": sprite_dim, "tac_far": sprite_far, "tac_mark": sprite_mark, "tac_pick": sprite_pick,
           "tac_back": sprite_back, "tac_cancel": sprite_cancel}


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
