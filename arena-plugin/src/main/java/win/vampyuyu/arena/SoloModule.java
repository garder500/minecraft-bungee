package win.vampyuyu.arena;

import java.util.Set;

/** Rules of players with their own goal: hoodlum, tanner and lone wolf. */
final class SoloModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.HOODLUM, WerewolfGame.Role.TANNER, WerewolfGame.Role.LONE_WOLF);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.HOODLUM);
  }
}
