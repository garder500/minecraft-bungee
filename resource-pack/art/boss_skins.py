#!/usr/bin/env python3
"""Custom looks of the bosses: Blockbench sources to pack models (stdlib only, deterministic).

    python3 resource-pack/art/boss_skins.py

The source of each boss is a Blockbench project, resource-pack/art/blockbench/bosses/<id>.bbmodel (format "Java
Block/Item", one embedded 128x128 texture of 16x16 swatches, a material per row pair and a swatch per face orientation,
painted in Blockbench; modelled there: edit them there, then run this script). For every boss it writes
  pack/assets/vampyuyu/models/item/boss/skin/<id>.json      (a model made of boxes, 16 units = 1 block)
  pack/assets/vampyuyu/textures/item/boss/skin/<id>.png     (the Blockbench texture, its glowing texels marked alpha 251)
  pack/assets/vampyuyu/items/boss/skin/<id>.json            (item model vampyuyu:boss/skin/<id>)
A boss whose project is in the Generic Model format is rigged and animated (bbrig.py): it also gets one item model per bone
(models/item/boss/skin/<id>/<bone>.json) and its baked animation clips for the plugin, written to
survival-plugin/src/main/resources/boss/rigs/<id>.json (BossRig.java, one item display per bone).
The plugin (BossSkinService) hides the vanilla mob and shows this model on an item display that follows it, so only
the Skyblock bosses change: vanilla zombies, spiders and dragons of Survival keep their look. The model's front is -z.
The output is deterministic.
"""
import json
import pathlib
import struct
import zlib

import bbmodel

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"


def png(pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def clamp(v):
    return max(0, min(255, int(round(v))))


# ---------------------------------------------------------------------------------------------------------------- bosses
# The Lava Colossus (a knuckle-walking basalt brute), the Boreal Watcher (a gaunt knight on his planted greatsword), the
# Mycelial Queen (a woman of bark rising from a mushroom-grown spider), the Chained Leviathan (a sea serpent rising out
# of its coil), the Judge of Lost Souls (a hooded lich holding the scales), the Echo of the Rift (a dragon) and Hell Gao,
# the Anomaly (the secret post-game superboss).
SOURCES = bbmodel.ROOT / "bosses"
BOSSES = ("magma_warden", "frost_revenant", "sylvan_matriarch", "abyssal_leviathan", "crypt_lord", "voidwyrm", "hell_gao")


RIGS = ART.parent.parent / "survival-plugin/src/main/resources/boss/rigs"


def write_rig(boss, src):
    """A rigged boss (Generic Model format): one item model per bone, models/item/boss/skin/<boss>/<bone>.json (the
    bone's own cubes, same texture), and the baked clips for the plugin, survival-plugin/.../boss/rigs/<boss>.json."""
    import bbrig
    rig, _ = bbrig.load(src.path)
    tex = f"vampyuyu:item/boss/skin/{boss}"
    folder = PACK / "models" / "item" / "boss" / "skin" / boss
    if folder.exists():
        for old in folder.glob("*.json"):
            old.unlink()
    for bone in rig.bones:
        for path, data in (
                (folder / f"{bone}.json", {"textures": {"0": tex, "particle": tex}, "elements": src.java(bone=bone)}),
                (PACK / "items" / "boss" / "skin" / boss / f"{bone}.json",
                 {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/boss/skin/{boss}/{bone}"}})):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    baked = rig.bake(lambda bone: f"vampyuyu:boss/skin/{boss}/{bone}")
    RIGS.mkdir(parents=True, exist_ok=True)
    (RIGS / f"{boss}.json").write_text(json.dumps(baked, separators=(",", ":")) + "\n", encoding="utf-8")
    return len(rig.bones), sorted(rig.animations)


def write(boss):
    src = bbmodel.Source(SOURCES / f"{boss}.bbmodel")
    tex = f"vampyuyu:item/boss/skin/{boss}"
    for path, data in (
            (PACK / "models" / "item" / "boss" / "skin" / f"{boss}.json",
             {"textures": {"0": tex, "particle": tex}, "elements": src.java()}),
            (PACK / "items" / "boss" / "skin" / f"{boss}.json",
             {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/boss/skin/{boss}"}})):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")
    out = PACK / "textures" / "item" / "boss" / "skin" / f"{boss}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(bbmodel.glow_png(src))   # glowing faces marked for the item shader (alpha 251)
    if src.free:
        bones, clips = write_rig(boss, src)
        print(f"  {boss}: rig of {bones} bones, clips {', '.join(clips)}")


def main(only=None):
    for boss in only or BOSSES:
        write(boss)
    print(f"wrote {len(only or BOSSES)} boss skins from {SOURCES.name}/")


if __name__ == "__main__":
    import sys
    main(sys.argv[1:] or None)
