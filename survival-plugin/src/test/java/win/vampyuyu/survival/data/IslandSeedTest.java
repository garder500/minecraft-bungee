package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static win.vampyuyu.survival.data.TestDb.count;

import org.junit.Test;

/** The island seed against the former island.yml: 4 worlds with 20 game rules, 9 upgrade steps, 5 templates, 3 chests, 45 level weights, 8 flags, 43 single settings. */
public class IslandSeedTest {
    @Test
    public void theConvertedSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            assertEquals(4, count(db, "SELECT COUNT(*) FROM island_world"));
            assertEquals(20, count(db, "SELECT COUNT(*) FROM island_world_gamerule"));
            assertEquals(9, count(db, "SELECT COUNT(*) FROM island_upgrade"));
            assertEquals(5, count(db, "SELECT COUNT(*) FROM island_template"));
            assertEquals(10, count(db, "SELECT COUNT(*) FROM island_template_line"));
            assertEquals(34, count(db, "SELECT COUNT(*) FROM island_template_item"));
            assertEquals(3, count(db, "SELECT COUNT(*) FROM island_chest"));
            assertEquals(11, count(db, "SELECT COUNT(*) FROM island_chest_item"));
            assertEquals(45, count(db, "SELECT COUNT(*) FROM island_level_weight"));
            assertEquals(8, count(db, "SELECT COUNT(*) FROM island_flag_default"));
            assertEquals(43, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'island.*'"));
            assertTrue(Settings.load(db, "island").problems().toString(), Settings.load(db, "island").problems().isEmpty());
        }
    }

    @Test
    public void theValuesAreTheOnesOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            Settings s = Settings.load(db, "island");
            assertEquals(400, s.getInt("grid.cell-size", -1));
            assertEquals(2, s.getInt("grid.edge-margin", -1));
            assertEquals(100.0, s.getDouble("level.divisor", -1), 0);
            assertEquals("You fell into the void and were pulled back to safety.", s.getString("void.message", ""));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM island_upgrade WHERE id = 'members.4' AND value = 12 AND cost = 500000"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM island_world_gamerule WHERE world = 'hub' AND rule = 'pvp' AND value = 'false'"));
        }
    }
}
