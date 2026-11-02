package win.vampyuyu.survival.realm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.EconomyApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.IslandConfig;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.PlanJob;
import win.vampyuyu.survival.island.WorldSetup;

/**
 * The Realm of the Cracked Ashes (GDD 3.1, issue 29).
 *
 * <p><b>World</b>: one shared world, {@code sb_ashes}, not an instance per island. Decision for GDD open question 2:
 * the Ashes hold no island build and no fight yet, exploration and harvesting are meant to be met by other players,
 * and a shared world costs one chunk set. Instances stay possible later for the Bastions and the Guardian fight,
 * which are phase 2 issues of their own (the Gate destination is a function, so only {@link #spawn} changes).
 *
 * <p><b>Opening</b>: the Gate lights for everyone who helps defeat the Echo (boss {@code voidwyrm}); the travel
 * itself is {@link SkyGateApi}.
 *
 * <p><b>Rules</b>: heat tiers (see {@link HeatMath}), four quest families (see {@link AshesProgress}), Cinderheart
 * nodes and heat vents, no building on the Ember Landing.
 */
public final class AshesRealm implements Listener {
    public static final String WORLD = SkyblockWorlds.ASHES;
    public static final String ECHO_BOSS = "voidwyrm";
    public static final String CINDERHEART = RealmItems.CINDERHEART;
    public static final String MARKER_FILE = "realms/ashes-world.json";
    public static final String ADMIN = "vampyuyu.sb.realms.admin";
    public static final String USE = "vampyuyu.sb.realms.use";
    private static final String LANE = "ashes";
    private static final double DISCOVER_RADIUS = 7;
    private static final long NODE_RESPAWN_TICKS = 90L * 20L;
    private static final int VOID_DEPTH = 45;
    private static final double BORDER = 500;

    /** Persisted marker of the build. */
    public static final class Marker {
        public int version;
        public boolean complete;
        public int originY;
    }

    private final Core core;
    private final Map<Long, Spot> nodeAt = new HashMap<>();
    private final Map<Long, AshesPlan.Spot> ventAt = new HashMap<>();
    private final Set<Long> pendingNodes = new HashSet<>();
    private AshesProgress progress = new AshesProgress();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private IslandService islands;
    private volatile boolean ready;
    private boolean dirty;

    private record Spot(int x, int y, int z) {}

    public AshesRealm(Core core) {
        this.core = core;
        for (AshesPlan.Spot node : AshesPlan.NODES) {
            nodeAt.put(key(node.x(), node.y(), node.z()), new Spot(node.x(), node.y(), node.z()));
        }
        for (AshesPlan.Spot vent : AshesPlan.VENTS) {
            ventAt.put(key(vent.x(), vent.y(), vent.z()), vent);
        }
    }

