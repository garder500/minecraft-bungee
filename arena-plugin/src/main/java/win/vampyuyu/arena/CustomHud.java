package win.vampyuyu.arena;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * The custom HUD, drawn from the resource pack instead of vanilla's bossbar and sidebar: glyphs of
 * font vampyuyu:hud2 in the action bar, each coloured (78, 100 + element, parameter) so the core
 * shader moves it to its place on screen (resource-pack/art/hud.py and art/fx_shaders.py). Pure
 * text: no Bukkit access.
 */
final class CustomHud {
  static final Key FONT = Key.key("vampyuyu", "hud2");
  private static final int ID_BASE = 100;
  private static final int SPRITE_BASE = 0xE000, NEG_BASE = 0xF000;
  /** Seats drawn in the panel's table strip (resource-pack/layout.py HUD_SEATS) and badge slots. */
  static final int SEATS = 20, BADGES = 4;

  private final Map<String, Integer> sprites = new HashMap<>();
  private final Map<String, Integer> elements = new HashMap<>();

  /** Reads hud-sprites.txt, which the pack's art script writes next to the sprites. */
  CustomHud() {
    try (var in = CustomHud.class.getResourceAsStream("/hud-sprites.txt")) {
      if (in == null) throw new IllegalStateException("hud-sprites.txt missing");
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        String[] parts = line.trim().split(" ");
        if (parts.length != 3) continue;
        (parts[0].equals("sprite") ? sprites : elements).put(parts[2], Integer.parseInt(parts[1]));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** What one player sees. {@code seconds} is the turn's time left, {@code fraction} its bar. */
  record State(
      String palette,
      String topIcon,
      int round,
      String label,
      long seconds,
      double fraction,
      boolean timed,
      boolean showRole,
      String roleIcon,
      String roleWord,
      String teamWord,
      String rolePalette,
      int alive,
      int total,
      List<String> seats,
      List<String> badges,
      int against) {}

  /** HUD sizes by name: the red channel of the glyphs (78 follows the window, 79 full, 80 medium, 81 small). */
  static int mode(String size) {
    return switch (size == null ? "auto" : size) {
      case "large" -> 79;
      case "medium" -> 80;
      case "small" -> 81;
      default -> 78;
    };
  }

  private Component glyph(int mode, String element, String sprite, int parameter) {
    Integer id = elements.get(element), index = sprites.get(sprite);
    if (id == null || index == null) return Component.empty();
    String chars = "" + (char) (SPRITE_BASE + index) + (char) (NEG_BASE + index);
    return Component.text(chars, TextColor.color(mode, ID_BASE + id, Math.max(0, Math.min(255, parameter))))
        .font(FONT);
  }

  /** A word's sprite key: lower case, anything else than letters and digits becomes one underscore. */
  static String word(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
  }

  /** Seconds left at which the plate and the clock turn red and flash. */
  static final int DANGER_SECONDS = 5;

  Component render(State s, int mode) {
    boolean danger = s.timed() && s.seconds() <= DANGER_SECONDS;
    long t = Math.max(0, Math.min(9 * 60 + 59, s.seconds()));
    // The plate flashes red every other second once the turn is nearly over.
    String plate = danger && t % 2 == 0 ? "danger" : s.palette();
    String clockKind = danger ? "x" : "d";
    Component out = Component.empty();
    // Top plate: phase medallion, round, label, draining bar and the clock.
    out = out.append(glyph(mode, "frame", "frame_" + plate, 0));
    out = out.append(glyph(mode, "icon", "icon_" + s.topIcon(), 0));
    out = out.append(glyph(mode, "round", "s" + Math.max(0, Math.min(9, s.round())), 0));
    out = out.append(glyph(mode, "label", "label_" + word(s.label()), 0));
    out = out.append(glyph(mode, "fill", "fill_" + (danger ? "danger" : s.palette()), (int) Math.round(s.fraction() * 255)));
    if (s.timed()) {
      String clock = String.format(Locale.ROOT, "%d%02d", t / 60, t % 60);
      out = out.append(glyph(mode, "t1", clockKind + clock.charAt(0), 0));
      out = out.append(glyph(mode, "tcolon", clockKind + "colon", 0));
      out = out.append(glyph(mode, "t3", clockKind + clock.charAt(1), 0));
      out = out.append(glyph(mode, "t4", clockKind + clock.charAt(2), 0));
    }
    // Right panel: role, team, the number alive and one pip per player.
    if (s.showRole()) {
      out = out.append(glyph(mode, "pframe", "pframe_" + s.rolePalette(), 0));
      out = out.append(glyph(mode, "picon", "picon_" + s.roleIcon(), 0));
      out = out.append(glyph(mode, "plabel", "plabel_" + word(s.roleWord()), 0));
      out = out.append(glyph(mode, "pteam", "pteam_" + word(s.teamWord()), 0));
      String alive = String.format(Locale.ROOT, "%2d", Math.min(99, s.alive()));
      String total = String.format(Locale.ROOT, "%2d", Math.min(99, s.total()));
      out = out.append(bigDigit(mode, "a1", alive.charAt(0))).append(bigDigit(mode, "a2", alive.charAt(1)));
      out = out.append(glyph(mode, "aslash", "dslash", 0));
      out = out.append(bigDigit(mode, "a3", total.charAt(0))).append(bigDigit(mode, "a4", total.charAt(1)));
      // The table strip: one dot per seat, in seat order.
      for (int i = 0; i < Math.min(SEATS, s.seats().size()); i++)
        out = out.append(glyph(mode, "m" + i, "dot_" + s.seats().get(i), 0));
      for (int i = 0; i < Math.min(BADGES, s.badges().size()); i++)
        out = out.append(glyph(mode, "b" + i, "badge_" + s.badges().get(i), 0));
      // During the day vote: how many ballots are against this player right now.
      if (s.against() >= 0) {
        boolean hot = s.against() > 0;
        String digits = String.format(Locale.ROOT, "%2d", Math.min(99, s.against()));
        out = out.append(glyph(mode, "vframe", "vframe_" + (hot ? "hot" : "calm"), 0));
        for (int i = 0; i < 2; i++) {
          char c = digits.charAt(i);
          out = out.append(glyph(mode, "v" + (i + 1), c == ' ' ? "dblank" : (hot ? "x" : "d") + c, 0));
        }
      }
    }
    return out;
  }

  private Component bigDigit(int mode, String element, char c) {
    return glyph(mode, element, c == ' ' ? "dblank" : "d" + c, 0);
  }

  // ------------------------------------------------------------------ derivation

  /** Palette names of resource-pack/layout.py HUD_PALETTES for a role colour. */
  static String palette(int color) {
    return switch (color) {
      case WerewolfPresentation.WEREWOLF -> "wolf";
      case WerewolfPresentation.SEER -> "seer";
      case WerewolfPresentation.WITCH -> "witch";
      case WerewolfPresentation.HUNTER -> "hunter";
      case WerewolfPresentation.CUPID -> "cupid";
      case WerewolfPresentation.GUARD -> "guard";
      case WerewolfPresentation.VOTE -> "vote";
      default -> "villager";
    };
  }

  /** The table strip for one viewer: dead, themselves, their ballot, their pack mates and lover, the rest. */
  static List<String> seats(WerewolfGame game, UUID viewer) {
    List<UUID> order = game.seatOrder();
    UUID ballot = game.ballot(viewer);
    UUID lover = game.lover(viewer);
    var pack = game.pack(viewer);
    List<String> out = new ArrayList<>();
    for (UUID p : order) {
      out.add(
          !game.alive().contains(p) ? "dead"
              : p.equals(viewer) ? "me"
              : p.equals(ballot) ? "ballot"
              : pack.contains(p) || p.equals(lover) ? "mate"
              : "alive");
    }
    return out;
  }

  /** What the viewer carries: their bond, potions left, infection, last guarded player. */
  static List<String> badges(WerewolfGame game, UUID viewer) {
    List<String> out = new ArrayList<>();
    if (!game.alive().contains(viewer)) return out;
    if (game.inLove(viewer)) out.add("lover");
    if (game.lifePotion(viewer)) out.add("life");
    if (game.deathPotion(viewer)) out.add("death");
    if (game.infectLeft(viewer)) out.add("infect");
    if (game.lastProtected(viewer) != null) out.add("guard");
    return out;
  }

  /** The state for a viewer of a werewolf game, from the same rules as the vanilla bossbar. */
  static State of(WerewolfGame game, UUID viewer, long seconds, int total, boolean faceUp) {
    String phase = game.phase();
    String role = game.selfRoleName(viewer);
    boolean dead = !game.alive().contains(viewer);
    boolean actor = game.actors().contains(viewer);
    boolean night = game.night();
    var boss = WerewolfPresentation.boss(game, viewer, seconds);
    String turn = WerewolfPresentation.turnRole(phase);
    String palette =
        dead
            ? "dead"
            : boss.color() == WerewolfPresentation.INK_DIM
                ? "sleep"
                : turn != null ? RoleInfo.of(turn).palette()
                : phase.equals("DAY_VOTE") || phase.equals("TRIAL") ? "vote" : "villager";
    String label;
    if (phase.equals("FINISHED")) label = "game over";
    else if (phase.equals("HUNTER_SHOT")) label = night && !actor ? "sleeping" : "last shot";
    else if (!night)
      label =
          switch (phase) {
            case "DAY_VOTE" -> "vote";
            case "DEFENSE" -> "defense";
            case "TRIAL" -> "trial";
            default -> "debate";
          };
    else if (!actor) label = "sleeping";
    else
      label =
          switch (phase) {
            case "CUPID" -> "cupid awake";
            case "GUARD" -> "guard awake";
            case "SEER" -> "seer awake";
            case "NIGHT" -> "wolves hunt";
            case "WITCH" -> "witch awake";
            default -> {
              RoleInfo info = RoleInfo.byPhase(phase);
              yield info == null ? "waiting" : info.call().label();
            }
          };
    if (phase.equals("MARTYR")) label = actor ? RoleInfo.of("MARTYR").call().label() : "vote";
    double fraction = boss.timer() && game.seconds() > 0 ? Math.max(0, Math.min(1, seconds / (double) game.seconds())) : 1;
    String rolePalette = dead ? "dead" : RoleInfo.of(role).palette();
    String team = dead ? "out" : RoleInfo.of(role).teamWord();
    return new State(
        palette,
        phase.equals("DAY_VOTE") ? "vote" : night ? "moon" : "sun",
        game.round(),
        label,
        seconds,
        fraction,
        boss.timer(),
        faceUp,
        WerewolfHud.iconName(role, dead),
        dead ? "fallen" : RoleInfo.of(role).word(),
        team,
        rolePalette,
        game.alive().size(),
        total,
        seats(game, viewer),
        badges(game, viewer),
        phase.equals("DAY_VOTE") && !dead && game.tallyVisible(viewer)
            ? game.votes(viewer).getOrDefault(viewer, 0)
            : -1);
  }
}
