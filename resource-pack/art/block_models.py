#!/usr/bin/env python3
"""The machines, the Forge and the cables as textured block models (replaces the flat-colour box models).

    python3 resource-pack/art/block_models.py        (stdlib only; run import_faces.py first)

Each machine is one or two cuboids whose faces use the 32x32 textures import_faces.py cut from the concept sheet
(textures/block/machine/<machine>_<face>.png). The front is the south face (z = 16), as the plugin turns the display
to face the player who placed it. The display transforms of the old models are kept. The cables keep their geometry
(one model per connection mask, item_models.py) and only swap their flat colours for the copper texture. Every id
written here is listed in imported.txt, so item_models.py no longer overwrites it.
"""
import json
import pathlib
import struct
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"
TEX = "vampyuyu:block/machine/"

# machine -> (model id, [(from, to)], faces: top / side / front / bottom texture names)
MACHINES = {
    "fountain": ("machine/fountain", [((0, 0, 0), (16, 12, 16))], "fountain_top", "fountain_side", "fountain_side"),
    "spring_stone": ("machine/spring_stone", [((0, 0, 0), (16, 16, 16))], "spring_stone_top", "spring_stone_side",
                     "spring_stone_side"),
    "coal_generator": ("machine/coal_generator", [((0, 0, 0), (16, 14, 16))], "coal_generator_top",
                       "coal_generator_side", "coal_generator_front"),
    "battery": ("machine/battery", [((2, 0, 2), (14, 16, 14))], "battery_top", "battery_side", "battery_side"),
    "solar_panel": ("machine/solar_panel", [((0, 0, 0), (16, 4, 16)), ((1, 4, 1), (15, 6, 15))], "solar_panel_top",
                    "solar_panel_side", "solar_panel_side"),
    "miner_1": ("machine/miner_1", [((0, 0, 0), (16, 16, 16))], "miner_1_top", "miner_1_side", "miner_1_front"),
    "miner_2": ("machine/miner_2", [((0, 0, 0), (16, 16, 16))], "miner_2_top", "miner_2_side", "miner_2_front"),
    "forge": ("forge/forge", [((0, 0, 0), (16, 16, 16))], "forge_top", "forge_side", "forge_front"),
}


# The Flux window: the 8x8 bright motif of the flat machine sprites (lore_sprites.py), drawn 2x2 pixels per cell on a
# 16x16 texture. Each motif is (rows, palette); "_" is the lit background (the glow colour, dark). Realm -> motif:
# water wheel (Fountain, Spring Stone), heat grate (Coal Generator, Forge), void spiral (Miner II), star lattice
# (Solar Panel), the cyan Flux bolt for the others.
_GOLD, _CYAN = "#f2c14e", "#4af0ff"
MOTIFS = {
    "wheel": (["e__dd__e", "_e_dd_e_", "__edde__", "dddeeddd", "dddeeddd", "__edde__", "_e_dd_e_", "e__dd__e"],
              {"e": "#3ad0e8", "d": _GOLD}, "#2a8a9a"),
    "grate": (["________", "_eeeeee_", "________", "_efeefe_", "________", "_effffe_", "________", "_eeeeee_"],
              {"e": "#ff8a2a", "f": "#fff0a0"}, "#ff6a1a"),
    "void": (["__eeee__", "_e____e_", "e__ee__e", "e_e__e_e", "e_e_fe_e", "e__e___e", "_e____e_", "__eeee__"],
             {"e": "#b07aff", "f": "#ff6aa8"}, "#8a4aff"),
    "lattice": (["e___e___", "_e_e_e_e", "__e___e_", "_e_fff_e", "e__fff__", "_e_e_e_e", "__e___e_", "_e_e_e_e"],
                {"e": "#e8d8ff", "f": "#ffffff"}, "#e0c8ff"),
    "flux": (["_____ee_", "____ee__", "___ee___", "__eeeee_", "____ee__", "___ee___", "__ee____", "_ee_____"],
             {"e": _CYAN, "f": "#e8ffff"}, "#4af0ff"),
}
# machine -> (motif, window x0, y0, x1, y1, z of the face it sits on)
WINDOWS = {
    "fountain": ("wheel", 4, 3, 12, 11, 16),
    "spring_stone": ("wheel", 4, 4, 12, 12, 16),
    "coal_generator": ("grate", 4, 3, 12, 11, 16),
    "battery": ("flux", 4, 4, 12, 12, 14),
    "solar_panel": ("lattice", 4, 0.5, 12, 3.5, 16),
    "miner_1": ("flux", 4, 4, 12, 12, 16),
    "miner_2": ("void", 4, 4, 12, 12, 16),
    "forge": ("grate", 4, 4, 12, 12, 16),
}


def _hex(c):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5))


