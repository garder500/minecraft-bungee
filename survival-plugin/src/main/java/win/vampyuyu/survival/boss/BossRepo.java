package win.vampyuyu.survival.boss;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.boss.slayer.SlayerDefinition;
import win.vampyuyu.survival.boss.slayer.SlayerParser;
import win.vampyuyu.survival.boss.world.WorldBossConfig;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.items.combat.PowerCurve;

/**
 * Reads the boss tables ({@code boss*}, {@code slayer*}, {@code boss_recipe*}, {@code boss_world_*}) and the
 * {@code boss.*} settings of the content database into a validated {@link BossConfig}. A bad row is dropped and listed
 * in {@link BossConfig#errors()}; the rest still loads. Never throws for bad content.
 */
public final class BossRepo {
    private BossRepo() {}

    /** One ability row before validation. */
    public record AbilityRaw(String type, double cooldown, double telegraph, double damage, String target, int fromTier,
            Map<String, Object> params) {}

    /** One equipment row before validation. */
    public record EquipRaw(String slot, String material, @Nullable Integer color) {}

    public static BossConfig load(Db db) {
        Settings settings = Settings.load(db, "boss");
        PowerCurve curve = PowerCurve.load(db);
        return db.read(c -> load(c, settings, curve));
    }

    static BossConfig load(Connection c, Settings settings) throws SQLException {
        return load(c, settings, null);
    }

    /**
     * Loads the boss tables. With a power curve, a boss with {@code hp_auto = 1} gets
     * {@code boss.settings.target-seconds x} the curve's weakness damage per second of its tier as health; a slayer with
     * {@code hp_auto = 1} gets {@code boss.slayer.target-seconds x} the neutral damage per second of the tier.
     */
    static BossConfig load(Connection c, Settings settings, PowerCurve curve) throws SQLException {
        List<String> errors = new ArrayList<>();
        double bossSeconds = settings.getDouble("settings.target-seconds", 240);
        Map<String, BossDefinition> bosses = readBosses(c, errors, curve, bossSeconds);
        Map<String, SlayerDefinition> slayers = SlayerParser.loadAll(c, errors, curve, settings.getDouble("slayer.target-seconds", 90));
        for (SlayerDefinition slayer : slayers.values()) {
            for (SlayerDefinition.Tier tier : slayer.tiers()) {
                if (bosses.containsKey(tier.boss().id())) {
                    errors.add("slayer boss id clashes with a boss: " + tier.boss().id());
                }
            }
        }
        WorldBossConfig world = readWorld(c, settings, bosses, errors);
        BossConfig.SlayerSettings slayerSettings = new BossConfig.SlayerSettings(settings.getBoolean("slayer.enabled", true),
            settings.getBoolean("slayer.ignore-spawner-mobs", true),
            Math.max(2, settings.getDouble("slayer.spawn-min-distance", 6)),
            Math.max(3, settings.getDouble("slayer.spawn-max-distance", 10)),
            Math.max(0, settings.getInt("slayer.spawn-countdown-seconds", 3)));
        BossSettings knobs = BossSettings.parse(settings);
        errors.addAll(settings.problems());
        return new BossConfig(knobs, bosses, slayers, world, slayerSettings, readMobDrops(c, errors),
            readRecipes(c, errors), readItemText(c), errors);
    }

    // ---------------------------------------------------------------- bosses

