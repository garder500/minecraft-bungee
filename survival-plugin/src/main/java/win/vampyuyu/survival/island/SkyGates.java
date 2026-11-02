package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.api.SkyGateApi;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * The Sky Gates of the Quai des Huit. Gate state is per player and saved in {@code island/gates.json}. Every Gate is
 * built dark (black glass); a player who lit one sees it coloured (client-side block changes, refreshed every
 * second while they are near the Quai) and walking into it starts the travel: a short blindness hides the
 * teleport, a title names the Realm. Until a Realm registers a destination (see {@link SkyGateApi}) a lit Gate leads
 * to a placeholder landing platform, so the travel can be tested. Back: {@code /hub} and {@code /island}.
 */
public final class SkyGates implements SkyGateApi, Listener {
    public static final String ADMIN = "vampyuyu.sb.gates.admin";
    public static final String USE = "vampyuyu.sb.gates.use";
    private static final String HUB_LANE = "hub";
    private static final int TRAVEL_FADE_TICKS = 24;
    private static final double VIEW_RANGE = 48;

    private final Core core;
    private final IslandService service;
    private final IslandConfig cfg;
    private final QuaiManager quai;
    private final Cooldowns notices = new Cooldowns();
    private final Map<Realm, Function<Player, Location>> destinations = new EnumMap<>(Realm.class);
    private final Map<Realm, CompletableFuture<Location>> landings = new EnumMap<>(Realm.class);
    private final Set<UUID> travelling = new HashSet<>();
    /** Gates currently shown lit to a player, so a gate put out is restored. */
    private final Map<UUID, Set<Realm>> shown = new HashMap<>();
    private SkyGateState state = new SkyGateState();
    private Scheduler.Handle viewTask;
    private int viewTick;

    public SkyGates(Core core, IslandService service) {
        this.core = core;
        this.service = service;
        this.cfg = service.config();
        this.quai = new QuaiManager(core, service);
    }

    public QuaiManager quai() {
        return quai;
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        state = SkyGateState.fromDoc(core.store().read(SkyGateState.FILE, SkyGateState.Doc.class, SkyGateState.Doc::new));
        core.permissions().register(ADMIN, "Light and put out Sky Gates", PermissionDefault.OP);
        core.listen(this);
        registerCommands();
        quai.start();
        viewTask = core.scheduler().repeating(10L, 10L, this::tick);
    }

    public void close() {
        if (viewTask != null) {
            viewTask.cancel();
            viewTask = null;
        }
    }

    private void save() {
        SkyGateState.Doc doc = state.toDoc();
        core.scheduler().async(() -> {
            try {
                core.store().write(SkyGateState.FILE, doc);
            } catch (java.io.IOException e) {
                service.logger().log(Level.WARNING, "Could not save the Sky Gates", e);
            }
        });
    }

    // ---------------------------------------------------------------- SkyGateApi

    @Override
    public boolean isLit(UUID player, Realm realm) {
        return state.isLit(player, realm);
    }

    @Override
    public Set<Realm> litRealms(UUID player) {
        return state.litRealms(player);
    }

    @Override
    public boolean light(UUID player, Realm realm) {
        if (!state.light(player, realm)) {
            return false;
        }
        save();
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            refreshView(online, true);
            core.msg().ok(online, "The Sky Gate to the " + realm.title() + " is lit.");
            online.playSound(online, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        }
        return true;
    }

