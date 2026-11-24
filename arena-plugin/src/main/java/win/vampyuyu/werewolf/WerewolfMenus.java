package win.vampyuyu.werewolf;

import static win.vampyuyu.werewolf.ambience.WerewolfPresentation.*;

import java.util.*;
import win.vampyuyu.arena.ArenaLang;
import win.vampyuyu.arena.ArenaLang.Lang;
import win.vampyuyu.arena.CompassGUI;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;

/**
 * Layout of the seven role menus (design guide "Les menus de rôle"). Pure: it says which item goes
 * in which raw slot of the 54-slot window and what a click does; CompassGUI turns it into items.
 *
 * <p>Raw slots: 0-53 chest (the painted scene, heads on a ring), 54-80 the player's inventory rows
 * (painted plaques and buttons), 81-89 the hotbar (card, timer, lock).
 *
 * <p>The backgrounds paint no word: the window title ({@link Kind#title}) and the names and tooltips
 * of the items are the only labels, all read from {@code lang/menus/*.yml} ({@link ArenaLang}, keys under
 * {@code werewolf.menu.}). A plaque whose content is not chosen yet holds a placeholder item, so no
 * well is left without a name. Role names, phase lines and role help still come from the role data
 * in English.
 */
public final class WerewolfMenus {
  private WerewolfMenus() {}

  /**
   * Painted background (resource-pack/layout.py), title key (lang/menus/*.yml {@code
   * werewolf.menu.title.<key>}) and colour of each menu.
   */
  public enum Kind {
    VOTE("menu_vote", '\uE900', "vote", WerewolfPresentation.VOTE),
    HUNT("menu_hunt", '\uEA00', "hunt", WEREWOLF),
    VISION("menu_vision", '\uEB00', "vision", SEER),
    POTIONS("menu_potions", '\uEC00', "potions", WITCH),
    GUARD("menu_guard", '\uED00', "guard", WerewolfPresentation.GUARD),
    LOVERS("menu_lovers", '\uEE00', "lovers", CUPID),
    SHOT("menu_shot", '\uEF00', "shot", HUNTER);

    public final String background;
    public final char tile;
    public final String key;
    public final int color;

    Kind(String background, char tile, String key, int color) {
      this.background = background;
      this.tile = tile;
      this.key = key;
      this.color = color;
    }

    /** The window title: the menu's name, which the background no longer paints. */
    public String title(Lang lang) {
      return t(lang, "title." + key);
    }
  }

  /** Title of the village overview. */
  public static String villageTitle(Lang lang) {
    return t(lang, "title.village");
  }

  private static String t(Lang lang, String key, Object... pairs) {
    return ArenaLang.tr(lang, "werewolf.menu." + key, pairs);
  }

  private static Line tl(Lang lang, int color, String key, Object... pairs) {
    return new Line(t(lang, key, pairs), color);
  }

  /** The 18 ring slots around the painted emblem, clockwise from the top left. */
  public static final int[] RING = {2, 3, 4, 5, 6, 16, 25, 34, 43, 51, 50, 49, 48, 47, 37, 28, 19, 10};

  /** Raw slots of the hotbar: card, timer, lock. */
  public static final int CARD = 81, TIMER = 85, LOCK = 89;

  /** Item row of the painted plaques (inventory row 1; rows 0 and 2 are ornament). */
  public static int box(int col) {
    return 63 + col;
  }

  public record Line(String text, int color) {}

  /**
   * One item. {@code head} is a player whose skin to show, {@code icon} an item model under
   * vampyuyu:icon/ (used instead of the skin when both are set). {@code click} is "select:<uuid>",
   * "aim:<uuid>", "use:<ability>", "leave" or null.
   */
  public record Entry(
      int raw, UUID head, String icon, int amount, boolean glint, List<Line> lines, String click) {}

  /** The menu the viewer must act in now, or null when no role menu applies. */
  public static Kind kind(WerewolfGame game, UUID viewer) {
    synchronized (game) {
      if (!game.actors().contains(viewer)) return null;
      return switch (game.phase()) {
        case "DAY_VOTE", "ELECTION" -> Kind.VOTE;
        case "NIGHT" -> Kind.HUNT;
        case "SEER" -> Kind.VISION;
        case "WITCH" -> Kind.POTIONS;
        case "GUARD" -> Kind.GUARD;
        case "CUPID" -> Kind.LOVERS;
        case "HUNTER_SHOT" -> Kind.SHOT;
        default -> null;
      };
    }
  }

