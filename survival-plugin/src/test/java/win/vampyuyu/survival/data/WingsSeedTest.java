package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static win.vampyuyu.survival.data.TestDb.count;

import org.junit.Test;

/** The Wings seed against the former wings.yml: 10 levels with 30 stat values, 9 upgrade steps, 5 cores, no no-fly region, 45 single settings. */
public class WingsSeedTest {
    @Test
    public void theConvertedSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            assertEquals(10, count(db, "SELECT COUNT(*) FROM wings_level"));
            assertEquals(30, count(db, "SELECT COUNT(*) FROM wings_level_stat"));
            assertEquals(9, count(db, "SELECT COUNT(*) FROM wings_upgrade"));
            assertEquals(5, count(db, "SELECT COUNT(*) FROM wings_core"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM wings_region"));
            assertEquals(45, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'wings.*'"));
            assertTrue(Settings.load(db, "wings").problems().toString(), Settings.load(db, "wings").problems().isEmpty());
        }
    }

    @Test
    public void theValuesAreTheOnesOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            Settings s = Settings.load(db, "wings");
            assertEquals(10, s.getInt("max-level", -1));
            assertEquals(java.util.List.of("sb_hub", "sb_world", "sb_nether", "sb_end", "world"), s.getList("worlds.allowed"));
            assertEquals(java.util.List.of("sb_dungeon_"), s.getList("worlds.denied-prefixes"));
            assertEquals(java.util.List.of("ENERGY", "UNEQUIP", "WORLD", "COMBAT", "ZONE"), s.getList("emergency.reasons"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM wings_level WHERE level = 10 AND capacity_bonus = 500 AND fly_speed = 0.22 "
                + "AND trail_particle = 'FIREWORK' AND trail_count = 3 AND trail_spread IS NULL"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM wings_upgrade WHERE level = 9 AND core = 5 AND amount = 1 AND coins = 4000000"));
        }
    }
}
