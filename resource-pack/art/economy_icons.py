#!/usr/bin/env python3
"""Draws the 16x16 Skyblock economy menu icons (stdlib only, deterministic) and their pack definitions.

    python3 resource-pack/art/economy_icons.py [--sheet out.png]

Writes, for every icon NAME below:
  assets/vampyuyu/textures/item/economy/NAME.png
  assets/vampyuyu/models/item/economy/NAME.json
  assets/vampyuyu/items/economy/NAME.json     (item model "vampyuyu:economy/NAME")
`--sheet` also renders an 8x contact sheet for a quick look. Output is byte for byte reproducible.
"""
import json, pathlib, struct, sys, zlib

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"
OUTLINE = (27, 18, 9, 255)

def c(r, g, b, a=255):
    return (r, g, b, a)

GOLD, GOLD_D, GOLD_L = c(255, 215, 64), c(214, 150, 24), c(255, 243, 160)
GREEN, GREEN_D = c(84, 214, 92), c(40, 140, 52)
RED, RED_D = c(235, 84, 70), c(160, 40, 40)
ORANGE = c(250, 150, 40)
PAPER, PAPER_D = c(244, 232, 200), c(200, 180, 140)
INK = c(92, 64, 36)
STONE, STONE_D = c(206, 206, 216), c(140, 140, 158)
WOOD, WOOD_D = c(166, 112, 60), c(110, 70, 36)
PURPLE, PURPLE_D = c(190, 120, 255), c(120, 60, 200)
BLUE, BLUE_D = c(100, 180, 255), c(50, 110, 200)
WHITE = c(255, 255, 255)


class Canvas:
    def __init__(self):
        self.p = [[None] * 16 for _ in range(16)]

    def px(self, x, y, col):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[y][x] = col

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, col)

    def line(self, x0, y0, x1, y1, col):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, col)
            if x0 == x1 and y0 == y1:
                return
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r2, col):
        """Filled disc; r2 is the squared radius (use .5 steps for odd sizes)."""
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r2:
                    self.px(x, y, col)

    def ring(self, cx, cy, r2_out, r2_in, col):
        for y in range(16):
            for x in range(16):
                d = (x - cx) ** 2 + (y - cy) ** 2
                if r2_in < d <= r2_out:
                    self.px(x, y, col)

    def outline(self):
        out = [row[:] for row in self.p]
        for y in range(16):
            for x in range(16):
                if self.p[y][x] is None:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < 16 and 0 <= ny < 16 and self.p[ny][nx] is not None:
                            out[y][x] = OUTLINE
                            break
        self.p = out

    def png(self):
        raw = bytearray()
        for row in self.p:
            raw.append(0)
            for px in row:
                raw.extend(px if px else (0, 0, 0, 0))
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def coin(cv, cx, cy, big=True):
    if big:
        cv.disc(cx, cy, 20, GOLD_D)
        cv.disc(cx, cy, 14, GOLD)
        cv.ring(cx, cy, 6, 1, GOLD_D)
        cv.px(cx - 3, cy - 3, GOLD_L); cv.px(cx - 2, cy - 3, GOLD_L); cv.px(cx - 3, cy - 2, GOLD_L)
    else:
        cv.disc(cx, cy, 7, GOLD_D)
        cv.disc(cx, cy, 3, GOLD)
        cv.px(cx - 1, cy - 1, GOLD_L)


def arrow_down(cv, cx, y, col, dark):
    cv.rect(cx - 1, y, cx, y + 3, col)
    cv.rect(cx - 3, y + 4, cx + 2, y + 4, col)
    cv.rect(cx - 2, y + 5, cx + 1, y + 5, col)
    cv.rect(cx - 1, y + 6, cx, y + 6, col)
    cv.px(cx, y + 6, dark); cv.px(cx, y + 5, dark)


def arrow_up(cv, cx, y, col, dark):
    cv.rect(cx - 1, y + 3, cx, y + 6, col)
    cv.rect(cx - 3, y + 2, cx + 2, y + 2, col)
    cv.rect(cx - 2, y + 1, cx + 1, y + 1, col)
    cv.rect(cx - 1, y, cx, y, col)
    cv.px(cx, y + 3, dark); cv.px(cx, y + 4, dark)


def scroll(cv):
    cv.rect(3, 2, 12, 13, PAPER)
    cv.rect(3, 2, 12, 2, PAPER_D); cv.rect(3, 13, 12, 13, PAPER_D)
    cv.rect(12, 3, 12, 12, PAPER_D)
    cv.rect(5, 5, 9, 5, INK); cv.rect(5, 7, 8, 7, INK)


