package win.vampyuyu.arena;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/**
 * The screen outside the menus: the bossbar on top says the phase, who acts and the time left; the
 * sidebar on the right carries the player's own role and the number of players alive, nothing else
 * about the cast. Must run on the player's thread.
 */
final class WerewolfHud {
  /** Role cards as font glyphs (resource-pack/art/render.py, font vampyuyu:hud). */
  static final Key FONT = Key.key("vampyuyu", "hud");

  static final List<String> CARDS =
      List.of("back", "villager", "werewolf", "seer", "cupid", "hunter", "witch", "guard");

  /** Glyph of a card: the back, a role card, or its grey version once the player is dead. */
  static char glyph(String role, boolean dead) {
    int found = CARDS.indexOf(role.toLowerCase(Locale.ROOT));
    // Only the first roles have a painted card: the others use the villager's.
    int index = found >= 0 ? found : role.equals("back") ? 0 : CARDS.indexOf("villager");
    return (char) ('\uE000' + index + (dead && index > 0 ? 0x10 : 0));
  }

  /** Role and HUD icons as font glyphs (resource-pack/art/icons.py, font vampyuyu:icons). */
  static final Key ICONS = Key.key("vampyuyu", "icons");

  /** The glyphs of the icons font, in order: the pack's art script writes the list (icon-names.txt). */
  static final List<String> ICON_NAMES = iconNames();

  private static List<String> iconNames() {
    List<String> base =
        List.of(
            "wolf", "villager", "seer", "witch", "hunter", "cupid", "guard", "dead", "moon", "sun",
            "vote", "timer", "alive", "alpha", "infector", "cub", "scapegoat", "bear");
    try (var in = WerewolfHud.class.getResourceAsStream("/icon-names.txt")) {
      if (in == null) return base;
      List<String> names =
          new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .lines()
              .map(String::trim)
              .filter(l -> !l.isEmpty())
              .toList();
      return names.isEmpty() ? base : names;
    } catch (java.io.IOException e) {
      return base;
    }
  }

  static Component icon(String name) {
    int index = ICON_NAMES.indexOf(name);
    return index < 0
        ? Component.empty()
        : Component.text(String.valueOf((char) ('\uE000' + index))).font(ICONS);
  }

  /** An icon followed by text; the icon's font must not leak into the text (children inherit). */
  static Component withIcon(String name, Component text) {
    return Component.text().append(icon(name)).append(Component.text(" ")).append(text).build();
  }

  /** Icon of a role, or of the dead once the player is out. */
  static String iconName(String role, boolean dead) {
    if (dead) return "dead";
    return RoleInfo.of(role).icon();
  }

  private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
  private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

  void show(ArenaSession s, Player p, WerewolfGame game, boolean cardFaceUp, boolean skinned) {
    UUID id = p.getUniqueId();
    long seconds = Math.max(0, (s.phaseEndsAt - System.currentTimeMillis() + 999) / 1000);
    var boss = WerewolfPresentation.boss(game, id, seconds);
    float progress =
        boss.timer() && game.seconds() > 0
            ? Math.max(0f, Math.min(1f, seconds / (float) game.seconds()))
            : 1f;
    // The moon or sun icon replaces the ☾ / ☀ character of the text.
    String words = skinned ? boss.text().replaceFirst("^[☾☀] ", "") : boss.text();
    Component text = Component.text(words, TextColor.color(boss.color()));
    final Component title =
        skinned && !game.phase().equals("FINISHED")
            ? withIcon(game.night() ? "moon" : "sun", text)
            : text;
    BossBar bar =
        bars.computeIfAbsent(
            id,
            k -> {
              BossBar created =
                  BossBar.bossBar(
                      title, progress, barColor(boss.color()), BossBar.Overlay.PROGRESS);
              p.showBossBar(created);
              return created;
            });
    if (skinned) {
      // The resource pack draws the HUD itself (CustomHud): no vanilla bossbar, no sidebar.
      p.hideBossBar(bar);
      sidebar(s, p, game, cardFaceUp, true);
      return;
    }
    p.showBossBar(bar); // No-op while shown; brings it back after a reconnect.
    bar.name(title);
    bar.progress(progress);
    bar.color(barColor(boss.color()));
    sidebar(s, p, game, cardFaceUp, skinned);
  }

  /** Nearest of the six bossbar colours to the role colour. */
  static BossBar.Color barColor(int color) {
    return switch (color) {
      case WerewolfPresentation.WEREWOLF -> BossBar.Color.RED;
      case WerewolfPresentation.SEER -> BossBar.Color.PURPLE;
      case WerewolfPresentation.WITCH -> BossBar.Color.GREEN;
      case WerewolfPresentation.CUPID -> BossBar.Color.PINK;
      case WerewolfPresentation.GUARD -> BossBar.Color.BLUE;
      case WerewolfPresentation.HUNTER, WerewolfPresentation.VOTE -> BossBar.Color.YELLOW;
      default -> BossBar.Color.WHITE;
    };
  }

