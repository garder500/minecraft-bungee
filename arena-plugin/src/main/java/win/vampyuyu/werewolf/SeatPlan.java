package win.vampyuyu.werewolf;

import java.util.*;

/**
 * Who sits where: one seat of the platform ring for every participant, drawn at random. The ring
 * order (the table order the game uses for neighbours) is the order of the seats around the fire.
 * Pure data, no Bukkit server needed.
 */
public record SeatPlan(Map<UUID, SeatSpot> seats, List<UUID> ring) {
  public SeatPlan {
    seats = Map.copyOf(seats);
    ring = List.copyOf(ring);
  }

  /** @throws IllegalArgumentException when there are fewer seats than participants. */
  public static SeatPlan assign(Collection<UUID> players, List<SeatSpot> spots, Random random) {
    if (spots.size() < players.size())
      throw new IllegalArgumentException(
          "The platform has " + spots.size() + " seats for " + players.size() + " players");
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < spots.size(); i++) order.add(i);
    Collections.shuffle(order, random);
    Map<UUID, Integer> chosen = new LinkedHashMap<>();
    int next = 0;
    for (UUID id : players) chosen.put(id, order.get(next++));
    Map<UUID, SeatSpot> seats = new LinkedHashMap<>();
    chosen.forEach((id, index) -> seats.put(id, spots.get(index)));
    List<UUID> ring = new ArrayList<>(chosen.keySet());
    ring.sort(Comparator.comparingInt(chosen::get));
    return new SeatPlan(seats, ring);
  }
}
