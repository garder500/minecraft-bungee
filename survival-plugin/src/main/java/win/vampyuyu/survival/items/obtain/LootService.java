package win.vampyuyu.survival.items.obtain;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.combat.MobScaling;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Gives the catalog a way into the game: boss kills (a {@code BossApi} loot hook registered lazily, because the boss
 * feature enables after this one), natural mob kills and fishing. Chances come from {@code loot.entries} and are boosted
 * by the player's Magic Find (Pet Luck for pet eggs). Spawner, summoned and spawn-egg mobs never drop loot.
 */
public final class LootService implements Listener {
    private static final int MAX_ATTEMPTS = 30;

    private final Core core;
    private final StatsEngine engine;
    private final LootRoller roller;
    private Scheduler.Handle lookup;
    private boolean hooked;

    public LootService(Core core, StatsEngine engine, LootRoller roller) {
        this.core = core;
        this.engine = engine;
        this.roller = roller;
    }

    private ItemsSettings settings() { return engine.settings(); }

    public boolean hooked() { return hooked; }

    /** Looks BossApi up a few ticks after enable (the boss feature loads later) and retries every second. */
    public void start() {
        attempt(1);
    }

    private void attempt(int number) {
        lookup = core.scheduler().later(number == 1 ? 2 : 20, () -> {
            if (hooked) {
                return;
            }
            var api = core.service(BossApi.class);
            if (api.isPresent()) {
                api.get().registerLootHook((event, recipient) -> bossLoot(event.getBossId(), recipient));
                hooked = true;
            } else if (number < MAX_ATTEMPTS) {
                attempt(number + 1);
            }
        });
    }

    public void stop() {
        if (lookup != null) {
            lookup.cancel();
        }
    }

    /** The boss loot of one participant. */
    public List<ItemStack> bossLoot(String bossId, Player recipient) {
        if (recipient == null || !recipient.isOnline() || !settings().lootEnabled) {
            return List.of();
        }
        return roller.roll(settings(), "boss", bossId, luck(recipient, Stat.MAGIC_FIND), luck(recipient, Stat.PET_LUCK), 1);
    }

    private double luck(Player player, Stat stat) {
        return engine.state(player).stat(stat);
    }

    /** Pure: whether a mob death may drop loot. */
    public static boolean eligible(boolean natural, boolean hasKiller, boolean levelled, boolean tamed) {
        return natural && hasKiller && levelled && !tamed;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (!(entity instanceof Mob) || killer == null || !killer.isOnline() || !engine.active(killer)
                || entity.hasMetadata("NPC") || !settings().lootEnabled) {
            return;
        }
        String reason = entity.getEntitySpawnReason().name();
        int level = MobScaling.levelOf(entity);
        if (!eligible(settings().lootMobSpawnReasons.contains(reason), true, level > 0, entity instanceof Tameable)) {
            return;
        }
        double multiplier = 1 + level * settings().lootMobLevelBonus;
        for (ItemStack stack : roller.roll(settings(), "mob", entity.getType().name(),
                luck(killer, Stat.MAGIC_FIND), luck(killer, Stat.PET_LUCK), multiplier)) {
            event.getDrops().add(stack);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !engine.active(player) || !settings().lootEnabled) {
            return;
        }
        for (ItemStack stack : roller.roll(settings(), "fishing", "", luck(player, Stat.MAGIC_FIND),
                luck(player, Stat.PET_LUCK), 1)) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            left.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
    }
}
