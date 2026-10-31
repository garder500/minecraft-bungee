#!/usr/bin/env python3
"""Writes survival-plugin/src/main/resources/quests.yml: the Skyblock quest plan (categories and quests).

    python3 scripts/gen_quests.py

The plan (docs/skyblock-notes/progression.md) is the spec. Every quest uses an existing quest type or the keyed
EVENT type; the events are fired by the quarry, dungeon and quest code (see QuarryProgressEvent users). A test loads
the written file and checks that every quest can be completed. In-game text is English.
"""
import pathlib

OUT = pathlib.Path(__file__).resolve().parent.parent / "survival-plugin/src/main/resources/quests.yml"

CATEGORIES = []


def cat(cid, name, blurb, main, unlock, icon):
    """Main path categories are one chain; the others open all at once after their unlock quest."""
    c = {"id": cid, "name": name, "blurb": blurb, "main": main, "unlock": unlock, "icon": icon, "quests": []}
    CATEGORIES.append(c)
    return c


def q(c, name, desc, typ, target, amount, icon, coins, items=None, label=None):
    c["quests"].append(dict(name=name, desc=desc, type=typ, target=target, amount=amount, icon=icon, coins=coins,
                            items=items or {}, label=label))


def craft(c, name, what, mat, amount, icon, coins, **kw):
    q(c, name, f"Craft {what}.", "CRAFT_ITEM", mat, amount, icon, coins, **kw)


def brk(c, name, what, target, amount, icon, coins, **kw):
    q(c, name, f"Break {what}.", "BREAK_BLOCK", target, amount, icon, coins, **kw)


def ev(c, name, desc, key, amount, icon, coins, **kw):
    q(c, name, desc, "EVENT", key, amount, icon, coins, **kw)


def forge(c, name, item, amount, icon, coins, **kw):
    q(c, name, f"Collect {'a' if amount == 1 else amount} {item.replace('_', ' ')} from the Forge (/forge).", "FORGE_ITEM",
      item, amount, icon, coins, **kw)


def ore(c, name, what, ore_id, amount, icon, coins, **kw):
    where = {"frostium": "the blocks of your Frost generator", "sunstone": "the blocks of your Ember generator"}.get(ore_id, "your generator")
    q(c, name, f"Find {what} in {where}.", "ORE_FOUND", ore_id, amount, icon, coins, **kw)


def kill(c, name, what, mob, amount, icon, coins, **kw):
    q(c, name, f"Defeat {what}.", "KILL_MOB", mob, amount, icon, coins, **kw)


def earn(c, name, amount, icon, coins, **kw):
    q(c, name, f"Earn {amount:,} coins.", "COINS_EARN", "", amount, icon, coins, **kw)


def cmd(c, name, desc, command, icon, coins, **kw):
    q(c, name, desc, "COMMAND", command, 1, icon, coins, **kw)


# --------------------------------------------------------------------------------------------- main path
c = cat("start", "The Beginning", "Slow and safe. You learn the basics, pick up pebbles and finish with your Fountain.", True, None, "OAK_SAPLING")
brk(c, "First Tree", "8 logs", "LOGS", 8, "OAK_LOG", 20)
craft(c, "Planks", "4 planks", "PLANKS", 4, "OAK_PLANKS", 20)
craft(c, "Workbench", "a crafting table", "CRAFTING_TABLE", 1, "CRAFTING_TABLE", 20)
craft(c, "Sticks", "4 sticks", "STICK", 4, "STICK", 20)
craft(c, "Wooden Pickaxe", "a wooden pickaxe", "WOODEN_PICKAXE", 1, "WOODEN_PICKAXE", 20)
ev(c, "Pebbles", "Right-click the ground of your island with an empty hand to pick up 9 Stone Pebbles.", "pebble", 9, "FLINT", 30)
craft(c, "First Cobblestone", "a cobblestone from 9 pebbles", "COBBLESTONE", 1, "COBBLESTONE", 20)
craft(c, "Stone Pickaxe", "a stone pickaxe", "STONE_PICKAXE", 1, "STONE_PICKAXE", 30)
craft(c, "Wooden Sword", "a wooden sword", "WOODEN_SWORD", 1, "WOODEN_SWORD", 20)
q(c, "Plant a Sapling", "Plant a sapling.", "PLACE_BLOCK", "SAPLINGS", 1, "OAK_SAPLING", 20)
brk(c, "First Harvest", "8 fully grown crops", "CROPS", 8, "WHEAT", 30)
craft(c, "Bread", "bread", "BREAD", 1, "BREAD", 20)
craft(c, "The Fountain", "a Fountain from 4 cobblestone and 4 logs", "custom:fountain", 1, "SMOOTH_STONE", 100, label="fountain_craft")
ev(c, "Place the Fountain", "Place your Fountain on your island. It makes a cobblestone above itself every 2 seconds.", "machine_placed:fountain", 1, "SMOOTH_STONE", 50)
brk(c, "Fountain Cobble", "64 cobblestone", "COBBLESTONE", 64, "COBBLESTONE", 50)
brk(c, "Fountain Cobble II", "128 more cobblestone", "COBBLESTONE", 128, "COBBLESTONE", 80)

