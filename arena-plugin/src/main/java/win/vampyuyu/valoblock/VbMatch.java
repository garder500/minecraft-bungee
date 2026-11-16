package win.vampyuyu.valoblock;

import java.util.*;
import win.vampyuyu.arena.GamePreset;

/**
 * One ValoBlock match as pure state: teams, phases, health, Shards, the Rift Seed and the score. No
 * Bukkit. The arena engine owns the phase clock ({@link #seconds()} / {@link #advance()}); the
 * game services report what happens in the world (damage, plant and seal holds) through the methods
 * below, which are synchronized (the clock thread and the server thread both call in).
 *
 * <p>Squads 0 and 1 are the two fixed teams; their side (Seedbearers or Sealers) comes from
 * {@link VbScore#sideOf}.
 */
public final class VbMatch implements GamePreset {
  public enum Reason {
    ELIMINATION,
    SEALED,
    FUSE,
    TIME,
    FORCED
  }

  public record RoundResult(int round, int winnerSquad, VbSide winnerSide, Reason reason) {}

  /** Mutable per-player state. */
  static final class Member {
    final UUID id;
    final int squad;
    final boolean bot;
    String agent = "";
    int hp, shield, credits, kills;
    boolean alive = true, left, survivedLast;

    Member(UUID id, int squad, boolean bot) {
      this.id = id;
      this.squad = squad;
      this.bot = bot;
    }
  }

  private final VbRules rules;
  private final Random random;
  private final Map<UUID, Member> members = new LinkedHashMap<>();
  private final VbScore score;
  private final int[] lossStreak = new int[2];
  private final List<RoundResult> results = new ArrayList<>();
  private VbEvents events = VbEvents.NONE;

  private VbPhase phase = VbPhase.AGENT_SELECT;
  private int round;
  private UUID seedCarrier;
  private HoldTimer plantTimer, sealTimer;
  private VbSite plantedSite, plantingSite;
  private String winner = "";
  private boolean botsFilled;

  public VbMatch(VbRules rules, Collection<UUID> humans, Random random) {
    this.rules = rules;
    this.random = random;
    this.score = new VbScore(rules);
    if (humans.size() > rules.teamSize() * 2)
      throw new IllegalArgumentException("Too many players for " + rules.teamSize() + "v" + rules.teamSize());
    List<UUID> order = new ArrayList<>(humans);
    Collections.shuffle(order, random);
    int i = 0;
    for (UUID id : order) members.put(id, new Member(id, i++ % 2, false));
    resetTimers();
  }

  private void resetTimers() {
    plantTimer = new HoldTimer(rules.plantHoldSeconds(), 0);
    sealTimer = new HoldTimer(rules.sealSeconds(), rules.sealCheckpointSeconds());
  }

  public synchronized void setEvents(VbEvents events) {
    this.events = events == null ? VbEvents.NONE : events;
  }

  // -------------------------------------------------------------- queries

  public VbRules rules() {
    return rules;
  }

  public synchronized int round() {
    return round;
  }

  public synchronized VbScore score() {
    return score;
  }

  public synchronized int squadOf(UUID id) {
    return member(id).squad;
  }

  public synchronized VbSide side(UUID id) {
    return score.sideOf(member(id).squad, Math.max(1, round));
  }

  /** Members of a squad (humans and bots). */
  public synchronized List<UUID> squad(int squad) {
    return members.values().stream().filter(m -> m.squad == squad).map(m -> m.id).toList();
  }

  public synchronized Set<UUID> participants() {
    return Set.copyOf(members.keySet());
  }

  public synchronized Set<UUID> bots() {
    Set<UUID> out = new HashSet<>();
    for (Member m : members.values()) if (m.bot) out.add(m.id);
    return out;
  }

  public synchronized boolean isBot(UUID id) {
    return member(id).bot;
  }

  public synchronized int health(UUID id) {
    return member(id).hp;
  }

  public synchronized int shield(UUID id) {
    return member(id).shield;
  }

  public synchronized int credits(UUID id) {
    return member(id).credits;
  }

  public synchronized int kills(UUID id) {
    return member(id).kills;
  }

  public synchronized String agent(UUID id) {
    return member(id).agent;
  }

  /** A survivor of the last round keeps its guns, except at a pistol round (everyone restarts). */
  public synchronized boolean keepsLoadout(UUID id) {
    return member(id).survivedLast && !score.pistolRound(round);
  }

  public synchronized UUID seedCarrier() {
    return seedCarrier;
  }

  public synchronized VbSite plantedSite() {
    return plantedSite;
  }

  public synchronized double plantProgress() {
    return plantTimer.progress();
  }

