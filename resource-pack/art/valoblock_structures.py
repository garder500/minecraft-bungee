#!/usr/bin/env python3
"""ValoBlock map set pieces: Blockbench sources to models of the ValoBlock pack (stdlib only, deterministic).

    python3 resource-pack/art/valoblock_structures.py

The parts of a ValoBlock map that blocks cannot make (the giant hourglass over Hourfall's mid, its gold ring, the scarab on
the B temple) are item-display models placed by the plugin for the length of a match (VbCanvas.Model, VbMapService),
centred on the display: the source is centred on pixel (8, 8, 8) and the plugin scales it up. They are scenery only:
no collision, and a gun never tests entities that are not players.

Each source is a Blockbench project, resource-pack/art/blockbench/valoblock/<name>.bbmodel (format "Java Block/Item",
palette texture painted in Blockbench, shade false); edit it there, then run this script. It writes, in the second pack
(resource-pack/valoblock):

  models/item/valoblock/map/<name>.json, textures/item/valoblock/map/<name>.png, items/valoblock/map/<name>.json
  (item model vampyuyu:valoblock/map/<name>)
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "valoblock" / "assets" / "vampyuyu"
SOURCES = bbmodel.ROOT / "valoblock"


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def model_path(name):
    return PACK / "models" / "item" / "valoblock" / "map" / f"{name}.json"


def write(src):
    tex = f"vampyuyu:item/valoblock/map/{src.name}"
    png = PACK / "textures" / "item" / "valoblock" / "map" / f"{src.name}.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    png.write_bytes(src.png)
    dump(model_path(src.name), {"textures": {"0": tex, "particle": tex}, "elements": src.java()})
    dump(PACK / "items" / "valoblock" / "map" / f"{src.name}.json", {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/valoblock/map/{src.name}"}})
    return f"valoblock/map/{src.name}"


def main():
    sources = sorted(SOURCES.glob("*.bbmodel")) if SOURCES.exists() else []
    written = [write(bbmodel.Source(p)) for p in sources]
    print(f"wrote {len(written)} ValoBlock map models from {len(sources)} sources in {SOURCES.name}/: {', '.join(written)}")


if __name__ == "__main__":
    main()
