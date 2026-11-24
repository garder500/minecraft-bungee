#!/usr/bin/env python3
"""Paints the 32x32 Skyblock economy menu icons (stdlib only, deterministic) and their pack definitions.

    python3 resource-pack/art/economy_icons.py [--sheet out.png]

Writes, for every icon NAME below:
  assets/vampyuyu/textures/item/economy/NAME.png
  assets/vampyuyu/models/item/economy/NAME.json
  assets/vampyuyu/items/economy/NAME.json     (item model "vampyuyu:economy/NAME")
`--sheet` renders a contact sheet instead. Output is byte for byte reproducible.
Painted with sb_paint in the style of the imported item/menu icons: gold, wood, parchment and stone ramps, light from
the top left. Green means in (buy, deposit), orange means out (sell, withdraw), a bolt means instant.
"""
import json, pathlib, sys

from sb_paint import (Art, INK, GOLD, WOOD, DARKWOOD, STONE, IRON, STEEL, LEAF, RED, WHITE, PAPER, GLASS, LEATHER, CLOTH,
                      ramp, tones, rect, rrect, ell, ring, poly, line, path, pts, shift, edge, ball, grad, vcyl, hcyl, sheet)
from sb_things import GREEN, ORANGE, TEAL, coin, arrow, harrow, scroll, chest, check, plus, bolt, gavel, magnifier

PACK = pathlib.Path(__file__).resolve().parent.parent / "pack" / "assets" / "vampyuyu"
MARBLE = tones("5a5a84", "9498bc", "c8cce2", "eceef6", "ffffff")


def strongbox(a, y0=14):
    """The bank's iron-bound coffer, seen from the front."""
    a.fill(rrect(3, y0, 28, 29, 1), STEEL, grad(0, y0, 0, 29, 0.8, 0.3), rim=0.18)
    a.fill(rect(3, y0, 28, y0 + 2), IRON, 0.75, rim=0.15)
    a.fill(rect(3, 27, 28, 29), IRON, 0.4, rim=0.15)
    for x in (6, 24):
        a.put(pts(x, y0 + 5, x, 25), IRON[4])
    a.fill(ell(15.5, y0 + 8.5, 4, 4), GOLD, ball(15.5, y0 + 8.5, 4.4))
    a.put(rect(15, y0 + 7, 16, y0 + 10), INK)
    return a


def bag(a, cx=15.5, mat=LEATHER):
    a.fill(poly((cx - 5, 9), (cx - 7, 3), (cx - 2, 5), (cx, 2), (cx + 2, 5), (cx + 7, 3), (cx + 5, 9)), mat, grad(cx - 7, 2, cx + 7, 9, 0.9, 0.35))
    a.fill(ell(cx, 19.5, 12, 10), mat, ball(cx, 19.5, 12.5, 10.5))
    a.fill(rect(round(cx - 5), 9, round(cx + 5), 10), GOLD, grad(cx - 5, 0, cx + 5, 0, 0.95, 0.4))
    return a


