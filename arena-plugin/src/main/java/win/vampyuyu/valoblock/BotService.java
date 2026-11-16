package win.vampyuyu.valoblock;

import java.util.UUID;

/** Slice 4: bodies and brains of the bots that fill empty seats. */
public interface BotService {
  BotService NOOP = new BotService() {};

  default String name(UUID bot) {
    return "[BOT] " + bot.toString().substring(0, 4);
  }

  default void spawn(VbMatch match, UUID bot, VbSide side) {}

  default void remove(UUID bot) {}
}
