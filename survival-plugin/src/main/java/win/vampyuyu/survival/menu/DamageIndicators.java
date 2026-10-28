package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Floating damage numbers. A {@link TextDisplay} is spawned at the victim, hidden from everyone and shown only to the
 * attacker (and only when the attacker kept "Damage Indicators" on). Rate limited per attacker, short lived, never
 * persisted, and removed on disable.
 */
final class DamageIndicators implements Listener {
    private final MenuRuntime rt;
    private final NamespacedKey tag;
    private final List<TextDisplay> live = new ArrayList<>();
    private final Map<UUID, long[]> rate = new HashMap<>();

    DamageIndicators(MenuRuntime rt) {
        this.rt = rt;
        this.tag = new NamespacedKey(rt.core.getPlugin(), "menu_damage");
    }

    void stop() {
        for (TextDisplay display : live) {
            display.remove();
        }
        live.clear();
    }

    /** Formats a damage amount: 12 -> "12", 1234.5 -> "1.2k". */
    static String format(double damage) {
        return Msg.number(Math.round(damage));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        MenuConfig cfg = rt.config();
        if (!cfg.damageEnabled || !(event.getEntity() instanceof LivingEntity victim)
                || event.getEntity() instanceof org.bukkit.entity.ArmorStand) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || !attacker.isOnline() || !MenuRuntime.inSkyblock(victim.getWorld())
                || !rt.prefs(attacker.getUniqueId()).damageIndicators) {
            return;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0 || !allowed(attacker.getUniqueId())) {
            return;
        }
        boolean crit = event.getDamager() instanceof Player && attacker.getFallDistance() > 0
            && !attacker.isOnGround() && !attacker.isSprinting();
        Location at = victim.getLocation().add((Math.random() - 0.5) * 0.8, victim.getHeight() + 0.2,
            (Math.random() - 0.5) * 0.8);
        spawn(attacker, at, damage, crit);
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** At most {@code max-per-second} numbers per attacker. */
    private boolean allowed(UUID attacker) {
        long now = System.currentTimeMillis();
        long[] window = rate.computeIfAbsent(attacker, id -> new long[] {now, 0});
        if (now - window[0] >= 1000) {
            window[0] = now;
            window[1] = 0;
        }
        return ++window[1] <= rt.config().damageMaxPerSecond;
    }

    private void spawn(Player viewer, Location at, double damage, boolean crit) {
        Component text = crit
            ? Component.text("✧" + format(damage) + "✧", TextColor.color(0xFFAA00))
            : Component.text(format(damage), NamedTextColor.GRAY);
        TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.text(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.setDefaultBackground(false);
            e.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            e.setViewRange(0.4f);
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        viewer.showEntity(rt.core.getPlugin(), display);
        live.add(display);
        int lifetime = rt.config().damageLifetimeTicks;
        int[] age = {0};
        Scheduler.Handle[] handle = new Scheduler.Handle[1];
        handle[0] = rt.core.scheduler().repeating(1, 2, () -> {
            age[0] += 2;
            if (!display.isValid() || age[0] >= lifetime) {
                display.remove();
                live.remove(display);
                handle[0].cancel();
                return;
            }
            display.teleport(display.getLocation().add(0, 0.04, 0));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rate.remove(event.getPlayer().getUniqueId());
    }
}
