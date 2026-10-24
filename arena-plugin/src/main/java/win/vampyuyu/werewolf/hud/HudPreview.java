package win.vampyuyu.werewolf.hud;

import java.util.*;
import win.vampyuyu.werewolf.ambience.WerewolfPresentation;
import win.vampyuyu.werewolf.hud.CustomHud.State;
import win.vampyuyu.werewolf.roles.RoleInfo;

/**
 * Synthetic HUD states for the /ww debug hud previews: every phase and night call, each role's
 * panel, the danger flash, the badges, the away and dead looks, the ballots against and the seat
 * strips. Built the way {@link CustomHud#of} derives a state from a game (resource-pack/art/hud_sheet.py
 * is the Python twin, and most ids are the same as its states.json), but from plain facts, so no game
 * is needed. Pure: no Bukkit access.
 */
public final class HudPreview {
  /** One named state. phase and role are set when the entry stands for one (used by the lookup). */
  public record Entry(
      String id, String group, String title, String phase, String role, State state, boolean live) {
    /** The state with its clock {@code elapsedSeconds} later, for entries that count down. */
    public State at(long elapsedSeconds) {
      if (!live || elapsedSeconds <= 0) return state;
      long left = Math.max(0, state.seconds() - elapsedSeconds);
      return new State(
          state.palette(), state.topIcon(), state.round(), state.label(), left, state.fraction(),
          state.timed(), state.showRole(), state.roleIcon(), state.roleWord(), state.teamWord(),
          state.rolePalette(), state.alive(), state.total(), state.seats(), state.badges(), state.against());
    }
  }

  /** Everything a state is derived from (the twin of hud_sheet.py's Facts). */
  private static final class Facts {
    String phase = "DISCUSSION", role = "VILLAGER", palette;
    boolean night, actor, dead, exiled, minion, revealFull = true, tallyVisible = true, faceUp = true, live;
    long seconds = 60;
    int round = 2, n = 12, me = 3, ballot = -1, lover = -1, votesAgainst = 0;
    Set<Integer> deadSeats;
    Set<Integer> mates = Set.of();
    List<String> badges = List.of();

    Facts phase(String v) { phase = v; return this; }
    Facts role(String v) { role = v; return this; }
    Facts night() { night = true; return this; }
    Facts actor() { actor = true; return this; }
    Facts seconds(long v) { seconds = v; return this; }
    Facts round(int v) { round = v; return this; }
    Facts n(int v) { n = v; return this; }
    Facts me(int v) { me = v; return this; }
    Facts dead(Integer... v) { deadSeats = new HashSet<>(Arrays.asList(v)); return this; }
    Facts ballot(int v) { ballot = v; return this; }
    Facts mates(Integer... v) { mates = new HashSet<>(Arrays.asList(v)); return this; }
    Facts lover(int v) { lover = v; return this; }
    Facts badges(String... v) { badges = List.of(v); return this; }
    Facts against(int v) { votesAgainst = v; return this; }
    Facts viewerDead() { dead = true; return this; }
    Facts exiled() { exiled = true; return this; }
    Facts minion() { minion = true; return this; }
    Facts hiddenReveal() { revealFull = false; return this; }
    Facts hiddenTally() { tallyVisible = false; return this; }
    Facts live() { live = true; return this; }
    Facts palette(String v) { palette = v; return this; }

