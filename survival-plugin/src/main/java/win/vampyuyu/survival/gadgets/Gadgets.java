package win.vampyuyu.survival.gadgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.quarry.OreRoller;
import win.vampyuyu.survival.quarry.QuarryData;
import win.vampyuyu.survival.quarry.QuarryService;

/** The rules of the Skyhook, Cloud Anchor, Ore Compass, Void Net and Rain Totem. Events are translated by {@link GadgetListener}. */
public final class Gadgets {
    private final Core core;
    private final GadgetSettings settings;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, Long> fallGrace = new HashMap<>();
    private final Map<UUID, Location> safeSpot = new HashMap<>();
    private final Map<UUID, Scheduler.Handle> warmups = new HashMap<>();
    private final List<Scheduler.Handle> totems = new ArrayList<>();

    Gadgets(Core core, GadgetSettings settings) {
        this.core = core;
        this.settings = settings;
    }

    GadgetSettings settings() {
        return settings;
    }

    private IslandApi islands() {
        return core.require(IslandApi.class);
    }

    /** True when the location is inside the island the player owns or belongs to. */
    boolean onOwnIsland(Player player, Location where) {
        Optional<IslandInfo> own = islands().islandOf(player.getUniqueId());
        Optional<IslandInfo> here = islands().islandAt(where);
        return own.isPresent() && here.isPresent() && own.get().id().equals(here.get().id());
    }

    // ---------------------------------------------------------------- Skyhook

