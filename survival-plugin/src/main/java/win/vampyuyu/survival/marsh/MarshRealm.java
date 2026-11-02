package win.vampyuyu.survival.marsh;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.RealmResourceApi;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.api.event.QuarryProgressEvent;
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
 * The Putrid Marshes (Realm 5, GDD 3.1, issue 43): a shared world {@code sb_marsh} built once from {@link MarshPlan}, the
 * destination of the fifth Sky Gate. Mechanics: a miasma that weakens (until the Filtered affinity), the Rot Vats where
 * expired items are salvaged into Rot Resin rolls ({@link Salvage}) and purification points ({@link MarshProgress}), a rescue
 * from the void, and no building on the landing. The Gate lights for the players who defeat the boss named by the setting
 * {@code island.marsh.gate-boss} (the Mycelial Queen by default, the previous Guardian of the chain).
 */
public final class MarshRealm implements Listener {
    public static final String WORLD = SkyblockWorlds.MARSH;
    public static final String MARKER_FILE = "realms/marsh-world.json";
    public static final String RESIN = "rot_resin";
    public static final String VAT_KIND = "vat";
    private static final String LANE = "marsh";
    private static final String DEFAULT_GATE_BOSS = "sylvan_matriarch";
    private static final int MIASMA_TICKS = 100;
    private static final int VOID_DEPTH = 70;

    /** Persisted marker of the build. */
    public static final class Marker {
        public int version;
        public boolean complete;
    }

    private final Core core;
    private MarshProgress progress = new MarshProgress();
    private final List<Scheduler.Handle> tasks = new ArrayList<>();
    private IslandService islands;
    private volatile boolean ready;
    private boolean dirty;
    private int tick;

    public MarshRealm(Core core) {
        this.core = core;
    }

    public boolean ready() {
        return ready;
    }

