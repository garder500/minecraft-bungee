package win.vampyuyu.arena;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.*;

/**
 * A zone and its life-cycle limits; nothing here knows what game runs inside. Whatever is specific
 * to a game (Werewolf rules, free-play objectives, timers) lives in {@link Options}, which
 * the game's {@link ArenaPreset} parses and validates.
 *
 * <p>On disk and on the wire the options sit flat beside the generic fields, exactly as every file
 * written so far (top-level {@code werewolf}, {@code freeplay}, {@code places}, {@code
 * discussionSeconds}...): old files load unchanged and old readers can still read new ones. A
 * nested {@code options} object is accepted too; a flat key wins over it.
 */
@JsonAdapter(ArenaConfig.Codec.class)
public record ArenaConfig(
    String id,
    String name,
    String worldName,
    String preset,
    Bounds bounds,
    Spawn spawn,
    Set<Material> allowedBreakBlocks,
    Set<Material> allowedPlaceBlocks,
    boolean allowPvP,
    boolean allowChests,
    boolean allowDoors,
    GameMode gameMode,
    int durationSeconds,
    int minPlayers,
    int maxPlayers,
    Rewards rewards,
    Options options) {
  public record Rewards(int participation, int victory) {
    public Rewards {
      if (participation < 0 || victory < 0 || participation > 100000 || victory > 100000)
        throw new IllegalArgumentException("Rewards must be 0–100000");
    }
  }

  public record Bounds(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Bounds {
      // Either corner may be entered first; retain exact coordinates.
      double x = minX, y = minY, z = minZ;
      minX = Math.min(x, maxX);
      minY = Math.min(y, maxY);
      minZ = Math.min(z, maxZ);
      maxX = Math.max(x, maxX);
      maxY = Math.max(y, maxY);
      maxZ = Math.max(z, maxZ);
      for (double n : new double[] {minX, minY, minZ, maxX, maxY, maxZ})
        if (!Double.isFinite(n) || Math.abs(n) > 29_999_000)
          throw new IllegalArgumentException("Invalid arena coordinate");
      if (maxX - minX < 4
          || maxZ - minZ < 4
          || maxY - minY < 3
          || maxX - minX > 1024
          || maxZ - minZ > 1024
          || maxY - minY > 4096)
        throw new IllegalArgumentException(
            "Bounds must be 4–1024 blocks wide and at least 3 blocks high");
    }

    public boolean contains(double x, double y, double z) {
      return x >= minX + .3
          && x < maxX - .3
          && y >= minY
          && y < maxY - 1.8
          && z >= minZ + .3
          && z < maxZ - .3;
    }

    public boolean overlaps(Bounds b) {
      return minX < b.maxX
          && maxX > b.minX
          && minY < b.maxY
          && maxY > b.minY
          && minZ < b.maxZ
          && maxZ > b.minZ;
    }
  }

  public record Spawn(double x, double y, double z, float yaw, float pitch) {}

  /**
   * The game-specific settings of an arena: an opaque JSON object, plus a cache of the typed views
   * the game parsed from it (so hot loops do not parse it again).
   */
  public static final class Options {
    private final JsonObject json;
    private final Map<Class<?>, Object> views = new ConcurrentHashMap<>();

    public Options(JsonObject json) {
      this.json = json == null ? new JsonObject() : json.deepCopy();
    }

    public static Options empty() {
      return new Options(null);
    }

    /** A copy of the raw settings. */
    public JsonObject json() {
      return json.deepCopy();
    }

    public boolean has(String key) {
      return json.has(key);
    }

    /** The typed view of these settings, parsed once; a failed parse throws and caches nothing. */
    @SuppressWarnings("unchecked")
    public <T> T view(Class<T> type, Function<JsonObject, T> parse) {
      Object cached = views.get(type);
      if (cached != null) return (T) cached;
      T parsed = parse.apply(json.deepCopy());
      Object raced = views.putIfAbsent(type, parsed);
      return raced == null ? parsed : (T) raced;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Options o && json.equals(o.json);
    }

    @Override
    public int hashCode() {
      return json.hashCode();
    }

    @Override
    public String toString() {
      return json.toString();
    }
  }

  public ArenaConfig {
    rewards = rewards == null ? new Rewards(10, 5) : rewards;
    options = options == null ? Options.empty() : options;
    if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,39}"))
      throw new IllegalArgumentException(
          "Arena ID must use lowercase letters, digits, _ or - (40 characters maximum)");
    if (name == null
        || name.isBlank()
        || name.length() > 80
        || name.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid arena name");
    if (worldName == null || worldName.isBlank() || worldName.length() > 128)
      throw new IllegalArgumentException("A loaded world is required");
    if (preset == null || !preset.matches("[a-z0-9_-]{1,40}"))
      throw new IllegalArgumentException("Unknown preset");
    if (bounds == null
        || spawn == null
        || !Double.isFinite(spawn.x)
        || !Double.isFinite(spawn.y)
        || !Double.isFinite(spawn.z)
        || !Float.isFinite(spawn.yaw)
        || !Float.isFinite(spawn.pitch)
        || Math.abs(spawn.pitch) > 90
        || !bounds.contains(spawn.x, spawn.y, spawn.z))
      throw new IllegalArgumentException("Spawn must be inside the arena bounds");
    if (gameMode == null || gameMode == GameMode.SPECTATOR)
      throw new IllegalArgumentException("Choose survival, creative or adventure");
    if (durationSeconds < 60
        || durationSeconds > 14400
        || minPlayers < 1
        || maxPlayers < minPlayers
        || maxPlayers > 45)
      throw new IllegalArgumentException("Invalid duration or player limits");
    if (allowedBreakBlocks != null && allowedBreakBlocks.stream().anyMatch(Objects::isNull)
        || allowedPlaceBlocks != null && allowedPlaceBlocks.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Unknown block material in allowlist");
    allowedBreakBlocks = allowedBreakBlocks == null ? Set.of() : Set.copyOf(allowedBreakBlocks);
    allowedPlaceBlocks = allowedPlaceBlocks == null ? Set.of() : Set.copyOf(allowedPlaceBlocks);
    if (allowedBreakBlocks.size() > 128
        || allowedPlaceBlocks.size() > 128
        || allowedBreakBlocks.stream().anyMatch(m -> !m.isBlock())
        || allowedPlaceBlocks.stream().anyMatch(m -> !m.isBlock()))
      throw new IllegalArgumentException(
          "Block allowlists must contain at most 128 block materials");
  }

  /** The same arena with other options. */
  public ArenaConfig withOptions(Options other) {
    return new ArenaConfig(
        id, name, worldName, preset, bounds, spawn, allowedBreakBlocks, allowedPlaceBlocks, allowPvP,
        allowChests, allowDoors, gameMode, durationSeconds, minPlayers, maxPlayers, rewards, other);
  }

  public Location location() {
    World w = Bukkit.getWorld(worldName);
    if (w == null) throw new IllegalArgumentException("Arena world is not loaded");
    return new Location(w, spawn.x, spawn.y, spawn.z, spawn.yaw, spawn.pitch);
  }

  /** The generic fields; every other top-level key of a file is a game option. */
  static final Set<String> GENERIC =
      Set.of(
          "id", "name", "worldName", "preset", "bounds", "spawn", "allowedBreakBlocks",
          "allowedPlaceBlocks", "allowPvP", "allowChests", "allowDoors", "gameMode",
          "durationSeconds", "minPlayers", "maxPlayers", "rewards", "options");

  /** Reads and writes the flat file format (see the class comment). */
  public static final class Codec implements TypeAdapterFactory {
    private static final java.lang.reflect.Type BLOCKS =
        new TypeToken<Set<Material>>() {}.getType();

    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (type.getRawType() != ArenaConfig.class) return null;
      TypeAdapter<JsonElement> tree = gson.getAdapter(JsonElement.class);
      return (TypeAdapter<T>)
          new TypeAdapter<ArenaConfig>() {
            @Override
            public void write(JsonWriter out, ArenaConfig c) throws IOException {
              if (c == null) {
                out.nullValue();
                return;
              }
              JsonObject o = new JsonObject();
              o.addProperty("id", c.id);
              o.addProperty("name", c.name);
              o.addProperty("worldName", c.worldName);
              o.addProperty("preset", c.preset);
              o.add("bounds", gson.toJsonTree(c.bounds));
              o.add("spawn", gson.toJsonTree(c.spawn));
              o.add("allowedBreakBlocks", gson.toJsonTree(c.allowedBreakBlocks));
              o.add("allowedPlaceBlocks", gson.toJsonTree(c.allowedPlaceBlocks));
              o.addProperty("allowPvP", c.allowPvP);
              o.addProperty("allowChests", c.allowChests);
              o.addProperty("allowDoors", c.allowDoors);
              o.add("gameMode", gson.toJsonTree(c.gameMode));
              o.addProperty("durationSeconds", c.durationSeconds);
              o.addProperty("minPlayers", c.minPlayers);
              o.addProperty("maxPlayers", c.maxPlayers);
              o.add("rewards", gson.toJsonTree(c.rewards));
              for (var option : c.options.json().entrySet())
                o.add(option.getKey(), option.getValue());
              tree.write(out, o);
            }

            @Override
            public ArenaConfig read(JsonReader in) throws IOException {
              if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
              }
              JsonObject o = tree.read(in).getAsJsonObject();
              JsonObject options = new JsonObject();
              if (o.get("options") instanceof JsonObject nested)
                for (var option : nested.entrySet())
                  if (!GENERIC.contains(option.getKey()))
                    options.add(option.getKey(), option.getValue());
              for (var entry : o.entrySet())
                if (!GENERIC.contains(entry.getKey()))
                  options.add(entry.getKey(), entry.getValue());
              return new ArenaConfig(
                  string(o, "id"),
                  string(o, "name"),
                  string(o, "worldName"),
                  string(o, "preset"),
                  gson.fromJson(o.get("bounds"), Bounds.class),
                  gson.fromJson(o.get("spawn"), Spawn.class),
                  gson.fromJson(o.get("allowedBreakBlocks"), BLOCKS),
                  gson.fromJson(o.get("allowedPlaceBlocks"), BLOCKS),
                  bool(o, "allowPvP"),
                  bool(o, "allowChests"),
                  bool(o, "allowDoors"),
                  gson.fromJson(o.get("gameMode"), GameMode.class),
                  number(o, "durationSeconds"),
                  number(o, "minPlayers"),
                  number(o, "maxPlayers"),
                  gson.fromJson(o.get("rewards"), Rewards.class),
                  new Options(options));
            }
          };
    }

    private static String string(JsonObject o, String key) {
      return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
      return o.get(key) instanceof JsonPrimitive p && p.getAsBoolean();
    }

    private static int number(JsonObject o, String key) {
      return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : 0;
    }
  }
}
