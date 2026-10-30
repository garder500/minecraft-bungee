package win.vampyuyu.survival.items.combat;

import java.util.Map;
import java.util.Set;
import org.bukkit.entity.EntityType;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;

/** Additive damage bonus (as a fraction) that the combat enchants give against one target (pure). */
public final class EnchantCombat {
    private EnchantCombat() {}

    private static final Set<EntityType> CUBISM = Set.of(EntityType.CREEPER, EntityType.SLIME, EntityType.MAGMA_CUBE);
    private static final Set<EntityType> UNDEAD = Set.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK,
        EntityType.DROWNED, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.ZOMBIE_VILLAGER, EntityType.PHANTOM,
        EntityType.ZOGLIN, EntityType.ZOMBIFIED_PIGLIN, EntityType.WITHER, EntityType.SKELETON_HORSE,
        EntityType.ZOMBIE_HORSE, EntityType.BOGGED);
    private static final Set<EntityType> ARTHROPODS = Set.of(EntityType.SPIDER, EntityType.CAVE_SPIDER,
        EntityType.SILVERFISH, EntityType.ENDERMITE);
    private static final Set<EntityType> ENDER = Set.of(EntityType.ENDERMAN, EntityType.ENDERMITE,
        EntityType.ENDER_DRAGON, EntityType.SHULKER);

    /** What the enchants need to know about the hit. */
    public record Target(EntityType type, double health, double maxHealth, double attackerMaxHealth, boolean firstHit) {}

    /**
     * Sum of the melee enchant bonuses. {@code oneForAll} wins over everything else: only its own bonus counts.
     */
    public static double melee(Map<String, Integer> levels, EnchantRegistry registry, Target target) {
        if (levels.containsKey("one_for_all")) {
            return registry.value("one_for_all", levels.get("one_for_all")) / 100.0;
        }
        double sum = 0;
        sum += registry.value("sharpness", levels.getOrDefault("sharpness", 0)) / 100.0;
        sum += DamageFormula.execute(levels.getOrDefault("execute", 0), registry.perLevel("execute"), target.health(),
            target.maxHealth()) ;
        sum += DamageFormula.giantKiller(levels.getOrDefault("giant_killer", 0), registry.perLevel("giant_killer"),
            target.maxHealth(), target.attackerMaxHealth());
        if (target.firstHit()) {
            sum += registry.value("first_strike", levels.getOrDefault("first_strike", 0)) / 100.0;
        }
        if (CUBISM.contains(target.type())) {
            sum += registry.value("cubism", levels.getOrDefault("cubism", 0)) / 100.0;
        }
        if (UNDEAD.contains(target.type())) {
            sum += registry.value("smite", levels.getOrDefault("smite", 0)) / 100.0;
        }
        if (ARTHROPODS.contains(target.type())) {
            sum += registry.value("bane_of_arthropods", levels.getOrDefault("bane_of_arthropods", 0)) / 100.0;
        }
        if (ENDER.contains(target.type())) {
            sum += registry.value("ender_slayer", levels.getOrDefault("ender_slayer", 0)) / 100.0;
        }
        return sum;
    }

    /** Bow enchants: Power plus Snipe by distance plus the ultimate Rend. */
    public static double ranged(Map<String, Integer> levels, EnchantRegistry registry, double distance) {
        if (levels.containsKey("one_for_all")) {
            return 0;
        }
        double sum = registry.value("power", levels.getOrDefault("power", 0)) / 100.0;
        sum += DamageFormula.snipe(levels.getOrDefault("snipe", 0), registry.perLevel("snipe"), distance);
        sum += registry.value("rend", levels.getOrDefault("rend", 0)) / 100.0;
        return sum;
    }
}
