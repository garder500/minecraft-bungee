package win.vampyuyu.survival.core;

import com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * How charged a melee swing was. The server resets the attack charge before it fires the damage event, so
 * {@code getAttackCooldown()} is about 0 there and every hit looked like a spam click. Paper reports the charge just
 * before the reset ({@link PlayerAttackEntityCooldownResetEvent}); it is remembered for the damage event of the same tick.
 */
public final class SwingCharge implements Listener {
    private record Swing(double strength, int tick) {}

    private static final Map<UUID, Swing> LAST = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReset(PlayerAttackEntityCooldownResetEvent event) {
        LAST.put(event.getPlayer().getUniqueId(), new Swing(event.getCooledAttackStrength(), event.getPlayer().getServer().getCurrentTick()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LAST.remove(event.getPlayer().getUniqueId());
    }

    /**
     * The charge of the swing that is hitting now, 0.2 to 1: the value seen at the reset in this tick, else the live
     * cooldown (an arrow, a test), else 1 when the server cannot tell.
     */
    public static double of(Player player) {
        Swing swing = LAST.get(player.getUniqueId());
        try {
            if (swing != null && swing.tick() == player.getServer().getCurrentTick()) {
                return clamp(swing.strength());
            }
            return clamp(player.getAttackCooldown());
        } catch (RuntimeException | LinkageError e) {
            return 1.0;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.2, Math.min(1.0, value));
    }
}
