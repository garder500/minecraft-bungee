#!/usr/bin/env python3
"""Werewolf sky platform decor: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/werewolf_models.py

The parts of the platform's village that blocks cannot make (the red moon, the mill sails, the belfry bell and its wolf
weathervane) are item displays the arena plugin spawns with the platform (DecorConfig.Model, sky/DecorModels.java),
centred on the display: the source is centred on pixel (8, 8, 8). Each source is a Blockbench project,
resource-pack/art/blockbench/werewolf/<name>.bbmodel (format "Java Block/Item", one palette texture painted in
Blockbench, shade false); edit it there, then run this script.

  SPLIT   a model per top-level group, so the plugin can turn one part on its own: werewolf/<name>_<group>
  else    one model, werewolf/<name>

  models/item/werewolf/<id>.json, textures/item/werewolf/<name>.png, items/werewolf/<id>.json
Every id is listed in imported.txt, so import_textures.py leaves it alone.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SOURCES = bbmodel.ROOT / "werewolf"
SPLIT = {"belfry_crown"}


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def outputs(src):
    """[(model id, elements)] for one source; scripts/test_blockbench_models.py uses it too."""
    if src.name in SPLIT:
        return [(f"{src.name}_{g}", src.java(only=[g])) for g in src.groups()]
    return [(src.name, src.java())]


def write(src):
    tex = f"vampyuyu:item/werewolf/{src.name}"
    (PACK / "textures" / "item" / "werewolf").mkdir(parents=True, exist_ok=True)
    (PACK / "textures" / "item" / "werewolf" / f"{src.name}.png").write_bytes(src.png)
    written = []
    for model_id, elements in outputs(src):
        dump(PACK / "models" / "item" / "werewolf" / f"{model_id}.json",
             {"textures": {"0": tex, "particle": tex}, "elements": elements})
        dump(PACK / "items" / "werewolf" / f"{model_id}.json",
             {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/werewolf/{model_id}"}})
        written.append(f"werewolf/{model_id}")
    return written


def main():
    sources = sorted(SOURCES.glob("*.bbmodel")) if SOURCES.exists() else []
    written = []
    for path in sources:
        written += write(bbmodel.Source(path))
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | set(written))) + "\n")
    print(f"wrote {len(written)} werewolf decor models from {len(sources)} sources in {SOURCES.name}/")


if __name__ == "__main__":
    main()
