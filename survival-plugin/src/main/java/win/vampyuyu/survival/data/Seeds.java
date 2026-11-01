package win.vampyuyu.survival.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The default content shipped in the jar ({@code db/seed/<domain>.sql}, plain INSERTs) and how it reaches a server.
 *
 * <p>Every root row carries {@code origin}: {@code seed} (a copy of the jar's default) or {@code panel} (edited by
 * staff). When the jar's seed changes, the seed rows of that domain are replaced by the new ones; panel rows, and the
 * child rows that hang off them, are never touched. {@link #reset} puts one row back to the jar's default.
 */
public final class Seeds {
    /** A table whose rows belong to a root row through {@code fk}. */
    public record Child(String table, String fk) {}

    /**
     * A table edited as a whole row: {@code key} is its primary key, {@code filter} (SQL, may be null) narrows it when
     * two domains share the table.
     */
    public record Root(String table, String key, String filter, List<Child> children) {
        public Root {
            children = List.copyOf(children);
        }

        String where() {
            return filter == null ? "" : " AND (" + filter + ")";
        }
    }

    /** One seed file and the tables it fills. */
    public record Domain(String name, List<Root> roots) {
        public Domain {
            roots = List.copyOf(roots);
        }

        public Root root(String table) {
            return roots.stream().filter(r -> r.table().equals(table)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Domain " + name + " has no table " + table));
        }
    }

    private Seeds() {}

    /** Brings every domain whose jar seed changed up to date. Returns the names of the domains that were updated. */
    public static List<String> sync(Db db, List<Domain> domains, Logger log) {
        List<String> updated = new ArrayList<>();
        for (Domain domain : domains) {
            String sql = SqlScript.resource("db/seed/" + domain.name() + ".sql");
            String hash = hash(Migrations.latest() + "\n" + sql);
            List<String> stored = db.read(c -> Db.query(c, "SELECT hash FROM seed_state WHERE domain = ?",
                rs -> rs.getString(1), domain.name()));
            if (!stored.isEmpty() && stored.get(0).equals(hash)) {
                continue;
            }
            try (Db stage = stage(domain, sql)) {
                db.write(c -> {
                    for (Root root : domain.roots()) {
                        replaceSeedRows(c, stage, root);
                    }
                    Db.update(c, "INSERT INTO seed_state (domain, hash) VALUES (?, ?) "
                        + "ON CONFLICT(domain) DO UPDATE SET hash = excluded.hash", domain.name(), hash);
                    return null;
                });
            }
            updated.add(domain.name());
            if (log != null) {
                log.info("Skyblock data: '" + domain.name() + "' updated from the plugin's defaults");
            }
        }
        return updated;
    }

    /** Puts one root row (and its children) back to the jar's default. False when the jar has no such row. */
    public static boolean reset(Db db, Domain domain, String table, String key) {
        Root root = domain.root(table);
        try (Db stage = stage(domain, SqlScript.resource("db/seed/" + domain.name() + ".sql"))) {
            List<Object[]> rows = stage.read(c -> rows(c, "SELECT * FROM " + root.table() + " WHERE " + root.key() + " = ?"
                + root.where(), key));
            if (rows.isEmpty()) {
                return false;
            }
            db.write(c -> {
                deleteRoot(c, root, key);
                copy(c, stage, root.table(), "SELECT * FROM " + root.table() + " WHERE " + root.key() + " = ?" + root.where(), key);
                for (Child child : root.children()) {
                    copy(c, stage, child.table(), "SELECT * FROM " + child.table() + " WHERE " + child.fk() + " = ?", key);
                }
                return null;
            });
            return true;
        }
    }

    /** Removes one root row and its children (only inside the root's filter: another domain's row is left alone). */
    public static void deleteRoot(Connection c, Root root, String key) throws SQLException {
        if (Db.query(c, "SELECT 1 FROM " + root.table() + " WHERE " + root.key() + " = ?" + root.where(), rs -> 1, key).isEmpty()) {
            return;
        }
        for (Child child : root.children()) {
            Db.update(c, "DELETE FROM " + child.table() + " WHERE " + child.fk() + " = ?", key);
        }
        Db.update(c, "DELETE FROM " + root.table() + " WHERE " + root.key() + " = ?", key);
    }

    /** A fresh in-memory database holding only the jar's default content of {@code domain} (the caller closes it). */
    public static Db defaults(Domain domain) {
        return stage(domain, SqlScript.resource("db/seed/" + domain.name() + ".sql"));
    }

    private static Db stage(Domain domain, String sql) {
        Db stage = Db.memory();
        try {
            stage.write(c -> {
                SqlScript.run(c, sql, "seed " + domain.name());
                return null;
            });
            return stage;
        } catch (RuntimeException e) {
            stage.close();
            throw e;
        }
    }

    private static void replaceSeedRows(Connection c, Db stage, Root root) throws SQLException {
        String seedKeys = "SELECT " + root.key() + " FROM " + root.table() + " WHERE origin <> 'panel'" + root.where();
        for (Child child : root.children()) {
            Db.update(c, "DELETE FROM " + child.table() + " WHERE " + child.fk() + " IN (" + seedKeys + ")");
        }
        Db.update(c, "DELETE FROM " + root.table() + " WHERE origin <> 'panel'" + root.where());
        Set<String> panel = Set.copyOf(Db.query(c, "SELECT " + root.key() + " FROM " + root.table()
            + " WHERE origin = 'panel'" + root.where(), rs -> rs.getString(1)));
        copyExcept(c, stage, root.table(), "SELECT * FROM " + root.table() + " WHERE 1=1" + root.where(), root.key(), panel);
        for (Child child : root.children()) {
            String sql = "SELECT * FROM " + child.table() + " WHERE " + child.fk() + " IN (SELECT " + root.key() + " FROM "
                + root.table() + " WHERE 1=1" + root.where() + ")";
            copyExcept(c, stage, child.table(), sql, child.fk(), panel);
        }
    }

    private static void copy(Connection into, Db stage, String table, String select, Object... args) throws SQLException {
        copyExcept(into, stage, table, select, null, Set.of(), args);
    }

    /** Copies the rows {@code select} returns from the stage into {@code table}, skipping rows whose {@code keyColumn} is in {@code skip}. */
    private static void copyExcept(Connection into, Db stage, String table, String select, String keyColumn, Set<String> skip,
            Object... args) throws SQLException {
        record Batch(List<String> columns, List<Object[]> rows) {}
        Batch batch = stage.read(c -> {
            try (PreparedStatement ps = c.prepareStatement(select)) {
                Db.bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnName(i));
                    List<Object[]> rows = new ArrayList<>();
                    int keyIndex = keyColumn == null ? -1 : columns.indexOf(keyColumn);
                    while (rs.next()) {
                        if (keyIndex >= 0 && skip.contains(rs.getString(keyIndex + 1))) continue;
                        Object[] row = new Object[columns.size()];
                        for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
                        rows.add(row);
                    }
                    return new Batch(columns, rows);
                }
            }
        });
        if (batch.rows().isEmpty()) return;
        String sql = "INSERT INTO " + table + " (" + String.join(", ", batch.columns()) + ") VALUES ("
            + String.join(", ", java.util.Collections.nCopies(batch.columns().size(), "?")) + ")";
        try (PreparedStatement ps = into.prepareStatement(sql)) {
            for (Object[] row : batch.rows()) {
                Db.bind(ps, row);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<Object[]> rows(Connection c, String sql, Object... args) throws SQLException {
        return Db.query(c, sql, rs -> {
            Object[] row = new Object[rs.getMetaData().getColumnCount()];
            for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
            return row;
        }, args);
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
