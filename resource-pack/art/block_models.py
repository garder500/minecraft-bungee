#!/usr/bin/env python3
"""The machines, the Forge and the cables: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/block_models.py

The source of each block is a Blockbench project, resource-pack/art/blockbench/blocks/<name>.bbmodel (format "Java
Block/Item", one embedded palette texture painted in Blockbench, shade: false everywhere; edit them there, then run this
script). The front is the south face (z = 16), as the plugin turns the display to face the player who placed it
(BlockModels.java puts the model on an item display over the vanilla block, which stays the hitbox). The glowing parts
(fire, water, Flux windows, solar cells) carry light_emission from Blockbench, so they stay bright at night.

  models/item/<family>/<name>_3d.json     the block (machine/<name>, forge/forge)
  textures/item/<family>/<name>_3d.png    the Blockbench texture, byte for byte
  items/<family>/<name>.json              gui -> the flat sprite (when there is one), anything else -> the block

The cables come from one source, cable.bbmodel: group "node" (the junction on the redstone dust), "arm_n/e/s/w" (one
arm per connected side: N=1 E=2 S=4 W=8), "stub_n/e/s/w" (the short stubs of a lone cable, mask 0) and "item" (the
cable in the hand). It has two textures on the same layout: "cable" (copper) and "cable_on" (lit, while it carries Flux):
machine/cable_<mask>_3d.json and its twin machine/cable_<mask>_on_3d.json, the same for the item (machine/cable_3d).
Every id written here is listed in imported.txt, so import_textures.py leaves it alone.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"
SOURCES = bbmodel.ROOT / "blocks"
MACHINES = {"fountain": "machine", "spring_stone": "machine", "coal_generator": "machine", "battery": "machine",
            "solar_panel": "machine", "miner_1": "machine", "miner_2": "machine", "forge": "forge"}
BLOCK_DISPLAY = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
}
SIDES = {1: "n", 2: "e", 4: "s", 8: "w"}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def model(texture, elements):
    return {"textures": {"0": texture, "particle": texture}, "gui_light": "front", "display": BLOCK_DISPLAY,
            "elements": elements}


def cable_groups(mask):
    """The groups of cable.bbmodel shown for a connection mask (None: the cable item)."""
    if mask is None:
        return ["item"]
    if mask == 0:
        return ["node"] + [f"stub_{d}" for d in "nesw"]
    return ["node"] + [f"arm_{SIDES[bit]}" for bit in (1, 2, 4, 8) if mask & bit]


def cable_states(src):
    """(label, elements) of every cable model, for the checks."""
    return [(f"cable_{m}", src.java(only=cable_groups(m))) for m in range(16)] + [("cable", src.java(only=cable_groups(None)))]


def write_machine(name, family):
    src = bbmodel.Source(SOURCES / f"{name}.bbmodel")
    tex = f"vampyuyu:item/{family}/{name}_3d"
    (PACK / "textures/item" / family / f"{name}_3d.png").write_bytes(src.png)
    dump(PACK / "models/item" / family / f"{name}_3d.json", model(tex, src.java()))
    three = {"type": "minecraft:model", "model": f"vampyuyu:item/{family}/{name}_3d"}
    if (PACK / "models/item" / family / f"{name}.json").exists():   # its flat sprite in the inventory
        three = {"type": "minecraft:select", "property": "minecraft:display_context",
                 "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": f"vampyuyu:item/{family}/{name}"}}],
                 "fallback": three}
    dump(PACK / "items" / family / f"{name}.json", {"model": three})
    return f"{family}/{name}_3d"


def write_cables():
    src = bbmodel.Source(SOURCES / "cable.bbmodel")
    out = PACK / "textures/item/machine"
    (out / "cable_3d.png").write_bytes(src.pngs["cable"])
    (out / "cable_on_3d.png").write_bytes(src.pngs["cable_on"])
    written = []
    for mask in [None] + list(range(16)):
        name = "cable" if mask is None else f"cable_{mask}"
        elements = src.java(only=cable_groups(mask))
        for suffix, tex in (("", "cable_3d"), ("_on", "cable_on_3d")):
            dump(PACK / "models/item/machine" / f"{name}{suffix}_3d.json", model(f"vampyuyu:item/machine/{tex}", elements))
            written.append(f"machine/{name}{suffix}_3d")
        for suffix in ("", "_on"):
            dump(PACK / "items/machine" / f"{name}{suffix}.json",
                 {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/machine/{name}{suffix}_3d"}})
    return written


def main():
    written = [write_machine(name, family) for name, family in MACHINES.items()]
    written += write_cables()
    for stale in (PACK / "textures/block/machine").glob("window_*.png"):   # the glowing panes of the old models
        stale.unlink()
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | set(written))) + "\n")
    print(f"wrote {len(MACHINES)} machines and {len(written) - len(MACHINES)} cable models from {SOURCES.relative_to(ART)}/")


if __name__ == "__main__":
    main()
