package win.vampyuyu.valoblock;

import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.GameHooks;

/** What ValoBlock adds around the arena life cycle. Slice 1: the checks; the scenery comes with later slices. */
final class VbHooks implements GameHooks {
  private final ValoBlockModule module;

  VbHooks(ValoBlockModule module) {
    this.module = module;
  }

  @Override
  public String canStart(ArenaSession s) {
    return module.map.problem();
  }

  @Override
  public boolean tickGame(ArenaSession s, long now, boolean due) {
    // The match only changes phase through damage, holds and the engine's clock, which the engine
    // already follows; the HUD is drawn here once slice 5 provides it.
    if (s.game instanceof VbMatch m) module.hud.render(m, now);
    return false;
  }

  @Override
  public void onStop(ArenaSession s, Throwable error) {
    if (s.game instanceof VbMatch m) {
      module.hud.clear(m);
      for (java.util.UUID id : m.participants()) {
        module.weapons.clear(id);
        module.agents.restore(id);
      }
      for (java.util.UUID bot : m.bots()) module.bots.remove(bot);
    }
  }
}
