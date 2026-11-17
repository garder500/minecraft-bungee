package win.vampyuyu.valoblock;

import java.util.UUID;

/**
 * The ability charges of one player, no Bukkit: slots 4 and 5 are bought with Shards (buy phase only, up
 * to {@code maxCharges}), slot 6 refills every round and gains a charge every {@code rechargeKills} kills,
 * slot 7 needs ultimate points (kills, orbs and a point per round, capped at its cost). Bought charges and
 * points stay across rounds; bought charges are lost where loadouts reset (pistol rounds) and for the dead.
 */
public final class AgentKit {
  public enum Result {
    OK(""),
    NOT_BUY_PHASE("You can only buy abilities in the buy phase"),
    TOO_POOR("Not enough Shards"),
    FULL("You carry the most you can"),
    NOT_FOR_SALE("That ability is not sold"),
    NONE_LEFT("No charge left"),
    NOT_READY("Not enough ultimate points"),
    NOTHING_TO_SELL("Nothing bought this round to sell back");

    public final String text;

    Result(String text) {
      this.text = text;
    }
  }

  private final VbAgent agent;
  private final VbAgents.Ultimate rules;
  private final int[] charges = new int[8]; // by slot (4-7 used)
  private final int[] boughtThisPhase = new int[8];
  private int ultPoints;
  private int killsSinceRecharge;

  public AgentKit(VbAgent agent, VbAgents.Ultimate rules) {
    this.agent = agent;
    this.rules = rules;
    charges[6] = agent.ability(6).charges();
  }

  public VbAgent agent() {
    return agent;
  }

  public int charges(int slot) {
    return slot == 7 ? (ultReady() ? 1 : 0) : charges[slot];
  }

  public int ultPoints() {
    return ultPoints;
  }

  public boolean ultReady() {
    return ultPoints >= agent.ability(7).ultPoints();
  }

  /** A new round: the signature refills, a point comes in; bought charges stay unless {@code keep} is false (pistol round). */
  public void roundStart(boolean keep) {
    if (!keep) {
      charges[4] = 0;
      charges[5] = 0;
    }
    charges[6] = agent.ability(6).charges();
    killsSinceRecharge = 0;
    java.util.Arrays.fill(boughtThisPhase, 0);
    addPoints(rules.perRound());
  }

  public Result buy(VbMatch m, UUID player, int slot) {
    VbAbility a = agent.ability(slot);
    if (a == null || a.kind() != VbAbility.Kind.BUY) return Result.NOT_FOR_SALE;
    if (m.vbPhase() != VbPhase.BUY) return Result.NOT_BUY_PHASE;
    if (charges[slot] >= a.maxCharges()) return Result.FULL;
    if (!m.buy(player, a.cost())) return Result.TOO_POOR;
    charges[slot]++;
    boughtThisPhase[slot]++;
    return Result.OK;
  }

  /** Sells back a charge bought in this very buy phase, for the full price. */
  public Result sell(VbMatch m, UUID player, int slot) {
    VbAbility a = agent.ability(slot);
    if (a == null || a.kind() != VbAbility.Kind.BUY) return Result.NOT_FOR_SALE;
    if (m.vbPhase() != VbPhase.BUY) return Result.NOT_BUY_PHASE;
    if (boughtThisPhase[slot] <= 0 || charges[slot] <= 0) return Result.NOTHING_TO_SELL;
    m.refund(player, a.cost());
    charges[slot]--;
    boughtThisPhase[slot]--;
    return Result.OK;
  }

  /** Whether the ability could be used now (charge or points). */
  public Result canUse(int slot) {
    if (slot < 4 || slot > 7) return Result.NOT_FOR_SALE;
    if (slot == 7) return ultReady() ? Result.OK : Result.NOT_READY;
    return charges[slot] > 0 ? Result.OK : Result.NONE_LEFT;
  }

  /** Spends a charge (or the ultimate's points). */
  public Result use(int slot) {
    Result r = canUse(slot);
    if (r != Result.OK) return r;
    if (slot == 7) ultPoints -= agent.ability(7).ultPoints();
    else charges[slot]--;
    return Result.OK;
  }

  /** Gives a charge back (an ability that could not be placed). */
  public void refund(int slot) {
    if (slot == 7) ultPoints = Math.min(agent.ability(7).ultPoints(), ultPoints + agent.ability(7).ultPoints());
    else charges[slot] = Math.min(slot == 6 ? agent.ability(6).charges() : agent.ability(slot).maxCharges(), charges[slot] + 1);
  }

  /** The owner killed an enemy. */
  public void onKill() {
    addPoints(rules.perKill());
    int every = agent.ability(6).rechargeKills();
    if (every > 0 && ++killsSinceRecharge >= every) {
      killsSinceRecharge = 0;
      charges[6] = Math.min(agent.ability(6).charges(), charges[6] + 1);
    }
  }

  /** The owner picked an ultimate orb up. */
  public void onOrb() {
    addPoints(rules.perOrb());
  }

  public void addPoints(int n) {
    ultPoints = Math.min(agent.ability(7).ultPoints(), ultPoints + Math.max(0, n));
  }
}
