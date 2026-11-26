package win.vampyuyu.valoblock;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;
import win.vampyuyu.werewolf.sky.SkyWorld;

/**
 * The map side of a ValoBlock match. Every match plays in the void world {@value #WORLD}: its map is
 * built from the plan (a greybox) at a slot, {@value #SPACING} blocks from the next one, and cleared
 * when the arena stops, so each match starts on a fresh copy. Also: the spawns (humans only, bots use the
 * waypoint graph), the spawn barriers of the buy phase and the ultimate orbs of the combat phase.
 * Block work runs on the server thread.
 */
public final class VbMapService implements MapService {
  public static final String WORLD = "vb_arena";
  public static final int SPACING = 256;
  public static final int MAX_SLOTS = 1000;
  /** Slot of the editor preview (/vb map preview), beyond the ones matches take. */
  public static final int PREVIEW_SLOT = MAX_SLOTS;
  /** Y of a player's feet standing on the floor. */
  public static final int FEET_Y = 65;

  /** One built map: who plays on it and where it is. */
  static final class Instance {
    final String arena;
    final int slot;
    final VbMap map;
    volatile VbMatch match;
    final List<ItemDisplay> orbs = new ArrayList<>();
    /** The set pieces of the build (item-display models), spawned once the blocks stand, removed with them. Server thread. */
    final List<ItemDisplay> models = new ArrayList<>();
    org.bukkit.scheduler.BukkitTask spin;
    volatile boolean barriersUp;
    /** Everything the themed build placed (set by {@link #build}); clearing sets the same blocks to air. */
    volatile VbCanvas canvas;
    /** Completes (server thread) when the slot has been wiped and the map built: nothing may be put on the map before. */
    final java.util.concurrent.CompletableFuture<Void> ready = new java.util.concurrent.CompletableFuture<>();
    /** The match is over: a build still waiting for the wipe is skipped. */
    volatile boolean released;

    Instance(String arena, int slot, VbMap map) {
      this.arena = arena;
      this.slot = slot;
      this.map = map;
    }

    int ox() {
      return slot * SPACING;
    }
  }

  private final NexusArenaEngine plugin;
  private final Map<String, Instance> instances = new ConcurrentHashMap<>();
  private final BitSet used = new BitSet();
  /** The map an administrator forced for the next match (/vb map use), or null: the players' vote decides. Used up by one match. */
  private volatile String forced;
  private volatile World world;
  final VbRealmService realm;
  private final Map<String, org.bukkit.block.data.BlockData> dataCache = new ConcurrentHashMap<>();
  private final VbSlotWiper wiper;
  final VbDirtySlots dirty;

  public VbMapService(NexusArenaEngine plugin) {
    this.plugin = plugin;
    this.wiper = new VbSlotWiper(plugin);
    this.dirty = new VbDirtySlots(plugin.getDataFolder().toPath().resolve("valoblock-dirty-slots.txt"));
    this.realm = new VbRealmService(plugin, this);
  }

