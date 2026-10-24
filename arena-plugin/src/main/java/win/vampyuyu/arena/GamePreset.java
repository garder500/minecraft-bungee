package win.vampyuyu.arena;

import java.util.*;

/**
 * Pure game state. ArenaManager owns lifecycle; game implementations never mutate Bukkit entities.
 */
public interface GamePreset {
  String phase();

  String role(UUID player);

  /** The role this player believes they hold (a drunk thinks they are a villager). */
  default String selfRoleName(UUID player) {
    return role(player);
  }

  Set<UUID> alive();

  Map<UUID, Integer> votes(UUID viewer);

  String select(UUID voter, UUID target);

  void remove(UUID player);

  void advance();

  String winner();

  int seconds();

  default Map<String, String> abilities(UUID player) {
    return Map.of();
  }

  default String use(UUID player, String ability) {
    throw new IllegalArgumentException("Ability unavailable");
  }

  default String help(UUID player) {
    return "Open a player head to use the current action.";
  }

  default Map<UUID, Integer> scores() {
    return Map.of();
  }

  default void event(UUID player, String event) {}

  default void visit(UUID player, double x, double y, double z) {}

  /** Private messages the game wants delivered right now, once (e.g. a role's secret). */
  default Map<UUID, String> drainNotices() {
    return Map.of();
  }

  default void finish() {}

  /** The players who won, when the game has individual winners (the winner is a player id). */
  default Set<UUID> winners() {
    try {
      return Set.of(UUID.fromString(winner()));
    } catch (IllegalArgumentException notAPlayer) {
      return Set.of();
    }
  }

  /** Identifies the current turn inside a phase; it changes when the turn passes to someone else. */
  default String turnKey() {
    return "";
  }
}
