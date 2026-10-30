package win.vampyuyu.survival.boss.slayer;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.boss.BossDefinition;

/**
 * One slayer line (Zombie, Spider, Wolf, Enderman, Blaze) with five tiers.
 *
 * @param id yaml id, {@code [a-z0-9_]+}
 * @param name display name of the slayer boss family
 * @param icon menu icon
 * @param description menu text
 * @param targets vanilla mobs whose kills fill the quest progress
 * @param killXp quest progress per kill by mob type
 * @param defaultKillXp quest progress for a target without its own value
 * @param worlds worlds where kills count and the boss may spawn
 * @param levelXp slayer experience needed for levels 1..9 (cumulative)
 * @param perks permanent stat rewards by level
 * @param tiers exactly five tiers, index 0 = tier I
 */
public record SlayerDefinition(String id, String name, Material icon, String description, List<EntityType> targets,
        Map<EntityType, Double> killXp, double defaultKillXp, List<String> worlds, List<Long> levelXp,
        List<Perk> perks, List<Tier> tiers) {

    /** A stat bonus unlocked at a slayer level. */
    public record Perk(int level, Stat stat, double amount) {}

    /**
     * One tier.
     *
     * @param cost coins to start the quest
     * @param requiredXp quest progress needed to spawn the boss
     * @param slayerXp slayer experience for a win
     * @param timeLimitSeconds seconds to kill the boss before the quest fails
     * @param unlockLevel slayer level (of this slayer) needed to start the tier
     * @param boss generated boss definition, id {@code slayer_<id>_<tier>}
     */
    public record Tier(int tier, double cost, double requiredXp, long slayerXp, int timeLimitSeconds,
            int unlockLevel, BossDefinition boss) {}

    public SlayerDefinition {
        targets = List.copyOf(targets);
        killXp = Map.copyOf(killXp);
        worlds = List.copyOf(worlds);
        levelXp = List.copyOf(levelXp);
        perks = List.copyOf(perks);
        tiers = List.copyOf(tiers);
    }

    /** Quest progress granted by killing a {@code type}, 0 when it is not a target. */
    public double progressFor(EntityType type) {
        if (!targets.contains(type)) {
            return 0;
        }
        return killXp.getOrDefault(type, defaultKillXp);
    }

    public Tier tier(int tier) {
        return tiers.get(Math.max(1, Math.min(tiers.size(), tier)) - 1);
    }

    public static String bossId(String slayerId, int tier) {
        return "slayer_" + slayerId + "_" + tier;
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
