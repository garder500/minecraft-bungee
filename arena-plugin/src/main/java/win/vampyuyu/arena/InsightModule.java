package win.vampyuyu.arena;

import java.util.Set;

/** Rules of the seers' cousins: apprentice seer, aura seer, P.I., sorcerer and the lycan's disguise. */
final class InsightModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.APPRENTICE_SEER, WerewolfGame.Role.AURA_SEER, WerewolfGame.Role.PI, WerewolfGame.Role.SORCERER, WerewolfGame.Role.LYCAN);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.AURA, WerewolfGame.Phase.PI, WerewolfGame.Phase.SORCERER);
  }
}
