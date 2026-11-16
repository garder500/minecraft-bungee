package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The tooltip text of a weapon in the Armory, as plain data so it is testable without a server. */
public final class WeaponText {
  public static final String GOLD = "#ffd36a", GRAY = "#a8a8a8", WHITE = "#eef0f4", GREEN = "#5ad07a", RED = "#ff6b6b";

  /** One tooltip line and its colour. */
  public record Line(String text, String color) {}

  private WeaponText() {}

  public static String num(double v) {
    return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
  }

  public static String shards(int v) {
    return String.format(Locale.ROOT, "%,d", v) + " Shards";
  }

  private static String zones(VbWeapon w) {
    if (w.pellets() > 1) return "Head " + w.pellets() + "×" + w.head() + " · Body " + w.pellets() + "×" + w.body() + " · Legs " + w.pellets() + "×" + w.legs();
    return "Head " + w.head() + " · Body " + w.body() + " · Legs " + w.legs();
  }

  /** Tooltip lines in the design's order: name, kind, damage, rate, note, price, action. */
  public static List<Line> tooltip(VbWeapon w, Armory.Display display, int missing) {
    List<Line> out = new ArrayList<>();
    out.add(new Line(w.name(), GOLD));
    out.add(new Line(w.kind() + " · " + w.metalName(), GRAY));
    if (w.isShield()) {
      out.add(new Line("Shield +" + w.shield(), WHITE));
    } else if (w.isGun()) {
      out.add(new Line(zones(w), WHITE));
      String rate = w.mode() == VbWeapon.Mode.BURST ? num(w.rate()) + " bursts/s · " + w.burst() + " rounds each" : num(w.rate()) + " shots/s";
      out.add(new Line(rate + " · " + w.magazine() + " rounds · reload " + num(w.reload()) + " s", WHITE));
    } else {
      out.add(new Line("Body " + w.body() + " · From behind " + w.backstab(), WHITE));
    }
    out.add(new Line(w.note(), GRAY));
    out.add(new Line(w.free() ? "Free" : "Price: " + shards(w.price()), GOLD));
    switch (display) {
      case OWNED -> out.add(new Line(w.free() ? "Owned" : "Owned · right-click to sell back", GREEN));
      case AFFORDABLE -> out.add(new Line(w.free() ? "Click to take" : "Click to buy · right-click to sell back", GREEN));
      case LOCKED -> out.add(new Line("Missing " + shards(missing), RED));
    }
    return out;
  }

  /** The window title: Shards left. */
  public static String title(int credits) {
    return "Armory · " + shards(credits);
  }
}
