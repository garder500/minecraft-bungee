package win.vampyuyu.survival.items.pet;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;

/**
 * A pet species.
 *
 * @param id lower case id (item model {@code vampyuyu:items/pet_<id>})
 * @param name display name
 * @param skill the skill whose experience this pet levels with
 * @param perLevel stat gained per pet level at the base rarity multiplier
 * @param perks passive abilities
 */
public record PetType(String id, String name, Skill skill, Map<Stat, Double> perLevel, List<Perk> perks) {

    /** What a perk does. */
    public enum Kind { STAT, DAMAGE_VS, REVIVE, DAMAGE_REDUCTION, POTION }

    /**
     * One passive ability whose strength is {@code base + perLevel * level} (scaled by the rarity multiplier for
     * stat perks).
     *
     * @param stat the stat for {@link Kind#STAT} perks
     * @param targets entity types for {@link Kind#DAMAGE_VS}
     * @param potion effect for {@link Kind#POTION}
     * @param description MiniMessage with {@code {v}} for the current value
     */
    public record Perk(String name, Kind kind, Stat stat, Set<EntityType> targets, PotionEffectType potion, double base,
            double perLevel, String description) {
        public double value(int level) { return base + perLevel * level; }

        public static Perk stat(String name, Stat stat, double base, double perLevel, String description) {
            return new Perk(name, Kind.STAT, stat, Set.of(), null, base, perLevel, description);
        }

        public static Perk damageVs(String name, Set<EntityType> targets, double base, double perLevel, String description) {
            return new Perk(name, Kind.DAMAGE_VS, null, targets, null, base, perLevel, description);
        }

        public static Perk reduction(String name, double base, double perLevel, String description) {
            return new Perk(name, Kind.DAMAGE_REDUCTION, null, Set.of(), null, base, perLevel, description);
        }

        public static Perk revive(String name, double healthPercent, String description) {
            return new Perk(name, Kind.REVIVE, null, Set.of(), null, healthPercent, 0, description);
        }

        public static Perk potion(String name, PotionEffectType type, String description) {
            return new Perk(name, Kind.POTION, null, Set.of(), type, 0, 0, description);
        }
    }

    public static Map<Stat, Double> stats(Object... pairs) {
        Map<Stat, Double> map = new EnumMap<>(Stat.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Stat) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }
}
