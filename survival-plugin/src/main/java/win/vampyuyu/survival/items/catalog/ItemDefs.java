package win.vampyuyu.survival.items.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import win.vampyuyu.survival.api.Element;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.items.gear.Keys;

/**
 * Reads the {@code item_def} tables into {@link CustomItemDef}s. Each feature registers the rows of its own owner
 * ({@code items}, {@code quarry}, {@code boss}); a bad row is skipped and reported, the others still load.
 */
public final class ItemDefs {
    private ItemDefs() {}

    /** Every definition of {@code owner} in position order. Problems (unknown material, bad stat...) are appended to {@code problems}. */
    public static List<CustomItemDef> load(Db db, String owner, List<String> problems) {
        return db.read(c -> {
            Map<String, Map<Stat, Double>> stats = new LinkedHashMap<>();
            Db.query(c, "SELECT s.item_id, s.stat, s.value FROM item_stat s JOIN item_def d ON d.id = s.item_id "
                + "WHERE d.owner = ? ORDER BY s.item_id, s.stat", rs -> {
                    Stat.Converted[] converted = Stat.resolve(rs.getString(2)).toArray(new Stat.Converted[0]);
                    if (converted.length == 0 && !Stat.known(rs.getString(2))) {
                        problems.add(rs.getString(1) + ": unknown stat " + rs.getString(2));
                    }
                    for (Stat.Converted conv : converted) {
                        stats.computeIfAbsent(rs.getString(1), k -> new java.util.EnumMap<>(Stat.class))
                            .merge(conv.stat(), rs.getDouble(3) * conv.factor(), Double::sum);
                    }
                    return null;
                }, owner);
            Map<String, List<String>> abilities = grouped(c, "item_ability", "ability", "position", owner);
            Map<String, List<String>> lore = grouped(c, "item_lore", "line", "position", owner);
            Map<String, List<String[]>> pdc = new LinkedHashMap<>();
            Db.query(c, "SELECT p.item_id, p.key, p.kind, p.value FROM item_pdc p JOIN item_def d ON d.id = p.item_id "
                + "WHERE d.owner = ? ORDER BY p.item_id, p.key", rs -> {
                    pdc.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new String[] {rs.getString(2), rs.getString(3), rs.getString(4)});
                    return null;
                }, owner);
            List<CustomItemDef> out = new ArrayList<>();
            Db.query(c, "SELECT * FROM item_def WHERE owner = ? ORDER BY position", rs -> {
                String id = rs.getString("id");
                try {
                    out.add(build(rs, stats.getOrDefault(id, Map.of()), abilities.getOrDefault(id, List.of()),
                        lore.getOrDefault(id, List.of()), pdc.getOrDefault(id, List.of())));
                } catch (RuntimeException e) {
                    problems.add(id + ": " + e.getMessage());
                }
                return null;
            }, owner);
            return out;
        });
    }

    private static Map<String, List<String>> grouped(java.sql.Connection c, String table, String column, String order, String owner)
            throws java.sql.SQLException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Db.query(c, "SELECT t.item_id, t." + column + " FROM " + table + " t JOIN item_def d ON d.id = t.item_id "
            + "WHERE d.owner = ? ORDER BY t.item_id, t." + order, rs -> {
                out.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                return null;
            }, owner);
        return out;
    }

    private static CustomItemDef build(java.sql.ResultSet rs, Map<Stat, Double> stats, List<String> abilities, List<String> lore,
            List<String[]> pdc) throws java.sql.SQLException {
        Material material = Material.matchMaterial(rs.getString("material"));
        if (material == null) {
            throw new IllegalArgumentException("unknown material " + rs.getString("material"));
        }
        Rarity rarity = Rarity.valueOf(rs.getString("rarity").toUpperCase(Locale.ROOT));
        CustomItemDef.Builder builder = CustomItemDef.builder(rs.getString("id"), rs.getString("name"), rarity, material)
            .unbreakable(rs.getInt("unbreakable") == 1).hideFlags(rs.getInt("hide_flags") == 1).glint(rs.getInt("glint") == 1);
        if (rs.getString("item_model") != null) {
            builder.itemModel(rs.getString("item_model"));
        }
        if (rs.getString("type_label") != null) {
            builder.type(rs.getString("type_label"));
        }
        if (rs.getObject("max_stack") != null) {
            builder.maxStackSize(rs.getInt("max_stack"));
        }
        if (rs.getString("equip_slot") != null) {
            builder.equippable(EquipmentSlot.valueOf(rs.getString("equip_slot")), rs.getString("equip_asset"));
        }
        if (rs.getObject("damage") != null) {
            builder.pdc(Keys.DAMAGE, rs.getDouble("damage"));
        }
        if (!abilities.isEmpty()) {
            builder.pdc(Keys.ABILITIES, String.join(",", abilities));
        }
        for (String[] row : pdc) {
            switch (row[1]) {
                case "string" -> builder.pdc(row[0], row[2]);
                case "long" -> builder.pdc(row[0], Long.parseLong(row[2]));
                default -> builder.pdc(row[0], Double.parseDouble(row[2]));
            }
        }
        if (rs.getString("element") != null) {
            Element element = Element.parse(rs.getString("element"));
            if (element == null) {
                throw new IllegalArgumentException("unknown element " + rs.getString("element"));
            }
            builder.element(element);
        }
        if (rs.getObject("tier") != null) {
            builder.tier(rs.getInt("tier"));
        }
        stats.forEach(builder::stat);
        builder.lore(lore.toArray(new String[0]));
        return builder.build();
    }
}
