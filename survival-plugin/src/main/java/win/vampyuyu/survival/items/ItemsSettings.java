package win.vampyuyu.survival.items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import win.vampyuyu.survival.api.Stat;

/**
 * Typed, immutable view of {@code items.yml}. Every getter has the same default as the shipped file, so an empty
 * configuration (tests, a user file missing keys) behaves like the defaults.
 */
public final class ItemsSettings {
    private final ConfigurationSection root;

    // stats engine
    public final Set<String> heldTypes;
    public final Set<String> ignoredItemIds;
    public final int flushTicks;
    public final int scanTicks;
    public final double maxHealth;
    public final float healthScale;
    public final double maxSpeedPercent;
    public final double maxAttackSpeedPercent;
    public final double maxMiningSpeedPercent;
    public final double regenPercentPerSecond;
    public final boolean cancelVanillaRegen;
    public final boolean skyblockOnly;

    // combat
    public final double damageBonus;
    public final double vanillaDamageMultiplier;
    public final double mobHealthMultiplier;
    public final Set<String> scaledSpawnReasons;
    public final double incomingScale;
    public final Map<String, Double> incomingScaleByCause;
    public final Map<String, Double> trueCausePercent;
    public final double critCap;
    public final boolean useAttackCooldown;
    public final double ferocityDelayTicks;
    public final boolean indicators;
    public final int indicatorTicks;
    public final int indicatorPool;
    public final boolean healthBars;
    public final double abilityIntelligenceDivisor;

    // mana / hud
    public final double manaRegenPercent;
    public final double overflowMaxPercent;
    public final double overflowDecayPercent;
    public final int hudPeriodTicks;
    public final int hudKeepAliveTicks;
    public final int xpPopupSeconds;

    // fortune
    public final Set<String> fortuneOres;
    public final Set<String> fortuneCrops;
    public final Set<String> fortuneLogs;
    public final int placedBlockMemory;

    // economy
    public final boolean requireEconomy;
    public final double enchantCostBase;
    public final double enchantCostGrowth;
    public final double enchantXpPerLevel;
    public final String anvilCostMode;
    public final double anvilCostPerLevel;
    public final double reforgeCostBase;
    public final double bagUpgradeCost;
    public final double bagUpgradeGrowth;

    // accessories
    public final int bagBaseSlots;
    public final int bagSlotsPerUpgrade;
    public final int bagMaxSlots;
    public final Map<String, Integer> magicalPower;
    public final Map<Stat, Double> perMagicalPower;

    // pets
    public final int petMaxOwned;
    public final boolean petHideFromOwner;
    public final double petXpShare;
    public final double petOtherSkillShare;
    public final double petBaseXp;
    public final double petXpGrowth;
    public final Map<String, Double> petRarityXp;
    public final int petUpdateTicks;

