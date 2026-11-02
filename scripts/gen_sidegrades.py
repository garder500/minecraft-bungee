#!/usr/bin/env python3
"""Generates the Starcore sidegrade sets (GDD 9.2) as seed SQL, appended to items.sql (item_def rows, owner quarry) and
quarry.sql (Forge recipes). Rule (GDD 3.2): the same total of stat points as the Starcore piece, each stat within
+-20 % of the Starcore value, and an extra functional effect that is not a stat. SidegradeBudgetTest re-checks the result.

  python3 scripts/gen_sidegrades.py items  >> survival-plugin/src/main/resources/db/seed/items.sql
  python3 scripts/gen_sidegrades.py quarry >> survival-plugin/src/main/resources/db/seed/quarry.sql
Add a set name after the kind (python3 scripts/gen_sidegrades.py items frost_starcore) to print only that set.
"""
import sys

ONLY = sys.argv[2:]  # optional set names: only these are printed (to append a new set to the seeds)

STARCORE = {  # slot: (might, ward, vitality)
    "helmet": (16, 69, 82), "chestplate": (25, 104, 123), "leggings": (21, 86, 103), "boots": (14, 61, 72)}
MATERIAL = {"helmet": "NETHERITE_HELMET", "chestplate": "NETHERITE_CHESTPLATE", "leggings": "NETHERITE_LEGGINGS",
            "boots": "NETHERITE_BOOTS"}
EQUIP = {"helmet": ("HEAD", None), "chestplate": ("CHEST", "vampyuyu:starcore"), "leggings": ("LEGS", "vampyuyu:starcore"),
         "boots": ("FEET", "vampyuyu:starcore")}
# the Starcore pieces' own rows, to copy the durability
SETS = {
    "cinder_starcore": dict(
        title="Cinder Starcore", realm="Cracked Ashes", color="gold", resource="cinderheart", position=300,
        might=0.85, ward=1.0,  # less damage, same Ward, the difference goes to Vitality
        pdc={"helmet": {"heat_resist": 6.0}, "chestplate": {"heat_resist": 10.0}, "leggings": {"heat_resist": 8.0},
             "boots": {"heat_resist": 6.0}},
        lore=["<gold>Sidegrade of the <red>Cracked Ashes<gold>: different, not stronger.",
              "<gray>Heat resistance: <gold>+{heat}<gray> (Cracked Ashes).",
              "<gold>Full set, Ashen Mantle: <gray>fire, lava and hot floors hurt half as much."]),
    "grove_starcore": dict(
        title="Grove Starcore", realm="Whispering Grove", color="green", resource="heartwood", position=320,
        might=1.15, ward=0.85,  # more damage, less Ward, the difference goes to Vitality
        pdc={"helmet": {"respiration": 1.0}, "chestplate": {}, "leggings": {}, "boots": {}},
        lore=["<green>Sidegrade of the <dark_green>Whispering Grove<green>: different, not stronger.",
              "<gray>The helmet lets you breathe under water.",
              "<green>Full set, Verdant Mending: <gray>heals 1 heart every 5 s while nothing has hurt you for 8 s."]),
    "frost_starcore": dict(
        title="Frost Starcore", realm="Frozen Heights", color="aqua", resource="stasis_crystal", position=340,
        might=0.85, ward=0.95,  # less damage, a little less Ward, the difference goes to Vitality
        pdc={"helmet": {"cold_resist": 6.0}, "chestplate": {"cold_resist": 10.0}, "leggings": {"cold_resist": 8.0},
             "boots": {"cold_resist": 6.0}},
        lore=["<aqua>Sidegrade of the <blue>Frozen Heights<aqua>: different, not stronger.",
              "<gray>Cold resistance: <aqua>+{cold}<gray> (Frozen Heights).",
              "<aqua>Full set, Glacial Poise: <gray>frost never bites you and falls hurt half as much."]),
}
COST = {"helmet": 8, "chestplate": 12, "leggings": 10, "boots": 6}
SECONDS = {"helmet": 2400, "chestplate": 2700, "leggings": 2600, "boots": 2300}


