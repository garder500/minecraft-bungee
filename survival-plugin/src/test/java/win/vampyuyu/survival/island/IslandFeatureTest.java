package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.event.IslandEnterEvent;
import win.vampyuyu.survival.core.Gui;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandService.Upgrade;

/** Boots the whole plugin: the island feature, its commands, protection, portals, menus and exploit guards. */
public class IslandFeatureTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private IslandService service;
    private World overworld;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        service = (IslandService) plugin.core().require(IslandApi.class);
        overworld = Bukkit.getWorld(SkyblockWorlds.WORLD);
        assertNotNull("sb_world is created at enable", overworld);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    // ---------------------------------------------------------------- helpers

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Everything the player was told since the last call. */
    private static String said(PlayerMock player) {
        StringBuilder out = new StringBuilder();
        Component next;
        while ((next = player.nextComponentMessage()) != null) {
            out.append(plain(next)).append('\n');
        }
        return out.toString();
    }

    private PlayerMock player(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(false);
        return player;
    }

    /** A finished island, as if generation had completed. */
    private Island readyIsland(PlayerMock owner) {
        Island island = service.registry().create(owner.getUniqueId(), owner.getName(), "classic", System.currentTimeMillis());
        island.state(Island.State.READY);
        island.platformsReady = true;
        return island;
    }

    private Location center(Island island) {
        return new Location(overworld, service.grid().centerOf(island.cellX) + 0.5, 65,
            service.grid().centerOf(island.cellZ) + 0.5);
    }

    private static final class FakeEconomy implements EconomyApi {
        double balance;
        int takes;

        @Override
        public double coins(UUID player) {
            return balance;
        }

        @Override
        public boolean has(UUID player, double amount) {
            return balance >= amount;
        }

        @Override
        public boolean take(UUID player, double amount, String reason) {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            takes++;
            return true;
        }

        @Override
        public void add(UUID player, double amount, String reason) {
            balance += amount;
        }

        @Override
        public void addTransactionListener(java.util.function.Consumer<Transaction> listener) {}
    }

    // ---------------------------------------------------------------- wiring

    @Test
    public void featureEnablesAndPublishesEveryApi() {
        assertTrue(plugin.features().failedIds().isEmpty());
        assertTrue(plugin.features().enabledIds().contains("island"));
        assertSame(service, plugin.core().require(IslandAltarApi.class));
        assertSame(service, plugin.core().require(IslandUpgradesApi.class));
        for (String name : List.of(SkyblockWorlds.HUB, SkyblockWorlds.WORLD, SkyblockWorlds.NETHER, SkyblockWorlds.END)) {
            World world = Bukkit.getWorld(name);
            assertNotNull(name, world);
            assertTrue(service.isSkyblockWorld(world));
        }
        assertEquals(World.Environment.NETHER, Bukkit.getWorld(SkyblockWorlds.NETHER).getEnvironment());
        assertEquals(World.Environment.THE_END, Bukkit.getWorld(SkyblockWorlds.END).getEnvironment());
        assertFalse(service.isSkyblockWorld(null));
    }

    @Test
    public void commandsAndPermissionsAreRegistered() {
        var map = Bukkit.getCommandMap();
        for (String name : List.of("island", "is", "sb-island", "hub", "islandadmin")) {
            assertNotNull("/" + name, map.getCommand(name));
        }
        assertSame(map.getCommand("island"), map.getCommand("is"));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.island.use"));
        assertNotNull(Bukkit.getPluginManager().getPermission("vampyuyu.sb.island.admin"));
        assertNotNull(Bukkit.getPluginManager().getPermission(IslandService.BYPASS));
        assertFalse(player("Plain").hasPermission("vampyuyu.sb.island.admin"));
    }

    @Test
    public void disablingRemovesCommandsAndSavesTheRegistry() throws Exception {
        PlayerMock owner = player("Saver");
        readyIsland(owner);
        server.getPluginManager().disablePlugin(plugin);
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("island"));
        assertNull(Bukkit.getCommandMap().getKnownCommands().get("hub"));
        Path file = plugin.getDataFolder().toPath().resolve(IslandStorage.FILE);
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains(owner.getUniqueId().toString()));
    }

    // ---------------------------------------------------------------- commands

    @Test
    public void helpAndUnknownSubcommandsAnswer() {
        PlayerMock p = player("Helper");
        p.performCommand("island help");
        String help = said(p);
        assertTrue(help, help.contains("/island create"));
        assertTrue(help, help.contains("/island transfer"));
        p.performCommand("island nonsense");
        assertTrue(said(p).contains("Unknown subcommand"));
        p.performCommand("island info");
        assertTrue(said(p).contains("do not have an island"));
        p.performCommand("island home");
        assertTrue(said(p).contains("do not have an island"));
        p.performCommand("island kick Nobody");
        assertTrue(said(p).contains("Unknown player"));
    }

    @Test
    public void everyHelpEntryIsHandledByTheCommand() {
        PlayerMock p = player("Walker");
        for (String[] row : IslandCommands.HELP) {
            p.performCommand("island " + row[0]);
            assertFalse("/island " + row[0] + " was not recognised", said(p).contains("Unknown subcommand"));
        }
        p.closeInventory();
        said(p);
        p.performCommand("island " + "zzz");
        assertTrue(said(p).contains("Unknown subcommand"));
    }

    @Test
    public void tabCompletion() {
        PlayerMock p = player("Tabber");
        player("Taboo");
        var cmd = Bukkit.getCommandMap().getCommand("island");
        assertTrue(cmd.tabComplete(p, "island", new String[] {"cr"}).contains("create"));
        assertTrue(cmd.tabComplete(p, "island", new String[] {"create", ""}).containsAll(List.of("classic", "desert", "frozen", "jungle", "mushroom")));
        assertTrue(cmd.tabComplete(p, "island", new String[] {"flag", "p"}).contains("pvp"));
        assertEquals(List.of("on", "off").stream().sorted().toList(), cmd.tabComplete(p, "island", new String[] {"flag", "pvp", ""}));
        assertTrue(cmd.tabComplete(p, "island", new String[] {"invite", "Tab"}).contains("Taboo"));
        assertTrue(cmd.tabComplete(p, "island", new String[] {"upgrade", ""}).contains("radius"));
        assertTrue(cmd.tabComplete(p, "island", new String[] {"setrole", "x", "c"}).contains("co-owner"));
        assertTrue("admin only", Bukkit.getCommandMap().getCommand("islandadmin").tabComplete(p, "islandadmin", new String[] {""}).isEmpty());
    }

    @Test
    public void createRegistersOneIslandAndRefusesASecond() {
        PlayerMock p = player("Founder");
        p.performCommand("island create bogus");
        assertTrue(said(p).contains("Unknown island type"));
        assertFalse(service.registry().hasIsland(p.getUniqueId()));
        p.performCommand("island create classic");
        assertTrue(service.registry().hasIsland(p.getUniqueId()));
        Island island = service.registry().ofPlayer(p.getUniqueId()).orElseThrow();
        assertEquals("classic", island.template);
        int count = service.registry().count();
        p.performCommand("island create desert");
        assertTrue(said(p).contains("already belong"));
        assertEquals(count, service.registry().count());
    }

    @Test
    public void inviteAcceptKickAndLeave() {
        PlayerMock owner = player("Boss");
        PlayerMock guest = player("Pal");
        Island island = readyIsland(owner);
        guest.performCommand("island accept");
        assertTrue(said(guest).contains("no pending invitation"));
        owner.performCommand("island invite Pal");
        String sent = said(owner);
        assertTrue(sent, sent.contains("Invitation sent"));
        assertTrue(said(guest).contains("invited you"));
        guest.performCommand("island accept");
        assertTrue(said(guest).contains("You joined"));
        assertEquals(IslandRole.MEMBER, island.roleOf(guest.getUniqueId()));
        guest.performCommand("island accept");
        assertTrue(said(guest).contains("no pending invitation"));
        assertEquals(2, service.islandOf(owner.getUniqueId()).orElseThrow().members().size());
        assertEquals(service.islandOf(owner.getUniqueId()).orElseThrow().id(), service.islandOf(guest.getUniqueId()).orElseThrow().id());

        // members cannot manage
        guest.performCommand("island kick Boss");
        assertTrue(said(guest).contains("Only the owner and co-owners"));
        guest.performCommand("island lock");
        assertFalse(island.locked);

        // leaving needs a second command
        guest.performCommand("island leave");
        assertTrue(said(guest).contains("again within"));
        assertEquals(IslandRole.MEMBER, island.roleOf(guest.getUniqueId()));
        guest.performCommand("island leave");
        assertEquals(IslandRole.VISITOR, island.roleOf(guest.getUniqueId()));
        assertFalse(service.registry().hasIsland(guest.getUniqueId()));

        // owner cannot leave, kick works
        owner.performCommand("island leave");
        assertTrue(said(owner).contains("owner cannot leave"));
        owner.performCommand("island invite Pal");
        guest.performCommand("island accept Boss");
        assertEquals(IslandRole.MEMBER, island.roleOf(guest.getUniqueId()));
        owner.performCommand("island kick Pal");
        assertEquals(IslandRole.VISITOR, island.roleOf(guest.getUniqueId()));
        owner.performCommand("island kick Boss");
        assertTrue(said(owner).contains("cannot"));
    }

    @Test
    public void memberSlotsBansAndTrust() {
        PlayerMock owner = player("Chief");
        Island island = readyIsland(owner);
        int slots = service.config().memberSlotsFor(island);
        List<PlayerMock> others = new ArrayList<>();
        for (int i = 0; i < slots + 1; i++) {
            others.add(player("Other" + i));
        }
        for (int i = 0; i < slots - 1; i++) {
            owner.performCommand("island invite Other" + i);
            others.get(i).performCommand("island accept");
        }
        assertEquals(slots, island.size());
        said(owner);
        owner.performCommand("island invite Other" + (slots - 1));
        PlayerMock extra = others.get(slots);
        owner.performCommand("island invite " + extra.getName());
        String full = said(owner);
        assertTrue(full, full.contains("full"));
        assertEquals(slots, island.size());

        PlayerMock bad = others.get(slots);
        owner.performCommand("island ban " + bad.getName());
        assertEquals(IslandRole.BANNED, island.roleOf(bad.getUniqueId()));
        assertFalse(service.canEnter(bad, island));
        owner.performCommand("island invite " + bad.getName());
        assertTrue(said(owner).contains("banned"));
        owner.performCommand("island trust " + bad.getName());
        assertEquals("a banned player cannot be trusted", IslandRole.BANNED, island.roleOf(bad.getUniqueId()));
        owner.performCommand("island unban " + bad.getName());
        assertEquals(IslandRole.VISITOR, island.roleOf(bad.getUniqueId()));
        owner.performCommand("island trust " + bad.getName());
        assertEquals(IslandRole.TRUSTED, island.roleOf(bad.getUniqueId()));
        owner.performCommand("island untrust " + bad.getName());
        assertEquals(IslandRole.VISITOR, island.roleOf(bad.getUniqueId()));
        owner.performCommand("island ban Chief");
        assertEquals("the owner cannot be banned", IslandRole.OWNER, island.roleOf(owner.getUniqueId()));
    }

    @Test
    public void rolesFlagsAndLocking() {
        PlayerMock owner = player("Lord");
        PlayerMock co = player("Deputy");
        PlayerMock stranger = player("Stranger");
        Island island = readyIsland(owner);
        owner.performCommand("island invite Deputy");
        co.performCommand("island accept");
        owner.performCommand("island setrole Deputy co-owner");
        assertEquals(IslandRole.CO_OWNER, island.roleOf(co.getUniqueId()));
        co.performCommand("island setrole Lord member");
        assertEquals(IslandRole.OWNER, island.roleOf(owner.getUniqueId()));
        co.performCommand("island flag pvp on");
        assertTrue(island.flag(IslandFlag.PVP));
        co.performCommand("island flag pvp maybe");
        assertTrue(island.flag(IslandFlag.PVP));
        co.performCommand("island time midnight");
        assertEquals(TimeLock.MIDNIGHT, island.timeLock());
        stranger.performCommand("island flag pvp off");
        assertTrue("strangers cannot change flags", island.flag(IslandFlag.PVP));
        co.performCommand("island lock");
        assertTrue(island.locked);
        assertFalse(service.canEnter(stranger, island));
        assertTrue(service.canEnter(co, island));
        stranger.performCommand("island visit Lord");
        assertTrue(said(stranger).contains("locked"));
        co.performCommand("island unlock");
        assertTrue(service.canEnter(stranger, island));
        // co-owners cannot reset or transfer
        co.performCommand("island reset");
        assertTrue(said(co).contains("Only the island owner"));
        co.performCommand("island transfer Lord");
        assertTrue(said(co).contains("Only the island owner"));
    }

    @Test
    public void transferNeedsAMemberAndAConfirmation() {
        PlayerMock owner = player("Old");
        PlayerMock heir = player("Heir");
        Island island = readyIsland(owner);
        owner.performCommand("island transfer Heir");
        assertTrue(said(owner).contains("must first be a member"));
        owner.performCommand("island invite Heir");
        heir.performCommand("island accept");
        owner.performCommand("island transfer Heir");
        assertEquals(owner.getUniqueId(), island.owner);
        owner.performCommand("island transfer Heir");
        assertEquals(heir.getUniqueId(), island.owner);
        assertEquals(IslandRole.CO_OWNER, island.roleOf(owner.getUniqueId()));
        assertSame(island, service.registry().ofPlayer(owner.getUniqueId()).orElseThrow());
    }

    @Test
    public void transferAndKickWorkForOfflineMembers() {
        PlayerMock owner = player("Present");
        PlayerMock heir = player("Gone");
        PlayerMock other = player("Gone2");
        Island island = readyIsland(owner);
        owner.performCommand("island invite Gone");
        heir.performCommand("island accept");
        owner.performCommand("island invite Gone2");
        other.performCommand("island accept");
        heir.disconnect();
        other.disconnect();
        owner.performCommand("island transfer Gone");
        owner.performCommand("island transfer Gone");
        assertEquals(heir.getUniqueId(), island.owner);
        // the former owner is now a co-owner of an island owned by an offline player
        owner.performCommand("island kick Gone2");
        assertEquals(IslandRole.VISITOR, island.roleOf(other.getUniqueId()));
        server.getScheduler().performTicks(5);
    }

    @Test
    public void resetNeedsConfirmationAndHonoursTheCooldown() {
        PlayerMock owner = player("Wiper");
        Island island = readyIsland(owner);
        island.lastResetAt = System.currentTimeMillis();
        owner.performCommand("island reset");
        String wait = said(owner);
        assertTrue(wait, wait.contains("You can reset again in"));
        assertEquals(Island.State.READY, island.state());
        owner.performCommand("island reset");
        assertEquals("cooldown still holds", Island.State.READY, island.state());
        island.lastResetAt = 0;
        owner.performCommand("island reset frozen");
        assertTrue(said(owner).contains("again within"));
        assertEquals("first call only arms", Island.State.READY, island.state());
        owner.performCommand("island reset frozen");
        assertEquals(Island.State.RESETTING, island.state());
        assertEquals("frozen", island.template);
        assertTrue(island.lastResetAt > 0);
        owner.performCommand("island reset frozen");
        assertTrue("busy while resetting", said(owner).contains("busy"));
    }

    @Test
    public void upgradesChargeOnceAndNeverWithoutCoins() {
        PlayerMock owner = player("Buyer");
        Island island = readyIsland(owner);
        int radiusBefore = service.config().radiusFor(island);
        plugin.core().unpublish(EconomyApi.class); // the real economy feature is enabled now: simulate it being off
        owner.performCommand("island upgrade radius");
        assertTrue("no economy: refused", said(owner).contains("not available"));
        assertEquals(0, island.radiusTier);

        FakeEconomy economy = new FakeEconomy();
        plugin.core().replace(EconomyApi.class, economy);
        owner.performCommand("island upgrade radius");
        assertTrue(said(owner).contains("You need"));
        assertEquals(0, island.radiusTier);
        assertEquals(0, economy.takes);

        double price = service.tiers(Upgrade.RADIUS).get(1).cost();
        assertTrue(price > 0);
        economy.balance = price + 1;
        owner.performCommand("island upgrade radius");
        assertEquals(1, island.radiusTier);
        assertEquals(1, economy.takes);
        assertEquals(1.0, economy.balance, 1e-9);
        assertTrue(service.config().radiusFor(island) > radiusBefore);
        assertEquals(service.config().radiusFor(island), service.radius(owner.getUniqueId()));
        assertEquals(service.config().radiusFor(island), service.islandOf(owner.getUniqueId()).orElseThrow().radius());

        // maximum: no charge
        economy.balance = 1e12;
        for (int i = 0; i < 40; i++) {
            owner.performCommand("island upgrade radius");
        }
        assertEquals(service.tiers(Upgrade.RADIUS).size() - 1, island.radiusTier);
        double after = economy.balance;
        owner.performCommand("island upgrade radius");
        assertEquals(after, economy.balance, 0);
        assertTrue(service.config().radiusFor(island) <= service.config().maxRadius());

        // visitors cannot buy
        PlayerMock visitor = player("Visitor");
        visitor.performCommand("island upgrade radius");
        assertTrue(said(visitor).contains("do not have an island"));
        assertEquals(0, service.minionSlotBonus(UUID.randomUUID()));
        assertEquals(0, service.radius(UUID.randomUUID()));
    }

    @Test
    public void adminCommandsNeedThePermission() {
        PlayerMock admin = player("Admin");
        PlayerMock owner = player("Victim");
        Island island = readyIsland(owner);
        admin.performCommand("islandadmin delete Victim");
        assertTrue(service.registry().hasIsland(owner.getUniqueId()));
        admin.setOp(true);
        admin.performCommand("islandadmin info Victim");
        assertTrue(said(admin).contains("Island of Victim"));
        admin.performCommand("islandadmin delete Victim");
        assertTrue("needs a repeat", service.registry().hasIsland(owner.getUniqueId()));
        admin.performCommand("islandadmin delete Victim");
        assertFalse(service.registry().hasIsland(owner.getUniqueId()));
        assertTrue(service.registry().byId(island.id).isEmpty());
        assertFalse("the cell is parked until wiped", service.registry().dirtyCells().isEmpty());
    }

    // ---------------------------------------------------------------- protection

    @Test
    public void blocksFollowTheRoleMatrix() {
        PlayerMock owner = player("Builder");
        PlayerMock guest = player("Guest");
        PlayerMock trusted = player("Friend");
        PlayerMock banned = player("Griefer");
        Island island = readyIsland(owner);
        island.trusted.add(trusted.getUniqueId());
        island.banned.add(banned.getUniqueId());
        Block block = overworld.getBlockAt(center(island));
        block.setType(Material.DIRT);

        assertFalse(breakBy(owner, block));
        assertTrue(breakBy(guest, block));
        assertFalse(breakBy(trusted, block));
        assertTrue(breakBy(banned, block));
        assertTrue("outside any island", breakBy(owner, overworld.getBlockAt(10, 65, 10)));

        // outside the radius of the island, still its cell
        int edge = service.config().radiusFor(island) + 5;
        Block far = overworld.getBlockAt(service.grid().centerOf(island.cellX) + edge, 65, service.grid().centerOf(island.cellZ));
        assertTrue("beyond the border", breakBy(owner, far));
        assertTrue(service.canBuild(owner, center(island)));
        assertFalse(service.canBuild(guest, center(island)));
        assertFalse(service.canBuild(owner, far.getLocation()));
        owner.addAttachment(plugin, IslandService.BYPASS, true);
        assertTrue("bypass", service.canBuild(owner, far.getLocation()));
    }

    private boolean breakBy(PlayerMock player, Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    public void placingIsBlockedWhileTheIslandIsBuilding() {
        PlayerMock owner = player("Early");
        Island island = service.registry().create(owner.getUniqueId(), "Early", "classic", 1);
        island.state(Island.State.BUILDING);
        Block block = overworld.getBlockAt(center(island));
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
            new org.bukkit.inventory.ItemStack(Material.DIRT), owner, true, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    public void islandLookupFollowsTheGrid() {
        PlayerMock owner = player("Mapper");
        Island island = readyIsland(owner);
        IslandInfo info = service.islandAt(center(island)).orElseThrow();
        assertEquals(island.id, info.id());
        assertEquals(owner.getUniqueId(), info.owner());
        assertTrue(service.islandAt(new Location(overworld, 5, 65, 5)).isEmpty());
        World nether = Bukkit.getWorld(SkyblockWorlds.NETHER);
        Location inNether = new Location(nether, center(island).getX(), 65, center(island).getZ());
        assertEquals("same cell in the Nether", island.id, service.islandAt(inNether).orElseThrow().id());
        assertEquals(1, service.all().size());
        assertTrue(service.dragonAltar(island.id).isPresent());
        assertEquals(SkyblockWorlds.END, service.dragonAltar(island.id).orElseThrow().getWorld().getName());
        assertEquals(center(island).getX(), service.dragonAltar(island.id).orElseThrow().getX(), 0.6);
        assertEquals(SkyblockWorlds.NETHER, service.netherSpawn(island.id).orElseThrow().getWorld().getName());
        assertTrue(service.dragonAltarAt(inNether).isEmpty());
    }

    // ---------------------------------------------------------------- portals, void, events

    @Test
    public void portalsStayInsideTheIslandDimensions() {
        PlayerMock owner = player("Portaler");
        Island island = readyIsland(owner);
        Location from = center(island);
        PlayerPortalEvent event = new PlayerPortalEvent(owner, from, from.clone(), TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertEquals(SkyblockWorlds.NETHER, event.getTo().getWorld().getName());
        assertEquals("1:1 coordinates, no x8", service.grid().cellOf(from.getX()), service.grid().cellOf(event.getTo().getX()));
        assertEquals(service.grid().cellOf(from.getZ()), service.grid().cellOf(event.getTo().getZ()));

        PlayerPortalEvent end = new PlayerPortalEvent(owner, from, from.clone(), TeleportCause.END_PORTAL);
        server.getPluginManager().callEvent(end);
        assertEquals(SkyblockWorlds.END, end.getTo().getWorld().getName());

        // from the island Nether back to the overworld home
        Location nether = event.getTo();
        PlayerPortalEvent back = new PlayerPortalEvent(owner, nether, nether.clone(), TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(back);
        assertEquals(SkyblockWorlds.WORLD, back.getTo().getWorld().getName());

        // gateway and portals in the hub lead nowhere
        Location hub = new Location(Bukkit.getWorld(SkyblockWorlds.HUB), 0, 100, 0);
        PlayerPortalEvent inHub = new PlayerPortalEvent(owner, hub, hub.clone(), TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(inHub);
        assertTrue(inHub.isCancelled());

        // not ready on the other side: refused
        island.platformsReady = false;
        PlayerPortalEvent early = new PlayerPortalEvent(owner, from, from.clone(), TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(early);
        assertTrue(early.isCancelled());
    }

    @Test
    public void foreignWorldsAreNotTouched() {
        PlayerMock p = player("Traveller");
        World vanilla = server.addSimpleWorld("creative_like");
        Location at = new Location(vanilla, 0, 70, 0);
        PlayerPortalEvent event = new PlayerPortalEvent(p, at, at.clone(), TeleportCause.NETHER_PORTAL);
        server.getPluginManager().callEvent(event);
        assertFalse("other worlds keep their vanilla portals", event.isCancelled());
        BlockBreakEvent breakEvent = new BlockBreakEvent(vanilla.getBlockAt(0, 70, 0), p);
        server.getPluginManager().callEvent(breakEvent);
        assertFalse(breakEvent.isCancelled());
    }

    @Test
    public void bannedPlayersCannotEnterAndNoticeIsOnce() {
        PlayerMock owner = player("Gate");
        PlayerMock bad = player("Intruder");
        Island island = readyIsland(owner);
        island.banned.add(bad.getUniqueId());
        Location target = center(island);
        var event = new org.bukkit.event.player.PlayerTeleportEvent(bad, bad.getLocation(), target, TeleportCause.COMMAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        Location pearl = center(island);
        var pearlEvent = new org.bukkit.event.player.PlayerTeleportEvent(owner, new Location(overworld, 5, 65, 5), pearl,
            TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(pearlEvent);
        assertTrue("pearls cannot cross the border", pearlEvent.isCancelled());
    }

    @Test
    public void enterEventFiresOnceWhenAPlayerArrives() {
        PlayerMock owner = player("Arriver");
        Island island = readyIsland(owner);
        AtomicInteger entered = new AtomicInteger();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(IslandEnterEvent event) {
                entered.incrementAndGet();
            }
        }, plugin);
        service.teleport(owner, center(island));
        server.getScheduler().performTicks(5);
        owner.teleport(center(island).add(2, 0, 2));
        server.getScheduler().performTicks(5);
        assertEquals(1, entered.get());
    }

    @Test
    public void voidRescueSendsThePlayerHomeOnce() {
        PlayerMock owner = player("Faller");
        Island island = readyIsland(owner);
        Location below = center(island).clone();
        below.setY(overworld.getMinHeight() - 40);
        owner.teleport(below);
        server.getScheduler().performTicks(2);
        IslandTracker tracker = null;
        for (var listener : org.bukkit.event.HandlerList.getRegisteredListeners(plugin)) {
            if (listener.getListener() instanceof IslandTracker t) {
                tracker = t;
            }
        }
        assertNotNull(tracker);
        tracker.rescue(owner, below);
        server.getScheduler().performTicks(5);
        assertTrue(said(owner).contains("fell into the void"));
        assertTrue("back above the void", owner.getLocation().getY() > overworld.getMinHeight());
        tracker.rescue(owner, below);
        assertFalse("a second rescue inside the guard window is ignored", said(owner).contains("fell into the void"));
    }

    // ---------------------------------------------------------------- hub

    @Test
    public void hubTeleportNeedsTheHubToBeReady() {
        PlayerMock p = player("Visitor");
        service.hubState(false, false);
        assertFalse(service.teleportHub(p));
        p.performCommand("hub");
        assertTrue(said(p).contains("not ready"));
        service.hubState(true, true);
        assertTrue(service.teleportHub(p));
        server.getScheduler().performTicks(3);
        assertEquals(SkyblockWorlds.HUB, p.getWorld().getName());
        assertTrue(service.hubSpawn().isPresent());
        assertEquals(HubPlan.PADS.size(), service.hubPads().size());
    }

    @Test
    public void teleportHomeWorksOnlyForReadyIslands() {
        PlayerMock owner = player("Homebody");
        assertFalse(service.teleportHome(owner));
        Island island = service.registry().create(owner.getUniqueId(), "Homebody", "classic", 1);
        island.state(Island.State.BUILDING);
        assertFalse(service.teleportHome(owner));
        island.state(Island.State.READY);
        assertTrue(service.teleportHome(owner));
    }

    // ---------------------------------------------------------------- menus

    private Gui openGui(PlayerMock p) {
        var holder = p.getOpenInventory().getTopInventory().getHolder();
        assertTrue("a menu is open", holder instanceof Gui);
        return (Gui) holder;
    }

    @Test
    public void everyMenuOpensForEveryKindOfPlayer() {
        PlayerMock owner = player("Menu");
        PlayerMock none = player("Nobody");
        IslandMenus menus = new IslandMenus(service);
        menus.main(none);
        openGui(none);
        menus.templates(none, id -> {}, "Pick");
        assertTrue(openGui(none).size() >= 27);
        menus.warps(none);
        menus.top(none);
        Island island = readyIsland(owner);
        island.warp = new Island.Warp();
        island.warp.name = "<red>Bad name";
        island.warp.pos = Pos.of(center(island));
        island.level = 5;
        menus.main(owner);
        assertEquals(45, openGui(owner).size());
        menus.settings(owner);
        menus.upgrades(owner);
        menus.members(owner);
        menus.top(owner);
        menus.warps(owner);
        menus.confirm(owner, "Sure?", List.of("<gray>x"), () -> {});
        assertEquals("exactly one top-level menu", 27, openGui(owner).size());
        assertEquals(1, service.publicWarps().size());
        owner.closeInventory();
    }

    @Test
    public void settingsMenuTogglesAFlag() {
        PlayerMock owner = player("Toggler");
        Island island = readyIsland(owner);
        IslandMenus menus = new IslandMenus(service);
        menus.settings(owner);
        boolean before = island.flag(IslandFlag.PVP);
        var inventory = owner.getOpenInventory();
        var view = owner.getOpenInventory();
        var click = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            10, org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue("clicks never move items", click.isCancelled());
        assertEquals(!before, island.flag(IslandFlag.PVP));
        assertNotNull(inventory);
    }
}
