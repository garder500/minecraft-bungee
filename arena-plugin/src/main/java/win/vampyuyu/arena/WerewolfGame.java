package win.vampyuyu.arena;

import java.util.*;
import java.util.stream.Collectors;

/** Pure, synchronized social-deduction state. No Bukkit/world access. */
public final class WerewolfGame implements GamePreset {
  public enum Role {
    VILLAGER,
    WEREWOLF,
    SEER,
    CUPID,
    HUNTER,
    WITCH,
    GUARD,
    ALPHA_WOLF,
    INFECTOR,
    WOLF_CUB,
    SCAPEGOAT,
    BEAR_TAMER,
    // Ultimate Werewolf: the village team
    APPRENTICE_SEER,
    AURA_SEER,
    DISEASED,
    GHOST,
    IDIOT,
    LYCAN,
    MARTYR,
    MASON,
    MAYOR,
    OLD_HAG,
    OLD_MAN,
    PI,
    PACIFIST,
    PRIEST,
    PRINCE,
    SPELLCASTER,
    TOUGH_GUY,
    TROUBLEMAKER,
    // the wolf side that does not hunt with the pack
    SORCERER,
    MINION,
    // roles that change sides or hide what they are
    CURSED,
    DOPPELGANGER,
    DRUNK,
    // other teams
    CULT_LEADER,
    HOODLUM,
    TANNER,
    LONE_WOLF,
    VAMPIRE
  }

  /**
   * Roles that wake with the pack at night and hunt with it. The lone wolf hunts with the pack
   * but wins alone; the minion and the sorcerer are on the wolves' side without hunting.
   */
  static boolean wolf(Role role) {
    return role == Role.WEREWOLF
        || role == Role.ALPHA_WOLF
        || role == Role.INFECTOR
        || role == Role.WOLF_CUB
        || role == Role.LONE_WOLF;
  }

  /** The team a role plays for right now: village, wolves, vampires, cult or solo. */
  static String team(Role role) {
    return RoleInfo.of(role.name()).team();
  }

