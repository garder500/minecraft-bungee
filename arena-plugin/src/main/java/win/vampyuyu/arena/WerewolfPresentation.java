package win.vampyuyu.arena;

import java.util.*;

/** Viewer-specific text policy, shared by announcements, HUD and compass. No Bukkit access. */
final class WerewolfPresentation {
  record View(String phase, String instruction, boolean actor, boolean timerVisible) {}

  record Cue(String title, String subtitle) {}

  /** Palette of the art direction (resource-pack design guide, tokens role-* / ink-* / state-*). */
  static final int WEREWOLF = 0xff5a5a,
      VILLAGER = 0xe9d99b,
      SEER = 0xb892ff,
      WITCH = 0x6fdc8c,
      HUNTER = 0xf0a050,
      CUPID = 0xff8fc0,
      GUARD = 0x6cc6ff,
      VOTE = 0xffd36e,
      INK = 0xf6ead0,
      INK_MUTED = 0xcdbfe3,
      INK_DIM = 0x8f84b3,
      OK = 0x9cf09c,
      NO = 0xff6b6b;

  /** One colour per role, used by its menu, card, bossbar and chat lines. */
  static int color(String role) {
    return RoleInfo.of(role).color();
  }

  /** Singular display name: "Werewolf", "Seer", "Guard"... */
  static String roleName(String role) {
    return RoleInfo.of(role).name();
  }

  /** Roles whose whole side wakes together, so their call is plural: "The Werewolves wake up". */
  private static boolean plural(String role) {
    return role.equals("WEREWOLF") || role.equals("VAMPIRE");
  }

  static String plural(String role, int count) {
    if (count == 1) return roleName(role);
    return role.equals("WEREWOLF") ? "Werewolves" : roleName(role) + "s";
  }

  static String team(String role) {
    RoleInfo info = RoleInfo.of(role);
    return switch (info.team()) {
      case "wolves" -> WerewolfGame.isWolf(role) ? "Wolf pack" : "Wolf side";
      case "vampires" -> "Vampires";
      case "cult" -> "Cult";
      case "solo" -> "On your own";
      default -> "Village team";
    };
  }

  /** Bossbar countdown: "12 s" under a minute, "1 min 20" above. */
  static String clock(long seconds) {
    seconds = Math.max(0, seconds);
    return seconds < 60
        ? seconds + " s"
        : seconds / 60 + " min" + (seconds % 60 == 0 ? "" : String.format(" %02d", seconds % 60));
  }

  record Boss(String text, int color, boolean timer) {}

  /**
   * Bossbar for this viewer: phase · who acts · time left. Sleepers learn nothing about the turn.
   */
  static Boss boss(WerewolfGame game, UUID viewer, long seconds) {
    synchronized (game) {
      String phase = game.phase();
      int round = game.round();
      if (phase.equals("FINISHED"))
        return new Boss("Game over · " + result(game.winner()), VOTE, false);
      boolean actor = game.actors().contains(viewer);
      if (game.exiled(viewer) && !game.night())
        return new Boss("☀ Day " + round + " · Away from the village · " + clock(seconds), INK_DIM, true);
      if (game.night()) {
        String head = "☾ Night " + round + " · ";
        // With a hidden reveal the clock of a call tells whether anybody living answers it: hide it.
        boolean hidden = !game.reveal().equals("FULL");
        if (!actor)
          return hidden
              ? new Boss(head + "The village sleeps", INK_DIM, false)
              : new Boss(head + "The village sleeps · " + clock(seconds), INK_DIM, true);
        String role = turnRole(phase);
        String turn =
            switch (phase) {
              case "NIGHT" -> "The Werewolves hunt";
              case "HUNTER_SHOT" -> "The Hunter takes a last shot";
              case "AMULET" -> "The amulet's holder is awake";
              default -> "The " + roleName(role) + " is awake";
            };
        return new Boss(head + turn + " · " + clock(seconds), color(role), true);
      }
      String head = "☀ Day " + round + " · ";
      return switch (phase) {
        case "DISCUSSION" -> new Boss(head + "Debate · " + clock(seconds), VILLAGER, true);
        case "DAY_VOTE" -> new Boss(head + "Village vote · " + clock(seconds), VOTE, true);
        case "ELECTION" -> new Boss(head + "Electing the mayor · " + clock(seconds), VOTE, true);
        case "DEFENSE" -> new Boss(head + "The accused speaks · " + clock(seconds), VOTE, true);
        case "TRIAL" -> new Boss(head + "Thumbs up or down · " + clock(seconds), VOTE, true);
        case "MARTYR" ->
            // Only the martyr is told what the pause is: it would give his role away.
            actor
                ? new Boss(head + "The Martyr may step in · " + clock(seconds), color("MARTYR"), true)
                : new Boss(head + "The village weighs the verdict · " + clock(seconds), VOTE, true);
        default ->
            !actor && !game.reveal().equals("FULL")
                ? new Boss(head + "A last shot is fired · " + clock(seconds), VOTE, true)
                : new Boss(head + "The Hunter takes a last shot · " + clock(seconds), HUNTER, true);
      };
    }
  }

