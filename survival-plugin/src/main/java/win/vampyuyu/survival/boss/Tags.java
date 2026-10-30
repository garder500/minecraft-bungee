package win.vampyuyu.survival.boss;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** PDC keys written on boss entities, minions, crystals and dropped loot. */
public final class Tags {
    /** Boss id on the boss entity. */
    public static final NamespacedKey BOSS = new NamespacedKey("vampyuyu", "boss_id");
    /** Session id on the boss entity. */
    public static final NamespacedKey SESSION = new NamespacedKey("vampyuyu", "boss_session");
    /** Session id on minions and crystals. */
    public static final NamespacedKey MINION = new NamespacedKey("vampyuyu", "boss_minion");
    /** Owner UUID on dropped loot. */
    public static final NamespacedKey LOOT_OWNER = new NamespacedKey("vampyuyu", "boss_loot_owner");

    private Tags() {}

    public static @Nullable String bossId(@Nullable Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(BOSS, PersistentDataType.STRING);
    }

    public static @Nullable String session(@Nullable Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(SESSION, PersistentDataType.STRING);
    }

    public static @Nullable String minionOf(@Nullable Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(MINION, PersistentDataType.STRING);
    }

    /** True for any entity this feature spawned (boss, minion, crystal). */
    public static boolean isOurs(@Nullable Entity entity) {
        return bossId(entity) != null || minionOf(entity) != null;
    }
}
