package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.event.CoinsChangeEvent;

/** Boots the whole plugin: the economy feature, its API and its commands. */
public class EconomyServiceTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyApi api;
    private PlayerMock alice;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        api = plugin.core().service(EconomyApi.class).orElseThrow();
        alice = server.addPlayer("Alice");
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private EconomyService service() {
        return (EconomyService) api;
    }

    @Test
    public void addTakeAndHas() {
        assertEquals(0, api.coins(alice.getUniqueId()), 0);
        api.add(alice.getUniqueId(), 100.5, "test");
        assertEquals(100.5, api.coins(alice.getUniqueId()), 0.0001);
        assertTrue(api.has(alice.getUniqueId(), 100.5));
        assertFalse(api.has(alice.getUniqueId(), 100.51));
        assertTrue(api.take(alice.getUniqueId(), 50.25, "test"));
        assertEquals(50.25, api.coins(alice.getUniqueId()), 0.0001);
    }

    @Test
    public void neverNegativeAndBadAmountsAreRefused() {
        UUID id = alice.getUniqueId();
        api.add(id, 10, "t");
        assertFalse(api.take(id, 10.01, "t"));
        assertFalse(api.take(id, 0, "t"));
        assertFalse(api.take(id, -5, "t"));
        assertFalse(api.take(id, Double.NaN, "t"));
        assertFalse(api.take(id, Double.POSITIVE_INFINITY, "t"));
        api.add(id, -5, "t");
        api.add(id, Double.NaN, "t");
        api.add(id, Double.POSITIVE_INFINITY, "t");
        api.add(id, 0, "t");
        assertEquals(10, api.coins(id), 0);
        assertFalse(api.has(id, Double.NaN));
    }

    @Test
    public void balanceIsCappedNotOverflowed() {
        UUID id = alice.getUniqueId();
        long cap = service().settings().maxBalanceCents();
        service().addCents(id, cap - 100, "t");
        assertEquals(100, service().addCents(id, 5000, "t"));
        assertEquals(cap, service().balanceCents(id));
        assertEquals(0, service().addCents(id, Long.MAX_VALUE, "t"));
        assertEquals(cap, service().balanceCents(id));
    }

    @Test
    public void everyChangeFiresAnEventAndListenerWithReason() {
        List<CoinsChangeEvent> events = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(CoinsChangeEvent e) { events.add(e); }
        }, plugin);
        AtomicInteger listened = new AtomicInteger();
        api.addTransactionListener(tx -> {
            assertEquals("shop:buy", tx.reason());
            listened.incrementAndGet();
        });
        api.add(alice.getUniqueId(), 20, "shop:buy");
        assertEquals(1, events.size());
        assertEquals(20, events.get(0).getDelta(), 0);
        assertEquals(20, events.get(0).getNewBalance(), 0);
        assertEquals("shop:buy", events.get(0).getReason());
        assertEquals(1, listened.get());
    }

    @Test
    public void aFailingListenerDoesNotBreakTheTransaction() {
        api.addTransactionListener(tx -> { throw new IllegalStateException("boom"); });
        api.add(alice.getUniqueId(), 5, "t");
        assertEquals(5, api.coins(alice.getUniqueId()), 0);
    }

    @Test
    public void ledgerIsARingBufferWithAnUnbrokenChain() {
        UUID id = alice.getUniqueId();
        int size = service().settings().ledgerSize();
        for (int i = 0; i < size + 20; i++) {
            if (i % 3 == 2) {
                service().takeCents(id, 50, "spend");
            } else {
                service().addCents(id, 100 + i, "earn");
            }
        }
        EconomyData d = service().data(id);
        assertEquals(size, d.ledger.size());
        List<String> problems = new ArrayList<>();
        EconomyAudit.check(id, d, problems);
        assertTrue(problems.toString(), problems.isEmpty());
        assertEquals(d.cents, d.ledger.get(d.ledger.size() - 1).after);
    }

    @Test
    public void auditDetectsATamperedBalance() {
        UUID id = alice.getUniqueId();
        service().addCents(id, 1000, "t");
        service().data(id).cents += 77;
        List<String> problems = new ArrayList<>();
        EconomyAudit.check(id, service().data(id), problems);
        assertFalse(problems.isEmpty());
    }

    @Test
    public void payMovesCoinsAndEnforcesLimits() {
        PlayerMock bob = server.addPlayer("Bob");
        service().addCents(alice.getUniqueId(), 100_000, "seed");
        long now = System.currentTimeMillis();
        assertEquals(EconomyService.PayDebit.OK, service().debitForPay(alice.getUniqueId(), 100, now, "pay"));
        assertEquals(EconomyService.PayDebit.COOLDOWN, service().debitForPay(alice.getUniqueId(), 100, now + 10, "pay"));
        assertEquals(EconomyService.PayDebit.FUNDS,
            service().debitForPay(alice.getUniqueId(), 900_000_000, now + 60_000, "pay"));
        assertEquals(EconomyService.PayDebit.OK, service().debitForPay(alice.getUniqueId(), 100, now + 60_000, "pay"));
        assertEquals(99_800, service().balanceCents(alice.getUniqueId()));
        assertNotNull(bob);
    }

    @Test
    public void dailyPayLimitResetsNextDay() {
        UUID id = alice.getUniqueId();
        service().addCents(id, service().settings().maxBalanceCents() / 2, "seed");
        long limit = service().settings().payDailyCents();
        long now = 10L * 86_400_000L + 1000;
        assertEquals(EconomyService.PayDebit.OK, service().debitForPay(id, limit, now, "pay"));
        assertEquals(EconomyService.PayDebit.DAILY_LIMIT, service().debitForPay(id, 100, now + 60_000, "pay"));
        assertEquals(EconomyService.PayDebit.OK, service().debitForPay(id, 100, now + 86_400_000L, "pay"));
    }

    @Test
    public void balanceCommandsAndPayCommandWork() {
        PlayerMock bob = server.addPlayer("Bob");
        service().addCents(alice.getUniqueId(), 5_000, "seed");
        alice.performCommand("balance");
        assertNotNull(alice.nextComponentMessage());
        alice.performCommand("pay Bob 10");
        // The sender is debited immediately; Bob online is credited immediately.
        assertEquals(4_000, service().balanceCents(alice.getUniqueId()));
        assertEquals(1_000, service().balanceCents(bob.getUniqueId()));
        alice.performCommand("pay Alice 10");
        assertEquals(4_000, service().balanceCents(alice.getUniqueId()));
        alice.performCommand("pay Bob -5");
        alice.performCommand("pay Bob NaN");
        assertEquals(1_000, service().balanceCents(bob.getUniqueId()));
    }

    @Test
    public void payOverTheThresholdNeedsConfirmation() {
        PlayerMock bob = server.addPlayer("Bob");
        service().addCents(alice.getUniqueId(), 10_000_000, "seed");
        alice.performCommand("pay Bob 60000");
        assertEquals(10_000_000, service().balanceCents(alice.getUniqueId()));
        alice.performCommand("pay confirm");
        assertEquals(10_000_000 - 6_000_000, service().balanceCents(alice.getUniqueId()));
        assertEquals(6_000_000, service().balanceCents(bob.getUniqueId()));
        alice.performCommand("pay confirm");
        assertEquals(6_000_000, service().balanceCents(bob.getUniqueId()));
    }

    @Test
    public void adminCommandsRequireThePermission() {
        alice.performCommand("ecoadmin give Alice 100");
        assertEquals(0, service().balanceCents(alice.getUniqueId()));
        alice.setOp(true);
        alice.performCommand("ecoadmin give Alice 100");
        assertEquals(10_000, service().balanceCents(alice.getUniqueId()));
        alice.performCommand("ecoadmin take Alice 30");
        assertEquals(7_000, service().balanceCents(alice.getUniqueId()));
        alice.performCommand("ecoadmin set Alice 5");
        assertEquals(500, service().balanceCents(alice.getUniqueId()));
        alice.performCommand("ecoadmin reset Alice");
        assertEquals(0, service().balanceCents(alice.getUniqueId()));
    }

    @Test
    public void purseSurvivesAProfileRoundTrip() {
        UUID id = alice.getUniqueId();
        service().addCents(id, 12_345, "t");
        plugin.core().profiles().saveDirty();
        plugin.core().profiles().awaitWrites();
        String text;
        try {
            text = plugin.core().store().readText("players/" + id + ".json");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(text.contains("12345"));
    }
}
