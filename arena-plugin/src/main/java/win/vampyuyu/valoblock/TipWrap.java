package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;

/** Wraps ValoBlock tooltip and lore lines at {@link #WIDTH} characters, at spaces only (a word is never cut). Pure helper. */
public final class TipWrap {
  public static final int WIDTH = 40;

  private TipWrap() {}

  /** The text cut into lines of at most {@code width} characters; a word longer than that stays whole on its own line. */
  public static List<String> wrap(String text, int width) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    for (String word : text.split(" +")) {
      if (word.isEmpty()) continue;
      if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
        out.add(cur.toString());
        cur.setLength(0);
      }
      if (cur.length() > 0) cur.append(' ');
      cur.append(word);
    }
    if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
    return out;
  }

  /** One lore line as one or more italic-free components of the same colour. */
  public static void add(List<Component> lore, String text, String color) {
    for (String s : wrap(text, WIDTH)) lore.add(WeaponItems.text(s, color));
  }
}
