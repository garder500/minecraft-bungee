package win.vampyuyu.survival.boss;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Spawn reasons a player can farm. Mobs that came from them give no slayer progress and no summon item drops (unless
 * a mob-drops entry sets {@code allow-spawner: true}), otherwise a spawner, a breeding pen or a built golem would
 * mint summon items and quests for free.
 */
public final class SpawnSources {
    private static final Set<SpawnReason> ARTIFICIAL = EnumSet.of(SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER,
        SpawnReason.SPAWNER_EGG, SpawnReason.DISPENSE_EGG, SpawnReason.BREEDING, SpawnReason.EGG,
        SpawnReason.BUILD_SNOWMAN, SpawnReason.BUILD_IRONGOLEM, SpawnReason.BUILD_WITHER, SpawnReason.INFECTION,
        SpawnReason.CURED, SpawnReason.SLIME_SPLIT);

    private SpawnSources() {}

    public static boolean isArtificial(SpawnReason reason) {
        return reason != null && ARTIFICIAL.contains(reason);
    }

    /** True when the entity was spawned by a farmable source. An unknown reason counts as natural. */
    public static boolean isArtificial(LivingEntity entity) {
        try {
            return isArtificial(entity.getEntitySpawnReason());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