    public ItemsSettings(ConfigurationSection root) {
        this.root = root;
        heldTypes = upperSet("stats.held-types", List.of("SWORD", "BOW", "AXE", "PICKAXE", "SHOVEL", "HOE", "DRILL",
            "ROD", "STAFF", "WAND", "HOOK", "TRIDENT", "CROSSBOW"));
        ignoredItemIds = lowerSet("stats.ignore-item-ids", List.of("wings"));
        flushTicks = Math.max(1, i("stats.flush-ticks", 2));
        scanTicks = Math.max(2, i("stats.scan-ticks", 10));
        maxHealth = clamp(d("attributes.max-health", 1024), 20, 1024);
        healthScale = (float) clamp(d("attributes.health-scale", 40), 2, 40);
        maxSpeedPercent = d("attributes.max-speed-percent", 400);
        maxAttackSpeedPercent = d("attributes.max-attack-speed-percent", 300);
        maxMiningSpeedPercent = d("attributes.max-mining-speed-percent", 2000);
        regenPercentPerSecond = d("stats.regen-percent-per-second", 1.5);
        cancelVanillaRegen = b("stats.cancel-vanilla-regen", true);
        skyblockOnly = b("stats.skyblock-worlds-only", true);

        damageBonus = d("combat.damage-bonus", 5);
        vanillaDamageMultiplier = d("combat.vanilla-damage-multiplier", 3);
        mobHealthMultiplier = d("combat.mob-health-multiplier", 5);
        scaledSpawnReasons = upperSet("combat.scaled-spawn-reasons", List.of("NATURAL", "SPAWNER", "CHUNK_GEN", "JOCKEY",
            "REINFORCEMENTS", "PATROL", "RAID", "VILLAGE_INVASION", "TRAP"));
        incomingScale = d("combat.incoming-damage-scale.default", 5);
        incomingScaleByCause = doubles("combat.incoming-damage-scale.causes", Map.of("FALL", 5.0, "FIRE", 3.0,
            "FIRE_TICK", 2.0, "LAVA", 8.0, "DROWNING", 3.0, "CONTACT", 3.0, "SUFFOCATION", 3.0, "HOT_FLOOR", 3.0,
            "FREEZE", 2.0, "LIGHTNING", 10.0));
        trueCausePercent = doubles("combat.true-damage-percent-of-max-health", Map.of("VOID", 100.0, "STARVATION", 2.0,
            "SUICIDE", 100.0, "KILL", 100.0, "WORLD_BORDER", 100.0));
        critCap = d("combat.crit-chance-cap", 100);
        useAttackCooldown = b("combat.use-attack-cooldown", true);
        ferocityDelayTicks = d("combat.ferocity-delay-ticks", 3);
        indicators = b("combat.damage-indicators.enabled", true);
        indicatorTicks = Math.max(5, i("combat.damage-indicators.lifetime-ticks", 24));
        indicatorPool = Math.max(0, i("combat.damage-indicators.pool-size", 32));
        healthBars = b("combat.mob-health-bars", true);
        abilityIntelligenceDivisor = d("combat.ability-intelligence-divisor", 100);

        manaRegenPercent = d("mana.regen-percent-per-second", 2.0);
        overflowMaxPercent = d("mana.overflow.max-percent", 100);
        overflowDecayPercent = d("mana.overflow.decay-percent-per-second", 2.0);
        hudPeriodTicks = Math.max(1, i("hud.period-ticks", 5));
        hudKeepAliveTicks = Math.max(5, i("hud.keep-alive-ticks", 40));
        xpPopupSeconds = Math.max(1, i("hud.xp-popup-seconds", 3));

        fortuneOres = upperSet("fortune.ores", List.of("COAL_ORE", "DEEPSLATE_COAL_ORE", "IRON_ORE",
            "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
            "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "DIAMOND_ORE",
            "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE",
            "ANCIENT_DEBRIS", "GLOWSTONE", "AMETHYST_CLUSTER"));
        fortuneCrops = upperSet("fortune.crops", List.of("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART",
            "COCOA", "MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "SWEET_BERRY_BUSH"));
        fortuneLogs = upperSet("fortune.logs", List.of());
        placedBlockMemory = Math.max(0, i("fortune.placed-block-memory", 50000));

        requireEconomy = b("costs.require-economy", false);
        enchantCostBase = d("costs.enchant-table.base", 150);
        enchantCostGrowth = d("costs.enchant-table.growth-per-level", 1.9);
        enchantXpPerLevel = d("costs.enchant-table.enchanting-xp-per-level", 12);
        anvilCostMode = s("costs.anvil.mode", "coins").toLowerCase(Locale.ROOT);
        anvilCostPerLevel = d("costs.anvil.per-enchant-level", 250);
        reforgeCostBase = d("costs.reforge.base", 400);
        bagUpgradeCost = d("costs.accessory-bag.upgrade-base", 25000);
        bagUpgradeGrowth = d("costs.accessory-bag.upgrade-growth", 1.8);

        bagBaseSlots = Math.max(1, Math.min(45, i("accessories.base-slots", 9)));
        bagSlotsPerUpgrade = Math.max(1, i("accessories.slots-per-upgrade", 3));
        bagMaxSlots = Math.max(bagBaseSlots, Math.min(45, i("accessories.max-slots", 45)));
        magicalPower = ints("accessories.magical-power", Map.of("COMMON", 3, "UNCOMMON", 5, "RARE", 8, "EPIC", 12,
            "LEGENDARY", 16, "MYTHIC", 22, "DIVINE", 30, "SPECIAL", 3));
        Map<Stat, Double> mp = new EnumMap<>(Stat.class);
        ConfigurationSection per = root.getConfigurationSection("accessories.per-magical-power");
        if (per != null) {
            for (String key : per.getKeys(false)) {
                try {
                    mp.put(Stat.valueOf(key.toUpperCase(Locale.ROOT)), per.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                    // unknown stat name in the config: skipped
                }
            }
        } else {
            mp.put(Stat.HEALTH, 0.6);
            mp.put(Stat.DEFENSE, 0.25);
            mp.put(Stat.STRENGTH, 0.3);
            mp.put(Stat.INTELLIGENCE, 0.35);
            mp.put(Stat.CRIT_DAMAGE, 0.1);
            mp.put(Stat.SPEED, 0.03);
        }
        perMagicalPower = mp;

        petMaxOwned = Math.max(1, i("pets.max-owned", 45));
        petHideFromOwner = b("pets.hide-from-owner", false);
        petXpShare = d("pets.xp-per-skill-xp", 1.0);
        petOtherSkillShare = d("pets.other-skill-share", 0.0);
        petBaseXp = d("pets.xp-curve.base", 100);
        petXpGrowth = d("pets.xp-curve.growth", 1.075);
        petRarityXp = doubles("pets.xp-curve.rarity-multiplier", Map.of("COMMON", 0.4, "UNCOMMON", 0.7, "RARE", 1.0,
            "EPIC", 1.6, "LEGENDARY", 2.4, "MYTHIC", 3.4));
        petUpdateTicks = Math.max(1, i("pets.update-ticks", 2));
    }

    public ConfigurationSection root() { return root; }

    // ---------------------------------------------------------------- generic readers (used by feature classes)

    public double d(String path, double def) { return root.getDouble(path, def); }

    public int i(String path, int def) { return root.getInt(path, def); }

    public boolean b(String path, boolean def) { return root.getBoolean(path, def); }

    public String s(String path, String def) { return root.getString(path, def); }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private Set<String> upperSet(String path, List<String> def) {
        List<String> list = root.isList(path) ? root.getStringList(path) : def;
        return list.stream().map(x -> x.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> lowerSet(String path, List<String> def) {
        List<String> list = root.isList(path) ? root.getStringList(path) : def;
        return list.stream().map(x -> x.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    private Map<String, Double> doubles(String path, Map<String, Double> def) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            return Map.copyOf(def);
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key.toUpperCase(Locale.ROOT), section.getDouble(key));
        }
        return Map.copyOf(out);
    }

    private Map<String, Integer> ints(String path, Map<String, Integer> def) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) {
            return Map.copyOf(def);
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key.toUpperCase(Locale.ROOT), section.getInt(key));
        }
        return Map.copyOf(out);
    }

    /** A list of strings, or the default when the key is missing. */
    public List<String> strings(String path, List<String> def) {
        return root.isList(path) ? new ArrayList<>(root.getStringList(path)) : def;
    }

    /** A double from {@code path}, falling back to {@code def}; used for per-item tuning like {@code abilities.<id>.mana}. */
    public double tuned(String path, double def) {
        return root.contains(path) ? root.getDouble(path) : def;
    }
}
