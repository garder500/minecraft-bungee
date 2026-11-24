package win.vampyuyu.werewolf.hud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import win.vampyuyu.werewolf.WerewolfGame;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;
import win.vampyuyu.werewolf.roles.RoleInfo;

/**
 * The custom HUD, drawn from the resource pack instead of vanilla's bossbar and sidebar: glyphs of
 * font vampyuyu:hud2 in the action bar, each coloured (78, 100 + element, parameter) so the core
 * shader moves it to its place on screen (resource-pack/art/hud.py and art/fx_shaders.py).
 *
 * <p>No word is painted in a sprite: the phase, the role, the team and the vote caption are text, read from the
 * language files ({@link HudLang}) and written one glyph per character with the HUD's own pixel fonts
 * (resource-pack/art/hud_font.py). For a character the colour's parameter is how far right of its line's origin
 * the glyph starts, so this class lays the line out: capitals, proportional advances, a cut when a word is too
 * wide. resource-pack/art/hud_sheet.py is the Python twin (scripts/test_hud_sheet.py). No Bukkit access.
 */
public final class CustomHud {
  public static final Key FONT = Key.key("vampyuyu", "hud2");
  private static final int ID_BASE = 100;
  private static final int SPRITE_BASE = 0xE000, NEG_BASE = 0xF000;
  /** Seats drawn in the panel's table strip (resource-pack/layout.py HUD_SEATS) and badge slots. */
  public static final int SEATS = 20, BADGES = 4;

  private final Map<String, Integer> sprites = new HashMap<>();
  private final Map<String, Integer> elements = new HashMap<>();
  /** Text: the advance of each character sprite, of each font's space, and each line's font, alignment and width. */
  private final Map<String, Integer> advances = new HashMap<>();
  private final Map<String, Integer> spaces = new HashMap<>();
  private final Map<String, Line> lines = new HashMap<>();

  private record Line(String font, String align, int limit) {}

  /** Reads hud-sprites.txt, which the pack's art script writes next to the sprites. */
  public CustomHud() {
    try (var in = CustomHud.class.getResourceAsStream("/hud-sprites.txt")) {
      if (in == null) throw new IllegalStateException("hud-sprites.txt missing");
      for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
        String[] parts = line.trim().split(" ");
        if (parts.length < 3) continue;
        switch (parts[0]) {
          case "sprite" -> sprites.put(parts[2], Integer.parseInt(parts[1]));
          case "element" -> elements.put(parts[2], Integer.parseInt(parts[1]));
          case "advance" -> advances.put(parts[2], Integer.parseInt(parts[1]));
          case "space" -> spaces.put(parts[2], Integer.parseInt(parts[1]));
          case "text" -> {
            if (parts.length == 5) lines.put(parts[1], new Line(parts[2], parts[3], Integer.parseInt(parts[4])));
          }
          default -> {}
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** What one player sees. {@code seconds} is the turn's time left, {@code fraction} its bar. */
  public record State(
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
  public static int mode(String size) {
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

  /** A word's key (language files, hud.phase.<key>): lower case, anything else than letters and digits becomes one underscore. */
  public static String word(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
  }

  // ------------------------------------------------------------------ text

  private static String charSprite(String font, char ch) {
    return "f" + font + "_" + String.format(Locale.ROOT, "%04x", (int) ch);
  }

  /**
   * What the HUD can write of a text: capitals, single spaces, the accented letters its fonts have; another
   * accented letter loses its accent, anything else becomes '?'. (hud_font.normalise)
   */
  String normalise(String text) {
    String upper =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .toUpperCase(Locale.ROOT)
            .replace('\u2019', '\'')
            .replace("\u0152", "OE")
            .replace("\u00C6", "AE")
            .strip()
            .replaceAll("\\s+", " ");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < upper.length(); i++) {
      char ch = upper.charAt(i);
      if (ch == ' ' || advances.containsKey(charSprite("m", ch))) {
        out.append(ch);
        continue;
      }
      char plain = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).charAt(0);
      out.append(advances.containsKey(charSprite("m", plain)) ? plain : '?');
    }
    return out.toString();
  }

  /** Pixel width of a normalised line in a font: each character's ink and one pixel between two. */
  int width(String font, String line) {
    int total = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      total += ch == ' ' ? spaces.getOrDefault(font, 0) : advances.getOrDefault(charSprite(font, ch), 0);
    }
    return Math.max(0, total - 1);
  }

  /**
   * The line cut to {@code limit} pixels, ending with a dot when something was dropped. A net for words that come
   * from a data override: the shipped ones all fit (scripts/test_hud_sheet.py).
   */
  String fit(String font, String line, int limit) {
    if (width(font, line) <= limit) return line;
    while (!line.isEmpty() && width(font, line.stripTrailing() + ".") > limit) line = line.substring(0, line.length() - 1);
    return line.stripTrailing() + ".";
  }

  /** One line of text at a text element: a glyph per character, its parameter where its cell starts. */
  private Component text(int mode, String element, String raw) {
    Line spec = lines.get(element);
    if (spec == null) return Component.empty();
    String line = fit(spec.font(), normalise(raw), spec.limit());
    int free = spec.limit() - width(spec.font(), line);
    int x = spec.align().equals("right") ? free : spec.align().equals("centre") ? free / 2 : 0;
    Component out = Component.empty();
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == ' ') {
        x += spaces.getOrDefault(spec.font(), 0);
        continue;
      }
      String sprite = charSprite(spec.font(), ch);
      out = out.append(glyph(mode, element, sprite, x));
      x += advances.getOrDefault(sprite, 0);
    }
    return out;
  }

