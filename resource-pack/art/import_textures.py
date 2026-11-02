#!/usr/bin/env python3
"""Turns concept sheets (generated images, one item per grid cell on a flat magenta background) into pack sprites.

    python3 resource-pack/art/import_textures.py [--sheet out.png]     (needs Pillow)

The sheets live in resource-pack/art/concepts/ and are committed, so the sprites can be rebuilt. Each cell becomes a
32x32 pixel-art sprite: the magenta is keyed out, the item is trimmed and centred, scaled down, reduced to a small
palette and outlined. It is written where the procedural scripts used to write the flat sprite:
  pack/assets/vampyuyu/textures/item/<family>/<id>.png  (+ models/item and items definitions when missing)
Every imported id is listed in art/imported.txt; the procedural scripts skip those ids (see imported.py), so running
them again never overwrites an imported sprite. 3D models keep their own atlas (<id>_3d.png).
"""
import json
import pathlib
import sys

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent
CONCEPTS = ART / "concepts"
PACK = ART.parent / "pack/assets/vampyuyu"
SIZE = 32
COLORS = 24

# sheet file -> (columns, rows, [family/id per cell, row by row; None = unused cell])
SHEETS = {
    "ores.png": (5, 2, ["ore/bronzite", "ore/frostium", "ore/sunstone", "ore/voidglass", "ore/starcore",
                        "ore/bronzite_bar", "ore/frostium_bar", "ore/sunstone_bar", "ore/voidglass_bar", "ore/starcore_bar"]),
    "realm.png": (2, 1, ["ore/cinderheart", "ore/heartwood"]),
    "weapons.png": (5, 2, ["weapon/bronze_cleaver", "weapon/rimeblade", "weapon/ember_sword", "weapon/void_sword",
                           "weapon/starcore_sword", "tool/bronze_pick", "tool/rime_pick", "tool/ember_pick",
                           "tool/void_pick", "tool/starcore_pick"]),
    "machines.png": (4, 3, ["machine/fountain", "machine/spring_stone", "machine/coal_generator", "machine/battery",
                            "machine/solar_panel", "machine/miner_1", "machine/miner_2", "forge/forge",
                            "utility/skyhook", "utility/cloud_anchor", "utility/ore_compass", "utility/void_net"]),
    "extras.png": (4, 1, ["utility/rain_totem", "utility/satchel", "items/hot_potato_book", "items/recombobulator"]),
    "armor.png": (5, 4, [f"armor/{tier}_{slot}" for slot in ("helmet", "chestplate", "leggings", "boots")
                         for tier in ("bronze", "rime", "ember", "void", "starcore")]),
    "menu.png": (4, 4, [f"menu/{name}" for name in ("island", "quests", "forge", "skills", "collections", "exchange",
                                                     "market", "bank", "minions", "bosses", "hunts", "crypt", "warps",
                                                     "settings", "wisps", "leaderboards")]),
}


def key_out(img):
    """Magenta (and the pink fringe around it) becomes transparent; the fringe loses its magenta tint."""
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            magenta = min(r, b) - g  # how much the pixel leans to magenta
            if magenta > 120 and r > 150 and b > 150:
                px[x, y] = (0, 0, 0, 0)
            elif magenta > 40:
                k = min(r, b) - (magenta - 40)
                px[x, y] = (min(r, k), g, min(b, k), int(a * (1 - min(1, (magenta - 40) / 80))))
    return img


