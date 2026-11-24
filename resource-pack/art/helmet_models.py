#!/usr/bin/env python3
"""The five ladder helmets worn on the head: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/helmet_models.py

The source of each helmet is a Blockbench project, resource-pack/art/blockbench/armor/<tier>_helmet.bbmodel (format
"Java Block/Item", one embedded 64x64 palette texture, modelled and painted in Blockbench: edit them there, then run
this script). A helmet item with no equipment asset is drawn on the head as its item model (like a carved pumpkin);
the head fills about 1.5-14.5 of the model space, so the shell is modelled at its final size around it (1-15), the
front is north (z = 0) and a visor slit at y 6.2-7.8 leaves the player's eyes visible. Every element is shade: false
(the light is painted into the palette) and every shown texel is above 25 % grey (bbmodel.problems checks both).

Per tier, under resource-pack/pack/assets/vampyuyu/:
  models/item/armor/<tier>_helmet_3d.json     the helmet on the head, in the hand and on the ground
  textures/item/armor/helm/<tier>_helmet.png  the Blockbench texture, byte for byte
  items/armor/<tier>_helmet.json              gui -> the flat inventory sprite (armor_tiers.py), anything else -> 3D

two_metals() and png() stay here for armor_layers.py.
"""
import json
import pathlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack/assets/vampyuyu"
SOURCES = bbmodel.ROOT / "armor"
TIERS = ("bronze", "rime", "ember", "void", "starcore")


def two_metals(pixels):
    """(plate pixels, trim pixels or None): a sprite painted in two metals (Starcore: white plates, gold trim) is split
    by saturation, the paler group is the plate. A sprite of one metal has no second group."""
    import colorsys
    sat = lambda c: colorsys.rgb_to_hsv(*(v / 255 for v in c))[1]  # noqa: E731
    ranked = sorted(pixels, key=sat)
    cut = max(range(len(ranked) // 5, len(ranked) * 4 // 5), key=lambda i: sat(ranked[i + 1]) - sat(ranked[i]) if i + 1 < len(ranked) else 0)
    pale, vivid = ranked[:cut + 1], ranked[cut + 1:]
    mean = lambda part: sum(sat(c) for c in part) / len(part)  # noqa: E731
    if mean(vivid) - mean(pale) < 0.3:
        return pixels, None
    lum = lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]  # noqa: E731
    return sorted(pale, key=lum), sorted(vivid, key=lum)


def png(width, height, rows):
    assert len(rows) == height and len(rows[0]) == width
    return bbmodel.png(rows)


DISPLAY = {"head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
           "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
           "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
           "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3}}


def helmet(src, tier):
    tex = f"vampyuyu:item/armor/helm/{tier}_helmet"
    return {"textures": {"0": tex, "particle": tex}, "elements": src.java(), "display": DISPLAY}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n")


def main():
    helm = PACK / "textures/item/armor/helm"
    helm.mkdir(parents=True, exist_ok=True)
    for tier in TIERS:
        name = f"{tier}_helmet"
        src = bbmodel.Source(SOURCES / f"{name}.bbmodel")
        (helm / f"{name}.png").write_bytes(src.png)
        write(PACK / "models/item/armor" / f"{name}_3d.json", helmet(src, tier))
        write(PACK / "items/armor" / f"{name}.json", {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": f"vampyuyu:item/armor/{name}"}}],
            "fallback": {"type": "minecraft:model", "model": f"vampyuyu:item/armor/{name}_3d"}}})
        for kind in ("plate", "trim", "dark", "accent"):   # the four textures of the Python-built helmets
            stale = helm / f"{tier}_{kind}.png"
            if stale.exists():
                stale.unlink()
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"armor/{t}_helmet_3d" for t in TIERS})) + "\n")
    print(f"wrote {len(TIERS)} helmets from {SOURCES.relative_to(ART)}/")


if __name__ == "__main__":
    main()
