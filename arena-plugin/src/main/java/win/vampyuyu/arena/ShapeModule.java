package win.vampyuyu.arena;

import java.util.Set;

/** Rules of roles that are not what they seem: drunk and doppelganger. */
final class ShapeModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.DRUNK, WerewolfGame.Role.DOPPELGANGER);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.DOPPEL);
  }
}
