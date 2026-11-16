#!/usr/bin/env python3
"""ValoBlock agent select sprites (stdlib only, reuses the PNG encoder of valoblock_items.py).

The single source is arena-plugin/src/main/resources/valoblock/agents/*.json: each agent has a face
(8x8 palette letters, the colour plan of the design's VB-Agents sheet). Per agent three 32x32 items are
written: agent_<id> (the face on its role tint), agent_<id>_dim (35 % alpha: taken or filtered out) and
agent_<id>_ring (a yellow frame: the current pick). Also agent_empty (a grey orb: still choosing),
role_<name> (a coloured square per role), lock_in (green) and random (grey orb with a pip).
Pure art, no lettering: every word is a window title or an item tooltip.

    models/item/valoblock/<name>.json, items/valoblock/<name>.json, textures/item/valoblock/<name>.png
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import valoblock_items as vi  # noqa: E402

AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"
SIZE = 32
FACE = 24  # 8 pixels x 3
DIM_ALPHA = 0.35
RING = (0xFF, 0xE9, 0x4A)


def tint(hex6, a=1.0):
    r, g, b = vi.rgb(hex6.lstrip("#"))
    return (r, g, b, round(255 * a))


def blank():
    return [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]


def face_sprite(agent, alpha=1.0, ring=False):
    img = blank()
    bg = tint(agent["menuColor"], alpha)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = bg
    off = (SIZE - FACE) // 2
    pal = agent["face"]["palette"]
    for y, row in enumerate(agent["face"]["rows"]):
        for x, ch in enumerate(row):
            px = tint(pal[ch], alpha)
            for dy in range(3):
                for dx in range(3):
                    img[off + y * 3 + dy][off + x * 3 + dx] = px
    if ring:
        for i in range(SIZE):
            for t in (0, 1):
                for (yy, xx) in ((t, i), (SIZE - 1 - t, i), (i, t), (i, SIZE - 1 - t)):
                    img[yy][xx] = (*RING, 255)
    return img


def orb(color, radius=8, pip=None):
    img = blank()
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            if (x - c) ** 2 + (y - c) ** 2 <= radius * radius:
                img[y][x] = tint(color)
    if pip:
        for y in range(14, 18):
            for x in range(14, 18):
                img[y][x] = tint(pip)
    return img


def square(color, inset=4):
    img = blank()
    for y in range(inset, SIZE - inset):
        for x in range(inset, SIZE - inset):
            img[y][x] = tint(color)
    return img


def agents():
    out = []
    for f in sorted(AGENTS.glob("*.json")):
        if f.name == "index.json":
            continue
        out.append(json.loads(f.read_text()))
    return out


def main():
    n = 0
    for a in agents():
        vi.write_item("agent_" + a["id"], face_sprite(a))
        vi.write_item("agent_" + a["id"] + "_dim", face_sprite(a, DIM_ALPHA))
        vi.write_item("agent_" + a["id"] + "_ring", face_sprite(a, 1.0, ring=True))
        n += 3
    vi.write_item("agent_empty", orb("#3a3f4c"))
    index = json.loads((AGENTS / "index.json").read_text())
    for r in index["roles"]:
        vi.write_item("role_" + r["name"].lower(), square(r["color"]))
        n += 1
    vi.write_item("lock_in", square("#5ad07a", 3))
    vi.write_item("random", orb("#9aa3b5", 9, "#ffffff"))
    n += 3
    print(f"wrote {n} items under {vi.PACK}")


if __name__ == "__main__":
    main()