  /** True for the roles of the wolf side, by name ("WEREWOLF", "ALPHA_WOLF"...). */
  public static boolean isWolf(String role) {
    try {
      return wolf(Role.valueOf(role));
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }

  public enum Phase {
    CUPID,
    DISCUSSION,
    DAY_VOTE,
    GUARD,
    SEER,
    NIGHT,
    WITCH,
    HUNTER_SHOT,
    FINISHED,
    // night calls of the Ultimate Werewolf roles (each owned by a RoleModule)
    DOPPEL,
    HOODLUM,
    PRIEST,
    VAMPIRE,
    PI,
    AURA,
    SORCERER,
    SPELL,
    HAG,
    CULT,
    TROUBLE,
    GHOST,
    AMULET,
    /** A day interruption: the martyr may take the condemned player's place. */
    MARTYR,
    /** Nominated voting: the nominee speaks for themselves. */
    DEFENSE,
    /** Nominated voting: thumbs up to live, thumbs down to die. */
    TRIAL
  }

  /** How long a nominee defends themselves. */
  static final int DEFENSE_SECONDS = 20;

  /** The order the night's calls are made in; a call with nobody to wake is skipped. */
  static final List<Phase> NIGHT_ORDER =
      List.of(
          Phase.CUPID,
          Phase.DOPPEL,
          Phase.HOODLUM,
          Phase.PRIEST,
          Phase.GUARD,
          Phase.SEER,
          Phase.NIGHT,
          Phase.VAMPIRE,
          Phase.WITCH,
          Phase.PI,
          Phase.AURA,
          Phase.SORCERER,
          Phase.SPELL,
          Phase.HAG,
          Phase.CULT,
          Phase.TROUBLE,
          Phase.GHOST,
          Phase.AMULET);

  /** A death about to happen: who, from what ({@code source}) and the journal's cause. */
  record Kill(UUID victim, String source, String cause) {}

  /** Opening words of the lovers' notice; the GUI dresses only these notices as a love title. */
  static final String LOVE_NOTICE = "Cupid's arrow struck you";

  /** Automatic cast: each special role joins once the table reaches this many players. */
  private static final List<Map.Entry<Role, Integer>> UNLOCK =
      List.of(
          Map.entry(Role.WITCH, 6),
          Map.entry(Role.HUNTER, 7),
          Map.entry(Role.CUPID, 8),
          Map.entry(Role.GUARD, 10),
          Map.entry(Role.SCAPEGOAT, 11),
          Map.entry(Role.BEAR_TAMER, 12),
          // the Ultimate Werewolf roles join big tables; the other teams (vampires, cult, lone
          // wolf, hoodlum, tanner) only come with a custom cast
          Map.entry(Role.MAYOR, 13),
          Map.entry(Role.SORCERER, 14),
          Map.entry(Role.PRINCE, 15),
          Map.entry(Role.PRIEST, 16),
          Map.entry(Role.LYCAN, 17),
          Map.entry(Role.MASON, 18),
          Map.entry(Role.MASON, 18),
          Map.entry(Role.APPRENTICE_SEER, 19),
          Map.entry(Role.AURA_SEER, 20),
          Map.entry(Role.OLD_HAG, 21),
          Map.entry(Role.TOUGH_GUY, 22),
          Map.entry(Role.MINION, 23),
          Map.entry(Role.MARTYR, 24),
          Map.entry(Role.PI, 25),
          Map.entry(Role.CURSED, 26),
          Map.entry(Role.DISEASED, 27),
          Map.entry(Role.SPELLCASTER, 28),
          Map.entry(Role.DRUNK, 29),
          Map.entry(Role.TROUBLEMAKER, 30),
          Map.entry(Role.IDIOT, 31),
          Map.entry(Role.PACIFIST, 32),
          Map.entry(Role.DOPPELGANGER, 33),
          Map.entry(Role.OLD_MAN, 34),
          Map.entry(Role.GHOST, 35));

  /** Wolves at a table of this size, as the rulebook has it: 6-8 one, 9-11 two, 12-15 three, then one per five. */
  static int wolvesAt(int players) {
    return players <= 8 ? 1 : players <= 11 ? 2 : players <= 15 ? 3 : players / 5;
  }

  private final Map<UUID, Role> roles = new LinkedHashMap<>();

  /** The role modules (see {@link RoleModule}); fresh for every game. */
  private final List<RoleModule> modules = Modules.create();

  private final Random random;

  /** Actors whose call is over in the current phase. */
  private final Set<UUID> done = new HashSet<>();

  /** Away for the day (old hag) and silenced for the day (spellcaster); cleared at nightfall. */
  private final Set<UUID> exiled = new HashSet<>(), muted = new HashSet<>();

  /** Players who die at the next dawn (a tough guy's delayed death): victim -> cause. */
  private final Map<UUID, String> doomed = new LinkedHashMap<>();

  /** Why the player who just died died, for the modules' died hook. */
  private final Map<UUID, String> sources = new HashMap<>();

  /** The victim a pack chose in a pack call other than the wolves' (the vampires'). */
  private final Map<Phase, UUID> packVictims = new EnumMap<>(Phase.class);

  /** Tonight's hunt is called off (the diseased was eaten the night before). */
  private boolean huntCalledOff, huntCalledOffNext;

  /** The day vote's bookkeeping: the condemned awaiting a martyr, rounds, extra lynchings. */
  private UUID pendingLynch;

  /** Variants: the amulet's holder, today's dice effects. */
  private UUID amuletHolder;

  private boolean amuletSaved, secretToday, lightningToday, diceInfectNext, defangNext, defangTonight;
  private final Set<UUID> amuletSeen = new HashSet<>();
  private int lastDice;
  private UUID seerLast;

  /** Nominated voting: the pending nominee and nominator, the thumbs of the trial, the day's time left. */
  private UUID nominee, nominator;

  private final Map<UUID, Boolean> thumbs = new HashMap<>();
  private int dayLeft;

  private int voteRound, lynchRound, lynchesLeft;
  private List<UUID> candidates;

  /** Seating order around the table (for the bear tamer's neighbours). */
  private List<UUID> ring = List.of();

  private final Set<UUID> infectUsed = new HashSet<>();
  private boolean infectTonight, cubRevenge;
  private UUID nightVictim2;
  private final Set<UUID> alive = new LinkedHashSet<>(),
      visions = new HashSet<>(),
      healUsed = new HashSet<>(),
      poisonUsed = new HashSet<>();
  private final Map<UUID, UUID> ballots = new HashMap<>(),
      lovers = new HashMap<>(),
      guards = new HashMap<>(),
      lastGuard = new HashMap<>(),
      poisons = new HashMap<>();
  private final Set<UUID> heals = new HashSet<>(), abstained = new HashSet<>();

  /** Every vision a seer had, oldest first: the menu keeps showing them on later nights. */
  private final Map<UUID, Map<UUID, Role>> seen = new HashMap<>();

  /**
   * Heads picked in a menu before a confirming button (Cupid's two lovers, the hunter's target, the
   * witch's poison target). They belong to the phase they were picked in.
   */
  private final Map<UUID, List<UUID>> aims = new HashMap<>();

  private Phase aimPhase;
  private int dawns;
  private final Deque<UUID> hunters = new ArrayDeque<>();
  private final int discussion, vote, night;
  private final GameRules.Werewolf settings;
  private UUID firstLover, nightVictim;

  /** The last day vote's outcome, until the presentation reads it. */
  private Verdict verdict;

  private boolean victimGuarded;
  private final Map<UUID, String> notices = new HashMap<>();
  private Phase phase = Phase.DISCUSSION, resume = Phase.DISCUSSION;
  private String winner = "";
  private WerewolfJournal journal = WerewolfJournal.SILENT;

  /** Nesting depth of player actions: only the outermost one is journaled as an action. */
  private int acting;

  /** Classic composition retained as an explicit constructor for integrations/tests. */
  public WerewolfGame(
      Collection<UUID> players, int discussion, int vote, int night, Random random) {
    this(players, discussion, vote, night, random, null);
  }

  public WerewolfGame(
      Collection<UUID> players,
      int discussion,
      int vote,
      int night,
      Random random,
      GameRules.Werewolf settings) {
    this(players, discussion, vote, night, random, settings, Map.of());
  }

  /** As above, with roles forced for given players (admin tests): they win over the shuffle. */
  public WerewolfGame(
      Collection<UUID> players,
      int discussion,
      int vote,
      int night,
      Random random,
      GameRules.Werewolf settings,
      Map<UUID, String> forced) {
    if (players.size() < 4
        || players.size() > 45
        || new HashSet<>(players).size() != players.size())
      throw new IllegalArgumentException("Werewolf requires 4–45 distinct participants");
    this.random = random;
    this.discussion = discussion;
    this.vote = vote;
    this.night = night;
    this.settings = settings == null ? GameRules.Werewolf.defaults() : settings;
    List<Role> deck = new ArrayList<>();
    if (settings != null && !settings.roles().isEmpty()) {
      for (Role role : Role.values())
        for (int i = 0; i < settings.roles().getOrDefault(role.name(), 0); i++) deck.add(role);
    } else {
      // The classic constructor (no settings) keeps one wolf per four players.
      int wolves = settings == null ? Math.max(1, players.size() / 4) : wolvesAt(players.size());
      for (int i = 0; i < wolves; i++) deck.add(Role.WEREWOLF);
      deck.add(Role.SEER);
      if (settings != null)
        for (var unlock : UNLOCK)
          if (players.size() >= unlock.getValue() && deck.size() < players.size())
            deck.add(unlock.getKey());
    }
    long wolves = deck.stream().filter(WerewolfGame::wolf).count();
    long killers = wolves + deck.stream().filter(r -> r == Role.VAMPIRE).count();
    if (deck.size() > players.size() || killers < 1 || killers * 2 >= players.size())
      throw new IllegalArgumentException(
          "Role composition must fit the players and start with fewer wolves than villagers");
    while (deck.size() < players.size()) deck.add(Role.VILLAGER);
    Collections.shuffle(deck, random);
    int i = 0;
    for (UUID id : players) roles.put(id, deck.get(i++));
    for (var force : forced.entrySet()) {
      if (!roles.containsKey(force.getKey())) continue;
      Role wanted = Role.valueOf(force.getValue());
      Role current = roles.get(force.getKey());
      if (wanted == current) continue;
      // Swap with whoever holds the wanted role; if nobody does, the player simply takes it.
      for (var other : roles.entrySet())
        if (other.getValue() == wanted
            && !forced.containsKey(other.getKey())
            && !other.getKey().equals(force.getKey())) {
          other.setValue(current);
          break;
        }
      roles.put(force.getKey(), wanted);
    }
    ring = List.copyOf(players);
    alive.addAll(players);
    for (RoleModule m : modules) m.deal(this);
    if (this.settings.amulet()) dealAmulet();
    // Like the card game, the story opens at night: Cupid first, then the usual night turns.
    startNight();
  }

  /**
   * Starts writing this game's play-by-play to the journal, opening with the deal: who holds which
   * card, the timers and the first phase.
   */
  public synchronized void journal(WerewolfJournal journal) {
    this.journal = journal;
    journal.at(where() + " " + phase);
    journal.log(
        "DEAL",
        roles.size()
            + " players, timers: discussion "
            + discussion
            + "s, vote "
            + vote
            + "s, night "
            + night
            + "s, cupid "
            + settings.cupidSeconds()
            + "s, witch "
            + settings.witchSeconds()
            + "s, hunter "
            + settings.hunterSeconds()
            + "s");
    journal.log("DEAL", "Composition: " + composition());
    for (UUID p : roles.keySet()) journal.log("DEAL", who(p));
    journal.log("PHASE", "The story opens with " + phase);
  }

  public synchronized WerewolfJournal journal() {
    return journal;
  }

  public synchronized String phase() {
    return phase.name();
  }

  /**
   * Names the current turn: it changes whenever someone new must act, including one hunter's shot
   * followed by another's, where the phase name stays the same.
   */
  public synchronized String turnKey() {
    return phase
        + ":"
        + dawns
        + ":"
        + lynchRound
        + "."
        + voteRound
        + ":"
        + (phase == Phase.HUNTER_SHOT ? hunters.peekFirst() : "");
  }

  public synchronized String role(UUID p) {
    return roles.getOrDefault(p, Role.VILLAGER).name();
  }

  public synchronized Set<UUID> alive() {
    return Set.copyOf(alive);
  }

  public synchronized String winner() {
    return winner;
  }

  public synchronized int seconds() {
    return switch (phase) {
      case CUPID -> settings.cupidSeconds();
      case DISCUSSION -> nominating() ? Math.max(5, dayLeft) : discussion;
      case DAY_VOTE -> voteRound > 0 ? Math.max(10, vote / 2) : vote;
      case DEFENSE -> DEFENSE_SECONDS;
      case TRIAL -> vote;
      case WITCH -> settings.witchSeconds();
      case HUNTER_SHOT, MARTYR -> settings.hunterSeconds();
      case FINISHED -> 0;
      // A call only the dead would answer is played out briefly so the table learns nothing.
      default -> callActors(phase).isEmpty() ? Math.min(night, 6) : night;
    };
  }

  /**
   * Whether this viewer may see the running ballots: everyone at the day vote, only the pack during
   * the hunt. The one place that decides it, so a secret-ballot variant changes only this.
   */
  public synchronized boolean tallyVisible(UUID viewer) {
    Role mine = roles.get(viewer);
    return phase == Phase.DAY_VOTE && !secretBallots()
        || phase == Phase.NIGHT && mine != null && wolf(mine)
        || phase == Phase.VAMPIRE && mine == Role.VAMPIRE;
  }

  /** The day's ballots are hidden until the vote closes (a setting, or today's dice). */
  public synchronized boolean secretBallots() {
    return settings.ballots().equals("SECRET") || secretToday;
  }

  /** How a death is told: FULL role, TEAM, WOLF or not, NONE (a setting of the arena). */
  public synchronized String reveal() {
    return settings.reveal();
  }

  public synchronized Map<UUID, Integer> votes(UUID viewer) {
    if (!tallyVisible(viewer)) return Map.of();
    Map<UUID, Integer> totals = new HashMap<>();
    ballots.values().forEach(p -> totals.merge(p, 1, Integer::sum));
    return Map.copyOf(totals);
  }

  private static String loveNotice(UUID partner) {
    return LOVE_NOTICE
        + ": you are in love with "
        + partner
        + ". If either of you dies, both die. Only you two know it.";
  }

  @Override
  public synchronized Map<UUID, String> drainNotices() {
    Map<UUID, String> out = Map.copyOf(notices);
    notices.clear();
    return out;
  }

  public synchronized String help(UUID p) {
    String bond =
        lovers.containsKey(p)
            ? " You are in love with " + lovers.get(p) + "; if either dies, both die."
            : "";
    String text = RoleInfo.of(selfRole(p).name()).help();
    return (text.isEmpty() ? RoleInfo.of("VILLAGER").help() : text) + bond;
  }

  /** The role this player believes they hold (a drunk thinks they are a villager). */
  public synchronized Role selfRole(UUID p) {
    Role actual = roles.getOrDefault(p, Role.VILLAGER);
    for (RoleModule m : modules) actual = m.perceived(this, p, actual);
    return actual;
  }

  /** What the HUD and help show as the player's own role. */
  public synchronized String selfRoleName(UUID p) {
    return selfRole(p).name();
  }

  /**
   * Buttons the player can press right now, in menu order: skip (day vote), heal / poison / sleep
   * (witch), bind (Cupid, once two lovers are picked), shoot (hunter, once a target is picked) and
   * protect-self (guard).
   */
  public synchronized Map<String, String> abilities(UUID p) {
    Map<String, String> out = new LinkedHashMap<>();
    Role role = roles.get(p);
    boolean living = alive.contains(p);
    switch (phase) {
      case DAY_VOTE -> {
        if (living && !exiled.contains(p))
          out.put("skip", abstained.contains(p) ? "Vote after all" : "Skip the vote");
      }
      case WITCH -> {
        if (!living || role != Role.WITCH) break;
        if (!healUsed.contains(p)
            && nightVictim != null
            && alive.contains(nightVictim)
            && !heals.contains(p)) out.put("heal", "Use the life potion · save " + nightVictim);
        if (!poisonUsed.contains(p) && !aims(p).isEmpty())
          out.put("poison", "Use the death potion on " + aims(p).getFirst());
        out.put("sleep", "Go back to sleep");
      }
      case CUPID -> {
        if (living && role == Role.CUPID && lovers.isEmpty() && aims(p).size() == 2)
          out.put("bind", "Bind " + aims(p).get(0) + " and " + aims(p).get(1));
      }
      case HUNTER_SHOT -> {
        if (Objects.equals(hunters.peekFirst(), p) && !aims(p).isEmpty())
          out.put("shoot", "Shoot " + aims(p).getFirst());
      }
      case GUARD -> {
        if (living && role == Role.GUARD && !Objects.equals(lastGuard.get(p), p))
          out.put("protect-self", "Protect yourself tonight");
      }
      case NIGHT -> {
        if (living && role == Role.INFECTOR && !infectUsed.contains(p) && !infectTonight)
          out.put("infect", "Infect tonight's victim");
      }
      case DISCUSSION, FINISHED, DEFENSE -> {}
      case TRIAL -> {
        if (jurors().contains(p)) {
          out.put("live", "Thumbs up: let " + nominee + " live");
          out.put("die", "Thumbs down: eliminate " + nominee);
        }
      }
      default -> {
        // A call owned by a module: its buttons, and a way to go back to sleep.
        if (callActors(phase).contains(p)) {
          if (phase != Phase.MARTYR) out.put("pass", "Go back to sleep");
          for (RoleModule m : modules) out.putAll(m.abilities(this, p));
        }
      }
    }
    return out;
  }

  public synchronized String use(UUID p, String ability) {
    return act(p, "presses " + ability.toUpperCase(Locale.ROOT), () -> press(p, ability));
  }

  private String press(UUID p, String ability) {
    if (!abilities(p).containsKey(ability))
      throw new IllegalArgumentException(
          switch (ability) {
            case "heal" -> "Life potion unavailable";
            case "poison" -> "Right-click a player to poison first";
            case "bind" -> "Pick two lovers first";
            case "shoot" -> "Pick your target first";
            case "infect" -> "You can only infect once, at night";
            default -> "This action is unavailable now";
          });
    switch (ability) {
      case "heal" -> {
        heals.add(p);
        healUsed.add(p);
        journal.log("WITCH", who(p) + " saves " + who(nightVictim) + " with the life potion");
        return "Life potion used. The wolves' victim will survive this night.";
      }
      case "poison" -> {
        UUID target = aims.remove(p).getFirst();
        return poison(p, target);
      }
      case "sleep" -> {
        aims.remove(p);
        next();
        return "You go back to sleep.";
      }
      case "live", "die" -> {
        thumbs.put(p, ability.equals("die"));
        if (thumbs.keySet().containsAll(jurors())) next();
        return ability.equals("die") ? "Thumbs down." : "Thumbs up.";
      }
      case "pass" -> {
        done(p);
        return phase == Phase.MARTYR ? "You let it pass." : "You go back to sleep.";
      }
      case "infect" -> {
        infectTonight = true;
        infectUsed.add(p);
        journal.log("INFECT", who(p) + " will infect tonight's victim");
        return "Infection armed: tonight's victim turns into a werewolf instead of dying.";
      }
      case "bind" -> {
        List<UUID> pair = aims.remove(p);
        firstLover = null;
        select(p, pair.get(0));
        return select(p, pair.get(1));
      }
      case "shoot" -> {
        return select(p, aims.remove(p).getFirst());
      }
      case "protect-self" -> {
        return select(p, p);
      }
      default -> {
        for (RoleModule m : modules)
          if (m.abilities(this, p).containsKey(ability)) return m.use(this, p, ability);
        // skip
        ballots.remove(p);
        if (abstained.remove(p)) return "You may vote again: right-click a player.";
        abstained.add(p);
        return "You skip this vote. Right-click a player if you change your mind.";
      }
    }
  }

  /**
   * Picks or unpicks a head before a confirming button: Cupid's lovers (two), the hunter's target
   * and the witch's poison target (one each; a new pick replaces the old one).
   */
  public synchronized String aim(UUID p, UUID target) {
    return act(p, "picks " + who(target), () -> pick(p, target));
  }

  private String pick(UUID p, UUID target) {
    if (!alive.contains(target)) throw new IllegalArgumentException("Select a living player");
    Role role = roles.get(p);
    int max;
    if (phase == Phase.CUPID && role == Role.CUPID && alive.contains(p) && lovers.isEmpty())
      max = 2;
    else if (phase == Phase.HUNTER_SHOT && Objects.equals(hunters.peekFirst(), p)) max = 1;
    else if (phase == Phase.WITCH && role == Role.WITCH && alive.contains(p)) {
      if (poisonUsed.contains(p))
        throw new IllegalArgumentException("Your death potion has already been used");
      max = 1;
    } else throw new IllegalArgumentException("You cannot pick a player during this phase");
    if (aimPhase != phase) {
      aims.clear();
      aimPhase = phase;
    }
    List<UUID> picks = aims.computeIfAbsent(p, k -> new ArrayList<>());
    if (picks.remove(target)) return "Removed " + target + ".";
    if (picks.size() >= max) {
      if (max > 1)
        throw new IllegalArgumentException("Two lovers are already picked: right-click one to remove them");
      picks.clear();
    }
    picks.add(target);
    return switch (phase) {
      case CUPID ->
          picks.size() == 2
              ? "Both lovers picked: use Bind to unite them."
              : "First lover picked: " + target + ". Pick a second one.";
      case HUNTER_SHOT -> "Target: " + target + ". Use Shoot to fire.";
      default -> "Target: " + target + ". Use the death potion to poison them.";
    };
  }

  /** Heads this player picked in the current phase, in pick order. */
  public synchronized List<UUID> aims(UUID p) {
    return aimPhase == phase ? List.copyOf(aims.getOrDefault(p, List.of())) : List.of();
  }

  private String poison(UUID witch, UUID target) {
    poisonUsed.add(witch);
    poisons.put(witch, target);
    journal.log("WITCH", who(witch) + " poisons " + who(target) + " (dies at dawn)");
    return "Death potion used. " + target + " will die at dawn.";
  }

  public synchronized String select(UUID voter, UUID target) {
    return act(voter, "selects " + who(target), () -> choose(voter, target));
  }

  private String choose(UUID voter, UUID target) {
    if (!alive.contains(target)) throw new IllegalArgumentException("Select a living player");
    if (phase == Phase.HUNTER_SHOT && Objects.equals(hunters.peekFirst(), voter)) {
      if (exiled.contains(target))
        throw new IllegalArgumentException("That player is away from the village today");
      hunters.removeFirst();
      resolveKill(new Kill(target, "SHOT", "shot by the hunter " + who(voter)));
      resumeAfterHunters();
      return "Your final shot has been fired.";
    }
    if (!alive.contains(voter)) throw new IllegalArgumentException("Eliminated players cannot act");
    Role role = roles.get(voter);
    if (phase == Phase.CUPID && role == Role.CUPID) {
      if (!lovers.isEmpty())
        throw new IllegalArgumentException("The lovers have already been chosen");
      if (firstLover == null) {
        firstLover = target;
        return "First lover selected. Choose a different second lover.";
      }
      if (firstLover.equals(target))
        throw new IllegalArgumentException("Choose a different second lover");
      lovers.put(firstLover, target);
      lovers.put(target, firstLover);
      journal.log(
          "LOVERS", who(firstLover) + " and " + who(target) + " are bound by " + who(voter));
      notices.put(firstLover, loveNotice(target));
      notices.put(target, loveNotice(firstLover));
      advanceNight(Phase.CUPID);
      checkWinner(); // Binding the last two players as a mixed couple ends the story at once.
      return "The lovers are bound. Only they know their bond.";
    }
    if (phase == Phase.GUARD && role == Role.GUARD) {
      if (Objects.equals(lastGuard.get(voter), target))
        throw new IllegalArgumentException(
            "You cannot protect the same player on consecutive nights");
      guards.put(voter, target);
      journal.log("GUARD", who(voter) + " protects " + who(target) + " tonight");
      next();
      return "Protection chosen: " + target + " is safe from the wolves tonight.";
    }
    if (phase == Phase.WITCH && role == Role.WITCH) {
      if (poisonUsed.contains(voter))
        throw new IllegalArgumentException("Your death potion has already been used");
      return poison(voter, target);
    }
    if (phase == Phase.DISCUSSION && nominating()) return nominate(voter, target);
    if (phase == Phase.AMULET) return passAmulet(voter, target);
    for (RoleModule m : modules)
      if (m.phases().contains(phase) && !pack(phase)) {
        if (!callActors(phase).contains(voter))
          throw new IllegalArgumentException("You cannot pick a player during this phase");
        return m.select(this, phase, voter, target);
      }
    if (voter.equals(target)) throw new IllegalArgumentException("Select another living player");
    if (phase == Phase.SEER && role == Role.SEER) {
      if (!visions.add(voter))
        throw new IllegalArgumentException("You already used your vision tonight");
      seerLast = target;
      Role shown = seenBySeer(target);
      seen.computeIfAbsent(voter, k -> new LinkedHashMap<>()).put(target, shown);
      String vision = "Your vision: " + target + " is " + shown + ".";
      next();
      return vision;
    }
    boolean hunting = pack(phase) && callActors(phase).contains(voter);
    if (phase != Phase.DAY_VOTE && !hunting)
      throw new IllegalArgumentException("You cannot vote during this phase");
    if (phase == Phase.NIGHT && wolf(roles.get(target)))
      throw new IllegalArgumentException("Wolves cannot target another wolf");
    if (phase == Phase.VAMPIRE && roles.get(target) == Role.VAMPIRE)
      throw new IllegalArgumentException("Vampires cannot target another vampire");
    if (phase == Phase.DAY_VOTE) {
      if (exiled.contains(voter))
        throw new IllegalArgumentException("You are away from the village today");
      if (exiled.contains(target))
        throw new IllegalArgumentException("That player is away from the village today");
      if (candidates != null && !candidates.contains(target))
        throw new IllegalArgumentException("The vote is tied: choose between the tied players");
    }
    abstained.remove(voter);
    if (target.equals(ballots.get(voter))) {
      ballots.remove(voter); // A second click on the same head takes the vote back.
      return "Vote withdrawn.";
    }
    ballots.put(voter, target);
    if (phase == Phase.DAY_VOTE && lightningToday && tallies().getOrDefault(target, 0) >= 3) {
      journal.log("DICE", "Lightning vote: " + who(target) + " gathered three ballots");
      next();
      return "Lightning! " + target + " gathered three ballots and is judged at once.";
    }
    if (pack(phase) && packAgrees()) {
      journal.log("HUNT", "The pack agrees on " + who(target) + ": " + tally());
      next();
      return "The pack agrees: " + target + " is tonight's prey.";
    }
    return pack(phase)
        ? "Vote recorded. The hunt ends once every wolf picks the same victim."
        : "Vote recorded. You may change it until the phase ends.";
  }

  /** The calls where the actors vote as one pack: the wolves' hunt and the vampires' feast. */
  private static boolean pack(Phase ph) {
    return ph == Phase.NIGHT || ph == Phase.VAMPIRE;
  }

  /** Every pack member called now has voted, and all for the same victim. */
  private boolean packAgrees() {
    if (!pack(phase)) return false;
    Set<UUID> members = callActors(phase);
    return !members.isEmpty()
        && members.stream().allMatch(ballots::containsKey)
        && members.stream().map(ballots::get).distinct().count() == 1;
  }

  /** The arena's time limit ran out: nobody wins and every menu unlocks. */
  @Override
  public synchronized void finish() {
    if (phase == Phase.FINISHED) return;
    journal.log("TIMEOUT", "The arena's time limit ran out: nobody wins.");
    to(Phase.FINISHED);
    journal.log("GAME", "Final roles: " + cast());
  }

  public synchronized void remove(UUID p) {
    if (phase == Phase.FINISHED) return; // The result (and the winners' list) is settled.
    journal.log("LEAVE", who(p) + " left the game");
    eliminate(p, false, "left the game");
    hunters.remove(p);
    guards.remove(p);
    poisons.remove(p);
    aims.remove(p);
    abstained.remove(p);
    done.remove(p);
    doomed.remove(p);
    thumbs.remove(p);
    if (p.equals(nominee) || p.equals(nominator)) {
      nominee = null;
      nominator = null;
      if (phase == Phase.DEFENSE || phase == Phase.TRIAL) to(Phase.DISCUSSION);
    }
    if (p.equals(pendingLynch) && phase == Phase.MARTYR) pendingLynch = null;
    if (p.equals(firstLover)) firstLover = null;
    if (phase == Phase.HUNTER_SHOT) resumeAfterHunters();
    // A departing lover can take a hunter down with them: let them shoot now, then carry on.
    else if (!hunters.isEmpty() && phase != Phase.FINISHED) continueWith(phase);
    else checkWinner();
    skipUnavailable();
    if (packAgrees()) next();
  }

  /** Moves the game on when the call it is in has nobody left to wake or everyone is done. */
  private void skipUnavailable() {
    for (int guard = 0; guard < 40 && NIGHT_ORDER.contains(phase); guard++) {
      Set<UUID> called = callActors(phase);
      boolean over = called.isEmpty() || !pack(phase) && done.containsAll(called);
      if (!over) return;
      Phase was = phase;
      next();
      if (phase == was) return;
    }
  }

  /** Ballot weight: the alpha counts double at night; at the day vote the modules decide. */
  private int weight(UUID voter) {
    if (phase == Phase.NIGHT) return roles.get(voter) == Role.ALPHA_WOLF ? 2 : 1;
    if (phase != Phase.DAY_VOTE) return 1;
    int w = 1;
    for (RoleModule m : modules) w *= Math.max(0, m.weight(this, voter));
    return w;
  }

  private Map<UUID, Integer> tallies() {
    Map<UUID, Integer> totals = new HashMap<>();
    for (var ballot : ballots.entrySet())
      if (alive.contains(ballot.getValue())
          && !exiled.contains(ballot.getValue())
          && !exiled.contains(ballot.getKey())) {
        int w = weight(ballot.getKey());
        if (w > 0) totals.merge(ballot.getValue(), w, Integer::sum);
      }
    return totals;
  }

  /** The players sharing the highest tally, when that tally is shared. */
  private List<UUID> tiedLeaders() {
    Map<UUID, Integer> totals = tallies();
    int max = totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    if (max <= 0) return List.of();
    List<UUID> top =
        totals.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
    return top.size() > 1 ? top : List.of();
  }

  /** The second victim of a wolf cub's revenge: next in line after the first, ties by seat order. */
  private UUID runnerUp(UUID first) {
    Map<UUID, Integer> totals = tallies();
    totals.remove(first);
    return totals.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<UUID, Integer>>comparingInt(e -> -e.getValue())
                .thenComparingInt(e -> ring.indexOf(e.getKey())))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  private UUID majority() {
    Map<UUID, Integer> totals = tallies();
    int max = totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    List<UUID> top =
        totals.entrySet().stream().filter(e -> e.getValue() == max).map(Map.Entry::getKey).toList();
    return top.size() == 1 ? top.getFirst() : null;
  }

  private void eliminate(UUID p, boolean shot, String cause) {
    if (p == null || !alive.remove(p)) return;
    journal.log("DEATH", who(p) + " · " + cause);
    // Only the living vote, and only for the living.
    ballots.remove(p);
    ballots.values().removeIf(p::equals);
    aims.values().forEach(picks -> picks.remove(p));
    exiled.remove(p);
    if (roles.get(p) == Role.WOLF_CUB && phase != Phase.FINISHED) {
      cubRevenge = true;
      journal.log("CUB", who(p) + " is dead: the pack will kill twice the next night");
    }
    if (shot && roles.get(p) == Role.HUNTER) {
      hunters.addLast(p);
      journal.log("HUNTER", who(p) + " may take a last shot");
    }
    String source = sources.getOrDefault(p, "OTHER");
    sources.remove(p);
    for (RoleModule m : modules) m.died(this, p, source, cause);
    UUID partner = lovers.get(p);
    if (partner != null) eliminate(partner, true, "died of grief for their lover " + who(p));
  }

  /**
   * A death goes through the protections (the witch's flask, the guard, the modules' saves) and,
   * when nothing saves the victim, happens. Returns true when the victim died.
   */
  private boolean resolveKill(Kill k) {
    UUID v = k.victim();
    if (v == null || !alive.contains(v)) return false;
    String source = k.source();
    if (source.equals("WOLVES") && !heals.isEmpty() && v.equals(nightVictim)) {
      journal.log("SAVED", who(v) + " is saved by the witch");
      return false;
    }
    if ((source.equals("WOLVES") || source.equals("WOLVES2")) && guards.containsValue(v)) {
      journal.log("SAVED", who(v) + " is saved by the guard");
      return false;
    }
    if (v.equals(amuletHolder) && !source.equals("OTHER")) {
      amuletSaved = true;
      notify(
          v,
          "The Amulet of Protection saved your life. Pass it on tonight, or it shatters.");
      journal.log("SAVED", who(v) + " is saved by the amulet from " + source);
      return false;
    }
    for (RoleModule m : modules)
      if (m.saves(this, k)) {
        journal.log("SAVED", who(v) + " survives " + source + " (" + k.cause() + ")");
        return false;
      }
    if (source.equals("WOLVES") && defangTonight && !wolf(roles.get(v))) {
      setRole(v, Role.VILLAGER);
      notify(v, "The wolves took your power: you are a plain villager now.");
      journal.log("DICE", who(v) + " loses their power instead of dying");
      return false;
    }
    if (source.equals("WOLVES") && infectTonight) {
      roles.put(v, Role.WEREWOLF);
      notify(
          v, "The infector bit you: you are now a werewolf. The pack knows you; you win with it.");
      journal.log("INFECT", who(v) + " turns into a werewolf");
      return false;
    }
    sources.put(v, source);
    eliminate(v, true, k.cause());
    return !alive.contains(v);
  }

  private void nightDeaths() {
    journal.log("DAWN", "The village wakes up");
    if (nightVictim == null) journal.log("DAWN", "The wolves chose no victim");
    else if (!heals.isEmpty()) journal.log("DAWN", who(nightVictim) + " is saved by the witch");
    else if (victimGuarded) journal.log("DAWN", who(nightVictim) + " is saved by the guard");
    List<Kill> kills = new ArrayList<>();
    if (nightVictim != null && alive.contains(nightVictim))
      kills.add(new Kill(nightVictim, "WOLVES", "killed by the wolves"));
    if (nightVictim2 != null)
      kills.add(new Kill(nightVictim2, "WOLVES2", "killed by the wolves (the cub's revenge)"));
    for (var poison : poisons.entrySet())
      kills.add(
          new Kill(poison.getValue(), "POISON", "poisoned by the witch " + who(poison.getKey())));
    for (var late : doomed.entrySet()) kills.add(new Kill(late.getKey(), "DELAYED", late.getValue()));
    doomed.clear();
    for (Kill k : kills) resolveKill(k);
    for (RoleModule m : modules) m.dawn(this);
    lastGuard.clear();
    lastGuard.putAll(guards);
    guards.clear();
    poisons.clear();
    heals.clear();
    dawns++;
    nightVictim = null;
    nightVictim2 = null;
    infectTonight = false;
    defangTonight = false;
    victimGuarded = false;
    growl();
    rollDice();
    voteRound = 0;
    lynchRound = 0;
    nominee = null;
    nominator = null;
    dayLeft = discussion + vote;
    continueWith(Phase.DISCUSSION);
  }

  /** Carries on to {@code next} (null: the night begins) once the pending hunters have shot. */
  private void continueWith(Phase next) {
    resume = next;
    if (!hunters.isEmpty()) to(Phase.HUNTER_SHOT);
    else enterResume();
  }

  private void enterResume() {
    if (resume == null) {
      checkWinner();
      if (phase != Phase.FINISHED) startNight();
    } else {
      to(resume);
      checkWinner();
    }
  }

  private void resumeAfterHunters() {
    if (hunters.isEmpty()) {
      enterResume();
      skipUnavailable();
      if (packAgrees()) next();
    }
  }

  // ------------------------------------------------------------------ the night's queue

  /** A night begins: yesterday's exile and silence end, the modules stir, the first call opens. */
  private void startNight() {
    exiled.clear();
    muted.clear();
    visions.clear();
    packVictims.clear();
    huntCalledOff = huntCalledOffNext;
    huntCalledOffNext = false;
    secretToday = false;
    lightningToday = false;
    if (diceInfectNext) infectTonight = true;
    defangTonight = defangNext;
    diceInfectNext = false;
    defangNext = false;
    for (RoleModule m : modules) m.nightfall(this);
    if (huntCalledOff)
      for (UUID p : alive)
        if (wolf(roles.get(p))) notify(p, "The pack is too sick to hunt tonight.");
    advanceNight(null);
  }

  /** Opens the next call after {@code after} that has somebody to wake, or the dawn. */
  private void advanceNight(Phase after) {
    int from = after == null ? 0 : NIGHT_ORDER.indexOf(after) + 1;
    for (int i = from; i < NIGHT_ORDER.size(); i++) {
      Phase call = NIGHT_ORDER.get(i);
      boolean hidden = !settings.reveal().equals("FULL");
      if (!callActors(call, hidden).isEmpty()) {
        to(call);
        return;
      }
    }
    nightDeaths();
  }

  /** Whether a role is called in this phase, before any readiness check. */
  private boolean calls(Phase ph, Role r) {
    return switch (ph) {
      case CUPID -> r == Role.CUPID;
      case GUARD -> r == Role.GUARD;
      case SEER -> r == Role.SEER;
      case NIGHT -> wolf(r);
      case WITCH -> r == Role.WITCH;
      default -> {
        RoleInfo info = RoleInfo.byPhase(ph.name());
        yield info != null && info.id().equals(r.name());
      }
    };
  }

  /** Who is called in this phase right now: living (the ghost excepted), ready, in the right night. */
  private Set<UUID> callActors(Phase ph) {
    return callActors(ph, false);
  }

  /**
   * As above; {@code dead} also counts the dead players who hold the called role: with a hidden
   * reveal the call still happens, so nobody learns who is gone from the narrator's silence.
   */
  private Set<UUID> callActors(Phase ph, boolean dead) {
    if (ph == Phase.AMULET) {
      Set<UUID> holder = new LinkedHashSet<>();
      if (amuletHolder != null && alive.contains(amuletHolder)) holder.add(amuletHolder);
      return holder;
    }
    boolean open =
        switch (ph) {
          case CUPID -> dawns == 0 && lovers.isEmpty();
          case DOPPEL, HOODLUM -> dawns == 0;
          case MARTYR -> pendingLynch != null;
          default -> true;
        };
    Set<UUID> out = new LinkedHashSet<>();
    if (!open) return out;
    for (var entry : roles.entrySet()) {
      UUID p = entry.getKey();
      if (!calls(ph, entry.getValue())) continue;
      if (!alive.contains(p) && ph != Phase.GHOST && !dead) continue;
      if (ph == Phase.MARTYR && p.equals(pendingLynch)) continue;
      boolean ready = true;
      for (RoleModule m : modules) ready &= m.ready(this, ph, p);
      if (ready) out.add(p);
    }
    return out;
  }

  /** What the seer sees of a player: the role, unless the role disguises itself (lycan, minion...). */
  private Role seenBySeer(UUID target) {
    Role actual = roles.get(target);
    String disguise = RoleInfo.of(actual.name()).seerSees();
    return disguise == null ? actual : Role.valueOf(disguise);
  }

  /** The phase ran out of time. */
  public synchronized void advance() {
    if (phase != Phase.FINISHED)
      journal.log("TIMEOUT", phase + " ended by the clock (" + seconds() + "s)");
    next();
  }

  /** Closes the current phase: its time ran out, or a player's action completed it. */
  private void next() {
    switch (phase) {
      case FINISHED -> {
        return;
      }
      case CUPID -> {
        // Two picked lovers are bound when time runs out, as if Cupid had pressed BIND.
        for (UUID cupid : alive)
          if (roles.get(cupid) == Role.CUPID && abilities(cupid).containsKey("bind")) {
            use(cupid, "bind");
            return;
          }
        if (lovers.isEmpty())
          for (UUID cupid : alive)
            if (roles.get(cupid) == Role.CUPID)
              notify(cupid, "Time ran out before you chose two lovers: nobody is in love.");
        if (lovers.isEmpty()) journal.log("CUPID", "Time ran out before two lovers were chosen");
        firstLover = null;
        advanceNight(Phase.CUPID);
      }
      case DISCUSSION -> {
        if (nominating()) endDay("Nobody was nominated in time");
        else to(Phase.DAY_VOTE);
      }
      case DAY_VOTE -> closeVote();
      case DEFENSE -> to(Phase.TRIAL);
      case TRIAL -> closeTrial();
      case GUARD -> advanceNight(Phase.GUARD);
      case SEER -> advanceNight(Phase.SEER);
      case NIGHT -> {
        // The witch is shown whoever the pack chose, even if the guard will save them.
        nightVictim = majority();
        nightVictim2 = null;
        if (cubRevenge) {
          // The pack hunts for the cub: its two favourite prey die, even when its votes are split.
          if (nightVictim == null) nightVictim = runnerUp(null);
          if (nightVictim != null) nightVictim2 = runnerUp(nightVictim);
          cubRevenge = false;
        }
        if (huntCalledOff) {
          journal.log("HUNT", "The pack was too sick to hunt");
          nightVictim = null;
          nightVictim2 = null;
        }
        journal.log(
            "HUNT",
            "The wolves' victim: "
                + who(nightVictim)
                + (nightVictim2 == null ? "" : " and " + who(nightVictim2))
                + " · "
                + tally());
        ballots.clear();
        victimGuarded = nightVictim != null && guards.containsValue(nightVictim);
        advanceNight(Phase.NIGHT);
      }
      case VAMPIRE -> {
        packVictims.put(Phase.VAMPIRE, majority());
        journal.log("HUNT", "The vampires' victim: " + who(packVictims.get(Phase.VAMPIRE)));
        ballots.clear();
        advanceNight(Phase.VAMPIRE);
      }
      case WITCH -> advanceNight(Phase.WITCH);
      case MARTYR -> finishLynch(pendingLynch);
      case AMULET -> {
        if (amuletSaved) {
          journal.log("AMULET", "The amulet saved a life and was not passed: it shatters");
          if (amuletHolder != null) notify(amuletHolder, "The Amulet of Protection shatters.");
          amuletHolder = null;
        }
        amuletSaved = false;
        advanceNight(Phase.AMULET);
      }
      case HUNTER_SHOT -> {
        // A picked target is shot when time runs out, as if the hunter had pressed SHOOT.
        UUID hunter = hunters.peekFirst();
        if (hunter != null && abilities(hunter).containsKey("shoot")) {
          use(hunter, "shoot");
          return;
        }
        journal.log("HUNTER", who(hunters.peekFirst()) + " did not shoot in time");
        hunters.pollFirst();
        resumeAfterHunters();
      }
      default -> advanceNight(phase); // a call owned by a module
    }
  }

  /** The day vote closes: forced ballots, ties, the scapegoat, a martyr, then the verdict. */
  private void closeVote() {
    if (secretBallots()) revealBallots();
    List<UUID> pool = new ArrayList<>(alive);
    pool.removeAll(exiled);
    if (candidates != null) pool.retainAll(candidates);
    for (UUID voter : new ArrayList<>(alive)) {
      if (ballots.containsKey(voter) || exiled.contains(voter)) continue;
      for (RoleModule m : modules) {
        UUID forced = m.autoBallot(this, voter, List.copyOf(pool));
        if (forced != null && pool.contains(forced) && !forced.equals(voter)) {
          ballots.put(voter, forced);
          abstained.remove(voter);
          journal.log("BALLOT", who(voter) + " votes against " + who(forced) + " without being asked");
          break;
        }
      }
    }
    UUID chosen = majority();
    List<UUID> tied = chosen == null ? tiedLeaders() : List.of();
    if (!tied.isEmpty()) {
      UUID scapegoat = null;
      for (UUID p : alive) if (roles.get(p) == Role.SCAPEGOAT) scapegoat = p;
      if (scapegoat != null) {
        chosen = scapegoat;
        journal.log("SCAPEGOAT", who(scapegoat) + " takes the blame for the tied vote");
      } else if (voteRound == 0) {
        // A tie asks for one more vote, among the tied players only.
        voteRound++;
        candidates = List.copyOf(tied);
        journal.log("REVOTE", "Tied between " + names(tied) + " · " + tally());
        for (UUID p : alive)
          notify(p, "The vote is tied: vote again, only for the tied players.");
        ballots.clear();
        abstained.clear();
        done.clear();
        return;
      }
    }
    condemn(chosen);
  }

  /** The village condemned this player (or nobody): a martyr may stand in, then the verdict. */
  private void condemn(UUID chosen) {
    if (chosen != null) {
      pendingLynch = chosen;
      Set<UUID> martyrs = callActors(Phase.MARTYR);
      if (!martyrs.isEmpty()) {
        journal.log(
            "MARTYR", who(chosen) + " is condemned; " + names(martyrs) + " may take their place");
        to(Phase.MARTYR);
        return;
      }
      pendingLynch = null;
    }
    finishLynch(chosen);
  }

  // ------------------------------------------------------------------ nominated voting

  /** Whether the village eliminates by nomination (a setting) instead of pointing. */
  public synchronized boolean nominating() {
    return settings.voting().equals("NOMINATE");
  }

  /** The player nominated and waiting to be seconded, or defending, or on trial. */
  public synchronized UUID nominee() {
    return nominee;
  }

  /** The living players who cast a thumb at the trial: everybody but the accused and the exiled. */
  private Set<UUID> jurors() {
    Set<UUID> jury = new LinkedHashSet<>(alive);
    jury.remove(nominee);
    jury.removeAll(exiled);
    return jury;
  }

  /** A click during the debate: the first click nominates, another player's click on the same player seconds. */
  private String nominate(UUID voter, UUID target) {
    if (exiled.contains(voter))
      throw new IllegalArgumentException("You are away from the village today");
    if (exiled.contains(target))
      throw new IllegalArgumentException("That player is away from the village today");
    if (voter.equals(target)) throw new IllegalArgumentException("Select another living player");
    if (!target.equals(nominee)) {
      nominee = target;
      nominator = voter;
      journal.log("NOMINATE", who(voter) + " nominates " + who(target));
      notifyLiving(
          voter + " nominates " + target + ". Right-click " + target
              + " to second the nomination, or nominate somebody else.");
      return "You nominate " + target + ". Somebody must second it.";
    }
    if (voter.equals(nominator))
      throw new IllegalArgumentException("You made this nomination: another player must second it");
    journal.log("NOMINATE", who(voter) + " seconds the nomination of " + who(target));
    voteRound++;
    thumbs.clear();
    notifyLiving(voter + " seconds. " + target + " may now defend themselves.");
    if (voteRound == 1) {
      // The day's vote opens with its first seconded nomination: the vampires' victim dies now.
      int before = alive.size();
      for (RoleModule m : modules) m.entered(this, Phase.DAY_VOTE);
      if (alive.size() != before) {
        checkWinner();
        if (phase == Phase.FINISHED) return "The game is over.";
        if (!alive.contains(target)) {
          nominee = null;
          nominator = null;
          return "The nomination falls with its nominee.";
        }
        if (!hunters.isEmpty()) {
          resume = Phase.DEFENSE;
          to(Phase.HUNTER_SHOT);
          return "You second the nomination.";
        }
      }
    }
    to(Phase.DEFENSE);
    return "You second the nomination.";
  }

  /** The thumbs are counted: more than half of the living down eliminates the accused. */
  private void closeTrial() {
    UUID accused = nominee;
    long down = thumbs.values().stream().filter(b -> b).count();
    long up = thumbs.size() - down;
    boolean condemned = accused != null && alive.contains(accused) && down * 2 > alive.size();
    journal.log(
        "TRIAL",
        who(accused) + ": " + down + " down, " + up + " up of " + alive.size() + " living"
            + (condemned ? " · condemned" : " · spared"));
    notifyLiving(
        "Thumbs: " + down + " down, " + up + " up. " + accused
            + (condemned ? " is condemned." : " is spared."));
    nominee = null;
    nominator = null;
    thumbs.clear();
    if (condemned) {
      condemn(accused);
      return;
    }
    dayLeft = Math.max(0, dayLeft - DEFENSE_SECONDS - vote);
    if (dayLeft < 5) endDay("The day is over and nobody was condemned");
    else to(Phase.DISCUSSION);
  }

  /** The day ends with no verdict (nominated voting ran out of time). */
  private void endDay(String why) {
    journal.log("VERDICT", why);
    verdict = new Verdict(null);
    nominee = null;
    nominator = null;
    thumbs.clear();
    continueWith(null);
  }

  /** A secret ballot is opened: everybody learns who voted for whom, after the fact. */
  private void revealBallots() {
    List<String> lines = new ArrayList<>();
    for (var vote : ballots.entrySet()) lines.add(vote.getKey() + " → " + vote.getValue());
    if (lines.isEmpty()) return;
    String text = "The ballots are opened: " + String.join(", ", lines) + ".";
    for (UUID p : roles.keySet()) notify(p, text);
  }

  // ------------------------------------------------------------------ variants

  private void dealAmulet() {
    List<UUID> pool =
        roles.entrySet().stream()
            .filter(e -> !wolf(e.getValue()) && !e.getValue().equals(Role.VAMPIRE))
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(ArrayList::new));
    if (pool.isEmpty()) return;
    amuletHolder = pool.get(random.nextInt(pool.size()));
    amuletSeen.add(amuletHolder);
    notify(
        amuletHolder,
        "You hold the Amulet of Protection: it saves you once from any death. Each night you may pass it on.");
    journal.log("AMULET", who(amuletHolder) + " holds the amulet");
  }

