#!/usr/bin/env python3
"""Turns block texture sheets (generated images: square textures in a grid, magenta gutters) into pack textures.

    nix-shell -p "python3.withPackages (p: [p.pillow])" --run "python3 resource-pack/art/import_faces.py [--sheet out.png]"

Unlike import_textures.py (items on a background), every cell here IS the texture: the cells are found from the
magenta gutters, cut to their centre square, scaled down and reduced to a small palette.
- machine faces -> pack/assets/vampyuyu/textures/block/machine/<face>.png (32x32), used by block_models.py;
- generator blocks -> pack/assets/minecraft/textures/block/<block>.png (16x16): they replace the vanilla look of the
  blocks the ring generators make, everywhere on the server. They are made seamless before scaling down.
"""
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ART = pathlib.Path(__file__).resolve().parent
CONCEPTS = ART / "concepts"
ASSETS = ART.parent / "pack/assets"

MACHINE = "vampyuyu/textures/block/machine"
VANILLA = "minecraft/textures/block"

# sheet -> (columns, rows, size, seamless, [target per cell, row by row; None = unused]); CROP narrows a sheet whose
# cells hold a drawn object rather than a flat texture to the object's middle
CROP = {}
SHEETS = {
    "machine_faces.png": (6, 4, 32, False, [
        f"{MACHINE}/fountain_top", f"{MACHINE}/fountain_side", f"{MACHINE}/spring_stone_top",
        f"{MACHINE}/spring_stone_side", f"{MACHINE}/coal_generator_front", f"{MACHINE}/coal_generator_side",
        f"{MACHINE}/coal_generator_top", f"{MACHINE}/battery_side", f"{MACHINE}/battery_top",
        f"{MACHINE}/solar_panel_top", f"{MACHINE}/solar_panel_side", f"{MACHINE}/miner_1_front",
        f"{MACHINE}/miner_1_side", f"{MACHINE}/miner_1_top", f"{MACHINE}/miner_2_front",
        f"{MACHINE}/miner_2_side", f"{MACHINE}/miner_2_top", f"{MACHINE}/forge_front",
        f"{MACHINE}/forge_side", f"{MACHINE}/forge_top", f"{MACHINE}/cable",
        f"{MACHINE}/bottom", None, None]),
    "generator_blocks.png": (3, 3, 16, True, [
        f"{VANILLA}/stone", f"{VANILLA}/cobblestone", f"{VANILLA}/sand",
        f"{VANILLA}/red_sand", f"{VANILLA}/gravel", f"{VANILLA}/snow",
        f"{VANILLA}/packed_ice", f"{VANILLA}/netherrack", f"{VANILLA}/soul_sand"]),
}


def magenta(px):
    r, g, b = px[:3]
    return r > 170 and b > 170 and g < 110


def runs(flags, want):
    """[start, end) spans where flags == want, longest first trimmed of tiny noise spans."""
    spans, start = [], None
    for i, f in enumerate(flags + [not want]):
        if f == want and start is None:
            start = i
        elif f != want and start is not None:
            spans.append((start, i))
            start = None
    return spans


def cells(img, cols, rows):
    """Cell boxes from the gutters: columns/rows that are mostly magenta separate the textures."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    col_m = [sum(magenta(px[x, y]) for y in range(0, h, 4)) / (h / 4) > 0.6 for x in range(w)]
    row_m = [sum(magenta(px[x, y]) for x in range(0, w, 4)) / (w / 4) > 0.6 for y in range(h)]
    xs = sorted(runs(col_m, False), key=lambda s: s[1] - s[0], reverse=True)[:cols]
    ys = sorted(runs(row_m, False), key=lambda s: s[1] - s[0], reverse=True)[:rows]
    if len(xs) < cols or len(ys) < rows:  # no clean gutters: fall back to an even grid
        xs = [(int(c * w / cols), int((c + 1) * w / cols)) for c in range(cols)]
        ys = [(int(r * h / rows), int((r + 1) * h / rows)) for r in range(rows)]
    xs.sort()
    ys.sort()
    return [(x0, y0, x1, y1) for (y0, y1) in ys for (x0, x1) in xs]


def square(img, box, fraction=1.0):
    x0, y0, x1, y1 = box
    side = int(min(x1 - x0, y1 - y0) * fraction)
    inset = max(2, side // 40)  # stay clear of anti-aliased gutter edges
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    half = side // 2 - inset
    return img.crop((cx - half, cy - half, cx + half, cy + half)).convert("RGB")


def seamless(tex):
    """Blends the texture with itself shifted by half, weighted towards the edges, so it tiles without seams."""
    w, h = tex.size
    shifted = ImageChops.offset(tex, w // 2, h // 2)
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    margin = w // 5
    draw.rectangle((margin, margin, w - margin, h - margin), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(margin / 2))
    return Image.composite(tex, shifted, mask)


def pixel(tex, size):
    small = tex.resize((size, size), Image.BOX)
    return small.quantize(colors=20 if size >= 32 else 12, method=Image.Quantize.MEDIANCUT,
                          dither=Image.Dither.NONE).convert("RGBA")


def main():
    done = []
    for sheet, (cols, rows, size, tile, targets) in SHEETS.items():
        path = CONCEPTS / sheet
        if not path.exists():
            print(f"skip {sheet}: not generated yet")
            continue
        img = Image.open(path)
        for box, target in zip(cells(img, cols, rows), targets):
            if target is None:
                continue
            tex = square(img, box, CROP.get(sheet, 1.0))
            if tile:
                tex = seamless(tex)
            out = ASSETS / f"{target}.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            pixel(tex, size).save(out, optimize=True)
            done.append(target)
    print(f"imported {len(done)} block textures")
    if "--sheet" in sys.argv:
        contact(done, sys.argv[sys.argv.index("--sheet") + 1])


def contact(targets, out):
    """Each texture at x8, and the 16x16 ones also tiled 3x3 to show the seams."""
    tiles = []
    for t in targets:
        tex = Image.open(ASSETS / f"{t}.png").convert("RGBA")
        if tex.width == 16:
            grid = Image.new("RGBA", (48, 48))
            for gx in range(3):
                for gy in range(3):
                    grid.paste(tex, (gx * 16, gy * 16))
            tiles.append(grid.resize((288, 288), Image.NEAREST))
        else:
            tiles.append(tex.resize((256, 256), Image.NEAREST))
    per = 6
    cell = 300
    sheet = Image.new("RGBA", (cell * per, cell * ((len(tiles) + per - 1) // per)), (50, 50, 60, 255))
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, ((i % per) * cell + 6, (i // per) * cell + 6))
    sheet.save(out)
    print(f"contact sheet: {out}")


if __name__ == "__main__":
    main()
