package win.vampyuyu.werewolf;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.ArenaConfig;
import win.vampyuyu.arena.ArenaManager;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.sky.PlatformLayout;
import win.vampyuyu.werewolf.sky.SkyWorld;

/**
 * /ww: werewolf management. Admins set the cast and the timers, start and stop games, and drive practice games and tests; players only vote.
 */
public final class WwCommand implements CommandExecutor, TabCompleter {
  private static final List<String> ADMIN =
      List.of(
          "use", "status", "cast", "time", "rules", "start", "stop", "practice", "phase", "force",
          "check", "reload", "act", "click", "peek", "players", "debug", "help");
  private static final Map<String, String> ALIASES =
      Map.ofEntries(
          Map.entry("WOLF", "WEREWOLF"),
          Map.entry("ALPHA", "ALPHA_WOLF"),
          Map.entry("CUB", "WOLF_CUB"),
          Map.entry("BEAR", "BEAR_TAMER"),
          Map.entry("GOAT", "SCAPEGOAT"),
          Map.entry("VILLAGE", "VILLAGER"),
          Map.entry("APPRENTICE", "APPRENTICE_SEER"),
          Map.entry("AURA", "AURA_SEER"),
          Map.entry("HAG", "OLD_HAG"),
          Map.entry("OLDMAN", "OLD_MAN"),
          Map.entry("INVESTIGATOR", "PI"),
          Map.entry("P.I.", "PI"),
          Map.entry("TOUGH", "TOUGH_GUY"),
          Map.entry("TROUBLE", "TROUBLEMAKER"),
          Map.entry("SPELL", "SPELLCASTER"),
          Map.entry("DOPPEL", "DOPPELGANGER"),
          Map.entry("CULT", "CULT_LEADER"),
          Map.entry("LONE", "LONE_WOLF"),
          Map.entry("VAMP", "VAMPIRE"),
          Map.entry("SORC", "SORCERER"));

  private final NexusArenaEngine plugin;
  private final Map<String, String> selected = new HashMap<>();
  private final WwDebugCommand debug;

  public WwCommand(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.debug = new WwDebugCommand(plugin, this);
  }

  /** Every role name, for the completion of /ww debug force. */
  static List<String> roleNames() {
    return Arrays.stream(WerewolfGame.Role.values()).map(r -> r.name().toLowerCase(Locale.ROOT)).toList();
  }

  // ------------------------------------------------------------------ plumbing

  private static void say(CommandSender to, String text) {
    to.sendMessage(Component.text(text, NamedTextColor.GOLD));
  }

  private String arena(CommandSender sender) {
    String name = sender.getName();
    String id = selected.get(name);
    if (id != null && plugin.arenas.all().stream().anyMatch(s -> s.config.id().equals(id))) return id;
    var all = plugin.arenas.all().stream().map(s -> s.config.id()).sorted().toList();
    if (all.size() == 1) return all.get(0);
    throw new IllegalArgumentException("Choose an arena first: /ww use <id> (" + String.join(", ", all) + ")");
  }

  private static void need(String[] args, int n, String usage) {
    if (args.length < n) throw new IllegalArgumentException("Usage: " + usage);
  }

  private static String role(String text) {
    String name = text.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    name = ALIASES.getOrDefault(name, name);
    try {
      return WerewolfGame.Role.valueOf(name).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown role '" + text + "'. Roles: " + Arrays.toString(WerewolfGame.Role.values()));
    }
  }

  /** A participant by name, or botN for the practice bot "[Bot] Guest N". */
  private UUID who(ArenaSession s, String token) {
    String wanted = token.matches("(?i)bot\\d+") ? "[Bot] Guest " + token.substring(3) : token;
    for (var entry : s.players.entrySet())
      if (entry.getValue().equalsIgnoreCase(wanted)) return entry.getKey();
    throw new IllegalArgumentException("Nobody called " + token + " in this arena");
  }

