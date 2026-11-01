package win.vampyuyu.survival.menu;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import win.vampyuyu.survival.data.Db;

/** Reads the quest plan and the daily pool from the content database ({@code quest*} tables). */
public final class QuestRepo {
    private QuestRepo() {}

    /** The whole catalog; a broken row is skipped and reported in {@link QuestCatalog#problems()}. */
    public static QuestCatalog load(Db db) {
        return db.read(QuestRepo::load);
    }

    static QuestCatalog load(Connection c) throws SQLException {
        List<String> problems = new ArrayList<>();
        Map<String, QuestCatalog.Category> categories = new LinkedHashMap<>();
        for (QuestCatalog.Category cat : Db.query(c, "SELECT id, name, description, main, icon, unlock FROM quest_category ORDER BY position",
                rs -> new QuestCatalog.Category(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1,
                    material(rs.getString(5)), rs.getString(6)))) {
            categories.put(cat.id(), cat);
        }
        Map<String, List<String>> requires = new HashMap<>();
        for (String[] r : Db.query(c, "SELECT quest_id, requires FROM quest_requirement ORDER BY quest_id, position",
                rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            requires.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        }
        Map<String, Map<String, Double>> xp = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT quest_id, skill, amount FROM quest_reward_xp ORDER BY quest_id, skill",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getDouble(3)})) {
            xp.computeIfAbsent((String) r[0], k -> new LinkedHashMap<>()).put(((String) r[1]).toUpperCase(Locale.ROOT), (Double) r[2]);
        }
        Map<String, Map<String, Integer>> items = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT quest_id, item, amount FROM quest_reward_item ORDER BY quest_id, item",
                rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3)})) {
            items.computeIfAbsent((String) r[0], k -> new LinkedHashMap<>()).put((String) r[1], (Integer) r[2]);
        }
        Map<String, QuestDef> plan = new LinkedHashMap<>();
        Map<String, QuestDef> daily = new LinkedHashMap<>();
        Db.query(c, "SELECT id, pool, category, name, description, type, target, amount, icon, reward_coins FROM quest "
            + "ORDER BY pool, position", rs -> {
                String id = rs.getString(1);
                try {
                    QuestType type = QuestType.valueOf(rs.getString(6).toUpperCase(Locale.ROOT));
                    QuestDef def = new QuestDef(id, rs.getString(4), rs.getString(5), type, rs.getString(7), rs.getInt(8),
                        material(rs.getString(9)), requires.getOrDefault(id, List.of()),
                        new QuestDef.Rewards(rs.getDouble(10), xp.getOrDefault(id, Map.of()), items.getOrDefault(id, Map.of())),
                        rs.getString(3));
                    (rs.getString(2).equals("daily") ? daily : plan).put(id, def);
                } catch (IllegalArgumentException e) {
                    problems.add("quest '" + id + "': " + e.getMessage());
                }
                return null;
            });
        return QuestCatalog.of(categories, plan, daily, problems);
    }

    private static Material material(String name) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || m.isAir() ? Material.PAPER : m;
    }
}
