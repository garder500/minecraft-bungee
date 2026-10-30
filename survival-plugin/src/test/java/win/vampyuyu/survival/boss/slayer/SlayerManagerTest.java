package win.vampyuyu.survival.boss.slayer;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.boss.BossConfig;
import win.vampyuyu.survival.boss.BossData;
import win.vampyuyu.survival.boss.BossManager;

/** The paid path of a quest: refusals take nothing, a start takes the cost exactly once. */
public class SlayerManagerTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private SlayerManager slayer;
    private Wallet wallet;

    /** Minimal economy with real balance checks. */
    static final class Wallet implements EconomyApi {
        final Map<UUID, Double> balance = new HashMap<>();
        int takes;

        @Override public double coins(UUID player) { return balance.getOrDefault(player, 0.0); }
        @Override public boolean has(UUID player, double amount) { return coins(player) >= amount; }
        @Override public boolean take(UUID player, double amount, String reason) {
            if (!has(player, amount)) {
                return false;
            }
            takes++;
            balance.merge(player, -amount, Double::sum);
            return true;
        }
        @Override public void add(UUID player, double amount, String reason) { balance.merge(player, amount, Double::sum); }
        @Override public void addTransactionListener(Consumer<Transaction> listener) {}
    }

    @Before
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        wallet = new Wallet();
        plugin.core().replace(EconomyApi.class, wallet);
        BossConfig config;
        try (var reader = new java.io.InputStreamReader(getClass().getResourceAsStream("/boss.yml"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            config = BossConfig.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader));
        }
        slayer = new SlayerManager(plugin.core(), new BossManager(plugin.core(), config));
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private BossData data(PlayerMock p) {
        return plugin.core().profiles().section(p.getUniqueId(), "boss", BossData.class, BossData::new);
    }

    @Test
    public void tooPoorIsRefusedAndNothingIsTaken() {
        PlayerMock p = server.addPlayer();
        wallet.balance.put(p.getUniqueId(), 100.0);
        String refusal = slayer.start(p, slayer.slayer("zombie").orElseThrow(), 1);
        assertNotNull(refusal);
        assertEquals(100.0, wallet.coins(p.getUniqueId()), 0);
        assertEquals(0, wallet.takes);
        assertNull(data(p).quest);
    }

    @Test
    public void validStartTakesTheCostOnceAndRefusesASecondQuest() {
        PlayerMock p = server.addPlayer();
        SlayerDefinition zombie = slayer.slayer("zombie").orElseThrow();
        double cost = zombie.tier(1).cost();
        wallet.balance.put(p.getUniqueId(), cost * 3);
        assertNull(slayer.start(p, zombie, 1));
        assertEquals(cost * 2, wallet.coins(p.getUniqueId()), 1e-9);
        assertEquals(BossData.Quest.COLLECTING, data(p).quest.state);
        assertNotNull("a second quest is refused", slayer.start(p, zombie, 1));
        assertEquals("and costs nothing", cost * 2, wallet.coins(p.getUniqueId()), 1e-9);
        assertEquals(1, wallet.takes);
    }

    @Test
    public void lockedTierIsRefusedWithoutPaying() {
        PlayerMock p = server.addPlayer();
        wallet.balance.put(p.getUniqueId(), 1e9);
        assertNotNull(slayer.start(p, slayer.slayer("zombie").orElseThrow(), 5));
        assertEquals(1e9, wallet.coins(p.getUniqueId()), 0);
        assertEquals(0, wallet.takes);
    }

    @Test
    public void cancelClearsTheQuestWithoutRefund() {
        PlayerMock p = server.addPlayer();
        SlayerDefinition zombie = slayer.slayer("zombie").orElseThrow();
        wallet.balance.put(p.getUniqueId(), zombie.tier(1).cost());
        assertNull(slayer.start(p, zombie, 1));
        assertTrue(slayer.cancel(p));
        assertNull(data(p).quest);
        assertEquals(0, wallet.coins(p.getUniqueId()), 0);
        assertFalse(slayer.cancel(p));
    }

    @Test
    public void spawnBeforeReadyIsRefused() {
        PlayerMock p = server.addPlayer();
        SlayerDefinition zombie = slayer.slayer("zombie").orElseThrow();
        assertNotNull("no quest", slayer.spawnBoss(p));
        wallet.balance.put(p.getUniqueId(), zombie.tier(1).cost());
        slayer.start(p, zombie, 1);
        assertTrue(slayer.spawnBoss(p).contains("hunting"));
        assertEquals(BossData.Quest.COLLECTING, data(p).quest.state);
    }

    @Test
    public void withoutAnEconomyNothingStarts() throws Exception {
        MockBukkit.unmock();
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        // the real economy feature is loaded now: take its service away to model a network without an economy
        java.lang.reflect.Field services = win.vampyuyu.survival.core.Core.class.getDeclaredField("services");
        services.setAccessible(true);
        ((java.util.Map<?, ?>) services.get(plugin.core())).remove(win.vampyuyu.survival.api.EconomyApi.class);
        assertTrue(plugin.core().service(win.vampyuyu.survival.api.EconomyApi.class).isEmpty());
        PlayerMock p = server.addPlayer();
        BossConfig config = BossConfig.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
            new java.io.InputStreamReader(getClass().getResourceAsStream("/boss.yml"),
                java.nio.charset.StandardCharsets.UTF_8)));
        SlayerManager noEconomy = new SlayerManager(plugin.core(), new BossManager(plugin.core(), config));
        assertTrue(noEconomy.start(p, noEconomy.slayer("zombie").orElseThrow(), 1).contains("economy"));
    }
}
