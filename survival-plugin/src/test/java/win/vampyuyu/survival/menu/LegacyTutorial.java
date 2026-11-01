package win.vampyuyu.survival.menu;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * A small deterministic quest line for the quest service tests: the tutorial of the former YAML configuration, kept as a
 * test fixture (the shipped plan is in the content database and is covered by QuestFlowTest and QuestPlanTest).
 */
final class LegacyTutorial {
    private LegacyTutorial() {}

    static Map<String, QuestDef> plan() {
        Map<String, QuestDef> plan = new LinkedHashMap<>();
        add(plan, "create_island", "A Place to Call Home", "Create your island. Use the Skyblock Menu or the prompt in the hub.", QuestType.ISLAND_CREATE, "", 1, Material.GRASS_BLOCK, List.of(), 100.0, xp());
        add(plan, "visit_hub", "Meet the Hub", "Visit the hub through the Warps menu.", QuestType.VISIT_WORLD, "hub", 1, Material.BELL, List.of("create_island"), 50.0, xp());
        add(plan, "break_wood", "Timber!", "Break 8 logs.", QuestType.BREAK_BLOCK, "LOGS", 8, Material.OAK_LOG, List.of("create_island"), 50.0, xp("FORAGING", 20.0));
        add(plan, "craft_table", "Workbench", "Craft a crafting table.", QuestType.CRAFT_ITEM, "CRAFTING_TABLE", 1, Material.CRAFTING_TABLE, List.of("break_wood"), 50.0, xp("CARPENTRY", 20.0));
        add(plan, "craft_pickaxe", "First Tools", "Craft a wooden pickaxe.", QuestType.CRAFT_ITEM, "WOODEN_PICKAXE", 1, Material.WOODEN_PICKAXE, List.of("craft_table"), 50.0, xp("CARPENTRY", 20.0));
        add(plan, "mine_stone", "Rock Bottom", "Mine 24 stone blocks.", QuestType.BREAK_BLOCK, "STONE", 24, Material.COBBLESTONE, List.of("craft_pickaxe"), 100.0, xp("MINING", 30.0));
        add(plan, "farm_crops", "Farmer's Start", "Harvest 10 fully grown crops.", QuestType.BREAK_BLOCK, "CROPS", 10, Material.WHEAT, List.of("create_island"), 100.0, xp("FARMING", 30.0));
        add(plan, "first_blood", "First Blood", "Defeat 5 mobs.", QuestType.KILL_MOB, "ANY", 5, Material.IRON_SWORD, List.of("create_island"), 100.0, xp("COMBAT", 30.0));
        add(plan, "first_catch", "Something is Biting", "Catch 3 fish.", QuestType.FISH, "", 3, Material.FISHING_ROD, List.of("craft_table"), 100.0, xp("FISHING", 30.0));
        add(plan, "foraging_3", "Forester", "Reach Foraging level 3.", QuestType.SKILL_LEVEL, "FORAGING", 3, Material.JUNGLE_SAPLING, List.of("break_wood"), 200.0, xp());
        add(plan, "mining_3", "Miner", "Reach Mining level 3.", QuestType.SKILL_LEVEL, "MINING", 3, Material.STONE_PICKAXE, List.of("mine_stone"), 250.0, xp());
        add(plan, "farming_3", "Farmer", "Reach Farming level 3.", QuestType.SKILL_LEVEL, "FARMING", 3, Material.WOODEN_HOE, List.of("farm_crops"), 200.0, xp());
        add(plan, "combat_3", "Fighter", "Reach Combat level 3.", QuestType.SKILL_LEVEL, "COMBAT", 3, Material.STONE_SWORD, List.of("first_blood"), 250.0, xp());
        add(plan, "craft_minion", "Automation", "Craft your first minion.", QuestType.CRAFT_ITEM, "custom:re:minion_(?!fuel_|upgrade_|skin_|enchanted_|super_)[a-z_]+_[0-9]+", 1, Material.ARMOR_STAND, List.of("mining_3"), 500.0, xp("CARPENTRY", 80.0));
        add(plan, "earn_coins", "Pocket Change", "Earn 1,000 coins.", QuestType.COINS_EARN, "", 1000, Material.GOLD_NUGGET, List.of("create_island"), 100.0, xp());
        add(plan, "open_bazaar", "Open for Business", "Open the Bazaar.", QuestType.COMMAND, "bazaar", 1, Material.EMERALD, List.of("earn_coins"), 100.0, xp());
        add(plan, "open_auction", "Going Once", "Open the Auction House.", QuestType.COMMAND, "auctionhouse", 1, Material.GOLD_BLOCK, List.of("open_bazaar"), 100.0, xp());
        add(plan, "first_soul", "A Glimmer", "Collect your first Fairy Soul.", QuestType.COLLECT_SOUL, "", 1, Material.PINK_DYE, List.of("create_island"), 100.0, xp());
        add(plan, "five_souls", "Soul Collector", "Collect 5 Fairy Souls.", QuestType.COLLECT_SOUL, "", 5, Material.PINK_DYE, List.of("first_soul"), 300.0, xp());
        add(plan, "enchant_first", "Arcane Beginnings", "Enchant an item.", QuestType.ENCHANT_ITEM, "", 1, Material.ENCHANTING_TABLE, List.of("mining_3"), 200.0, xp("ENCHANTING", 40.0));
        add(plan, "skill_five", "Getting Serious", "Reach level 5 in any skill.", QuestType.SKILL_LEVEL, "ANY", 5, Material.EXPERIENCE_BOTTLE, List.of("mining_3"), 500.0, xp());
        add(plan, "visit_nether", "Into the Fire", "Visit the Nether.", QuestType.VISIT_WORLD, "nether", 1, Material.NETHERRACK, List.of("combat_3"), 300.0, xp());
        add(plan, "first_boss", "Boss Slayer", "Take part in defeating a boss.", QuestType.BOSS_KILL, "", 1, Material.WITHER_SKELETON_SKULL, List.of("combat_3"), 1000.0, xp("COMBAT", 300.0));
        return plan;
    }

    private static Map<String, Double> xp(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], (Double) pairs[i + 1]);
        }
        return out;
    }

    private static void add(Map<String, QuestDef> plan, String id, String name, String description, QuestType type, String target,
            int amount, Material icon, List<String> requires, double coins, Map<String, Double> skillXp) {
        plan.put(id, new QuestDef(id, name, description, type, target, amount, icon, requires,
            new QuestDef.Rewards(coins, skillXp, Map.of())));
    }
}
