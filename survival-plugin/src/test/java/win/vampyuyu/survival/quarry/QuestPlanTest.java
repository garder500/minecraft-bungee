package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.menu.QuestMatcher;
import win.vampyuyu.survival.menu.QuestRepo;
import win.vampyuyu.survival.menu.QuestCatalog;
import win.vampyuyu.survival.menu.QuestDef;
import win.vampyuyu.survival.menu.QuestType;

/**
 * The gate of the quest plan: the shipped quest seed must load completely (no quest silently dropped) and every quest
 * must be completable by something that exists. A quest nobody can finish would block the rest of its category.
 */
public class QuestPlanTest {
    /** Keys fired by the plugin as EVENT quest progress (QuarryProgressEvent kind EVENT). */
    private static final Set<String> EVENT_KEYS = Set.of("pebble", "spring_filled", "forge_placed", "machine_mined",
        "machine_placed", "machine_placed:fountain", "machine_placed:spring_stone", "machine_placed:cable",
        "machine_placed:battery", "machine_placed:coal_generator", "machine_placed:solar_panel", "machine_placed:miner_1",
        "machine_placed:miner_2", "generator_built", "generator_built:stone", "generator_built:dunes",
        "generator_built:frost", "generator_built:ember", "dungeon_clear", "dungeon_clear_nodeath", "dungeon_clear_party",
        "dungeon_floor:1", "dungeon_floor:2", "dungeon_floor:3", "dungeon_floor:4", "dungeon_floor:5", "dungeon_floor:6",
        "dungeon_floor:7", "quest_done", "armor_set:bronze", "armor_set:rime", "armor_set:ember", "armor_set:void",
        "armor_set:starcore", "grove_enter", "grove_affinity:1", "grove_affinity:2",
        "heights_enter", "heights_affinity:1", "heights_affinity:2",
        "marsh_enter", "marsh_salvage", "marsh_purify:1", "marsh_purify:2");
    private static final Set<String> COMMANDS = Set.of("guide", "market", "ah", "forge", "dungeon", "balance", "pay", "bank",
        "baltop");

    /** Things a crafting table never makes (furnace or stonecutter output): a CRAFT_ITEM quest on them never ends. */
    private static final Set<String> NOT_CRAFTED = Set.of("GLASS", "STONE", "SMOOTH_STONE", "BRICK", "NETHER_BRICK",
        "CHARCOAL", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "BAKED_POTATO", "DRIED_KELP", "GREEN_DYE",
        "NETHERITE_SCRAP", "COPPER_INGOT", "GOLD_INGOT", "TERRACOTTA", "SMOOTH_SANDSTONE", "CRACKED_STONE_BRICKS",
        "SPONGE", "POPPED_CHORUS_FRUIT");

