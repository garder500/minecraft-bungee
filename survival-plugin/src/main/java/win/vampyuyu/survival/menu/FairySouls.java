package win.vampyuyu.survival.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.api.IslandInfo;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.menu.SoulLogic.SoulDef;
import win.vampyuyu.survival.menu.SoulLogic.SoulSpot;

/**
 * Fairy Souls. Each soul is one non-persistent {@link ItemDisplay} that is hidden from everybody by default and shown
 * (per player, {@code Player.showEntity}) only to players within range who have not collected it. Souls are found by
 * walking up to them (display entities have no hitbox to click). The set of found souls lives in the profile section
 * {@code fairy_souls}; stat rewards are re-applied as the {@code "fairy"} StatsApi source on every join because
 * modifiers live in memory only.
 */
final class FairySouls implements Listener {
    static final String STAT_SOURCE = "fairy";
    private static final String ADMIN_FILE = "menu/fairy_souls.json";

    /** Admin placed souls (file format). */
    static final class AdminFile {
        public List<AdminSoul> souls = new ArrayList<>();
    }

    /** One admin placed soul. */
    static final class AdminSoul {
        public String id = "";
        public String world = "";
        public double x;
        public double y;
        public double z;
    }

    private static final class Live {
        final ItemDisplay entity;
        final Set<UUID> shownTo = new HashSet<>();

        Live(ItemDisplay entity) {
            this.entity = entity;
        }
    }

    private final MenuRuntime rt;
    private final QuestService quests;
    private final NamespacedKey tag;
    private final Map<String, Live> lives = new HashMap<>();
    private final List<SoulDef> adminSouls = new ArrayList<>();
    private final OrderedJsonWriter adminWriter;
    private Scheduler.Handle task;
    private int tickCount;

    FairySouls(MenuRuntime rt, QuestService quests) {
        this.rt = rt;
        this.quests = quests;
        this.tag = new NamespacedKey(rt.core.getPlugin(), "menu_soul");
        this.adminWriter = new OrderedJsonWriter(rt.core, ADMIN_FILE);
    }

    // ---------------------------------------------------------------- lifecycle

    void start() {
        AdminFile file = rt.core.store().read(ADMIN_FILE, AdminFile.class, AdminFile::new);
        adminSouls.clear();
        if (file.souls != null) {
            for (AdminSoul soul : file.souls) {
                try {
                    adminSouls.add(new SoulDef(soul.id, soul.world, soul.x, soul.y, soul.z, false));
                } catch (RuntimeException e) {
                    rt.core.debug("Ignoring bad admin fairy soul " + soul.id + ": " + e.getMessage());
                }
            }
        }
        removeLeftovers();
        task = rt.core.scheduler().repeating(20, 10, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Live live : lives.values()) {
            live.entity.remove();
        }
        lives.clear();
        removeLeftovers();
    }