c = cat("generator", "The First Real Generator", "The Spring Stone and the stone generator: a ring around your Fountain. A wrong ring never costs you anything.", True, "fountain_craft", "STONE_BRICKS")
cmd(c, "Read the Plan", "Read the generator plan with /guide.", "guide", "WRITABLE_BOOK", 20, items={"spring_stone": 1}, label="read_plan")
ev(c, "Spring Stone", "Place the Spring Stone on the south side of your Fountain's ring.", "machine_placed:spring_stone", 1, "PRISMARINE_BRICKS", 40)
craft(c, "Stone Bricks", "16 stone bricks", "STONE_BRICKS", 16, "STONE_BRICKS", 30)
q(c, "The Ring", "Place 8 stone bricks around your Fountain.", "PLACE_BLOCK", "STONE_BRICKS", 8, "STONE_BRICKS", 60)
ev(c, "Stone Generator", "Complete the ring: your Fountain becomes a Stone generator (right-click it to check what is missing).", "generator_built:stone", 1, "FURNACE", 120, label="stone_gen")
brk(c, "Mine the Generator", "64 cobblestone from it", "COBBLESTONE", 64, "IRON_PICKAXE", 50)
ore(c, "Iron Nugget", "your first iron nugget", "iron_nugget", 1, "IRON_NUGGET", 30)
ore(c, "Nugget Stash", "9 iron nuggets", "iron_nugget", 9, "IRON_NUGGET", 50)
craft(c, "Iron Ingot", "an iron ingot", "IRON_INGOT", 1, "IRON_INGOT", 50)
craft(c, "First Bucket", "a bucket", "BUCKET", 1, "BUCKET", 80)
ev(c, "Bottomless Water", "Fill your bucket from the Spring Stone.", "spring_filled", 1, "WATER_BUCKET", 50)
ore(c, "Copper", "16 raw copper", "copper", 16, "RAW_COPPER", 50)
ore(c, "Coal", "16 coal", "coal", 16, "COAL", 50)
ore(c, "Gold", "a gold nugget", "gold_nugget", 1, "GOLD_NUGGET", 60)
ore(c, "Bronze Age", "your first Bronzite", "bronzite", 1, "RAW_COPPER", 100, items={"bronze_core": 1}, label="bronzite_1")
brk(c, "Mine 250", "250 cobblestone", "COBBLESTONE", 250, "COBBLESTONE", 100)
brk(c, "Mine 500", "500 cobblestone", "COBBLESTONE", 500, "COBBLESTONE", 150)
craft(c, "Smelter", "a furnace", "FURNACE", 1, "FURNACE", 40)
craft(c, "Storage", "two chests", "CHEST", 2, "CHEST", 40)
craft(c, "Iron Pickaxe", "an iron pickaxe", "IRON_PICKAXE", 1, "IRON_PICKAXE", 100, label="iron_pick")

c = cat("variety", "Different Generators", "Dunes for sand and gravel, frost for ice and Frostium. The market opens.", True, "stone_gen", "SAND")
cmd(c, "Visit the Market", "Open the market with /market.", "market", "EMERALD", 20, items={"generator_plan": 1})
earn(c, "First Sales", 200, "GOLD_NUGGET", 60)
cmd(c, "Dune Plan", "Read the plans of the other generators with /guide.", "guide", "WRITABLE_BOOK", 30, label="dune_plan")
craft(c, "Sandstone", "16 sandstone", "SANDSTONE", 16, "SANDSTONE", 40)
q(c, "Dune Ring", "Place 8 sandstone blocks around a second Fountain.", "PLACE_BLOCK", "SANDSTONE", 8, "SANDSTONE", 80)
ev(c, "Dune Generator", "Complete the sandstone ring: a Dune generator makes sand, gravel and red sand.", "generator_built:dunes", 1, "SAND", 150, label="dune_gen")
brk(c, "Sand", "64 sand", "SAND", 64, "SAND", 60)
brk(c, "Gravel", "64 gravel", "GRAVEL", 64, "GRAVEL", 60)
brk(c, "Red Sand", "16 red sand", "RED_SAND", 16, "RED_SAND", 40)
craft(c, "Glass", "8 glass", "GLASS", 8, "GLASS", 50)
craft(c, "Concrete", "8 concrete powder", "WHITE_CONCRETE_POWDER", 8, "WHITE_CONCRETE_POWDER", 60)
craft(c, "More Glass", "16 glass", "GLASS", 16, "GLASS", 60)
q(c, "Frost Ring", "Place 8 glass blocks around a third Fountain.", "PLACE_BLOCK", "GLASS", 8, "GLASS", 100)
ev(c, "Frost Generator", "Complete the glass ring: a Frost generator makes snow blocks, packed ice and Frostium.", "generator_built:frost", 1, "PACKED_ICE", 200, label="frost_gen")
brk(c, "Snow", "32 snow blocks", "SNOW_BLOCK", 32, "SNOW_BLOCK", 60)
brk(c, "Ice", "16 packed ice", "PACKED_ICE", 16, "PACKED_ICE", 80)
ore(c, "Frostium", "your first Frostium", "frostium", 1, "PRISMARINE_CRYSTALS", 100)
ore(c, "Frostium Haul", "8 Frostium", "frostium", 8, "PRISMARINE_CRYSTALS", 200, items={"rime_pick": 1})
ev(c, "Second Spring", "Place 2 more Spring Stones.", "machine_placed:spring_stone", 2, "PRISMARINE_BRICKS", 100)
ev(c, "More Fountains", "Place 3 more Fountains.", "machine_placed:fountain", 3, "SMOOTH_STONE", 300)