    private static boolean isGroup(String target) {
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (!m.isLegacy() && QuestMatcher.inGroup(target, m)) return true;
        }
        return false;
    }

    private SurvivalPlugin plugin;
    private QuestCatalog catalog;

    @Before
    public void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        catalog = QuestRepo.load(plugin.core().data());
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void thePlanLoadsWithoutLosingAQuest() {
        assertTrue(catalog.problems().toString(), catalog.problems().isEmpty());
        assertTrue("at least 250 quests, got " + catalog.tutorial().size(), catalog.tutorial().size() >= 250);
        assertEquals(17, catalog.categories().size());
        long main = catalog.categories().stream().filter(QuestCatalog.Category::main).count();
        assertEquals(9, main);
        for (QuestCatalog.Category category : catalog.categories()) {
            assertFalse(category.id() + " is empty", catalog.questsOf(category.id()).isEmpty());
        }
        assertEquals("every quest is in a category", catalog.tutorial().size(),
            catalog.categories().stream().mapToInt(c -> catalog.questsOf(c.id()).size()).sum());
    }

    @Test
    public void everyRewardItemExists() {
        for (QuestDef def : catalog.tutorial()) {
            for (String item : def.rewards().items().keySet()) {
                assertTrue(def.id() + " rewards the unknown item " + item, plugin.core().items().def(item).isPresent());
            }
        }
    }

    @Test
    public void everyQuestHasSomethingThatCompletesIt() {
        var quarry = plugin.core().require(QuarryService.class).config();
        Set<String> forgeIds = quarry.recipes.keySet();
        Set<String> oreIds = new HashSet<>();
        quarry.roller.ores().forEach(o -> oreIds.add(o.id()));
        quarry.outputs.ores().forEach(o -> oreIds.add(o.id())); // dropped by the ring generators
        Set<String> bosses = win.vampyuyu.survival.boss.BossTestData.shipped().bosses().keySet();
        Set<String> bad = new HashSet<>();
        for (QuestDef def : catalog.tutorial()) {
            String t = def.target();
            switch (def.type()) {
                case EVENT -> {
                    if (!EVENT_KEYS.contains(t)) bad.add(def.id() + " event " + t);
                }
                case FORGE_ITEM -> {
                    if (!forgeIds.contains(t) || plugin.core().items().def(t).isEmpty()) bad.add(def.id() + " forge " + t);
                }
                case ORE_FOUND -> {
                    if (!oreIds.contains(t)) bad.add(def.id() + " ore " + t);
                }
                case BOSS_KILL -> {
                    if (!t.isEmpty() && !bosses.contains(t)) bad.add(def.id() + " boss " + t);
                }
                case COMMAND -> {
                    if (!COMMANDS.contains(t)) bad.add(def.id() + " command " + t);
                }
                case CRAFT_ITEM -> {
                    if (t.startsWith("custom:") && plugin.core().items().def(t.substring(7)).isEmpty()) {
                        bad.add(def.id() + " custom " + t);
                    }
                    if (NOT_CRAFTED.contains(t)) bad.add(def.id() + " asks to craft " + t + ", which is smelted: use OBTAIN_ITEM");
                }
                case OBTAIN_ITEM, PLACE_BLOCK, BREAK_BLOCK -> {
                    if (!t.isEmpty() && !t.startsWith("custom:") && org.bukkit.Material.matchMaterial(t) == null
                            && !isGroup(t)) {
                        bad.add(def.id() + " target " + t + " is neither a material nor a group");
                    }
                }
                case GENERATOR_TIER -> {
                    if (def.amount() < 2 || def.amount() > 4) bad.add(def.id() + " tier " + def.amount());
                }
                case GENERATOR_UPGRADE -> bad.add(def.id() + " is an event that can be missed: use GENERATOR_TIER");
                case SKILL_LEVEL, COLLECT_SOUL, ENCHANT_ITEM, ISLAND_ENTER -> bad.add(def.id() + " type " + def.type());
                default -> { }
            }
        }
        assertTrue(bad.toString(), bad.isEmpty());
    }

    /** Progress is stored by quest id: a shipped id must keep meaning the same quest (quest_ids.txt). */
    @Test
    public void shippedQuestIdsNeverChangeMeaning() throws java.io.IOException {
        List<String> lines;
        try (var in = getClass().getResourceAsStream("/quest_ids.txt")) {
            lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        }
        java.util.Set<String> retired = new java.util.HashSet<>();
        for (String line : lines) {
            if (line.startsWith("retired ")) {
                retired.add(line.substring(8));
            }
        }
        for (String line : lines) {
            String[] f = line.split(" ", 4);
            if (f[0].equals("retired") || retired.contains(f[0])) {
                assertTrue(line + " was retired: its id must stay unused",
                    catalog.tutorialQuest(f[f[0].equals("retired") ? 1 : 0]).isEmpty());
                continue;
            }
            QuestDef def = catalog.tutorialQuest(f[0]).orElseThrow(() -> new AssertionError(f[0] + " disappeared"));
            assertEquals(f[0] + " category", f[1], def.category());
            assertEquals(f[0] + " type", f[2], def.type().name());
            assertEquals(f[0] + " target", f.length > 3 ? f[3] : "", def.target());
        }
    }

    @Test
    public void aRewardIsNeverAnItemTheNextQuestsMakeYouGetAnyway() {
        java.util.Set<String> skinsGiven = new java.util.HashSet<>();
        for (QuestDef def : catalog.tutorial()) {
            for (String item : def.rewards().items().keySet()) {
                assertTrue(def.id() + " gives " + item + ", which a Forge quest asks for", catalog.tutorial().stream()
                    .noneMatch(other -> other.type() == QuestType.FORGE_ITEM && other.target().equals(item)));
                if (item.startsWith("skin_")) {
                    assertTrue("two quests give " + item + ", a skin unlocks once", skinsGiven.add(item));
                }
            }
        }
    }

    @Test
    public void theQuestCommandsAreRealCommands() {
        for (String name : COMMANDS) {
            assertTrue(name + " is not a command", org.bukkit.Bukkit.getCommandMap().getCommand(name) != null);
        }
    }

    @Test
    public void everyCategoryUnlocksFromAQuestThatExists() {
        for (QuestCatalog.Category category : catalog.categories()) {
            if (!category.unlock().isEmpty()) {
                assertTrue(category.id() + " unlocks from " + category.unlock(), catalog.tutorialQuest(category.unlock()).isPresent());
            }
        }
        var first = catalog.questsOf("start").get(0);
        assertTrue("the plan starts with a quest that needs nothing", first.requires().isEmpty());
        assertEquals(QuestType.BREAK_BLOCK, first.type());
    }
}
