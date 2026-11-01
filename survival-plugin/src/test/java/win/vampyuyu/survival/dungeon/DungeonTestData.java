package win.vampyuyu.survival.dungeon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;

/** Dungeon data for tests: the shipped seed, or an empty database filled by hand. */
final class DungeonTestData {
    private DungeonTestData() {}

    /** An in-memory database with the shipped dungeon seed applied. */
    static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.DUNGEON), null);
        return db;
    }

    /** An in-memory database with the schema and nothing else (every value takes its default). */
    static Db empty() {
        return Db.memory();
    }

    static void exec(Db db, String... statements) {
        for (String sql : statements) {
            db.write(c -> {
                try (var st = c.createStatement()) {
                    st.execute(sql);
                }
                return null;
            });
        }
    }

    static void setting(Db db, String path, String type, String value) {
        exec(db, "INSERT INTO setting (id, type, value) VALUES ('dungeon." + path + "', '" + type + "', '" + value + "')");
    }

    /** One {@code dungeon_floor} insert: floor 1 of the shipped file with {@code overrides} (column to SQL literal) applied. */
    static String floor(int number, Map<String, String> overrides) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("id", "'" + number + "'");
        v.put("number", String.valueOf(number));
        v.put("name", "'F" + number + "'");
        v.put("palette", "'stone_brick'");
        v.put("grid", "3");
        v.put("rooms", "8");
        v.put("puzzles", "1");
        v.put("traps", "0");
        v.put("secret_rooms", "1");
        v.put("mini_bosses", "0");
        v.put("shop", "0");
        v.put("fairy", "1");
        v.put("wither_doors", "0");
        v.put("loops", "0");
        v.put("hp", "1.0");
        v.put("damage", "1.0");
        v.put("mob_level", "3");
        v.put("par_seconds", "420");
        v.put("secrets_needed_percent", "40");
        v.put("reward", "1.0");
        v.put("min_combat_level", "0");
        v.putAll(overrides);
        return "INSERT INTO dungeon_floor (" + String.join(", ", v.keySet()) + ") VALUES (" + String.join(", ", v.values()) + ")";
    }

    static String grade(String id, int position, int minScore) {
        return "INSERT INTO dungeon_grade (id, position, min_score, coins, xp, rolls, chest) VALUES ('" + id + "', " + position + ", "
            + minScore + ", 100, 10, 1, 'Chest')";
    }
}