c = cat("tiers", "Raise the Generators", "The Forge, the cores, tiers I to IV. This is where progress speeds up.", True, "bronzite_1", "AMETHYST_SHARD")
ev(c, "The Forge", "Craft a Forge with a blast furnace, 4 iron ingots and 4 coal, then place it.", "forge_placed", 1, "BLAST_FURNACE", 100, label="forge_placed")
cmd(c, "Open the Forge", "Open the Forge menu with /forge.", "forge", "BLAST_FURNACE", 30)
forge(c, "First Forging", "bronze_core", 1, "AMETHYST_SHARD", 80)
q(c, "Raise the Generator", "Reach generator tier II: forge a Bronze Core and right-click it on your island.", "GENERATOR_TIER", "", 2, "AMETHYST_SHARD", 150, label="tier2")
brk(c, "Tier II Mining", "500 more cobblestone", "COBBLESTONE", 500, "COBBLESTONE", 150)
ore(c, "Bronzite Hoard", "12 Bronzite", "bronzite", 12, "RAW_COPPER", 150)
ore(c, "Diamond", "a diamond", "diamond", 1, "DIAMOND", 200)
forge(c, "Ember Core", "ember_core", 1, "FIRE_CHARGE", 250)
q(c, "Tier III", "Reach generator tier III: forge an Ember Core and right-click it on your island.", "GENERATOR_TIER", "", 3, "FIRE_CHARGE", 400, items={"endless_bucket": 1}, label="tier3")
ore(c, "Ancient Debris", "ancient debris", "ancient_debris", 1, "ANCIENT_DEBRIS", 400)
brk(c, "Mine 2,000", "2,000 cobblestone", "COBBLESTONE", 2000, "COBBLESTONE", 300)
forge(c, "Void Lens", "void_lens", 1, "ENDER_EYE", 500)
q(c, "The Void Lens", "Reach generator tier IV: right-click the Void Lens on your island. It also opens your End (/island end).", "GENERATOR_TIER", "", 4, "ENDER_EYE", 800, label="tier4")
ore(c, "Voidglass", "a Voidglass", "voidglass", 1, "ECHO_SHARD", 500)
ore(c, "Starcore", "a Starcore Shard", "starcore", 1, "NETHER_STAR", 800)
brk(c, "Mine 5,000", "5,000 cobblestone", "COBBLESTONE", 5000, "COBBLESTONE", 500)

c = cat("nether", "The Nether and the Ember", "A portal, the Ember generator, quartz and soul sand.", True, "tier2", "NETHERRACK")
cmd(c, "Nether Plan", "Read the Nether plan with /guide.", "guide", "WRITABLE_BOOK", 50, label="nether_plan")
craft(c, "Flint and Steel", "flint and steel", "FLINT_AND_STEEL", 1, "FLINT_AND_STEEL", 80)
q(c, "Light a Portal", "Place 10 obsidian.", "PLACE_BLOCK", "OBSIDIAN", 10, "OBSIDIAN", 100)
q(c, "Enter the Nether", "Enter your Nether.", "VISIT_WORLD", "nether", 1, "NETHERRACK", 150)
brk(c, "Netherrack", "64 netherrack", "NETHERRACK", 64, "NETHERRACK", 100)
craft(c, "Nether Bricks", "16 nether bricks", "NETHER_BRICKS", 16, "NETHER_BRICKS", 100)
q(c, "Magma Block", "Craft a magma block from 4 netherrack and 1 coal.", "CRAFT_ITEM", "MAGMA_BLOCK", 1, "MAGMA_BLOCK", 100)
q(c, "Ember Ring", "Place 8 nether bricks around a Fountain.", "PLACE_BLOCK", "NETHER_BRICKS", 8, "NETHER_BRICKS", 150)
ev(c, "Ember Generator", "Complete the ring with a magma block on the south side: an Ember generator makes netherrack and soul sand.", "generator_built:ember", 1, "MAGMA_BLOCK", 300, label="ember_gen")
craft(c, "Quartz Block", "a quartz block from 4 nether quartz (from the Ember generator or the market)", "QUARTZ_BLOCK", 1, "QUARTZ", 120)
brk(c, "Soul Sand", "32 soul sand", "SOUL_SAND", 32, "SOUL_SAND", 120)
ore(c, "Ember Sunstone", "a Sunstone", "sunstone", 1, "GLOWSTONE_DUST", 200)
brk(c, "Wart", "16 nether wart", "NETHER_WART", 16, "NETHER_WART", 120)
kill(c, "Magma Cubes", "10 magma cubes (the Ember Spawn of the Magma Warden)", "MAGMA_CUBE", 10, "MAGMA_CREAM", 250)
kill(c, "Piglin Hunter", "20 zombified piglins", "ZOMBIFIED_PIGLIN", 20, "GOLD_INGOT", 200)
brk(c, "Netherrack Digger", "256 netherrack", "NETHERRACK", 256, "NETHERRACK", 200)
brk(c, "Soul Digger", "128 soul sand", "SOUL_SAND", 128, "SOUL_SAND", 300)
brk(c, "Mine 1,000 Netherrack", "1,000 netherrack", "NETHERRACK", 1000, "NETHERRACK", 300)