  public synchronized double sealProgress() {
    return sealTimer.progress();
  }

  public synchronized List<RoundResult> results() {
    return List.copyOf(results);
  }

  public synchronized int lossStreak(int squad) {
    return lossStreak[squad];
  }

  private Member member(UUID id) {
    Member m = members.get(id);
    if (m == null) throw new IllegalArgumentException("Not in this match");
    return m;
  }

  private int living(VbSide side) {
    int n = 0;
    for (Member m : members.values()) if (m.alive && !m.left && sideOf(m) == side) n++;
    return n;
  }

  private VbSide sideOf(Member m) {
    return score.sideOf(m.squad, Math.max(1, round));
  }

  // ------------------------------------------------------- GamePreset

  @Override
  public synchronized String phase() {
    return phase.name();
  }

  public synchronized VbPhase vbPhase() {
    return phase;
  }

  @Override
  public synchronized String role(UUID player) {
    Member m = members.get(player);
    return m == null ? "" : sideOf(m).name();
  }

  @Override
  public synchronized Set<UUID> alive() {
    Set<UUID> out = new HashSet<>();
    for (Member m : members.values()) if (m.alive && !m.left) out.add(m.id);
    return out;
  }

  @Override
  public Map<UUID, Integer> votes(UUID viewer) {
    return Map.of();
  }

  @Override
  public String select(UUID voter, UUID target) {
    throw new IllegalArgumentException("There is no voting in ValoBlock");
  }

  @Override
  public synchronized void remove(UUID player) {
    Member m = members.get(player);
    if (m == null || m.left) return;
    m.left = true;
    if (m.alive && (phase == VbPhase.COMBAT || phase == VbPhase.PLANTED)) {
      m.alive = false;
      m.hp = 0;
      dropSeedIf(player);
      checkElimination();
    } else m.alive = false;
    // A whole squad gone: the other one takes the match.
    for (int squad = 0; squad < 2; squad++) {
      final int s = squad;
      if (phase != VbPhase.END && members.values().stream().filter(x -> x.squad == s).allMatch(x -> x.left))
        endMatch("Team " + teamName(1 - s));
    }
  }

  @Override
  public synchronized void advance() {
    switch (phase) {
      case AGENT_SELECT -> {
        fillBots();
        startRound();
      }
      case BUY -> setPhase(VbPhase.COMBAT);
      case COMBAT -> endRound(score.squadOf(VbSide.SEALERS, round), Reason.TIME);
      case PLANTED -> endRound(score.squadOf(VbSide.SEEDBEARERS, round), Reason.FUSE);
      case ROUND_END -> {
        int w = score.matchWinner();
        if (w >= 0) endMatch("Team " + teamName(w));
        else startRound();
      }
      default -> {}
    }
  }

  @Override
  public synchronized String winner() {
    return winner;
  }

  @Override
  public synchronized Set<UUID> winners() {
    if (winner.isEmpty() || winner.equals("DRAW")) return Set.of();
    int squad = winner.equals("Team " + teamName(0)) ? 0 : 1;
    Set<UUID> out = new HashSet<>();
    for (Member m : members.values()) if (m.squad == squad && !m.bot) out.add(m.id);
    return out;
  }

  @Override
  public synchronized int seconds() {
    return switch (phase) {
      case AGENT_SELECT -> rules.agentSelectSeconds();
      case BUY -> rules.buySeconds();
      case COMBAT -> rules.combatSeconds();
      case PLANTED -> rules.fuseSeconds();
      case ROUND_END -> rules.roundEndSeconds();
      default -> 3600;
    };
  }

  /** The arena's time ran out: the squad ahead wins, a tie is a draw. */
  @Override
  public synchronized void finish() {
    if (phase == VbPhase.END) return;
    int a = score.wins(0), b = score.wins(1);
    endMatch(a == b ? "DRAW" : "Team " + teamName(a > b ? 0 : 1));
  }

  @Override
  public synchronized String help(UUID player) {
    return switch (phase) {
      case AGENT_SELECT -> "Pick and lock in an agent.";
      case BUY -> "Spend your Shards in the armory.";
      case COMBAT -> sideOf(member(player)) == VbSide.SEEDBEARERS
          ? "Plant the Rift Seed on a site, or eliminate the Sealers."
          : "Hold the sites, or eliminate the Seedbearers.";
      case PLANTED -> sideOf(member(player)) == VbSide.SEEDBEARERS
          ? "Guard the Seed until it opens."
          : "Hold right-click on the Seed for 7 seconds to seal it.";
      default -> "";
    };
  }

