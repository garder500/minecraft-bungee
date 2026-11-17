package win.vampyuyu.survival.wings;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import win.vampyuyu.survival.core.Core;

/**
 * The worn look of the Wings: a pair of {@code ItemDisplay}s per wearer (left and right wing, each its own model), mounted on
 * the player as passengers and showing the pack's 3D wing models (folded, or spread with a dihedral and flapping about
 * each wing's own root while gliding). The elytra equipment layer is
 * transparent, this is what the other players and the wearer see.
 *
 * <p>Entities are tagged (PDC {@link #TAG} and a scoreboard tag), not persistent, removed on unequip, quit, world
 * change, death, spectator, invisibility and plugin disable, and the leftovers of a crash are purged at start.
 * Updates are packet-free while nothing changes (see {@link WingsRig}).
 */
public final class WingsDisplay implements Listener, WingsRig.Backend {
    /** PDC tag (value = owner UUID) on every display. */
    public static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "sb_wings_display");
    /** Scoreboard tag, handy for /kill @e[tag=...] by hand. */
    public static final String SCOREBOARD_TAG = "vampyuyu_wings_display";

    private static final long RETRY_MS = 5000;

    private final Core core;
    private final WingsRig rig = new WingsRig(this);
    private final Map<UUID, EnumMap<WingsPose.Side, ItemDisplay>> displays = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private boolean failedOnce;

    public WingsDisplay(Core core) {
        this.core = core;
    }

    WingsRig rig() {
        return rig;
    }

    /** Removes the displays an earlier run left behind (a crash, a failed disable). */
    public void purgeLeftovers() {
        for (World w : Bukkit.getWorlds()) {
            try {
                for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                    if (d.getPersistentDataContainer().has(TAG, PersistentDataType.STRING)
                        || d.getScoreboardTags().contains(SCOREBOARD_TAG)) {
                        d.remove();
                    }
                }
            } catch (RuntimeException e) {
                core.debug("Wings display purge failed in " + w.getName() + ": " + e);
            }
        }
    }

    /** Drops every display (plugin disable). */
    public void shutdown() {
        rig.detachAll();
        displays.values().forEach(pair -> pair.values().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        }));
        displays.clear();
        retryAt.clear();
    }

    public void detach(UUID id) {
        rig.detach(id);
        retryAt.remove(id);
    }

    // ---------------------------------------------------------------- per tick

    /** Called every flight tick for a wearer of {@code level}; {@code flying} is the Wings' own creative style flight. */
    public void tick(Player p, int level, boolean flying, long now) {
        UUID id = p.getUniqueId();
        if (hidden(p)) {
            detach(id);
            return;
        }
        if (!rig.attached(id) && retryAt.getOrDefault(id, 0L) > now) {
            return;
        }
        WingsPose.Look look = WingsPose.decide(new WingsPose.Input(p.isGliding(), flying, p.isSwimming(), p.isSneaking()));
        Location loc = p.getLocation();
        double pitch = loc.getPitch();
        double lean = WingsMath.lean(look.stance(), pitch);
        WingsRig.Desired before = rig.current(id);
        boolean hide = WingsMath.hideFromOwner(look.stance(), pitch, before != null && before.hideFromOwner());
        int phase = look.flap() ? WingsPose.FlapCycle.phase(Bukkit.getCurrentTick()) : 0;
        WingsRig.Desired want = new WingsRig.Desired(WingsSettings.tierOf(level), look.variant(), phase, look.flap(),
            look.stance(), Math.round(bodyYaw(p)), Math.round((float) lean / 2f) * 2, hide);
        boolean done = rig.sync(id, want);
        if (!done && !rig.attached(id)) {
            retryAt.put(id, now + RETRY_MS);
        }
    }

    private static boolean hidden(Player p) {
        return p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isInvisible() || !p.isOnline()
            || p.hasMetadata("vanished") && p.getMetadata("vanished").stream().anyMatch(m -> m.asBoolean());
    }

    private static float bodyYaw(Player p) {
        try {
            return p.getBodyYaw();
        } catch (RuntimeException | LinkageError e) {
            return p.getLocation().getYaw();
        }
    }

    // ---------------------------------------------------------------- backend

    static ItemStack stack(int tier, WingsPose.Variant variant, WingsPose.Side side) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey model = NamespacedKey.fromString(
            "vampyuyu:wings/worn_tier" + tier + "_" + variant.key() + "_" + side.key());
        if (model != null) {
            meta.setItemModel(model);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Each wing turns about its own root (right rotation), the whole pair leans like the body (left rotation). */
    private static Transformation transform(WingsRig.Desired d, WingsPose.Side side) {
        Vector3f t = WingsMath.translation(d.stance(), d.lean(), d.variant(), side);
        Quaternionf wing = WingsMath.wingRotation(side, d.variant(), d.stance(), d.flap(), d.flapPhase());
        return new Transformation(t, WingsMath.leanRotation(d.lean()), new Vector3f(1f, 1f, 1f), wing);
    }

    @Override
    public boolean spawn(UUID owner, WingsPose.Side side, WingsRig.Desired d) {
        Player p = Bukkit.getPlayer(owner);
        if (p == null || !p.isOnline()) {
            return false;
        }
        try {
            ItemDisplay display = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, e -> {
                e.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, owner.toString());
                e.addScoreboardTag(SCOREBOARD_TAG);
                e.setPersistent(false);
                e.setInvulnerable(true);
                e.setGravity(false);
                e.setItemStack(stack(d.tier(), d.variant(), side));
                e.setBillboard(Display.Billboard.FIXED);
                e.setTeleportDuration(WingsRig.FAST);
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(0);
                e.setViewRange(1.0f);
                e.setRotation(d.yaw(), 0);
                e.setTransformation(transform(d, side));
            });
            if (!p.addPassenger(display)) {
                display.remove();
                return false;
            }
            displays.computeIfAbsent(owner, k -> new EnumMap<>(WingsPose.Side.class)).put(side, display);
            if (d.hideFromOwner()) {
                p.hideEntity(core.getPlugin(), display);
            }
            return true;
        } catch (RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                core.getPlugin().getLogger().warning("Wings display could not be created: " + e);
            }
            return false;
        }
    }

    @Override
    public void update(UUID owner, WingsPose.Side side, WingsRig.Desired prev, WingsRig.Desired next, int ticks) {
        ItemDisplay display = display(owner, side);
        Player p = Bukkit.getPlayer(owner);
        if (display == null || p == null) {
            return;
        }
        if (prev.tier() != next.tier() || prev.variant() != next.variant()) {
            display.setItemStack(stack(next.tier(), next.variant(), side));
        }
        if (prev.yaw() != next.yaw()) {
            display.setRotation(next.yaw(), 0);
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transform(next, side));
        if (prev.hideFromOwner() != next.hideFromOwner()) {
            if (next.hideFromOwner()) {
                p.hideEntity(core.getPlugin(), display);
            } else {
                p.showEntity(core.getPlugin(), display);
            }
        }
    }

    @Override
    public void remove(UUID owner, WingsPose.Side side) {
        EnumMap<WingsPose.Side, ItemDisplay> pair = displays.get(owner);
        if (pair == null) {
            return;
        }
        ItemDisplay display = pair.remove(side);
        if (display != null) {
            display.remove();
        }
        if (pair.isEmpty()) {
            displays.remove(owner);
        }
    }

    @Override
    public boolean alive(UUID owner, WingsPose.Side side) {
        ItemDisplay display = display(owner, side);
        Player p = Bukkit.getPlayer(owner);
        return display != null && p != null && display.isValid() && display.getVehicle() == p;
    }

    private ItemDisplay display(UUID owner, WingsPose.Side side) {
        EnumMap<WingsPose.Side, ItemDisplay> pair = displays.get(owner);
        return pair == null ? null : pair.get(side);
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        detach(event.getPlayer().getUniqueId());  // respawned on the next tick in the new world
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        detach(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            detach(event.getPlayer().getUniqueId());
        }
    }
}
