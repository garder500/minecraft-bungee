#!/usr/bin/env python3
"""Arena waiting hubs: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/hub_models.py

The shapes of the two waiting hubs (WaitingHubs.java: the ValoBlock Staging Deck in vb_arena, the Werewolf Moonlit
Village in ww_sky) that blocks cannot make are item-display models spawned by the plugin (HubDisplays.java), centred on
the display: the source is centred on pixel (8, 8, 8) and scaled up by the plugin. Each source is a Blockbench project,
resource-pack/art/blockbench/hubs/<name>.bbmodel (format "Java Block/Item", one palette texture painted in Blockbench,
shade false, light_emission on the glowing cubes); edit it there, then run this script. They go in the base pack, which
every player holds in both hubs (the ValoBlock pack is an extra pack on top of it).

  models/item/hub/<name>.json, textures/item/hub/<name>.png, items/hub/<name>.json   (item model vampyuyu:hub/<name>)
Every id is listed in imported.txt, so import_textures.py leaves it alone.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SOURCES = bbmodel.ROOT / "hubs"


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def write(src):
    tex = f"vampyuyu:item/hub/{src.name}"
    (PACK / "textures" / "item" / "hub").mkdir(parents=True, exist_ok=True)
    (PACK / "textures" / "item" / "hub" / f"{src.name}.png").write_bytes(src.png)
    dump(PACK / "models" / "item" / "hub" / f"{src.name}.json",
         {"textures": {"0": tex, "particle": tex}, "elements": src.java()})
    dump(PACK / "items" / "hub" / f"{src.name}.json",
         {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/hub/{src.name}"}})
    return f"hub/{src.name}"


def main():
    sources = sorted(SOURCES.glob("*.bbmodel")) if SOURCES.exists() else []
    written = [write(bbmodel.Source(p)) for p in sources]
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | set(written))) + "\n")
    print(f"wrote {len(written)} hub models from {len(sources)} sources in {SOURCES.name}/")


if __name__ == "__main__":
    main()
