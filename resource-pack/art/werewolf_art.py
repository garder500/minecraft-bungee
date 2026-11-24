#!/usr/bin/env python3
"""Werewolf role art of the base pack, from the renders in art/werewolf/skins_src/ (stdlib only, deterministic).

    python3 resource-pack/art/werewolf_art.py [--sheet DIR]

Per role (lower-case id = file name of roles/<ID>.json), skins_src holds card_<id>.png (256 bust) and medal_<id>.png
(256 head). This script writes:
  textures/font/card/<id>.png, <id>-dead.png   HUD card glyphs of the vampyuyu:hud font (128 px, shown CARD_HEIGHT GUI px
                                               high: the provider height keeps the display size of the old 64 px cards)
  font/hud.json                                the providers; font/card-names.txt is not needed: arena-plugin/src/main/
                                               resources/card-names.txt lists CARDS in glyph order (like icon-names.txt)
  textures/item/icon/card-<id>.png (+ model and item definition) for the roles that had no card item
  textures/font/icon/<icon>.png                the role medallions (64 px, the head render as it is; the six non-role
                                               icons alive, dead, moon, sun, vote, timer stay SVG, art/icons.py)
`back` keeps its painted card. layout.py still has the old 8-card list: CARDS and CARD_DEAD below are the truth.
"""
import json
import pathlib
import struct
import sys
import zlib

ART = pathlib.Path(__file__).resolve().parent
PACK = ART.parent / "pack" / "assets" / "vampyuyu"
SRC = ART / "werewolf" / "skins_src"
sys.path.insert(0, str(ART.parent))
sys.path.insert(0, str(ART))
import layout  # noqa: E402
from valoblock_models import read_png  # noqa: E402

CARD_PX = 128            # texture of a card glyph (displayed layout.CARD_HEIGHT GUI px high)
ITEM_PX = 64             # card as an item sprite
FIRST = ["back", "villager", "werewolf", "seer", "cupid", "hunter", "witch", "guard"]  # codepoints that never move
CARD_DEAD = 0x40         # dead glyph = alive glyph + this (41 cards do not fit layout.CARD_DEAD = 0x10)
CARDS_TXT = ART.parent.parent / "arena-plugin" / "src" / "main" / "resources" / "card-names.txt"


def role_ids():
    return [stem.lower() for stem in layout.roles()]


def cards():
    """Glyph order: the old eight, then every other role by id."""
    roles = role_ids()
    return FIRST + sorted(r for r in roles if r not in FIRST)


def icon_roles():
    """icon name -> role id, for every role's icon."""
    return {info["icon"]: stem.lower() for stem, info in layout.roles().items()}


def load(path):
    return read_png(path.read_bytes())


def box(rows, factor):
    """Box filter on premultiplied alpha, by an integer factor."""
    h, w = len(rows), len(rows[0])
    out, n = [], factor * factor
    for y in range(0, h, factor):
        line = []
        for x in range(0, w, factor):
            r = g = b = a = 0
            for yy in range(y, y + factor):
                row = rows[yy]
                for xx in range(x, x + factor):
                    pr, pg, pb, pa = row[xx]
                    r += pr * pa
                    g += pg * pa
                    b += pb * pa
                    a += pa
            if a == 0:
                line.append((0, 0, 0, 0))
            else:
                line.append((round(r / a), round(g / a), round(b / a), round(a / n)))
        out.append(line)
    return out


def dead(rows):
    """Darker, desaturated, a trace of red."""
    out = []
    for row in rows:
        line = []
        for r, g, b, a in row:
            v = (0.299 * r + 0.587 * g + 0.114 * b) * 0.55
            line.append((min(255, round(v * 1.18 + 7)), round(v * 0.94), round(v * 0.94), a))
        out.append(line)
    return out


def png(rows):
    h, w = len(rows), len(rows[0])
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for px in row:
            raw.extend(px if px[3] else (0, 0, 0, 0))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def check_sources():
    for role in role_ids():
        for prefix in ("card_", "medal_"):
            rows = load(SRC / f"{prefix}{role}.png")
            assert len(rows) == 256 and len(rows[0]) == 256, f"{prefix}{role}.png must be 256x256"


