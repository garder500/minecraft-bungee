package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Turns {@code boss.yml} sections into validated {@link BossDefinition}s. Every problem is appended to the
 * {@code errors} list with the path of the offending key; a definition with errors is dropped (the others load).
 */
public final class DefinitionParser {
    public static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    public static final Set<String> TARGETS = Set.of("nearest", "random", "top", "far", "all");
    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "mainhand", "offhand");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private DefinitionParser() {}

    /** Parses every child of {@code section} as a boss. */
    public static Map<String, BossDefinition> parseAll(@Nullable ConfigurationSection section,
            BossDefinition.Kind defaultKind, List<String> errors) {
        Map<String, BossDefinition> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(id);
            if (child == null) {
                errors.add(section.getName() + "." + id + ": not a section");
                continue;
            }
            BossDefinition def = parseBoss(id, child, defaultKind, errors);
            if (def != null) {
                result.put(id, def);
            }
        }
        return result;
    }

    /** Parses one boss, null when invalid (errors tell why). */
    public static @Nullable BossDefinition parseBoss(String id, ConfigurationSection s,
            BossDefinition.Kind defaultKind, List<String> errors) {
        int before = errors.size();
        String path = id;
        if (!ID.matcher(id).matches()) {
            errors.add(path + ": id must match [a-z0-9_]+");
            return null;
        }
        String name = s.getString("name", id);
        EntityType entity = parseEntity(s.getString("entity", ""), path + ".entity", errors);
        double health = s.getDouble("health", 100_000);
        if (!(health > 0) || health > 1e15) {
            errors.add(path + ".health: must be between 0 and 1e15");
        }
        double damage = s.getDouble("damage", 6);
        if (damage < 0 || damage > 1000) {
            errors.add(path + ".damage: must be between 0 and 1000");
        }
        double armor = s.getDouble("armor", 0);
        double scale = s.getDouble("scale", 1.0);
        if (scale < 0.0625 || scale > 16) {
            errors.add(path + ".scale: must be between 0.0625 and 16");
        }
        double speed = s.getDouble("speed", 1.0);
        if (speed <= 0 || speed > 5) {
            errors.add(path + ".speed: must be between 0 and 5");
        }
        double kb = s.getDouble("knockback-resistance", 1.0);
        if (kb < 0 || kb > 1) {
            errors.add(path + ".knockback-resistance: must be between 0 and 1");
        }
        ConfigurationSection bar = s.getConfigurationSection("bar");
        String color = bar == null ? "PURPLE" : bar.getString("color", "PURPLE").toUpperCase(Locale.ROOT);
        String overlay = bar == null ? "PROGRESS" : bar.getString("overlay", "PROGRESS").toUpperCase(Locale.ROOT);
        try {
            BossBar.Color.valueOf(color);
        } catch (IllegalArgumentException e) {
            errors.add(path + ".bar.color: unknown colour " + color);
        }
        try {
            BossBar.Overlay.valueOf(overlay);
        } catch (IllegalArgumentException e) {
            errors.add(path + ".bar.overlay: unknown overlay " + overlay);
        }
        BossDefinition.Arena arena = parseArena(s.getConfigurationSection("arena"), path + ".arena", errors);
        List<BossDefinition.Phase> phases = parsePhases(s, path, errors);
        Map<String, BossDefinition.EquipSpec> equipment = parseEquipment(s.getConfigurationSection("equipment"),
            path + ".equipment", errors);
        Map<String, Object> options = new LinkedHashMap<>();
        ConfigurationSection opt = s.getConfigurationSection("options");
        if (opt != null) {
            options.putAll(opt.getValues(false));
        }
        BossDefinition.Loot loot = parseLoot(s.getConfigurationSection("loot"), path + ".loot", errors);
        BossDefinition.Summon summon = parseSummon(s.getConfigurationSection("summon"), path + ".summon", errors);
        BossDefinition.Kind kind = defaultKind;
        String kindText = s.getString("kind");
        if (kindText != null) {
            try {
                kind = BossDefinition.Kind.valueOf(kindText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(path + ".kind: expected boss, slayer or world");
            }
        }
        if (errors.size() > before || entity == null || arena == null || phases.isEmpty()) {
            return null;
        }
        return new BossDefinition(id, name, s.getString("description", ""), entity, health, damage, armor, scale,
            speed, kb, color, overlay, arena, phases, equipment, options, loot, summon, kind);
    }

    public static @Nullable EntityType parseEntity(String text, String path, List<String> errors) {
        EntityType type;
        try {
            type = EntityType.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(path + ": unknown entity type '" + text + "'");
            return null;
        }
        Class<?> cls = type.getEntityClass();
        if (type == EntityType.PLAYER || cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            errors.add(path + ": " + type + " is not a living mob");
            return null;
        }
        return type;
    }

    static BossDefinition.@Nullable Arena parseArena(@Nullable ConfigurationSection a, String path,
            List<String> errors) {
        int before = errors.size();
        double radius = a == null ? 60 : a.getDouble("radius", 60);
        double leash = a == null ? radius + 10 : a.getDouble("leash", radius + 10);
        double projectile = a == null ? radius + 4 : a.getDouble("projectile-range", radius + 4);
        if (radius <= 0 || radius > 256) {
            errors.add(path + ".radius: must be between 0 and 256");
        }
        if (leash < radius || leash > 512) {
            errors.add(path + ".leash: must be at least the radius and at most 512");
        }
        if (projectile <= 0) {
            errors.add(path + ".projectile-range: must be positive");
        }
        int minPlayers = a == null ? 1 : a.getInt("min-players", 1);
        double perPlayer = a == null ? 0.25 : a.getDouble("scale-per-player", 0.25);
        double cap = a == null ? 3.0 : a.getDouble("scale-cap", 3.0);
        if (minPlayers < 1 || perPlayer < 0 || cap < 1) {
            errors.add(path + ": min-players >= 1, scale-per-player >= 0, scale-cap >= 1 required");
        }
        int enrage = a == null ? 600 : a.getInt("enrage-seconds", 600);
        int idle = a == null ? 60 : a.getInt("idle-despawn-seconds", 60);
        int limit = a == null ? 0 : a.getInt("time-limit-seconds", 0);
        int minions = a == null ? 12 : a.getInt("max-minions", 12);
        if (enrage < 0 || idle < 0 || limit < 0 || minions < 0 || minions > 64) {
            errors.add(path + ": enrage-seconds, idle-despawn-seconds, time-limit-seconds must be >= 0 and max-minions 0-64");
        }
        if (errors.size() > before) {
            return null;
        }
        return new BossDefinition.Arena(radius, leash, projectile, a != null && a.getBoolean("no-fly", false),
            a == null || a.getBoolean("no-build", true), a == null || a.getBoolean("no-teleport", true), minPlayers,
            perPlayer, cap, enrage, a == null ? 1.5 : a.getDouble("enrage-damage", 1.5),
            a == null ? 0.25 : a.getDouble("enrage-growth", 0.25), idle, limit, minions);
    }

    static List<BossDefinition.Phase> parsePhases(ConfigurationSection s, String path, List<String> errors) {
        List<BossDefinition.Phase> phases = new ArrayList<>();
        List<Map<?, ?>> raw = s.getMapList("phases");
        if (raw.isEmpty()) {
            List<AbilitySpec> abilities = parseAbilities(s.getMapList("abilities"), path + ".abilities", errors);
            phases.add(new BossDefinition.Phase("Fight", 1.0, "", "", 1.0, 1.0, List.of(), abilities));
            return phases;
        }
        double last = 2.0;
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> map = stringKeys(raw.get(i));
            String at = path + ".phases[" + i + "]";
            double threshold = number(map.get("at"), i == 0 ? 1.0 : -1);
            if (i == 0) {
                threshold = 1.0;
            }
            if (!(threshold > 0 && threshold <= 1) || threshold >= last) {
                errors.add(at + ".at: thresholds must be in (0, 1] and strictly decreasing");
                continue;
            }
            last = threshold;
            phases.add(new BossDefinition.Phase(String.valueOf(map.getOrDefault("name", "Phase " + (i + 1))),
                threshold, String.valueOf(map.getOrDefault("title", "")),
                String.valueOf(map.getOrDefault("subtitle", "")), number(map.get("damage"), 1.0),
                number(map.get("speed"), 1.0), parseAbilities(list(map.get("enter")), at + ".enter", errors),
                parseAbilities(list(map.get("abilities")), at + ".abilities", errors)));
        }
        return phases;
    }

    /** Abilities from a yaml list of maps. */
    public static List<AbilitySpec> parseAbilities(List<Map<?, ?>> raw, String path, List<String> errors) {
        List<AbilitySpec> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            AbilitySpec spec = parseAbility(stringKeys(raw.get(i)), path + "[" + i + "]", errors);
            if (spec != null) {
                result.add(spec);
            }
        }
        return result;
    }

    static @Nullable AbilitySpec parseAbility(Map<String, Object> map, String path, List<String> errors) {
        Object typeText = map.get("type");
        AbilityType type = AbilityType.byId(typeText == null ? null : String.valueOf(typeText)).orElse(null);
        if (type == null) {
            errors.add(path + ".type: unknown ability '" + typeText + "'");
            return null;
        }
        double cooldown = number(map.get("cooldown"), 12);
        double telegraph = number(map.get("telegraph"), 0);
        double damage = number(map.get("damage"), 1.0);
        String target = String.valueOf(map.getOrDefault("target", "nearest")).toLowerCase(Locale.ROOT);
        int fromTier = (int) number(map.get("from-tier"), 1);
        if (cooldown < 0.5 || cooldown > 600) {
            errors.add(path + ".cooldown: must be between 0.5 and 600 seconds");
        }
        if (telegraph < 0 || telegraph > 30) {
            errors.add(path + ".telegraph: must be between 0 and 30 seconds");
        }
        if (damage < 0 || damage > 100) {
            errors.add(path + ".damage: must be between 0 and 100");
        }
        if (!TARGETS.contains(target)) {
            errors.add(path + ".target: expected one of " + TARGETS);
        }
        if (fromTier < 1 || fromTier > 5) {
            errors.add(path + ".from-tier: must be 1-5");
        }
        Map<String, Object> params = new LinkedHashMap<>(map);
        for (String key : List.of("type", "cooldown", "telegraph", "damage", "target", "from-tier")) {
            params.remove(key);
        }
        return new AbilitySpec(type, (int) Math.round(cooldown * 20), (int) Math.round(telegraph * 20), damage, target,
            fromTier, params);
    }

    public static Map<String, BossDefinition.EquipSpec> parseEquipment(@Nullable ConfigurationSection s, String path,
            List<String> errors) {
        Map<String, BossDefinition.EquipSpec> result = new LinkedHashMap<>();
        if (s == null) {
            return result;
        }
        for (String slot : s.getKeys(false)) {
            String key = slot.toLowerCase(Locale.ROOT);
            if (!SLOTS.contains(key)) {
                errors.add(path + "." + slot + ": slot must be one of " + SLOTS);
                continue;
            }
            String text = s.getString(slot, "");
            String[] parts = text.split(":", 2);
            Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || material.isAir() || !material.isItem()) {
                errors.add(path + "." + slot + ": unknown item '" + parts[0] + "'");
                continue;
            }
            Integer rgb = null;
            if (parts.length == 2) {
                if (!COLOR.matcher(parts[1].trim()).matches()) {
                    errors.add(path + "." + slot + ": colour must look like #5a1e8c");
                    continue;
                }
                rgb = Integer.parseInt(parts[1].trim().substring(1), 16);
            }
            result.put(key, new BossDefinition.EquipSpec(material, rgb));
        }
        return result;
    }

    public static BossDefinition.Loot parseLoot(@Nullable ConfigurationSection s, String path, List<String> errors) {
        if (s == null) {
            return BossDefinition.Loot.none();
        }
        double coins = s.getDouble("coins", 0);
        double xp = s.getDouble("combat-xp", 0);
        double minShare = s.getDouble("min-share", 0.01);
        double floor = s.getDouble("share-floor", 0.25);
        if (coins < 0 || xp < 0 || minShare < 0 || minShare > 1 || floor < 0 || floor > 1) {
            errors.add(path + ": coins, combat-xp >= 0 and min-share, share-floor within 0-1 required");
        }
        List<Double> rank = new ArrayList<>();
        for (Object o : s.getList("rank-bonus", List.of(1.5, 1.25, 1.1))) {
            rank.add(number(o, 1.0));
        }
        List<BossDefinition.LootEntry> entries = new ArrayList<>();
        List<Map<?, ?>> raw = s.getMapList("drops");
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> m = stringKeys(raw.get(i));
            String at = path + ".drops[" + i + "]";
            String item = String.valueOf(m.getOrDefault("item", ""));
            if (!validItemSpec(item)) {
                errors.add(at + ".item: expected custom:<id> or vanilla:<MATERIAL>, got '" + item + "'");
                continue;
            }
            double chance = number(m.get("chance"), 1.0);
            int min = (int) number(m.get("min"), 1);
            int max = (int) number(m.get("max"), min);
            if (chance < 0 || chance > 1 || min < 1 || max < min || max > 64) {
                errors.add(at + ": chance 0-1, 1 <= min <= max <= 64 required");
                continue;
            }
            entries.add(new BossDefinition.LootEntry(item, chance, min, max, number(m.get("min-share"), 0),
                (int) number(m.get("max-rank"), 0), !Boolean.FALSE.equals(m.get("magic-find")), number(m.get("pity"), 0),
                number(m.get("pity-cap"), 0.5), (int) number(m.get("from-tier"), 1)));
        }
        return new BossDefinition.Loot(coins, xp, minShare, floor, rank, entries,
            s.getDouble("wings-min-share", -1), s.getDouble("announce-below", 0.01));
    }

    /** {@code custom:id} or {@code vanilla:MATERIAL}. */
    public static boolean validItemSpec(String item) {
        if (item.startsWith("custom:")) {
            return ID.matcher(item.substring(7)).matches();
        }
        if (item.startsWith("vanilla:")) {
            Material m = Material.matchMaterial(item.substring(8));
            return m != null && !m.isAir() && m.isItem();
        }
        return false;
    }

    static BossDefinition.@Nullable Summon parseSummon(@Nullable ConfigurationSection s, String path,
            List<String> errors) {
        if (s == null || !s.contains("item")) {
            return null;
        }
        int before = errors.size();
        String item = s.getString("item", "");
        if (!ID.matcher(item).matches()) {
            errors.add(path + ".item: must match [a-z0-9_]+");
        }
        String mode = s.getString("mode", "free").toLowerCase(Locale.ROOT);
        if (!mode.equals("altar") && !mode.equals("free")) {
            errors.add(path + ".mode: expected altar or free");
        }
        BossDefinition.Altar altar = null;
        ConfigurationSection a = s.getConfigurationSection("altar");
        if (mode.equals("altar")) {
            if (a == null || a.getString("world", "").isEmpty()) {
                errors.add(path + ".altar: the altar mode needs world, x, y, z, radius");
            } else {
                altar = new BossDefinition.Altar(a.getString("world"), a.getDouble("x"), a.getDouble("y"),
                    a.getDouble("z"), a.getDouble("radius", 16), Math.max(0, a.getDouble("cell-size", 0)));
            }
        }
        int cooldown = s.getInt("cooldown-seconds", 60);
        int global = s.getInt("global-cooldown-seconds", 0);
        int concurrent = s.getInt("max-concurrent", 1);
        if (cooldown < 0 || global < 0 || concurrent < 1) {
            errors.add(path + ": cooldowns >= 0 and max-concurrent >= 1 required");
        }
        if (errors.size() > before) {
            return null;
        }
        return new BossDefinition.Summon(item, mode, s.getStringList("worlds"), altar,
            s.getDouble("max-spawn-distance", 0), cooldown, global, s.getBoolean("require-island-member", true),
            s.getBoolean("require-water", false), concurrent, s.getBoolean("refund-on-despawn", true),
            Math.max(concurrent, s.getInt("global-max-concurrent", 6)));
    }

    // ---------------------------------------------------------------- yaml helpers

    public static Map<String, Object> stringKeys(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<?, ?>> list(@Nullable Object value) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object o : items) {
                if (o instanceof Map<?, ?> m) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    public static double number(@Nullable Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
