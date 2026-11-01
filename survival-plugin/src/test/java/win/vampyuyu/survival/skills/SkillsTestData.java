package win.vampyuyu.survival.skills;

import java.util.function.Consumer;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** The skills configuration as tests need it: the jar's seed in an in-memory database, optionally edited. */
final class SkillsTestData {
    private SkillsTestData() {}

    /** A database holding the jar's seeds. */
    static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        return db;
    }

    static SkillsConfig load() {
        return load(db -> {});
    }

    /** The seeded configuration after {@code edit} changed the database. */
    static SkillsConfig load(Consumer<Db> edit) {
        try (Db db = seeded()) {
            edit.accept(db);
            return SkillsRepo.load(db);
        }
    }

    /** The configuration of a database with nothing in it (every value is its default) after {@code fill}. */
    static SkillsConfig loadEmpty(Consumer<Db> fill) {
        try (Db db = Db.memory()) {
            fill.accept(db);
            return SkillsRepo.load(db);
        }
    }

    static void sql(Db db, String sql, Object... args) {
        db.write(c -> Db.update(c, sql, args));
    }

    static void setting(Db db, String path, String value) {
        sql(db, "UPDATE setting SET value = ?, origin = 'panel' WHERE id = ?", value, "skills." + path);
    }
}
