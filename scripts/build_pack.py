#!/usr/bin/env python3
"""Packs a resource pack into a reproducible ZIP and writes its SHA-1.

    python3 scripts/build_pack.py [--pack menus|valoblock] [output-dir]    # default: menus, resource-pack/dist

Two packs: "menus" (resource-pack/pack, the base pack every player gets) and "valoblock" (resource-pack/valoblock,
sent on top of it only while a player is in a ValoBlock match).

Timestamps, ordering and permissions are fixed so the same sources always
yield the same SHA-1 (clients cache packs by hash).
"""
import hashlib, pathlib, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACKS = {"menus": ("pack", "vampyuyu-menus"), "valoblock": ("valoblock", "vampyuyu-valoblock")}
SOURCE = ROOT / "resource-pack" / PACKS["menus"][0]
NAME = PACKS["menus"][1]

def build(out_dir, pack="menus"):
    folder, name = PACKS[pack]
    source = ROOT / "resource-pack" / folder
    out = pathlib.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    target = out / f"{name}.zip"
    # Written next to the target then renamed: the panel serving dist/ never reads a half-written ZIP.
    partial = out / f".{name}.zip.partial"
    files = sorted(p for p in source.rglob("*") if p.is_file())
    if not any(p.name == "pack.mcmeta" for p in files):
        sys.exit("pack.mcmeta missing")
    with zipfile.ZipFile(partial, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for path in files:
            info = zipfile.ZipInfo(path.relative_to(source).as_posix(), (2024, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, path.read_bytes())
    digest = hashlib.sha1(partial.read_bytes()).hexdigest()
    old = target.read_bytes() if target.is_file() else None
    if old is not None and hashlib.sha1(old).hexdigest() == digest:
        partial.unlink()  # unchanged: keep the file (and its mtime) the panel already serves
    else:
        partial.replace(target)
    (out / f"{name}.sha1").write_text(digest + "\n")
    return target, digest

if __name__ == "__main__":
    args = sys.argv[1:]
    pack = "menus"
    if "--pack" in args:
        i = args.index("--pack")
        pack = args[i + 1]
        del args[i:i + 2]
        if pack not in PACKS:
            sys.exit(f"unknown pack {pack!r}: {', '.join(PACKS)}")
    path, digest = build(args[0] if args else ROOT / "resource-pack" / "dist", pack)
    print(f"{path} {digest}")
