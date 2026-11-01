#!/usr/bin/env python3
"""One-off conversion of wings.yml into db/seed/wings.sql (V006__wings.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_wings.py <yaml dir>
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from seed_settings import comment_notes, setting_rows  # noqa: E402
from sqlseed import insert, write  # noqa: E402


def convert(src):
    text = (src / "wings.yml").read_text(encoding="utf-8")
    y = yaml.safe_load(text)
    lv = y["levels"]
    rows = []
    for i in range(y["max-level"]):
        particle, _, rest = str(lv["trail"][i]).partition(":")
        parts = rest.split(":") if rest else []
        rows.append(insert("wings_level", {
            "level": i + 1, "capacity_bonus": float(lv["capacity-bonus"][i]), "regen_multiplier": float(lv["regen-multiplier"][i]),
            "drain_multiplier": float(lv["drain-multiplier"][i]), "fly_speed": float(lv["fly-speed"][i]),
            "trail_particle": particle, "trail_count": int(parts[0]) if parts else 1,
            "trail_spread": float(parts[1]) if len(parts) > 1 else None}))
        for stat, values in lv["stats"].items():
            rows.append(insert("wings_level_stat", {"level": i + 1, "stat": stat, "value": float(values[i])}))
    for level, u in y["upgrades"].items():
        rows.append(insert("wings_upgrade", {"level": int(level), "core": int(u["core"]), "amount": int(u["amount"]),
                                             "coins": float(u["coins"])}))
    for tier, name in enumerate(y["cores"]["names"], start=1):
        rows.append(insert("wings_core", {"tier": tier, "name": name}))
    for n, r in enumerate(y["no-fly"]["regions"] or [], start=1):
        rows.append(insert("wings_region", {"id": f"region{n}", "world": r["world"], "min_x": r["min"][0], "min_y": r["min"][1],
                                            "min_z": r["min"][2], "max_x": r["max"][0], "max_y": r["max"][1], "max_z": r["max"][2]}))
    skip = ("levels", "upgrades", "cores", "no-fly.regions")
    return rows + setting_rows("wings", y, comment_notes(text), skip)


if __name__ == "__main__":
    statements = convert(pathlib.Path(sys.argv[1]))
    out = write("wings", ["The Wings (levels, upgrade costs, energy, combat, abilities, no-fly zones).",
                          "Converted from the former wings.yml by scripts/yaml_to_seed_wings.py; edit it in the panel."], statements)
    print(f"wings: {len(statements)} rows -> {out}")
