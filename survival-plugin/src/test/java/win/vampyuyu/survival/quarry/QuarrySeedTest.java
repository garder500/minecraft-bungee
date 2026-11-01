package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.Test;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The Quarry seed keeps what the old quarry.yml had, plus the pity balance pass and the ring ores. */
public class QuarrySeedTest {
    private static final Random NEVER = new Random() {
        @Override
        public double nextDouble() {
            return 0.999999;
        }
    };

    private static int count(Db db, String sql) {
        return db.read(c -> Db.query(c, sql, rs -> rs.getInt(1)).get(0));
    }

    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        return db;
    }

    @Test
    public void theSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = seeded()) {
            // 9 cobble ores of the old file + Frostium, Sunstone and quartz of the ring generators
            assertEquals(9, count(db, "SELECT COUNT(*) FROM ore_roll WHERE source = 'cobble'"));
            assertEquals(3, count(db, "SELECT COUNT(*) FROM ore_roll WHERE source <> 'cobble'"));
            assertEquals(12 * 4, count(db, "SELECT COUNT(*) FROM ore_roll_chance"));
            assertEquals(66, count(db, "SELECT COUNT(*) FROM forge_recipe"));
            assertEquals(176, count(db, "SELECT COUNT(*) FROM forge_recipe_ingredient"));
            assertEquals(3, count(db, "SELECT COUNT(*) FROM forge_block_ingredient"));
            assertEquals(18, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'quarry.*'"));
        }
    }

    @Test
    public void theRepoBuildsTheConfig() {
        try (Db db = seeded()) {
            QuarryConfig config = QuarryRepo.load(db, Logger.getAnonymousLogger());
            assertEquals(4, config.tiers);
            assertEquals(66, config.recipes.size());
            assertEquals(3, config.forgeSlots);
            assertEquals(3, config.forgeBlockRecipe.size());
            assertEquals(2, config.recipes.get("bronze_core").cost().size());
            assertEquals(10, config.machines.generatorOutput, 0);
            assertEquals(20, config.machines.miner2Consume, 0);
            assertEquals(9, config.roller.ores().size());
            assertEquals(3, config.outputs.ores().size());
        }
    }

    @Test
    public void generatorOresHaveTheNewChancesAndPity() {
        try (Db db = seeded()) {
            Map<String, OreRoller.Ore> ores = new HashMap<>();
            QuarryRepo.load(db, Logger.getAnonymousLogger()).roller.ores().forEach(o -> ores.put(o.id(), o));
            assertArrayEquals(new double[] {2.25, 2.25, 1.5, 0.75}, ores.get("bronzite").chance(), 1e-9);
            assertEquals(300, ores.get("bronzite").pity());
            assertArrayEquals(new double[] {0, 0.3, 0.3, 0.3}, ores.get("diamond").chance(), 1e-9);
            assertEquals(1500, ores.get("diamond").pity());
            assertArrayEquals(new double[] {0, 0, 0.1, 0.1}, ores.get("ancient_debris").chance(), 1e-9);
            assertEquals(5000, ores.get("ancient_debris").pity());
            assertArrayEquals(new double[] {0, 0, 0, 0.03}, ores.get("voidglass").chance(), 1e-9);
            assertEquals(15000, ores.get("voidglass").pity());
            assertArrayEquals("untouched", new double[] {10, 10, 8, 6}, ores.get("iron_nugget").chance(), 1e-9);
        }
    }

    @Test
    public void frostiumAndSunstoneAreRingOresWithAPity() {
        try (Db db = seeded()) {
            Map<String, OreRoller.Ore> rings = new HashMap<>();
            QuarryRepo.load(db, Logger.getAnonymousLogger()).outputs.ores().forEach(o -> rings.put(o.id(), o));
            assertEquals("frost", rings.get("frostium").source());
            assertEquals(3.0, rings.get("frostium").at(1), 0);
            assertEquals(60, rings.get("frostium").pity());
            assertEquals("ember", rings.get("sunstone").source());
            assertEquals(2.0, rings.get("sunstone").at(4), 0);
            assertEquals(90, rings.get("sunstone").pity());
            assertEquals(java.util.Set.of("NETHERRACK"), rings.get("quartz").blocks());
        }
    }

    @Test
    public void frostiumIsGuaranteedByTheSixtiethFrostBlockWithoutLuck() {
        try (Db db = seeded()) {
            OreRoller rings = QuarryRepo.load(db, Logger.getAnonymousLogger()).outputs;
            Map<String, Integer> counters = new HashMap<>();
            for (int i = 1; i < 60; i++) {
                assertTrue("block " + i, rings.rollOutput("frost", "SNOW_BLOCK", 1, counters, NEVER).drops().isEmpty());
            }
            OreRoller.Roll sixtieth = rings.rollOutput("frost", "PACKED_ICE", 1, counters, NEVER);
            assertTrue(sixtieth.pity());
            assertEquals("frostium", sixtieth.drops().get(0).ore());
            assertEquals(0, (int) counters.get("frostium"));
            assertTrue("the next block is not forced", rings.rollOutput("frost", "SNOW_BLOCK", 1, counters, NEVER).drops().isEmpty());
            assertFalse("Ember blocks never count for Frostium", counters.containsKey("sunstone"));
        }
    }

    @Test
    public void sunstoneIsGuaranteedByTheNinetiethEmberBlockAndQuartzIsNetherrackOnly() {
        try (Db db = seeded()) {
            OreRoller rings = QuarryRepo.load(db, Logger.getAnonymousLogger()).outputs;
            Map<String, Integer> counters = new HashMap<>();
            for (int i = 1; i < 90; i++) {
                assertTrue("block " + i, rings.rollOutput("ember", "SOUL_SAND", 1, counters, NEVER).drops().isEmpty());
            }
            assertEquals("sunstone", rings.rollOutput("ember", "SOUL_SAND", 1, counters, NEVER).drops().get(0).ore());
            assertFalse("no quartz counter for soul sand", counters.containsKey("quartz"));
        }
    }
}