c = cat("end", "The End and the Void", "The Void Lens opens /island end. The Voidwyrm guards the Void.", True, "tier3", "END_STONE")
q(c, "Enter the End", "Enter your End with /island end (needs the Void Lens).", "VISIT_WORLD", "end", 1, "END_STONE", 500)
brk(c, "End Stone", "64 end stone", "END_STONE", 64, "END_STONE", 200)
kill(c, "Void Mites", "15 endermites (the Void Mites of the Voidwyrm)", "ENDERMITE", 15, "ENDER_PEARL", 250)
craft(c, "End Bricks", "16 end stone bricks", "END_STONE_BRICKS", 16, "END_STONE_BRICKS", 200)
forge(c, "Void Sigil", "void_sigil", 1, "NETHER_STAR", 400, label="sigil")
q(c, "Face the Voidwyrm", "Defeat the Voidwyrm.", "BOSS_KILL", "voidwyrm", 1, "DRAGON_HEAD", 1500, label="voidwyrm_1")
q(c, "Voidwyrm Again", "Defeat the Voidwyrm 3 times.", "BOSS_KILL", "voidwyrm", 3, "DRAGON_HEAD", 2000)
q(c, "Voidwyrm Master", "Defeat the Voidwyrm 10 times.", "BOSS_KILL", "voidwyrm", 10, "DRAGON_HEAD", 4000, items={"skin_wyrmfang": 1})

# --------------------------------------------------------------------------------------------- parallel
c = cat("farm", "Farming and Food", "A calm category. Play it alongside the generators.", False, "fountain_craft", "WHEAT")
craft(c, "Hoe", "a wooden hoe", "WOODEN_HOE", 1, "WOODEN_HOE", 20)
q(c, "Till the Soil", "Plant 16 crops or seeds.", "PLACE_BLOCK", "CROPS", 16, "WHEAT_SEEDS", 20)
brk(c, "Wheat", "32 wheat", "WHEAT", 32, "WHEAT", 40)
brk(c, "Carrots", "16 carrots", "CARROTS", 16, "CARROT", 40)
brk(c, "Potatoes", "16 potatoes", "POTATOES", 16, "POTATO", 40)
craft(c, "Loaves", "8 bread", "BREAD", 8, "BREAD", 30)
q(c, "Plant a Forest", "Plant 8 saplings.", "PLACE_BLOCK", "SAPLINGS", 8, "OAK_SAPLING", 30)
brk(c, "Lumber", "64 logs", "LOGS", 64, "OAK_LOG", 50)
brk(c, "Leaf Blower", "64 oak leaves", "OAK_LEAVES", 64, "OAK_LEAVES", 30)
craft(c, "Hay Bale", "a hay bale", "HAY_BLOCK", 1, "HAY_BLOCK", 50)
brk(c, "Sugar Cane", "32 sugar cane", "SUGAR_CANE", 32, "SUGAR_CANE", 50)
craft(c, "Golden Carrot", "a golden carrot from gold nuggets and a carrot", "GOLDEN_CARROT", 1, "GOLDEN_CARROT", 80)
brk(c, "Melons", "16 melons", "MELON", 16, "MELON_SLICE", 50)
brk(c, "Pumpkins", "8 pumpkins", "PUMPKIN", 8, "PUMPKIN", 50)
q(c, "Farm Plot", "Plant 64 crops or seeds.", "PLACE_BLOCK", "CROPS", 64, "WHEAT_SEEDS", 100)
brk(c, "Harvest 256", "256 crops", "CROPS", 256, "WHEAT", 150)
craft(c, "Composter", "a composter", "COMPOSTER", 1, "COMPOSTER", 60)
craft(c, "Golden Apple", "a golden apple", "GOLDEN_APPLE", 1, "GOLDEN_APPLE", 150)
brk(c, "Harvest 1,000", "1,000 crops", "CROPS", 1000, "HAY_BLOCK", 300)
q(c, "Fisher", "Catch 20 fish.", "FISH", "", 20, "FISHING_ROD", 120)