  /** The holder passes the amulet on; a player who already held it destroys it. */
  private String passAmulet(UUID voter, UUID target) {
    if (!voter.equals(amuletHolder))
      throw new IllegalArgumentException("You cannot pick a player during this phase");
    amuletSaved = false;
    if (amuletSeen.contains(target)) {
      journal.log("AMULET", who(voter) + " destroys the amulet");
      amuletHolder = null;
      next();
      return "That player already held the amulet: it crumbles to dust.";
    }
    amuletSeen.add(target);
    amuletHolder = target;
    notify(target, "A tap on your shoulder: you hold the Amulet of Protection now.");
    journal.log("AMULET", who(voter) + " passes the amulet to " + who(target));
    next();
    return "You pass the amulet on.";
  }

  /** The dawn's dice (a variant): two six-sided dice choose a twist for the day. */
  private void rollDice() {
    secretToday = false;
    lightningToday = false;
    if (!settings.dice() || phase == Phase.FINISHED) return;
    int roll = 2 + random.nextInt(6) + random.nextInt(6);
    if (roll == 9 && lastDice != 0) roll = lastDice;
    lastDice = roll;
    String text = null;
    switch (roll) {
      case 2 -> {
        diceInfectNext = true;
        text = "The next night, the wolves' victim becomes a werewolf.";
      }
      case 3 -> {
        lightningToday = true;
        text = "Lightning vote: the first player to gather three ballots is judged at once.";
      }
      case 4 -> {
        lynchesLeft++;
        text = "Trouble: the village eliminates two players today.";
      }
      case 5 -> {
        text =
            seerLast != null && alive.contains(seerLast)
                ? "The seer last looked at " + seerLast + "."
                : "The mist hides the seer's last look.";
      }
      case 10 -> {
        secretToday = true;
        text = "Silent ballots: nobody sees the running count today.";
      }
      case 11 -> {
        muted.addAll(alive);
        text = "A silent day: nobody may speak. Only the vote is left.";
      }
      case 12 -> {
        defangNext = true;
        text = "The next night, the wolves' victim loses their power instead of dying.";
      }
      default -> {}
    }
    journal.log("DICE", "Rolled " + roll + (text == null ? " (a plain day)" : ": " + text));
    if (text != null) notifyLiving("The dice fall: " + text);
  }