    State build() {
      // WerewolfPresentation.boss(): is the plate dimmed (asleep or away) and is a clock shown
      boolean dim, timed;
      if (phase.equals("FINISHED")) {
        dim = false;
        timed = false;
      } else if (exiled && !night) {
        dim = true;
        timed = true;
      } else if (night) {
        dim = !actor;
        timed = actor || revealFull;
      } else {
        dim = false;
        timed = true;
      }
      String turn = WerewolfPresentation.turnRole(phase);
      boolean neutralPause = phase.equals("MARTYR") && !actor;
      String plate =
          dead ? "dead"
              : dim ? "sleep"
              : neutralPause ? "vote"
              : turn != null ? RoleInfo.of(turn).palette()
              : phase.equals("DAY_VOTE") || phase.equals("TRIAL") || phase.equals("ELECTION") ? "vote" : "villager";
      if (palette != null) plate = palette;
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
                yield info == null || info.call() == null ? "waiting" : info.call().label();
              }
            };
      if (phase.equals("MARTYR")) label = actor ? RoleInfo.of("MARTYR").call().label() : "vote";
      if (exiled && !night && !phase.equals("FINISHED")) label = "away";
      RoleInfo info = RoleInfo.of(role);
      Set<Integer> deadAt = deadSeats != null ? deadSeats : n > 7 ? Set.of(1, 7) : Set.of();
      if (dead) {
        deadAt = new HashSet<>(deadAt);
        deadAt.add(me);
      }
      List<String> seats = new ArrayList<>(n);
      for (int i = 0; i < n; i++)
        seats.add(
            deadAt.contains(i) ? "dead"
                : i == me ? "me"
                : i == ballot ? "ballot"
                : mates.contains(i) || i == lover ? "mate"
                : "alive");
      int alive = n - deadAt.size();
      double fraction = timed ? Math.max(0, Math.min(1, seconds / 75.0)) : 1;
      return new State(
          plate,
          phase.equals("DAY_VOTE") ? "vote" : night ? "moon" : "sun",
          round,
          label,
          seconds,
          fraction,
          timed,
          faceUp,
          dead ? "dead" : info.icon(),
          dead ? "fallen" : info.word(),
          dead ? "out" : minion ? "wolf side" : info.teamWord(),
          dead ? "dead" : info.palette(),
          alive,
          n,
          List.copyOf(seats),
          dead ? List.of() : List.copyOf(badges),
          phase.equals("DAY_VOTE") && !dead && tallyVisible ? votesAgainst : -1);
    }
  }

  /** The id of the state shown by default for size previews: a vote with ballots against, a mayor. */
  public static final String SIZE_STATE = "day-day_vote-2";

  /** Ambience keys of AmbienceProfile besides the game phases. */
  public static final List<String> AMBIENCE_KEYS = List.of("lobby", "day", "night", "vote", "trial", "finale");

  private static final List<Entry> ALL = build();
  private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();

  static {
    for (Entry e : ALL) BY_ID.put(e.id(), e);
  }

  private HudPreview() {}

  /** Every named state, in display order. */
  public static List<Entry> all() {
    return ALL;
  }

  public static List<String> ids() {
    return List.copyOf(BY_ID.keySet());
  }

  /** Every role id (lower case) that has a panel preview. */
  public static List<String> roles() {
    return ALL.stream().filter(e -> e.id().startsWith("role-")).map(e -> e.role().toLowerCase(Locale.ROOT)).toList();
  }

  /** Every phase name (lower case) with a call or day preview. */
  public static List<String> phases() {
    return ALL.stream().map(Entry::phase).filter(Objects::nonNull).map(x -> x.toLowerCase(Locale.ROOT)).distinct().toList();
  }

  /** Whether a phase is played at night (WerewolfGame.night(), the hunter's shot counted as day). */
  public static boolean isNight(String phase) {
    return switch (phase.toUpperCase(Locale.ROOT)) {
      case "DISCUSSION", "DAY_VOTE", "FINISHED", "MARTYR", "DEFENSE", "TRIAL", "ELECTION", "HUNTER_SHOT", "DAY", "VOTE",
          "FINALE", "LOBBY" -> false;
      default -> true;
    };
  }

  /** Ids containing the filter (case-insensitive); everything for an empty filter. */
  public static List<String> list(String filter) {
    String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
    return BY_ID.keySet().stream().filter(id -> id.contains(f)).toList();
  }

  /**
   * One preview by exact id, else by role ("seer" is the seer's panel), else by phase (the first
   * state of that phase), else a table of N players ("seats-18" for 2 to 45).
   */
  public static Optional<Entry> find(String token) {
    if (token == null || token.isBlank()) return Optional.empty();
    String t = token.toLowerCase(Locale.ROOT).replace('-', '_');
    if (t.equals("vote")) t = "day_vote";
    if (t.equals("day") || t.equals("debate")) t = "discussion";
    String dashed = token.toLowerCase(Locale.ROOT);
    Entry exact = BY_ID.get(dashed);
    if (exact != null) return Optional.of(exact);
    Entry role = BY_ID.get("role-" + t);
    if (role != null) return Optional.of(role);
    for (Entry e : ALL) if (e.phase() != null && e.phase().equalsIgnoreCase(t)) return Optional.of(e);
    Entry night = BY_ID.get("night-" + t);
    if (night != null) return Optional.of(night);
    if (dashed.matches("seats-\\d{1,2}")) {
      int n = Integer.parseInt(dashed.substring(6));
      if (n >= 2 && n <= 45) return Optional.of(table(n));
    }
    return Optional.empty();
  }

  /** The seat strip of a table of n players (the panel draws the first CustomHud.SEATS). */
  public static Entry table(int n) {
    Facts f =
        new Facts().phase("DAY_VOTE").role("WEREWOLF").n(n).me(Math.min(2, n - 1)).ballot(Math.min(n - 1, 4)).against(1);
    f.deadSeats = new HashSet<>();
    for (int i : new int[] {1, 5, 9}) if (i < n && i != f.me) f.deadSeats.add(i);
    f.mates = n > 6 ? Set.of(0, 6) : Set.of(0);
    if (n > 4) f.lover = 3;
    return new Entry("seats-" + n, "Seat strips", "Table of " + n, null, null, f.build(), false);
  }

  // ------------------------------------------------------------------ the catalogue

  private static List<Entry> build() {
    List<Entry> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    class Add {
      void add(String group, String id, String title, Facts f) {
        if (!seen.add(id)) return;
        out.add(new Entry(id, group, title, null, null, f.build(), f.live));
      }

      void add(String group, String id, String title, Facts f, boolean withPhase, boolean withRole) {
        if (!seen.add(id)) return;
        out.add(new Entry(id, group, title, withPhase ? f.phase : null, withRole ? f.role : null, f.build(), f.live));
      }
    }
    Add a = new Add();
    String calls = "Night calls", days = "Day phases";

    // night calls: the viewer is the actor of each call
    String[][] fixed = {
      {"CUPID", "CUPID"}, {"GUARD", "GUARD"}, {"SEER", "SEER"}, {"NIGHT", "WEREWOLF"}, {"WITCH", "WITCH"},
      {"MINION", "WEREWOLF"}, {"AMULET", "VILLAGER"}
    };
    for (String[] c : fixed) {
      Facts f = new Facts().phase(c[0]).role(c[1]).night().actor().seconds(24);
      if (c[1].equals("WITCH")) f.badges("life", "death");
      if (c[1].equals("GUARD")) f.badges("guard");
      a.add(calls, "night-" + c[0].toLowerCase(Locale.ROOT), "Night call: " + c[0], f, true, false);
    }
    for (RoleInfo r : RoleInfo.all()) {
      if (r.call() == null || r.call().phase() == null) continue;
      String phase = r.call().phase();
      if (phase.equals("MARTYR") || phase.equals("MINION")) continue;
      Facts f = new Facts().phase(phase).role(r.id()).night().actor().seconds(30);
      a.add(calls, "night-" + phase.toLowerCase(Locale.ROOT), "Night call: " + phase + " (" + r.name() + ")", f, true, false);
    }
    a.add(calls, "night-sleeping", "Asleep, full reveal", new Facts().phase("SEER").night().seconds(41));
    a.add(calls, "night-sleeping-hidden", "Asleep, hidden reveal (no clock)", new Facts().phase("SEER").night().hiddenReveal());
    a.add(calls, "night-wolf-asleep", "Werewolf, not their turn", new Facts().phase("SEER").role("WEREWOLF").night().mates(5, 9));
    a.add(calls, "night-minion-viewer", "Minion asleep", new Facts().phase("SEER").role("MINION").night().minion().mates(5, 9));

    // day phases
    Object[][] day = {
      {"DISCUSSION", "Debate", "VILLAGER", 0, true},
      {"DAY_VOTE", "Vote (no ballots against)", "VILLAGER", 0, false},
      {"DAY_VOTE", "Vote (3 ballots against)", "SEER", 3, false},
      {"DAY_VOTE", "Vote (12 ballots against)", "HUNTER", 12, false},
      {"ELECTION", "Mayor election", "VILLAGER", 0, true},
      {"DEFENSE", "Nominee's defense", "VILLAGER", 0, true},
      {"TRIAL", "Trial: thumbs up or down", "GUARD", 0, true},
      {"FINISHED", "Game over", "VILLAGER", 0, true}
    };
    for (int i = 0; i < day.length; i++) {
      String phase = (String) day[i][0];
      Facts f = new Facts().phase(phase).role((String) day[i][2]).seconds(phase.equals("FINISHED") ? 0 : 75).against((int) day[i][3]);
      if (i == 2) f.badges("mayor");
      a.add(days, "day-" + phase.toLowerCase(Locale.ROOT) + "-" + i, (String) day[i][1], f, i != 2 && i != 3, false);
    }
    a.add(days, "day-vote-hidden", "Vote, tally hidden (secret ballots)", new Facts().phase("DAY_VOTE").hiddenTally().ballot(5));
    a.add(days, "day-vote-ballot", "Vote with a ballot cast", new Facts().phase("DAY_VOTE").role("WITCH").ballot(5).against(2).badges("life"));
    a.add(days, "day-martyr-actor", "Martyr pause (you are the martyr)", new Facts().phase("MARTYR").role("MARTYR").actor().seconds(14), true, false);
    a.add(days, "day-martyr-other", "Martyr pause (everybody else)", new Facts().phase("MARTYR").seconds(14));
    a.add(days, "day-hunter-shot", "Hunter's last shot (by day, you)", new Facts().phase("HUNTER_SHOT").role("HUNTER").actor().seconds(12).dead(1, 7, 3), true, false);
    a.add(days, "day-hunter-others", "Hunter's last shot (by day, others)", new Facts().phase("HUNTER_SHOT").seconds(12));
    a.add(days, "night-hunter-asleep", "Hunter's shot at night, you sleep", new Facts().phase("HUNTER_SHOT").night().seconds(12));
    a.add(days, "night-hunter-actor", "Hunter's shot at night, you shoot", new Facts().phase("HUNTER_SHOT").role("HUNTER").night().actor().seconds(12));
    a.add(days, "day-away", "Away from the village", new Facts().exiled());
    a.add(days, "day-waiting", "Waiting call (unknown phase)", new Facts().phase("UNKNOWN").night().actor());
    a.add(days, "day-round9", "Round clamp (round 12 shows 9)", new Facts().round(12));

    // badges
    Object[][] badges = {
      {"lover", "VILLAGER"}, {"life", "WITCH"}, {"death", "WITCH"}, {"infect", "INFECTOR"}, {"guard", "GUARD"},
      {"mayor", "VILLAGER"}, {"silenced", "VILLAGER"}
    };
    for (Object[] b : badges)
      a.add("Badges", "badge-" + b[0], "Badge: " + b[0], new Facts().role((String) b[1]).badges((String) b[0]));
    a.add("Badges", "badge-four", "Four badges (the panel's maximum)", new Facts().role("WITCH").badges("lover", "mayor", "silenced", "life"));
    a.add("Badges", "badge-away-silenced", "Sent away: no silenced badge", new Facts().exiled());
    a.add("Badges", "badge-dead", "Dead: no badges", new Facts().role("WITCH").viewerDead().dead(1, 7, 3));

    // danger flash
    Object[][] danger = {{7, "7 s: calm"}, {5, "5 s: odd, plate stays calm"}, {4, "4 s: even, plate flashes red"},
        {3, "3 s: odd, calm plate, red clock"}, {0, "0 s: red"}};
    for (Object[] d : danger)
      a.add("Danger flash", "danger-" + d[0], "Danger flash: " + d[1],
          new Facts().phase("SEER").role("SEER").night().actor().seconds((int) d[0]));
    a.add("Danger flash", "danger-vote", "Danger on the vote plate", new Facts().phase("DAY_VOTE").seconds(4).against(5));
    a.add("Danger flash", "danger-clock-max", "Clock bounds (9:59 is the maximum)", new Facts().seconds(5000));
    a.add("Danger flash", "danger-countdown", "Live countdown from 9 s (watch the flash)",
        new Facts().phase("SEER").role("SEER").night().actor().seconds(9).live());

    // dead
    a.add("Dead player", "dead-day", "Dead, by day", new Facts().role("WEREWOLF").viewerDead().dead(1, 7, 3));
    a.add("Dead player", "dead-night", "Dead, at night", new Facts().phase("SEER").role("SEER").night().viewerDead().dead(1, 7, 3));
    a.add("Dead player", "dead-vote", "Dead during the vote", new Facts().phase("DAY_VOTE").role("GUARD").viewerDead().dead(1, 7, 3).against(4));
    a.add("Dead player", "dead-finished", "Dead at game over", new Facts().phase("FINISHED").viewerDead().dead(1, 7, 3).seconds(0));

    // seat strips
    for (int n : new int[] {4, 6, 8, 12, 16, 20, 24}) {
      Entry t = table(n);
      if (seen.add(t.id())) out.add(t);
    }
    a.add("Seat strips", "seats-allalive", "Everybody alive", new Facts().n(10).dead());
    a.add("Seat strips", "seats-nearlyall-dead", "Nearly everybody dead", new Facts().n(10).dead(0, 1, 2, 4, 5, 6, 7, 9));

    // roles: the panel in the debate, with every badge the role could carry
    for (RoleInfo r : RoleInfo.all()) {
      Facts f = new Facts().role(r.id()).badges("life", "death", "infect", "guard");
      if (r.team().equals("wolves")) f.mates(5, 9);
      a.add("Roles", "role-" + r.id().toLowerCase(Locale.ROOT), r.name() + " (" + r.id() + ")", f, false, true);
    }

    // plates: one per palette
    Set<String> palettes = new TreeSet<>(List.of("villager", "vote", "sleep", "dead", "danger"));
    for (RoleInfo r : RoleInfo.all()) palettes.add(r.palette());
    for (String p : palettes)
      a.add("Palettes", "palette-" + p, "Plate palette: " + p, new Facts().palette(p));
    return List.copyOf(out);
  }
}
