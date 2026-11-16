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
  private static final List<String> SUBS = List.of("help", "use", "start", "stop", "status", "reload", "debug");
  private static final List<String> DEBUG = List.of("skip", "state", "win", "kill", "plant", "seal");

  private final NexusArenaEngine plugin;
  private final Map<String, String> selected = new HashMap<>();

  public VbCommand(NexusArenaEngine plugin) {
    this.plugin = plugin;
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

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      if (!sender.hasPermission("nexusarena.admin"))
        throw new IllegalArgumentException("Arena administrator permission required");
      if (sender instanceof Player p && plugin.arenas.locked(p.getUniqueId()))
        throw new IllegalArgumentException("Administrator access outside an arena required");
      String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
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
        case "debug" -> debug(sender, args);
        default -> {
          for (String line :
              List.of(
                  "/vb use <arena> · start · stop · status · reload",
                  "/vb debug skip | state | win <a|b> | kill <player> | plant <a|b> | seal"))
            say(sender, line);
        }
      }
    } catch (Exception e) {
      sender.sendMessage(Component.text(NexusArenaEngine.reason(e), NamedTextColor.RED));
    }
    return true;
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
      default -> throw new IllegalArgumentException("Debug: " + String.join(", ", DEBUG));
    }
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission("nexusarena.admin")) return List.of();
    if (args.length == 1) return SUBS;
    if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return DEBUG;
    if (args.length == 2 && args[0].equalsIgnoreCase("use")) return plugin.arenas.all().stream().map(s -> s.config.id()).toList();
    return List.of();
  }
}