    /** Pulls the player toward {@code target}. False (nothing changes) outside Skyblock or while cooling down. */
    public boolean skyhook(Player player, Location target) {
        if (!SkyblockWorlds.isSkyblock(player.getWorld().getName())) {
            core.msg().warn(player, "The Skyhook only works in the Skyblock.");
            return false;
        }
        long left = cooldowns.remaining(player.getUniqueId(), "skyhook");
        if (left > 0) {
            core.msg().warn(player, "The Skyhook is recharging: " + (left + 999) / 1000 + " s.");
            return false;
        }
        Vector pull = target.toVector().subtract(player.getEyeLocation().toVector());
        double distance = pull.length();
        if (distance < 1.5) {
            return false;
        }
        cooldowns.set(player.getUniqueId(), "skyhook", settings.skyhookCooldownMs());
        double speed = Math.min(3.0, 0.5 + distance * settings.skyhookSpeed());
        player.setVelocity(pull.normalize().multiply(speed).add(new Vector(0, 0.15, 0)));
        player.setFallDistance(0);
        fallGrace.put(player.getUniqueId(), System.currentTimeMillis() + settings.skyhookFallGraceMs());
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 1.4f);
        return true;
    }

    /** True while the player's fall damage is cancelled after a hook. */
    boolean fallProtected(UUID player) {
        Long until = fallGrace.get(player);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            fallGrace.remove(player);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- Cloud Anchor

    private GadgetData data(UUID id) {
        return core.profiles().section(id, "gadgets", GadgetData.class, GadgetData::new);
    }

    /** Sets the return point at the player's feet; only on their own island. */
    public boolean setAnchor(Player player) {
        Location l = player.getLocation();
        if (!onOwnIsland(player, l)) {
            core.msg().warn(player, "The Cloud Anchor can only be set on your own island.");
            return false;
        }
        GadgetData d = data(player.getUniqueId());
        d.anchorSet = true;
        d.anchorWorld = l.getWorld().getName();
        d.x = l.getX();
        d.y = l.getY();
        d.z = l.getZ();
        d.yaw = l.getYaw();
        d.pitch = l.getPitch();
        core.profiles().markDirty(player.getUniqueId());
        core.msg().ok(player, "Return point set.");
        return true;
    }

    /** The saved return point, if it is still on the player's own island. */
    Optional<Location> anchor(Player player) {
        GadgetData d = data(player.getUniqueId());
        World world = d.anchorSet && d.anchorWorld != null ? Bukkit.getWorld(d.anchorWorld) : null;
        if (world == null) {
            return Optional.empty();
        }
        Location l = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
        return onOwnIsland(player, l) ? Optional.of(l) : Optional.empty();
    }

    /** Starts the warmup of the return. The cooldown starts when the teleport happens. */
    public boolean startReturn(Player player) {
        UUID id = player.getUniqueId();
        if (warmups.containsKey(id)) {
            return false;
        }
        long left = cooldowns.remaining(id, "anchor");
        if (left > 0) {
            core.msg().warn(player, "The Cloud Anchor is recharging: " + (left + 999) / 1000 + " s.");
            return false;
        }
        Optional<Location> target = anchor(player);
        if (target.isEmpty()) {
            core.msg().warn(player, "No return point on your island. Sneak + right-click to set one.");
            return false;
        }
        Location start = player.getLocation().clone();
        core.msg().info(player, "Hold still...");
        int[] waited = {0};
        Scheduler.Handle[] handle = new Scheduler.Handle[1];
        handle[0] = core.scheduler().repeating(1, 1, () -> {
            if (!player.isOnline() || moved(start, player.getLocation())) {
                handle[0].cancel();
                warmups.remove(id);
                if (player.isOnline()) {
                    core.msg().warn(player, "You moved: the return is cancelled.");
                }
                return;
            }
            if (++waited[0] >= settings.anchorWarmupTicks()) {
                handle[0].cancel();
                warmups.remove(id);
                Optional<Location> now = anchor(player);
                if (now.isEmpty()) {
                    core.msg().warn(player, "Your return point is gone.");
                    return;
                }
                player.teleport(now.get());
                player.setFallDistance(0);
                cooldowns.set(id, "anchor", settings.anchorCooldownMs());
                core.msg().ok(player, "Back at your anchor.");
            }
        });
        warmups.put(id, handle[0]);
        return true;
    }

    private static boolean moved(Location a, Location b) {
        return a.getWorld() != b.getWorld() || a.distanceSquared(b) > 0.09;
    }

    // ---------------------------------------------------------------- Ore Compass

    /** One line per generator ore with a pity: how many blocks until it is guaranteed. */
    public List<String> compassLines(Player player) {
        QuarryService quarry = core.require(QuarryService.class);
        UUID owner = islands().islandOf(player.getUniqueId()).map(IslandInfo::owner).orElse(player.getUniqueId());
        QuarryData data = quarry.data(owner);
        int tier = quarry.tier(owner);
        List<String> out = new ArrayList<>();
        List<OreRoller.Ore> all = new ArrayList<>(quarry.config().roller.ores());
        all.addAll(quarry.config().outputs.ores());
        for (OreRoller.Ore ore : all) {
            if (ore.pity() <= 0 || ore.at(tier) <= 0) {
                continue;
            }
            int left = Math.max(1, ore.pity() - data.counters.getOrDefault(ore.id(), 0));
            String where = switch (ore.source()) {
                case "frost" -> " (Frost generator)";
                case "ember" -> " (Ember generator)";
                default -> "";
            };
            out.add(oreName(ore) + where + ": " + left + (left == 1 ? " more block" : " more blocks"));
        }
        return out;
    }

    private String oreName(OreRoller.Ore ore) {
        if (ore.item().startsWith("custom:")) {
            return core.items().def(ore.item().substring(7)).map(d -> d.name()).orElse(ore.id());
        }
        String[] words = ore.item().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    void readCompass(Player player) {
        List<String> lines = compassLines(player);
        if (lines.isEmpty()) {
            core.msg().info(player, "No ore of your generator has a guaranteed drop yet.");
            return;
        }
        core.msg().info(player, "Guaranteed ores, counted in generator blocks:");
        lines.forEach(line -> core.msg().info(player, line));
    }

    // ---------------------------------------------------------------- Void Net

    private double netFloor(Player player) {
        double top = islands().islandOf(player.getUniqueId()).map(i -> i.center().getY()).orElse(64.0);
        return top - settings.voidNetIslandDepth() - settings.voidNetMargin();
    }

    private int findNet(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && core.items().is(stack, GadgetItems.VOID_NET) && GadgetItems.charges(core.items(), stack) > 0) {
                return i;
            }
        }
        return -1;
    }

    boolean hasNet(Player player) {
        return findNet(player) >= 0;
    }

    /**
     * One look at a player: remembers the last safe block, or catches a fall under the island. Returns true when the
     * player was saved (one charge used).
     */
    boolean checkNet(Player player) {
        UUID id = player.getUniqueId();
        if (!SkyblockWorlds.isSkyblock(player.getWorld().getName()) || !hasNet(player)) {
            safeSpot.remove(id);
            return false;
        }
        double floor = netFloor(player);
        Location at = player.getLocation();
        catchItems(player, floor);
        if (at.getY() >= floor) {
            Block below = at.clone().subtract(0, 0.2, 0).getBlock();
            if (player.isOnGround() && below.getType().isSolid()) {
                safeSpot.put(id, at.clone());
            }
            return false;
        }
        Location back = safeSpot.get(id);
        if (back == null || back.getWorld() != at.getWorld()) {
            back = islands().islandOf(id).map(IslandInfo::center).filter(c -> c.getWorld() == at.getWorld()).orElse(null);
        }
        int slot = findNet(player);
        if (back == null || slot < 0) {
            return false;
        }
        ItemStack net = player.getInventory().getItem(slot).clone();
        int left = GadgetItems.charges(core.items(), net) - 1;
        player.teleport(back);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        if (left <= 0) {
            player.getInventory().setItem(slot, null);
            core.msg().warn(player, "The Void Net caught you and fell apart.");
        } else {
            GadgetItems.setCharges(core.items(), net, left, settings.voidNetCharges());
            player.getInventory().setItem(slot, net);
            core.msg().ok(player, "The Void Net caught you. Charges: " + left + "/" + settings.voidNetCharges());
        }
        player.playSound(back, Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 1.2f);
        return true;
    }

    private void catchItems(Player player, double floor) {
        double r = settings.voidNetItemRadius();
        for (var entity : player.getNearbyEntities(r, 512, r)) {
            if (entity instanceof Item item && item.getLocation().getY() < floor) {
                item.teleport(player.getLocation());
                item.setVelocity(new Vector(0, 0, 0));
            }
        }
    }

    void tickNets() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkNet(p);
        }
    }

    void forget(UUID id) {
        safeSpot.remove(id);
        fallGrace.remove(id);
        Scheduler.Handle w = warmups.remove(id);
        if (w != null) {
            w.cancel();
        }
    }

    // ---------------------------------------------------------------- Rain Totem

    /** Starts a totem around {@code center}. False when the spot is not on the player's own island. */
    public boolean startTotem(Player player, Location center) {
        if (!onOwnIsland(player, center)) {
            core.msg().warn(player, "The Rain Totem only works on your own island.");
            return false;
        }
        IslandInfo island = islands().islandAt(center).orElseThrow();
        int[] runs = {settings.totemSeconds() * 20 / settings.totemIntervalTicks()};
        Scheduler.Handle[] handle = new Scheduler.Handle[1];
        handle[0] = core.scheduler().repeating(settings.totemIntervalTicks(), settings.totemIntervalTicks(), () -> {
            growCrops(center, island);
            if (--runs[0] <= 0) {
                handle[0].cancel();
                totems.remove(handle[0]);
            }
        });
        totems.add(handle[0]);
        core.msg().ok(player, "Rain falls on your crops for " + settings.totemSeconds() + " seconds.");
        return true;
    }

    /** One extra growth tick on the crops around the center. Returns how many grew. */
    int growCrops(Location center, IslandInfo island) {
        int r = settings.totemRadius();
        World world = center.getWorld();
        int grown = 0;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) {
                    continue;
                }
                for (int y = -2; y <= 2; y++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (!Tag.CROPS.isTagged(block.getType()) || !(block.getBlockData() instanceof Ageable age)
                            || age.getAge() >= age.getMaximumAge()) {
                        continue;
                    }
                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= settings.totemChance()) {
                        continue;
                    }
                    Optional<IslandInfo> there = islands().islandAt(block.getLocation());
                    if (there.isEmpty() || !there.get().id().equals(island.id())) {
                        continue;
                    }
                    age.setAge(age.getAge() + 1);
                    block.setBlockData(age);
                    world.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2);
                    grown++;
                }
            }
        }
        return grown;
    }

    void stop() {
        totems.forEach(Scheduler.Handle::cancel);
        totems.clear();
        warmups.values().forEach(Scheduler.Handle::cancel);
        warmups.clear();
    }
}
