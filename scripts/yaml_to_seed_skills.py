#!/usr/bin/env python3
"""One-off conversion of the former skills.yml into db/seed/skills.sql (tables of db/migrations/V009__skills.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_skills.py <dir holding skills.yml>

The YAML is gone from the plugin: this script documents how the seed was made.
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

# yaml path -> note. Every scalar / list outside the collection tables is listed here (the script checks it).
SETTINGS = {
    "only_skyblock_worlds": "Skills only earn experience in Skyblock worlds (sb_hub, sb_world, sb_nether, sb_end, dungeons).",
    "blocked_worlds": "Worlds that never give experience even when allowed above.",
    "xp_multiplier": "Global experience multiplier applied before skill multipliers and boosts. 1.0 = normal.",
    "boosts.max_total": "Cap of the combined /skilladmin boost multiplier (boosts add up as 1 + sum(m - 1)).",
    "levels.default_max": "Highest level of a skill that sets no max_level of its own.",
    "levels.base": "Experience to go from level n-1 to n is base * growth^(n-1), rounded to significant_digits digits.",
    "levels.growth": "Growth of the level curve.",
    "levels.significant_digits": "Digits kept when rounding the cost of a level.",
    "levels.xp_table": "Optional explicit list (one number per level) that overrides the curve.",
    "rewards.coins.base": "Coins paid at each level: base + step * level.",
    "rewards.coins.step": "Coins added per level.",
    "rewards.coins.milestone_every": "Every n-th level pays the milestone multiplier.",
    "rewards.coins.milestone_multiplier": "Multiplier of the coins on a milestone level.",
    "average.skills": "Skills counted in the skill average. Empty = every skill except Carpentry.",
    "xp.combat.health_factor": "Other hostile mobs give max health * this much experience.",
    "xp.combat.max_per_kill": "No kill gives more than this.",
    "xp.combat.blocked_entities": "Entities that never give experience or collections.",
    "xp.combat.blocked_spawn_reasons": "Mobs that appeared for these reasons give nothing.",
    "xp.fishing.default_catch": "Experience of a catch that is not listed.",
    "xp.fishing.default_sea_creature": "Experience of a sea creature kill that is not listed.",
    "xp.enchanting.per_cost_level": "Enchanting table: experience per cost level (1-3 buttons).",
    "xp.enchanting.per_enchant_level": "Enchanting table: experience per enchantment level.",
    "xp.enchanting.anvil_per_level": "Experience per level of the anvil cost when a player takes an anvil result.",
    "xp.alchemy.owner_radius": "Blocks: the stand owner must be this close to be paid (0 = no distance check).",
    "xp.alchemy.default": "Experience per potion changed with an ingredient that is not listed.",
    "xp.carpentry.default": "Experience per crafted item that is not listed.",
    "xp.carpentry.max_batch": "Upper bound of crafts counted for one shift-click.",
    "anti_farm.mobs.cell": "Mobs: size of the cell (blocks) where kills are counted.",
    "anti_farm.mobs.window_seconds": "Mobs: seconds events are remembered.",
    "anti_farm.mobs.free": "Mobs: events in the window that give full experience.",
    "anti_farm.mobs.step": "Mobs: experience lost per further event.",
    "anti_farm.mobs.min": "Mobs: experience never goes below this fraction.",
    "anti_farm.blocks.cell": "Blocks: size of the cell (blocks) where breaks are counted.",
    "anti_farm.blocks.window_seconds": "Blocks: seconds events are remembered.",
    "anti_farm.blocks.free": "Blocks: events in the window that give full experience.",
    "anti_farm.blocks.step": "Blocks: experience lost per further event.",
    "anti_farm.blocks.min": "Blocks: experience never goes below this fraction.",
    "fortune.enabled": "Fortune stats add drops to blocks that give skill experience (off: the items feature already does it).",
    "fortune.max_multiplier": "Largest fortune multiplier.",
    "enchanted_guard.enabled": "Enchanted resources cannot be placed, eaten, burned, smelted or used in vanilla recipes.",
    "sea_creatures.enabled": "A catch can summon a sea creature.",
    "sea_creatures.base_chance": "chance = base_chance + per_fishing_level * level + per_chance_stat * SEA_CREATURE_CHANCE, capped at max_chance.",
    "sea_creatures.per_fishing_level": "Chance added per Fishing level.",
    "sea_creatures.per_chance_stat": "Chance added per point of the sea creature chance stat.",
    "sea_creatures.max_chance": "Cap of the chance.",
    "sea_creatures.max_alive_per_player": "Sea creatures of one player alive at once.",
    "sea_creatures.despawn_seconds": "Seconds before an unkilled sea creature goes away.",
    "placed_tracking.extra": "Extra block types remembered when placed.",
    "leaderboard.refresh_seconds": "Seconds between two rebuilds of the leaderboard.",
    "leaderboard.size": "Players kept per skill.",
    "leaderboard.max_files": "Most player files read per rebuild.",
}

# yaml path -> table (material/entity -> xp maps)
XP_TABLES = {
    "xp.combat.mobs": ("skill_mob_xp", "entity"),
    "xp.fishing.catches": ("skill_catch_xp", "material"),
    "xp.fishing.sea_creatures": ("skill_sea_xp", "entity"),
    "xp.alchemy.ingredients": ("skill_brew_xp", "material"),
    "xp.carpentry.crafts": ("skill_craft_xp", "material"),
}


def dig(tree, path):
    for part in path.split("."):
        tree = tree[part]
    return tree


def setting_rows(path, value, note):
    sid = "skills." + path
    if isinstance(value, list):
        rows = [insert("setting", {"id": sid, "type": "list", "value": "", "note": note})]
        for pos, item in enumerate(value):
            rows.append(insert("setting_item", {"setting_id": sid, "position": pos, "value": str(item)}))
        return rows
    kind = "bool" if isinstance(value, bool) else "int" if isinstance(value, int) else "real" if isinstance(value, float) else "text"
    text = str(value).lower() if kind == "bool" else repr(value) if kind == "real" else str(value)
    return [insert("setting", {"id": sid, "type": kind, "value": text, "note": note})]


def main():
    src = pathlib.Path(sys.argv[1])
    with open(src / "skills.yml", encoding="utf-8") as f:
        y = yaml.safe_load(f)
    rows = []
    counts = {}

    for path, note in SETTINGS.items():
        rows += setting_rows(path, dig(y, path), note)
    counts["settings"] = len(SETTINGS)

    for sid, e in y["skills"].items():
        rows.append(insert("skill", {"id": sid, "max_level": int(e["max_level"]), "multiplier": float(e["multiplier"])}))
        pos = 0
        for stat, ranges in e["stats"].items():
            for r in ranges:
                rows.append(insert("skill_stat_range", {"skill_id": sid, "position": pos, "stat": stat, "from_level": int(r["from"]),
                                                        "to_level": int(r["to"]), "amount": float(r["amount"])}))
                pos += 1
        for level, text in e["unlocks"].items():
            assert isinstance(level, int)
            rows.append(insert("skill_unlock", {"skill_id": sid, "level": level, "text": text}))
    counts["skills"] = len(y["skills"])

    mature = set(y["xp"]["farming"]["mature_only"])
    column = set(y["xp"]["farming"]["column_plants"])
    seen = set()
    pos = 0
    for skill in ("mining", "foraging", "farming"):
        for material, xp in y["xp"][skill]["blocks"].items():
            assert material not in seen, material
            seen.add(material)
            rows.append(insert("skill_block_xp", {"material": material, "position": pos, "skill": skill, "xp": float(xp),
                                                  "mature_only": material in mature and skill == "farming",
                                                  "column_plant": material in column and skill == "farming"}))
            pos += 1
    farm = set(y["xp"]["farming"]["blocks"])
    assert mature <= farm and column <= farm, (mature - farm, column - farm)
    counts["blocks"] = pos

    for path, (table, col) in XP_TABLES.items():
        for pos, (key, xp) in enumerate(dig(y, path).items()):
            rows.append(insert(table, {col: key, "position": pos, "xp": float(xp)}))
        counts[table] = len(dig(y, path))

    for pos, (cid, e) in enumerate(y["sea_creatures"]["creatures"].items()):
        rows.append(insert("sea_creature", {"id": cid, "position": pos, "entity": e["entity"], "name": e["name"],
                                            "min_level": int(e["min_level"]), "weight": float(e["weight"]),
                                            "health": float(e["health"])}))
    counts["sea_creature"] = len(y["sea_creatures"]["creatures"])

    for pos, (pid, e) in enumerate(y["collections"]["presets"].items()):
        rows.append(insert("collection_preset", {"id": pid, "position": pos}))
        assert len(e["tiers"]) == len(e["coins"])
        for tier, (amount, coins) in enumerate(zip(e["tiers"], e["coins"]), start=1):
            rows.append(insert("collection_preset_tier", {"preset_id": pid, "tier": tier, "amount": int(amount), "coins": float(coins)}))
    counts["presets"] = len(y["collections"]["presets"])

    for pos, (cid, e) in enumerate(y["collections"]["items"].items()):
        assert cid == cid.upper() and e["preset"] in y["collections"]["presets"]
        mats = e.get("materials") or [cid]
        rows.append(insert("collection", {"id": cid, "position": pos, "name": e.get("name"), "category": e["category"],
                                          "preset": e["preset"], "icon": e.get("icon")}))
        for mpos, m in enumerate(mats):
            rows.append(insert("collection_material", {"collection_id": cid, "position": mpos, "material": m}))
    counts["collections"] = len(y["collections"]["items"])

    for pos, (iid, e) in enumerate(y["enchanted_items"].items()):
        assert set(e) <= {"name", "material", "rarity", "tier"}, e
        rows.append(insert("enchanted_item", {"id": iid, "position": pos, "name": e["name"], "material": e["material"],
                                              "rarity": e.get("rarity", "UNCOMMON"), "model": "vampyuyu:skills/" + iid,
                                              "tier": int(e.get("tier", 1))}))
    counts["enchanted_items"] = len(y["enchanted_items"])

    for pos, (rid, e) in enumerate(y["recipes"].items()):
        assert set(e) <= {"result_custom", "result_item", "ingredients", "requires", "xp", "amount"}, e
        xp = e.get("xp") or {}
        rows.append(insert("skill_recipe", {"id": rid, "position": pos, "result_custom": e.get("result_custom"),
                                            "result_item": e.get("result_item"), "amount": int(e.get("amount", 1)),
                                            "xp_skill": xp.get("skill"), "xp_amount": float(xp.get("amount", 0))}))
        for ipos, ing in enumerate(e["ingredients"]):
            rows.append(insert("skill_recipe_ingredient", {"recipe_id": rid, "position": ipos, "item": ing.get("item"),
                                                           "custom": ing.get("custom"), "slots": int(ing["slots"]),
                                                           "per_slot": int(ing["per_slot"])}))
        for coll, tier in (e.get("requires") or {}).items():
            rows.append(insert("skill_recipe_requirement", {"recipe_id": rid, "collection": coll, "tier": int(tier)}))
    counts["recipes"] = len(y["recipes"])

    out = write("skills", ["Skills: levels and rewards, experience sources, sea creatures, collections, compacted items and recipes.",
                           "Converted from the former skills.yml by scripts/yaml_to_seed_skills.py; edit it in the panel."], rows)
    print(f"skills: {len(rows)} rows -> {out}")
    print(counts)


if __name__ == "__main__":
    main()
