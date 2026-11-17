package win.vampyuyu.valoblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The one name tag of a bot: a text display that follows the body (the body's own name is hidden), so the tag is hidden
 * by walls (a vanilla name tag shows through everything, which stacked the tags of the whole team above one bot). Pure.
 */
final class BotTag {
  static final TextColor[] TEAM = {TextColor.color(0xff9a7a), TextColor.color(0x7fd8ff)};
  /** Height of the tag above the feet of the body. */
  static final double HEIGHT = 2.35;

  private BotTag() {}

  static TextColor colour(int squad) {
    return TEAM[Math.floorMod(squad, TEAM.length)];
  }

  static Component text(String name, int squad) {
    return Component.text(name, colour(squad));
  }
}
