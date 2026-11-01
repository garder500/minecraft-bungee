#!/usr/bin/env python3
"""One-off conversion of menu.yml into db/seed/menu.sql (V007__menu.sql).

The quest plan and the daily pool are not converted here: they have their own seeds (quests.sql, quests_daily.sql), so
quests.daily.pool and quests.tutorial are left out.

    /tmp/v/bin/python scripts/yaml_to_seed_menu.py <yaml dir>
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from seed_settings import comment_notes, setting_rows  # noqa: E402
from sqlseed import insert, write  # noqa: E402


def convert(src):
    text = (src / "menu.yml").read_text(encoding="utf-8")
    y = yaml.safe_load(text)
    rows = []
    for pos, (name, aliases) in enumerate(y["commands"].items()):
        rows.append(insert("menu_command", {"name": name, "position": pos}))
        for p, alias in enumerate(aliases):
            rows.append(insert("menu_command_alias", {"command": name, "position": p, "alias": alias}))
    for world in ("nether", "end"):
        w = y["warps"].get(world) or {}
        if "x" in w and "y" in w and "z" in w:
            rows.append(insert("menu_warp", {"world": world, "x": float(w["x"]), "y": float(w["y"]), "z": float(w["z"]),
                                             "yaw": float(w.get("yaw", 0)), "pitch": float(w.get("pitch", 0))}))
    for pos, (world, name) in enumerate(y["sidebar"]["default-zones"].items()):
        rows.append(insert("menu_default_zone", {"world": world, "position": pos, "name": name}))
    for pos, z in enumerate(y["sidebar"]["zones"], start=1):
        rows.append(insert("menu_zone", {"id": f"zone{pos}", "position": pos, "name": z["name"], "world": z["world"],
                                         "min_x": float(z["min"][0]), "min_y": float(z["min"][1]), "min_z": float(z["min"][2]),
                                         "max_x": float(z["max"][0]), "max_y": float(z["max"][1]), "max_z": float(z["max"][2])}))
    for pos, (event, sound) in enumerate(y["sounds"].items()):
        rows.append(insert("menu_sound", {"event": event, "position": pos, "sound": sound}))
    fairy = y["fairy-souls"]
    for pos, step in enumerate(fairy["stat-cycle"], start=1):
        rows.append(insert("menu_fairy_stat", {"id": f"step{pos}", "position": pos, "stat": step["stat"], "amount": float(step["amount"])}))
    for kind in ("fixed", "island"):
        for pos, s in enumerate(fairy[kind]):
            rows.append(insert("menu_fairy_soul", {"id": s["id"], "kind": kind, "position": pos, "world": s["world"],
                                                   "x": float(s["x"]), "y": float(s["y"]), "z": float(s["z"])}))
    skip = ("commands", "warps.nether", "warps.end", "sidebar.default-zones", "sidebar.zones", "sounds",
            "fairy-souls.stat-cycle", "fairy-souls.fixed", "fairy-souls.island", "quests.daily.pool", "quests.tutorial")
    return rows + setting_rows("menu", y, comment_notes(text), skip)


if __name__ == "__main__":
    statements = convert(pathlib.Path(sys.argv[1]))
    out = write("menu", ["The menu feature (join routing, guide commands, sidebar, sounds, fairy souls, quest tuning).",
                         "Converted from the former menu.yml by scripts/yaml_to_seed_menu.py; edit it in the panel."], statements)
    print(f"menu: {len(statements)} rows -> {out}")
