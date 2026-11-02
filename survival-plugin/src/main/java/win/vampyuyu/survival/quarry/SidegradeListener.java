package win.vampyuyu.survival.quarry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.core.Core;

/** The affinities of the Starcore sidegrades: Ashen Mantle (Cinder), the Grove helmet's breathing and Verdant Mending, Glacial Poise (Frost). */
final class SidegradeListener implements Listener {
    /** Heart (2 health points) mended every this many seconds by Verdant Mending. */
    static final int MEND_PERIOD_SECONDS = 5;

    private final Core core;
    private final Map<UUID, Long> lastHurt = new ConcurrentHashMap<>();
    private int seconds;

    SidegradeListener(Core core) {
        this.core = core;
    }

    private List<String> wornIds(Player player) {
        List<String> ids = new ArrayList<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            ids.add(core.items().idOf(piece).orElse(null));
        }
        return ids;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        String set = Sidegrades.fullSet(wornIds(player));
        double factor = Sidegrades.fireFactor(set, event.getCause()) * Sidegrades.poiseFactor(set, event.getCause());
        if (factor != 1.0) {
            event.setDamage(event.getDamage() * factor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastHurt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastHurt.remove(event.getPlayer().getUniqueId());
    }

    /** Once a second, main thread. */
    void tick() {
        seconds++;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> worn = wornIds(player);
            if (Sidegrades.isHelmet(worn.get(3), Sidegrades.GROVE)) { // armor contents: boots, leggings, chestplate, helmet
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, true, false, false));
            }
            String set = Sidegrades.fullSet(worn);
            if (seconds % MEND_PERIOD_SECONDS == 0 && Sidegrades.mends(set, lastHurt.getOrDefault(player.getUniqueId(), 0L), now)) {
                var max = player.getAttribute(Attribute.MAX_HEALTH);
                double limit = max == null ? 20 : max.getValue();
                if (!player.isDead() && player.getHealth() < limit) {
                    player.setHealth(Math.min(limit, player.getHealth() + 2));
                }
            }
        }
    }
}
