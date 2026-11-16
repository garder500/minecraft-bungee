package win.vampyuyu.survival.showcase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.BlockPlan;
import win.vampyuyu.survival.island.IslandService;
import win.vampyuyu.survival.island.PlanJob;
import win.vampyuyu.survival.quarry.ShowcaseBlocks;

/**
 * {@code /sbshowcase build|tp|clear}: a protected gallery in the hub world, far from the hub, where every registered
 * custom item hangs in a glowing item frame, every placed custom block stands on a pedestal and the 3D weapons, tools,
 * cores and helmets turn slowly at double size. Admin only (same permission as {@code /sbgive}). Nothing here touches
 * the island logic: the gallery is plain blocks, tagged entities and a marker file.
 */
public final class ShowcaseFeature implements Feature, Listener {
    public static final int ORIGIN_X = 2000;
    public static final int ORIGIN_Z = 2000;
    static final String MARKER_FILE = "showcase.json";
    static final String TAG = "vy_showcase";
    static final String TAG_SPIN = "vy_showcase_spin";
    private static final String LANE = "showcase";
    private static final int SPAWNS_PER_TICK = 40;
    private static final int SPIN_PERIOD = 80;
    /** Region protected before any marker exists is none; with one, its saved length. */
    private static final int DEFAULT_LENGTH = 0;

    /** What is saved about the built gallery. */
    public static final class Marker {
        public int x;
        public int y;
        public int z;
        public int length = DEFAULT_LENGTH;
        public int frames;
        public int blocks;
        public int spins;
    }

