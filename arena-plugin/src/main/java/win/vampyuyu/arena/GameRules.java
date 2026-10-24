package win.vampyuyu.arena;

import java.util.*;
import org.bukkit.Material;

/** Versioned, bounded gameplay settings persisted alongside each arena. */
public final class GameRules {
  private GameRules() {}

  public record Checkpoint(double x, double y, double z, double radius) {
    public Checkpoint {
      if (!Double.isFinite(x)
          || !Double.isFinite(y)
          || !Double.isFinite(z)
          || !Double.isFinite(radius)
          || radius < 1
          || radius > 8) throw new IllegalArgumentException("Checkpoint radius must be 1–8 blocks");
    }
  }

  public record Freeplay(
      String objective,
      int targetScore,
      int pointsPerEvent,
      boolean allowFlight,
      boolean hunger,
      long personalTime,
      String weather,
      Map<String, Integer> kit,
      Map<String, Integer> effects,
      List<Checkpoint> checkpoints,
      String instructions) {
    public Freeplay {
      if (!Set.of("SANDBOX", "PVP", "BLOCK_BREAK", "BLOCK_PLACE", "RACE")
          .contains(objective == null ? "" : objective))
        throw new IllegalArgumentException("Unknown free-play objective");
      if (targetScore < 1 || targetScore > 1000 || pointsPerEvent < 1 || pointsPerEvent > 100)
        throw new IllegalArgumentException("Invalid score settings");
      if (personalTime < -1
          || personalTime > 23999
          || !Set.of("WORLD", "CLEAR", "DOWNFALL").contains(weather == null ? "" : weather))
        throw new IllegalArgumentException("Invalid personal time or weather");
      kit = kit == null ? Map.of() : Map.copyOf(kit);
      effects = effects == null ? Map.of() : Map.copyOf(effects);
      checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
      if (kit.size() > 24 || effects.size() > 6 || checkpoints.size() > 20)
        throw new IllegalArgumentException("Too many kit items, effects or checkpoints");
      for (var e : kit.entrySet()) {
        Material mat = Material.matchMaterial(e.getKey());
        if (mat == null
            || !mat.isItem()
            || mat.isAir()
            || e.getValue() == null
            || e.getValue() < 1
            || e.getValue() > mat.getMaxStackSize())
          throw new IllegalArgumentException("Invalid kit material or stack size: " + e.getKey());
      }
      for (var e : effects.entrySet())
        if (!Set.of("speed", "jump_boost", "strength", "regeneration", "night_vision", "haste")
                .contains(e.getKey())
            || e.getValue() == null
            || e.getValue() < 1
            || e.getValue() > 5) throw new IllegalArgumentException("Effect levels must be 1–5");
      if (objective.equals("RACE") && checkpoints.size() < 2)
        throw new IllegalArgumentException("A race requires at least two checkpoints");
      if (instructions == null || instructions.length() > 600)
        throw new IllegalArgumentException("Instructions must contain at most 600 characters");
    }

    /** The free-play settings of an arena, validated against its limits; parsed once per config. */
    public static Freeplay of(ArenaConfig c) {
      return c.options()
          .view(
              Freeplay.class,
              json -> {
                Freeplay rules =
                    json.has("freeplay")
                        ? new com.google.gson.Gson().fromJson(json.get("freeplay"), Freeplay.class)
                        : defaults();
                if (rules == null) rules = defaults();
                if (c.preset().equals("freeplay")) {
                  if (rules.objective().equals("PVP") && !c.allowPvP())
                    throw new IllegalArgumentException("PvP objectives require player combat enabled");
                  if (rules.objective().equals("BLOCK_BREAK") && c.allowedBreakBlocks().isEmpty())
                    throw new IllegalArgumentException("Choose which blocks score when broken");
                  if (rules.objective().equals("BLOCK_PLACE") && c.allowedPlaceBlocks().isEmpty())
                    throw new IllegalArgumentException("Choose which blocks score when placed");
                  for (var point : rules.checkpoints())
                    if (!c.bounds().contains(point.x(), point.y(), point.z()))
                      throw new IllegalArgumentException("Checkpoints must be inside the arena");
                  for (int a = 0; a < rules.checkpoints().size(); a++)
                    for (int b = a + 1; b < rules.checkpoints().size(); b++) {
                      var p = rules.checkpoints().get(a);
                      var q = rules.checkpoints().get(b);
                      if (Math.pow(p.x() - q.x(), 2)
                              + Math.pow(p.y() - q.y(), 2)
                              + Math.pow(p.z() - q.z(), 2)
                          < Math.pow(p.radius() + q.radius(), 2))
                        throw new IllegalArgumentException("Checkpoint areas must not overlap");
                    }
                }
                return rules;
              });
    }

    public static Freeplay defaults() {
      return new Freeplay(
          "SANDBOX",
          10,
          1,
          false,
          false,
          -1,
          "WORLD",
          Map.of(),
          Map.of(),
          List.of(),
          "Enjoy the arena. Use your compass to follow the game or leave.");
    }
  }
}
