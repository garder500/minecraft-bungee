package win.vampyuyu.werewolf;

import java.util.*;
import win.vampyuyu.arena.ArenaConfig;

/**
 * Where the game puts players, once, when it starts: a seat each (sitting or standing, looking at
 * the vote circle). Optional; without seats players stay where they spawn. Nobody is moved
 * afterwards.
 */
public record Places(List<Seat> seats, ArenaConfig.Spawn circle) {
  public Places {
    seats = seats == null ? List.of() : List.copyOf(seats);
    if (seats.size() > 45) throw new IllegalArgumentException("At most 45 seats");
    Set<String> names = new HashSet<>();
    for (Seat seat : seats)
      if (!names.add(seat.name())) throw new IllegalArgumentException("Seat names must be unique");
  }

  /** What still stops a game of this size from using the places, or null. */
  public String missing(int players) {
    if (seats.isEmpty()) return null; // No seats configured: players stay where they spawn.
    return seats.size() < players
        ? "Places are missing seats: " + seats.size() + "/" + players + " (one per player, bots included)"
        : null;
  }

  public List<ArenaConfig.Spawn> points() {
    List<ArenaConfig.Spawn> all = new ArrayList<>();
    for (Seat seat : seats) all.add(seat.at());
    if (circle != null) all.add(circle);
    return all;
  }

  public static Places defaults() {
    return new Places(List.of(), null);
  }
}