  record Line(String text, int color) {}

  /**
   * Line under the crosshair for the whole turn: whose turn it is and the time left. The narrator
   * announces every turn in the chat, so sleepers may know it too. Null once the game is over.
   */
  static Line center(WerewolfGame game, UUID viewer, long seconds) {
    synchronized (game) {
      String phase = game.phase();
      if (phase.equals("FINISHED")) return null;
      boolean sleeper = game.night() && !game.actors().contains(viewer);
      String time = sleeper && !game.reveal().equals("FULL") ? "" : " · " + clock(seconds);
      String role = turnRole(phase);
      if (role == null && game.night()) // a call without a role of its own (the amulet's)
        return sleeper
            ? new Line("☾ The village sleeps" + time, INK_DIM)
            : new Line("☾ Your turn" + time, VOTE);
      if (role == null)
        return switch (phase) {
          case "DAY_VOTE" -> new Line("☀ Village vote" + time, VOTE);
          case "ELECTION" -> new Line("☀ Electing the mayor" + time, VOTE);
          case "DEFENSE" -> new Line("☀ The accused speaks" + time, VOTE);
          case "TRIAL" -> new Line("☀ Thumbs up or down" + time, VOTE);
          default -> new Line("☀ Debate" + time, VILLAGER);
        };
      String moon = game.night() ? "☾ " : "☀ ";
      if (phase.equals("MARTYR") && !game.actors().contains(viewer))
        return new Line("☀ The village weighs the verdict" + time, VOTE);
      if (game.actors().contains(viewer)) return new Line(moon + "Your turn" + time, color(role));
      String turn =
          switch (phase) {
            case "NIGHT" -> "The Werewolves' turn";
            case "HUNTER_SHOT" ->
                game.reveal().equals("FULL") ? "The Hunter's last shot" : "A last shot";
            default -> "The " + roleName(role) + "'s turn";
          };
      return new Line(moon + turn + time, color(role));
    }
  }

  /**
   * Pack sound for a successful menu click, or null for the plain click. Hunter shots are heard by
   * everyone when the death is announced, so the click itself stays quiet.
   */
  static String actionSound(String role, String click) {
    return switch (role) {
      case "WITCH" ->
          click.equals("use:heal")
              ? "potion_life"
              : click.equals("use:poison") || click.startsWith("select:") ? "potion_death" : null;
      case "SEER" -> click.startsWith("select:") ? "vision" : null;
      case "CUPID" -> click.equals("use:bind") ? "bind" : null;
      case "GUARD" ->
          click.startsWith("select:") || click.equals("use:protect-self") ? "protect" : null;
      default -> null;
    };
  }

  /** True when this player must act now: their menu opens by itself and cannot be closed. */
  static boolean menuRequired(WerewolfGame game, UUID id) {
    synchronized (game) {
      String phase = game.phase();
      return !phase.equals("DISCUSSION") && !phase.equals("FINISHED") && game.actors().contains(id);
    }
  }

