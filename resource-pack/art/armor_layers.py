#!/usr/bin/env python3
"""The worn look of the five ladder armor sets, cut from their concept art and made to shine.

    nix-shell -p "python3.withPackages (p: [p.pillow])" --run "python3 resource-pack/art/armor_layers.py [--sheet out.png]"

A worn armor is two textures in the player-skin layout, humanoid/<tier>.png (chestplate with arms, boots) and
humanoid_leggings/<tier>.png. They are drawn at 4x (256x128) so the plates keep their detail. Every face is a piece of
the generated concept (concepts/armor.png: one helmet, chestplate, leggings and boots per tier): the chestplate's
middle on the chest, its shoulder pads on the arms, one leg of the leggings on each leg, one boot on the feet. The
result is then made to shine: more colour and contrast, a gold rim around each plate and a few sparkles on the
brightest spots. The helmet is a 3D model (helmet_models.py), so the head of the layer stays empty.
armor_tiers.py skips these files (imported.txt: equipment/<tier>).
"""
import pathlib
import random
import sys
import zlib

from PIL import Image, ImageEnhance

from import_textures import CONCEPTS, key_out, main_shape

ART = pathlib.Path(__file__).resolve().parent
ASSETS = ART.parent / "pack/assets/vampyuyu"
TIERS = ["bronze", "rime", "ember", "void", "starcore"]
SLOTS = ["helmet", "chestplate", "leggings", "boots"]
X = 4  # texture scale: 4x the vanilla 64x32 layout