def _png(path, pixels):
    """16x16 RGBA PNG from rows of (r, g, b) tuples, stdlib only and deterministic."""
    raw = b"".join(b"\x00" + b"".join(bytes(px) + b"\xff" for px in row) for row in pixels)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def window_textures():
    """window_<motif>.png (bright, flat, no shading baked in) and window_frame.png (the bronze rim)."""
    for name, (rows, pal, glow) in MOTIFS.items():
        g = _hex(glow)
        dark = (0x1a, 0x16, 0x22)
        bg = tuple(int(d * 0.55 + c * 0.30) for d, c in zip(dark, g))
        pix = [[bg] * 16 for _ in range(16)]
        for r, line in enumerate(rows):
            for c, ch in enumerate(line):
                if ch in pal:
                    for dy in (0, 1):
                        for dx in (0, 1):
                            pix[r * 2 + dy][c * 2 + dx] = _hex(pal[ch])
        for i in range(16):  # lit inner edge, so the pane reads as light and not as a hole
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                pix[y][x] = tuple((v + a) // 2 for v, a in zip(bg, g))
        _png(PACK / "textures/block/machine" / f"window_{name}.png", pix)
    a, b, c = _hex("#4a2a12"), _hex("#7a4a1e"), _hex("#c98442")
    _png(PACK / "textures/block/machine/window_frame.png",
         [[a if x in (0, 15) or y in (0, 15) else (c if x == 1 or y == 1 else b) for x in range(16)] for y in range(16)])


def window_elements(name):
    """A bronze rim and, in it, the glowing pane. Only the pane's front face is full-bright (light_emission)."""
    motif, x0, y0, x1, y1, z = WINDOWS[name]
    rim = {f: {"uv": [0, 0, 16, 16], "texture": "#frame"} for f in ("north", "east", "south", "west", "up", "down")}
    rim_el = {"from": [x0 - 1, max(y0 - 1, 0), z - 1], "to": [x1 + 1, y1 + 1, z + 0.1], "faces": rim}
    pane = {"south": {"uv": [0, 0, 16, 16], "texture": "#window"}}
    pane_el = {"from": [x0, y0, z - 1], "to": [x1, y1, z + 0.25], "shade": False, "light_emission": 15, "faces": pane}
    return [rim_el, pane_el], TEX + "window_" + motif


def uv(face, a, b):
    """The face's own area in a 16x16 texture square, so a smaller box shows a slice instead of a squeezed image."""
    (x0, y0, z0), (x1, y1, z1) = a, b
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    if face in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def machine_model(name, old, boxes, top, side, front):
    elements = []
    for a, b in boxes:
        faces = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            key = {"up": "top", "down": "bottom", "south": "front"}.get(face, "side")
            faces[face] = {"uv": uv(face, a, b), "texture": "#" + key}
        elements.append({"from": list(a), "to": list(b), "faces": faces})
    extra, window = window_elements(name)
    elements += extra
    return {"textures": {"top": TEX + top, "side": TEX + side, "front": TEX + front, "bottom": TEX + "bottom",
                         "particle": TEX + side, "frame": TEX + "window_frame", "window": window},
            "gui_light": old.get("gui_light", "side"), "display": old["display"], "elements": elements}


def retexture_cable(path):
    """Copper texture on the cable's shape, plus its lit twin (<name>_on, cable_on.png) shown while it carries Flux."""
    model = json.loads(path.read_text())
    model["textures"] = {"0": TEX + "cable", "particle": TEX + "cable"}
    for el in model["elements"]:
        for face, spec in el["faces"].items():
            spec["uv"] = uv(face, el["from"], el["to"])
    path.write_text(json.dumps(model, indent=1) + "\n")
    name = path.stem[:-len("_3d")]  # cable_5
    lit = dict(model, textures={"0": TEX + "cable_on", "particle": TEX + "cable_on"})
    (path.parent / f"{name}_on_3d.json").write_text(json.dumps(lit, indent=1) + "\n")
    item = PACK / "items/machine" / f"{name}_on.json"
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/machine/{name}_on_3d"}},
                               indent=1) + "\n")


def main():
    written = []
    window_textures()
    for name, (model_id, boxes, top, side, front) in MACHINES.items():
        family, ident = model_id.split("/")
        path = PACK / "models/item" / family / f"{ident}_3d.json"
        old = json.loads(path.read_text())
        path.write_text(json.dumps(machine_model(name, old, boxes, top, side, front), indent=1) + "\n")
        written.append(f"{model_id}_3d")
        flat = {"type": "minecraft:model", "model": f"vampyuyu:item/{model_id}"}
        flat_model = PACK / "models/item" / family / f"{ident}.json"
        if not flat_model.exists():
            continue
        definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                "cases": [{"when": "gui", "model": flat}],
                                "fallback": {"type": "minecraft:model", "model": f"vampyuyu:item/{model_id}_3d"}}}
        (PACK / "items" / family / f"{ident}.json").write_text(json.dumps(definition, indent=1) + "\n")
    cables = sorted(p for p in (PACK / "models/item/machine").glob("cable*_3d.json") if "_on_" not in p.name)
    for path in cables:
        retexture_cable(path)
        written.append(f"machine/{path.stem}")
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | set(written))) + "\n")
    print(f"wrote {len(MACHINES)} machine models, retextured {len(cables)} cable models")


if __name__ == "__main__":
    main()
