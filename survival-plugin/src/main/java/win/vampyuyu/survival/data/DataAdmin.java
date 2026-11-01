package win.vampyuyu.survival.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import win.vampyuyu.survival.data.Seeds.Child;
import win.vampyuyu.survival.data.Seeds.Domain;
import win.vampyuyu.survival.data.Seeds.Root;

/**
 * Staff editing of the content database (the panel's Skyblock data page). Fully generic: the domains, roots and children
 * come from {@link ContentDomains}, the columns from the SQLite schema ({@code PRAGMA table_info}), so a new domain
 * needs no code here. Pure JDK + Gson (no Bukkit): every method takes and returns plain maps and lists, so it is tested
 * on an in-memory database and served as JSON by {@code DataHttpApi}.
 *
 * <p>A write sets {@code origin = 'panel'} on the root row (so a new jar seed never overwrites it), replaces its child
 * rows, and leaves a {@code data_audit} row. A refused write rolls back completely and throws {@link Invalid} with a
 * message that names the column.
 */
public final class DataAdmin {
    /** A request the data cannot accept (unknown row, wrong type, constraint failed): the caller answers 400 or 404. */
    public static class Invalid extends IllegalArgumentException {
        private final int status;
        private final String column;

        public Invalid(String message, String column, int status) {
            super(message);
            this.column = column;
            this.status = status;
        }

        public Invalid(String message) {
            this(message, null, 400);
        }

        public int status() { return status; }

        /** The column the message is about, or null. */
        public String column() { return column; }
    }

    private static final int MAX_PAGE = 200;
    private static final int MAX_CHILD_ROWS = 2000;
    private static final Gson AUDIT_JSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String ORIGIN = "origin";

    private final Db db;
    private final Consumer<String> onChange;

    /** @param onChange told the domain name after every committed write (may be null) */
    public DataAdmin(Db db, Consumer<String> onChange) {
        this.db = db;
        this.onChange = onChange == null ? d -> {} : onChange;
    }

    // ------------------------------------------------------------------------------------------ schema