  /** The condemned (or nobody) faces the verdict; another round may follow, else the night. */
  private void finishLynch(UUID chosen) {
    pendingLynch = null;
    UUID dead = null;
    String counted = tally();
    if (chosen != null
        && resolveKill(new Kill(chosen, "VOTE", "eliminated by the village vote"))) dead = chosen;
    verdict = new Verdict(dead);
    journal.log(
        "VERDICT",
        (dead == null
                ? chosen == null ? "Nobody is eliminated (no majority)" : who(chosen) + " is spared"
                : who(dead) + " is eliminated")
            + " · "
            + counted);
    ballots.clear();
    abstained.clear();
    candidates = null;
    voteRound = 0;
    if (lynchesLeft > 0 && alive.size() > 2) {
      // The troublemaker's day: the village votes again at once.
      lynchesLeft--;
      lynchRound++;
      journal.log("TROUBLE", "Another vote: the village must eliminate a second player");
      for (UUID p : alive) notify(p, "Trouble! The village must eliminate a second player.");
      Phase again = nominating() ? Phase.DISCUSSION : Phase.DAY_VOTE;
      dayLeft = Math.max(30, (discussion + vote) / 2);
      if (hunters.isEmpty()) {
        if (phase == again) done.clear();
        else to(again);
        checkWinner();
      } else {
        resume = again;
        to(Phase.HUNTER_SHOT);
      }
      return;
    }
    lynchesLeft = 0;
    continueWith(null);
  }

