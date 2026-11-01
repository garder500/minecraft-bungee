package win.vampyuyu.survival.boss;

import java.util.List;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The boss data as shipped in the jar (the seeded content database), for tests. */
public final class BossTestData {
    private BossTestData() {}

    /** An in-memory database with the shipped boss seed applied. */
    public static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BOSS), null);
        return db;
    }

    /** The shipped boss configuration. */
    public static BossConfig shipped() {
        try (Db db = seeded()) {
            return BossConfig.load(db);
        }
    }
}