def main_shape(img):
    """Keeps the item and drops what spills in from the neighbouring cells. The cell is cut with a margin (rows of a
    generated sheet are not evenly spaced), so the item is the big blob nearest the centre; the blobs of a fair size
    that do not touch the border stay with it (a sparkle next to the item, unless it is tiny)."""
    step = 4  # blobs are found on a quarter-size mask, which is plenty to tell them apart
    w, h = img.width // step, img.height // step
    mask = img.getchannel("A").resize((w, h), Image.BOX).point(lambda v: 1 if v > 96 else 0).load()
    seen, blobs = set(), []
    for y in range(h):
        for x in range(w):
            if not mask[x, y] or (x, y) in seen:
                continue
            stack, cells = [(x, y)], []
            seen.add((x, y))
            while stack:
                cx, cy = stack.pop()
                cells.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and mask[nx, ny] and (nx, ny) not in seen:
                        seen.add((nx, ny))
                        stack.append((nx, ny))
            border = any(cx in (0, w - 1) or cy in (0, h - 1) for cx, cy in cells)
            blobs.append((cells, border))
    if not blobs:
        return img
    biggest = max(len(c) for c, _ in blobs)

    def off_centre(cells):
        mx = sum(c[0] for c in cells) / len(cells) - w / 2
        my = sum(c[1] for c in cells) / len(cells) - h / 2
        return mx * mx + my * my

    item = min((c for c, _ in blobs if len(c) >= biggest * 0.25), key=off_centre)
    keep = set(item)
    for cells, border in blobs:
        if cells is not item and not border and len(cells) >= len(item) * 0.04:
            keep.update(cells)
    out = img.copy()
    px = out.load()
    for y in range(img.height):
        for x in range(img.width):
            if px[x, y][3] and (min(x // step, w - 1), min(y // step, h - 1)) not in keep:
                px[x, y] = (0, 0, 0, 0)
    return out


def trim(img):
    img = main_shape(img)
    alpha = img.getchannel("A").point(lambda v: 255 if v > 96 else 0)
    box = alpha.getbbox()
    if not box:
        return None
    img = img.crop(box)
    side = max(img.width, img.height)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return square


def pixelate(img):
    """Down to SIZE-2 (room for the outline), hard alpha, small palette."""
    inner = SIZE - 2
    small = img.resize((inner, inner), Image.BOX)
    alpha = small.getchannel("A").point(lambda v: 255 if v > 110 else 0)
    rgb = Image.new("RGB", small.size, (0, 0, 0))
    rgb.paste(small.convert("RGB"), mask=alpha)
    pal = rgb.quantize(colors=COLORS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    piece = pal.convert("RGBA")
    piece.putalpha(alpha)
    out.paste(piece, (1, 1))
    return out


def outline(img):
    """A dark one-pixel outline around the shape, tinted by the darkest colour of the item."""
    px = img.load()
    opaque = [(x, y) for y in range(SIZE) for x in range(SIZE) if px[x, y][3]]
    if not opaque:
        return img
    darkest = min((px[x, y] for x, y in opaque), key=lambda c: c[0] + c[1] + c[2])
    ink = tuple(int(v * 0.45) for v in darkest[:3]) + (255,)
    edge = []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and px[x + dx, y + dy][3] and px[x + dx, y + dy] != ink
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for x, y in edge:
        px[x, y] = ink
    return img


# Families shown as the flat sprite everywhere (held tools and weapons use the handheld pose, like vanilla). The
# machines and the Forge keep their 3D model in the world (they stand on blocks) and use the sprite in menus only.
FLAT = {"ore", "weapon", "tool", "armor", "utility", "menu"}
HANDHELD = {"weapon", "tool"}


def write(model_id, sprite):
    family, name = model_id.split("/")
    tex = PACK / "textures/item" / family / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    sprite.save(tex, optimize=True)
    model = PACK / "models/item" / family / f"{name}.json"
    parent = "minecraft:item/handheld" if family in HANDHELD else "minecraft:item/generated"
    if family in FLAT or not model.exists():
        model.parent.mkdir(parents=True, exist_ok=True)
        model.write_text(json.dumps({"parent": parent, "textures": {"layer0": f"vampyuyu:item/{model_id}"}}, indent=2) + "\n")
    item = PACK / "items" / family / f"{name}.json"
    if family in FLAT or not item.exists():
        item.parent.mkdir(parents=True, exist_ok=True)
        item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"vampyuyu:item/{model_id}"}},
                                   indent=1) + "\n")


def main():
    done = []
    for sheet, (cols, rows, ids) in SHEETS.items():
        path = CONCEPTS / sheet
        if not path.exists():
            print(f"skip {sheet}: not generated yet")
            continue
        img = key_out(Image.open(path))
        cw, ch = img.width / cols, img.height / rows
        for i, model_id in enumerate(ids):
            if model_id is None:
                continue
            col, row = i % cols, i // cols
            mx, my = cw * 0.2, ch * 0.2
            cell = img.crop((max(0, int(col * cw - mx)), max(0, int(row * ch - my)),
                             min(img.width, int((col + 1) * cw + mx)), min(img.height, int((row + 1) * ch + my))))
            shape = trim(cell)
            if shape is None:
                print(f"  {model_id}: empty cell, skipped")
                continue
            write(model_id, outline(pixelate(shape)))
            done.append(model_id)
    listed = set((ART / "imported.txt").read_text().split()) if (ART / "imported.txt").exists() else set()
    (ART / "imported.txt").write_text("\n".join(sorted(listed | set(done))) + "\n")
    print(f"imported {len(done)} sprites")
    if "--sheet" in sys.argv:
        contact(done, sys.argv[sys.argv.index("--sheet") + 1])


def contact(ids, out):
    """Every imported sprite at x8 on a grey checker, for review."""
    scale, per = 8, 6
    cell = SIZE * scale + 8
    sheet = Image.new("RGBA", (cell * per, cell * ((len(ids) + per - 1) // per)), (60, 60, 70, 255))
    for i, model_id in enumerate(ids):
        family, name = model_id.split("/")
        sprite = Image.open(PACK / "textures/item" / family / f"{name}.png").resize((SIZE * scale,) * 2, Image.NEAREST)
        sheet.alpha_composite(sprite, ((i % per) * cell + 4, (i // per) * cell + 4))
    sheet.save(out)
    print(f"contact sheet: {out}")


if __name__ == "__main__":
    main()
