package win.vampyuyu.valoblock;

import java.util.Map;

/**
 * One ability of an agent (agents/&lt;id&gt;.json). Hotbar 4 and 5 are bought each round with Shards ({@code cost}),
 * 6 is the free signature ({@code charges} per round, plus one more every {@code rechargeKills} kills), 7 is the
 * ultimate ({@code ultPoints}). {@code num} holds the ability's own numbers (seconds, radius, damage...).
 */
public record VbAbility(int slot, String id, String name, String item, String text, int cost, int maxCharges, int charges, int rechargeKills, int ultPoints, Map<String, Double> num) {
  public enum Kind {
    BUY,
    SIGNATURE,
    ULTIMATE
  }

  public Kind kind() {
    return slot == 6 ? Kind.SIGNATURE : slot == 7 ? Kind.ULTIMATE : Kind.BUY;
  }

  public double num(String key, double def) {
    return num.getOrDefault(key, def);
  }

  /** The price as the tooltips show it: "200", "free", "free x2" or "7 pts". */
  public String costLabel() {
    return switch (kind()) {
      case BUY -> Integer.toString(cost);
      case SIGNATURE -> charges > 1 ? "free x" + charges : "free";
      case ULTIMATE -> ultPoints + " pts";
    };
  }

  /** Hotbar index (0-based) of the ability: slot 4 is index 3. */
  public int hotbar() {
    return slot - 1;
  }
}
