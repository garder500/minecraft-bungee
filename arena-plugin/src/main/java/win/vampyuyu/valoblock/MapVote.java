package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ballot of one ValoBlock lobby: who voted for which map. Pure (no Bukkit): clicks come from the server thread and the
 * resolution from whichever thread starts the arena, so the state is concurrent. A ballot is opened for each lobby cycle ({@link
 * #generation} is what the menu plan keys "offered once" on), voted on until the match starts, then resolved and closed.
 * Resolution: a forced map (an admin's /vb map use) wins; else the most votes; a tie is drawn among the tied maps; no vote at all is
 * a draw among every map.
 */
final class MapVote {
  /** The seconds the title counts down from when a ballot opens: the lobby has no clock of its own, so this is how long the vote is advertised. */
  static final int WINDOW_SECONDS = 90;

  /** How a map was chosen (the announcement says it). */
  enum How {
    FORCED,
    VOTES,
    TIE,
    RANDOM
  }

  record Result(String map, How how, int votes) {}

  private final List<String> maps;
  private final Map<UUID, String> votes = new ConcurrentHashMap<>();
  private volatile int generation;
  private volatile boolean open;
  private volatile long openedAt;

  MapVote(List<String> maps) {
    if (maps.isEmpty()) throw new IllegalArgumentException("A ballot needs maps");
    this.maps = List.copyOf(maps);
  }

  List<String> maps() {
    return maps;
  }

  /** Starts a new ballot: votes dropped, a new generation. */
  synchronized void open(long nowMillis) {
    votes.clear();
    generation++;
    openedAt = nowMillis;
    open = true;
  }

  /** Closes the ballot (the match starts, or nobody is left); the votes are dropped with it. */
  synchronized void close() {
    open = false;
    votes.clear();
  }

  boolean isOpen() {
    return open;
  }

  /** The number of this ballot (0 before the first). */
  int generation() {
    return generation;
  }

  /** The generation while the ballot is open, else 0: what the menu plan sees. */
  int ballot() {
    return open ? generation : 0;
  }

  /** The seconds left of the advertised window, never below 0. */
  int secondsLeft(long nowMillis) {
    return (int) Math.max(0, WINDOW_SECONDS - (nowMillis - openedAt) / 1000);
  }

  /** Records or changes a vote. False when the ballot is closed or the map is unknown. */
  boolean cast(UUID voter, String map) {
    if (!open || !maps.contains(map)) return false;
    votes.put(voter, map);
    return true;
  }

  void retract(UUID voter) {
    votes.remove(voter);
  }

  /** The map this player voted for, or null. */
  String choice(UUID voter) {
    return votes.get(voter);
  }

  /** Votes per map, every map present (0 when nobody picked it), in map order. */
  Map<String, Integer> counts() {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (String m : maps) out.put(m, 0);
    for (String m : votes.values()) out.merge(m, 1, Integer::sum);
    return out;
  }

  /** The winner (see the class comment). {@code forced} is the admin's map or null. Does not close the ballot. */
  Result resolve(Random rnd, String forced) {
    if (forced != null) return new Result(forced, How.FORCED, counts().getOrDefault(forced, 0));
    Map<String, Integer> counts = counts();
    int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    if (best == 0) return new Result(maps.get(rnd.nextInt(maps.size())), How.RANDOM, 0);
    List<String> tied = new ArrayList<>();
    for (var e : counts.entrySet()) if (e.getValue() == best) tied.add(e.getKey());
    if (tied.size() == 1) return new Result(tied.get(0), How.VOTES, best);
    return new Result(tied.get(rnd.nextInt(tied.size())), How.TIE, best);
  }

  /** The leaders right now (every map with the top count, or empty when nobody voted): the menu says who is ahead. */
  List<String> leaders() {
    Map<String, Integer> counts = counts();
    int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    if (best == 0) return List.of();
    return counts.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
  }
}