    private static Map<String, BossDefinition> readBosses(Connection c, List<String> errors, PowerCurve curve, double seconds)
            throws SQLException {
        Map<String, Map<String, List<AbilityRaw>>> abilities = readAbilities(c, "boss_ability", "boss_id", "boss_ability_param",
            "phase || ':' || role");
        Map<String, List<double[]>> phaseNumbers = new HashMap<>();
        Map<String, List<String[]>> phaseText = new HashMap<>();
        for (Object[] r : Db.query(c, "SELECT boss_id, name, at, title, subtitle, damage, speed FROM boss_phase "
                + "ORDER BY boss_id, position", rs -> new Object[] {rs.getString(1), rs.getString(2), rs.getDouble(3),
                    rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getDouble(7)})) {
            String id = (String) r[0];
            phaseText.computeIfAbsent(id, k -> new ArrayList<>()).add(new String[] {(String) r[1], (String) r[3], (String) r[4]});
            phaseNumbers.computeIfAbsent(id, k -> new ArrayList<>()).add(new double[] {(Double) r[2], (Double) r[5], (Double) r[6]});
        }
        Map<String, List<EquipRaw>> equipment = readEquipment(c, "boss_equipment", "boss_id");
        Map<String, List<Double>> ranks = readRankBonus(c, "boss_rank_bonus", "boss_id");
        Map<String, List<BossDefinition.LootEntry>> drops = readDrops(c, "boss_drop", "boss_id");
        Map<String, List<String>> summonWorlds = grouped(c, "SELECT boss_id, world FROM boss_summon_world ORDER BY boss_id, position",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        Map<String, BossDefinition.Summon> summons = new HashMap<>();
        Map<String, Boolean> altarMissing = new HashMap<>();
        Db.query(c, "SELECT * FROM boss_summon", rs -> {
            String id = rs.getString("boss_id");
            String mode = rs.getString("mode").toLowerCase(Locale.ROOT);
            String world = rs.getString("altar_world");
            BossDefinition.Altar altar = null;
            if (mode.equals("altar") && world != null && !world.isEmpty()) {
                altar = new BossDefinition.Altar(world, rs.getDouble("altar_x"), rs.getDouble("altar_y"), rs.getDouble("altar_z"),
                    rs.getObject("altar_radius") == null ? 16 : rs.getDouble("altar_radius"),
                    Math.max(0, rs.getDouble("altar_cell_size")));
            }
            altarMissing.put(id, mode.equals("altar") && altar == null);
            int concurrent = rs.getInt("max_concurrent");
            summons.put(id, new BossDefinition.Summon(rs.getString("item"), mode, summonWorlds.getOrDefault(id, List.of()), altar,
                rs.getDouble("max_spawn_distance"), rs.getInt("cooldown_seconds"), rs.getInt("global_cooldown_seconds"),
                rs.getInt("require_island_member") == 1, rs.getInt("require_water") == 1, concurrent,
                rs.getInt("refund_on_despawn") == 1, Math.max(concurrent, rs.getInt("global_max_concurrent"))));
            return null;
        });
        Map<String, BossDefinition> result = new LinkedHashMap<>();
        Db.query(c, "SELECT * FROM boss ORDER BY rowid", rs -> {
            String id = rs.getString("id");
            BossDefinition def = assemble(rs, id, abilities.getOrDefault(id, Map.of()), phaseText.getOrDefault(id, List.of()),
                phaseNumbers.getOrDefault(id, List.of()), equipment.getOrDefault(id, List.of()),
                ranks.getOrDefault(id, List.of()), drops.getOrDefault(id, List.of()), summons.get(id),
                altarMissing.getOrDefault(id, false), errors, curve, seconds);
            if (def != null) {
                result.put(id, def);
            }
            return null;
        });
        return result;
    }

    private static @Nullable BossDefinition assemble(ResultSet rs, String id, Map<String, List<AbilityRaw>> abilities,
            List<String[]> phaseText, List<double[]> phaseNumbers, List<EquipRaw> equipment, List<Double> ranks,
            List<BossDefinition.LootEntry> drops, BossDefinition.@Nullable Summon summon, boolean altarMissing,
            List<String> errors, PowerCurve curve, double seconds) throws SQLException {
        int before = errors.size();
        String path = id;
        if (!DefinitionParser.ID.matcher(id).matches()) {
            errors.add(path + ": id must match [a-z0-9_]+");
            return null;
        }
        EntityType entity = DefinitionParser.parseEntity(rs.getString("entity"), path + ".entity", errors);
        String color = rs.getString("bar_color").toUpperCase(Locale.ROOT);
        String overlay = rs.getString("bar_overlay").toUpperCase(Locale.ROOT);
        double health = rs.getDouble("health");
        int tier = rs.getObject("tier") == null ? 0 : rs.getInt("tier");
        if (curve != null && rs.getInt("hp_auto") == 1 && tier > 0 && curve.has(tier) && seconds > 0) {
            health = curve.bossHealth(tier, seconds); // the written health is only the fallback
        }
        double damage = rs.getDouble("damage");
        if (curve != null && rs.getInt("hp_auto") == 1 && tier > 0 && curve.has(tier)) {
            damage = curve.bossHitDamage(tier); // tuned to the armor of the tier, like the health
        }
        double scale = rs.getDouble("scale");
        double speed = rs.getDouble("speed");
        double kb = rs.getDouble("knockback_resistance");
        DefinitionParser.checkBoss(path, health, damage, scale, speed, kb, color, overlay, errors);
        BossDefinition.Arena arena = DefinitionParser.checkArena(new BossDefinition.Arena(rs.getDouble("arena_radius"),
            rs.getDouble("arena_leash"), rs.getDouble("arena_projectile_range"), rs.getInt("arena_no_fly") == 1,
            rs.getInt("arena_no_build") == 1, rs.getInt("arena_no_teleport") == 1, rs.getInt("arena_min_players"),
            rs.getDouble("arena_scale_per_player"), rs.getDouble("arena_scale_cap"), rs.getInt("arena_enrage_seconds"),
            rs.getDouble("arena_enrage_damage"), rs.getDouble("arena_enrage_growth"), rs.getInt("arena_idle_despawn_seconds"),
            rs.getInt("arena_time_limit_seconds"), rs.getInt("arena_max_minions")), path + ".arena", errors);
        List<BossDefinition.Phase> phases = new ArrayList<>();
        if (phaseText.isEmpty()) {
            phases.add(new BossDefinition.Phase("Fight", 1.0, "", "", 1.0, 1.0, List.of(), List.of()));
        }
        double last = 2.0;
        for (int i = 0; i < phaseText.size(); i++) {
            String at = path + ".phases[" + i + "]";
            double threshold = i == 0 ? 1.0 : phaseNumbers.get(i)[0];
            if (!DefinitionParser.checkThreshold(threshold, last, at, errors)) {
                continue;
            }
            last = threshold;
            phases.add(new BossDefinition.Phase(phaseText.get(i)[0], threshold, phaseText.get(i)[1], phaseText.get(i)[2],
                phaseNumbers.get(i)[1], phaseNumbers.get(i)[2],
                parseAbilities(abilities.getOrDefault(i + ":enter", List.of()), at + ".enter", errors),
                parseAbilities(abilities.getOrDefault(i + ":cast", List.of()), at + ".abilities", errors)));
        }
        Map<String, BossDefinition.EquipSpec> equip = parseEquipment(equipment, path + ".equipment", errors);
        BossDefinition.Loot loot = parseLoot(path + ".loot", rs.getDouble("loot_coins"), rs.getDouble("loot_combat_xp"),
            rs.getDouble("loot_min_share"), rs.getDouble("loot_share_floor"), ranks, drops,
            rs.getDouble("loot_wings_min_share"), rs.getDouble("loot_announce_below"), errors);
        if (summon != null) {
            summon = DefinitionParser.checkSummon(summon, altarMissing, path + ".summon", errors);
        }
        BossDefinition.Kind kind = BossDefinition.Kind.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT));
        if (errors.size() > before || entity == null || arena == null || phases.isEmpty()) {
            return null;
        }
        return new BossDefinition(id, rs.getString("name"), rs.getString("description"), entity, health, damage,
            rs.getDouble("armor"), scale, speed, kb, color, overlay, arena, phases, equip, options(rs), loot, summon, kind, tier,
            rs.getString("weakness"), rs.getString("resistance"));
    }

    /** The entity options of a boss or slayer row ({@code option_*} columns; NULL ones are left out). */
    public static Map<String, Object> options(ResultSet rs) throws SQLException {
        Map<String, Object> options = new LinkedHashMap<>();
        if (rs.getObject("option_size") != null) {
            options.put("size", rs.getInt("option_size"));
        }
        if (rs.getObject("option_baby") != null) {
            options.put("baby", rs.getInt("option_baby") == 1);
        }
        if (rs.getObject("option_glow") != null) {
            options.put("glow", rs.getInt("option_glow") == 1);
        }
        if (rs.getObject("option_contact_radius") != null) {
            options.put("contact-radius", rs.getDouble("option_contact_radius"));
        }
        return options;
    }

    // ---------------------------------------------------------------- shared readers (bosses and slayers)

    /**
     * Abilities of {@code table} by owner then scope ({@code scopeExpr}: an SQL expression over the table's columns that
     * tells which list of the owner an ability belongs to), in position order, with their parameters.
     */
    public static Map<String, Map<String, List<AbilityRaw>>> readAbilities(Connection c, String table, String key,
            String paramTable, String scopeExpr) throws SQLException {
        Map<String, Object> noParams = Map.of();
        Map<String, Map<String, Object>> params = new HashMap<>();
        Db.query(c, "SELECT " + key + " || '/' || " + scopeExpr + " || '/' || ability, name, num, text FROM " + paramTable
            + " ORDER BY " + key + ", ability, name", rs -> {
                Object value = rs.getObject(3) != null ? (Object) rs.getDouble(3) : rs.getString(4);
                params.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>()).put(rs.getString(2), value);
                return null;
            });
        Map<String, Map<String, List<AbilityRaw>>> result = new HashMap<>();
        Db.query(c, "SELECT " + key + ", " + scopeExpr + ", position, type, cooldown, telegraph, damage, target, from_tier, "
            + key + " || '/' || " + scopeExpr + " || '/' || position FROM " + table + " ORDER BY " + key + ", " + scopeExpr
            + ", position", rs -> {
                result.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).computeIfAbsent(rs.getString(2), k -> new ArrayList<>())
                    .add(new AbilityRaw(rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getString(8),
                        rs.getInt(9), params.getOrDefault(rs.getString(10), noParams)));
                return null;
            });
        return result;
    }

    public static List<AbilitySpec> parseAbilities(List<AbilityRaw> raw, String path, List<String> errors) {
        List<AbilitySpec> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            AbilityRaw a = raw.get(i);
            AbilitySpec spec = DefinitionParser.parseAbility(a.type(), a.cooldown(), a.telegraph(), a.damage(), a.target(),
                a.fromTier(), a.params(), path + "[" + i + "]", errors);
            if (spec != null) {
                result.add(spec);
            }
        }
        return result;
    }

    public static Map<String, List<EquipRaw>> readEquipment(Connection c, String table, String key) throws SQLException {
        return grouped(c, "SELECT " + key + ", slot, material, color FROM " + table + " ORDER BY rowid",
            rs -> new SimpleEntry<>(rs.getString(1), new EquipRaw(rs.getString(2), rs.getString(3),
                rs.getObject(4) == null ? null : rs.getInt(4))));
    }

    public static Map<String, BossDefinition.EquipSpec> parseEquipment(List<EquipRaw> raw, String path, List<String> errors) {
        Map<String, BossDefinition.EquipSpec> result = new LinkedHashMap<>();
        for (EquipRaw e : raw) {
            BossDefinition.EquipSpec spec = DefinitionParser.parseEquip(e.slot(), e.material(), e.color(), path, errors);
            if (spec != null) {
                result.put(e.slot().toLowerCase(Locale.ROOT), spec);
            }
        }
        return result;
    }

    public static Map<String, List<Double>> readRankBonus(Connection c, String table, String key) throws SQLException {
        return grouped(c, "SELECT " + key + ", multiplier FROM " + table + " ORDER BY " + key + ", rank",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getDouble(2)));
    }

    /** Drops of {@code table} by owner, as stored (validated by {@link #parseLoot}). */
    public static Map<String, List<BossDefinition.LootEntry>> readDrops(Connection c, String table, String key) throws SQLException {
        return grouped(c, "SELECT " + key + ", item, chance, min_amount, max_amount, min_share, max_rank, magic_find, pity, "
            + "pity_cap, from_tier FROM " + table + " ORDER BY " + key + ", position",
            rs -> new SimpleEntry<>(rs.getString(1), new BossDefinition.LootEntry(rs.getString(2), rs.getDouble(3), rs.getInt(4),
                rs.getInt(5), rs.getDouble(6), rs.getInt(7), rs.getInt(8) == 1, rs.getDouble(9), rs.getDouble(10), rs.getInt(11))));
    }

    /** Validates the loot of one owner; invalid drops are reported and left out. */
    public static BossDefinition.Loot parseLoot(String path, double coins, double xp, double minShare, double floor,
            List<Double> rankBonus, List<BossDefinition.LootEntry> drops, double wingsMinShare, double announceBelow,
            List<String> errors) {
        DefinitionParser.checkLoot(path, coins, xp, minShare, floor, errors);
        List<BossDefinition.LootEntry> entries = new ArrayList<>();
        for (int i = 0; i < drops.size(); i++) {
            BossDefinition.LootEntry e = DefinitionParser.checkDrop(path + ".drops[" + i + "]", drops.get(i), errors);
            if (e != null) {
                entries.add(e);
            }
        }
        return new BossDefinition.Loot(coins, xp, minShare, floor, rankBonus, entries, wingsMinShare, announceBelow);
    }

    public static <T> Map<String, List<T>> grouped(Connection c, String sql, Db.RowMapper<SimpleEntry<String, T>> mapper)
            throws SQLException {
        Map<String, List<T>> out = new LinkedHashMap<>();
        for (SimpleEntry<String, T> e : Db.query(c, sql, mapper)) {
            out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
        return out;
    }

    // ---------------------------------------------------------------- world bosses, mob drops, recipes, item text

    private static WorldBossConfig readWorld(Connection c, Settings s, Map<String, BossDefinition> bosses, List<String> errors)
            throws SQLException {
        List<WorldBossConfig.Spot> spots = Db.query(c, "SELECT world, x, y, z, name FROM boss_world_spot ORDER BY position",
            rs -> new WorldBossConfig.Spot(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getString(5)));
        List<WorldBossConfig.Entry> pool = Db.query(c, "SELECT boss_id, weight FROM boss_world_pool ORDER BY rowid",
            rs -> new WorldBossConfig.Entry(rs.getString(1), rs.getDouble(2)));
        List<Integer> announce = new ArrayList<>();
        if (s.has("world.announce-before-seconds")) {
            for (String text : s.getList("world.announce-before-seconds")) {
                try {
                    announce.add(Integer.parseInt(text.trim()));
                } catch (NumberFormatException e) {
                    errors.add("world-bosses.announce-before-seconds: '" + text + "' is not a whole number");
                }
            }
        } else {
            announce.addAll(List.of(300, 60, 10));
        }
        return WorldBossConfig.parse(s, announce, spots, pool, bosses, errors);
    }

    private static List<BossConfig.MobDrop> readMobDrops(Connection c, List<String> errors) throws SQLException {
        Map<String, List<String>> entities = grouped(c, "SELECT drop_id, entity FROM boss_mob_drop_entity ORDER BY rowid",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        Map<String, List<String>> worlds = grouped(c, "SELECT drop_id, world FROM boss_mob_drop_world ORDER BY rowid",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        List<BossConfig.MobDrop> drops = new ArrayList<>();
        Db.query(c, "SELECT id, item, chance, magic_find, allow_spawner FROM boss_mob_drop ORDER BY rowid", rs -> {
            String id = rs.getString(1);
            String item = rs.getString(2);
            Set<EntityType> types = new HashSet<>();
            for (String text : entities.getOrDefault(id, List.of())) {
                EntityType type = DefinitionParser.parseEntity(text, "mob-drops." + item, errors);
                if (type != null) {
                    types.add(type);
                }
            }
            if (!DefinitionParser.ID.matcher(item).matches() || types.isEmpty()) {
                errors.add("mob-drops: entry '" + item + "' needs an item id, entities and a chance in (0, 1]");
                return null;
            }
            drops.add(new BossConfig.MobDrop(item, types, new HashSet<>(worlds.getOrDefault(id, List.of())), rs.getDouble(3),
                rs.getInt(4) == 1, rs.getInt(5) == 1));
            return null;
        });
        return drops;
    }

    private static Map<String, BossConfig.RecipeSpec> readRecipes(Connection c, List<String> errors) throws SQLException {
        Map<String, List<String>> shapes = grouped(c, "SELECT recipe, pattern FROM boss_recipe_row ORDER BY recipe, position",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        Map<String, List<String[]>> ingredients = grouped(c, "SELECT recipe, symbol, material FROM boss_recipe_ingredient ORDER BY rowid",
            rs -> new SimpleEntry<>(rs.getString(1), new String[] {rs.getString(2), rs.getString(3)}));
        Map<String, BossConfig.RecipeSpec> recipes = new LinkedHashMap<>();
        Db.query(c, "SELECT item FROM boss_recipe WHERE enabled = 1 ORDER BY rowid", rs -> {
            String item = rs.getString(1).toLowerCase(Locale.ROOT);
            List<String> shape = shapes.getOrDefault(rs.getString(1), List.of());
            Map<Character, Material> map = new LinkedHashMap<>();
            boolean ok = !shape.isEmpty() && shape.size() <= 3;
            for (String[] ing : ingredients.getOrDefault(rs.getString(1), List.of())) {
                Material m = Material.matchMaterial(ing[1]);
                if (ing[0].length() != 1 || m == null || !m.isItem()) {
                    ok = false;
                    errors.add("recipes." + item + ": bad ingredient " + ing[0]);
                } else {
                    map.put(ing[0].charAt(0), m);
                }
            }
            if (map.isEmpty()) {
                ok = false;
            }
            for (String row : shape) {
                if (row.length() < 1 || row.length() > 3) {
                    ok = false;
                }
                for (char ch : row.toCharArray()) {
                    if (ch != ' ' && !map.containsKey(ch)) {
                        ok = false;
                        errors.add("recipes." + item + ": shape uses '" + ch + "' with no ingredient");
                    }
                }
            }
            if (ok) {
                recipes.put(item, new BossConfig.RecipeSpec(item, shape, map));
            } else {
                errors.add("recipes." + item + ": invalid shape or ingredients, recipe skipped");
            }
            return null;
        });
        return recipes;
    }

    private static Map<String, BossConfig.ItemText> readItemText(Connection c) throws SQLException {
        Map<String, List<String>> lore = grouped(c, "SELECT item, line FROM boss_item_text_lore ORDER BY item, position",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        Map<String, BossConfig.ItemText> result = new LinkedHashMap<>();
        Db.query(c, "SELECT item, name FROM boss_item_text ORDER BY rowid", rs -> {
            result.put(rs.getString(1), new BossConfig.ItemText(rs.getString(2), lore.get(rs.getString(1))));
            return null;
        });
        return result;
    }
}
