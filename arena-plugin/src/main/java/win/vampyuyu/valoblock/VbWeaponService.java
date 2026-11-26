package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The guns, the knife, the shield and the Armory, on the server thread. Right-click fires (a held
 * button repeats about every 4 ticks, so "held" means a click in the last 260 ms and a per-tick pump
 * keeps automatic weapons going), a shot is a server-side ray: blocks stop it (glass and leaves do not),
 * enemies are tested against where they were up to 200 ms ago. Damage goes through {@link VbMatch#damage}
 * so health, shield and kills are the core's. The vanilla damage system is cancelled for participants.
 */
public final class VbWeaponService implements WeaponService, Listener {
  static final long HOLD_MS = 260;
  static final int SLOT_PRIMARY = 0, SLOT_SIDEARM = 1, SLOT_MELEE = 2, SLOT_ARMORY = 8;
  private static final int MAX_TRACERS = 3;

  /** Per player state, server thread only. */
  static final class Gear {
    Loadout loadout = Loadout.starting(VbData.weapons());
    final Map<String, GunState> guns = new HashMap<>();
    final PositionHistory history = new PositionHistory();
    long lastRight = Long.MIN_VALUE / 2;
    long lastKnife = Long.MIN_VALUE / 2;
    boolean wasReloading;
    String shownId = "";
    String look = ""; // the custom_model_data state last written on the held stack
    boolean scoped;
    boolean scopeCancelled; // F or a slot change while the scope is up: the release fires nothing
    boolean scopeForced; // the scope was dropped by the game (reload): the release fires nothing
    Boolean releaseScoped; // set while a release shot is taken: the cone it uses
    long inspectUntil; // end of the inspect pose of the gun just drawn (ms)

    GunState gun(VbWeapon w) {
      return guns.computeIfAbsent(w.id(), k -> new GunState(w));
    }
  }

  private final NexusArenaEngine plugin;
  private final Map<UUID, Gear> gear = new ConcurrentHashMap<>();
  private final Set<Item> drops = new HashSet<>();
  private final Random random = new Random();
  private final ZoomModifier zoomMods = new ZoomModifier();
  private static final NamespacedKey ZOOM_KEY = new NamespacedKey("vampyuyu", "vb_zoom");
  private final Map<UUID, double[]> reloadMods = new ConcurrentHashMap<>(); // {factor, until ms}

  public VbWeaponService(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  /** Registers the listener and the per-tick task. */
  public void start() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
  }

  // ------------------------------------------------------------------ lookups

  private VbMatch matchOf(UUID id) {
    ArenaSession s = plugin.arenas.session(id);
    return s != null && s.game instanceof VbMatch m ? m : null;
  }

  private static boolean fighting(VbMatch m) {
    return m.vbPhase() == VbPhase.COMBAT || m.vbPhase() == VbPhase.PLANTED;
  }

  private Gear gear(UUID id) {
    return gear.computeIfAbsent(id, k -> new Gear());
  }

  private VbWeapon held(Player p) {
    String id = WeaponItems.id(p.getInventory().getItemInMainHand());
    return id == null ? null : VbData.weapons().get(id);
  }

  @Override
  public String heldName(UUID id) {
    Player p = Bukkit.getPlayer(id);
    VbWeapon w = p == null ? null : held(p);
    return w == null ? null : w.name();
  }

  @Override
  public String ammoText(UUID id) {
    Player p = Bukkit.getPlayer(id);
    VbWeapon w = p == null ? null : held(p);
    Gear g = gear.get(id);
    if (w == null || g == null || !w.isGun()) return null;
    return g.gun(w).magazine() + " / " + w.magazine();
  }

  private void sync(Runnable r) {
    if (Bukkit.isPrimaryThread()) r.run();
    else Bukkit.getScheduler().runTask(plugin, r);
  }

  // ------------------------------------------------------------------ WeaponService

  @Override
  public void roundStarted(VbMatch match, UUID player, boolean keep) {
    if (match.hasLeft(player)) return;
    sync(
        () -> {
          for (Item d : drops) d.remove();
          drops.clear();
          Player p = Bukkit.getPlayer(player);
          if (p == null) return;
          Gear g = gear(player);
          if (!keep) g.loadout = Loadout.starting(VbData.weapons());
          else g.loadout.newBuyPhase();
          g.guns.clear(); // every magazine is full again at the start of a round
          deal(p, match, g);
          p.getInventory().setHeldItemSlot(g.loadout.get(VbWeapon.Slot.PRIMARY) != null ? SLOT_PRIMARY : SLOT_SIDEARM);
          p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
          p.setFoodLevel(20);
        });
  }

  @Override
  public void reloadModifier(UUID player, double factor, double seconds) {
    if (seconds <= 0) reloadMods.remove(player);
    else reloadMods.put(player, new double[] {factor, System.currentTimeMillis() + seconds * 1000});
  }

  /** The reload speed an ability gives the player right now (1 when none). */
  private void applyMods(UUID id, GunState gs, long now) {
    double[] mod = reloadMods.get(id);
    if (mod != null && now >= mod[1]) {
      reloadMods.remove(id);
      mod = null;
    }
    gs.setReloadFactor(mod == null ? 1 : mod[0]);
  }

  @Override
  public void adopt(UUID player, Loadout loadout) {
    sync(() -> {
      Gear g = gear(player);
      g.loadout = loadout;
      g.guns.clear();
    });
  }

  @Override
  public void dropGun(Location at, VbWeapon w, int rounds) {
    sync(() -> {
      Item item = at.getWorld().dropItemNaturally(at.clone().add(0, 0.5, 0), WeaponItems.carried(w, rounds));
      drops.add(item);
    });
  }

  /** Debug (/vb debug give): the weapon goes into its slot for free and is held. */
  void debugGive(Player p, VbMatch m, VbWeapon w) {
    sync(() -> {
      Gear g = gear(p.getUniqueId());
      g.loadout.set(w);
      g.guns.clear();
      deal(p, m, g);
      p.getInventory().setHeldItemSlot(w.slot() == VbWeapon.Slot.PRIMARY ? SLOT_PRIMARY : w.slot() == VbWeapon.Slot.MELEE ? SLOT_MELEE : SLOT_SIDEARM);
    });
  }

  @Override
  public void clear(UUID player) {
    sync(
        () -> {
          reloadMods.remove(player);
          gear.remove(player);
          zoomMods.update(player, false);
          for (Item d : drops) d.remove();
          drops.clear();
          Player p = Bukkit.getPlayer(player);
          if (p == null) return;
          removeZoom(p);
          PlayerInventory inv = p.getInventory();
          for (int i = 0; i < inv.getSize(); i++) if (WeaponItems.isVb(inv.getItem(i))) inv.setItem(i, null);
          if (WeaponItems.isVb(inv.getChestplate())) inv.setChestplate(null);
          if (WeaponItems.isVb(inv.getHelmet())) inv.setHelmet(null); // the scope
          p.setWalkSpeed(0.2f);
        });
  }

  /** Puts the loadout in the hotbar, the shield on the chest, the Armory in slot 9. */
  private void deal(Player p, VbMatch m, Gear g) {
    PlayerInventory inv = p.getInventory();
    place(inv, SLOT_PRIMARY, g, g.loadout.get(VbWeapon.Slot.PRIMARY));
    place(inv, SLOT_SIDEARM, g, g.loadout.get(VbWeapon.Slot.SIDEARM));
    place(inv, SLOT_MELEE, g, g.loadout.get(VbWeapon.Slot.MELEE));
    inv.setItem(SLOT_ARMORY, WeaponItems.armoryItem());
    int shield = m.shield(p.getUniqueId());
    VbWeapon ward = null;
    for (VbWeapon w : VbData.weapons().all()) if (w.isShield() && w.shield() == shield) ward = w;
    inv.setChestplate(ward == null ? null : WeaponItems.worn(ward));
    g.shownId = "";
    g.look = "";
    g.scoped = false;
    zoomMods.update(p.getUniqueId(), false);
    removeZoom(p);
    if (WeaponItems.isScope(inv.getHelmet())) inv.setHelmet(null);
    plugin.valoblock.agents.dressed(p);
  }

  private void place(PlayerInventory inv, int slot, Gear g, VbWeapon w) {
    inv.setItem(slot, w == null ? null : WeaponItems.carried(w, w.isGun() ? g.gun(w).magazine() : 0));
  }

  // ------------------------------------------------------------------ the per-tick task

  private void tick() {
    long now = System.currentTimeMillis();
    for (var e : gear.entrySet()) {
      Player p = Bukkit.getPlayer(e.getKey());
      if (p == null) continue;
      VbMatch m = matchOf(p.getUniqueId());
      if (m == null) continue;
      Gear g = e.getValue();
      Location l = p.getLocation();
      g.history.record(now, l.getX(), l.getY(), l.getZ(), p.isSneaking());
      // The two-handed guns stay on their (invisible) cooldown: the client then sends each right-click without playing the
      // crossbow's use, so the gun does not dip in first person at every shot (see WeaponItems.holdWithBothHands).
      if (p.getCooldown(WeaponItems.GUN_COOLDOWN) < WeaponItems.GUN_COOLDOWN_TICKS / 2) p.setCooldown(WeaponItems.GUN_COOLDOWN, WeaponItems.GUN_COOLDOWN_TICKS);
      VbWeapon w = held(p);
      // Walk speed: the knife is the fastest thing to hold.
      String id = w == null ? "" : w.id();
      if (!id.equals(g.shownId)) {
        g.shownId = id;
        p.setWalkSpeed(w != null && w.speed() > 1 ? (float) Math.min(1.0, 0.2 * w.speed()) : 0.2f);
      }
      if (w == null || !w.isGun()) {
        applyZoom(p, null, false);
        continue;
      }
      GunState gs = g.gun(w);
      applyMods(p.getUniqueId(), gs, now);
      gs.tick(now);
      boolean reloading = gs.reloading();
      // Every reload start (F, the shot that empties the magazine, an empty sniper) and end is heard here, once.
      if (!g.wasReloading && reloading) p.playSound(p.getLocation(), "item.crossbow.loading_start", SoundCategory.PLAYERS, 0.8f, 1.0f);
      if (g.wasReloading && !reloading) p.playSound(p.getLocation(), "item.crossbow.loading_end", SoundCategory.PLAYERS, 0.8f, 1.3f);
      g.wasReloading = reloading;
      applyZoom(p, w, ZoomModifier.wanted(w, p.isSneaking(), reloading, fighting(m), m.alive().contains(p.getUniqueId())));
      if (reloading && ScopeInput.holdScope(w) && usingMain(p)) {
        g.scopeForced = true;
        p.clearActiveItem();
      } // the spyglass drops while the magazine is out
      pump(p, m, g, w, gs, now, false);
      refreshStack(p, w, gs);
      look(p, g, w, gs, now);
    }
  }

  /**
   * The pack's states: the muzzle flash of the last 2 ticks, the magazine out while reloading, the tighter hold while
   * sneaking, and the sniper scope. The stack is only rewritten when the state changes (a rewrite every tick would
   * flicker); a state left on a gun put away is cleared in heldChange.
   */
  private void look(Player p, Gear g, VbWeapon w, GunState gs, long now) {
    boolean sneaking = p.isSneaking();
    boolean scoped = ScopeInput.scoped(w, sneaking, usingMain(p), gs.reloading());
    // A hold scope aims with the held button, not with sneaking: the scoped look hides the gun, the sneak pose is not used.
    String want = GunVisual.state(gs.reloadProgress(now), gs.reloading(), gs.sinceShot(now), ScopeInput.holdScope(w) ? scoped : sneaking, now < g.inspectUntil, w.zoom());
    if (!want.equals(g.look)) {
      g.look = want;
      WeaponItems.setState(p.getInventory().getItemInMainHand(), want);
    }
    setScope(p, g, scoped && !ScopeInput.holdScope(w)); // the helmet overlay is only for the sneak zoom: a spyglass draws its own
  }

  /** The player is using (right-click held) the item in the main hand. */
  private static boolean usingMain(Player p) {
    return p.hasActiveItem() && p.getActiveItemHand() == EquipmentSlot.HAND;
  }

  /** Adds or removes the zoom modifier as the bookkeeping says: one modifier at most, whatever the call order. */
  private void applyZoom(Player p, VbWeapon w, boolean wanted) {
    ZoomModifier.Op op = zoomMods.update(p.getUniqueId(), wanted);
    if (op == ZoomModifier.Op.NONE) return;
    AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
    if (speed == null) return;
    speed.removeModifier(ZOOM_KEY);
    if (op == ZoomModifier.Op.ADD && w != null) speed.addTransientModifier(new AttributeModifier(ZOOM_KEY, ZoomModifier.amount(w.zoom()), AttributeModifier.Operation.MULTIPLY_SCALAR_1));
  }

  private void removeZoom(Player p) {
    zoomMods.update(p.getUniqueId(), false);
    AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
    if (speed != null) speed.removeModifier(ZOOM_KEY);
  }

  private void setScope(Player p, Gear g, boolean on) {
    if (on == g.scoped) return;
    g.scoped = on;
    p.getInventory().setHelmet(on ? WeaponItems.scope() : null);
    if (!on) plugin.valoblock.agents.dressed(p); // the hood comes back
  }

  private void refreshStack(Player p, VbWeapon w, GunState gs) {
    ItemStack it = p.getInventory().getItemInMainHand();
    int want = gs.reloading() ? 1 : Math.max(1, Math.min(99, gs.magazine()));
    if (it.getAmount() != want) it.setAmount(want);
  }

  private void pump(Player p, VbMatch m, Gear g, VbWeapon w, GunState gs, long now, boolean click) {
    if (!fighting(m) || !m.alive().contains(p.getUniqueId()) || plugin.valoblock.agents.frozen(p.getUniqueId())) return;
    boolean held = now - g.lastRight < HOLD_MS;
    for (int i = 0; i < 4 && gs.poll(now, click, held); i++) {
      click = false;
      shoot(p, m, g, w, gs, now);
    }
  }

  // ------------------------------------------------------------------ shooting

  private void shoot(Player p, VbMatch m, Gear g, VbWeapon w, GunState gs, long now) {
    Location eyeLoc = p.getEyeLocation();
    double[] origin = {eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ()};
    Vector look = eyeLoc.getDirection().normalize();
    double[] aim = {look.getX(), look.getY(), look.getZ()};
    int shot = gs.lastShotIndex();
    boolean moving = moving(g, now);
    double spread = ScopeInput.spread(w, GunMath.spread(w, p.isSneaking(), moving, !p.isOnGround(), shot), g.releaseScoped != null ? g.releaseScoped : ScopeInput.scoped(w, p.isSneaking(), usingMain(p), false));
    World world = p.getWorld();
    long rewind = Math.min(PositionHistory.MAX_REWIND_MS, Math.max(0, p.getPing()));
    Map<UUID, int[]> damage = new HashMap<>(); // victim -> {total, head pellets}
    int traced = 0;
    for (int pellet = 0; pellet < w.pellets(); pellet++) {
      double[] dir = GunMath.jitter(aim, spread, random);
      Ray ray = trace(world, p, m, g, w, origin, dir, now, rewind);
      if (ray.victim != null) {
        int[] d = damage.computeIfAbsent(ray.victim, k -> new int[2]);
        d[0] += ray.damage;
        if (ray.zone == GunMath.Zone.HEAD) d[1]++;
        Player v = Bukkit.getPlayer(ray.victim);
        if (v != null && w.slowOnHit() > 0)
          v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (w.slowOnHit() * 20), 0, false, false, false));
      }
      plugin.valoblock.agents.bulletPassed(p, origin, dir, ray.end == null ? w.range() : Math.sqrt(Math.pow(ray.end[0] - origin[0], 2) + Math.pow(ray.end[1] - origin[1], 2) + Math.pow(ray.end[2] - origin[2], 2)));
      if (traced++ < MAX_TRACERS) tracer(world, origin, dir, ray.end, WeaponItems.tracer(w.metal()));
    }
    fireSound(p, w);
    plugin.valoblock.bots.gunfire(m, p.getUniqueId(), origin[0], origin[1], origin[2]);
    for (var e : damage.entrySet()) {
      UUID vid = e.getKey();
      boolean died = m.damage(vid, p.getUniqueId(), e.getValue()[0]);
      boolean head = e.getValue()[1] > 0;
      Player v = m.isBot(vid) ? null : Bukkit.getPlayer(vid);
      if (v != null) mirrorHealth(v, m);
      if (died && v != null) spectate(v);
      if (m.isBot(vid)) plugin.valoblock.bots.damaged(vid);
      p.playSound(p.getLocation(), "entity.arrow.hit_player", SoundCategory.PLAYERS, 0.7f, head ? 1.9f : 1.2f);
      plugin.valoblock.hud.hitMarker(p.getUniqueId(), head, died);
      if (died && !w.killSound().isEmpty()) world.playSound(p.getLocation(), w.killSound(), SoundCategory.PLAYERS, 1f, 0.8f);
    }
    // Small camera kick: the pattern is what the crosshair really moves by, so the next shot follows it.
    double[] k = w.recoil().at(shot);
    Location now2 = p.getLocation();
    float pitch = (float) Math.max(-90, now2.getPitch() - k[0]);
    p.setRotation((float) (now2.getYaw() + k[1]), pitch);
  }

  /** A player killed mid-round watches the rest of it (nothing brings them back until the respawn slice). */
  private void spectate(Player v) {
    Bukkit.getScheduler().runTask(plugin, () -> v.setGameMode(org.bukkit.GameMode.SPECTATOR));
  }

  private boolean moving(Gear g, long now) {
    PositionHistory.Sample a = g.history.at(now, 0), b = g.history.at(now, 100);
    if (a == null || b == null) return false;
    return Math.hypot(a.x() - b.x(), a.z() - b.z()) > 0.1; // > 1 block/s
  }

  private void mirrorHealth(Player v, VbMatch m) {
    double max = v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
    int hp = m.health(v.getUniqueId());
    // Never let vanilla health reach 0: the core decides who dies (the engine spectates them).
    v.setHealth(Math.max(1.0, Math.min(max, max * hp / VbData.rules().maxHealth())));
  }

  /** What one ray did. */
  private static final class Ray {
    UUID victim;
    int damage;
    GunMath.Zone zone;
    double[] end;
  }

  private Ray trace(World world, Player shooter, VbMatch m, Gear g, VbWeapon w, double[] o, double[] d, long now, long rewind) {
    Ray r = new Ray();
    Vector dir = new Vector(d[0], d[1], d[2]);
    // Walls: glass and leaves are crossed, thin walls are crossed by weapons that penetrate.
    double stop = w.range();
    List<Double> crossed = new ArrayList<>(); // distances of the penetrated walls
    double from = 0;
    int penetrations = w.penetration();
    for (int guard = 0; guard < 24 && from < stop; guard++) {
      Location start = new Location(world, o[0] + d[0] * from, o[1] + d[1] * from, o[2] + d[2] * from);
      RayTraceResult hit = world.rayTraceBlocks(start, dir, stop - from, FluidCollisionMode.NEVER, true);
      if (hit == null || hit.getHitBlock() == null) break;
      double at = from + hit.getHitPosition().distance(start.toVector());
      Material mat = hit.getHitBlock().getType();
      if (!(mat.name().contains("GLASS") || org.bukkit.Tag.LEAVES.isTagged(mat))) plugin.valoblock.agents.bulletHit(hit.getHitBlock());
      if (mat.name().contains("GLASS") || org.bukkit.Tag.LEAVES.isTagged(mat)) {
        from = at + 0.05;
        continue;
      }
      if (penetrations > 0) {
        penetrations--;
        crossed.add(at);
        from = at + 1.0;
        continue;
      }
      stop = at;
      break;
    }
    // Enemies: nearest hit of every living enemy, rewound to where the shooter saw them.
    double best = stop;
    int shooterSquad = m.squadOf(shooter.getUniqueId());
    for (UUID id : m.alive()) {
      if (id.equals(shooter.getUniqueId()) || m.squadOf(id) == shooterSquad) continue;
      Player v = m.isBot(id) ? null : Bukkit.getPlayer(id);
      PositionHistory.Sample s = null;
      double x, y, z;
      boolean sneak;
      if (m.isBot(id)) { // a bot's body: where its brain stands now (no rewind)
        double[] at = plugin.valoblock.bots.position(id);
        if (at == null) continue;
        x = at[0];
        y = at[1];
        z = at[2];
        sneak = at[3] != 0;
      } else if (v == null || !v.getWorld().equals(world)) continue;
      else if ((s = gear(id).history.at(now, rewind)) == null) {
        Location l = v.getLocation();
        x = l.getX();
        y = l.getY();
        z = l.getZ();
        sneak = v.isSneaking();
      } else {
        x = s.x();
        y = s.y();
        z = s.z();
        sneak = s.sneaking();
      }
      GunMath.Hit h = GunMath.rayHitbox(o, d, best, x, y, z, sneak);
      if (h == null) continue;
      best = h.distance();
      r.victim = id;
      r.zone = h.zone();
      double wall = 1;
      for (double c : crossed) if (c < h.distance()) wall *= w.penetrationFactor();
      r.damage = GunMath.damage(w, h.zone(), h.distance(), wall);
    }
    r.end = new double[] {o[0] + d[0] * best, o[1] + d[1] * best, o[2] + d[2] * best};
    return r;
  }

  private void tracer(World world, double[] o, double[] d, double[] end, org.bukkit.Color color) {
    double len = Math.sqrt(Math.pow(end[0] - o[0], 2) + Math.pow(end[1] - o[1], 2) + Math.pow(end[2] - o[2], 2));
    Particle.DustOptions dust = new Particle.DustOptions(color, 0.6f);
    for (double t = 1.5; t < len; t += 0.5) world.spawnParticle(Particle.DUST, o[0] + d[0] * t, o[1] + d[1] * t - 0.1, o[2] + d[2] * t, 1, 0, 0, 0, 0, dust);
  }

  private void fireSound(Player p, VbWeapon w) {
    String sound;
    float pitch;
    switch (w.texture()) {
      case "shotgun" -> {
        sound = "entity.generic.explode";
        pitch = 1.9f;
      }
      case "sniper" -> {
        sound = "entity.firework_rocket.large_blast";
        pitch = 0.8f;
      }
      case "rifle" -> {
        sound = "entity.firework_rocket.blast";
        pitch = 1.3f;
      }
      case "lmg" -> {
        sound = "entity.firework_rocket.blast";
        pitch = 0.9f;
      }
      case "smg" -> {
        sound = "entity.firework_rocket.shoot";
        pitch = 1.9f;
      }
      default -> {
        sound = "entity.firework_rocket.shoot";
        pitch = 1.5f;
      }
    }
    p.getWorld().playSound(p.getLocation(), sound, SoundCategory.PLAYERS, 1.0f, pitch);
  }

  // ------------------------------------------------------------------ the knife

  private void knife(Player p, VbMatch m, Gear g, VbWeapon knife) {
    long now = System.currentTimeMillis();
    if (!fighting(m) || !m.alive().contains(p.getUniqueId()) || now - g.lastKnife < 500 || plugin.valoblock.agents.frozen(p.getUniqueId())) return;
    g.lastKnife = now;
    Location eye = p.getEyeLocation();
    double[] o = {eye.getX(), eye.getY(), eye.getZ()};
    Vector dv = eye.getDirection().normalize();
    double[] d = {dv.getX(), dv.getY(), dv.getZ()};
    RayTraceResult wall = p.getWorld().rayTraceBlocks(eye, dv, knife.reach(), FluidCollisionMode.NEVER, true);
    double reach = wall == null ? knife.reach() : wall.getHitPosition().distance(eye.toVector());
    UUID target = null;
    double best = reach;
    for (UUID id : m.alive()) {
      if (id.equals(p.getUniqueId()) || m.squadOf(id) == m.squadOf(p.getUniqueId())) continue;
      double[] at;
      if (m.isBot(id)) {
        at = plugin.valoblock.bots.position(id);
        if (at == null) continue;
      } else {
        Player v = Bukkit.getPlayer(id);
        if (v == null || !v.getWorld().equals(p.getWorld())) continue;
        Location l = v.getLocation();
        at = new double[] {l.getX(), l.getY(), l.getZ(), v.isSneaking() ? 1 : 0, l.getYaw()};
      }
      GunMath.Hit h = GunMath.rayHitbox(o, d, best, at[0], at[1], at[2], at[3] != 0);
      if (h != null) {
        best = h.distance();
        target = id;
      }
    }
    p.getWorld().playSound(p.getLocation(), "entity.player.attack.sweep", SoundCategory.PLAYERS, 0.6f, 1.4f);
    if (target == null) return;
    Player v = m.isBot(target) ? null : Bukkit.getPlayer(target);
    double[] tp = v == null ? plugin.valoblock.bots.position(target) : null;
    if (v == null && tp == null) return;
    double faceX = v != null ? v.getLocation().getDirection().getX() : -Math.sin(Math.toRadians(tp[4]));
    double faceZ = v != null ? v.getLocation().getDirection().getZ() : Math.cos(Math.toRadians(tp[4]));
    double vx = v != null ? v.getLocation().getX() : tp[0], vz = v != null ? v.getLocation().getZ() : tp[2];
    int dmg = GunMath.knifeDamage(knife, faceX, faceZ, p.getLocation().getX() - vx, p.getLocation().getZ() - vz);
    boolean died = m.damage(target, p.getUniqueId(), dmg);
    if (v != null) mirrorHealth(v, m);
    else plugin.valoblock.bots.damaged(target);
    if (died && v != null) spectate(v);
    p.playSound(p.getLocation(), "entity.arrow.hit_player", SoundCategory.PLAYERS, 0.7f, dmg > knife.body() ? 1.9f : 1.2f);
    plugin.valoblock.hud.hitMarker(p.getUniqueId(), dmg > knife.body(), died);
  }

  // ------------------------------------------------------------------ events

  /** Backstop of the two-handed hold: the guns are charged crossbows (WeaponItems.holdWithBothHands) and must never shoot their arrow. */
  @EventHandler(priority = EventPriority.LOWEST)
  public void crossbowShot(org.bukkit.event.entity.EntityShootBowEvent e) {
    if (WeaponItems.isVb(e.getBow())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void interact(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    ItemStack it = e.getItem();
    if (!WeaponItems.isVb(it)) return;
    boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
    // A Sealer next to the planted Seed and looking at it: right-click seals, it never fires, scopes or opens anything.
    if (right && plugin.valoblock.seeds != null && plugin.valoblock.seeds.claimsRightClick(m, p)) {
      e.setCancelled(true);
      e.setUseItemInHand(Event.Result.DENY);
      e.setUseInteractedBlock(Event.Result.DENY);
      return;
    }
    boolean armory = WeaponItems.isArmory(it);
    String wid = armory ? null : WeaponItems.id(it);
    VbWeapon w = wid == null ? null : VbData.weapons().get(wid);
    ScopeInput.Action action = ScopeInput.action(w, armory, right ? ScopeInput.Click.RIGHT : ScopeInput.Click.LEFT);
    e.setUseInteractedBlock(Event.Result.DENY);
    if (action == ScopeInput.Action.SCOPE) {
      // Right-click on a spyglass sniper: the vanilla use starts (zoom and overlay), so the event is NOT cancelled. Not while the magazine is out.
      Gear sg = gear(p.getUniqueId());
      GunState sgs = sg.gun(w);
      if (sgs.reloading() || sgs.magazine() <= 0) {
        e.setCancelled(true);
        e.setUseItemInHand(Event.Result.DENY);
        // An empty sniper does not scope: the right-click reloads it instead.
        if (!sgs.reloading()) startReload(p, w, sgs);
      } else {
        sg.scopeCancelled = false;
        sg.scopeForced = false;
      }
      return;
    }
    e.setCancelled(true);
    e.setUseItemInHand(Event.Result.DENY);
    if (w == null && !armory) return;
    Gear g = gear(p.getUniqueId());
    long now = System.currentTimeMillis();
    switch (action) {
      case OPEN_ARMORY -> openArmory(p, m);
      case FIRE -> {
        if (right) g.lastRight = now; // a held button repeats: only the guns fired with right-click
        applyMods(p.getUniqueId(), g.gun(w), now);
        pump(p, m, g, w, g.gun(w), now, true);
        refreshStack(p, w, g.gun(w));
      }
      case STAB -> knife(p, m, g, w);
      default -> {}
    }
  }

  /** Releasing right-click on the HX-50 Sovereign fires it (the stop-using-item event, like a bow). */
  @EventHandler(priority = EventPriority.HIGH)
  public void release(io.papermc.paper.event.player.PlayerStopUsingItemEvent e) {
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    ItemStack it = e.getItem();
    String wid = WeaponItems.id(it);
    VbWeapon w = wid == null ? null : VbData.weapons().get(wid);
    if (!WeaponItems.isVb(it) || !ScopeInput.holdScope(w) || !w.isGun()) return;
    Gear g = gear(p.getUniqueId());
    boolean cancelled = g.scopeCancelled, forced = g.scopeForced;
    g.scopeCancelled = false;
    g.scopeForced = false;
    GunState gs = g.gun(w);
    long now = System.currentTimeMillis();
    boolean live = fighting(m) && m.alive().contains(p.getUniqueId()) && !plugin.valoblock.agents.frozen(p.getUniqueId());
    boolean heldNow = w == held(p);
    ScopeInput.Release r = ScopeInput.release(e.getTicksHeldFor(), cancelled, forced || gs.reloading() || !live || !heldNow);
    if (r == ScopeInput.Release.NONE) return;
    if (ScopeInput.dryClick(gs.magazine())) {
      p.playSound(p.getLocation(), "block.dispenser.fail", SoundCategory.PLAYERS, 0.6f, 1.6f);
      startReload(p, w, gs); // the dry click, then the magazine comes out
      return;
    }
    applyMods(p.getUniqueId(), gs, now);
    g.releaseScoped = r == ScopeInput.Release.SCOPED;
    try {
      pump(p, m, g, w, gs, now, true); // the usual fire-rate/bolt cooldown applies
    } finally {
      g.releaseScoped = null;
    }
    refreshStack(p, w, gs);
  }

  /** A left-click on a player with the knife arrives as damage, not as an interact. */
  @EventHandler(priority = EventPriority.HIGH)
  public void damage(EntityDamageEvent e) {
    UUID bot = plugin.valoblock.bots.botOf(e.getEntity());
    if (bot != null) { // a bot's mannequin: never vanilla damage, a knife swing still counts
      e.setCancelled(true);
      if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && by.getDamager() instanceof Player a) {
        VbMatch m = matchOf(a.getUniqueId());
        VbWeapon w = held(a);
        if (m != null && w != null && w.slot() == VbWeapon.Slot.MELEE) knife(a, m, gear(a.getUniqueId()), w);
      }
      return;
    }
    if (!(e.getEntity() instanceof Player v) || matchOf(v.getUniqueId()) == null) return;
    e.setCancelled(true); // health is the match's: never vanilla damage
    if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent by && by.getDamager() instanceof Player a) {
      VbMatch m = matchOf(a.getUniqueId());
      VbWeapon w = held(a);
      if (m != null && w != null && w.slot() == VbWeapon.Slot.MELEE) knife(a, m, gear(a.getUniqueId()), w);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void swap(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    e.setCancelled(true);
    VbMenus menus = plugin.valoblock.menus;
    if (m.vbPhase() == VbPhase.BUY && menus != null && menus.swapTap.press(p.getUniqueId(), System.currentTimeMillis())) {
      if (m.alive().contains(p.getUniqueId()) && !AgentSelectMenu.isMenu(p.getOpenInventory().getTopInventory())) openArmory(p, m);
      return;
    }
    VbWeapon w = held(p);
    if (w == null || !w.isGun()) return;
    if (ScopeInput.holdScope(w) && usingMain(p)) { // F while the scope is up only cancels the shot
      gear(p.getUniqueId()).scopeCancelled = true;
      p.clearActiveItem();
      return;
    }
    startReload(p, w, gear(p.getUniqueId()).gun(w));
  }

  /** Starts a reload (F, an empty sniper's right-click or release); the per-tick task plays its sound. False when none starts. */
  private boolean startReload(Player p, VbWeapon w, GunState gs) {
    long now = System.currentTimeMillis();
    applyMods(p.getUniqueId(), gs, now);
    if (plugin.valoblock.agents.frozen(p.getUniqueId()) || !gs.startReload(now)) return false;
    refreshStack(p, w, gs);
    return true;
  }

  @EventHandler
  public void heldChange(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    if (matchOf(p.getUniqueId()) == null) return;
    removeZoom(p); // the weapon in hand changes: no sneak zoom carried over
    if (usingMain(p)) gear(p.getUniqueId()).scopeCancelled = true; // a slot change cancels a scoped shot
    gear(p.getUniqueId()).inspectUntil = System.currentTimeMillis() + GunVisual.INSPECT_MS; // the gun drawn next shows its inspect pose
    VbWeapon w = held(p); // the gun being put away: a reload in progress is lost
    if (w != null && w.isGun()) {
      Gear g = gear(p.getUniqueId());
      g.gun(w).cancelReload();
      WeaponItems.setState(p.getInventory().getItemInMainHand(), ""); // back to the plain look in the hotbar slot
      g.look = "";
      setScope(p, g, false);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void drop(PlayerDropItemEvent e) {
    Player p = e.getPlayer();
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    ItemStack stack = e.getItemDrop().getItemStack();
    String id = WeaponItems.id(stack);
    VbWeapon w = id == null ? null : VbData.weapons().get(id);
    if (!WeaponItems.isVb(stack) || w == null || w.free() || !w.isGun()) {
      e.setCancelled(true); // the SP-9 Sentry, the knife and the Armory stay
      return;
    }
    Gear g = gear(p.getUniqueId());
    GunState gs = g.gun(w);
    ItemStack out = WeaponItems.carried(w, gs.magazine()); // the rounds travel with the gun
    e.getItemDrop().setItemStack(out);
    drops.add(e.getItemDrop());
    g.guns.remove(w.id());
    g.loadout.remove(w.slot());
    Bukkit.getScheduler().runTask(plugin, () -> deal(p, m, g)); // the free sidearm comes back after a dropped sidearm
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void pickup(EntityPickupItemEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    String id = WeaponItems.id(e.getItem().getItemStack());
    if (id == null) return;
    VbMatch m = matchOf(p.getUniqueId());
    e.setCancelled(true);
    if (m == null || !m.alive().contains(p.getUniqueId())) return;
    VbWeapon w = VbData.weapons().get(id);
    if (w == null || !w.isGun()) return;
    Gear g = gear(p.getUniqueId());
    VbWeapon have = g.loadout.get(w.slot());
    if (have != null && !have.free()) return; // slot taken by a bought gun
    g.loadout.set(w);
    g.guns.put(w.id(), new GunState(w, WeaponItems.mag(e.getItem().getItemStack(), w.magazine())));
    drops.remove(e.getItem());
    e.getItem().remove();
    deal(p, m, g);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void click(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) return;
    VbMatch m = matchOf(p.getUniqueId());
    if (m == null) return;
    if (!ArmoryMenu.isMenu(e.getView().getTopInventory())) {
      if (e.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) e.setCancelled(true); // own inventory: no shuffling the loadout
      return;
    }
    e.setCancelled(true);
    if (e.getClickedInventory() != e.getView().getTopInventory()) return;
    Gear g = gear(p.getUniqueId());
    if (plugin.valoblock.agents.armoryClick(p, m, e.getSlot(), e.isRightClick())) {
      refresh(p, m, g);
      return;
    }
    int tab = VbArmoryLayout.tabAt(e.getSlot());
    if (tab >= 0) {
      ArmoryMenu.setTab(p.getUniqueId(), tab);
      p.playSound(p.getLocation(), "ui.button.click", SoundCategory.PLAYERS, 0.6f, 1.2f);
      refresh(p, m, g);
      return;
    }
    VbWeapon w = ArmoryMenu.at(p.getUniqueId(), e.getSlot());
    if (w == null) return;
    Armory.Result r = e.isRightClick() ? Armory.sell(m, g.loadout, p.getUniqueId(), w) : e.isLeftClick() ? Armory.buy(m, g.loadout, p.getUniqueId(), w) : null;
    if (r == null) return;
    if (r.ok()) {
      g.guns.remove(w.id());
      if (r == Armory.Result.SOLD && w.slot() != VbWeapon.Slot.SHIELD) g.guns.clear();
      deal(p, m, g);
      p.playSound(p.getLocation(), r == Armory.Result.BOUGHT ? "entity.experience_orb.pickup" : "block.note_block.hat", SoundCategory.PLAYERS, 0.8f, 1.2f);
      refresh(p, m, g);
    } else {
      p.sendMessage(Component.text(r.text, NamedTextColor.RED));
      p.playSound(p.getLocation(), "block.note_block.bass", SoundCategory.PLAYERS, 0.8f, 0.7f);
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p && matchOf(p.getUniqueId()) != null) e.setCancelled(true);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    gear.remove(e.getPlayer().getUniqueId());
    ArmoryMenu.forget(e.getPlayer().getUniqueId());
  }

  // ------------------------------------------------------------------ the menu

  @Override
  public void openArmory(Player p, VbMatch m) {
    sync(() -> openArmoryNow(p, m));
  }

  private void openArmoryNow(Player p, VbMatch m) {
    if (m.vbPhase() != VbPhase.BUY) {
      p.sendMessage(Component.text(Armory.Result.NOT_BUY_PHASE.text, NamedTextColor.RED));
      return;
    }
    openMenu(p, m, gear(p.getUniqueId()));
  }

  private void openMenu(Player p, VbMatch m, Gear g) {
    Inventory inv = ArmoryMenu.build(m, g.loadout, p);
    plugin.valoblock.agents.decorateArmory(inv, p, m);
    p.openInventory(inv);
  }

  /** Re-draws the chest after a purchase: new title (Shards left) and states, opened again. */
  private void refresh(Player p, VbMatch m, Gear g) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (p.isOnline()) openMenu(p, m, g);
    });
  }
}