c = cat("forging", "Forge and Gear", "Everything the Forge makes, from the bronze tools to the boss keys.", False, "forge_placed", "BLAST_FURNACE")
forge(c, "Bronze Cleaver", "bronze_cleaver", 1, "IRON_SWORD", 100)
forge(c, "Bronze Pick", "bronze_pick", 1, "IRON_PICKAXE", 100)
forge(c, "Rimeblade", "rimeblade", 1, "IRON_SWORD", 200)
forge(c, "Rime Pick", "rime_pick", 1, "IRON_PICKAXE", 200)
forge(c, "Solar Panel", "solar_panel", 1, "DAYLIGHT_DETECTOR", 150)
forge(c, "Copper Cable", "cable", 4, "LIGHTNING_ROD", 100)
forge(c, "Coal Generator", "coal_generator", 1, "STONECUTTER", 100)
forge(c, "Battery", "battery", 1, "WAXED_COPPER_BLOCK", 120)
forge(c, "Miner I", "miner_1", 1, "LODESTONE", 200)
forge(c, "Miner II", "miner_2", 1, "LODESTONE", 400)
forge(c, "Magma Core", "magma_core", 1, "MAGMA_CREAM", 250)
forge(c, "Frost Heart", "frost_heart", 1, "BLUE_ICE", 250)
forge(c, "Matriarch Egg", "matriarch_egg", 1, "SPIDER_EYE", 250)
forge(c, "Leviathan Scale", "leviathan_scale", 1, "PRISMARINE_SHARD", 300)
forge(c, "Crystal Saber Skin", "skin_crystal_saber", 1, "PAPER", 300)
forge(c, "Forge 5", "bronze_core", 5, "AMETHYST_SHARD", 200)
forge(c, "Forge 10 Cores", "bronze_core", 10, "AMETHYST_SHARD", 400)
forge(c, "Ember Cores", "ember_core", 3, "FIRE_CHARGE", 500)
forge(c, "Void Lenses", "void_lens", 2, "ENDER_EYE", 800)
forge(c, "Solar Farm", "solar_panel", 5, "DAYLIGHT_DETECTOR", 400)
forge(c, "Miners at Work", "miner_1", 3, "LODESTONE", 500)
forge(c, "Battery Bank", "battery", 5, "WAXED_COPPER_BLOCK", 400)
for tier, coins, icon in (("bronze", 80, "CHAINMAIL"), ("rime", 150, "IRON"), ("ember", 300, "DIAMOND"), ("void", 600, "NETHERITE"), ("starcore", 1000, "NETHERITE")):
    for slot in ("helmet", "chestplate", "leggings", "boots"):
        forge(c, f"{tier.title()} {slot.title()}", f"{tier}_{slot}", 1, f"{icon}_{slot.upper()}", coins)
    ev(c, f"{tier.title()} Armor", f"Wear a full set of {tier} armor: helmet, chestplate, leggings and boots.", f"armor_set:{tier}", 1, f"{icon}_CHESTPLATE", coins * 2,
       items=({"skin_titan_edge": 1} if tier == "starcore" else {}))
for tier, coins in (("ember", 400), ("void", 800), ("starcore", 1500)):
    forge(c, f"{tier.title()} Sword", f"{tier}_sword", 1, "NETHERITE_SWORD" if tier != "ember" else "DIAMOND_SWORD", coins)
    forge(c, f"{tier.title()} Pick", f"{tier}_pick", 1, "NETHERITE_PICKAXE" if tier != "ember" else "DIAMOND_PICKAXE", coins)

c = cat("machines", "Machines and Energy", "Coal, cables on redstone, batteries, solar panels, then Miners that break the generator for you.", False, "stone_gen", "REDSTONE")
ev(c, "Coal Power", "Place a Coal Generator.", "machine_placed:coal_generator", 1, "STONECUTTER", 100)
ev(c, "Cables", "Place 8 Copper Cables.", "machine_placed:cable", 8, "LIGHTNING_ROD", 80)
ev(c, "Store It", "Place a Battery.", "machine_placed:battery", 1, "WAXED_COPPER_BLOCK", 80)
ev(c, "Sunlight", "Place a Solar Panel under the open sky.", "machine_placed:solar_panel", 1, "DAYLIGHT_DETECTOR", 100)
ev(c, "Miner I", "Place a Miner I next to your generator.", "machine_placed:miner_1", 1, "LODESTONE", 150)
ev(c, "Miner II", "Place a Miner II.", "machine_placed:miner_2", 1, "LODESTONE", 300)
ev(c, "Two Batteries", "Place 3 more Batteries.", "machine_placed:battery", 3, "WAXED_COPPER_BLOCK", 150)
ev(c, "A Real Network", "Place 20 more Copper Cables.", "machine_placed:cable", 20, "LIGHTNING_ROD", 200)
ev(c, "Solar Farm", "Place 4 more Solar Panels.", "machine_placed:solar_panel", 4, "DAYLIGHT_DETECTOR", 300)
ev(c, "More Miners", "Place 3 more Miner I.", "machine_placed:miner_1", 3, "LODESTONE", 300)
ev(c, "Mining Crew", "Place 3 more Miner II.", "machine_placed:miner_2", 3, "LODESTONE", 500)
ev(c, "Coal Plant", "Place 3 more Coal Generators.", "machine_placed:coal_generator", 3, "STONECUTTER", 300)
ev(c, "Machine Mined 100", "Let your Miners break 100 generator blocks.", "machine_mined", 100, "IRON_PICKAXE", 200)
ev(c, "Machine Mined 500", "Let your Miners break 500 generator blocks.", "machine_mined", 500, "IRON_PICKAXE", 400)
ev(c, "Machine Mined 2,000", "Let your Miners break 2,000 generator blocks.", "machine_mined", 2000, "IRON_PICKAXE", 600)
ev(c, "Machine Mined 5,000", "Let your Miners break 5,000 generator blocks.", "machine_mined", 5000, "DIAMOND_PICKAXE", 1000)
ev(c, "Machine Mined 20,000", "Let your Miners break 20,000 generator blocks.", "machine_mined", 20000, "NETHERITE_PICKAXE", 2000)
ev(c, "Machine Park", "Place 10 more machines of any kind.", "machine_placed", 10, "REDSTONE", 400)
craft(c, "Hopper", "a hopper", "HOPPER", 1, "HOPPER", 150)
craft(c, "Lever", "a lever", "LEVER", 1, "LEVER", 80)

