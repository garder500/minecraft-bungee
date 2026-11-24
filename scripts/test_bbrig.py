#!/usr/bin/env python3
"""The rig math of resource-pack/art/bbrig.py reproduces Blockbench: the bone matrices of a two-bone test rig, recorded in
the app at three times of an animation (resource-pack/art/blockbench/rig_fixture.json), match the baked ones."""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "resource-pack/art"))
import bbrig  # noqa: E402


def main():
    fx = json.loads((ROOT / "resource-pack/art/blockbench/rig_fixture.json").read_text())
    uuids = {g["name"]: f"u-{g['name']}" for g in fx["groups"]}
    groups = [{"name": g["name"], "uuid": uuids[g["name"]], "origin": g["origin"], "rotation": [0, 0, 0]} for g in fx["groups"]]
    outliner = [{"uuid": uuids["A"], "children": ["c-a", {"uuid": uuids["B"], "children": ["c-b"]}]}]
    animators = {uuids[b]: {"name": b, "type": "bone", "keyframes": [
        {"channel": k["ch"], "time": k["t"], "interpolation": k["i"], "data_points": [dict(zip("xyz", k["v"]))]} for k in keys]}
        for b, keys in fx["animation"]["keys"].items()}
    data = {"meta": {"model_format": "free"}, "groups": groups, "outliner": outliner,
            "animations": [{"name": "t", "length": fx["animation"]["length"], "loop": "once", "animators": animators}]}
    rig = bbrig.Rig(data, "fixture")
    errors = 0
    for t in ("0.5", "1"):
        for bone, cube in (("A", "ca"), ("B", "cb")):
            got = rig.matrix("t", bone, float(t))
            want = fx["matrices"][t][cube]               # THREE.js column-major
            for i in range(4):
                for j in range(4):
                    if abs(got[i][j] - want[j * 4 + i]) > 1e-3:
                        print(f"FAIL t={t} {bone}[{i}][{j}] = {got[i][j]:.4f}, Blockbench {want[j * 4 + i]:.4f}")
                        errors += 1
    # the Java frame of a rest pose is the identity
    rest = rig.java_frame(None, "B", 0)
    if any(abs(a - b) > 1e-6 for a, b in zip(rest, [0, 0, 0, 0, 0, 0, 1, 1])):
        print("FAIL rest frame", rest)
        errors += 1
    print(f"bbrig: {errors} mismatches against Blockbench")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
