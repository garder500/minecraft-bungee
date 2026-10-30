package win.vampyuyu.survival.island;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandAccess.Action;
import win.vampyuyu.survival.island.IslandAccess.Cap;

/** Pure logic of the island feature: grid, roles, config, registry, invites, portals, levels, templates, hub plan. */
public class IslandPureTest {
    private static final Logger LOG = Logger.getAnonymousLogger();

    @Before
    public void setUp() {
        MockBukkit.mock();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return yaml;
    }

    static IslandConfig defaults() {
        var in = IslandPureTest.class.getResourceAsStream("/island.yml");
        assertNotNull("island.yml must ship in the jar", in);
        return new IslandConfig(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), LOG);
    }

    // ---------------------------------------------------------------- grid

    @Test
    public void spiralCellsAreUniqueAndAdjacent() {
        Set<Long> seen = new HashSet<>();
        int[] previous = null;
        for (long n = 0; n < 5000; n++) {
            int[] cell = IslandGrid.spiral(n);
            assertTrue("duplicate cell at " + n, seen.add(IslandGrid.key(cell[0], cell[1])));
            if (previous != null) {
                int d = Math.abs(cell[0] - previous[0]) + Math.abs(cell[1] - previous[1]);
                assertEquals("cells " + (n - 1) + " and " + n + " must touch", 1, d);
            }
            previous = cell;
        }
        assertArrayEquals(new int[] {0, 0}, IslandGrid.spiral(0));
        assertArrayEquals(new int[] {1, 0}, IslandGrid.spiral(1));
        assertTrue(IslandGrid.reserved(0, 0));
        assertFalse(IslandGrid.reserved(1, 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void tinyCellSizeIsRefused() {
        new IslandGrid(10);
    }

    @Test
    public void gridCoordinatesRoundTrip() {
        IslandGrid grid = new IslandGrid(400);
        assertEquals(0, grid.cellOf(0));
        assertEquals(0, grid.cellOf(199.9));
        assertEquals(1, grid.cellOf(200));
        assertEquals(-1, grid.cellOf(-201));
        assertEquals(0, grid.cellOf(-200));
        for (int cell = -5; cell <= 5; cell++) {
            assertEquals(cell, grid.cellOf(grid.centerOf(cell)));
        }
        assertTrue(grid.within(1, 0, 30, 400, 0));
        assertTrue(grid.within(1, 0, 30, 430, 30));
        assertFalse(grid.within(1, 0, 30, 431, 0));
        assertFalse(grid.within(1, 0, 30, 400, -31));
    }

    // ---------------------------------------------------------------- roles

    private static Island island() {
        Island island = new Island();
        island.id = UUID.randomUUID();
        island.owner = UUID.randomUUID();
        return island;
    }

    @Test
    public void rolesAreResolvedWithBanFirst() {
        Island island = island();
        UUID co = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        island.members.put(co, "CO_OWNER");
        island.members.put(member, "MEMBER");
        island.trusted.add(guest);
        island.banned.add(bad);
        island.trusted.add(bad);
        assertEquals(IslandRole.OWNER, island.roleOf(island.owner));
        assertEquals(IslandRole.CO_OWNER, island.roleOf(co));
        assertEquals(IslandRole.MEMBER, island.roleOf(member));
        assertEquals(IslandRole.TRUSTED, island.roleOf(guest));
        assertEquals(IslandRole.BANNED, island.roleOf(bad));
        assertEquals(IslandRole.VISITOR, island.roleOf(UUID.randomUUID()));
        assertEquals(IslandRole.VISITOR, island.roleOf(null));
        assertEquals(3, island.size());
    }

    @Test
    public void accessMatrix() {
        Island island = island();
        for (IslandRole role : List.of(IslandRole.OWNER, IslandRole.CO_OWNER, IslandRole.MEMBER)) {
            for (Action action : Action.values()) {
                assertTrue(role + " " + action, IslandAccess.can(island, role, action, false));
            }
        }
        // trusted guests build but only open chests when allowed
        assertTrue(IslandAccess.can(island, IslandRole.TRUSTED, Action.BUILD, false));
        assertTrue(IslandAccess.can(island, IslandRole.TRUSTED, Action.ANIMAL, false));
        assertFalse(IslandAccess.can(island, IslandRole.TRUSTED, Action.CONTAINER, false));
        assertTrue(IslandAccess.can(island, IslandRole.TRUSTED, Action.CONTAINER, true));
        // visitors follow the flags
        assertFalse(IslandAccess.can(island, IslandRole.VISITOR, Action.BUILD, true));
        assertFalse(IslandAccess.can(island, IslandRole.VISITOR, Action.ANIMAL, true));
        assertTrue(IslandAccess.can(island, IslandRole.VISITOR, Action.INTERACT, false));
        island.flag(IslandFlag.VISITOR_INTERACT, false);
        assertFalse(IslandAccess.can(island, IslandRole.VISITOR, Action.INTERACT, false));
        assertFalse(IslandAccess.can(island, IslandRole.VISITOR, Action.CONTAINER, true));
        island.flag(IslandFlag.VISITOR_CHESTS, true);
        assertTrue(IslandAccess.can(island, IslandRole.VISITOR, Action.CONTAINER, false));
        assertTrue(IslandAccess.can(island, IslandRole.TRUSTED, Action.CONTAINER, false));
        // banned players can do nothing
        for (Action action : Action.values()) {
            assertFalse(IslandAccess.can(island, IslandRole.BANNED, action, true));
        }
    }

    @Test
    public void capabilitiesAndRank() {
        for (Cap cap : Cap.values()) {
            assertTrue(IslandAccess.has(IslandRole.OWNER, cap));
            assertFalse(IslandAccess.has(IslandRole.MEMBER, cap));
            assertFalse(IslandAccess.has(IslandRole.TRUSTED, cap));
            assertFalse(IslandAccess.has(IslandRole.VISITOR, cap));
        }
        assertTrue(IslandAccess.has(IslandRole.CO_OWNER, Cap.KICK));
        assertFalse(IslandAccess.has(IslandRole.CO_OWNER, Cap.TRANSFER));
        assertFalse(IslandAccess.has(IslandRole.CO_OWNER, Cap.RESET));
        assertFalse(IslandAccess.has(IslandRole.CO_OWNER, Cap.SETROLE));
        assertTrue(IslandAccess.outranks(IslandRole.OWNER, IslandRole.CO_OWNER));
        assertFalse(IslandAccess.outranks(IslandRole.CO_OWNER, IslandRole.CO_OWNER));
        assertFalse(IslandAccess.outranks(IslandRole.MEMBER, IslandRole.OWNER));
    }

    @Test
    public void flagsFallBackToDefaultsAndIdsRoundTrip() {
        Island island = island();
        for (IslandFlag flag : IslandFlag.values()) {
            assertEquals(flag.defaultValue(), island.flag(flag));
            assertSame(flag, IslandFlag.byId(flag.id()));
        }
        assertSame(IslandFlag.VISITOR_CHESTS, IslandFlag.byId("visitor-chests"));
        assertNull(IslandFlag.byId("nope"));
        assertNull(IslandFlag.byId(null));
        island.flag(IslandFlag.PVP, true);
        assertTrue(island.flag(IslandFlag.PVP));
        assertFalse(IslandFlag.PVP.defaultValue());
    }

    @Test
    public void timeLocksParseAndCycle() {
        assertSame(TimeLock.NONE, TimeLock.parse(null));
        assertSame(TimeLock.NONE, TimeLock.parse("garbage"));
        assertSame(TimeLock.NIGHT, TimeLock.parse("night"));
        TimeLock lock = TimeLock.NONE;
        Set<TimeLock> seen = new HashSet<>();
        for (int i = 0; i < TimeLock.values().length; i++) {
            seen.add(lock);
            lock = lock.next();
        }
        assertEquals(TimeLock.values().length, seen.size());
        assertSame(TimeLock.NONE, lock);
        assertEquals(-1, TimeLock.NONE.ticks());
    }

    // ---------------------------------------------------------------- config

    @Test
    public void shippedConfigIsSane() {
        IslandConfig cfg = defaults();
        assertEquals(400, cfg.cellSize);
        assertTrue("at least 5 enabled templates", cfg.enabledTemplates().size() >= 5);
        Set<String> chestSignatures = new HashSet<>();
        for (IslandConfig.TemplateCfg t : cfg.enabledTemplates()) {
            assertTrue("template " + t.id() + " is implemented", IslandTemplates.exists(t.id()));
            assertFalse("template " + t.id() + " has a chest", t.chest().isEmpty());
            assertTrue("distinct chest for " + t.id(), chestSignatures.add(t.chest().toString()));
            assertFalse(t.name().isBlank());
        }
        assertEquals(0, cfg.radiusTiers.get(0).cost(), 0);
        for (int i = 1; i < cfg.radiusTiers.size(); i++) {
            assertTrue(cfg.radiusTiers.get(i).value() > cfg.radiusTiers.get(i - 1).value());
        }
        assertTrue(cfg.radiusTiers.get(cfg.radiusTiers.size() - 1).value() <= cfg.maxRadius());
        assertTrue(cfg.memberSlotsFor(new Island()) >= 1);
        for (String key : List.of("hub", "world", "nether", "end")) {
            assertNotNull("world config " + key, cfg.worlds.get(key));
        }
        assertEquals(0.0, cfg.minionSlotsFor(new Island()), 0);
    }

    @Test
    public void tiersAreClampedSortedAndFirstIsFree() {
        IslandConfig cfg = new IslandConfig(yaml("""
            grid:
              cell-size: 200
              edge-margin: 10
            upgrades:
              radius:
                - {radius: 20, cost: 50}
                - {radius: 40, cost: 100}
                - {radius: 30, cost: 100}
                - {radius: 5000, cost: 900}
                - {radius: 6000, cost: 900}
              members:
                - {slots: 2, cost: 10}
            """), LOG);
        assertEquals(90, cfg.maxRadius());
        List<IslandConfig.Tier> tiers = cfg.radiusTiers;
        assertEquals(List.of(20, 40, 90), tiers.stream().map(IslandConfig.Tier::value).toList());
        assertEquals(0, tiers.get(0).cost(), 0);
        assertEquals(0, cfg.memberTiers.get(0).cost(), 0);
        Island island = new Island();
        island.radiusTier = 99;
        assertEquals(90, cfg.radiusFor(island));
        island.radiusTier = -3;
        assertEquals(20, cfg.radiusFor(island));
    }

    @Test
    public void itemSpecsSkipGarbage() {
        List<IslandConfig.ItemSpec> items = IslandConfig.items(
            List.of("ICE:2", "lava_bucket", "NOT_A_THING", "DIRT:x", "STONE:0", "OAK_SAPLING:500", "AIR"), LOG);
        assertEquals(3, items.size());
        assertEquals(Material.ICE, items.get(0).material());
        assertEquals(2, items.get(0).amount());
        assertEquals(1, items.get(1).amount());
        assertEquals(Material.OAK_SAPLING.getMaxStackSize(), items.get(2).amount());
    }

    // ---------------------------------------------------------------- registry

    private IslandRegistry registry(AtomicLong changes) {
        return new IslandRegistry(LOG, changes::incrementAndGet);
    }

    @Test
    public void registryAllocatesDistinctCellsAndSkipsTheReservedOne() {
        AtomicLong changes = new AtomicLong();
        IslandRegistry registry = registry(changes);
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Island island = registry.create(UUID.randomUUID(), "P" + i, "classic", 1000 + i);
            assertFalse(IslandGrid.reserved(island.cellX, island.cellZ));
            assertTrue(cells.add(IslandGrid.key(island.cellX, island.cellZ)));
        }
        assertEquals(50, registry.count());
        assertTrue(changes.get() >= 50);
    }

    @Test(expected = IllegalStateException.class)
    public void onePlayerCannotOwnTwoIslands() {
        IslandRegistry registry = registry(new AtomicLong());
        UUID owner = UUID.randomUUID();
        registry.create(owner, "A", "classic", 1);
        registry.create(owner, "A", "classic", 2);
    }

    @Test
    public void membershipTransferAndDelete() {
        IslandRegistry registry = registry(new AtomicLong());
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        registry.rememberName(b, "Bee");
        Island island = registry.create(a, "Ay", "classic", 1);
        assertTrue(registry.addMember(island, b, IslandRole.MEMBER));
        assertFalse("already in an island", registry.addMember(island, b, IslandRole.MEMBER));
        assertFalse("owner role cannot be dealt", registry.addMember(island, c, IslandRole.OWNER));
        assertFalse("trusted is not co-op", registry.addMember(island, c, IslandRole.TRUSTED));
        assertSame(island, registry.ofPlayer(b).orElseThrow());
        assertFalse("members cannot found a second island", registry.ofPlayer(c).isPresent());
        assertTrue(registry.transfer(island, b));
        assertEquals(b, island.owner);
        assertEquals(IslandRole.CO_OWNER, island.roleOf(a));
        assertFalse(registry.transfer(island, c));
        assertTrue(registry.removeMember(island, a));
        assertFalse(registry.ofPlayer(a).isPresent());

        int cellX = island.cellX;
        int cellZ = island.cellZ;
        registry.delete(island);
        assertFalse(registry.hasIsland(b));
        assertEquals(1, registry.dirtyCells().size());
        Island next = registry.create(a, "Ay", "classic", 5);
        assertFalse("a dirty cell is not reused", next.cellX == cellX && next.cellZ == cellZ);
        registry.releaseCell(cellX, cellZ);
        assertTrue(registry.dirtyCells().isEmpty());
        Island reuse = registry.create(c, "Cee", "classic", 6);
        assertEquals("the released cell comes back first", cellX, reuse.cellX);
        assertEquals(cellZ, reuse.cellZ);
    }

    @Test
    public void registryJsonRoundTripKeepsEverything() {
        IslandRegistry registry = registry(new AtomicLong());
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        Island island = registry.create(owner, "Owner", "desert", 99);
        registry.addMember(island, member, IslandRole.CO_OWNER);
        island.trusted.add(guest);
        island.banned.add(bad);
        island.flag(IslandFlag.PVP, true);
        island.flag(IslandFlag.WEATHER, false);
        island.timeLock = TimeLock.NIGHT.name();
        island.locked = true;
        island.radiusTier = 2;
        island.memberTier = 1;
        island.minionTier = 3;
        island.value = 1234.5;
        island.level = 12;
        island.platformsReady = true;
        island.state(Island.State.READY);
        island.home = new Pos("sb_world", 1.5, 65, 2.5, 90, 0);
        island.warp = new Island.Warp();
        island.warp.name = "Cool spot";
        island.warp.pos = new Pos("sb_world", 3, 65, 4, 0, 0);
        island.warp.visits = 7;
        registry.rememberName(owner, "Owner");

        IslandRegistry loaded = IslandRegistry.fromJson(registry.toJson(), LOG, null);
        Island copy = loaded.byId(island.id).orElseThrow();
        assertEquals(owner, copy.owner);
        assertEquals("desert", copy.template);
        assertEquals(IslandRole.CO_OWNER, copy.roleOf(member));
        assertEquals(IslandRole.TRUSTED, copy.roleOf(guest));
        assertEquals(IslandRole.BANNED, copy.roleOf(bad));
        assertTrue(copy.flag(IslandFlag.PVP));
        assertFalse(copy.flag(IslandFlag.WEATHER));
        assertEquals(TimeLock.NIGHT, copy.timeLock());
        assertTrue(copy.locked);
        assertEquals(2, copy.radiusTier);
        assertEquals(1, copy.memberTier);
        assertEquals(3, copy.minionTier);
        assertEquals(1234.5, copy.value, 0);
        assertEquals(12, copy.level);
        assertTrue(copy.platformsReady);
        assertEquals(Island.State.READY, copy.state());
        assertEquals(2.5, copy.home.z, 0);
        assertEquals("Cool spot", copy.warp.name);
        assertEquals(7, copy.warp.visits);
        assertEquals(island.cellX, copy.cellX);
        assertSame(copy, loaded.ofPlayer(member).orElseThrow());
        assertSame(copy, loaded.byCell(island.cellX, island.cellZ).orElseThrow());
        assertEquals("Owner", loaded.nameOf(owner));
        assertEquals(owner, loaded.uuidOf("owner").orElseThrow());
        assertEquals(registry.nextIndex(), loaded.nextIndex());
        // the next island must not collide with the loaded one
        Island fresh = loaded.create(UUID.randomUUID(), "New", "classic", 1);
        assertFalse(fresh.cellX == copy.cellX && fresh.cellZ == copy.cellZ);
    }

    @Test
    public void registryRejectsDuplicatesAndTheReservedCell() {
        UUID owner = UUID.randomUUID();
        String json = """
            {"version":1,"nextIndex":3,"islands":[
              {"id":"%s","owner":"%s","cellX":1,"cellZ":0},
              {"id":"%s","owner":"%s","cellX":1,"cellZ":0},
              {"id":"%s","owner":"%s","cellX":0,"cellZ":0},
              {"id":"%s","owner":"%s","cellX":4,"cellZ":4},
              {"owner":"%s","cellX":5,"cellZ":5}
            ]}""".formatted(UUID.randomUUID(), owner, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), owner, UUID.randomUUID());
        IslandRegistry loaded = IslandRegistry.fromJson(json, LOG, null);
        assertEquals(1, loaded.count());
        
    }

    @Test(expected = IllegalArgumentException.class)
    public void brokenRegistryJsonIsReported() {
        IslandRegistry.fromJson("{not json", LOG, null);
    }

    // ---------------------------------------------------------------- invites and confirms

    @Test
    public void invitesExpireRefreshAndAreCapped() {
        AtomicLong clock = new AtomicLong(1000);
        Invites invites = new Invites(clock::get);
        UUID target = UUID.randomUUID();
        UUID islandA = UUID.randomUUID();
        UUID islandB = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();
        assertEquals(Invites.Added.SENT, invites.add(target, islandA, inviter, 5000, 2));
        assertEquals(Invites.Added.REFRESHED, invites.add(target, islandA, inviter, 5000, 2));
        assertEquals(Invites.Added.SENT, invites.add(target, islandB, inviter, 5000, 2));
        assertEquals(Invites.Added.TOO_MANY, invites.add(target, UUID.randomUUID(), inviter, 5000, 2));
        assertEquals(2, invites.pending(target).size());
        assertEquals(islandB, invites.take(target, null).orElseThrow().island());
        assertEquals(islandA, invites.take(target, islandA).orElseThrow().island());
        assertTrue(invites.take(target, islandA).isEmpty());
        invites.add(target, islandA, inviter, 5000, 2);
        clock.set(7000);
        assertTrue("expired", invites.pending(target).isEmpty());
        assertTrue(invites.take(target, null).isEmpty());
        invites.add(target, islandA, inviter, 5000, 2);
        invites.dropIsland(islandA);
        assertTrue(invites.pending(target).isEmpty());
    }

    @Test
    public void confirmationsAreOneShotAndExpire() {
        AtomicLong clock = new AtomicLong(0);
        Confirms confirms = new Confirms(clock::get);
        UUID player = UUID.randomUUID();
        assertFalse(confirms.consume(player, "reset", ""));
        confirms.arm(player, "reset", "", 1000);
        assertTrue(confirms.consume(player, "reset", ""));
        assertFalse("single use", confirms.consume(player, "reset", ""));
        confirms.arm(player, "reset", "", 1000);
        assertFalse("another argument", confirms.consume(player, "reset", "frozen"));
        confirms.arm(player, "transfer", "x", 1000);
        clock.set(1001);
        assertFalse("expired", confirms.consume(player, "transfer", "x"));
        confirms.arm(player, "leave", null, 1000);
        confirms.clear(player);
        assertFalse(confirms.consume(player, "leave", null));
    }

    // ---------------------------------------------------------------- portals

    @Test
    public void portalsOnlyLinkTheIslandDimensions() {
        PortalMapper.Route toNether = PortalMapper.route(SkyblockWorlds.WORLD, TeleportCause.NETHER_PORTAL, true, true);
        assertEquals(SkyblockWorlds.NETHER, toNether.world());
        assertFalse(toNether.home());
        assertTrue(PortalMapper.route(SkyblockWorlds.NETHER, TeleportCause.NETHER_PORTAL, true, true).home());
        assertEquals(SkyblockWorlds.WORLD, PortalMapper.route(SkyblockWorlds.NETHER, TeleportCause.NETHER_PORTAL, true, true).world());
        assertNull(PortalMapper.route(SkyblockWorlds.WORLD, TeleportCause.END_PORTAL, true, true));
        assertNull(PortalMapper.route(SkyblockWorlds.END, TeleportCause.END_PORTAL, true, true));
        // never anywhere else
        assertNull(PortalMapper.route(SkyblockWorlds.HUB, TeleportCause.NETHER_PORTAL, true, true));
        assertNull(PortalMapper.route(SkyblockWorlds.END, TeleportCause.NETHER_PORTAL, true, true));
        assertNull(PortalMapper.route(SkyblockWorlds.NETHER, TeleportCause.END_PORTAL, true, true));
        assertNull(PortalMapper.route("world", TeleportCause.NETHER_PORTAL, true, true));
        assertNull(PortalMapper.route("creative_nether", TeleportCause.END_PORTAL, true, true));
        assertNull(PortalMapper.route(null, TeleportCause.NETHER_PORTAL, true, true));
        assertNull(PortalMapper.route(SkyblockWorlds.WORLD, TeleportCause.END_GATEWAY, true, true));
        assertNull(PortalMapper.route(SkyblockWorlds.WORLD, TeleportCause.NETHER_PORTAL, false, true));
        assertNull(PortalMapper.route(SkyblockWorlds.WORLD, TeleportCause.END_PORTAL, true, false));
    }

    // ---------------------------------------------------------------- level

    @Test
    public void levelUsesWeightsAndIgnoresNegatives() {
        Map<Material, Long> counts = Map.of(Material.DIAMOND_BLOCK, 10L, Material.DIRT, 1000L, Material.GOLD_BLOCK, -5L);
        Map<Material, Double> weights = Map.of(Material.DIAMOND_BLOCK, 50.0, Material.GOLD_BLOCK, 20.0);
        assertEquals(500.0, LevelCalculator.value(counts, weights, 0), 1e-9);
        assertEquals(500.0 + 1000 * 0.1, LevelCalculator.value(counts, weights, 0.1), 1e-9);
        assertEquals(5, LevelCalculator.level(500, 100));
        assertEquals(4, LevelCalculator.level(499.99, 100));
        assertEquals(0, LevelCalculator.level(-3, 100));
        assertEquals(0, LevelCalculator.level(100, 0));
    }

    // ---------------------------------------------------------------- templates and hub

    @Test
    public void everyTemplateHasSolidSpawnAndItsChest() {
        IslandConfig cfg = defaults();
        for (IslandConfig.TemplateCfg t : cfg.enabledTemplates()) {
            BlockPlan plan = IslandTemplates.overworld(t.id(), IslandTemplates.random(UUID.randomUUID()), t.chest(),
                t.biome(), true);
            assertTrue(t.id() + " places blocks", plan.size() > 100);
            assertTrue(t.id() + " solid ground under the spawn", plan.solidAt(0, 0, 0));
            assertFalse(t.id() + " feet free", plan.solidAt(0, 1, 0));
            assertFalse(t.id() + " head free", plan.solidAt(0, 2, 0));
            assertEquals(t.id() + " has exactly one starter chest", 1, plan.chests().size());
            assertEquals(t.chest(), plan.chests().get(0).items());
            assertTrue(t.id() + " keeps a bedrock core", plan.count(Material.BEDROCK) >= 1);
        }
        // deterministic for one island
        UUID id = UUID.randomUUID();
        BlockPlan one = IslandTemplates.overworld("classic", IslandTemplates.random(id), List.of(), "", true);
        BlockPlan two = IslandTemplates.overworld("classic", IslandTemplates.random(id), List.of(), "", true);
        assertEquals(one.blocks().keySet(), two.blocks().keySet());
    }

    @Test
    public void netherAndEndPlatformsHaveSpawnAndAltar() {
        BlockPlan nether = IslandTemplates.nether(IslandTemplates.random(UUID.randomUUID()), List.of(Material.COOKED_BEEF).stream()
            .map(m -> new IslandConfig.ItemSpec(m, 4)).toList());
        int[] ns = IslandTemplates.NETHER_SPAWN;
        assertTrue("solid under the nether spawn", nether.solidAt(ns[0], ns[1] - 1, ns[2]));
        assertFalse(nether.solidAt(ns[0], ns[1], ns[2]));
        assertTrue(nether.count(Material.GLOWSTONE) > 0);
        assertTrue(nether.count(Material.SOUL_SAND) > 0);
        assertTrue(nether.count(Material.NETHER_WART) > 0);
        assertTrue(nether.count(Material.LAVA) > 0);
        assertTrue(nether.count(Material.NETHER_QUARTZ_ORE) > 0);
        assertFalse("no spawner in the nether ruin", nether.count(Material.SPAWNER) > 0);
        assertEquals(1, nether.chests().size());

        BlockPlan end = IslandTemplates.end(IslandTemplates.random(UUID.randomUUID()), List.of());
        int[] altar = IslandTemplates.END_ALTAR;
        assertEquals("altar heart", Material.CRYING_OBSIDIAN, end.get(altar[0], altar[1] - 1, altar[2]));
        assertEquals("obsidian altar slab", Material.OBSIDIAN, end.get(1, altar[1] - 1, 1));
        assertArrayEquals(altar, end.anchor("altar"));
        int[] es = IslandTemplates.END_SPAWN;
        assertTrue(end.solidAt(es[0], es[1] - 1, es[2]));
        assertTrue(end.count(Material.OBSIDIAN) > 25);
        assertTrue(end.count(Material.END_STONE) > 100);
    }

    @Test
    public void hubPlanRespectsTheCapAndKeepsTheSpawn() {
        BlockPlan plan = HubPlan.build(2000);
        assertTrue("cap respected: " + plan.size(), plan.size() <= 2000);
        assertNotNull(plan.anchor("spawn"));
        assertArrayEquals(new int[] {HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z}, plan.anchor("spawn"));
        BlockPlan full = HubPlan.build(45000);
        assertTrue(full.size() > 2000);
        assertTrue(full.size() <= 45000);
        assertTrue("spawn pad is solid", full.solidAt(HubPlan.SPAWN_X, 0, HubPlan.SPAWN_Z));
        assertFalse(full.solidAt(HubPlan.SPAWN_X, 1, HubPlan.SPAWN_Z));
        for (HubPlan.Pad pad : HubPlan.PADS) {
            assertNotNull("anchor for pad " + pad.id(), full.anchor("pad:" + pad.id()));
            assertEquals(pad, HubPlan.pad(pad.id()));
        }
        assertEquals(10, HubPlan.PADS.size());
        assertTrue(HubPlan.emergencySpawn().size() < 300);
        assertNull(HubPlan.pad("nope"));
    }

    @Test
    public void hubMarkerDecidesWhetherToRebuild() {
        HubManager.Marker marker = new HubManager.Marker();
        assertFalse(HubManager.upToDate(null, 100));
        assertFalse(HubManager.upToDate(marker, 100));
        marker.version = HubPlan.VERSION;
        marker.hubY = 100;
        assertFalse("not complete", HubManager.upToDate(marker, 100));
        marker.complete = true;
        assertTrue(HubManager.upToDate(marker, 100));
        assertFalse("hub moved", HubManager.upToDate(marker, 90));
        marker.version = HubPlan.VERSION - 1;
        assertFalse("old plan", HubManager.upToDate(marker, 100));
    }

    @Test
    public void namesAreSanitisedForWarps() {
        assertEquals("Cool spot", IslandService.sanitizeName("  Cool   spot "));
        assertEquals("redabc", IslandService.sanitizeName("<red>a§b&c"));
        assertEquals("It's-ok_1", IslandService.sanitizeName("It's-ok_1"));
    }

    @Test
    public void escapingKeepsPlayerNamesInert() {
        assertFalse(IslandMenus.esc("<red>Evil").contains("<red>") && !IslandMenus.esc("<red>Evil").contains("\\<"));
        assertEquals("", IslandMenus.esc(null));
    }
}
