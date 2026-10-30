package win.vampyuyu.survival.items.enchant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.gear.ItemKind;

/** All enchants: built-in definitions whose numbers can be overridden in {@code items.yml} under {@code enchants.<id>}. */
public final class EnchantRegistry {
    private static final Set<ItemKind> SWORDS = EnumSet.of(ItemKind.SWORD, ItemKind.AXE, ItemKind.STAFF, ItemKind.WAND,
        ItemKind.TRIDENT);
    private static final Set<ItemKind> BOWS = EnumSet.of(ItemKind.BOW, ItemKind.CROSSBOW);
    private static final Set<ItemKind> TOOLS = EnumSet.of(ItemKind.PICKAXE, ItemKind.DRILL, ItemKind.AXE, ItemKind.SHOVEL);
    private static final Set<ItemKind> ARMOR = EnumSet.of(ItemKind.HELMET, ItemKind.CHESTPLATE, ItemKind.LEGGINGS,
        ItemKind.BOOTS);
    private static final Set<ItemKind> MINING = EnumSet.of(ItemKind.PICKAXE, ItemKind.DRILL);
    private static final Set<ItemKind> HOES = EnumSet.of(ItemKind.HOE);

    private final Map<String, Enchant> byId = new LinkedHashMap<>();

    public EnchantRegistry(@Nullable ConfigurationSection config) {
        // melee
        add("sharpness", "Sharpness", 7, false, SWORDS, null, 5, "Increases melee damage by <green>{v}%</green>.", 5);
        add("critical", "Critical", 7, false, merge(SWORDS, BOWS), Stat.CRIT_DAMAGE, 10,
            "Grants <green>+{v}%</green> Crit Damage.", 5);
        add("execute", "Execute", 5, false, SWORDS, null, 0.2,
            "Deals <green>{v}%</green> more damage for every 1% of health the enemy is missing.", 5);
        add("life_steal", "Life Steal", 5, false, SWORDS, null, 0.5,
            "Heals you for <green>{v}%</green> of your max health on hit.", 5);
        add("giant_killer", "Giant Killer", 7, false, SWORDS, null, 5,
            "Deals up to <green>{v}%</green> more damage to enemies with more health than you.", 5);
        add("first_strike", "First Strike", 5, false, SWORDS, null, 25,
            "The first hit on an enemy deals <green>{v}%</green> more damage.", 4);
        add("looting", "Looting", 5, false, merge(SWORDS, BOWS), null, 15,
            "Gives a <green>{v}%</green> chance to double the drops of slain mobs.", 4);
        add("cubism", "Cubism", 5, false, SWORDS, null, 10,
            "Increases damage dealt to Creepers, Slimes and Magma Cubes by <green>{v}%</green>.", 5);
        add("smite", "Smite", 7, false, SWORDS, null, 8,
            "Increases damage dealt to undead mobs by <green>{v}%</green>.", 5);
        add("bane_of_arthropods", "Bane of Arthropods", 7, false, SWORDS, null, 8,
            "Increases damage dealt to spiders and silverfish by <green>{v}%</green>.", 5);
        add("ender_slayer", "Ender Slayer", 7, false, SWORDS, null, 12,
            "Increases damage dealt to Endermen, Endermites and the Dragon by <green>{v}%</green>.", 5);
        add("cleave", "Cleave", 5, false, SWORDS, null, 20,
            "Deals <green>{v}%</green> of your damage to enemies near your target.", 5);
        // bows
        add("power", "Power", 7, false, BOWS, null, 8, "Increases arrow damage by <green>{v}%</green>.", 5);
        add("snipe", "Snipe", 4, false, BOWS, null, 1.5,
            "Arrows deal <green>{v}%</green> more damage for every block beyond 5 (max 60%).", 3);
        add("infinite_quiver", "Infinite Quiver", 10, false, BOWS, null, 4,
            "Gives a <green>{v}%</green> chance not to consume an arrow.", 5);
        // armor
        add("protection", "Protection", 7, false, ARMOR, Stat.DEFENSE, 3, "Grants <green>+{v}</green> Defense.", 5);
        add("growth", "Growth", 7, false, ARMOR, Stat.HEALTH, 15, "Grants <green>+{v}</green> Health.", 5);
        add("rejuvenate", "Rejuvenate", 5, false, ARMOR, null, 0.5,
            "Regenerates an extra <green>{v}%</green> of your max health every second.", 5);
        add("thorns", "Thorns", 5, false, ARMOR, null, 3,
            "Reflects <green>{v}%</green> of the damage you take back at the attacker.", 4);
        add("feather_falling", "Feather Falling", 10, false, EnumSet.of(ItemKind.BOOTS), null, 8,
            "Reduces fall damage by <green>{v}%</green>.", 5);
        add("respiration", "Respiration", 5, false, EnumSet.of(ItemKind.HELMET), null, 20,
            "Reduces drowning damage by <green>{v}%</green>.", 4);
        // tools
        add("efficiency", "Efficiency", 10, false, TOOLS, Stat.MINING_SPEED, 20,
            "Grants <green>+{v}</green> Mining Speed.", 5);
        add("fortune", "Fortune", 4, false, merge(TOOLS, HOES), null, 10,
            "Grants <green>+{v}</green> Fortune for the tool's kind of work.", 4);
        add("smelting_touch", "Smelting Touch", 1, false, merge(MINING, EnumSet.of(ItemKind.SHOVEL)), null, 1,
            "Automatically smelts ores and sand you mine.", 1);
        add("telekinesis", "Telekinesis", 1, false, merge(TOOLS, merge(HOES, SWORDS)), null, 1,
            "Block and mob drops go straight into your inventory.", 1);
        add("replenish", "Replenish", 1, false, HOES, null, 1,
            "Replants crops you harvest, using one seed from the drops.", 1);
        add("harvesting", "Harvesting", 6, false, HOES, Stat.FARMING_FORTUNE, 12.5,
            "Grants <green>+{v}</green> Farming Fortune.", 5);
        // ultimates
        add("soul_eater", "Soul Eater", 5, true, SWORDS, null, 40,
            "Your next hit after a kill deals <green>{v}%</green> more damage.", 0);
        add("one_for_all", "One For All", 1, true, SWORDS, null, 200,
            "Your hits deal <green>{v}%</green> more damage but every other enchant on the item stops working.", 0);
        add("rend", "Rend", 5, true, BOWS, null, 10,
            "Arrows deal <green>{v}%</green> more damage and make the target bleed for 3 seconds.", 0);
        add("wise", "Wise", 5, true, ARMOR, null, 10, "Reduces the mana cost of abilities by <green>{v}%</green>.", 0);
        add("last_stand", "Last Stand", 5, true, ARMOR, null, 15,
            "Below 40% health your Defense is increased by <green>{v}%</green>.", 0);
        if (config != null) {
            applyOverrides(config);
        }
    }