  /**
   * Spreads n heads evenly on the ring (never all at the top with a hole at the bottom); past 18
   * players the rest fill the free slots left to right, top to bottom.
   */
  public static int[] slots(int n) {
    int[] out = new int[n];
    for (int i = 0; i < Math.min(n, RING.length); i++)
      out[i] = RING[(int) Math.floor((double) i * RING.length / Math.min(n, RING.length))];
    Set<Integer> ring = new HashSet<>();
    for (int r : RING) ring.add(r);
    int next = 0;
    for (int i = RING.length; i < n; i++) {
      while (ring.contains(next)) next++;
      out[i] = next++;
    }
    return out;
  }

  private static Line name(String name) {
    return new Line(name, 0xffffff);
  }

  private static final Line BLANK = new Line("", 0xffffff);

  private static List<Line> lines(Line... lines) {
    return List.of(lines);
  }

  private static String who(Map<UUID, String> names, UUID id) {
    return names.getOrDefault(id, "Player");
  }

  /** {@link #menu(WerewolfGame, UUID, Map, long, boolean, Lang)} in English, the source language. */
  public static List<Entry> menu(
      WerewolfGame game,
      UUID viewer,
      Map<UUID, String> names,
      long secondsLeft,
      boolean leaveLocked) {
    return menu(game, viewer, names, secondsLeft, leaveLocked, Lang.EN);
  }

  /** Every item of the viewer's role menu, or an empty list when no role menu applies. */
  public static List<Entry> menu(
      WerewolfGame game,
      UUID viewer,
      Map<UUID, String> names,
      long secondsLeft,
      boolean leaveLocked,
      Lang lang) {
    synchronized (game) {
      Kind kind = kind(game, viewer);
      if (kind == null) return List.of();
      List<Entry> out = new ArrayList<>();
      List<UUID> targets =
          game.alive().stream()
              .filter(p -> !p.equals(viewer))
              .sorted(Comparator.comparing((UUID p) -> who(names, p).toLowerCase(Locale.ROOT)))
              .toList();
      int[] ring = slots(targets.size());
      for (int i = 0; i < targets.size() && i < 54; i++)
        out.add(head(game, kind, viewer, targets.get(i), ring[i], names, lang));
      bottom(game, kind, viewer, names, out, lang);
      hotbar(game, viewer, names, secondsLeft, leaveLocked, out, lang);
      return out;
    }
  }