  /** Role that acts during a night phase, or null outside the night. */
  static String turnRole(String phase) {
    return switch (phase) {
      case "CUPID" -> "CUPID";
      case "GUARD" -> "GUARD";
      case "SEER" -> "SEER";
      case "NIGHT", "MINION" -> "WEREWOLF"; // the minion's pick is the wolves' call
      case "WITCH" -> "WITCH";
      case "HUNTER_SHOT" -> "HUNTER";
      default -> {
        RoleInfo info = RoleInfo.byPhase(phase);
        yield info == null ? null : info.id();
      }
    };
  }

  static String roleLabel(String role) {
    return role.equals("WEREWOLF")
        ? "Werewolves"
        : role.equals("VAMPIRE") ? "Vampires" : roleName(role);
  }

  static String deathLine(String name, String role) {
    return deathLine(name, role, "FULL");
  }

  /** What a death tells the table, by the arena's reveal setting: FULL role, TEAM, WOLF or NONE. */
  static String deathLine(String name, String role, String reveal) {
    String what = revealed(role, reveal);
    return name + " has died" + (what.isEmpty() ? "" : " — they were " + what);
  }

  /** The words for what a dead player was ("the Seer", "on the village's side", ""). */
  static String revealed(String role, String reveal) {
    return switch (reveal == null ? "FULL" : reveal) {
      case "TEAM" ->
          switch (RoleInfo.of(role).team()) {
            case "wolves" -> "on the wolves' side";
            case "vampires" -> "on the vampires' side";
            case "cult" -> "on the cult's side";
            case "solo" -> "on nobody's side";
            default -> "on the village's side";
          };
      case "WOLF" -> WerewolfGame.isWolf(role) ? "a werewolf" : "not a werewolf";
      case "NONE" -> "";
      default ->
          (role.equals("WEREWOLF")
                  ? "a Werewolf"
                  : role.equals("VAMPIRE") ? "a Vampire" : "the " + roleLabel(role))
              .replace("the Villager", "a Villager");
    };
  }

  static String wakeLine(String phase) {
    if (phase.equals("MARTYR")) return null; // a day pause: it would give the martyr away
    String role = turnRole(phase);
    return role == null
        ? null
        : "The " + roleLabel(role) + " wake" + (plural(role) ? "" : "s") + " up";
  }

  static String sleepLine(String phase) {
    if (phase.equals("MARTYR")) return null;
    String role = turnRole(phase);
    return role == null
        ? null
        : "The "
            + roleLabel(role)
            + " go"
            + (plural(role) ? "" : "es")
            + " back to sleep";
  }

  /** Font of the screen-effect glyph (resource-pack core shader rendertype_text). */
  static final String FX_GLYPH = "\uF100";

  /** Its negative-advance twin (resource-pack font fx.json): the glyph takes no room in the bar. */
  static final String FX_BACK = "\uF101";

  /**
   * Screen effect for the core shader: 1 night, 2 dawn, 3 fading dawn, 0 none. The shader reads
   * the id from the blue channel of the glyph colour (78, 92, id).
   */
  static int fx(boolean night, long sinceDawnMillis) {
    if (night) return 1;
    if (sinceDawnMillis < 0 || sinceDawnMillis >= 3000) return 0;
    return sinceDawnMillis < 1500 ? 2 : 3;
  }

  static int fxColor(int id) {
    return 78 << 16 | 92 << 8 | id;
  }

  static boolean dusk(boolean wasNight, boolean night) {
    return !wasNight && night;
  }

  static boolean dawn(boolean wasNight, boolean night, String phase) {
    return wasNight && !night && !phase.equals("FINISHED");
  }

