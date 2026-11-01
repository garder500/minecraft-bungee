package win.vampyuyu.survival.items.pet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Skill;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.items.pet.PetType.Perk;

/** The eight pet species. */
public final class PetTypes {
    private PetTypes() {}

    private static final Map<String, PetType> ALL = new LinkedHashMap<>();

    private static void add(PetType type) {
        ALL.put(type.id(), type);
    }

    static {
        add(new PetType("wolf", "Wolf", Skill.COMBAT,
            PetType.stats(Stat.MIGHT, 0.45, Stat.SWIFTNESS, 0.1),
            List.of(Perk.stat("Alpha Dog", Stat.ATTUNEMENT, 0, 0.05, "<gray>Grants <light_purple>+{v}% Attunement</light_purple>."),
                Perk.damageVs("Skeleton Chaser", Set.of(EntityType.SKELETON, EntityType.ZOMBIE, EntityType.HUSK, EntityType.STRAY,
                    EntityType.DROWNED), 0, 0.25, "<gray>Deal <green>+{v}%</green> damage to undead."))));
        add(new PetType("rabbit", "Rabbit", Skill.FARMING,
            PetType.stats(Stat.SWIFTNESS, 0.3, Stat.VITALITY, 1.2),
            List.of(Perk.potion("Springy Legs", PotionEffectType.JUMP_BOOST, "<gray>Grants <green>Jump Boost</green>."),
                Perk.stat("Farmer's Luck", Stat.FARMING_YIELD, 0, 0.3, "<gray>Grants <gold>+{v} Farming Yield</gold>."))));
        add(new PetType("enderman", "Enderman", Skill.COMBAT,
            PetType.stats(Stat.AETHER, 0.6, Stat.MIGHT, 0.325),
            List.of(Perk.damageVs("Enderian", Set.of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER,
                    EntityType.ENDER_DRAGON), 0, 0.5, "<gray>Deal <green>+{v}%</green> damage to End mobs."),
                Perk.stat("Teleport Savvy", Stat.MIGHT, 0, 0.1, "<gray>Grants <red>+{v} Might</red>."))));
        add(new PetType("phoenix", "Phoenix", Skill.COMBAT,
            PetType.stats(Stat.AETHER, 1.0, Stat.MIGHT, 0.5),
            List.of(Perk.revive("Rekindle", 30, "<gray>When you would die, revive with <green>{v}%</green> health."),
                Perk.stat("Ashen Wings", Stat.SWIFTNESS, 0, 0.1, "<gray>Grants <white>+{v} Swiftness</white>."))));
        add(new PetType("ender_dragon", "Ender Dragon", Skill.COMBAT,
            PetType.stats(Stat.MIGHT, 0.675, Stat.ATTUNEMENT, 0.02, Stat.AETHER, 0.4),
            List.of(Perk.damageVs("End Strike", Set.of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER,
                    EntityType.ENDER_DRAGON), 0, 0.6, "<gray>Deal <green>+{v}%</green> damage to End mobs."),
                Perk.stat("One With the Dragon", Stat.MIGHT, 0, 0.15, "<gray>Grants <red>+{v} Might</red>."))));
        add(new PetType("tiger", "Tiger", Skill.COMBAT,
            PetType.stats(Stat.ATTUNEMENT, 0.03, Stat.MIGHT, 0.625),
            List.of(Perk.stat("Merciless Swipe", Stat.MIGHT, 0, 0.1, "<gray>Grants <red>+{v} Might</red>."),
                Perk.damageVs("Hunter's Instinct", Set.of(EntityType.PIG, EntityType.COW, EntityType.SHEEP,
                    EntityType.CHICKEN, EntityType.RABBIT), 0, 0.3, "<gray>Deal <green>+{v}%</green> damage to animals."))));
        add(new PetType("elephant", "Elephant", Skill.FARMING,
            PetType.stats(Stat.VITALITY, 4.0, Stat.WARD, 0.7, Stat.AETHER, 0.3),
            List.of(Perk.stat("Stomp", Stat.WARD, 0, 0.35, "<gray>Grants <green>+{v} Ward</green>."),
                Perk.stat("Trunk Efficiency", Stat.FARMING_YIELD, 0, 0.5, "<gray>Grants <gold>+{v} Farming Yield</gold>."))));
        add(new PetType("turtle", "Turtle", Skill.COMBAT,
            PetType.stats(Stat.WARD, 1.3, Stat.VITALITY, 1.5),
            List.of(Perk.reduction("Turtle Tactics", 0, 0.15, "<gray>Take <green>{v}%</green> less damage."),
                Perk.stat("Genius Amniote", Stat.WARD, 0, 0.3, "<gray>Grants <green>+{v} Ward</green>."))));
    }

    public static Optional<PetType> get(String id) {
        return Optional.ofNullable(ALL.get(id));
    }

    public static java.util.Collection<PetType> all() {
        return ALL.values();
    }
}