# skin-layout boxes: name -> (u, v, width, height, depth) at 1x
BOXES = {"body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4), "leg": (0, 16, 4, 12, 4)}
GOLD, GOLD_LIGHT, GOLD_DARK = (236, 186, 64), (255, 236, 150), (150, 98, 24)


def faces(box):
    """The face rectangles (x0, y0, x1, y1, exclusive) of a box at X scale."""
    u, v, w, h, d = BOXES[box]
    r = {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
         "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
         "left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}
    return {k: tuple(c * X for c in rect) for k, rect in r.items()}


def concept_pieces():
    """tier -> slot -> the piece cut out of the concept sheet (RGBA, trimmed)."""
    sheet = key_out(Image.open(CONCEPTS / "armor.png"))
    cols, rows = 5, 4
    cw, ch = sheet.width / cols, sheet.height / rows
    out = {}
    for row, slot in enumerate(SLOTS):
        for col, tier in enumerate(TIERS):
            mx, my = cw * 0.2, ch * 0.2
            cell = sheet.crop((max(0, int(col * cw - mx)), max(0, int(row * ch - my)),
                               min(sheet.width, int((col + 1) * cw + mx)), min(sheet.height, int((row + 1) * ch + my))))
            shape = main_shape(cell)
            box = shape.getchannel("A").point(lambda v: 255 if v > 96 else 0).getbbox()
            out.setdefault(tier, {})[slot] = shape.crop(box)
    return out


def part(piece, fx0, fy0, fx1, fy1, size, darken=1.0, mirror=False):
    """A fraction of a piece, solid (its holes filled with its mean colour), scaled to `size`."""
    w, h = piece.size
    crop = piece.crop((int(w * fx0), int(h * fy0), max(int(w * fx0) + 1, int(w * fx1)), max(int(h * fy0) + 1, int(h * fy1))))
    opaque = [p for p in crop.getdata() if p[3] > 128] or [(128, 128, 128, 255)]
    mean = tuple(sum(p[i] for p in opaque) // len(opaque) for i in range(3)) + (255,)
    solid = Image.new("RGBA", crop.size, mean)
    solid.alpha_composite(crop)
    if mirror:
        solid = solid.transpose(Image.FLIP_LEFT_RIGHT)
    img = solid.resize(size, Image.LANCZOS)
    if darken != 1.0:
        img = ImageEnhance.Brightness(img).enhance(darken)
    return img


def place(canvas, rect, img):
    x0, y0, x1, y1 = rect
    canvas.alpha_composite(img.resize((x1 - x0, y1 - y0), Image.LANCZOS), (x0, y0))


def rim(canvas, rect, thickness=2):
    """A gold rim around a plate: light on the top edge, gold on the sides, dark gold on the bottom."""
    x0, y0, x1, y1 = rect
    px = canvas.load()
    for t in range(thickness):
        for x in range(x0, x1):
            px[x, y0 + t] = GOLD_LIGHT + (255,)
            px[x, y1 - 1 - t] = GOLD_DARK + (255,)
        for y in range(y0, y1):
            px[x0 + t, y] = GOLD + (255,)
            px[x1 - 1 - t, y] = GOLD + (255,)


def shine(canvas, tier):
    """More colour and contrast, and a few white-gold sparkles on the brightest pixels."""
    alpha = canvas.getchannel("A")
    rgb = canvas.convert("RGB")
    rgb = ImageEnhance.Color(rgb).enhance(1.35)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.2)
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    px = out.load()
    bright = sorted(((sum(px[x, y][:3]), x, y) for y in range(out.height) for x in range(out.width) if px[x, y][3]),
                    reverse=True)
    rnd = random.Random(zlib.crc32(tier.encode()))
    for _, x, y in rnd.sample(bright[:400], min(24, len(bright[:400]))):
        px[x, y] = (255, 252, 230, 255)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= x + dx < out.width and 0 <= y + dy < out.height and px[x + dx, y + dy][3]:
                px[x + dx, y + dy] = (255, 240, 180, 255)
    return out


def worn(pieces):
    """humanoid layer: the chestplate on the body and arms, the boots on the feet."""
    canvas = Image.new("RGBA", (64 * X, 32 * X), (0, 0, 0, 0))
    chest, boots = pieces["chestplate"], pieces["boots"]
    body = faces("body")
    place(canvas, body["front"], part(chest, 0.22, 0.12, 0.78, 1.0, (32, 48)))
    place(canvas, body["back"], part(chest, 0.22, 0.12, 0.78, 1.0, (32, 48), 0.78, mirror=True))
    place(canvas, body["right"], part(chest, 0.22, 0.2, 0.36, 1.0, (16, 48), 0.88))
    place(canvas, body["left"], part(chest, 0.64, 0.2, 0.78, 1.0, (16, 48), 0.88))
    place(canvas, body["top"], part(chest, 0.25, 0.0, 0.75, 0.2, (32, 16), 1.1))
    place(canvas, body["bottom"], part(chest, 0.25, 0.85, 0.75, 1.0, (32, 16), 0.7))
    arm = faces("arm")
    for face, (fx0, fx1, dark) in {"front": (0.0, 0.28, 1.0), "right": (0.0, 0.2, 0.9), "left": (0.08, 0.28, 0.9),
                                   "back": (0.0, 0.28, 0.78)}.items():
        x0, y0, x1, y1 = arm[face]
        split = y0 + (y1 - y0) * 5 // 12  # the shoulder pad over the upper arm, the sleeve below
        place(canvas, (x0, y0, x1, split), part(chest, fx0, 0.0, fx1, 0.45, (16, 20), dark))
        place(canvas, (x0, split, x1, y1), part(chest, 0.2, 0.45, 0.34, 1.0, (16, 28), dark * 0.92))
        rim(canvas, (x0, y0, x1, split))
    place(canvas, arm["top"], part(chest, 0.0, 0.0, 0.28, 0.2, (16, 16), 1.1))
    leg = faces("leg")
    for face, dark in {"front": 1.0, "right": 0.88, "left": 0.88, "back": 0.78}.items():  # boots: the last 4 rows
        x0, _, x1, y1 = leg[face]
        place(canvas, (x0, y1 - 4 * X, x1, y1), part(boots, 0.0, 0.15, 0.5, 1.0, (16, 16), dark, mirror=face == "back"))
        rim(canvas, (x0, y1 - 4 * X, x1, y1))
    place(canvas, leg["bottom"], part(boots, 0.05, 0.85, 0.45, 1.0, (16, 16), 0.7))
    for face in ("front", "back", "right", "left"):
        rim(canvas, body[face])
    return shine(canvas, "worn")


def leggings(pieces):
    """humanoid_leggings layer: the belt on the lower body, one leg of the leggings on each leg."""
    canvas = Image.new("RGBA", (64 * X, 32 * X), (0, 0, 0, 0))
    legs = pieces["leggings"]
    body = faces("body")
    for face, dark in {"front": 1.0, "back": 0.8, "right": 0.88, "left": 0.88}.items():
        x0, _, x1, y1 = body[face]
        belt = (x0, y1 - 4 * X, x1, y1)
        place(canvas, belt, part(legs, 0.1, 0.0, 0.9, 0.2, (x1 - x0, 4 * X), dark))
        rim(canvas, belt)
    leg = faces("leg")
    for face, (fx0, fx1, dark) in {"front": (0.08, 0.48, 1.0), "right": (0.04, 0.2, 0.88), "left": (0.32, 0.48, 0.88),
                                   "back": (0.08, 0.48, 0.78)}.items():
        x0, y0, x1, y1 = leg[face]
        plate = (x0, y0, x1, y1 - 3 * X)
        place(canvas, plate, part(legs, fx0, 0.12, fx1, 1.0, (16, 36), dark, mirror=face == "back"))
        rim(canvas, plate)
    place(canvas, leg["top"], part(legs, 0.1, 0.0, 0.45, 0.2, (16, 16), 1.1))
    return shine(canvas, "legs")


def main():
    cut = concept_pieces()
    for tier in TIERS:
        for folder, image in (("humanoid", worn(cut[tier])), ("humanoid_leggings", leggings(cut[tier]))):
            path = ASSETS / "textures/entity/equipment" / folder / f"{tier}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path, optimize=True)
    listed = ART / "imported.txt"
    have = set(listed.read_text().split()) if listed.exists() else set()
    listed.write_text("\n".join(sorted(have | {f"equipment/{t}" for t in TIERS})) + "\n")
    print(f"painted {2 * len(TIERS)} worn armor layers at {64 * X}x{32 * X}")
    if "--sheet" in sys.argv:
        sheet = Image.new("RGBA", (2 * 64 * X + 16, len(TIERS) * (32 * X + 8)), (50, 50, 60, 255))
        for i, tier in enumerate(TIERS):
            for j, folder in enumerate(("humanoid", "humanoid_leggings")):
                img = Image.open(ASSETS / "textures/entity/equipment" / folder / f"{tier}.png")
                sheet.alpha_composite(img, (j * (64 * X + 16), i * (32 * X + 8)))
        sheet.save(sys.argv[sys.argv.index("--sheet") + 1])


if __name__ == "__main__":
    main()
