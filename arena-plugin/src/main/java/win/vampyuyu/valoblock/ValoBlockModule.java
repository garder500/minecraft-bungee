package win.vampyuyu.valoblock;

import java.util.Objects;
import java.util.UUID;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * ValoBlock as a module of the arena engine: its preset and hooks, the /vb command and the services
 * later slices plug in (all no-ops for now). The constructor has no side effects.
 */
public final class ValoBlockModule {
  private final NexusArenaEngine plugin;
  private final VbPreset preset;

  // Replaced by their real implementations in later slices.
  public volatile WeaponService weapons = WeaponService.NOOP;
  public volatile AgentService agents = AgentService.NOOP;
  public volatile BotService bots = BotService.NOOP;
  public volatile MapService map = MapService.NOOP;
  public volatile VbHud hud = VbHud.NOOP;

  public ValoBlockModule(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.preset = new VbPreset(this, new VbHooks(this));
  }

  public VbPreset preset() {
    return preset;
  }

  /** Routes match events to the services. */
  VbEvents events() {
    return new VbEvents() {
      @Override
      public void phaseChanged(VbMatch m, VbPhase from, VbPhase to) {
        if (to == VbPhase.BUY) map.barriers(true);
        if (to == VbPhase.COMBAT) map.barriers(false);
      }

      @Override
      public void roundStarted(VbMatch m, int round) {
        for (UUID id : m.participants()) {
          if (m.isBot(id) && round == 1) bots.spawn(m, id, m.side(id));
          map.teleportToSpawn(id, m.side(id));
          weapons.roundStarted(m, id, m.keepsLoadout(id));
        }
      }
    };
  }

  public void registerCommand() {
    VbCommand vb = new VbCommand(plugin);
    Objects.requireNonNull(plugin.getCommand("vb")).setExecutor(vb);
    Objects.requireNonNull(plugin.getCommand("vb")).setTabCompleter(vb);
  }
}
