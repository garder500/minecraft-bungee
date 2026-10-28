package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;

public class BankServiceTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private Bank bank;
    private PlayerMock alice;
    private UUID id;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        bank = new Bank(plugin.core(), eco, plugin.core().featureConfig("economy"));
        alice = server.addPlayer("Alice");
        id = alice.getUniqueId();
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void shippedTiersAreSortedAndPositive() {
        long cap = 0;
        for (Bank.Tier t : bank.tiers()) {
            assertTrue(t.capacityCents() > cap);
            cap = t.capacityCents();
            assertTrue(t.rateBp() > 0);
        }
        assertEquals("Starter", bank.tiers().get(0).name());
        assertEquals(0, bank.tiers().get(0).costCents());
    }

    @Test
    public void depositAndWithdrawMoveCoinsWithoutCreatingAny() {
        eco.addCents(id, 100_000, "seed");
        assertTrue(bank.deposit(id, 60_000).ok());
        assertEquals(40_000, eco.balanceCents(id));
        assertEquals(60_000, eco.bankCents(id));
        assertFalse(bank.deposit(id, 50_000).ok());
        assertFalse(bank.deposit(id, -5).ok());
        assertTrue(bank.withdraw(id, 1_000_000).ok());
        assertEquals(100_000, eco.balanceCents(id));
        assertEquals(0, eco.bankCents(id));
        assertFalse(bank.withdraw(id, 5).ok());
        List<String> problems = new ArrayList<>();
        EconomyAudit.check(id, eco.data(id), problems);
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void depositIsClippedToTheTierCapacity() {
        long capacity = bank.tiers().get(0).capacityCents();
        eco.addCents(id, capacity + 5_000, "seed");
        Bank.Move move = bank.deposit(id, capacity + 5_000);
        assertTrue(move.ok());
        assertEquals(capacity, move.moved());
        assertEquals(5_000, eco.balanceCents(id));
        assertFalse(bank.deposit(id, 100).ok());
    }

    @Test
    public void upgradeCostsCoinsAndRaisesTheCap() {
        Bank.Tier gold = bank.tiers().get(1);
        assertFalse(bank.upgrade(id).ok());
        eco.addCents(id, gold.costCents() + 7, "seed");
        assertTrue(bank.upgrade(id).ok());
        assertEquals(7, eco.balanceCents(id));
        assertEquals(1, eco.data(id).bankTier);
        assertEquals("Gold", bank.tierOf(eco.data(id)).name());
    }

    @Test
    public void interestIsSettledBeforeADepositSoLateMoneyEarnsNothingRetroactively() {
        long periodMs = bank.periodMs();
        long now = System.currentTimeMillis();
        eco.update(id, (d, txs) -> {
            d.bankCents = 1_000_000;
            d.bankInterestAt = now - 3 * periodMs - 10;
            return null;
        });
        eco.addCents(id, 50_000_000, "seed");
        Bank.Move move = bank.deposit(id, 50_000_000);
        assertTrue(move.ok());
        // Three periods were paid on the OLD balance only (compounding 2% of 10,000.00 coins).
        long expected = 20_000 + 20_400 + 20_808;
        assertEquals(expected, move.interest());
        assertEquals(1_000_000 + expected + 50_000_000, eco.bankCents(id));
        assertTrue(eco.data(id).bankInterestAt > now - periodMs);
        // Nothing more is due immediately.
        assertEquals(0, bank.settle(id));
    }

    @Test
    public void depositIntoAnEmptyBankStartsTheClock() {
        eco.addCents(id, 10_000, "seed");
        assertEquals(0, eco.data(id).bankInterestAt);
        bank.deposit(id, 10_000);
        assertTrue(eco.data(id).bankInterestAt > 0);
        bank.withdraw(id, 10_000);
        assertEquals(0, eco.data(id).bankInterestAt);
    }

    @Test
    public void bankCommandOpensTheMenuAndMovesCoins() {
        eco.addCents(id, 10_000, "seed");
        alice.performCommand("bank deposit half");
        assertEquals(5_000, eco.bankCents(id));
        alice.performCommand("bank withdraw all");
        assertEquals(10_000, eco.balanceCents(id));
        alice.performCommand("bank");
        assertNotNull(alice.getOpenInventory().getTopInventory());
        assertEquals(36, alice.getOpenInventory().getTopInventory().getSize());
    }
}
