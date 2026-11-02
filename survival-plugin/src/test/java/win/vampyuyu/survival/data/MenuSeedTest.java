package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static win.vampyuyu.survival.data.TestDb.count;

import org.junit.Test;

/** The menu seed against the former menu.yml (without its quests, which are the quests seeds): 17 commands with 38 aliases, 4 default zones, 4 zones, 7 sounds, 8 stat steps, 43 fairy souls, 39 single settings. */
public class MenuSeedTest {
    @Test
    public void theConvertedSeedKeepsTheCountsOfTheOldYaml() {
        try (Db db = TestDb.seeded()) {
            assertEquals(18, count(db, "SELECT COUNT(*) FROM menu_command"));
            assertEquals(39, count(db, "SELECT COUNT(*) FROM menu_command_alias"));
            assertEquals(4, count(db, "SELECT COUNT(*) FROM menu_default_zone"));
            assertEquals(4, count(db, "SELECT COUNT(*) FROM menu_zone"));
            assertEquals(7, count(db, "SELECT COUNT(*) FROM menu_sound"));
            assertEquals(8, count(db, "SELECT COUNT(*) FROM menu_fairy_stat"));
            assertEquals(43, count(db, "SELECT COUNT(*) FROM menu_fairy_soul"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM menu_warp"));
            assertEquals(46, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'menu.*'"));
            assertTrue(Settings.load(db, "menu").problems().toString(), Settings.load(db, "menu").problems().isEmpty());
        }
    }

    @Test
    public void theQuestSettingsAreThereAndTheQuestsAreNotDuplicated() {
        try (Db db = TestDb.seeded()) {
            Settings s = Settings.load(db, "menu");
            assertEquals(3, s.getInt("quests.daily.count", -1));
            assertEquals("04:00", s.getString("quests.daily.reset-time", ""));
            assertEquals(10, s.getList("quests.daily.ignore-coin-reasons").size());
            assertEquals(0, count(db, "SELECT COUNT(*) FROM setting WHERE id GLOB 'menu.quests.daily.pool*' OR id GLOB 'menu.quests.tutorial*'"));
            assertEquals(24, count(db, "SELECT COUNT(*) FROM quest WHERE pool = 'daily'"));
        }
    }
}
