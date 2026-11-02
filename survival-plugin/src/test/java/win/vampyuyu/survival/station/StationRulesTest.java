package win.vampyuyu.survival.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import win.vampyuyu.survival.station.StationConfig.Contract;
import win.vampyuyu.survival.station.StationConfig.Product;

class StationRulesTest {
    private static final long HOUR = 3_600_000L;

    @Test
    void aQueueKeepsRunningWhileThePlayerIsAway() {
        StationData d = new StationData();
        d.jobs.add(new StationData.Job("a"));
        d.jobs.add(new StationData.Job("b"));
        d.jobs.add(new StationData.Job("c"));
        StationRules.advance(d, 0, 2, r -> 100);
        assertEquals(2, d.jobs.stream().filter(j -> j.endAt > 0).count(), "two slots run at once");
        // 250 s later: a and b done at 100 s, c started at 100 s and ended at 200 s
        int finished = StationRules.advance(d, 250_000, 2, r -> 100);
        assertEquals(3, finished);
        assertEquals(List.of("a", "b", "c"), d.ready);
        assertTrue(d.jobs.isEmpty());
    }

    @Test
    void aWaitingBrewStartsWhenASlotFreesUpNotWhenThePlayerReturns() {
        StationData d = new StationData();
        d.jobs.add(new StationData.Job("a"));
        d.jobs.add(new StationData.Job("b"));
        StationRules.advance(d, 0, 1, r -> 100);
        StationRules.advance(d, 150_000, 1, r -> 100);
        assertEquals(List.of("a"), d.ready);
        assertEquals(1, d.jobs.size());
        assertEquals(200_000, d.jobs.get(0).endAt, "b started when a ended (100 s), so it ends at 200 s");
    }

    @Test
    void ranchOutputGrowsWithTheTierAndStopsAtTheStorage() {
        Product wool = new Product("wool", "WHITE_WOOL", 1, 240);
        assertEquals(15, StationRules.ranchUnits(1, 1 + HOUR, wool, 1, 0.5, 12));
        assertEquals(37, StationRules.ranchUnits(1, 1 + HOUR, wool, 4, 0.5, 12), "tier IV makes x2.5");
        assertEquals(15 * 12, StationRules.ranchUnits(1, 1 + 100 * HOUR, wool, 1, 0.5, 12), "12 hours are kept, no more");
        assertEquals(0, StationRules.ranchUnits(0, 5 * HOUR, wool, 1, 0.5, 12), "no Ranch placed yet");
    }

    @Test
    void ranchProductsWaitForTheirTier() {
        Product marrow = new Product("marrow", "custom:marrow", 2, 900);
        assertEquals(0, StationRules.ranchUnits(1, 1 + 10 * HOUR, marrow, 1, 0.5, 12));
        assertEquals(60, StationRules.ranchUnits(1, 1 + 10 * HOUR, marrow, 2, 0.5, 12), "10 h x 4 an hour x 1.5");
    }

    @Test
    void theCrucibleKeepsComponentsWholeAndSixtyPercentOfTheRest() {
        Map<String, Integer> cost = new LinkedHashMap<>();
        cost.put("custom:bronzite", 12);
        cost.put("IRON_SWORD", 1);
        cost.put("custom:warden_heart", 2);
        Map<String, Integer> back = StationRules.refund(cost, 0.6, Set.of("warden_heart"));
        assertEquals(7, back.get("custom:bronzite"));
        assertFalse(back.containsKey("IRON_SWORD"), "60 percent of one piece rounds down to nothing");
        assertEquals(2, back.get("custom:warden_heart"));
    }

    private static Contract c(String id, String kind, String target, int amount, int tier) {
        return new Contract(id, kind, id, target, amount, tier, 100, 0, null, 1);
    }

    @Test
    void theSamePlayerGetsTheSameContractsAllDayAndOnlyThoseTheirTierAllows() {
        List<Contract> pool = List.of(c("a", "kill", "ZOMBIE", 5, 1), c("b", "kill", "SPIDER", 5, 1),
            c("c", "deliver", "COAL", 5, 1), c("d", "boss", "magma_warden", 1, 3), c("e", "kill", "BLAZE", 5, 3));
        UUID p = UUID.fromString("00000000-0000-0000-0000-000000000042");
        List<String> low = StationRules.offer("2026-11-02", p, pool, 1, 3);
        assertEquals(3, low.size());
        assertEquals(low, StationRules.offer("2026-11-02", p, pool, 1, 3));
        assertFalse(low.contains("d") || low.contains("e"), "tier III contracts stay hidden at tier I");
        assertEquals(5, StationRules.offer("2026-11-02", p, pool, 3, 9).size());
    }

    @Test
    void theDayRollsOverOnceAndClearsTheSheet() {
        List<Contract> pool = List.of(c("a", "kill", "ZOMBIE", 2, 1));
        StationData d = new StationData();
        UUID p = UUID.randomUUID();
        assertTrue(StationRules.rollDay(d, "2026-11-02", p, pool, 1, 3));
        d.progress.put("a", 2);
        d.done.add("a");
        d.coinsToday = 99;
        assertFalse(StationRules.rollDay(d, "2026-11-02", p, pool, 1, 3));
        assertTrue(StationRules.rollDay(d, "2026-11-03", p, pool, 1, 3));
        assertTrue(d.progress.isEmpty() && d.done.isEmpty());
        assertEquals(0, d.coinsToday);
    }

    @Test
    void killsCountUpToTheAmountAndReportTheContractThatBecameReady() {
        Map<String, Contract> all = Map.of("a", c("a", "kill", "ZOMBIE", 2, 1), "b", c("b", "boss", "magma_warden", 1, 1));
        StationData d = new StationData();
        d.offered = List.of("a", "b");
        assertEquals(List.of(), StationRules.count(d, all, "kill", "ZOMBIE"));
        assertEquals(List.of("a"), StationRules.count(d, all, "kill", "zombie"));
        assertEquals(List.of(), StationRules.count(d, all, "kill", "ZOMBIE"), "already full");
        assertEquals(2, d.progress.get("a"));
        assertEquals(List.of(), StationRules.count(d, all, "kill", "magma_warden"), "a boss is not a kill");
        assertEquals(List.of("b"), StationRules.count(d, all, "boss", "magma_warden"));
    }

    @Test
    void theDailyCoinCapLimitsThePayment() {
        StationData d = new StationData();
        Contract c = c("a", "kill", "ZOMBIE", 1, 1);
        assertEquals(100, StationRules.payableCoins(d, c, 3000));
        d.coinsToday = 2950;
        assertEquals(50, StationRules.payableCoins(d, c, 3000));
        d.coinsToday = 3000;
        assertEquals(0, StationRules.payableCoins(d, c, 3000));
    }
}