  /** Orb pick-up check, five times a second. */
  public void start() {
    Bukkit.getScheduler().runTaskTimer(plugin, this::tickOrbs, 20, 4);
    realm.start();
    // Create the world once the arenas are loaded, so a start from the panel (not the server thread) finds it.
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (plugin.arenas.all().stream().anyMatch(a -> a.config.preset().equals(VbPreset.ID))) {
        String err = createWorld();
        if (err != null) plugin.getLogger().warning("ValoBlock: " + err);
        else wipeIdle("start");
      }
    });
  }

  /** The volume a slot can touch: the largest map footprint, the build height range and a margin. */
  VbSlots.Box slotBox(int slot) {
    int[] f = VbSlots.footprint(VbData.maps().values());
    return VbSlots.box(slot, SPACING, FEET_Y, f[0], f[1]);
  }

  private boolean matchRunning() {
    for (ArenaSession s : plugin.arenas.all()) if (s.config.preset().equals(VbPreset.ID) && s.game != null) return true;
    for (String k : instances.keySet()) if (!k.equals("preview")) return true;
    return false;
  }

  private void markDirty(int slot, boolean on) {
    try {
      if (on) dirty.mark(slot);
      else dirty.clear(slot);
    } catch (RuntimeException e) {
      plugin.getLogger().warning("ValoBlock: could not write the dirty-slot file (" + e + ")");
    }
  }

  /**
   * Wipes every slot that has blocks (the dirty ones, then a scan of all the slots) when no match is running. Used at start and on
   * /vb reload. Returns a message for the operator.
   */
  public String wipeIdle(String why) {
    World w = world();
    if (w == null) return "no ValoBlock world yet";
    if (matchRunning()) return "a match is running: nothing wiped";
    List<VbSlots.Box> boxes = new ArrayList<>();
    List<Integer> slots = new ArrayList<>();
    boolean previewUp = instances.containsKey("preview");
    for (int slot = 0; slot <= PREVIEW_SLOT; slot++) {
      if (slot == PREVIEW_SLOT && previewUp) continue;
      slots.add(slot);
    }
    List<Integer> found = new ArrayList<>();
    for (int slot : slots) {
      VbSlots.Box b = slotBox(slot);
      if (dirty.isDirty(slot) || !VbSlotWiper.withData(w, List.of(b)).isEmpty()) {
        boxes.add(b);
        found.add(slot);
      }
    }
    if (found.isEmpty()) return "no slot has blocks";
    for (int slot : found) markDirty(slot, true);
    plugin.getLogger().info("ValoBlock: wiping slots " + found + " (" + why + ")");
    wiper.wipe(w, boxes).whenComplete((v, e) -> {
      if (e != null) plugin.getLogger().warning("ValoBlock: wipe failed (" + e + "); the slots stay marked dirty");
      else for (int slot : found) markDirty(slot, false);
    });
    return "wiping " + found.size() + " slot(s)";
  }

  /** The map forced for the next match, or null when the vote (or chance) decides. */
  public String selected() {
    return forced;
  }

  /** Forces the map of the next match: it overrides the vote. */
  public void select(String id) {
    if (!VbData.maps().containsKey(id)) throw new IllegalArgumentException("Unknown map " + id + " (" + String.join(", ", VbData.maps().keySet()) + ")");
    forced = id;
  }

  /** The map of a match that starts now: the forced one (used up), else the vote of the lobby, else chance. Announced to the humans. */
  private VbMap choose(ArenaSession s) {
    String force = forced;
    forced = null;
    VbMapVoteService votes = plugin.valoblock.votes;
    MapVote.Result r = votes != null ? votes.resolve(s, force) : new MapVote(VbData.MAP_IDS).resolve(new Random(), force);
    VbMap map = VbData.maps().get(r.map());
    if (map == null) throw new IllegalStateException("Unknown map " + r.map());
    if (votes != null) onServer(() -> votes.announce(s, map, r));
    return map;
  }

  void onServer(Runnable r) {
    if (Bukkit.isPrimaryThread()) r.run();
    else Bukkit.getScheduler().runTask(plugin, r);
  }

  @Override
  public World world() {
    World w = world;
    if (w == null) {
      w = Bukkit.getWorld(WORLD);
      world = w;
    }
    return w;
  }

  /** Creates the world (server thread only) unless it exists. Returns null on success, else why not. */
  public String createWorld() {
    try {
      if (world() == null) {
        // Matches are ephemeral: blocks a crashed server left in an older copy are discarded first.
        deleteStale(Bukkit.getWorldContainer().toPath().resolve(WORLD));
        World created = new WorldCreator(WORLD).generator(new SkyWorld.VoidGenerator()).createWorld();
        if (created == null) return "The server refused to create the world " + WORLD;
        world = created;
      }
      World w = world;
      w.setDifficulty(Difficulty.PEACEFUL);
      w.setSpawnFlags(false, false);
      w.setStorm(false);
      w.setThundering(false);
      w.setTime(6000);
      try {
        w.setGameRule(GameRules.ADVANCE_TIME, false);
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.SPAWN_MOBS, false);
      } catch (Throwable ignored) {
        // Game rule API differences must not stop the match: peaceful and fixed spawn flags already hold.
      }
      return null;
    } catch (Throwable e) {
      return "Could not create the world " + WORLD + " (" + e + ")";
    }
  }

  private static void deleteStale(java.nio.file.Path dir) throws java.io.IOException {
    if (!java.nio.file.Files.isDirectory(dir)) return;
    try (var walk = java.nio.file.Files.walk(dir)) {
      for (var path : walk.sorted(Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
    }
  }

  @Override
  public String problem(ArenaSession s) {
    if (world() == null && !Bukkit.isPrimaryThread())
      return "The ValoBlock world does not exist yet: start the first match from the console (/vb start)";
    for (ArenaSession other : plugin.arenas.all())
      if (other != s && other.config.preset().equals(VbPreset.ID) && other.game != null)
        return "Another ValoBlock match is running (one at a time: they share the bot graph and sites)";
    return null;
  }

  private synchronized int allocate() {
    int i = used.nextClearBit(0);
    if (i >= MAX_SLOTS) throw new IllegalStateException("Every ValoBlock slot is in use");
    used.set(i);
    return i;
  }

  @Override
  public void prepare(ArenaSession s) {
    Instance old = instances.remove(s.config.id());
    if (old != null) clear(old);
    String err = Bukkit.isPrimaryThread() ? createWorld() : world() == null ? "The ValoBlock world does not exist yet" : null;
    if (err != null) throw new IllegalStateException(err);
    VbMap map = choose(s);
    Instance in = new Instance(s.config.id(), allocate(), map);
    instances.put(s.config.id(), in);
    onServer(() -> beginBuild(in));
  }

  /** Marks the slot dirty, wipes its whole volume (chunk by chunk), then builds the map; {@code ready} completes at the end. */
  private void beginBuild(Instance in) {
    World w = world();
    if (w == null) {
      in.ready.complete(null);
      return;
    }
    markDirty(in.slot, true);
    wiper.wipe(w, List.of(slotBox(in.slot))).whenComplete((v, e) -> onServer(() -> {
      if (e != null) plugin.getLogger().warning("ValoBlock: wiping slot " + in.slot + " failed (" + e + ")");
      if (in.released) {
        in.ready.complete(null);
        return;
      }
      CompletableFuture<Void> built;
      try {
        built = buildAsync(in);
      } catch (Throwable t) {
        built = CompletableFuture.failedFuture(t);
      }
      built.whenComplete((v2, t) -> onServer(() -> {
        if (t != null) plugin.getLogger().warning("ValoBlock: building slot " + in.slot + " failed (" + t + ")");
        in.ready.complete(null);
      }));
    }));
  }

  /** Blocks of a match build placed per tick: a whole map in one tick held the server thread for seconds. */
  static final int BLOCKS_PER_TICK = 30_000;

  /** The themed build of a map, computed once off the server thread (the builders are pure and deterministic). */
  private final Map<VbMap, CompletableFuture<VbCanvas>> canvases = Collections.synchronizedMap(new WeakHashMap<>());

  private CompletableFuture<VbCanvas> canvasOf(VbMap map) {
    CompletableFuture<VbCanvas> f = canvases.computeIfAbsent(map, m -> CompletableFuture.supplyAsync(() -> VbThemes.canvas(m)));
    f.whenComplete((c, e) -> {
      if (e != null) canvases.remove(map, f); // a failed build is tried again by the next match
    });
    return f;
  }

  /** Every chunk a map covers with its scenery (one past each edge), the same set the tickets hold. */
  private void forEachChunk(Instance in, java.util.function.BiConsumer<Integer, Integer> each) {
    int ox = in.ox(), s = VbMap.SCENERY_MARGIN + 1;
    for (int cx = Math.floorDiv(ox - s, 16); cx <= Math.floorDiv(ox + in.map.width() + s, 16); cx++)
      for (int cz = Math.floorDiv(-s, 16); cz <= Math.floorDiv(in.map.depth() + s, 16); cz++) each.accept(cx, cz);
  }

  /**
   * A match build that never holds the server thread: the chunks are loaded (generated on a fresh world) in the
   * background, the canvas is computed off-thread, then the blocks go down {@link #BLOCKS_PER_TICK} per tick.
   * Before, a fresh ValoBlock world generated every chunk synchronously and the watchdog fired.
   */
  private CompletableFuture<Void> buildAsync(Instance in) {
    World w = world();
    if (w == null) return CompletableFuture.completedFuture(null);
    List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
    forEachChunk(in, (cx, cz) -> chunks.add(w.getChunkAtAsync(cx, cz)));
    CompletableFuture<VbCanvas> canvas = canvasOf(in.map);
    return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenCombine(canvas, (v, c) -> c).thenCompose(c -> {
      CompletableFuture<Void> done = new CompletableFuture<>();
      onServer(() -> {
        if (in.released) {
          done.complete(null);
          return;
        }
        tickets(in, true); // the chunks are loaded: no synchronous load here any more
        in.canvas = c;
        place(w, in, c, done);
      });
      return done;
    });
  }

  private record Placed(int x, int y, int z, String data) {}

  private void place(World w, Instance in, VbCanvas canvas, CompletableFuture<Void> done) {
    List<Placed> todo = new ArrayList<>(canvas.size());
    canvas.emit((x, y, z, d) -> todo.add(new Placed(x, y, z, d)));
    int ox = in.ox();
    new org.bukkit.scheduler.BukkitRunnable() {
      int next;

      @Override
      public void run() {
        try {
          int end = Math.min(todo.size(), next + BLOCKS_PER_TICK);
          for (; next < end; next++) {
            Placed b = todo.get(next);
            w.getBlockAt(ox + b.x(), FEET_Y + b.y(), b.z()).setBlockData(blockData(b.data()), false);
          }
          if (next >= todo.size()) {
            cancel();
            spawnModels(w, in, canvas);
            done.complete(null);
          }
        } catch (Throwable t) {
          cancel();
          done.completeExceptionally(t);
        }
      }
    }.runTaskTimer(plugin, 0, 1);
  }

  /** Runs on the server thread once the map of the instance stands. */
  private void whenReady(Instance in, Runnable r) {
    in.ready.thenRun(() -> onServer(r));
  }

  /** Keeps the map's chunks loaded and ticking for the match: bots far from every human must still live. */
  private void tickets(Instance in, boolean on) {
    World w = world();
    if (w == null) return;
    forEachChunk(in, (cx, cz) -> {
      if (on) w.addPluginChunkTicket(cx, cz, plugin);
      else w.removePluginChunkTicket(cx, cz, plugin);
    });
  }

  /** The block data of a canvas string (cached); an unknown block becomes stone and is logged once. */
  private org.bukkit.block.data.BlockData blockData(String d) {
    return dataCache.computeIfAbsent(d, k -> {
      try {
        return Bukkit.createBlockData(k);
      } catch (IllegalArgumentException e) {
        plugin.getLogger().warning("ValoBlock: unknown block '" + k + "' in a map build");
        return Material.STONE.createBlockData();
      }
    });
  }

  /** Seconds between two steps of a spinning set piece: each step interpolates over the whole interval, so the turn is smooth. */
  private static final int SPIN_TICKS = 40;

  /** Spawns the set pieces of the build (server thread, once the blocks stand) and the task that turns the spinning ones. */
  private void spawnModels(World w, Instance in, VbCanvas canvas) {
    removeModels(in);
    if (in.released) return;
    List<VbCanvas.Model> list = canvas.models();
    if (list.isEmpty()) return;
    int ox = in.ox();
    for (VbCanvas.Model m : list) {
      Location at = new Location(w, ox + m.x(), FEET_Y + m.y(), m.z());
      ItemStack item = new ItemStack(Material.PAPER);
      var meta = item.getItemMeta();
      meta.setItemModel(new NamespacedKey("vampyuyu", "valoblock/map/" + m.id()));
      item.setItemMeta(meta);
      in.models.add(w.spawn(at, ItemDisplay.class, d -> {
        d.setPersistent(false);
        d.addScoreboardTag(VbSlotWiper.TAG);
        d.setItemStack(item);
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setViewRange(4f); // seen from the far side of the map
        d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // painted light: never in the shade of its own entity
        d.setTransformation(modelPose(m, 0));
      }));
    }
    in.spin = new org.bukkit.scheduler.BukkitRunnable() {
      long step;

      @Override
      public void run() {
        step++;
        for (int i = 0; i < in.models.size() && i < list.size(); i++) {
          VbCanvas.Model m = list.get(i);
          ItemDisplay d = in.models.get(i);
          if (m.spin() == 0 || !d.isValid()) continue;
          d.setInterpolationDelay(0);
          d.setInterpolationDuration(SPIN_TICKS);
          d.setTransformation(modelPose(m, step));
        }
      }
    }.runTaskTimer(plugin, SPIN_TICKS, SPIN_TICKS);
  }

  /** Pose of a set piece after {@code step} spin steps: its yaw plus the spin, its scale. */
  static org.bukkit.util.Transformation modelPose(VbCanvas.Model m, long step) {
    float deg = m.yaw() + m.spin() * (SPIN_TICKS / 20f) * step;
    var rot = new org.joml.Quaternionf().rotateY((float) Math.toRadians(-deg));
    return new org.bukkit.util.Transformation(new org.joml.Vector3f(), rot, new org.joml.Vector3f(m.scale(), m.scale(), m.scale()), new org.joml.Quaternionf());
  }

  private void removeModels(Instance in) {
    if (in.spin != null) in.spin.cancel();
    in.spin = null;
    for (ItemDisplay d : in.models) if (d.isValid()) d.remove();
    in.models.clear();
  }

  /**
   * Sets every block the build placed to air (and the buy-phase barriers), {@link #BLOCKS_PER_TICK} a tick like the build: with its
   * scenery a map is a few hundred thousand blocks, too many for one tick. Completes on the server thread.
   */
  private CompletableFuture<Void> erase(Instance in, World w) {
    removeModels(in);
    int ox = in.ox();
    VbCanvas canvas = in.canvas;
    org.bukkit.block.data.BlockData air = Material.AIR.createBlockData();
    in.map.barrierBlocks((x, y, z, k) -> w.getBlockAt(ox + x, FEET_Y + y, z).setBlockData(air, false));
    CompletableFuture<Void> done = new CompletableFuture<>();
    if (canvas == null) {
      done.complete(null);
      return done;
    }
    List<int[]> todo = new ArrayList<>(canvas.size());
    canvas.emit((x, y, z, d) -> todo.add(new int[] {x, y, z}));
    new org.bukkit.scheduler.BukkitRunnable() {
      int next;

      @Override
      public void run() {
        try {
          int end = Math.min(todo.size(), next + BLOCKS_PER_TICK);
          for (; next < end; next++) {
            int[] b = todo.get(next);
            w.getBlockAt(ox + b[0], FEET_Y + b[1], b[2]).setBlockData(air, false);
          }
          if (next >= todo.size()) {
            cancel();
            done.complete(null);
          }
        } catch (Throwable t) {
          cancel();
          done.completeExceptionally(t);
        }
      }
    }.runTaskTimer(plugin, 0, 1);
    return done;
  }

  private void clear(Instance in) {
    World w = world();
    in.released = true;
    clearOrbs(in);
    if (w == null) {
      synchronized (this) {
        used.clear(in.slot);
      }
      return;
    }
    // After the build (it may still be waiting for the wipe): the slot is free again only once its blocks are gone.
    whenReady(in, () -> {
      realm.stopNow(in); // gates open, effects off: the Realm rules never outlive the match
      erase(in, w).whenComplete((v, e) -> {
        if (e != null) plugin.getLogger().warning("ValoBlock: clearing slot " + in.slot + " failed (" + e + "); it stays marked dirty");
        tickets(in, false);
        if (e == null) markDirty(in.slot, false);
        synchronized (this) {
          used.clear(in.slot); // a dirty slot is wiped whole before its next build anyway
        }
      });
    });
  }

  @Override
  public void started(ArenaSession s, VbMatch match) {
    Instance in = instances.get(s.config.id());
    if (in == null) return;
    in.match = match;
    Waypoints graph = in.map.waypoints(in.ox(), FEET_Y, 0);
    VbData.activate(in.map.sites(in.ox(), FEET_Y, 0), graph);
    whenReady(in, () -> validateLinks(in, graph));
    // teleportToSpawn waits for the wipe and the build: nobody is sent to a map that is not there yet
    for (UUID id : match.participants()) if (!match.isBot(id)) teleportToSpawn(id, match.side(id));
  }

  /** Once the map stands: drops the graph links whose straight line crosses real solid blocks (bots walk lines, not paths). */
  private void validateLinks(Instance in, Waypoints graph) {
    World w = world();
    if (w == null || in.released) return;
    int ox = in.ox();
    LinkCheck.Solid solid = (x, y, z) -> {
      var b = w.getBlockAt(ox + x, FEET_Y + y, z);
      return !b.isPassable() && !org.bukkit.Tag.CLIMBABLE.isTagged(b.getType());
    };
    // Node coordinates of the graph are absolute: bring them back to the map's frame for the probe.
    List<String> report = graph.pruneLinks(l -> {
      Waypoints.Node a = graph.node(l[0]), b = graph.node(l[1]);
      return LinkCheck.blocked(shifted(a, ox), shifted(b, ox), solid);
    });
    long dropped = report.stream().filter(r -> r.startsWith("dropped")).count();
    plugin.getLogger().info("ValoBlock: " + in.map.id() + " link check: " + dropped + " link(s) crossing real blocks dropped");
    for (String r : report) if (r.startsWith("kept")) plugin.getLogger().warning("ValoBlock: " + r);
  }

  private static Waypoints.Node shifted(Waypoints.Node n, int ox) {
    return new Waypoints.Node(n.id(), n.x() - ox, n.y() - FEET_Y, n.z());
  }

  @Override
  public void release(ArenaSession s) {
    Instance in = instances.remove(s.config.id());
    if (in == null) return;
    in.match = null;
    onServer(() -> realm.stopNow(in));
    VbData.deactivate();
    clear(in);
  }

  @Override
  public boolean covers(ArenaSession s, Location where) {
    Instance in = instances.get(s.config.id());
    if (in == null || where.getWorld() == null || !where.getWorld().getName().equals(WORLD)) return false;
    double x = where.getX() - in.ox();
    return x >= -2 && x <= in.map.width() + 2 && where.getZ() >= -2 && where.getZ() <= in.map.depth() + 2;
  }

  /** Where a match is built: its map and the x of its min corner (z and the floor level are {@code 0} and {@link #FEET_Y}). */
  record Stage(VbMap map, int ox) {}

  /** The stage of a running match, or null (not built yet, or already released). */
  Stage stageOf(VbMatch m) {
    Instance in = instanceOf(m);
    return in == null ? null : new Stage(in.map, in.ox());
  }

  private Instance instanceOf(VbMatch m) {
    for (Instance in : instances.values()) if (in.match == m) return in;
    return null;
  }

  @Override
  public void teleportToSpawn(UUID player, VbSide side) {
    ArenaSession s = plugin.arenas.session(player);
    if (s == null || !(s.game instanceof VbMatch m)) return;
    Instance in = instances.get(s.config.id());
    if (in == null) return;
    int index = 0;
    for (UUID other : m.squad(m.squadOf(player))) {
      if (other.equals(player)) break;
      if (!m.isBot(other)) index++;
    }
    double[] at = in.map.spawn(side, index, in.ox() + 0.0, FEET_Y, 0);
    float yaw = side == VbSide.SEEDBEARERS ? 180f : 0f; // Seedbearers spawn at the south edge, Sealers at the north
    GameMode mode = s.config.gameMode();
    whenReady(in, () -> {
      Player p = Bukkit.getPlayer(player);
      World w = world();
      if (p == null || w == null) return;
      // The engine spectates players who are not alive(); a new round brings everyone back.
      p.setGameMode(mode);
      var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
      p.setHealth(max == null ? 20 : max.getValue());
      p.setFoodLevel(20);
      p.setFireTicks(0);
      p.setFallDistance(0);
      p.setVelocity(new org.bukkit.util.Vector());
      // The engine cancels teleports of arena players unless they are allowed.
      plugin.arenas.allowTeleport(player, true);
      p.teleportAsync(new Location(w, at[0], at[1], at[2], yaw, 0)).whenComplete((ok, err) -> plugin.arenas.allowTeleport(player, false));
    });
  }

  @Override
  public void phaseChanged(VbMatch match, VbPhase from, VbPhase to) {
    Instance in = instanceOf(match);
    if (in == null) return;
    if (to == VbPhase.BUY) {
      barriers(in, true);
      clearOrbs(in);
    }
    if (to == VbPhase.COMBAT) {
      barriers(in, false);
      onServer(() -> placeOrbs(in));
    }
    if (to == VbPhase.ROUND_END || to == VbPhase.END) clearOrbs(in);
    realm.phaseChanged(in, from, to);
  }

  private void barriers(Instance in, boolean up) {
    in.barriersUp = up;
    World w = world();
    if (w == null) return;
    int ox = in.ox();
    Material m = up ? Material.BARRIER : Material.AIR;
    onServer(() -> in.map.barrierBlocks((x, y, z, k) -> w.getBlockAt(ox + x, FEET_Y + y, z).setType(m, false)));
  }

  private void placeOrbs(Instance in) {
    World w = world();
    if (w == null || in.match == null) return;
    clearOrbs(in);
    for (double[] p : in.map.orbs()) {
      Location at = new Location(w, in.ox() + p[0], FEET_Y + p[1] + 1.0, p[2]);
      ItemDisplay d = w.spawn(at, ItemDisplay.class, e -> {
        e.setItemStack(new ItemStack(Material.AMETHYST_SHARD));
        e.setGlowing(true);
        e.setGlowColorOverride(Color.fromRGB(0xc9a8ff));
        e.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        e.setPersistent(false);
        e.addScoreboardTag(VbSlotWiper.TAG);
      });
      synchronized (in.orbs) {
        in.orbs.add(d);
      }
    }
  }

  private void clearOrbs(Instance in) {
    List<ItemDisplay> copy;
    synchronized (in.orbs) {
      copy = new ArrayList<>(in.orbs);
      in.orbs.clear();
    }
    if (copy.isEmpty()) return;
    onServer(() -> copy.forEach(d -> {
      if (d.isValid()) d.remove();
    }));
  }

  private void tickOrbs() {
    for (Instance in : instances.values()) {
      VbMatch m = in.match;
      if (m == null) continue;
      List<ItemDisplay> list;
      synchronized (in.orbs) {
        if (in.orbs.isEmpty()) continue;
        list = new ArrayList<>(in.orbs);
      }
      Set<UUID> alive = m.alive();
      for (ItemDisplay d : list) {
        if (!d.isValid()) continue;
        for (UUID id : m.participants()) {
          if (m.isBot(id) || !alive.contains(id)) continue;
          Player p = Bukkit.getPlayer(id);
          if (p == null || p.getWorld() != d.getWorld() || p.getLocation().distanceSquared(d.getLocation()) > 2.5 * 2.5) continue;
          synchronized (in.orbs) {
            if (!in.orbs.remove(d)) break;
          }
          d.remove();
          p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.4f);
          plugin.valoblock.agents.orb(m, id);
          break;
        }
      }
    }
  }

  // ------------------------------------------------------------------ editor

  /** Builds a map at the preview slot (in the background) and completes with the Seedbearer spawn. Server thread. */
  public CompletableFuture<Location> preview(String mapId) {
    String err = createWorld();
    if (err != null) throw new IllegalStateException(err);
    VbMap map = VbData.maps().get(mapId);
    if (map == null) throw new IllegalArgumentException("Unknown map " + mapId);
    Instance old = instances.remove("preview");
    CompletableFuture<Void> gone = old != null ? in_clear(old) : CompletableFuture.completedFuture(null);
    Instance in = new Instance("preview", PREVIEW_SLOT, map);
    instances.put("preview", in);
    markDirty(PREVIEW_SLOT, true);
    // the old preview is erased first: its erase must not eat the new build
    return gone.exceptionally(e -> null).thenCompose(v -> buildAsync(in)).thenApply(v -> {
      in.ready.complete(null);
      double[] at = map.spawn(VbSide.SEEDBEARERS, 0, in.ox(), FEET_Y, 0);
      return new Location(world(), at[0], at[1], at[2], 180f, 0f);
    });
  }

  private CompletableFuture<Void> in_clear(Instance in) {
    World w = world();
    if (w == null) return CompletableFuture.completedFuture(null);
    in.released = true;
    return erase(in, w).thenRun(() -> tickets(in, false));
  }

  /** The preview's map and where a player stands in it (relative coordinates), or null when not in the preview. */
  public double[] previewPosition(Location l) {
    Instance in = instances.get("preview");
    if (in == null || l.getWorld() == null || !l.getWorld().getName().equals(WORLD)) return null;
    return new double[] {l.getX() - in.ox(), l.getY() - FEET_Y, l.getZ()};
  }

  public String previewMap() {
    Instance in = instances.get("preview");
    return in == null ? null : in.map.id();
  }

  /** Plugin disable: clear every instance. */
  public void shutdown() {
    realm.stopAll();
    for (Instance in : instances.values()) {
      markDirty(in.slot, true); // stopped with the map standing: the next start wipes it
      clearOrbs(in);
      removeModels(in);
      tickets(in, false);
    }
  }
}
