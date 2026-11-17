package win.vampyuyu.valoblock;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The Rift Seed in the world (server thread, one task per tick): the item in hotbar slot 8 of the carrier, the plant hold, the
 * Seed on the carrier's back (shown to the carrier's squad only), and a dropped Seed that glows with a marker for Seedbearers
 * only. Who carries comes from polling {@link VbMatch#seedCarrier()} through a {@link SeedTracker}, so a death, a drop with Q, a
 * plant, a leave and a round end all end the same way: the item, the back display and the drop are put right. Who sees what is
 * {@link SeedSight}. Messages for the carrier go through the HUD service (it is the only writer of the action bar).
 */
public final class VbSeedService implements Listener {
  static final long HOLD_MS = 260, DROP_LOCK_MS = 2500;
  static final double PICKUP_RADIUS = 1.6;
  private static final TextColor PINK = TextColor.color(0xff4a8a);

  /** A dropped Seed: the plain model everyone sees, the glowing copy and the marker only Seedbearers see. */
  private static final class Drop {
    final ItemDisplay plain, glow;
    final TextDisplay marker;
    final Set<UUID> shown = new HashSet<>();
    final UUID by;
    final long lockUntil;
    final Location at;

    Drop(ItemDisplay plain, ItemDisplay glow, TextDisplay marker, UUID by, long lockUntil, Location at) {
      this.plain = plain;
      this.glow = glow;
      this.marker = marker;
      this.by = by;
      this.lockUntil = lockUntil;
      this.at = at;
    }

    void remove() {
      for (Display d : new Display[] {plain, glow, marker}) if (d != null && d.isValid()) d.remove();
    }
  }

  /** The planted Seed in the world: a big glowing spinning model, a marker, a light, a beam and a ticking fuse. Everything carries {@link #TAG}. */
  private static final class Planted {
    final ItemDisplay item;
    final TextDisplay marker;
    final Location at;
    final long since;
    final Block light;
    long nextTick, nextBeam, nextPulse;
    boolean big;
    float yaw;

    Planted(ItemDisplay item, TextDisplay marker, Location at, long since, Block light) {
      this.item = item;
      this.marker = marker;
      this.at = at;
      this.since = since;
      this.light = light;
    }

    void remove() {
      for (Display d : new Display[] {item, marker}) if (d != null && d.isValid()) d.remove();
      if (light != null && light.getType() == Material.LIGHT) light.setType(Material.AIR, false);
    }
  }

  static final String TAG = "vb_planted_seed";

  /** What one running match holds. */
  private static final class Track {
    final SeedTracker tracker = new SeedTracker();
    ItemDisplay back;
    final Set<UUID> backShown = new HashSet<>();
    Drop drop;
    double[] lastPos; // {x, y, z} of the carrier at the last look: where a death drops the Seed
    World lastWorld;
    long lastTick;
    final Set<UUID> inSite = new HashSet<>();
    long lastSound;
    long spin;
    Planted planted;
    UUID sealer; // the human who holds the seal this tick
    boolean sealCalled; // we told the match something last tick (so a release is reported once)
    long lastSealSound, lastSealTick;
    final Set<UUID> inReach = new HashSet<>();
    boolean cleared; // clear() ran and nothing came since: the per-tick calls between rounds do nothing
  }

  private final NexusArenaEngine plugin;
  private final Map<VbMatch, Track> tracks = new IdentityHashMap<>();
  private final Map<UUID, Long> lastRight = new HashMap<>();
  private final Map<UUID, Long> lastRaw = new HashMap<>();

  public VbSeedService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  public void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    Bukkit.getScheduler().runTask(plugin, VbSeedService::purgeLeftovers);
  }

  /** Removes every tagged planted-Seed display left in any world (a crash, a reload). */
  static void purgeLeftovers() {
    for (World w : Bukkit.getWorlds())
      for (Display d : w.getEntitiesByClass(Display.class)) if (d.getScoreboardTags().contains(TAG)) d.remove();
  }

  /** True while this human holds the seal of the planted Seed (the HUD draws the seal bar). */
  boolean sealing(VbMatch m, UUID id) {
    Track t = tracks.get(m);
    return t != null && id.equals(t.sealer) && m.vbPhase() == VbPhase.PLANTED;
  }

  /** Where the Seed lies dropped in this match, or null (carried, planted, or none). */
  Location droppedAt(VbMatch m) {
    Track t = tracks.get(m);
    return t == null || t.drop == null ? null : t.drop.at.clone();
  }

  private VbMatch matchOf(UUID id) {
    ArenaSession s = plugin.arenas.session(id);
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  // ------------------------------------------------------------------ input

  /** Right-click with the Seed: a click in the last 260 ms is "held" (the client repeats a held button every few ticks, like the guns). */
  @EventHandler(priority = EventPriority.NORMAL)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL || !SeedItems.isSeed(e.getItem())) return;
    if (matchOf(e.getPlayer().getUniqueId()) == null) return;
    e.setCancelled(true);
    if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) lastRight.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  /**
   * Any right-click of a player of a match, read first and even when the arena protection cancelled it (it cancels interacts and
   * entity interacts): this is what a Sealer holds to seal.
   */
  @EventHandler(priority = EventPriority.LOWEST)
  public void rawRight(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
    if (matchOf(e.getPlayer().getUniqueId()) != null) lastRaw.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void rawRightEntity(PlayerInteractEntityEvent e) {
    if (e.getHand() == EquipmentSlot.HAND && matchOf(e.getPlayer().getUniqueId()) != null) lastRaw.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
  }

  /** Q with the Seed in hand drops it where the carrier stands; any other Q of a Seed carrier is the weapon service's. */
  @EventHandler(priority = EventPriority.NORMAL)
  public void drop(PlayerDropItemEvent e) {
    if (!SeedItems.isSeed(e.getItemDrop().getItemStack())) return;
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    e.setCancelled(true); // the stack stays in the slot until the next tick takes it away; no loose item entity ever exists
    if (m.dropSeed(p.getUniqueId())) {
      Track t = tracks.get(m);
      if (t != null) t.lastPos = new double[] {p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()};
    }
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || matchOf(p.getUniqueId()) == null) return;
    boolean hotbarSwap = e.getHotbarButton() >= 0 && SeedItems.isSeed(p.getInventory().getItem(e.getHotbarButton()));
    if (SeedItems.isSeed(e.getCurrentItem()) || SeedItems.isSeed(e.getCursor()) || hotbarSwap) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p && matchOf(p.getUniqueId()) != null && SeedItems.isSeed(e.getOldCursor())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void swap(PlayerSwapHandItemsEvent e) {
    if (matchOf(e.getPlayer().getUniqueId()) == null) return;
    if (SeedItems.isSeed(e.getMainHandItem()) || SeedItems.isSeed(e.getOffHandItem())) e.setCancelled(true);
  }

  // ------------------------------------------------------------------ the tick

  private void tick() {
    Set<VbMatch> live = Collections.newSetFromMap(new IdentityHashMap<>());
    for (ArenaSession s : plugin.arenas.all()) {
      if (s.game instanceof VbMatch m) {
        live.add(m);
        look(m);
      }
    }
    for (Iterator<Map.Entry<VbMatch, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) {
      var e = it.next();
      if (live.contains(e.getKey())) continue;
      clear(e.getKey(), e.getValue());
      it.remove();
    }
  }

  private static boolean roundOn(VbPhase ph) {
    return ph == VbPhase.BUY || ph == VbPhase.COMBAT || ph == VbPhase.PLANTED;
  }

  private void look(VbMatch m) {
    Track t = tracks.computeIfAbsent(m, k -> new Track());
    long now = System.currentTimeMillis();
    VbPhase phase = m.vbPhase();
    if (!roundOn(phase)) {
      clear(m, t);
      t.tracker.reset();
      return;
    }
    t.cleared = false;
    UUID carrier = SeedSight.carrier(m);
    if (carrier != null) remember(m, t, carrier);
    // A Seedbearer walking over the dropped Seed picks it up.
    if (t.drop != null && phase == VbPhase.COMBAT && m.seedCarrier() == null) pickUps(m, t, now);
    carrier = SeedSight.carrier(m);
    SeedTracker.Change c = t.tracker.update(carrier, phase, m.plantedSite() != null);
    switch (c.kind()) {
      case GAINED -> gained(m, t, c);
      case DROPPED -> dropped(m, t, c, now);
      case LOST -> removeBack(t);
      default -> {}
    }
    if (carrier != null) {
      if (c.kind() == SeedTracker.Kind.GAINED && t.drop != null) removeDrop(t);
      backFollows(m, t, carrier);
    }
    items(m, carrier);
    if (carrier != null && !m.isBot(carrier)) plant(m, t, carrier, now);
    if (phase == VbPhase.PLANTED) planted(m, t, now);
    else removePlanted(t);
    if (t.drop != null) dropFollows(m, t, now);
    if (t.back == null) t.backShown.clear();
  }

  private void remember(VbMatch m, Track t, UUID carrier) {
    if (m.isBot(carrier)) {
      double[] pos = plugin.valoblock.bots.position(carrier);
      if (pos != null) t.lastPos = new double[] {pos[0], pos[1], pos[2]};
      return;
    }
    Player p = Bukkit.getPlayer(carrier);
    if (p == null) return;
    Location l = p.getLocation();
    t.lastPos = new double[] {l.getX(), l.getY(), l.getZ()};
    t.lastWorld = l.getWorld();
  }

  private World worldOf(Track t, VbMatch m) {
    if (t.lastWorld != null) return t.lastWorld;
    for (UUID id : m.participants()) {
      Player p = m.isBot(id) ? null : Bukkit.getPlayer(id);
      if (p != null) return p.getWorld();
    }
    return null;
  }

  // ------------------------------------------------------------------ changes

  private void gained(VbMatch m, Track t, SeedTracker.Change c) {
    Player p = c.to() == null || m.isBot(c.to()) ? null : Bukkit.getPlayer(c.to());
    if (p == null) return;
    if (c.roundStart()) {
      p.showTitle(Title.title(Component.text("You carry the Rift Seed", PINK), Component.text("Select it (slot 8), then hold right-click inside a site", NamedTextColor.GRAY),
          Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(500))));
      p.playSound(p.getLocation(), "block.amethyst_cluster.break", SoundCategory.PLAYERS, 0.8f, 0.6f);
    } else {
      plugin.valoblock.hud.notice(p, Component.text("You picked up the Rift Seed", PINK));
      p.playSound(p.getLocation(), "entity.item.pickup", SoundCategory.PLAYERS, 0.8f, 0.7f);
    }
  }

  private void dropped(VbMatch m, Track t, SeedTracker.Change c, long now) {
    removeBack(t);
    World w = worldOf(t, m);
    if (w == null || t.lastPos == null) return;
    Location at = ground(new Location(w, t.lastPos[0], t.lastPos[1], t.lastPos[2]));
    removeDrop(t);
    t.drop = spawnDrop(at, c.from(), now);
    w.playSound(at, "block.amethyst_block.break", SoundCategory.PLAYERS, 1.0f, 0.5f);
  }

  /** Where the Seed comes to rest: straight down from where the carrier was, up to 40 blocks. */
  private static Location ground(Location from) {
    RayTraceResult r = from.getWorld().rayTraceBlocks(from.clone().add(0, 0.5, 0), new org.bukkit.util.Vector(0, -1, 0), 40, org.bukkit.FluidCollisionMode.NEVER, true);
    return r == null ? from : r.getHitPosition().toLocation(from.getWorld());
  }

  private Drop spawnDrop(Location at, UUID by, long now) {
    World w = at.getWorld();
    Location base = at.clone().add(0, 0.45, 0);
    ItemDisplay plain = w.spawn(base, ItemDisplay.class, d -> style(d, 0.55f));
    ItemDisplay glow = w.spawn(base, ItemDisplay.class, d -> {
      style(d, 0.6f);
      d.setVisibleByDefault(false);
      d.setGlowing(true);
      d.setGlowColorOverride(Color.fromRGB(0xff4a8a));
    });
    TextDisplay marker = w.spawn(base.clone().add(0, 1.1, 0), TextDisplay.class, d -> {
      d.setPersistent(false);
      d.setVisibleByDefault(false);
      d.text(Component.text("▼", PINK));
      d.setBillboard(Display.Billboard.CENTER);
      d.setSeeThrough(true);
      d.setShadowed(true);
      d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
      d.setViewRange(2.0f);
      d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.6f, 1.6f, 1.6f), new AxisAngle4f()));
    });
    return new Drop(plain, glow, marker, by, now + DROP_LOCK_MS, at);
  }

  private void style(ItemDisplay d, float scale) {
    d.setPersistent(false);
    d.setItemStack(SeedItems.item());
    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
    d.setViewRange(1.5f);
    d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
    d.setTeleportDuration(1);
  }

  private void pickUps(VbMatch m, Track t, long now) {
    Drop d = t.drop;
    for (UUID id : m.participants()) {
      if (m.isBot(id) || !m.alive().contains(id) || m.hasLeft(id) || m.side(id) != VbSide.SEEDBEARERS) continue;
      if (id.equals(d.by) && now < d.lockUntil) continue;
      Player p = Bukkit.getPlayer(id);
      if (p == null || p.getWorld() != d.at.getWorld()) continue;
      Location l = p.getLocation();
      if (Math.hypot(l.getX() - d.at.getX(), l.getZ() - d.at.getZ()) <= PICKUP_RADIUS && Math.abs(l.getY() - d.at.getY()) < 2.2 && m.pickUpSeed(id)) return;
    }
  }

  private void removeDrop(Track t) {
    if (t.drop != null) t.drop.remove();
    t.drop = null;
  }

  private void removeBack(Track t) {
    if (t.back != null && t.back.isValid()) t.back.remove();
    t.back = null;
    t.backShown.clear();
  }

  /** Everything this match put in the world or in an inventory goes (round over, arena gone). */
  private void clear(VbMatch m, Track t) {
    if (t.back == null && t.drop == null && t.planted == null && t.inSite.isEmpty() && t.inReach.isEmpty() && t.tracker.last() == null && t.cleared) return;
    t.cleared = true;
    removeBack(t);
    removeDrop(t);
    removePlanted(t);
    t.inSite.clear();
    for (UUID id : m.participants()) {
      Player p = m.isBot(id) ? null : Bukkit.getPlayer(id);
      if (p != null) strip(p.getInventory(), -1);
      lastRight.remove(id);
      lastRaw.remove(id);
    }
  }

  void shutdown() {
    for (var e : tracks.entrySet()) clear(e.getKey(), e.getValue());
    tracks.clear();
    purgeLeftovers();
  }

  // ------------------------------------------------------------------ items

  /** Removes every Seed stack from an inventory except the one at {@code keep}. */
  private static void strip(PlayerInventory inv, int keep) {
    for (int i = 0; i < inv.getSize(); i++) if (i != keep && SeedItems.isSeed(inv.getItem(i))) inv.setItem(i, null);
    if (SeedItems.isSeed(inv.getItemInOffHand())) inv.setItemInOffHand(null);
  }

  /** The carrier has the Seed in slot 8, nobody else has one anywhere. */
  private void items(VbMatch m, UUID carrier) {
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      PlayerInventory inv = p.getInventory();
      if (id.equals(carrier)) {
        strip(inv, SeedItems.SLOT);
        if (!SeedItems.isSeed(inv.getItem(SeedItems.SLOT))) inv.setItem(SeedItems.SLOT, SeedItems.item());
      } else strip(inv, -1);
    }
  }

  // ------------------------------------------------------------------ plant

  private void plant(VbMatch m, Track t, UUID id, long now) {
    Player p = Bukkit.getPlayer(id);
    if (p == null || m.vbPhase() != VbPhase.COMBAT) return;
    Location l = p.getLocation();
    VbSite site = SeedSight.siteAt(VbData.sites(), l.getX(), l.getY(), l.getZ());
    boolean in = site != null;
    if (in && t.inSite.add(id)) plugin.valoblock.hud.notice(p, Component.text("Select the Rift Seed (slot 8) and hold right-click to plant", PINK));
    else if (!in) t.inSite.remove(id);
    boolean holding = SeedItems.isSeed(p.getInventory().getItemInMainHand()) && now - lastRight.getOrDefault(id, 0L) < HOLD_MS;
    double dt = t.lastTick == 0 ? 0.05 : Math.min(0.1, (now - t.lastTick) / 1000.0);
    t.lastTick = now;
    boolean done = m.holdPlant(holding && in ? id : null, holding && in ? site : null, dt);
    if (holding && in && !done && now - t.lastSound > 500) {
      t.lastSound = now;
      p.playSound(l, "block.amethyst_block.resonate", SoundCategory.PLAYERS, 0.7f, (float) (0.6 + m.plantProgress() / Math.max(0.1, m.rules().plantHoldSeconds())));
    }
    if (done) {
      p.playSound(l, "block.end_portal.spawn", SoundCategory.PLAYERS, 0.6f, 1.6f);
      plugin.valoblock.hud.notice(p, Component.text("Rift Seed planted", PINK));
    }
  }

  // ------------------------------------------------------------------ the planted Seed

  private void removePlanted(Track t) {
    if (t.planted != null) t.planted.remove();
    t.planted = null;
    t.sealer = null;
    t.sealCalled = false;
    t.inReach.clear();
  }

  private void planted(VbMatch m, Track t, long now) {
    if (t.planted == null || !t.planted.item.isValid()) {
      if (t.planted != null) t.planted.remove();
      t.planted = spawnPlanted(m, t, now);
      if (t.planted == null) return;
    }
    Planted pl = t.planted;
    World w = pl.at.getWorld();
    // the model: spin, and swell and shrink once a second (interpolated)
    pl.yaw += 6;
    Location spot = pl.at.clone().add(0, 0.9 + 0.1 * Math.sin(now / 350.0), 0);
    spot.setYaw(pl.yaw);
    pl.item.teleport(spot);
    if (now >= pl.nextPulse) {
      pl.nextPulse = now + 1000;
      pl.big = !pl.big;
      float sc = pl.big ? 1.9f : 1.6f;
      pl.item.setInterpolationDelay(0);
      pl.item.setInterpolationDuration(20);
      pl.item.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(sc, sc, sc), new AxisAngle4f()));
    }
    if (pl.marker.isValid()) pl.marker.teleport(pl.at.clone().add(0, 2.2 + 0.12 * Math.sin(now / 300.0), 0));
    w.spawnParticle(Particle.DUST, spot, 2, 0.25, 0.3, 0.25, 0, new Particle.DustOptions(Color.fromRGB(0xff4a8a), 1.2f), true);
    if (now >= pl.nextBeam) {
      pl.nextBeam = now + 1000;
      Particle.DustOptions beam = new Particle.DustOptions(Color.fromRGB(0xe0c8ff), 1.4f);
      for (double y = 0; y < 36; y += 0.75) w.spawnParticle(Particle.DUST, pl.at.clone().add(0, y, 0), 1, 0, 0, 0, 0, beam, true);
    }
    // the fuse ticks faster as it runs out
    double fuse = m.rules().fuseSeconds();
    double elapsed = (now - pl.since) / 1000.0;
    if (now >= pl.nextTick) {
      pl.nextTick = now + (long) (SealRules.tickInterval(elapsed, fuse) * 1000);
      w.playSound(pl.at, "block.note_block.hat", SoundCategory.PLAYERS, 1.2f, (float) (1.0 + Math.min(1, elapsed / fuse)));
    }
    sealing(m, t, pl, now);
  }

  private Planted spawnPlanted(VbMatch m, Track t, long now) {
    World w = worldOf(t, m);
    Location at = null;
    if (w != null && t.lastPos != null) at = ground(new Location(w, t.lastPos[0], t.lastPos[1], t.lastPos[2]));
    else if (w != null && m.plantedSite() != null) {
      VbSite s = m.plantedSite();
      at = new Location(w, (s.min()[0] + s.max()[0]) / 2, s.min()[1], (s.min()[2] + s.max()[2]) / 2);
    }
    if (at == null) return null;
    final Location base = at.clone().add(0, 0.9, 0);
    ItemDisplay item = w.spawn(base, ItemDisplay.class, d -> {
      style(d, 1.6f);
      d.setViewRange(8f);
      d.setGlowing(true);
      d.setGlowColorOverride(Color.fromRGB(0xff4a8a));
      d.setBrightness(new Display.Brightness(15, 15));
      d.addScoreboardTag(TAG);
    });
    TextDisplay marker = w.spawn(at.clone().add(0, 2.2, 0), TextDisplay.class, d -> {
      d.setPersistent(false);
      d.text(Component.text("\u25bc", PINK));
      d.setBillboard(Display.Billboard.CENTER);
      d.setSeeThrough(true);
      d.setShadowed(true);
      d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
      d.setViewRange(8f);
      d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f), new AxisAngle4f()));
      d.addScoreboardTag(TAG);
    });
    Block lb = at.clone().add(0, 0.5, 0).getBlock();
    Block light = null;
    if (lb.getType().isAir()) {
      Light data = (Light) Material.LIGHT.createBlockData();
      data.setLevel(12);
      lb.setBlockData(data, false);
      light = lb;
    }
    w.playSound(at, "block.end_portal.spawn", SoundCategory.PLAYERS, 1.0f, 1.4f);
    Planted pl = new Planted(item, marker, at, now, light);
    pl.nextTick = now + 500;
    return pl;
  }

  /** The seal: a living Sealer close to the Seed and looking at it who holds right-click; one at a time. */
  private void sealing(VbMatch m, Track t, Planted pl, long now) {
    List<SealRules.Candidate> cands = new ArrayList<>();
    Map<UUID, Player> players = new HashMap<>();
    Location target = pl.at.clone().add(0, 0.9, 0);
    for (UUID id : m.participants()) {
      if (m.isBot(id) || m.hasLeft(id)) continue;
      Player p = Bukkit.getPlayer(id);
      if (p == null || p.getWorld() != pl.at.getWorld()) continue;
      boolean alive = m.alive().contains(id);
      VbSide side = m.side(id);
      double dist = p.getLocation().distance(pl.at);
      if (SealRules.inReach(side, alive, dist)) {
        if (t.inReach.add(id)) plugin.valoblock.hud.notice(p, Component.text("Hold right-click on the Seed to seal it", PINK));
      } else t.inReach.remove(id);
      Location eye = p.getEyeLocation();
      var dir = eye.getDirection();
      double angle = SealRules.angle(dir.getX(), dir.getY(), dir.getZ(), target.getX() - eye.getX(), target.getY() - eye.getY(), target.getZ() - eye.getZ());
      boolean scoped = WeaponItems.holdScoped(p) || WeaponItems.isScope(p.getInventory().getHelmet());
      boolean holding = now - lastRaw.getOrDefault(id, 0L) < HOLD_MS;
      cands.add(new SealRules.Candidate(id, side, alive, dist, angle, scoped, holding));
      players.put(id, p);
    }
    UUID holder = SealRules.pick(t.sealer, cands);
    t.sealer = holder;
    // Only talk to the match when a human holds (or just let go): a bot's own hold must not be reset by us every tick.
    if (holder == null && !t.sealCalled) return;
    double dt = t.lastSealTick == 0 ? 0.05 : Math.min(0.1, (now - t.lastSealTick) / 1000.0);
    t.lastSealTick = now;
    t.sealCalled = holder != null;
    boolean done = m.holdSeal(holder, dt);
    if (holder == null) {
      t.lastSealTick = 0;
      return;
    }
    Player p = players.get(holder);
    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 6, 3, false, false, false));
    if (done) {
      pl.at.getWorld().playSound(pl.at, "block.beacon.deactivate", SoundCategory.PLAYERS, 1.2f, 1.4f);
      plugin.valoblock.hud.notice(p, Component.text("Rift Seed sealed", PINK));
    } else if (now - t.lastSealSound > 350) {
      t.lastSealSound = now;
      double f = m.sealProgress() / Math.max(0.1, m.rules().sealSeconds());
      pl.at.getWorld().playSound(pl.at, "block.amethyst_block.resonate", SoundCategory.PLAYERS, 1.0f, (float) (0.5 + 1.5 * Math.min(1, f)));
    }
  }

  // ------------------------------------------------------------------ displays

  private void backFollows(VbMatch m, Track t, UUID carrier) {
    double[] where = null;
    float yaw = 0;
    boolean sneak = false;
    World w = null;
    if (m.isBot(carrier)) {
      double[] pos = plugin.valoblock.bots.position(carrier);
      if (pos != null) {
        where = new double[] {pos[0], pos[1], pos[2]};
        sneak = pos[3] != 0;
        yaw = (float) pos[4];
        w = worldOf(t, m);
      }
    } else {
      Player p = Bukkit.getPlayer(carrier);
      if (p != null) {
        Location l = p.getLocation();
        where = new double[] {l.getX(), l.getY(), l.getZ()};
        sneak = p.isSneaking();
        yaw = l.getYaw();
        w = l.getWorld();
      }
    }
    if (where == null || w == null) return;
    double rad = Math.toRadians(yaw);
    // Behind the player (the forward vector of yaw is (-sin, cos)), between the shoulder blades.
    Location at = new Location(w, where[0] + Math.sin(rad) * 0.34, where[1] + (sneak ? 0.95 : 1.15), where[2] - Math.cos(rad) * 0.34, yaw, 0);
    if (t.back == null || !t.back.isValid()) {
      t.backShown.clear();
      t.back = w.spawn(at, ItemDisplay.class, d -> {
        style(d, 0.32f);
        d.setVisibleByDefault(false);
      });
    } else t.back.teleport(at);
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player v = Bukkit.getPlayer(id);
      if (v == null) continue;
      boolean want = SeedSight.seesBack(m, id);
      if (want && t.backShown.add(id)) v.showEntity(plugin, t.back);
      else if (!want && t.backShown.remove(id)) v.hideEntity(plugin, t.back);
    }
  }

  private void dropFollows(VbMatch m, Track t, long now) {
    Drop d = t.drop;
    t.spin += 4;
    Location base = d.at.clone().add(0, 0.45 + 0.08 * Math.sin(now / 300.0), 0);
    base.setYaw(t.spin);
    if (d.plain.isValid()) d.plain.teleport(base);
    if (d.glow.isValid()) d.glow.teleport(base);
    if (d.marker.isValid()) d.marker.teleport(d.at.clone().add(0, 1.3 + 0.1 * Math.sin(now / 300.0), 0));
    for (UUID id : m.participants()) {
      if (m.isBot(id)) continue;
      Player v = Bukkit.getPlayer(id);
      if (v == null) continue;
      boolean want = SeedSight.seesDrop(m, id);
      if (want && d.shown.add(id)) {
        v.showEntity(plugin, d.glow);
        v.showEntity(plugin, d.marker);
      } else if (!want && d.shown.remove(id)) {
        v.hideEntity(plugin, d.glow);
        v.hideEntity(plugin, d.marker);
      }
    }
  }
}
