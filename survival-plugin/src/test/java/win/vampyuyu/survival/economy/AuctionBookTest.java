package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class AuctionBookTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR = 3_600_000L;
    private AuctionBook book;

    @Before
    public void setUp() {
        book = new AuctionBook(new AuctionBook.State(),
            new AuctionBook.Rules(3, 1_000, 100_000_000, 100, 5, 100, 120_000, 120_000, 2, 5));
    }

    private AuctionBook.Auction auction(String seller, long start, boolean bin) {
        return book.list(seller, seller, "item", "DIAMOND_SWORD", "Sword", "WEAPONS", 1, bin, start, HOUR, NOW);
    }

    @Test
    public void minBidFollowsTheIncrementRule() {
        var a = auction("S", 10_000, false);
        assertEquals(10_000, book.minBid(a));
        book.bid(a, "B", "B", 10_000, NOW + 1000);
        assertEquals(10_000 + 500, book.minBid(a)); // 5%
        var cheap = auction("S", 1_000, false);
        book.bid(cheap, "B", "B", 1_000, NOW);
        assertEquals(1_100, book.minBid(cheap)); // minimum step of 1 coin
    }

    @Test
    public void bidRulesAreEnforced() {
        var a = auction("S", 10_000, false);
        assertFalse(book.checkBid(a, "S", 20_000, NOW).ok());       // own auction
        assertFalse(book.checkBid(a, "B", 9_999, NOW).ok());         // below start
        assertTrue(book.checkBid(a, "B", 10_000, NOW).ok());
        book.bid(a, "B", "B", 10_000, NOW);
        assertFalse(book.checkBid(a, "B", 20_000, NOW).ok());       // already highest
        assertFalse(book.checkBid(a, "C", 10_400, NOW).ok());        // under the increment
        assertTrue(book.checkBid(a, "C", 10_500, NOW).ok());
        assertFalse(book.checkBid(a, "C", 10_500, NOW + HOUR).ok()); // after the end
        var bin = auction("S", 5_000, true);
        assertFalse(book.checkBid(bin, "B", 5_000, NOW).ok());
    }

    @Test
    public void outbidCoinsBecomeARefundClaim() {
        var a = auction("S", 10_000, false);
        book.bid(a, "B", "B", 10_000, NOW);
        String prev = book.bid(a, "C", "C", 11_000, NOW);
        assertEquals("B", prev);
        assertEquals(10_000, book.claimable("B").cents());
        assertEquals(10_000 + 11_000, book.escrowedCents());
    }

    @Test
    public void antiSnipeExtendsAtMostTwice() {
        var a = auction("S", 10_000, false);
        long end = a.endsAt;
        book.bid(a, "B", "B", 10_000, end - 10_000);
        assertEquals(end - 10_000 + 120_000, a.endsAt);
        book.bid(a, "C", "C", 20_000, a.endsAt - 1_000);
        assertEquals(2, a.extensions);
        long last = a.endsAt;
        book.bid(a, "B", "B", 30_000, a.endsAt - 1_000);
        assertEquals(last, a.endsAt);
        // An early bid does not extend.
        var b = auction("S", 10_000, false);
        long original = b.endsAt;
        book.bid(b, "B", "B", 10_000, NOW + 60_000);
        assertEquals(original, b.endsAt);
    }

    @Test
    public void settlingSellsWithBidsAndExpiresWithout() {
        var sold = auction("S", 10_000, false);
        book.bid(sold, "B", "B", 12_000, NOW);
        var expired = auction("S", 10_000, false);
        assertTrue(book.settleDue(NOW + HOUR - 1).isEmpty());
        var changed = book.settleDue(NOW + HOUR);
        assertEquals(2, changed.size());
        assertEquals(AuctionBook.SOLD, sold.state);
        assertEquals(12_000, sold.finalCents);
        assertEquals(120, sold.taxCents);
        assertEquals(AuctionBook.EXPIRED, expired.state);
        assertEquals(1, book.state().sales.size());
    }

    @Test
    public void buyNowSellsInstantly() {
        var a = auction("S", 5_000, true);
        assertFalse(book.checkBuyNow(a, "S", NOW).ok());
        assertTrue(book.checkBuyNow(a, "B", NOW).ok());
        book.buyNow(a, "B", "B", NOW);
        assertEquals(AuctionBook.SOLD, a.state);
        assertFalse(book.checkBuyNow(a, "C", NOW).ok());
        assertEquals(5_000 - 50, book.claimable("S").cents());
        assertEquals(1, book.claimable("B").items().size());
    }

    @Test
    public void claimsPayOnceAndFinishedListingsDisappear() {
        var a = auction("S", 5_000, true);
        book.buyNow(a, "B", "B", NOW);
        var s = book.takeClaim("S", List.of(), true);
        assertEquals(4_950, s.cents());
        assertTrue(book.claimable("S").empty());
        var b = book.takeClaim("B", book.claimable("B").items(), false);
        assertNotNull(b);
        assertTrue(book.state().auctions.isEmpty());
        assertNull(book.takeClaim("B", List.of(a), false));
    }

    @Test
    public void aFailedClaimIsRestoredExactly() {
        var a = auction("S", 5_000, true);
        book.buyNow(a, "B", "B", NOW);
        var b = auction("S", 6_000, false);
        book.bid(b, "X", "X", 6_000, NOW);
        book.bid(b, "B", "B", 7_000, NOW); // X is refunded 6_000
        book.settleDue(NOW + HOUR);
        long beforeS = book.claimable("S").cents();
        long beforeX = book.claimable("X").cents();
        var taken = book.takeClaim("S", List.of(), true);
        assertEquals(beforeS, taken.cents());
        assertTrue(book.claimable("S").empty());
        book.restore(taken);
        assertEquals(beforeS, book.claimable("S").cents());
        var x = book.takeClaim("X", List.of(), true);
        book.restore(x);
        assertEquals(beforeX, book.claimable("X").cents());
        var items = book.claimable("B").items();
        assertEquals(2, items.size());
        var t2 = book.takeClaim("B", items, false);
        assertTrue(book.claimable("B").empty());
        book.restore(t2);
        assertEquals(2, book.claimable("B").items().size());
    }

    @Test
    public void cancelOnlyWithoutBids() {
        var a = auction("S", 5_000, false);
        assertFalse(book.cancel(a, "B", NOW).ok());
        book.bid(a, "B", "B", 5_000, NOW);
        assertFalse(book.cancel(a, "S", NOW).ok());
        var c = auction("S", 5_000, false);
        assertTrue(book.cancel(c, "S", NOW).ok());
        assertEquals(1, book.claimable("S").items().size());
        assertFalse(book.cancel(c, "S", NOW).ok());
    }

    @Test
    public void listingLimitAndPriceBounds() {
        assertFalse(book.checkList("S", 999).ok());
        assertFalse(book.checkList("S", 100_000_001).ok());
        for (int i = 0; i < 3; i++) {
            assertTrue(book.checkList("S", 2_000).ok());
            auction("S", 2_000, false);
        }
        assertFalse(book.checkList("S", 2_000).ok());
        assertTrue(book.checkList("T", 2_000).ok());
    }

    @Test
    public void coinsAreConservedThroughALifecycle() {
        // B bids 10_000, C outbids with 20_000, sale at 20_000: held coins = refund(B) + seller net + tax.
        var a = auction("S", 10_000, false);
        book.bid(a, "B", "B", 10_000, NOW);
        book.bid(a, "C", "C", 20_000, NOW);
        book.settleDue(NOW + HOUR);
        assertEquals(30_000, book.escrowedCents() + book.state().taxBurnedCents);
        assertTrue(book.verify().isEmpty());
    }

    @Test
    public void saleHistoryIsARing() {
        for (int i = 0; i < 9; i++) {
            var a = auction("S" + i, 2_000, true);
            book.buyNow(a, "B", "B", NOW + i);
        }
        assertEquals(5, book.state().sales.size());
    }

    @Test
    public void survivesAJsonRoundTrip() {
        var a = auction("S", 10_000, false);
        book.bid(a, "B", "B", 10_000, NOW);
        book.bid(a, "C", "C", 12_000, NOW);
        String json = new com.google.gson.Gson().toJson(book.state());
        var back = new com.google.gson.Gson().fromJson(json, AuctionBook.State.class);
        var b2 = new AuctionBook(back, book.rules());
        assertEquals(book.escrowedCents(), b2.escrowedCents());
        assertEquals(12_000, b2.byId(a.id).bidCents);
    }
}
