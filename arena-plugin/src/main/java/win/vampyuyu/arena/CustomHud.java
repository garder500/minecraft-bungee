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
      boolean showRole,
      String roleIcon,
      String roleWord,
      String teamWord,
      String rolePalette,
      int alive,
      int total) {}

  private Component glyph(String element, String sprite, int parameter) {
    Integer id = elements.get(element), index = sprites.get(sprite);
    if (id == null || index == null) return Component.empty();
    String chars = "" + (char) (SPRITE_BASE + index) + (char) (NEG_BASE + index);
    return Component.text(chars, TextColor.color(78, ID_BASE + id, Math.max(0, Math.min(255, parameter))))
        .font(FONT);
  }

  private static String word(String text) {
    return text.toLowerCase(Locale.ROOT).replace(' ', '_');
  }

  private Component digit(String element, char c) {
    return glyph(element, c == ' ' ? "dblank" : "d" + c, 0);
  }

  Component render(State s) {
    Component out = Component.empty();
    // Top plate: phase, round, label, time and the draining bar.
    out = out.append(glyph("frame", "frame_" + s.palette(), 0));
    out = out.append(glyph("icon", "icon_" + s.topIcon(), 0));
    out = out.append(digit("round", (char) ('0' + Math.max(0, Math.min(9, s.round())))));
    out = out.append(glyph("label", "label_" + word(s.label()), 0));
    long t = Math.max(0, Math.min(99 * 60 + 59, s.seconds()));
    String clock = String.format(Locale.ROOT, "%02d%02d", t / 60, t % 60);
    out = out.append(digit("t1", clock.charAt(0))).append(digit("t2", clock.charAt(1)));
    out = out.append(glyph("tcolon", "dcolon", 0));
    out = out.append(digit("t3", clock.charAt(2))).append(digit("t4", clock.charAt(3)));
    out = out.append(glyph("fill", "fill_" + s.palette(), (int) Math.round(s.fraction() * 255)));
    // Right panel: role, team, and how many are alive.
    if (s.showRole()) {
      out = out.append(glyph("pframe", "pframe_" + s.rolePalette(), 0));
      out = out.append(glyph("picon", "picon_" + s.roleIcon(), 0));
      out = out.append(glyph("plabel", "plabel_" + word(s.roleWord()), 0));
      out = out.append(glyph("pteam", "pteam_" + word(s.teamWord()), 0));
      String alive = String.format(Locale.ROOT, "%2d", Math.min(99, s.alive()));
      String total = String.format(Locale.ROOT, "%2d", Math.min(99, s.total()));
      out = out.append(digit("a1", alive.charAt(0))).append(digit("a2", alive.charAt(1)));
      out = out.append(glyph("aslash", "dslash", 0));
      out = out.append(digit("a3", total.charAt(0))).append(digit("a4", total.charAt(1)));
    }
    return out;
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

  /** The state for a viewer of a werewolf game, from the same rules as the vanilla bossbar. */
  static State of(WerewolfGame game, UUID viewer, long seconds, int total, boolean faceUp) {
    String phase = game.phase();
    String role = game.role(viewer);
    boolean dead = !game.alive().contains(viewer);
    boolean actor = game.actors().contains(viewer);
    boolean night = game.night();
    var boss = WerewolfPresentation.boss(game, viewer, seconds);
    String palette = dead ? "dead" : boss.color() == WerewolfPresentation.INK_DIM ? "sleep" : palette(boss.color());
    String label;
    if (phase.equals("FINISHED")) label = "game over";
    else if (phase.equals("HUNTER_SHOT")) label = night && !actor ? "village sleeps" : "last shot";
    else if (!night) label = phase.equals("DAY_VOTE") ? "vote" : "debate";
    else if (!actor) label = "village sleeps";
    else
      label =
          switch (phase) {
            case "CUPID" -> "cupid awake";
            case "GUARD" -> "guard awake";
            case "SEER" -> "seer awake";
            case "NIGHT" -> "wolves hunt";
            case "WITCH" -> "witch awake";
            default -> "waiting";
          };
    double fraction = boss.timer() && game.seconds() > 0 ? Math.max(0, Math.min(1, seconds / (double) game.seconds())) : 1;
    String rolePalette = dead ? "dead" : palette(WerewolfPresentation.color(role));
    String team = dead ? "out" : WerewolfGame.isWolf(role) ? "wolf pack" : "village";
    return new State(
        palette,
        phase.equals("DAY_VOTE") ? "vote" : night ? "moon" : "sun",
        game.round(),
        label,
        seconds,
        fraction,
        faceUp,
        WerewolfHud.iconName(role, dead),
        dead ? "fallen" : WerewolfPresentation.roleName(role),
        team,
        rolePalette,
        game.alive().size(),
        total);
  }
}
