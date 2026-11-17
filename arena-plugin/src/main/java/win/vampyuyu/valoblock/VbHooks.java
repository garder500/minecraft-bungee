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
    if (module.disabled()) return ValoBlockModule.FOLIA_REFUSAL;
    return module.map.problem(s);
  }

  @Override
  public boolean ownsInventory(ArenaSession s, org.bukkit.inventory.Inventory inventory) {
    var holder = inventory.getHolder();
    // Every ValoBlock window must be listed: the arena cancels the opening of any other inventory.
    return holder instanceof AgentSelectMenu || holder instanceof ArmoryMenu || holder instanceof TacMapService.Menu
        || holder instanceof MapVoteMenu;
  }

  /** Players reopen their menus by command (/vb agent, /vb buy, /vb tac, Quick Actions key). */
  @Override
  public boolean allowsCommand(String command) {
    // Everyone: reopen their menus. Admins (VbCommand checks the permission): run the match they are playing in.
    return command.equals("/vb agent") || command.equals("/vb buy") || command.equals("/vb vote") || command.equals("/vb tac")
        || command.equals("/vb map use") || command.startsWith("/vb map use ") // an admin forces the map for the next match (the vote is for everyone)
        || command.equals("/vb start") || command.equals("/vb stop") || command.equals("/vb status");
  }

  /** The HUD shows the phase and the result; the engine's chat lines talk about the compass (Werewolf). */
  @Override
  public boolean announcesInChat() {
    return false;
  }

  @Override
  public void beforeStart(ArenaSession s) {
    module.map.prepare(s);
  }

  @Override
  public void onStart(ArenaSession s) {
    if (s.game instanceof VbMatch m) module.map.started(s, m);
  }

  @Override
  public void onJoin(ArenaSession s, org.bukkit.entity.Player p) {
    VbPack pack = module.pack;
    if (module.menus != null) module.menus.forget(p.getUniqueId()); // a join or rejoin is offered the menu again
    if (pack != null) pack.sendSoon(p);
    // The lobby spawn may sit inside blocks (a map built over it): lift the player to the nearest safe spot.
    org.bukkit.Location here = p.getLocation();
    org.bukkit.Location safe = SafeSpawn.lifted(here.getWorld(), here);
    if (safe == null) module.plugin.getLogger().warning("ValoBlock: the spawn of arena " + s.config.id() + " is inside blocks and no free spot was found above it");
    else if (safe != here) {
      module.plugin.getLogger().warning("ValoBlock: the spawn of arena " + s.config.id() + " was inside blocks, moved " + p.getName() + " up to y=" + safe.getBlockY());
      p.teleport(safe);
    }
  }

  @Override
  public boolean hosts(ArenaSession s, org.bukkit.Location where) {
    return module.map.covers(s, where);
  }

  @Override
  public boolean tickGame(ArenaSession s, long now, boolean due) {
    // The match only changes phase through damage, holds and the engine's clock, which the engine
    // already follows; the HUD is drawn here once slice 5 provides it.
    if (s.game instanceof VbMatch m) {
      // Bots fill the empty seats after the select: a practice match, no rewards (EventRewards reads this).
      if (!s.practice && m.hasBots()) s.practice = true;
      module.hud.render(m, now, s.phaseEndsAt);
      module.tags.sync(module, m, now);
    }
    return false;
  }

  /**
   * The match is cleaned up here, while the session still has its game: by onStop the engine has
   * already dropped it (stopPlayers sets game to null), so nothing keyed on it would run.
   */
  @Override
  public java.util.concurrent.CompletableFuture<Void> concluding(ArenaSession s, String reason) {
    if (s.game instanceof VbMatch m) {
      module.hud.clear(m);
      module.tags.end(m);
      module.agents.matchEnded(m);
      for (java.util.UUID id : m.participants()) {
        if (module.menus != null) module.menus.forget(id);
        module.weapons.clear(id);
        module.agents.restore(id);
      }
      module.bots.matchEnded(m);
      if (module.pack != null) module.pack.removeAll(m.participants());
      for (java.util.UUID bot : m.bots()) module.bots.remove(bot);
    }
    return GameHooks.DONE;
  }

  /**
   * A player leaves mid-match with /arena leave and stays online: strip the ValoBlock items, drop the boss
   * bars and the action bar, give the skin back. The seat is released here too (idempotent), so the HUD
   * and the round start skip them while the snapshot is still being restored.
   */
  @Override
  public void onLeave(ArenaSession s, org.bukkit.entity.Player p) {
    java.util.UUID id = p.getUniqueId();
    if (module.menus != null) module.menus.forget(id);
    if (module.votes != null) module.votes.left(s, p);
    if (s.game instanceof VbMatch m) {
      m.remove(id);
      module.tags.left(m, p);
      module.hud.left(m, id);
    }
    module.weapons.clear(id);
    module.agents.restore(id);
    VbPack pack = module.pack;
    if (pack != null) pack.remove(p);
  }

  @Override
  public void onStop(ArenaSession s, Throwable error) {
    if (module.votes != null) module.votes.release(s);
    module.map.release(s);
  }
}