def draw(name):
    cv = Canvas()
    if name == "coin":
        coin(cv, 8, 8)
    elif name == "bank":
        for i, (x0, x1) in enumerate(((7, 8), (6, 9), (5, 10), (4, 11), (3, 12))):
            cv.rect(x0, 2 + i, x1, 2 + i, STONE)
        cv.rect(7, 3, 8, 4, GOLD)
        cv.rect(2, 7, 13, 7, STONE_D)
        for x in (3, 7, 11):
            cv.rect(x, 8, x + 1, 11, STONE)
            cv.rect(x + 1, 8, x + 1, 11, STONE_D)
        cv.rect(1, 12, 14, 13, STONE_D)
        cv.rect(1, 12, 14, 12, STONE)
    elif name == "deposit":
        cv.rect(2, 10, 13, 14, WOOD); cv.rect(2, 10, 13, 10, WOOD_D); cv.rect(2, 14, 13, 14, WOOD_D)
        cv.rect(7, 11, 8, 12, GOLD)
        arrow_down(cv, 8, 2, GREEN, GREEN_D)
        cv.disc(8, 2, 1, GOLD)
    elif name == "withdraw":
        cv.rect(2, 10, 13, 14, WOOD); cv.rect(2, 10, 13, 10, WOOD_D); cv.rect(2, 14, 13, 14, WOOD_D)
        cv.rect(7, 11, 8, 12, GOLD)
        arrow_up(cv, 8, 1, ORANGE, RED_D)
    elif name == "upgrade":
        cv.rect(6, 8, 9, 13, PURPLE); cv.rect(9, 8, 9, 13, PURPLE_D)
        for i, (x0, x1) in enumerate(((7, 8), (6, 9), (5, 10), (4, 11), (3, 12))):
            cv.rect(x0, 2 + i, x1, 2 + i, PURPLE)
        cv.px(8, 3, PURPLE_D); cv.px(8, 4, PURPLE_D)
        cv.rect(4, 14, 11, 14, GOLD)
    elif name == "history":
        scroll(cv)
        cv.rect(5, 9, 10, 9, INK); cv.rect(5, 11, 7, 11, INK)
        coin(cv, 11, 11, big=False)
    elif name == "buyback":
        cv.ring(8, 8, 22, 9, BLUE)
        cv.rect(9, 2, 14, 2, BLUE); cv.rect(10, 3, 13, 3, BLUE); cv.rect(11, 4, 12, 4, BLUE)
        coin(cv, 8, 8, big=False)
    elif name == "sell_all":
        cv.rect(2, 3, 13, 4, STONE); cv.rect(3, 5, 12, 6, STONE_D)
        cv.rect(4, 7, 11, 8, STONE_D); cv.rect(6, 9, 9, 11, STONE)
        cv.rect(7, 11, 8, 13, STONE_D)
        coin(cv, 8, 14, big=False)
    elif name == "bazaar":
        for i, x in enumerate(range(2, 14, 2)):
            col = RED if i % 2 == 0 else WHITE
            cv.rect(x, 3, x + 1, 6, col)
        cv.rect(2, 7, 13, 7, RED_D)
        cv.rect(3, 8, 3, 13, WOOD_D); cv.rect(12, 8, 12, 13, WOOD_D)
        cv.rect(2, 10, 13, 11, WOOD); cv.rect(2, 10, 13, 10, WOOD_D)
        coin(cv, 8, 8, big=False)
    elif name == "buy_now":
        coin(cv, 8, 5, big=False)
        arrow_down(cv, 8, 7, GREEN, GREEN_D)
        cv.rect(3, 14, 12, 14, GOLD_D)
    elif name == "sell_now":
        arrow_up(cv, 8, 1, ORANGE, RED_D)
        coin(cv, 8, 11, big=False)
        cv.rect(3, 14, 12, 14, GOLD_D)
    elif name == "buy_order":
        scroll(cv)
        cv.rect(5, 9, 10, 9, GREEN); cv.rect(7, 8, 8, 10, GREEN)
    elif name == "sell_order":
        scroll(cv)
        cv.rect(5, 9, 10, 10, ORANGE)
    elif name == "orders":
        cv.rect(2, 4, 10, 13, PAPER_D); cv.rect(4, 2, 12, 11, PAPER)
        cv.rect(6, 5, 10, 5, INK); cv.rect(6, 7, 9, 7, INK)
        cv.line(13, 7, 8, 13, WOOD); cv.line(13, 8, 9, 13, WOOD_D)
        cv.px(8, 13, INK)
    elif name == "auction":
        cv.rect(2, 12, 9, 13, WOOD_D)
        cv.rect(2, 3, 8, 6, STONE_D); cv.rect(2, 3, 8, 3, STONE)
        cv.line(9, 6, 5, 10, WOOD); cv.line(9, 7, 6, 10, WOOD_D)
        cv.rect(9, 8, 13, 9, GOLD_D); cv.rect(10, 7, 13, 7, GOLD)
        cv.rect(11, 12, 13, 13, GOLD)
    elif name == "browse":
        cv.ring(6, 6, 20, 8, STONE)
        cv.disc(6, 6, 8, BLUE)
        cv.px(4, 4, WHITE); cv.px(5, 4, WHITE)
        cv.line(10, 10, 13, 13, WOOD); cv.line(11, 10, 13, 12, WOOD_D)
    elif name == "create":
        cv.rect(2, 4, 10, 13, PAPER); cv.rect(2, 4, 10, 4, PAPER_D)
        cv.rect(4, 7, 8, 7, INK); cv.rect(4, 9, 7, 9, INK)
        cv.rect(11, 2, 12, 8, GREEN); cv.rect(9, 4, 14, 5, GREEN)
    elif name == "my_auctions":
        cv.rect(3, 2, 12, 13, PURPLE_D); cv.rect(4, 3, 12, 12, PAPER)
        cv.rect(3, 2, 3, 13, PURPLE)
        cv.rect(6, 5, 10, 5, INK); cv.rect(6, 7, 9, 7, INK)
        coin(cv, 9, 10, big=False)
    elif name == "buy_it_now":
        cv.rect(2, 4, 10, 12, GREEN); cv.rect(3, 3, 9, 3, GREEN); cv.rect(11, 5, 11, 11, GREEN_D)
        cv.rect(2, 4, 10, 4, GREEN_D)
        cv.px(4, 6, PAPER); cv.px(5, 6, PAPER)
        coin(cv, 7, 9, big=False)
        cv.line(11, 3, 13, 1, INK)
    elif name == "bid":
        cv.disc(7, 5, 12, WOOD)
        cv.ring(7, 5, 12, 7, WOOD_D)
        cv.rect(7, 9, 8, 14, WOOD_D)
        cv.rect(5, 4, 9, 4, GOLD_L); cv.rect(6, 3, 8, 3, GOLD_L); cv.rect(6, 5, 8, 5, GOLD_L)
    elif name == "claim":
        cv.rect(2, 8, 13, 13, WOOD); cv.rect(2, 8, 13, 8, WOOD_D)
        cv.rect(2, 4, 13, 7, WOOD_D); cv.rect(3, 5, 12, 7, GOLD_D)
        cv.rect(7, 9, 8, 11, GOLD)
        cv.px(2, 1, GOLD_L); cv.px(8, 0, GOLD_L); cv.px(14, 2, GOLD_L); cv.px(5, 2, GOLD_L); cv.px(11, 1, GOLD_L)
    elif name == "confirm":
        cv.disc(7.5, 7.5, 31, GREEN_D)
        cv.disc(7.5, 7.5, 21, GREEN)
        cv.line(4, 8, 7, 11, WHITE); cv.line(5, 8, 7, 10, WHITE); cv.line(7, 11, 12, 5, WHITE); cv.line(7, 10, 11, 5, WHITE)
    else:
        raise SystemExit("no drawing for " + name)
    cv.outline()
    return cv


