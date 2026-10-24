package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Rules of the vampires, their delayed kills, and the hunter who hunts them. */
final class VampireModule implements RoleModule {
  private int drainedOn = -1; // the night whose victim was already drained
  private StringBuilder news; // set while a drain resolves: one notice per player, so news merge

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.VAMPIRE);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.VAMPIRE);
  }

  /** The night's victim dies as the day vote opens. */
  @Override
  public void entered(WerewolfGame g, WerewolfGame.Phase phase) {
    if (phase != WerewolfGame.Phase.DAY_VOTE || drainedOn == g.nightNumber()) return;
    UUID victim = g.packVictim(WerewolfGame.Phase.VAMPIRE);
    if (victim == null || !g.living().contains(victim) || isVampire(g, victim)) return;
    drainedOn = g.nightNumber();
    news = new StringBuilder();
    boolean drained = g.kill(victim, "VAMPIRE", "drained by the vampires");
    String told = news.toString();
    news = null;
    if (drained) g.notifyLiving("The vampires drained " + victim + " during the night." + told);
    else if (!told.isEmpty()) g.notifyLiving(told.strip());
  }

  /** Wolves cannot kill a vampire. */
  @Override
  public boolean saves(WerewolfGame g, WerewolfGame.Kill kill) {
    return isVampire(g, kill.victim())
        && (kill.source().equals("WOLVES") || kill.source().equals("WOLVES2"));
  }

  /** With a vampire alive, a dying hunter's last strike falls on the vampire nearest to him. */
  @Override
  public void died(WerewolfGame g, UUID player, String source, String cause) {
    if (g.roleOf(player) != WerewolfGame.Role.HUNTER || cause.startsWith("left")) return;
    UUID target = nearestVampire(g, player);
    if (target == null) return;
    g.cancelShot(player);
    g.slay(target, "killed by the vampire hunter's last strike");
    // Under a hidden reveal the table is not told what the victim was.
    String text =
        g.reveal().equals("FULL")
            ? "The vampire hunter's last strike killed the vampire " + target + "."
            : "A last strike killed " + target + ".";
    if (news == null) g.notifyLiving(text);
    else news.append(' ').append(text);
  }

  @Override
  public String teamWin(WerewolfGame g) {
    boolean any = false;
    for (UUID p : g.living()) {
      if (!isVampire(g, p)) return null;
      any = true;
    }
    return any ? "VAMPIRES" : null;
  }

  @Override
  public Set<UUID> members(WerewolfGame g, String winnerLabel) {
    Set<UUID> out = new LinkedHashSet<>();
    if (!"VAMPIRES".equals(winnerLabel)) return out;
    for (UUID p : g.table()) if (isVampire(g, p)) out.add(p);
    return out;
  }

  /** A vampire bot follows a human vampire's pick, else strikes a random non-vampire. */
  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (phase != WerewolfGame.Phase.VAMPIRE || !isVampire(g, bot)) return;
    UUID target = null;
    for (UUID mate : g.pack(bot)) {
      UUID pick = g.ballot(mate);
      if (pick != null && g.living().contains(pick) && !isVampire(g, pick)) target = pick;
    }
    if (target == null) {
      List<UUID> prey = new ArrayList<>();
      for (UUID p : g.living()) if (!isVampire(g, p)) prey.add(p);
      if (prey.isEmpty()) return;
      target = prey.get(random.nextInt(prey.size()));
    }
    if (target.equals(g.ballot(bot))) return; // a second click would take the vote back
    g.select(bot, target);
  }

  private static boolean isVampire(WerewolfGame g, UUID p) {
    return g.roleOf(p) == WerewolfGame.Role.VAMPIRE;
  }

  /** The living vampire seated closest to {@code hunter}, either way round; the lower seat on a tie. */
  private static UUID nearestVampire(WerewolfGame g, UUID hunter) {
    List<UUID> table = g.table();
    int at = table.indexOf(hunter), n = table.size();
    UUID best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (int seat = 0; seat < n; seat++) {
      UUID p = table.get(seat);
      if (!g.living().contains(p) || !isVampire(g, p)) continue;
      int gap = Math.abs(seat - at);
      int distance = Math.min(gap, n - gap);
      if (distance < bestDistance) {
        best = p;
        bestDistance = distance;
      }
    }
    return best;
  }
}
