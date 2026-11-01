package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** The content database: schema, the jar seeds, and the rule that a panel edit survives a new seed. */
public class SeedsTest {
    private static int count(Db db, String sql) {
        return db.read(c -> Db.query(c, sql, rs -> rs.getInt(1)).get(0));
    }

    @Test
    public void everySeedLoadsIntoTheSchema() {
        try (Db db = Db.memory()) {
            assertEquals(ContentDomains.ALL.size(), Seeds.sync(db, ContentDomains.ALL, null).size());
            assertTrue(count(db, "SELECT COUNT(*) FROM quest WHERE pool = 'plan'") >= 250);
            assertTrue(count(db, "SELECT COUNT(*) FROM quest WHERE pool = 'daily'") > 5);
            assertEquals("a second sync with the same jar changes nothing", List.of(), Seeds.sync(db, ContentDomains.ALL, null));
        }
    }

    @Test
    public void aPanelEditSurvivesANewSeedAndResetBringsTheDefaultBack() {
        try (Db db = Db.memory()) {
            Seeds.sync(db, ContentDomains.ALL, null);
            db.write(c -> Db.update(c, "UPDATE quest SET amount = 99, origin = 'panel' WHERE id = 'q046'"));
            db.write(c -> Db.update(c, "DELETE FROM quest_requirement WHERE quest_id = 'q046'"));
            db.write(c -> Db.update(c, "UPDATE quest SET amount = 77 WHERE id = 'q047'")); // a seed row edited by hand
            db.write(c -> Db.update(c, "DELETE FROM seed_state")); // as if the jar had a new seed
            Seeds.sync(db, ContentDomains.ALL, null);
            assertEquals(99, count(db, "SELECT amount FROM quest WHERE id = 'q046'"));
            assertEquals("the panel row keeps its own requirements", 0,
                count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));
            assertEquals("a seed row follows the jar", 8, count(db, "SELECT amount FROM quest WHERE id = 'q047'"));

            assertTrue(Seeds.reset(db, ContentDomains.QUESTS, "quest", "q046"));
            assertEquals(8, count(db, "SELECT amount FROM quest WHERE id = 'q046'"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM quest WHERE origin = 'panel'"));
        }
    }

    @Test
    public void scriptsSplitOnSemicolonsOutsideQuotes() {
        assertEquals(List.of("INSERT INTO t VALUES ('a;b')", "SELECT 1"),
            SqlScript.statements("-- a comment; with a semicolon\nINSERT INTO t VALUES ('a;b');\nSELECT 1;"));
    }
}
