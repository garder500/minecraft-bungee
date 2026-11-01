package win.vampyuyu.survival.minions;

import java.util.function.Consumer;
import java.util.logging.Logger;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The minions configuration as tests need it: the jar's seed in an in-memory database, optionally edited. */
final class TestConfig {
    private TestConfig() {}

    /** A database holding the jar's seeds. */
    static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        return db;
    }

    static MinionConfig load() {
        return load(db -> {}, Logger.getLogger("test"));
    }

    /** The seeded configuration after {@code edit} changed the database; warnings go to {@code log}. */
    static MinionConfig load(Consumer<Db> edit, Logger log) {
        try (Db db = seeded()) {
            edit.accept(db);
            return MinionRepo.load(db, log);
        }
    }

    /** The configuration of a database with nothing in it after {@code fill}. */
    static MinionConfig loadEmpty(Consumer<Db> fill, Logger log) {
        try (Db db = Db.memory()) {
            fill.accept(db);
            return MinionRepo.load(db, log);
        }
    }

    static void sql(Db db, String sql, Object... args) {
        db.write(c -> Db.update(c, sql, args));
    }
}
