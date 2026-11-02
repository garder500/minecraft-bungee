package win.vampyuyu.survival.depths;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.RealmResourceApi;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
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
 * The Abyssal Depths (GDD 3.1, issue 45), seventh Celestial Realm: domes of water held in prismarine shells, the Pressure
 * Pearl, the pumps, and the sealed door of the Chained Leviathan.
 *
 * <p><b>World</b>: one shared world, {@code sb_depths}, like the Cracked Ashes. The Leviathan fight itself is the
 * instanced Bastion ({@code depths_bastion}).
 *
 * <p><b>Systems</b> (GDD 3.1): fishing (the Pressure Pearl is a catch, with its pity curve), breathing and pressure (see
 * {@link DepthsMath}), liquid transport (the pumps of the world quest, and the Tide Module of the Miners) and irregular
 * gravity (GDD 12.5: Slow Falling, Jump Boost and a telegraphed lift, never a real change of gravity).
 */
public final class DepthsRealm implements Listener {
    public static final String WORLD = SkyblockWorlds.DEPTHS;
    public static final String PEARL = "pressure_pearl";
    public static final String MARKER_FILE = "realms/depths-world.json";
    public static final String ADMIN = "vampyuyu.sb.depths.admin";
    public static final String USE = "vampyuyu.sb.depths.use";
    /** Boss whose defeat lights the Gate for the players who fought it (setting {@code realm.depths.gate-boss}). */
    public static final String DEFAULT_GATE_BOSS = "sylvan_matriarch";
    private static final String LANE = "depths";
    private static final double DISCOVER_RADIUS = 8;
    private static final double BORDER = 500;
    private static final int GRAVITY_PERIOD = 10;

    /** Persisted marker of the build. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int originY;
    }

    private final Core core;
    private DepthsProgress progress = new DepthsProgress();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private IslandService islands;
    private volatile boolean ready;
    private boolean dirty;
    private long gravityTicks;

    public DepthsRealm(Core core) {
        this.core = core;
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        progress = DepthsProgress.fromDoc(core.store().read(DepthsProgress.FILE, DepthsProgress.Doc.class, DepthsProgress.Doc::new));
        core.permissions().register(ADMIN, "Build, reset and enter the Abyssal Depths", PermissionDefault.OP);
        core.permissions().register(USE, "Use /depths", PermissionDefault.TRUE);
        core.listen(this);
        registerCommand();
        islands = core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.DEPTHS, p -> ready ? spawn() : null));
        core.scheduler().nextTick(this::setupWorld);
        tasks.add(core.scheduler().repeating(40L, 40L, this::pressureTick));
        tasks.add(core.scheduler().repeating(20L, 20L, this::discoverTick));
        tasks.add(core.scheduler().repeating(GRAVITY_PERIOD, GRAVITY_PERIOD, this::gravityTick));
        tasks.add(core.scheduler().repeating(600L, 600L, this::flush));
    }

    public void close() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.DEPTHS, null));
        try {
            core.store().write(DepthsProgress.FILE, progress.toDoc());
        } catch (IOException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Abyssal Depths progress", e);
        }
    }

    public boolean ready() {
        return ready;
    }

    public World world() {
        return Bukkit.getWorld(WORLD);
    }

    public DepthsProgress progress() {
        return progress;
    }

    /** Where the Gate lands the player; null while the world is missing. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, DepthsLayout.SPAWN[0] + 0.5, DepthsLayout.ORIGIN_Y + DepthsLayout.SPAWN[1],
            DepthsLayout.SPAWN[2] + 0.5, 180f, 0f);
    }

    private void setupWorld() {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        World world = setup.ensure(WORLD, World.Environment.NORMAL, Biome.DEEP_OCEAN, DepthsLayout.ORIGIN_Y + 1);
        if (world == null) {
            core.getPlugin().getLogger().warning("The Abyssal Depths world could not be created: the Realm stays closed");
            return;
        }
        setup.configure(world, new IslandConfig.WorldCfg("CYCLE", BORDER, true, java.util.Map.of("mob_griefing", false)),
            DepthsLayout.SPAWN[0], DepthsLayout.ORIGIN_Y + DepthsLayout.SPAWN[1], DepthsLayout.SPAWN[2]);
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        boolean current = marker.complete && marker.version == DepthsLayout.VERSION && marker.originY == DepthsLayout.ORIGIN_Y;
        if (current && world.getBlockAt(0, DepthsLayout.ORIGIN_Y, 0).getType() != Material.AIR) {
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
        BlockPlan plan = DepthsPlan.build();
        islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "depths", world, plan, 0, DepthsLayout.ORIGIN_Y, 0,
            islands.config().chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    core.getPlugin().getLogger().warning("The Abyssal Depths could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = DepthsLayout.VERSION;
                marker.complete = true;
                marker.originY = DepthsLayout.ORIGIN_Y;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Abyssal Depths marker", e);
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
        DepthsProgress.Doc doc = progress.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(DepthsProgress.FILE, doc);
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Abyssal Depths progress", e);
            }
        });
    }

    boolean inRealm(World world) {
        return world != null && WORLD.equals(world.getName());
    }

    private static boolean playing(Player player) {
        return !player.isDead() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    // ---------------------------------------------------------------- opening

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        String gateBoss = Settings.load(core.data(), "realm").getString("depths.gate-boss", DEFAULT_GATE_BOSS);
        if (!gateBoss.equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> winners = new HashSet<>();
        if (event.getKiller() != null) {
            winners.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> winners.add(d.player()));
        for (UUID id : winners) {
            openFor(id);
        }
    }

    /** Lights the Gate of the Depths for a player and tells them when they are online. */
    public boolean openFor(UUID id) {
        SkyGateApi gates = core.service(SkyGateApi.class).orElse(null);
        if (gates == null || !gates.light(id, Realm.DEPTHS)) {
            return false;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            core.msg().ok(player, "Gate 7 of the Quai des Huit lights: the Abyssal Depths are open.");
        }
        return true;
    }

