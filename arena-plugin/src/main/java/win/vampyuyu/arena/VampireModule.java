package win.vampyuyu.arena;

import java.util.Set;

/** Rules of the vampires, their delayed kills, and the hunter who hunts them. */
final class VampireModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.VAMPIRE);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.VAMPIRE);
  }
}
