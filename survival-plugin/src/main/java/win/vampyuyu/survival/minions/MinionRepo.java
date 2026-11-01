package win.vampyuyu.survival.minions;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.minions.MinionConfig.Drop;
import win.vampyuyu.survival.minions.MinionConfig.Effect;
import win.vampyuyu.survival.minions.MinionConfig.Fuel;
import win.vampyuyu.survival.minions.MinionConfig.ItemRecipe;
import win.vampyuyu.survival.minions.MinionConfig.Kind;
import win.vampyuyu.survival.minions.MinionConfig.Recipe;
import win.vampyuyu.survival.minions.MinionConfig.Skin;
import win.vampyuyu.survival.minions.MinionConfig.Type;
import win.vampyuyu.survival.minions.MinionConfig.Upgrade;

/**
 * Reads the minions tuning from the content database ({@code minion_*} tables and the {@code minions.*} settings) into a
 * {@link MinionConfig}. A bad row is skipped with a warning, so one typo never disables the feature.
 */
public final class MinionRepo {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final int MAX_TIER = MinionConfig.MAX_TIER;

    private MinionRepo() {}

    /** The whole configuration. */
    public static MinionConfig load(Db db, Logger log) {
        Settings settings = Settings.load(db, "minions");
        settings.problems().forEach(p -> log.warning("minions data: " + p));
        return db.read(c -> load(c, settings, log));
    }

