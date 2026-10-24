package win.vampyuyu.werewolf;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.ambience.AmbienceProfile;
import win.vampyuyu.werewolf.hud.CustomHud;
import win.vampyuyu.werewolf.hud.HudPreview;
import win.vampyuyu.werewolf.sky.PlatformLayout;

/**
 * /ww debug: the admin test and preview commands, in one place. HUD previews need no game: a
 * synthetic state is drawn in the action bar four times a second for a few seconds. The old
 * practice, force, act, click, peek and phase spellings still work as /ww aliases.
 */
final class WwDebugCommand {
  /** The test commands that used to live at /ww top level; WwCommand still runs them. */
  static final List<String> MOVED = List.of("practice", "force", "act", "click", "peek", "phase");

  static final List<String> SUBS = List.of("help", "hud", "ambience", "sky", "practice", "force", "act", "click", "peek", "phase");
  static final List<String> SIZES = List.of("auto", "large", "medium", "small");
  static final int SHOW_SECONDS = 10, CYCLE_SECONDS = 4;
  private static final long PERIOD_TICKS = 5; // 4 refreshes a second

  private final NexusArenaEngine plugin;
  private final WwCommand ww;
  private final Map<UUID, ScheduledTask> shows = new ConcurrentHashMap<>();

  WwDebugCommand(NexusArenaEngine plugin, WwCommand ww) {
    this.plugin = plugin;
    this.ww = ww;
  }

  // ------------------------------------------------------------------- aliases

  /** The deprecation hint of an old /ww spelling, or null when the word is not a moved command. */
  static String deprecation(String sub) {
    String s = sub == null ? "" : sub.toLowerCase(Locale.ROOT);
    return MOVED.contains(s) ? "/ww " + s + " is now /ww debug " + s + " (the old spelling still works)" : null;
  }

  /** The new spelling of an old command line, e.g. [phase, skip] becomes [debug, phase, skip]. */
  static String[] forwarded(String[] args) {
    String[] out = new String[args.length + 1];
    out[0] = "debug";
    System.arraycopy(args, 0, out, 1, args.length);
    return out;
  }

  private static void say(CommandSender to, String text) {
    to.sendMessage(Component.text(text, NamedTextColor.GOLD));
  }

  // ------------------------------------------------------------------ dispatch