  private static Entry head(
      WerewolfGame game,
      Kind kind,
      UUID viewer,
      UUID target,
      int slot,
      Map<UUID, String> names,
      Lang lang) {
    String n = who(names, target);
    int c = kind.color;
    switch (kind) {
      case VOTE -> {
        int votes = game.votes(viewer).getOrDefault(target, 0);
        boolean mine = target.equals(game.ballot(viewer));
        return new Entry(
            slot,
            target,
            null,
            Math.max(1, votes),
            mine,
            lines(
                name(n),
                votes == 0
                    ? tl(lang, c, "vote.none")
                    : tl(lang, c, votes == 1 ? "vote.against_one" : "vote.against_many", "count", votes),
                BLANK,
                mine ? tl(lang, OK, "vote.mine") : tl(lang, OK, "vote.click", "player", n)),
            "select:" + target);
      }
      case HUNT -> {
        if (game.pack(viewer).contains(target))
          return new Entry(
              slot,
              target,
              "mark-wolf",
              1,
              false,
              lines(
                  name(n),
                  tl(lang, c, "hunt.packmate"),
                  BLANK,
                  tl(lang, NO, "hunt.never")),
              null);
        int votes = game.votes(viewer).getOrDefault(target, 0);
        boolean mine = target.equals(game.ballot(viewer));
        int pack = game.pack(viewer).size() + 1;
        return new Entry(
            slot,
            target,
            null,
            Math.max(1, votes),
            mine,
            lines(
                name(n),
                tl(lang, c, pack == 1 ? "hunt.count_one" : "hunt.count_many", "votes", votes, "pack", pack),
                BLANK,
                mine ? tl(lang, VOTE, "hunt.mine") : tl(lang, OK, "hunt.click", "player", n)),
            "select:" + target);
      }
      case VISION -> {
        String seen = game.visions(viewer).get(target);
        if (seen != null)
          return new Entry(
              slot,
              target,
              "card-" + seen.toLowerCase(Locale.ROOT),
              1,
              false,
              lines(
                  name(n),
                  tl(lang, c, "vision.seen", "role", roleName(seen)),
                  BLANK,
                  tl(lang, INK_DIM, "vision.already")),
              null);
        return new Entry(
            slot,
            target,
            null,
            1,
            false,
            lines(
                name(n),
                tl(lang, c, "vision.unknown"),
                BLANK,
                tl(lang, OK, "vision.click", "player", n)),
            "select:" + target);
      }
      case POTIONS -> {
        boolean victim = target.equals(game.victim(viewer));
        boolean picked = game.aims(viewer).contains(target);
        boolean death = game.deathPotion(viewer);
        List<Line> l = new ArrayList<>();
        l.add(name(n));
        if (victim) l.add(tl(lang, WEREWOLF, "potions.victim"));
        else l.add(tl(lang, c, picked ? "potions.picked" : "potions.asleep"));
        l.add(BLANK);
        if (victim) l.add(tl(lang, VOTE, "potions.save"));
        if (!death) l.add(tl(lang, NO, "potions.death_used"));
        else
          l.add(
              picked
                  ? tl(lang, OK, "potions.unpick", "player", n)
                  : tl(lang, OK, "potions.pick"));
        return new Entry(
            slot, target, null, 1, picked, List.copyOf(l), death ? "aim:" + target : null);
      }
      case GUARD -> {
        if (target.equals(game.lastProtected(viewer)))
          return new Entry(
              slot,
              target,
              "mark-last",
              1,
              false,
              lines(
                  name(n),
                  tl(lang, c, "guard.last"),
                  BLANK,
                  tl(lang, NO, "guard.unavailable")),
              null);
        return new Entry(
            slot,
            target,
            null,
            1,
            false,
            lines(
                name(n),
                tl(lang, c, "guard.sleeping"),
                BLANK,
                tl(lang, OK, "guard.click", "player", n)),
            "select:" + target);
      }
      case LOVERS -> {
        List<UUID> picks = game.aims(viewer);
        int at = picks.indexOf(target);
        return new Entry(
            slot,
            target,
            null,
            1,
            at >= 0,
            at >= 0
                ? lines(
                    name(n),
                    tl(lang, c, "lovers.number", "number", at + 1),
                    BLANK,
                    tl(lang, INK_DIM, "lovers.unpick", "player", n))
                : lines(
                    name(n),
                    tl(lang, c, "lovers.none"),
                    BLANK,
                    picks.size() < 2
                        ? tl(lang, OK, "lovers.pick", "player", n)
                        : tl(lang, NO, "lovers.full")),
            "aim:" + target);
      }
      default -> { // SHOT
        boolean picked = game.aims(viewer).contains(target);
        return new Entry(
            slot,
            target,
            null,
            1,
            picked,
            picked
                ? lines(name(n), tl(lang, c, "shot.target"), BLANK, tl(lang, VOTE, "shot.then"))
                : lines(
                    name(n),
                    tl(lang, c, "shot.alive"),
                    BLANK,
                    tl(lang, OK, "shot.click", "player", n)),
            "aim:" + target);
      }
    }
  }

  /** A plaque whose content is not chosen yet: its name, why it is empty, what to do. */
  private static Entry empty(int raw, String icon, Line... lines) {
    return new Entry(raw, null, icon, 1, false, List.of(lines), null);
  }

