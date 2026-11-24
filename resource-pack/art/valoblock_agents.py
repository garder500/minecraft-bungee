#!/usr/bin/env python3
"""ValoBlock agent select sprites (stdlib only, reuses the PNG encoder of valoblock_items.py).

The agents come from arena-plugin/src/main/resources/valoblock/agents/*.json; the art is the painted bust of
valoblock_portraits.py (built from the same colour plan as the in-game skin). Per agent three 32x32 items are
written: agent_<id> (the bust's head on its menu colour), agent_<id>_dim (35 % alpha: taken or filtered out) and
agent_<id>_ring (a yellow frame: the current pick). Also agent_empty (the unlit bust of an empty seat),
role_<name> (the role's glyph in its colour), lock_in (a padlock: the green button is in the background) and random (a die);
those four come from valoblock_menukit.py.
Pure art, no lettering: every word is a window title or an item tooltip.

    models/item/valoblock/<name>.json, items/valoblock/<name>.json, textures/item/valoblock/<name>.png
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import valoblock_items as vi  # noqa: E402
import valoblock_menukit as kit  # noqa: E402
import valoblock_portraits as portraits  # noqa: E402

AGENTS = vi.ROOT.parent / "arena-plugin/src/main/resources/valoblock/agents"
SIZE = 32
DIM_ALPHA = 0.35
RING = (0xFF, 0xE9, 0x4A)


def face_sprite(agent, alpha=1.0, ring=False):
    """The head of the agent's painted bust on its menu colour (valoblock_portraits.py)."""
    return portraits.tile(agent, alpha, RING if ring else None)


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
    vi.write_item("agent_empty", kit.seat_glyph())
    index = json.loads((AGENTS / "index.json").read_text())
    for r in index["roles"]:
        vi.write_item("role_" + r["name"].lower(), kit.role_glyph(r["name"].lower(), kit.col(r["color"])))
        n += 1
    vi.write_item("lock_in", kit.lock_glyph())
    vi.write_item("random", kit.die_glyph())
    n += 3
    print(f"wrote {n} items under {vi.PACK}")


if __name__ == "__main__":
    main()
