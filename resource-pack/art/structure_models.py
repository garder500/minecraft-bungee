#!/usr/bin/env python3
"""Skyblock world structures: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/structure_models.py

The parts of a structure that blocks cannot make (the armillary over the Quai des Huit, the halos over its Sky Gates) are
item-display models placed by the plugin (BlockPlan.Model, StructureModels.java), centred on the display: the source is
centred on pixel (8, 8, 8). Each source is a Blockbench project, resource-pack/art/blockbench/structures/<name>.bbmodel
(format "Java Block/Item", palette texture painted in Blockbench, shade false); edit it there, then run this script.

  SPLIT   a model per top-level group, so the plugin turns each one on its own: structure/<name>_<group>
  TWINS   a second texture on the same layout gives a twin: structure/<name> (first texture, no glow, no tint) and
          structure/<name>_lit (the twin texture, light_emission kept, tinted by the custom_model_data colour 0, which
          the plugin sets to the Realm's colour)
  TINTED  one model, tinted like the lit twin (the seal sigil, in each Realm's colour)
  else    one model, structure/<name>

  models/item/structure/<id>.json, textures/item/structure/<id>.png, items/structure/<id>.json
Every id is listed in imported.txt, so import_textures.py leaves it alone.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SOURCES = bbmodel.ROOT / "structures"
SPLIT = {"quai_armillary", "hub_memory"}
TWINS = {"quai_halo": "quai_halo_lit"}
TINTED = {"seal_sigil"}   # one white model recoloured per Realm (custom_model_data colour 0)
WHITE = 16777215


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def no_glow(elements):
    return [{k: v for k, v in el.items() if k != "light_emission"} for el in elements]


def outputs(src):
    """[(model id, texture id, png bytes, elements, tinted)] for one source; the checks use it too."""
    if src.name in SPLIT:
        return [(f"{src.name}_{g}", src.name, src.png, src.java(only=[g]), False) for g in src.groups()]
    if src.name in TWINS:
        lit = TWINS[src.name]
        return [(src.name, src.name, src.pngs[src.name], no_glow(src.java()), False),
                (lit, lit, src.pngs[lit], src.java(), True)]
    return [(src.name, src.name, src.png, src.java(), src.name in TINTED)]


def write(src):
    written = []
    for model_id, tex_id, png, elements, tinted in outputs(src):
        tex = f"vampyuyu:item/structure/{tex_id}"
        (PACK / "textures" / "item" / "structure").mkdir(parents=True, exist_ok=True)
        (PACK / "textures" / "item" / "structure" / f"{tex_id}.png").write_bytes(png)
        dump(PACK / "models" / "item" / "structure" / f"{model_id}.json",
             {"textures": {"0": tex, "particle": tex}, "elements": elements})
        item = {"type": "minecraft:model", "model": f"vampyuyu:item/structure/{model_id}"}
        if tinted:
            item["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": WHITE}]
        dump(PACK / "items" / "structure" / f"{model_id}.json", {"model": item})
        written.append(f"structure/{model_id}")
    return written


def main():
    sources = sorted(SOURCES.glob("*.bbmodel")) if SOURCES.exists() else []
    written = []
    for path in sources:
        written += write(bbmodel.Source(path))
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | set(written))) + "\n")
    print(f"wrote {len(written)} structure models from {len(sources)} sources in {SOURCES.name}/")


if __name__ == "__main__":
    main()