  private static void bottom(
      WerewolfGame game,
      Kind kind,
      UUID viewer,
      Map<UUID, String> names,
      List<Entry> out,
      Lang lang) {
    Map<String, String> can = game.abilities(viewer);
    switch (kind) {
      case VOTE -> {
        UUID mine = game.ballot(viewer);
        if (mine != null)
          out.add(
              new Entry(
                  box(1),
                  mine,
                  null,
                  1,
                  false,
                  lines(name(who(names, mine)), tl(lang, VOTE, "vote.box_mine")),
                  "select:" + mine));
        else
          out.add(
              empty(
                  box(1),
                  "tag",
                  tl(lang, VOTE, "vote.box_mine"),
                  tl(lang, INK_MUTED, "vote.box_mine_none"),
                  tl(lang, INK_DIM, "vote.box_mine_hint")));
        Map<UUID, Integer> votes = game.votes(viewer);
        int top = votes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> leaders =
            votes.entrySet().stream()
                .filter(e -> e.getValue() == top)
                .map(Map.Entry::getKey)
                .toList();
        if (top > 0 && leaders.size() == 1)
          out.add(
              new Entry(
                  box(4),
                  leaders.getFirst(),
                  null,
                  top,
                  false,
                  lines(
                      name(who(names, leaders.getFirst())),
                      tl(lang, VOTE, top == 1 ? "vote.leading_one" : "vote.leading_many", "count", top)),
                  null));
        else if (top > 0)
          out.add(
              new Entry(
                  box(4),
                  null,
                  "skip",
                  1,
                  false,
                  lines(tl(lang, 0xffffff, "vote.tie"), tl(lang, INK_MUTED, "vote.tie_info")),
                  null));
        else
          out.add(
              empty(
                  box(4),
                  "star",
                  tl(lang, VOTE, "vote.box_leading"),
                  tl(lang, INK_MUTED, "vote.none")));
        boolean skipped = game.abstained(viewer);
        out.add(
            new Entry(
                box(7),
                null,
                "skip",
                1,
                skipped,
                skipped
                    ? lines(tl(lang, 0xffffff, "vote.skip_on"), BLANK, tl(lang, OK, "vote.skip_back"))
                    : lines(
                        tl(lang, 0xffffff, "vote.skip"),
                        tl(lang, INK_MUTED, "vote.skip_info"),
                        BLANK,
                        tl(lang, OK, "vote.skip_click")),
                "use:skip"));
      }
      case HUNT -> {
        UUID prey = game.ballot(viewer);
        if (prey != null)
          out.add(
              new Entry(
                  box(1),
                  prey,
                  null,
                  1,
                  false,
                  lines(name(who(names, prey)), tl(lang, WEREWOLF, "hunt.box_prey")),
                  "select:" + prey));
        else
          out.add(
              empty(
                  box(1),
                  "target",
                  tl(lang, WEREWOLF, "hunt.box_prey"),
                  tl(lang, INK_MUTED, "hunt.box_prey_none"),
                  tl(lang, INK_DIM, "hunt.box_prey_hint")));
        List<UUID> pack = game.pack(viewer);
        for (int i = 0; i < pack.size() && i < 3; i++) {
          UUID wolf = pack.get(i), choice = game.ballot(wolf);
          out.add(
              new Entry(
                  box(6 + i),
                  wolf,
                  null,
                  1,
                  false,
                  lines(
                      name(who(names, wolf)),
                      tl(lang, INK_MUTED, "hunt.box_pack"),
                      choice == null
                          ? tl(lang, WEREWOLF, "hunt.waiting")
                          : tl(lang, WEREWOLF, "hunt.hunts", "player", who(names, choice))),
                  null));
        }
        if (pack.isEmpty())
          out.add(
              new Entry(
                  box(6),
                  null,
                  "mark-wolf",
                  1,
                  false,
                  lines(tl(lang, 0xffffff, "hunt.box_pack"), tl(lang, INK_MUTED, "hunt.alone")),
                  null));
      }
      case VISION -> {
        List<Map.Entry<UUID, String>> seen = new ArrayList<>(game.visions(viewer).entrySet());
        List<Map.Entry<UUID, String>> last =
            seen.subList(Math.max(0, seen.size() - 3), seen.size());
        for (int i = 0; i < last.size(); i++) {
          var v = last.get(i);
          String n = who(names, v.getKey()), role = roleName(v.getValue());
          out.add(
              new Entry(
                  box(1 + 3 * i),
                  v.getKey(),
                  null,
                  1,
                  false,
                  lines(
                      name(n), tl(lang, SEER, "vision.box_role", "role", role), tl(lang, INK_DIM, "vision.box")),
                  null));
          out.add(
              new Entry(
                  box(2 + 3 * i),
                  null,
                  "card-" + v.getValue().toLowerCase(Locale.ROOT),
                  1,
                  false,
                  lines(
                      new Line(role, WerewolfPresentation.color(v.getValue())),
                      tl(lang, INK_MUTED, "vision.box_is", "player", n, "role", role)),
                  null));
        }
        if (last.isEmpty())
          out.add(
              empty(
                  box(1),
                  "card-back",
                  tl(lang, SEER, "vision.box"),
                  tl(lang, INK_MUTED, "vision.box_none"),
                  tl(lang, INK_DIM, "vision.box_hint")));
      }
      case POTIONS -> {
        UUID victim = game.victim(viewer);
        boolean life = can.containsKey("heal");
        out.add(
            new Entry(
                box(1),
                null,
                game.lifePotion(viewer) ? "potion-life" : "potion-life-off",
                1,
                false,
                life
                    ? lines(
                        tl(lang, VOTE, "potions.life"),
                        tl(lang, INK_MUTED, "potions.life_save", "player", who(names, victim)),
                        BLANK,
                        tl(lang, OK, "potions.life_click"))
                    : lines(
                        tl(lang, VOTE, "potions.life"),
                        tl(lang, NO, game.lifePotion(viewer) ? "potions.life_nobody" : "potions.used")),
                life ? "use:heal" : null));
        boolean death = can.containsKey("poison");
        List<UUID> aim = game.aims(viewer);
        out.add(
            new Entry(
                box(4),
                null,
                game.deathPotion(viewer) ? "potion-death" : "potion-death-off",
                1,
                false,
                death
                    ? lines(
                        tl(lang, WITCH, "potions.death"),
                        tl(lang, INK_MUTED, "potions.death_poison", "player", who(names, aim.getFirst())),
                        BLANK,
                        tl(lang, OK, "potions.death_click"))
                    : lines(
                        tl(lang, WITCH, "potions.death"),
                        tl(lang, NO, game.deathPotion(viewer) ? "potions.death_first" : "potions.used")),
                death ? "use:poison" : null));
        out.add(
            new Entry(
                box(7),
                null,
                "moon",
                1,
                false,
                lines(
                    tl(lang, INK_DIM, "potions.sleep"),
                    tl(lang, INK_MUTED, "potions.sleep_info"),
                    BLANK,
                    tl(lang, OK, "potions.sleep_click")),
                "use:sleep"));
      }
      case GUARD -> {
        UUID last = game.lastProtected(viewer);
        if (last != null)
          out.add(
              new Entry(
                  box(1),
                  last,
                  last.equals(viewer) ? "shield" : null,
                  1,
                  false,
                  lines(
                      name(last.equals(viewer) ? t(lang, "guard.you") : who(names, last)),
                      tl(lang, WerewolfPresentation.GUARD, "guard.last")),
                  null));
        else
          out.add(
              empty(
                  box(1),
                  "mark-last",
                  tl(lang, WerewolfPresentation.GUARD, "guard.box_last"),
                  tl(lang, INK_MUTED, "guard.box_last_none")));
        boolean self = can.containsKey("protect-self");
        out.add(
            new Entry(
                box(6),
                null,
                "shield",
                1,
                false,
                self
                    ? lines(
                        tl(lang, WerewolfPresentation.GUARD, "guard.self"),
                        tl(lang, INK_MUTED, "guard.self_info"),
                        BLANK,
                        tl(lang, OK, "guard.self_click"))
                    : lines(
                        tl(lang, WerewolfPresentation.GUARD, "guard.self"), tl(lang, NO, "guard.self_no")),
                self ? "use:protect-self" : null));
      }
      case LOVERS -> {
        List<UUID> picks = game.aims(viewer);
        for (int i = 0; i < picks.size(); i++)
          out.add(
              new Entry(
                  box(1 + 2 * i),
                  picks.get(i),
                  null,
                  1,
                  false,
                  lines(name(who(names, picks.get(i))), tl(lang, CUPID, "lovers.number", "number", i + 1)),
                  "aim:" + picks.get(i)));
        for (int i = picks.size(); i < 2; i++)
          out.add(
              empty(
                  box(1 + 2 * i),
                  "tag",
                  tl(lang, CUPID, "lovers.number", "number", i + 1),
                  tl(lang, INK_MUTED, "lovers.box_none"),
                  tl(lang, INK_DIM, "lovers.box_hint")));
        out.add(
            new Entry(
                box(2),
                null,
                "heart",
                1,
                false,
                picks.size() == 2
                    ? lines(
                        tl(lang, CUPID, "lovers.box"),
                        tl(
                            lang,
                            INK_MUTED,
                            "lovers.bind_pair",
                            "first",
                            who(names, picks.get(0)),
                            "second",
                            who(names, picks.get(1))))
                    : lines(tl(lang, CUPID, "lovers.box"), tl(lang, INK_MUTED, "lovers.box_hint")),
                null));
        boolean bind = can.containsKey("bind");
        out.add(
            new Entry(
                box(7),
                null,
                "heart",
                1,
                bind,
                bind
                    ? lines(
                        tl(lang, CUPID, "lovers.bind"),
                        tl(
                            lang,
                            INK_MUTED,
                            "lovers.bind_pair",
                            "first",
                            who(names, picks.get(0)),
                            "second",
                            who(names, picks.get(1))),
                        BLANK,
                        tl(lang, OK, "lovers.bind_click"))
                    : lines(tl(lang, CUPID, "lovers.bind"), tl(lang, NO, "lovers.bind_first")),
                bind ? "use:bind" : null));
      }
      case SHOT -> {
        List<UUID> aim = game.aims(viewer);
        if (!aim.isEmpty())
          out.add(
              new Entry(
                  box(1),
                  aim.getFirst(),
                  null,
                  1,
                  false,
                  lines(name(who(names, aim.getFirst())), tl(lang, HUNTER, "shot.target")),
                  "aim:" + aim.getFirst()));
        else
          out.add(
              empty(
                  box(1),
                  "tag",
                  tl(lang, HUNTER, "shot.target"),
                  tl(lang, INK_MUTED, "shot.target_none"),
                  tl(lang, INK_DIM, "shot.target_hint")));
        boolean shoot = can.containsKey("shoot");
        out.add(
            new Entry(
                box(7),
                null,
                "target",
                1,
                shoot,
                shoot
                    ? lines(
                        tl(lang, HUNTER, "shot.shoot"),
                        tl(lang, INK_MUTED, "shot.shoot_info", "player", who(names, aim.getFirst())),
                        BLANK,
                        tl(lang, OK, "shot.shoot_click"))
                    : lines(tl(lang, HUNTER, "shot.shoot"), tl(lang, NO, "shot.shoot_first")),
                shoot ? "use:shoot" : null));
      }
    }
  }

