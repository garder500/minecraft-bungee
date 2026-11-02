package win.vampyuyu.survival.astral;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.AstralApi;
import win.vampyuyu.survival.api.BossApi;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.ParagonApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.boss.Guardians;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.data.Settings;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.IslandConfig;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.PlanJob;
import win.vampyuyu.survival.island.WorldSetup;

/**
 * The Astral Interiority (GDD 4.5, issue 49), Act III: the inside of the Devourer, entered by the players who defeated the
 * Guardian of every Realm. Shared world {@code sb_astral}, three regions:
 *
 * <ul>
 *   <li>the <b>Sea of Devoured Ideas</b>: liquid memory that carries swimmers back toward the Landing; the glass threads
 *       between the ruined islets are the way across;</li>
 *   <li>the <b>Nervous Tree</b>: a vertical climb by synapses, guided lifts that carry the player to the next platform
 *       (never a free fall: crouch to cancel, Slow Falling everywhere, back to the last platform when lost);</li>
 *   <li>the <b>Gravitational Throne</b>: a causeway around a singularity whose pull is telegraphed (surges), halved by
 *       crouching and cancelled in the shelter of a lit Anchor. Sitting on the Throne after lighting every Anchor makes the
 *       player a survivor, the condition of the Paragon ({@link AstralApi}, and {@link ParagonApi#markAstralCleared}).</li>
 * </ul>
 *
 * <p>Safety rules of the movement mechanics (GDD 12.5): no instant death, forces are bounded ({@link AstralMath}), a lost
 * player goes back to the last shelter or platform, and every danger has a telegraph and a counter. The fight against the
 * Devourer itself is another feature (the Raid, issue 50).
 */
public final class AstralRealm implements Listener, AstralApi {
    public static final String WORLD = SkyblockWorlds.ASTRAL;
    public static final String MARKER_FILE = "realms/astral-world.json";
    public static final String ADMIN = "vampyuyu.sb.astral.admin";
    public static final String USE = "vampyuyu.sb.astral.use";
    private static final String LANE = "astral";
    private static final double DISCOVER_RADIUS = 8;
    private static final double BORDER = 800;
    private static final int TICK = 2;

    /** Persisted marker of the build. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int originY;
    }

    /** A guided lift in progress. */
    private static final class Ride {
        final AstralLayout.Synapse synapse;
        int ticks;

        Ride(AstralLayout.Synapse synapse) {
            this.synapse = synapse;
        }
    }

    private final Core core;
    private AstralProgress progress = new AstralProgress();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Location> grounded = new HashMap<>();
    private IslandService islands;
    private volatile boolean ready;
    private boolean dirty;
    private long clock;

    public AstralRealm(Core core) {
        this.core = core;
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        progress = AstralProgress.fromDoc(core.store().read(AstralProgress.FILE, AstralProgress.Doc.class, AstralProgress.Doc::new));
        core.permissions().register(ADMIN, "Build, reset and enter the Astral Interiority", PermissionDefault.OP);
        core.permissions().register(USE, "Use /astral", PermissionDefault.TRUE);
        core.listen(this);
        registerCommand();
        core.register(AstralApi.class, this);
        islands = core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
        core.scheduler().nextTick(this::setupWorld);
        tasks.add(core.scheduler().repeating(TICK, TICK, this::tick));
        tasks.add(core.scheduler().repeating(20L, 20L, this::second));
        tasks.add(core.scheduler().repeating(600L, 600L, this::flush));
    }

