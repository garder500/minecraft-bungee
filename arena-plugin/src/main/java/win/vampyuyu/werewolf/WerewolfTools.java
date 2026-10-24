package win.vampyuyu.werewolf;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.*;
import win.vampyuyu.arena.ArenaAdmin;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.ArenaManager;
import win.vampyuyu.arena.ArenaTools;
import win.vampyuyu.arena.GameHooks;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.sky.PlatformLayout;
import win.vampyuyu.werewolf.sky.SkyWorld;

/**
 * What the admin kit and setup menu add for Werewolf arenas: the sky platform in the checklist, the
 * ambience switch and the phase timers.
 */
public final class WerewolfTools {
  private final NexusArenaEngine plugin;

  public WerewolfTools(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  public List<GameHooks.Tool> tools() {
    return List.of();
  }

  public boolean use(
      ArenaTools tools, Player p, String tool, boolean left, boolean sneak, Block block) {
    return false;
  }

  /** The Werewolf lines of the readiness checklist; returns what stops a start, or null. */
  public String report(ArenaTools tools, ArenaConfig c, List<String> lines) {
    String problem = plugin.werewolf.stage.problem(Math.max(c.minPlayers(), PlatformLayout.MIN_PLAYERS));
    lines.add(
        ArenaTools.ok(
            problem == null,
            "Sky platform: "
                + (problem == null
                    ? "world " + SkyWorld.NAME + " ready, one platform per game"
                    : problem)));
    return problem;
  }

  /** Nothing asynchronous to check: the platform is generated for every game. */
  public void reportMore(Player p, ArenaConfig c) {}

  public void draw(ArenaTools tools, Player p, ArenaConfig c) {}

  public void setupMenu(ArenaAdmin admin, ArenaAdmin.Menu m, Player p, ArenaConfig c) {
    String id = c.id();
    var options = WerewolfOptions.of(c);
    m.toggle(
        15,
        Material.NOTE_BLOCK,
        "Immersive ambience",
        options.rules().ambienceEnabled(),
        () -> {
          admin.change(
              id,
              j ->
                  j.getAsJsonObject("werewolf")
                      .addProperty("ambienceEnabled", !options.rules().ambienceEnabled()));
          admin.setup(p, id);
        },
        "Native sounds and titles",
        "Private instructions remain available when disabled");
  }

  public Map<String, Integer> numbers(ArenaConfig c) {
    var options = WerewolfOptions.of(c);
    Map<String, Integer> numbers = new LinkedHashMap<>();
    numbers.put("discussionSeconds", options.discussionSeconds());
    numbers.put("voteSeconds", options.voteSeconds());
    numbers.put("nightSeconds", options.nightSeconds());
    return numbers;
  }
}
