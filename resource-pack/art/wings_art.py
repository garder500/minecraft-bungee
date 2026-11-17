#!/usr/bin/env python3
"""Draws the Wings pixel art (stdlib only, deterministic) and the pack JSON that declares it.

    python3 resource-pack/art/wings_art.py [--preview DIR]

Writes, under resource-pack/pack/assets/vampyuyu/:
  textures/item/wings/wings.png, wings_tier2..5.png   16x16 item icons of the five tiers
  textures/item/wings/core_1..5.png                   16x16 Wing Core icons
  textures/entity/equipment/wings/wings_tier1..5.png  64x32 FULLY TRANSPARENT elytra-layer textures (see below)
  textures/entity/equipment/humanoid/wings_torso.png  64x32 humanoid-layer harness (layer type "humanoid")
  equipment/wings.json, wings_tier1..5.json           equipment assets (asset id vampyuyu:wings, vampyuyu:wings_tierN)
  items/wings/*.json, models/item/wings/*.json        item definitions (item model vampyuyu:wings/<name>)

Worn look (the real 3D model, shown by the plugin as an ItemDisplay on the wearer's back):
  textures/item/wings/worn_tier1..5.png               16x16 feather strips (one 2 px column pair per feather)
  models/item/wings/worn_tierN_folded.json / _spread.json   feather-by-feather 3D wings, pivot = shoulder blades
  items/wings/worn_tierN_folded.json / _spread.json   item definitions (item model vampyuyu:wings/worn_tierN_<state>)
The elytra equipment layer is a fully transparent texture: the vanilla geometry never shows, flight is untouched.
Model space: the pivot (8, 8, 8) is the middle of the shoulder blades, +x = the wing side, the wing plane is
x/y and the feathers lie behind the body on +z (the item display renderer turns the model half a turn, so
model +z ends up behind the player: see docs/skyblock-notes/wings.md).

Elytra UV layout (64x32): the visible wing is the 10x20 face at x36..46, y2..22 (the model mirrors it for
the second wing); the inner face is x24..34, y2..22. Everything inside x22..46, y0..22 is painted.
"""
import json, pathlib, struct, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack" / "assets" / "vampyuyu"

def _h(*hexes):
    return tuple(tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes)


# Realm palettes (shadow, base, light, highlight, glow) from the lore bible: tier 1 Citadel of Golden Sands,
# 2 Frozen Heights, 3 Abyssal Depths, 4 Ethereal Necropolis, 5 the Astral Interiority.
REALM = {
    1: _h("#5a3a12", "#a8742a", "#f2c14e", "#fff1b0", "#3fb6a8"),
    2: _h("#1f3557", "#3b6fa8", "#7fc8e8", "#e6f7ff", "#b6a8ff"),
    3: _h("#0a1a2a", "#12405a", "#2a8a9a", "#bff0f0", "#ff6ab0"),
    4: _h("#241f3a", "#4a4570", "#9c95c8", "#e8e4ff", "#6affd8"),
    5: _h("#0e0a1a", "#3a1a6a", "#8a4aff", "#e0c8ff", "#ff4a8a"),
}
OUTLINE = (20, 15, 24)
# palette per tier for the equipment texture: (light feather, mid feather, dark feather, outline, glow)
TIERS = {t: (r[3], r[2], r[1], r[0], r[4]) for t, r in REALM.items()}
CORE = TIERS  # the Wing Cores take the same Realm as their wings


