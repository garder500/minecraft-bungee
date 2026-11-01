package win.vampyuyu.survival.data;

import java.sql.SQLException;

/** Test helpers: an in-memory content database filled with the jar's seeds, and one-line edits of it. */
public final class TestDb {
    private TestDb() {}

    /** A fresh in-memory database with every seed of the jar applied. */
    public static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        return db;
    }

    /** Runs one INSERT/UPDATE/DELETE (a setting changed, a row removed...). */
    public static void exec(Db db, String sql, Object... args) {
        db.write(c -> Db.update(c, sql, args));
    }

    /** Sets a scalar setting row, creating it when missing. */
    public static void set(Db db, String id, String type, String value) {
        exec(db, "INSERT INTO setting (id, type, value) VALUES (?, ?, ?) ON CONFLICT(id) DO UPDATE SET value = excluded.value",
            id, type, value);
    }

    /** Replaces a list setting. */
    public static void setList(Db db, String id, String... values) {
        exec(db, "DELETE FROM setting_item WHERE setting_id = ?", id);
        exec(db, "INSERT INTO setting (id, type, value) VALUES (?, 'list', '') ON CONFLICT(id) DO NOTHING", id);
        for (int i = 0; i < values.length; i++) {
            exec(db, "INSERT INTO setting_item (setting_id, position, value) VALUES (?, ?, ?)", id, i, values[i]);
        }
    }

    /** The first column of every row a select returns. */
    public static java.util.List<String> strings(Db db, String sql, Object... args) {
        return db.read(c -> Db.query(c, sql, rs -> rs.getString(1), args));
    }

    public static int count(Db db, String sql, Object... args) {
        try {
            return db.read(c -> Db.query(c, sql, rs -> rs.getInt(1), args).get(0));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException) {
                throw new AssertionError(sql, e);
            }
            throw e;
        }
    }
}
