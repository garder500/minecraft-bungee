package win.vampyuyu.survival.quarry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;

/** Tier gear: every piece exists and has a Forge recipe; a full set fires the quest event; the Endless Bucket and pebbles. */
public class GearTest {
    private ServerMock server;
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void everyTierPieceExistsWithAForgeRecipeAndAnEquipmentAsset() {
        QuarryService quarry = plugin.core().require(QuarryService.class);
        for (String tier : List.of("bronze", "rime", "ember", "void", "starcore")) {
            for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                String id = tier + "_" + slot;
                assertTrue(id, plugin.core().items().def(id).isPresent());
                assertNotNull(id + " has a Forge recipe", quarry.config().recipes.get(id));
                assertTrue(id + " is wearable", plugin.core().items().create(id).getItemMeta().hasEquippable());
            }
        }
        for (String id : List.of("ember_sword", "void_sword", "starcore_sword", "ember_pick", "void_pick", "starcore_pick",
                "endless_bucket", "generator_plan", "pebble", "fountain", "spring_stone")) {
            assertTrue(id, plugin.core().items().def(id).isPresent());
        }
    }

    @Test
    public void aFullSetOfOneTierFiresTheQuestEventOnce() {
        PlayerMock player = server.addPlayer("Knight");
        List<String> seen = new ArrayList<>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(QuarryProgressEvent event) {
                seen.add(event.getKind() + ":" + event.getSubject());
            }
        }, plugin);
        player.getInventory().setHelmet(plugin.core().items().create("rime_helmet"));
        player.getInventory().setChestplate(plugin.core().items().create("rime_chestplate"));
        player.getInventory().setLeggings(plugin.core().items().create("rime_leggings"));
        player.getInventory().setBoots(plugin.core().items().create("rime_boots"));
        Bukkit.getPluginManager().callEvent(new PlayerArmorChangeEvent(player, PlayerArmorChangeEvent.SlotType.FEET,
            new ItemStack(org.bukkit.Material.AIR), plugin.core().items().create("rime_boots")));
        assertEquals(List.of("EVENT:armor_set:rime"), seen);
        seen.clear();
        player.getInventory().setBoots(plugin.core().items().create("bronze_boots"));
        Bukkit.getPluginManager().callEvent(new PlayerArmorChangeEvent(player, PlayerArmorChangeEvent.SlotType.FEET,
            plugin.core().items().create("rime_boots"), plugin.core().items().create("bronze_boots")));
        assertFalse("a mixed set counts as nothing", seen.stream().anyMatch(s -> s.startsWith("EVENT:armor_set")));
    }

    @Test
    public void frostAndEmberBlocksDropTheirOre() {
        PlayerMock player = server.addPlayer("Miner");
        org.bukkit.World sky = Bukkit.getWorld(win.vampyuyu.survival.core.SkyblockWorlds.WORLD);
        assertNotNull(sky);
        var islands = (win.vampyuyu.survival.island.IslandService) plugin.core().require(win.vampyuyu.survival.api.IslandApi.class);
        var island = islands.registry().create(player.getUniqueId(), player.getName(), "classic", System.currentTimeMillis());
        island.state(win.vampyuyu.survival.island.Island.State.READY);
        island.platformsReady = true;
        int bx = islands.grid().centerOf(island.cellX) + 3;
        int bz = islands.grid().centerOf(island.cellZ) + 3;
        BlockMarks marks = new BlockMarks("genout");
        List<String> dropped = new ArrayList<>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(QuarryProgressEvent event) {
                if (event.getKind().equals("ORE_FOUND")) {
                    dropped.add(event.getSubject());
                }
            }
        }, plugin);
        // 6% and 3% per block: a few hundred blocks make a miss astronomically unlikely
        for (int i = 0; i < 600; i++) {
            org.bukkit.block.Block frost = sky.getBlockAt(bx + i % 20, 70 + i / 20, bz);
            frost.setType(org.bukkit.Material.SNOW_BLOCK);
            marks.add(frost);
            assertTrue("the mark is readable", marks.has(frost));
            server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(frost, player));
            org.bukkit.block.Block ember = sky.getBlockAt(bx + i % 20, 70 + i / 20, bz + 4);
            ember.setType(org.bukkit.Material.NETHERRACK);
            marks.add(ember);
            server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(ember, player));
        }
        assertTrue("frostium dropped", dropped.contains("frostium"));
        assertTrue("sunstone dropped", dropped.contains("sunstone"));
        assertFalse("a Fountain's plain cobble never drops them", dropped.contains("starcore"));
    }

    @Test
    public void theEndlessBucketIsNeverEmptiedAndThePlanReadsTheGuide() {
        PlayerMock player = server.addPlayer("Waterer");
        ItemStack bucket = plugin.core().items().create("endless_bucket");
        player.getInventory().setItemInMainHand(bucket);
        org.bukkit.World world = server.addSimpleWorld("water_test");
        org.bukkit.block.Block block = world.getBlockAt(0, 70, 0);
        var empty = new org.bukkit.event.player.PlayerBucketEmptyEvent(player, block, block, org.bukkit.block.BlockFace.UP,
            org.bukkit.Material.WATER_BUCKET, bucket, org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(empty);
        assertEquals("endless_bucket", plugin.core().items().idOf(empty.getItemStack()).orElse("?"));
        assertEquals(org.bukkit.Material.WATER_BUCKET, empty.getItemStack().getType());

        ItemStack plan = plugin.core().items().create("generator_plan");
        player.getInventory().setItemInMainHand(plan);
        var click = new org.bukkit.event.player.PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, plan,
            null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(click);
        assertTrue("the plan item is used up by the guide, not placed or dropped", click.isCancelled());
    }

    @Test
    public void aPlayerWhoStartedBeforeThePlanKeepsWhatTheirDataProves() {
        PlayerMock player = server.addPlayer("Veteran");
        org.bukkit.World sky = Bukkit.getWorld(win.vampyuyu.survival.core.SkyblockWorlds.WORLD);
        player.teleport(sky.getSpawnLocation());
        QuarryData data = plugin.core().require(QuarryService.class).data(player.getUniqueId());
        data.generatorTier = 3;
        data.blocks = 600;
        player.performCommand("quests categories");
        var quests = plugin.core().profiles().section(player.getUniqueId(), "quests",
            win.vampyuyu.survival.menu.QuestData.class, win.vampyuyu.survival.menu.QuestData::new);
        assertTrue(quests.planMigrated);
        var catalog = new win.vampyuyu.survival.menu.MenuConfig(
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/menu.yml"))), null, true,
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/quests.yml")))).catalog;
        for (var def : catalog.tutorial()) {
            if (def.type() == win.vampyuyu.survival.menu.QuestType.GENERATOR_TIER) {
                assertEquals(def.id(), def.amount() <= 3, quests.completed.contains(def.id()));
            }
            if (def.type() == win.vampyuyu.survival.menu.QuestType.BREAK_BLOCK && def.target().equals("COBBLESTONE")) {
                assertEquals(def.id(), def.amount() <= 600, quests.completed.contains(def.id()));
            }
        }
        assertFalse("nothing else is handed out", quests.completed.contains("q001"));
    }

    @Test
    public void thePebbleRecipeIsTheOnlyOneThatMayTakeAQuarryItem() {
        var pebbles = new org.bukkit.inventory.ShapelessRecipe(new org.bukkit.NamespacedKey(plugin, QuarryFeature.PEBBLE_RECIPE),
            new ItemStack(org.bukkit.Material.COBBLESTONE));
        var other = new org.bukkit.inventory.ShapelessRecipe(new org.bukkit.NamespacedKey(plugin, "something_else"),
            new ItemStack(org.bukkit.Material.COBBLESTONE));
        assertTrue(QuarryGuard.isPebbleRecipe(pebbles));
        assertFalse(QuarryGuard.isPebbleRecipe(other));
        assertFalse("a missing recipe lets the guard clear the result", QuarryGuard.isPebbleRecipe(null));
        assertNotNull("the recipe is registered", Bukkit.getRecipe(new org.bukkit.NamespacedKey(plugin, QuarryFeature.PEBBLE_RECIPE)));
    }
}
