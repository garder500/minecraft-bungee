package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;

/** The words of the agent select menu and of the ability items (pure, English). The same text as agents/&lt;id&gt;.json. */
public final class AgentText {
  public static final String GRAY = "#a8a8a8", GOLD = "#ffd36a", YELLOW = "#ffe94a", RED = "#ff5a5a", GREEN = "#5ad07a";

  public record Line(String text, String color) {}

  private AgentText() {}

  /** "Choose your agent · 0:44". */
  public static String title(int seconds) {
    int s = Math.max(0, seconds);
    return "Choose your agent · " + (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
  }

  /** The roster tooltip: name, role and realm, quote, the four abilities with slot and price, then a footer line. */
  public static List<Line> tooltip(VbAgent a, String footer) {
    List<Line> l = new ArrayList<>();
    l.add(new Line(a.name(), a.glow()));
    l.add(new Line(a.role() + " · " + a.realm(), a.roleColor()));
    l.add(new Line(a.quote(), GRAY));
    for (VbAbility ab : a.abilities()) {
      l.add(new Line("[" + ab.slot() + "] " + ab.name() + " · " + ab.costLabel(), ab.slot() == 7 ? GOLD : a.trim()));
      l.add(new Line(ab.text(), GRAY));
    }
    if (footer != null && !footer.isEmpty()) l.add(new Line(footer, YELLOW));
    return l;
  }

  /** Tooltip of an ability item on the hotbar. */
  public static List<Line> abilityLines(VbAgent a, VbAbility ab, AgentKit kit) {
    List<Line> l = new ArrayList<>();
    l.add(new Line(ab.text(), GRAY));
    switch (ab.kind()) {
      case BUY -> l.add(new Line("Charges: " + kit.charges(ab.slot()) + " / " + ab.maxCharges() + " · bought in the Armory for " + ab.cost(), GOLD));
      case SIGNATURE -> l.add(new Line("Free · charges: " + kit.charges(6) + " / " + ab.charges() + (ab.rechargeKills() > 0 ? " · +1 every " + ab.rechargeKills() + " kills" : ""), a.trim()));
      case ULTIMATE -> l.add(new Line("Ultimate: " + kit.ultPoints() + " / " + ab.ultPoints() + " pts", GOLD));
    }
    l.add(new Line("Right-click to use", YELLOW));
    return l;
  }

  /** The Lock in button's tooltip. */
  public static List<Line> lockIn(VbAgent picked, boolean locked) {
    if (locked) return List.of(new Line("Locked in", GREEN), new Line("You play " + picked.name() + ".", GRAY));
    if (picked == null) return List.of(new Line("Lock in", GREEN), new Line("Pick an agent first.", GRAY));
    return List.of(new Line("Lock in " + picked.name(), GREEN), new Line("Applies the skin and fills the hotbar. Final.", GRAY));
  }

  public static List<Line> random() {
    return List.of(new Line("Random", "#9aa3b5"), new Line("Pick a free agent for me.", GRAY));
  }

  public static List<Line> role(VbAgents.Role r) {
    return List.of(new Line(r.name(), r.color()), new Line(r.text(), GRAY), new Line("Click to dim the other roles.", YELLOW));
  }
}
