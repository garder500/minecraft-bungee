package win.vampyuyu.arena;

import java.util.Set;

/** Rules of the day vote's special voters: mayor, idiot, pacifist, prince, martyr, troublemaker. */
final class CourtModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.MAYOR, WerewolfGame.Role.IDIOT, WerewolfGame.Role.PACIFIST, WerewolfGame.Role.PRINCE, WerewolfGame.Role.MARTYR, WerewolfGame.Role.TROUBLEMAKER);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.MARTYR, WerewolfGame.Phase.TROUBLE);
  }
}