c = cat("market", "Market and Economy", "Sell, buy and auction. The market never sells or buys ultra-rare items.", False, "stone_gen", "EMERALD")
cmd(c, "Browse the Auction House", "Open the auction house with /ah.", "ah", "GOLD_INGOT", 30)
earn(c, "Pocket Money", 500, "GOLD_NUGGET", 50)
earn(c, "Merchant", 2000, "GOLD_INGOT", 100)
earn(c, "Trader", 5000, "GOLD_INGOT", 150)
earn(c, "Dealer", 10000, "GOLD_BLOCK", 200)
earn(c, "Wholesaler", 25000, "GOLD_BLOCK", 400)
earn(c, "Tycoon", 100000, "EMERALD", 1000)
earn(c, "Magnate", 500000, "EMERALD_BLOCK", 2000)
earn(c, "Millionaire", 1000000, "DIAMOND", 5000)
cmd(c, "Check Your Purse", "Check your balance with /balance.", "balance", "GOLD_NUGGET", 20)
cmd(c, "Pay a Friend", "Pay someone with /pay.", "pay", "PAPER", 30)
cmd(c, "Bank Visit", "Open your bank with /bank.", "bank", "CHEST", 40)
cmd(c, "Top Players", "Open the balance leaderboard with /baltop.", "baltop", "PLAYER_HEAD", 30)
earn(c, "Steady Income", 50000, "EMERALD", 600)
earn(c, "Big Spender", 250000, "EMERALD", 1500)
q(c, "Selling Spree", "Earn 1,500 more coins.", "COINS_EARN", "", 1500, "GOLD_INGOT", 200)
q(c, "Selling Spree II", "Earn 15,000 more coins.", "COINS_EARN", "", 15000, "GOLD_BLOCK", 500)
q(c, "Selling Spree III", "Earn 75,000 more coins.", "COINS_EARN", "", 75000, "EMERALD", 1200)
q(c, "Selling Spree IV", "Earn 150,000 more coins.", "COINS_EARN", "", 150000, "EMERALD_BLOCK", 2500)

c = cat("dungeons", "Dungeons", "Rooms, secrets and boss keys. Floors 6 and 7 hold the Wing Cores.", False, "iron_pick", "DEEPSLATE_BRICKS")
cmd(c, "Find the Dungeon", "Open the dungeon menu with /dungeon.", "dungeon", "DEEPSLATE_BRICKS", 40)
ev(c, "First Clear", "Clear a dungeon floor.", "dungeon_clear", 1, "DEEPSLATE_BRICKS", 150, label="first_clear")
ev(c, "Floor 1", "Clear floor 1.", "dungeon_floor:1", 1, "DEEPSLATE_BRICKS", 100)
ev(c, "Floor 2", "Clear floor 2.", "dungeon_floor:2", 1, "DEEPSLATE_BRICKS", 150)
ev(c, "Floor 3", "Clear floor 3.", "dungeon_floor:3", 1, "DEEPSLATE_BRICKS", 200)
ev(c, "Floor 4", "Clear floor 4.", "dungeon_floor:4", 1, "DEEPSLATE_BRICKS", 300)
ev(c, "Floor 5", "Clear floor 5.", "dungeon_floor:5", 1, "DEEPSLATE_BRICKS", 400)
ev(c, "Floor 6", "Clear floor 6.", "dungeon_floor:6", 1, "CRACKED_DEEPSLATE_BRICKS", 600)
ev(c, "Floor 7", "Clear floor 7.", "dungeon_floor:7", 1, "CRACKED_DEEPSLATE_BRICKS", 800)
ev(c, "Flawless", "Clear a floor without dying.", "dungeon_clear_nodeath", 1, "TOTEM_OF_UNDYING", 400)
ev(c, "Team Run", "Clear a floor with a party.", "dungeon_clear_party", 1, "PLAYER_HEAD", 300)
ev(c, "Regular", "Clear 5 floors.", "dungeon_clear", 5, "DEEPSLATE_BRICKS", 500)
ev(c, "Veteran", "Clear 15 floors.", "dungeon_clear", 15, "DEEPSLATE_BRICKS", 800)
ev(c, "Dungeon Master", "Clear 30 floors.", "dungeon_clear", 30, "DEEPSLATE_BRICKS", 1500, items={"skin_titan_edge": 1})
ev(c, "Flawless x5", "Clear 5 floors without dying.", "dungeon_clear_nodeath", 5, "TOTEM_OF_UNDYING", 800)
ev(c, "Party x5", "Clear 5 floors with a party.", "dungeon_clear_party", 5, "PLAYER_HEAD", 600)
kill(c, "Zombies", "50 zombies", "ZOMBIE", 50, "ROTTEN_FLESH", 150)
kill(c, "Skeletons", "50 skeletons", "SKELETON", 50, "BONE", 150)
kill(c, "Spiders", "30 spiders", "SPIDER", 30, "STRING", 150)
kill(c, "Creepers", "20 creepers", "CREEPER", 20, "GUNPOWDER", 150)

