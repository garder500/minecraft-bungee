#!/usr/bin/env python3
"""One-off conversion of quarry.yml into db/seed/quarry.sql (tables of db/migrations/V010__quarry.sql).

    /tmp/v/bin/python scripts/yaml_to_seed_quarry.py <dir holding the old quarry.yml>

The quests.tutorial section of the old file is dropped on purpose (the quest plan lives in the quests seed).
Kept to document how the seed was made; the YAML is gone from the plugin.
"""
import pathlib
import sys

import yaml

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sqlseed import insert, write  # noqa: E402

# Balance pass (the pity was far too generous). Chances are percent per block for tiers I..IV, pity in blocks.
# Diamond, debris and voidglass keep the tiers where they already dropped. Bronzite is the old chances x0.75.
ORE_TWEAKS = {
    "bronzite": {"chance": [round(c * 0.75, 2) for c in (3.0, 3.0, 2.0, 1.0)], "pity": 300},
    "diamond": {"chance": [0, 0.3, 0.3, 0.3], "pity": 1500},
    "ancient_debris": {"chance": [0, 0, 0.1, 0.1], "pity": 5000},
    "voidglass": {"chance": [0, 0, 0, 0.03], "pity": 15000},
}

# Formerly a flat roll in OutputListener (12% / 8%, no pity). Now rows of the table, from their ring generator only:
# chance 3% / 2% (every tier, the ring is not tier-gated) and a guarantee at 60 / 90 blocks.
RING_ORES = [
    ("frostium", "custom:frostium", "frost", 3.0, 60, ()),
    ("sunstone", "custom:sunstone", "ember", 2.0, 90, ()),
    # the old 12% quartz of Ember netherrack (not soul sand), tried after Sunstone
    ("quartz", "QUARTZ", "ember", 12.0, 0, ("NETHERRACK",)),
]

NOTES = {
    "generator.geode-chance": "Percent of generator blocks that are a geode (3 to 6 of the first geode ore of the tier).",
    "generator.geode-min": "Fewest ores in a geode.",
    "generator.geode-max": "Most ores in a geode.",
    "generator.luck-cap": "Mining luck from the held pickaxe is capped here.",
    "generator.tiers": "Number of generator tiers (II Vein, III Ember, IV Void come from cores and the lens).",
    "generator.geode-bonus.ore": "Ore a geode of the bonus tier may add one of.",
    "generator.geode-bonus.percent": "Percent of such geodes that add the bonus ore.",
    "generator.geode-bonus.from-tier": "First generator tier where the bonus can happen.",
    "forge.slots": "Forge jobs that run at the same time.",
    "machines.coal-generator.output": "Flux per second a burning coal generator makes.",
    "machines.coal-generator.coal-seconds": "Seconds one coal burns.",
    "machines.battery.capacity": "Flux a battery stores.",
    "machines.solar-panel.output": "Flux per second of a solar panel in daylight under the open sky.",
    "machines.miner-1.consume": "Flux per second the Miner I uses.",
    "machines.miner-1.cycles-per-second": "Mining cycles per second of the Miner I.",
    "machines.miner-2.consume": "Flux per second the Miner II uses.",
    "machines.miner-2.cycles-per-second": "Mining cycles per second of the Miner II.",
}


def setting(path, value):
    typ = "int" if isinstance(value, int) and not isinstance(value, bool) else "real" if isinstance(value, float) else "text"
    return insert("setting", {"id": "quarry." + path, "type": typ, "value": str(value), "note": NOTES.get(path, "")})


def main():
    src = pathlib.Path(sys.argv[1]) / "quarry.yml"
    y = yaml.safe_load(src.read_text(encoding="utf-8"))
    rows = []

    gen = y["generator"]
    for key in ("geode-chance", "geode-min", "geode-max", "luck-cap", "tiers"):
        rows.append(setting("generator." + key, gen[key]))
    for key in ("ore", "percent", "from-tier"):
        rows.append(setting("generator.geode-bonus." + key, gen["geode-bonus"][key]))
    rows.append(setting("forge.slots", y["forge"]["slots"]))
    for machine, values in y["machines"].items():
        for key, value in values.items():
            rows.append(setting(f"machines.{machine}.{key}", value))

    position = 0
    for oid, ore in y["ores"].items():
        tweak = ORE_TWEAKS.get(oid, {})
        chances = tweak.get("chance", ore["chance"])
        rows.append(insert("ore_roll", {"id": oid, "position": position, "item": ore["item"], "source": "cobble",
                                        "pity": tweak.get("pity", ore.get("pity", 0)), "geode": bool(ore.get("geode", False))}))
        for tier, chance in enumerate(chances, 1):
            rows.append(insert("ore_roll_chance", {"ore_roll_id": oid, "tier": tier, "chance": float(chance)}))
        position += 1
    for oid, item, source, chance, pity, blocks in RING_ORES:
        rows.append(insert("ore_roll", {"id": oid, "position": position, "item": item, "source": source, "pity": pity, "geode": False}))
        for tier in range(1, gen["tiers"] + 1):
            rows.append(insert("ore_roll_chance", {"ore_roll_id": oid, "tier": tier, "chance": chance}))
        for block in blocks:
            rows.append(insert("ore_roll_block", {"ore_roll_id": oid, "block": block}))
        position += 1

    for pos, (item, amount) in enumerate(y["forge"]["recipe"].items()):
        rows.append(insert("forge_block_ingredient", {"item": item, "position": pos, "amount": amount}))
    for pos, (rid, recipe) in enumerate(y["forge"]["recipes"].items()):
        rows.append(insert("forge_recipe", {"id": rid, "position": pos, "result": recipe["result"],
                                            "amount": recipe.get("amount", 1), "seconds": recipe["seconds"]}))
        for ipos, (item, amount) in enumerate(recipe["cost"].items()):
            rows.append(insert("forge_recipe_ingredient", {"forge_recipe_id": rid, "position": ipos, "item": item, "amount": amount}))

    out = write("quarry", ["The Quarry: ore table, Forge recipes and machine numbers.",
                           "Converted from the former quarry.yml by scripts/yaml_to_seed_quarry.py; edit it in the panel."], rows)
    print(f"{len(rows)} rows -> {out}")
    print("ores", len(y["ores"]), "recipes", len(y["forge"]["recipes"]),
          "ingredients", sum(len(r["cost"]) for r in y["forge"]["recipes"].values()), "block", len(y["forge"]["recipe"]))


if __name__ == "__main__":
    main()