  @Override
  public synchronized Map<UUID, Integer> scores() {
    Map<UUID, Integer> out = new HashMap<>();
    for (Member m : members.values()) out.put(m.id, m.kills);
    return out;
  }

  /** "A" or "B": squad names do not change when the sides swap. */
  public static String teamName(int squad) {
    return squad == 0 ? "A" : "B";
  }

  // ------------------------------------------------------- agent select

  public synchronized void lockAgent(UUID player, String agent) {
    if (phase != VbPhase.AGENT_SELECT) throw new IllegalStateException("Agent select is over");
    Member m = member(player);
    for (Member o : members.values())
      if (o != m && o.squad == m.squad && agent.equals(o.agent))
        throw new IllegalArgumentException("That agent is already taken in your team");
    m.agent = agent;
  }

  /** Tops each squad up to the team size with bots (names and bodies are the BotService's job). */
  private void fillBots() {
    if (botsFilled) return;
    botsFilled = true;
    for (int squad = 0; squad < 2; squad++) {
      final int s = squad;
      long have = members.values().stream().filter(x -> x.squad == s).count();
      for (long i = have; i < rules.teamSize(); i++) {
        UUID id = new UUID(random.nextLong(), random.nextLong());
        members.put(id, new Member(id, squad, true));
      }
    }
  }

  // ------------------------------------------------------- economy

  /** Spends Shards in the buy phase; false (nothing spent) when not allowed or too poor. */
  public synchronized boolean buy(UUID player, int cost) {
    Member m = member(player);
    if (phase != VbPhase.BUY || !m.alive || cost < 0 || m.credits < cost) return false;
    m.credits -= cost;
    return true;
  }

  /** Sells something back during the buy phase. */
  public synchronized boolean refund(UUID player, int amount) {
    Member m = member(player);
    if (phase != VbPhase.BUY || amount < 0) return false;
    m.credits = Math.min(rules.economy().cap(), m.credits + amount);
    return true;
  }

  /** Buys a shield of one of the configured values (25 or 50); false when not allowed or not better than the current one. */
  public synchronized boolean buyShield(UUID player, int value, int cost) {
    Member m = member(player);
    if (!rules.shields().contains(value) || m.shield >= value) return false;
    if (!buy(player, cost)) return false;
    m.shield = value;
    return true;
  }

  private void pay(Member m, int amount) {
    m.credits = Math.min(rules.economy().cap(), m.credits + amount);
  }

  // ------------------------------------------------------- combat

