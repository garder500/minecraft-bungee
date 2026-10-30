package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class BazaarMarketTest {
    private static final long NOW = 1_700_000_000_000L;
    private BazaarMarket m;

    @Before
    public void setUp() {
        m = new BazaarMarket(new BazaarMarket.State(), new BazaarMarket.Rules(125, 5, 10_000, 10_000_000, 0, 48));
    }

    private BazaarMarket.Order sell(String who, long price, int amount) {
        var p = m.placeSell(who, who, "WHEAT", price, amount, NOW);
        assertTrue(p.check().error(), p.check().ok());
        return p.order();
    }

    private BazaarMarket.Order buy(String who, long price, int amount) {
        var p = m.placeBuy(who, who, "WHEAT", price, amount, NOW);
        assertTrue(p.check().error(), p.check().ok());
        return p.order();
    }

    @Test
    public void limitOrdersRestWithoutCrossing() {
        sell("A", 50, 10);
        buy("B", 40, 10);
        assertEquals(50, m.bestAsk("WHEAT"));
        assertEquals(40, m.bestBid("WHEAT"));
        assertTrue(m.verify().isEmpty());
    }

    @Test
    public void crossingBuyTradesAtTheRestingPriceAndRefundsTheDifference() {
        var ask = sell("A", 50, 10);
        var bid = buy("B", 60, 4);
        assertEquals(4, bid.filled());
        assertEquals(4, bid.claimItems);
        assertEquals((60 - 50) * 10 * 4, bid.claimCents); // price improvement refunded
        assertEquals(6, ask.remaining);
        long gross = 50 * 10 * 4;
        long tax = gross * 125 / 10_000;
        assertEquals(gross - tax, ask.claimCents);
        assertEquals(tax, m.state().taxBurnedCents);
        assertEquals(50, m.lastPrice("WHEAT"));
        assertTrue(m.verify().isEmpty());
    }

    @Test
    public void coinsAreConservedAcrossATrade() {
        var ask = sell("A", 50, 10);
        var bid = buy("B", 60, 4);
        assertEquals(2400, ask.claimCents + bid.claimCents + m.state().taxBurnedCents);
    }

    @Test
    public void priceTimePriority() {
        var first = sell("A", 50, 5);
        var second = sell("B", 50, 5);
        var cheaper = sell("C", 45, 5);
        var bid = buy("D", 50, 12);
        assertEquals(0, cheaper.remaining);
        assertEquals(0, first.remaining);
        assertEquals(3, second.remaining);
        assertEquals(12, bid.claimItems);
    }

    @Test
    public void crossingSellHitsTheHighestBidFirst() {
        var low = buy("A", 40, 5);
        var high = buy("B", 45, 5);
        var s = sell("C", 40, 7);
        assertEquals(0, high.remaining);
        assertEquals(3, low.remaining);
        long expected = (45 * 10 * 5 - 45 * 10 * 5 * 125 / 10_000) + (45 * 10 * 0) ;
        // Trades happen at the RESTING bid price: 5 at 45, then 2 at 40.
        long g1 = 45 * 10 * 5;
        long g2 = 40 * 10 * 2;
        assertEquals(g1 - g1 * 125 / 10_000 + g2 - g2 * 125 / 10_000, s.claimCents);
        assertTrue(expected > 0);
        assertTrue(m.verify().isEmpty());
    }

    @Test
    public void selfTradeIsRefusedForLimitOrders() {
        sell("A", 50, 5);
        assertFalse(m.placeBuy("A", "A", "WHEAT", 50, 1, NOW).check().ok());
        assertTrue(m.placeBuy("A", "A", "WHEAT", 40, 1, NOW).check().ok());
        assertFalse(m.placeSell("A", "A", "WHEAT", 40, 1, NOW).check().ok());
    }

    @Test
    public void instantTradesSkipTheCallersOwnOrders() {
        sell("A", 40, 5);
        sell("B", 50, 5);
        var quote = m.quoteBuy("A", "WHEAT", 3);
        assertTrue(quote.check().ok());
        assertEquals(3 * 50 * 10, quote.cents());
        assertEquals(1, quote.fills().size());
        assertEquals("B", quote.fills().get(0).resting().owner);
        assertFalse(m.quoteBuy("A", "WHEAT", 6).check().ok());
    }

    @Test
    public void instantBuyWalksTheBookAndCreditsSellers() {
        var a = sell("A", 40, 2);
        var b = sell("B", 50, 5);
        var q = m.quoteBuy("Z", "WHEAT", 4);
        assertEquals(2 * 400 + 2 * 500, q.cents());
        m.commitBuy(q, "WHEAT", NOW);
        assertEquals(0, a.remaining);
        assertEquals(3, b.remaining);
        assertEquals(800 - 800 * 125 / 10_000, a.claimCents);
        assertEquals(50, m.bestAsk("WHEAT"));
    }

    @Test
    public void instantSellPaysNetOfTaxAndFillsTheBids() {
        var bid = buy("A", 100, 10);
        var q = m.quoteSell("Z", "WHEAT", 4);
        long gross = 4 * 100 * 10;
        assertEquals(gross - gross * 125 / 10_000, q.cents());
        m.commitSell(q, "WHEAT", NOW);
        assertEquals(4, bid.claimItems);
        assertEquals(6, bid.remaining);
        assertFalse(m.quoteSell("Z", "WHEAT", 7).check().ok());
    }

    @Test
    public void cancelMovesTheEscrowToTheClaimAndOnlyOnce() {
        var b = buy("A", 50, 10);
        assertTrue(m.cancel(b.id, "A"));
        assertEquals(10 * 50 * 10, b.claimCents);
        assertFalse(m.cancel(b.id, "A"));
        assertFalse(m.cancel(b.id, "someone else"));
        assertEquals(0, m.bestBid("WHEAT"));
        var s = sell("A", 70, 3);
        assertTrue(m.cancel(s.id, "A"));
        assertEquals(3, s.claimItems);
    }

    @Test
    public void partialFillThenCancelRefundsOnlyTheRest() {
        var ask = sell("A", 50, 10);
        buy("B", 50, 4);
        assertTrue(m.cancel(ask.id, "A"));
        assertEquals(6, ask.claimItems);
        assertEquals(4 * 500 - 4 * 500 * 125 / 10_000, ask.claimCents);
    }

    @Test
    public void claimsAreTakenExactlyOnceAndFinishedOrdersDisappear() {
        sell("A", 50, 4);
        buy("B", 50, 4);
        var claimA = m.claimable("A");
        var claimB = m.claimable("B");
        assertEquals(2000 - 25, claimA.cents());
        assertEquals(Map.of("WHEAT", 4), claimB.items());
        assertFalse(m.takeClaim("A", claimA.cents() + 1, Map.of()));
        assertFalse(m.takeClaim("B", 0, Map.of("WHEAT", 5)));
        assertTrue(m.takeClaim("A", claimA.cents(), Map.of()));
        assertTrue(m.takeClaim("B", 0, Map.of("WHEAT", 4)));
        assertTrue(m.claimable("A").empty());
        assertTrue(m.claimable("B").empty());
        assertTrue(m.state().orders.isEmpty());
        assertFalse(m.takeClaim("A", 1, Map.of()));
    }

    @Test
    public void partialClaimKeepsTheRemainder() {
        sell("A", 50, 4);
        buy("B", 50, 4);
        assertTrue(m.takeClaim("B", 0, Map.of("WHEAT", 3)));
        assertEquals(Map.of("WHEAT", 1), m.claimable("B").items());
        assertEquals(1, m.state().orders.stream().filter(o -> o.owner.equals("B")).count());
    }

    @Test
    public void restoredClaimsComeBack() {
        m.restoreClaim("A", "A", 500, Map.of("WHEAT", 7), NOW);
        var c = m.claimable("A");
        assertEquals(500, c.cents());
        assertEquals(Map.of("WHEAT", 7), c.items());
    }

    @Test
    public void orderLimitAndBoundsAreEnforced() {
        for (int i = 0; i < 5; i++) {
            buy("A", 10 + i, 1);
        }
        assertFalse(m.placeBuy("A", "A", "WHEAT", 9, 1, NOW).check().ok());
        assertFalse(m.placeBuy("B", "B", "WHEAT", 0, 1, NOW).check().ok());
        assertFalse(m.placeBuy("B", "B", "WHEAT", 5, 0, NOW).check().ok());
        assertFalse(m.placeBuy("B", "B", "WHEAT", 5, 10_001, NOW).check().ok());
        assertFalse(m.placeBuy("B", "B", "WHEAT", 10_000_001, 1, NOW).check().ok());
        assertFalse(m.quoteBuy("B", "WHEAT", 0).check().ok());
    }

    @Test
    public void priceBandBlocksManipulation() {
        m.rules(new BazaarMarket.Rules(125, 50, 10_000, 10_000_000, 5.0, 48));
        sell("A", 100, 1);
        buy("B", 100, 1);
        assertFalse(m.placeSell("C", "C", "WHEAT", 600, 1, NOW).check().ok());
        assertFalse(m.placeBuy("C", "C", "WHEAT", 10, 1, NOW).check().ok());
        assertTrue(m.placeSell("C", "C", "WHEAT", 400, 1, NOW).check().ok());
    }

    @Test
    public void historyKeepsAnHourlyRingAndAverages() {
        sell("A", 100, 10);
        buy("B", 100, 10);
        assertEquals(100, m.average24h("WHEAT", NOW));
        assertEquals(10, m.volume24h("WHEAT", NOW));
        assertEquals(0, m.average24h("WHEAT", NOW + 30L * 3_600_000L));
        for (int h = 0; h < 100; h++) {
            m.placeSell("S" + h, "S", "WHEAT", 100, 1, NOW + h * 3_600_000L);
            m.placeBuy("T" + h, "T", "WHEAT", 100, 1, NOW + h * 3_600_000L);
        }
        assertTrue(m.state().history.get("WHEAT").buckets.size() <= 48);
        assertFalse(m.sparkline("WHEAT", 24).isEmpty());
    }

    @Test
    public void survivesAJsonRoundTrip() {
        sell("A", 50, 4);
        buy("B", 40, 3);
        buy("C", 45, 2);
        String json = new com.google.gson.Gson().toJson(m.state());
        var back = new com.google.gson.Gson().fromJson(json, BazaarMarket.State.class);
        var m2 = new BazaarMarket(back, m.rules());
        assertEquals(m.bestAsk("WHEAT"), m2.bestAsk("WHEAT"));
        assertEquals(m.bestBid("WHEAT"), m2.bestBid("WHEAT"));
        assertEquals(m.escrowedCents(), m2.escrowedCents());
        assertTrue(m2.verify().isEmpty());
    }

    @Test
    public void randomisedTradingConservesValueAndKeepsTheBookSound() {
        java.util.Random rnd = new java.util.Random(42);
        long escrowIn = 0;
        long itemsIn = 0;
        for (int i = 0; i < 3000; i++) {
            String who = "P" + rnd.nextInt(12);
            long price = 90 + rnd.nextInt(21);
            int amount = 1 + rnd.nextInt(20);
            int op = rnd.nextInt(10);
            if (op < 4) {
                var p = m.placeBuy(who, who, "WHEAT", price, amount, NOW + i);
                if (p.check().ok()) {
                    escrowIn += price * 10 * amount;
                }
            } else if (op < 8) {
                var p = m.placeSell(who, who, "WHEAT", price, amount, NOW + i);
                if (p.check().ok()) {
                    itemsIn += amount;
                }
            } else if (op == 8) {
                var orders = m.ordersOf(who);
                if (!orders.isEmpty()) {
                    m.cancel(orders.get(rnd.nextInt(orders.size())).id, who);
                }
            } else {
                var c = m.claimable(who);
                m.takeClaim(who, c.cents(), c.items());
                escrowIn -= c.cents();
                itemsIn -= c.items().values().stream().mapToInt(Integer::intValue).sum();
            }
            assertTrue("step " + i + ": " + m.verify(), m.verify().isEmpty());
        }
        assertEquals(escrowIn, m.escrowedCents() + m.state().taxBurnedCents);
        long itemsHeld = 0;
        for (var o : m.state().orders) {
            itemsHeld += o.claimItems + (!o.buy && o.live() ? o.remaining : 0);
        }
        assertEquals(itemsIn, itemsHeld);
    }
}