    private static MinionConfig load(Connection c, Settings s, Logger log) throws SQLException {
        MinionConfig out = new MinionConfig();
        readSettings(out, s);
        readTiers(c, out, log);
        for (String[] row : Db.query(c, "SELECT id, result FROM minion_smelt ORDER BY position",
                rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            Material from = Material.matchMaterial(row[0]);
            Material to = Material.matchMaterial(row[1]);
            if (from == null || to == null) {
                log.warning("minions data: bad smelt entry " + row[0]);
            } else {
                out.smelt.put(from, to);
            }
        }
        readTypes(c, out, log);
        readFuels(c, out, log);
        readUpgrades(c, out, log);
        readSkins(c, out, log);
        readItemRecipes(c, out, log);
        return out;
    }

    private static void readSettings(MinionConfig c, Settings s) {
        c.tickTicks = Math.max(1, s.getInt("tick_ticks", c.tickTicks));
        c.visualRange = Math.max(4, s.getDouble("visual_range", c.visualRange));
        c.scanSeconds = Math.max(1, s.getInt("scan_seconds", c.scanSeconds));
        c.maxCatchupHours = Math.max(0, s.getDouble("max_catchup_hours", c.maxCatchupHours));
        c.islandCheckSeconds = Math.max(1, s.getInt("island_check_seconds", c.islandCheckSeconds));
        c.islandMissingHours = Math.max(0.01, s.getDouble("island_missing_hours", c.islandMissingHours));
        c.memberCollect = s.getBoolean("member_collect", c.memberCollect);
        c.guiClickMillis = Math.max(0, s.getInt("gui_click_millis", c.guiClickMillis));
        c.baseSlots = Math.max(0, s.getInt("base_slots", c.baseSlots));
        c.maxSlots = Math.max(c.baseSlots, s.getInt("max_slots", c.maxSlots));
        List<Integer> thresholds = new ArrayList<>();
        for (String v : s.getList("slot_thresholds")) {
            try {
                thresholds.add(Integer.parseInt(v.trim()));
            } catch (NumberFormatException ignored) {
                // like the old getIntegerList: an entry that is not a number is dropped
            }
        }
        if (!thresholds.isEmpty()) {
            Collections.sort(thresholds);
            c.slotThresholds = List.copyOf(thresholds);
        }
        c.perIslandCap = Math.max(1, s.getInt("per_island_cap", c.perIslandCap));
        c.scale = Math.max(0.2, Math.min(2.0, s.getDouble("scale", c.scale)));
        c.compactRatio = Math.max(2, s.getInt("compact_ratio", c.compactRatio));
        c.superRatio = Math.max(2, s.getInt("super_ratio", c.superRatio));
        c.spreadEvery = Math.max(1, s.getInt("spread_every", c.spreadEvery));
        c.fuelMaxHours = Math.max(0.1, s.getDouble("fuel_max_hours", c.fuelMaxHours));
        c.upgradeSlots = Math.max(0, Math.min(5, s.getInt("upgrade_slots", c.upgradeSlots)));
        c.fishingMinWater = Math.max(1, s.getInt("fishing_min_water", c.fishingMinWater));
        c.chatCooldownSeconds = Math.max(0, s.getInt("chat_cooldown_seconds", c.chatCooldownSeconds));
        c.actionsPerCycle = Math.max(1, s.getInt("actions_per_cycle", c.actionsPerCycle));
        c.fishingActionsPerCycle = Math.max(1, s.getInt("fishing_actions_per_cycle", c.fishingActionsPerCycle));
    }

    private static void readTiers(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Db.query(conn, "SELECT tier, action_factor, storage_slots, radius, rarity, chestplate, tier_up_item, tier_up_amount "
                + "FROM minion_tier ORDER BY tier", rs -> {
            int tier = rs.getInt(1);
            if (tier < 1 || tier > MAX_TIER) {
                log.warning("minions data: tier " + tier + " is outside 1.." + MAX_TIER);
                return null;
            }
            int i = tier - 1;
            c.actionFactor[i] = Math.max(0.05, rs.getDouble(2));
            c.storageSlots[i] = Math.max(1, rs.getInt(3));
            c.radius[i] = Math.max(1, Math.min(6, rs.getInt(4)));
            c.tierRarity[i] = enumOf(Rarity.class, rs.getString(5), Rarity.COMMON, log);
            c.chestplate[i] = material(rs.getString(6), Material.LEATHER_CHESTPLATE, log);
            String upItem = rs.getString(7);
            int upAmount = rs.getInt(8);
            if (i < MAX_TIER - 1 && upItem != null && !rs.wasNull()) {
                c.tierUpSuper[i] = upItem.equalsIgnoreCase("super");
                c.tierUpAmount[i] = Math.max(1, upAmount);
            }
            return null;
        });
    }

    private static void readTypes(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Map<String, List<Object[]>> drops = new HashMap<>();
        for (Object[] r : Db.query(conn, "SELECT type_id, item, min_amount, max_amount, chance FROM minion_drop "
                + "ORDER BY type_id, position", rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getInt(3),
                    rs.getInt(4), rs.getDouble(5)})) {
            drops.computeIfAbsent((String) r[0], k -> new ArrayList<>()).add(r);
        }
        Db.query(conn, "SELECT id, name, kind, action_seconds, hand, hat, block, sapling, ageable, support, mob, mode, color, "
                + "recipe_ingredient, recipe_center FROM minion_type ORDER BY position", rs -> {
            String id = rs.getString(1);
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Kind kind = Kind.valueOf(rs.getString(3).toUpperCase(Locale.ROOT));
                Material block = optMaterial(rs.getString(7));
                Material sapling = optMaterial(rs.getString(8));
                String mobName = rs.getString(11);
                EntityType mob = mobName == null ? null : EntityType.valueOf(mobName.toUpperCase(Locale.ROOT));
                if ((kind == Kind.MINING || kind == Kind.FARMING || kind == Kind.FORAGING) && block == null) {
                    throw new IllegalArgumentException("needs a block");
                }
                if (kind == Kind.FORAGING && sapling == null) {
                    throw new IllegalArgumentException("needs a sapling");
                }
                if ((kind == Kind.ANIMAL || kind == Kind.COMBAT) && mob == null) {
                    throw new IllegalArgumentException("needs a mob");
                }
                List<Drop> list = new ArrayList<>();
                for (Object[] d : drops.getOrDefault(id, List.of())) {
                    Material item = Material.matchMaterial((String) d[1]);
                    if (item == null) {
                        throw new IllegalArgumentException("unknown drop item " + d[1]);
                    }
                    list.add(new Drop(item, (Integer) d[2], (Integer) d[3], (Double) d[4]));
                }
                if (list.isEmpty()) {
                    throw new IllegalArgumentException("no drops");
                }
                Material ingredient = optMaterial(rs.getString(14));
                Material center = optMaterial(rs.getString(15));
                if (ingredient == null || center == null) {
                    throw new IllegalArgumentException("bad recipe");
                }
                double seconds = rs.getDouble(4);
                if (seconds <= 0) {
                    throw new IllegalArgumentException("action-seconds must be positive");
                }
                c.types.put(id, new Type(id, rs.getString(2), kind, seconds,
                    material(rs.getString(5), Material.WOODEN_PICKAXE, log),
                    material(rs.getString(6), Material.LEATHER_HELMET, log), block, sapling,
                    rs.getInt(9) == 1, rs.getInt(10) == 1, mob, "weighted".equalsIgnoreCase(rs.getString(12)), list,
                    new Recipe(ingredient, center), rs.getString(13)));
            } catch (RuntimeException ex) {
                log.warning("minions data: skipped type '" + id + "': " + ex.getMessage());
            }
            return null;
        });
    }

    private static void readFuels(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Map<String, List<String>> kindsOf = new HashMap<>();
        for (String[] r : Db.query(conn, "SELECT fuel_id, kind FROM minion_fuel_kind ORDER BY fuel_id, kind",
                rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            kindsOf.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        }
        Db.query(conn, "SELECT id, name, rarity, material, custom, speed, hours, compressed FROM minion_fuel ORDER BY position", rs -> {
            String id = rs.getString(1);
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material material = Material.matchMaterial(rs.getString(4));
                if (material == null) {
                    throw new IllegalArgumentException("unknown material");
                }
                boolean custom = rs.getInt(5) == 1;
                double speed = rs.getDouble(6);
                double hours = rs.getDouble(7);
                if (speed < 0 || hours <= 0) {
                    throw new IllegalArgumentException("speed must be >= 0 and hours > 0");
                }
                Set<Kind> kinds = EnumSet.noneOf(Kind.class);
                for (String k : kindsOf.getOrDefault(id, List.of())) {
                    kinds.add(Kind.valueOf(k.toUpperCase(Locale.ROOT)));
                }
                Material compressed = null;
                String compressedName = rs.getString(8);
                if (compressedName != null) {
                    compressed = Material.matchMaterial(compressedName);
                    if (compressed == null || !c.producible().contains(compressed)) {
                        throw new IllegalArgumentException("'compressed' must be a resource a minion can produce");
                    }
                    custom = false;
                }
                String name = rs.getString(2);
                c.fuels.put(id, new Fuel(id, name == null ? MinionConfig.prettify(material) : name,
                    enumOf(Rarity.class, rs.getString(3), Rarity.COMMON, log), material, custom, speed, hours, kinds,
                    compressed));
            } catch (RuntimeException ex) {
                log.warning("minions data: skipped fuel '" + id + "': " + ex.getMessage());
            }
            return null;
        });
    }

    private static void readUpgrades(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Map<String, List<String>> lore = new HashMap<>();
        for (String[] r : Db.query(conn, "SELECT upgrade_id, line FROM minion_upgrade_lore ORDER BY upgrade_id, position",
                rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            lore.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        }
        Db.query(conn, "SELECT id, name, rarity, material, effect, speed, burn, radius FROM minion_upgrade ORDER BY position", rs -> {
            String id = rs.getString(1);
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material material = Material.matchMaterial(rs.getString(4));
                if (material == null) {
                    throw new IllegalArgumentException("unknown material");
                }
                Effect effect = Effect.valueOf(rs.getString(5).toUpperCase(Locale.ROOT));
                c.upgrades.put(id, new Upgrade(id, rs.getString(2),
                    enumOf(Rarity.class, rs.getString(3), Rarity.UNCOMMON, log), material, effect,
                    Math.max(0, rs.getDouble(6)), Math.max(0.1, rs.getDouble(7)), Math.max(0, rs.getInt(8)),
                    lore.getOrDefault(id, List.of())));
            } catch (RuntimeException ex) {
                log.warning("minions data: skipped upgrade '" + id + "': " + ex.getMessage());
            }
            return null;
        });
    }

    private static void readSkins(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Db.query(conn, "SELECT id, name, rarity, head FROM minion_skin ORDER BY position", rs -> {
            String id = rs.getString(1);
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material head = Material.matchMaterial(rs.getString(4));
                if (head == null) {
                    throw new IllegalArgumentException("unknown head material");
                }
                c.skins.put(id, new Skin(id, rs.getString(2), enumOf(Rarity.class, rs.getString(3), Rarity.UNCOMMON, log), head));
            } catch (RuntimeException ex) {
                log.warning("minions data: skipped skin '" + id + "': " + ex.getMessage());
            }
            return null;
        });
    }

    private static void readItemRecipes(Connection conn, MinionConfig c, Logger log) throws SQLException {
        Map<String, List<String>> shapes = new HashMap<>();
        for (String[] r : Db.query(conn, "SELECT recipe_id, pattern FROM minion_recipe_row ORDER BY recipe_id, position",
                rs -> new String[] {rs.getString(1), rs.getString(2)})) {
            shapes.computeIfAbsent(r[0], k -> new ArrayList<>()).add(r[1]);
        }
        Map<String, Map<Character, String>> ingredients = new HashMap<>();
        for (String[] r : Db.query(conn, "SELECT recipe_id, letter, spec FROM minion_recipe_ingredient ORDER BY recipe_id, rowid",
                rs -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)})) {
            ingredients.computeIfAbsent(r[0], k -> new LinkedHashMap<>()).put(r[1].charAt(0), r[2]);
        }
        Db.query(conn, "SELECT id, result FROM minion_recipe ORDER BY position", rs -> {
            String id = rs.getString(1);
            try {
                if (!ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                String result = rs.getString(2);
                if (!resultExists(c, result)) {
                    throw new IllegalArgumentException("unknown result '" + result + "'");
                }
                List<String> shape = shapes.getOrDefault(id, List.of());
                if (shape.isEmpty() || shape.size() > 3) {
                    throw new IllegalArgumentException("shape needs 1 to 3 rows");
                }
                for (String row : shape) {
                    if (row.isEmpty() || row.length() > 3) {
                        throw new IllegalArgumentException("shape rows need 1 to 3 characters");
                    }
                }
                Map<Character, String> map = ingredients.getOrDefault(id, Map.of());
                for (String row : shape) {
                    for (char ch : row.toCharArray()) {
                        if (ch != ' ' && !map.containsKey(ch)) {
                            throw new IllegalArgumentException("shape uses '" + ch + "' with no ingredient");
                        }
                    }
                }
                c.itemRecipes.put(id, new ItemRecipe(id, result, shape, map));
            } catch (RuntimeException ex) {
                log.warning("minions data: skipped recipe '" + id + "': " + ex.getMessage());
            }
            return null;
        });
    }

    private static boolean resultExists(MinionConfig c, String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String id = spec.substring(colon + 1);
        return switch (spec.substring(0, colon)) {
            case "upgrade" -> c.upgrades.containsKey(id);
            case "skin" -> c.skins.containsKey(id);
            case "fuel" -> c.fuels.containsKey(id) && c.fuels.get(id).custom();
            default -> false;
        };
    }

    private static @Nullable Material optMaterial(@Nullable String name) {
        return name == null ? null : Material.matchMaterial(name);
    }

    private static Material material(@Nullable String name, Material fallback, Logger log) {
        Material m = optMaterial(name);
        if (m == null) {
            if (name != null) {
                log.warning("minions data: unknown material " + name);
            }
            return fallback;
        }
        return m;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback, Logger log) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("minions data: unknown " + type.getSimpleName() + " " + name);
            return fallback;
        }
    }
}
