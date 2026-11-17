package win.vampyuyu.valoblock;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/** /vb: operate ValoBlock arenas (start, stop, status, reload) and drive tests (debug). Admin only. */
public final class VbCommand implements CommandExecutor, TabCompleter {
  private static final List<String> SUBS = List.of("help", "agent", "buy", "vote", "use", "start", "stop", "status", "reload", "level", "map", "debug");
  private static final List<String> DEBUG = List.of("skip", "state", "win", "kill", "plant", "seal", "armory", "block", "where", "tp", "god", "notice", "tags");

  private final NexusArenaEngine plugin;
  private final Map<String, String> selected = new HashMap<>();

  public VbCommand(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  /** Subcommands an admin may run while inside an arena (to run the match they are playing in); the rest stays locked. */
  static boolean allowedInsideArena(String[] args) {
    String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
    if (sub.equals("map")) return args.length >= 2 && (args[1].equalsIgnoreCase("use") || args[1].equalsIgnoreCase("list")); // not the editor (preview teleports, marks build)
    return sub.equals("start") || sub.equals("stop") || sub.equals("status") || sub.equals("help");
  }

  private static void say(CommandSender to, String text) {
    to.sendMessage(Component.text(text, NamedTextColor.GOLD));
  }

  private String arena(CommandSender sender) {
    String id = selected.get(sender.getName());
    var mine = plugin.arenas.all().stream().filter(s -> s.config.preset().equals(VbPreset.ID)).map(s -> s.config.id()).sorted().toList();
    if (id != null && mine.contains(id)) return id;
    if (mine.size() == 1) return mine.get(0);
    throw new IllegalArgumentException(
        mine.isEmpty() ? "No ValoBlock arena exists (create one with preset valoblock)" : "Choose an arena first: /vb use <id> (" + String.join(", ", mine) + ")");
  }

  private VbMatch match(CommandSender sender) {
    ArenaSession s = plugin.arenas.require(arena(sender));
    if (!(s.game instanceof VbMatch m)) throw new IllegalArgumentException("No ValoBlock match is running");
    return m;
  }

  /** /vb agent and /vb buy: any player in a match reopens their window (no permission needed). */
  private void reopen(CommandSender sender, boolean agent) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Only a player can use this");
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof VbMatch m)) throw new IllegalArgumentException("You are not in a ValoBlock match");
    if (agent) {
      String why = plugin.valoblock.agents.openSelect(p, m);
      if (why != null) throw new IllegalArgumentException(why);
    } else {
      plugin.valoblock.weapons.openArmory(p, m); // says by itself when it is not the buy phase
    }
  }

  /** /vb tac (Quick Actions "Map"): the tactical map for the held ability, else a short hint. */
  private void tac(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Only a player can use this");
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof VbMatch m)) throw new IllegalArgumentException("You are not in a ValoBlock match");
    String why = plugin.valoblock.agents.openTacticalMap(p, m);
    if (why != null) throw new IllegalArgumentException(why);
  }

  /** /vb vote: any player waiting in a ValoBlock lobby reopens the map vote (no permission needed). */
  private void vote(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Only a player can use this");
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !s.config.preset().equals(VbPreset.ID)) throw new IllegalArgumentException("You are not in a ValoBlock lobby");
    VbMapVoteService votes = plugin.valoblock.votes;
    String why = votes == null ? "The map vote is not running" : votes.open(p, s);
    if (why != null) throw new IllegalArgumentException(why);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      if (args.length >= 1 && args[0].equalsIgnoreCase("vote")) {
        vote(sender);
        return true;
      }
      if (args.length >= 1 && (args[0].equalsIgnoreCase("agent") || args[0].equalsIgnoreCase("buy"))) {
        reopen(sender, args[0].equalsIgnoreCase("agent"));
        return true;
      }
      if (args.length >= 1 && args[0].equalsIgnoreCase("tac")) {
        tac(sender);
        return true;
      }
      if (!sender.hasPermission("nexusarena.admin"))
        throw new IllegalArgumentException("Arena administrator permission required");
      String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
      if (sender instanceof Player p && plugin.arenas.locked(p.getUniqueId()) && !allowedInsideArena(args))
        throw new IllegalArgumentException("Administrator access outside an arena required");
      switch (sub) {
        case "use" -> {
          if (args.length < 2) throw new IllegalArgumentException("Usage: /vb use <arena>");
          plugin.arenas.require(args[1]);
          selected.put(sender.getName(), args[1]);
          say(sender, "Arena selected: " + args[1]);
        }
        case "start" -> {
          plugin.arenas.start(arena(sender));
          say(sender, "ValoBlock match started");
        }
        case "stop" ->
            plugin.arenas.stop(arena(sender)).whenComplete((v, e) -> say(sender, e == null ? "Arena stopped and restored" : NexusArenaEngine.reason(e)));
        case "status" -> {
          ArenaSession s = plugin.arenas.require(arena(sender));
          say(sender, s.game instanceof VbMatch m ? m.status() : "Queue: " + s.players.size() + " player(s), no match running");
        }
        case "reload" -> {
          VbData.reload(); // validated as a whole: a broken file throws and the old data stays
          plugin.arenas.reload();
          var files = VbData.overrides();
          say(sender, "ValoBlock data reloaded" + (files.isEmpty() ? "" : " (overrides: " + String.join(", ", files) + ")"));
        }
        case "level" -> {
          if (args.length >= 2) plugin.valoblock.bots.level(args[1]); // validated: unknown level throws
          say(sender, "Bot level for the next match: " + plugin.valoblock.bots.level() + " (" + String.join(", ", VbData.bots().levels().keySet()) + ")");
        }
        case "map" -> map(sender, args);
        case "debug" -> debug(sender, args);
        default -> {
          for (String line :
              List.of(
                  "/vb use <arena> · start · stop · status · reload · vote (reopen the map vote) · level [recruit|veteran|elite]",
                  "/vb map [list | use <id> | preview <id> | mark ... | save | discard]  (map editor, see /vb map help)",
                  "/vb debug skip | state | win <a|b> | kill <player> | plant <a|b> | seal"))
            say(sender, line);
        }
      }
    } catch (Exception e) {
      sender.sendMessage(Component.text(NexusArenaEngine.reason(e), NamedTextColor.RED));
    }
    return true;
  }

  // ------------------------------------------------------------- map editor

  private static final List<String> MAP_SUBS = List.of("help", "list", "use", "preview", "mark", "save", "discard");
  private static final List<String> MARKS = List.of("spawn", "site", "buy", "barrier", "orb", "node", "rmnode", "link");
  private final Map<String, VbMapEditor> editors = new HashMap<>();

  private VbMapService maps() {
    if (plugin.valoblock.map instanceof VbMapService m) return m;
    throw new IllegalArgumentException("The ValoBlock map service is not running");
  }

  private void map(CommandSender sender, String[] args) {
    String sub = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "list" -> {
        String forced = maps().selected();
        say(sender, forced == null ? "Next match map: decided by the players' vote (random when nobody votes)" : "Next match map: " + forced + " (forced, overrides the vote)");
        for (VbMap m : VbData.maps().values())
          say(sender, " " + m.id() + " · " + m.name() + " · " + m.realm() + " · " + m.width() + "x" + m.depth() + " blocks, " + m.cell() + " per cell");
      }
      case "use" -> {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb map use <id>");
        maps().select(args[2].toLowerCase(Locale.ROOT));
        say(sender, "The next match is played on " + maps().selected() + " (forced: it overrides the vote and applies to one match)");
      }
      case "preview" -> {
        if (!(sender instanceof Player p)) throw new IllegalArgumentException("Only a player can preview a map");
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb map preview <id>");
        String id = args[2].toLowerCase(Locale.ROOT);
        var at = maps().preview(id);
        editors.put(sender.getName(), new VbMapEditor(VbData.maps().get(id).source()));
        p.teleport(at);
        say(sender, "Preview of " + id + " built; you are on the Seedbearer spawn. Place markers with /vb map mark, then /vb map save.");
      }
      case "mark" -> mark(sender, args);
      case "save" -> {
        VbMapEditor ed = editors.get(sender.getName());
        if (ed == null) throw new IllegalArgumentException("Nothing to save: /vb map preview <id> first");
        VbMap checked = ed.check(); // refuses an inconsistent file before it is written
        var file = win.vampyuyu.werewolf.DataFiles.target(VbData.mapFile(checked.id()));
        if (file == null) throw new IllegalArgumentException("Data overrides are off");
        try {
          java.nio.file.Files.createDirectories(file.getParent());
          java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(ed.json()));
        } catch (java.io.IOException e) {
          throw new IllegalArgumentException("Could not write " + file + ": " + e.getMessage());
        }
        VbData.reload();
        say(sender, "Saved as an override: overrides/" + VbData.mapFile(checked.id()).substring(1) + " (loaded)");
      }
      case "discard" -> {
        editors.remove(sender.getName());
        say(sender, "Unsaved map edits dropped");
      }
      default -> {
        for (String line :
            List.of(
                "/vb map list | use <id> | preview <id> | save | discard",
                "In the preview, stand where the marker goes, then /vb map mark:",
                "spawn <seedbearers|sealers> <n> · site <id> <min|max> · buy <side> <min|max> · barrier <side> <min|max>",
                "orb <n> · node <id> · link <a> <b> · rmnode <id>"))
          say(sender, line);
      }
    }
  }

  private void mark(CommandSender sender, String[] args) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Only a player can place a marker");
    VbMapEditor ed = editors.get(sender.getName());
    if (ed == null) throw new IllegalArgumentException("Start with /vb map preview <id>");
    double[] at = maps().previewPosition(p.getLocation());
    if (at == null) throw new IllegalArgumentException("Stand inside the map preview");
    if (args.length < 3) throw new IllegalArgumentException("Usage: /vb map mark " + String.join("|", MARKS) + " ...");
    String what = args[2].toLowerCase(Locale.ROOT);
    String msg;
    try {
      msg = switch (what) {
        case "spawn" -> ed.spawn(VbSide.valueOf(sideName(args, 3)), Integer.parseInt(args[4]), at[0], at[1], at[2]);
        case "site", "buy", "barrier" -> ed.corner(what, args[3], args[4].equalsIgnoreCase("max"), at[0], at[1], at[2]);
        case "orb" -> ed.orb(Integer.parseInt(args[3]), at[0], at[1], at[2]);
        case "node" -> ed.node(args[3], at[0], at[1], at[2]);
        case "link" -> ed.link(args[3], args[4]);
        case "rmnode" -> ed.removeNode(args[3]);
        default -> throw new IllegalArgumentException("Marker kinds: " + String.join(", ", MARKS));
      };
    } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
      throw new IllegalArgumentException("Missing or wrong argument for " + what + " (/vb map help)");
    }
    say(sender, msg);
  }

  private static String sideName(String[] args, int i) {
    String k = args[i].toUpperCase(Locale.ROOT);
    if (k.startsWith("SEEDB")) return "SEEDBEARERS";
    if (k.startsWith("SEAL")) return "SEALERS";
    throw new IllegalArgumentException("Side must be seedbearers or sealers");
  }

  private void debug(CommandSender sender, String[] args) {
    String what = args.length < 2 ? "state" : args[1].toLowerCase(Locale.ROOT);
    VbMatch m = match(sender);
    switch (what) {
      case "skip" -> {
        m.advance();
        plugin.arenas.phaseChanged(plugin.arenas.require(arena(sender)));
        say(sender, m.status());
      }
      case "state" -> say(sender, m.status());
      case "win" -> {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb debug win <a|b>");
        m.forceRound(args[2].equalsIgnoreCase("a") ? 0 : 1);
        say(sender, m.status());
      }
      case "kill" -> {
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb debug kill <player>");
        Player p = plugin.getServer().getPlayerExact(args[2]);
        if (p == null) throw new IllegalArgumentException("Nobody called " + args[2] + " is online");
        m.kill(p.getUniqueId(), null);
        say(sender, m.status());
      }
      case "plant" -> {
        // Plants the Seed on the first site as the carrier, as if the hold had completed.
        UUID carrier = m.seedCarrier();
        if (carrier == null) throw new IllegalArgumentException("Nobody carries the Seed");
        m.holdPlant(carrier, VbData.sites().get(0), m.rules().plantHoldSeconds());
        say(sender, m.status());
      }
      case "seal" -> {
        UUID sealer = m.alive().stream().filter(id -> m.side(id) == VbSide.SEALERS).findFirst().orElseThrow(() -> new IllegalArgumentException("No living Sealer"));
        m.holdSeal(sealer, m.rules().sealSeconds());
        say(sender, m.status());
      }
      case "armory" -> {
        Player p = args.length >= 3 ? plugin.getServer().getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (p == null) throw new IllegalArgumentException("Usage: /vb debug armory <player>");
        plugin.valoblock.weapons.openArmory(p, m);
        say(sender, "Armory opened for " + p.getName() + " (buy phase only)");
      }
      case "block" -> {
        // The block of the ValoBlock world at a position: checks barriers and the greybox without a client.
        if (args.length < 5) throw new IllegalArgumentException("Usage: /vb debug block <x> <y> <z>");
        var w = plugin.valoblock.map.world();
        if (w == null) throw new IllegalArgumentException("The ValoBlock world is not loaded");
        say(sender, "block " + w.getBlockAt(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])).getType());
      }
      case "god" -> {
        // /vb debug god <player> [on|off]: the bots cannot hurt the player (screenshots of a Realm mechanic need a living human).
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb debug god <player> [on|off]");
        Player p = plugin.getServer().getPlayerExact(args[2]);
        if (p == null) throw new IllegalArgumentException("Nobody called " + args[2] + " is online");
        boolean on = args.length < 4 || !args[3].equalsIgnoreCase("off");
        m.setInvulnerable(p.getUniqueId(), on);
        say(sender, "god " + p.getName() + " " + (on ? "on" : "off"));
      }
      case "tags" -> {
        // /vb debug tags <player>: for each bot, its squad, its position and whether the player's client is shown its tag.
        if (args.length < 3) throw new IllegalArgumentException("Usage: /vb debug tags <player>");
        Player p = plugin.getServer().getPlayerExact(args[2]);
        if (p == null) throw new IllegalArgumentException("Nobody called " + args[2] + " is online");
        say(sender, "viewer squad " + m.squadOf(p.getUniqueId()) + " board=" + (p.getScoreboard() != plugin.getServer().getScoreboardManager().getMainScoreboard() ? "match" : "main"));
        for (java.util.UUID id : m.bots()) {
          var tag = plugin.valoblock.bots.tag(id);
          var body = plugin.valoblock.bots.body(id);
          say(sender, plugin.valoblock.bots.name(id) + " squad " + m.squadOf(id) + " at " + (body == null ? "-" : body.getLocation().getBlockX() + "," + body.getLocation().getBlockZ()) + " tagSeen=" + (tag != null && p.canSee(tag)));
        }
      }
      case "notice" -> {
        // /vb debug notice <player> <text...>: shows a HUD notice (screenshots of the HUD while a message is up).
        if (args.length < 4) throw new IllegalArgumentException("Usage: /vb debug notice <player> <text>");
        Player p = plugin.getServer().getPlayerExact(args[2]);
        if (p == null) throw new IllegalArgumentException("Nobody called " + args[2] + " is online");
        plugin.valoblock.hud.notice(p, net.kyori.adventure.text.Component.text(String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)), net.kyori.adventure.text.format.NamedTextColor.GOLD));
        say(sender, "notice sent");
      }
      case "tp" -> {
        // /vb debug tp <player> <x> <y> <z> [yaw pitch]: x and z are blocks of the map (slot 0); the engine cancels arena teleports unless allowed.
        if (args.length < 6) throw new IllegalArgumentException("Usage: /vb debug tp <player> <x> <y> <z> [yaw pitch]");
        Player p = plugin.getServer().getPlayerExact(args[2]);
        var w = plugin.valoblock.map.world();
        if (p == null || w == null) throw new IllegalArgumentException("No such player or the ValoBlock world is not loaded");
        float yaw = args.length > 6 ? Float.parseFloat(args[6]) : p.getLocation().getYaw();
        float pitch = args.length > 7 ? Float.parseFloat(args[7]) : p.getLocation().getPitch();
        plugin.arenas.allowTeleport(p.getUniqueId(), true);
        p.teleportAsync(new org.bukkit.Location(w, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]), yaw, pitch))
            .whenComplete((ok, err) -> plugin.arenas.allowTeleport(p.getUniqueId(), false));
        say(sender, "tp " + p.getName() + " queued");
      }
      case "where" -> {
        Player p = args.length >= 3 ? plugin.getServer().getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (p == null) throw new IllegalArgumentException("Usage: /vb debug where <player>");
        var l = p.getLocation();
        say(sender, String.format(Locale.ROOT, "where %s %.1f %.1f %.1f mode=%s alive=%s hp=%d", l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), p.getGameMode(), m.alive().contains(p.getUniqueId()), m.health(p.getUniqueId())));
      }
      default -> throw new IllegalArgumentException("Debug: " + String.join(", ", DEBUG));
    }
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission("nexusarena.admin")) return List.of();
    if (args.length == 1) return SUBS;
    if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return DEBUG;
    if (args.length == 2 && args[0].equalsIgnoreCase("level")) return new ArrayList<>(VbData.bots().levels().keySet());
    if (args.length == 2 && args[0].equalsIgnoreCase("map")) return MAP_SUBS;
    if (args.length == 3 && args[0].equalsIgnoreCase("map") && (args[1].equalsIgnoreCase("use") || args[1].equalsIgnoreCase("preview"))) return new ArrayList<>(VbData.maps().keySet());
    if (args.length == 3 && args[0].equalsIgnoreCase("map") && args[1].equalsIgnoreCase("mark")) return MARKS;
    if (args.length == 2 && args[0].equalsIgnoreCase("use")) return plugin.arenas.all().stream().map(s -> s.config.id()).toList();
    return List.of();
  }
}
