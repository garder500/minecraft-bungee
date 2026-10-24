package win.vampyuyu.arena;

import java.util.Set;

/** Rules of the cult leader and the cult's win. */
final class CultModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.CULT_LEADER);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.CULT);
  }
}