  /** Moves to the next phase, and journals the move. */
  private void to(Phase next) {
    if (next == phase) return;
    Phase was = phase;
    for (RoleModule m : modules) m.closed(this, was);
    phase = next;
    done.clear();
    aims.clear(); // A head picked in one phase is never carried into the next one.
    journal.phase(was.name(), next.name(), where());
    int before = alive.size();
    for (RoleModule m : modules) m.entered(this, next);
    if (alive.size() != before && next != Phase.FINISHED && next != Phase.HUNTER_SHOT) {
      // A phase that opens with a death (the vampires' victim) lets hunters shoot, then ends the game if need be.
      if (!hunters.isEmpty()) {
        resume = next;
        to(Phase.HUNTER_SHOT);
      } else checkWinner();
    }
  }

  private String where() {
    return (night() ? "Night " : "Day ") + round();
  }

  private void end(String side, String why) {
    winner = side;
    to(Phase.FINISHED);
    journal.log("GAME", "Winner: " + side + " (" + why + ")");
    journal.log("GAME", "Winners: " + names(winners()) + " · survivors: " + names(alive));
    journal.log("GAME", "Final roles: " + cast());
  }

  /** A player as the journal shows them: name, card, and a cross once dead. */
  private String who(UUID p) {
    if (p == null) return "nobody";
    Role role = roles.get(p);
    return journal.name(p)
        + "["
        + (role == null ? "?" : role.name())
        + (lovers.containsKey(p) ? " ♥" : "")
        + "]"
        + (alive.contains(p) ? "" : "†");
  }

