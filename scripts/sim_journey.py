#!/usr/bin/env python3
"""Estimates the prologue (island -> Echo de la Faille, GDD 4.1 / 13.2 / 21: target ~5 h) from the real seeds.

    python3 scripts/sim_journey.py                 # table for 1, 2 and 3 stone rings
    python3 scripts/sim_journey.py --rings 2 --runs 2000 --seed 7
    python3 scripts/sim_journey.py --check         # exit 1 when the median is outside 4-6 h (CI-style gate)

What is measured (Monte Carlo, same rules as OreRoller.roll): how many generator blocks a player must break to hold
everything the Bronze stage asks for (Forge, Bronze Core, Bronze set, Coal Generator, Battery, Miner I and the iron
and copper that go with them). The ore chances, the pity counters and the Forge recipes are read from
survival-plugin/src/main/resources/db/seed/quarry.sql, so a balance change in the seed moves the result.

What is NOT measured (flat guesses, argued in docs/skyblock-notes/journey-5h.md): the speed of the player, the
tutorial and building time, the Hub and the Echo fight. Those depend on #21-#25 and on real play; the knobs are the
command line options below. Nothing here replaces a playtest.
"""
import argparse
import random
import re
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QUARRY = ROOT / "survival-plugin/src/main/resources/db/seed/quarry.sql"

# Vanilla iron gear and blocks, in ingots (the Forge recipes take the vanilla piece as an ingredient).
IRON_COST = {"IRON_HELMET": 5, "IRON_CHESTPLATE": 8, "IRON_LEGGINGS": 7, "IRON_BOOTS": 4, "IRON_PICKAXE": 3,
             "IRON_SWORD": 2, "IRON_AXE": 3, "IRON_SHOVEL": 1, "BUCKET": 3, "BLAST_FURNACE": 5, "FURNACE": 0}
BRONZE_SET = ["bronze_helmet", "bronze_chestplate", "bronze_leggings", "bronze_boots"]
STAGE = ["bronze_core", *BRONZE_SET, "coal_generator", "battery", "miner_1", "cable"]


def parse_seed():
    text = QUARRY.read_text()
    ores = []  # (id, pity, geode, {tier: chance}) in table order, cobble source only
    rows = re.findall(r"INSERT INTO ore_roll \(id, position, item, source, pity, geode\) VALUES \('(\w+)', (\d+), '[^']*', '(\w+)', (\d+), (\d+)\);", text)
    chances = {}
    for ore, tier, chance in re.findall(r"INSERT INTO ore_roll_chance \(ore_roll_id, tier, chance\) VALUES \('(\w+)', (\d+), ([\d.]+)\);", text):
        chances.setdefault(ore, {})[int(tier)] = float(chance)
    for oid, pos, source, pity, geode in sorted(rows, key=lambda r: int(r[1])):
        if source == "cobble":
            ores.append((oid, int(pity), bool(int(geode)), chances.get(oid, {})))
    recipes = {}
    for rid, seconds in re.findall(r"INSERT INTO forge_recipe \(id, position, result, amount, seconds\) VALUES \('(\w+)', \d+, '\w+', \d+, (\d+)\);", text):
        recipes[rid] = {"seconds": int(seconds), "in": {}}
    for rid, item, amount in re.findall(r"INSERT INTO forge_recipe_ingredient \(forge_recipe_id, position, item, amount\) VALUES \('(\w+)', \d+, '([^']+)', (\d+)\);", text):
        recipes[rid]["in"][item] = int(amount)
    settings = {k: float(v) for k, v in re.findall(r"INSERT INTO setting \(id, type, value, note\) VALUES \('([\w.\-]+)', '(?:int|real)', '([\d.]+)'", text)}
    return ores, recipes, settings


def shopping_list(recipes):
    """Raw needs of the Bronze stage: ores from the generator (counts) and iron ingots."""
    need = {"bronzite": 0, "iron_ingot": 0, "copper_ingot": 0, "coal": 0, "redstone": 0}
    # Forge block (quest text): a blast furnace + 4 iron + 4 coal; a bucket and an iron pickaxe for the quests; a second
    # iron pickaxe is eaten by the Miner I recipe.
    need["iron_ingot"] += IRON_COST["BLAST_FURNACE"] + 4 + IRON_COST["BUCKET"] + 2 * IRON_COST["IRON_PICKAXE"]
    need["coal"] += 4 + 8  # forge + a few for smelting the ingots
    for rid in STAGE:
        for item, amount in recipes[rid]["in"].items():
            if item == "custom:bronzite":
                need["bronzite"] += amount
            elif item == "IRON_INGOT":
                need["iron_ingot"] += amount
            elif item == "COPPER_INGOT":
                need["copper_ingot"] += amount
            elif item == "REDSTONE":
                need["redstone"] += amount
            elif item in IRON_COST:
                need["iron_ingot"] += IRON_COST[item] * amount
    # the miner burns coal: 1 per 40 s of a coal generator, about 30 minutes before the Echo
    need["coal"] += 45
    return need


