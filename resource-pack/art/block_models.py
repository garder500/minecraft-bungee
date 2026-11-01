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


def uv(face, a, b):
    """The face's own area in a 16x16 texture square, so a smaller box shows a slice instead of a squeezed image."""
    (x0, y0, z0), (x1, y1, z1) = a, b
    if face in ("up", "down"):
        return [x0, z0, x1, z1]
    if face in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    return [z0, 16 - y1, z1, 16 - y0]


def machine_model(old, boxes, top, side, front):
    elements = []
    for a, b in boxes:
        faces = {}
        for face in ("north", "east", "south", "west", "up", "down"):
            key = {"up": "top", "down": "bottom", "south": "front"}.get(face, "side")
            faces[face] = {"uv": uv(face, a, b), "texture": "#" + key}
        elements.append({"from": list(a), "to": list(b), "faces": faces})
    return {"textures": {"top": TEX + top, "side": TEX + side, "front": TEX + front, "bottom": TEX + "bottom",
                         "particle": TEX + side},
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
    for name, (model_id, boxes, top, side, front) in MACHINES.items():
        family, ident = model_id.split("/")
        path = PACK / "models/item" / family / f"{ident}_3d.json"
        old = json.loads(path.read_text())
        path.write_text(json.dumps(machine_model(old, boxes, top, side, front), indent=1) + "\n")
        written.append(f"{model_id}_3d")
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
