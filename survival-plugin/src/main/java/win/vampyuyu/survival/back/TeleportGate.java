package win.vampyuyu.survival.back;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The rules every teleport of the plugin goes through (dungeon and combat refusal, warm-up). /back asks it, so the
 * back package does not depend on the utility implementation. {@link #DIRECT} is the behaviour from before the gate:
 * no refusal, teleport at once.
 */
public interface TeleportGate {
    /** Null when the player may teleport to {@code destination} (null = unknown yet), otherwise the English reason. */
    String denial(Player player, Location destination);

    /**
     * Runs the warm-up, then teleports. {@code onArrive} runs on the main thread only when the teleport really
     * happened. Messages for refusals and cancellations are sent by the gate.
     */
    void teleport(Player player, String label, Location destination, Runnable onArrive);

    TeleportGate DIRECT = new TeleportGate() {
        @Override
        public String denial(Player player, Location destination) {
            return null;
        }

        @Override
        public void teleport(Player player, String label, Location destination, Runnable onArrive) {
            player.teleportAsync(destination).thenAccept(done -> {
                if (done) {
                    onArrive.run();
                }
            });
        }
    };
}
