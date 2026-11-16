package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The bot's buy rules: a full buy (rifle and the best shield it can pay) at 3,900 Shards or more,
 * otherwise it saves and keeps Pip with a light shield. Goes through {@link Armory}, so the buy phase,
 * the price and the sell-back rules are the humans' own. Pure.
 */
public final class BotBuyer {
  public record Outcome(boolean fullBuy, List<VbWeapon> bought) {}

  private BotBuyer() {}

  public static Outcome buy(VbMatch m, UUID bot, Loadout loadout, BotConfig cfg, VbWeapons catalogue) {
    List<VbWeapon> bought = new ArrayList<>();
    VbWeapon heavy = catalogue.get("heavy_ward"), light = catalogue.get("light_ward");
    boolean pistol = m.score().pistolRound(m.round());
    boolean hasPrimary = loadout.get(VbWeapon.Slot.PRIMARY) != null;
    boolean full = !pistol && (hasPrimary || m.credits(bot) >= cfg.fullBuyAt());
    if (full) {
      if (!hasPrimary) {
        VbWeapon pick = null;
        // The first gun of the list that still leaves the heavy shield affordable, else the first it can pay.
        for (String id : cfg.primaryOrder()) {
          VbWeapon w = catalogue.get(id);
          if (w != null && m.credits(bot) >= w.price() + heavy.price()) {
            pick = w;
            break;
          }
        }
        if (pick == null)
          for (String id : cfg.primaryOrder()) {
            VbWeapon w = catalogue.get(id);
            if (w != null && m.credits(bot) >= w.price()) {
              pick = w;
              break;
            }
          }
        if (pick != null && Armory.buy(m, loadout, bot, pick).ok()) bought.add(pick);
      }
      if (m.shield(bot) < heavy.shield() && Armory.buy(m, loadout, bot, heavy).ok()) bought.add(heavy);
      else if (m.shield(bot) < light.shield() && Armory.buy(m, loadout, bot, light).ok()) bought.add(light);
    } else if ((pistol || m.credits(bot) >= cfg.saveShieldMin()) && m.shield(bot) < light.shield()) {
      if (Armory.buy(m, loadout, bot, light).ok()) bought.add(light);
    }
    return new Outcome(full, bought);
  }

  /** Spends what is left on the two buyable ability charges (a full buy only). Returns how many it bought. */
  public static int buyAbilities(VbMatch m, UUID bot, AgentKit kit) {
    int n = 0;
    for (int slot : new int[] {4, 5}) if (kit.buy(m, bot, slot) == AgentKit.Result.OK) n++;
    return n;
  }
}
