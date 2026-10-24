package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Rules of the cult leader and the cult's win. */
final class CultModule implements RoleModule {
  private static final String CULT = "CULT";

  /** The recruits in order; the leader joins at the front when he first recruits. */
  private final Set<UUID> members = new LinkedHashSet<>();

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.CULT_LEADER);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.CULT);
  }

  @Override
  public String select(WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    if (phase != WerewolfGame.Phase.CULT) return RoleModule.super.select(g, phase, actor, target);
    if (g.roleOf(actor) != WerewolfGame.Role.CULT_LEADER)
      throw new IllegalArgumentException("Only the cult leader recruits");
    if (target.equals(actor)) throw new IllegalArgumentException("You already lead the cult");
    if (!g.living().contains(target)) throw new IllegalArgumentException("Select a living player");
    if (members.contains(target))
      throw new IllegalArgumentException("That player is already in your cult");
    members.add(actor);
    members.add(target);
    g.log("CULT", g.describe(actor) + " recruits " + g.describe(target));
    g.done(actor);
    return "You recruited " + target + ". They do not know it.";
  }

  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (phase != WerewolfGame.Phase.CULT || g.roleOf(bot) != WerewolfGame.Role.CULT_LEADER) return;
    List<UUID> targets = new ArrayList<>();
    for (UUID p : g.table())
      if (g.living().contains(p) && !p.equals(bot) && !members.contains(p)) targets.add(p);
    if (!targets.isEmpty()) g.select(bot, targets.get(random.nextInt(targets.size())));
  }

  @Override
  public String teamWin(WerewolfGame g) {
    Set<UUID> cult = cult(g);
    boolean anyAlive = false;
    for (UUID p : g.living()) {
      if (!cult.contains(p)) return null;
      anyAlive = true;
    }
    return anyAlive ? CULT : null;
  }

  @Override
  public Set<UUID> members(WerewolfGame g, String winnerLabel) {
    return CULT.equals(winnerLabel) ? cult(g) : Set.of();
  }

  /** The recruits, living or dead, plus the leader whatever he did. */
  private Set<UUID> cult(WerewolfGame g) {
    Set<UUID> all = new LinkedHashSet<>(members);
    for (UUID p : g.table()) if (g.roleOf(p) == WerewolfGame.Role.CULT_LEADER) all.add(p);
    return all;
  }
}