def png(path, w, h, px):
    """px: dict (x, y) -> (r, g, b, a). Writes an RGBA PNG with fixed compression (reproducible)."""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(px.get((x, y), (0, 0, 0, 0)))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def wing_face(tier):
    """The 10x20 feathered wing: dict (x, y) -> rgba. x=0 is the body side, x=9 the outer edge."""
    light, mid, dark, outline, glow = TIERS[tier]
    # how far each column reaches down (feather tips), inner columns are shorter (taper to the body)
    reach = [12, 15, 17, 19, 20, 20, 19, 18, 16, 13]
    top = [0, 0, 0, 0, 0, 0, 0, 1, 2, 4]
    cells = {}
    for x in range(10):
        for y in range(top[x], reach[x]):
            cells[(x, y)] = None
    out = {}
    for (x, y) in cells:
        t = y / 19.0
        base = mix(light, mid, min(1.0, t * 1.6)) if t < 0.62 else mix(mid, dark, (t - 0.62) / 0.38)
        # feather ribs: diagonal darker lines every 3 columns
        if (x + y // 3) % 3 == 0 and y > 2:
            base = mix(base, dark, 0.35)
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if y < 2:  # leading edge: the gold arm bone
            base = mix(dark, outline, 0.25) if y == 0 else dark
        elif edge:
            base = mix(base, outline, 0.55)
        if y == reach[x] - 1 and y > 6:  # glowing feather tips
            base = mix(base, glow, 0.85)
        out[(x, y)] = base + (255,)
    # soft halo under the tips (semi transparent), the "glow" of the tier
    strength = 60 + 25 * tier
    for x in range(10):
        gy = reach[x]
        if gy < 20 and (x, gy) not in out:
            out[(x, gy)] = glow + (strength,)
    return out


def wings_texture(tier):
    px = {}
    face = wing_face(tier)
    for (x, y), c in face.items():
        px[(36 + x, 2 + y)] = c                       # outer face (the visible one)
        px[(24 + 9 - x, 2 + y)] = c                   # inner face, mirrored so the feathers meet
    _, mid, dark, outline, glow = TIERS[tier]
    for x in range(24, 34):                           # top and bottom caps of the wing box
        px[(x, 0)] = dark + (255,)
        px[(x + 10, 0)] = dark + (255,)
    for y in range(2, 22):                            # thin side strips
        px[(22, y)] = outline + (255,)
        px[(23, y)] = outline + (255,)
        px[(34, y)] = outline + (255,)
        px[(35, y)] = outline + (255,)
    for y in range(2, 22):
        px.setdefault((22, y), outline + (255,))
    return px


def torso_texture():
    """Humanoid layer (armor layer 1, 64x32): a thin gold harness over the chest and back."""
    gold, shade, light = (222, 178, 74, 255), (170, 118, 40, 255), (255, 240, 180, 255)
    px = {}
    for base_x in (20, 32):                           # chest (front) and back, 8 wide x 12 tall at y=20
        for i in range(8):                            # two crossing straps
            for (x, y) in ((base_x + i, 20 + i), (base_x + 7 - i, 20 + i)):
                px[(x, y)] = gold
                if x + 1 < base_x + 8:
                    px.setdefault((x + 1, y), shade)
        for x in range(base_x, base_x + 8):           # shoulder band and belt
            px[(x, 20)] = light
            px[(x, 31)] = shade
        px[(base_x + 3, 24)] = light                   # clasp
        px[(base_x + 4, 24)] = light
        px[(base_x + 3, 25)] = gold
        px[(base_x + 4, 25)] = gold
    for x in range(16, 20):                           # side strips of the body box
        for y in range(20, 32):
            if y in (20, 31):
                px[(x, y)] = shade
    return px


WING_ROWS = [  # left wing, 8 columns (x=7 is next to the body), rows from y=1; o outline, l/m/d shades, g glow
    "oo......",
    "olo.....",
    "ollo....",
    "olllo.oo",
    "ollllolo",
    "olllllmo",
    "ollllmmo",
    ".olllmdo",
    ".ollmddo",
    "..omdddo",
    "..ogddo.",
    "...ogo..",
    "....o...",
]


def icon_wings(tier):
    """16x16 pair of feathered wings (the wing silhouette) with the Realm glow pixel on the chest; flat steps only."""
    sh, base, lt, hi, glow = REALM[tier]
    shade = {"l": hi, "m": lt, "d": base, "o": OUTLINE, "g": glow}
    px = {}
    for row, line in enumerate(WING_ROWS):
        y = 1 + row
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            c = shade[ch]
            if ch in "lm" and (col + row) % 4 == 0:  # feather ribs
                c = lt if ch == "l" else base
            px[(col, y)] = c + (255,)
            px[(15 - col, y)] = c + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):     # the Realm motif between the wings
        px[(x, y)] = glow + (255,)
    px[(7, 9)] = OUTLINE + (255,)
    px[(8, 9)] = OUTLINE + (255,)
    for i in range(tier - 1):                           # sparkles: one more per tier
        sx, sy = ((2, 14), (13, 14), (4, 0), (11, 0))[i % 4]
        px[(sx, sy)] = hi + (255,)
        px.setdefault((sx + 1, sy), lt + (255,))
        px.setdefault((sx - 1, sy), lt + (255,))
    return {k: v for k, v in px.items() if 0 <= k[0] < 16 and 0 <= k[1] < 16}