  static View view(WerewolfGame game, UUID viewer) {
    synchronized (game) {
      String phase = game.phase();
      boolean actor = game.actors().contains(viewer);
      if (game.night() && !actor)
        return new View("The village sleeps", "Zzz… You sleep until dawn", false, false);
      if (game.exiled(viewer) && !game.night() && !phase.equals("FINISHED"))
        return new View("Away", "You are away from the village today. You come back at nightfall.", false, true);
      String label =
          switch (phase) {
            case "DISCUSSION" -> "Day · Discussion";
            case "ELECTION" -> "Day · Election";
            case "DAY_VOTE" -> "Day · Voting";
            case "DEFENSE" -> "Day · Defense";
            case "MARTYR" -> actor ? "Martyr's choice" : "Day · Verdict";
            case "AMULET" -> "Amulet";
            case "TRIAL" -> "Day · Trial";
            case "CUPID" -> "Cupid's choice";
            case "GUARD" -> "Guard's turn";
            case "SEER" -> "Seer's turn";
            case "NIGHT" -> "Wolf hunt";
            case "WITCH" -> "Potions";
            case "HUNTER_SHOT" -> "Hunter's final shot";
            case "FINISHED" -> "Game over";
            default -> {
              RoleInfo info = RoleInfo.byPhase(phase);
              yield info == null ? "Game over" : info.call().label() + " call";
            }
          };
      String instruction =
          actor
              ? switch (phase) {
                case "DISCUSSION" -> "Talk it over: who are the wolves?";
                case "DAY_VOTE" -> "Right-click a player to vote, or use Skip";
                case "ELECTION" -> "Right-click the player you want as mayor: their vote will count twice";
                case "DEFENSE" -> "The accused speaks: the thumbs come next";
                case "TRIAL" -> "Thumbs up to let them live, thumbs down to eliminate them";
                case "AMULET" -> "Right-click a player to pass the amulet on, or go back to sleep";
                case "CUPID" -> "Right-click two players, then use Bind";
                case "GUARD" -> "Right-click a player to protect them tonight";
                case "SEER" -> "Right-click a player to see their role";
                case "NIGHT" -> "Right-click a player to choose your prey";
                case "WITCH" -> "Life saves, Death poisons the player you picked, Sleep ends your turn";
                case "HUNTER_SHOT" -> "Right-click a target, then use Shoot";
                case "FINISHED" -> "The game has ended";
                default -> {
                  RoleInfo info = RoleInfo.byPhase(phase);
                  yield info == null ? "The game has ended" : info.call().instruction();
                }
              }
              : phase.equals("FINISHED") ? "The game has ended" : "Watch the village";
      return new View(label, instruction, actor, !phase.equals("FINISHED"));
    }
  }

  static Cue awakening(String phase) {
    return switch (phase) {
      case "CUPID" -> new Cue("Cupid, bind two hearts", "Right-click two players");
      case "GUARD" -> new Cue("Guard, awaken", "Choose someone to protect");
      case "SEER" -> new Cue("Seer, awaken", "Discover one player's secret");
      case "NIGHT" -> new Cue("Wolves, awaken", "Right-click your victim");
      case "WITCH" -> new Cue("Witch, awaken", "Your potions may change their fate");
      case "HUNTER_SHOT" -> new Cue("Hunter, take your final shot", "Choose your last target");
      case "AMULET" -> new Cue("Amulet holder, awaken", "Pass the amulet on, or keep it");
      default -> {
        RoleInfo info = RoleInfo.byPhase(phase);
        yield info == null ? null : new Cue(info.call().title(), info.call().subtitle());
      }
    };
  }

  static String result(String winner) {
    return switch (winner) {
      case "VILLAGE" -> "The village wins";
      case "WEREWOLVES" -> "The wolves win";
      case "LOVERS" -> "Love conquers all";
      case "VAMPIRES" -> "The vampires win";
      case "CULT" -> "The cult wins";
      case "LONE_WOLF" -> "The lone wolf wins alone";
      case "DRAW" -> "The story ends in a draw";
      default -> "Time is up";
    };
  }

  static boolean current(String event, long epoch, String expectedEvent, long expectedEpoch) {
    return Objects.equals(event, expectedEvent) && epoch == expectedEpoch;
  }
}
