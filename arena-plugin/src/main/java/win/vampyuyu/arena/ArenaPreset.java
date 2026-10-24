package win.vampyuyu.arena;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * A game the arena engine can run. The engine knows zones; a preset brings the game (its rules, its
 * settings in {@link ArenaConfig#options()} and its {@link GameHooks}) without changing arena
 * protection or player snapshots.
 */
public interface ArenaPreset {
  String id();

  GamePreset create(ArenaConfig config, Collection<UUID> participants);

  /** As above, with roles forced for given players (admin testing); most games ignore them. */
  default GamePreset create(
      ArenaConfig config, Collection<UUID> participants, java.util.Map<UUID, String> forced) {
    return create(config, participants);
  }

  /** What the game adds around the zone life cycle. */
  default GameHooks hooks() {
    return GameHooks.NONE;
  }

  /**
   * Parses the game's options and rejects a configuration the game cannot run. Called whenever an
   * arena is loaded or saved, in a loaded world.
   */
  default void validate(ArenaConfig config, World world) {}

  /**
   * Completes the options of an arena read from disk (or edited) with this game's defaults, so
   * files written by earlier versions load unchanged and are saved in full. Called before
   * {@link #validate}; must not change what the options already say.
   */
  default JsonObject migrate(JsonObject options) {
    return options;
  }

  /** Name and icon of the game in the admin menu. */
  default String title() {
    return id();
  }

  default Material icon() {
    return Material.GRASS_BLOCK;
  }

  /** Preferred slot of the game in the "Choose preset" menu (taken slots shift to a free one). */
  default int menuSlot() {
    return 13;
  }

  /** The options of a new arena of this game. */
  default JsonObject defaultOptions() {
    return new JsonObject();
  }

  /** The smallest player count a new arena of this game starts with. */
  default int defaultMinPlayers() {
    return 1;
  }
}
