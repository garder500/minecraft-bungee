package win.vampyuyu.survival.data;

import java.util.List;

/**
 * The schema, versioned: {@code db/migrations/<name>.sql} applied in this order, each once. A new table or column is a
 * new file appended to {@link #ALL}; a shipped file is never edited (a server that already ran it would not see the edit).
 */
public final class Migrations {
    /** Every migration, oldest first. Version n is {@code ALL.get(n - 1)}. */
    static final List<String> ALL = List.of(
        "V001__base",
        "V002__quests",
        "V003__economy",
        "V004__island",
        "V005__utility",
        "V006__wings",
        "V007__menu",
        "V008__minions",
        "V009__skills",
        "V010__quarry",
        "V011__boss",
        "V012__dungeon",
        "V013__items",
        "V014__elements",
        "V015__dungeon_bosses",
        "V016__realm_resources",
        "V017__bastion",
        "V018__promotion");

    private Migrations() {}

    /** The latest schema version this jar knows. */
    public static int latest() {
        return ALL.size();
    }

    static void apply(Db db) {
        db.write(c -> {
            Db.update(c, "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            List<Integer> rows = Db.query(c, "SELECT version FROM schema_version", rs -> rs.getInt(1));
            int current = rows.isEmpty() ? 0 : rows.get(0);
            if (current > ALL.size()) {
                throw new IllegalStateException("The database schema is version " + current
                    + " but this plugin only knows " + ALL.size() + ": install the newer plugin");
            }
            for (int v = current; v < ALL.size(); v++) {
                String name = ALL.get(v);
                SqlScript.run(c, SqlScript.resource("db/migrations/" + name + ".sql"), name);
            }
            Db.update(c, "DELETE FROM schema_version");
            Db.update(c, "INSERT INTO schema_version (version) VALUES (?)", ALL.size());
            return null;
        });
    }
}