    /** Every domain with its roots and their children. */
    public List<Map<String, Object>> domains() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Domain d : ContentDomains.ALL) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.name());
            List<Map<String, Object>> roots = new ArrayList<>();
            for (Root r : d.roots()) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("table", r.table());
                rm.put("key", r.key());
                rm.put("filter", r.filter());
                rm.put("children", r.children().stream().map(ch -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("table", ch.table());
                    cm.put("fk", ch.fk());
                    return cm;
                }).toList());
                roots.add(rm);
            }
            m.put("roots", roots);
            out.add(m);
        }
        return out;
    }

    /** Columns of a root and of each of its children (name, type, kind, notnull, pk, default, checks, choices). */
    public Map<String, Object> schema(String domain, String table) {
        Root root = root(domain, table);
        return db.read(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("domain", domain);
            m.put("table", root.table());
            m.put("key", root.key());
            m.put("filter", root.filter());
            m.put("originColumn", hasColumn(columns(c, root.table()), ORIGIN) ? ORIGIN : null);
            m.put("columns", columns(c, root.table()).stream().map(Col::toMap).toList());
            m.put("tableChecks", tableChecks(c, root.table()));
            m.put("createSql", createSql(c, root.table()));
            List<Map<String, Object>> children = new ArrayList<>();
            for (Child ch : root.children()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("table", ch.table());
                cm.put("fk", ch.fk());
                cm.put("columns", columns(c, ch.table()).stream().map(Col::toMap).toList());
                cm.put("references", fkRefs(c, ch.table()));
                cm.put("createSql", createSql(c, ch.table()));
                children.add(cm);
            }
            m.put("children", children);
            return m;
        });
    }

    // ------------------------------------------------------------------------------------------ reading

    /**
     * One page of a root's rows. {@code q} matches any column (case-insensitive substring), {@code origin} is
     * {@code seed}, {@code panel} or null for both.
     */
    public Map<String, Object> rows(String domain, String table, String q, String origin, int offset, int limit) {
        Root root = root(domain, table);
        int lim = Math.max(1, Math.min(MAX_PAGE, limit));
        int off = Math.max(0, offset);
        return db.read(c -> {
            List<Col> cols = columns(c, root.table());
            StringBuilder where = new StringBuilder("1=1" + root.where());
            List<Object> args = new ArrayList<>();
            boolean hasOrigin = hasColumn(cols, ORIGIN);
            if (origin != null && !origin.isBlank() && hasOrigin) {
                if (!origin.equals("seed") && !origin.equals("panel")) throw new Invalid("origin must be seed or panel");
                where.append(" AND ").append(ORIGIN).append(" = ?");
                args.add(origin);
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<String> parts = new ArrayList<>();
                for (Col col : cols) {
                    parts.add("LOWER(CAST(" + quote(col.name) + " AS TEXT)) LIKE ? ESCAPE '\\'");
                    args.add(like);
                }
                where.append(" AND (").append(String.join(" OR ", parts)).append(")");
            }
            int total = Db.query(c, "SELECT COUNT(*) FROM " + root.table() + " WHERE " + where, rs -> rs.getInt(1), args.toArray()).get(0);
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(lim);
            pageArgs.add(off);
            List<Map<String, Object>> rows = Db.query(c, "SELECT * FROM " + root.table() + " WHERE " + where
                + " ORDER BY " + quote(root.key()) + " LIMIT ? OFFSET ?", rs -> rowMap(rs, cols), pageArgs.toArray());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", total);
            out.put("offset", off);
            out.put("limit", lim);
            out.put("rows", rows);
            return out;
        });
    }

    /** One root row with its child rows: {@code {row, origin, children: {table: [rows]}}}. */
    public Map<String, Object> get(String domain, String table, String key) {
        Root root = root(domain, table);
        Map<String, Object> out = db.read(c -> snapshot(c, root, key));
        if (out == null) throw new Invalid("No " + table + " row '" + key + "'", null, 404);
        return out;
    }

    // ------------------------------------------------------------------------------------------ writing

    /**
     * Creates or replaces a root row and its children in one transaction. {@code row} holds the root's columns
     * (the key may be left out), {@code children} maps a child table to its rows (the foreign key is filled in; a
     * missing {@code position} is the row's index). A child table absent from {@code children} is emptied.
     *
     * @return the stored row, as {@link #get}
     */
    public Map<String, Object> put(String domain, String table, String key, Map<String, Object> row,
            Map<String, List<Map<String, Object>>> children, String actor) {
        Root root = root(domain, table);
        requireKey(key);
        Map<String, Object> input = row == null ? Map.of() : row;
        Map<String, List<Map<String, Object>>> kids = children == null ? Map.of() : children;
        for (String name : kids.keySet()) {
            if (root.children().stream().noneMatch(ch -> ch.table().equals(name))) {
                throw new Invalid("Unknown child table '" + name + "' for " + table);
            }
        }
        Map<String, Object> result;
        try {
            result = db.write(c -> {
                List<Col> cols = columns(c, root.table());
                Col keyCol = cols.stream().filter(x -> x.name.equals(root.key())).findFirst()
                    .orElseThrow(() -> new Invalid("Table " + table + " has no column " + root.key()));
                Object given = input.get(root.key());
                if (given != null && !String.valueOf(given).equals(key) && !sameNumber(given, key)) {
                    throw new Invalid("The row's " + root.key() + " ('" + given + "') differs from the key '" + key + "'", root.key(), 400);
                }
                if (exists(c, root.table(), root.key(), key) && !existsIn(c, root, key)) {
                    throw new Invalid("'" + key + "' is a row of another data set (" + root.filter() + " does not match); it cannot be edited here",
                        root.key(), 400);
                }
                Map<String, Object> before = snapshot(c, root, key);
                writeRoot(c, root, cols, keyCol, key, input);
                if (!existsIn(c, root, key)) {
                    throw new Invalid("The row does not belong to this data set: it must match " + root.filter(), firstMentioned(cols, root.filter()), 400);
                }
                for (Child ch : root.children()) {
                    Db.update(c, "DELETE FROM " + ch.table() + " WHERE " + quote(ch.fk()) + " = ?", key);
                }
                for (Child ch : root.children()) {
                    List<Map<String, Object>> list = kids.getOrDefault(ch.table(), List.of());
                    if (list.size() > MAX_CHILD_ROWS) throw new Invalid("Too many rows in " + ch.table() + " (max " + MAX_CHILD_ROWS + ")");
                    List<Col> childCols = columns(c, ch.table());
                    for (int i = 0; i < list.size(); i++) {
                        writeChild(c, ch, childCols, key, list.get(i), i);
                    }
                }
                Map<String, Object> after = snapshot(c, root, key);
                audit(c, actor, "put", root.table(), key, before, after);
                return after;
            });
        } catch (Db.DataException e) {
            throw new Invalid(e.getMessage());
        }
        onChange.accept(domain);
        return result;
    }

    /** Deletes a root row and its children. A seed row comes back the next time the jar's seed changes (see reset). */
    public void delete(String domain, String table, String key, String actor) {
        Root root = root(domain, table);
        requireKey(key);
        try {
            db.write(c -> {
                if (!existsIn(c, root, key)) throw new Invalid("No " + table + " row '" + key + "'", null, 404);
                Map<String, Object> before = snapshot(c, root, key);
                try {
                    Seeds.deleteRoot(c, root, key);
                } catch (SQLException e) {
                    if (isForeignKey(e)) {
                        throw new Invalid(table + " '" + key + "' is still used by other data and cannot be deleted");
                    }
                    throw e;
                }
                audit(c, actor, "delete", root.table(), key, before, null);
                return null;
            });
        } catch (Db.DataException e) {
            throw new Invalid(clean(e.getMessage(), root.table(), null));
        }
        onChange.accept(domain);
    }

    /** Puts a row back to the jar's default (also refreshes its children). Fails when the jar has no such row. */
    public Map<String, Object> reset(String domain, String table, String key, String actor) {
        Domain dom = domain(domain);
        Root root = dom.root(table);
        requireKey(key);
        Map<String, Object> after;
        synchronized (db) { // the snapshot, the reset and the audit row stay together for other threads
            Map<String, Object> before = db.read(c -> snapshot(c, root, key));
            if (!Seeds.reset(db, dom, root.table(), key)) {
                throw new Invalid("The plugin ships no default for " + table + " '" + key + "'; delete the row instead", null, 404);
            }
            after = db.read(c -> snapshot(c, root, key));
            db.write(c -> {
                audit(c, actor, "reset", root.table(), key, before, after);
                return null;
            });
        }
        onChange.accept(domain);
        return after;
    }

    /** The audit trail, newest first. Every filter is optional. */
    public Map<String, Object> audit(String table, String key, String actor, int offset, int limit) {
        int lim = Math.max(1, Math.min(MAX_PAGE, limit));
        int off = Math.max(0, offset);
        return db.read(c -> {
            StringBuilder where = new StringBuilder("1=1");
            List<Object> args = new ArrayList<>();
            for (String[] f : new String[][] {{"table_name", table}, {"row_key", key}, {"actor", actor}}) {
                if (f[1] != null && !f[1].isBlank()) {
                    where.append(" AND ").append(f[0]).append(" = ?");
                    args.add(f[1]);
                }
            }
            int total = Db.query(c, "SELECT COUNT(*) FROM data_audit WHERE " + where, rs -> rs.getInt(1), args.toArray()).get(0);
            args.add(lim);
            args.add(off);
            List<Map<String, Object>> rows = Db.query(c, "SELECT id, at, actor, action, table_name, row_key, before, after FROM data_audit WHERE "
                + where + " ORDER BY id DESC LIMIT ? OFFSET ?", rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong(1));
                    m.put("at", rs.getLong(2));
                    m.put("actor", rs.getString(3));
                    m.put("action", rs.getString(4));
                    m.put("table", rs.getString(5));
                    m.put("key", rs.getString(6));
                    m.put("before", rs.getString(7));
                    m.put("after", rs.getString(8));
                    return m;
                }, args.toArray());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", total);
            out.put("offset", off);
            out.put("limit", lim);
            out.put("rows", rows);
            return out;
        });
    }

    // ------------------------------------------------------------------------------------------ write internals

    private void writeRoot(Connection c, Root root, List<Col> cols, Col keyCol, String key, Map<String, Object> input) throws SQLException {
        for (String name : input.keySet()) {
            if (cols.stream().noneMatch(x -> x.name.equals(name))) throw new Invalid("Unknown column '" + name + "' in " + root.table(), name, 400);
        }
        boolean update = exists(c, root.table(), root.key(), key);
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Col col : cols) {
            if (col.name.equals(ORIGIN)) continue;
            if (col == keyCol) {
                if (!update) {
                    names.add(col.name);
                    values.add(coerce(col, input.containsKey(col.name) ? input.get(col.name) : key, root.table()));
                }
                continue;
            }
            if (!input.containsKey(col.name)) {
                if (update) continue; // an edit that leaves a column out keeps it
                if (col.notNull && col.dflt == null && col.pk == 0) {
                    throw new Invalid(root.table() + "." + col.name + " is required", col.name, 400);
                }
                continue; // the column's default applies
            }
            Object v = input.get(col.name);
            if (v == null && col.notNull && col.dflt != null) continue;
            names.add(col.name);
            values.add(coerce(col, v, root.table()));
        }
        try {
            if (update) {
                List<String> sets = new ArrayList<>();
                for (String n : names) sets.add(quote(n) + " = ?");
                if (hasColumn(cols, ORIGIN)) sets.add(ORIGIN + " = 'panel'");
                if (!sets.isEmpty()) {
                    values.add(key);
                    Db.update(c, "UPDATE " + root.table() + " SET " + String.join(", ", sets) + " WHERE " + quote(root.key()) + " = ?",
                        values.toArray());
                }
            } else {
                if (hasColumn(cols, ORIGIN)) {
                    names.add(ORIGIN);
                    values.add("panel");
                }
                Db.update(c, "INSERT INTO " + root.table() + " (" + String.join(", ", names.stream().map(DataAdmin::quote).toList())
                    + ") VALUES (" + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")", values.toArray());
            }
        } catch (SQLException e) {
            throw new Invalid(clean(e.getMessage(), root.table(), cols), constraintColumn(e.getMessage(), cols), 400);
        }
    }

    private void writeChild(Connection c, Child ch, List<Col> cols, String key, Map<String, Object> input, int index) throws SQLException {
        String label = ch.table() + " row " + (index + 1);
        for (String name : input.keySet()) {
            if (cols.stream().noneMatch(x -> x.name.equals(name))) throw new Invalid("Unknown column '" + name + "' in " + label, name, 400);
        }
        List<String> names = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Col col : cols) {
            Object v;
            if (col.name.equals(ch.fk())) {
                v = key;
            } else if (input.containsKey(col.name)) {
                v = input.get(col.name);
                if (v == null && col.notNull && col.dflt != null) continue;
            } else if (col.name.equals("position") && col.notNull && col.dflt == null) {
                v = index;
            } else if (col.notNull && col.dflt == null) {
                throw new Invalid(label + ": " + col.name + " is required", col.name, 400);
            } else {
                continue;
            }
            names.add(col.name);
            values.add(col.name.equals(ch.fk()) ? coerce(col, v, ch.table()) : coerce(col, v, label));
        }
        try {
            Db.update(c, "INSERT INTO " + ch.table() + " (" + String.join(", ", names.stream().map(DataAdmin::quote).toList()) + ") VALUES ("
                + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")", values.toArray());
        } catch (SQLException e) {
            throw new Invalid(label + ": " + clean(e.getMessage(), ch.table(), cols), constraintColumn(e.getMessage(), cols), 400);
        }
    }

    /** A JSON value as the column's declared type; a value that does not fit is refused naming the column. */
    private static Object coerce(Col col, Object v, String where) {
        String what = where + "." + col.name;
        if (v == null) return null;
        switch (col.affinity) {
            case INTEGER -> {
                if (v instanceof Boolean b) return b ? 1 : 0;
                try {
                    BigDecimal d = v instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(v.toString().trim());
                    return d.longValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    throw new Invalid(what + " must be a whole number (got '" + v + "')", col.name, 400);
                }
            }
            case REAL -> {
                try {
                    double d = v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString().trim());
                    if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
                    return d;
                } catch (NumberFormatException e) {
                    throw new Invalid(what + " must be a number (got '" + v + "')", col.name, 400);
                }
            }
            default -> {
                if (v instanceof Boolean b) return b ? "true" : "false";
                if (v instanceof Number n) return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
                if (v instanceof String s) {
                    if (s.indexOf('\0') >= 0) throw new Invalid(what + " contains an invalid character", col.name, 400);
                    return s;
                }
                throw new Invalid(what + " must be text", col.name, 400);
            }
        }
    }

    private static boolean sameNumber(Object given, String key) {
        try {
            return given instanceof Number n && new BigDecimal(n.toString()).compareTo(new BigDecimal(key)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final Pattern NOT_NULL = Pattern.compile("NOT NULL constraint failed: (\\w+)\\.(\\w+)");
    private static final Pattern UNIQUE = Pattern.compile("UNIQUE constraint failed: ([\\w., ]+)");
    private static final Pattern CHECK = Pattern.compile("CHECK constraint failed: (.+?)\\)?$");

    /** An SQLite constraint message as one sentence about the column, without the driver's brackets. */
    static String clean(String message, String table, List<Col> cols) {
        String m = message == null ? "" : message;
        Matcher nn = NOT_NULL.matcher(m);
        if (nn.find()) return nn.group(1) + "." + nn.group(2) + " is required";
        Matcher un = UNIQUE.matcher(m);
        if (un.find()) return "Duplicate value: " + un.group(1).replaceAll("\\s+", " ").trim() + " must be unique";
        Matcher ck = CHECK.matcher(m);
        if (ck.find()) {
            String expr = ck.group(1).trim();
            String col = cols == null ? null : firstMentioned(cols, expr);
            return (col == null ? table : table + "." + col) + " is not accepted: " + expr + " is not satisfied";
        }
        if (m.contains("FOREIGN KEY")) return "A referenced row does not exist (" + table + ")";
        int paren = m.lastIndexOf('(');
        return m.startsWith("[SQLITE") && paren > 0 && m.endsWith(")") ? m.substring(paren + 1, m.length() - 1) : m;
    }

    private static String constraintColumn(String message, List<Col> cols) {
        if (message == null) return null;
        Matcher nn = NOT_NULL.matcher(message);
        if (nn.find()) return nn.group(2);
        Matcher un = UNIQUE.matcher(message);
        if (un.find()) {
            String first = un.group(1).split(",")[0].trim();
            return first.contains(".") ? first.substring(first.indexOf('.') + 1) : first;
        }
        Matcher ck = CHECK.matcher(message);
        return ck.find() ? firstMentioned(cols, ck.group(1)) : null;
    }

    private static String firstMentioned(List<Col> cols, String expr) {
        if (cols == null || expr == null) return null;
        for (Col col : cols) {
            if (Pattern.compile("\\b" + Pattern.quote(col.name) + "\\b").matcher(expr).find()) return col.name;
        }
        return null;
    }

    private static boolean isForeignKey(SQLException e) {
        return e.getMessage() != null && e.getMessage().contains("FOREIGN KEY");
    }

    private void audit(Connection c, String actor, String action, String table, String key, Map<String, Object> before,
            Map<String, Object> after) throws SQLException {
        String who = actor == null || actor.isBlank() ? "unknown" : actor.length() > 64 ? actor.substring(0, 64) : actor;
        Db.update(c, "INSERT INTO data_audit (at, actor, action, table_name, row_key, before, after) VALUES (?, ?, ?, ?, ?, ?, ?)",
            System.currentTimeMillis(), who, action, table, key,
            before == null ? null : AUDIT_JSON.toJson(before), after == null ? null : AUDIT_JSON.toJson(after));
    }

    // ------------------------------------------------------------------------------------------ reading internals

    private Map<String, Object> snapshot(Connection c, Root root, String key) throws SQLException {
        List<Col> cols = columns(c, root.table());
        List<Map<String, Object>> rows = Db.query(c, "SELECT * FROM " + root.table() + " WHERE " + quote(root.key()) + " = ?" + root.where(),
            rs -> rowMap(rs, cols), key);
        if (rows.isEmpty()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("row", rows.get(0));
        out.put("origin", hasColumn(cols, ORIGIN) ? rows.get(0).get(ORIGIN) : null);
        Map<String, Object> kids = new LinkedHashMap<>();
        for (Child ch : root.children()) {
            List<Col> childCols = columns(c, ch.table());
            String order = childCols.stream().anyMatch(x -> x.name.equals("position")) ? " ORDER BY position, rowid" : " ORDER BY rowid";
            kids.put(ch.table(), Db.query(c, "SELECT * FROM " + ch.table() + " WHERE " + quote(ch.fk()) + " = ?" + order,
                rs -> rowMap(rs, childCols), key));
        }
        out.put("children", kids);
        return out;
    }

    private static Map<String, Object> rowMap(java.sql.ResultSet rs, List<Col> cols) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i++) {
            m.put(cols.get(i).name, rs.getObject(i + 1));
        }
        return m;
    }

    private static boolean exists(Connection c, String table, String keyCol, String key) throws SQLException {
        return !Db.query(c, "SELECT 1 FROM " + table + " WHERE " + quote(keyCol) + " = ?", rs -> 1, key).isEmpty();
    }

    private static boolean existsIn(Connection c, Root root, String key) throws SQLException {
        return !Db.query(c, "SELECT 1 FROM " + root.table() + " WHERE " + quote(root.key()) + " = ?" + root.where(), rs -> 1, key).isEmpty();
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 256 || key.indexOf('\0') >= 0) throw new Invalid("Invalid row key");
    }

    private static Domain domain(String name) {
        return ContentDomains.ALL.stream().filter(d -> d.name().equals(name)).findFirst()
            .orElseThrow(() -> new Invalid("Unknown data domain '" + name + "'", null, 404));
    }

    private static Root root(String domain, String table) {
        Domain d = domain(domain);
        return d.roots().stream().filter(r -> r.table().equals(table)).findFirst()
            .orElseThrow(() -> new Invalid("Domain " + domain + " has no table '" + table + "'", null, 404));
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    // ------------------------------------------------------------------------------------------ schema internals

    enum Affinity { INTEGER, REAL, TEXT }

    /** One column as SQLite describes it, plus what the CHECK constraints say about it. */
    static final class Col {
        final String name;
        final String type;
        final boolean notNull;
        final int pk;
        final String dflt;
        final Affinity affinity;
        final List<String> checks = new ArrayList<>();
        List<String> choices;
        boolean bool;

        Col(String name, String type, boolean notNull, int pk, String dflt) {
            this.name = name;
            this.type = type;
            this.notNull = notNull;
            this.pk = pk;
            this.dflt = dflt;
            String t = type.toUpperCase(Locale.ROOT);
            this.affinity = t.contains("INT") || t.contains("BOOL") ? Affinity.INTEGER
                : t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") || t.contains("NUM") ? Affinity.REAL : Affinity.TEXT;
        }

        String kind() {
            return bool || type.toUpperCase(Locale.ROOT).contains("BOOL") ? "bool" : affinity == Affinity.INTEGER ? "int"
                : affinity == Affinity.REAL ? "real" : "text";
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("type", type);
            m.put("kind", kind());
            m.put("notnull", notNull);
            m.put("pk", pk);
            m.put("default", dflt);
            m.put("checks", checks);
            m.put("choices", choices);
            return m;
        }
    }

    private static boolean hasColumn(List<Col> cols, String name) {
        return cols.stream().anyMatch(x -> x.name.equals(name));
    }

    private static List<Col> columns(Connection c, String table) throws SQLException {
        List<Col> cols = Db.query(c, "SELECT name, type, \"notnull\", pk, dflt_value FROM pragma_table_info(?)",
            rs -> new Col(rs.getString(1), rs.getString(2), rs.getInt(3) == 1, rs.getInt(4), rs.getString(5)), table);
        if (cols.isEmpty()) throw new Invalid("Table " + table + " does not exist");
        for (String check : checkExpressions(createSql(c, table))) {
            for (Col col : cols) {
                if (!Pattern.compile("\\b" + Pattern.quote(col.name) + "\\b").matcher(check).find()) continue;
                col.checks.add(check);
                Matcher in = Pattern.compile("^\\s*\\(?\\s*" + Pattern.quote(col.name) + "\\s+IN\\s*\\(([^)]*)\\)\\s*\\)?\\s*$", Pattern.CASE_INSENSITIVE).matcher(check);
                if (in.matches()) {
                    List<String> values = new ArrayList<>();
                    for (String part : in.group(1).split(",")) values.add(part.trim().replaceAll("^'|'$", ""));
                    if (col.affinity == Affinity.INTEGER && values.equals(List.of("0", "1"))) col.bool = true;
                    else if (!ORIGIN.equals(col.name)) col.choices = values;
                }
            }
        }
        return cols;
    }

    private static List<String> tableChecks(Connection c, String table) throws SQLException {
        List<String> out = new ArrayList<>();
        List<Col> cols = Db.query(c, "SELECT name, type, \"notnull\", pk, dflt_value FROM pragma_table_info(?)",
            rs -> new Col(rs.getString(1), rs.getString(2), rs.getInt(3) == 1, rs.getInt(4), rs.getString(5)), table);
        for (String check : checkExpressions(createSql(c, table))) {
            if (cols.stream().filter(col -> Pattern.compile("\\b" + Pattern.quote(col.name) + "\\b").matcher(check).find()).count() > 1) {
                out.add(check);
            }
        }
        return out;
    }

    private static String createSql(Connection c, String table) throws SQLException {
        List<String> sql = Db.query(c, "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", rs -> rs.getString(1), table);
        return sql.isEmpty() || sql.get(0) == null ? "" : sql.get(0);
    }

    private static List<Map<String, Object>> fkRefs(Connection c, String table) throws SQLException {
        return Db.query(c, "SELECT \"from\", \"table\", \"to\", on_delete FROM pragma_foreign_key_list(?)", rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("column", rs.getString(1));
            m.put("table", rs.getString(2));
            m.put("to", rs.getString(3));
            m.put("onDelete", rs.getString(4));
            return m;
        }, table);
    }

    /** The expression of every {@code CHECK (...)} in a CREATE TABLE, comments and string literals respected. */
    static List<String> checkExpressions(String sql) {
        String s = stripComments(sql);
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\bCHECK\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(s);
        int from = 0;
        while (m.find(from)) {
            int start = m.end(); // just after the opening parenthesis
            int depth = 1;
            int i = start;
            boolean quoted = false;
            for (; i < s.length() && depth > 0; i++) {
                char ch = s.charAt(i);
                if (ch == '\'') quoted = !quoted;
                else if (!quoted && ch == '(') depth++;
                else if (!quoted && ch == ')') depth--;
            }
            if (depth != 0) break;
            out.add(s.substring(start, i - 1).trim().replaceAll("\\s+", " "));
            from = i;
        }
        return out;
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') quoted = !quoted;
            if (!quoted && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                out.append('\n');
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }
}