c = cat("bosses", "The Bosses", "Eight bosses of the world, each with its own silhouette and its glowing weak spot.", False, "first_clear", "DRAGON_HEAD")
q(c, "Magma Warden", "Defeat the Magma Warden.", "BOSS_KILL", "magma_warden", 1, "MAGMA_BLOCK", 500)
q(c, "Frost Revenant", "Defeat the Frost Revenant.", "BOSS_KILL", "frost_revenant", 1, "BLUE_ICE", 500)
q(c, "Sylvan Matriarch", "Defeat the Sylvan Matriarch.", "BOSS_KILL", "sylvan_matriarch", 1, "SPIDER_EYE", 600)
q(c, "Abyssal Leviathan", "Defeat the Abyssal Leviathan.", "BOSS_KILL", "abyssal_leviathan", 1, "PRISMARINE_SHARD", 700)
q(c, "Any Boss x3", "Defeat 3 bosses.", "BOSS_KILL", "", 3, "DRAGON_HEAD", 400)
q(c, "Any Boss x10", "Defeat 10 bosses.", "BOSS_KILL", "", 10, "DRAGON_HEAD", 1000)
q(c, "Any Boss x25", "Defeat 25 bosses.", "BOSS_KILL", "", 25, "DRAGON_HEAD", 2500, items={"skin_flame_brand": 1})
q(c, "Magma Warden x3", "Defeat the Magma Warden 3 times.", "BOSS_KILL", "magma_warden", 3, "MAGMA_BLOCK", 800)
q(c, "Frost Revenant x3", "Defeat the Frost Revenant 3 times.", "BOSS_KILL", "frost_revenant", 3, "BLUE_ICE", 800)
q(c, "Matriarch x3", "Defeat the Sylvan Matriarch 3 times.", "BOSS_KILL", "sylvan_matriarch", 3, "SPIDER_EYE", 900)
q(c, "Leviathan x3", "Defeat the Abyssal Leviathan 3 times.", "BOSS_KILL", "abyssal_leviathan", 3, "PRISMARINE_SHARD", 1000)
q(c, "Crypt Lord", "Defeat the Crypt Lord, the boss of the dungeon floors (/dungeon).", "BOSS_KILL", "crypt_lord", 1, "SKELETON_SKULL", 800)
q(c, "Crypt Lord x3", "Defeat the Crypt Lord 3 times.", "BOSS_KILL", "crypt_lord", 3, "SKELETON_SKULL", 1100)
q(c, "Any Boss x50", "Defeat 50 bosses.", "BOSS_KILL", "", 50, "DRAGON_HEAD", 5000, items={"skin_kitsune": 1})
q(c, "Any Boss x100", "Defeat 100 bosses.", "BOSS_KILL", "", 100, "DRAGON_HEAD", 10000)
forge(c, "Summon Items", "magma_core", 2, "MAGMA_CREAM", 400)

c = cat("mastery", "Mastery", "Long goals for players who have done it all.", False, "tier3", "NETHER_STAR")
q(c, "Quest Starter", "Complete 10 more quests.", "EVENT", "quest_done", 10, "BOOK", 200)
q(c, "Quest Hunter", "Complete 25 more quests.", "EVENT", "quest_done", 25, "BOOK", 500)
q(c, "Quest Master", "Complete 50 more quests.", "EVENT", "quest_done", 50, "ENCHANTED_BOOK", 1000)
q(c, "Quest Legend", "Complete 100 more quests.", "EVENT", "quest_done", 100, "ENCHANTED_BOOK", 3000)
brk(c, "Quarryman", "10,000 blocks of any kind", "", 10000, "DIAMOND_PICKAXE", 1000)
brk(c, "Quarry Titan", "50,000 blocks of any kind", "", 50000, "NETHERITE_PICKAXE", 3000)
brk(c, "Ore Hunter", "250 ore blocks", "ORES", 250, "IRON_ORE", 600)
kill(c, "Monster Slayer", "200 mobs of any kind", "", 200, "IRON_SWORD", 600)
kill(c, "Monster Slayer II", "1,000 mobs of any kind", "", 1000, "DIAMOND_SWORD", 2000)
ore(c, "Bronzite 50", "50 Bronzite", "bronzite", 50, "RAW_COPPER", 800)
ore(c, "Frostium 25", "25 Frostium", "frostium", 25, "PRISMARINE_CRYSTALS", 1000)
ore(c, "Sunstone 10", "10 Sunstone", "sunstone", 10, "GLOWSTONE_DUST", 1500)
ore(c, "Diamonds 20", "20 diamonds", "diamond", 20, "DIAMOND", 1000)
ore(c, "Debris 5", "5 ancient debris", "ancient_debris", 5, "ANCIENT_DEBRIS", 1500)
ore(c, "Voidglass 3", "3 Voidglass", "voidglass", 3, "ECHO_SHARD", 3000)
ore(c, "Starcore 2", "2 Starcore Shards", "starcore", 2, "NETHER_STAR", 5000)
ev(c, "Floor 7 Again", "Clear floor 7 three more times.", "dungeon_floor:7", 3, "CRACKED_DEEPSLATE_BRICKS", 2000)
ev(c, "Generators Everywhere", "Complete 3 more generator rings of any kind.", "generator_built", 3, "SMOOTH_STONE", 1500)

