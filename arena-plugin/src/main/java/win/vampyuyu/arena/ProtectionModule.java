package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Rules of who survives what: priest, tough guy, diseased and cursed. */
final class ProtectionModule implements RoleModule {
  private final Set<UUID> blessed = new HashSet<>();
  private final Set<UUID> blessers = new HashSet<>();
  private final Set<UUID> wounded = new HashSet<>();
  private final Map<UUID, Integer> bitten = new HashMap<>();

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(
        WerewolfGame.Role.PRIEST,
        WerewolfGame.Role.TOUGH_GUY,
        WerewolfGame.Role.DISEASED,
        WerewolfGame.Role.CURSED);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.PRIEST);
  }

  // ------------------------------------------------------------------ priest

  @Override
  public boolean ready(WerewolfGame g, WerewolfGame.Phase phase, UUID actor) {
    return phase != WerewolfGame.Phase.PRIEST || !blessers.contains(actor);
  }

  @Override
  public String select(WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    if (phase != WerewolfGame.Phase.PRIEST) {
      throw new IllegalArgumentException("You cannot pick a player now");
    }
    if (g.roleOf(actor) != WerewolfGame.Role.PRIEST) {
      throw new IllegalArgumentException("Only the priest can bless");
    }
    if (blessers.contains(actor)) {
      throw new IllegalArgumentException("You have already blessed somebody");
    }
    if (actor.equals(target)) {
      throw new IllegalArgumentException("You cannot bless yourself");
    }
    if (!g.living().contains(target)) {
      throw new IllegalArgumentException("Select a living player");
    }
    blessers.add(actor);
    blessed.add(target);
    g.log("PRIEST", g.describe(actor) + " blesses " + g.describe(target));
    g.done(actor);
    return target + " is blessed. The blessing guards them against the next attempt on their life.";
  }

  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (phase != WerewolfGame.Phase.PRIEST || blessers.contains(bot)) {
      return;
    }
    List<UUID> pool = new ArrayList<>(g.living());
    pool.remove(bot);
    if (!pool.isEmpty()) {
      g.select(bot, pool.get(random.nextInt(pool.size())));
    }
  }

  // ------------------------------------------------------------------ deaths

  @Override
  public boolean saves(WerewolfGame g, WerewolfGame.Kill kill) {
    UUID victim = kill.victim();
    String source = kill.source();
    if (blessed.contains(victim) && !source.equals("OTHER")) {
      blessed.remove(victim);
      g.notify(victim, "A blessing protected you.");
      return true;
    }
    boolean wolves = source.equals("WOLVES") || source.equals("WOLVES2");
    WerewolfGame.Role role = g.roleOf(victim);
    if (wolves && role == WerewolfGame.Role.TOUGH_GUY && wounded.add(victim)) {
      g.doom(victim, "died of the wounds the wolves gave them");
      g.notify(victim, "The wolves wounded you. You survive tonight but will die at the next dawn.");
      return true;
    }
    if (source.equals("WOLVES") && role == WerewolfGame.Role.CURSED) {
      bitten.putIfAbsent(victim, g.nightNumber());
      g.notify(victim, "The wolves bit you but you live. You will turn into a werewolf next night.");
      return true;
    }
    return false;
  }

  @Override
  public void died(WerewolfGame g, UUID player, String source, String cause) {
    bitten.remove(player);
    if (g.roleOf(player) != WerewolfGame.Role.DISEASED || !source.equals("WOLVES")) {
      return;
    }
    g.callOffNextHunt();
    for (UUID p : g.living()) {
      if (WerewolfGame.wolf(g.roleOf(p))) {
        g.notify(p, "The diseased was sick: the pack will be too sick to hunt next night.");
      }
    }
  }

  // ------------------------------------------------------------------ cursed

  @Override
  public void nightfall(WerewolfGame g) {
    for (var entry : new ArrayList<>(bitten.entrySet())) {
      UUID cursed = entry.getKey();
      if (entry.getValue() >= g.nightNumber()) {
        continue;
      }
      bitten.remove(cursed);
      if (!g.living().contains(cursed) || g.roleOf(cursed) != WerewolfGame.Role.CURSED) {
        continue;
      }
      g.setRole(cursed, WerewolfGame.Role.WEREWOLF);
      for (UUID p : g.living()) {
        if (!p.equals(cursed) && WerewolfGame.wolf(g.roleOf(p))) {
          g.notify(p, cursed + " was cursed and joins the pack as a werewolf.");
        }
      }
      g.notify(cursed, "You are a werewolf now. Join the pack and win with it.");
    }
  }
}