  /** Painted background of the village overview (resource-pack/layout.py "menu_village"). */
  public static final char VILLAGE = '\uF000';

  /**
   * The village overview, opened whenever no role menu applies (waiting, the day debate, a sleeper,
   * the dead): living heads on the ring, the game and the village in the bottom boxes, then the
   * same card / timer / lock hotbar. Nothing here is clickable but the leave button.
   *
   * @param game null before the game starts
   */
  public static List<Entry> village(
      WerewolfGame game,
      UUID viewer,
      Map<UUID, String> names,
      String arena,
      long secondsLeft,
      boolean leaveLocked) {
    return village(game, viewer, names, arena, secondsLeft, leaveLocked, Lang.EN);
  }

  /** The village overview in the viewer's language. */
  public static List<Entry> village(
      WerewolfGame game,
      UUID viewer,
      Map<UUID, String> names,
      String arena,
      long secondsLeft,
      boolean leaveLocked,
      Lang lang) {
    List<Entry> out = new ArrayList<>();
    if (game == null) {
      List<UUID> waiting =
          names.keySet().stream()
              .sorted(Comparator.comparing((UUID p) -> who(names, p).toLowerCase(Locale.ROOT)))
              .toList();
      int[] ring = slots(waiting.size());
      for (int i = 0; i < waiting.size() && i < 54; i++)
        out.add(
            new Entry(
                ring[i],
                waiting.get(i),
                null,
                1,
                waiting.get(i).equals(viewer),
                lines(
                    name(
                        waiting.get(i).equals(viewer)
                            ? t(lang, "village.you_suffix", "player", who(names, waiting.get(i)))
                            : who(names, waiting.get(i))),
                    tl(lang, VILLAGER, "village.ready"),
                    BLANK,
                    tl(lang, INK_DIM, "village.ready_info")),
                null));
      out.add(
          new Entry(
              box(1),
              null,
              "moon",
              1,
              false,
              withRules(
                  lang,
                  arena,
                  tl(lang, VILLAGER, "village.waiting"),
                  tl(lang, INK_MUTED, "village.waiting_info")),
              null));
      out.add(
          new Entry(
              box(6),
              null,
              "heart",
              Math.max(1, names.size()),
              false,
              lines(tl(lang, 0xffffff, "village.count", "count", names.size())),
              null));
      out.add(
          new Entry(
              CARD,
              null,
              "card-back",
              1,
              false,
              lines(tl(lang, INK, "village.card"), tl(lang, INK_MUTED, "village.card_info")),
              null));
      timerAndLock(secondsLeft, leaveLocked, out, lang);
      out.removeIf(e -> e.raw() == TIMER); // No turn runs yet.
      return out;
    }
    synchronized (game) {
      List<UUID> pack = game.pack(viewer);
      Map<UUID, String> seen = game.visions(viewer);
      UUID partner = game.partner(viewer);
      List<UUID> living =
          game.alive().stream()
              .sorted(Comparator.comparing((UUID p) -> who(names, p).toLowerCase(Locale.ROOT)))
              .toList();
      int[] ring = slots(living.size());
      for (int i = 0; i < living.size() && i < 54; i++) {
        UUID p = living.get(i);
        Line state =
            p.equals(viewer)
                ? tl(
                    lang,
                    WerewolfPresentation.color(game.selfRoleName(p)),
                    "village.you",
                    "role",
                    roleName(game.selfRoleName(p)))
                : pack.contains(p)
                    ? tl(lang, WEREWOLF, "hunt.packmate")
                    : p.equals(partner)
                        ? tl(lang, CUPID, "village.lover")
                        : seen.containsKey(p)
                            ? tl(lang, SEER, "vision.seen", "role", roleName(seen.get(p)))
                            : tl(lang, VILLAGER, "shot.alive");
        out.add(
            new Entry(
                ring[i], p, null, 1, p.equals(viewer), lines(name(who(names, p)), state), null));
      }
      String phase = game.phase();
      boolean night = game.night();
      var view = WerewolfPresentation.view(game, viewer);
      out.add(
          new Entry(
              box(1),
              null,
              night ? "moon" : "card-villager",
              1,
              false,
              withRules(
                  lang,
                  arena,
                  tl(lang, night ? SEER : VILLAGER, night ? "village.night" : "village.day", "round", game.round()),
                  new Line(view.phase(), INK_MUTED),
                  new Line(view.instruction(), INK)),
              null));
      int alive = game.alive().size(), all = names.size();
      out.add(
          new Entry(
              box(6),
              null,
              "heart",
              Math.max(1, alive),
              false,
              lines(
                  tl(lang, 0xffffff, "village.alive", "alive", alive, "all", all),
                  all - alive == 0
                      ? tl(lang, INK_MUTED, "village.no_dead")
                      : tl(lang, INK_MUTED, "village.dead", "count", all - alive)),
              null));
      List<Line> roles = new ArrayList<>();
      roles.add(tl(lang, 0xffffff, "village.roles"));
      int count = 0;
      for (var e : game.composition().entrySet()) {
        count += e.getValue();
        roles.add(
            new Line(
                e.getValue() + " " + plural(e.getKey(), e.getValue()),
                WerewolfPresentation.color(e.getKey())));
      }
      out.add(
          new Entry(
              box(7), null, "card-back", Math.max(1, count), false, List.copyOf(roles), null));
      if (partner != null)
        out.add(
            new Entry(
                box(8),
                partner,
                null,
                1,
                false,
                lines(name(who(names, partner)), tl(lang, CUPID, "village.lover")),
                null));
      out.add(card(game, viewer, names, lang));
      timerAndLock(phase.equals("FINISHED") ? 0 : secondsLeft, leaveLocked, out, lang);
      return out;
    }
  }

