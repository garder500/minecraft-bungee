#!/usr/bin/env python3
"""The five ladder helmets as sculpted 3D models worn on the head, and the Starcore wings.

    python3 resource-pack/art/helmet_models.py        (stdlib only)

A helmet item with no equipment asset is drawn on the head as its item model (like a carved pumpkin); the head fills
about 1.5-14.5 of the model space, so the helmet is grown to clear it (GROW). Each helmet is built from
several boxes instead of one cube: a rounded dome and cap, cheek guards, a face plate under a real visor slit (the
player's eyes show through it), a brow band, and the tier's ornaments (a bronze crest and nasal, a ring of rime
crystals, an ember sun disc with rays, void horns and blade, starcore feather crown). The textures are clean pixel art
drawn here from each tier's palette: plates with a light-to-dark sheen and rivets, gold trim, dark visor, gems.
In menus the helmet keeps its flat sprite. The Starcore chestplate also gets a "wings" equipment layer (the elytra
shape, no flight) in gold and white.
"""
import json
import pathlib
import struct
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"
TIERS = ("bronze", "rime", "ember", "void", "starcore")

def _h(hexes):
    return tuple(tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes.split())


def two_metals(pixels):
    """(plate pixels, trim pixels or None): a sprite painted in two metals (Starcore: white plates, gold trim) is split
    by saturation, the paler group is the plate. A sprite of one metal has no second group."""
    import colorsys
    sat = lambda c: colorsys.rgb_to_hsv(*(v / 255 for v in c))[1]
    ranked = sorted(pixels, key=sat)
    cut = max(range(len(ranked) // 5, len(ranked) * 4 // 5), key=lambda i: sat(ranked[i + 1]) - sat(ranked[i]) if i + 1 < len(ranked) else 0)
    pale, vivid = ranked[:cut + 1], ranked[cut + 1:]
    mean = lambda part: sum(sat(c) for c in part) / len(part)
    if mean(vivid) - mean(pale) < 0.3:
        return pixels, None
    lum = lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    return sorted(pale, key=lum), sorted(vivid, key=lum)


def sprite_palette(tier):
    """(shadow, base, light, highlight, glow) read from the helmet's imported sprite, so the helmet on the head is the
    metal of its inventory icon (same method as the worn armour, armor_layers.palette): four tones of the sprite from
    dark to bright without its ink outline, and for the glow the sprite's accent (the saturated pixels whose hue is
    furthest from the metal's) or, without one, a brightened highlight."""
    import colorsys
    import sprite_models
    _, _, px = sprite_models.read_png(PACK / "textures/item/armor" / f"{tier}_helmet.png")
    lum = lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    pixels = sorted((c[:3] for row in px for c in row if c[3] >= 128), key=lum)
    pixels, second = two_metals(pixels[len(pixels) // 6:])
    n = len(pixels)
    avg = lambda part: tuple(sum(c[k] for c in part) // len(part) for k in range(3))
    tones = [avg(pixels[int(n * a):int(n * b)]) for a, b in ((0.1, 0.35), (0.42, 0.68), (0.72, 0.9), (0.94, 1.0))]
    if second:  # the trims and ornaments of the helmet (the highlight tone) take the second metal
        tones[3] = avg(second[len(second) // 2:])
        # the pale metal is judged by its lit side, as armor_layers.palette does (its mid tones shade to putty in game)
        tones[:3] = [avg(pixels[int(n * a):int(n * b)]) for a, b in ((0.3, 0.55), (0.7, 0.92), (0.92, 1.0))]
    hsv = lambda c: colorsys.rgb_to_hsv(*(v / 255 for v in c))
    base = hsv(tones[1])[0]
    accents = [c for c in pixels if hsv(c)[1] > 0.35 and hsv(c)[2] > 0.45
               and min(abs(hsv(c)[0] - base), 1 - abs(hsv(c)[0] - base)) > 0.12]
    glow = avg(accents) if len(accents) >= 4 else tuple(int(v + (255 - v) * 0.5) for v in tones[3])
    return (*tones, glow)


REALM = {tier: sprite_palette(tier) for tier in TIERS}
OUTLINE = (20, 15, 24)
# glow pixels of the Realm motif on the plate: ashes crack, frost eye, sand clock, soul flame, singularity spark
MOTIF = {
    "bronze": ((12, 7), (12, 8), (11, 8)),
    "rime": ((7, 13), (8, 13), (7, 12), (8, 12)),
    "ember": ((11, 7), (11, 8), (12, 9), (12, 10), (13, 11)),
    "void": ((12, 7), (12, 8), (11, 9), (12, 9)),
    "starcore": ((12, 7), (11, 8), (13, 8), (12, 9)),
}
PALETTES = {t: (REALM[t][2], REALM[t][3], REALM[t][0], REALM[t][3], REALM[t][4]) for t in REALM}  # legacy order


def png(width, height, rows):
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def texture(kind, tier):
    """A 16x16 flat-shaded texture of one material of a helmet: palette steps only, light from the top left."""
    sh, base, lt, hi, glow = REALM[tier]
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if kind == "plate":  # three flat steps along the light, panel lines, rivets, the Realm motif
                v = x + y
                c = hi if v < 4 else lt if v < 13 else base if v < 25 else sh
                if y in (5, 11):
                    c = base if c in (hi, lt) else sh
                if (x, y) in ((2, 2), (13, 2), (2, 8), (13, 8), (2, 14), (13, 14)):
                    c = hi
                if (x, y) in MOTIF[tier]:
                    c = glow
            elif kind == "trim":  # a bright edge, a body step and a deep edge
                c = hi if y <= 2 else sh if y >= 14 else base if y >= 11 else lt
            elif kind == "dark":
                c = OUTLINE if (x + y) % 5 else sh
            else:  # accent: a faceted gem, outline ring, highlight top left, glow core
                d = abs(x - 7.5) + abs(y - 7.5)
                c = OUTLINE if d > 9 else sh if d > 7 else base if d > 4 else lt if d > 2 else glow
                if d <= 7 and (x, y) in ((5, 5), (6, 5), (5, 6)):
                    c = hi
            row.append(tuple(c) + (255,))
        rows.append(row)
    return png(16, 16, rows)


def uv(face, a, b):
    (x0, y0, z0), (x1, y1, z1) = a, b
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    if face in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def box(a, b, tex, rotation=None, sample=None):
    """A cuboid in one material; `sample` maps every face to that texture area (small ornaments)."""
    faces = {f: {"uv": sample or uv(f, a, b), "texture": "#" + tex} for f in ("north", "east", "south", "west", "up", "down")}
    el = {"from": list(a), "to": list(b), "faces": faces}
    if rotation:
        el["rotation"] = rotation
    return el


def rot(angle, axis, origin):
    return {"angle": angle, "axis": axis, "origin": origin}


def shell():
    """The helmet itself: dome, cap, back, cheeks, face plate, brow band. Front is north (z = 0)."""
    return [
        box((2, 9, 2), (14, 13.5, 14), "plate"),            # dome
        box((3.5, 13.5, 3.5), (12.5, 14.6, 12.5), "plate"),  # rounded cap
        box((2, 2, 7), (14, 9, 14), "plate"),                # back of the head and the sides behind the ears
        box((2, 2, 2), (4, 9, 7), "plate"),                  # cheek guards
        box((12, 2, 2), (14, 9, 7), "plate"),
        box((4, 2, 1.6), (12, 6.4, 2.2), "plate"),           # face plate under the visor slit (6.4 - 7.8 is open)
        box((1.8, 7.8, 1.6), (14.2, 9.2, 2.2), "plate"),     # brow band above the slit
    ]


def mirror(el):
    """The same piece on the other side of the head (x -> 16 - x, z-tilt reversed)."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    out = dict(el, **{"from": [16 - x1, y0, z0], "to": [16 - x0, y1, z1]})
    if "rotation" in el:
        r = el["rotation"]
        o = list(r["origin"])
        o[0] = 16 - o[0]
        out["rotation"] = dict(r, origin=o, angle=-r["angle"] if r["axis"] != "x" else r["angle"])
    return out


def ornaments(tier):
    g = [5, 5, 11, 11]  # small pieces sample the middle of their texture
    if tier == "bronze":
        # Citadel scarab crest: a gilded beetle on the crown, split wing cases, a teal sun-ball between its horns, legs
        out = [box((5.8, 13.5, 4.2), (10.2, 15.6, 10.4), "trim"),             # wing cases
               box((7.85, 15.5, 4.4), (8.15, 15.75, 10.2), "dark"),            # the split between the cases
               box((6.6, 13.5, 2.7), (9.4, 14.9, 4.2), "trim"),                # head
               box((6.6, 14.3, 1.5), (7.3, 15.4, 2.7), "trim"),                # the two clypeus horns
               box((8.7, 14.3, 1.5), (9.4, 15.4, 2.7), "trim"),
               box((7.2, 14.6, 1.2), (8.8, 16.2, 2.6), "accent", sample=g),    # the sun-ball it pushes
               box((7.4, 3, 1.1), (8.6, 7.9, 1.6), "trim")]                    # nasal
        for z in (5.0, 6.8, 8.6):                                              # three legs a side
            out.append(box((4.6, 13.4, z), (5.8, 14.2, z + 0.7), "trim", rot(22.5, "z", [5.8, 13.8, z])))
            out.append(box((10.2, 13.4, z), (11.4, 14.2, z + 0.7), "trim", rot(-22.5, "z", [10.2, 13.8, z])))
        return out
    if tier == "rime":
        # Frozen Heights: a ring of stasis crystals leaning out from the crown, and the violet eye on the brow
        out = []
        ring = ((8, 3.6, 3.6, "x", -22.5), (8, 12.4, 3.6, "x", 22.5), (3.6, 8, 3.6, "z", 22.5), (12.4, 8, 3.6, "z", -22.5),
                (4.7, 4.7, 2.6, None, 0), (11.3, 4.7, 2.6, None, 0), (4.7, 11.3, 2.6, None, 0), (11.3, 11.3, 2.6, None, 0))
        for cx, cz, h, axis, ang in ring:
            out.append(box((cx - 0.7, 13.0, cz - 0.7), (cx + 0.7, 13.0 + h, cz + 0.7), "accent",
                           rot(ang, axis, [cx, 13.0, cz]) if axis else None, sample=g))
        out.append(box((6.7, 9.4, 1.0), (9.3, 11.4, 1.6), "accent", sample=g))  # the violet eye
        out.append(box((7.3, 11.4, 1.2), (8.7, 12.6, 1.7), "accent", sample=g))
        return out
    if tier == "ember":
        # Cracked Ashes: a lava sun disc standing behind the crown, eight rays, a lava glint on the brow
        c = [8, 16.4, 7.8]
        out = [box((5.6, 15.0, 7.2), (10.4, 17.8, 8.4), "accent", sample=g),   # the disc (octagon from two boxes)
               box((6.6, 14.0, 7.2), (9.4, 18.8, 8.4), "accent", sample=g),
               box((6.6, 9.6, 1.0), (9.4, 12.2, 1.6), "accent", sample=g)]
        out.append(box((7.55, 12.6, 7.4), (8.45, 20.2, 8.2), "trim"))
        out.append(box((4.2, 15.95, 7.4), (11.8, 16.85, 8.2), "trim"))
        out.append(box((7.55, 12.6, 7.4), (8.45, 20.2, 8.2), "trim", rot(45, "z", c)))
        out.append(box((7.55, 12.6, 7.4), (8.45, 20.2, 8.2), "trim", rot(-45, "z", c)))
        return out
    if tier == "void":
        # Ethereal Necropolis: bone horns in three bending segments, a blade crest, a soul flame on the brow
        out = [box((7.4, 13.5, 2.5), (8.6, 17, 12), "dark")]
        side = [box((0.4, 9.0, 5.6), (2.2, 11.2, 7.8), "trim"),
                box((-0.2, 10.8, 5.9), (1.4, 13.4, 7.5), "trim", rot(22.5, "z", [0.6, 10.8, 6.7])),
                box((-1.4, 13.0, 6.2), (-0.1, 15.8, 7.2), "trim", rot(45, "z", [-0.75, 13.0, 6.7]))]
        out += side + [mirror(e) for e in side]
        out += [box((7.3, 9.6, 1.2), (8.7, 11.0, 1.7), "accent", sample=g),    # soul flame, a drop and a tip
                box((7.65, 11.0, 1.2), (8.35, 12.3, 1.5), "accent", sample=g),
                box((7.45, 17, 6.5), (8.55, 18.4, 7.6), "accent", sample=g)]   # and a spark on the blade
        return out
    # starcore, the Astral Interiority: a crown of feathers fanned behind the brow, the star on the brow
    out = [box((7, 10, 1.1), (9, 12, 1.7), "accent", sample=g)]
    for ang, h, tex in ((-45, 5.0, "plate"), (-22.5, 6.2, "trim"), (0, 7.4, "plate"), (22.5, 6.2, "trim"), (45, 5.0, "plate")):
        out.append(box((7.2, 13.0, 5.4), (8.8, 13.0 + h, 6.2), tex, rot(ang, "z", [8, 13.0, 5.8])))
    for ang, h, tex in ((-22.5, 5.4, "trim"), (0, 6.4, "trim"), (22.5, 5.4, "trim")):  # a back row between the first
        out.append(box((7.4, 13.0, 7.2), (8.6, 13.0 + h, 7.8), tex, rot(ang, "z", [8, 13.0, 7.5])))
    out.append(box((7.4, 18.6, 5.3), (8.6, 20.0, 6.3), "accent", sample=g))   # star at the tip of the tallest
    return out


# The head fills about 1.5-14.5 of the model space: the helmet is drawn for a 2-14 head and grown around the head's
# centre so its shell (2-14 -> 1-15) clears the skin on every side.
GROW = 7 / 6


def grown(el):
    g = lambda v: round(8 + (v - 8) * GROW, 3)
    el = dict(el, **{"from": [g(v) for v in el["from"]], "to": [g(v) for v in el["to"]]})
    if "rotation" in el:
        el["rotation"] = dict(el["rotation"], origin=[g(v) for v in el["rotation"]["origin"]])
    return el


def helmet(tier):
    tex = {k: f"vampyuyu:item/armor/helm/{tier}_{k}" for k in ("plate", "trim", "dark", "accent")}
    tex["particle"] = tex["plate"]
    return {"textures": tex, "elements": [grown(el) for el in shell() + ornaments(tier)],
            "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
                        "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3}}}


def wings():
    """Elytra-layout texture (64x32, the wing box at u=22 v=0, 10x20x2): rows of white feathers with gold tips."""
    rows = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    white, cream, gold, deep = REALM["starcore"][3], REALM["starcore"][2], REALM["starcore"][4], REALM["starcore"][1]
    for y in range(0, 22):
        for x in range(22, 46):
            band = (y // 3) % 2
            feather = (x - 22 + (y // 3)) % 4
            base = white if band == 0 else cream
            if y >= 16 or feather == 3:
                base = gold if y < 20 else deep
            if feather == 0 and y % 3 == 2:
                base = deep
            cut = 22 - (4 if ((x - 22) // 2) % 2 else 0) - (2 if ((x - 22) // 2) % 3 == 0 else 0)
            rows[y][x] = base + (255,) if y < cut else (0, 0, 0, 0)
    return png(64, 32, rows)


def main():
    helm = PACK / "textures/item/armor/helm"
    helm.mkdir(parents=True, exist_ok=True)
    for tier in TIERS:
        for kind in ("plate", "trim", "dark", "accent"):
            (helm / f"{tier}_{kind}.png").write_bytes(texture(kind, tier))
        name = f"{tier}_helmet"
        (PACK / "models/item/armor" / f"{name}_3d.json").write_text(json.dumps(helmet(tier), indent=1) + "\n")
        definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                "cases": [{"when": "gui", "model": {"type": "minecraft:model",
                                                                    "model": f"vampyuyu:item/armor/{name}"}}],
                                "fallback": {"type": "minecraft:model", "model": f"vampyuyu:item/armor/{name}_3d"}}}
        (PACK / "items/armor" / f"{name}.json").write_text(json.dumps(definition, indent=1) + "\n")
    for old in helm.glob("*_front.png"):  # the textures of the first (cube) helmets
        for kind in ("front", "side", "top", "back", "ornament"):
            stale = helm / old.name.replace("_front", f"_{kind}")
            if stale.exists():
                stale.unlink()
    # the old starcore "wings" equipment layer is gone: the real wings are the separate Wings item
    stale = PACK / "textures/entity/equipment/wings/starcore_wings.png"
    if stale.exists():
        stale.unlink()
    asset = PACK / "equipment/starcore.json"
    data = json.loads(asset.read_text())
    data["layers"].pop("wings", None)
    asset.write_text(json.dumps(data, indent=1) + "\n")
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"armor/{t}_helmet_3d" for t in TIERS})) + "\n")
    print("wrote 5 sculpted helmet models")


if __name__ == "__main__":
    main()