    private Core core;
    private Marker marker;
    private boolean busy;
    private final Map<UUID, Integer> turns = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "showcase";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        this.marker = core.store().read(MARKER_FILE, Marker.class, () -> null);
        core.listen(this);
        core.scheduler().repeating(SPIN_PERIOD, SPIN_PERIOD, this::spin);
        core.commands().command("sbshowcase").description("Gallery of every custom item and block")
            .usage("/<command> build|tp|clear")
            .permission("vampyuyu.sb.admin", PermissionDefault.OP)
            .executor((sender, command, label, args) -> {
                if (args.length != 1) {
                    return false;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "build" -> build(sender);
                    case "tp" -> tp(sender);
                    case "clear" -> clear(sender);
                    default -> {
                        return false;
                    }
                }
                return true;
            })
            .tab((sender, command, alias, args) -> args.length == 1
                ? List.of("build", "tp", "clear").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList()
                : List.of())
            .register();
    }

    // ------------------------------------------------------------------ data

    /** True for a weapon, tool, core or helmet: something whose 3D model deserves a big rotating display. */
    static boolean wantsSpin(CustomItemDef def) {
        String m = def.material().name();
        return m.endsWith("_SWORD") || m.endsWith("_AXE") || m.endsWith("_PICKAXE") || m.endsWith("_SHOVEL") || m.endsWith("_HOE")
            || m.equals("BOW") || m.equals("CROSSBOW") || m.equals("TRIDENT") || m.equals("MACE") || m.endsWith("_HELMET")
            || (def.equippable() != null && def.equippable().slot() == EquipmentSlot.HEAD)
            || def.id().contains("core");
    }

    private ShowcaseLayout layout() {
        return layoutOf(core);
    }

    /** The gallery for what is registered right now: one frame per custom item, a slot per placed block. */
    public static ShowcaseLayout layoutOf(Core core) {
        List<ShowcaseLayout.Item> items = new ArrayList<>();
        for (String id : core.items().ids()) {
            core.items().def(id).ifPresent(def -> items.add(new ShowcaseLayout.Item(id, def.name(), core.items().ownerOf(id), wantsSpin(def))));
        }
        List<ShowcaseLayout.BlockEntry> blocks = new ArrayList<>();
        for (ShowcaseBlocks.Entry e : ShowcaseBlocks.entries()) {
            if (core.items().def(e.itemId()).isPresent()) {
                blocks.add(new ShowcaseLayout.BlockEntry(e.itemId(), e.title(), e.hitbox(), e.modelKey()));
            }
        }
        return ShowcaseLayout.build(items, blocks);
    }

    private IslandService islands() {
        return core.service(IslandApi.class).filter(IslandService.class::isInstance).map(IslandService.class::cast).orElse(null);
    }

    // ---------------------------------------------------------------- commands

    private void build(CommandSender sender) {
        World world = Bukkit.getWorld(SkyblockWorlds.HUB);
        IslandService islands = islands();
        if (world == null || islands == null) {
            core.msg().error(sender, "The hub world is not loaded.");
            return;
        }
        if (busy) {
            core.msg().warn(sender, "The showcase is already being built or cleared.");
            return;
        }
        busy = true;
        ShowcaseLayout layout = layout();
        Marker next = new Marker();
        next.x = ORIGIN_X;
        next.y = islands.config().hubY;
        next.z = ORIGIN_Z;
        next.length = layout.length();
        next.frames = layout.frames().size();
        next.blocks = layout.blocks().size();
        next.spins = layout.spins().size();
        core.msg().info(sender, "Building the showcase: " + next.frames + " frames, " + next.blocks + " blocks, " + next.spins
            + " rotating displays, " + (layout.length() + 1) + " columns long...");
        wipe(world, islands, marker).thenCompose(ok -> {
            BlockPlan plan = layout.plan();
            return islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "showcase", world, plan, next.x, next.y, next.z,
                islands.config().chunkTimeoutTicks));
        }).whenComplete((ok, error) -> core.scheduler().sync(() -> {
            if (error != null || !Boolean.TRUE.equals(ok)) {
                busy = false;
                core.msg().error(sender, "The showcase could not be built (see the console).");
                if (error != null) {
                    core.getPlugin().getLogger().log(Level.WARNING, "Showcase build failed", error);
                }
                return;
            }
            spawnAll(world, next, layout, () -> {
                marker = next;
                try {
                    core.store().write(MARKER_FILE, next);
                } catch (IOException e) {
                    core.getPlugin().getLogger().log(Level.WARNING, "Could not save the showcase marker", e);
                }
                busy = false;
                core.msg().ok(sender, "Showcase ready: " + next.frames + " item frames, " + next.blocks + " placed blocks, "
                    + next.spins + " rotating displays. Use /sbshowcase tp.");
            });
        }));
    }

    private void tp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.msg().error(sender, "Only a player can teleport.");
            return;
        }
        if (marker == null) {
            core.msg().info(sender, "No showcase yet: building it first, run /sbshowcase tp again when it is ready.");
            build(sender);
            return;
        }
        World world = Bukkit.getWorld(SkyblockWorlds.HUB);
        if (world == null) {
            core.msg().error(sender, "The hub world is not loaded.");
            return;
        }
        double[] e = ShowcaseLayout.entrance();
        Location target = new Location(world, marker.x + e[0], marker.y + e[1], marker.z + e[2], ShowcaseLayout.ENTRANCE_YAW, 0f);
        if (!world.getWorldBorder().isInside(target)) {
            // the hub's own border is small: give this player a border around the gallery so the teleport is allowed
            WorldBorder own = Bukkit.createWorldBorder();
            own.setCenter(marker.x + marker.length / 2.0, marker.z + ShowcaseLayout.WIDTH / 2.0);
            own.setSize(marker.length + 200.0);
            player.setWorldBorder(own);
        }
        player.teleportAsync(target).thenAccept(ok -> core.msg().ok(player, ok ? "Welcome to the showcase." : "Teleport refused."));
    }

    private void clear(CommandSender sender) {
        World world = Bukkit.getWorld(SkyblockWorlds.HUB);
        IslandService islands = islands();
        if (world == null || islands == null) {
            core.msg().error(sender, "The hub world is not loaded.");
            return;
        }
        if (marker == null) {
            core.msg().info(sender, "There is no showcase to clear.");
            return;
        }
        if (busy) {
            core.msg().warn(sender, "The showcase is being built or cleared.");
            return;
        }
        busy = true;
        wipe(world, islands, marker).whenComplete((ok, error) -> core.scheduler().sync(() -> {
            busy = false;
            if (error != null) {
                core.msg().error(sender, "Clearing failed (see the console).");
                core.getPlugin().getLogger().log(Level.WARNING, "Showcase clear failed", error);
                return;
            }
            marker = null;
            try {
                core.store().delete(MARKER_FILE);
            } catch (IOException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Could not delete the showcase marker", e);
            }
            core.msg().ok(sender, "Showcase removed.");
        }));
    }

    // ------------------------------------------------------------------- world

    /** Removes the tagged entities and every block of an old gallery; completes true at once when there is none. */
    private CompletableFuture<Boolean> wipe(World world, IslandService islands, Marker old) {
        if (old == null) {
            return CompletableFuture.completedFuture(true);
        }
        int[] b = ShowcaseLayout.bounds(old.length);
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = Math.floorDiv(old.x + b[0], 16); cx <= Math.floorDiv(old.x + b[3], 16); cx++) {
            for (int cz = Math.floorDiv(old.z + b[2], 16); cz <= Math.floorDiv(old.z + b[5], 16); cz++) {
                loads.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).thenCompose(v -> {
            for (CompletableFuture<Chunk> load : loads) {
                for (Entity entity : load.join().getEntities()) {
                    if (entity.getScoreboardTags().contains(TAG)) {
                        entity.remove();
                    }
                }
            }
            BlockPlan air = new BlockPlan();
            air.fill(b[0], b[1], b[2], b[3], b[4], b[5], Material.AIR);
            return islands.runner().submit(LANE, new PlanJob(core.getPlugin(), "showcase clear", world, air, old.x, old.y, old.z,
                islands.config().chunkTimeoutTicks));
        });
    }

    /** Spawns frames, block models and rotating displays a few per tick, then calls {@code done}. */
    private void spawnAll(World world, Marker at, ShowcaseLayout layout, Runnable done) {
        List<Runnable> jobs = new ArrayList<>();
        for (ShowcaseLayout.Frame frame : layout.frames()) {
            jobs.add(() -> spawnFrame(world, at, frame));
        }
        for (ShowcaseLayout.BlockSlot slot : layout.blocks()) {
            jobs.add(() -> spawnBlock(world, at, slot));
        }
        for (ShowcaseLayout.SpinSlot slot : layout.spins()) {
            jobs.add(() -> spawnSpin(world, at, slot));
        }
        int[] next = {0};
        win.vampyuyu.survival.core.Scheduler.Handle[] handle = new win.vampyuyu.survival.core.Scheduler.Handle[1];
        handle[0] = core.scheduler().repeating(1L, 1L, () -> {
            int end = Math.min(jobs.size(), next[0] + SPAWNS_PER_TICK);
            for (; next[0] < end; next[0]++) {
                try {
                    jobs.get(next[0]).run();
                } catch (RuntimeException e) {
                    core.getPlugin().getLogger().log(Level.WARNING, "Showcase entity skipped", e);
                }
            }
            if (next[0] >= jobs.size()) {
                handle[0].cancel();
                done.run();
            }
        });
    }

    private static Location center(Marker at, double x, double y, double z) {
        return new Location(null, at.x + x, at.y + y, at.z + z);
    }

    private ItemStack stack(String itemId, String modelKey) {
        ItemStack stack = core.items().create(itemId);
        if (modelKey != null) {
            NamespacedKey key = new NamespacedKey("vampyuyu", modelKey);
            stack.editMeta(meta -> meta.setItemModel(key));
        }
        return stack;
    }

    private void spawnFrame(World world, Marker at, ShowcaseLayout.Frame frame) {
        ItemStack stack;
        try {
            stack = core.items().create(frame.id());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Showcase: item " + frame.id() + " cannot be created: " + e);
            return;
        }
        Location loc = center(at, frame.x(), frame.y(), frame.z());
        loc.setWorld(world);
        world.spawn(loc, GlowItemFrame.class, f -> {
            f.setFacingDirection(frame.face(), true);
            f.setItem(stack, false);
            f.setFixed(true);
            f.setInvulnerable(true);
            f.setVisible(true);
            f.setPersistent(true);
            f.addScoreboardTag(TAG);
        });
    }

    private void spawnBlock(World world, Marker at, ShowcaseLayout.BlockSlot slot) {
        ItemStack stack;
        try {
            stack = stack(slot.entry().itemId(), slot.entry().modelKey());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Showcase: block " + slot.entry().itemId() + " cannot be drawn: " + e);
            return;
        }
        Location loc = center(at, slot.x() + 0.5, slot.y() + 0.5, slot.z() + 0.5);
        loc.setWorld(world);
        loc.setYaw(180f);
        world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.003f), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.addScoreboardTag(TAG);
            d.setPersistent(true);
            d.setInvulnerable(true);
        });
    }

    private void spawnSpin(World world, Marker at, ShowcaseLayout.SpinSlot slot) {
        ItemStack stack;
        try {
            stack = core.items().create(slot.item().id());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().warning("Showcase: item " + slot.item().id() + " cannot be drawn: " + e);
            return;
        }
        Location loc = center(at, slot.x() + 0.5, slot.y(), slot.z() + 0.5);
        loc.setWorld(world);
        world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(spinTransform(0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.addScoreboardTag(TAG);
            d.addScoreboardTag(TAG_SPIN);
            d.setPersistent(true);
            d.setInvulnerable(true);
        });
    }

    private static Transformation spinTransform(int quarterTurns) {
        float angle = (float) (quarterTurns * Math.PI / 2);
        return new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0),
            new Vector3f(ShowcaseLayout.SPIN_SCALE), new AxisAngle4f());
    }

    /** Every few seconds, turns the displays near a visitor a quarter turn, smoothly (client-side interpolation). */
    private void spin() {
        if (marker == null) {
            return;
        }
        World world = Bukkit.getWorld(SkyblockWorlds.HUB);
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (!inside(player.getLocation())) {
                continue;
            }
            try {
                for (ItemDisplay d : player.getWorld().getNearbyEntitiesByType(ItemDisplay.class, player.getLocation(), 64,
                        e -> e.getScoreboardTags().contains(TAG_SPIN))) {
                    int turn = turns.merge(d.getUniqueId(), 1, Integer::sum);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(SPIN_PERIOD);
                    d.setTransformation(spinTransform(turn));
                }
            } catch (UnsupportedOperationException e) {
                return; // a server without entity queries (tests)
            }
        }
    }

    // -------------------------------------------------------------- protection

    private boolean inside(Location at) {
        Marker m = marker;
        if (m == null || at.getWorld() == null || !SkyblockWorlds.HUB.equals(at.getWorld().getName())) {
            return false;
        }
        int[] b = ShowcaseLayout.bounds(m.length);
        return at.getX() >= m.x + b[0] && at.getX() <= m.x + b[3] + 1 && at.getZ() >= m.z + b[2] && at.getZ() <= m.z + b[5] + 1
            && at.getY() >= m.y + b[1] - 1 && at.getY() <= m.y + b[4] + 1;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (inside(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (inside(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && inside(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (inside(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakEvent event) {
        if (inside(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }
}
