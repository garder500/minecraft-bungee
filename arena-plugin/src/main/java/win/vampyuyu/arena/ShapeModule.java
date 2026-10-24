package win.vampyuyu.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import win.vampyuyu.arena.WerewolfGame.Phase;
import win.vampyuyu.arena.WerewolfGame.Role;

/** Rules of roles that are not what they seem: drunk and doppelganger. */
final class ShapeModule implements RoleModule {
  /** Roles whose job happens only on the first night: a drunk would wake too late for them. */
  private static final Set<Role> FIRST_NIGHT =
      Set.of(
          Role.CUPID,
          Role.DOPPELGANGER,
          Role.HOODLUM,
          Role.MASON,
          Role.MINION,
          Role.GHOST,
          Role.DRUNK,
          Role.APPRENTICE_SEER);

  /** The night the drunks sober up. */
  private static final int SOBER_NIGHT = 3;

  /** Drunks that were dealt (dead ones stay), and those who already woke up. */
  private final Set<UUID> drunks = new LinkedHashSet<>();

  private final Set<UUID> awake = new HashSet<>();

  /** Each doppelganger and the player she chose. */
  private final Map<UUID, UUID> targets = new HashMap<>();

  @Override
  public Set<Role> roles() {
    return Set.of(Role.DRUNK, Role.DOPPELGANGER);
  }

  @Override
  public Set<Phase> phases() {
    return Set.of(Phase.DOPPEL);
  }

  // ------------------------------------------------------------------ drunk

  @Override
  public void deal(WerewolfGame g) {
    for (UUID p : g.table()) if (g.roleOf(p) == Role.DRUNK) drunks.add(p);
    for (UUID p : drunks) g.setRole(p, realRole(g));
  }

  /**
   * A werewolf, a villager or a village role nobody holds yet (never a first-night one). Decisions:
   * a sleeping drunk who dies before night 3 is announced with his real role and his death powers
   * (hunter, prince, cub) work; a doppelganger copies that real role; the engine keeps him out of
   * the pack and its ballots until he wakes, so wolves may pick him by mistake.
   */
  private Role realRole(WerewolfGame g) {
    Set<Role> held = new HashSet<>();
    for (UUID p : g.table()) held.add(g.roleOf(p));
    List<Role> pool = new ArrayList<>(List.of(Role.WEREWOLF, Role.VILLAGER));
    // A drunk wolf must not tip the table into parity before the first night.
    long wolves = g.table().stream().filter(p -> WerewolfGame.wolf(g.roleOf(p))).count();
    if ((wolves + 1) * 2 >= g.table().size()) pool.remove(Role.WEREWOLF);
    for (Role r : Role.values())
      if (!FIRST_NIGHT.contains(r)
          && !pool.contains(r)
          && !held.contains(r)
          && RoleInfo.of(r.name()).team().equals("village")) pool.add(r);
    return pool.get(g.random().nextInt(pool.size()));
  }

  private boolean asleep(UUID p) {
    return drunks.contains(p) && !awake.contains(p);
  }

  @Override
  public Role perceived(WerewolfGame g, UUID player, Role actual) {
    return asleep(player) ? Role.VILLAGER : actual;
  }

  @Override
  public boolean ready(WerewolfGame g, Phase phase, UUID actor) {
    return !asleep(actor);
  }

  @Override
  public void nightfall(WerewolfGame g) {
    if (g.nightNumber() != SOBER_NIGHT) return;
    for (UUID p : drunks) {
      if (!g.living().contains(p) || !awake.add(p)) continue;
      Role real = g.roleOf(p);
      g.notify(p, "You sober up: you are the " + RoleInfo.of(real.name()).name());
      if (!WerewolfGame.wolf(real)) continue;
      for (UUID q : g.living())
        if (!q.equals(p) && WerewolfGame.wolf(g.roleOf(q)))
          g.notify(q, p + " was the drunk, and is a werewolf: he joins the pack.");
    }
  }

  // ------------------------------------------------------------------ doppelganger

  @Override
  public String select(WerewolfGame g, Phase phase, UUID actor, UUID target) {
    if (phase != Phase.DOPPEL) throw new IllegalArgumentException("You cannot pick a player now");
    if (!g.living().contains(target) || actor.equals(target))
      throw new IllegalArgumentException("Select another living player");
    if (targets.containsKey(actor))
      throw new IllegalArgumentException("You already chose who to become");
    targets.put(actor, target);
    g.done(actor);
    return "You will become " + target + " when they die.";
  }

  /** Copies waiting for the dawn: each doppelganger and the role her target held when he died. */
  private final Map<UUID, Role> pending = new LinkedHashMap<>();

  @Override
  public void died(WerewolfGame g, UUID player, String source, String cause) {
    // The real role counts: a sleeping drunk is copied for what he really was.
    Role role = g.roleOf(player);
    for (var e : new ArrayList<>(targets.entrySet())) {
      UUID doppel = e.getKey();
      if (!e.getValue().equals(player) || !g.living().contains(doppel)) continue;
      targets.remove(doppel);
      // A death of the night's resolution waits for dawn(), which runs after the apprentice seer
      // has been promoted (InsightModule): the uniqueness rule must see that promotion.
      if (g.night()) pending.put(doppel, role);
      else copy(g, doppel, role);
    }
  }

  @Override
  public void dawn(WerewolfGame g) {
    for (var e : new ArrayList<>(pending.entrySet())) {
      pending.remove(e.getKey());
      if (g.living().contains(e.getKey())) copy(g, e.getKey(), e.getValue());
    }
  }

  private void copy(WerewolfGame g, UUID doppel, Role role) {
    if (taken(g, role, doppel)) {
      g.setRole(doppel, Role.VILLAGER);
      g.notify(
          doppel,
          "Your target died, but somebody already holds the "
              + RoleInfo.of(role.name()).name()
              + ": you stay a plain Villager.");
    } else {
      g.setRole(doppel, role);
      g.notify(doppel, "Your target died: you are now the " + RoleInfo.of(role.name()).name());
    }
  }

  /** A role of which only one exists cannot be held twice. */
  private boolean taken(WerewolfGame g, Role role, UUID doppel) {
    if (RoleInfo.of(role.name()).max() > 1) return false;
    for (UUID p : g.living()) if (!p.equals(doppel) && g.roleOf(p) == role) return true;
    return false;
  }

  @Override
  public void bot(WerewolfGame g, Phase phase, UUID bot, Random random) {
    if (phase != Phase.DOPPEL || !g.living().contains(bot)) return;
    List<UUID> pool = new ArrayList<>();
    for (UUID p : g.table()) if (g.living().contains(p) && !p.equals(bot)) pool.add(p);
    if (pool.isEmpty()) {
      g.done(bot);
      return;
    }
    g.select(bot, pool.get(random.nextInt(pool.size())));
  }
}