    public void close() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        rides.clear();
        try {
            core.store().write(AstralProgress.FILE, progress.toDoc());
        } catch (IOException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Astral Interiority progress", e);
        }
    }

    public boolean ready() {
        return ready;
    }

    public World world() {
        return Bukkit.getWorld(WORLD);
    }

    public AstralProgress progress() {
        return progress;
    }

    /** Where players arrive; null while the world is missing. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, AstralLayout.SPAWN[0] + 0.5, AstralLayout.ORIGIN_Y + AstralLayout.SPAWN[1],
            AstralLayout.SPAWN[2] + 0.5, 180f, 0f);
    }

    private void setupWorld() {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        World world = setup.ensure(WORLD, World.Environment.NORMAL, Biome.THE_VOID, AstralLayout.ORIGIN_Y + 1);
        if (world == null) {
            core.getPlugin().getLogger().warning("The Astral Interiority world could not be created: the Realm stays closed");
            return;
        }
        setup.configure(world, new IslandConfig.WorldCfg("MIDNIGHT", BORDER, true,
            Map.of("mob_griefing", false, "spawn_monsters", false)),
            AstralLayout.SPAWN[0], AstralLayout.ORIGIN_Y + AstralLayout.SPAWN[1], AstralLayout.SPAWN[2]);
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        boolean current = marker.complete && marker.version == AstralLayout.VERSION && marker.originY == AstralLayout.ORIGIN_Y;
        if (current && world.getBlockAt(0, AstralLayout.ORIGIN_Y, 0).getType() != Material.AIR) {
            ready = true;
            return;
        }
        rebuild();
    }

    /** Builds the Realm again over the old one (admin command, layout change). */
    public boolean rebuild() {
        World world = world();
        if (world == null || islands == null) {
            return false;
        }
        ready = false;
        BlockPlan plan = AstralPlan.build();
        islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "astral", world, plan, 0, AstralLayout.ORIGIN_Y, 0,
            islands.config().chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    core.getPlugin().getLogger().warning("The Astral Interiority could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = AstralLayout.VERSION;
                marker.complete = true;
                marker.originY = AstralLayout.ORIGIN_Y;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Astral Interiority marker", e);
                    }
                });
            });
        return true;
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        AstralProgress.Doc doc = progress.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(AstralProgress.FILE, doc);
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Astral Interiority progress", e);
            }
        });
    }

    boolean inRealm(World world) {
        return world != null && WORLD.equals(world.getName());
    }

    private static boolean playing(Player player) {
        return !player.isDead() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static double relY(double worldY) {
        return worldY - AstralLayout.ORIGIN_Y;
    }

    // ---------------------------------------------------------------- access

    /** The Realms whose Guardian the player did not defeat yet. */
    public List<Realm> missing(UUID id) {
        BossApi bosses = core.service(BossApi.class).orElse(null);
        java.util.Set<Realm> defeated = new java.util.HashSet<>();
        if (bosses != null) {
            Guardians guardians = Guardians.parse(Settings.load(core.data(), "boss"));
            for (Guardians.Guardian guardian : guardians.byBoss().values()) {
                Realm realm = Realm.byId(guardian.realm());
                if (realm != null && bosses.kills(id, guardian.bossId()) > 0) {
                    defeated.add(realm);
                }
            }
        }
        return AstralMath.missing(defeated);
    }

    @Override
    public boolean canEnter(UUID id) {
        if (!Settings.load(core.data(), "realm").getBoolean("astral.require-guardians", true)) {
            return true;
        }
        return missing(id).isEmpty();
    }

    @Override
    public boolean survived(UUID id) {
        return progress.survived(id);
    }

    /** The Devourer's mouth: sends the player in, with the account of what the victory caused (GDD 4.4). */
    public void enter(Player player, boolean force) {
        Location spawn = ready ? spawn() : null;
        if (spawn == null) {
            core.msg().error(player, "The Astral Interiority is not built yet.");
            return;
        }
        if (!force) {
            List<Realm> missing = canEnter(player.getUniqueId()) ? List.of() : missing(player.getUniqueId());
            if (!missing.isEmpty()) {
                core.msg().warn(player, "The Devourer is still asleep for you. Defeat the Guardian of every Realm first. Missing: "
                    + String.join(", ", missing.stream().map(r -> r.guardian() + " (" + r.title() + ")").toList()) + ".");
                return;
            }
        }
        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
        player.showTitle(Title.title(Component.text("THE DEVOURER OPENS", NamedTextColor.DARK_PURPLE),
            Component.text("Act III - Astral Interiority", NamedTextColor.GRAY)));
        player.playSound(spawn, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
        if (progress.discover(player.getUniqueId(), "rift") >= 0) {
            core.msg().info(player, "The eighth Sarcophagus broke, and the sky cracked. The old world was never a prison: it was a held breath."
                + " The Devourer woke, and it let you in.");
        }
    }

    // ---------------------------------------------------------------- the clock

    private void tick() {
        World world = world();
        if (world == null) {
            return;
        }
        clock += TICK;
        double max = Settings.load(core.data(), "realm").getDouble("astral.pull-max", AstralMath.DEFAULT_PULL_MAX);
        boolean surge = AstralMath.surging(clock);
        for (Player player : world.getPlayers()) {
            if (!playing(player)) {
                rides.remove(player.getUniqueId());
                continue;
            }
            Location at = player.getLocation();
            double rx = at.getX();
            double ry = relY(at.getY());
            double rz = at.getZ();
            if (rides.containsKey(player.getUniqueId())) {
                ride(player);
                continue;
            }
            if (!player.isSneaking()) {
                AstralLayout.Synapse synapse = AstralMath.synapseAt(rx, ry, rz);
                if (synapse != null) {
                    startRide(player, synapse);
                    continue;
                }
            }
            if (AstralLayout.inSea(rx, ry + 0.2, rz)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, false));
                player.setVelocity(player.getVelocity().add(new Vector(-AstralMath.CURRENT, 0, 0)));
                continue;
            }
            double dist = AstralLayout.horizonDistance(rx, rz);
            if (dist <= AstralLayout.PULL_RADIUS && Math.abs(ry - AstralLayout.SINGULARITY[1]) <= 25) {
                throneZone(player, dist, rx, ry, rz, max, surge);
            }
        }
    }

    private void throneZone(Player player, double dist, double rx, double ry, double rz, double max, boolean surge) {
        UUID id = player.getUniqueId();
        if (dist <= AstralLayout.HORIZON) {
            returnToSafety(player, "The singularity lets go of you. You were not ready to be understood.");
            return;
        }
        if (AstralMath.shelter(rx, ry, rz, a -> progress.lit(id, a)) != null) {
            return;
        }
        double force = AstralMath.pull(dist, surge, player.isSneaking(), max);
        if (force <= 0) {
            return;
        }
        Vector toward = new Vector(AstralLayout.SINGULARITY[0] - rx, 0, AstralLayout.SINGULARITY[2] - rz);
        if (toward.lengthSquared() < 1.0e-6) {
            return;
        }
        player.setVelocity(player.getVelocity().add(toward.normalize().multiply(force)));
    }

    private void second() {
        World world = world();
        if (world == null) {
            return;
        }
        boolean warn = AstralMath.warning(clock);
        boolean surge = AstralMath.surging(clock);
        for (Player player : world.getPlayers()) {
            if (!playing(player)) {
                continue;
            }
            UUID id = player.getUniqueId();
            // low gravity everywhere inside the Devourer: no fall damage, ever
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false, false));
            player.setFallDistance(0f);
            Location at = player.getLocation();
            double rx = at.getX();
            double ry = relY(at.getY());
            double rz = at.getZ();
            double dist = AstralLayout.horizonDistance(rx, rz);
            boolean inZone = dist <= AstralLayout.PULL_RADIUS && Math.abs(ry - AstralLayout.SINGULARITY[1]) <= 25;
            if (inZone) {
                boolean sheltered = AstralMath.shelter(rx, ry, rz, a -> progress.lit(id, a)) != null;
                if (warn) {
                    player.sendActionBar(Component.text("The singularity inhales... crouch, or reach a lit Anchor", NamedTextColor.GOLD));
                    player.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.5f);
                } else if (surge && !sheltered) {
                    player.sendActionBar(Component.text("SURGE - crouch!", NamedTextColor.RED));
                    player.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
                }
            }
            // last standing place, used to bring a lost player back: not inside the pull, except in the shelter of an Anchor
            if (player.isOnGround() && !rides.containsKey(id) && !AstralLayout.inSea(rx, ry + 0.2, rz)
                    && (!inZone || AstralMath.shelter(rx, ry, rz, a -> progress.lit(id, a)) != null)) {
                grounded.put(id, at.clone());
            }
            discoverNear(player, rx, ry, rz);
        }
    }

    private void discoverNear(Player player, double rx, double ry, double rz) {
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            if (Math.hypot(rx - mark.x(), rz - mark.z()) <= DISCOVER_RADIUS && Math.abs(ry - mark.y()) <= 8) {
                discover(player, mark);
            }
        }
    }

    void discover(Player player, AstralLayout.Landmark mark) {
        int coins = progress.discover(player.getUniqueId(), mark.id());
        if (coins < 0) {
            return;
        }
        dirty = true;
        player.showTitle(Title.title(Component.text(mark.name(), NamedTextColor.LIGHT_PURPLE),
            Component.text("Discovered " + progress.of(player.getUniqueId()).discovered.size() + " / "
                + AstralLayout.LANDMARKS.size(), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
        core.msg().info(player, mark.lore());
        pay(player, coins, "realm:astral:discover");
        if (progress.exploreDone(player.getUniqueId())) {
            core.msg().ok(player, "Exploration complete: every place of the Astral Interiority is charted.");
        }
    }

    private void pay(Player player, int coins, String reason) {
        if (coins <= 0) {
            return;
        }
        core.service(EconomyApi.class).ifPresent(eco -> eco.add(player.getUniqueId(), coins, reason));
        core.msg().ok(player, "+" + coins + " coins");
    }

    // ---------------------------------------------------------------- synapses (guided lifts)

    private void startRide(Player player, AstralLayout.Synapse synapse) {
        rides.put(player.getUniqueId(), new Ride(synapse));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, true, false, false));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        player.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 0.5, 0), 25, 0.3, 0.1, 0.3, 0.08);
        int coins = progress.fire(player.getUniqueId(), synapse.id());
        if (coins >= 0) {
            dirty = true;
            core.msg().ok(player, "Synapse fired (" + progress.of(player.getUniqueId()).synapses.size() + " / "
                + AstralLayout.SYNAPSES.size() + "). Crouch to leave the current.");
            pay(player, coins, "realm:astral:synapse");
            if (progress.climbDone(player.getUniqueId())) {
                core.msg().ok(player, "The whole Nervous Tree answers you.");
            }
        }
    }

    private void ride(Player player) {
        Ride ride = rides.get(player.getUniqueId());
        Location at = player.getLocation();
        double[] from = {at.getX(), relY(at.getY()), at.getZ()};
        AstralLayout.Synapse s = ride.synapse;
        double[] to = {s.toX() + 0.5, s.toY(), s.toZ() + 0.5};
        ride.ticks += TICK;
        if (player.isSneaking() || ride.ticks > AstralMath.LIFT_MAX_TICKS || AstralMath.arrived(from, to)) {
            rides.remove(player.getUniqueId());
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
            return;
        }
        double[] v = AstralMath.lift(from, to, AstralMath.LIFT_SPEED);
        player.setVelocity(new Vector(v[0], v[1], v[2]));
        player.setFallDistance(0f);
        player.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.3, 0), 2, 0.15, 0.15, 0.15, 0.01);
    }

    // ---------------------------------------------------------------- safety

    /** Where a lost player is brought back: the last lit Anchor on the Throne side, else the last place they stood. */
    Location safeSpot(Player player) {
        World world = world();
        UUID id = player.getUniqueId();
        double rx = player.getLocation().getX();
        if (world != null && rx >= AstralLayout.NEXUS[0] - AstralLayout.NEXUS_RADIUS - 2) {
            int[] spot = AstralMath.checkpoint(a -> progress.lit(id, a));
            return new Location(world, spot[0] + 1.5, AstralLayout.ORIGIN_Y + spot[1], spot[2] + 0.5);
        }
        Location last = grounded.get(id);
        return last != null ? last : spawn();
    }

    private void returnToSafety(Player player, String message) {
        Location back = safeSpot(player);
        if (back == null) {
            return;
        }
        rides.remove(player.getUniqueId());
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        core.msg().warn(player, message);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getBlockY() == event.getFrom().getBlockY() || !inRealm(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (!playing(player) || rides.containsKey(player.getUniqueId())) {
            return;
        }
        Location last = grounded.get(player.getUniqueId());
        boolean lost = relY(to.getY()) < -AstralLayout.VOID_DEPTH || (last != null && to.getY() < last.getY() - 30);
        if (lost) {
            returnToSafety(player, "The Devourer carries you back to the last place you stood.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rides.remove(event.getPlayer().getUniqueId());
        grounded.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- protection

    private boolean denied(Player player, World world) {
        return inRealm(world) && !player.hasPermission(IslandService.BYPASS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- anchors and Throne

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !inRealm(block.getWorld())) {
            return;
        }
        int y = block.getY() - AstralLayout.ORIGIN_Y;
        for (AstralLayout.Anchor anchor : AstralLayout.ANCHORS) {
            if (anchor.x() == block.getX() && anchor.y() == y && anchor.z() == block.getZ()) {
                event.setCancelled(true);
                lightAnchor(event.getPlayer(), anchor, block);
                return;
            }
        }
        if (AstralLayout.THRONE[0] == block.getX() && AstralLayout.THRONE[1] + 1 == y && AstralLayout.THRONE[2] == block.getZ()) {
            event.setCancelled(true);
            sit(event.getPlayer());
        }
    }

    void lightAnchor(Player player, AstralLayout.Anchor anchor, Block block) {
        UUID id = player.getUniqueId();
        if (progress.lit(id, anchor.id())) {
            core.msg().info(player, "This Anchor already holds you. Its shelter reaches " + AstralLayout.ANCHOR_SHELTER + " blocks around.");
            return;
        }
        int coins = progress.light(id, anchor.id());
        dirty = true;
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f);
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, block.getLocation().add(0.5, 1.2, 0.5), 40, 0.4, 0.8, 0.4, 0.02);
        core.msg().ok(player, "Anchor lit (" + progress.of(id).anchors.size() + " / " + AstralLayout.ANCHORS.size()
            + "): the pull cannot reach you within " + AstralLayout.ANCHOR_SHELTER + " blocks of it, and it is where you come back to.");
        pay(player, coins, "realm:astral:anchor");
        if (progress.anchorsDone(id)) {
            core.msg().ok(player, "Every Anchor holds. The Throne is within reach.");
        }
    }

    void sit(Player player) {
        UUID id = player.getUniqueId();
        if (progress.survived(id)) {
            core.msg().info(player, "You already sat here. The Paragon waits for you.");
            return;
        }
        if (!progress.readyForThrone(id)) {
            core.msg().warn(player, "The Throne does not know you yet. "
                + (progress.discovered(id, "crown") ? "" : "Reach the Crown of the Nervous Tree. ")
                + (progress.anchorsDone(id) ? "" : "Light every Anchor of the causeway."));
            return;
        }
        int coins = progress.survive(id);
        dirty = true;
        player.showTitle(Title.title(Component.text("YOU SURVIVED", NamedTextColor.GOLD),
            Component.text("The Paragon is open to you", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        core.msg().info(player, "From the Throne you see the Devourer's whole hunger, and what it was holding back."
            + " You absorb a drop of divine aether. The Devourer cannot be killed: it breathes for the world. Learn to breathe with it.");
        pay(player, coins, "realm:astral:throne");
        core.service(ParagonApi.class).ifPresent(paragon -> paragon.markAstralCleared(id));
    }

    // ---------------------------------------------------------------- /astral

    private void registerCommand() {
        core.commands().command("astral").description("Journal of the Astral Interiority")
            .usage("/astral [journal | go | build | reset <player>]")
            .permission(USE, PermissionDefault.TRUE)
            .tab((CommandSender sender, Command command, String alias, String[] args) ->
                args.length == 1 ? List.of("journal", "go", "build", "reset") : List.<String>of())
            .executor(this::run).register();
    }

    private boolean run(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "journal" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "journal" -> {
                if (sender instanceof Player player) {
                    journal(player);
                } else {
                    core.msg().error(sender, "Only players have a journal.");
                }
            }
            case "go" -> {
                if (!(sender instanceof Player player)) {
                    core.msg().error(sender, "Only players can travel.");
                } else {
                    enter(player, player.hasPermission(ADMIN) && args.length > 1 && args[1].equalsIgnoreCase("force"));
                }
            }
            case "build" -> {
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (rebuild()) {
                    core.msg().ok(sender, "Building the Astral Interiority. This takes a minute.");
                } else {
                    core.msg().error(sender, "The Astral Interiority world is missing.");
                }
            }
            case "reset" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (target == null) {
                    core.msg().error(sender, "Usage: /astral reset <online player>");
                } else {
                    progress.reset(target.getUniqueId());
                    dirty = true;
                    core.msg().ok(sender, "The Astral Interiority progress of " + target.getName() + " was reset.");
                }
            }
            default -> core.msg().error(sender, "Usage: /astral [journal | go | build | reset <player>]");
        }
        return true;
    }

    private void journal(Player player) {
        UUID id = player.getUniqueId();
        AstralProgress.Entry entry = progress.of(id);
        core.msg().info(player, "Astral Interiority journal");
        List<Realm> missing = missing(id);
        core.msg().info(player, "Access: " + (canEnter(id) ? "open (/astral go)" : "closed, missing the Guardians of "
            + String.join(", ", missing.stream().map(Realm::title).toList())));
        core.msg().info(player, "Exploration: " + entry.discovered.size() + " / " + AstralLayout.LANDMARKS.size() + " places"
            + (progress.exploreDone(id) ? " (done)" : ""));
        core.msg().info(player, "Nervous Tree: " + entry.synapses.size() + " / " + AstralLayout.SYNAPSES.size() + " synapses fired"
            + (progress.climbDone(id) ? " (done)" : ""));
        core.msg().info(player, "Gravitational Throne: " + entry.anchors.size() + " / " + AstralLayout.ANCHORS.size() + " Anchors lit"
            + (progress.anchorsDone(id) ? " (done)" : ""));
        core.msg().info(player, "The Throne: " + (entry.survived ? "taken, the Paragon is open to you"
            : progress.readyForThrone(id) ? "ready, sit on it" : "reach the Crown and light every Anchor first"));
        for (AstralLayout.Landmark mark : AstralLayout.LANDMARKS) {
            if (progress.discovered(id, mark.id())) {
                core.msg().info(player, mark.name() + ": " + mark.lore());
            }
        }
    }
}
