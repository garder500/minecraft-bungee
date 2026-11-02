package win.vampyuyu.survival.economy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The converted seed keeps everything the former economy.yml held (counts measured on the YAML). */
public class EconomySeedTest {
    @Test
    public void theSeedKeepsTheCountsOfTheOldYaml() {
        EconomyConfig c = EconomyFixtures.shipped();
        assertEquals(List.of("farmer", "miner", "lumberjack", "fisherman", "mob_hunter", "alchemist", "adventurer",
            "weaponsmith", "builder", "rancher"), c.merchants().stream().map(EconomyConfig.Merchant::id).toList());
        assertEquals(288, c.offers().size());
        assertEquals(6, c.tiers().size());
        assertEquals("Starter", c.tiers().get(0).name());
        assertEquals("Prestige", c.tiers().get(5).name());
        assertEquals(Map.of(), c.pressurePrefixes());
        assertEquals(346, c.products().size());
        Map<String, Long> perCategory = c.products().stream()
            .collect(Collectors.groupingBy(EconomyConfig.Product::category, Collectors.counting()));
        assertEquals(Map.of("farming", 44L, "mining", 68L, "woods", 53L, "combat", 67L, "fishing", 49L, "misc", 65L), perCategory);
        assertEquals(List.of("sunstone", "starcore", "wings_core_*", "chrono_sand", "cinderheart", "heartwood", "soul_ash").stream().sorted().toList(), c.neverTraded());
        assertEquals(13, c.blockedMaterials().size());
        assertEquals(0, c.blockedItems().size());
        assertEquals(List.of("1", "6", "12", "24", "48"), c.settings().getList("auction.durations-hours"));
    }

    @Test
    public void spotValuesAreIdentical() {
        EconomyConfig c = EconomyFixtures.shipped();
        var s = c.settings();
        assertEquals(10_000_000_000L, s.getLong("currency.max-balance", 0));
        assertEquals(1.25, s.getDouble("bazaar.tax-percent", 0), 0);
        assertEquals(71_680, s.getInt("bazaar.max-amount", 0));
        assertEquals(0.2, s.getDouble("pressure.min-multiplier", 0), 0);
        assertEquals(50_000.0, c.tiers().get(0).capacity(), 0);
        assertEquals(0.6, c.tiers().get(2).rate(), 0);
        var wheat = c.offersOf("farmer").get(0);
        assertEquals("WHEAT", wheat.key());
        assertEquals(8.0, wheat.buy(), 0);
        assertEquals(2.0, wheat.sell(), 0);
        assertEquals("<green>Farmer", c.merchants().get(0).npcName());
        assertTrue(s.problems().isEmpty());
    }

    @Test
    public void aSecondSyncChangesNothing() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            assertEquals(List.of(), Seeds.sync(db, ContentDomains.ALL, null));
        }
    }
}
