package win.vampyuyu.arena;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;

/**
 * Owns the spatial side of a game: seats around the vote circle (sit or stand), the wolves' den,
 * night spots, the practice bots' bodies, and the night effects. Game state stays Bukkit-free.
 */
final class PlaceController {
  private final NexusArenaEngine plugin;
  private final Set<UUID> releasing = ConcurrentHashMap.newKeySet();

  PlaceController(NexusArenaEngine plugin) {
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

  private static String where(String what, double x, double y, double z) {
    return String.format(Locale.ROOT, "%s (%.0f, %.0f, %.0f)", what, x, y, z);
  }

  /** Everything that would make the places unusable, found without moving anyone. */
  CompletableFuture<List<String>> problems(ArenaConfig c) {
    var places = c.places();
    World w = Bukkit.getWorld(c.worldName());
    if (w == null)
      return CompletableFuture.completedFuture(List.of("World " + c.worldName() + " is not loaded"));
    List<CompletableFuture<String>> checks = new ArrayList<>();
    java.util.function.BiConsumer<String, ArenaConfig.Spawn> point =
        (name, p) -> {
          if (p == null) return;
          checks.add(
              region(
                  w,
                  p.x(),
                  p.z(),
                  () -> {
                    String why = unsafe(new Location(w, p.x(), p.y(), p.z()));
                    return why == null ? null : where(name, p.x(), p.y(), p.z()) + ": " + why;
                  }));
        };
    for (var seat : places.seats()) point.accept("Seat " + seat.name(), seat.at());
    point.accept("Vote circle", places.circle());
    return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
        .thenApply(
            v -> checks.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList());
  }

  /**
   * Height above the block's base where someone sits (or stands) on it: half a block on bottom
   * slabs and bottom stairs, a whole block on top ones and full blocks, the collision height otherwise.
   */
  static double surface(org.bukkit.block.Block block, boolean sit) {
    var data = block.getBlockData();
    if (data instanceof org.bukkit.block.data.type.Stairs stairs)
      return sit ? (stairs.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? 0.5 : 1.0) : 1.0;
    if (data instanceof org.bukkit.block.data.type.Slab slab)
      return slab.getType() == org.bukkit.block.data.type.Slab.Type.BOTTOM ? 0.5 : 1.0;
    double top = 0;
    for (var box : block.getCollisionShape().getBoundingBoxes()) top = Math.max(top, box.getMaxY());
    return top > 0 ? top : 1.0;
  }

  /** The seat or stand spot on the top of a block: its centre, at the surface height. */
  static ArenaConfig.Spawn onBlock(org.bukkit.block.Block block, boolean sit) {
    return new ArenaConfig.Spawn(block.getX() + 0.5, block.getY() + surface(block, sit), block.getZ() + 0.5, 0f, 0f);
  }

  boolean sleeping(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && s.game instanceof WerewolfGame game
        && !s.seats.isEmpty()
        && game.sleeping(p.getUniqueId());
  }

  /** True while the player must stay on the spot the game gave them. */
  boolean pinned(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && s.game instanceof WerewolfGame
        && s.seats.containsKey(p.getUniqueId())
        && !s.stopping
        && !s.finishing;
  }

  /** The chair under a seated player, so dismounting can be refused. */
  boolean seated(Player p) {
    var s = plugin.arenas.session(p.getUniqueId());
    return s != null
        && s.chairs.containsKey(p.getUniqueId())
        && !s.stopping
        && !s.finishing
        && !releasing.contains(p.getUniqueId());
  }

  /** Assigns the seats at random and checks they are usable. */
  CompletableFuture<Void> prepare(ArenaSession s) {
    var places = s.config.places();
    s.seats.clear();
    s.placed.clear();
    if (places.seats().isEmpty() || !(s.game instanceof WerewolfGame))
      return CompletableFuture.completedFuture(null);
    String missing = places.missing(s.players.size());
    if (missing != null)
      return CompletableFuture.failedFuture(new IllegalArgumentException(missing));
    return problems(s.config)
        .thenCompose(
            found -> {
              if (!found.isEmpty())
                return CompletableFuture.<Void>failedFuture(
                    new IllegalArgumentException(
                        "Places problems: "
                            + String.join("; ", found.stream().limit(4).toList())
                            + (found.size() > 4 ? " (+" + (found.size() - 4) + " more)" : "")));
              List<ArenaConfig.Seat> pool = new ArrayList<>(places.seats());
              Collections.shuffle(pool);
              int i = 0;
              for (UUID id : s.players.keySet()) s.seats.put(id, pool.get(i++));
              // The table order around the circle is the order of the configured seats.
              List<UUID> ring = new ArrayList<>(s.seats.keySet());
              ring.sort(Comparator.comparingInt(id -> places.seats().indexOf(s.seats.get(id))));
              if (s.game instanceof WerewolfGame wolf) wolf.ring(ring);
              return spawnBodies(s);
            });
  }

  /** Practice bots get a mannequin at their seat: something to look at and click. */
  private CompletableFuture<Void> spawnBodies(ArenaSession s) {
    World w = Bukkit.getWorld(s.config.worldName());
    if (w == null) return CompletableFuture.completedFuture(null);
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (UUID bot : s.bots) {
      ArenaConfig.Seat seat = s.seats.get(bot);
      if (seat == null) continue;
      Location at = faced(s, seat.at(), w);
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
                s.bodies.put(bot, body);
                return null;
              }));
    }
    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
  }

  /** A seat's position turned towards the vote circle, when there is one. */
  Location faced(ArenaSession s, ArenaConfig.Spawn p, World w) {
    float yaw = p.yaw();
    var circle = s.config.places().circle();
    if (circle != null) {
      double dx = circle.x() - p.x(), dz = circle.z() - p.z();
      if (dx * dx + dz * dz > 0.25) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
    return new Location(w, p.x(), p.y(), p.z(), yaw, circle != null ? 0f : p.pitch());
  }

  /** Removes the bots' bodies and the chairs; safe to call twice. */
  CompletableFuture<Void> restore(ArenaSession s) {
    List<CompletableFuture<?>> tasks = new ArrayList<>();
    for (var entry : List.copyOf(s.chairs.entrySet())) {
      releasing.add(entry.getKey());
      tasks.add(removeEntity(entry.getValue()).whenComplete((v, e) -> releasing.remove(entry.getKey())));
    }
    for (var body : List.copyOf(s.bodies.values())) tasks.add(removeEntity(body));
    s.chairs.clear();
    s.bodies.clear();
    s.seats.clear();
    s.placed.clear();
    return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
  }

  private CompletableFuture<Void> removeEntity(Entity e) {
    CompletableFuture<Void> f = new CompletableFuture<>();
    e.getScheduler()
        .run(
            plugin,
            t -> {
              e.remove();
              f.complete(null);
            },
            () -> f.complete(null));
    return f;
  }

  CompletableFuture<Void> present(ArenaSession s, String previous) {
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
      if (s.seats.containsKey(id) && game.alive().contains(id) && s.placed.putIfAbsent(id, "seat") == null)
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
    Entity body = s.bodies.remove(bot);
    return body == null ? CompletableFuture.completedFuture(null) : removeEntity(body);
  }

  private CompletableFuture<Void> goTo(Player p, ArenaSession s, UUID id) {
    World w = Bukkit.getWorld(s.config.worldName());
    var seat = s.seats.get(id);
    if (w == null || seat == null) return CompletableFuture.completedFuture(null);
    final Location destination = faced(s, seat.at(), w);
    final boolean seatIt = seat.sit();
    return release(s, p)
        .thenCompose(v -> teleport(p, s, destination))
        .thenCompose(v -> seatIt ? sitDown(p, s, destination) : CompletableFuture.completedFuture(null));
  }

  /** Gets the player off their chair, if they are on one. */
  private CompletableFuture<Void> release(ArenaSession s, Player p) {
    Entity chair = s.chairs.remove(p.getUniqueId());
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
          Location low = at.clone().subtract(0, 0.45, 0);
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
          s.chairs.put(p.getUniqueId(), chair);
          return null;
        })
        .thenCompose(
            v ->
                plugin.onPlayer(
                    p,
                    () -> {
                      Entity chair = s.chairs.get(p.getUniqueId());
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
      boolean hide = viewerAlive && (viewerAsleep || !game.alive().contains(other));
      try {
        if (hide) viewer.hidePlayer(plugin, o);
        else viewer.showPlayer(plugin, o);
      } catch (RuntimeException ignored) {
        // Visibility is cosmetic; never abort a phase transition over it.
      }
    }
    for (var body : s.bodies.entrySet()) {
      try {
        if (viewerAlive && viewerAsleep) viewer.hideEntity(plugin, body.getValue());
        else viewer.showEntity(plugin, body.getValue());
      } catch (RuntimeException ignored) {
      }
    }
  }

  /** Makes a leaving player and everyone else visible to each other again. */
  void unconceal(Player leaving, ArenaSession s) {
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
    Entity chair = s.chairs.remove(leaving.getUniqueId());
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