    private static Set<ItemKind> merge(Set<ItemKind> a, Set<ItemKind> b) {
        EnumSet<ItemKind> out = EnumSet.copyOf(a);
        out.addAll(b);
        return out;
    }

    private void add(String id, String name, int max, boolean ultimate, Set<ItemKind> applies, @Nullable Stat stat,
            double perLevel, String description, int tableMax) {
        byId.put(id, new Enchant(id, name, max, ultimate, applies, stat, perLevel, description, tableMax));
    }

    private void applyOverrides(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("enchants");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Enchant base = byId.get(id.toLowerCase(Locale.ROOT));
            ConfigurationSection e = section.getConfigurationSection(id);
            if (base == null || e == null) {
                continue;
            }
            if (!e.getBoolean("enabled", true)) {
                byId.remove(base.id());
                continue;
            }
            int max = Math.max(1, Math.min(10, e.getInt("max-level", base.maxLevel())));
            byId.put(base.id(), new Enchant(base.id(), base.name(), max, base.ultimate(), base.applies(), base.stat(),
                e.getDouble("per-level", base.perLevel()), base.description(),
                Math.min(max, e.getInt("table-max", base.tableMax()))));
        }
    }

    public Optional<Enchant> get(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Collection<Enchant> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    /** Strength of {@code id} at {@code level} (per-level value times level), 0 when unknown. */
    public double value(String id, int level) {
        Enchant enchant = byId.get(id);
        return enchant == null ? 0 : enchant.perLevel() * level;
    }

    /** Per-level value, 0 when the enchant does not exist. */
    public double perLevel(String id) {
        Enchant enchant = byId.get(id);
        return enchant == null ? 0 : enchant.perLevel();
    }

    /** Enchants the table offers for an item kind (ultimates excluded), in definition order. */
    public List<Enchant> tableFor(ItemKind kind) {
        List<Enchant> out = new ArrayList<>();
        for (Enchant enchant : byId.values()) {
            if (!enchant.ultimate() && enchant.tableMax() > 0 && enchant.appliesTo(kind)) {
                out.add(enchant);
            }
        }
        return out;
    }
}
