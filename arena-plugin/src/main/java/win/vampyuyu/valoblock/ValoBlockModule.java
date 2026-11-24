package win.vampyuyu.valoblock;

import java.util.Objects;
import java.util.UUID;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * ValoBlock as a module of the arena engine: its preset and hooks, the /vb command and the services
 * later slices plug in (all no-ops for now). The constructor has no side effects.
 */
public final class ValoBlockModule {
  final NexusArenaEngine plugin;
  private final VbPreset preset;

  // Replaced by their real implementations in later slices.
  public volatile WeaponService weapons = WeaponService.NOOP;
  public volatile AgentService agents = AgentService.NOOP;
  /** Automatic opening of the select and the Armory (set with the agents service). */
  volatile VbMenus menus;
  /** The map vote of the lobby (null until the maps are enabled). */
  volatile VbMapVoteService votes;
  public volatile BotService bots = BotService.NOOP;
  public volatile MapService map = MapService.NOOP;
  public volatile VbHud hud = VbHud.NOOP;
  /** Name tags: allies only (a scoreboard team per squad for players, per-viewer displays for bots). */
  public final VbNameTags tags = new VbNameTags();
  /** The tactical map menu (null until the agents are enabled) and the minimap items (null until the maps are enabled). */
  volatile TacMapService tacmap;
  volatile MiniMapService minimap;
  /** The Rift Seed service (null until enabled). */
  volatile VbSeedService seeds;
  /** The ValoBlock resource pack (null when not configured). */
  public volatile VbPack pack;

  /**
   * ValoBlock runs on the Bukkit scheduler and touches entities from it, which Folia forbids. On Folia the
   * module stays inert (the rest of the plugin is unaffected) and arenas of this game refuse to start.
   */
  public static final String FOLIA_REFUSAL = "ValoBlock is not available on Folia (it needs the Bukkit scheduler)";

  private final boolean folia;
  private boolean refusalLogged;

