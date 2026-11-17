package win.vampyuyu.valoblock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who gets a ValoBlock menu opened for them, and when. Pure: the service reads a {@link View} of the player and does what this
 * says. The agent select cannot be dismissed: it is reopened whenever it is missing until the player locks in or the phase ends.
 * The Armory is offered once per buy phase: a player who closes it keeps it closed until the next buy phase, and a player who left
 * and came back (forget) is offered it again.
 */
final class MenuPlan {
  enum Menu {
    NONE,
    SELECT,
    ARMORY
  }

  /**
   * What the service sees of one human player.
   *
   * @param phase the match phase
   * @param round the round number (a buy phase is identified by it)
   * @param locked the agent is locked in
   * @param alive the player is alive in the match
   * @param packReady the ValoBlock pack is loaded (or there is none): nothing opens before it, or the client drops the menu
   * @param open the ValoBlock menu the player has open now
   * @param busy another window (a chest, anything that is not their own inventory nor a ValoBlock menu) is open
   */
  record View(VbPhase phase, int round, boolean locked, boolean alive, boolean packReady, Menu open, boolean busy) {}

  private final Map<UUID, String> served = new ConcurrentHashMap<>();

  /** The menu this phase calls for, whatever is open. */
  static Menu wanted(View v) {
    return switch (v.phase()) {
      case AGENT_SELECT -> v.locked() ? Menu.NONE : Menu.SELECT;
      case BUY -> v.alive() ? Menu.ARMORY : Menu.NONE;
      default -> Menu.NONE;
    };
  }

  private static String key(View v) {
    return "buy:" + v.round();
  }

  /**
   * The menu to open for this player now, or NONE. Nothing is offered while the pack is not loaded or another window is open (it
   * is tried again at the next look, which is also what stops a loop against a menu another plugin forces open). The select is
   * offered every time it is missing; the Armory once per buy phase (one the player opened by hand counts as offered).
   */
  Menu decide(UUID id, View v) {
    Menu want = wanted(v);
    if (want == Menu.NONE) return Menu.NONE;
    if (v.open() == want) {
      if (want == Menu.ARMORY) served.put(id, key(v));
      return Menu.NONE;
    }
    if (!v.packReady() || v.open() != Menu.NONE || v.busy()) return Menu.NONE;
    if (want == Menu.SELECT) return Menu.SELECT;
    return key(v).equals(served.get(id)) ? Menu.NONE : want;
  }

  /** The menu was opened by the service. */
  void opened(UUID id, View v) {
    if (wanted(v) == Menu.ARMORY) served.put(id, key(v));
  }

  /** The menu the player has open is out of date (a buy window in combat, a select window once locked in): close it. */
  static boolean mustClose(View v) {
    return v.open() != Menu.NONE && v.open() != wanted(v);
  }

  /** The player left, quit or joined: whatever they were offered is forgotten. */
  void forget(UUID id) {
    served.remove(id);
  }
}
