#!/usr/bin/env python3
"""The worn look of the five ladder armor sets, cut from their concept art and made to shine.

    nix-shell -p "python3.withPackages (p: [p.pillow])" --run "python3 resource-pack/art/armor_layers.py [--sheet out.png]"

A worn armor is two textures in the player-skin layout, humanoid/<tier>.png (chestplate with arms, boots) and
humanoid_leggings/<tier>.png. They are drawn at 4x (256x128) so the plates keep their detail. Every face is a piece of
the generated concept (concepts/armor.png: one helmet, chestplate, leggings and boots per tier): the chestplate's
middle on the chest, its shoulder pads on the arms, one leg of the leggings on each leg, one boot on the feet. The
result is then made to shine: more colour and contrast, darker seams where pieces meet and a few sparkles on the
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


def solid(img):
    """The image over its own mean colour, so holes (background between the plates) never show."""
    opaque = [p for p in img.getdata() if p[3] > 128] or [(128, 128, 128, 255)]
    mean = tuple(sum(p[i] for p in opaque) // len(opaque) for i in range(3)) + (255,)
    out = Image.new("RGBA", img.size, mean)
    out.alpha_composite(img)
    return out


def fitted(piece, cx, cy, fh, aspect, size, darken=1.0):
    """A crop of the piece around (cx, cy) (fractions), fh of its height tall, with the face's aspect (w / h): the
    concept is never stretched, only scaled."""
    w, h = piece.size
    ch = max(2, int(h * fh))
    cw = max(2, int(ch * aspect))
    x0 = max(0, min(w - cw, int(w * cx - cw / 2)))
    y0 = max(0, min(h - ch, int(h * cy - ch / 2)))
    img = solid(piece.crop((x0, y0, x0 + cw, y0 + ch))).resize(size, Image.LANCZOS)
    return ImageEnhance.Brightness(img).enhance(darken) if darken != 1.0 else img


def patch(piece, cx, cy, size, darken=1.0):
    """Plain plate in the piece's colours for the faces without a picture (sides, back, sleeves): the mean colour
    of a small area at (cx, cy) with a light-to-dark sheen and a rivet at each corner."""
    w, h = piece.size
    side = max(4, int(min(w, h) * 0.2))
    x0 = max(0, min(w - side, int(w * cx - side / 2)))
    y0 = max(0, min(h - side, int(h * cy - side / 2)))
    opaque = [p for p in piece.crop((x0, y0, x0 + side, y0 + side)).getdata() if p[3] > 128] or [(128, 128, 128, 255)]
    mean = tuple(sum(p[i] for p in opaque) // len(opaque) for i in range(3))
    out = Image.new("RGBA", size)
    px = out.load()
    fw, fh = size
    for y in range(fh):
        for x in range(fw):
            t = y / max(1, fh - 1)
            k = 1.22 - 0.5 * t - 0.04 * (x / max(1, fw - 1))
            px[x, y] = tuple(max(0, min(255, int(v * k * darken))) for v in mean) + (255,)
    for rx, ry in ((X, X), (fw - 2 * X, X), (X, fh - 2 * X), (fw - 2 * X, fh - 2 * X)):
        for dx in range(X):
            for dy in range(X):
                if 0 <= rx + dx < fw and 0 <= ry + dy < fh:  # a rivet: a lighter dot of the same metal
                    px[rx + dx, ry + dy] = tuple(min(255, int(v * 1.35)) for v in mean) + (255,)
    return out


def backplate(chest):
    """The back of the chestplate, which the concept does not draw: the plates left of the front emblem, mirrored to
    the right, so the back is symmetric armour with a spine in the middle, a little darker than the front."""
    half = fitted(chest, 0.31, 0.55, 0.9, 4 / 12, (16, 48), SHADE["back"])
    out = Image.new("RGBA", (32, 48))
    out.alpha_composite(half, (0, 0))
    out.alpha_composite(half.transpose(Image.FLIP_LEFT_RIGHT), (16, 0))
    return out


def place(canvas, rect, img):
    x0, y0, x1, y1 = rect
    canvas.alpha_composite(img.resize((x1 - x0, y1 - y0), Image.LANCZOS), (x0, y0))


def band(canvas, x0, x1, y, thickness=X):
    """A seam across a face (shoulder pad, cuff, belt, boot top): one darker line of the plate under it, no trim."""
    px = canvas.load()
    for x in range(x0, x1):
        if 0 <= y < canvas.height and px[x, y][3]:
            px[x, y] = tuple(int(v * 0.55) for v in px[x, y][:3]) + (255,)


def shine(canvas, tier):
    """More colour and contrast, and a few white-gold sparkles on the brightest pixels."""
    alpha = canvas.getchannel("A")
    rgb = canvas.convert("RGB")
    rgb = ImageEnhance.Color(rgb).enhance(1.3)
    rgb = ImageEnhance.Contrast(rgb).enhance(1.15)
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    px = out.load()
    bright = sorted(((sum(px[x, y][:3]), x, y) for y in range(out.height) for x in range(out.width) if px[x, y][3]),
                    reverse=True)
    rnd = random.Random(zlib.crc32(tier.encode()))
    for _, x, y in rnd.sample(bright[:300], min(14, len(bright[:300]))):
        px[x, y] = (255, 252, 230, 255)
    return out


SIDES = ("front", "right", "left", "back")
SHADE = {"front": 1.0, "right": 0.88, "left": 0.88, "back": 0.8}


def worn(pieces):
    """humanoid layer: the chestplate on the body and arms, the boots on the feet."""
    canvas = Image.new("RGBA", (64 * X, 32 * X), (0, 0, 0, 0))
    chest, boots = pieces["chestplate"], pieces["boots"]
    body = faces("body")
    # the chest picture (with its emblem) on the front only, at the 8x12 face's own shape
    place(canvas, body["front"], fitted(chest, 0.5, 0.55, 0.9, 8 / 12, (32, 48)))
    for face in ("right", "left"):
        x0, y0, x1, y1 = body[face]
        place(canvas, body[face], patch(chest, 0.5, 0.72, (x1 - x0, y1 - y0), SHADE[face]))
    place(canvas, body["back"], backplate(chest))
    place(canvas, body["top"], patch(chest, 0.5, 0.72, (32, 16), 1.1))
    place(canvas, body["bottom"], patch(chest, 0.5, 0.72, (32, 16), 0.7))
    arm = faces("arm")
    for face in SIDES:
        x0, y0, x1, y1 = arm[face]
        pad = y0 + 5 * X  # shoulder pad over the upper arm, plain sleeve below, a gold cuff at the wrist
        place(canvas, (x0, y0, x1, pad), fitted(chest, 0.14, 0.18, 0.34, 4 / 5, (16, 20), SHADE[face]))
        place(canvas, (x0, pad, x1, y1), patch(chest, 0.5, 0.72, (x1 - x0, y1 - pad), SHADE[face] * 0.92))
        band(canvas, x0, x1, pad - X // 2)
        band(canvas, x0, x1, y1 - 2 * X, 2 * X)
    place(canvas, arm["top"], fitted(chest, 0.14, 0.12, 0.24, 1.0, (16, 16), 1.1))
    leg = faces("leg")
    for face in SIDES:  # boots: the last 4 rows of the leg
        x0, _, x1, y1 = leg[face]
        top = y1 - 4 * X
        img = fitted(boots, 0.27, 0.6, 0.8, 1.0, (16, 16), SHADE[face]) if face in ("front", "back") \
            else patch(boots, 0.27, 0.6, (16, 16), SHADE[face])
        place(canvas, (x0, top, x1, y1), img)
        band(canvas, x0, x1, top)
    place(canvas, leg["bottom"], patch(boots, 0.27, 0.6, (16, 16), 0.7))
    return shine(canvas, "worn")


def leggings(pieces):
    """humanoid_leggings layer: the belt on the lower body, one leg of the leggings on each leg."""
    canvas = Image.new("RGBA", (64 * X, 32 * X), (0, 0, 0, 0))
    legs = pieces["leggings"]
    body = faces("body")
    for face in SIDES:
        x0, _, x1, y1 = body[face]
        belt = (x0, y1 - 3 * X, x1, y1)
        place(canvas, belt, patch(legs, 0.5, 0.1, (x1 - x0, 3 * X), SHADE[face]))
        band(canvas, x0, x1, y1 - 3 * X)
        band(canvas, x0, x1, y1 - X)
    leg = faces("leg")
    for face in SIDES:
        x0, y0, x1, y1 = leg[face]
        plate = (x0, y0, x1, y1 - 3 * X)  # down to the boots
        img = fitted(legs, 0.27, 0.58, 0.82, 4 / 9, (16, 36), SHADE[face]) if face in ("front", "back") \
            else patch(legs, 0.27, 0.45, (16, 36), SHADE[face])
        place(canvas, plate, img)
    place(canvas, leg["top"], patch(legs, 0.27, 0.2, (16, 16), 1.1))
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