def blocks_for(need, ores, rng):
    """Breaks generator blocks (tier I rules, tier II chances are the same for these ores) until the list is met."""
    targets = {"bronzite": need["bronzite"], "iron_nugget": need["iron_ingot"] * 9, "copper": need["copper_ingot"],
               "coal": need["coal"]}
    got = {k: 0 for k in targets}
    counters = {}
    best = next((o for o in ores if o[2] and o[3].get(1, 0) > 0), None)
    blocks = 0
    first_bronzite = {}
    while any(got[k] < targets[k] for k in targets):
        blocks += 1
        for oid, pity, _, ch in ores:
            if ch.get(1, 0) > 0:
                counters[oid] = counters.get(oid, 0) + 1
        if best and rng.random() * 100 < 0.05:  # geode
            counters[best[0]] = 0
            if best[0] in got:
                got[best[0]] += rng.randint(3, 6)
            continue
        for oid, pity, _, ch in ores:
            chance = ch.get(1, 0)
            if chance <= 0:
                continue
            forced = pity > 0 and counters.get(oid, 0) >= pity
            if forced or rng.random() * 100 < chance:
                counters[oid] = 0
                if oid in got:
                    got[oid] += 1
                break
        if blocks > 2_000_000:
            raise SystemExit("a need never fills: check the seed")
    return blocks


def forge_tail(recipes):
    """The bronze core is slow (and gated by 32 Bronzite): longest single job is what is left after the last ore."""
    return max(recipes[r]["seconds"] for r in STAGE)


def run(args):
    ores, recipes, settings = parse_seed()
    need = shopping_list(recipes)
    rng = random.Random(args.seed)
    samples = sorted(blocks_for(need, ores, rng) for _ in range(args.runs))
    pick = lambda p: samples[min(len(samples) - 1, int(p * len(samples)))]
    print("Shopping list of the Bronze stage (from quarry.sql):")
    print("  " + ", ".join(f"{k} {v}" for k, v in need.items()) + f"   (iron nuggets {need['iron_ingot'] * 9})")
    print(f"Blocks to break  p10 {pick(.1):,}  p50 {pick(.5):,}  p90 {pick(.9):,}  (mean {int(statistics.mean(samples)):,})")
    print(f"Forge tail {forge_tail(recipes) // 60} min after the last ore; Miner I {settings.get('quarry.machines.miner-1.cycles-per-second', 0.5)} cycles/s")
    print()
    print("rings  blocks/s  mining(h)  total p50(h)  total p90(h)   (total = start + build + mining + forge tail + hub + Echo)")
    verdict = None
    for rings in ([args.rings] if args.rings else [1, 2, 3]):
        production = rings * 1.0 + args.miners * settings.get("quarry.machines.miner-1.cycles-per-second", 0.5)
        rate = args.efficiency * min(args.hand, production)  # blocks per second really mined
        fixed = (args.start + args.build + args.hub + args.echo) * 60 + forge_tail(recipes)
        row = []
        for p in (.5, .9):
            row.append((pick(p) / rate + fixed) / 3600)
        print(f"{rings:>5}  {rate:8.2f}  {pick(.5) / rate / 3600:9.2f}  {row[0]:12.2f}  {row[1]:12.2f}")
        if rings == (args.rings or 2):
            verdict = row[0]
    print()
    print(f"Fixed minutes: start {args.start}, build {args.build}, hub {args.hub}, echo {args.echo} (guesses). "
          f"Player: {args.hand} blocks/s by hand, {args.efficiency:.0%} of the time mining, {args.miners} Miner I.")
    if args.check and not 4.0 <= verdict <= 6.0:
        print(f"FAIL: median {verdict:.2f} h is outside 4-6 h")
        return 1
    return 0


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--runs", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--rings", type=int, default=0, help="stone rings feeding the ore table (0 = show 1, 2 and 3)")
    ap.add_argument("--hand", type=float, default=2.0, help="blocks per second a player can break by hand")
    ap.add_argument("--efficiency", type=float, default=0.6, help="share of the time spent mining (the rest: crafting, walking)")
    ap.add_argument("--miners", type=int, default=0, help="Miner I running during the mining (they only arrive near the end)")
    ap.add_argument("--start", type=float, default=25, help="minutes: trees, pebbles, Fountain (A1-A3)")
    ap.add_argument("--build", type=float, default=25, help="minutes: rings, Forge, machines, quests that are not mining")
    ap.add_argument("--hub", type=float, default=25, help="minutes: door, NPCs, market tutorial, Echo Key (A9-A11)")
    ap.add_argument("--echo", type=float, default=25, help="minutes: the Faille and the Echo fight (A12-A13)")
    ap.add_argument("--check", action="store_true")
    sys.exit(run(ap.parse_args()))
