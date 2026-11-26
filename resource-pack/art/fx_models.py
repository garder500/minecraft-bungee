#!/usr/bin/env python3
"""Boss VFX primitives: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/fx_models.py

Every ability of every Skyblock boss is drawn with these models, never with vanilla particles (BossVfx.java). The source of
each primitive is a Blockbench project, resource-pack/art/blockbench/fx/<name>.bbmodel (format "Java Block/Item", one embedded
texture painted in near-white greys, every face tint 0, every cube light_emission 15). For each one this writes
  pack/assets/vampyuyu/models/item/fx/<name>.json     (the boxes, faces with tintindex 0)
  pack/assets/vampyuyu/textures/item/fx/<name>.png    (the Blockbench texture, every texel marked alpha 251: it all glows)
  pack/assets/vampyuyu/items/fx/<name>.json           (item model vampyuyu:fx/<name>, tinted by custom_model_data colour 0)
and, for the big effects (HALOS), a <name>_halo twin with a translucent texture: the halo shell BossVfx draws round them.
The plugin spawns them on item displays at full brightness and gives each stack its colour (the element of the ability:
magenta void, ice blue, ember orange...), so one white model serves every boss. Edit the shapes in Blockbench, never here.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SOURCES = bbmodel.ROOT / "fx"


# primitives that also get a halo shell (<name>_halo: same boxes, a translucent texture): the big effects
HALOS = ("beam", "bolt", "pillar", "orb", "burst", "spike", "shard", "vortex", "wave", "slash")


def write(src):
    tex = f"vampyuyu:item/fx/{src.name}"
    model = {"textures": {"0": tex, "particle": tex}, "elements": src.java()}
    item = {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/fx/{src.name}",
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}}
    for path, data in ((PACK / "models" / "item" / "fx" / f"{src.name}.json", model),
                       (PACK / "items" / "fx" / f"{src.name}.json", item)):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    out = PACK / "textures" / "item" / "fx" / f"{src.name}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(bbmodel.glow_png(src, everything=True))   # the whole primitive glows (item shader, alpha 251)
    if src.name in HALOS:   # the halo shell: same boxes, translucent texture (alpha 96), scaled up by BossVfx
        name = f"{src.name}_halo"
        htex = f"vampyuyu:item/fx/{name}"
        for path, data in ((PACK / "models" / "item" / "fx" / f"{name}.json",
                            {"textures": {"0": htex, "particle": htex}, "elements": src.java()}),
                           (PACK / "items" / "fx" / f"{name}.json",
                            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/fx/{name}",
                                       "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}})):
            path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
        (PACK / "textures" / "item" / "fx" / f"{name}.png").write_bytes(bbmodel.halo_png(src))


def main():
    sources = sorted(SOURCES.glob("*.bbmodel")) if SOURCES.exists() else []
    for path in sources:
        write(bbmodel.Source(path))
    print(f"wrote {len(sources)} fx primitives from {SOURCES.name}/")


if __name__ == "__main__":
    main()