    public MarshProgress progress() {
        return progress;
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
        return new Location(world, MarshPlan.SPAWN[0] + 0.5, MarshPlan.ORIGIN_Y + MarshPlan.SPAWN[1], MarshPlan.SPAWN[2] + 0.5, 180f, 5f);
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        progress = MarshProgress.fromDoc(core.store().read(MarshProgress.FILE, MarshProgress.Doc.class, MarshProgress.Doc::new));
        core.listen(this);
        islands = core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.MARSHES, p -> ready ? spawn() : null));
        core.scheduler().nextTick(this::setupWorld);
        tasks.add(core.scheduler().repeating(20L, 20L, this::tick));
        tasks.add(core.scheduler().repeating(600L, 600L, this::flush));
    }

    public void close() {
        tasks.forEach(Scheduler.Handle::cancel);
        tasks.clear();
        core.service(SkyGateApi.class).ifPresent(gates -> gates.setDestination(Realm.MARSHES, null));
        dirty = true;
        flushNow();
    }

    private void setupWorld() {
        WorldSetup setup = new WorldSetup(core.getPlugin().getLogger());
        World world = setup.ensure(WORLD, World.Environment.NORMAL, Biome.MANGROVE_SWAMP, MarshPlan.ORIGIN_Y + 1);
        if (world == null) {
            core.getPlugin().getLogger().warning("The Putrid Marshes world could not be created: the Realm stays closed");
            return;
        }
        setup.configure(world, new IslandConfig.WorldCfg("MIDNIGHT", MarshPlan.BORDER, true,
            Map.of("keep_inventory", true, "mob_griefing", false, "spawn_phantoms", false, "spawn_patrols", false, "raids", false)),
            MarshPlan.SPAWN[0], MarshPlan.ORIGIN_Y + MarshPlan.SPAWN[1], MarshPlan.SPAWN[2]);
        Marker marker = core.store().read(MARKER_FILE, Marker.class, Marker::new);
        if (marker.complete && marker.version == MarshPlan.VERSION && world.getBlockAt(0, MarshPlan.ORIGIN_Y, 0).getType() != Material.AIR) {
            ready = true;
            return;
        }
        rebuild();
    }

    /** Builds the Marsh again over the old one (plan change). */
    public boolean rebuild() {
        World world = world();
        if (world == null || islands == null) {
            return false;
        }
        ready = false;
        BlockPlan plan = MarshPlan.build(islands.config().hubMaxBlocks);
        islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "marsh", world, plan, 0, MarshPlan.ORIGIN_Y, 0,
            islands.config().chunkTimeoutTicks)).thenAccept(ok -> {
                if (!ok) {
                    core.getPlugin().getLogger().warning("The Putrid Marshes could not be built");
                    return;
                }
                ready = true;
                Marker marker = new Marker();
                marker.version = MarshPlan.VERSION;
                marker.complete = true;
                core.scheduler().async(() -> {
                    try {
                        core.store().write(MARKER_FILE, marker);
                    } catch (IOException e) {
                        core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Marsh marker", e);
                    }
                });
            });
        return true;
    }

    private void flush() {
        if (dirty) {
            flushNow();
        }
    }

    private void flushNow() {
        dirty = false;
        MarshProgress.Doc doc = progress.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(MarshProgress.FILE, doc);
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not save the Marsh progress", e);
            }
        });
    }

    static boolean inMarsh(World world) {
        return world != null && WORLD.equals(world.getName());
    }

    private static void event(Player player, String key) {
        Bukkit.getPluginManager().callEvent(new QuarryProgressEvent(player, "EVENT", key, 1));
    }

    // ---------------------------------------------------------------- mechanics

    private void tick() {
        tick++;
        World world = world();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (at.getY() < MarshPlan.ORIGIN_Y - VOID_DEPTH) {
                Location back = spawn();
                if (back != null) {
                    player.setFallDistance(0f);
                    player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    core.msg().info(player, "The mud caught you and carried you back to the landing.");
                }
                continue;
            }
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at.clone().add(0, 1.5, 0), 4, 4, 2, 4, 0);
            if (progress.tier(player.getUniqueId()) < 1) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, MIASMA_TICKS, 0, true, false, false));
                if (tick % 30 == 0) {
                    core.msg().info(player, "The miasma weakens you. Salvage expired items in a Rot Vat to earn the Filtered affinity.");
                }
            }
        }
    }

    @EventHandler
    public void onArrive(PlayerChangedWorldEvent event) {
        if (inMarsh(event.getPlayer().getWorld())) {
            event(event.getPlayer(), "marsh_enter");
        }
    }

    /** The landing platform stays as built. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Location at = event.getBlock().getLocation();
        if (inMarsh(at.getWorld()) && !event.getPlayer().hasPermission("vampyuyu.sb.realms.admin")
                && Math.hypot(at.getX() - MarshPlan.SPAWN[0], at.getZ() - (MarshPlan.SPAWN[2] - 1)) <= 5) {
            event.setCancelled(true);
            core.msg().warn(event.getPlayer(), "No building on the landing.");
        }
    }

    // ---------------------------------------------------------------- the Rot Vats

    static boolean isVat(Block block) {
        if (!inMarsh(block.getWorld()) || (block.getType() != Material.WATER_CAULDRON && block.getType() != Material.CAULDRON)) {
            return false;
        }
        for (MarshPlan.Spot vat : MarshPlan.VATS) {
            if (block.getX() == vat.x() && block.getY() == MarshPlan.ORIGIN_Y + vat.y() && block.getZ() == vat.z()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onVat(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !isVat(event.getClickedBlock())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        int unit = Salvage.points(held.getType());
        if (unit == 0 || core.items().idOf(held).isPresent()) {
            core.msg().info(player, "The Rot Vat takes expired things: rotten flesh, spider eyes, poisonous potatoes, bones, slime, "
                + "pufferfish, fermented eyes, suspicious stew. Hold a stack and right-click.");
            return;
        }
        UUID id = player.getUniqueId();
        int added = unit * held.getAmount();
        int amount = held.getAmount();
        player.getInventory().setItemInMainHand(null);
        int tier = progress.tier(id);
        int total = added + progress.carry(id);
        int rolls = Salvage.rolls(total, tier);
        int cost = Salvage.rollCost(tier);
        progress.setCarry(id, total - rolls * cost);
        int crossed = progress.add(id, added);
        dirty = true;
        int found = 0;
        RealmResourceApi api = core.service(RealmResourceApi.class).orElse(null);
        for (int i = 0; api != null && i < rolls; i++) {
            for (ItemStack stack : api.roll(id, RESIN, VAT_KIND)) {
                found += stack.getAmount();
                player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 0.6f);
        core.msg().ok(player, "Salvaged " + amount + " expired items (" + added + " points)."
            + (found > 0 ? " The Vat gave " + found + " Rot Resin!" : " Nothing came up this time (" + rolls + " rolls)."));
        event(player, "marsh_salvage");
        if (crossed > 0) {
            core.msg().ok(player, "Marsh affinity: " + MarshProgress.NAMES[crossed] + ".");
            for (int t = 1; t <= crossed; t++) {
                event(player, "marsh_purify:" + t);
            }
        }
    }

    // ---------------------------------------------------------------- opening: the previous Guardian falls

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(BossKillEvent event) {
        Settings settings = Settings.load(core.data(), "island");
        if (!settings.getString("marsh.gate-boss", DEFAULT_GATE_BOSS).equalsIgnoreCase(event.getBossId())) {
            return;
        }
        Set<UUID> who = new HashSet<>();
        if (event.getKiller() != null) {
            who.add(event.getKiller().getUniqueId());
        }
        event.getTopDamagers().forEach(d -> who.add(d.player()));
        core.service(SkyGateApi.class).ifPresent(gates -> who.forEach(id -> gates.light(id, Realm.MARSHES)));
    }
}