def icon_core(tier):
    """16x16 faceted crystal (outline, light top left, glow core), no halo."""
    sh, base, lt, hi, glow = REALM[tier]
    px = {}
    shape = [(7, 1, 8), (6, 2, 9), (5, 3, 10), (4, 4, 11), (3, 5, 12), (3, 6, 12), (3, 7, 12), (4, 8, 11),
             (4, 9, 11), (5, 10, 10), (5, 11, 10), (6, 12, 9), (7, 13, 8)]
    for left, y, right in shape:
        for x in range(left, right + 1):
            t = (x - left) / max(1, right - left)
            c = lt if t < 0.3 and y < 8 else (base if t < 0.62 else sh)
            if y >= 9 and t < 0.3:
                c = base
            px[(x, y)] = c + (255,)
    cells = set(px)
    for (x, y) in list(cells):
        if any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[(x, y)] = OUTLINE + (255,)
    for (x, y) in ((7, 3), (6, 4), (6, 5), (5, 6)):     # highlight streak
        px[(x, y)] = hi + (255,)
    for (x, y) in ((8, 7), (9, 7), (8, 8)):             # the glow pixel marks the Realm motif
        px[(x, y)] = glow + (255,)
    if tier >= 3:                                       # small orbiting sparkle for the higher cores
        px[(13, 2)] = hi + (255,)
        for (x, y) in ((12, 2), (14, 2), (13, 1), (13, 3)):
            px[(x, y)] = lt + (255,)
    return {k: v for k, v in px.items() if 0 <= k[0] < 16 and 0 <= k[1] < 16}


# ---- worn 3D model ----------------------------------------------------------------------------------------
FEATHERS = 6
# per feather: (length in px, angle from straight down in degrees, spread / folded). 90 = horizontal.
# Spread never goes above the horizontal (angle <= 90) so no feather ever rises above the shoulder plane.
SPREAD_ANGLES = (90, 80, 68, 56, 44, 32)
FOLDED_ANGLES = (22, 17, 12, 8, 4, 0)
LENGTHS_SPREAD = (14, 18, 19, 18, 16, 13)
LENGTHS_FOLDED = (14, 17, 19, 18, 16, 13)
FEATHER_W = 3.0
FEATHER_T = 0.6
ROOT_X = 10.0   # the wing root sits 2 px beside the spine
# Envelope (blocks, relative to the shoulder pivot, x outward, y up, behind = +): the plugin's WingsMath.ENVELOPE
# must contain every vertex; the generator asserts it.
ENVELOPE = {"folded": (0.60, -1.25, 0.20, 0.0, 0.14), "spread": (1.35, -1.20, 0.20, 0.0, 0.14)}
# (max |x|, min y, max y, min behind, max behind)


