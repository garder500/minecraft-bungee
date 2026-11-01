#!/usr/bin/env python3
"""One-off conversion of boss.yml into survival-plugin/src/main/resources/db/seed/boss.sql (tables of V011__boss.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_boss.py <dir holding boss.yml>

The YAML is gone from the plugin: this script documents how the seed was made (git show <commit>:survival-plugin/src/main/resources/boss.yml).
Besides the conversion it applies the owner's loot balance decision: every drop that had a pity gets PITY (+0.1 percentage
point per dry kill) and PITY_CAP (+1.5 percentage points at most); drops without pity keep pity 0 and the new default cap.
Every boss gets the 'tier' it is rated at (TIERS).
"""
import pathlib
import re
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

PITY = 0.001
PITY_CAP = 0.015
TIERS = {"crypt_lord": 1, "magma_warden": 2, "frost_revenant": 3, "sylvan_matriarch": 3, "abyssal_leviathan": 4, "voidwyrm": 5}
DEFAULT_RANK_BONUS = [1.5, 1.25, 1.1]

SETTING_NOTES = {
    "hp-scale": "Multiplies every boss health pool.",
    "damage-scale": "Multiplies every hit bosses deal to players.",
    "incoming-damage-scale": "Multiplies every hit players deal to bosses (calibrate to the items damage formula).",
    "tick-period": "Ticks between two boss updates (1-10).",
    "bar-range": "Players closer than this see the boss bar.",
    "visual-health": "Max health attribute of the entity (only mirrors the percentage).",
    "hit-iframes": "Invulnerability ticks of a boss between two hits.",
    "magic-find-cap": "Magic Find above this is ignored.",
    "purge-orphans": "Remove tagged boss entities left behind (bosses never persist across a restart).",
    "scale-min-share": "A player grows the boss health pool only after dealing this fraction of its base health.",
    "repeat-window-minutes": "Kills of the same boss inside this window lower the next payout (coins and combat xp)...",
    "repeat-decay": "...by this fraction per earlier kill...",
    "repeat-floor": "...down to this fraction (0 decay turns the damper off).",
    "damage-numbers": "Floating damage numbers on bosses (they take a token real hit, so the generic ones read 0).",
}


def setting(sid, value, note=""):
    if isinstance(value, bool):
        typ, text = "bool", "true" if value else "false"
    elif isinstance(value, int):
        typ, text = "int", str(value)
    elif isinstance(value, float):
        typ, text = "real", repr(value)
    else:
        typ, text = "text", str(value)
    return [insert("setting", {"id": sid, "type": typ, "value": text, "note": note})]


def setting_list(sid, values, note=""):
    rows = [insert("setting", {"id": sid, "type": "list", "value": "", "note": note})]
    for i, v in enumerate(values):
        rows.append(insert("setting_item", {"setting_id": sid, "position": i, "value": str(v)}))
    return rows