def piece(name, slot):
    might, ward, vit = STARCORE[slot]
    total = might + ward + vit
    s = SETS[name]
    m = round(might * s["might"])
    w = round(ward * s["ward"])
    return m, w, total - m - w


def items():
    out = []
    for name, s in SETS.items():
        if ONLY and name not in ONLY:
            continue
        for n, slot in enumerate(["helmet", "chestplate", "leggings", "boots"]):
            m, w, v = piece(name, slot)
            slot_eq, asset = EQUIP[slot]
            id_ = f"{name}_{slot}"
            title = f"{s['title']} {slot.capitalize()}"
            asset_sql = "NULL" if asset is None else f"'{asset}'"
            out.append(f"INSERT INTO item_def (id, owner, position, name, rarity, material, item_model, type_label, damage, unbreakable, "
                       f"hide_flags, glint, max_stack, equip_slot, equip_asset, element, tier) VALUES ('{id_}', 'quarry', {s['position'] + n}, "
                       f"'{title}', 'MYTHIC', '{MATERIAL[slot]}', 'vampyuyu:armor/starcore_{slot}', '{slot.upper()}', NULL, 0, 1, 0, NULL, "
                       f"'{slot_eq}', {asset_sql}, NULL, 5);")
            out.append(f"INSERT INTO item_pdc (item_id, key, kind, value) VALUES ('{id_}', 'max_durability', 'long', '4000');")
            for stat, val in (("might", m), ("ward", w), ("vitality", v)):
                out.append(f"INSERT INTO item_stat (item_id, stat, value) VALUES ('{id_}', '{stat}', {val}.0);")
            lines = [l.replace("{heat}", str(int(s["pdc"][slot].get("heat_resist", 0))))
                     .replace("{cold}", str(int(s["pdc"][slot].get("cold_resist", 0)))) for l in s["lore"]]
            keep = []
            for l in lines:
                if "Heat resistance" in l and "heat_resist" not in s["pdc"][slot]:
                    continue
                if "Cold resistance" in l and "cold_resist" not in s["pdc"][slot]:
                    continue
                if "breathe" in l and slot != "helmet":
                    continue
                keep.append(l)
            keep.append(f"<gray>Four pieces of the <{s['color']}>{s['title']}<gray> set.")
            for i, l in enumerate(keep):
                out.append(f"INSERT INTO item_lore (item_id, position, line) VALUES ('{id_}', {i}, '{l}');")
            for key, val in s["pdc"][slot].items():
                out.append(f"INSERT INTO item_pdc (item_id, key, kind, value) VALUES ('{id_}', '{key}', 'double', '{val}');")
            out.append(f"INSERT INTO item_pdc (item_id, key, kind, value) VALUES ('{id_}', 'extra_armor', 'long', '1');")
            out.append(f"INSERT INTO item_pdc (item_id, key, kind, value) VALUES ('{id_}', 'extra_toughness', 'long', '1');")
    return out


def recipes():
    out = []
    pos = 100
    for name, s in SETS.items():
        if ONLY and name not in ONLY:
            pos += 4
            continue
        for slot in ["helmet", "chestplate", "leggings", "boots"]:
            id_ = f"{name}_{slot}"
            out.append(f"INSERT INTO forge_recipe (id, position, result, amount, seconds) VALUES ('{id_}', {pos}, '{id_}', 1, {SECONDS[slot]});")
            ing = [(f"custom:starcore_{slot}", 1), (f"custom:{s['resource']}", COST[slot]), ("custom:starcore", 1)]
            for i, (item, amount) in enumerate(ing):
                out.append(f"INSERT INTO forge_recipe_ingredient (forge_recipe_id, position, item, amount) VALUES ('{id_}', {i}, '{item}', {amount});")
            pos += 1
    return out


if __name__ == "__main__":
    print("\n".join(items() if sys.argv[1] == "items" else recipes()))
