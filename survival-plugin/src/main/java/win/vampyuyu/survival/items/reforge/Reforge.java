package win.vampyuyu.survival.items.reforge;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.gear.ItemKind;

/**
 * A reforge: a name prefix and stat bonuses that scale with the item's rarity.
 *
 * @param id lower case id stored in the item PDC
 * @param name prefix shown on the item ("Sharp Aspect of the Void")
 * @param types item groups that can carry it
 * @param base bonuses at COMMON rarity, multiplied by {@link ReforgeRegistry#scale} for the real rarity
 * @param stoneOnly true when only a reforge stone can apply it (never rolled at the reforge anvil)
 * @param stoneId id of the stone item that applies it, or null
 */
public record Reforge(String id, String name, Set<ItemKind.Group> types, Map<Stat, Double> base, boolean stoneOnly,
        String stoneId) {

    public Reforge {
        types = Set.copyOf(types.isEmpty() ? EnumSet.noneOf(ItemKind.Group.class) : EnumSet.copyOf(types));
        EnumMap<Stat, Double> copy = new EnumMap<>(Stat.class);
        copy.putAll(base);
        base = java.util.Collections.unmodifiableMap(copy);
    }

    public boolean appliesTo(ItemKind kind) {
        return types.contains(kind.group());
    }

    /** Bonuses at a rarity, using the given multiplier (see {@link ReforgeRegistry#scale}). */
    public Map<Stat, Double> at(Rarity rarity, double multiplier) {
        EnumMap<Stat, Double> out = new EnumMap<>(Stat.class);
        base.forEach((stat, value) -> out.put(stat, round(value * multiplier)));
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