def write_cards():
    order = cards()
    glyphs = PACK / "textures" / "font" / "card"
    glyphs.mkdir(parents=True, exist_ok=True)
    providers = []

    def provider(name, code):
        providers.append({"type": "bitmap", "file": f"vampyuyu:font/card/{name}.png", "ascent": layout.CARD_ASCENT,
                          "height": layout.CARD_HEIGHT, "chars": [chr(layout.CARD_BASE + code)]})

    for i, role in enumerate(order):
        if role == "back":
            provider(role, i)
            continue
        alive = box(load(SRC / f"card_{role}.png"), 256 // CARD_PX)
        (glyphs / f"{role}.png").write_bytes(png(alive))
        (glyphs / f"{role}-dead.png").write_bytes(png(dead(alive)))
        provider(role, i)
        provider(f"{role}-dead", CARD_DEAD + i)
    providers.sort(key=lambda p: ord(p["chars"][0]))
    (PACK / "font").mkdir(parents=True, exist_ok=True)
    (PACK / "font" / "hud.json").write_text(json.dumps({"providers": providers}, indent=1) + "\n")
    CARDS_TXT.write_text("\n".join(order) + "\n")
    # card items (menus: "card-<role>"): only the roles that had no sprite; the old eight are skyblock_icon.py's
    for role in order:
        if role in FIRST:
            continue
        sprite = box(load(SRC / f"card_{role}.png"), 256 // ITEM_PX)
        name = f"card-{role}"
        for sub in ("textures/item/icon", "models/item/icon", "items/icon"):
            (PACK / sub).mkdir(parents=True, exist_ok=True)
        (PACK / "textures/item/icon" / f"{name}.png").write_bytes(png(sprite))
        (PACK / "models/item/icon" / f"{name}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")
        (PACK / "items/icon" / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/icon/{name}"}}, indent=2) + "\n")
    print("wrote", len(providers), "card glyphs and", len(order) - len(FIRST), "card items")


def medal_png(icon):
    """The 64 px medallion of a role icon, or None for the icons that are not a role's."""
    role = icon_roles().get(icon)
    if role is None:
        return None
    return png(box(load(SRC / f"medal_{role}.png"), 256 // layout.ICON_PX))


def write_medals():
    out = PACK / "textures" / "font" / "icon"
    out.mkdir(parents=True, exist_ok=True)
    count = 0
    for icon in layout.ICONS:
        data = medal_png(icon)
        if data:
            (out / f"{icon}.png").write_bytes(data)
            count += 1
    print("wrote", count, "role medallions")


def main():
    check_sources()
    write_cards()
    write_medals()


def sheet(directory):
    """Contact sheets at 2x: cards alive on dark and light, dead on dark, medallions on dark and light."""
    directory.mkdir(parents=True, exist_ok=True)
    order = [c for c in cards() if c != "back"]

    def compose(images, cols, size, bg, scale=2):
        cell = size * scale + 8
        rows_n = -(-len(images) // cols)
        W, H = cols * cell, rows_n * cell
        canvas = [[bg] * W for _ in range(H)]
        for n, (img, back) in enumerate(images):
            ox, oy = (n % cols) * cell + 4, (n // cols) * cell + 4
            for y, row in enumerate(img):
                for x, (r, g, b, a) in enumerate(row):
                    br, bg_, bb = back
                    px = (round((r * a + br * (255 - a)) / 255), round((g * a + bg_ * (255 - a)) / 255),
                          round((b * a + bb * (255 - a)) / 255), 255)
                    for dy in range(scale):
                        line = canvas[oy + y * scale + dy]
                        for dx in range(scale):
                            line[ox + x * scale + dx] = px
        return canvas

    dark, light = (28, 24, 40), (214, 208, 190)
    alive = [load(PACK / "textures/font/card" / f"{r}.png") for r in order]
    deads = [load(PACK / "textures/font/card" / f"{r}-dead.png") for r in order]
    (directory / "cards_alive.png").write_bytes(png(compose(
        [(img, dark if i % 8 < 4 else light) for i, img in enumerate(alive)], 8, CARD_PX, (0, 0, 0, 255))))
    (directory / "cards_dead.png").write_bytes(png(compose([(img, dark) for img in deads], 8, CARD_PX, (0, 0, 0, 255))))
    icons = [load(PACK / "textures/font/icon" / f"{n}.png") for n in layout.ICONS]
    (directory / "icons.png").write_bytes(png(compose(
        [(img, dark if (i // 8) % 2 == 0 else light) for i, img in enumerate(icons)], 8, layout.ICON_PX, (0, 0, 0, 255))))
    print("sheets in", directory)


if __name__ == "__main__":
    if "--sheet" in sys.argv:
        sheet(pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1]))
    else:
        main()
