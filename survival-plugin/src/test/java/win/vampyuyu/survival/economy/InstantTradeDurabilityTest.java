package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * The two phase rule for instant Bazaar trades: the taker is paid / given the goods only once the market file that
 * records the fill is durable. Uses an environment where the write happens later.
 */
public class InstantTradeDurabilityTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private ItemKeys keys;
    private BazaarService bz;
    private BazaarServiceTest.Manual env;
    private PlayerMock alice;
    private PlayerMock bob;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        keys = new ItemKeys(plugin.core());
        env = new BazaarServiceTest.Manual();
        bz = new BazaarService(plugin.core(), eco, keys, env);
        bz.enable(plugin.core().featureConfig("economy"));
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private void fund(PlayerMock p, long coins) {
        eco.addCents(p.getUniqueId(), coins * 100, "test");
    }

    private long purse(PlayerMock p) {
        return eco.balanceCents(p.getUniqueId());
    }

    private int carried(PlayerMock p, String key) {
        return Inv.count(p, keys, key);
    }

    /** Bob has a buy order for 10 wheat at 2.0 each, already durable. */
    private void bobBids() {
        fund(bob, 100);
        assertTrue(bz.placeBuy(bob, "WHEAT", 20, 10).ok());
        env.runAll();
    }

    @Test
    public void instantSellPaysOnlyWhenTheMarketFileIsDurable() {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        var result = BazaarServiceTest.sell(bz, alice, "WHEAT", 10);
        assertNull("still waiting for the write", result);
        assertEquals("items left the inventory at once", 0, carried(alice, "WHEAT"));
        assertEquals("but no coins yet: a crash now must not pay the seller", 0, purse(alice));
        env.runAll();
        long net = 2000 - Money.basisPoints(2000, 125);
        assertEquals(net, purse(alice));
    }

    @Test
    public void instantSellCallbackReportsTheSaleOnce() {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 4));
        var out = new BazaarService.Outcome[2];
        int[] calls = {0};
        bz.instantSell(alice, "WHEAT", 4, o -> {
            out[calls[0]++ % 2] = o;
        });
        assertEquals(0, calls[0]);
        env.runAll();
        assertEquals(1, calls[0]);
        assertTrue(out[0].message(), out[0].ok());
        env.runAll();
        assertEquals("no second delivery", 1, calls[0]);
    }

    @Test
    public void instantBuyGivesTheGoodsOnlyWhenTheMarketFileIsDurable() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 10).ok());
        env.runAll();
        fund(alice, 100);
        var result = BazaarServiceTest.buy(bz, alice, "WHEAT", 10);
        assertNull(result);
        assertEquals("coins are taken at once", 10_000 - 2_000, purse(alice));
        assertEquals("the goods wait for the file", 0, carried(alice, "WHEAT"));
        env.runAll();
        assertEquals(10, carried(alice, "WHEAT"));
    }

    @Test
    public void aFailedWriteTurnsAnInstantSellIntoAClaim() throws Exception {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        var out = new BazaarService.Outcome[1];
        bz.instantSell(alice, "WHEAT", 10, o -> out[0] = o);
        // make the next write fail: a non-empty directory sits where the file must be moved
        Path target = plugin.core().store().root().resolve(BazaarService.FILE);
        Files.deleteIfExists(target);
        Files.createDirectories(target);
        Files.writeString(target.resolve("blocker"), "x");
        env.runAll();
        assertNotNull(out[0]);
        assertFalse(out[0].ok());
        assertEquals("nothing paid directly", 0, purse(alice));
        long net = 2000 - Money.basisPoints(2000, 125);
        assertEquals("the proceeds are waiting in the claim", net, bz.claimable(alice).cents());
        // the blocker goes away, the retry succeeds and the claim can be collected once
        Files.delete(target.resolve("blocker"));
        Files.delete(target);
        env.runAll();
        var claimed = new BazaarService.Outcome[1];
        bz.claim(alice, o -> claimed[0] = o);
        env.runAll();
        assertTrue(claimed[0].message(), claimed[0].ok());
        assertEquals(net, purse(alice));
        assertTrue(bz.claimable(alice).empty());
    }

    @Test
    public void aFailedWriteTurnsAnInstantBuyIntoAClaim() throws Exception {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 10).ok());
        env.runAll();
        fund(alice, 100);
        var out = new BazaarService.Outcome[1];
        bz.instantBuy(alice, "WHEAT", 10, o -> out[0] = o);
        Path target = plugin.core().store().root().resolve(BazaarService.FILE);
        Files.deleteIfExists(target);
        Files.createDirectories(target);
        Files.writeString(target.resolve("blocker"), "x");
        env.runAll();
        assertFalse(out[0].ok());
        assertEquals(0, carried(alice, "WHEAT"));
        assertEquals(10, bz.claimable(alice).items().get("WHEAT").intValue());
    }

    @Test
    public void closeWithAnInstantSellPendingKeepsTheProceedsInTheFinalFile() {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        var out = new BazaarService.Outcome[1];
        bz.instantSell(alice, "WHEAT", 10, o -> out[0] = o);
        bz.close(null);
        env.runAll();
        assertNull("nothing delivered after close", out[0]);
        assertEquals(0, purse(alice));
        BazaarMarket.State saved = plugin.core().store().read(BazaarService.FILE, BazaarMarket.State.class,
            BazaarMarket.State::new);
        var market = new BazaarMarket(saved, bz.market().rules());
        long net = 2000 - Money.basisPoints(2000, 125);
        assertEquals("paid on the next start, once", net, market.claimable(alice.getUniqueId().toString()).cents());
        assertEquals("the buyer's bid is filled in the same file", 10,
            market.claimable(bob.getUniqueId().toString()).items().get("WHEAT").intValue());
    }

    @Test
    public void closeWithAnInstantBuyPendingKeepsTheGoodsInTheFinalFile() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 10).ok());
        env.runAll();
        fund(alice, 100);
        bz.instantBuy(alice, "WHEAT", 10, o -> { });
        bz.close(null);
        env.runAll();
        assertEquals(0, carried(alice, "WHEAT"));
        BazaarMarket.State saved = plugin.core().store().read(BazaarService.FILE, BazaarMarket.State.class,
            BazaarMarket.State::new);
        var market = new BazaarMarket(saved, bz.market().rules());
        assertEquals(10, market.claimable(alice.getUniqueId().toString()).items().get("WHEAT").intValue());
    }

    @Test
    public void goodsThatNoLongerFitStayClaimableInsteadOfBeingDropped() {
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        assertTrue(bz.placeSell(bob, "WHEAT", 20, 10).ok());
        env.runAll();
        fund(alice, 100);
        var out = new BazaarService.Outcome[1];
        bz.instantBuy(alice, "WHEAT", 10, o -> out[0] = o);
        // the inventory fills up while the file is being written
        for (int i = 0; i < 36; i++) {
            alice.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        env.runAll();
        assertTrue(out[0].message(), out[0].ok());
        assertEquals(0, carried(alice, "WHEAT"));
        assertEquals(10, bz.claimable(alice).items().get("WHEAT").intValue());
    }

    @Test
    public void sellInventoryUsesTheBestBidsAndSkipsYourOwn() {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 25));
        alice.getInventory().addItem(new ItemStack(Material.COAL, 5));
        var out = new BazaarService.Outcome[1];
        bz.sellInventory(alice, o -> out[0] = o);
        env.runAll();
        assertNotNull(out[0]);
        assertTrue(out[0].message(), out[0].ok());
        assertEquals("only the 10 wanted units are sold", 15, carried(alice, "WHEAT"));
        assertEquals("nobody bids for coal", 5, carried(alice, "COAL"));
        assertTrue(purse(alice) > 0);
    }

    @Test
    public void sellInventoryNeverUsesYourOwnOrders() {
        bobBids();
        bob.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        var out = new BazaarService.Outcome[1];
        bz.sellInventory(bob, o -> out[0] = o);
        assertFalse(out[0].ok());
        assertEquals(10, carried(bob, "WHEAT"));
    }

    @Test
    public void ownersOfRestingOrdersAreToldWhenTheyFill() {
        bobBids();
        bob.nextMessage(); // clear
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 4));
        BazaarServiceTest.sell(bz, alice, "WHEAT", 4);
        String message = null;
        String line;
        while ((line = bob.nextMessage()) != null) {
            message = line;
        }
        assertNotNull("Bob got a notification", message);
        assertTrue(message, message.contains("order #"));
    }

    @Test
    public void joinReminderMentionsWaitingClaims() {
        bobBids();
        alice.getInventory().addItem(new ItemStack(Material.WHEAT, 10));
        BazaarServiceTest.sell(bz, alice, "WHEAT", 10);
        env.runAll();
        while (bob.nextMessage() != null) {
            // drain
        }
        bz.remind(bob);
        String line = bob.nextMessage();
        assertNotNull(line);
        assertTrue(line, line.contains("/bz claim"));
    }
}
