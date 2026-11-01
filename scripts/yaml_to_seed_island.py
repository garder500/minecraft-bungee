#!/usr/bin/env python3
"""One-off conversion of island.yml into db/seed/island.sql (tables of db/migrations/V004__island.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_island.py <yaml dir>
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from seed_settings import comment_notes, setting_rows  # noqa: E402
from sqlseed import insert, write  # noqa: E402


def item(entry):
    material, _, amount = str(entry).partition(":")
    return material.strip(), int(amount) if amount.strip() else 1


def convert(src):
    text = (src / "island.yml").read_text(encoding="utf-8")
    y = yaml.safe_load(text)
    rows = []
    for key, w in y["worlds"].items():
        rows.append(insert("island_world", {"key": key, "time": w["time"], "border": float(w["border"]),
                                            "weather_clear": bool(w["weather-clear"])}))
        for pos, (rule, value) in enumerate((w.get("gamerules") or {}).items()):
            rows.append(insert("island_world_gamerule", {"world": key, "position": pos, "rule": rule,
                                                         "value": str(value).lower() if isinstance(value, bool) else str(value)}))
    for kind, value_key in (("radius", "radius"), ("members", "slots"), ("minions", "slots")):
        for pos, tier in enumerate(y["upgrades"][kind]):
            rows.append(insert("island_upgrade", {"id": f"{kind}.{pos + 1}", "kind": kind, "position": pos,
                                                  "value": int(tier[value_key]), "cost": float(tier["cost"])}))
    for pos, (tid, t) in enumerate(y["templates"].items()):
        rows.append(insert("island_template", {"id": tid, "position": pos, "enabled": t["enabled"], "name": t["name"],
                                               "icon": t["icon"], "biome": t["biome"]}))
        for p, line in enumerate(t["description"]):
            rows.append(insert("island_template_line", {"template_id": tid, "position": p, "text": line}))
        for p, entry in enumerate(t["chest"]):
            material, amount = item(entry)
            rows.append(insert("island_template_item", {"template_id": tid, "position": p, "material": material, "amount": amount}))
    for cid, entries in (("starter-repeat", y["starter-chest"]["repeat-items"]), ("nether", y["platforms"]["nether-chest"]),
                         ("end", y["platforms"]["end-chest"])):
        rows.append(insert("island_chest", {"id": cid}))
        for p, entry in enumerate(entries):
            material, amount = item(entry)
            rows.append(insert("island_chest_item", {"chest_id": cid, "position": p, "material": material, "amount": amount}))
    for material, weight in y["level"]["weights"].items():
        rows.append(insert("island_level_weight", {"material": material, "weight": float(weight)}))
    for flag, value in y["flags"]["defaults"].items():
        rows.append(insert("island_flag_default", {"flag": flag, "value": bool(value)}))
    skip = ("worlds", "upgrades.radius", "upgrades.members", "upgrades.minions", "templates", "starter-chest.repeat-items",
            "platforms.nether-chest", "platforms.end-chest", "level.weights", "flags.defaults")
    return rows + setting_rows("island", y, comment_notes(text), skip)


if __name__ == "__main__":
    statements = convert(pathlib.Path(sys.argv[1]))
    out = write("island", ["The island feature (grid, worlds, upgrades, templates, chests, level weights, protection...).",
                           "Converted from the former island.yml by scripts/yaml_to_seed_island.py; edit it in the panel."], statements)
    print(f"island: {len(statements)} rows -> {out}")