# --------------------------------------------------------------------------------------------- daily style
c = cat("challenges", "Challenges", "Repeatable-feeling goals: bigger numbers, once each.", False, "stone_gen", "TARGET")
brk(c, "Daily Miner", "200 blocks", "STONE", 200, "STONE_PICKAXE", 50)
brk(c, "Daily Woodcutter", "100 logs", "LOGS", 100, "OAK_LOG", 50)
brk(c, "Daily Farmer", "100 crops", "CROPS", 100, "WHEAT", 50)
kill(c, "Daily Hunter", "40 mobs", "", 40, "IRON_SWORD", 50)
craft(c, "Daily Crafter", "20 planks", "PLANKS", 20, "OAK_PLANKS", 50)
earn(c, "Daily Earner", 1000, "GOLD_NUGGET", 50)
brk(c, "Weekly Miner", "2,000 blocks", "STONE", 2000, "IRON_PICKAXE", 300)
q(c, "Weekly Hunter", "Defeat 3 bosses.", "BOSS_KILL", "", 3, "DRAGON_HEAD", 400)
ev(c, "Weekly Raider", "Clear 3 dungeon floors.", "dungeon_clear", 3, "DEEPSLATE_BRICKS", 400)
earn(c, "Weekly Earner", 5000, "GOLD_INGOT", 300)
brk(c, "Weekly Digger", "3,000 blocks", "STONE", 3000, "AMETHYST_SHARD", 500)
brk(c, "Hour of Power", "1,000 blocks", "STONE", 1000, "DIAMOND_PICKAXE", 300)
kill(c, "Hundred Kills", "100 mobs", "", 100, "IRON_SWORD", 300)
brk(c, "Lumber Baron", "1,000 logs", "LOGS", 1000, "OAK_LOG", 400)
brk(c, "Farm Baron", "1,000 crops", "CROPS", 1000, "HAY_BLOCK", 400)
craft(c, "Hundred Planks", "100 planks", "PLANKS", 100, "OAK_PLANKS", 200)
craft(c, "Iron Works", "16 iron ingots", "IRON_INGOT", 16, "IRON_INGOT", 200)
craft(c, "Diamond Works", "a diamond pickaxe", "DIAMOND_PICKAXE", 1, "DIAMOND_PICKAXE", 400)
q(c, "Fishing Day", "Catch 50 fish.", "FISH", "", 50, "FISHING_ROD", 300)
q(c, "Fishing Week", "Catch 250 fish.", "FISH", "", 250, "FISHING_ROD", 1000)
brk(c, "Digger", "5,000 blocks", "STONE", 5000, "NETHERITE_PICKAXE", 1000)
kill(c, "Zombie Wave", "100 zombies", "ZOMBIE", 100, "ROTTEN_FLESH", 300)
kill(c, "Skeleton Wave", "100 skeletons", "SKELETON", 100, "BONE", 300)
kill(c, "Spider Wave", "60 spiders", "SPIDER", 60, "STRING", 300)
kill(c, "Creeper Wave", "40 creepers", "CREEPER", 40, "GUNPOWDER", 300)
kill(c, "Endermite Wave", "40 endermites", "ENDERMITE", 40, "ENDER_PEARL", 400)
kill(c, "Zombie Siege", "200 zombies", "ZOMBIE", 200, "ROTTEN_FLESH", 400)


def yaml_str(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    ids = {}
    n = 0
    for category in CATEGORIES:
        for item in category["quests"]:
            n += 1
            item["id"] = f"q{n:03d}"
            if item["label"]:
                ids[item["label"]] = item["id"]
    lines = ["# Generated by scripts/gen_quests.py from docs/skyblock-notes/progression.md. Edit the script, not this file.",
             "# Main path categories are chains (each quest needs the one before it); the others open all their quests after the unlock quest.",
             "plan: true", "categories:"]
    for category in CATEGORIES:
        first = category["quests"][0]["id"]
        last = category["quests"][-1]["id"]
        unlock = ids.get(category["unlock"]) if category["unlock"] else None
        lines += [f"  {category['id']}:", f"    name: {yaml_str(category['name'])}", f"    description: {yaml_str(category['blurb'])}",
                  f"    main: {'true' if category['main'] else 'false'}", f"    icon: {category['icon']}",
                  f"    unlock: {unlock if unlock else '\"\"'}"]
    lines.append("quests:")
    lines.append("  tutorial:")
    for category in CATEGORIES:
        unlock = ids.get(category["unlock"]) if category["unlock"] else None
        previous = None
        for item in category["quests"]:
            chained = category["main"]
            requires = [previous] if previous and chained else ([unlock] if unlock else [])
            previous = item["id"]
            lines += [f"    {item['id']}:", f"      name: {yaml_str(item['name'])}", f"      description: {yaml_str(item['desc'])}",
                      f"      category: {category['id']}", f"      type: {item['type']}"]
            if item["target"]:
                lines.append(f"      target: {yaml_str(item['target'])}")
            lines += [f"      amount: {item['amount']}", f"      icon: {item['icon']}",
                      f"      requires: [{', '.join(requires)}]"]
            rewards = f"coins: {item['coins']}" if item["coins"] else "coins: 0"
            if item["items"]:
                rewards += ", items: {" + ", ".join(f"{k}: {v}" for k, v in item["items"].items()) + "}"
            lines.append(f"      rewards: {{{rewards}}}")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {n} quests in {len(CATEGORIES)} categories to {OUT}")


if __name__ == "__main__":
    main()
