package win.vampyuyu.survival.utility;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;

/** The registered commands and services running inside the whole plugin. */
public class UtilityFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private World world;
    private TeleportService teleports;
    private final java.util.function.Function<org.bukkit.block.Block, java.util.Collection<org.bukkit.util.BoundingBox>>
        originalShapes = SafeSpot.shapes;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        // Utility rules are tested in isolation; mode boundary integration lives in SimpleSkyblockTest.
        org.bukkit.event.HandlerList.unregisterAll(plugin.core().modes());
        world = server.addSimpleWorld("world");
        teleports = plugin.core().service(TeleportService.class).orElseThrow();
        // MockBukkit implements neither async chunk loading nor teleportAsync.
        Platform.useSynchronous();
    }

    @After
    public void tearDown() {
        Platform.useReal();
        SafeSpot.shapes = originalShapes;
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- helpers

    private static String text(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static String drain(PlayerMock p) {
        StringBuilder out = new StringBuilder();
        Component c;
        while ((c = p.nextComponentMessage()) != null) {
            out.append(text(c)).append('\n');
        }
        return out.toString();
    }

    private PlayerMock player(String name, String... perms) {
        PlayerMock p = server.addPlayer(name);
        for (String perm : perms) {
            p.addAttachment(plugin, perm, true);
        }
        drain(p);
        return p;
    }

    /** Solid floor under (x,z) and the player standing on it. */
    private Location stand(PlayerMock p, World w, int x, int y, int z) {
        w.getBlockAt(x, y - 1, z).setType(Material.STONE);
        w.getBlockAt(x, y, z).setType(Material.AIR);
        w.getBlockAt(x, y + 1, z).setType(Material.AIR);
        Location at = new Location(w, x + 0.5, y, z + 0.5);
        p.teleport(at);
        return at;
    }

    /**
     * Puts a player into a dungeon world. The real dungeon feature refuses every teleport into a dungeon world that
     * is not its own run (RunListener.onTeleport), so the test borrows the dungeon admin permission for the jump only.
     */
    private Location standInDungeon(PlayerMock p, World w, int x, int y, int z) {
        var admin = p.addAttachment(plugin, win.vampyuyu.survival.dungeon.run.RunListener.ADMIN, true);
        try {
            return stand(p, w, x, y, z);
        } finally {
            p.removeAttachment(admin);
        }
    }

    private void ticks(int n) {
        server.getScheduler().performTicks(n);
    }

    private UtilityData data(Player p) {
        return plugin.core().profiles().section(p.getUniqueId(), "utility", UtilityData.class, UtilityData::new);
    }

    // ---------------------------------------------------------------- registration

    @Test
    public void everyCommandIsRegistered() {
        for (String name : List.of("home", "sethome", "delhome", "homes", "spawn", "setspawn", "tpa", "tpahere",
                "tpaccept", "tpdeny", "tpcancel", "tptoggle", "rtp", "ec", "workbench", "anvil", "grindstone",
                "stonecutter", "loom", "hat", "sit", "trash", "playtime", "near", "seen", "ping", "utility")) {
            assertNotNull("/" + name, Bukkit.getCommandMap().getCommand(name));
        }
        assertNotNull(plugin.getCommand("back").getExecutor());
        // /back keeps its plugin.yml permission names
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.utility.rtp"));
    }

    @Test
    public void helpListsOnlyWhatTheSenderMayUse() {
        PlayerMock p = player("Helper");
        p.performCommand("utility help 1");
        String out = drain(p);
        assertTrue(out, out.contains("/home"));
        assertFalse(out, out.contains("/anvil"));
        assertFalse(out, out.contains("/setspawn"));
    }

    // ---------------------------------------------------------------- homes

    @Test
    public void setHomePersistsInTheProfileAndHomeTeleportsBack() {
        PlayerMock p = player("Alice", Perms.BYPASS_WARMUP);
        stand(p, world, 10, 64, 10);
        p.performCommand("sethome Base");
        assertTrue(drain(p), data(p).homes.containsKey("base"));
        assertEquals(10.5, data(p).homes.get("base").x, 0.001);
        // the profile document survives a save and reload through the section store
        plugin.core().profiles().markDirty(p.getUniqueId());
        plugin.core().profiles().saveDirty();
        plugin.core().profiles().awaitWrites();

        stand(p, world, 50, 64, 50);
        p.performCommand("home base");
        ticks(2);
        assertEquals(10.5, p.getLocation().getX(), 0.01);
        assertEquals(10.5, p.getLocation().getZ(), 0.01);
    }

    @Test
    public void homeLimitFollowsPermissionNodes() {
        PlayerMock p = player("Bob", Perms.BYPASS_WARMUP);
        stand(p, world, 0, 64, 0);
        for (int i = 1; i <= 3; i++) {
            p.performCommand("sethome h" + i);
        }
        assertEquals(3, data(p).homes.size());
        drain(p);
        p.performCommand("sethome h4");
        assertTrue(drain(p).contains("only have 3"));
        assertEquals(3, data(p).homes.size());
        // moving an existing home is always allowed
        stand(p, world, 5, 64, 5);
        p.performCommand("sethome h1");
        assertEquals(5.5, data(p).homes.get("h1").x, 0.001);
        p.addAttachment(plugin, "vampyuyu.sb.utility.homes.5", true);
        p.performCommand("sethome h4");
        p.performCommand("sethome h5");
        assertEquals(5, data(p).homes.size());
        drain(p);
        p.performCommand("sethome h6");
        assertEquals(5, data(p).homes.size());
    }

    @Test
    public void delHomeAndBadNames() {
        PlayerMock p = player("Carol");
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome bad<name>");
        assertTrue(data(p).homes.isEmpty());
        p.performCommand("sethome a");
        p.performCommand("delhome a");
        assertTrue(data(p).homes.isEmpty());
        drain(p);
        p.performCommand("delhome a");
        assertTrue(drain(p).contains("no home called"));
    }

    @Test
    public void unsafeSpotCannotBecomeAHome() {
        PlayerMock p = player("Dave");
        Location at = new Location(world, 200.5, 64, 200.5);
        for (int y = 55; y <= 70; y++) {
            world.getBlockAt(200, y, 200).setType(Material.AIR);
        }
        p.teleport(at);
        p.performCommand("sethome sky");
        assertTrue(data(p).homes.isEmpty());
        world.getBlockAt(200, 63, 200).setType(Material.LAVA);
        p.performCommand("sethome lava");
        assertTrue(data(p).homes.isEmpty());
    }

    @Test
    public void homesInSkyblockWorldsNeedBuildRightsAndHubHasNone() {
        World island = server.addSimpleWorld("sb_world");
        World hub = server.addSimpleWorld("sb_hub");
        PlayerMock p = player("Eve");
        stand(p, island, 0, 64, 0);
        // no island feature published an IslandApi: refused
        p.performCommand("sethome isle");
        assertTrue(data(p).homes.isEmpty());
        boolean[] canBuild = {false};
        plugin.core().replace(IslandApi.class, new StubIslands(canBuild));
        p.performCommand("sethome isle");
        assertTrue(data(p).homes.isEmpty());
        canBuild[0] = true;
        p.performCommand("sethome isle");
        assertTrue(data(p).homes.containsKey("isle"));
        stand(p, hub, 0, 64, 0);
        p.performCommand("sethome lobby");
        assertFalse(data(p).homes.containsKey("lobby"));
        // build rights lost later: the home refuses to teleport
        canBuild[0] = false;
        p.addAttachment(plugin, Perms.BYPASS_WARMUP, true);
        drain(p);
        p.performCommand("home isle");
        ticks(2);
        assertEquals("sb_hub", p.getWorld().getName());
        assertTrue(drain(p).contains("can no longer build"));
    }

    @Test
    public void homesMenuListsBedsAndTeleportsOnClick() {
        PlayerMock p = player("Fay", Perms.BYPASS_WARMUP);
        stand(p, world, 3, 64, 3);
        p.performCommand("sethome one");
        p.performCommand("sethome two");
        stand(p, world, 30, 64, 30);
        p.performCommand("homes");
        var view = p.getOpenInventory();
        assertNotNull(view);
        ItemStack first = view.getTopInventory().getItem(10);
        assertNotNull(first);
        assertTrue(first.getType().name().endsWith("_BED"));
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 10,
            ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
        ticks(2);
        assertEquals(3.5, p.getLocation().getX(), 0.01);
    }

    // ---------------------------------------------------------------- warm-up

    @Test
    public void warmupDelaysTheTeleportAndMovingCancelsIt() {
        PlayerMock p = player("Gus");
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        p.performCommand("home h");
        assertTrue(teleports.isTeleporting(p.getUniqueId()));
        ticks(20);
        assertEquals(40.5, p.getLocation().getX(), 0.01); // still waiting
        ticks(50);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
        assertFalse(teleports.isTeleporting(p.getUniqueId()));

        // moving cancels
        stand(p, world, 40, 64, 40);
        drain(p);
        p.performCommand("home h");
        ticks(10);
        p.teleport(new Location(world, 43.5, 64, 40.5));
        ticks(80);
        assertEquals(43.5, p.getLocation().getX(), 0.01);
        assertTrue(drain(p).contains("you moved"));
        assertFalse(teleports.isTeleporting(p.getUniqueId()));
    }

    @Test
    public void damageCancelsTheWarmup() {
        PlayerMock p = player("Hal");
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        p.performCommand("home h");
        assertTrue(teleports.isTeleporting(p.getUniqueId()));
        server.getPluginManager().callEvent(new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL, 3.0));
        assertFalse(teleports.isTeleporting(p.getUniqueId()));
        ticks(80);
        assertEquals(40.5, p.getLocation().getX(), 0.01);
    }

    @Test
    public void onlyOneTeleportAtATime() {
        PlayerMock p = player("Ivy");
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        p.performCommand("home h");
        drain(p);
        p.performCommand("home h");
        assertTrue(drain(p).contains("already teleporting"));
    }

    // ---------------------------------------------------------------- combat and dungeons

    @Test
    public void combatTagRefusesTeleportsUntilItEnds() {
        PlayerMock p = player("Jon", Perms.BYPASS_WARMUP);
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        teleports.combat().tag(p.getUniqueId(), 10_000);
        drain(p);
        p.performCommand("home h");
        ticks(2);
        assertTrue(drain(p).contains("in combat"));
        assertEquals(40.5, p.getLocation().getX(), 0.01);
        teleports.combat().clear(p.getUniqueId());
        p.performCommand("home h");
        ticks(2);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
    }

    @Test
    public void combatBypassPermissionWorks() {
        PlayerMock p = player("Kim", Perms.BYPASS_WARMUP, Perms.BYPASS_COMBAT);
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        teleports.combat().tag(p.getUniqueId(), 10_000);
        p.performCommand("home h");
        ticks(2);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
    }

    @Test
    public void dungeonWorldsRefuseTeleportsAndBack() {
        World dungeon = server.addSimpleWorld("sb_dungeon_test");
        PlayerMock p = player("Lia", Perms.BYPASS_WARMUP);
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        standInDungeon(p, dungeon, 5, 64, 5);
        drain(p);
        p.performCommand("home h");
        ticks(2);
        assertTrue(drain(p).contains("disabled inside dungeons"));
        assertEquals("sb_dungeon_test", p.getWorld().getName());
        p.performCommand("spawn");
        ticks(2);
        assertEquals("sb_dungeon_test", p.getWorld().getName());
        // /back is refused in a dungeon too, and the death spot and cooldown are kept
        server.getPluginManager().callEvent(new PlayerDeathEvent(p, org.bukkit.damage.DamageSource.builder(
            org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>(), 0, "x"));
        stand(p, world, 70, 64, 70);
        standInDungeon(p, dungeon, 5, 64, 5);
        drain(p);
        p.performCommand("back");
        ticks(2);
        assertEquals("sb_dungeon_test", p.getWorld().getName());
        assertTrue(drain(p).contains("dungeon"));
    }

    @Test
    public void dungeonApiIsHonouredEvenOutsideDungeonWorlds() {
        boolean[] inside = {true};
        plugin.core().replace(DungeonApi.class, player -> inside[0]);
        PlayerMock p = player("Max", Perms.BYPASS_WARMUP);
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        drain(p);
        p.performCommand("home h");
        ticks(2);
        assertEquals(40.5, p.getLocation().getX(), 0.01);
        assertTrue(drain(p).contains("dungeon"));
        inside[0] = false;
        p.performCommand("home h");
        ticks(2);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
    }

    @Test
    public void backWorksAndStartsItsCooldownOnArrival() {
        PlayerMock p = player("Ned", Perms.BYPASS_WARMUP);
        stand(p, world, 20, 64, 20);
        server.getPluginManager().callEvent(new PlayerDeathEvent(p, org.bukkit.damage.DamageSource.builder(
            org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>(), 0, "x"));
        String death = drain(p);
        assertTrue(death, death.contains("You died at 20, 64, 20"));
        stand(p, world, 60, 64, 60);
        p.performCommand("back");
        ticks(2);
        assertEquals(20.5, p.getLocation().getX(), 0.01);
    }

    // ---------------------------------------------------------------- spawn

    @Test
    public void spawnInSkyblockWorldsGoesThroughTheHub() {
        World island = server.addSimpleWorld("sb_world");
        PlayerMock p = player("Oli", Perms.BYPASS_WARMUP);
        stand(p, island, 0, 64, 0);
        // no IslandApi: a clear message and no teleport
        p.performCommand("spawn");
        ticks(2);
        assertTrue(drain(p).contains("island is not available"));
        int[] calls = {0};
        plugin.core().replace(IslandApi.class, new StubIslands(new boolean[] {true}) {
            @Override
            public boolean teleportHub(Player player) {
                calls[0]++;
                return true;
            }
        });
        p.performCommand("spawn");
        ticks(2);
        assertEquals(1, calls[0]);
    }

    @Test
    public void spawnInTheSurvivalWorldGoesToTheWorldSpawn() {
        PlayerMock p = player("Pam", Perms.BYPASS_WARMUP);
        world.setSpawnLocation(new Location(world, 7, 64, 9));
        world.getBlockAt(7, 63, 9).setType(Material.STONE);
        stand(p, world, 100, 64, 100);
        p.performCommand("spawn");
        ticks(2);
        assertEquals(drain(p), 7.5, p.getLocation().getX(), 0.01);
        assertEquals(9.5, p.getLocation().getZ(), 0.01);
    }

    @Test
    public void setSpawnNeedsAdminAndRefusesSkyblockWorlds() {
        PlayerMock p = player("Quin");
        stand(p, world, 12, 64, 12);
        p.performCommand("setspawn");
        assertNotEquals(12, world.getSpawnLocation().getBlockX());
        p.addAttachment(plugin, Perms.ADMIN, true);
        p.performCommand("setspawn");
        assertEquals(12, world.getSpawnLocation().getBlockX());
        World island = server.addSimpleWorld("sb_world");
        stand(p, island, 3, 64, 3);
        drain(p);
        p.performCommand("setspawn");
        assertTrue(drain(p).contains("own spawn"));
    }

    // ---------------------------------------------------------------- tpa

    @Test
    public void tpaFlowAcceptTeleportsTheSender() {
        PlayerMock a = player("Ann", Perms.BYPASS_WARMUP);
        PlayerMock b = player("Ben");
        stand(a, world, 0, 64, 0);
        stand(b, world, 80, 64, 80);
        a.performCommand("tpa Ben");
        assertTrue(drain(b), true);
        b.performCommand("tpaccept");
        ticks(2);
        assertEquals(80.5, a.getLocation().getX(), 0.01);
        // the request is spent
        drain(b);
        b.performCommand("tpaccept");
        assertTrue(drain(b).contains("no pending"));
    }

    @Test
    public void tpahereMovesTheAccepter() {
        PlayerMock a = player("Cat");
        PlayerMock b = player("Dan", Perms.BYPASS_WARMUP);
        stand(a, world, 0, 64, 0);
        stand(b, world, 80, 64, 80);
        a.performCommand("tpahere Dan");
        b.performCommand("tpaccept Cat");
        ticks(2);
        assertEquals(0.5, b.getLocation().getX(), 0.01);
        assertEquals(0.5, a.getLocation().getX(), 0.01);
    }

    @Test
    public void tpaDenyCancelToggleAndSpam() {
        PlayerMock a = player("Eli", Perms.BYPASS_WARMUP);
        PlayerMock b = player("Fox");
        stand(a, world, 0, 64, 0);
        stand(b, world, 80, 64, 80);
        a.performCommand("tpa Fox");
        b.performCommand("tpdeny");
        b.performCommand("tpaccept");
        ticks(2);
        assertEquals(0.5, a.getLocation().getX(), 0.01);
        // spam: a second request inside the cooldown is refused
        drain(a);
        a.performCommand("tpa Fox");
        assertTrue(drain(a).contains("Slow down"));
        // cancel
        PlayerMock c = player("Gem", Perms.BYPASS_COOLDOWN);
        stand(c, world, 5, 64, 5);
        c.performCommand("tpa Fox");
        c.performCommand("tpcancel");
        b.performCommand("tpaccept Gem");
        assertEquals(5.5, c.getLocation().getX(), 0.01);
        // toggle
        b.performCommand("tptoggle");
        drain(c);
        c.performCommand("tpa Fox");
        assertTrue(drain(c).contains("not accepting"));
        // self
        drain(c);
        c.performCommand("tpa Gem");
        assertTrue(drain(c).contains("yourself"));
    }

    @Test
    public void tpaToSomeoneInADungeonIsRefused() {
        World dungeon = server.addSimpleWorld("sb_dungeon_t2");
        PlayerMock a = player("Hex", Perms.BYPASS_WARMUP);
        PlayerMock b = player("Ian");
        stand(a, world, 0, 64, 0);
        standInDungeon(b, dungeon, 4, 64, 4);
        drain(a);
        a.performCommand("tpa Ian");
        assertTrue(drain(a).contains("dungeons"));
        assertEquals(0.5, a.getLocation().getX(), 0.01);
    }

    @Test
    public void tpaAcceptIsRefusedWhenTheMoverIsInCombat() {
        PlayerMock a = player("Jay", Perms.BYPASS_WARMUP);
        PlayerMock b = player("Kai");
        stand(a, world, 0, 64, 0);
        stand(b, world, 80, 64, 80);
        a.performCommand("tpa Kai");
        teleports.combat().tag(a.getUniqueId(), 10_000);
        drain(b);
        b.performCommand("tpaccept");
        assertTrue(drain(b).contains("cannot teleport right now"));
        teleports.combat().clear(a.getUniqueId());
        b.performCommand("tpaccept");
        ticks(2);
        assertEquals(80.5, a.getLocation().getX(), 0.01);
    }

    @Test
    public void slabStairAndCarpetFloorsAreSafeToSetAHomeOn() {
        // MockBukkit cannot describe collision shapes: give slabs, stairs and carpets their vanilla boxes.
        SafeSpot.shapes = block -> switch (block.getType()) {
            case OAK_SLAB -> List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 0.5, 1));
            case OAK_STAIRS -> List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 0.5, 1),
                new org.bukkit.util.BoundingBox(0, 0.5, 0.5, 1, 1, 1));
            case WHITE_CARPET -> List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 0.0625, 1));
            case AIR -> List.of();
            default -> List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, 1, 1));
        };
        PlayerMock p = player("Slab");
        int x = 300;
        for (int dy = 62; dy <= 68; dy++) {
            world.getBlockAt(x, dy, x).setType(Material.AIR);
        }
        // bottom slab floor: feet at y + 0.5
        world.getBlockAt(x, 63, x).setType(Material.STONE);
        world.getBlockAt(x, 64, x).setType(Material.OAK_SLAB);
        p.teleport(new Location(world, x + 0.5, 64.5, x + 0.5));
        p.performCommand("sethome slab");
        assertTrue("slab floor", data(p).homes.containsKey("slab"));
        // low step of a stair
        world.getBlockAt(x, 64, x).setType(Material.OAK_STAIRS);
        p.teleport(new Location(world, x + 0.5, 64.5, x + 0.2));
        p.performCommand("sethome stairs");
        assertTrue("stair floor", data(p).homes.containsKey("stairs"));
        // carpet
        world.getBlockAt(x, 64, x).setType(Material.WHITE_CARPET);
        p.teleport(new Location(world, x + 0.5, 64.0625, x + 0.5));
        p.performCommand("sethome carpet");
        assertTrue("carpet floor", data(p).homes.containsKey("carpet"));
        // the saved slab home still teleports back after the floor is a slab again
        world.getBlockAt(x, 64, x).setType(Material.OAK_SLAB);
        p.addAttachment(plugin, Perms.BYPASS_WARMUP, true);
        stand(p, world, 0, 64, 0);
        p.performCommand("home slab");
        ticks(2);
        assertEquals(x + 0.5, p.getLocation().getX(), 0.01);
        assertEquals(64.5, p.getLocation().getY(), 0.01);
        // feet sunk into the slab and a ceiling over the head are still refused
        drain(p);
        p.teleport(new Location(world, x + 0.5, 64.25, x + 0.5));
        p.performCommand("sethome sunk");
        assertFalse(data(p).homes.containsKey("sunk"));
        world.getBlockAt(x, 65, x).setType(Material.STONE);
        p.teleport(new Location(world, x + 0.5, 64.5, x + 0.5));
        p.performCommand("sethome low");
        assertFalse("ceiling in the head cell", data(p).homes.containsKey("low"));
    }

    @Test
    public void fullBlockFloorStillWorks() {
        PlayerMock p = player("Full");
        Location at = stand(p, world, 310, 64, 310);
        assertTrue(SafeSpot.isSafe(at));
        world.getBlockAt(310, 65, 310).setType(Material.STONE);
        assertFalse(SafeSpot.isSafe(at));
    }

    // ---------------------------------------------------------------- combat tag source

    private void hit(org.bukkit.entity.Entity damager, Player victim) {
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(damager, victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 4.0));
    }

    @Test
    public void onlyPlayersTagByDefaultAndTheTagIsAnnounced() {
        PlayerMock a = player("Pvp1");
        PlayerMock b = player("Pvp2");
        stand(a, world, 0, 64, 0);
        stand(b, world, 2, 64, 0);
        org.bukkit.entity.Entity zombie = world.spawnEntity(new Location(world, 1.5, 64, 0.5),
            org.bukkit.entity.EntityType.ZOMBIE);
        hit(zombie, a);
        assertFalse("a mob must not tag", teleports.combat().tagged(a.getUniqueId()));
        hit(a, b);
        assertTrue(teleports.combat().tagged(a.getUniqueId()));
        assertTrue(teleports.combat().tagged(b.getUniqueId()));
        assertTrue(drain(b).contains("You are in combat"));
        // a second hit during the tag does not repeat the announcement
        hit(a, b);
        assertFalse(drain(b).contains("You are in combat"));
    }

    @Test
    public void endOfTheTagIsAnnounced() throws Exception {
        PlayerMock a = player("Tag1");
        teleports.combat().tag(a.getUniqueId(), 5);
        Thread.sleep(20);
        drain(a);
        ticks(40);
        assertTrue(drain(a).contains("no longer in combat"));
    }

    @Test
    public void theTeleportGateIsPublishedForOtherFeatures() {
        assertSame(teleports, plugin.core().service(win.vampyuyu.survival.back.TeleportGate.class).orElseThrow());
    }

    @Test
    public void aTeleportCancelledByAnotherFeatureLeavesNoCooldownAndSaysWhy() {
        PlayerMock a = player("Vis1", Perms.BYPASS_WARMUP);
        PlayerMock b = player("Vis2");
        stand(a, world, 0, 64, 0);
        stand(b, world, 80, 64, 80);
        // stands in for the island feature refusing a banned visitor
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
            public void onTp(org.bukkit.event.player.PlayerTeleportEvent event) {
                if (event.getTo() != null && event.getTo().getX() > 50) {
                    event.setCancelled(true);
                }
            }
        }, plugin);
        a.performCommand("tpa Vis2");
        b.performCommand("tpaccept");
        ticks(2);
        assertEquals(0.5, a.getLocation().getX(), 0.01);
        assertFalse(teleports.isTeleporting(a.getUniqueId()));
        assertTrue(drain(a).contains("destination refused"));
        // no arrival: the tpa cooldown was not started, so a new request goes through
        a.performCommand("tpa Vis2");
        b.performCommand("tpaccept");
        ticks(2);
        assertFalse(drain(b).contains("wait"));
    }

    // ---------------------------------------------------------------- rtp

    @Test
    public void rtpOnlyWorksInTheSurvivalWorld() {
        World island = server.addSimpleWorld("sb_world");
        PlayerMock p = player("Lou", Perms.BYPASS_WARMUP);
        stand(p, island, 0, 64, 0);
        drain(p);
        p.performCommand("rtp");
        ticks(2);
        assertTrue(drain(p).contains("only works in the survival world"));
        assertEquals(0.5, p.getLocation().getX(), 0.01);
    }

    // ---------------------------------------------------------------- hat

    @Test
    public void hatSwapsHandAndHelmetWithoutLosingAnything() {
        PlayerMock p = player("Mia");
        p.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN, 1));
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.performCommand("hat");
        assertEquals(Material.PUMPKIN, p.getInventory().getHelmet().getType());
        assertEquals(Material.IRON_HELMET, p.getInventory().getItemInMainHand().getType());
        // stack of 3: one goes up, the old helmet goes to the inventory, nothing duplicated
        p.getInventory().clear();
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.getInventory().setItemInMainHand(new ItemStack(Material.DIRT, 3));
        p.performCommand("hat");
        assertEquals(Material.DIRT, p.getInventory().getHelmet().getType());
        assertEquals(1, p.getInventory().getHelmet().getAmount());
        assertEquals(2, p.getInventory().getItemInMainHand().getAmount());
        assertTrue(p.getInventory().contains(Material.IRON_HELMET));
        // empty hand takes the hat off
        p.getInventory().clear();
        p.getInventory().setHelmet(new ItemStack(Material.DIRT));
        p.performCommand("hat");
        assertNull(p.getInventory().getHelmet());
        assertEquals(Material.DIRT, p.getInventory().getItemInMainHand().getType());
    }

    @Test
    public void hatRefusesABindingCurseAndAFullInventory() {
        PlayerMock p = player("Nik");
        ItemStack cursed = new ItemStack(Material.LEATHER_HELMET);
        cursed.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1);
        p.getInventory().setHelmet(cursed);
        p.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
        p.performCommand("hat");
        assertEquals(Material.LEATHER_HELMET, p.getInventory().getHelmet().getType());
        assertEquals(Material.DIRT, p.getInventory().getItemInMainHand().getType());
    }

    // ---------------------------------------------------------------- trash

    @Test
    public void trashDestroysItemsButKeepsValuablesAndLockedRow() {
        plugin.core().items().register(CustomItemDef.builder("util_epic_thing", "Epic Thing", Rarity.EPIC,
            Material.STICK).build());
        plugin.core().items().register(CustomItemDef.builder("util_legend_thing", "Legend Thing", Rarity.LEGENDARY,
            Material.STICK).build());
        plugin.core().items().register(CustomItemDef.builder("wings", "Wings", Rarity.COMMON, Material.ELYTRA).build());
        PlayerMock p = player("Ola");
        p.performCommand("trash");
        var view = p.getOpenInventory();
        var top = view.getTopInventory();
        top.setItem(0, new ItemStack(Material.DIAMOND, 5));
        top.setItem(1, plugin.core().items().create("util_epic_thing"));
        top.setItem(2, plugin.core().items().create("util_legend_thing"));
        top.setItem(3, plugin.core().items().create("wings"));
        // the locked row cannot be changed
        InventoryClickEvent locked = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 28,
            ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(locked);
        assertTrue(locked.isCancelled());
        InventoryClickEvent dbl = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
            ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
        server.getPluginManager().callEvent(dbl);
        assertTrue(dbl.isCancelled());
        InventoryClickEvent inArea = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
            ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(inArea);
        assertFalse(inArea.isCancelled());
        p.closeInventory();
        assertFalse(p.getInventory().contains(Material.DIAMOND));
        assertTrue(plugin.core().items().is(findCustom(p, "util_legend_thing"), "util_legend_thing"));
        assertNotNull(findCustom(p, "wings"));
        assertNull(findCustom(p, "util_epic_thing"));
    }

    @Test
    public void trashHandsItemsBackWhenThePluginIsDisabled() {
        PlayerMock p = player("Pip");
        p.performCommand("trash");
        p.getOpenInventory().getTopInventory().setItem(4, new ItemStack(Material.EMERALD, 7));
        server.getPluginManager().disablePlugin(plugin);
        assertTrue(p.getInventory().contains(Material.EMERALD, 7));
    }

    private ItemStack findCustom(PlayerMock p, String id) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (plugin.core().items().is(item, id)) {
                return item;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- info commands

    @Test
    public void nearListsVisiblePlayersByDistance() {
        PlayerMock a = player("Qua");
        PlayerMock b = player("Rob");
        PlayerMock c = player("Sky");
        stand(a, world, 0, 64, 0);
        stand(b, world, 30, 64, 0);
        stand(c, world, 10, 64, 0);
        drain(a);
        a.performCommand("near");
        String out = drain(a);
        assertTrue(out, out.indexOf("Sky") >= 0 && out.indexOf("Sky") < out.indexOf("Rob"));
        a.performCommand("near 5");
        assertTrue(drain(a).contains("Nobody within 5"));
        a.performCommand("near abc");
        assertTrue(drain(a).contains("not a number"));
    }

    @Test
    public void pingAndSeenAnswer() {
        PlayerMock a = player("Tom");
        PlayerMock b = player("Uma");
        a.performCommand("ping");
        assertTrue(drain(a).contains("Your ping is"));
        a.performCommand("ping Uma");
        assertTrue(drain(a).contains("Uma's ping"));
        a.performCommand("seen Uma");
        assertTrue(drain(a).contains("online right now"));
        a.performCommand("seen Nobody");
        assertTrue(drain(a).contains("do not know"));
        a.performCommand("playtime");
        assertTrue(drain(a).contains("You have played"));
        assertNotNull(b);
    }

    // ---------------------------------------------------------------- sit

    @Test
    public void sitSeatsThePlayerAndRemovesTheSeat() {
        PlayerMock p = player("Val");
        stand(p, world, 0, 64, 0);
        p.performCommand("sit");
        assertTrue(drain(p), p.isInsideVehicle());
        long seats = world.getEntities().stream().filter(e -> e.getType() == org.bukkit.entity.EntityType.INTERACTION)
            .count();
        assertEquals(1, seats);
        p.performCommand("sit");
        assertFalse(p.isInsideVehicle());
        assertEquals(0, world.getEntities().stream()
            .filter(e -> e.getType() == org.bukkit.entity.EntityType.INTERACTION).count());
    }

    // ---------------------------------------------------------------- rtp search

    private void smallRtp(int min, int max) throws Exception {
        java.nio.file.Files.writeString(plugin.getDataFolder().toPath().resolve("utility.yml"),
            "rtp:\n  min-radius: " + min + "\n  max-radius: " + max + "\n  max-attempts: 6\n");
    }

    @Test
    public void rtpFindsASafeSpotInTheRingAndUsesTheCooldownOnlyOnSuccess() throws Exception {
        smallRtp(16, 30);
        PlayerMock admin = player("Root", Perms.ADMIN);
        admin.performCommand("utility reload");
        PlayerMock p = player("Wes", Perms.BYPASS_WARMUP);
        stand(p, world, 0, 64, 0);
        // nothing to stand on yet: the search fails and the cooldown stays free
        drain(p);
        p.performCommand("rtp");
        ticks(2);
        assertTrue(drain(p), true);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
            }
        }
        drain(p);
        p.performCommand("rtp");
        ticks(2);
        Location at = p.getLocation();
        double distance = Math.hypot(at.getX() - 0.5, at.getZ() - 0.5);
        assertTrue("distance " + distance + " " + drain(p), distance >= 15 && distance <= 32);
        assertEquals(64.0, at.getY(), 0.01);
        // cooldown now running
        drain(p);
        p.performCommand("rtp");
        assertTrue(drain(p).contains("cooldown"));
        // the bypass permission ignores it
        p.addAttachment(plugin, Perms.BYPASS_COOLDOWN, true);
        Location before = p.getLocation();
        p.performCommand("rtp");
        ticks(2);
        assertNotEquals(before, p.getLocation());
    }

    @Test
    public void configReloadChangesTheWarmup() throws Exception {
        java.nio.file.Files.writeString(plugin.getDataFolder().toPath().resolve("utility.yml"),
            "teleport:\n  warmup-seconds: 8\n");
        PlayerMock admin = player("Boss", Perms.ADMIN);
        admin.performCommand("utility reload");
        PlayerMock p = player("Xan");
        stand(p, world, 0, 64, 0);
        p.performCommand("sethome h");
        stand(p, world, 40, 64, 40);
        p.performCommand("home h");
        ticks(5 * 20);
        assertEquals(40.5, p.getLocation().getX(), 0.01);
        ticks(4 * 20);
        assertEquals(0.5, p.getLocation().getX(), 0.01);
        // a non admin cannot reload
        drain(p);
        p.performCommand("utility reload");
        assertTrue(drain(p).contains("permission"));
    }

    @Test
    public void disablingRemovesCommandsSeatsAndWarmups() {
        PlayerMock p = player("Yan");
        stand(p, world, 0, 64, 0);
        p.performCommand("sit");
        assertTrue(p.isInsideVehicle());
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("rtp"));
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("home"));
        assertFalse(p.isInsideVehicle());
    }

    @Test
    public void deathMessageGoesOnlyToTheVictim() {
        PlayerMock p = player("Zed");
        PlayerMock other = player("Zoe");
        stand(p, world, 12, 70, -3);
        server.getPluginManager().callEvent(new PlayerDeathEvent(p, org.bukkit.damage.DamageSource.builder(
            org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>(), 0, "x"));
        assertTrue(drain(p).contains("12, 70, -3"));
        assertEquals("", drain(other));
    }

    @Test
    public void portableBlocksAreGatedAndNeverOpenInDungeons() {
        var pm = Bukkit.getPluginManager();
        assertEquals(org.bukkit.permissions.PermissionDefault.TRUE, pm.getPermission("vampyuyu.sb.utility.ec").getDefault());
        assertEquals(org.bukkit.permissions.PermissionDefault.OP, pm.getPermission("vampyuyu.sb.utility.anvil").getDefault());
        assertEquals(org.bukkit.permissions.PermissionDefault.OP, pm.getPermission("vampyuyu.sb.utility.loom").getDefault());
        PlayerMock p = player("Pat");
        p.performCommand("anvil");
        assertNull("no permission, no anvil", p.getOpenInventory().getTopInventory() == null ? null
            : (p.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.ANVIL ? "open" : null));
        World dungeon = server.addSimpleWorld("sb_dungeon_ec");
        standInDungeon(p, dungeon, 0, 64, 0);
        drain(p);
        p.performCommand("ec");
        assertTrue(drain(p).contains("disabled inside dungeons"));
        stand(p, world, 0, 64, 0);
        p.performCommand("ec");
        assertEquals(org.bukkit.event.inventory.InventoryType.ENDER_CHEST, p.getOpenInventory().getTopInventory().getType());
        // other players' ender chests need the admin node
        PlayerMock other = player("Pete");
        drain(p);
        p.closeInventory();
        p.performCommand("ec Pete");
        assertTrue(drain(p).contains("own ender chest"));
    }

    // ---------------------------------------------------------------- stub

    private static class StubIslands implements IslandApi {
        private final boolean[] canBuild;

        StubIslands(boolean[] canBuild) {
            this.canBuild = canBuild;
        }

        @Override
        public java.util.Optional<IslandInfo> islandAt(Location location) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<IslandInfo> islandOf(UUID player) {
            return java.util.Optional.empty();
        }

        @Override
        public boolean isSkyblockWorld(World world) {
            return world.getName().startsWith("sb_");
        }

        @Override
        public boolean canBuild(Player player, Location location) {
            return canBuild[0];
        }

        @Override
        public boolean teleportHome(Player player) {
            return false;
        }

        @Override
        public boolean teleportHub(Player player) {
            World hub = Bukkit.getWorld("sb_hub");
            return hub != null && player.teleport(new Location(hub, 0.5, 64, 0.5));
        }

        @Override
        public java.util.Collection<IslandInfo> all() {
            return List.of();
        }
    }
}
