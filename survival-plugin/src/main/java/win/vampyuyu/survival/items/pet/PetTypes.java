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
            PetType.stats(Stat.STRENGTH, 0.35, Stat.CRIT_DAMAGE, 0.4, Stat.SPEED, 0.1),
            List.of(Perk.stat("Alpha Dog", Stat.CRIT_CHANCE, 0, 0.05, "<gray>Grants <blue>+{v}% Crit Chance</blue>."),
                Perk.damageVs("Skeleton Chaser", Set.of(EntityType.SKELETON, EntityType.ZOMBIE, EntityType.HUSK, EntityType.STRAY,
                    EntityType.DROWNED), 0, 0.25, "<gray>Deal <green>+{v}%</green> damage to undead."))));
        add(new PetType("rabbit", "Rabbit", Skill.FARMING,
            PetType.stats(Stat.SPEED, 0.3, Stat.HEALTH, 1.2),
            List.of(Perk.potion("Springy Legs", PotionEffectType.JUMP_BOOST, "<gray>Grants <green>Jump Boost</green>."),
                Perk.stat("Farmer's Luck", Stat.FARMING_FORTUNE, 0, 0.3, "<gray>Grants <gold>+{v} Farming Fortune</gold>."))));
        add(new PetType("enderman", "Enderman", Skill.COMBAT,
            PetType.stats(Stat.CRIT_DAMAGE, 0.5, Stat.INTELLIGENCE, 0.6, Stat.STRENGTH, 0.2),
            List.of(Perk.damageVs("Enderian", Set.of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER,
                    EntityType.ENDER_DRAGON), 0, 0.5, "<gray>Deal <green>+{v}%</green> damage to End mobs."),
                Perk.stat("Teleport Savvy", Stat.ABILITY_DAMAGE, 0, 0.1, "<gray>Grants <red>+{v}% Ability Damage</red>."))));
        add(new PetType("phoenix", "Phoenix", Skill.COMBAT,
            PetType.stats(Stat.INTELLIGENCE, 1.0, Stat.STRENGTH, 0.4, Stat.ABILITY_DAMAGE, 0.1),
            List.of(Perk.revive("Rekindle", 30, "<gray>When you would die, revive with <green>{v}%</green> health."),
                Perk.stat("Ashen Wings", Stat.SPEED, 0, 0.1, "<gray>Grants <white>+{v} Speed</white>."))));
        add(new PetType("ender_dragon", "Ender Dragon", Skill.COMBAT,
            PetType.stats(Stat.STRENGTH, 0.5, Stat.CRIT_DAMAGE, 0.7, Stat.CRIT_CHANCE, 0.1, Stat.INTELLIGENCE, 0.4),
            List.of(Perk.damageVs("End Strike", Set.of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER,
                    EntityType.ENDER_DRAGON), 0, 0.6, "<gray>Deal <green>+{v}%</green> damage to End mobs."),
                Perk.stat("One With the Dragon", Stat.STRENGTH, 0, 0.15, "<gray>Grants <red>+{v} Strength</red>."))));
        add(new PetType("tiger", "Tiger", Skill.COMBAT,
            PetType.stats(Stat.CRIT_CHANCE, 0.15, Stat.CRIT_DAMAGE, 0.8, Stat.FEROCITY, 0.25, Stat.STRENGTH, 0.3),
            List.of(Perk.stat("Merciless Swipe", Stat.FEROCITY, 0, 0.1, "<gray>Grants <red>+{v} Ferocity</red>."),
                Perk.damageVs("Hunter's Instinct", Set.of(EntityType.PIG, EntityType.COW, EntityType.SHEEP,
                    EntityType.CHICKEN, EntityType.RABBIT), 0, 0.3, "<gray>Deal <green>+{v}%</green> damage to animals."))));
        add(new PetType("elephant", "Elephant", Skill.FARMING,
            PetType.stats(Stat.HEALTH, 4.0, Stat.DEFENSE, 0.7, Stat.INTELLIGENCE, 0.3),
            List.of(Perk.stat("Stomp", Stat.DEFENSE, 0, 0.35, "<gray>Grants <green>+{v} Defense</green>."),
                Perk.stat("Trunk Efficiency", Stat.FARMING_FORTUNE, 0, 0.5, "<gray>Grants <gold>+{v} Farming Fortune</gold>."))));
        add(new PetType("turtle", "Turtle", Skill.COMBAT,
            PetType.stats(Stat.DEFENSE, 1.2, Stat.TRUE_DEFENSE, 0.1, Stat.HEALTH, 1.5),
            List.of(Perk.reduction("Turtle Tactics", 0, 0.15, "<gray>Take <green>{v}%</green> less damage."),
                Perk.stat("Genius Amniote", Stat.DEFENSE, 0, 0.3, "<gray>Grants <green>+{v} Defense</green>."))));
    }

    public static Optional<PetType> get(String id) {
        return Optional.ofNullable(ALL.get(id));
    }

    public static java.util.Collection<PetType> all() {
        return ALL.values();
    }
}
