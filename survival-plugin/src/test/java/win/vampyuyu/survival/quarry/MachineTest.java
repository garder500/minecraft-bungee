package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
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

public class MachineTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService islands;
    private QuarryService quarry;
    private MachineService machines;
    private World sky;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        islands = (IslandService) plugin.core().require(IslandApi.class);
        quarry = plugin.core().require(QuarryService.class);
        machines = plugin.core().require(MachineService.class);
        sky = Bukkit.getWorld(SkyblockWorlds.WORLD);
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

    private Block base(Island island) {
        return sky.getBlockAt(islands.grid().centerOf(island.cellX) + 4, 70, islands.grid().centerOf(island.cellZ) + 4);
    }

    private boolean place(PlayerMock player, Block block, String itemId) {
        ItemStack item = plugin.core().items().create(itemId);
        Material wanted = MachineType.ofItem(itemId).orElseThrow().block;
        block.setType(wanted);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0), item, player, true,
            EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            block.setType(Material.AIR);
        }
        return !event.isCancelled();
    }

    @Test
    public void machinesOnTheIslandProveTheirForgeQuests() {
        PlayerMock owner = server.addPlayer("Builder");
        Island island = island(owner);
        owner.teleport(base(island).getLocation());
        Block at = base(island);
        assertTrue(place(owner, at, "battery"));
        assertTrue(place(owner, at.getRelative(2, 0, 0), "miner_2"));
        var quests = plugin.core().profiles().section(owner.getUniqueId(), "quests",
            win.vampyuyu.survival.menu.QuestData.class, win.vampyuyu.survival.menu.QuestData::new);
        var catalog = win.vampyuyu.survival.menu.QuestRepo.load(plugin.core().data());
        var battery = catalog.tutorial().stream().filter(d -> d.name().equals("Battery")).findFirst().orElseThrow();
        var miner = catalog.tutorial().stream().filter(d -> d.name().equals("Miner I")).findFirst().orElseThrow();
        quests.completed.addAll(battery.requires());
        owner.performCommand("quests categories");
        assertTrue(quests.completed.contains(battery.id()));
        assertTrue("a Miner II is made from a Miner I", quests.completed.contains(miner.id()));
    }

    @Test
    public void aBlockPlacedIntoACableCellIsRefusedAndTheCableStays() {
        PlayerMock owner = server.addPlayer();
        Block cell = base(island(owner));
        assertTrue(place(owner, cell, "cable"));
        for (ItemStack item : new ItemStack[] {plugin.core().items().create("cable"), new ItemStack(Material.COBBLESTONE)}) {
            BlockPlaceEvent event = new BlockPlaceEvent(cell, cell.getState(), cell.getRelative(0, -1, 0), item, owner, true,
                EquipmentSlot.HAND);
            server.getPluginManager().callEvent(event);
            assertTrue(event.isCancelled());
            assertEquals(MachineType.CABLE, machines.store().get(sky.getName(), MachineStore.pos(cell)).kind());
        }
    }

    @Test
    public void aBatteryPoweredMinerBreaksGeneratorCobbleIntoTheChest() {
        PlayerMock owner = server.addPlayer("Engineer");
        Island island = island(owner);
        Block miner = base(island);
        assertTrue(place(owner, miner, "miner_2"));
        Block battery = miner.getRelative(1, 0, 0);
        assertTrue(place(owner, battery, "battery"));
        Block barrel = miner.getRelative(0, 1, 0);
        barrel.setType(Material.BARREL);
        Block cobble = miner.getRelative(-1, 0, 0);
        cobble.setType(Material.COBBLESTONE);
        server.getPluginManager().callEvent(new BlockFormEvent(cobble, cobble.getState()));
        quarry.data(owner.getUniqueId()).counters.put("bronzite", 299); // the pity forces a bronzite
        machines.store().get(sky.getName(), MachineStore.pos(battery)).charge = 100;

        machines.tick();

        assertEquals("the cobble was broken", Material.AIR, cobble.getType());
        assertEquals("6 Flux used", 94, machines.store().get(sky.getName(), MachineStore.pos(battery)).charge);
        Container container = (Container) barrel.getState();
        boolean found = false;
        for (ItemStack stack : container.getInventory().getContents()) {
            if (stack != null && plugin.core().items().idOf(stack).filter("bronzite"::equals).isPresent()) {
                found = true;
            }
        }
        assertTrue("the ore is in the barrel", found);
    }

    @Test
    public void aMinerAlsoBreaksTheBlocksOfARingGeneratorAndRollsItsOres() {
        PlayerMock owner = server.addPlayer("Icebreaker");
        Island island = island(owner);
        Block miner = base(island);
        assertTrue(place(owner, miner, "miner_2"));
        Block battery = miner.getRelative(1, 0, 0);
        assertTrue(place(owner, battery, "battery"));
        Block barrel = miner.getRelative(0, 1, 0);
        barrel.setType(Material.BARREL);
        Block ice = miner.getRelative(-1, 0, 0);
        ice.setType(Material.PACKED_ICE);
        new BlockMarks("genout").add(ice); // what a Frost generator made above its Fountain
        quarry.data(owner.getUniqueId()).counters.put("frostium", 59); // the pity forces a Frostium
        machines.store().get(sky.getName(), MachineStore.pos(battery)).charge = 100;

        machines.tick();

        assertEquals("the packed ice was broken", Material.AIR, ice.getType());
        Container container = (Container) barrel.getState();
        boolean iceKept = false;
        boolean frostium = false;
        for (ItemStack stack : container.getInventory().getContents()) {
            if (stack == null) continue;
            iceKept |= stack.getType() == Material.PACKED_ICE;
            frostium |= plugin.core().items().idOf(stack).filter("frostium"::equals).isPresent();
        }
        assertTrue("the packed ice itself is kept, as with Silk Touch", iceKept);
        assertTrue("the ring's ore is rolled with the pity", frostium);
    }

    @Test
    public void aMinerWithoutPowerOrChestDoesNothing() {
        PlayerMock owner = server.addPlayer("Idle");
        Island island = island(owner);
        Block miner = base(island);
        assertTrue(place(owner, miner, "miner_2"));
        Block cobble = miner.getRelative(-1, 0, 0);
        cobble.setType(Material.COBBLESTONE);
        server.getPluginManager().callEvent(new BlockFormEvent(cobble, cobble.getState()));
        machines.tick();
        assertEquals(Material.COBBLESTONE, cobble.getType());
    }

    @Test
    public void machinesOnlyGoOnYourOwnIslandWithNoLimit() {
        PlayerMock owner = server.addPlayer("Builder");
        PlayerMock stranger = server.addPlayer("Stranger");
        Island island = island(owner);
        Block start = base(island);
        assertFalse("not your island", place(stranger, start, "battery"));
        assertEquals(0, machines.store().countOnIsland(island.id.toString()));
        for (int i = 0; i < 80; i++) { // well past the old cap of 60
            assertTrue(place(owner, start.getRelative(i % 20 * 2, 0, i / 20 * 2), "cable"));
        }
        assertEquals(80, machines.store().countOnIsland(island.id.toString()));
    }

    @Test
    public void deletingAnIslandForgetsItsMachines() {
        PlayerMock owner = server.addPlayer("Leaver");
        Island island = island(owner);
        Block start = base(island);
        assertTrue(place(owner, start, "cable"));
        assertTrue(place(owner, start.getRelative(2, 0, 0), "battery"));
        assertEquals(2, machines.store().countOnIsland(island.id.toString()));
        server.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.IslandDeleteEvent(island.id));
        assertEquals(0, machines.store().countOnIsland(island.id.toString()));
    }

    @Test
    public void breakingAMachineGivesTheItemBackAndForgetsIt() {
        PlayerMock owner = server.addPlayer("Breaker");
        Island island = island(owner);
        Block block = base(island);
        assertTrue(place(owner, block, "coal_generator"));
        assertNotNull(machines.store().get(sky.getName(), MachineStore.pos(block)));
        server.getPluginManager().callEvent(new BlockBreakEvent(block, owner));
        assertNull(machines.store().get(sky.getName(), MachineStore.pos(block)));
    }

    @Test
    public void machinesRefuseToBePlacedOutsideSkyblock() {
        PlayerMock player = server.addPlayer("Cheater");
        World survival = server.addSimpleWorld("world");
        assertFalse(place(player, survival.getBlockAt(0, 70, 0), "battery"));
    }
}
