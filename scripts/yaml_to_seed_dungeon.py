#!/usr/bin/env python3
"""One-off conversion of dungeon.yml into survival-plugin/src/main/resources/db/seed/dungeon.sql (tables of V012__dungeon.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_dungeon.py <dir holding dungeon.yml>

The YAML is gone from the plugin: this script documents how the seed was made (git show <commit>:survival-plugin/src/main/resources/dungeon.yml).
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

NOTES = {
    "max-concurrent-runs": "Dungeon worlds alive at the same time (generation, run, closing).",
    "close-delay-seconds": "Seconds between the end of a run and the destruction of its world.",
    "run-timeout-minutes": "A run that lasts longer than this is aborted.",
    "boss-timeout-minutes": "Time given to the party to kill the final boss once it spawned.",
    "boss-external-from-floor": "0 = always the scripted Crypt Lord. N > 0 = from floor N on the boss feature's crypt_lord is used.",
    "ready-countdown-seconds": "Countdown between the end of the generation and the moment the doors open.",
    "sidebar": "Wither key / blood key broadcast and sidebar.",
    "darkness-effect": "A faint Darkness effect on everybody in the dungeon.",
    "require-previous-floor": "Unlock floor n only after clearing floor n-1.",
    "party.max-size": "1 to 5 (a party of one is a solo run).",
    "party.members-can-invite": "When false only the leader invites.",
    "generation.max-ms-per-tick": "Time budget of the block placement per server tick, milliseconds (1 to 40).",
    "generation.base-y": "Y of the floor of every room.",
    "generation.blocks-per-check": "Blocks placed per budget check.",
    "entrance.world": "The hint pad: standing near the point shows a clickable message that tells you to type /dungeon.",
    "rules.deaths-allowed": "Ghost deaths allowed per player and per run; the next death makes a spectator.",
    "rules.ghost-seconds": "A ghost is brought back by itself after this many seconds.",
    "rules.revive-seconds": "Seconds a teammate has to stay next to a body (sneaking) to revive it.",
    "rules.revive-health-percent": "Health a revived player comes back with, percent of max.",
    "rules.void-damage": "Fall into the void: damage taken, then back to the start of the room.",
    "rules.allowed-commands": "Commands that stay available while in a run.",
    "mobs.party-health-scale": "Extra health per additional party member (0.30 = +30 percent each).",
    "mobs.watcher-guards-base": "Guards of the Watcher in the entrance room: base + per floor.",
    "shop.spirit-leaf-price": "Crypt Merchant prices; multiplied by the floor reward multiplier.",
    "shop.flask-heal-percent": "Healing Flask: percent of max health restored.",
    "scoring.speed-decay-seconds": "Seconds past the par time that cost one speed point.",
    "scoring.death-penalty": "Points lost per death (team total).",
    "catacombs.level-base": "Level n needs n^2 * level-base XP in total.",
    "catacombs.reward-percent-per-level": "Percent added to the coins and Combat XP of every clear, per level.",
}


def setting(sid, value):
    note = NOTES.get(sid[len("dungeon."):], "")
    if isinstance(value, list):
        rows = [insert("setting", {"id": sid, "type": "list", "value": "", "note": note})]
        rows += [insert("setting_item", {"setting_id": sid, "position": i, "value": str(v)}) for i, v in enumerate(value)]
        return rows
    if isinstance(value, bool):
        typ, text = "bool", "true" if value else "false"
    elif isinstance(value, int):
        typ, text = "int", str(value)
    elif isinstance(value, float):
        typ, text = "real", repr(value)
    else:
        typ, text = "text", str(value)
    return [insert("setting", {"id": sid, "type": typ, "value": text, "note": note})]


def convert(y):
    rows = []
    scalars = {"scoring": ("speed-decay-seconds", "death-penalty")}
    for key, value in y.items():
        if key in ("forge", "floors", "rewards"):
            continue
        if key == "scoring":
            for k in scalars[key]:
                rows += setting(f"dungeon.scoring.{k}", value[k])
        elif isinstance(value, dict):
            for k, v in value.items():
                rows += setting(f"dungeon.{key}.{k}", v)
        else:
            rows += setting(f"dungeon.{key}", value)
    tiers = y["rewards"]["tiers"]
    thresholds = y["scoring"]["thresholds"]
    for pos, grade in enumerate(["S+", "S", "A", "B", "C", "D"]):
        t = tiers[grade]
        rows.append(insert("dungeon_grade", {"id": grade, "position": pos, "min_score": int(thresholds[grade]), "coins": float(t["coins"]),
                                             "xp": float(t["xp"]), "rolls": int(t["rolls"]), "chest": t["chest"]}))
    for pos, r in enumerate(y["rewards"]["items"]):
        rows.append(insert("dungeon_reward", {"id": r["item"], "position": pos, "item": r["item"], "weight": int(r["weight"]),
                                              "min_amount": int(r["min"]), "max_amount": int(r["max"]), "min_floor": int(r["min-floor"]),
                                              "min_grade": r["min-grade"]}))
    for pos, t in enumerate(y["forge"]["trades"]):
        rows.append(insert("dungeon_trade", {"id": t["id"], "position": pos, "name": t["name"], "icon": t["icon"], "give": t.get("give", ""),
                                             "amount": int(t.get("amount", 0)), "cache_rolls": int(t.get("cache-rolls", 0)),
                                             "cache_grade": t.get("cache-grade", "D"), "coins": float(t.get("coins", 0))}))
        for item, amount in t["cost"].items():
            rows.append(insert("dungeon_trade_cost", {"trade_id": t["id"], "item": item, "amount": int(amount)}))
    for number, f in y["floors"].items():
        rows.append(insert("dungeon_floor", {
            "id": str(number), "number": int(number), "name": f["name"], "palette": f["palette"], "grid": f["grid"], "rooms": f["rooms"],
            "puzzles": f["puzzles"], "traps": f["traps"], "secret_rooms": f["secret-rooms"], "mini_bosses": f["mini-bosses"],
            "shop": f["shop"], "fairy": f["fairy"], "wither_doors": f["wither-doors"], "loops": f["loops"], "hp": float(f["hp"]),
            "damage": float(f["damage"]), "mob_level": f["mob-level"], "par_seconds": f["par-seconds"],
            "secrets_needed_percent": f["secrets-needed-percent"], "reward": float(f["reward"]), "min_combat_level": f["min-combat-level"]}))
    return rows


def main():
    src = pathlib.Path(sys.argv[1])
    with open(src / "dungeon.yml", encoding="utf-8") as f:
        y = yaml.safe_load(f)
    rows = convert(y)
    out = write("dungeon", ["The Catacombs dungeon: grades, reward table, Crypt Forge trades, floors; the rules are 'dungeon.*' setting rows.",
                            "Converted from the former dungeon.yml by scripts/yaml_to_seed_dungeon.py; edit it in the panel."], rows)
    print(f"dungeon: {len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
