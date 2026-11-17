#!/usr/bin/env python3
"""The worn look of the ladder armour sets and the three Starcore sidegrades, as clean 64x32 pixel art.

    python3 resource-pack/art/armor_layers.py [--preview out.png]        (stdlib only, deterministic)

A worn armour is two textures in the vanilla player-skin layout (exact UVs of the armour model), humanoid/<set>.png
(chestplate on body and arms, boots on the feet) and humanoid_leggings/<set>.png (belt and legs). The helmet is a 3D
model (helmet_models.py), so the head of the layer stays empty. Every piece is drawn by hand with flat plates:
a bevelled plate has four tones (light from the top left: highlight corner, lit top/left edge, body, shadowed
bottom/right edge), no dithering, no noise. One trim colour and ONE glowing motif per set, from the Realm palettes:

    bronze    Flux machinery bronze plates, dark iron trim, cyan Flux core
    ember     Cracked Ashes obsidian plates, lava trim, a jagged lava crack
    rime      Frozen Heights ice plates, navy trim, a violet stasis crystal
    void      Necropolis dark plates, lavender ash trim, a pink rift eye
    starcore  Citadel gold plates, turquoise trim, a turquoise star
    cinder_starcore / grove_starcore / frost_starcore: the Starcore gold with the Realm's trim and gem colour.

The front has the motif, the back has a spine and shoulder blades, so both read at a glance. armor_tiers.py skips
these files (imported.txt: equipment/<set>) and keeps the equipment/<set>.json layers this script writes.
"""
import json
import pathlib
import sys

from helmet_models import png

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack/assets/vampyuyu"
W, H = 64, 32


def rgb(hexes):
    return tuple(tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes.split())


def lighter(c, t=0.4):
    return tuple(int(v + (255 - v) * t) for v in c)


def darker(c, t=0.45):
    return tuple(int(v * (1 - t)) for v in c)


def trim4(hexes):
    dk, md, lt = rgb(hexes)
    return (dk, md, lt, lighter(lt))


# plate tones are (shadow, body, light, highlight); glow is (rim, glow, core); motif bitmaps use
# g = glow, d = rim, b = core, k = dark socket floor, . = socket floor
SETS = {
    "bronze": dict(  # Flux machinery: bronzite casing, dark iron trim, cyan core in a window
        plate=rgb("#4a2a12 #7a4a1e #c98442 #f2c14e"), trim=trim4("#1c1a22 #3a3742 #6a6676"),
        glow=rgb("#1a8f9c #4af0ff #e6ffff"), motif=["dggd", "gbbg", "gbbg", "dggd"], motif_xy=(2, 3)),
    "ember": dict(  # Cracked Ashes: obsidian split by lava veins
        plate=rgb("#120a16 #2a1a2e #4a3550 #7a5a82"), trim=trim4("#5a1a10 #c8461e #ff8a2a"),
        glow=rgb("#c8461e #ff8a2a #ffe08a"), motif=[".gb.", ".gb.", "gb..", "..bg", ".gb.", ".gb."], motif_xy=(2, 2)),
    "rime": dict(  # Frozen Heights: plates of ice, a stasis crystal
        plate=rgb("#1f3557 #3b6fa8 #7fc8e8 #e6f7ff"), trim=trim4("#101e3a #1f3557 #5a86b8"),
        glow=rgb("#7a68d8 #b6a8ff #f0ecff"), motif=[".gg.", "gbgg", "gggg", "gggg", ".gg."], motif_xy=(2, 3)),
    "void": dict(  # Ethereal Necropolis: dark ash plates, a rift that looks back
        plate=rgb("#15121f #241f3a #4a4570 #9c95c8"), trim=trim4("#2e2a4a #6a6498 #9c95c8"),
        glow=rgb("#b02a68 #ff4a8a #ffd6e8"), motif=[".dggd.", "dgbbgd", "dgbbgd", ".dggd."], motif_xy=(1, 3)),
    "starcore": dict(  # Astral Interiority star on Citadel gold
        plate=rgb("#5a3a12 #a8742a #f2c14e #fff1b0"), trim=trim4("#14403e #2a8a82 #3fb6a8"),
        glow=rgb("#2a8a82 #3fb6a8 #d2fff6"), motif=["..gg..", "..gg..", "ggbbgg", "ggbbgg", "..gg..", "..gg.."],
        motif_xy=(1, 2)),
}
for realm, trim, glow in (("cinder", "#5a1a10 #c8461e #ff8a2a", "#c8461e #ff8a2a #ffe08a"),
                          ("grove", "#2c3a1e #4f6b2a #8fb84a", "#8fb84a #d6f08a #f1e7c9"),
                          ("frost", "#1f3557 #3b6fa8 #7fc8e8", "#7a68d8 #b6a8ff #f0ecff")):
    SETS[f"{realm}_starcore"] = dict(SETS["starcore"], trim=trim4(trim), glow=rgb(glow))