  private String names(Collection<UUID> players) {
    if (players.isEmpty()) return "none";
    List<String> out = new ArrayList<>();
    for (UUID p : players) out.add(who(p));
    return String.join(", ", out);
  }

  private String cast() {
    return names(roles.keySet());
  }

  /** Every current ballot and abstention, for the journal. */
  private String tally() {
    List<String> out = new ArrayList<>();
    ballots.forEach((voter, target) -> out.add(journal.name(voter) + "→" + journal.name(target)));
    if (out.isEmpty()) out.add("no ballots");
    if (!abstained.isEmpty()) {
      List<String> skipped = new ArrayList<>();
      for (UUID p : abstained) skipped.add(journal.name(p));
      out.add("skipped: " + String.join(", ", skipped));
    }
    return String.join(", ", out);
  }

  /**
   * Runs a player's action and journals it: what they tried, then what the game answered or why it
   * refused. Actions an action triggers (Cupid's BIND selects both lovers) are not repeated.
   */
  private String act(UUID actor, String what, java.util.function.Supplier<String> action) {
    boolean outer = acting++ == 0;
    Phase during = phase;
    String attempt = who(actor) + " " + what + " during " + during;
    try {
      // Logged before the action runs, so the phases and deaths it causes follow it.
      if (outer) journal.attempt(attempt);
      String reply = action.get();
      if (outer) journal.log("RESULT", reply);
      return reply;
    } catch (IllegalArgumentException refused) {
      if (outer)
        journal.refused(journal.name(actor) + during, attempt + ": " + refused.getMessage());
      throw refused;
    } finally {
      acting--;
    }
  }


