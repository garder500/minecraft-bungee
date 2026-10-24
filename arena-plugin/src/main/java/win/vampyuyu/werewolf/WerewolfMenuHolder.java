package win.vampyuyu.werewolf;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import win.vampyuyu.arena.CompassMenuHolder;

/** A Werewolf menu window: a role menu, or the village overview. */
public final class WerewolfMenuHolder extends CompassMenuHolder {
  /** Role menu this window was opened as (its painted title), or null for the village. */
  public WerewolfMenus.Kind kind;

  /** True for the village overview (WerewolfMenus.village). */
  public boolean village;

  public WerewolfMenuHolder(UUID viewer, String arenaId, boolean skinned, Component title) {
    super(viewer, arenaId, skinned, title);
  }
}