NAMES = ["coin", "bank", "deposit", "withdraw", "upgrade", "history", "buyback", "sell_all", "bazaar", "buy_now",
         "sell_now", "buy_order", "sell_order", "orders", "auction", "browse", "create", "my_auctions", "buy_it_now",
         "bid", "claim", "confirm"]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode) as f:
        f.write(data)


def main():
    for name in NAMES:
        write(PACK / "textures/item/economy" / f"{name}.png", draw(name).png())
        write(PACK / "models/item/economy" / f"{name}.json", json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/economy/{name}"}}, indent=2) + "\n")
        write(PACK / "items/economy" / f"{name}.json", json.dumps(
            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/economy/{name}"}}, indent=2) + "\n")
    if "--sheet" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1])
        cols, scale = 6, 8
        rows = (len(NAMES) + cols - 1) // cols
        w, h = cols * 18 * scale, rows * 18 * scale
        raw = bytearray()
        for y in range(h):
            raw.append(0)
            for x in range(w):
                cell = (x // scale) // 18 + ((y // scale) // 18) * cols
                lx, ly = (x // scale) % 18 - 1, (y // scale) % 18 - 1
                px = None
                if cell < len(NAMES) and 0 <= lx < 16 and 0 <= ly < 16:
                    px = draw(NAMES[cell]).p[ly][lx]
                raw.extend(px if px else (60, 60, 70, 255))
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        out.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    main()
