package win.vampyuyu.survival.boss.slayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.boss.AbilitySpec;
import win.vampyuyu.survival.boss.BossDefinition;
import win.vampyuyu.survival.boss.BossRepo;
import win.vampyuyu.survival.boss.DefinitionParser;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.items.combat.PowerCurve;

/** Reads the {@code slayer*} tables into {@link SlayerDefinition}s and their generated tier bosses. */
public final class SlayerParser {
    public static final int TIERS = 5;
    public static final int LEVELS = 9;

    private SlayerParser() {}

    /** One {@code slayer_tier} row. */
    private record TierRow(double cost, double required, double health, double damage, double slayerXp, double limit,
            double coins, double combatXp, double speed, double scale, double armor, double unlock) {}

    /** Every slayer line of the database; a broken one is dropped and reported in {@code errors}. */
    public static Map<String, SlayerDefinition> loadAll(Connection c, List<String> errors) throws SQLException {
        return loadAll(c, errors, null, 0);
    }

    /**
     * Same, with the power curve: a slayer with {@code hp_auto = 1} gets {@code seconds x} the curve's damage per second
     * of its tier as the health of that tier (the written health is the fallback when the curve has no such tier).
     */
    public static Map<String, SlayerDefinition> loadAll(Connection c, List<String> errors, PowerCurve curve, double seconds)
            throws SQLException {
        Map<String, List<Object[]>> targets = BossRepo.grouped(c, "SELECT slayer_id, entity, kill_xp FROM slayer_target ORDER BY slayer_id, position",
            rs -> new SimpleEntry<>(rs.getString(1), new Object[] {rs.getString(2), rs.getObject(3) == null ? null : rs.getDouble(3)}));
        Map<String, List<String>> worlds = BossRepo.grouped(c, "SELECT slayer_id, world FROM slayer_world ORDER BY slayer_id, position",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getString(2)));
        Map<String, List<Long>> levels = BossRepo.grouped(c, "SELECT slayer_id, xp FROM slayer_level ORDER BY slayer_id, level",
            rs -> new SimpleEntry<>(rs.getString(1), rs.getLong(2)));
        Map<String, List<Object[]>> perks = BossRepo.grouped(c, "SELECT slayer_id, level, stat, amount FROM slayer_perk ORDER BY slayer_id, position",
            rs -> new SimpleEntry<>(rs.getString(1), new Object[] {rs.getInt(2), rs.getString(3), rs.getDouble(4)}));
        Map<String, List<TierRow>> tiers = BossRepo.grouped(c, "SELECT slayer_id, cost, required_xp, health, damage, slayer_xp, "
            + "time_limit, coins, combat_xp, speed, scale, armor, unlock_level FROM slayer_tier ORDER BY slayer_id, tier",
            rs -> new SimpleEntry<>(rs.getString(1), new TierRow(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5),
                rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getDouble(9), rs.getDouble(10), rs.getDouble(11),
                rs.getDouble(12), rs.getDouble(13))));
        Map<String, List<BossRepo.EquipRaw>> equipment = BossRepo.readEquipment(c, "slayer_equipment", "slayer_id");
        Map<String, Map<String, List<BossRepo.AbilityRaw>>> abilities = BossRepo.readAbilities(c, "slayer_ability", "slayer_id",
            "slayer_ability_param", "''");
        Map<String, List<Double>> ranks = BossRepo.readRankBonus(c, "slayer_rank_bonus", "slayer_id");
        Map<String, List<BossDefinition.LootEntry>> drops = BossRepo.readDrops(c, "slayer_drop", "slayer_id");
        Map<String, SlayerDefinition> result = new LinkedHashMap<>();
        Db.query(c, "SELECT * FROM slayer ORDER BY position, rowid", rs -> {
            String id = rs.getString("id");
            SlayerDefinition def = parse(id, rs, targets.getOrDefault(id, List.of()), worlds.getOrDefault(id, List.of()),
                levels.getOrDefault(id, List.of()), perks.getOrDefault(id, List.of()), tiers.getOrDefault(id, List.of()),
                equipment.getOrDefault(id, List.of()), abilities.getOrDefault(id, Map.of()).getOrDefault("", List.of()),
                ranks.getOrDefault(id, List.of()), drops.getOrDefault(id, List.of()), errors, curve, seconds);
            if (def != null) {
                result.put(id, def);
            }
            return null;
        });
        return result;
    }

    private static @Nullable SlayerDefinition parse(String id, ResultSet s, List<Object[]> targetRows, List<String> worlds,
            List<Long> levelXp, List<Object[]> perkRows, List<TierRow> tierRows, List<BossRepo.EquipRaw> equipRows,
            List<BossRepo.AbilityRaw> abilityRows, List<Double> rankBonus, List<BossDefinition.LootEntry> dropRows,
            List<String> errors, PowerCurve curve, double seconds) throws SQLException {
        int before = errors.size();
        String path = "slayers." + id;
        if (!DefinitionParser.ID.matcher(id).matches()) {
            errors.add(path + ": id must match [a-z0-9_]+");
            return null;
        }
        String name = s.getString("name");
        Material icon = Material.matchMaterial(s.getString("icon"));
        if (icon == null || icon.isAir() || !icon.isItem()) {
            errors.add(path + ".icon: unknown item");
            icon = Material.BARRIER;
        }
        EntityType bossEntity = DefinitionParser.parseEntity(s.getString("entity"), path + ".entity", errors);
        double defaultKill = s.getDouble("default_kill_xp");
        List<EntityType> targets = new ArrayList<>();
        Map<EntityType, Double> killXp = new LinkedHashMap<>();
        for (Object[] row : targetRows) {
            EntityType type = DefinitionParser.parseEntity((String) row[0], path + ".targets", errors);
            if (type != null) {
                targets.add(type);
                if (row[1] != null) {
                    killXp.put(type, (Double) row[1]);
                }
            }
        }
        if (targets.isEmpty()) {
            errors.add(path + ".targets: at least one mob type is required");
        }
        if (defaultKill <= 0) {
            errors.add(path + ".kill-xp.default: must be positive");
        }
        if (levelXp.size() != LEVELS) {
            errors.add(path + ".level-xp: exactly " + LEVELS + " values required");
        } else {
            for (int i = 1; i < LEVELS; i++) {
                if (levelXp.get(i) <= levelXp.get(i - 1)) {
                    errors.add(path + ".level-xp: values must be strictly increasing");
                    break;
                }
            }
        }
        List<SlayerDefinition.Perk> perks = new ArrayList<>();
        for (Object[] row : perkRows) {
            if (!Stat.known((String) row[1])) {
                errors.add(path + ".perks: unknown stat " + row[1]);
                continue;
            }
            // an old stat name converts (Strength becomes Might); a dropped stat gives no perk
            for (Stat.Converted conv : Stat.resolve((String) row[1])) {
                perks.add(new SlayerDefinition.Perk((Integer) row[0], conv.stat(), (Double) row[2] * conv.factor()));
            }
        }
        if (tierRows.size() != TIERS) {
            errors.add(path + ".tiers: expected five tiers (I to V)");
        } else {
            for (TierRow t : tierRows) {
                if (t.cost() <= 0 || t.required() <= 0 || t.health() <= 0 || t.damage() <= 0 || t.slayerXp() <= 0 || t.limit() <= 0) {
                    errors.add(path + ".tiers: cost, required-xp, health, damage, slayer-xp and time-limit must be positive");
                    break;
                }
            }
        }
        String color = s.getString("bar_color").toUpperCase(Locale.ROOT);
        String overlay = s.getString("bar_overlay").toUpperCase(Locale.ROOT);
        Map<String, BossDefinition.EquipSpec> equipment = BossRepo.parseEquipment(equipRows, path + ".equipment", errors);
        Map<String, Object> options = BossRepo.options(s);
        List<AbilitySpec> abilities = BossRepo.parseAbilities(abilityRows, path + ".abilities", errors);
        BossDefinition.Loot baseLoot = BossRepo.parseLoot(path + ".loot", 0, 0, s.getDouble("loot_min_share"),
            s.getDouble("loot_share_floor"), rankBonus, dropRows, -1, s.getDouble("loot_announce_below"), errors);
        Integer frenzyFrom = s.getObject("frenzy_from_tier") == null ? null : s.getInt("frenzy_from_tier");
        double frenzyAt = s.getDouble("frenzy_at");
        double frenzyDamage = s.getDouble("frenzy_damage");
        double frenzySpeed = s.getDouble("frenzy_speed");
        String frenzyTitle = s.getString("frenzy_title");
        String frenzySubtitle = s.getString("frenzy_subtitle");
        if (errors.size() > before || bossEntity == null) {
            return null;
        }
        List<SlayerDefinition.Tier> tiers = new ArrayList<>();
        for (int t = 1; t <= TIERS; t++) {
            final int tier = t;
            TierRow row = tierRows.get(t - 1);
            List<AbilitySpec> active = abilities.stream().filter(a -> a.fromTier() <= tier).toList();
            List<BossDefinition.Phase> phases = new ArrayList<>();
            phases.add(new BossDefinition.Phase("Hunt", 1.0, "", "", 1.0, 1.0, List.of(), active));
            if (frenzyFrom != null && t >= frenzyFrom) {
                if (frenzyAt > 0 && frenzyAt < 1) {
                    phases.add(new BossDefinition.Phase("Frenzy", frenzyAt, frenzyTitle == null ? "<red><bold>FRENZY" : frenzyTitle,
                        frenzySubtitle == null ? "<gray>It is enraged!" : frenzySubtitle, frenzyDamage, frenzySpeed, List.of(), active));
                }
            }
            String bossName = name + " " + SlayerDefinition.roman(t);
            List<BossDefinition.LootEntry> entries = baseLoot.entries().stream().filter(e -> e.fromTier() <= tier).toList();
            BossDefinition.Loot loot = new BossDefinition.Loot(row.coins(), row.combatXp(), baseLoot.minShare(),
                baseLoot.shareFloor(), baseLoot.rankBonus(), entries, -1, baseLoot.announceBelow());
            BossDefinition.Arena arena = new BossDefinition.Arena(36, 48, 48, false, false, false, 1, 0, 1, 0, 1.5, 0,
                25, (int) row.limit() + 30, 8);
            BossDefinition boss = new BossDefinition(SlayerDefinition.bossId(id, t), "<red>" + name + " <yellow>"
                + SlayerDefinition.roman(t), "Hunt boss tier " + t, bossEntity, health(row.health(), t, s, curve, seconds), row.damage(),
                row.armor(), row.scale(), row.speed(), 0.8, color, overlay, arena, phases, equipment, options, loot, null,
                BossDefinition.Kind.SLAYER, t, s.getString("weakness"), s.getString("resistance"));
            tiers.add(new SlayerDefinition.Tier(t, row.cost(), row.required(), (long) row.slayerXp(), (int) row.limit(),
                (int) row.unlock(), boss));
        }
        return new SlayerDefinition(id, name, icon, s.getString("description"), targets, killXp, defaultKill,
            worlds, levelXp, perks, tiers);
    }

    /** Health of tier {@code t}: derived from the curve when the slayer row has {@code hp_auto = 1}, else the written one. */
    private static double health(double written, int t, ResultSet slayer, PowerCurve curve, double seconds) throws SQLException {
        if (curve != null && slayer.getInt("hp_auto") == 1 && seconds > 0 && curve.has(t)) {
            return curve.neutralHealth(t, seconds);
        }
        return written;
    }
}
