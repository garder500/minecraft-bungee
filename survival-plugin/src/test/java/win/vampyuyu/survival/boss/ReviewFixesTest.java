package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.WingsApi;

/** Regression tests of the review fixes: one test per bug. */
public class ReviewFixesTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private WorldMock world;
    private BossManager manager;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        world = server.addSimpleWorld("sb_world");
        world.getChunkAt(world.getSpawnLocation()).load();
        manager = feature().manager();
        assertNotNull(manager);
    }

    private BossFeature feature() {
        try {
            java.lang.reflect.Field field = win.vampyuyu.survival.core.FeatureManager.class.getDeclaredField("running");
            field.setAccessible(true);
            for (Object feature : (List<?>) field.get(plugin.features())) {
                if (feature instanceof BossFeature boss) {
                    return boss;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("boss feature not running");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private BossSession spawn(String id) {
        BossDefinition def = manager.config().definition(id).orElseThrow();
        return manager.spawn(def, world.getSpawnLocation(), null, BossSession.SpawnOptions.regular()).orElseThrow();
    }

    // ---------------------------------------------------------------- damage vs the items pipeline

    /** Stands in for the items feature: mitigates at HIGH and records what the player finally takes at MONITOR. */
    private static final class FakeItems implements Listener {
        final List<Double> seenAtHigh = new ArrayList<>();
        final List<Double> finalDamage = new ArrayList<>();

        @EventHandler(priority = EventPriority.HIGH)
        public void high(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player) {
                seenAtHigh.add(event.getDamage());
                event.setDamage(event.getDamage() / 2.0); // 100 defense
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void monitor(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player) {
                finalDamage.add(event.getDamage());
            }
        }
    }

    @Test
    public void rawDragonHitReachesTheItemsPipelineAndIsNotOverwrittenAfterIt() {
        BossSession session = spawn("voidwyrm");
        PlayerMock victim = server.addPlayer();
        victim.teleport(session.entity().getLocation().add(3, 0, 0));
        FakeItems items = new FakeItems();
        Bukkit.getPluginManager().registerEvents(items, plugin);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(session.entity(), victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        double expected = Math.max(0.5, session.hitDamage(1.0));
        assertEquals("the boss damage is set BEFORE the items listener (LOW)", expected, items.seenAtHigh.get(0), 1e-9);
        assertEquals("and the items mitigation survives", expected / 2.0, items.finalDamage.get(0), 1e-9);
        session.end(BossSession.End.ADMIN, null);
    }

    @Test
    public void abilityHitKeepsItsMultiplier() {
        BossSession session = spawn("voidwyrm");
        PlayerMock victim = server.addPlayer();
        victim.teleport(session.entity().getLocation().add(3, 0, 0));
        FakeItems items = new FakeItems();
        Bukkit.getPluginManager().registerEvents(items, plugin);
        session.hit(victim, 2.2);
        // MockBukkit's PlayerMock.damage(amount, source) may fire no event: then only the flag test above covers this path
        org.junit.Assume.assumeFalse("MockBukkit fires no damage event for damage(amount, source)", items.seenAtHigh.isEmpty());
        assertEquals(session.hitDamage(2.2), items.seenAtHigh.get(0), 1e-9);
        assertTrue(items.seenAtHigh.get(0) > session.hitDamage(1.0) * 2);
        session.end(BossSession.End.ADMIN, null);
    }

    @Test
    public void abilityFlagMakesTheListenerLeaveTheAmountAlone() {
        BossSession session = spawn("voidwyrm");
        PlayerMock victim = server.addPlayer();
        BossListener listener = new BossListener(plugin.core(), manager);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(session.entity(), victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 77.0);
        session.setAbilityHitForTest(true);
        listener.onPlayerDamage(event);
        session.setAbilityHitForTest(false);
        assertEquals(77.0, event.getDamage(), 0);
        listener.onPlayerDamage(event);
        assertEquals(Math.max(0.5, session.hitDamage(1.0)), event.getDamage(), 1e-9);
        session.end(BossSession.End.ADMIN, null);
    }

    // ---------------------------------------------------------------- chunk tickets

    @Test
    public void fightHoldsChunksAndReleasesThemAtTheEnd() {
        BossSession session = spawn("crypt_lord");
        // MockBukkit has no chunk tickets: the session must survive that, and never claim more than it released
        session.end(BossSession.End.ADMIN, null);
        assertEquals(0, session.heldChunkCount());
    }

    // ---------------------------------------------------------------- refunds

    @Test
    public void vanishedBossAfterDamageIsNotRefunded() {
        assertTrue(BossManager.refundable(BossSession.End.SHUTDOWN, true));
        assertTrue(BossManager.refundable(BossSession.End.WORLD_UNLOAD, true));
        assertTrue(BossManager.refundable(BossSession.End.ERROR, true));
        assertTrue(BossManager.refundable(BossSession.End.ENTITY_LOST, false));
        assertFalse("chunk unload after damage must not be a free retry", BossManager.refundable(BossSession.End.ENTITY_LOST, true));
        assertTrue(BossManager.refundable(BossSession.End.DESPAWN, false));
        assertFalse(BossManager.refundable(BossSession.End.DESPAWN, true));
        assertFalse(BossManager.refundable(BossSession.End.TIMEOUT, false));
        assertFalse(BossManager.refundable(BossSession.End.OWNER_DIED, false));
    }

    // ---------------------------------------------------------------- spawn sources

    @Test
    public void conversionsAreNotNaturalSpawns() {
        for (SpawnReason reason : List.of(SpawnReason.FROZEN, SpawnReason.DROWNED, SpawnReason.SHEARED,
                SpawnReason.SPAWNER, SpawnReason.CURED)) {
            assertTrue(reason.name(), SpawnSources.isArtificial(reason));
        }
        assertFalse(SpawnSources.isArtificial(SpawnReason.NATURAL));
    }

    // ---------------------------------------------------------------- loot delivery

    @Test
    public void fullInventoryQueuesInsteadOfDropping() {
        PlayerMock p = server.addPlayer();
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            p.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        int dropsBefore = world.getEntities().size();
        int overflow = manager.delivery().give(p, List.of(new ItemStack(Material.DIAMOND, 3)));
        assertEquals(1, overflow);
        assertEquals("nothing is dropped where the void or a timer could eat it", dropsBefore, world.getEntities().size());
        assertEquals(1, manager.delivery().pendingCount(p.getUniqueId()));
        // still full: nothing is delivered, nothing is lost or duplicated
        assertEquals(0, manager.delivery().claimPending(p));
        assertEquals(1, manager.delivery().pendingCount(p.getUniqueId()));
        p.getInventory().clear();
        assertEquals(1, manager.delivery().claimPending(p));
        assertEquals(0, manager.delivery().pendingCount(p.getUniqueId()));
        assertTrue(p.getInventory().contains(Material.DIAMOND, 3));
        assertEquals("a second claim has nothing left to give", 0, manager.delivery().claimPending(p));
    }

    @Test
    public void queueRunsInlineWhenThePluginIsDisabled() {
        assertTrue(LootDelivery.useAsync(true, false, true));
        assertFalse("a disabled plugin cannot schedule", LootDelivery.useAsync(true, false, false));
        assertFalse(LootDelivery.useAsync(true, true, true));
        assertFalse(LootDelivery.useAsync(false, false, true));
    }

    // ---------------------------------------------------------------- hp scaling

    @Test
    public void altAccountsDoNotGrowTheBoss() {
        BossSession session = spawn("voidwyrm");
        double before = session.maxHp();
        for (int i = 0; i < 8; i++) {
            PlayerMock alt = server.addPlayer();
            alt.teleport(session.entity().getLocation().add(3, 0, 0));
            session.applyHit(alt, 1.0);
        }
        assertEquals(before, session.maxHp(), 1e-9);
        PlayerMock real = server.addPlayer();
        PlayerMock second = server.addPlayer();
        session.applyHit(real, before * 0.02);
        session.applyHit(second, before * 0.02);
        assertTrue("two real fighters do", session.maxHp() > before);
        session.end(BossSession.End.ADMIN, null);
    }

    // ---------------------------------------------------------------- arena rules

    @Test
    public void summonedFightOnlyRestrainsItsFighters() {
        PlayerMock summoner = server.addPlayer();
        PlayerMock neighbour = server.addPlayer();
        PlayerMock fighter = server.addPlayer();
        BossDefinition def = manager.config().definition("magma_warden").orElseThrow();
        BossSession session = manager.spawn(def, world.getSpawnLocation(), summoner, BossSession.SpawnOptions.regular())
            .orElseThrow();
        assertTrue(session.restrains(summoner));
        assertFalse("a neighbour is never locked out", session.restrains(neighbour));
        session.ledger().record(fighter.getUniqueId(), 5, 100);
        assertTrue(session.restrains(fighter));
        session.end(BossSession.End.ADMIN, null);
        BossSession event = spawn("crypt_lord");
        assertTrue("a public event restrains everybody", event.restrains(neighbour));
        event.end(BossSession.End.ADMIN, null);
    }

    // ---------------------------------------------------------------- item guard

    @Test
    public void bossItemsAreInertInVanillaWays() {
        PlayerMock p = server.addPlayer();
        ItemStack web = plugin.core().items().create("tarantula_web");
        assertNotNull(web);
        BossItemGuard guard = new BossItemGuard(plugin.core());
        assertTrue(guard.isBossItem(web));
        assertFalse(guard.isBossItem(new ItemStack(Material.COBWEB)));
        PlayerItemConsumeEvent eat = new PlayerItemConsumeEvent(p, plugin.core().items().create("revenant_flesh"), EquipmentSlot.HAND);
        guard.onConsume(eat);
        assertTrue(eat.isCancelled());
        PlayerItemConsumeEvent normal = new PlayerItemConsumeEvent(p, new ItemStack(Material.ROTTEN_FLESH), EquipmentSlot.HAND);
        guard.onConsume(normal);
        assertFalse("ordinary rotten flesh is untouched", normal.isCancelled());
        org.bukkit.event.block.BlockPlaceEvent place = new org.bukkit.event.block.BlockPlaceEvent(
            world.getBlockAt(5, 5, 5), world.getBlockAt(5, 5, 5).getState(), world.getBlockAt(5, 4, 5), web, p, true, EquipmentSlot.HAND);
        guard.onPlace(place);
        assertTrue(place.isCancelled());
    }

    // ---------------------------------------------------------------- offline rewards

    @Test
    public void offlineParticipantStillGetsWingsXpAndPityAtLogin() {
        List<String> grants = new ArrayList<>();
        plugin.core().replace(WingsApi.class, new WingsApi() {
            @Override public ItemStack create() { return null; }
            @Override public boolean isWings(ItemStack stack) { return false; }
            @Override public boolean isWearing(Player player) { return false; }
            @Override public boolean grantOnce(Player player, String source) {
                return !grants.contains(source) && grants.add(source);
            }
            @Override public boolean hasReceived(UUID player) { return false; }
        });
        BossSession session = spawn("voidwyrm");
        PlayerMock killer = server.addPlayer();
        killer.teleport(session.entity().getLocation().add(3, 0, 0));
        UUID ghost = UUID.randomUUID(); // an offline participant with 30 percent of the damage
        session.ledger().record(ghost, session.maxHp() * 0.3, session.maxHp());
        session.applyHit(killer, 1e15);
        assertTrue(session.isEnded());
        server.getScheduler().waitAsyncTasksFinished(); // an unloaded profile is edited off the main thread
        BossData parked = plugin.core().profiles().section(ghost, "boss", BossData.class, BossData::new);
        assertEquals(List.of("boss:voidwyrm"), parked.pendingWings);
        assertEquals("one kill is queued", 1, parked.pendingKills.size());
        assertFalse("the loot is queued as well", parked.pending.isEmpty() && parked.pendingDry.isEmpty());
        // login: applied exactly once
        PlayerMock back = new PlayerMock(server, "ghost", ghost);
        server.addPlayer(back);
        assertTrue(manager.rewards().applyPending(back) >= 1);
        assertEquals(List.of("boss:voidwyrm"), grants);
        assertEquals(1, (int) parked.kills.get("voidwyrm"));
        assertTrue(parked.pendingWings.isEmpty() && parked.pendingKills.isEmpty());
        assertEquals("a second login applies nothing", 0, manager.rewards().applyPending(back));
        assertEquals(1, grants.size());
    }

    @Test
    public void repeatedKillsPayLess() {
        assertEquals(1.0, LootMath.repeatMultiplier(0, 0.2, 0.25), 1e-9);
        assertEquals(0.8, LootMath.repeatMultiplier(1, 0.2, 0.25), 1e-9);
        assertEquals(0.25, LootMath.repeatMultiplier(10, 0.2, 0.25), 1e-9);
        assertEquals("a zero decay turns the damper off", 1.0, LootMath.repeatMultiplier(50, 0, 0.25), 1e-9);
    }

    // ---------------------------------------------------------------- altar

    @Test
    public void altarIsFoundInTheOwnIslandCell() {
        BossDefinition.Altar altar = new BossDefinition.Altar("sb_end", 0.5, 65, 0.5, 40, 400);
        assertArrayEquals(new double[] {800.5, -399.5}, altar.centreNear(805, -390), 1e-9);
        assertArrayEquals(new double[] {0.5, 0.5}, altar.centreNear(3, -2), 1e-9);
        BossDefinition.Altar fixed = new BossDefinition.Altar("sb_end", 10, 65, 10, 40);
        assertArrayEquals(new double[] {10, 10}, fixed.centreNear(5000, 5000), 1e-9);
    }

    @Test
    public void voidSigilWorksAtTheAltarOfAnyIslandCell() {
        WorldMock end = server.addSimpleWorld("sb_end");
        PlayerMock p = server.addPlayer();
        Location at = new Location(end, 805, 65, -390);
        end.getChunkAt(at).load();
        end.getChunkAt(new Location(end, 800.5, 65, -399.5)).load();
        p.teleport(at);
        BossDefinition def = manager.config().definition("voidwyrm").orElseThrow();
        SummonListener listener = new SummonListener(plugin.core(), manager);
        String denial = listener.summon(p, def, EquipmentSlot.HAND);
        assertNull("own cell altar accepted: " + denial, denial);
        BossSession session = manager.active().iterator().next();
        assertEquals(800.5, session.anchor().getX(), 1e-9);
        assertEquals(-399.5, session.anchor().getZ(), 1e-9);
        PlayerMock second = server.addPlayer();
        second.teleport(at);
        assertNotNull("a second player cannot stack another one on the same altar",
            listener.summon(second, def, EquipmentSlot.HAND));
        session.end(BossSession.End.ADMIN, null);
        p.teleport(new Location(end, 1000, 65, 1000));
        assertNotNull("far from every altar", listener.summon(p, def, EquipmentSlot.HAND));
    }
}
