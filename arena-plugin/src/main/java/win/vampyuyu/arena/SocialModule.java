package win.vampyuyu.arena;

import java.util.Set;

/** Rules of the village's quiet powers: spellcaster, old hag, ghost, mason, minion and old man. */
final class SocialModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.SPELLCASTER, WerewolfGame.Role.OLD_HAG, WerewolfGame.Role.GHOST, WerewolfGame.Role.MASON, WerewolfGame.Role.MINION, WerewolfGame.Role.OLD_MAN);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.SPELL, WerewolfGame.Phase.HAG, WerewolfGame.Phase.GHOST);
  }
}