def draw(name):
    a = Art()
    if name == "coin":
        coin(a, 15, 15, 13)
    elif name == "bank":
        a.fill(rect(1, 27, 30, 29), MARBLE, 0.5, rim=0.2)
        a.fill(rect(3, 24, 28, 26), MARBLE, 0.65, rim=0.2)
        for x in (5, 11, 18, 24):
            a.fill(rect(x, 13, x + 2, 23), MARBLE, vcyl(x, x + 2, 0.98, 0.35))
        a.fill(rect(3, 11, 28, 12), MARBLE, 0.8, rim=0.15)
        a.fill(poly((2, 10), (15.5, 1), (29, 10)), MARBLE, grad(4, 2, 26, 11, 0.98, 0.45))
        coin(a, 15.5, 6.5, 2.6, emblem=False)
        a.fill(rect(14, 16, 17, 23), DARKWOOD, 0.4)
    elif name in ("deposit", "withdraw"):
        strongbox(a, 15)
        if name == "deposit":
            coin(a, 22, 6, 5, emblem=False)
            arrow(a, 9, 1, 13, GREEN)
        else:
            coin(a, 22, 7, 5, emblem=False)
            arrow(a, 9, 13, 1, ORANGE)
    elif name == "upgrade":
        strongbox(a, 17)
        for y in (2, 8):
            a.fill(path([(6, y + 6), (15.5, y), (25, y + 6)], 3.2), GOLD, grad(6, y, 25, y + 7, 1.0, 0.4))
    elif name == "history":
        scroll(a, 2, 2, 22, 29)
        a.fill(ell(21.5, 21.5, 8.5, 8.5), GOLD, ball(21.5, 21.5, 9))
        a.fill(ell(21.5, 21.5, 6.2, 6.2), MARBLE, 0.85)
        a.put(line(21, 21, 21, 17) | line(21, 21, 24, 23), INK)
    elif name == "buyback":
        coin(a, 15.5, 15.5, 8, emblem=True)
        arc = (ring(15.5, 15.5, 14, 14, 3) - poly((15.5, 15.5), (33, 4), (33, 20))) - poly((15.5, 15.5), (0, 34), (9, 34))
        a.fill(arc, TEAL, ball(15.5, 15.5, 15))
        a.fill(poly((22, 1), (30, 6), (22, 11)), TEAL, grad(22, 1, 30, 11, 0.95, 0.4))
    elif name == "sell_all":
        bag(a, 13)
        coin(a, 13, 20, 5, emblem=False)
        for y in (26, 22, 18):
            a.fill(ell(25, y + 1, 5.5, 2.6), GOLD, 0.25)
            a.fill(ell(25, y, 5.5, 2.6), GOLD, grad(19, y - 2, 31, y + 2, 0.98, 0.5), ink=False)
    elif name == "bazaar":
        a.fill(rect(3, 13, 5, 29) | rect(26, 13, 28, 29), WOOD, vcyl(3, 5))
        a.fill(rect(26, 13, 28, 29), WOOD, vcyl(26, 28), ink=False)
        a.fill(rect(2, 21, 29, 29), WOOD, grad(0, 21, 0, 29, 0.75, 0.3), rim=0.18)
        a.tone(rect(3, 25, 28, 25), -1)
        for cx, mat in ((9, RED), (15.5, GOLD), (22, LEAF)):
            a.fill(ell(cx, 18.5, 3, 3), mat, ball(cx, 18.5, 3.3))
        awning = poly((3, 3), (28, 3), (30, 12), (1, 12))
        a.fill(awning, RED, grad(0, 3, 0, 12, 0.9, 0.4))
        for i, x0 in enumerate(range(1, 31, 5)):
            if i % 2:
                a.fill(rect(x0, 3, x0 + 4, 12) & awning, WHITE, grad(0, 3, 0, 12, 0.98, 0.55), ink=False)
        for x0 in range(1, 31, 5):
            a.fill(ell(x0 + 2, 12.5, 2.4, 2), RED if (x0 // 5) % 2 == 0 else WHITE, 0.5, ink=False)
    elif name in ("buy_now", "sell_now"):
        if name == "buy_now":
            a.fill(ring(15.5, 13, 9, 9, 2) & rect(0, 0, 31, 13), WOOD, grad(6, 4, 25, 4, 0.9, 0.4), ink=False)
            basket = poly((2, 13), (29, 13), (26, 29), (5, 29))
            a.fill(basket, ramp("c89a4a"), grad(2, 13, 29, 29, 0.95, 0.25))
            for y in (17, 21, 25):
                a.tone(rect(3, y, 28, y) & basket, -1)
            for x in (9, 15, 21):
                a.tone(rect(x, 14, x, 28) & basket, -1)
            a.fill(rect(1, 12, 30, 14), ramp("c89a4a"), 0.8, rim=0.15)
        else:
            for y in (25, 20, 15):
                a.fill(ell(13, y + 2, 10.5, 4), GOLD, 0.2)
                a.fill(ell(13, y, 10.5, 4), GOLD, grad(3, y - 3, 23, y + 3, 0.98, 0.5), ink=False)
                a.tone(ring(13, y, 7.5, 2.6, 1), -1)
        bolt(a, 22, 1, 1.0, GREEN if name == "buy_now" else ORANGE)
    elif name in ("buy_order", "sell_order", "create"):
        scroll(a, 2, 2, 22, 29)
        if name == "buy_order":
            arrow(a, 23, 12, 29, GREEN)
            coin(a, 24, 6, 4.5, emblem=False)
        elif name == "sell_order":
            arrow(a, 23, 29, 12, ORANGE)
            coin(a, 24, 6, 4.5, emblem=False)
        else:
            plus(a, 22, 22, 6, GREEN)
    elif name == "orders":
        a.fill(rrect(3, 3, 28, 30, 2), WOOD, None)
        a.fill(rect(6, 7, 25, 28), PAPER, grad(6, 7, 25, 28, 0.9, 0.55))
        a.fill(rrect(10, 1, 21, 6, 1), IRON, None)
        for y, mat in ((11, GREEN), (17, GREEN), (23, ORANGE)):
            a.fill(rect(8, y, 10, y + 2), mat, 0.6, ink=False)
            a.put(rect(13, y + 1, 23 - (y % 3), y + 1), (106, 86, 80))
    elif name == "auction":
        a.fill(rrect(3, 24, 20, 29, 1), DARKWOOD, None)
        a.fill(rect(5, 22, 18, 24), WOOD, 0.7, rim=0.15)
        gavel(a)
    elif name == "browse":
        magnifier(a)
    elif name == "my_auctions":
        tag = poly((3, 13), (13, 3), (28, 3), (28, 18), (18, 28), (13, 28), (3, 18))
        a.fill(tag, PAPER, grad(3, 3, 28, 28, 0.95, 0.45), rim=0.14)
        a.fill(ring(23.5, 7.5, 2.6, 2.6, 1.2), IRON, 0.5, ink=False)
        star = poly((14, 9), (16, 14), (21, 14), (17, 17), (19, 23), (14, 19), (9, 23), (11, 17), (7, 14), (12, 14))
        a.fill(star, GOLD, grad(7, 9, 21, 23, 1.0, 0.4))
    elif name == "buy_it_now":
        coin(a, 13, 17, 11.5, emblem=False)
        bolt(a, 20, 1, 1.25, GREEN)
    elif name == "bid":
        a.fill(line(15.5, 20, 15.5, 29, 3.4), WOOD, vcyl(13, 18))
        a.fill(ell(15.5, 12, 12, 11), ramp("c8483a"), ball(15.5, 12, 12.5, 11.5))
        a.fill(ell(15.5, 12, 9, 8), PAPER, grad(7, 4, 24, 20, 0.95, 0.6), ink=True)
        coin(a, 15.5, 12, 5, emblem=True)
    elif name == "claim":
        chest(a, 2, 5, 29, 29, opened=True)
        for cx, cy in ((8, 11), (23, 11), (15.5, 9), (11, 13), (20, 13)):
            coin(a, cx, cy, 3.4, emblem=False)
        a.put(pts(5, 3, 26, 2, 15, 1), (255, 250, 214))
    elif name == "confirm":
        a.fill(ell(15.5, 15.5, 14, 14), GREEN, ball(15.5, 15.5, 14.5))
        a.tone(ring(15.5, 15.5, 11.5, 11.5, 1), -1)
        check(a, 13, 22, WHITE, 3.6, 0.95)
    return a.outline()


NAMES = ["coin", "bank", "deposit", "withdraw", "upgrade", "history", "buyback", "sell_all", "bazaar", "buy_now",
         "sell_now", "buy_order", "sell_order", "orders", "auction", "browse", "create", "my_auctions", "buy_it_now",
         "bid", "claim", "confirm"]


def write(path_, data):
    path_.parent.mkdir(parents=True, exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path_, mode) as f:
        f.write(data)


def main():
    if "--sheet" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1])
        out.write_bytes(sheet([draw(n).grid() for n in NAMES], cols=11, scale=6))
        return
    for name in NAMES:
        write(PACK / "textures/item/economy" / f"{name}.png", draw(name).png())
        write(PACK / "models/item/economy" / f"{name}.json", json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"vampyuyu:item/economy/{name}"}}, indent=2) + "\n")
        write(PACK / "items/economy" / f"{name}.json", json.dumps(
            {"model": {"type": "minecraft:model", "model": f"vampyuyu:item/economy/{name}"}}, indent=2) + "\n")
    print(f"wrote {len(NAMES)} economy icons")


if __name__ == "__main__":
    main()
