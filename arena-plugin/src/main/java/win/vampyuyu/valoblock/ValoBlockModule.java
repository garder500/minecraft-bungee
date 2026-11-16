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
        agents.phaseChanged(m, from, to);
        bots.phaseChanged(m, from, to);
      }

      @Override
      public void agentLocked(VbMatch m, UUID player, String agent) {
        agents.lockedIn(m, player, agent);
        bots.agentLocked(m, player, agent);
      }

      @Override
      public void killed(VbMatch m, UUID victim, UUID killer) {
        agents.killed(m, victim, killer);
        bots.killed(m, victim, killer);
      }

      @Override
      public void botReplaced(VbMatch m, UUID bot, UUID human) {
        bots.replaced(m, bot, human);
      }

      @Override
      public void roundStarted(VbMatch m, int round) {
        for (UUID id : m.participants()) {
          if (m.isBot(id)) bots.roundStarted(m, id, m.keepsLoadout(id));
          else map.teleportToSpawn(id, m.side(id));
          weapons.roundStarted(m, id, m.keepsLoadout(id));
          agents.roundStarted(m, id, m.keepsLoadout(id));
        }
      }
    };
  }

  /** The guns: the listener and the per-tick task (server thread). */
  public void enableWeapons() {
    VbWeaponService service = new VbWeaponService(plugin);
    service.start();
    weapons = service;
  }

  /** The agents: select menu, skins, abilities (listener and per-tick task). */
  public void enableAgents() {
    VbAgentService service = new VbAgentService(plugin);
    service.start();
    agents = service;
  }

  /** The bots: bodies and brains that fill empty seats (server-thread task). */
  public void enableBots() {
    VbBotService service = new VbBotService(plugin);
    service.start();
    bots = service;
  }

  /** Plugin disable: skins and temporary blocks back, bot bodies gone. */
  public void shutdown() {
    if (bots instanceof VbBotService s) s.shutdown();
    if (agents instanceof VbAgentService s) s.shutdown();
  }

  public void registerCommand() {
    VbCommand vb = new VbCommand(plugin);
    Objects.requireNonNull(plugin.getCommand("vb")).setExecutor(vb);
    Objects.requireNonNull(plugin.getCommand("vb")).setTabCompleter(vb);
  }
}
