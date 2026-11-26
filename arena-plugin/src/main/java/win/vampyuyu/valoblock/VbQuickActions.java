package win.vampyuyu.valoblock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Quick Actions dialog. The vanilla client has a "Quick Actions" key (default G, rebindable in Controls) that opens
 * the dialogs listed in the data tag {@code minecraft:quick_actions}; the server cannot read any other key. The dialog
 * is static data (the same for everybody, it cannot change per player), so it is shipped as a small datapack written
 * into the world's {@code datapacks} folder at start (idempotent, creative server only). Its buttons run the /vb
 * commands, which do the per-player checks (phase, match) and answer with a short message.
 */
public final class VbQuickActions {
  /** Shown in the agent select and the Armory tooltips. */
  public static final String TIP = "Tip: bind Quick Actions (Controls) to B to open the shop";

  /** Datapack folder name and dialog id. */
  static final String PACK = "vampyuyu-valoblock-quick";
  static final String DIALOG_ID = "vampyuyu:valoblock";

  public enum Action { SHOP, AGENT, MAP, VOTE }

  /** One button: what the dialog shows and the command it runs (with the leading slash). */
  public record Button(Action action, String label, String tooltip, String command) {}

  private VbQuickActions() {}

  /** The buttons, in order. */
  public static List<Button> buttons() {
    return List.of(
        new Button(Action.SHOP, "Shop", "Open the Armory (buy phase only)", "/vb buy"),
        new Button(Action.AGENT, "Agent", "Open the agent select (while agents are chosen)", "/vb agent"),
        new Button(Action.MAP, "Map", "Open the tactical map for the ability in your hand", "/vb tac"),
        new Button(Action.VOTE, "Vote", "Reopen the map vote (while voting is open)", "/vb vote"));
  }

  /**
   * Whether an action can be used now: null when it can, else the short message the player gets. {@code inMatch}: the
   * player is in a ValoBlock arena; {@code voting}: the lobby map vote is open. (The commands apply the same rules.)
   */
  public static String unavailable(Action a, boolean inMatch, VbPhase phase, boolean voting) {
    if (!inMatch) return "You are not in a ValoBlock match";
    return switch (a) {
      case SHOP -> phase == VbPhase.BUY ? null : "The Armory opens during the buy phase";
      case AGENT -> phase == VbPhase.AGENT_SELECT ? null : "Agent select is over";
      case VOTE -> phase == VbPhase.QUEUE && voting ? null : "The map vote is not open";
      case MAP -> phase == VbPhase.COMBAT || phase == VbPhase.PLANTED ? null : "The tactical map opens with a placed ability during combat";
    };
  }

  /** The held item is not an ability placed on the map: the minimap (top-left corner of the HUD) is the map to read. */
  public static final String MAP_HINT = "Hold an ability that is placed on the map; otherwise the minimap in the top-left corner is enough";

  // ------------------------------------------------------------------ datapack

  private static String esc(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /** The dialog: a multi_action with one button per {@link #buttons()} (run_command has no leading slash in 26.x). */
  public static String dialogJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"type\":\"minecraft:multi_action\",\"title\":{\"text\":\"ValoBlock\"},\"can_close_with_escape\":true,")
        .append("\"body\":[{\"type\":\"minecraft:plain_message\",\"contents\":{\"text\":\"Quick actions for a ValoBlock match. Outside a match they only say so.\"}}],")
        .append("\"columns\":2,\"actions\":[");
    boolean first = true;
    for (Button b : buttons()) {
      if (!first) sb.append(',');
      first = false;
      sb.append("{\"label\":{\"text\":\"").append(esc(b.label())).append("\"},\"tooltip\":{\"text\":\"").append(esc(b.tooltip()))
          .append("\"},\"width\":120,\"action\":{\"type\":\"minecraft:run_command\",\"command\":\"").append(esc(b.command().substring(1))).append("\"}}");
    }
    sb.append("],\"exit_action\":{\"label\":{\"text\":\"Close\"},\"width\":120}}");
    return sb.toString();
  }

  public static String tagJson() {
    return "{\"values\":[\"" + DIALOG_ID + "\"]}";
  }

  public static String mcmeta() {
    return "{\"pack\":{\"description\":\"ValoBlock quick actions\",\"min_format\":[82,0],\"max_format\":[1000,0]}}";
  }

  /** The files of the datapack, relative to its folder. */
  public static Map<String, String> files() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("pack.mcmeta", mcmeta());
    m.put("data/vampyuyu/dialog/valoblock.json", dialogJson());
    m.put("data/minecraft/tags/dialog/quick_actions.json", tagJson());
    return m;
  }

  /** Writes the files that are missing or different; true when something changed (a restart is then needed). */
  static boolean write(Path pack) throws IOException {
    boolean changed = false;
    for (Map.Entry<String, String> e : files().entrySet()) {
      Path f = pack.resolve(e.getKey());
      if (Files.isRegularFile(f) && Files.readString(f, StandardCharsets.UTF_8).equals(e.getValue())) continue;
      Files.createDirectories(f.getParent());
      Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
      changed = true;
    }
    return changed;
  }

  /** Creative server only (the other servers never get the quick action). A change needs a restart to apply. */
  static void install(JavaPlugin plugin, String serverId) {
    if (!"creative".equals(serverId)) return;
    try {
      String world = "world";
      Path properties = Path.of("server.properties");
      if (Files.isRegularFile(properties))
        for (String line : Files.readAllLines(properties))
          if (line.startsWith("level-name=") && line.length() > 11) world = line.substring(11).trim();
      Path pack = plugin.getServer().getWorldContainer().toPath().resolve(world).resolve("datapacks").resolve(PACK);
      if (write(pack)) plugin.getLogger().info("ValoBlock quick actions datapack installed; restart to apply");
    } catch (IOException | RuntimeException e) {
      plugin.getLogger().warning("ValoBlock quick actions datapack not installed: " + e);
    }
  }
}
