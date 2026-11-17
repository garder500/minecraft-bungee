package win.vampyuyu.valoblock;

import java.util.*;
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
    volatile boolean barriersUp;
    /** Everything the themed build placed (set by {@link #build}); clearing sets the same blocks to air. */
    volatile VbCanvas canvas;

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
  private volatile String selected = VbData.MAP_IDS.get(0);
  private volatile World world;
  final VbRealmService realm;
  private final Map<String, org.bukkit.block.data.BlockData> dataCache = new ConcurrentHashMap<>();

  public VbMapService(NexusArenaEngine plugin) {
    this.plugin = plugin;
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
      }
    });
  }

  public String selected() {
    return selected;
  }

  public void select(String id) {
    if (!VbData.maps().containsKey(id)) throw new IllegalArgumentException("Unknown map " + id + " (" + String.join(", ", VbData.maps().keySet()) + ")");
    selected = id;
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
    VbMap map = VbData.maps().get(selected);
    Instance in = new Instance(s.config.id(), allocate(), map);
    instances.put(s.config.id(), in);
    onServer(() -> build(in));
  }

  /** Keeps the map's chunks loaded and ticking for the match: bots far from every human must still live. */
  private void tickets(Instance in, boolean on) {
    World w = world();
    if (w == null) return;
    int ox = in.ox();
    for (int cx = Math.floorDiv(ox - 1, 16); cx <= Math.floorDiv(ox + in.map.width() + 1, 16); cx++)
      for (int cz = Math.floorDiv(-1, 16); cz <= Math.floorDiv(in.map.depth() + 1, 16); cz++) {
        if (on) w.addPluginChunkTicket(cx, cz, plugin);
        else w.removePluginChunkTicket(cx, cz, plugin);
      }
  }

  private void build(Instance in) {
    World w = world();
    if (w == null) return;
    tickets(in, true);
    int ox = in.ox();
    VbCanvas canvas = VbThemes.canvas(in.map);
    in.canvas = canvas;
    canvas.emit((x, y, z, d) -> w.getBlockAt(ox + x, FEET_Y + y, z).setBlockData(blockData(d), false));
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

  /** Sets every block the build placed to air (and the buy-phase barriers). */
  private void erase(Instance in, World w) {
    int ox = in.ox();
    VbCanvas canvas = in.canvas;
    org.bukkit.block.data.BlockData air = Material.AIR.createBlockData();
    if (canvas != null) canvas.emit((x, y, z, d) -> w.getBlockAt(ox + x, FEET_Y + y, z).setBlockData(air, false));
    in.map.barrierBlocks((x, y, z, k) -> w.getBlockAt(ox + x, FEET_Y + y, z).setBlockData(air, false));
  }

  private void clear(Instance in) {
    World w = world();
    synchronized (this) {
      used.clear(in.slot);
    }
    clearOrbs(in);
    if (w == null) return;
    int ox = in.ox();
    onServer(() -> {
      realm.stopNow(in); // gates open, effects off: the Realm rules never outlive the match
      erase(in, w);
      tickets(in, false);
    });
  }

  @Override
  public void started(ArenaSession s, VbMatch match) {
    Instance in = instances.get(s.config.id());
    if (in == null) return;
    in.match = match;
    VbData.activate(in.map.sites(in.ox(), FEET_Y, 0), in.map.waypoints(in.ox(), FEET_Y, 0));
    for (UUID id : match.participants()) if (!match.isBot(id)) teleportToSpawn(id, match.side(id));
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
    onServer(() -> {
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

  /** Builds a map at the preview slot and returns where its origin is (x of the min corner). Server thread. */
  public Location preview(String mapId) {
    String err = createWorld();
    if (err != null) throw new IllegalStateException(err);
    VbMap map = VbData.maps().get(mapId);
    if (map == null) throw new IllegalArgumentException("Unknown map " + mapId);
    Instance old = instances.remove("preview");
    if (old != null) {
      in_clear(old);
    }
    Instance in = new Instance("preview", PREVIEW_SLOT, map);
    instances.put("preview", in);
    build(in);
    double[] at = map.spawn(VbSide.SEEDBEARERS, 0, in.ox(), FEET_Y, 0);
    return new Location(world(), at[0], at[1], at[2], 180f, 0f);
  }

  private void in_clear(Instance in) {
    World w = world();
    if (w == null) return;
    erase(in, w);
    tickets(in, false);
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
      clearOrbs(in);
      tickets(in, false);
    }
  }
}