  /** The game's lines, then the arena's rules: the overview's only reading matter. */
  private static List<Line> withRules(Lang lang, String arena, Line... head) {
    List<Line> out = new ArrayList<>(List.of(head));
    out.add(BLANK);
    out.add(new Line(arena, 0xffffff));
    out.add(tl(lang, INK_MUTED, "village.rule_village"));
    out.add(tl(lang, INK_MUTED, "village.rule_wolves"));
    out.add(tl(lang, INK_MUTED, "village.rule_tie"));
    out.add(tl(lang, INK_DIM, "village.rule_items"));
    return List.copyOf(out);
  }

  public static String article(String role) {
    return role.equals("WEREWOLF") || role.equals("VILLAGER") ? "a " : "the ";
  }

  /** Card, timer (stack size = seconds left) and lock: the same hotbar in every menu. */
  private static void hotbar(
      WerewolfGame game,
      UUID viewer,
      Map<UUID, String> names,
      long secondsLeft,
      boolean leaveLocked,
      List<Entry> out,
      Lang lang) {
    out.add(card(game, viewer, names, lang));
    timerAndLock(secondsLeft, leaveLocked, out, lang);
  }

  private static void timerAndLock(
      long secondsLeft, boolean leaveLocked, List<Entry> out, Lang lang) {
    out.add(
        new Entry(
            TIMER,
            null,
            "timer",
            (int) Math.max(1, Math.min(99, secondsLeft)),
            false,
            lines(
                tl(lang, VOTE, "hotbar.left", "clock", clock(secondsLeft)),
                tl(lang, INK_MUTED, "hotbar.timer_info")),
            null));
    out.add(
        leaveLocked
            ? new Entry(
                LOCK,
                null,
                "lock",
                1,
                false,
                lines(tl(lang, 0xffffff, "hotbar.locked"), tl(lang, INK_MUTED, "hotbar.locked_info")),
                null)
            : new Entry(
                LOCK,
                null,
                "lock",
                1,
                false,
                lines(
                    tl(lang, 0xffffff, "hotbar.leave"),
                    tl(lang, INK_MUTED, "hotbar.leave_info"),
                    BLANK,
                    tl(lang, OK, "hotbar.leave_click")),
                "leave"));
  }