  /** args[0] is "debug". */
  void run(CommandSender sender, String[] args) {
    String sub = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "hud" -> hud(sender, args);
      case "ambience" -> ambience(sender, args);
      case "sky" -> sky(sender, args);
      case "practice", "force", "act", "click", "peek", "phase" -> {
        String[] old = Arrays.copyOfRange(args, 1, args.length); // the old /ww <cmd> argument list
        switch (sub) {
          case "practice" -> ww.practice(sender, old);
          case "force" -> ww.force(sender, old);
          case "act" -> ww.act(sender, old);
          case "click" -> ww.click(sender, old);
          case "peek" -> ww.peek(sender);
          default -> ww.phase(sender, old);
        }
      }
      default -> help(sender);
    }
  }

  void help(CommandSender sender) {
    for (String line :
        List.of(
            "/ww debug hud <stateId|phase|role> [player]   preview a HUD for " + SHOW_SECONDS + " s (no game needed)",
            "/ww debug hud list [filter]  ·  hud cycle [seconds] [player]  ·  hud stop [player]",
            "/ww debug hud size auto|large|medium|small [player]   preview a size mode (keep one with /ww hud <size>)",
            "/ww debug ambience <phase> [player]   the resolved ambience entry, and its sting",
            "/ww debug sky <players>   the platform layout numbers for that many players",
            "/ww debug practice <bots> [player] · force <player|botN> <role>|clear · phase skip",
            "/ww debug act <player|botN> select|aim <target> | use <ability> · click <player> <target|ability:name> · peek",
            "The old /ww practice, force, act, click, peek and phase spellings still work."))
      say(sender, line);
  }

  // ----------------------------------------------------------------------- hud

  private void hud(CommandSender sender, String[] args) {
    if (args.length < 3) throw new IllegalArgumentException("Usage: /ww debug hud <stateId|phase|role> | list [filter] | cycle [seconds] | stop | size <mode>");
    String what = args[2].toLowerCase(Locale.ROOT);
    switch (what) {
      case "list" -> {
        var ids = HudPreview.list(args.length > 3 ? args[3] : "");
        say(sender, ids.size() + " states: " + String.join(", ", ids));
      }
      case "stop" -> {
        Player p = target(sender, args.length > 3 ? args[3] : null);
        stop(p);
        say(sender, "HUD preview stopped");
      }
      case "cycle" -> {
        int seconds = CYCLE_SECONDS;
        String named = null;
        for (int i = 3; i < args.length; i++) {
          if (args[i].matches("\\d{1,3}")) seconds = Math.max(1, Integer.parseInt(args[i]));
          else named = args[i];
        }
        Player p = target(sender, named);
        var entries = HudPreview.all();
        warnPack(sender, p);
        show(p, sender, entries, null, seconds, true);
        say(sender, "Cycling " + entries.size() + " HUD states, " + seconds + " s each (/ww debug hud stop to end)");
      }
      case "size" -> {
        if (args.length < 4 || !SIZES.contains(args[3].toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("Usage: /ww debug hud size auto|large|medium|small [player]");
        String size = args[3].toLowerCase(Locale.ROOT);
        Player p = target(sender, args.length > 4 ? args[4] : null);
        warnPack(sender, p);
        show(p, sender, List.of(HudPreview.find(HudPreview.SIZE_STATE).orElseThrow()), CustomHud.mode(size), SHOW_SECONDS, false);
        say(sender, "Previewing size " + size + " on " + HudPreview.SIZE_STATE + " (your saved size is not changed)");
      }
      default -> {
        var entry =
            HudPreview.find(args[2])
                .orElseThrow(() -> new IllegalArgumentException("Unknown HUD state '" + args[2] + "': try /ww debug hud list"));
        Player p = target(sender, args.length > 3 ? args[3] : null);
        warnPack(sender, p);
        show(p, sender, List.of(entry), null, SHOW_SECONDS, false);
        say(sender, "HUD " + entry.id() + ": " + entry.title() + " (" + SHOW_SECONDS + " s)");
      }
    }
  }

  private Player target(CommandSender sender, String name) {
    if (name != null) {
      Player p = Bukkit.getPlayerExact(name);
      if (p == null) throw new IllegalArgumentException("Player offline: " + name);
      return p;
    }
    if (sender instanceof Player p) return p;
    throw new IllegalArgumentException("From the console name a player");
  }

  private void warnPack(CommandSender sender, Player p) {
    if (plugin.skin == null || !plugin.skin.active(p))
      sender.sendMessage(Component.text("The resource pack is not active for " + p.getName() + ": the HUD glyphs will not show", NamedTextColor.RED));
  }

  /** Draws the entries in the player's action bar until the time is up; one preview per player. */
  private void show(Player p, CommandSender reporter, List<HudPreview.Entry> entries, Integer mode, int stepSeconds, boolean announce) {
    CustomHud hud = plugin.werewolf.customHud;
    if (hud == null) throw new IllegalArgumentException("The custom HUD is not loaded");
    stop(p);
    long started = System.currentTimeMillis();
    long total = (long) stepSeconds * 1000 * (announce ? entries.size() : 1);
    int[] last = {-1};
    ScheduledTask task =
        p.getScheduler()
            .runAtFixedRate(
                plugin,
                t -> {
                  long elapsed = System.currentTimeMillis() - started;
                  if (elapsed >= total) {
                    t.cancel();
                    shows.remove(p.getUniqueId(), t);
                    p.sendActionBar(Component.empty());
                    return;
                  }
                  int index = announce ? (int) (elapsed / (stepSeconds * 1000L)) : 0;
                  var entry = entries.get(index);
                  if (announce && index != last[0]) {
                    last[0] = index;
                    reporter.sendMessage(Component.text((index + 1) + "/" + entries.size() + " " + entry.id() + ": " + entry.title(), NamedTextColor.GRAY));
                  }
                  long into = announce ? elapsed - index * stepSeconds * 1000L : elapsed;
                  p.sendActionBar(hud.render(entry.at(into / 1000), mode != null ? mode : CustomHud.mode(plugin.hudSize(p))));
                },
                () -> shows.remove(p.getUniqueId()),
                1L,
                PERIOD_TICKS);
    if (task != null) shows.put(p.getUniqueId(), task);
  }

  private void stop(Player p) {
    ScheduledTask old = shows.remove(p.getUniqueId());
    if (old != null) old.cancel();
    p.sendActionBar(Component.empty());
  }

  // ------------------------------------------------------------------ ambience

  private void ambience(CommandSender sender, String[] args) {
    if (args.length < 3) throw new IllegalArgumentException("Usage: /ww debug ambience <phase> [player]   (" + String.join(", ", ambiencePhases()) + ")");
    String phase = args[2];
    var entry = AmbienceProfile.defaults().forPhase(phase, HudPreview.isNight(phase));
    say(sender, "Ambience " + phase + ": bed " + entry.ambient().sound() + " (" + entry.ambient().loopMillis() + " ms, vol " + entry.ambient().volume() + ")");
    say(sender, "Sting " + entry.sting().sound() + " (vol " + entry.sting().volume() + ") · fallback " + entry.fallback());
    Player p = args.length > 3 ? target(sender, args[3]) : sender instanceof Player self ? self : null;
    if (p == null) return;
    if (plugin.skin != null && plugin.skin.active(p)) {
      p.playSound(Sound.sound(Key.key(entry.sting().sound()), Sound.Source.PLAYER, 1f, 1f), Sound.Emitter.self());
      say(sender, "Sting played for " + p.getName());
    } else say(sender, "The resource pack is not active for " + p.getName() + ": no sting played");
  }

  static List<String> ambiencePhases() {
    List<String> out = new ArrayList<>(HudPreview.AMBIENCE_KEYS);
    for (var phase : WerewolfGame.Phase.values()) out.add(phase.name().toLowerCase(Locale.ROOT));
    return out;
  }

  // ----------------------------------------------------------------------- sky

  private void sky(CommandSender sender, String[] args) {
    if (args.length < 3 || !args[2].matches("\\d{1,3}"))
      throw new IllegalArgumentException("Usage: /ww debug sky <players " + PlatformLayout.MIN_PLAYERS + "-" + PlatformLayout.MAX_PLAYERS + ">");
    say(sender, skySummary(Integer.parseInt(args[2])));
  }

  /** The layout numbers of a platform for that many players (pure, tested). */
  static String skySummary(int players) {
    PlatformLayout l = PlatformLayout.of(players);
    var first = l.seats().get(0);
    double spacing = 2 * l.ringRadius() * Math.sin(Math.PI / players);
    return String.format(
        Locale.ROOT,
        "Sky platform for %d players: ring radius %.1f, platform radius %.1f, seat spacing %.2f, first seat (%.1f, %.1f) yaw %.0f",
        l.players(), l.ringRadius(), l.platformRadius(), spacing, first.x(), first.z(), first.yaw());
  }

  // ---------------------------------------------------------------- completion

  /** Completion for /ww debug ...; args[0] is "debug". */
  List<String> complete(String[] args) {
    String last = args[args.length - 1].toLowerCase(Locale.ROOT);
    return candidates(args).stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
  }

  static List<String> candidates(String[] args) {
    if (args.length == 2) return SUBS;
    String sub = args[1].toLowerCase(Locale.ROOT);
    if (sub.equals("hud")) {
      if (args.length == 3) {
        List<String> out = new ArrayList<>(List.of("list", "cycle", "stop", "size"));
        out.addAll(HudPreview.ids());
        out.addAll(HudPreview.roles());
        out.addAll(HudPreview.phases());
        return out.stream().distinct().toList();
      }
      String what = args[2].toLowerCase(Locale.ROOT);
      if (args.length == 4 && what.equals("size")) return SIZES;
      if (args.length == 4 && what.equals("cycle")) return List.of("2", "4", "8");
      if (args.length == 4 && what.equals("list")) return List.of("role", "night", "day", "badge", "danger", "dead", "seats", "palette");
      return onlinePlayers();
    }
    if (sub.equals("ambience")) return args.length == 3 ? ambiencePhases() : args.length == 4 ? onlinePlayers() : List.of();
    if (sub.equals("sky")) return args.length == 3 ? List.of("4", "8", "12", "20", "30", "45") : List.of();
    if (sub.equals("force") && args.length == 4) return WwCommand.roleNames();
    if (sub.equals("phase") && args.length == 3) return List.of("skip");
    if (sub.equals("act") && args.length == 4) return List.of("select", "aim", "use");
    return List.of();
  }

  private static List<String> onlinePlayers() {
    try {
      return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    } catch (RuntimeException | LinkageError e) {
      return List.of(); // no server (unit tests)
    }
  }
}
