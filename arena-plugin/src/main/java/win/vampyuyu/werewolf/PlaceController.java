package win.vampyuyu.werewolf;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.sky.PlatformHandle;

/**
 * Owns the spatial side of a game: the seats around the campfire of the sky platform (sit or
 * stand), the practice bots' bodies, and the night effects. Game state stays Bukkit-free.
 */
public final class PlaceController {
  private final NexusArenaEngine plugin;
  private final Set<UUID> releasing = ConcurrentHashMap.newKeySet();

  public PlaceController(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  private <T> CompletableFuture<T> region(World world, double x, double z, Supplier<T> action) {
    CompletableFuture<T> f = new CompletableFuture<>();
    Bukkit.getRegionScheduler()
        .execute(
            plugin,
            world,
            (int) Math.floor(x) >> 4,
            (int) Math.floor(z) >> 4,
            () -> {
              try {
                f.complete(action.get());
              } catch (Throwable e) {
                f.completeExceptionally(e);
              }
            });
    return f;
  }

  private static final Set<Material> HAZARDS =
      Set.of(
          Material.LAVA,
          Material.FIRE,
          Material.SOUL_FIRE,
          Material.MAGMA_BLOCK,
          Material.CACTUS,
          Material.CAMPFIRE,
          Material.SOUL_CAMPFIRE,
          Material.POWDER_SNOW,
          Material.SWEET_BERRY_BUSH,
          Material.WITHER_ROSE,
          Material.NETHER_PORTAL,
          Material.END_PORTAL,
          Material.END_GATEWAY);

  /** Why standing at this point would be unsafe, or null. Must run on the owning region thread. */
  private static String unsafe(Location l) {
    var feet = l.getBlock();
    // A point on the surface of a stair or slab (half a block up) is inside that block's cell but
    // stands on it: the real feet are in the cell above it.
    if (!feet.isPassable() && !feet.isLiquid()) {
      double top = 0;
      for (var box : feet.getCollisionShape().getBoundingBoxes()) top = Math.max(top, box.getMaxY());
      if (top > 0 && l.getY() - feet.getY() >= top - 0.01) feet = feet.getRelative(0, 1, 0);
    }
    var head = feet.getRelative(0, 1, 0);
    var floor = feet.getRelative(0, -1, 0);
    if (!l.getWorld().getWorldBorder().isInside(l)) return "outside the world border";
    if (!feet.isPassable() || feet.isLiquid()) return "feet are inside " + feet.getType().name();
    if (!head.isPassable() || head.isLiquid()) return "head is inside " + head.getType().name();
    if (!floor.getType().isSolid()) return "no solid floor (" + floor.getType().name() + " below)";
    for (var block : List.of(feet, head, floor))
      if (HAZARDS.contains(block.getType())) return "hazard " + block.getType().name();
    return null;
  }

  public boolean sleeping(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && s.game instanceof WerewolfGame game
        && !WerewolfSession.of(s).seats.isEmpty()
        && game.sleeping(p.getUniqueId());
  }

  /** True while the player must stay on the spot the game gave them. */
  public boolean pinned(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && s.game instanceof WerewolfGame
        && WerewolfSession.of(s).seats.containsKey(p.getUniqueId())
        && !s.stopping
        && !s.finishing;
  }

  /** The chair under a seated player, so dismounting can be refused. */
  public boolean seated(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && WerewolfSession.of(s).chairs.containsKey(p.getUniqueId())
        && !s.stopping
        && !s.finishing
        && !releasing.contains(p.getUniqueId());
  }

  /** Assigns the platform's seats at random and gives the practice bots their bodies. */
  public CompletableFuture<Void> prepare(ArenaSession s, PlatformHandle platform) {
    WerewolfSession.of(s).seats.clear();
    WerewolfSession.of(s).placed.clear();
    if (!(s.game instanceof WerewolfGame wolf))
      return CompletableFuture.completedFuture(null);
    SeatPlan plan;
    try {
      plan =
          SeatPlan.assign(
              s.players.keySet(),
              platform.seats().stream().map(at -> new SeatSpot(at, true)).toList(),
              new Random());
    } catch (IllegalArgumentException e) {
      return CompletableFuture.failedFuture(e);
    }
    WerewolfSession.of(s).seats.putAll(plan.seats());
    wolf.ring(plan.ring());
    return spawnBodies(s);
  }

  /** Practice bots get a mannequin at their seat: something to look at and click. */
  private CompletableFuture<Void> spawnBodies(ArenaSession s) {
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (UUID bot : s.bots) {
      SeatSpot seat = WerewolfSession.of(s).seats.get(bot);
      if (seat == null) continue;
      Location at = seat.at().clone();
      World w = at.getWorld();
      String name = s.players.getOrDefault(bot, "Bot");
      tasks.add(
          region(
              w,
              at.getX(),
              at.getZ(),
              () -> {
                Mannequin body =
                    w.spawn(
                        at,
                        Mannequin.class,
                        m -> {
                          m.setImmovable(true);
                          m.setInvulnerable(true);
                          m.setAI(false);
                          m.setSilent(true);
                          m.setPersistent(false);
                          m.setDescription(Component.empty());
                          m.customName(Component.text(name));
                          m.setCustomNameVisible(true);
                        });
                WerewolfSession.of(s).bodies.put(bot, body);
                return null;
              }));
    }
    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
  }

  /** Removes the bots' bodies and the chairs; safe to call twice. */
  public CompletableFuture<Void> restore(ArenaSession s) {
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (var entry : List.copyOf(WerewolfSession.of(s).chairs.entrySet())) {
      releasing.add(entry.getKey());
      tasks.add(removeEntity(entry.getValue()).whenComplete((v, e) -> releasing.remove(entry.getKey())));
    }
    for (var body : List.copyOf(WerewolfSession.of(s).bodies.values())) tasks.add(removeEntity(body));
    WerewolfSession.of(s).chairs.clear();
    WerewolfSession.of(s).bodies.clear();
    WerewolfSession.of(s).seats.clear();
    WerewolfSession.of(s).placed.clear();
    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> removeEntity(Entity e) {
    CompletableFuture<Void> f = new CompletableFuture<>();
    // run() returns null (and calls neither callback) for an entity that is already gone, e.g. a
    // practice body whose chunk unloaded once the last human left the sky world.
    var handle = e.getScheduler()
        .run(
            plugin,
            t -> {
              e.remove();
              f.complete(null);
            },
            () -> f.complete(null));
    if (handle == null) f.complete(null);
    return f;
  }

  public CompletableFuture<Void> present(ArenaSession s, String previous) {
    if (!(s.game instanceof WerewolfGame game)) return CompletableFuture.completedFuture(null);
    String phase = game.phase();
    boolean night = game.night();
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (UUID id : s.players.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        if (s.bots.contains(id) && !game.alive().contains(id)) dropBody(s, id);
        continue;
      }
      boolean asleep = game.sleeping(id);
      CompletableFuture<Void> move = CompletableFuture.completedFuture(null);
      // Players are seated once, when the game opens; nobody is moved afterwards.
      if (WerewolfSession.of(s).seats.containsKey(id) && game.alive().contains(id) && WerewolfSession.of(s).placed.putIfAbsent(id, "seat") == null)
        move = move.thenCompose(v -> goTo(p, s, id));
      tasks.add(
          move.thenCompose(
              v ->
                  plugin.onPlayer(
                      p,
                      () -> {
                        if (s.game != game || s.stopping) return null;
                        if (asleep) {
                          // The pack blacks the screen out itself; these cover players without it.
                          p.addPotionEffect(
                              new PotionEffect(
                                  PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
                          p.addPotionEffect(
                              new PotionEffect(
                                  PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false));
                        } else {
                          p.removePotionEffect(PotionEffectType.BLINDNESS);
                          p.removePotionEffect(PotionEffectType.DARKNESS);
                        }
                        p.setPlayerTime(night ? 18000 : 1000, false);
                        conceal(p, s, game);
                        return null;
                      })));
    }
    for (UUID bot : s.bots) if (!game.alive().contains(bot)) tasks.add(dropBody(s, bot));
    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> dropBody(ArenaSession s, UUID bot) {
    Entity body = WerewolfSession.of(s).bodies.remove(bot);
    return body == null ? CompletableFuture.completedFuture(null) : removeEntity(body);
  }

  private CompletableFuture<Void> goTo(Player p, ArenaSession s, UUID id) {
    var seat = WerewolfSession.of(s).seats.get(id);
    var platform = WerewolfSession.of(s).platform;
    if (seat == null || platform == null) return CompletableFuture.completedFuture(null);
    final Location destination = seat.at().clone();
    final boolean seatIt = seat.sit();
    return release(s, p)
        // The player's own border follows them first: the zone's border would refuse the sky.
        .thenCompose(v -> plugin.onPlayer(p, () -> fence(p, platform)))
        .thenCompose(v -> teleport(p, s, destination))
        .thenCompose(v -> seatIt ? sitDown(p, s, destination) : CompletableFuture.completedFuture(null));
  }

  /** A border inside the platform, only this player sees it (the snapshot restore removes it). */
  private Void fence(Player p, PlatformHandle platform) {
    WorldBorder border = Bukkit.createWorldBorder();
    border.setCenter(platform.slot().centreX() + 0.5, platform.slot().centreZ() + 0.5);
    // A safety net inside the rim: seated players do not move, but nobody may reach the edge.
    border.setSize((platform.platformRadius() - 1.5) * 2);
    border.setWarningDistance(0);
    border.setDamageAmount(0);
    p.setWorldBorder(border);
    return null;
  }

  /** Gets the player off their chair, if they are on one. */
  private CompletableFuture<Void> release(ArenaSession s, Player p) {
    Entity chair = WerewolfSession.of(s).chairs.remove(p.getUniqueId());
    if (chair == null) return CompletableFuture.completedFuture(null);
    releasing.add(p.getUniqueId());
    return removeEntity(chair).whenComplete((v, e) -> releasing.remove(p.getUniqueId()));
  }

  private CompletableFuture<Void> sitDown(Player p, ArenaSession s, Location at) {
    World w = at.getWorld();
    return region(
        w,
        at.getX(),
        at.getZ(),
        () -> {
          Location low = at.clone().add(0, 0.55, 0);
          ArmorStand chair =
              w.spawn(
                  low,
                  ArmorStand.class,
                  c -> {
                    c.setInvisible(true);
                    c.setMarker(true);
                    c.setGravity(false);
                    c.setSmall(true);
                    c.setInvulnerable(true);
                    c.setPersistent(false);
                  });
          WerewolfSession.of(s).chairs.put(p.getUniqueId(), chair);
          return null;
        })
        .thenCompose(
            v ->
                plugin.onPlayer(
                    p,
                    () -> {
                      Entity chair = WerewolfSession.of(s).chairs.get(p.getUniqueId());
                      if (chair != null) chair.addPassenger(p);
                      return null;
                    }));
  }

  /**
   * Who sees whom. A sleeper sees nobody (no player, no practice body, no name tag): the pack's
   * screen goes fully black too. Whoever is awake sees every living player, so they can aim at them.
   * The eliminated are hidden from the living; spectators see everyone.
   */
  private void conceal(Player viewer, ArenaSession s, WerewolfGame game) {
    boolean viewerAlive = game.alive().contains(viewer.getUniqueId());
    boolean viewerAsleep = game.sleeping(viewer.getUniqueId());
    for (UUID other : s.players.keySet()) {
      Player o = Bukkit.getPlayer(other);
      if (o == null || other.equals(viewer.getUniqueId())) continue;
      // Whoever the hag sent away is only gone from view once the day is here: at night the pick is a secret.
      boolean hide =
          viewerAlive
              && (viewerAsleep
                  || !game.alive().contains(other)
                  || game.exiled(other) && !game.night());
      try {
        if (hide) viewer.hidePlayer(plugin, o);
        else viewer.showPlayer(plugin, o);
      } catch (RuntimeException ignored) {
        // Visibility is cosmetic; never abort a phase transition over it.
      }
    }
    for (var body : WerewolfSession.of(s).bodies.entrySet()) {
      try {
        if (viewerAlive && (viewerAsleep || game.exiled(body.getKey()) && !game.night()))
          viewer.hideEntity(plugin, body.getValue());
        else viewer.showEntity(plugin, body.getValue());
      } catch (RuntimeException ignored) {
      }
    }
  }

  /** Makes a leaving player and everyone else visible to each other again. */
  public void unconceal(Player leaving, ArenaSession s) {
    for (UUID other : s.players.keySet()) {
      Player o = Bukkit.getPlayer(other);
      if (o == null || o == leaving) continue;
      try {
        leaving.showPlayer(plugin, o);
      } catch (RuntimeException ignored) {
      }
      plugin.onPlayer(
          o,
          () -> {
            try {
              o.showPlayer(plugin, leaving);
            } catch (RuntimeException ignored) {
            }
            return null;
          });
    }
    // The leaver's chair goes with them.
    Entity chair = WerewolfSession.of(s).chairs.remove(leaving.getUniqueId());
    if (chair != null) {
      releasing.add(leaving.getUniqueId());
      removeEntity(chair).whenComplete((v, e) -> releasing.remove(leaving.getUniqueId()));
    }
  }

  private CompletableFuture<Void> teleport(Player p, ArenaSession s, Location to) {
    return region(
            to.getWorld(),
            to.getX(),
            to.getZ(),
            () -> {
              String problem = unsafe(to);
              if (problem != null)
                throw new IllegalArgumentException("Unsafe place: " + problem);
              return to;
            })
        .thenCompose(
            l -> {
              if (s.stopping || s.game == null)
                return CompletableFuture.<Boolean>failedFuture(
                    new IllegalStateException("Arena stopping"));
              plugin.arenas.allowTeleport(p.getUniqueId(), true);
              return p.teleportAsync(l);
            })
        .thenAccept(
            ok -> {
              if (!ok) throw new IllegalStateException("Place teleport refused");
            })
        .whenComplete((v, e) -> plugin.arenas.allowTeleport(p.getUniqueId(), false));
  }
}