  /** Participants entitled to private phase instructions; the pending hunter may be dead. */
  public synchronized Set<UUID> actors() {
    if (phase == Phase.HUNTER_SHOT) {
      UUID hunter = hunters.peekFirst();
      return hunter == null ? Set.of() : Set.of(hunter);
    }
    if (phase == Phase.FINISHED) return Set.of();
    if (phase == Phase.DEFENSE) return nominee == null ? Set.of() : Set.of(nominee);
    if (phase == Phase.TRIAL) return Set.copyOf(jurors());
    if (phase == Phase.DISCUSSION || phase == Phase.DAY_VOTE) {
      Set<UUID> day = new HashSet<>(alive);
      day.removeAll(exiled);
      return Set.copyOf(day);
    }
    return Set.copyOf(callActors(phase));
  }

  /** Nights so far: night N is followed by day N. */
  public synchronized int round() {
    return night() ? dawns + 1 : Math.max(1, dawns);
  }

  /** The player's current ballot (day vote, or the wolves' hunt), or null. */
  public synchronized UUID ballot(UUID voter) {
    return ballots.get(voter);
  }

  public synchronized boolean abstained(UUID voter) {
    return abstained.contains(voter);
  }

  /** The other living members of the hunting pack (wolves, or vampires), known only to the pack. */
  public synchronized List<UUID> pack(UUID member) {
    Role mine = roles.get(member);
    if (wolf(mine))
      return alive.stream().filter(p -> !p.equals(member) && wolf(roles.get(p))).toList();
    if (mine == Role.VAMPIRE)
      return alive.stream().filter(p -> !p.equals(member) && roles.get(p) == Role.VAMPIRE).toList();
    return List.of();
  }

  /** What this seer has seen so far, oldest first (player -> role). */
  public synchronized Map<UUID, String> visions(UUID seer) {
    Map<UUID, String> out = new LinkedHashMap<>();
    seen.getOrDefault(seer, Map.of()).forEach((p, r) -> out.put(p, r.name()));
    return out;
  }

  /** Whether this living player wakes with the pack at night. */
  public synchronized boolean packMember(UUID p) {
    return alive.contains(p) && roles.get(p) != null && wolf(roles.get(p));
  }

  /** The table order, as set by {@link #ring}: every player of the game, dead or alive. */
  public synchronized List<UUID> seatOrder() {
    return ring;
  }

  /** Whether the player is bound by Cupid, and not yet dead. */
  public synchronized boolean inLove(UUID p) {
    return alive.contains(p) && lovers.containsKey(p);
  }

  public synchronized UUID lover(UUID p) {
    return lovers.get(p);
  }

  public synchronized boolean infectLeft(UUID p) {
    return roles.get(p) == Role.INFECTOR && !infectUsed.contains(p);
  }

  /** Sets the table order (seat order), which decides who sits next to whom. */
  public synchronized void ring(List<UUID> order) {
    ring = List.copyOf(order);
  }

  /** At dawn the bear tamer's bear growls when a living wolf sits next to him. */
  private void growl() {
    for (UUID tamer : alive) {
      if (roles.get(tamer) != Role.BEAR_TAMER) continue;
      int at = ring.indexOf(tamer), n = ring.size();
      if (at < 0 || n < 3) continue;
      boolean wolfNear = false;
      for (int dir : new int[] {-1, 1})
        for (int step = 1; step < n; step++) {
          UUID next = ring.get(Math.floorMod(at + dir * step, n));
          if (!alive.contains(next)) continue;
          wolfNear |= wolf(roles.get(next));
          break;
        }
      if (!wolfNear) continue;
      journal.log("BEAR", who(tamer) + "'s bear growls: a wolf sits next to the bear tamer");
      for (UUID p : alive) notify(p, "The bear growls! A wolf sits next to the bear tamer.");
    }
  }

  /** The wolves' victim, shown to the witch during her turn while she holds the life potion. */
  public synchronized UUID victim(UUID witch) {
    return phase == Phase.WITCH
            && roles.get(witch) == Role.WITCH
            && !healUsed.contains(witch)
            && nightVictim != null
            && alive.contains(nightVictim)
        ? nightVictim
        : null;
  }

  public synchronized boolean lifePotion(UUID witch) {
    return roles.get(witch) == Role.WITCH && !healUsed.contains(witch);
  }

  public synchronized boolean deathPotion(UUID witch) {
    return roles.get(witch) == Role.WITCH && !poisonUsed.contains(witch);
  }