# face rectangles of the vanilla layout: (u, v, width, height, depth)
BOXES = {"body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4)}
SIDE_SHIFT = {"front": 0, "right": 0, "left": 1, "back": 1}  # light from the top left: the far sides are a step darker


def faces(box):
    u, v, w, h, d = BOXES[box]
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


class Grid:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def fill(self, x, y, w, h, c):
        for j in range(h):
            for i in range(w):
                self.set(x + i, y + j, c)

    def bevel(self, x, y, w, h, t):
        """A flat plate: body tone, lit top and left edges, shadowed bottom and right edges, bright top-left corner."""
        for j in range(h):
            for i in range(w):
                if j == h - 1 or i == w - 1:
                    c = t[0]
                elif i == 0 and j == 0:
                    c = t[3]
                elif i == 0 or j == 0:
                    c = t[2]
                else:
                    c = t[1]
                self.set(x + i, y + j, c)


def shifted(t, n):
    """The tones one step darker per n (the darkest repeated), so side and back faces sit in the shade."""
    return (t[0],) * n + tuple(t[:4 - n]) if n else tuple(t)


def motif(g, s):
    """The set's glowing motif in a trim socket (the socket never covers the collar rows 0-1)."""
    bmp = s["motif"]
    mx, my = s["motif_xy"]
    mw, mh = len(bmp[0]), len(bmp)
    sx, sy = mx - 1, max(2, my - 1)
    g.bevel(sx, sy, mw + 2, my + mh + 1 - sy, s["trim"])
    rim, glow, core = s["glow"]
    tone = {"g": glow, "d": rim, "b": core}
    for j, row in enumerate(bmp):
        for i, ch in enumerate(row):
            g.set(mx + i, my + j, tone.get(ch, s["plate"][0]))


def chest_front(s, shift):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(8, 12)
    g.bevel(0, 0, 8, 2, t)                       # collar
    g.bevel(0, 2, 3, 6, p)                       # pectorals
    g.bevel(5, 2, 3, 6, p)
    g.fill(3, 2, 2, 8, t[0])                     # sternum
    g.fill(3, 2, 1, 8, t[1])
    g.bevel(0, 8, 3, 2, p)                       # lower plates
    g.bevel(5, 8, 3, 2, p)
    motif(g, {**s, "plate": p, "trim": t})
    g.bevel(0, 10, 8, 2, t)                      # belt
    g.set(3, 10, t[3])
    g.set(4, 10, t[2])
    g.set(3, 11, t[1])
    g.set(4, 11, t[0])
    return g


def chest_back(s, shift):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(8, 12)
    g.bevel(0, 0, 8, 2, t)
    g.bevel(0, 2, 3, 6, p)                       # shoulder blades
    g.bevel(5, 2, 3, 6, p)
    g.fill(3, 2, 2, 8, t[0])                     # spine
    g.fill(3, 2, 1, 8, t[1])
    for y in (4, 6):                             # spine ribs
        g.set(3, y, t[3])
    g.bevel(0, 8, 3, 2, p)
    g.bevel(5, 8, 3, 2, p)
    g.bevel(0, 10, 8, 2, t)
    return g


def chest_side(s, shift):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(4, 12)
    g.bevel(0, 0, 4, 2, t)
    g.bevel(0, 2, 4, 8, p)
    g.bevel(0, 10, 4, 2, t)
    return g


def arm(s, shift):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(4, 12)
    g.bevel(0, 0, 4, 4, p)                       # pauldron
    g.bevel(0, 4, 4, 1, t)                       # its trim lip
    g.fill(0, 5, 4, 3, p[0])                     # sleeve
    g.fill(0, 5, 1, 3, p[1])
    g.bevel(0, 8, 4, 4, p)                       # vambrace
    g.bevel(0, 8, 4, 1, t)
    g.bevel(0, 11, 4, 1, t)
    return g


def boot(s, shift, front):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(4, 5)                               # the last five rows of the leg
    g.bevel(0, 0, 4, 1, t)                       # cuff
    g.bevel(0, 1, 4, 2, p)                       # greave
    g.bevel(0, 3, 4, 1, p)                       # boot
    if front:
        g.set(1, 3, p[3])                        # toe cap catches the light
    g.fill(0, 4, 4, 1, t[0])                     # sole
    return g


def thigh(s, shift, front):
    p, t = shifted(s["plate"], shift), shifted(s["trim"], shift)
    g = Grid(4, 12)
    g.bevel(0, 0, 4, 5, p)                       # tasset
    if not front:
        g.fill(0, 2, 4, 1, p[0])                 # back lamellae
    g.bevel(0, 5, 4, 1, t)                       # lip
    g.bevel(0, 6, 4, 2, t)                       # knee guard
    if front:
        g.set(1, 6, t[3])
    g.bevel(0, 8, 4, 4, p)                       # shin
    return g


def belt(s, shift):
    t = shifted(s["trim"], shift)
    p = shifted(s["plate"], shift)
    g = Grid(8, 12)
    g.fill(0, 8, 8, 1, p[0])
    g.bevel(0, 9, 8, 3, t)
    return g


def blit(tex, g, x, y, flip=False):
    for j in range(g.h):
        for i in range(g.w):
            c = g.px[j][g.w - 1 - i if flip else i]
            if c is not None:
                tex[y + j][x + i] = c


def worn(s):
    tex = [[None] * W for _ in range(H)]
    body, ar, leg = faces("body"), faces("arm"), faces("leg")
    for name, fn in (("front", chest_front), ("back", chest_back)):
        x, y, _, _ = body[name]
        blit(tex, fn(s, SIDE_SHIFT[name]), x, y)
    for name in ("right", "left"):
        x, y, _, _ = body[name]
        blit(tex, chest_side(s, SIDE_SHIFT[name]), x, y)
    x, y, _, _ = body["top"]
    for j in range(4):
        for i in range(8):
            tex[y + j][x + i] = s["trim"][2] if j < 2 else s["trim"][1]
    x, y, _, _ = body["bottom"]
    for j in range(4):
        for i in range(8):
            tex[y + j][x + i] = s["plate"][0]
    for name in ("front", "right", "left", "back"):
        x, y, _, _ = ar[name]
        blit(tex, arm(s, SIDE_SHIFT[name]), x, y)
        x, y, _, _ = leg[name]
        blit(tex, boot(s, SIDE_SHIFT[name], name == "front"), x, y + 7)
    x, y, _, _ = ar["top"]
    g = Grid(4, 4)
    g.bevel(0, 0, 4, 4, s["plate"])
    blit(tex, g, x, y)
    x, y, _, _ = ar["bottom"]
    g = Grid(4, 4)
    g.fill(0, 0, 4, 4, s["plate"][0])
    blit(tex, g, x, y)
    x, y, _, _ = leg["bottom"]
    blit(tex, g, x, y)
    return tex


def leggings(s):
    tex = [[None] * W for _ in range(H)]
    body, leg = faces("body"), faces("leg")
    for name in ("front", "right", "left", "back"):
        x, y, w, _ = body[name]
        g = belt(s, SIDE_SHIFT[name])
        if w == 4:
            g = Grid(4, 12)
            t, p = shifted(s["trim"], SIDE_SHIFT[name]), shifted(s["plate"], SIDE_SHIFT[name])
            g.fill(0, 8, 4, 1, p[0])
            g.bevel(0, 9, 4, 3, t)
        blit(tex, g, x, y)
        x, y, _, _ = leg[name]
        blit(tex, thigh(s, SIDE_SHIFT[name], name == "front"), x, y)
    x, y, _, _ = leg["top"]
    g = Grid(4, 4)
    g.bevel(0, 0, 4, 4, s["plate"])
    blit(tex, g, x, y)
    x, y, _, _ = leg["bottom"]
    g = Grid(4, 4)
    g.fill(0, 0, 4, 4, s["plate"][0])
    blit(tex, g, x, y)
    return tex


def to_png(tex):
    return png(W, H, [[c + (255,) if c else (0, 0, 0, 0) for c in row] for row in tex])


def preview(path, scale=8):
    """A flat front and back view of the layered model (head as skin, leggings under boots under the chestplate)."""
    skin = (200, 150, 120)
    cols = []
    for name, s in SETS.items():
        w_, l_ = worn(s), leggings(s)
        views = []
        for side in ("front", "back"):
            canvas = [[(60, 60, 72)] * 16 for _ in range(32)]

            def put(layer, face, ox, oy, flip=False, fw=4, fh=12, fy=0):
                u, v, _, _ = faces(face_box[face])[side]
                for j in range(fh):
                    for i in range(fw):
                        c = layer[v + j][u + (fw - 1 - i if flip else i)]
                        if c:
                            canvas[oy + j][ox + i] = c
            face_box = {"leg": "leg", "arm": "arm", "body": "body"}
            for j in range(8):                       # head
                for i in range(8):
                    canvas[j][4 + i] = skin
            for part, ox, flip in (("leg", 4, False), ("leg", 8, True), ("arm", 0, False), ("arm", 12, True),
                                   ("body", 4, False)):
                fw = 8 if part == "body" else 4
                oy = 8 if part != "leg" else 20
                for j in range(12):
                    for i in range(fw):
                        canvas[oy + j][ox + i] = skin if part != "body" else (90, 90, 110)
                if side == "back":
                    flip = not flip
                for layer in (l_, w_):
                    if part == "arm" and layer is l_:
                        continue
                    put(layer, part, ox, oy, flip, fw)
            views.append(canvas)
        cols.append(views)
    gap = 2
    cw = 16 + gap
    img = [[(40, 40, 50)] * (cw * 2 * len(SETS)) for _ in range(32)]
    for k, views in enumerate(cols):
        for v, canvas in enumerate(views):
            for y in range(32):
                for x in range(16):
                    img[y][(k * 2 + v) * cw + x] = canvas[y][x]
    big = [[c for c in row for _ in range(scale)] for row in img for _ in range(scale)]
    pathlib.Path(path).write_bytes(png(len(big[0]), len(big), [[c + (255,) for c in row] for row in big]))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def main():
    for name, s in SETS.items():
        for folder, tex in (("humanoid", worn(s)), ("humanoid_leggings", leggings(s))):
            path = ASSETS / "textures/entity/equipment" / folder / f"{name}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(to_png(tex))
        asset = ASSETS / "equipment" / f"{name}.json"
        layers = {"humanoid": [{"texture": f"vampyuyu:{name}"}], "humanoid_leggings": [{"texture": f"vampyuyu:{name}"}]}
        if name.endswith("starcore"):  # the wings layer of the Starcore chestplate
            layers["wings"] = [{"texture": "vampyuyu:starcore_wings"}]
        write_json(asset, {"layers": layers})
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"equipment/{n}" for n in SETS})) + "\n")
    print(f"painted {2 * len(SETS)} worn armor layers at {W}x{H}")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
