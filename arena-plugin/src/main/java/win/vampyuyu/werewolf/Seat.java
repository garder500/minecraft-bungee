package win.vampyuyu.werewolf;

import win.vampyuyu.arena.ArenaConfig;

/** A named spot where a player sits or stands during the game. */
public record Seat(String name, ArenaConfig.Spawn at, boolean sit) {
  public Seat {
    if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Seat names use letters, digits, _ or - (32 maximum)");
    if (at == null) throw new IllegalArgumentException("A seat needs a position");
  }
}
