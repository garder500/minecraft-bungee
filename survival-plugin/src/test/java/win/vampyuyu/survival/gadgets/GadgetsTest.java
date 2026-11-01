package win.vampyuyu.survival.gadgets;

import static org.junit.Assert.*;

import java.util.List;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Items;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.Island;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.quarry.QuarryService;

public class GadgetsTest {
    private static final List<String> IDS = List.of("skyhook", "cloud_anchor", "ore_compass", "void_net", "rain_totem", "satchel");

    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService islands;
    private QuarryService quarry;
    private Gadgets gadgets;
    private SatchelService satchel;
    private Items items;
    private World sky;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        islands = (IslandService) plugin.core().require(IslandApi.class);
        quarry = plugin.core().require(QuarryService.class);
        gadgets = plugin.core().require(Gadgets.class);
        satchel = plugin.core().require(SatchelService.class);
        items = plugin.core().items();
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

    private Location onIsland(Island island, double dy) {
        return new Location(sky, islands.grid().centerOf(island.cellX) + 3.5, 65 + dy, islands.grid().centerOf(island.cellZ) + 3.5);
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private List<String> drain(PlayerMock p) {
        List<String> out = new java.util.ArrayList<>();
        net.kyori.adventure.text.Component c;
        while ((c = p.nextComponentMessage()) != null) {
            out.add(plain(c));
        }
        return out;
    }

    @Test
    public void everyItemExistsWithItsRecipeAndModel() {
        var recipes = quarry.config().recipes;
        for (String id : IDS) {
            var def = items.def(id);
            assertTrue(id, def.isPresent());
            assertEquals("vampyuyu:utility/" + id, def.get().itemModel());
            assertNotNull(id + " has a Forge recipe", recipes.get(id));
            assertEquals(id, recipes.get(id).result());
            assertTrue(id + " has lore with a use line", def.get().lore().size() >= 2);
        }
    }

    @Test
    public void skyhookHasACooldownAndOnlyWorksInTheSkyblock() {
        PlayerMock p = server.addPlayer("Hooker");
        Island island = island(p);
        p.teleport(onIsland(island, 0));
        Location target = p.getEyeLocation().add(10, 3, 0);
        assertTrue(gadgets.skyhook(p, target));
        assertFalse("4 s cooldown", gadgets.skyhook(p, target));
        assertTrue(gadgets.fallProtected(p.getUniqueId()));
        assertTrue(p.getVelocity().getX() > 0);
    }

    @Test
    public void cloudAnchorOnlyOnYourOwnIsland() {
        PlayerMock a = server.addPlayer("Owner");
        PlayerMock b = server.addPlayer("Visitor");
        Island ia = island(a);
        island(b);
        b.teleport(onIsland(ia, 0));
        assertFalse("someone else's island", gadgets.setAnchor(b));
        a.teleport(onIsland(ia, 0));
        assertTrue(gadgets.setAnchor(a));
        assertTrue(gadgets.anchor(a).isPresent());
        assertFalse("nothing set for b", gadgets.anchor(b).isPresent());
        // the warmup cancels when the player moves
        assertTrue(gadgets.startReturn(a));
        a.teleport(onIsland(ia, 0).add(5, 0, 0));
        server.getScheduler().performTicks(5);
        assertTrue(drain(a).stream().anyMatch(m -> m.contains("cancelled")));
        // standing still: the return happens after the warmup
        assertTrue(gadgets.startReturn(a));
        server.getScheduler().performTicks(45);
        assertEquals(onIsland(ia, 0).getX(), a.getLocation().getX(), 0.01);
        assertFalse("60 s cooldown", gadgets.startReturn(a));
    }

    @Test
    public void oreCompassShowsThePityCounters() {
        PlayerMock p = server.addPlayer("Prospector");
        island(p);
        assertTrue(quarry.upgrade(p.getUniqueId(), 4));
        var counters = quarry.data(p.getUniqueId()).counters;
        counters.put("diamond", 1499);
        counters.put("bronzite", 100);
        List<String> lines = gadgets.compassLines(p);
        assertTrue(lines.toString(), lines.contains("Diamond: 1 more block"));
        assertTrue(lines.toString(), lines.contains("Bronzite: 200 more blocks"));
        assertTrue(lines.toString(), lines.contains("Voidglass: 15000 more blocks"));
        gadgets.readCompass(p);
        List<String> chat = drain(p);
        assertTrue(chat.toString(), chat.stream().anyMatch(m -> m.endsWith("Bronzite: 200 more blocks")));
    }

    @Test
    public void voidNetSpendsOneChargePerSaveAndBreaksAtZero() {
        PlayerMock p = server.addPlayer("Faller");
        Island island = island(p);
        Location safe = onIsland(island, 0);
        p.teleport(safe);
        assertFalse("no net, no save", gadgets.checkNet(p));
        p.getInventory().addItem(items.create("void_net"));
        for (int used = 1; used <= 4; used++) {
            p.teleport(onIsland(island, -60));
            assertTrue(gadgets.checkNet(p));
            assertTrue("back above the void", p.getLocation().getY() > 40);
            ItemStack net = p.getInventory().getItem(0);
            assertEquals(5 - used, GadgetItems.charges(items, net));
            assertTrue(net.lore().stream().map(GadgetsTest::plain).anyMatch(("Charges: " + (5 - used) + "/5")::equals));
        }
        p.teleport(onIsland(island, -60));
        assertTrue(gadgets.checkNet(p));
        assertFalse("the last charge used up the net", gadgets.hasNet(p));
        p.teleport(onIsland(island, -60));
        assertFalse(gadgets.checkNet(p));
    }

    @Test
    public void voidNetBringsBackItemsLostNearby() {
        PlayerMock p = server.addPlayer("Dropper");
        Island island = island(p);
        p.teleport(onIsland(island, 0));
        p.getInventory().addItem(items.create("void_net"));
        Item lost = sky.dropItem(onIsland(island, -70).add(2, 0, 0), new ItemStack(Material.DIAMOND));
        Item far = sky.dropItem(onIsland(island, -70).add(30, 0, 0), new ItemStack(Material.DIAMOND));
        gadgets.checkNet(p);
        assertTrue("caught", lost.getLocation().getY() > 40);
        assertTrue("too far", far.getLocation().getY() < 0);
    }

    @Test
    public void satchelTakesOresAndOverflowAndKeepsThemAcrossReloads() {
        PlayerMock p = server.addPlayer("Packrat");
        Island island = island(p);
        p.teleport(onIsland(island, 0));
        // without the satchel nothing is taken
        Item first = sky.dropItem(p.getLocation(), items.create("bronzite", 3));
        PlayerAttemptPickupItemEvent plainPickup = new PlayerAttemptPickupItemEvent(p, first, 0);
        server.getPluginManager().callEvent(plainPickup);
        assertFalse(plainPickup.isCancelled());
        assertEquals(0, count(satchel.bag(p.getUniqueId())));
        p.getInventory().addItem(items.create("satchel"));
        // a generator ore goes in the bag
        PlayerAttemptPickupItemEvent ore = new PlayerAttemptPickupItemEvent(p, first, 0);
        server.getPluginManager().callEvent(ore);
        assertTrue(ore.isCancelled());
        assertTrue(first.isDead());
        assertEquals(3, count(satchel.bag(p.getUniqueId())));
        // a resource goes in only when the inventory is full
        Item cobble = sky.dropItem(p.getLocation(), new ItemStack(Material.COBBLESTONE, 10));
        PlayerAttemptPickupItemEvent roomy = new PlayerAttemptPickupItemEvent(p, cobble, 0);
        server.getPluginManager().callEvent(roomy);
        assertFalse("there was room", roomy.isCancelled());
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i) == null) {
                p.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
            }
        }
        PlayerAttemptPickupItemEvent full = new PlayerAttemptPickupItemEvent(p, cobble, 0);
        server.getPluginManager().callEvent(full);
        assertTrue(full.isCancelled());
        assertEquals(13, count(satchel.bag(p.getUniqueId())));
        // persistence: forget the live bag, read it again from the profile
        satchel.release(p.getUniqueId());
        assertEquals(13, count(satchel.bag(p.getUniqueId())));
        assertEquals(27, satchel.bag(p.getUniqueId()).getSize());
        assertEquals("bronzite", items.idOf(satchel.bag(p.getUniqueId()).getItem(0)).orElse(""));
    }

    private static int count(org.bukkit.inventory.Inventory inv) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            n += s == null ? 0 : s.getAmount();
        }
        return n;
    }
}
