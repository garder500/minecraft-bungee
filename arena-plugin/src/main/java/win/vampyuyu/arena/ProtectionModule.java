package win.vampyuyu.arena;

import java.util.Set;

/** Rules of who survives what: priest, tough guy, diseased and cursed. */
final class ProtectionModule implements RoleModule {
  @Override
  public Set<WerewolfGame.Role> roles() {
    return Set.of(WerewolfGame.Role.PRIEST, WerewolfGame.Role.TOUGH_GUY, WerewolfGame.Role.DISEASED, WerewolfGame.Role.CURSED);
  }

  @Override
  public Set<WerewolfGame.Phase> phases() {
    return Set.of(WerewolfGame.Phase.PRIEST);
  }
}