  private void sidebar(
      ArenaSession s, Player p, WerewolfGame game, boolean cardFaceUp, boolean skinned) {
    Scoreboard board = boards.get(p.getUniqueId());
    if (board == null) {
      // Folia has no scoreboard API: the bossbar and the menus still say everything.
      ScoreboardManager manager;
      try {
        manager = Bukkit.getScoreboardManager();
      } catch (UnsupportedOperationException folia) {
        return;
      }
      if (manager == null) return;
      board = manager.getNewScoreboard();
      Objective objective =
          board.registerNewObjective("werewolf", Criteria.DUMMY, Component.empty());
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      objective.numberFormat(NumberFormat.blank());
      boards.put(p.getUniqueId(), board);
      p.setScoreboard(board);
    }
    if (p.getScoreboard() != board) p.setScoreboard(board);
    voteTags(board, s, game, p.getUniqueId());
    Objective objective = board.getObjective("werewolf");
    if (objective == null) return;
    // With the pack the sidebar is part of the custom HUD: only the vote tags keep the scoreboard.
    if (skinned) {
      if (objective.getDisplaySlot() != null) objective.setDisplaySlot(null);
      return;
    }
    if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    UUID id = p.getUniqueId();
    String role = game.role(id);
    boolean dead = !game.alive().contains(id);
    int roleColor = dead ? WerewolfPresentation.INK_DIM : WerewolfPresentation.color(role);
    Component roleLine =
        Component.text(
            cardFaceUp ? WerewolfPresentation.roleName(role) : "Dealing your role…",
            TextColor.color(roleColor));
    objective.displayName(
        skinned && cardFaceUp
            ? withIcon(iconName(role, dead), roleLine)
            : roleLine);
    List<Component> lines = new ArrayList<>();
    lines.add(
        Component.text(
            cardFaceUp ? WerewolfPresentation.team(role) + (dead ? " · out" : "") : "",
            TextColor.color(WerewolfPresentation.INK_MUTED)));
    lines.add(Component.empty());
    Component alive =
        Component.text("Alive ", TextColor.color(WerewolfPresentation.INK_MUTED))
            .append(
                Component.text(
                    game.alive().size() + " / " + s.players.size(),
                    TextColor.color(WerewolfPresentation.INK)));
    lines.add(skinned ? withIcon("alive", alive) : alive);
    UUID partner = game.partner(id);
    if (partner != null) {
      lines.add(Component.empty());
      lines.add(
          Component.text(
              "♥ " + s.players.getOrDefault(partner, "Your lover"),
              TextColor.color(WerewolfPresentation.CUPID)));
    }
    List<UUID> pack = game.pack(id);
    if (!pack.isEmpty()) {
      lines.add(Component.empty());
      lines.add(Component.text("Pack", TextColor.color(WerewolfPresentation.INK_MUTED)));
      for (UUID mate : pack)
        lines.add(
            Component.text(
                " " + s.players.getOrDefault(mate, "Wolf"),
                TextColor.color(
                    game.alive().contains(mate)
                        ? WerewolfPresentation.WEREWOLF
                        : WerewolfPresentation.INK_DIM)));
    }
    for (int i = 0; i < 15; i++) {
      String entry = "line" + i;
      if (i < lines.size()) {
        Score score = objective.getScore(entry);
        score.setScore(lines.size() - i);
        score.customName(lines.get(i));
      } else board.resetScores(entry);
    }
  }

  /**
   * During the day vote, everyone's ballots hang under their name tag ("✖ 3 votes"), the same
   * public totals the vote menu shows. Outside the vote the line disappears.
   */
  private static void voteTags(Scoreboard board, ArenaSession s, WerewolfGame game, UUID viewer) {
    Objective tags = board.getObjective("votes");
    if (tags == null) tags = board.registerNewObjective("votes", Criteria.DUMMY, Component.empty());
    if (!game.phase().equals("DAY_VOTE")) {
      if (tags.getDisplaySlot() != null) tags.setDisplaySlot(null);
      return;
    }
    if (tags.getDisplaySlot() != DisplaySlot.BELOW_NAME) tags.setDisplaySlot(DisplaySlot.BELOW_NAME);
    Map<UUID, Integer> totals = game.votes(viewer);
    for (var entry : s.players.entrySet()) {
      int votes = totals.getOrDefault(entry.getKey(), 0);
      Score score = tags.getScore(entry.getValue());
      score.setScore(votes);
      score.numberFormat(
          votes == 0
              ? NumberFormat.blank()
              : NumberFormat.fixed(
                  Component.text(
                      "✖ " + votes + (votes == 1 ? " vote" : " votes"),
                      TextColor.color(WerewolfPresentation.VOTE))));
    }
  }

  /**
   * Takes the ballots off the name tags as soon as the vote is counted: they last showed the
   * tally of the previous second, which a last-moment vote may contradict.
   */
  void hideVotes(Player p) {
    Scoreboard board = boards.get(p.getUniqueId());
    Objective tags = board == null ? null : board.getObjective("votes");
    if (tags != null && tags.getDisplaySlot() != null) tags.setDisplaySlot(null);
  }

  /** Back to the server's scoreboard and no bossbar, when the player leaves or the game ends. */
  void clear(Player p) {
    BossBar bar = bars.remove(p.getUniqueId());
    if (bar != null) p.hideBossBar(bar);
    if (boards.remove(p.getUniqueId()) != null)
      try {
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
      } catch (UnsupportedOperationException ignored) {
      }
  }
}