  private void done(CommandSender sender, CompletableFuture<?> future, String ok) {
    future.whenComplete(
        (v, e) -> {
          if (sender instanceof Player p) plugin.message(p, e == null ? ok : NexusArenaEngine.reason(e));
          else
            sender.sendMessage(
                Component.text(e == null ? ok : NexusArenaEngine.reason(e), e == null ? NamedTextColor.GREEN : NamedTextColor.RED));
        });
  }

  // ----------------------------------------------------------------- dispatch

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
      if (sub.equals("hud")) {
        if (!(sender instanceof Player p)) throw new IllegalArgumentException("Players only");
        String size = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!List.of("auto", "large", "medium", "small").contains(size))
          throw new IllegalArgumentException("Usage: /ww hud auto|large|medium|small (now " + plugin.hudSize(p) + ")");
        plugin.hudSize(p, size);
        say(sender, "HUD size: " + size);
        return true;
      }
      if (sub.equals("vote") || sub.equals("abstain")) {
        player(sender, sub, args);
        return true;
      }
      if (!sender.hasPermission("nexusarena.admin"))
        throw new IllegalArgumentException("Arena administrator permission required");
      if (sender instanceof Player p && plugin.arenas.locked(p.getUniqueId()))
        throw new IllegalArgumentException("Administrator access outside an arena required");
      String moved = WwDebugCommand.deprecation(sub);
      if (moved != null && sender instanceof Player) say(sender, moved);
      switch (sub) {
        case "debug" -> debug.run(sender, args);
        case "use" -> {
          need(args, 2, "/ww use <arena>");
          plugin.arenas.require(args[1]);
          selected.put(sender.getName(), args[1]);
          say(sender, "Arena selected: " + args[1]);
        }
        case "status" -> status(sender);
        case "cast" -> cast(sender, args);
        case "time" -> time(sender, args);
        case "rules" -> rules(sender, args);
        case "start" -> {
          plugin.arenas.start(arena(sender));
          say(sender, "Game started");
        }
        case "stop" -> done(sender, plugin.arenas.stop(arena(sender)), "Arena stopped and restored");
        case "practice" -> practice(sender, args);
        case "phase" -> phase(sender, args);
        case "force" -> force(sender, args);
        case "check" -> check(sender);
        case "reload" -> {
          plugin.arenas.reload();
          say(sender, "Arenas reloaded");
        }
        case "act" -> act(sender, args);
        case "click" -> click(sender, args);
        case "peek" -> peek(sender);
        case "players" -> players(sender, args);
        default -> help(sender);
      }
    } catch (Exception e) {
      sender.sendMessage(Component.text(NexusArenaEngine.reason(e), NamedTextColor.RED));
    }
    return true;
  }

  private void help(CommandSender sender) {
    for (String line :
        List.of(
            "/ww use <arena> · status · check · reload",
            "/ww cast set wolf:2 alpha:1 seer:1 ... · auto · show · roles",
            "/ww time set discussion|vote|night|cupid|witch|hunter <seconds>",
            "/ww start · stop · debug help   (test tools: debug hud|ambience|sky|practice|force|act|click|peek|phase)",
            "/ww force <player> <role> | clear     (roles for the next game, for tests)",
            "/ww players kick <player> · peek      (peek lists every role: admins only)",
            "/ww act <player|botN> select|aim <target> | use <ability>   (test: act as someone)",
            "/ww click <player> <target>           (test: the right-click on a player)",
            "Players: /ww vote <player> · /ww abstain · /ww hud auto|large|medium|small"))
      say(sender, line);
  }

  // ------------------------------------------------------------------ players

  private void player(CommandSender sender, String sub, String[] args) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Players only");
    ArenaSession s = plugin.arenas.session(p.getUniqueId());
    if (s == null || !(s.game instanceof WerewolfGame) || !WerewolfCompass.inGame(s))
      throw new IllegalArgumentException("You are not in a werewolf game");
    if (sub.equals("abstain")) plugin.werewolf.controls.useItem(p, s, "skip");
    else {
      need(args, 2, "/ww vote <player>");
      plugin.werewolf.controls.pickPlayer(p, s, who(s, args[1]));
    }
  }

  // ------------------------------------------------------------------- status

  private void status(CommandSender sender) {
    ArenaSession s = plugin.arenas.require(arena(sender));
    var c = s.config;
    say(
        sender,
        c.id() + ": " + s.phase() + " · players " + s.players.size() + "/" + c.maxPlayers()
            + " (min " + c.minPlayers() + ")" + (s.bots.isEmpty() ? "" : " · " + s.bots.size() + " bots"));
    var platform = WerewolfSession.of(s).platform;
    say(
        sender,
        "Sky platform: "
            + (platform == null
                ? "none (built when the game starts)"
                : "slot " + platform.slot().index() + " · " + platform.players() + " seats"));
    say(sender, "Cast: " + (WerewolfOptions.of(c).rules().roles().isEmpty() ? "automatic" : WerewolfOptions.of(c).rules().roles()));
    say(
        sender,
        "Timers: discussion " + WerewolfOptions.of(c).discussionSeconds() + "s, vote " + WerewolfOptions.of(c).voteSeconds() + "s, night "
            + WerewolfOptions.of(c).nightSeconds() + "s, cupid " + WerewolfOptions.of(c).rules().cupidSeconds() + "s, witch "
            + WerewolfOptions.of(c).rules().witchSeconds() + "s, hunter " + WerewolfOptions.of(c).rules().hunterSeconds() + "s");
    var forcedNow = plugin.arenas.forced.get(c.id());
    if (forcedNow != null && !forcedNow.isEmpty()) say(sender, "Forced roles: " + forcedNow);
    if (s.game instanceof WerewolfGame game)
      say(sender, "Game: " + game.phase() + " · round " + game.round() + " · alive " + game.alive().size());
  }

  private void check(CommandSender sender) {
    String id = arena(sender);
    ArenaConfig c = plugin.arenas.require(id).config;
    String problem = plugin.werewolf.stage.problem(Math.max(c.minPlayers(), PlatformLayout.MIN_PLAYERS));
    say(
        sender,
        problem == null
            ? "Sky platform: ready (world " + SkyWorld.NAME + ")"
            : "Not ready: " + problem);
    if (problem != null && plugin.werewolf.skyError() != null)
      sender.sendMessage(Component.text(plugin.werewolf.skyError(), NamedTextColor.RED));
  }

  // --------------------------------------------------------------------- cast

  private void cast(CommandSender sender, String[] args) throws Exception {
    need(args, 2, "/ww cast set|auto|show|roles");
    String id = arena(sender);
    ArenaConfig c = plugin.arenas.require(id).config;
    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "set" -> {
        need(args, 3, "/ww cast set wolf:2 alpha:1 seer:1 witch:1 ...");
        Map<String, Integer> roles = new LinkedHashMap<>();
        for (int i = 2; i < args.length; i++) {
          String[] pair = args[i].split(":");
          roles.merge(role(pair[0]), pair.length > 1 ? Integer.parseInt(pair[1]) : 1, Integer::sum);
        }
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").add("roles", ArenaManager.JSON.toJsonTree(roles)));
        say(sender, "Cast: " + roles + " (the rest are villagers)");
      }
      case "auto" -> {
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").add("roles", new JsonObject()));
        say(sender, "Cast: automatic");
      }
      case "show" -> say(sender, WerewolfOptions.of(c).rules().roles().isEmpty() ? "Cast: automatic" : "Cast: " + WerewolfOptions.of(c).rules().roles());
      case "roles" -> say(sender, "Roles: " + Arrays.toString(WerewolfGame.Role.values()));
      default -> throw new IllegalArgumentException("Use set, auto, show or roles");
    }
  }

  /** The variants of the game: what a death reveals, secret ballots, the daily dice, the amulet. */
  private void rules(CommandSender sender, String[] args) throws Exception {
    String id = arena(sender);
    var w = WerewolfOptions.of(plugin.arenas.require(id).config).rules();
    if (args.length < 3) {
      say(sender, "voting " + w.voting() + " · reveal " + w.reveal() + " · ballots " + w.ballots()
          + " · dice " + (w.dice() ? "on" : "off") + " · amulet " + (w.amulet() ? "on" : "off")
          + " · mayor " + w.mayor() + " · minion " + w.minion());
      say(sender, "Change with /ww rules voting point|nominate, reveal full|team|wolf|none, ballots open|secret, dice on|off, amulet on|off, mayor dealt|elected, minion dealt|picked");
      return;
    }
    String key = args[1].toLowerCase(Locale.ROOT), value = args[2].toLowerCase(Locale.ROOT);
    switch (key) {
      case "reveal" -> {
        if (!WerewolfRules.REVEALS.contains(value.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use full, team, wolf or none");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty("reveal", value.toUpperCase(Locale.ROOT)));
      }
      case "voting" -> {
        if (!WerewolfRules.VOTINGS.contains(value.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use point or nominate");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty("voting", value.toUpperCase(Locale.ROOT)));
      }
      case "mayor" -> {
        if (!WerewolfRules.MAYORS.contains(value.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use dealt or elected");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty("mayor", value.toUpperCase(Locale.ROOT)));
      }
      case "minion" -> {
        if (!WerewolfRules.MINIONS.contains(value.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use dealt or picked");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty("minion", value.toUpperCase(Locale.ROOT)));
      }
      case "ballots" -> {
        if (!WerewolfRules.BALLOTS.contains(value.toUpperCase(Locale.ROOT)))
          throw new IllegalArgumentException("Use open or secret");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty("ballots", value.toUpperCase(Locale.ROOT)));
      }
      case "dice", "amulet" -> {
        if (!value.equals("on") && !value.equals("off")) throw new IllegalArgumentException("Use on or off");
        plugin.admin.change(id, j -> j.getAsJsonObject("werewolf").addProperty(key, value.equals("on")));
      }
      default -> throw new IllegalArgumentException("Use voting, reveal, ballots, dice, amulet, mayor or minion");
    }
    say(sender, key + " = " + value);
  }

  private void time(CommandSender sender, String[] args) throws Exception {
    need(args, 4, "/ww time set discussion|vote|night|cupid|witch|hunter <seconds>");
    String id = arena(sender);
    int seconds = Integer.parseInt(args[3]);
    String field =
        switch (args[2].toLowerCase(Locale.ROOT)) {
          case "discussion", "day" -> "discussionSeconds";
          case "vote" -> "voteSeconds";
          case "night" -> "nightSeconds";
          case "cupid" -> "cupidSeconds";
          case "witch" -> "witchSeconds";
          case "hunter" -> "hunterSeconds";
          default -> throw new IllegalArgumentException("Use discussion, vote, night, cupid, witch or hunter");
        };
    boolean special = field.equals("cupidSeconds") || field.equals("witchSeconds") || field.equals("hunterSeconds");
    plugin.admin.change(id, j -> (special ? j.getAsJsonObject("werewolf") : j).addProperty(field, seconds));
    say(sender, field + " = " + seconds);
  }

  // ----------------------------------------------------------------- lifecycle

  void practice(CommandSender sender, String[] args) {
    need(args, 2, "/ww practice <bots> [player]");
    String id = arena(sender);
    Player p = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
    done(sender, plugin.arenas.startPractice(id, Integer.parseInt(args[1]), p), "Practice game started");
  }

  void phase(CommandSender sender, String[] args) {
    need(args, 2, "/ww phase skip");
    ArenaSession s = plugin.arenas.require(arena(sender));
    if (!(s.game instanceof WerewolfGame game)) throw new IllegalArgumentException("No game is running");
    if (!args[1].equalsIgnoreCase("skip")) throw new IllegalArgumentException("Use skip");
    game.advance();
    plugin.arenas.phaseChanged(s);
    say(sender, "Phase is now " + game.phase());
  }

  void force(CommandSender sender, String[] args) {
    need(args, 2, "/ww force <player> <role> | clear");
    String id = arena(sender);
    if (args[1].equalsIgnoreCase("clear")) {
      plugin.arenas.forced.remove(id);
      say(sender, "Forced roles cleared");
      return;
    }
    need(args, 3, "/ww force <player|botN> <role>");
    // botN is the practice bot "[Bot] Guest N"; the player may not have joined yet.
    String name = args[1].matches("(?i)bot\\d+") ? "[Bot] Guest " + args[1].substring(3) : args[1];
    plugin.arenas.forced.computeIfAbsent(id, k -> new java.util.concurrent.ConcurrentHashMap<>())
        .put(name.toLowerCase(Locale.ROOT), role(args[2]));
    say(sender, "Forced for the next game: " + plugin.arenas.forced.get(id));
  }

  private void players(CommandSender sender, String[] args) {
    need(args, 3, "/ww players kick <player>");
    if (!args[1].equalsIgnoreCase("kick")) throw new IllegalArgumentException("Use kick");
    Player p = Bukkit.getPlayerExact(args[2]);
    if (p == null) throw new IllegalArgumentException("Player offline");
    done(sender, plugin.arenas.leaveArena(p), p.getName() + " removed from the arena");
  }

  void peek(CommandSender sender) {
    ArenaSession s = plugin.arenas.require(arena(sender));
    if (!(s.game instanceof WerewolfGame game)) throw new IllegalArgumentException("No game is running");
    for (var entry : s.players.entrySet())
      say(sender, entry.getValue() + ": " + game.role(entry.getKey()) + (game.alive().contains(entry.getKey()) ? "" : " (dead)"));
  }

  // -------------------------------------------------------------------- tests

  /** Acts as a participant (a bot too): the same game calls a click makes. */
  void act(CommandSender sender, String[] args) {
    need(args, 4, "/ww act <player|botN> select|aim <target> | use <ability>");
    ArenaSession s = plugin.arenas.require(arena(sender));
    if (!(s.game instanceof WerewolfGame game)) throw new IllegalArgumentException("No game is running");
    UUID actor = who(s, args[1]);
    String reply =
        switch (args[2].toLowerCase(Locale.ROOT)) {
          case "select" -> game.select(actor, who(s, args[3]));
          case "aim" -> game.aim(actor, who(s, args[3]));
          case "use" -> game.use(actor, args[3]);
          default -> throw new IllegalArgumentException("Use select, aim or use");
        };
    plugin.gui.deliverNotices(s);
    plugin.arenas.phaseChanged(s);
    say(sender, reply);
  }

  /** Right-click on a player as the real controls do (needs the actor online). */
  void click(CommandSender sender, String[] args) {
    need(args, 3, "/ww click <player> <target|ability:name>");
    ArenaSession s = plugin.arenas.require(arena(sender));
    Player p = Bukkit.getPlayerExact(args[1]);
    if (p == null) throw new IllegalArgumentException("Player offline");
    boolean ok =
        args[2].startsWith("ability:")
            ? plugin.werewolf.controls.useItem(p, s, args[2].substring(8))
            : plugin.werewolf.controls.pickPlayer(p, s, who(s, args[2]));
    say(sender, ok ? "Clicked" : "Nothing happened (not in a running game?)");
  }

  // --------------------------------------------------------------- completion

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      List<String> all = new ArrayList<>(ADMIN);
      all.addAll(List.of("vote", "abstain", "hud"));
      return all.stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
    if (args[0].equalsIgnoreCase("debug") && sender.hasPermission("nexusarena.admin")) return debug.complete(args);
    if (args.length == 2 && args[0].equalsIgnoreCase("hud")) return List.of("auto", "large", "medium", "small");
    if (args.length == 2 && args[0].equalsIgnoreCase("cast")) return List.of("set", "auto", "show", "roles");
    if (args.length == 2 && args[0].equalsIgnoreCase("use")) return plugin.arenas.all().stream().map(s -> s.config.id()).toList();
    return List.of();
  }
}
