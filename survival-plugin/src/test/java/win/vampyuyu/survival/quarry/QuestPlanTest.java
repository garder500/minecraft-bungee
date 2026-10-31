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
import win.vampyuyu.survival.menu.MenuConfig;
import win.vampyuyu.survival.menu.QuestCatalog;
import win.vampyuyu.survival.menu.QuestDef;
import win.vampyuyu.survival.menu.QuestType;

/**
 * The gate of the quest plan: the shipped quests.yml must load completely (no quest silently dropped) and every quest
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
        "armor_set:starcore");
    private static final Set<String> COMMANDS = Set.of("guide", "market", "ah", "forge", "dungeon", "balance", "pay", "bank",
        "baltop");

    private SurvivalPlugin plugin;
    private QuestCatalog catalog;

    @Before
    public void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        var menu = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/menu.yml")));
        var plan = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/quests.yml")));
        catalog = new MenuConfig(menu, null, true, plan).catalog;
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void thePlanLoadsWithoutLosingAQuest() {
        assertTrue(catalog.problems().toString(), catalog.problems().isEmpty());
        assertTrue("at least 250 quests, got " + catalog.tutorial().size(), catalog.tutorial().size() >= 250);
        assertEquals(14, catalog.categories().size());
        long main = catalog.categories().stream().filter(QuestCatalog.Category::main).count();
        assertEquals(6, main);
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
        var quarry = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/quarry.yml")));
        var boss = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/boss.yml")));
        Set<String> forgeIds = quarry.getConfigurationSection("forge.recipes").getKeys(false);
        Set<String> oreIds = quarry.getConfigurationSection("ores").getKeys(false);
        Set<String> bosses = boss.getConfigurationSection("bosses").getKeys(false);
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