  /** Whom this guard protected last night, who cannot be protected again tonight. */
  public synchronized UUID lastProtected(UUID guard) {
    return lastGuard.get(guard);
  }

  /** A counted day vote: whom the village eliminated, or null when no one had a majority. */
  public record Verdict(UUID lynched) {}

  /**
   * The day vote counted since the last call, or null when the day vote ended some other way (the
   * time limit, a departure). Read once, so the village hears each verdict a single time.
   */
  public synchronized Verdict takeVerdict() {
    Verdict taken = verdict;
    verdict = null;
    return taken;
  }

  /** This player's lover, known only to the two of them. */
  public synchronized UUID partner(UUID p) {
    return lovers.get(p);
  }

  /** How many of each role were dealt, in enum order. */
  public synchronized Map<String, Integer> composition() {
    Map<String, Integer> out = new LinkedHashMap<>();
    for (Role role : Role.values()) {
      int n = (int) roles.values().stream().filter(role::equals).count();
      if (n > 0) out.put(role.name(), n);
    }
    return out;
  }

  /**
   * True while the village sleeps. A hunter's shot belongs to the phase it interrupts: taken at
   * dawn or during the day it happens in daylight, during the night it stays secret.
   */
  public synchronized boolean night() {
    return switch (phase) {
      case DISCUSSION, DAY_VOTE, FINISHED, MARTYR, DEFENSE, TRIAL -> false;
      case HUNTER_SHOT -> resume != Phase.DISCUSSION && resume != Phase.DAY_VOTE;
      default -> true;
    };
  }

  public synchronized boolean sleeping(UUID p) {
    if (!alive.contains(p)) return false;
    return switch (phase) {
      case DISCUSSION, DAY_VOTE, FINISHED, MARTYR, DEFENSE, TRIAL -> false;
      case HUNTER_SHOT -> night() && !Objects.equals(hunters.peekFirst(), p);
      default -> !callActors(phase).contains(p);
    };
  }

  public synchronized Set<UUID> winners() {
    Set<UUID> result = new HashSet<>();
    if (winner.isEmpty()) return Set.of();
    for (UUID p : roles.keySet())
      if (winner.equals("LOVERS")
          ? alive.contains(p) && lovers.containsKey(p)
          : winner.equals("VILLAGE")
              ? team(roles.get(p)).equals("village")
              : winner.equals("WEREWOLVES") && team(roles.get(p)).equals("wolves")) result.add(p);
    for (RoleModule m : modules) {
      result.addAll(m.members(this, winner));
      result.addAll(m.sideWinners(this));
    }
    return Set.copyOf(result);
  }

  private void checkWinner() {
    if (phase == Phase.FINISHED || !hunters.isEmpty()) return;
    if (alive.isEmpty()) {
      end("DRAW", "nobody is left alive");
      return;
    }
    if (alive.size() == 2) {
      UUID a = alive.iterator().next(), b = lovers.get(a);
      if (b != null
          && alive.contains(b)
          && !team(roles.get(a)).equals(team(roles.get(b)))) {
        end("LOVERS", "the last two players are on different sides and in love");
        return;
      }
    }
    for (RoleModule m : modules) {
      String won = m.teamWin(this);
      if (won != null && !won.isEmpty()) {
        end(won, "its team is all that is left");
        return;
      }
    }
    // Wolves that win with the pack, the lone wolf, the wolves' helpers, the vampires.
    long pack = alive.stream().filter(p -> wolf(roles.get(p)) && roles.get(p) != Role.LONE_WOLF).count();
    long lone = alive.stream().filter(p -> roles.get(p) == Role.LONE_WOLF).count();
    long helpers = alive.stream().filter(p -> RoleInfo.of(roles.get(p).name()).team().equals("wolves") && !wolf(roles.get(p))).count();
    long vampires = alive.stream().filter(p -> roles.get(p) == Role.VAMPIRE).count();
    long others = alive.size() - pack - lone - helpers - vampires;
    if (pack + lone == 0 && vampires == 0) end("VILLAGE", "every wolf is dead");
    else if (pack > 0 && vampires == 0 && pack + lone >= others + 0)
      // As many wolves as everyone else together: the village can no longer outvote them.
      end("WEREWOLVES", "the wolves are as many as the rest of the village");
  }

  // ------------------------------------------------------------------ module API
  //
  // What the role modules may call. Every method here runs inside the game's lock (a module is
  // only ever called from a game method), so none of them synchronizes.

  /** The role a player holds, whatever they believe; null for a stranger. */
  Role roleOf(UUID p) {
    return roles.get(p);
  }

  /** Gives a player another role (a conversion, a copy); the modules are not told. */
  void setRole(UUID p, Role role) {
    Role was = roles.put(p, role);
    journal.log("ROLE", who(p) + " was " + was + ", is now " + role);
  }

  /** The living players. Read it, never change it. */
  Set<UUID> living() {
    return alive;
  }

  /** Every player of the game, living or dead, in seat order. */
  List<UUID> table() {
    return ring;
  }

  /** The nearest living player from {@code p} going round the table in {@code dir} (+1 or -1). */
  UUID neighbour(UUID p, int dir) {
    int at = ring.indexOf(p), n = ring.size();
    if (at < 0 || n < 2) return null;
    for (int step = 1; step < n; step++) {
      UUID next = ring.get(Math.floorMod(at + dir * step, n));
      if (alive.contains(next)) return next;
    }
    return null;
  }

  /** The number of the night in progress (1 on the first night). */
  int nightNumber() {
    return dawns + 1;
  }

  boolean hasLiving(Role role) {
    return alive.stream().anyMatch(p -> roles.get(p) == role);
  }

  /** A private message for a player, delivered by the plugin (a player's id inside it is shown as their name). */
  void notify(UUID p, String text) {
    notices.merge(p, text, (old, more) -> old + "\n" + more);
  }

  void notifyLiving(String text) {
    for (UUID p : alive) notify(p, text);
  }

  void log(String tag, String text) {
    journal.log(tag, text);
  }

  /** A player's name and card for the journal. */
  String describe(UUID p) {
    return who(p);
  }

  Random random() {
    return random;
  }

  /** Runs a death through every protection. Returns true when the victim died. */
  boolean kill(UUID victim, String source, String cause) {
    return resolveKill(new Kill(victim, source, cause));
  }

  /** A death nothing can prevent (a ghost's fate, old age). */
  void slay(UUID victim, String cause) {
    sources.put(victim, "OTHER");
    eliminate(victim, true, cause);
  }

  /** The player dies at the next dawn, whatever the night brings (a tough guy's delayed death). */
  void doom(UUID victim, String cause) {
    doomed.put(victim, cause);
  }

  /** The hunter keeps their shot out of the queue (the vampire hunter has no shot). */
  void cancelShot(UUID hunter) {
    hunters.remove(hunter);
  }

  /** Sends a player away for the day: no chat, no vote, nobody can vote for them or shoot them. */
  void exile(UUID p) {
    exiled.add(p);
    ballots.remove(p);
  }

  /** Silences a player for the day: they may vote, not chat. */
  void mute(UUID p) {
    muted.add(p);
  }

  /** The wolves cannot kill during the next night (the diseased was eaten). */
  void callOffNextHunt() {
    huntCalledOffNext = true;
  }

  /** The village will eliminate {@code n} more players today than usual. */
  void extraLynches(int n) {
    lynchesLeft += n;
  }

  /** The condemned player a martyr may stand in for, or null outside the martyr's call. */
  UUID condemned() {
    return phase == Phase.MARTYR ? pendingLynch : null;
  }

  /** The martyr takes the condemned player's place. */
  void substituteCondemned(UUID martyr) {
    pendingLynch = martyr;
  }

  /** The victim a pack chose in a call other than the wolves' hunt (the vampires'). */
  UUID packVictim(Phase call) {
    return packVictims.get(call);
  }

  /** This actor's call is over; the phase ends once every called actor is. */
  void done(UUID actor) {
    done.add(actor);
    Set<UUID> called = callActors(phase);
    if (phase != Phase.NIGHT && phase != Phase.VAMPIRE && done.containsAll(called)) next();
  }

  /** The player's current silence, away status, for the plugin. */
  public synchronized boolean exiled(UUID p) {
    return exiled.contains(p);
  }

  /** True when a module takes this chat message instead of the table (a ghost's single letter). */
  public synchronized boolean chat(UUID speaker, String text) {
    for (RoleModule m : modules) if (m.chat(this, speaker, text)) return true;
    return false;
  }

  /** A practice bot's turn in a call a module owns. */
  public synchronized void botAct(UUID bot, Random botRandom) {
    for (RoleModule m : modules)
      if (m.phases().contains(phase)) {
        m.bot(this, phase, bot, botRandom);
        return;
      }
  }

  /** True while the player may not chat: silenced for the day, or away. */
  public synchronized boolean silenced(UUID p) {
    if (muted.contains(p) || exiled.contains(p)) return true;
    for (RoleModule m : modules) if (m.silenced(this, p)) return true;
    return false;
  }

  /** The tied players of a revote, or null outside one. */
  public synchronized List<UUID> candidates() {
    return candidates == null ? null : List.copyOf(candidates);
  }
}