  /** The viewer's role card: its name in the role colour, the team, then how to play it. */
  public static Entry card(WerewolfGame game, UUID viewer, Map<UUID, String> names) {
    return card(game, viewer, names, Lang.EN);
  }

  /** The card in the viewer's language (the role's name, team and help are role data, in English). */
  public static Entry card(WerewolfGame game, UUID viewer, Map<UUID, String> names, Lang lang) {
    String role = game.selfRoleName(viewer);
    String help = game.help(viewer);
    for (var entry : names.entrySet())
      help = help.replace(entry.getKey().toString(), entry.getValue());
    List<Line> l = new ArrayList<>();
    l.add(new Line(roleName(role), WerewolfPresentation.color(role)));
    l.add(game.isMinion(viewer) ? tl(lang, INK, "hotbar.wolf_side") : new Line(team(role), INK));
    l.add(BLANK);
    for (String part : wrap(help, 40)) l.add(new Line(part, INK_MUTED));
    return new Entry(
        CARD, null, "card-" + role.toLowerCase(Locale.ROOT), 1, false, List.copyOf(l), null);
  }

  /**
   * Splits text on spaces into lines of at most {@code width} characters (tooltips do not wrap).
   */
  public static List<String> wrap(String text, int width) {
    List<String> out = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() > 0 && line.length() + 1 + word.length() > width) {
        out.add(line.toString());
        line.setLength(0);
      }
      if (line.length() > 0) line.append(' ');
      line.append(word);
    }
    if (line.length() > 0) out.add(line.toString());
    return out;
  }
}