  /** The phase on the top plate: the headline font, or the bold one when the headline would be too wide. */
  private Component label(int mode, String phase) {
    Line headline = lines.get("label");
    boolean wide = headline != null && width(headline.font(), normalise(phase)) > headline.limit();
    return text(mode, wide ? "label2" : "label", phase);
  }

  // ------------------------------------------------------------------ rendering

  /** Seconds left at which the plate and the clock turn red and flash. */
  public static final int DANGER_SECONDS = 5;

  /** The HUD in English (tests and tools). */
  public Component render(State s, int mode) {
    return render(s, mode, HudLang.DEFAULT);
  }

  /** The HUD of one player; {@code lang} is their language file ({@link HudLang#of}). */
  public Component render(State s, int mode, String lang) {
    boolean danger = s.timed() && s.seconds() <= DANGER_SECONDS;
    long t = Math.max(0, Math.min(9 * 60 + 59, s.seconds()));
    // The plate flashes red every other second once the turn is nearly over.
    String plate = danger && t % 2 == 0 ? "danger" : s.palette();
    String clockKind = danger ? "x" : "d";
    Component out = Component.empty();
    // Top plate: phase medallion, round, the phase's name and the clock (the time as digits and a bar that drains).
    out = out.append(glyph(mode, "frame", "frame_" + plate, 0));
    out = out.append(glyph(mode, "icon", "icon_" + s.topIcon(), 0));
    out = out.append(glyph(mode, "round", "s" + Math.max(0, Math.min(9, s.round())), 0));
    out = out.append(label(mode, HudLang.tr(lang, "hud.phase." + word(s.label()), s.label())));
    if (s.timed()) {
      // The draining bar: the shader stretches it to the blue channel's share of its full width.
      out = out.append(glyph(mode, "fill", "fill_" + plate, (int) Math.round(Math.max(0, Math.min(1, s.fraction())) * 255)));
      String clock = String.format(Locale.ROOT, "%d%02d", t / 60, t % 60);
      out = out.append(glyph(mode, "t1", clockKind + clock.charAt(0), 0));
      out = out.append(glyph(mode, "tcolon", clockKind + "colon", 0));
      out = out.append(glyph(mode, "t3", clockKind + clock.charAt(1), 0));
      out = out.append(glyph(mode, "t4", clockKind + clock.charAt(2), 0));
    }
    // Right panel: role medallion, the number alive, the badges, then the role, the team and one pip per player.
    if (s.showRole()) {
      out = out.append(glyph(mode, "pframe", "pframe_" + s.rolePalette(), 0));
      out = out.append(glyph(mode, "picon", "picon_" + s.roleIcon(), 0));
      out = out.append(text(mode, "pcount", Math.min(99, s.alive()) + "/" + Math.min(99, s.total())));
      out = out.append(text(mode, "prole", HudLang.tr(lang, "hud.role." + word(s.roleWord()), s.roleWord())));
      out = out.append(text(mode, "pteam", HudLang.tr(lang, "hud.team." + word(s.teamWord()), s.teamWord())));
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
        out = out.append(text(mode, "vcap1", HudLang.tr(lang, "hud.votes.line1", "VOTES")));
        out = out.append(text(mode, "vcap2", HudLang.tr(lang, "hud.votes.line2", "AGAINST YOU")));
        for (int i = 0; i < 2; i++) {
          char c = digits.charAt(i);
          if (c != ' ') out = out.append(glyph(mode, "v" + (i + 1), (hot ? "x" : "d") + c, 0));
        }
      }
    }
    return out;
  }

  // ------------------------------------------------------------------ derivation

  /** Palette names of resource-pack/layout.py HUD_PALETTES for a role colour. */
  public static String palette(int color) {
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
  public static List<String> seats(WerewolfGame game, UUID viewer) {
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
  public static List<String> badges(WerewolfGame game, UUID viewer) {
    List<String> out = new ArrayList<>();
    if (!game.alive().contains(viewer)) return out;
    if (game.inLove(viewer)) out.add("lover");
    if (viewer.equals(game.mayor())) out.add("mayor");
    if (game.silenced(viewer) && !game.exiled(viewer)) out.add("silenced");
    // Only what the player believes they are: a drunk witch has no potions to show before he wakes.
    String believed = game.selfRoleName(viewer);
    if (believed.equals("WITCH") && game.lifePotion(viewer)) out.add("life");
    if (believed.equals("WITCH") && game.deathPotion(viewer)) out.add("death");
    if (believed.equals("INFECTOR") && game.infectLeft(viewer)) out.add("infect");
    if (believed.equals("GUARD") && game.lastProtected(viewer) != null) out.add("guard");
    return out;
  }

  /** The state for a viewer of a werewolf game, from the same rules as the vanilla bossbar. */
  public static State of(WerewolfGame game, UUID viewer, long seconds, int total, boolean faceUp) {
    String phase = game.phase();
    String role = game.selfRoleName(viewer);
    boolean dead = !game.alive().contains(viewer);
    boolean actor = game.actors().contains(viewer);
    boolean night = game.night();
    var boss = WerewolfPresentation.boss(game, viewer, seconds);
    String turn = WerewolfPresentation.turnRole(phase);
    // A day pause only its actor may name (the martyr's) wears the vote colours for everybody else.
    boolean neutralPause = phase.equals("MARTYR") && !actor;
    String palette =
        dead
            ? "dead"
            : boss.color() == WerewolfPresentation.INK_DIM
                ? "sleep"
                : neutralPause ? "vote"
                : turn != null ? RoleInfo.of(turn).palette()
                : phase.equals("DAY_VOTE") || phase.equals("TRIAL") || phase.equals("ELECTION") ? "vote" : "villager";
    String label;
    if (phase.equals("FINISHED")) label = "game over";
    else if (phase.equals("HUNTER_SHOT")) label = night && !actor ? "sleeping" : "last shot";
    else if (!night)
      label =
          switch (phase) {
            case "DAY_VOTE" -> "vote";
            case "ELECTION" -> "election";
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
            case "AMULET" -> "amulet";
            default -> {
              RoleInfo info = RoleInfo.byPhase(phase);
              yield info == null ? "waiting" : info.call().label();
            }
          };
    if (phase.equals("MARTYR")) label = actor ? RoleInfo.of("MARTYR").call().label() : "vote";
    if (game.exiled(viewer) && !night && !phase.equals("FINISHED")) label = "away";
    double fraction = boss.timer() && game.seconds() > 0 ? Math.max(0, Math.min(1, seconds / (double) game.seconds())) : 1;
    String rolePalette = dead ? "dead" : RoleInfo.of(role).palette();
    String team = dead ? "out" : game.isMinion(viewer) ? "wolf side" : RoleInfo.of(role).teamWord();
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
