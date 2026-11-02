package win.vampyuyu.survival.trades;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import win.vampyuyu.survival.api.Trade;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** Trade levels, the number of trades a player may hold, and the shipped seed (GDD 11.3, 11.4). */
public class TradeRulesTest {
    private final TradeConfig cfg = seeded();

    private static TradeConfig seeded() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            return TradeConfig.load(db);
        }
    }

    @Test
    public void theSeedLoadsWithTheGddNumbers() {
        assertTrue(cfg.problems().toString(), cfg.problems().isEmpty());
        assertEquals(0.85, cfg.forgeFactor(), 1e-9);
        assertEquals(0.90, cfg.minerEnergyFactor(), 1e-9);
        assertEquals(1.20, cfg.yieldFactor(), 1e-9);
        assertEquals(0.15, cfg.mendFraction(), 1e-9);
        assertEquals(30, cfg.overclockSeconds());
        assertEquals(2.0, cfg.overclockSpeed(), 1e-9);
    }

    @Test
    public void levelsFollowTheExperience() {
        assertEquals(1, TradeRules.level(0, cfg));
        assertEquals(1, TradeRules.level(99, cfg));
        assertEquals(2, TradeRules.level(100, cfg));
        assertEquals(5, TradeRules.level(4000, cfg));
        assertEquals(5, TradeRules.level(1_000_000, cfg));
        assertEquals(100, TradeRules.toNext(0, cfg));
        assertEquals(0, TradeRules.toNext(5000, cfg));
    }

    @Test
    public void oneTradeThenASecondAtMaster() {
        TradeData d = new TradeData();
        assertEquals(TradeRules.Choice.OK, TradeRules.choose(d, Trade.ARTISAN, cfg));
        assertEquals(TradeRules.Choice.ALREADY, TradeRules.choose(d, Trade.ARTISAN, cfg));
        assertEquals(TradeRules.Choice.FULL, TradeRules.choose(d, Trade.WARDEN, cfg));
        d.xp.put("artisan", 4000L);
        assertEquals(TradeRules.Choice.OK, TradeRules.choose(d, Trade.WARDEN, cfg));
        assertEquals(2, d.trades.size());
    }

    @Test
    public void aTradeYouDoNotHaveIsLevelZero() {
        TradeData d = new TradeData();
        d.xp.put("warden", 9999L);
        assertEquals(0, TradeRules.level(d, Trade.WARDEN, cfg));
    }

    @Test
    public void badThresholdsAreReportedAndNeverGrantLevels() {
        var s = win.vampyuyu.survival.data.Settings.empty("trades");
        TradeConfig empty = TradeConfig.parse(s);
        assertEquals(1, TradeRules.level(1_000_000, empty));
    }
}
