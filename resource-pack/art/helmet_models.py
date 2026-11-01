#!/usr/bin/env python3
"""The five ladder helmets as 3D models worn on the head, and the Starcore wings.

    python3 resource-pack/art/helmet_models.py        (stdlib only; run import_faces.py first for the helm textures)

A helmet item with no equipment asset is drawn on the head as its item model (like a carved pumpkin), so each helmet
is a shell around the head (13 units: a little larger than the 8 px head, as the head context scales by 0.625) with the
tier's ornaments: a bronze crest, rime crystal spikes, an ember sun crown, a void crest and horns, starcore wings.
The faces use the 16x16 textures import_faces.py cut from concepts/helmet_faces.png (item/armor/helm/<tier>_<face>).
In menus the helmet keeps its flat sprite. The Starcore chestplate also gets a "wings" equipment layer (the elytra
shape, no flight) painted here in its gold and white.
"""
import json
import pathlib
import struct
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"

LO, HI = 1.5, 14.5  # the shell around the head, in model units
FRONT = "north"


def face_uv(face, a, b):
    (x0, y0, z0), (x1, y1, z1) = a, b
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    if face in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def cuboid(a, b, tex, front=None, rotation=None):
    """A box; `tex` names the texture of every face, `front` (optional) the north face's."""
    faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        key = front if (face == FRONT and front) else tex
        # an ornament samples the middle of its texture (the edges of a drawn crystal or feather are background)
        uv = [5, 5, 11, 11] if key == "ornament" else face_uv(face, a, b)
        faces[face] = {"uv": uv, "texture": "#" + key}
    el = {"from": list(a), "to": list(b), "faces": faces}
    if rotation:
        el["rotation"] = rotation
    return el


def shell():
    return [{"from": [LO, LO, LO], "to": [HI, HI, HI], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#front"}, "south": {"uv": [0, 0, 16, 16], "texture": "#back"},
        "east": {"uv": [0, 0, 16, 16], "texture": "#side"}, "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#back"}}}]


def ornaments(tier):
    o = "ornament"
    if tier == "bronze":  # a crest from the brow to the nape, and cheek guards
        return [cuboid((7, HI, 2), (9, HI + 2.5, 14), o), cuboid((LO - 0.5, 3, 1), (LO, 9, 6), o),
                cuboid((HI, 3, 1), (HI + 0.5, 9, 6), o)]
    if tier == "rime":  # a ring of ice spikes, the middle ones tallest
        out = []
        for x, h in ((2.5, 3), (5, 5), (7.25, 7), (9.5, 5), (12, 3)):
            out.append(cuboid((x, HI, 2), (x + 1.5, HI + h, 3.5), o))
        out.append(cuboid((2, HI, 7), (3.5, HI + 3, 8.5), o))
        out.append(cuboid((12.5, HI, 7), (14, HI + 3, 8.5), o))
        return out
    if tier == "ember":  # a sun crown: rays fanned around the brow, a gem in front
        out = [cuboid((6.5, 9, LO - 0.6), (9.5, 12, LO), o)]
        for x, h, tilt in ((3, 3, -22.5), (5.5, 4, 0), (7.25, 5, 0), (9, 4, 0), (11.5, 3, 22.5)):
            out.append(cuboid((x, HI, 1.2), (x + 1.5, HI + h, 2.7), o,
                              rotation={"angle": tilt, "axis": "z", "origin": [x + 0.75, HI, 2]} if tilt else None))
        return out
    if tier == "void":  # a tall blade crest and two swept horns
        return [cuboid((7, HI, 3), (9, HI + 4, 12), o),
                cuboid((LO - 3, 11, 6), (LO, 12.5, 7.5), o, rotation={"angle": 22.5, "axis": "z", "origin": [LO, 11, 7]}),
                cuboid((HI, 11, 6), (HI + 3, 12.5, 7.5), o, rotation={"angle": -22.5, "axis": "z", "origin": [HI, 11, 7]})]
    # starcore: a wing on each side, swept back, and a star on the brow
    return [cuboid((LO - 0.5, 8, 5), (LO, 15, 14), o, rotation={"angle": -22.5, "axis": "y", "origin": [LO, 8, 5]}),
            cuboid((LO - 1, 10, 7), (LO - 0.5, 17, 15), o, rotation={"angle": -22.5, "axis": "y", "origin": [LO, 8, 5]}),
            cuboid((HI, 8, 5), (HI + 0.5, 15, 14), o, rotation={"angle": 22.5, "axis": "y", "origin": [HI, 8, 5]}),
            cuboid((HI + 0.5, 10, 7), (HI + 1, 17, 15), o, rotation={"angle": 22.5, "axis": "y", "origin": [HI, 8, 5]}),
            cuboid((7, 11, LO - 0.6), (9, 13, LO), o)]


def helmet(tier):
    tex = {k: f"vampyuyu:item/armor/helm/{tier}_{k}" for k in ("front", "side", "top", "back", "ornament")}
    tex["particle"] = tex["side"]
    return {"textures": tex, "elements": shell() + ornaments(tier),
            "display": {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
                        "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3}}}


def png(width, height, rows):
    raw = b"".join(b"\x00" + bytes(v for px in row for v in px) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


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
    for tier in ("bronze", "rime", "ember", "void", "starcore"):
        name = f"{tier}_helmet"
        (PACK / "models/item/armor" / f"{name}_3d.json").write_text(json.dumps(helmet(tier), indent=1) + "\n")
        definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                "cases": [{"when": "gui", "model": {"type": "minecraft:model",
                                                                    "model": f"vampyuyu:item/armor/{name}"}}],
                                "fallback": {"type": "minecraft:model", "model": f"vampyuyu:item/armor/{name}_3d"}}}
        (PACK / "items/armor" / f"{name}.json").write_text(json.dumps(definition, indent=1) + "\n")
    tex = PACK / "textures/entity/equipment/wings/starcore_wings.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    tex.write_bytes(wings())
    asset = PACK / "equipment/starcore.json"
    data = json.loads(asset.read_text())
    data["layers"]["wings"] = [{"texture": "vampyuyu:starcore_wings"}]
    asset.write_text(json.dumps(data, indent=1) + "\n")
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"armor/{t}_helmet_3d" for t in ("bronze", "rime", "ember", "void", "starcore")}
                                       | {"equipment/starcore_asset"})) + "\n")
    print("wrote 5 helmet models and the starcore wings")


if __name__ == "__main__":
    main()
