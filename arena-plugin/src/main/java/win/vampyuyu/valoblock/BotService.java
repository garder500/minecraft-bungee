package win.vampyuyu.valoblock;

import java.util.UUID;

/** Slice 4: bodies and brains of the bots that fill empty seats. Every call may arrive under the match lock on any thread. */
public interface BotService {
  BotService NOOP = new BotService() {};

  default String name(UUID bot) {
    return "[BOT] " + bot.toString().substring(0, 4);
  }

  default void spawn(VbMatch match, UUID bot, VbSide side) {}

  default void remove(UUID bot) {}

  /** A round starts (every round, bots come back to life): buy, stand at the spawn, plan. */
  default void roundStarted(VbMatch match, UUID bot, boolean keep) {}

  default void agentLocked(VbMatch match, UUID bot, String agent) {}

  default void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {}

  /** Anyone died: a bot victim loses its body and drops its gun, a bot killer charges its ultimate. */
  default void killed(VbMatch match, UUID victim, UUID killer) {}

  /** A human fired at {@code x,y,z}: defenders may rotate to that site. */
  default void gunfire(VbMatch match, UUID shooter, double x, double y, double z) {}

  /** {x, y, z, sneaking (0/1), yaw} of a living bot's body, or null (not a bot, dead, no body). */
  default double[] position(UUID bot) {
    return null;
  }

  /** The bot a mannequin body belongs to, or null. */
  default UUID botOf(org.bukkit.entity.Entity body) {
    return null;
  }

  /** A bot took a hit from {@code attacker}: hurt animation. */
  default void damaged(UUID bot) {}

  /** A late human took the bot's seat: its guns and Shards go to the human. */
  default void replaced(VbMatch match, UUID bot, UUID human) {}

  /** The match is over: bodies and state go. */
  default void matchEnded(VbMatch match) {}

  /** Level name for the bots of the next match (recruit, veteran, elite). */
  default void level(String level) {}

  default String level() {
    return VbData.bots().defaultLevel();
  }

  /** The name of the gun a bot fights with, or null. */
  default String weaponName(UUID bot) {
    return null;
  }
}
