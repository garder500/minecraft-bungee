#!/usr/bin/env python3
"""Draws the Wings pixel art (stdlib only, deterministic) and the pack JSON that declares it.

    python3 resource-pack/art/wings_art.py [--preview DIR]

Writes, under resource-pack/pack/assets/vampyuyu/:
  textures/item/wings/wings.png, wings_tier2..5.png   16x16 item icons of the five tiers
  textures/item/wings/core_1..5.png                   16x16 Wing Core icons
  textures/entity/equipment/wings/wings_tier1..5.png  64x32 elytra-layout wing textures (layer type "wings")
  textures/entity/equipment/humanoid/wings_torso.png  64x32 humanoid-layer harness (layer type "humanoid")
  equipment/wings.json, wings_tier1..5.json           equipment assets (asset id vampyuyu:wings, vampyuyu:wings_tierN)
  items/wings/*.json, models/item/wings/*.json        item definitions (item model vampyuyu:wings/<name>)

Elytra UV layout (64x32): the visible wing is the 10x20 face at x36..46, y2..22 (the model mirrors it for
the second wing); the inner face is x24..34, y2..22. Everything inside x22..46, y0..22 is painted.
"""
import json, pathlib, struct, sys, zlib

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack" / "assets" / "vampyuyu"

# palette per tier: (light feather, mid feather, dark feather, outline, glow)
TIERS = {
    1: ((255, 250, 235), (247, 226, 160), (214, 170, 72), (150, 104, 36), (255, 244, 190)),
    2: ((250, 255, 255), (214, 240, 248), (150, 200, 226), (70, 112, 160), (120, 230, 255)),
    3: ((255, 248, 252), (250, 214, 236), (222, 150, 196), (138, 70, 120), (255, 150, 220)),
    4: ((252, 246, 255), (222, 204, 250), (166, 130, 226), (84, 54, 150), (190, 140, 255)),
    5: ((255, 255, 250), (255, 236, 150), (255, 190, 70), (190, 110, 20), (140, 255, 235)),
}
CORE = {  # (light, mid, dark, outline) of the five Wing Cores
    1: ((220, 240, 255), (130, 190, 240), (60, 110, 200), (24, 40, 110)),
    2: ((255, 232, 255), (226, 150, 240), (150, 70, 200), (70, 24, 120)),
    3: ((255, 245, 190), (255, 200, 60), (214, 130, 20), (110, 56, 8)),
    4: ((255, 220, 244), (255, 120, 190), (200, 50, 130), (100, 14, 70)),
    5: ((235, 255, 255), (120, 240, 255), (40, 170, 230), (10, 60, 130)),
}


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
    """16x16 pair of feathered wings with a gem on the chest; one more sparkle per tier."""
    light, mid, dark, outline, glow = TIERS[tier]
    shade = {"l": light, "m": mid, "d": dark, "o": outline, "g": glow}
    px = {}
    for row, line in enumerate(WING_ROWS):
        y = 1 + row
        for col, ch in enumerate(line):
            if ch == ".":
                continue
            c = shade[ch]
            if ch in "lmd" and (col + row) % 4 == 0:  # feather ribs
                c = mix(c, dark, 0.3)
            px[(col, y)] = c + (255,)
            px[(15 - col, y)] = c + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):     # body gem between the wings
        px[(x, y)] = glow + (255,)
    px[(7, 9)] = outline + (255,)
    px[(8, 9)] = outline + (255,)
    for i in range(tier - 1):                           # sparkles: one more per tier
        sx, sy = ((2, 14), (13, 14), (4, 0), (11, 0))[i % 4]
        px[(sx, sy)] = light + (255,)
        px.setdefault((sx + 1, sy), glow + (200,))
        px.setdefault((sx - 1, sy), glow + (200,))
    return {k: v for k, v in px.items() if 0 <= k[0] < 16 and 0 <= k[1] < 16}


def icon_core(tier):
    """16x16 faceted crystal with a halo."""
    light, mid, dark, outline = CORE[tier]
    px = {}
    shape = [(7, 1, 8), (6, 2, 9), (5, 3, 10), (4, 4, 11), (3, 5, 12), (3, 6, 12), (3, 7, 12), (4, 8, 11),
             (4, 9, 11), (5, 10, 10), (5, 11, 10), (6, 12, 9), (7, 13, 8)]
    for left, y, right in shape:
        for x in range(left, right + 1):
            t = (x - left) / max(1, right - left)
            c = light if t < 0.3 and y < 8 else (mid if t < 0.62 else dark)
            if y >= 9 and t < 0.3:
                c = mid
            px[(x, y)] = c + (255,)
    cells = set(px)
    for (x, y) in list(cells):
        if any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[(x, y)] = outline + (255,)
    for (x, y) in ((7, 3), (6, 4), (6, 5), (5, 6)):     # highlight streak
        px[(x, y)] = (255, 255, 255, 255)
    halo = mid + (70,)
    for (x, y) in list(cells):
        for dx, dy in ((2, 0), (-2, 0), (0, 2)):
            px.setdefault((x + dx, y + dy), halo)
    if tier >= 3:                                       # small orbiting sparkle for the higher cores
        px[(13, 2)] = (255, 255, 255, 255)
        px[(12, 2)] = light + (170,)
        px[(14, 2)] = light + (170,)
        px[(13, 1)] = light + (170,)
        px[(13, 3)] = light + (170,)
    return {k: v for k, v in px.items() if 0 <= k[0] < 16 and 0 <= k[1] < 16}


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
        png(ASSETS / "textures" / "entity" / "equipment" / "wings" / f"wings_tier{tier}.png", 64, 32,
            wings_texture(tier))
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
