#!/usr/bin/env python3
"""Checks the Wings pack files without a client: python3 resource-pack/art/wings_check.py

Every item definition points at an existing model, every model at an existing 16x16 texture, every equipment
asset at 64x32 layer textures in the folders Minecraft resolves (entity/equipment/<layer type>/<name>.png), and
re-running the generator reproduces the committed files byte for byte.
"""
import hashlib, json, pathlib, struct, subprocess, sys

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack" / "assets" / "vampyuyu"


def size(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path} is not a PNG"
    return struct.unpack(">II", data[16:24])


def digest():
    h = hashlib.sha1()
    for root in ("items/wings", "models/item/wings", "textures/item/wings", "textures/entity/equipment", "equipment"):
        for p in sorted((ASSETS / root).rglob("*")):
            if p.is_file() and ("wings" in p.name):
                h.update(p.relative_to(ASSETS).as_posix().encode() + p.read_bytes())
    return h.hexdigest()


def main():
    errors = []
    names = ["wings"] + [f"wings_tier{t}" for t in range(2, 6)] + [f"core_{t}" for t in range(1, 6)]
    for name in names:
        item = json.loads((ASSETS / "items/wings" / f"{name}.json").read_text())
        model_ref = item["model"]["model"]
        ns, _, rel = model_ref.partition(":")
        model = json.loads((ASSETS / "models" / f"{rel}.json").read_text())
        tex_ref = model["textures"]["layer0"].partition(":")[2]
        tex = ASSETS / "textures" / f"{tex_ref}.png"
        if not tex.is_file():
            errors.append(f"{name}: missing texture {tex}")
        elif size(tex) != (16, 16):
            errors.append(f"{name}: icon must be 16x16, got {size(tex)}")
    for asset in ["wings"] + [f"wings_tier{t}" for t in range(1, 6)]:
        equip = json.loads((ASSETS / "equipment" / f"{asset}.json").read_text())
        for layer_type, layers in equip["layers"].items():
            if layer_type not in ("humanoid", "wings"):
                errors.append(f"{asset}: unexpected layer type {layer_type}")
            for layer in layers:
                ns, _, name = layer["texture"].partition(":")
                tex = ASSETS.parent / ns / "textures/entity/equipment" / layer_type / f"{name}.png"
                if not tex.is_file():
                    errors.append(f"{asset}: missing {tex}")
                elif size(tex) != (64, 32):
                    errors.append(f"{asset}: {tex.name} must be 64x32, got {size(tex)}")
    before = digest()
    subprocess.run([sys.executable, str(ART / "wings_art.py")], check=True, stdout=subprocess.DEVNULL)
    if digest() != before:
        errors.append("wings_art.py is not reproducible or the committed files are stale")
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    print("wings pack ok")


if __name__ == "__main__":
    main()
