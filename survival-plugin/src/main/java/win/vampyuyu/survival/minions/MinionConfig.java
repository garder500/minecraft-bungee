package win.vampyuyu.survival.minions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Rarity;

/**
 * Parsed {@code minions.yml}: pure data, no Bukkit state. A bad entry is skipped with a warning, so one typo never
 * disables the feature. Every lookup the rest of the feature does goes through this class.
 */
public final class MinionConfig {
    /** Highest tier. */
    public static final int MAX_TIER = 11;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    /** What a minion does. */
    public enum Kind { MINING, FARMING, FORAGING, ANIMAL, COMBAT, FISHING }

    /** One possible drop: {@code chance} is a probability (mode each) or a weight (mode weighted). */
    public record Drop(Material item, int min, int max, double chance) {
        public Drop {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("bad drop amount " + min + ".." + max);
            }
            if (chance < 0) {
                throw new IllegalArgumentException("bad chance " + chance);
            }
        }
    }

    /** Tier I recipe: eight {@code ingredient} around {@code center}. */
    public record Recipe(Material ingredient, Material center) {}

    /** A minion type. */
    public record Type(String id, String name, Kind kind, double actionSeconds, Material hand, Material hat,
            @Nullable Material block, @Nullable Material sapling, boolean ageable, boolean support,
            @Nullable EntityType mob, boolean weighted, List<Drop> drops, Recipe recipe, String color) {
        public Type {
            drops = List.copyOf(drops);
        }

        /** The first drop, the resource a tier-up is paid in. */
        public Drop primary() {
            return drops.get(0);
        }
    }

    /** A fuel. {@code custom} false means the plain vanilla item is the fuel. */
    public record Fuel(String id, String name, Rarity rarity, Material material, boolean custom, double speed,
            double hours, Set<Kind> kinds) {
        public Fuel {
            kinds = Set.copyOf(kinds);
        }

        public boolean accepts(Kind kind) {
            return kinds.isEmpty() || kinds.contains(kind);
        }

        public long durationMillis() {
            return Math.round(hours * 3_600_000d);
        }

        /** Item key of this fuel (see {@link ItemKey}). */
        public String key() {
            return custom ? ItemKey.custom(MinionItems.fuelId(id)) : ItemKey.vanilla(material);
        }
    }

    /** What an upgrade item does. */
    public enum Effect { SMELT, COMPACT, SUPER_COMPACT, SPREAD, EXPAND, SPEED }

    /** An upgrade item. */
    public record Upgrade(String id, String name, Rarity rarity, Material material, Effect effect, double speed,
            double burn, int radius, List<String> lore) {
        public Upgrade {
            lore = List.copyOf(lore);
        }

        public String key() {
            return ItemKey.custom(MinionItems.upgradeId(id));
        }
    }

    /** A cosmetic hat. */
    public record Skin(String id, String name, Rarity rarity, Material head) {
        public String key() {
            return ItemKey.custom(MinionItems.skinId(id));
        }
    }

    // ---------------------------------------------------------------- settings

    public int tickTicks = 20;
    public double visualRange = 48;
    public int scanSeconds = 10;
    public double maxCatchupHours = 336;
    public int islandCheckSeconds = 30;
    public int baseSlots = 5;
    public int maxSlots = 30;
    public List<Integer> slotThresholds = List.of(5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225, 250);
    public int perIslandCap = 48;
    public double scale = 0.7;
    public int compactRatio = 160;
    public int superRatio = 160;
    public int spreadEvery = 10;
    public double fuelMaxHours = 168;
    public int upgradeSlots = 2;
    public int fishingMinWater = 3;
    public int chatCooldownSeconds = 120;
    public int actionsPerCycle = 2;
    public int fishingActionsPerCycle = 1;

    public double[] actionFactor = {1, 1, 0.857, 0.857, 0.714, 0.714, 0.643, 0.643, 0.571, 0.571, 0.5};
    public int[] storageSlots = {1, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30};
    public int[] radius = {2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3};
    public Rarity[] tierRarity = new Rarity[MAX_TIER];
    public Material[] chestplate = new Material[MAX_TIER];
    public boolean[] tierUpSuper = new boolean[MAX_TIER - 1];
    public int[] tierUpAmount = {8, 8, 8, 8, 8, 8, 8, 2, 4, 8};

    public final Map<String, Type> types = new LinkedHashMap<>();
    public final Map<String, Fuel> fuels = new LinkedHashMap<>();
    public final Map<String, Upgrade> upgrades = new LinkedHashMap<>();
    public final Map<String, Skin> skins = new LinkedHashMap<>();
    public final Map<Material, Material> smelt = new LinkedHashMap<>();

    private MinionConfig() {
        for (int i = 0; i < MAX_TIER; i++) {
            tierRarity[i] = Rarity.COMMON;
            chestplate[i] = Material.LEATHER_CHESTPLATE;
        }
    }

    // ---------------------------------------------------------------- derived values

    /** Milliseconds of work (at normal speed) one resource takes: actions per cycle times the tier's action time. */
    public double cycleMillis(Type type, int tier) {
        int actions = type.kind() == Kind.FISHING ? fishingActionsPerCycle : actionsPerCycle;
        double ms = type.actionSeconds() * actionFactor[clampTier(tier) - 1] * 1000d * actions;
        return Math.max(250d, ms);
    }

    public int storageSlots(int tier) {
        return storageSlots[clampTier(tier) - 1];
    }

    /** Working radius of a tier, plus the expander. */
    public int radius(int tier, int bonus) {
        return radius[clampTier(tier) - 1] + bonus;
    }

    public long maxCatchupMillis() {
        return Math.round(maxCatchupHours * 3_600_000d);
    }

    public long maxFuelMillis() {
        return Math.round(fuelMaxHours * 3_600_000d);
    }

    public static int clampTier(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    /** Tier-up cost from {@code tier} to {@code tier + 1}: how many of which kind of compacted item. */
    public record TierUp(boolean superCompacted, int amount) {}

    public TierUp tierUp(int fromTier) {
        int i = Math.max(1, Math.min(MAX_TIER - 1, fromTier)) - 1;
        return new TierUp(tierUpSuper[i], tierUpAmount[i]);
    }

    /** What an item turns into in the Auto Smelter (itself when it has no recipe). */
    public Material smelted(Material material) {
        return smelt.getOrDefault(material, material);
    }

    /** Every material a minion can produce, smelted forms included (the compacted items exist for these). */
    public Set<Material> producible() {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (Type type : types.values()) {
            for (Drop drop : type.drops()) {
                out.add(drop.item());
                out.add(smelted(drop.item()));
            }
        }
        out.add(Material.DIAMOND);
        return out;
    }

    // ---------------------------------------------------------------- parsing

    /** Parses a configuration; entries that do not parse are skipped with a warning. */
    public static MinionConfig load(ConfigurationSection root, Logger log) {
        MinionConfig c = new MinionConfig();
        ConfigurationSection s = root.getConfigurationSection("settings");
        if (s != null) {
            c.tickTicks = Math.max(1, s.getInt("tick-ticks", c.tickTicks));
            c.visualRange = Math.max(4, s.getDouble("visual-range", c.visualRange));
            c.scanSeconds = Math.max(1, s.getInt("scan-seconds", c.scanSeconds));
            c.maxCatchupHours = Math.max(0, s.getDouble("max-catchup-hours", c.maxCatchupHours));
            c.islandCheckSeconds = Math.max(1, s.getInt("island-check-seconds", c.islandCheckSeconds));
            c.baseSlots = Math.max(0, s.getInt("base-slots", c.baseSlots));
            c.maxSlots = Math.max(c.baseSlots, s.getInt("max-slots", c.maxSlots));
            List<Integer> thresholds = new ArrayList<>(s.getIntegerList("slot-thresholds"));
            if (!thresholds.isEmpty()) {
                Collections.sort(thresholds);
                c.slotThresholds = List.copyOf(thresholds);
            }
            c.perIslandCap = Math.max(1, s.getInt("per-island-cap", c.perIslandCap));
            c.scale = Math.max(0.2, Math.min(2.0, s.getDouble("scale", c.scale)));
            c.compactRatio = Math.max(2, s.getInt("compact-ratio", c.compactRatio));
            c.superRatio = Math.max(2, s.getInt("super-ratio", c.superRatio));
            c.spreadEvery = Math.max(1, s.getInt("spread-every", c.spreadEvery));
            c.fuelMaxHours = Math.max(0.1, s.getDouble("fuel-max-hours", c.fuelMaxHours));
            c.upgradeSlots = Math.max(0, Math.min(5, s.getInt("upgrade-slots", c.upgradeSlots)));
            c.fishingMinWater = Math.max(1, s.getInt("fishing-min-water", c.fishingMinWater));
            c.chatCooldownSeconds = Math.max(0, s.getInt("chat-cooldown-seconds", c.chatCooldownSeconds));
            c.actionsPerCycle = Math.max(1, s.getInt("actions-per-cycle", c.actionsPerCycle));
            c.fishingActionsPerCycle = Math.max(1, s.getInt("fishing-actions-per-cycle", c.fishingActionsPerCycle));
        }
        ConfigurationSection t = root.getConfigurationSection("tiers");
        if (t != null) {
            double[] factor = doubles(t, "action-factor", MAX_TIER, log);
            if (factor != null) {
                for (int i = 0; i < MAX_TIER; i++) {
                    c.actionFactor[i] = Math.max(0.05, factor[i]);
                }
            }
            double[] storage = doubles(t, "storage-slots", MAX_TIER, log);
            if (storage != null) {
                for (int i = 0; i < MAX_TIER; i++) {
                    c.storageSlots[i] = Math.max(1, (int) storage[i]);
                }
            }
            double[] radius = doubles(t, "radius", MAX_TIER, log);
            if (radius != null) {
                for (int i = 0; i < MAX_TIER; i++) {
                    c.radius[i] = Math.max(1, Math.min(6, (int) radius[i]));
                }
            }
            List<String> rarity = t.getStringList("rarity");
            if (rarity.size() == MAX_TIER) {
                for (int i = 0; i < MAX_TIER; i++) {
                    c.tierRarity[i] = enumOf(Rarity.class, rarity.get(i), Rarity.COMMON, log);
                }
            }
            List<String> chest = t.getStringList("chestplate");
            if (chest.size() == MAX_TIER) {
                for (int i = 0; i < MAX_TIER; i++) {
                    c.chestplate[i] = material(chest.get(i), Material.LEATHER_CHESTPLATE, log);
                }
            }
            List<String> upItem = t.getStringList("tier-up-item");
            double[] upAmount = doubles(t, "tier-up-amount", MAX_TIER - 1, log);
            if (upItem.size() == MAX_TIER - 1 && upAmount != null) {
                for (int i = 0; i < MAX_TIER - 1; i++) {
                    c.tierUpSuper[i] = upItem.get(i).equalsIgnoreCase("super");
                    c.tierUpAmount[i] = Math.max(1, (int) upAmount[i]);
                }
            }
        }
        ConfigurationSection smelt = root.getConfigurationSection("smelt");
        if (smelt != null) {
            for (String key : smelt.getKeys(false)) {
                Material from = Material.matchMaterial(key);
                Material to = Material.matchMaterial(smelt.getString(key, ""));
                if (from == null || to == null) {
                    log.warning("minions.yml: bad smelt entry " + key);
                } else {
                    c.smelt.put(from, to);
                }
            }
        }
        readTypes(c, root.getConfigurationSection("types"), log);
        readFuels(c, root.getConfigurationSection("fuels"), log);
        readUpgrades(c, root.getConfigurationSection("upgrades"), log);
        readSkins(c, root.getConfigurationSection("skins"), log);
        return c;
    }

    private static void readTypes(MinionConfig c, @Nullable ConfigurationSection types, Logger log) {
        if (types == null) {
            return;
        }
        for (String id : types.getKeys(false)) {
            ConfigurationSection e = types.getConfigurationSection(id);
            try {
                if (e == null || !ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Kind kind = Kind.valueOf(e.getString("kind", "").toUpperCase(Locale.ROOT));
                Material block = optMaterial(e.getString("block"));
                Material sapling = optMaterial(e.getString("sapling"));
                EntityType mob = e.contains("mob") ? EntityType.valueOf(e.getString("mob", "").toUpperCase(Locale.ROOT)) : null;
                if ((kind == Kind.MINING || kind == Kind.FARMING || kind == Kind.FORAGING) && block == null) {
                    throw new IllegalArgumentException("needs a block");
                }
                if (kind == Kind.FORAGING && sapling == null) {
                    throw new IllegalArgumentException("needs a sapling");
                }
                if ((kind == Kind.ANIMAL || kind == Kind.COMBAT) && mob == null) {
                    throw new IllegalArgumentException("needs a mob");
                }
                List<Drop> drops = new ArrayList<>();
                for (Map<?, ?> raw : e.getMapList("drops")) {
                    Material item = Material.matchMaterial(String.valueOf(raw.get("item")));
                    if (item == null) {
                        throw new IllegalArgumentException("unknown drop item " + raw.get("item"));
                    }
                    drops.add(new Drop(item, num(raw.get("min"), 1).intValue(), num(raw.get("max"), 1).intValue(),
                        num(raw.get("chance"), 1).doubleValue()));
                }
                if (drops.isEmpty()) {
                    throw new IllegalArgumentException("no drops");
                }
                ConfigurationSection r = e.getConfigurationSection("recipe");
                Material ingredient = r == null ? null : optMaterial(r.getString("ingredient"));
                Material center = r == null ? null : optMaterial(r.getString("center"));
                if (ingredient == null || center == null) {
                    throw new IllegalArgumentException("bad recipe");
                }
                double seconds = e.getDouble("action-seconds", 20);
                if (seconds <= 0) {
                    throw new IllegalArgumentException("action-seconds must be positive");
                }
                c.types.put(id, new Type(id, e.getString("name", id), kind, seconds,
                    material(e.getString("hand"), Material.WOODEN_PICKAXE, log),
                    material(e.getString("hat"), Material.LEATHER_HELMET, log), block, sapling,
                    e.getBoolean("ageable", false), e.getBoolean("support", false), mob,
                    "weighted".equalsIgnoreCase(e.getString("mode", "each")), drops,
                    new Recipe(ingredient, center), e.getString("color", "#888888")));
            } catch (RuntimeException ex) {
                log.warning("minions.yml: skipped type '" + id + "': " + ex.getMessage());
            }
        }
    }

    private static void readFuels(MinionConfig c, @Nullable ConfigurationSection fuels, Logger log) {
        if (fuels == null) {
            return;
        }
        for (String id : fuels.getKeys(false)) {
            ConfigurationSection e = fuels.getConfigurationSection(id);
            try {
                if (e == null || !ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material material = Material.matchMaterial(e.getString("material", ""));
                if (material == null) {
                    throw new IllegalArgumentException("unknown material");
                }
                boolean custom = e.getBoolean("custom", true);
                double speed = e.getDouble("speed", 0.05);
                double hours = e.getDouble("hours", 1);
                if (speed < 0 || hours <= 0) {
                    throw new IllegalArgumentException("speed must be >= 0 and hours > 0");
                }
                Set<Kind> kinds = EnumSet.noneOf(Kind.class);
                for (String k : e.getStringList("kinds")) {
                    kinds.add(Kind.valueOf(k.toUpperCase(Locale.ROOT)));
                }
                c.fuels.put(id, new Fuel(id, e.getString("name", prettify(material)),
                    enumOf(Rarity.class, e.getString("rarity", "COMMON"), Rarity.COMMON, log), material, custom, speed,
                    hours, kinds));
            } catch (RuntimeException ex) {
                log.warning("minions.yml: skipped fuel '" + id + "': " + ex.getMessage());
            }
        }
    }

    private static void readUpgrades(MinionConfig c, @Nullable ConfigurationSection ups, Logger log) {
        if (ups == null) {
            return;
        }
        for (String id : ups.getKeys(false)) {
            ConfigurationSection e = ups.getConfigurationSection(id);
            try {
                if (e == null || !ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material material = Material.matchMaterial(e.getString("material", ""));
                if (material == null) {
                    throw new IllegalArgumentException("unknown material");
                }
                Effect effect = Effect.valueOf(e.getString("effect", "").toUpperCase(Locale.ROOT));
                c.upgrades.put(id, new Upgrade(id, e.getString("name", id),
                    enumOf(Rarity.class, e.getString("rarity", "UNCOMMON"), Rarity.UNCOMMON, log), material, effect,
                    Math.max(0, e.getDouble("speed", 0)), Math.max(0.1, e.getDouble("burn", 1)),
                    Math.max(0, e.getInt("radius", 0)), e.getStringList("lore")));
            } catch (RuntimeException ex) {
                log.warning("minions.yml: skipped upgrade '" + id + "': " + ex.getMessage());
            }
        }
    }

    private static void readSkins(MinionConfig c, @Nullable ConfigurationSection skins, Logger log) {
        if (skins == null) {
            return;
        }
        for (String id : skins.getKeys(false)) {
            ConfigurationSection e = skins.getConfigurationSection(id);
            try {
                if (e == null || !ID.matcher(id).matches()) {
                    throw new IllegalArgumentException("bad id");
                }
                Material head = Material.matchMaterial(e.getString("head", ""));
                if (head == null) {
                    throw new IllegalArgumentException("unknown head material");
                }
                c.skins.put(id, new Skin(id, e.getString("name", id),
                    enumOf(Rarity.class, e.getString("rarity", "UNCOMMON"), Rarity.UNCOMMON, log), head));
            } catch (RuntimeException ex) {
                log.warning("minions.yml: skipped skin '" + id + "': " + ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- small helpers

    private static double @Nullable [] doubles(ConfigurationSection s, String key, int size, Logger log) {
        List<Double> list = s.getDoubleList(key);
        if (list.size() != size) {
            if (s.contains(key)) {
                log.warning("minions.yml: tiers." + key + " needs " + size + " values, using the defaults");
            }
            return null;
        }
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static Number num(@Nullable Object value, Number fallback) {
        return value instanceof Number n ? n : fallback;
    }

    private static @Nullable Material optMaterial(@Nullable String name) {
        return name == null ? null : Material.matchMaterial(name);
    }

    private static Material material(@Nullable String name, Material fallback, Logger log) {
        Material m = optMaterial(name);
        if (m == null) {
            if (name != null) {
                log.warning("minions.yml: unknown material " + name);
            }
            return fallback;
        }
        return m;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String name, E fallback, Logger log) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("minions.yml: unknown " + type.getSimpleName() + " " + name);
            return fallback;
        }
    }

    /** "COBBLESTONE" becomes "Cobblestone", "RAW_BEEF" "Raw Beef". */
    public static String prettify(Material material) {
        StringBuilder out = new StringBuilder();
        for (String word : material.name().split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** Roman numeral of a tier (I to XI). */
    public static String roman(int tier) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};
        return numerals[clampTier(tier) - 1];
    }
}
