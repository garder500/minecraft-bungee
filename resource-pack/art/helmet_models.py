#!/usr/bin/env python3
"""The five ladder helmets as sculpted 3D models worn on the head, and the Starcore wings.

    python3 resource-pack/art/helmet_models.py        (stdlib only)

A helmet item with no equipment asset is drawn on the head as its item model (like a carved pumpkin); the head fills
about 1.5-14.5 of the model space, so the helmet is grown to clear it (GROW). Each helmet is built from
several boxes instead of one cube: a rounded dome and cap, cheek guards, a face plate under a real visor slit (the
player's eyes show through it), a brow band, and the tier's ornaments (a bronze crest and nasal, a ring of rime
crystals, an ember sun disc with rays, void horns and blade, starcore feather wings). The textures are clean pixel art
drawn here from each tier's palette: plates with a light-to-dark sheen and rivets, gold trim, dark visor, gems.
In menus the helmet keeps its flat sprite. The Starcore chestplate also gets a "wings" equipment layer (the elytra
shape, no flight) in gold and white.
"""
import json
import pathlib
import random
import struct
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"
TIERS = ("bronze", "rime", "ember", "void", "starcore")

PALETTES = {  # plate, light, dark, trim, accent (gem / glow / crystal)
    "bronze": ((178, 112, 48), (232, 170, 92), (104, 60, 26), (236, 186, 64), (255, 222, 128)),
    "rime": ((150, 196, 232), (226, 244, 255), (64, 104, 156), (236, 246, 255), (86, 156, 255)),
    "ember": ((228, 146, 38), (255, 214, 110), (146, 66, 18), (255, 204, 84), (255, 116, 32)),
    "void": ((62, 32, 104), (138, 84, 208), (24, 12, 44), (196, 136, 255), (232, 160, 255)),
    "starcore": ((238, 232, 216), (255, 255, 250), (176, 156, 116), (240, 192, 72), (120, 204, 255)),
}


def png(width, height, rows):
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def texture(kind, tier):
    """A 16x16 texture of one material of a helmet."""
    plate, light, dark, trim, accent = PALETTES[tier]
    rnd = random.Random(zlib.crc32(f"{tier}:{kind}".encode()))
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            t = y / 15
            if kind == "plate":  # a sheen from the top-left, panel lines, rivets
                c = mix(light, dark, min(1, t * 0.9 + x / 60))
                if y in (5, 11):
                    c = mix(c, dark, 0.6)
                if (x, y) in ((2, 2), (13, 2), (2, 8), (13, 8), (2, 14), (13, 14)):
                    c = trim
                c = mix(c, (255, 255, 255), rnd.random() * 0.06)
            elif kind == "trim":  # polished gold (or silver): a bright line and a deep edge
                c = mix(mix(trim, (255, 250, 220), 0.5), mix(trim, dark, 0.45), t)
                if y == 2:
                    c = (255, 250, 225)
                if y >= 14:
                    c = mix(trim, dark, 0.6)
            elif kind == "dark":
                c = mix(dark, (8, 6, 12), 0.6)
            else:  # accent: a gem or crystal with a facet highlight
                d = abs(x - 7.5) + abs(y - 7.5)
                c = mix(mix(accent, (255, 255, 255), 0.55), mix(accent, dark, 0.35), min(1, d / 11))
                if (x - y) in (0, 1) and x < 8:
                    c = mix(c, (255, 255, 255), 0.6)
            row.append(tuple(max(0, min(255, v)) for v in c) + (255,))
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


def ornaments(tier):
    g = [5, 5, 11, 11]  # small pieces sample the middle of their texture
    if tier == "bronze":
        return [box((7.3, 13.5, 3), (8.7, 16.5, 13.5), "trim"),                # crest
                box((7.4, 3, 1.1), (8.6, 7.9, 1.6), "trim"),                   # nasal
                box((7.6, 15.6, 2.6), (8.4, 16.4, 3.4), "accent", sample=g)]
    if tier == "rime":
        out = []
        for x, h, z in ((3, 2.5, 3), (5.2, 3.8, 2.4), (7.25, 5, 2), (9.3, 3.8, 2.4), (11.5, 2.5, 3)):
            out.append(box((x, 12.5, z), (x + 1.5, 12.5 + h, z + 1.5), "accent", sample=g))
        out.append(box((7.3, 7.9, 1.2), (8.7, 9.3, 1.6), "accent", sample=g))  # brow gem
        return out
    if tier == "ember":
        out = [box((6.5, 9.6, 1.0), (9.5, 12.6, 1.6), "accent", sample=g)]     # the sun disc on the brow
        for angle, x in ((-22.5, 5.2), (0, 7.4), (22.5, 9.6)):
            out.append(box((x, 13, 1.8), (x + 1.2, 16.6, 3.0), "trim", rot(angle, "z", [x + 0.6, 13, 2.4])))
        for angle, x in ((-45, 3.2), (45, 11.6)):
            out.append(box((x, 12, 2.2), (x + 1.2, 14.8, 3.4), "trim", rot(angle, "z", [x + 0.6, 12, 2.8])))
        return out
    if tier == "void":
        return [box((7.4, 13.5, 2.5), (8.6, 17, 12), "dark"),                   # blade crest
                box((0.5, 10, 6), (2.2, 11.6, 7.6), "accent", rot(22.5, "z", [2, 10, 7])),    # horns
                box((-0.8, 11.2, 6.2), (0.8, 13.6, 7.4), "accent", rot(22.5, "z", [2, 10, 7])),
                box((13.8, 10, 6), (15.5, 11.6, 7.6), "accent", rot(-22.5, "z", [14, 10, 7])),
                box((15.2, 11.2, 6.2), (16.8, 13.6, 7.4), "accent", rot(-22.5, "z", [14, 10, 7])),
                box((7.3, 10, 1.2), (8.7, 11.4, 1.7), "accent", sample=g)]
    # starcore: two feathers each side, swept back, and the star on the brow
    out = [box((7, 10, 1.1), (9, 12, 1.7), "accent", sample=g)]
    for side, x in ((-1, 1.4), (1, 14.1)):
        out.append(box((x, 8.5, 6), (x + 0.5, 15.5, 13), "plate", rot(22.5 * side, "y", [x, 8.5, 6])))
        out.append(box((x - 0.5 * side, 10.5, 8), (x + 0.5 - 0.5 * side, 17, 14.5), "trim", rot(22.5 * side, "y", [x, 8.5, 6])))
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
    white, cream, gold, deep = (250, 246, 232), (232, 220, 190), (240, 196, 80), (176, 128, 40)
    for y in range(0, 22):
        for x in range(22, 46):
            band = (y // 3) % 2
            feather = (x - 22 + (y // 3)) % 4
            base = white if band == 0 else cream
            if y >= 16 or feather == 3:
                base = gold if y < 20 else deep
            if feather == 0 and y % 3 == 2:
                base = deep
            rows[y][x] = base + (255,)
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
    tex = PACK / "textures/entity/equipment/wings/starcore_wings.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    tex.write_bytes(wings())
    asset = PACK / "equipment/starcore.json"
    data = json.loads(asset.read_text())
    data["layers"]["wings"] = [{"texture": "vampyuyu:starcore_wings"}]
    asset.write_text(json.dumps(data, indent=1) + "\n")
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"armor/{t}_helmet_3d" for t in TIERS})) + "\n")
    print("wrote 5 sculpted helmet models and the starcore wings")


if __name__ == "__main__":
    main()
