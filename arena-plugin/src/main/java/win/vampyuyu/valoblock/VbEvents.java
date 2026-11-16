package win.vampyuyu.valoblock;

import java.util.UUID;

/** What the match tells the world (weapons, HUD, bots, map) as it changes. Called under the match lock: stay quick. */
public interface VbEvents {
  VbEvents NONE = new VbEvents() {};

  default void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {}

  default void roundStarted(VbMatch match, int round) {}

  default void roundEnded(VbMatch match, VbMatch.RoundResult result) {}

  default void killed(VbMatch match, UUID victim, UUID killer) {}

  default void seedPlanted(VbMatch match, VbSite site, UUID planter) {}

  /** A player (human or bot) holds an agent: by lock in or by the random fill when the select time ran out. */
  default void agentLocked(VbMatch match, UUID player, String agent) {}

  default void matchEnded(VbMatch match, String winner) {}
}
