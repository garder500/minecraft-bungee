#!/usr/bin/env python3
"""Skyblock 3D assets modelled in Blockbench (resource-pack/art/blockbench/<family>/*.bbmodel): every source passes
bbmodel.problems (faces textured, texels above 25 % grey, one rotation axis, -16..32, no coplanar overlaps), and every
pack model a converter wrote from a source is the conversion of that source (no stale or hand-edited output)."""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ART = ROOT / "resource-pack/art"
PACK = ROOT / "resource-pack/pack/assets/vampyuyu"
sys.path.insert(0, str(ART))
import bbmodel  # noqa: E402
import block_models  # noqa: E402

# family -> (source stem -> [(pack model path, mirror_x)])
def outputs(path):
    stem, family = path.stem, path.parent.name
    if family == "blocks":
        if stem == "cable":
            return [(PACK / f"models/item/machine/{label}{on}_3d.json", label) for label in ["cable"] + [f"cable_{m}" for m in range(16)]
                    for on in ("", "_on")]
        fam = "forge" if stem == "forge" else "machine"
        return [(PACK / f"models/item/{fam}/{stem}_3d.json", None)]
    if family == "items":
        for kind in ("tool", "weapon", "skin", "ore"):
            if (PACK / f"models/item/{kind}/{stem}_3d.json").exists():
                return [(PACK / f"models/item/{kind}/{stem}_3d.json", None)]
        if stem.endswith("_bow"):
            return [(PACK / f"models/item/tool/{stem}_{n}.json", n) for n in range(4)]
        return [(PACK / f"models/item/{stem}_3d.json", None)]   # reported missing
    if family == "bosses":
        return [(PACK / f"models/item/boss/skin/{stem}.json", False)]
    if stem.endswith("_helmet"):
        return [(PACK / f"models/item/armor/{stem}_3d.json", False)]
    if stem.startswith("wings_tier"):
        tier, state = stem[len("wings_tier"):].split("_")
        base = PACK / "models/item/wings"
        return [(base / f"worn_tier{tier}_{state}_right.json", False), (base / f"worn_tier{tier}_{state}_left.json", True)]
    return []


def main():
    errors, n = [], 0
    for path in sorted(bbmodel.ROOT.rglob("*.bbmodel")):
        n += 1
        src = bbmodel.Source(path)
        for label, elements in (block_models.cable_states(src) if src.name == "cable" else bbmodel.states(src)):
            errors += bbmodel.problems(f"{path.parent.name}/{label}", elements, src.tex)
        for out, mirror in outputs(path):
            if not out.exists():
                errors.append(f"{src.name}: {out.relative_to(PACK)} missing (run the converter)")
                continue
            if isinstance(mirror, str):      # a cable model, by its label
                want = dict(block_models.cable_states(src))[mirror]
            elif isinstance(mirror, int) and not isinstance(mirror, bool):   # a bow stage
                want = bbmodel.states(src)[mirror][1]
            else:
                want = src.java(mirror_x=bool(mirror))
            if json.loads(out.read_text())["elements"] != want:
                errors.append(f"{src.name}: {out.relative_to(PACK)} is not the conversion of its source (run the converter)")
    # rigged bosses (Generic Model format): a model per bone equal to the bone's cubes, the baked rig in the plugin's jar
    for path in sorted((bbmodel.ROOT / "bosses").glob("*.bbmodel")):
        src = bbmodel.Source(path)
        if not src.free:
            continue
        import bbrig
        try:
            rig, _ = bbrig.load(path)
        except ValueError as e:
            errors.append(f"{src.name}: {e}")
            continue
        baked = ROOT / "survival-plugin/src/main/resources/boss/rigs" / f"{src.name}.json"
        if not baked.exists() or [b["name"] for b in json.loads(baked.read_text())["bones"]] != rig.bones:
            errors.append(f"{src.name}: the plugin's rig is missing or stale (run boss_skins.py)")
        for bone in rig.bones:
            out = PACK / f"models/item/boss/skin/{src.name}/{bone}.json"
            if not out.exists() or json.loads(out.read_text())["elements"] != src.java(bone=bone):
                errors.append(f"{src.name}: bone model {bone} missing or stale (run boss_skins.py)")
        for need in ("idle", "death"):
            if need not in rig.animations:
                errors.append(f"{src.name}: no {need} clip")
    for e in errors[:40]:
        print("FAIL", e)
    print(f"blockbench models: {n} sources checked, {len(errors)} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
