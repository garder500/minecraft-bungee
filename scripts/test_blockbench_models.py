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
import werewolf_models  # noqa: E402

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
    if family == "werewolf":
        return [(PACK / f"models/item/werewolf/{model_id}.json", model_id) for model_id, _ in werewolf_models.outputs(bbmodel.Source(path))]
    if family == "bosses":
        return [(PACK / f"models/item/boss/skin/{stem}.json", False)]
    if family == "fx":   # boss VFX primitives (fx_models.py), recoloured through their tint
        return [(PACK / f"models/item/fx/{stem}.json", False)]
    if family == "structures":   # world structure display models (structure_models.py): checked in main()
        return []
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
            if path.parent.name == "werewolf":   # a werewolf decor model, by its id
                want = dict(werewolf_models.outputs(src))[mirror]
            elif isinstance(mirror, str):      # a cable model, by its label
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
    # the halo shells: one per primitive the converter lists, and the plugin's list (BossVfx.HALOS) is the same
    import re
    import fx_models
    java = (ROOT / "survival-plugin/src/main/java/win/vampyuyu/survival/boss/BossVfx.java").read_text()
    found = re.search(r"HALOS = java.util.Set.of\((.*?)\);", java, re.S)
    if not found or set(re.findall(r'"([a-z_]+)"', found.group(1))) != set(fx_models.HALOS):
        errors.append("BossVfx.HALOS and fx_models.HALOS differ")
    for name in fx_models.HALOS:
        if not (PACK / f"items/fx/{name}_halo.json").exists():
            errors.append(f"fx/{name}_halo missing (run fx_models.py)")
    # world structure display models: the pack matches the sources, and every model a plan names exists
    import structure_models
    for path in sorted((bbmodel.ROOT / "structures").glob("*.bbmodel")):
        for model_id, _, _, elements, tinted in structure_models.outputs(bbmodel.Source(path)):
            out = PACK / f"models/item/structure/{model_id}.json"
            item = PACK / f"items/structure/{model_id}.json"
            if not out.exists() or not item.exists():
                errors.append(f"structure/{model_id} missing (run structure_models.py)")
            elif json.loads(out.read_text())["elements"] != elements:
                errors.append(f"structure/{model_id} is not the conversion of its source (run structure_models.py)")
            elif tinted != ("tints" in json.loads(item.read_text())["model"]):
                errors.append(f"structure/{model_id}: tint does not match")
    plans = ROOT / "survival-plugin/src/main/java/win/vampyuyu/survival"
    for java_file in plans.rglob("*.java"):
        for model_id in re.findall(r'"structure/([a-z0-9_]+)"', java_file.read_text()):
            if not (PACK / f"items/structure/{model_id}.json").exists():
                errors.append(f"{java_file.name} names structure/{model_id}, which the pack does not have")
    for e in errors[:40]:
        print("FAIL", e)
    print(f"blockbench models: {n} sources checked, {len(errors)} problems")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
