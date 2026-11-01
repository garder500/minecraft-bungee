package win.vampyuyu.survival.items.reforge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.gear.ItemKind;
import win.vampyuyu.survival.items.gear.ItemKind.Group;

/** All reforges and the rarity scaling table. Rarity multipliers come from {@code reforges.rarity-multiplier}. */
public final class ReforgeRegistry {
    private static final double[] DEFAULT_SCALE = {1.0, 1.4, 1.9, 2.6, 3.5, 4.6, 6.0, 1.0};

    private final Map<String, Reforge> byId = new LinkedHashMap<>();
    private final double[] scale = DEFAULT_SCALE.clone();

    public ReforgeRegistry(@Nullable ConfigurationSection config) {
        Set<Group> melee = EnumSet.of(Group.MELEE);
        Set<Group> bow = EnumSet.of(Group.RANGED);
        Set<Group> armor = EnumSet.of(Group.ARMOR);
        Set<Group> tool = EnumSet.of(Group.TOOL);
        Set<Group> acc = EnumSet.of(Group.ACCESSORY);
        // weapons
        add("sharp", "Sharp", melee, false, null, Stat.MIGHT, 3, Stat.ATTUNEMENT, 1);
        add("heroic", "Heroic", melee, false, null, Stat.MIGHT, 5, Stat.AETHER, 15);
        add("spicy", "Spicy", melee, false, null, Stat.MIGHT, 4, Stat.SWIFTNESS, 1);
        add("legendary", "Legendary", melee, false, null, Stat.MIGHT, 4, Stat.ATTUNEMENT, 1, Stat.AETHER, 8);
        add("fabled", "Fabled", melee, true, "reforge_stone_fabled", Stat.MIGHT, 7, Stat.ATTUNEMENT, 2);
        add("withered", "Withered", melee, true, "reforge_stone_withered", Stat.MIGHT, 10, Stat.ATTUNEMENT, 1);
        add("gentle", "Gentle", melee, false, null, Stat.MIGHT, 3, Stat.SWIFTNESS, 2);
        // bows
        add("deadly", "Deadly", bow, false, null, Stat.MIGHT, 4, Stat.ATTUNEMENT, 1);
        add("fine", "Fine", bow, false, null, Stat.MIGHT, 4);
        add("grand", "Grand", bow, false, null, Stat.MIGHT, 8);
        add("headstrong", "Headstrong", bow, false, null, Stat.MIGHT, 4, Stat.ATTUNEMENT, 1);
        // armor
        add("jaded", "Jaded", armor, false, null, Stat.VITALITY, 5, Stat.WARD, 4, Stat.AETHER, 2);
        add("pure", "Pure", armor, false, null, Stat.MIGHT, 2, Stat.AETHER, 3);
        add("giant", "Giant", armor, false, null, Stat.VITALITY, 12);
        add("fierce", "Fierce", armor, false, null, Stat.MIGHT, 3, Stat.WARD, 1);
        add("ancient", "Ancient", armor, true, "reforge_stone_ancient", Stat.MIGHT, 4, Stat.VITALITY, 8, Stat.WARD, 2);
        add("clean", "Clean", armor, false, null, Stat.WARD, 7);
        // tools
        add("fruitful", "Fruitful", tool, false, null, Stat.MINING_YIELD, 6, Stat.FARMING_YIELD, 6, Stat.FORAGING_YIELD, 6);
        add("swift", "Swift", tool, false, null, Stat.HASTE, 20, Stat.LURE, 5);
        add("refined", "Refined", tool, true, "reforge_stone_refined", Stat.HASTE, 35, Stat.MINING_YIELD, 10);
        add("blessed", "Blessed", tool, false, null, Stat.MINING_YIELD, 3, Stat.FARMING_YIELD, 3, Stat.FORAGING_YIELD, 3,
            Stat.HASTE, 10);
        // accessories
        add("simple", "Simple", acc, false, null, Stat.MIGHT, 1, Stat.AETHER, 1);
        add("bizarre", "Bizarre", acc, false, null, Stat.AETHER, 6, Stat.MIGHT, -2);
        add("ridiculous", "Ridiculous", acc, false, null, Stat.FORTUNE, 2, Stat.ATTUNEMENT, 1);
        if (config != null) {
            List<Double> table = config.getDoubleList("reforges.rarity-multiplier");
            for (int i = 0; i < scale.length && i < table.size(); i++) {
                scale[i] = table.get(i);
            }
            config.getStringList("reforges.disabled").forEach(byId::remove);
        }
    }

    private void add(String id, String name, Set<Group> types, boolean stoneOnly, @Nullable String stone,
            Object... pairs) {
        EnumMap<Stat, Double> stats = new EnumMap<>(Stat.class);
        for (int i = 0; i < pairs.length; i += 2) {
            stats.put((Stat) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        byId.put(id, new Reforge(id, name, types, stats, stoneOnly, stone));
    }

    public Optional<Reforge> get(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Collection<Reforge> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Multiplier applied to a reforge's COMMON values for an item of this rarity. */
    public double scale(Rarity rarity) {
        return scale[Math.min(rarity.ordinal(), scale.length - 1)];
    }

    /** Stat bonuses of {@code reforge} on an item of the given rarity. */
    public Map<Stat, Double> bonuses(@Nullable String reforgeId, Rarity rarity) {
        return get(reforgeId).map(r -> r.at(rarity, scale(rarity))).orElse(Map.of());
    }

    /** Reforges the anvil can roll for this kind (stone only reforges excluded). */
    public List<Reforge> rollable(ItemKind kind) {
        List<Reforge> out = new ArrayList<>();
        for (Reforge reforge : byId.values()) {
            if (!reforge.stoneOnly() && reforge.appliesTo(kind)) {
                out.add(reforge);
            }
        }
        return out;
    }

    /** Picks a rollable reforge different from {@code current}, or null when there is no other choice. */
    public @Nullable Reforge roll(ItemKind kind, @Nullable String current, Random random) {
        List<Reforge> pool = rollable(kind);
        pool.removeIf(r -> r.id().equals(current));
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    /** The reforge a stone item applies (by stone id), if any. */
    public Optional<Reforge> byStone(@Nullable String stoneId) {
        if (stoneId == null) {
            return Optional.empty();
        }
        return byId.values().stream().filter(r -> stoneId.equals(r.stoneId())).findFirst();
    }
}
