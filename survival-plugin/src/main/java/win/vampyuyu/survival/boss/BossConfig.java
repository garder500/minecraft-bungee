package win.vampyuyu.survival.boss;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.boss.slayer.SlayerDefinition;
import win.vampyuyu.survival.boss.world.WorldBossConfig;
import win.vampyuyu.survival.data.Db;

/**
 * Everything read from the boss tables of the content database, validated. Invalid entries are dropped and listed in {@link #errors()}; the rest
 * still loads. Immutable: a reload builds a new instance.
 */
public record BossConfig(BossSettings settings, Map<String, BossDefinition> bosses,
        Map<String, SlayerDefinition> slayers, WorldBossConfig worldBoss, SlayerSettings slayerSettings,
        List<MobDrop> mobDrops, Map<String, RecipeSpec> recipes, Map<String, ItemText> itemOverrides,
        Guardians guardians, List<String> errors) {

    /**
     * Slayer behaviour.
     *
     * @param enabled master switch
     * @param ignoreSpawnerMobs mobs from monster spawners give no quest progress
     * @param spawnMinDistance the boss appears at least this far from the player
     * @param spawnMaxDistance and at most this far
     * @param spawnCountdownSeconds warning before the boss appears
     */
    public record SlayerSettings(boolean enabled, boolean ignoreSpawnerMobs, double spawnMinDistance,
            double spawnMaxDistance, int spawnCountdownSeconds) {}

    /** Extra drops of vanilla mobs (sigils, eggs, scales). */
    public record MobDrop(String item, Set<EntityType> entities, Set<String> worlds, double chance,
            boolean magicFind, boolean allowSpawner) {}

    /** Crafting recipe of a custom item. */
    public record RecipeSpec(String item, List<String> shape, Map<Character, Material> ingredients) {}

    /** Display override of a boss item: a null name or lore keeps the built-in one. */
    public record ItemText(@Nullable String name, @Nullable List<String> lore) {}

    public Optional<BossDefinition> definition(String id) {
        BossDefinition def = bosses.get(id);
        if (def != null) {
            return Optional.of(def);
        }
        for (SlayerDefinition slayer : slayers.values()) {
            for (SlayerDefinition.Tier tier : slayer.tiers()) {
                if (tier.boss().id().equals(id)) {
                    return Optional.of(tier.boss());
                }
            }
        }
        return Optional.empty();
    }

    /** Reads the boss tables of the content database. Never throws for bad content. */
    public static BossConfig load(Db db) {
        return BossRepo.load(db);
    }
}
