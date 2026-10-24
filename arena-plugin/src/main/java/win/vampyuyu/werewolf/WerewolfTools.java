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

/**
 * What the admin kit and setup menu add for Werewolf arenas: the seat and vote-circle tools, the
 * places in the checklist, the ambience switch and the phase timers.
 */
public final class WerewolfTools {
  private static final double NEAREST_SEAT = 3.0;
  private final NexusArenaEngine plugin;

  public WerewolfTools(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  public List<GameHooks.Tool> tools() {
    return List.of(
        new GameHooks.Tool(
            "SEAT",
            Material.RED_BED,
            "Seats",
            "Right-click a block: a sitting seat on it (stairs and slabs work)",
            "Sneak + right-click a block: a standing spot on it",
            "Left-click a seat's block: remove it"),
        new GameHooks.Tool(
            "CIRCLE",
            Material.SUNFLOWER,
            "Vote circle",
            "Right-click the block at the centre of the table"));
  }

  public boolean use(
      ArenaTools tools, Player p, String tool, boolean left, boolean sneak, Block block)
      throws Exception {
    switch (tool) {
      case "SEAT" -> seat(tools, p, left, sneak, block);
      case "CIRCLE" -> {
        if (left) return true;
        if (block == null)
          throw new IllegalArgumentException("Right-click the block at the centre of the table");
        String id = tools.needArena(p);
        plugin.admin.point(p, id); // checks the arena and the world
        var position = PlaceController.onBlock(block, false);
        plugin.admin.change(
            id, j -> j.getAsJsonObject("places").add("circle", ArenaManager.JSON.toJsonTree(position)));
        tools.feedback(p, "Vote circle set for " + id);
        tools.report(p, id);
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  /** The Werewolf lines of the readiness checklist; returns what stops a start, or null. */
  public String report(ArenaTools tools, ArenaConfig c, List<String> lines) {
    var v = WerewolfOptions.of(c).places();
    lines.add(
        ArenaTools.ok(
            !v.seats().isEmpty(),
            "Seats "
                + v.seats().size()
                + " / "
                + c.minPlayers()
                + " needed (none: players stay where they spawn)"));
    lines.add(ArenaTools.ok(v.circle() != null, "Vote circle" + ArenaTools.at(v.circle())));
    return v.missing(c.minPlayers());
  }

  /** The asynchronous safety check of the places, after the checklist. */
  public void reportMore(Player p, ArenaConfig c) {
    if (WerewolfOptions.of(c).places().seats().isEmpty()) return;
    plugin
        .werewolf
        .places
        .problems(c)
        .thenAccept(
            found ->
                plugin.onPlayer(
                    p,
                    () -> {
                      if (found.isEmpty())
                        p.sendMessage(Component.text("§a✔ §fSafety check: every place is usable"));
                      for (String problem : found) p.sendMessage(Component.text("§c✘ §f" + problem));
                      return null;
                    }));
  }

  public void draw(ArenaTools tools, Player p, ArenaConfig c) {
    var w = p.getWorld();
    var places = WerewolfOptions.of(c).places();
    for (var seat : places.seats())
      tools.dot(p, w, seat.at(), seat.sit() ? Particle.HAPPY_VILLAGER : Particle.CRIT);
    tools.dot(p, w, places.circle(), Particle.END_ROD);
  }

  public void setupMenu(ArenaAdmin admin, ArenaAdmin.Menu m, Player p, ArenaConfig c) {
    String id = c.id();
    var options = WerewolfOptions.of(c);
    // Row 1: places (set them with /ww place, or the admin items).
    m.put(
        10,
        Material.RED_BED,
        "Places (" + options.places().seats().size() + " seats)",
        () -> {
          p.closeInventory();
          plugin.tools.report(p, id);
        },
        "Seats and the vote circle: /ww place list");
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

  private void seat(ArenaTools tools, Player p, boolean left, boolean sneak, Block block)
      throws Exception {
    String id = tools.needArena(p);
    var c = plugin.arenas.require(id).config;
    var seats = WerewolfOptions.of(c).places().seats();
    if (!left) {
      if (block == null)
        throw new IllegalArgumentException("Right-click the block to sit on (stairs and slabs work)");
      plugin.admin.point(p, id);
      var position = PlaceController.onBlock(block, !sneak);
      int n = seats.size() + 1;
      Set<String> names = new HashSet<>();
      for (var seat : seats) names.add(seat.name());
      while (names.contains("seat-" + n)) n++;
      final String chosen = "seat-" + n;
      final boolean sit = !sneak;
      plugin.admin.change(
          id,
          j ->
              j.getAsJsonObject("places")
                  .getAsJsonArray("seats")
                  .add(ArenaManager.JSON.toJsonTree(new Seat(chosen, position, sit))));
      var after = plugin.arenas.require(id).config;
      tools.feedback(
          p,
          "Seat "
              + chosen
              + (sit ? " (sit)" : " (stand)")
              + " added: "
              + WerewolfOptions.of(after).places().seats().size()
              + "/"
              + c.minPlayers()
              + " needed ("
              + c.maxPlayers()
              + " for a full lobby)");
      return;
    }
    String nearest = nearestSeat(p, seats, block);
    if (nearest == null)
      throw new IllegalArgumentException("No seat within " + (int) NEAREST_SEAT + " blocks");
    plugin.admin.change(
        id,
        j -> {
          var array = j.getAsJsonObject("places").getAsJsonArray("seats");
          for (int i = 0; i < array.size(); i++)
            if (array.get(i).getAsJsonObject().get("name").getAsString().equals(nearest)) {
              array.remove(i);
              break;
            }
        });
    tools.feedback(p, "Seat " + nearest + " removed (" + (seats.size() - 1) + " left)");
  }

  private String nearestSeat(Player p, List<Seat> seats, Block block) {
    String best = null;
    double bestDistance = (block != null ? 1.5 : NEAREST_SEAT) * (block != null ? 1.5 : NEAREST_SEAT);
    Location at = block != null ? block.getLocation().add(0.5, 0.5, 0.5) : p.getLocation();
    for (var seat : seats) {
      var s = seat.at();
      double d =
          Math.pow(s.x() - at.getX(), 2)
              + Math.pow(s.y() - at.getY(), 2)
              + Math.pow(s.z() - at.getZ(), 2);
      if (d <= bestDistance) {
        best = seat.name();
        bestDistance = d;
      }
    }
    return best;
  }
}
