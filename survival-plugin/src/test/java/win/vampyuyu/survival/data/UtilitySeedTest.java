package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static win.vampyuyu.survival.data.TestDb.count;

import org.junit.Test;

/** The utility seed against the former utility.yml: 6 portable blocks and 33 single settings (trash.keep-ids is a list). */
public class UtilitySeedTest {
    @Test
    public void theConvertedSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            assertEquals(6, count(db, "SELECT COUNT(*) FROM utility_portable"));
            assertEquals(33, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'utility.*'"));
            assertTrue(Settings.load(db, "utility").problems().toString(), Settings.load(db, "utility").problems().isEmpty());
        }
    }

    @Test
    public void theValuesAreTheOnesOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            Settings s = Settings.load(db, "utility");
            assertEquals(3, s.getInt("teleport.warmup-seconds", -1));
            assertEquals(600, s.getInt("rtp.cooldown-seconds", -1));
            assertEquals(java.util.List.of("wings"), s.getList("trash.keep-ids"));
            assertEquals("LEGENDARY", s.getString("trash.keep-rarity", ""));
            assertEquals(2, count(db, "SELECT COUNT(*) FROM utility_portable WHERE public = 1"));
        }
    }
}