def num(v, default):
    return float(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else float(default)


def flag(v, default):
    return 1 if (default if v is None else v) else 0


def equipment(table, key, owner, eq):
    rows = []
    for slot, text in (eq or {}).items():
        material, _, colour = str(text).partition(":")
        rows.append(insert(table, {key: owner, "slot": slot.lower(), "material": material.strip(),
                                   "color": int(colour.strip()[1:], 16) if colour else None}))
    return rows


def abilities(table, owner_cols, abilities_list, param_table):
    """Rows of the ability table and its params; owner_cols holds the leading key columns (boss_id/phase/role or slayer_id)."""
    rows = []
    for pos, a in enumerate(abilities_list or []):
        a = dict(a)
        row = dict(owner_cols)
        row["position"] = pos
        row["type"] = a.pop("type")
        row["cooldown"] = num(a.pop("cooldown", None), 12)
        row["telegraph"] = num(a.pop("telegraph", None), 0)
        row["damage"] = num(a.pop("damage", None), 1.0)
        row["target"] = str(a.pop("target", "nearest")).lower()
        row["from_tier"] = int(a.pop("from-tier", 1))
        rows.append(insert(table, row))
        for name, value in a.items():
            assert not isinstance(value, bool), (table, name)
            p = dict(owner_cols)
            p.pop("position", None)
            p["ability"] = pos
            p["name"] = name
            p["num"] = float(value) if isinstance(value, (int, float)) else None
            p["text"] = None if isinstance(value, (int, float)) else str(value)
            rows.append(insert(param_table, p))
    return rows


def drops(table, key, owner, entries):
    rows = []
    for pos, d in enumerate(entries or []):
        has_pity = "pity" in d
        rows.append(insert(table, {
            key: owner, "position": pos, "item": d["item"], "chance": float(d.get("chance", 1.0)),
            "min_amount": int(d.get("min", 1)), "max_amount": int(d.get("max", d.get("min", 1))),
            "min_share": num(d.get("min-share"), 0), "max_rank": int(d.get("max-rank", 0)),
            "magic_find": flag(d.get("magic-find"), True), "pity": PITY if has_pity else 0.0,
            "pity_cap": PITY_CAP, "from_tier": int(d.get("from-tier", 1))}))
    return rows


def rank_bonus(table, key, owner, loot):
    return [insert(table, {key: owner, "rank": i + 1, "multiplier": float(m)})
            for i, m in enumerate(loot.get("rank-bonus", DEFAULT_RANK_BONUS))]


def options(opt):
    opt = opt or {}
    return {"option_size": opt.get("size"), "option_baby": None if "baby" not in opt else flag(opt["baby"], False),
            "option_glow": None if "glow" not in opt else flag(opt["glow"], False),
            "option_contact_radius": None if "contact-radius" not in opt else float(opt["contact-radius"])}


def boss_rows(bid, b):
    arena = b.get("arena") or {}
    radius = num(arena.get("radius"), 60)
    loot = b.get("loot") or {}
    row = {"id": bid, "kind": b.get("kind", "boss"), "tier": TIERS.get(bid), "name": b["name"],
           "description": b.get("description", ""), "entity": b["entity"], "health": float(b["health"]),
           "damage": num(b.get("damage"), 6), "armor": num(b.get("armor"), 0), "scale": num(b.get("scale"), 1),
           "speed": num(b.get("speed"), 1), "knockback_resistance": num(b.get("knockback-resistance"), 1),
           "bar_color": b["bar"]["color"], "bar_overlay": b["bar"]["overlay"],
           "arena_radius": radius, "arena_leash": num(arena.get("leash"), radius + 10),
           "arena_projectile_range": num(arena.get("projectile-range"), radius + 4),
           "arena_no_fly": flag(arena.get("no-fly"), False), "arena_no_build": flag(arena.get("no-build"), True),
           "arena_no_teleport": flag(arena.get("no-teleport"), True), "arena_min_players": int(arena.get("min-players", 1)),
           "arena_scale_per_player": num(arena.get("scale-per-player"), 0.25), "arena_scale_cap": num(arena.get("scale-cap"), 3.0),
           "arena_enrage_seconds": int(arena.get("enrage-seconds", 600)),
           "arena_enrage_damage": num(arena.get("enrage-damage"), 1.5), "arena_enrage_growth": num(arena.get("enrage-growth"), 0.25),
           "arena_idle_despawn_seconds": int(arena.get("idle-despawn-seconds", 60)),
           "arena_time_limit_seconds": int(arena.get("time-limit-seconds", 0)), "arena_max_minions": int(arena.get("max-minions", 12))}
    row.update(options(b.get("options")))
    row.update({"loot_coins": num(loot.get("coins"), 0), "loot_combat_xp": num(loot.get("combat-xp"), 0),
                "loot_min_share": num(loot.get("min-share"), 0.01), "loot_share_floor": num(loot.get("share-floor"), 0.25),
                "loot_wings_min_share": num(loot.get("wings-min-share"), -1), "loot_announce_below": num(loot.get("announce-below"), 0.01)})
    rows = [insert("boss", row)]
    rows += equipment("boss_equipment", "boss_id", bid, b.get("equipment"))
    for pi, phase in enumerate(b["phases"]):
        rows.append(insert("boss_phase", {"boss_id": bid, "position": pi, "name": phase["name"], "at": float(phase["at"]),
                                          "title": phase.get("title", ""), "subtitle": phase.get("subtitle", ""),
                                          "damage": num(phase.get("damage"), 1), "speed": num(phase.get("speed"), 1)}))
        for role, key in (("enter", "enter"), ("cast", "abilities")):
            rows += abilities("boss_ability", {"boss_id": bid, "phase": pi, "role": role}, phase.get(key), "boss_ability_param")
    rows += rank_bonus("boss_rank_bonus", "boss_id", bid, loot)
    rows += drops("boss_drop", "boss_id", bid, loot.get("drops"))
    s = b.get("summon")
    if s:
        a = s.get("altar") or {}
        rows.append(insert("boss_summon", {
            "boss_id": bid, "item": s["item"], "mode": s.get("mode", "free"), "altar_world": a.get("world"),
            "altar_x": a.get("x"), "altar_y": a.get("y"), "altar_z": a.get("z"), "altar_radius": a.get("radius"),
            "altar_cell_size": a.get("cell-size"), "max_spawn_distance": num(s.get("max-spawn-distance"), 0),
            "cooldown_seconds": int(s.get("cooldown-seconds", 60)), "global_cooldown_seconds": int(s.get("global-cooldown-seconds", 0)),
            "require_island_member": flag(s.get("require-island-member"), True), "require_water": flag(s.get("require-water"), False),
            "max_concurrent": int(s.get("max-concurrent", 1)), "global_max_concurrent": int(s.get("global-max-concurrent", 6)),
            "refund_on_despawn": flag(s.get("refund-on-despawn"), True)}))
        for i, w in enumerate(s.get("worlds", [])):
            rows.append(insert("boss_summon_world", {"boss_id": bid, "position": i, "world": w}))
    return rows


def slayer_rows(position, sid, s):
    loot = s.get("loot") or {}
    fr = s.get("frenzy")
    bar = s.get("bar") or {}
    row = {"id": sid, "position": position, "name": s["name"], "icon": s.get("icon", "ROTTEN_FLESH"),
           "description": s.get("description", ""), "entity": s["entity"],
           "default_kill_xp": float(s["kill-xp"].get("default", 5)), "bar_color": bar.get("color", "RED"),
           "bar_overlay": bar.get("overlay", "PROGRESS")}
    row.update(options(s.get("options")))
    row.update({"frenzy_from_tier": int(fr.get("from-tier", 3)) if fr else None, "frenzy_at": num(fr.get("at"), 0.4) if fr else None,
                "frenzy_damage": num(fr.get("damage"), 1.25) if fr else None, "frenzy_speed": num(fr.get("speed"), 1.2) if fr else None,
                "frenzy_title": fr.get("title", "<red><bold>FRENZY") if fr else None,
                "frenzy_subtitle": fr.get("subtitle", "<gray>It is enraged!") if fr else None,
                "loot_min_share": num(loot.get("min-share"), 0.01), "loot_share_floor": num(loot.get("share-floor"), 0.25),
                "loot_announce_below": num(loot.get("announce-below"), 0.01)})
    rows = [insert("slayer", row)]
    for i, t in enumerate(s["targets"]):
        rows.append(insert("slayer_target", {"slayer_id": sid, "position": i, "entity": t, "kill_xp": s["kill-xp"].get(t)}))
    for i, w in enumerate(s.get("worlds", [])):
        rows.append(insert("slayer_world", {"slayer_id": sid, "position": i, "world": w}))
    for i, xp in enumerate(s["level-xp"]):
        rows.append(insert("slayer_level", {"slayer_id": sid, "level": i + 1, "xp": int(xp)}))
    for i, p in enumerate(s.get("perks", [])):
        rows.append(insert("slayer_perk", {"slayer_id": sid, "position": i, "level": p["level"], "stat": p["stat"], "amount": float(p["amount"])}))
    for t in range(5):
        def col(key, default=0.0):
            v = s.get(key, default)
            return float(v[t] if isinstance(v, list) else v)
        rows.append(insert("slayer_tier", {
            "slayer_id": sid, "tier": t + 1, "cost": col("costs"), "required_xp": col("required-xp"), "health": col("health"),
            "damage": col("damage"), "slayer_xp": col("slayer-xp"), "time_limit": col("time-limit"), "coins": col("coins"),
            "combat_xp": col("combat-xp"), "speed": col("speed", 1.0), "scale": col("scale", 1.0), "armor": col("armor"),
            "unlock_level": int(col("unlock-level"))}))
    rows += equipment("slayer_equipment", "slayer_id", sid, s.get("equipment"))
    rows += abilities("slayer_ability", {"slayer_id": sid}, s.get("abilities"), "slayer_ability_param")
    rows += rank_bonus("slayer_rank_bonus", "slayer_id", sid, loot)
    rows += drops("slayer_drop", "slayer_id", sid, loot.get("drops"))
    return rows


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def convert(y):
    rows = []
    for key, value in y["settings"].items():
        rows += setting("boss.settings." + key, value, SETTING_NOTES.get(key, ""))
    for key, value in y["slayer-settings"].items():
        rows += setting("boss.slayer." + key, value)
    for key, value in y["world-bosses"].items():
        if key in ("locations", "pool"):
            continue
        if isinstance(value, list):
            rows += setting_list("boss.world." + key, value, "Warnings before the spawn, seconds.")
        else:
            rows += setting("boss.world." + key, value)
    for bid, b in y["bosses"].items():
        rows += boss_rows(bid, b)
    for pos, (sid, s) in enumerate(y["slayers"].items()):
        rows += slayer_rows(pos, sid, s)
    for item, r in y["recipes"].items():
        rows.append(insert("boss_recipe", {"item": item, "enabled": flag(r.get("enabled"), True)}))
        for i, pattern in enumerate(r["shape"]):
            rows.append(insert("boss_recipe_row", {"recipe": item, "position": i, "pattern": pattern}))
        for sym, material in r["ingredients"].items():
            rows.append(insert("boss_recipe_ingredient", {"recipe": item, "symbol": sym, "material": material}))
    assert not y["mob-drops"] and not y["items"] and not y["world-bosses"]["pool"]
    for pos, loc in enumerate(y["world-bosses"]["locations"]):
        rows.append(insert("boss_world_spot", {"id": slug(loc["name"]), "position": pos, "world": loc["world"],
                                               "x": float(loc["x"]), "y": float(loc["y"]), "z": float(loc["z"]), "name": loc["name"]}))
    return rows


def main():
    src = pathlib.Path(sys.argv[1])
    with open(src / "boss.yml", encoding="utf-8") as f:
        y = yaml.safe_load(f)
    rows = convert(y)
    out = write("boss", ["Bosses, slayers, summon recipes and world boss spots; the global knobs are 'boss.*' setting rows.",
                         "Converted from the former boss.yml by scripts/yaml_to_seed_boss.py; edit it in the panel.",
                         "Loot balance: pity is +0.1 percentage point per dry kill, capped at +1.5 points."], rows)
    print(f"boss: {len(rows)} rows -> {out}")


if __name__ == "__main__":
    main()
