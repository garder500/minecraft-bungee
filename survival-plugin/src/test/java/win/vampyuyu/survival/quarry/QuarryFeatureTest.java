package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.Island;
import win.vampyuyu.survival.island.IslandService;

public class QuarryFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService islands;
    private QuarryService quarry;
    private World sky;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        islands = (IslandService) plugin.core().require(IslandApi.class);
        quarry = plugin.core().require(QuarryService.class);
        sky = Bukkit.getWorld(SkyblockWorlds.WORLD);
        assertNotNull(sky);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private Island island(PlayerMock owner) {
        Island island = islands.registry().create(owner.getUniqueId(), owner.getName(), "classic", System.currentTimeMillis());
        island.state(Island.State.READY);
        island.platformsReady = true;
        return island;
    }

    private Block blockOn(Island island) {
        return sky.getBlockAt(islands.grid().centerOf(island.cellX) + 3, 65, islands.grid().centerOf(island.cellZ) + 3);
    }

    private void formCobble(Block block) {
        block.setType(Material.COBBLESTONE);
        server.getPluginManager().callEvent(new BlockFormEvent(block, block.getState()));
    }

    private List<Item> dropped() {
        return sky.getEntitiesByClass(Item.class).stream().toList();
    }

    @Test
    public void featureLoadsAndRegistersItsItems() {
        assertTrue(plugin.core().items().def("bronzite").isPresent());
        assertTrue(plugin.core().items().def("forge").isPresent());
        assertNotNull(Bukkit.getCommandMap().getCommand("forge"));
    }

    @Test
    public void generatedCobbleRollsThePityOreAndPlacedCobbleDoesNot() {
        PlayerMock owner = server.addPlayer("Miner");
        Island island = island(owner);
        quarry.data(owner.getUniqueId()).counters.put("bronzite", 119);
        Block placed = blockOn(island);
        placed.setType(Material.COBBLESTONE);
        server.getPluginManager().callEvent(new BlockBreakEvent(placed, owner));
        assertTrue("placed cobble gives nothing extra", dropped().isEmpty());
        assertEquals(0L, quarry.data(owner.getUniqueId()).blocks);

        Block generated = sky.getBlockAt(placed.getX() + 1, 65, placed.getZ());
        formCobble(generated);
        server.getPluginManager().callEvent(new BlockBreakEvent(generated, owner));
        assertEquals(1L, quarry.data(owner.getUniqueId()).blocks);
        assertTrue("the pity forced a bronzite",
            dropped().stream().anyMatch(i -> plugin.core().items().idOf(i.getItemStack()).filter("bronzite"::equals).isPresent()));
        // the mark is gone: breaking it again (it is the same cobble) rolls nothing
        server.getPluginManager().callEvent(new BlockBreakEvent(generated, owner));
        assertEquals(1L, quarry.data(owner.getUniqueId()).blocks);
    }

    @Test
    public void generatedStoneFromFallingLavaRollsTheTableToo() {
        PlayerMock owner = server.addPlayer("StoneGen");
        Island island = island(owner);
        quarry.data(owner.getUniqueId()).counters.put("bronzite", 119);
        Block block = blockOn(island);
        block.setType(Material.STONE);
        server.getPluginManager().callEvent(new BlockFormEvent(block, block.getState()));
        server.getPluginManager().callEvent(new BlockBreakEvent(block, owner));
        assertEquals(1L, quarry.data(owner.getUniqueId()).blocks);
        assertTrue(dropped().stream().anyMatch(i -> plugin.core().items().idOf(i.getItemStack()).filter("bronzite"::equals).isPresent()));
    }

    @Test
    public void theEndStaysSealedUntilTheVoidLensIsUsed() {
        PlayerMock owner = server.addPlayer("Explorer");
        Island island = island(owner);
        island.endReady = true;
        var locked = islands.shortcut(owner, false);
        assertFalse(locked.ok());
        assertTrue(locked.message(), locked.message().contains("Void Lens"));
        assertFalse(quarry.canEnterEnd(owner.getUniqueId()));
        assertTrue(quarry.unlockEnd(owner.getUniqueId()));
        assertFalse("the lens works once", quarry.unlockEnd(owner.getUniqueId()));
        var open = islands.shortcut(owner, false);
        assertFalse(open.message(), open.message().contains("Void Lens"));
        assertTrue(plugin.core().items().def("void_lens").isPresent());
        assertTrue(quarry.config().recipes.containsKey("void_lens"));
    }

    @Test
    public void theVoidLensNeedsTierThreeThenOpensTheEndAndTierFour() {
        PlayerMock owner = server.addPlayer("Seeker");
        Island island = island(owner);
        var lens = plugin.core().items().create("void_lens");
        owner.getInventory().setItemInMainHand(lens);
        owner.teleport(new org.bukkit.Location(sky, islands.grid().centerOf(island.cellX) + 2, 70, islands.grid().centerOf(island.cellZ) + 2));
        var click = new org.bukkit.event.player.PlayerInteractEvent(owner, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, lens, null,
            org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(click);
        assertFalse("tier I: the lens refuses", quarry.canEnterEnd(owner.getUniqueId()));
        quarry.upgrade(owner.getUniqueId(), 2);
        quarry.upgrade(owner.getUniqueId(), 3);
        server.getPluginManager().callEvent(click);
        assertTrue(quarry.canEnterEnd(owner.getUniqueId()));
        assertEquals(4, quarry.tier(owner.getUniqueId()));
    }

    @Test
    public void thePackAndTheForgeKnowEveryItemOfTheRecipes() {
        for (String id : List.of("voidglass", "starcore", "solar_panel", "void_lens", "skin_crystal_saber")) {
            assertTrue(id, plugin.core().items().def(id).isPresent());
        }
        for (String id : List.of("warden_heart", "frost_shard", "leviathan_tooth")) {
            assertTrue(id + " is a boss key now", plugin.core().items().def(id).get().name().endsWith("Key"));
        }
        assertTrue(quarry.config().recipes.get("void_sigil").cost().containsKey("custom:starcore"));
    }

    @Test
    public void nothingFiresOutsideSkyblock() {
        PlayerMock owner = server.addPlayer("Farmer");
        island(owner);
        World survival = server.addSimpleWorld("world");
        Block block = survival.getBlockAt(0, 65, 0);
        block.setType(Material.COBBLESTONE);
        server.getPluginManager().callEvent(new BlockFormEvent(block, block.getState()));
        server.getPluginManager().callEvent(new BlockBreakEvent(block, owner));
        assertEquals(0L, quarry.data(owner.getUniqueId()).blocks);
        assertTrue(survival.getEntitiesByClass(Item.class).isEmpty());
    }

    @Test
    public void theBronzeCorePathAndTierTwoTable() {
        PlayerMock owner = server.addPlayer("Upgrader");
        island(owner);
        assertEquals(1, quarry.tier(owner.getUniqueId()));
        assertTrue(quarry.upgrade(owner.getUniqueId(), 2));
        assertFalse(quarry.upgrade(owner.getUniqueId(), 2));
        assertTrue(quarry.upgrade(owner.getUniqueId(), 3));
        assertTrue(quarry.upgrade(owner.getUniqueId(), 4));
        assertFalse("there is no tier V", quarry.upgrade(owner.getUniqueId(), 5));
        assertEquals(4, quarry.tier(owner.getUniqueId()));
    }

    @Test
    public void theForgeTakesMaterialsAllOrNothingAndPaysAfterTheTimer() {
        PlayerMock player = server.addPlayer("Smith");
        ItemStack bronzite = plugin.core().items().create("bronzite", 11);
        player.getInventory().addItem(bronzite, new ItemStack(Material.IRON_SWORD));
        long now = System.currentTimeMillis();
        assertNotNull("11 bronzite is not enough", quarry.start(player, "bronze_cleaver", now));
        assertEquals(0, quarry.data(player.getUniqueId()).jobs.size());
        player.getInventory().addItem(plugin.core().items().create("bronzite", 1));
        assertNull(quarry.start(player, "bronze_cleaver", now));
        assertFalse("the sword and the ore were consumed", player.getInventory().contains(Material.IRON_SWORD));
        assertTrue("not ready yet", quarry.collect(player, now + 1000).isEmpty());
        assertEquals(1, quarry.data(player.getUniqueId()).jobs.size());
        List<String> got = quarry.collect(player, now + 601_000);
        assertEquals(List.of("Bronze Cleaver"), got);
        assertTrue(quarry.data(player.getUniqueId()).jobs.isEmpty());
        assertTrue(player.getInventory().contains(Material.IRON_SWORD));
        assertNotNull(plugin.core().items().idOf(player.getInventory().getItem(player.getInventory().first(Material.IRON_SWORD))));
    }

    @Test
    public void everyConfiguredRecipeAndOreResolvesToARegisteredItem() {
        for (var recipe : quarry.config().recipes.values()) {
            assertTrue(recipe.id(), plugin.core().items().def(recipe.result()).isPresent());
            for (String key : recipe.cost().keySet()) {
                assertTrue(key, quarry.stack(key, 1).isPresent());
            }
        }
        for (var ore : quarry.config().roller.ores()) {
            assertTrue(ore.id(), quarry.stack(ore.item(), 1).isPresent());
        }
        for (int tier = 1; tier <= quarry.config().tiers; tier++) {
            assertNotNull("a geode ore exists at tier " + tier, quarry.config().roller.best(tier));
        }
    }

    @Test
    public void questsFollowTheDrawTheForgeAndTheGeneratorUpgrade() {
        PlayerMock owner = server.addPlayer("Quester");
        Island island = island(owner);
        var events = new java.util.ArrayList<String>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(win.vampyuyu.survival.api.event.QuarryProgressEvent e) {
                events.add(e.getKind() + ":" + e.getSubject() + ":" + e.getAmount());
            }
        }, plugin);
        quarry.data(owner.getUniqueId()).counters.put("bronzite", 119);
        Block generated = blockOn(island);
        formCobble(generated);
        server.getPluginManager().callEvent(new BlockBreakEvent(generated, owner));
        assertTrue(events.toString(), events.contains("ORE_FOUND:bronzite:1"));
        long now = System.currentTimeMillis();
        owner.getInventory().addItem(plugin.core().items().create("bronzite", 32), new ItemStack(Material.IRON_INGOT, 8));
        assertNull(quarry.start(owner, "bronze_core", now));
        quarry.collect(owner, now + 2_000_000, id -> events.add("FORGE_ITEM:" + id));
        assertTrue(events.toString(), events.contains("FORGE_ITEM:bronze_core"));
    }

    @Test
    public void theQuarryQuestsAreAppendedToTheTutorialLine() {
        var menu = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.InputStreamReader(getClass().getResourceAsStream("/menu.yml")));
        var quarryYml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.InputStreamReader(getClass().getResourceAsStream("/quarry.yml")));
        var config = new win.vampyuyu.survival.menu.MenuConfig(menu, quarryYml.getConfigurationSection("quests.tutorial"));
        var catalog = config.catalog;
        for (String id : List.of("lucky_cobble", "build_forge", "find_bronzite", "forge_core", "upgrade_vein", "forge_ember")) {
            assertTrue(id, catalog.tutorialQuest(id).isPresent());
        }
        assertTrue(catalog.problems().toString(), catalog.problems().isEmpty());
        assertEquals(win.vampyuyu.survival.menu.QuestType.ORE_FOUND, catalog.tutorialQuest("find_bronzite").get().type());
    }

    @Test
    public void quarryItemsCannotBeSmeltedLikeTheirVanillaLookAlike() {
        Block furnace = sky.getBlockAt(3, 70, 3);
        furnace.setType(Material.FURNACE);
        ItemStack bronzite = plugin.core().items().create("bronzite");
        var cook = new org.bukkit.event.block.BlockCookEvent(furnace, bronzite, new ItemStack(Material.COPPER_INGOT));
        server.getPluginManager().callEvent(cook);
        assertTrue("custom raw copper is not smelted", cook.isCancelled());
        var plain = new org.bukkit.event.block.BlockCookEvent(furnace, new ItemStack(Material.RAW_COPPER), new ItemStack(Material.COPPER_INGOT));
        server.getPluginManager().callEvent(plain);
        assertFalse("vanilla raw copper still smelts", plain.isCancelled());
    }

    @Test
    public void onlyThreeJobsRunAtOnce() {
        PlayerMock player = server.addPlayer("Busy");
        player.getInventory().addItem(plugin.core().items().create("bronzite", 64), new ItemStack(Material.IRON_PICKAXE, 4));
        long now = System.currentTimeMillis();
        assertNull(quarry.start(player, "bronze_pick", now));
        assertNull(quarry.start(player, "bronze_pick", now));
        assertNull(quarry.start(player, "bronze_pick", now));
        assertEquals("All your Forge slots are busy.", quarry.start(player, "bronze_pick", now));
    }
}