  static boolean detectFolia() {
    try {
      Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  public boolean disabled() {
    return folia;
  }

  private boolean refuse() {
    if (!folia) return false;
    if (!refusalLogged) {
      refusalLogged = true;
      plugin.getLogger().warning(FOLIA_REFUSAL + ": the ValoBlock services are not started.");
    }
    return true;
  }

  public ValoBlockModule(NexusArenaEngine plugin) {
    this.folia = detectFolia();
    this.plugin = plugin;
    this.preset = new VbPreset(this, new VbHooks(this));
  }

  public VbPreset preset() {
    return preset;
  }

  /** Ensures the waiting-room map vote is open and opens the ballot window for {@code p}. */
  public String openMapVote(org.bukkit.entity.Player p, win.vampyuyu.arena.ArenaSession s) {
    VbMapVoteService service = votes;
    if (service == null) return "The map vote is not running";
    service.ensureOpen(s);
    return service.open(p, s);
  }

  /** Routes match events to the services. */
  VbEvents events() {
    return new VbEvents() {
      @Override
      public void phaseChanged(VbMatch m, VbPhase from, VbPhase to) {
        map.phaseChanged(m, from, to);
        agents.phaseChanged(m, from, to);
        VbMenus mn = menus;
        if (mn != null) mn.phaseChanged(m);
        bots.phaseChanged(m, from, to);
      }

      @Override
      public void agentLocked(VbMatch m, UUID player, String agent) {
        agents.lockedIn(m, player, agent);
        bots.agentLocked(m, player, agent);
      }

      @Override
      public void killed(VbMatch m, UUID victim, UUID killer) {
        String weapon = killer == null ? null : m.isBot(killer) ? bots.weaponName(killer) : weapons.heldName(killer);
        hud.killed(m, victim, killer, weapon);
        agents.killed(m, victim, killer);
        bots.killed(m, victim, killer);
        spectate(m, victim);
      }

      @Override
      public void botReplaced(VbMatch m, UUID bot, UUID human) {
        bots.replaced(m, bot, human);
      }

      @Override
      public void roundStarted(VbMatch m, int round) {
        for (UUID id : m.participants()) {
          if (m.hasLeft(id)) continue; // left but still online: nothing is dealt, no teleport back
          if (m.isBot(id)) bots.roundStarted(m, id, m.keepsLoadout(id));
          else map.teleportToSpawn(id, m.side(id));
          weapons.roundStarted(m, id, m.keepsLoadout(id));
          agents.roundStarted(m, id, m.keepsAbilities(id));
        }
      }
    };
  }

  /** The guns: the listener and the per-tick task (server thread). */
  public void enableWeapons() {
    if (refuse()) return;
    VbWeaponService service = new VbWeaponService(plugin);
    service.start();
    weapons = service;
  }

  /** The agents: select menu, skins, abilities (listener and per-tick task). */
  public void enableAgents() {
    if (refuse()) return;
    VbAgentService service = new VbAgentService(plugin);
    service.start();
    agents = service;
  }

  /** The map: the vb_arena world, greybox maps, spawns, barriers, orbs (server-thread task). */
  public void enableMaps() {
    if (refuse()) return;
    VbMapService service = new VbMapService(plugin);
    if ("creative".equals(plugin.serverId)) {
      String err = service.createWorld();
      if (err != null) plugin.getLogger().warning("ValoBlock: " + err);
    }
    service.start();
    map = service;
    VbMapVoteService vote = new VbMapVoteService(plugin);
    vote.start();
    votes = vote;
    MiniMapService mini = new MiniMapService(plugin);
    mini.start();
    minimap = mini;
  }

  /** The HUD: boss bars for the score and the kill feed, the action bar for the player's own state. */
  public void enableHud() {
    if (refuse()) return;
    hud = new VbHudService(plugin);
  }

  /** The Rift Seed: its item, the plant hold, the back display and the dropped Seed (listener and per-tick task). */
  public void enableSeeds() {
    if (refuse()) return;
    VbSeedService service = new VbSeedService(plugin);
    service.start();
    seeds = service;
  }

  /** The bots: bodies and brains that fill empty seats (server-thread task). */
  public void enableBots() {
    if (refuse()) return;
    VbBotService service = new VbBotService(plugin);
    service.start();
    bots = service;
  }

  /** The second resource pack: offered on match join, taken back on leave (null when not configured). */
  public void enablePack() {
    pack = VbPack.load(plugin);
    VbQuickActions.install(plugin, plugin.serverId);
  }

  /** Plugin disable: skins and temporary blocks back, bot bodies gone. */
  public void shutdown() {
    if (seeds != null) seeds.shutdown();
    if (bots instanceof VbBotService s) s.shutdown();
    if (minimap != null) minimap.shutdown();
    if (map instanceof VbMapService s) s.shutdown();
    if (agents instanceof VbAgentService s) s.shutdown();
  }

  public void registerCommand() {
    if (folia) {
      Objects.requireNonNull(plugin.getCommand("vb")).setExecutor((sender, cmd, label, args) -> {
        sender.sendMessage(FOLIA_REFUSAL);
        return true;
      });
      return;
    }
    VbCommand vb = new VbCommand(plugin);
    Objects.requireNonNull(plugin.getCommand("vb")).setExecutor(vb);
    Objects.requireNonNull(plugin.getCommand("vb")).setTabCompleter(vb);
  }

  /**
   * Every death of a human (gun, knife, ability, the sea, an admin kill) ends in spectator mode here, once: before,
   * only the gun and knife paths did it, so a player drowned on Stormbreak stayed in the water in adventure mode. A
   * body below the map floor (the sea, the void) is lifted above it so the spectator sees the round.
   */
  private void spectate(VbMatch m, UUID victim) {
    if (m.isBot(victim)) return;
    Runnable apply = () -> {
      org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(victim);
      // Late (async kill): the round or the match may have moved on, never spectate a living or restored player.
      if (p == null || m.alive().contains(victim) || m.hasLeft(victim) || m.vbPhase() == VbPhase.END) return;
      p.setGameMode(org.bukkit.GameMode.SPECTATOR);
      org.bukkit.Location at = p.getLocation();
      if (at.getY() < VbMapService.FEET_Y) {
        at.setY(VbMapService.FEET_Y + 8);
        p.teleport(at);
      }
    };
    if (org.bukkit.Bukkit.isPrimaryThread()) apply.run();
    else org.bukkit.Bukkit.getScheduler().runTask(plugin, apply);
  }
}