def worn_texture(tier):
    """16x16: feather k is the 2 px column pair x=2k..2k+1, light at the root down to the glowing tip."""
    light, mid, dark, outline, glow = TIERS[tier]
    px = {}
    for k in range(FEATHERS):
        for y in range(16):
            t = y / 15.0
            c = mix(light, mid, t * 1.5) if t < 0.66 else mix(mid, dark, (t - 0.66) / 0.34)
            if y >= 14:
                c = mix(c, glow, 0.85)
            if y == 0:
                c = mix(dark, outline, 0.3)
            for x in (2 * k, 2 * k + 1):
                col = mix(c, outline, 0.5) if x == 2 * k + 1 else c  # one darker rib per feather
                px[(x, y)] = col + (255,)
    px[(14, 0)] = outline + (255,)   # edge pixel for the thin sides
    px[(14, 1)] = outline + (255,)
    px[(15, 0)] = glow + (255,)      # the Realm gem on the back plate
    px[(15, 1)] = mix(glow, TIERS[tier][0], 0.5) + (255,)
    return px


def _corners(x0, x1, y0, y1, ox, oy, theta):
    import math
    c, s_ = math.cos(math.radians(theta)), math.sin(math.radians(theta))
    out = []
    for x in (x0, x1):
        for y in (y0, y1):
            dx, dy = x - ox, y - oy
            out.append((ox + dx * c - dy * s_, oy + dx * s_ + dy * c))
    return out


def worn_model(tier, state):
    """The wings as feather boxes plus a back plate. Returns (model dict, vertices in px relative to the pivot)."""
    angles = SPREAD_ANGLES if state == "spread" else FOLDED_ANGLES
    lengths = LENGTHS_SPREAD if state == "spread" else LENGTHS_FOLDED
    elements, verts = [], []
    z0 = 8.0
    for side in (1, -1):                       # +1 the +x wing, -1 its mirror
        for k, (a, ln) in enumerate(zip(angles, lengths)):
            z = z0 + 0.2 + (k % 2) * FEATHER_T  # alternate layers: no z-fighting between neighbours
            ry = 8.0 + 0.0
            if a >= 67.5:                      # horizontal base box, tilted by a - 90
                base = (ROOT_X, ROOT_X + ln, ry - FEATHER_W / 2, ry + FEATHER_W / 2)
                theta = a - 90.0
                rot_face = 90
            else:                              # vertical base box (hangs down), tilted outward by a
                base = (ROOT_X - FEATHER_W / 2, ROOT_X + FEATHER_W / 2, ry - ln, ry)
                theta = float(a)
                rot_face = 0
            x0, x1, y0, y1 = base
            # rotation is about the root (ROOT_X, ry)
            if side == -1:                     # mirror x about the pivot x = 8
                x0, x1 = 16 - x1, 16 - x0
                ox, th = 16 - ROOT_X, -theta
            else:
                ox, th = ROOT_X, theta
            u = (2 * k, 0, 2 * k + 2, 16)
            face = {"uv": list(u), "texture": "#f", "rotation": rot_face}
            side_face = {"uv": [14, 0, 15, 2], "texture": "#f"}
            el = {"from": [x0, y0, z], "to": [x1, y1, z + FEATHER_T],
                  "faces": {"north": dict(face), "south": dict(face), "up": dict(side_face), "down": dict(side_face),
                            "east": dict(side_face), "west": dict(side_face)}}
            if abs(th) > 1e-6:
                el["rotation"] = {"angle": round(th, 3), "axis": "z", "origin": [ox, ry, z]}
            elements.append(el)
            for (vx, vy) in _corners(x0, x1, y0, y1, ox, ry, th):
                for vz in (z, z + FEATHER_T):
                    verts.append((vx - 8, vy - 8, vz - 8))
    plate = {"from": [5.5, 3.0, z0], "to": [10.5, 10.0, z0 + 1.0],
             "faces": {f: {"uv": [14, 0, 15, 1], "texture": "#f"} for f in ("north", "south", "up", "down", "east", "west")}}
    plate["faces"]["south"] = {"uv": [15, 0, 16, 2], "texture": "#f"}
    elements.append(plate)
    for vx in (5.5, 10.5):
        for vy in (3.0, 10.0):
            for vz in (z0, z0 + 1.0):
                verts.append((vx - 8, vy - 8, vz - 8))
    model = {"credit": "generated by art/wings_art.py", "textures": {"f": f"vampyuyu:item/wings/worn_tier{tier}",
                                                                    "particle": f"vampyuyu:item/wings/worn_tier{tier}"},
             "elements": elements}
    return model, verts