    @Override
    public boolean extinguish(UUID player, Realm realm) {
        if (!state.extinguish(player, realm)) {
            return false;
        }
        save();
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            refreshView(online, true);
        }
        return true;
    }

    @Override
    public void setDestination(Realm realm, Function<Player, Location> destination) {
        if (destination == null) {
            destinations.remove(realm);
        } else {
            destinations.put(realm, destination);
        }
    }

    @Override
    public Location quaiSpawn() {
        return quai.spawn();
    }

    // ---------------------------------------------------------------- travel

    @Override
    public boolean travel(Player player, Realm realm) {
        if (!state.isLit(player.getUniqueId(), realm)) {
            notice(player, "The Sky Gate to the " + realm.title() + " is dormant.", false);
            return false;
        }
        if (service.inDungeon(player)) {
            notice(player, "You cannot leave a dungeon run like that.", false);
            return false;
        }
        if (!travelling.add(player.getUniqueId())) {
            return false;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, TRAVEL_FADE_TICKS + 26, 0, false, false, false));
        player.playSound(player, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.3f);
        player.showTitle(Title.title(Component.text("Gate " + realm.number(), NamedTextColor.GOLD),
            Component.text(realm.title(), NamedTextColor.WHITE),
            Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1400), java.time.Duration.ofMillis(500))));
        core.scheduler().later(TRAVEL_FADE_TICKS, () -> arrive(player, realm));
        return true;
    }

    private void arrive(Player player, Realm realm) {
        Function<Player, Location> custom = destinations.get(realm);
        CompletableFuture<Location> where;
        try {
            Location chosen = custom == null ? null : custom.apply(player);
            where = custom == null ? landing(realm) : CompletableFuture.completedFuture(chosen);
        } catch (RuntimeException e) {
            service.logger().log(Level.WARNING, "The destination of " + realm.id() + " failed", e);
            where = CompletableFuture.completedFuture(null);
        }
        where.thenAccept(location -> core.scheduler().sync(() -> {
            travelling.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (location == null || location.getWorld() == null) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                core.msg().warn(player, "The way through the Gate is not open right now.");
                return;
            }
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.playSound(player, Sound.BLOCK_END_PORTAL_SPAWN, 0.3f, 1.8f);
        }));
    }

    /** The placeholder landing of a Realm, built on first use. Completes on the main thread. */
    CompletableFuture<Location> landing(Realm realm) {
        World hub = quai.hub();
        if (hub == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Location> cached = landings.get(realm);
        if (cached != null && !cached.isCompletedExceptionally()) {
            return cached;
        }
        int[] c = QuaiLanding.center(realm);
        int y = cfg.hubY + QuaiLanding.HEIGHT_ABOVE_HUB;
        Location spot = new Location(hub, c[0] + 0.5, y + 1, c[1] + 0.5, 0f, 0f);
        CompletableFuture<Location> future;
        if (hub.getBlockAt(c[0], y, c[1]).getType() != Material.AIR) {
            future = CompletableFuture.completedFuture(spot);
        } else {
            future = service.runner().submit(HUB_LANE, new PlanJob(core.getPlugin(), "landing " + realm.id(), hub,
                QuaiLanding.build(realm), c[0], y, c[1], cfg.chunkTimeoutTicks)).thenApply(ok -> ok ? spot : null);
        }
        landings.put(realm, future);
        future.thenAccept(location -> {
            if (location == null) {
                landings.remove(realm, future);
            }
        });
        return future;
    }

    // ---------------------------------------------------------------- walking into a gate

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()
                && to.getBlockY() == from.getBlockY()) {
            return;
        }
        World world = to.getWorld();
        if (world == null || !SkyblockWorlds.HUB.equals(world.getName())) {
            return;
        }
        Player player = event.getPlayer();
        Realm gate = gateAt(to.getX(), to.getY() - cfg.hubY, to.getZ());
        if (gate == null || travelling.contains(player.getUniqueId())) {
            return;
        }
        if (state.isLit(player.getUniqueId(), gate)) {
            travel(player, gate);
        } else {
            notice(player, "The Sky Gate to the " + gate.title() + " is dormant.", false);
        }
    }

    /**
     * The gate whose opening a position (hub world X, Y relative to the platform surface, Z) is touching, or null.
     * The player has to be on the dais side of the glass, within reach of it.
     */
    static Realm gateAt(double x, double yRel, double z) {
        double rz = z - QuaiPlan.CENTER_Z;
        if (yRel < -0.5 || yRel > 6.5 || Math.abs(x) > QuaiPlan.RADIUS || Math.abs(rz) > QuaiPlan.RADIUS) {
            return null;
        }
        for (QuaiPlan.Gate gate : QuaiPlan.GATES) {
            double dx = x - (gate.x() + 0.5);
            double dz = rz - (gate.z() + 0.5);
            double along = dx * gate.outX() + dz * gate.outZ();
            double across = Math.abs(-dx * gate.outZ() + dz * gate.outX());
            if (along <= 0.6 && along >= -2.2 && across <= 2.4) {
                return gate.realm();
            }
        }
        return null;
    }

    private void notice(Player player, String text, boolean ok) {
        if (notices.tryUse(player.getUniqueId(), "gate", 2500)) {
            if (ok) {
                core.msg().ok(player, text);
            } else {
                core.msg().warn(player, text);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
        travelling.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- what a player sees

    private void tick() {
        viewTick++;
        World hub = quai.hub();
        if (hub == null) {
            return;
        }
        Location center = quai.center();
        for (Player player : hub.getPlayers()) {
            if (player.getLocation().distanceSquared(center) > VIEW_RANGE * VIEW_RANGE) {
                shown.remove(player.getUniqueId());
                continue;
            }
            Set<Realm> lit = state.litRealms(player.getUniqueId());
            if (lit.isEmpty() && !shown.containsKey(player.getUniqueId())) {
                continue;
            }
            // a chunk the client reloaded forgets the block changes: resend the lit gates once a second
            refreshView(player, viewTick % 2 == 0);
            for (Realm realm : lit) {
                QuaiPlan.Gate gate = QuaiPlan.gate(realm);
                Location at = new Location(hub, gate.x() + 0.5, cfg.hubY + 3, QuaiPlan.CENTER_Z + gate.z() + 0.5);
                player.spawnParticle(Particle.PORTAL, at, 8, 0.9, 1.6, 0.9, 0.3);
            }
        }
    }

    /** Sends the lit gates, and restores the ones that went out since the last call. {@code resend}: lit ones too. */
    void refreshView(Player player, boolean resend) {
        World hub = quai.hub();
        if (hub == null || !hub.equals(player.getWorld())) {
            return;
        }
        Set<Realm> lit = state.litRealms(player.getUniqueId());
        Set<Realm> was = shown.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        for (Realm realm : Realm.values()) {
            boolean isLit = lit.contains(realm);
            if (isLit && (resend || !was.contains(realm))) {
                show(player, hub, realm, true);
                was.add(realm);
            } else if (!isLit && was.remove(realm)) {
                show(player, hub, realm, false);
            }
        }
        if (was.isEmpty()) {
            shown.remove(player.getUniqueId());
        }
    }

    private void show(Player player, World hub, Realm realm, boolean lit) {
        QuaiPlan.Gate gate = QuaiPlan.gate(realm);
        Map<Location, BlockData> changes = new HashMap<>();
        BlockData membrane = (lit ? gate.litMembrane() : QuaiPlan.DARK_MEMBRANE).createBlockData();
        for (int[] cell : gate.membrane()) {
            changes.put(new Location(hub, cell[0], cfg.hubY + cell[1], QuaiPlan.CENTER_Z + cell[2]), membrane);
        }
        int[] crown = gate.crown();
        changes.put(new Location(hub, crown[0], cfg.hubY + crown[1], QuaiPlan.CENTER_Z + crown[2]),
            (lit ? QuaiPlan.LIT_CROWN : QuaiPlan.DARK_CROWN).createBlockData());
        player.sendMultiBlockChange(changes);
        int[] at = QuaiPlan.signPos(gate);
        Block block = hub.getBlockAt(at[0], cfg.hubY + 1, QuaiPlan.CENTER_Z + at[1]);
        if (block.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(3, Component.text(lit ? "Lit" : "Dormant"));
            player.sendBlockUpdate(block.getLocation(), sign);
        }
    }

    // ---------------------------------------------------------------- commands

    private void registerCommands() {
        core.commands().command("quai").aliases("docks")
            .description("Go to the Quai des Huit").usage("/quai")
            .permission(USE, PermissionDefault.TRUE)
            .executor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    goQuai(player);
                } else {
                    core.msg().error(sender, "Only players can use /quai.");
                }
                return true;
            }).register();
        core.commands().command("gates")
            .description("Show which Sky Gates are lit").usage("/gates")
            .permission(USE, PermissionDefault.TRUE)
            .executor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    list(player, player.getUniqueId(), player.getName());
                } else {
                    core.msg().error(sender, "Only players can use /gates. Admins: /gateadmin.");
                }
                return true;
            }).register();
        core.commands().command("gateadmin")
            .description("Light, put out and test Sky Gates")
            .usage("/gateadmin <light|dark|list|travel|rebuild> [player] [realm|all]")
            .permission(ADMIN, PermissionDefault.OP)
            .executor(this::admin).tab(this::tab).register();
    }

    private void goQuai(Player player) {
        if (service.inDungeon(player)) {
            core.msg().error(player, "You cannot leave a dungeon run like that.");
            return;
        }
        Location spawn = quai.spawn();
        if (spawn == null || !quai.ready()) {
            core.msg().error(player, "The Quai des Huit is not built yet. Try again in a moment.");
            return;
        }
        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
        core.msg().info(player, "Welcome to the Quai des Huit. /hub or /island to leave.");
    }

    private void list(CommandSender to, UUID who, String name) {
        Set<Realm> lit = state.litRealms(who);
        core.msg().info(to, "Sky Gates of " + name + " (" + lit.size() + "/" + Realm.values().length + " lit):");
        for (Realm realm : Realm.values()) {
            boolean on = lit.contains(realm);
            to.sendMessage(Component.text(" " + realm.number() + ". ", NamedTextColor.DARK_GRAY)
                .append(Component.text(realm.title(), on ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                .append(Component.text(on ? "  lit" : "  dormant", on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        }
    }

    private boolean admin(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "rebuild" -> {
                if (quai.hub() == null) {
                    core.msg().error(sender, "There is no hub world.");
                } else {
                    quai.rebuild();
                    core.msg().ok(sender, "Rebuilding the Quai des Huit.");
                }
            }
            case "light", "dark" -> change(sender, sub.equals("light"), args);
            case "list" -> {
                Optional<UUID> who = playerArg(sender, args, 1);
                who.ifPresent(uuid -> list(sender, uuid, args.length > 1 ? args[1] : sender.getName()));
            }
            case "travel" -> {
                Optional<UUID> who = playerArg(sender, args, 1);
                Realm realm = args.length > 2 ? Realm.byId(args[2]) : null;
                Player online = who.map(Bukkit::getPlayer).orElse(null);
                if (online == null || realm == null) {
                    core.msg().error(sender, "Usage: /gateadmin travel <online player> <realm>");
                } else {
                    core.msg().info(sender, travel(online, realm) ? "Sent through the Gate." : "The Gate did not open (is it lit?).");
                }
            }
            default -> core.msg().error(sender, "/gateadmin <light|dark|list|travel|rebuild> [player] [realm|all]");
        }
        return true;
    }

    private void change(CommandSender sender, boolean light, String[] args) {
        Optional<UUID> who = playerArg(sender, args, 1);
        if (who.isEmpty()) {
            return;
        }
        if (args.length < 3) {
            core.msg().error(sender, "Name a realm (" + realmIds() + ") or 'all'.");
            return;
        }
        List<Realm> targets = new ArrayList<>();
        if (args[2].equalsIgnoreCase("all")) {
            targets.addAll(List.of(Realm.values()));
        } else {
            Realm realm = Realm.byId(args[2]);
            if (realm == null) {
                core.msg().error(sender, "Unknown realm '" + args[2] + "'. Realms: " + realmIds() + ", all.");
                return;
            }
            targets.add(realm);
        }
        int changed = 0;
        for (Realm realm : targets) {
            if (light ? light(who.get(), realm) : extinguish(who.get(), realm)) {
                changed++;
            }
        }
        core.msg().ok(sender, changed + " Sky Gate(s) " + (light ? "lit" : "put out") + " for " + args[1] + ".");
    }

    private Optional<UUID> playerArg(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            if (sender instanceof Player self) {
                return Optional.of(self.getUniqueId());
            }
            core.msg().error(sender, "Name a player.");
            return Optional.empty();
        }
        Optional<UUID> found = service.resolve(args[index]);
        if (found.isEmpty()) {
            core.msg().error(sender, "Unknown player '" + args[index] + "'. They must have played here before.");
        }
        return found;
    }

    private static String realmIds() {
        List<String> ids = new ArrayList<>();
        for (Realm realm : Realm.values()) {
            ids.add(realm.id());
        }
        return String.join(", ", ids);
    }

    private List<String> tab(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("light", "dark", "list", "travel", "rebuild"));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 3) {
            for (Realm realm : Realm.values()) {
                out.add(realm.id());
            }
            out.add("all");
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
