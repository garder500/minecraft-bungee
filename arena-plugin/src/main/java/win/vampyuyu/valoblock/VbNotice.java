package win.vampyuyu.valoblock;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/**
 * Where a short message to a ValoBlock player goes. Never into the action bar: the glyph HUD there is a run of glyphs the core
 * shader moves by colour, and text next to it garbles the health, hides the icons and overlaps the ability tiles. A message is
 * a subtitle with an empty title and a short fade. Pure.
 */
final class VbNotice {
  static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1700), Duration.ofMillis(300));

  private VbNotice() {}

  static Title title(Component text) {
    return Title.title(Component.empty(), text, TIMES);
  }

  /** The only text the action bar ever carries: it replaces the glyph run for a player who is out of the round. */
  static Component eliminated() {
    return Component.text("Eliminated: spectating", NamedTextColor.GRAY);
  }
}