    // ---------------------------------------------------------------- pressure and breath

    /** Pressure resistance the player has right now. Infinite with Conduit Power. */
    public double resistance(Player player) {
        if (player.hasPotionEffect(PotionEffectType.CONDUIT_POWER)) {
            return Double.POSITIVE_INFINITY;
        }
        double total = progress.attuned(player.getUniqueId()) ? DepthsMath.ATTUNEMENT : 0;
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null && helmet.getType() != Material.AIR) {
            total += DepthsMath.helmetPoints(helmet.getType().name());
            total += DepthsMath.respirationPoints(helmet.getEnchantmentLevel(Enchantment.RESPIRATION));
        }
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR) {
                total += core.items().getDouble(piece, DepthsMath.ITEM_KEY).orElse(0.0);
            }
        }
        return total;
    }

    /** Pressure tier where the player swims (0 outside the water of a dome). */
    public int tierAt(Player player) {
        Location eye = player.getEyeLocation();
        if (eye.getBlock().getType() != Material.WATER) {
            return 0;
        }
        DepthsLayout.Dome dome = DepthsLayout.waterAt(eye.getX(), eye.getY() - DepthsLayout.ORIGIN_Y, eye.getZ());
        if (dome == null) {
            return 0;
        }
        int depth = DepthsLayout.depth(player.getLocation().getY() - DepthsLayout.ORIGIN_Y);
        return DepthsMath.tier(depth, progress.pumpedDome(player.getUniqueId(), dome.id()));
    }

    private void pressureTick() {
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!playing(player)) {
                continue;
            }
            int tier = tierAt(player);
            if (tier <= 0) {
                continue;
            }
            int respiration = 0;
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null && helmet.getType() != Material.AIR) {
                respiration = helmet.getEnchantmentLevel(Enchantment.RESPIRATION);
            }
            if (!player.hasPotionEffect(PotionEffectType.WATER_BREATHING) && !player.hasPotionEffect(PotionEffectType.CONDUIT_POWER)) {
                int drain = DepthsMath.airDrain(tier, respiration);
                if (drain > 0) {
                    player.setRemainingAir(Math.max(0, player.getRemainingAir() - drain));
                }
            }
            double have = resistance(player);
            if (!DepthsMath.hurts(tier, have)) {
                continue;
            }
            player.setHealth(DepthsMath.healthAfter(player.getHealth(), DepthsMath.damage(tier, have)));
            player.playHurtAnimation(0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false, false));
            player.sendActionBar(Component.text("Pressure " + tier + ": resistance " + (int) have + " / "
                + DepthsMath.required(tier), NamedTextColor.DARK_AQUA));
        }
    }

    // ---------------------------------------------------------------- gravity (GDD 12.5)

    private void gravityTick() {
        World world = world();
        if (world == null) {
            return;
        }
        gravityTicks += GRAVITY_PERIOD;
        boolean lift = DepthsMath.drifting(gravityTicks);
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation();
            DepthsLayout.Gravity zone = DepthsLayout.gravityAt(at.getX(), at.getY() - DepthsLayout.ORIGIN_Y, at.getZ());
            if (zone == null) {
                continue;
            }
            // short effects: they run out by themselves when the player leaves the zone
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, true, false, false));
            if (zone.kind() == DepthsLayout.Gravity.Kind.LOW) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 1, true, false, false));
            } else if (lift) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0, true, false, false));
                if (gravityTicks % 40 == 0) {
                    player.sendActionBar(Component.text("The water lets go of you...", NamedTextColor.AQUA));
                }
            }
        }
    }

    // ---------------------------------------------------------------- exploration

    private void discoverTick() {
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            for (DepthsLayout.Landmark mark : DepthsLayout.LANDMARKS) {
                if (Math.hypot(at.getX() - mark.x(), at.getZ() - mark.z()) <= DISCOVER_RADIUS
                        && Math.abs(at.getY() - (DepthsLayout.ORIGIN_Y + mark.y())) <= 22) {
                    discover(player, mark);
                }
            }
        }
    }

    void discover(Player player, DepthsLayout.Landmark mark) {
        int coins = progress.discover(player.getUniqueId(), mark.id());
        if (coins < 0) {
            return;
        }
        dirty = true;
        player.showTitle(Title.title(Component.text(mark.name(), NamedTextColor.AQUA),
            Component.text("Discovered " + progress.of(player.getUniqueId()).discovered.size() + " / "
                + DepthsLayout.LANDMARKS.size(), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        core.msg().info(player, mark.lore());
        pay(player, coins, "realm:depths:discover");
        if (progress.exploreDone(player.getUniqueId())) {
            core.msg().ok(player, "Exploration complete: every landmark of the Abyssal Depths is charted.");
        }
    }

    private void pay(Player player, int coins, String reason) {
        if (coins <= 0) {
            return;
        }
        core.service(EconomyApi.class).ifPresent(eco -> eco.add(player.getUniqueId(), coins, reason));
        core.msg().ok(player, "+" + coins + " coins");
    }

    // ---------------------------------------------------------------- fishing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !inRealm(player.getWorld())) {
            return;
        }
        UUID id = player.getUniqueId();
        int coins = progress.fish(id);
        dirty = true;
        int have = progress.caught(id);
        if (have <= DepthsProgress.FISH_GOAL) {
            player.sendActionBar(Component.text("Catches in the Depths " + have + " / " + DepthsProgress.FISH_GOAL, NamedTextColor.AQUA));
        }
        pay(player, coins, "realm:depths:fish");
        core.service(RealmResourceApi.class).ifPresent(api -> {
            for (ItemStack stack : api.roll(id, PEARL, "fish")) {
                give(player, stack);
                core.msg().ok(player, "A Pressure Pearl comes up with the line!");
            }
        });
        String extra = DepthsMath.extraCatch(ThreadLocalRandom.current().nextDouble());
        Material material = extra == null ? null : Material.matchMaterial(extra);
        if (material != null) {
            give(player, new ItemStack(material));
        }
    }

    private void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    // ---------------------------------------------------------------- protection and pumps

    private static int relY(Block block) {
        return block.getY() - DepthsLayout.ORIGIN_Y;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && DepthsLayout.protectedSpot(block.getX(), relY(block), block.getZ())
                && !event.getPlayer().hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && DepthsLayout.protectedSpot(block.getX(), relY(block), block.getZ())
                && !event.getPlayer().hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    /** The water of the domes cannot be drained or poured out with a bucket. */
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && DepthsLayout.waterAt(block.getX(), relY(block), block.getZ()) != null
                && !event.getPlayer().hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
            core.msg().warn(event.getPlayer(), "The water of the domes is held by the pumps: it cannot be drained.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && DepthsLayout.protectedSpot(block.getX(), relY(block), block.getZ())
                && !event.getPlayer().hasPermission(IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !inRealm(block.getWorld())) {
            return;
        }
        for (DepthsLayout.Pump pump : DepthsLayout.PUMPS) {
            if (pump.x() == block.getX() && pump.y() == relY(block) && pump.z() == block.getZ()) {
                event.setCancelled(true);
                startPump(event.getPlayer(), pump, block);
                return;
            }
        }
    }

    void startPump(Player player, DepthsLayout.Pump pump, Block block) {
        UUID id = player.getUniqueId();
        if (progress.pumping(id, pump.id())) {
            core.msg().info(player, "This pump already runs for you. The water above it is lighter.");
            return;
        }
        int have = count(player);
        if (have < DepthsProgress.PUMP_COST) {
            core.msg().warn(player, "The pump is dry. It needs " + DepthsProgress.PUMP_COST + " Pressure Pearls to run again (you have "
                + have + ").");
            return;
        }
        take(player, DepthsProgress.PUMP_COST);
        int coins = progress.startPump(id, pump.id());
        dirty = true;
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1f, 0.8f);
        block.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP, block.getLocation().add(0.5, 1.2, 0.5), 30, 0.3, 0.5, 0.3, 0.01);
        core.msg().ok(player, "Pump running (" + progress.of(id).pumps.size() + " / " + DepthsLayout.PUMPS.size()
            + "): the pressure in this dome is one tier lighter for you.");
        pay(player, coins, "realm:depths:pump");
        if (progress.attuned(id)) {
            core.msg().ok(player, "Pressure Attunement: +" + DepthsMath.ATTUNEMENT + " pressure resistance for good.");
        }
    }

    private int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (core.items().is(stack, PEARL)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void take(Player player, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (core.items().is(stack, PEARL)) {
                int used = Math.min(left, stack.getAmount());
                stack.setAmount(stack.getAmount() - used);
                player.getInventory().setItem(i, stack.getAmount() <= 0 ? null : stack);
                left -= used;
            }
        }
    }

    // ---------------------------------------------------------------- falling

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getBlockY() == event.getFrom().getBlockY() || !inRealm(to.getWorld())
                || to.getY() >= DepthsLayout.ORIGIN_Y - DepthsLayout.VOID_DEPTH) {
            return;
        }
        Player player = event.getPlayer();
        Location back = spawn();
        if (back == null) {
            return;
        }
        player.setFallDistance(0f);
        player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        core.msg().warn(player, "The current carries you back to the Pressure Landing.");
    }

    // ---------------------------------------------------------------- /depths

    private void registerCommand() {
        core.commands().command("depths").description("Journal of the Abyssal Depths")
            .usage("/depths [journal | go | build | reset <player>]")
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
                } else if (player.hasPermission(ADMIN) && args.length > 1 && args[1].equalsIgnoreCase("force")) {
                    Location spawn = ready ? spawn() : null;
                    if (spawn == null) {
                        core.msg().error(player, "The Abyssal Depths are not built yet.");
                    } else {
                        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
                    }
                } else {
                    boolean lit = core.service(SkyGateApi.class).map(g -> g.isLit(player.getUniqueId(), Realm.DEPTHS)).orElse(false);
                    if (!lit) {
                        core.msg().warn(player, "Gate 7 is dark. Defeat the guardian that keeps it shut to open the Abyssal Depths.");
                    } else {
                        core.service(SkyGateApi.class).ifPresent(g -> g.travel(player, Realm.DEPTHS));
                    }
                }
            }
            case "build" -> {
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (rebuild()) {
                    core.msg().ok(sender, "Building the Abyssal Depths. This takes a minute.");
                } else {
                    core.msg().error(sender, "The Abyssal Depths world is missing.");
                }
            }
            case "reset" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (target == null) {
                    core.msg().error(sender, "Usage: /depths reset <online player>");
                } else {
                    progress.reset(target.getUniqueId());
                    dirty = true;
                    core.msg().ok(sender, "The Abyssal Depths progress of " + target.getName() + " was reset.");
                }
            }
            default -> core.msg().error(sender, "Usage: /depths [journal | go | build | reset <player>]");
        }
        return true;
    }

    private void journal(Player player) {
        UUID id = player.getUniqueId();
        DepthsProgress.Entry entry = progress.of(id);
        core.msg().info(player, "Abyssal Depths journal");
        core.msg().info(player, "Exploration: " + entry.discovered.size() + " / " + DepthsLayout.LANDMARKS.size() + " landmarks"
            + (progress.exploreDone(id) ? " (done)" : ""));
        core.msg().info(player, "Progression: catches " + Math.min(entry.caught, DepthsProgress.FISH_GOAL) + " / " + DepthsProgress.FISH_GOAL);
        core.msg().info(player, "World: " + entry.pumps.size() + " / " + DepthsLayout.PUMPS.size() + " pumps running ("
            + DepthsProgress.PUMP_COST + " Pressure Pearls each)");
        core.msg().info(player, "Pressure Attunement: " + (progress.attuned(id) ? "yes, +" + DepthsMath.ATTUNEMENT + " pressure resistance"
            : "run every pump to earn +" + DepthsMath.ATTUNEMENT + " pressure resistance"));
        double have = resistance(player);
        core.msg().info(player, "Your pressure resistance: " + (Double.isInfinite(have) ? "immune (Conduit Power)" : String.valueOf((int) have))
            + ". Needed: tier 1 " + DepthsMath.required(1) + ", tier 2 " + DepthsMath.required(2)
            + " (Respiration, a turtle shell and the pumps help)");
        for (DepthsLayout.Landmark mark : progress.fragments(id)) {
            core.msg().info(player, mark.name() + ": " + mark.lore());
        }
    }
}
