package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static win.vampyuyu.survival.data.TestDb.count;

import org.junit.Test;

/** The plugin level settings (the former config.yml): the debug flag and the four feature toggles. */
public class CoreSeedTest {
    @Test
    public void theConvertedSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {

            assertEquals(5, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'core.*'"));
            assertTrue(Settings.load(db, "core").problems().toString(), Settings.load(db, "core").problems().isEmpty());
        }
    }

    @Test
    public void everyFeatureIsOnAndDebugIsOff() {
        try (Db db = TestDb.seeded()) {
            Settings s = Settings.load(db, "core");
            assertEquals(false, s.getBoolean("debug", true));
            for (String id : new String[] {"utility", "island", "wings", "menu"}) {
                assertEquals(id, true, s.getBoolean("features." + id + ".enabled", false));
            }
        }
    }
}
