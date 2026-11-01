#!/usr/bin/env python3
"""One-off conversion of the former minions.yml into db/seed/minions.sql (tables of db/migrations/V008__minions.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_minions.py <dir holding minions.yml>

The YAML is gone from the plugin: this script documents how the seed was made.
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

NOTES = {
    "tick_ticks": "Ticker period in server ticks (20 = once a second); only the animation and the status line depend on it.",
    "visual_range": "Blocks: a minion does real block work only while a player is closer than this.",
    "scan_seconds": "How often the surroundings of a minion nobody is looking at are re-checked.",
    "max_catchup_hours": "Longest offline / unloaded-chunk catch-up window (production is also capped by the storage).",
    "island_check_seconds": "Island checks (reset, kicked from a co-op).",
    "island_missing_hours": "A minion whose island has been gone this long is returned to its owner.",
    "member_collect": "Co-op members can also take items out of a minion's storage.",
    "gui_click_millis": "Minimum time between two clicks in a minion menu.",
    "base_slots": "Minion slots: base + one per slot threshold reached by the number of different minions crafted.",
    "max_slots": "Most minion slots a player can have.",
    "slot_thresholds": "Numbers of different minions (type + tier) crafted that each give one more slot.",
    "per_island_cap": "Hard limit of minions on one island, all players together.",
    "scale": "Size of the minion body (1.0 = player size).",
    "compact_ratio": "Units of a resource needed for one compacted item.",
    "super_ratio": "Compacted items needed for one super compacted item.",
    "spread_every": "Diamond Spreading: one bonus diamond every N produced items.",
    "fuel_max_hours": "Most fuel a minion can hold, in hours.",
    "upgrade_slots": "Upgrade slots on every minion (fuel, skin and hat have their own slot).",
    "fishing_min_water": "Minimum water blocks (layer under the minion) a Fishing Minion needs.",
    "chat_cooldown_seconds": "Chat message to the owner when a minion gets blocked, at most once per this many seconds.",
    "actions_per_cycle": "Actions in one cycle (one resource): place + break, plant + harvest, spawn + kill.",
    "fishing_actions_per_cycle": "Fishing casts once per cycle.",
}


def setting_rows(name, value):
    path = "minions." + name.replace("-", "_")
    note = NOTES[name.replace("-", "_")]
    if isinstance(value, list):
        rows = [insert("setting", {"id": path, "type": "list", "value": "", "note": note})]
        for pos, item in enumerate(value):
            rows.append(insert("setting_item", {"setting_id": path, "position": pos, "value": str(item)}))
        return rows
    kind = "bool" if isinstance(value, bool) else "int" if isinstance(value, int) else "real" if isinstance(value, float) else "text"
    text = str(value).lower() if kind == "bool" else repr(value) if kind == "real" else str(value)
    return [insert("setting", {"id": path, "type": kind, "value": text, "note": note})]


def main():
    src = pathlib.Path(sys.argv[1])
    with open(src / "minions.yml", encoding="utf-8") as f:
        y = yaml.safe_load(f)
    rows = []
    for name, value in y["settings"].items():
        rows += setting_rows(name, value)

    t = y["tiers"]
    n = len(t["action-factor"])
    assert n == 11 and all(len(t[k]) == 11 for k in ("storage-slots", "radius", "rarity", "chestplate"))
    assert len(t["tier-up-item"]) == 10 and len(t["tier-up-amount"]) == 10
    for i in range(n):
        up = i < 10
        rows.append(insert("minion_tier", {
            "id": f"tier{i + 1}", "tier": i + 1, "action_factor": float(t["action-factor"][i]),
            "storage_slots": int(t["storage-slots"][i]), "radius": int(t["radius"][i]), "rarity": t["rarity"][i],
            "chestplate": t["chestplate"][i], "tier_up_item": t["tier-up-item"][i] if up else None,
            "tier_up_amount": int(t["tier-up-amount"][i]) if up else None}))

    for pos, (tid, e) in enumerate(y["types"].items()):
        rows.append(insert("minion_type", {
            "id": tid, "position": pos, "name": e["name"], "kind": e["kind"], "action_seconds": float(e["action-seconds"]),
            "hand": e["hand"], "hat": e["hat"], "block": e.get("block"), "sapling": e.get("sapling"),
            "ageable": bool(e.get("ageable", False)), "support": bool(e.get("support", False)), "mob": e.get("mob"),
            "mode": e.get("mode", "each"), "color": e["color"], "recipe_ingredient": e["recipe"]["ingredient"],
            "recipe_center": e["recipe"]["center"]}))
        for dpos, d in enumerate(e["drops"]):
            rows.append(insert("minion_drop", {
                "type_id": tid, "position": dpos, "item": d["item"], "min_amount": int(d["min"]), "max_amount": int(d["max"]),
                "chance": float(d.get("chance", 1))}))

    for pos, (fid, e) in enumerate(y["fuels"].items()):
        rows.append(insert("minion_fuel", {
            "id": fid, "position": pos, "name": e.get("name"), "rarity": e.get("rarity", "COMMON"), "material": e["material"],
            "custom": bool(e.get("custom", True)), "speed": float(e.get("speed", 0.05)), "hours": float(e.get("hours", 1)),
            "compressed": e.get("compressed")}))
        for kind in e.get("kinds", []):
            rows.append(insert("minion_fuel_kind", {"fuel_id": fid, "kind": kind}))

    for pos, (uid, e) in enumerate(y["upgrades"].items()):
        rows.append(insert("minion_upgrade", {
            "id": uid, "position": pos, "name": e["name"], "rarity": e.get("rarity", "UNCOMMON"), "material": e["material"],
            "effect": e["effect"], "speed": float(e.get("speed", 0)), "burn": float(e.get("burn", 1)),
            "radius": int(e.get("radius", 0))}))
        for lpos, line in enumerate(e.get("lore", [])):
            rows.append(insert("minion_upgrade_lore", {"upgrade_id": uid, "position": lpos, "line": line}))

    for pos, (sid, e) in enumerate(y["skins"].items()):
        rows.append(insert("minion_skin", {"id": sid, "position": pos, "name": e["name"],
                                           "rarity": e.get("rarity", "UNCOMMON"), "head": e["head"]}))

    for pos, (rid, e) in enumerate(y["recipes"].items()):
        rows.append(insert("minion_recipe", {"id": rid, "position": pos, "result": e["result"]}))
        for rpos, pattern in enumerate(e["shape"]):
            rows.append(insert("minion_recipe_row", {"recipe_id": rid, "position": rpos, "pattern": pattern}))
        for letter, spec in e["ingredients"].items():
            assert isinstance(letter, str) and len(letter) == 1, letter
            rows.append(insert("minion_recipe_ingredient", {"recipe_id": rid, "letter": letter, "spec": spec}))

    for pos, (raw, result) in enumerate(y["smelt"].items()):
        rows.append(insert("minion_smelt", {"id": raw, "position": pos, "result": result}))

    out = write("minions", ["Minions: tiers, types, fuels, upgrades, hats, recipes, smelting and the settings.",
                            "Converted from the former minions.yml by scripts/yaml_to_seed_minions.py; edit it in the panel."], rows)
    print(f"minions: {len(rows)} rows -> {out}")
    print({k: len(y[k]) for k in ("types", "fuels", "upgrades", "skins", "recipes", "smelt")}, len(y["settings"]),
          sum(len(e["drops"]) for e in y["types"].values()))


if __name__ == "__main__":
    main()
