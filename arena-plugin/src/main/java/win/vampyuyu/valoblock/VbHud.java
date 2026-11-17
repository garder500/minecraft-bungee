package win.vampyuyu.valoblock;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The in-round HUD: score and timer (boss bar), kill feed (boss bars), health, abilities and ammo (action bar).
 * One writer per surface: nothing else sends an action bar to a ValoBlock player, it goes through {@link #notice}.
 */
public interface VbHud {
  VbHud NOOP = new VbHud() {};

  /** Called by the clock; {@code phaseEndsAt} is the engine's end of the current phase (ms). */
  default void render(VbMatch match, long now, long phaseEndsAt) {}

  default void clear(VbMatch match) {}

  /** A player left the match and stays online: their boss bars and action bar go, nothing is drawn for them again. */
  default void left(VbMatch match, UUID player) {}

  /** The shooter hit someone (head shot, or a kill). */
  default void hitMarker(UUID shooter, boolean head, boolean kill) {}

  /** A kill for the feed. {@code killer} is null for a death without a killer. */
  default void killed(VbMatch match, UUID victim, UUID killer, String weapon) {}

  /** A short message for one player (a subtitle, never the action bar). */
  default void notice(Player player, Component text) {
    player.showTitle(VbNotice.title(text));
  }
}
