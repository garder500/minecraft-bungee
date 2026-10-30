package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.WingsApi;

public class AuctionServiceTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private EconomyService eco;
    private ItemKeys keys;
    private AuctionService ah;
    private PlayerMock seller;
    private PlayerMock buyer;
    private PlayerMock rival;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        eco = (EconomyService) plugin.core().service(EconomyApi.class).orElseThrow();
        keys = new ItemKeys(plugin.core());
        ah = service(new DurableFile.Inline());
        seller = server.addPlayer("Seller");
        buyer = server.addPlayer("Buyer");
        rival = server.addPlayer("Rival");
    }

    private AuctionService service(DurableFile.Env env) {
        AuctionService s = new AuctionService(plugin.core(), eco, keys, env);
        s.enable(plugin.core().featureConfig("economy"));
        return s;
    }

    @After
    public void tearDown() {
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    private void fund(PlayerMock p, long coins) {
        eco.addCents(p.getUniqueId(), coins * 100, "test");
    }

    private long balance(PlayerMock p) {
        return eco.balanceCents(p.getUniqueId());
    }

    private ItemStack sword() {
        ItemStack s = new ItemStack(Material.DIAMOND_SWORD);
        s.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        return s;
    }

    private long listSword(boolean bin, long priceCoins) {
        seller.getInventory().setItemInMainHand(sword());
        var r = ah.list(seller, priceCoins * 100, bin, 12);
        assertTrue(r.message(), r.ok());
        return ah.book().state().nextId - 1;
    }

    private BazaarService.Outcome claim(PlayerMock p) {
        var out = new BazaarService.Outcome[1];
        ah.claim(p, o -> out[0] = o);
        return out[0];
    }

    private int swords(PlayerMock p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == Material.DIAMOND_SWORD) {
                n += s.getAmount();
            }
        }
        return n;
    }

    @Test
    public void listingTakesTheItemAndTheFeeAndPersistsTheExactStack() {
        fund(seller, 1000);
        long id = listSword(false, 100);
        assertTrue(seller.getInventory().getItemInMainHand().getType().isAir());
        long fee = ah.settings().fee(100 * 100, 12);
        assertEquals(100_000 - fee, balance(seller));
        assertEquals(fee, ah.book().state().feesBurnedCents);
        ItemStack back = ah.stack(ah.book().byId(id));
        assertEquals(5, back.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals("Diamond Sword", ah.book().byId(id).itemName);
        assertEquals("WEAPONS", ah.book().byId(id).category);
    }

    @Test
    public void listingRequiresAnItemTheFeeAndRespectsLimits() {
        fund(seller, 1000);
        assertFalse("empty hand", ah.list(seller, 10_000, false, 12).ok());
        seller.getInventory().setItemInMainHand(sword());
        assertFalse("price below minimum", ah.list(seller, 5, false, 12).ok());
        assertFalse("bad duration", ah.list(seller, 10_000, false, 5).ok());
        assertEquals("nothing charged on refusals", 100_000, balance(seller));
        assertFalse(seller.getInventory().getItemInMainHand().getType().isAir());
        eco.setCents(seller.getUniqueId(), 0, "test");
        assertFalse("cannot pay the fee", ah.list(seller, 10_000, false, 12).ok());
        assertFalse("item kept", seller.getInventory().getItemInMainHand().getType().isAir());
    }

    @Test
    public void blockedItemsAndWingsAreRefused() {
        fund(seller, 1000);
        seller.getInventory().setItemInMainHand(new ItemStack(Material.BEDROCK));
        assertFalse(ah.list(seller, 10_000, false, 12).ok());
        ItemStack wings = new ItemStack(Material.ELYTRA);
        plugin.core().replace(WingsApi.class, new WingsApi() {
            public ItemStack create() { return wings.clone(); }
            public boolean isWings(ItemStack stack) { return stack.getType() == Material.ELYTRA; }
            public boolean isWearing(org.bukkit.entity.Player player) { return false; }
            public boolean grantOnce(org.bukkit.entity.Player player, String source) { return false; }
            public boolean hasReceived(UUID player) { return false; }
        });
        seller.getInventory().setItemInMainHand(wings);
        var r = ah.list(seller, 10_000, false, 12);
        assertFalse(r.ok());
        assertTrue(r.message().contains("Wings"));
        assertEquals(Material.ELYTRA, seller.getInventory().getItemInMainHand().getType());
    }

    @Test
    public void maxListingsIsEnforced() {
        fund(seller, 10_000_000);
        int max = ah.book().rules().maxListings();
        for (int i = 0; i < max; i++) {
            listSword(false, 100);
        }
        seller.getInventory().setItemInMainHand(sword());
        assertFalse(ah.list(seller, 10_000, false, 12).ok());
    }

    @Test
    public void buyItNowPaysTheSellerNetOfTaxAndGivesTheItemOnce() {
        fund(seller, 1000);
        fund(buyer, 1000);
        long id = listSword(true, 200);
        long sellerAfterFee = balance(seller);
        assertFalse("cannot buy your own listing", ah.buyNow(seller, id).ok());
        var r = ah.buyNow(buyer, id);
        assertTrue(r.message(), r.ok());
        assertEquals(100_000 - 20_000, balance(buyer));
        assertEquals("seller is paid on claim, not before", sellerAfterFee, balance(seller));
        assertFalse("sold listings cannot be bought again", ah.buyNow(rival, id).ok());

        assertTrue(claim(buyer).ok());
        assertEquals(1, swords(buyer));
        assertFalse("nothing left to claim", claim(buyer).ok());
        assertEquals(1, swords(buyer));

        assertTrue(claim(seller).ok());
        long tax = Money.basisPoints(20_000, ah.book().rules().saleTaxBp());
        assertEquals(sellerAfterFee + 20_000 - tax, balance(seller));
        assertFalse(claim(seller).ok());
        assertTrue("the listing is gone once both sides claimed", ah.book().state().auctions.isEmpty());
        assertTrue(ah.book().verify().isEmpty());
    }

    @Test
    public void outbidCoinsComeBackAsAClaimAndNothingIsCreatedOrLost() {
        fund(seller, 1000);
        fund(buyer, 1000);
        fund(rival, 1000);
        long id = listSword(false, 100);
        long total = balance(seller) + balance(buyer) + balance(rival);
        assertFalse("own auction", ah.bid(seller, id, 10_000).ok());
        assertTrue(ah.bid(buyer, id, 10_000).ok());
        assertFalse("already top", ah.bid(buyer, id, 20_000).ok());
        assertFalse("below the minimum increment", ah.bid(rival, id, 10_100).ok());
        assertTrue(ah.bid(rival, id, 11_000).ok());
        assertEquals(100_000 - 10_000, balance(buyer));
        assertTrue(claim(buyer).ok());
        assertEquals(100_000, balance(buyer));
        // coins in the house: the rival's bid
        assertEquals(total, balance(seller) + balance(buyer) + balance(rival) + ah.book().escrowedCents());
    }

    @Test
    public void settlementSellsToTheTopBidderAndReturnsUnsoldItems() {
        fund(seller, 1000);
        fund(buyer, 1000);
        long sold = listSword(false, 100);
        long unsold = listSword(false, 100);
        ah.bid(buyer, sold, 10_000);
        ah.book().byId(sold).endsAt = 1;
        ah.book().byId(unsold).endsAt = 1;
        ah.tick();
        assertEquals(AuctionBook.SOLD, ah.book().byId(sold).state);
        assertEquals(AuctionBook.EXPIRED, ah.book().byId(unsold).state);
        assertTrue(claim(buyer).ok());
        assertEquals(1, swords(buyer));
        long before = balance(seller);
        assertTrue(claim(seller).ok());
        assertEquals("one expired sword back", 1, swords(seller));
        assertTrue(balance(seller) > before);
        assertTrue(ah.book().state().auctions.isEmpty());
    }

    @Test
    public void aBidInTheLastSecondsExtendsTheAuction() {
        fund(seller, 1000);
        fund(buyer, 1000);
        long id = listSword(false, 100);
        long soon = System.currentTimeMillis() + 5_000;
        ah.book().byId(id).endsAt = soon;
        assertTrue(ah.bid(buyer, id, 10_000).ok());
        assertTrue(ah.book().byId(id).endsAt > soon + 60_000);
    }

    @Test
    public void cancelReturnsTheItemOnlyWhenThereAreNoBids() {
        fund(seller, 1000);
        fund(buyer, 1000);
        long a = listSword(false, 100);
        long b = listSword(false, 100);
        ah.bid(buyer, b, 10_000);
        assertTrue(ah.cancel(seller, a).ok());
        assertFalse(ah.cancel(seller, b).ok());
        assertFalse("not your listing", ah.cancel(buyer, b).ok());
        assertTrue(claim(seller).ok());
        assertEquals(1, swords(seller));
    }

    @Test
    public void aFullInventoryKeepsTheClaimInTheBook() {
        fund(seller, 1000);
        long id = listSword(false, 100);
        ah.cancel(seller, id);
        for (int i = 0; i < 36; i++) {
            seller.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        }
        var r = claim(seller);
        assertFalse(r.ok());
        assertFalse(ah.claimable(seller).empty());
        seller.getInventory().setItem(0, null);
        assertTrue(claim(seller).ok());
        assertEquals(1, swords(seller));
    }

    @Test
    public void aClaimInterruptedByCloseIsKeptNotDuplicated() {
        BazaarServiceTest.Manual env = new BazaarServiceTest.Manual();
        AuctionService slow = service(env);
        fund(seller, 1000);
        seller.getInventory().setItemInMainHand(sword());
        assertTrue(slow.list(seller, 10_000, false, 12).ok());
        env.runAll();
        slow.cancel(seller, 1);
        env.runAll();
        var result = new BazaarService.Outcome[1];
        slow.claim(seller, o -> result[0] = o);
        assertNull(result[0]);
        slow.close(null);
        env.runAll();
        assertNull(result[0]);
        assertEquals("not delivered", 0, swords(seller));
        AuctionBook.State saved = plugin.core().store().read(AuctionService.FILE, AuctionBook.State.class,
            AuctionBook.State::new);
        AuctionBook reopened = new AuctionBook(saved, slow.book().rules());
        assertEquals("the item is claimable again after a restart", 1,
            reopened.claimable(seller.getUniqueId().toString()).items().size());
    }

    @Test
    public void theStateSurvivesARestart() {
        fund(seller, 1000);
        fund(buyer, 1000);
        long id = listSword(false, 100);
        ah.bid(buyer, id, 10_000);
        ah.close(null);
        AuctionService reopened = service(new DurableFile.Inline());
        AuctionBook.Auction a = reopened.book().byId(id);
        assertNotNull(a);
        assertEquals(10_000, a.bidCents);
        assertEquals(5, reopened.stack(a).getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    public void disabledHouseRefusesEverything() {
        var cfg = plugin.core().featureConfig("economy");
        cfg.set("auction.enabled", false);
        ah.reload(cfg);
        fund(seller, 1000);
        seller.getInventory().setItemInMainHand(sword());
        assertFalse(ah.list(seller, 10_000, false, 12).ok());
        assertFalse(claim(seller).ok());
    }
}
