#!/usr/bin/env python3
"""Generates the ValoBlock map marker files (arena-plugin/src/main/resources/valoblock/maps/<id>.json)
from the 30x30 plans of the design (scripts/vb_map_plans.json).

Coordinates in the files are blocks from the map's min corner: x and z from the corner, y relative to
the feet of a player standing on the floor (0 = standing on the floor). The plugin translates them to
the slot where the map is built. Waypoint nodes are one per walkable cell, linked to the 4 neighbours.

Plan legend: # wall, . floor, R Seedbearer spawn, B Sealer spawn, A seed site, F feature, h high ground,
c crate, g foliage, w water.  Re-run after editing a plan; files are meant to be hand-tuned afterwards
(then do not re-run).  Usage: python3 scripts/gen_vb_maps.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "arena-plugin", "src", "main", "resources", "valoblock", "maps")

NAMES = {
    "Hourfall": ("hourfall", ["Sundial", "Scarab vault"]),
    "Rootvault": ("rootvault", ["Hollow Trunk", "Spore garden"]),
    "Stormbreak": ("stormbreak", ["Lighthouse", "Wreck"]),
}
PALETTES = {
    "hourfall": dict(floor="SMOOTH_SANDSTONE", wall="CUT_SANDSTONE", site="MAGENTA_TERRACOTTA", attack="ORANGE_CONCRETE",
                     defend="LIGHT_BLUE_CONCRETE", feature="GOLD_BLOCK", high="CUT_SANDSTONE", crate="BARREL",
                     foliage="AZALEA_LEAVES", water="BLUE_CONCRETE"),
    "rootvault": dict(floor="MOSS_BLOCK", wall="STRIPPED_OAK_LOG", site="MAGENTA_TERRACOTTA", attack="ORANGE_CONCRETE",
                      defend="LIGHT_BLUE_CONCRETE", feature="YELLOW_CONCRETE", high="OAK_PLANKS", crate="BARREL",
                      foliage="AZALEA_LEAVES", water="BLUE_CONCRETE"),
    "stormbreak": dict(floor="DEEPSLATE_TILES", wall="DEEPSLATE_BRICKS", site="MAGENTA_TERRACOTTA", attack="ORANGE_CONCRETE",
                       defend="LIGHT_BLUE_CONCRETE", feature="YELLOW_CONCRETE", high="WAXED_COPPER_BLOCK", crate="BARREL",
                       foliage="AZALEA_LEAVES", water="BLUE_CONCRETE"),
}
# Standing height of the high-ground cells (h), in blocks above the floor: the themed builders build to it.
HIGH_Y = {"hourfall": 4, "rootvault": 6, "stormbreak": 1}
WALKABLE = set(".RBAFh")  # h (high ground, 1 block up) is walked on top


def build(name, d):
    mid, site_names = NAMES[name]
    plan, cell = d["plan"], d["cell"]
    n = len(plan)

    def centre(col, row):
        return [round((col + 0.5) * cell, 2), HIGH_Y[mid] if plan[row][col] == "h" else 0, round((row + 0.5) * cell, 2)]

    def nid(col, row):
        return "n_%d_%d" % (col, row)

    open_cells = [(c, r) for r in range(n) for c in range(n) if plan[r][c] in WALKABLE]
    openset = set(open_cells)

    def nearest(col, row, pool=None):
        pool = pool or open_cells
        return min(pool, key=lambda p: (p[0] - col) ** 2 + (p[1] - row) ** 2)

    def side_cells(ch):
        return [(c, r) for r in range(n) for c in range(n) if plan[r][c] == ch]

    markers = {}
    spawn_nodes = {}
    for side, ch in (("SEEDBEARERS", "R"), ("SEALERS", "B")):
        cells = side_cells(ch)
        cols = [c for c, _ in cells]
        rows = [r for _, r in cells]
        c0, c1, r0, r1 = min(cols), max(cols), min(rows), max(rows)
        back = r1 if ch == "R" else r0  # the row farthest from the middle
        front = r0 if ch == "R" else r1  # the row at the exit: the barrier
        mid_col = (c0 + c1) // 2
        picks = [(mid_col + k, back + (-1 if ch == "R" else 1)) for k in (-2, -1, 0, 1, 2)]
        picks = [p if p in openset else nearest(*p) for p in picks]
        spawn_nodes[side] = [nid(*p) for p in picks]
        markers[side] = dict(
            spawns=[centre(*p) for p in picks],
            buy=dict(min=[c0 * cell, -1, r0 * cell], max=[(c1 + 1) * cell, 4, (r1 + 1) * cell]),
            barrier=dict(min=[c0 * cell, 0, front * cell], max=[(c1 + 1) * cell, 4, (front + 1) * cell]),
        )

    sites, site_data = [], {}
    half = n // 2
    for i, sid in enumerate(("A", "B")):
        cells = [(c, r) for (c, r) in side_cells("A") if (c < half) == (i == 0)]
        cols = [c for c, _ in cells]
        rows = [r for _, r in cells]
        c0, c1, r0, r1 = min(cols), max(cols), min(rows), max(rows)
        sites.append(dict(id=sid, name=site_names[i], min=[c0 * cell, -1, r0 * cell], max=[(c1 + 1) * cell, 3, (r1 + 1) * cell]))
        inside = [p for p in open_cells if c0 <= p[0] <= c1 and r0 <= p[1] <= r1]
        cc, cr = (c0 + c1) / 2, (r0 + r1) / 2
        plant = nearest(cc, cr, inside)
        rest = [p for p in inside if p != plant]
        outer = min(rest, key=lambda p: (p[1], abs(p[0] - (0 if i == 0 else n - 1))))
        deep = max([p for p in rest if p != outer], key=lambda p: (p[1], -abs(p[0] - half)))
        toward = 1 if i == 0 else -1  # toward the middle column
        stage = nearest(min(max(cc + toward * 5, 0), n - 1), min(r1 + 5, n - 1))
        peek = nearest(c1 + 1 if i == 0 else c0 - 1, min(r1 + 1, n - 1))
        smoke_at = centre(stage[0] - toward, stage[1] - 1)
        site_data[sid] = dict(
            weight=0.5,
            anchors=[nid(*outer), nid(*deep)],
            stage=nid(*stage),
            plant=nid(*plant),
            peeks=[nid(*peek)],
            smokes=[dict(node=nid(*stage), at=smoke_at)],
        )

    links = []
    for (c, r) in open_cells:
        for dc, dr in ((1, 0), (0, 1)):
            if (c + dc, r + dr) in openset:
                links.append([nid(c, r), nid(c + dc, r + dr)])

    orbs = [centre(*nearest(7, 14)), centre(*nearest(22, 15))]
    return dict(
        id=mid, name=name, realm=d["realm"], feature=d["featureName"], featureText=d["feature"],
        cell=cell, wallHeight=4, highY=HIGH_Y[mid], palette=PALETTES[mid],
        plan=plan,
        spawns={s: markers[s]["spawns"] for s in markers},
        buyZones={s: markers[s]["buy"] for s in markers},
        barriers=[dict(side=s, **markers[s]["barrier"]) for s in markers],
        sites=sites,
        orbs=orbs,
        waypoints=dict(
            map=mid,
            spawns=spawn_nodes,
            nodes=[dict(id=nid(c, r), pos=centre(c, r)) for (c, r) in open_cells],
            links=links,
            siteData=site_data,
        ),
    )


def dump(m):
    """One line per node, link and plan row: small diffs when a file is hand-tuned."""
    def one(x):
        return json.dumps(x, separators=(", ", ": "))
    w = m["waypoints"]
    out = ["{"]
    keys = [k for k in m if k != "waypoints"]
    for k in keys:
        v = m[k]
        if k == "plan":
            out.append('  "plan": [\n    ' + ",\n    ".join(one(r) for r in v) + "\n  ],")
        else:
            out.append('  %s: %s,' % (json.dumps(k), one(v)))
    out.append('  "waypoints": {')
    out.append('    "map": %s,' % one(w["map"]))
    out.append('    "spawns": %s,' % one(w["spawns"]))
    out.append('    "nodes": [\n      ' + ",\n      ".join(one(x) for x in w["nodes"]) + "\n    ],")
    out.append('    "links": [\n      ' + ",\n      ".join(one(x) for x in w["links"]) + "\n    ],")
    out.append('    "siteData": %s' % one(w["siteData"]))
    out.append("  }")
    out.append("}")
    return "\n".join(out) + "\n"


def main():
    plans = json.load(open(os.path.join(HERE, "vb_map_plans.json")))
    os.makedirs(OUT, exist_ok=True)
    for name, d in plans.items():
        m = build(name, d)
        path = os.path.join(OUT, m["id"] + ".json")
        with open(path, "w") as f:
            f.write(dump(m))
        json.load(open(path))  # must stay valid JSON
        print(path, len(m["waypoints"]["nodes"]), "nodes", len(m["waypoints"]["links"]), "links")


if __name__ == "__main__":
    main()
