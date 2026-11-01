package win.vampyuyu.survival.minions;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Rarity;

/**
 * The minions tuning read from the content database by {@link MinionRepo}: pure data, no Bukkit state. A bad row is
 * skipped with a warning, so one typo never disables the feature. Every lookup the rest of the feature does goes through this class.
 */
public final class MinionConfig {
    /** Highest tier. */
    public static final int MAX_TIER = 11;

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

    /**
     * A fuel. {@code custom} false means the plain vanilla item is the fuel. {@code compressed} non-null means the
     * fuel IS the "Enchanted X" item the minion menu makes from X (so there is a way to get it in normal play).
     */
    public record Fuel(String id, String name, Rarity rarity, Material material, boolean custom, double speed,
            double hours, Set<Kind> kinds, @Nullable Material compressed) {
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
            if (compressed != null) {
                return ItemKey.custom(MinionItems.enchantedId(compressed));
            }
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

    /**
     * A crafting recipe of a fuel, upgrade or hat. {@code result} and the ingredient specs use
     * {@code upgrade:<id>}, {@code skin:<id>}, {@code fuel:<id>}, {@code enchanted:<MATERIAL>},
     * {@code super:<MATERIAL>} or a plain {@code MATERIAL} (exactly that vanilla item, never a custom one).
     */
    public record ItemRecipe(String id, String result, List<String> shape, Map<Character, String> ingredients) {
        public ItemRecipe {
            shape = List.copyOf(shape);
            ingredients = Map.copyOf(ingredients);
        }
    }

    // ---------------------------------------------------------------- settings

    public int tickTicks = 20;
    public double visualRange = 48;
    public int scanSeconds = 10;
    public double maxCatchupHours = 336;
    public int islandCheckSeconds = 30;
    public double islandMissingHours = 6;
    public boolean memberCollect = false;
    public int guiClickMillis = 150;
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
    public final Map<String, ItemRecipe> itemRecipes = new LinkedHashMap<>();
    public final Map<String, Skin> skins = new LinkedHashMap<>();
    public final Map<Material, Material> smelt = new LinkedHashMap<>();

    /** Defaults only; {@link MinionRepo} fills it from the content database. */
    MinionConfig() {
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

    public long islandMissingMillis() {
        return Math.round(islandMissingHours * 3_600_000d);
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
