package win.vampyuyu.werewolf.roles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import win.vampyuyu.werewolf.WerewolfGame.Phase;
import win.vampyuyu.werewolf.WerewolfGame.Role;
import win.vampyuyu.werewolf.WerewolfGame;

/** Rules of the seers' cousins: apprentice seer, aura seer, P.I., sorcerer and the lycan's disguise. */
public final class InsightModule implements RoleModule {
  /** Roles that read as plain villagers to the aura seer, besides the villager and the werewolf. */
  private static final Set<Role> PLAIN_AURA =
      Set.of(Role.VILLAGER, Role.WEREWOLF, Role.CURSED, Role.DOPPELGANGER, Role.DRUNK);

  /** Players who already used their power in the call that is open tonight. */
  private final Set<UUID> acted = new HashSet<>();

  /** Investigators whose single investigation is spent. */
  private final Set<UUID> spent = new HashSet<>();

  /** The Seer died during the night: the apprentice is promoted at dawn, once all kills are in. */
  private boolean seerFell;

  /** Each bot's last pick, to avoid the same target two nights in a row. */
  private final Map<UUID, UUID> lastPick = new HashMap<>();

  @Override
  public Set<Role> roles() {
    return Set.of(
        Role.APPRENTICE_SEER, Role.AURA_SEER, Role.PI, Role.SORCERER, Role.LYCAN);
  }

  @Override
  public Set<Phase> phases() {
    return Set.of(Phase.AURA, Phase.PI, Phase.SORCERER);
  }

  @Override
  public void nightfall(WerewolfGame g) {
    acted.clear();
  }

  @Override
  public boolean ready(WerewolfGame g, Phase phase, UUID actor) {
    return phase != Phase.PI || !spent.contains(actor);
  }

  @Override
  public void died(WerewolfGame g, UUID player, String source, String cause) {
    if (g.roleOf(player) != Role.SEER) return;
    if (g.night()) seerFell = true; // the apprentice may die in the same dawn: decide in dawn()
    else promote(g);
  }

  @Override
  public void dawn(WerewolfGame g) {
    if (!seerFell) return;
    seerFell = false;
    promote(g);
  }

  /** The first living apprentice becomes the Seer, unless a living Seer remains. */
  private void promote(WerewolfGame g) {
    for (UUID p : g.table()) if (g.living().contains(p) && g.roleOf(p) == Role.SEER) return;
    for (UUID p : g.table()) {
      if (g.living().contains(p) && g.roleOf(p) == Role.APPRENTICE_SEER) {
        g.setRole(p, Role.SEER);
        g.notify(p, "The Seer is dead: you are the Seer now. You wake each night to inspect a player.");
        return;
      }
    }
  }

  @Override
  public String select(WerewolfGame g, Phase phase, UUID actor, UUID target) {
    if (!g.living().contains(target)) throw new IllegalArgumentException("Select a living player");
    if (acted.contains(actor))
      throw new IllegalArgumentException("You already used your power tonight");
    String reply =
        switch (phase) {
          case AURA -> aura(g, actor, target);
          case PI -> investigate(g, actor, target);
          case SORCERER ->
              sorcerer(actor, target)
                  + (g.selfRole(target) == Role.SEER ? " is the Seer." : " is not the Seer.");
          default -> throw new IllegalArgumentException("You cannot pick a player now");
        };
    acted.add(actor);
    if (phase == Phase.PI) spent.add(actor);
    g.done(actor);
    return reply;
  }

  private String sorcerer(UUID actor, UUID target) {
    if (actor.equals(target)) throw new IllegalArgumentException("Select another living player");
    return target.toString();
  }

  private String aura(WerewolfGame g, UUID actor, UUID target) {
    if (actor.equals(target)) throw new IllegalArgumentException("Select another living player");
    return PLAIN_AURA.contains(g.selfRole(target))
        ? target + " has a plain aura: a villager or a werewolf."
        : target + " has a special aura: a special role.";
  }

  private String investigate(WerewolfGame g, UUID actor, UUID target) {
    if (actor.equals(target)) throw new IllegalArgumentException("Select another living player");
    List<UUID> near = new ArrayList<>(List.of(target));
    for (int dir : new int[] {-1, 1}) {
      UUID beside = g.neighbour(target, dir);
      if (beside != null && !near.contains(beside)) near.add(beside);
    }
    boolean wolf = near.stream().anyMatch(p -> WerewolfGame.wolf(g.roleOf(p)));
    return wolf
        ? "There is at least one werewolf among " + target + " and the two players beside them."
        : "There is no werewolf among " + target + " and the two players beside them.";
  }

  /** The last target this bot picked (null if none yet). */
  public UUID lastPick(UUID bot) {
    return lastPick.get(bot);
  }

  @Override
  public void bot(WerewolfGame g, Phase phase, UUID bot, Random random) {
    if (!phases().contains(phase) || !g.living().contains(bot)) return;
    if (phase == Phase.PI && random.nextInt(3) != 0) {
      g.done(bot); // keeps the investigation for another night
      return;
    }
    List<UUID> pool = new ArrayList<>();
    for (UUID p : g.table()) if (g.living().contains(p) && !p.equals(bot)) pool.add(p);
    if (pool.size() > 1) pool.remove(lastPick.get(bot));
    if (pool.isEmpty()) {
      g.done(bot);
      return;
    }
    UUID target = pool.get(random.nextInt(pool.size()));
    lastPick.put(bot, target);
    g.select(bot, target);
  }
}