    private static long key(int x, int y, int z) {
        return BlockPlan.key(x, y, z);
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        progress = AshesProgress.fromDoc(core.store().read(AshesProgress.FILE, AshesProgress.Doc.class, AshesProgress.Doc::new));
        core.permissions().register(ADMIN, "Build, reset and enter the Cracked Ashes", PermissionDefault.OP);
        core.permissions().register(USE, "Use /ashes", PermissionDefault.TRUE);
        core.listen(this);
        registerCommand();
        islands = core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.ASHES, p -> ready ? spawn() : null));
        core.scheduler().nextTick(this::setupWorld);
        tasks.add(core.scheduler().repeating(40L, 40L, this::heatTick));
        tasks.add(core.scheduler().repeating(20L, 20L, this::discoverTick));
        tasks.add(core.scheduler().repeating(600L, 600L, this::flush));
    }

    public void close() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.ASHES, null));
        World world = world();
        if (world != null) {
            for (long k : pendingNodes) {
                world.getBlockAt(BlockPlan.keyX(k), AshesPlan.ORIGIN_Y + BlockPlan.keyY(k), BlockPlan.keyZ(k))
                    .setType(Material.GILDED_BLACKSTONE, false);
            }
        }
        pendingNodes.clear();
        try {
            core.store().write(AshesProgress.FILE, progress.toDoc());
        } catch (IOException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Cracked Ashes progress", e);
        }
    }

    public boolean ready() {
        return ready;
    }

    public World world() {
        return Bukkit.getWorld(WORLD);
    }

    /** Where the Gate lands the player; null while the world is missing. */
    public Location spawn() {
        World world = world();
        if (world == null) {
            return null;
        }
        return new Location(world, AshesPlan.SPAWN[0] + 0.5, AshesPlan.ORIGIN_Y + AshesPlan.SPAWN[1],
            AshesPlan.SPAWN[2] + 0.5, 180f, 0f);
    }

    private void setupWorld() {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        World world = setup.ensure(WORLD, World.Environment.NETHER, Biome.NETHER_WASTES, AshesPlan.ORIGIN_Y + 1);
        if (world == null) {
            core.getPlugin().getLogger().warning("The Cracked Ashes world could not be created: the Realm stays closed");
            return;
        }
        setup.configure(world, new IslandConfig.WorldCfg("CYCLE", BORDER, true, Map.of("mob_griefing", false)),
            AshesPlan.SPAWN[0], AshesPlan.ORIGIN_Y + AshesPlan.SPAWN[1], AshesPlan.SPAWN[2]);
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        boolean current = marker.complete && marker.version == AshesPlan.VERSION && marker.originY == AshesPlan.ORIGIN_Y;
        if (current && world.getBlockAt(0, AshesPlan.ORIGIN_Y, 0).getType() != Material.AIR) {
            restoreNodes(world);
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
        BlockPlan plan = AshesPlan.build();
        islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "ashes", world, plan, 0, AshesPlan.ORIGIN_Y, 0,
            islands.config().chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    core.getPlugin().getLogger().warning("The Cracked Ashes could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = AshesPlan.VERSION;
                marker.complete = true;
                marker.originY = AshesPlan.ORIGIN_Y;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Cracked Ashes marker", e);
                    }
                });
            });
        return true;
    }

    /** A crash can leave a harvested node as basalt: put the ore back. */
    private void restoreNodes(World world) {
        for (AshesPlan.Spot node : AshesPlan.NODES) {
            Block block = world.getBlockAt(node.x(), AshesPlan.ORIGIN_Y + node.y(), node.z());
            if (block.getType() == Material.BASALT) {
                block.setType(Material.GILDED_BLACKSTONE, false);
            }
        }
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        AshesProgress.Doc doc = progress.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(AshesProgress.FILE, doc);
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Cracked Ashes progress", e);
            }
        });
    }

    boolean inRealm(World world) {
        return world != null && WORLD.equals(world.getName());
    }

    public AshesProgress progress() {
        return progress;
    }

    // ---------------------------------------------------------------- opening: the Echo falls

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        if (!ECHO_BOSS.equals(event.getBossId())) {
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

    /** Lights the Gate of the Ashes for a player and tells them when they are online. */
    public boolean openFor(UUID id) {
        SkyGateApi gates = core.service(SkyGateApi.class).orElse(null);
        if (gates == null || !gates.light(id, Realm.ASHES)) {
            return false;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            core.msg().ok(player, "The Echo falls. Gate 1 of the Quai des Huit lights: the Cracked Ashes are open.");
        }
        return true;
    }

    // ---------------------------------------------------------------- heat

    /** Heat resistance the player has right now. Infinite with Fire Resistance. */
    public double resistance(Player player) {
        if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            return Double.POSITIVE_INFINITY;
        }
        double total = progress.attuned(player.getUniqueId()) ? HeatMath.ATTUNEMENT : 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) {
                continue;
            }
            total += HeatMath.armorPoints(piece.getType());
            total += core.items().getDouble(piece, HeatMath.ITEM_KEY).orElse(0.0);
        }
        return total;
    }

    /** Heat tier where the player stands. */
    public int tierAt(Location at) {
        int base = AshesPlan.baseTier(at.getX(), at.getZ());
        boolean lava = base > 0 && besideLava(at);
        return HeatMath.tier(base, lava);
    }

    private static boolean besideLava(Location at) {
        World world = at.getWorld();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Material type = world.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz).getType();
                    if (type == Material.LAVA || type == Material.MAGMA_BLOCK) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void heatTick() {
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            int tier = tierAt(player.getLocation());
            double have = resistance(player);
            if (!HeatMath.hurts(tier, have)) {
                continue;
            }
            double damage = HeatMath.damage(tier, have);
            player.setHealth(HeatMath.healthAfter(player.getHealth(), damage));
            player.playHurtAnimation(0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false, false));
            player.sendActionBar(Component.text("Heat " + tier + ": resistance " + (int) have + " / "
                + HeatMath.required(tier), NamedTextColor.GOLD));
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
            for (AshesPlan.Landmark mark : AshesPlan.LANDMARKS) {
                if (Math.hypot(at.getX() - mark.x(), at.getZ() - mark.z()) <= DISCOVER_RADIUS
                        && Math.abs(at.getY() - (AshesPlan.ORIGIN_Y + mark.y())) <= 14) {
                    discover(player, mark);
                }
            }
        }
    }

    void discover(Player player, AshesPlan.Landmark mark) {
        int coins = progress.discover(player.getUniqueId(), mark.id());
        if (coins < 0) {
            return;
        }
        dirty = true;
        player.showTitle(Title.title(Component.text(mark.name(), NamedTextColor.GOLD),
            Component.text("Discovered " + progress.of(player.getUniqueId()).discovered.size() + " / "
                + AshesPlan.LANDMARKS.size(), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        core.msg().info(player, mark.lore());
        pay(player, coins, "realm:ashes:discover");
        if (progress.exploreDone(player.getUniqueId())) {
            core.msg().ok(player, "Exploration complete: every landmark of the Cracked Ashes is charted.");
        }
    }

    private void pay(Player player, int coins, String reason) {
        if (coins <= 0) {
            return;
        }
        core.service(EconomyApi.class).ifPresent(eco -> eco.add(player.getUniqueId(), coins, reason));
        core.msg().ok(player, "+" + coins + " coins");
    }

    // ---------------------------------------------------------------- harvesting and vents

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!inRealm(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        int rx = block.getX();
        int ry = block.getY() - AshesPlan.ORIGIN_Y;
        int rz = block.getZ();
        long k = key(rx, ry, rz);
        if (nodeAt.containsKey(k) && block.getType() == Material.GILDED_BLACKSTONE) {
            event.setCancelled(true);
            if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) {
                core.msg().warn(player, "You need a pickaxe to cut Cinderheart out of the rock.");
                return;
            }
            harvest(player, block, k);
            return;
        }
        if (ventAt.containsKey(k) || protectedSpot(rx, ry, rz)) {
            if (!player.hasPermission(win.vampyuyu.survival.island.IslandService.BYPASS)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (inRealm(block.getWorld()) && protectedSpot(block.getX(), block.getY() - AshesPlan.ORIGIN_Y, block.getZ())
                && !event.getPlayer().hasPermission(win.vampyuyu.survival.island.IslandService.BYPASS)) {
            event.setCancelled(true);
        }
    }

    /** The Ember Landing and the sealed door of the Sarcophagus cannot be built on or broken. */
    static boolean protectedSpot(int x, int y, int z) {
        if (y >= -12 && Math.hypot(x, z) <= AshesPlan.LANDING_RADIUS + 1) {
            return true;
        }
        AshesPlan.Islet caldera = AshesPlan.at("caldera");
        return y >= 0 && y <= 7 && Math.abs(x - caldera.x()) <= 3 && Math.abs(z - (caldera.z() - 14)) <= 1;
    }

    private void harvest(Player player, Block block, long k) {
        if (!pendingNodes.add(k)) {
            return;
        }
        block.setType(Material.BASALT, false);
        int amount = 1 + (Math.random() < 0.35 ? 1 : 0);
        ItemStack drop = core.items().create(CINDERHEART, amount);
        player.getInventory().addItem(drop).values()
            .forEach(left -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), left));
        player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.6f);
        for (int i = 0; i < amount; i++) {
            int coins = progress.harvest(player.getUniqueId());
            pay(player, coins, "realm:ashes:gather");
        }
        dirty = true;
        int have = progress.harvested(player.getUniqueId());
        if (have <= AshesProgress.GATHER_GOAL) {
            player.sendActionBar(Component.text("Cinderheart gathered " + have + " / " + AshesProgress.GATHER_GOAL,
                NamedTextColor.GOLD));
        }
        core.scheduler().later(NODE_RESPAWN_TICKS, () -> {
            if (pendingNodes.remove(k) && block.getType() == Material.BASALT) {
                block.setType(Material.GILDED_BLACKSTONE, false);
            }
        });
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || !inRealm(block.getWorld())) {
            return;
        }
        AshesPlan.Spot vent = ventAt.get(key(block.getX(), block.getY() - AshesPlan.ORIGIN_Y, block.getZ()));
        if (vent == null) {
            return;
        }
        event.setCancelled(true);
        stabilise(event.getPlayer(), vent, block);
    }

    void stabilise(Player player, AshesPlan.Spot vent, Block block) {
        UUID id = player.getUniqueId();
        if (progress.stabilised(id, vent.id())) {
            core.msg().info(player, "This vent is already stable. The air above it cools.");
            return;
        }
        int have = count(player);
        if (have < AshesProgress.VENT_COST) {
            core.msg().warn(player, "The vent roars. It needs " + AshesProgress.VENT_COST + " Cinderheart to be calmed (you have "
                + have + ").");
            return;
        }
        take(player, AshesProgress.VENT_COST);
        int coins = progress.stabilise(id, vent.id());
        dirty = true;
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.7f);
        block.getWorld().spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, block.getLocation().add(0.5, 1.2, 0.5), 20, 0.2, 0.4, 0.2, 0.01);
        core.msg().ok(player, "Vent stabilised (" + progress.of(id).vents.size() + " / " + AshesPlan.VENTS.size() + ").");
        pay(player, coins, "realm:ashes:vent");
        if (progress.attuned(id)) {
            core.msg().ok(player, "Ashen Attunement: +" + HeatMath.ATTUNEMENT + " heat resistance for good.");
        }
    }

    private int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (core.items().is(stack, CINDERHEART)) {
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
            if (core.items().is(stack, CINDERHEART)) {
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
                || to.getY() >= AshesPlan.ORIGIN_Y - VOID_DEPTH) {
            return;
        }
        Player player = event.getPlayer();
        Location back = spawn();
        if (back == null) {
            return;
        }
        player.setFallDistance(0f);
        player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setHealth(HeatMath.healthAfter(player.getHealth(), 4.0));
        core.msg().warn(player, "The heat haze throws you back to the Ember Landing.");
    }

    // ---------------------------------------------------------------- /ashes

    private void registerCommand() {
        core.commands().command("ashes").description("Journal of the Cracked Ashes")
            .usage("/ashes [journal | go | build | reset <player>]")
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
                        core.msg().error(player, "The Cracked Ashes are not built yet.");
                    } else {
                        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
                    }
                } else {
                    boolean lit = core.service(SkyGateApi.class).map(g -> g.isLit(player.getUniqueId(), Realm.ASHES)).orElse(false);
                    if (!lit) {
                        core.msg().warn(player, "Gate 1 is dark. Defeat the Echo of the Rift to open the Cracked Ashes.");
                    } else {
                        core.service(SkyGateApi.class).ifPresent(g -> g.travel(player, Realm.ASHES));
                    }
                }
            }
            case "build" -> {
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (rebuild()) {
                    core.msg().ok(sender, "Building the Cracked Ashes. This takes a minute.");
                } else {
                    core.msg().error(sender, "The Cracked Ashes world is missing.");
                }
            }
            case "reset" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (!sender.hasPermission(ADMIN)) {
                    core.msg().error(sender, "You cannot do that.");
                } else if (target == null) {
                    core.msg().error(sender, "Usage: /ashes reset <online player>");
                } else {
                    progress.reset(target.getUniqueId());
                    dirty = true;
                    core.msg().ok(sender, "The Cracked Ashes progress of " + target.getName() + " was reset.");
                }
            }
            default -> core.msg().error(sender, "Usage: /ashes [journal | go | build | reset <player>]");
        }
        return true;
    }

    private void journal(Player player) {
        UUID id = player.getUniqueId();
        AshesProgress.Entry entry = progress.of(id);
        core.msg().info(player, "Cracked Ashes journal");
        core.msg().info(player, "Exploration: " + entry.discovered.size() + " / " + AshesPlan.LANDMARKS.size() + " landmarks"
            + (progress.exploreDone(id) ? " (done)" : ""));
        core.msg().info(player, "Progression: Cinderheart " + Math.min(entry.harvested, AshesProgress.GATHER_GOAL) + " / "
            + AshesProgress.GATHER_GOAL);
        core.msg().info(player, "World: " + entry.vents.size() + " / " + AshesPlan.VENTS.size() + " heat vents stabilised ("
            + AshesProgress.VENT_COST + " Cinderheart each)");
        core.msg().info(player, "Ashen Attunement: " + (progress.attuned(id) ? "yes, +" + HeatMath.ATTUNEMENT + " heat resistance"
            : "stabilise every vent to earn +" + HeatMath.ATTUNEMENT + " heat resistance"));
        double have = resistance(player);
        core.msg().info(player, "Your heat resistance: " + (Double.isInfinite(have) ? "immune (Fire Resistance)" : String.valueOf((int) have))
            + ". Needed: tier 1 " + HeatMath.required(1) + ", tier 2 " + HeatMath.required(2) + ", tier 3 " + HeatMath.required(3));
        for (AshesPlan.Landmark mark : progress.fragments(id)) {
            core.msg().info(player, mark.name() + ": " + mark.lore());
        }
    }
}
