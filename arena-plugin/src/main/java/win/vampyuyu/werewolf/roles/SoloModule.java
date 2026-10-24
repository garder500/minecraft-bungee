package win.vampyuyu.werewolf.roles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import win.vampyuyu.werewolf.WerewolfGame;

/** Rules of players with their own goal: hoodlum, tanner and lone wolf. */
public final class SoloModule implements RoleModule {
  private final List<UUID> marks = new ArrayList<>();
  private UUID hoodlum;
  private boolean marked;
  private final Set<UUID> deadTanners = new HashSet<>();

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.HOODLUM, WerewolfGame.Role.TANNER, WerewolfGame.Role.LONE_WOLF);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.HOODLUM);
  }

  // ------------------------------------------------------------------ hoodlum

  @Override
  public boolean ready(WerewolfGame g, WerewolfGame.Phase phase, UUID actor) {
    return phase != WerewolfGame.Phase.HOODLUM || !marked;
  }

  @Override
  public String select(WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    if (phase != WerewolfGame.Phase.HOODLUM) {
      throw new IllegalArgumentException("You cannot pick a player now");
    }
    if (g.roleOf(actor) != WerewolfGame.Role.HOODLUM) {
      throw new IllegalArgumentException("Only the hoodlum can mark players");
    }
    if (marked) {
      throw new IllegalArgumentException("You have already marked two players");
    }
    if (actor.equals(target)) {
      throw new IllegalArgumentException("You cannot mark yourself");
    }
    if (!g.living().contains(target)) {
      throw new IllegalArgumentException("Select a living player");
    }
    if (marks.contains(target)) {
      throw new IllegalArgumentException("That player is already marked");
    }
    marks.add(target);
    if (marks.size() < 2) {
      return "First mark chosen. Pick a second player.";
    }
    hoodlum = actor;
    marked = true;
    g.log("HOODLUM", g.describe(actor) + " marks " + g.describe(marks.get(0)) + " and " + g.describe(target));
    g.done(actor);
    return "Marked: " + marks.get(0) + " and " + target + ". Outlive them both.";
  }

  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (phase != WerewolfGame.Phase.HOODLUM || marked) {
      return;
    }
    List<UUID> pool = new ArrayList<>(g.living());
    pool.remove(bot);
    pool.removeAll(marks);
    while (!pool.isEmpty() && !marked) {
      g.select(bot, pool.remove(random.nextInt(pool.size())));
    }
  }

  // ------------------------------------------------------------------ tanner

  @Override
  public void died(WerewolfGame g, UUID player, String source, String cause) {
    if (g.roleOf(player) == WerewolfGame.Role.TANNER && !"left the game".equals(cause)) {
      deadTanners.add(player);
    }
  }

  // ------------------------------------------------------------------ winning

  @Override
  public String teamWin(WerewolfGame g) {
    UUID lone = loneWolf(g, true);
    if (lone == null) {
      return null;
    }
    if (g.living().size() == 1) {
      return "LONE_WOLF";
    }
    if (g.living().size() != 2) {
      return null;
    }
    UUID other = g.living().stream().filter(p -> !p.equals(lone)).findFirst().orElseThrow();
    return g.wolfSide(other) ? null : "LONE_WOLF"; // a picked minion is on the wolves' side too
  }

  @Override
  public Set<UUID> members(WerewolfGame g, String winnerLabel) {
    UUID lone = null;
    if ("LONE_WOLF".equals(winnerLabel)) {
      lone = loneWolf(g, true); // a doppelganger who copied him may be the one standing
      if (lone == null) lone = loneWolf(g, false);
    }
    return lone == null ? Set.of() : Set.of(lone);
  }

  @Override
  public Set<UUID> sideWinners(WerewolfGame g) {
    Set<UUID> result = new HashSet<>(deadTanners);
    if (marked
        && g.living().contains(hoodlum)
        && marks.stream().noneMatch(g.living()::contains)) {
      result.add(hoodlum);
    }
    return result;
  }

  private static UUID loneWolf(WerewolfGame g, boolean livingOnly) {
    for (UUID p : g.table()) {
      if (g.roleOf(p) == WerewolfGame.Role.LONE_WOLF && (!livingOnly || g.living().contains(p))) {
        return p;
      }
    }
    return null;
  }
}
