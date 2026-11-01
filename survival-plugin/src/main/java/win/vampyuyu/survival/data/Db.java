package win.vampyuyu.survival.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The Skyblock content database: one SQLite file ({@code skyblock.db} in the data folder) holding every quest, item,
 * boss, recipe and tuning number. One connection, every access synchronized: content is read in bulk when a feature
 * enables or reloads and written rarely (panel edits), so a pool would only add failure modes.
 */
public final class Db implements AutoCloseable {
    private final Connection connection;

    private Db(Connection connection) {
        this.connection = connection;
    }

    /** Opens (creates) the file and brings its schema up to date. */
    public static Db open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Db db = new Db(DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath()));
            db.pragmas(true);
            Migrations.apply(db);
            return db;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open the Skyblock database " + file + ": " + e.getMessage(), e);
        }
    }

    /** A private in-memory database with the same schema (seed staging, tests). */
    public static Db memory() {
        try {
            Db db = new Db(DriverManager.getConnection("jdbc:sqlite::memory:"));
            db.pragmas(false);
            Migrations.apply(db);
            return db;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open an in-memory database: " + e.getMessage(), e);
        }
    }

    private void pragmas(boolean file) throws SQLException {
        try (Statement st = connection.createStatement()) {
            if (file) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
            st.execute("PRAGMA foreign_keys=ON");
        }
    }

    /** Work done with the connection; may throw SQLException. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    /** Runs {@code work} alone on the connection (no transaction: reads). */
    public synchronized <T> T read(Work<T> work) {
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        }
    }

    /** Runs {@code work} in one transaction: everything or nothing is written. */
    public synchronized <T> T write(Work<T> work) {
        try {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw new DataException(e.getMessage(), e);
        }
    }

    /** One row as read by {@link #query}. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Runs a select and maps every row. */
    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        }
    }

    /** Runs an insert/update/delete and returns the number of rows changed. */
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            ps.setObject(i + 1, a instanceof Boolean b ? (b ? 1 : 0) : a);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // closing on shutdown: nothing left to do
        }
    }

    /** A database error, unchecked so feature code does not have to declare SQLException. */
    public static final class DataException extends RuntimeException {
        public DataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
