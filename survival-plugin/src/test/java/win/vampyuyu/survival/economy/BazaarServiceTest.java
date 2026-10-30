package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;

public class BazaarServiceTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private ItemKeys keys;
    private BazaarService bz;
    private PlayerMock alice;
    private PlayerMock bob;

    /** An environment where writes happen later, to test the window between "claim removed" and "file durable". */
    static final class Manual implements DurableFile.Env {
        final List<Runnable> deferred = new ArrayList<>();

        @Override
        public void defer(long ticks, Runnable task) { deferred.add(task); }

        @Override
        public void io(Runnable task) { task.run(); }

        @Override
        public void main(Runnable task) { task.run(); }

        void runAll() {
            List<Runnable> now = new ArrayList<>(deferred);
            deferred.clear();
            now.forEach(Runnable::run);
        }
    }

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        keys = new ItemKeys(plugin.core());
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        bz = service(new DurableFile.Inline());
    }

    private BazaarService service(DurableFile.Env env) {
        BazaarService s = new BazaarService(plugin.core(), eco, keys, env);
        s.enable(plugin.core().featureConfig("economy"));
        return s;
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    /** Runs an instant buy; the result is null while the market file is still being written. */
    static BazaarService.Outcome buy(BazaarService s, PlayerMock p, String product, int n) {
        BazaarService.Outcome[] out = new BazaarService.Outcome[1];
        s.instantBuy(p, product, n, o -> out[0] = o);
        return out[0];
    }

    static BazaarService.Outcome sell(BazaarService s, PlayerMock p, String product, int n) {
        BazaarService.Outcome[] out = new BazaarService.Outcome[1];
        s.instantSell(p, product, n, o -> out[0] = o);
        return out[0];
    }

    private void fund(PlayerMock p, long coins) {
        eco.addCents(p.getUniqueId(), coins * 100, "test");
    }

    private int carried(PlayerMock p, String key) {
        return Inv.count(p, keys, key);
    }

    @Test
    public void shippedCategoriesAllHaveProducts() {
        for (BazaarService.Category c : BazaarService.Category.values()) {
            assertFalse(c + " has no known product", bz.products(c).isEmpty());
        }
        assertTrue(bz.isProduct("WHEAT"));
        assertFalse(bz.isProduct("NOT_A_THING"));
        assertEquals("WHEAT", bz.find("wheat").orElseThrow());
    }

    @Test
    public void instantBuyAgainstASellOrderMovesItemsAndCoinsAndBurnsTax() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        var sellOrder = bz.placeSell(bob, "WHEAT", 20, 10);
        assertTrue(sellOrder.message(), sellOrder.ok());
        assertEquals(54, carried(bob, "WHEAT"));
        fund(alice, 100);
        var r = buy(bz, alice, "WHEAT", 4);
        assertTrue(r.message(), r.ok());
        assertEquals(4, carried(alice, "WHEAT"));
        assertEquals(10_000 - 800, eco.balanceCents(alice.getUniqueId()));

        var claimed = new BazaarService.Outcome[1];
        bz.claim(bob, o -> claimed[0] = o);
        assertTrue(claimed[0].message(), claimed[0].ok());
        long tax = Money.basisPoints(800, 125);
        assertEquals(800 - tax, eco.balanceCents(bob.getUniqueId()));
        assertEquals(tax, bz.market().state().taxBurnedCents);
        assertTrue(bz.market().verify().isEmpty());
    }

    @Test
    public void aSecondClaimFindsNothingAndNeverPaysTwice() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        bz.placeSell(bob, "WHEAT", 20, 10);
        fund(alice, 100);
        buy(bz, alice, "WHEAT", 10);
        bz.claim(bob, o -> { });
        long after = eco.balanceCents(bob.getUniqueId());
        var again = new BazaarService.Outcome[1];
        bz.claim(bob, o -> again[0] = o);
        assertFalse(again[0].ok());
        assertEquals(after, eco.balanceCents(bob.getUniqueId()));
    }

    @Test
    public void youCannotTradeWithYourself() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 20));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 10).ok());
        fund(bob, 1000);
        assertFalse("instant buy of own offer", buy(bz, bob, "WHEAT", 1).ok());
        assertFalse("crossing buy order", bz.placeBuy(bob, "WHEAT", 20, 1).ok());
        assertEquals(100_000, eco.balanceCents(bob.getUniqueId()));
    }

    @Test
    public void cannotSellWhatYouDoNotCarryOrModifiedItems() {
        assertFalse(sell(bz, alice, "WHEAT", 1).ok());
        assertFalse(bz.placeSell(alice, "WHEAT", 20, 5).ok());
        ItemStack named = new ItemStack(Material.WHEAT, 5);
        var meta = named.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Special"));
        named.setItemMeta(meta);
        alice.getInventory().addItem(named);
        assertFalse("renamed wheat is not clean", bz.placeSell(alice, "WHEAT", 20, 5).ok());
        assertEquals(5, alice.getInventory().getItem(0).getAmount());
    }

    @Test
    public void buyOrderEscrowsCoinsAndCancelReturnsThemThroughAClaim() {
        fund(alice, 100);
        var placed = bz.placeBuy(alice, "WHEAT", 10, 50); // 1.0 each x 50 = 50 coins
        assertTrue(placed.message(), placed.ok());
        assertEquals(5_000, eco.balanceCents(alice.getUniqueId()));
        long id = bz.market().ordersOf(alice.getUniqueId().toString()).get(0).id;
        assertTrue(bz.cancel(alice, id).ok());
        assertEquals("refund is a claim, not instant", 5_000, eco.balanceCents(alice.getUniqueId()));
        bz.claim(alice, o -> { });
        assertEquals(10_000, eco.balanceCents(alice.getUniqueId()));
    }

    @Test
    public void ordersNeedFundsAndRespectTheLimit() {
        assertFalse(bz.placeBuy(alice, "WHEAT", 10, 50).ok());
        fund(alice, 1_000_000);
        int placed = 0;
        for (int i = 0; i < 30; i++) {
            if (bz.placeBuy(alice, "WHEAT", 1 + i, 1).ok()) {
                placed++;
            }
        }
        assertEquals(bz.market().rules().maxOrders(), placed);
    }

    @Test
    public void aClaimThatCloseInterruptsIsNeitherLostNorDelivered() {
        Manual env = new Manual();
        BazaarService slow = service(env);
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        slow.placeSell(bob, "WHEAT", 20, 10);
        fund(alice, 100);
        buy(slow, alice, "WHEAT", 10);
        env.runAll();
        long before = eco.balanceCents(bob.getUniqueId());
        var result = new BazaarService.Outcome[1];
        slow.claim(bob, o -> result[0] = o);
        assertNull("waiting for the write", result[0]);
        slow.close(null);
        env.runAll();
        assertNull("nothing delivered after close", result[0]);
        assertEquals(before, eco.balanceCents(bob.getUniqueId()));
        // the final file still holds the claim, so the next start pays it once
        BazaarMarket.State saved = plugin.core().store().read(BazaarService.FILE, BazaarMarket.State.class,
            BazaarMarket.State::new);
        var again = new BazaarMarket(saved, slow.market().rules()).claimable(bob.getUniqueId().toString());
        assertEquals(1975, again.cents());
    }

    @Test
    public void aSecondClaimWhileOneIsBeingSavedIsRefusedAndTheFirstPaysOnce() {
        Manual env = new Manual();
        BazaarService slow = service(env);
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        slow.placeSell(bob, "WHEAT", 20, 10);
        fund(alice, 100);
        buy(slow, alice, "WHEAT", 10);
        env.runAll();
        slow.claim(bob, o -> { });
        var blocked = new BazaarService.Outcome[1];
        slow.claim(bob, o -> blocked[0] = o);
        assertFalse(blocked[0].ok());
        env.runAll();
        assertEquals(1975, eco.balanceCents(bob.getUniqueId()));
    }
}
