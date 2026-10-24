package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Rules of the day vote's special voters: mayor, idiot, pacifist, prince, martyr, troublemaker. */
final class CourtModule implements RoleModule {
  private final Set<UUID> stirred = new HashSet<>();
  private boolean princeRevealed;

  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(
        WerewolfGame.Role.MAYOR,
        WerewolfGame.Role.IDIOT,
        WerewolfGame.Role.PACIFIST,
        WerewolfGame.Role.PRINCE,
        WerewolfGame.Role.MARTYR,
        WerewolfGame.Role.TROUBLEMAKER);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.MARTYR, WerewolfGame.Phase.TROUBLE);
  }

  // ------------------------------------------------------------------ the vote

  @Override
  public int weight(WerewolfGame g, UUID voter) {
    if (!g.phase().equals("DAY_VOTE") && !g.phase().equals("TRIAL")) return 1;
    return switch (g.roleOf(voter)) {
      case MAYOR -> g.living().contains(voter) ? 2 : 1;
      case PACIFIST -> 0;
      case null, default -> 1;
    };
  }

  @Override
  public UUID autoBallot(WerewolfGame g, UUID voter, List<UUID> candidates) {
    if (g.roleOf(voter) != WerewolfGame.Role.IDIOT) return null;
    List<UUID> pool = new ArrayList<>(candidates);
    pool.remove(voter);
    return pool.isEmpty() ? null : pool.get(g.random().nextInt(pool.size()));
  }

  @Override
  public boolean spares(WerewolfGame g, WerewolfGame.Kill kill) {
    UUID victim = kill.victim();
    return kill.source().equals("VOTE")
        && !princeRevealed
        && g.living().contains(victim)
        && g.roleOf(victim) == WerewolfGame.Role.PRINCE;
  }

  @Override
  public boolean saves(WerewolfGame g, WerewolfGame.Kill kill) {
    UUID victim = kill.victim();
    if (!kill.source().equals("VOTE")
        || g.roleOf(victim) != WerewolfGame.Role.PRINCE
        || !g.living().contains(victim)
        || princeRevealed) return false;
    princeRevealed = true;
    g.log("PRINCE", g.describe(victim) + " is revealed and spared by the village vote");
    g.notifyLiving(victim + " is the Prince and cannot be eliminated by the village.");
    return true;
  }

  // ------------------------------------------------------------------ the calls

  @Override
  public boolean ready(WerewolfGame g, WerewolfGame.Phase phase, UUID actor) {
    return phase != WerewolfGame.Phase.TROUBLE || !stirred.contains(actor);
  }

  @Override
  public Map<String, String> abilities(WerewolfGame g, UUID player) {
    if (!g.living().contains(player)) return Map.of();
    WerewolfGame.Role role = g.roleOf(player);
    if (role == WerewolfGame.Role.MARTYR && g.phase().equals("MARTYR")) {
      Map<String, String> out = new java.util.LinkedHashMap<>();
      out.put("sacrifice", "Take " + g.condemned() + "'s place");
      out.put("let", "Let it pass");
      return out;
    }
    if (role == WerewolfGame.Role.TROUBLEMAKER
        && g.phase().equals("TROUBLE")
        && !stirred.contains(player))
      return Map.of("stir", "Stir up trouble: two eliminations tomorrow");
    return Map.of();
  }

  @Override
  public String use(WerewolfGame g, UUID player, String ability) {
    if (!abilities(g, player).containsKey(ability))
      throw new IllegalArgumentException("This action is unavailable now");
    switch (ability) {
      case "sacrifice" -> {
        UUID saved = g.condemned();
        g.log("MARTYR", g.describe(player) + " takes the place of " + g.describe(saved));
        g.substituteCondemned(player);
        g.done(player);
        return "You gave your life in " + saved + "'s place.";
      }
      case "let" -> {
        g.done(player);
        return "You let it pass.";
      }
      case "stir" -> {
        stirred.add(player);
        g.extraLynches(1);
        g.log("TROUBLE", g.describe(player) + " stirs up trouble: two eliminations tomorrow");
        g.done(player);
        return "You stir up trouble: the village must eliminate two players tomorrow.";
      }
      default -> throw new IllegalArgumentException("This action is unavailable now");
    }
  }

  @Override
  public String select(WerewolfGame g, WerewolfGame.Phase phase, UUID actor, UUID target) {
    throw new IllegalArgumentException("Use your buttons: there is nobody to pick");
  }

  @Override
  public void bot(WerewolfGame g, WerewolfGame.Phase phase, UUID bot, Random random) {
    if (!g.actors().contains(bot)) return;
    if (phase == WerewolfGame.Phase.MARTYR) {
      if (g.condemned() == null) return;
      // One flat chance: a bot knows nothing about the condemned player's real role.
      if (random.nextInt(100) < 25) g.use(bot, "sacrifice");
    } else if (phase == WerewolfGame.Phase.TROUBLE) {
      if (g.nightNumber() > 1 && random.nextInt(100) < 15) g.use(bot, "stir");
    }
  }
}