    /** Removes souls of an earlier run of the plugin (a /reload keeps entities alive). */
    private void removeLeftovers() {
        for (World world : Bukkit.getWorlds()) {
            if (!SkyblockWorlds.isSkyblock(world.getName())) {
                continue;
            }
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) {
                    display.remove();
                }
            }
        }
    }

    // ---------------------------------------------------------------- definitions

    private List<SoulDef> fixedSouls() {
        List<SoulDef> all = new ArrayList<>(rt.config().fairyFixed);
        all.addAll(adminSouls);
        return all;
    }

    /** Number of souls a player can find in total (fixed souls plus one island's worth). */
    int total() {
        return fixedSouls().size() + rt.config().fairyIsland.size();
    }

    List<SoulDef> adminSouls() {
        return List.copyOf(adminSouls);
    }

    /** Adds an admin soul at a location. Returns the id, or empty when the world is not a Skyblock world. */
    Optional<String> addAdmin(Location where, String requestedId) {
        World world = where.getWorld();
        if (world == null || !SkyblockWorlds.isSkyblock(world.getName()) || SkyblockWorlds.isDungeon(world.getName())) {
            return Optional.empty();
        }
        String id = requestedId == null || requestedId.isBlank() ? null : requestedId.toLowerCase(Locale.ROOT);
        if (id == null) {
            int n = 1;
            while (idTaken("soul_" + n)) {
                n++;
            }
            id = "soul_" + n;
        }
        if (!id.matches("[a-z0-9_]+") || idTaken(id)) {
            return Optional.empty();
        }
        adminSouls.add(new SoulDef(id, MenuConfig.worldToken(world.getName()), where.getX(), where.getY() + 0.5,
            where.getZ(), false));
        saveAdmin();
        return Optional.of(id);
    }

    boolean removeAdmin(String id) {
        boolean removed = adminSouls.removeIf(s -> s.id().equals(id));
        if (removed) {
            saveAdmin();
            discard(id);
        }
        return removed;
    }

    private boolean idTaken(String id) {
        return fixedSouls().stream().anyMatch(s -> s.id().equals(id))
            || rt.config().fairyIsland.stream().anyMatch(s -> s.id().equals(id));
    }

    private void saveAdmin() {
        AdminFile file = new AdminFile();
        for (SoulDef def : adminSouls) {
            AdminSoul soul = new AdminSoul();
            soul.id = def.id();
            soul.world = def.world();
            soul.x = def.x();
            soul.y = def.y();
            soul.z = def.z();
            file.souls.add(soul);
        }
        adminWriter.write(file);
    }

    /** Despawns the entities of a soul id (after an admin removed it). */
    private void discard(String id) {
        Iterator<Map.Entry<String, Live>> it = lives.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Live> entry = it.next();
            if (entry.getKey().startsWith(id + "@")) {
                entry.getValue().entity.remove();
                it.remove();
            }
        }
    }

    private List<SoulSpot> spotsAround(Player player) {
        Location loc = player.getLocation();
        double[] center = null;
        Optional<IslandInfo> island = rt.islands().flatMap(api -> api.islandAt(loc));
        if (island.isPresent() && island.get().center() != null && island.get().center().getWorld() != null
                && island.get().center().getWorld().equals(loc.getWorld())) {
            Location c = island.get().center();
            center = new double[] {c.getX(), c.getY(), c.getZ()};
        }
        return SoulLogic.spotsFor(player.getWorld().getName(), center, fixedSouls(), rt.config().fairyIsland,
            MenuConfig::worldName);
    }

    // ---------------------------------------------------------------- scanning

    private void tick() {
        MenuConfig cfg = rt.config();
        if (!cfg.fairyEnabled) {
            return;
        }
        tickCount++;
        double view = cfg.fairyViewDistance;
        double collect = cfg.fairyCollectRadius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                scan(player, view, collect);
            } catch (RuntimeException e) {
                rt.core.getPlugin().getLogger().warning("Fairy soul scan failed for " + player.getName() + ": " + e);
            }
        }
        // despawn souls nobody is near, and forget dead entities
        Iterator<Map.Entry<String, Live>> it = lives.entrySet().iterator();
        while (it.hasNext()) {
            Live live = it.next().getValue();
            if (!live.entity.isValid()) {
                it.remove();
                continue;
            }
            Location at = live.entity.getLocation();
            boolean near = false;
            for (Player player : at.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(at) <= view * view * 2.25) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                live.entity.remove();
                it.remove();
            }
        }
    }

    private void scan(Player player, double view, double collect) {
        if (!MenuRuntime.inSkyblock(player.getWorld()) || SkyblockWorlds.isDungeon(player.getWorld().getName())
                || player.isDead()) {
            return;
        }
        FairyData data = rt.fairy(player.getUniqueId());
        Location here = player.getLocation();
        for (SoulSpot spot : spotsAround(player)) {
            double d2 = spot.distanceSquared(here.getX(), here.getY(), here.getZ());
            String key = key(spot);
            Live live = lives.get(key);
            if (!SoulLogic.visibleTo(data.found, spot.id())) {
                if (live != null && live.shownTo.remove(player.getUniqueId()) && live.entity.isValid()) {
                    player.hideEntity(rt.core.getPlugin(), live.entity);
                }
                continue;
            }
            if (d2 > view * view) {
                if (live != null && live.shownTo.remove(player.getUniqueId()) && live.entity.isValid()) {
                    player.hideEntity(rt.core.getPlugin(), live.entity);
                }
                continue;
            }
            if (live == null || !live.entity.isValid()) {
                live = spawn(player.getWorld(), spot);
                if (live == null) {
                    continue;
                }
                lives.put(key, live);
            }
            if (live.shownTo.add(player.getUniqueId())) {
                player.showEntity(rt.core.getPlugin(), live.entity);
            }
            if (d2 <= collect * collect) {
                collect(player, spot, live);
            } else if (d2 <= 18 * 18 && tickCount % 2 == 0) {
                player.spawnParticle(Particle.END_ROD, spot.x(), spot.y() + 0.2, spot.z(), 1, 0.25, 0.3, 0.25, 0.01);
            }
        }
    }

    private static String key(SoulSpot spot) {
        return spot.id() + "@" + spot.worldName() + ":" + Math.round(spot.x() * 2) + ":" + Math.round(spot.y() * 2)
            + ":" + Math.round(spot.z() * 2);
    }

    private Live spawn(World world, SoulSpot spot) {
        int cx = (int) Math.floor(spot.x()) >> 4;
        int cz = (int) Math.floor(spot.z()) >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            return null;
        }
        MenuConfig cfg = rt.config();
        Color glow = parseColor(cfg.fairyGlow);
        ItemStack stack = new ItemStack(Material.PINK_DYE);
        stack.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("vampyuyu:menu/fairy_soul")));
        ItemDisplay display = world.spawn(new Location(world, spot.x(), spot.y(), spot.z()), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setItemStack(stack);
            e.setBillboard(Display.Billboard.CENTER);
            e.setGlowing(true);
            e.setGlowColorOverride(glow);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(0.6f);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.7f, 0.7f, 0.7f),
                new AxisAngle4f()));
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        });
        return new Live(display);
    }

    private static Color parseColor(String hex) {
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            int rgb = Integer.parseInt(clean, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (RuntimeException e) {
            return Color.fromRGB(0xFF, 0x7A, 0xD9);
        }
    }

    // ---------------------------------------------------------------- collecting

    /** Marks the soul found (exactly once), then pays milestones. Main thread. */
    void collect(Player player, SoulSpot spot, Live live) {
        FairyData data = rt.fairy(player.getUniqueId());
        if (!data.found.add(spot.id())) {
            return;
        }
        rt.dirty(player.getUniqueId());
        if (live != null) {
            live.shownTo.remove(player.getUniqueId());
            if (live.entity.isValid()) {
                player.hideEntity(rt.core.getPlugin(), live.entity);
            }
        }
        Location at = new Location(player.getWorld(), spot.x(), spot.y(), spot.z());
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 24, 0.3, 0.4, 0.3, 0.2);
        int count = data.found.size();
        rt.core.msg().mini(player, "<light_purple><bold>FAIRY SOUL</bold> <gray>You found a soul! <dark_gray>(<light_purple>"
            + count + "<dark_gray>/<light_purple>" + total() + "<dark_gray>)");
        rt.play(player, "soul-found");
        payMilestones(player, data);
        quests.record(player, QuestType.COLLECT_SOUL, "ANY", 1);
    }

    /** Pays each completed milestone once; the counter moves BEFORE the payment. */
    void payMilestones(Player player, FairyData data) {
        MenuConfig cfg = rt.config();
        int reached = SoulLogic.milestones(data.found.size(), cfg.fairyEvery);
        boolean any = false;
        while (data.paidMilestones < reached) {
            data.paidMilestones++;
            any = true;
            int number = data.paidMilestones;
            SoulLogic.StatStep step = cfg.fairyCycle.isEmpty() ? null
                : cfg.fairyCycle.get((number - 1) % cfg.fairyCycle.size());
            String reward = "";
            if (cfg.fairyCoinsPerMilestone > 0) {
                if (rt.economy().isPresent()) {
                    rt.economy().get().add(player.getUniqueId(), cfg.fairyCoinsPerMilestone, "fairy:milestone");
                } else {
                    data.pendingCoins += cfg.fairyCoinsPerMilestone;
                }
                reward += "<gold>+" + Msg.number(cfg.fairyCoinsPerMilestone) + " coins";
            }
            if (step != null) {
                reward += (reward.isEmpty() ? "" : "<gray>, ") + "<#" + String.format("%06x", step.stat().color().value())
                    + ">+" + Msg.number(step.amount()) + " " + step.stat().displayName();
            }
            rt.core.msg().mini(player, "<light_purple><bold>MILESTONE " + number + "</bold> <gray>" + reward);
            rt.play(player, "milestone");
        }
        if (any) {
            rt.dirty(player.getUniqueId());
            applyStats(player);
        }
        flushPending(player, data);
    }

    private void flushPending(Player player, FairyData data) {
        if (data.pendingCoins > 0 && rt.economy().isPresent()) {
            double coins = data.pendingCoins;
            data.pendingCoins = 0;
            rt.dirty(player.getUniqueId());
            rt.economy().get().add(player.getUniqueId(), coins, "fairy:pending");
        }
    }

    /** (Re)applies the stat bonuses of the paid milestones as the "fairy" source. Main thread. */
    void applyStats(Player player) {
        rt.stats().ifPresent(api -> {
            FairyData data = rt.fairy(player.getUniqueId());
            api.removeModifiers(player.getUniqueId(), STAT_SOURCE);
            for (Map.Entry<Stat, Double> bonus : SoulLogic.milestoneStats(data.paidMilestones, rt.config().fairyCycle).entrySet()) {
                api.addModifier(player.getUniqueId(), STAT_SOURCE, bonus.getKey(), bonus.getValue());
            }
            api.recompute(player);
        });
    }

    /** Join hook: stats come back (modifiers are not persisted) and unpaid coins are paid. */
    void onArrive(Player player) {
        FairyData data = rt.fairy(player.getUniqueId());
        payMilestones(player, data); // pays anything missed, e.g. after the milestone size was lowered
        applyStats(player);
        rt.core.scheduler().later(60, () -> {
            if (player.isOnline()) {
                applyStats(player); // the stats engine may have rebuilt its cache after the join
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Live live : lives.values()) {
            live.shownTo.remove(id);
        }
        rt.stats().ifPresent(api -> api.removeModifiers(id, STAT_SOURCE));
    }

    // ---------------------------------------------------------------- /fairy hint

    /** Tells the player about their progress and points at the nearest uncollected soul around them. */
    void hint(Player player) {
        FairyData data = rt.fairy(player.getUniqueId());
        MenuConfig cfg = rt.config();
        if (!cfg.fairyEnabled) {
            rt.core.msg().warn(player, "Fairy Souls are switched off on this server.");
            return;
        }
        int count = data.found.size();
        rt.core.msg().mini(player, "<light_purple>Fairy Souls: <white>" + count + "<gray>/<white>" + total()
            + (cfg.fairyEvery > 0 ? " <dark_gray>(next reward in " + SoulLogic.untilNext(count, cfg.fairyEvery) + ")" : ""));
        if (!MenuRuntime.inSkyblock(player.getWorld())) {
            return;
        }
        Location here = player.getLocation();
        Optional<SoulSpot> nearest = SoulLogic.nearest(spotsAround(player), data.found, here.getX(), here.getY(), here.getZ());
        if (nearest.isEmpty()) {
            rt.core.msg().info(player, "No uncollected souls around here. Try the hub or your island (see Warps).");
            return;
        }
        SoulSpot spot = nearest.get();
        double dx = spot.x() - here.getX();
        double dz = spot.z() - here.getZ();
        long distance = Math.round(Math.sqrt(spot.distanceSquared(here.getX(), here.getY(), here.getZ())));
        String vertical = Math.abs(spot.y() - here.getY()) < 3 ? ""
            : spot.y() > here.getY() ? ", above you" : ", below you";
        rt.core.msg().mini(player, "<gray>Nearest soul: <light_purple>" + distance + " blocks "
            + SoulLogic.direction(dx, dz) + vertical);
        player.setCompassTarget(new Location(player.getWorld(), spot.x(), spot.y(), spot.z()));
        trail(player, spot);
    }

    /** A short line of particles from the player toward the soul, visible to that player only. */
    private void trail(Player player, SoulSpot spot) {
        int[] runs = {0};
        Scheduler.Handle[] handle = new Scheduler.Handle[1];
        handle[0] = rt.core.scheduler().repeating(0, 4, () -> {
            if (!player.isOnline() || ++runs[0] > 12 || !player.getWorld().getName().equals(spot.worldName())) {
                handle[0].cancel();
                return;
            }
            Location from = player.getEyeLocation().add(0, -0.5, 0);
            org.bukkit.util.Vector toward = new org.bukkit.util.Vector(spot.x() - from.getX(), spot.y() - from.getY(),
                spot.z() - from.getZ());
            double length = toward.length();
            if (length < 1) {
                handle[0].cancel();
                return;
            }
            toward.normalize();
            for (int i = 2; i <= 8; i++) {
                if (i > length) {
                    break;
                }
                Location p = from.clone().add(toward.clone().multiply(i * 0.8));
                player.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
            }
        });
    }
}