def check_envelope(state, verts):
    """Every vertex (px relative to the pivot) lies inside the plugin's safety envelope, in blocks."""
    mx, ymin, ymax, bmin, bmax = ENVELOPE[state]
    for (x, y, z) in verts:
        assert abs(x) / 16 <= mx + 1e-6, (state, "x", x)
        assert ymin <= y / 16 <= ymax + 1e-6, (state, "y", y)
        assert bmin - 1e-6 <= z / 16 <= bmax + 1e-6, (state, "behind", z)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def item_files(name):
    write_json(ASSETS / "items" / "wings" / f"{name}.json",
               {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/wings/{name}"}})
    write_json(ASSETS / "models" / "item" / "wings" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/wings/{name}"}})


def build(preview=None):
    names = {1: "wings"}
    names.update({t: f"wings_tier{t}" for t in range(2, 6)})
    for tier, name in names.items():
        png(ASSETS / "textures" / "item" / "wings" / f"{name}.png", 16, 16, icon_wings(tier))
        item_files(name)
        # the worn look is the 3D display: the elytra layer stays fully transparent (flight is untouched)
        png(ASSETS / "textures" / "entity" / "equipment" / "wings" / f"wings_tier{tier}.png", 64, 32, {})
        png(ASSETS / "textures" / "item" / "wings" / f"worn_tier{tier}.png", 16, 16, worn_texture(tier))
        for state in ("folded", "spread"):
            model, verts = worn_model(tier, state)
            check_envelope(state, verts)
            write_json(ASSETS / "models" / "item" / "wings" / f"worn_tier{tier}_{state}.json", model)
            write_json(ASSETS / "items" / "wings" / f"worn_tier{tier}_{state}.json",
                       {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/wings/worn_tier{tier}_{state}"}})
        write_json(ASSETS / "equipment" / f"wings_tier{tier}.json", {"layers": {
            "humanoid": [{"texture": "vampyuyu:wings_torso"}],
            "wings": [{"texture": f"vampyuyu:wings_tier{tier}"}]}})
    # equipment/wings.json: the base asset (tier 1 look), used by items that only say vampyuyu:wings
    write_json(ASSETS / "equipment" / "wings.json", {"layers": {
        "humanoid": [{"texture": "vampyuyu:wings_torso"}],
        "wings": [{"texture": "vampyuyu:wings_tier1"}]}})
    png(ASSETS / "textures" / "entity" / "equipment" / "humanoid" / "wings_torso.png", 64, 32, torso_texture())
    for tier in range(1, 6):
        png(ASSETS / "textures" / "item" / "wings" / f"core_{tier}.png", 16, 16, icon_core(tier))
        item_files(f"core_{tier}")
    if preview:
        write_previews(pathlib.Path(preview))


def write_previews(out):
    """Nearest-neighbour enlarged sheets for a quick look (not part of the pack)."""
    scale = 8
    sheet = {}
    for tier in range(1, 6):
        for (x, y), c in icon_wings(tier).items():
            sheet[((tier - 1) * 17 + x, y)] = c
        for (x, y), c in icon_core(tier).items():
            sheet[((tier - 1) * 17 + x, 17 + y)] = c
    big = {}
    for (x, y), c in sheet.items():
        for dx in range(scale):
            for dy in range(scale):
                big[(x * scale + dx, y * scale + dy)] = c
    png(out / "icons.png", 85 * scale, 33 * scale, big)
    tex = {}
    for tier in range(1, 6):
        for (x, y), c in wings_texture(tier).items():
            for dx in range(scale):
                for dy in range(scale):
                    tex[((tier - 1) % 2 * 66 * scale + x * scale + dx, (tier - 1) // 2 * 34 * scale + y * scale + dy)] = c
    png(out / "textures.png", 132 * scale, 102 * scale, tex)


if __name__ == "__main__":
    build(sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "--preview" else None)
    print("wings art written")