  /**
   * Damage during combat. Friendly fire is ignored (self-damage counts). Shield absorbs first, the
   * rest goes to health. Returns true when the victim died.
   */
  public synchronized boolean damage(UUID victim, UUID attacker, int amount) {
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) return false;
    Member v = member(victim);
    if (!v.alive || amount <= 0) return false;
    Member a = attacker == null ? null : members.get(attacker);
    if (a != null && a != v && a.squad == v.squad) return false;
    int absorbed = Math.min(v.shield, amount);
    v.shield -= absorbed;
    v.hp -= amount - absorbed;
    if (v.hp > 0) return false;
    die(v, a);
    return true;
  }

  /** Kills a player outright (void, admin, ability). */
  public synchronized void kill(UUID victim, UUID killer) {
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) return;
    Member v = member(victim);
    if (v.alive) die(v, killer == null ? null : members.get(killer));
  }

  private void die(Member v, Member killer) {
    v.alive = false;
    v.hp = 0;
    if (killer != null && killer != v && killer.squad != v.squad) {
      killer.kills++;
      pay(killer, rules.economy().kill());
    }
    dropSeedIf(v.id);
    events.killed(this, v.id, killer == null ? null : killer.id);
    checkElimination();
  }

  private void dropSeedIf(UUID id) {
    if (id.equals(seedCarrier)) {
      seedCarrier = null;
      plantTimer.reset();
      plantingSite = null;
    }
  }

  /** A living Seedbearer picks the dropped Seed up (before it is planted). */
  public synchronized boolean pickUpSeed(UUID player) {
    Member m = member(player);
    if (phase != VbPhase.COMBAT || seedCarrier != null || !m.alive || sideOf(m) != VbSide.SEEDBEARERS) return false;
    seedCarrier = player;
    return true;
  }

  private void checkElimination() {
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) return;
    if (living(VbSide.SEALERS) == 0) {
      endRound(score.squadOf(VbSide.SEEDBEARERS, round), Reason.ELIMINATION);
    } else if (phase == VbPhase.COMBAT && living(VbSide.SEEDBEARERS) == 0) {
      endRound(score.squadOf(VbSide.SEALERS, round), Reason.ELIMINATION);
    }
    // Planted with every Seedbearer dead: the fuse keeps running, the Sealers must still seal.
  }

  // ------------------------------------------------------- plant and seal

  /**
   * One tick of the plant hold. {@code holder} is the player holding the plant button inside
   * {@code site}, or null when nobody is. Only the living Seed carrier can plant. Returns true when
   * the Seed has just been planted.
   */
  public synchronized boolean holdPlant(UUID holder, VbSite site, double dt) {
    if (phase != VbPhase.COMBAT) return false;
    Member m = holder == null ? null : members.get(holder);
    boolean valid = m != null && m.alive && holder.equals(seedCarrier) && site != null;
    if (valid && plantingSite != null && !plantingSite.id().equals(site.id())) plantTimer.reset();
    plantingSite = valid ? site : null;
    if (!plantTimer.tick(valid, dt)) return false;
    plantedSite = site;
    UUID planter = holder;
    seedCarrier = null;
    int squad = score.squadOf(VbSide.SEEDBEARERS, round);
    for (Member x : members.values()) if (x.squad == squad) pay(x, rules.economy().plantTeam());
    setPhase(VbPhase.PLANTED);
    events.seedPlanted(this, site, planter);
    // The Sealers may have been wiped out while the plant was finishing.
    checkElimination();
    return true;
  }

  /**
   * One tick of the seal hold. {@code holder} is the Sealer holding right-click on the Seed, or null
   * when nobody is. Returns true when the Seed has just been sealed (the round ends).
   */
  public synchronized boolean holdSeal(UUID holder, double dt) {
    if (phase != VbPhase.PLANTED) return false;
    Member m = holder == null ? null : members.get(holder);
    boolean valid = m != null && m.alive && sideOf(m) == VbSide.SEALERS;
    if (!sealTimer.tick(valid, dt)) return false;
    endRound(score.squadOf(VbSide.SEALERS, round), Reason.SEALED);
    return true;
  }

  // ------------------------------------------------------- rounds

  private void setPhase(VbPhase to) {
    VbPhase from = phase;
    phase = to;
    events.phaseChanged(this, from, to);
  }

  private void startRound() {
    round = score.nextRound();
    boolean pistol = score.pistolRound(round);
    if (pistol) lossStreak[0] = lossStreak[1] = 0;
    for (Member m : members.values()) {
      m.survivedLast = m.alive && !m.left && round > 1;
      m.alive = !m.left;
      m.hp = rules.maxHealth();
      if (!m.survivedLast || pistol) m.shield = 0;
      if (pistol) m.credits = rules.economy().pistolCredits();
      if (score.overtime(round)) m.credits = rules.economy().overtimeCredits();
    }
    seedCarrier = null;
    plantedSite = plantingSite = null;
    resetTimers();
    List<Member> bearers = new ArrayList<>();
    for (Member m : members.values()) if (m.alive && sideOf(m) == VbSide.SEEDBEARERS) bearers.add(m);
    if (!bearers.isEmpty()) seedCarrier = bearers.get(random.nextInt(bearers.size())).id;
    setPhase(VbPhase.BUY);
    events.roundStarted(this, round);
  }

  private void endRound(int squad, Reason reason) {
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) return;
    VbSide side = score.sideOf(squad, round);
    RoundResult result = new RoundResult(round, squad, side, reason);
    results.add(result);
    score.record(squad);
    int loser = 1 - squad;
    lossStreak[squad] = 0;
    lossStreak[loser]++;
    int bonus = rules.economy().lossBonus(lossStreak[loser]);
    for (Member m : members.values()) {
      if (m.squad == squad) pay(m, rules.economy().roundWon());
      else pay(m, bonus);
    }
    seedCarrier = null;
    setPhase(VbPhase.ROUND_END);
    events.roundEnded(this, result);
  }

  private void endMatch(String who) {
    winner = who;
    setPhase(VbPhase.END);
    events.matchEnded(this, who);
  }

  // ------------------------------------------------------- debug

  /** Ends the current round in favour of a squad (admin tests). */
  public synchronized void forceRound(int squad) {
    if (phase != VbPhase.COMBAT && phase != VbPhase.PLANTED) throw new IllegalStateException("No round in progress");
    endRound(squad, Reason.FORCED);
  }

  public synchronized String status() {
    return "Phase " + phase + ", round " + round + ", score A " + score.wins(0) + " - B " + score.wins(1)
        + (round > 0 ? " (A are " + score.sideOf(0, round).label() + ")" : "")
        + (phase == VbPhase.PLANTED && plantedSite != null ? ", Seed planted on " + plantedSite.name() : "")
        + (winner.isEmpty() ? "" : ", winner " + winner);
  }
}
