package win.vampyuyu.survival.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Runs the {@code .sql} files shipped in the jar (migrations and seeds): statements end with a {@code ;}. */
final class SqlScript {
    private SqlScript() {}

    /** The text of a jar resource, or an exception naming it. */
    static String resource(String path) {
        try (InputStream in = SqlScript.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    /** Splits on {@code ;} outside quotes; {@code --} comments run to the end of the line. */
    static List<String> statements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (!quoted && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                current.append('\n');
                continue;
            }
            if (ch == '\'') quoted = !quoted; // '' inside a string toggles twice: still correct
            if (!quoted && ch == ';') {
                String statement = current.toString().strip();
                if (!statement.isEmpty()) out.add(statement);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        String tail = current.toString().strip();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    static void run(Connection c, String sql, String name) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String statement : statements(sql)) {
                try {
                    st.execute(statement);
                } catch (SQLException e) {
                    String head = statement.length() > 160 ? statement.substring(0, 160) + "..." : statement;
                    throw new SQLException(name + ": " + e.getMessage() + " in: " + head, e);
                }
            }
        }
    }
}
