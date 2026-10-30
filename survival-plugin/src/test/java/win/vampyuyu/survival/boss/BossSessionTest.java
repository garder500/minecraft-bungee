package win.vampyuyu.survival.boss;

import static org.junit.Assert.*;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import win.vampyuyu.survival.SurvivalPlugin;

/** Drives a real session through the plugin: spawn, damage ledger, arena rules, end. */
public class BossSessionTest {
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
        manager = runningFeature().manager();
        assertNotNull("the boss feature must be running", manager);
    }

    /** The running BossFeature, so the test shares its listeners with the manager. */
    private BossFeature runningFeature() {
        try {
            java.lang.reflect.Field field = win.vampyuyu.survival.core.FeatureManager.class.getDeclaredField("running");
            field.setAccessible(true);
            for (Object feature : (java.util.List<?>) field.get(plugin.features())) {
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

    private Optional<BossSession> spawnCrypt() {
        Location at = world.getSpawnLocation();
        BossDefinition def = manager.config().definition("crypt_lord").orElseThrow();
        return manager.spawn(def, at, null, BossSession.SpawnOptions.regular());
    }

    @Test
    public void spawnTagsTheEntityAndTracksTheSession() {
        BossSession session = spawnCrypt().orElseThrow();
        assertTrue(Tags.isOurs(session.entity()));
        assertEquals(1, manager.activeCount("crypt_lord"));
        assertSame(session, manager.sessionOfBoss(session.entity()));
        session.end(BossSession.End.ADMIN, null);
        assertEquals(0, manager.activeCount("crypt_lord"));
        assertFalse(session.entity().isValid());
    }

    @Test
    public void playerDamageGoesToTheLedgerAndLowersVirtualHealth() {
        BossSession session = spawnCrypt().orElseThrow();
        PlayerMock player = server.addPlayer();
        player.teleport(session.entity().getLocation().add(2, 0, 0));
        LivingEntity boss = session.entity();
        double before = session.hp();
        EntityDamageEvent event = new org.bukkit.event.entity.EntityDamageByEntityEvent(player, boss,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1000.0);
        Bukkit.getPluginManager().callEvent(event);
        assertEquals(before - 1000.0, session.hp(), 1.0);
        assertEquals(1000.0, session.ledger().damageOf(player.getUniqueId()), 1.0);
        session.end(BossSession.End.ADMIN, null);
    }

    @Test
    public void personalBossRefusesOtherPlayers() {
        PlayerMock owner = server.addPlayer();
        PlayerMock other = server.addPlayer();
        Location at = world.getSpawnLocation();
        BossDefinition def = manager.config().definition("crypt_lord").orElseThrow();
        BossSession session = manager.spawn(def, at, owner, new BossSession.SpawnOptions(true, false, 0, 0, -1, null, null)).orElseThrow();
        other.teleport(session.entity().getLocation().add(1, 0, 0));
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(other,
            session.entity(), EntityDamageEvent.DamageCause.ENTITY_ATTACK, 500.0));
        assertEquals(0.0, session.ledger().damageOf(other.getUniqueId()), 0.0);
        session.end(BossSession.End.ADMIN, null);
    }

    @Test
    public void shutdownRemovesEveryBossEntity() {
        BossSession session = spawnCrypt().orElseThrow();
        LivingEntity entity = session.entity();
        manager.shutdown();
        assertFalse(entity.isValid());
        assertEquals(0, manager.active().size());
    }

    // ---------------------------------------------------------------- kill and rewards

    /** Collects what the framework pays. */
    private static final class Spy implements win.vampyuyu.survival.api.WingsApi, win.vampyuyu.survival.api.EconomyApi {
        final java.util.List<String> grants = new java.util.ArrayList<>();
        final java.util.Map<java.util.UUID, Double> paid = new java.util.HashMap<>();

        @Override public org.bukkit.inventory.ItemStack create() { return null; }
        @Override public boolean isWings(org.bukkit.inventory.ItemStack stack) { return false; }
        @Override public boolean isWearing(org.bukkit.entity.Player player) { return false; }
        @Override public boolean grantOnce(org.bukkit.entity.Player player, String source) { grants.add(source); return true; }
        @Override public boolean hasReceived(java.util.UUID player) { return false; }
        @Override public double coins(java.util.UUID player) { return paid.getOrDefault(player, 0.0); }
        @Override public boolean has(java.util.UUID player, double amount) { return coins(player) >= amount; }
        @Override public boolean take(java.util.UUID player, double amount, String reason) { return false; }
        @Override public void add(java.util.UUID player, double amount, String reason) { paid.merge(player, amount, Double::sum); }
        @Override public void addTransactionListener(java.util.function.Consumer<Transaction> listener) {}
    }

    @Test
    public void killFiresTheEventPaysCoinsAndGrantsWings() {
        Spy spy = new Spy();
        plugin.core().register(win.vampyuyu.survival.api.WingsApi.class, spy);
        plugin.core().register(win.vampyuyu.survival.api.EconomyApi.class, spy);
        java.util.List<win.vampyuyu.survival.api.event.BossKillEvent> events = new java.util.ArrayList<>();
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(win.vampyuyu.survival.api.event.BossKillEvent e) {
                events.add(e);
            }
        }, plugin);
        manager.addHook((event, recipient) -> java.util.List.of(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STICK, 2)));

        BossDefinition def = manager.config().definition("voidwyrm").orElseThrow();
        BossSession session = manager.spawn(def, world.getSpawnLocation(), null, BossSession.SpawnOptions.regular())
            .orElseThrow();
        PlayerMock big = server.addPlayer();
        PlayerMock small = server.addPlayer();
        PlayerMock tiny = server.addPlayer();
        big.teleport(session.entity().getLocation().add(3, 0, 0));
        small.teleport(session.entity().getLocation().add(3, 0, 1));
        tiny.teleport(session.entity().getLocation().add(3, 0, 2));
        double max = session.maxHp();
        // a newcomer rescales the pool; stay above the 70 percent shield phase, then finish with the big hitter
        session.applyHit(tiny, 1.0);
        session.applyHit(small, session.maxHp() * 0.20);
        session.applyHit(big, 1e15);
        assertTrue(session.isEnded());
        assertEquals(1, events.size());
        assertEquals("voidwyrm", events.get(0).getBossId());
        assertEquals(big.getUniqueId(), events.get(0).getTopDamagers().get(0).player());
        assertEquals("shares of the ledger add up", 1.0,
            session.ledger().participants().stream().mapToDouble(session.ledger()::share).sum(), 1e-9);
        // tiny is below the 5 percent wings share, big and small are above it
        assertEquals(2, spy.grants.size());
        assertEquals("boss:voidwyrm", spy.grants.get(0));
        // coins: everyone with at least the min-share is paid, the biggest damager the most
        assertTrue(spy.paid.getOrDefault(big.getUniqueId(), 0.0) > spy.paid.getOrDefault(small.getUniqueId(), 0.0));
        assertTrue(max > 0);
        double total = spy.paid.values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue("never pays out more than the pool: " + total, total <= def.loot().coins() * 1.0001);
        assertTrue("loot hook items reach the inventory", big.getInventory().contains(org.bukkit.Material.STICK));
        assertEquals(0, manager.activeCount("voidwyrm"));
    }

    @Test
    public void endingTwiceNeverPaysTwice() {
        Spy spy = new Spy();
        plugin.core().register(win.vampyuyu.survival.api.EconomyApi.class, spy);
        BossSession session = spawnCrypt().orElseThrow();
        PlayerMock p = server.addPlayer();
        p.teleport(session.entity().getLocation().add(2, 0, 0));
        session.applyHit(p, 1e15);
        double once = spy.coins(p.getUniqueId());
        assertTrue(once > 0);
        session.end(BossSession.End.KILLED, p);
        session.applyHit(p, 1e15);
        assertEquals(once, spy.coins(p.getUniqueId()), 0.0);
    }
}
