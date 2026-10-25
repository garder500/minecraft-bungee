package win.vampyuyu.werewolf.roles;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import win.vampyuyu.werewolf.DataFiles;
import win.vampyuyu.werewolf.WerewolfGame;

/**
 * What a role is, apart from how it plays: its name, team, balance value, colour, HUD words and
 * icon, the text it reads, and the night call it owns. One JSON file per role in resources/roles,
 * read by the plugin and by the pack's art scripts (resource-pack/layout.py), so a role is
 * described in a single place. Pure data: no Bukkit.
 */
public record RoleInfo(
    String id,
    String name,
    String team,
    int impact,
    int max,
    int color,
    String palette,
    String icon,
    String word,
    String teamWord,
    String help,
    String seerSees,
    Call call) {

  /** The night call (or day interruption) a role owns, with every text its turn needs. */
  public record Call(String phase, String label, String title, String subtitle, String instruction) {}

  /** The JSON shape of one role file. */
  private static final class Raw {
    public String name, team, color, palette, icon, word, teamWord, help, seerSees;
    public int impact, max = 1;
    public Raw.CallRaw call;

    public static final class CallRaw {
      public String phase, label, title, subtitle, instruction;
    }
  }

  private static final Gson GSON = new Gson();
  /** The loaded tables, swapped as one so a reload never shows half old and half new roles. */
  private record Tables(Map<String, RoleInfo> roles, Map<String, RoleInfo> byPhase) {}

  private static volatile Tables tables = read();

  private static Tables read() {
    Map<String, RoleInfo> roles = new LinkedHashMap<>();
    Map<String, RoleInfo> byPhase = new HashMap<>();
    for (WerewolfGame.Role role : WerewolfGame.Role.values()) {
      RoleInfo info = load(role.name());
      roles.put(role.name(), info);
      if (info.call() != null) byPhase.put(info.call().phase(), info);
    }
    return new Tables(roles, byPhase);
  }

  /**
   * Re-reads every role (jar defaults and overrides). A broken file throws and leaves the old
   * roles in place. Games already running keep the values they read.
   */
  public static void reload() {
    tables = read();
  }

  private static RoleInfo load(String id) {
    Raw raw = new Raw();
    try (InputStream in = DataFiles.open(RoleInfo.class, "/roles/" + id + ".json")) {
      if (in != null) raw = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Raw.class);
    } catch (IOException | com.google.gson.JsonParseException e) {
      // No cause: the command prints the root cause, and the role id is what tells which file is broken.
      throw new IllegalArgumentException("roles/" + id + ".json: " + e.getMessage());
    }
    String name = raw.name != null ? raw.name : fallbackName(id);
    String team = raw.team != null ? raw.team : "village";
    String word = raw.word != null ? raw.word : name.toUpperCase(Locale.ROOT);
    String teamWord =
        raw.teamWord != null
            ? raw.teamWord
            : switch (team) {
              case "wolves" -> "WOLF PACK";
              case "vampires" -> "VAMPIRES";
              case "cult" -> "THE CULT";
              case "solo" -> "ALONE";
              default -> "VILLAGE";
            };
    Call call =
        raw.call == null
            ? null
            : new Call(
                raw.call.phase,
                raw.call.label,
                raw.call.title,
                raw.call.subtitle,
                raw.call.instruction);
    return new RoleInfo(
        id,
        name,
        team,
        raw.impact,
        Math.max(1, raw.max),
        raw.color == null ? 0xe9d99b : Integer.parseInt(raw.color, 16),
        raw.palette != null ? raw.palette : "villager",
        raw.icon != null ? raw.icon : "villager",
        word,
        teamWord,
        raw.help != null ? raw.help : "",
        raw.seerSees,
        call);
  }

  private static String fallbackName(String id) {
    String spaced = id.replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  /** The role by its enum name; an unknown name reads as a plain villager. */
  public static RoleInfo of(String role) {
    RoleInfo found = role == null ? null : tables.roles().get(role);
    return found != null ? found : tables.roles().get("VILLAGER");
  }

  /** The role that owns a night call, or null when the phase is not a role's call. */
  public static RoleInfo byPhase(String phase) {
    return tables.byPhase().get(phase);
  }

  public static Collection<RoleInfo> all() {
    return Collections.unmodifiableCollection(tables.roles().values());
  }
}
