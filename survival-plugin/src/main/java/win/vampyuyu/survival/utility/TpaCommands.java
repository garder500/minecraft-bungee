package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionDefault;
import java.util.regex.Pattern;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Msg;

/** /tpa, /tpahere, /tpaccept, /tpdeny, /tpcancel and /tptoggle. */
final class TpaCommands implements Listener {
    private static final String COOLDOWN_KEY = "tpa";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.]{1,32}");

    private final Core core;
    private final Msg msg;
    private final TeleportService teleports;
    private final Supplier<UtilityConfig> config;
    private final TpaManager manager;
    private final Cooldowns cooldowns;

    TpaCommands(Core core, TeleportService teleports, Supplier<UtilityConfig> config, TpaManager manager,
            Cooldowns cooldowns) {
        this.core = core;
        this.msg = core.msg();
        this.teleports = teleports;
        this.config = config;
        this.manager = manager;
        this.cooldowns = cooldowns;
    }

    void register() {
        core.listen(this);
        core.scheduler().repeating(20, 20, this::expire);
        core.commands().command("tpa").description("Ask to teleport to a player").usage("/tpa <player>")
            .permission(Perms.TPA, PermissionDefault.TRUE)
            .executor((s, c, l, a) -> send(s, a, TpaManager.Type.TO_TARGET))
            .tab((s, c, l, a) -> a.length == 1 ? online(s, a[0]) : List.of()).register();
        core.commands().command("tpahere").description("Ask a player to teleport to you").usage("/tpahere <player>")
            .permission(Perms.TPA, PermissionDefault.TRUE)
            .executor((s, c, l, a) -> send(s, a, TpaManager.Type.HERE))
            .tab((s, c, l, a) -> a.length == 1 ? online(s, a[0]) : List.of()).register();
        core.commands().command("tpaccept").aliases("tpyes").description("Accept a teleport request")
            .usage("/tpaccept [player]").permission(Perms.TPA, PermissionDefault.TRUE)
            .executor(this::accept).tab((s, c, l, a) -> a.length == 1 ? senders(s, a[0]) : List.of()).register();
        core.commands().command("tpdeny").aliases("tpno").description("Deny a teleport request")
            .usage("/tpdeny [player]").permission(Perms.TPA, PermissionDefault.TRUE)
            .executor(this::deny).tab((s, c, l, a) -> a.length == 1 ? senders(s, a[0]) : List.of()).register();
        core.commands().command("tpcancel").description("Cancel the teleport request you sent")
            .usage("/tpcancel").permission(Perms.TPA, PermissionDefault.TRUE).executor(this::cancel).register();
        core.commands().command("tptoggle").description("Stop or resume receiving teleport requests")
            .usage("/tptoggle").permission(Perms.TPA, PermissionDefault.TRUE).executor(this::toggle).register();
    }

    private List<String> online(CommandSender sender, String typed) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(sender) && (!(sender instanceof Player self) || self.canSee(p))) {
                names.add(p.getName());
            }
        }
        return Cmd.match(names, typed);
    }

    private List<String> senders(CommandSender sender, String typed) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (TpaManager.Request r : manager.pendingFor(player.getUniqueId())) {
            Player from = Bukkit.getPlayer(r.sender());
            if (from != null) {
                names.add(from.getName());
            }
        }
        return Cmd.match(names, typed);
    }

    private UtilityData data(UUID player) {
        return core.profiles().section(player, "utility", UtilityData.class, UtilityData::new);
    }

    // ---------------------------------------------------------------- send

    private boolean send(CommandSender sender, String[] args, TpaManager.Type type) {
        Player from = Cmd.player(msg, sender);
        if (from == null) {
            return true;
        }
        if (args.length != 1) {
            msg.info(from, "Usage: /" + (type == TpaManager.Type.HERE ? "tpahere" : "tpa") + " <player>");
            return true;
        }
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null || !from.canSee(to) || to.hasMetadata("NPC")) {
            msg.error(from, "Player '" + args[0] + "' is not online.");
            return true;
        }
        if (to.equals(from)) {
            msg.error(from, "You cannot send a request to yourself.");
            return true;
        }
        if (data(to.getUniqueId()).tpaDisabled && !from.hasPermission(Perms.ADMIN)) {
            msg.error(from, to.getName() + " is not accepting teleport requests.");
            return true;
        }
        if (teleports.inDungeon(from) || teleports.inDungeon(to)) {
            msg.error(from, "Teleport requests are disabled inside dungeons.");
            return true;
        }
        boolean bypass = from.hasPermission(Perms.BYPASS_COOLDOWN);
        switch (manager.send(from.getUniqueId(), to.getUniqueId(), type, bypass)) {
            case TpaManager.SendResult.Self self -> msg.error(from, "You cannot send a request to yourself.");
            case TpaManager.SendResult.Cooldown c -> msg.error(from,
                "Slow down. You can send another request in " + Msg.duration(c.remainingMs()) + ".");
            case TpaManager.SendResult.TooMany many -> msg.error(from,
                to.getName() + " already has too many pending requests. Try again later.");
            case TpaManager.SendResult.Sent sent -> {
                int seconds = config.get().tpaExpireSeconds();
                if (sent.replaced() != null && !sent.replaced().target().equals(to.getUniqueId())) {
                    Player old = Bukkit.getPlayer(sent.replaced().target());
                    if (old != null) {
                        msg.info(old, from.getName() + " cancelled their teleport request.");
                    }
                }
                msg.ok(from, "Request sent to " + to.getName() + ". It expires in " + seconds + " seconds. "
                    + "Cancel it with /tpcancel.");
                String who = to.getName();
                String line = type == TpaManager.Type.TO_TARGET
                    ? from.getName() + " wants to teleport to you." : from.getName() + " wants you to teleport to them.";
                if (SAFE_NAME.matcher(from.getName()).matches()) {
                    msg.mini(to, "<yellow>" + line + " <green><click:run_command:'/tpaccept " + from.getName()
                        + "'><hover:show_text:'Accept'>[Accept]</hover></click> <red><click:run_command:'/tpdeny "
                        + from.getName() + "'><hover:show_text:'Deny'>[Deny]</hover></click> <gray>(expires in "
                        + seconds + "s)");
                } else {
                    msg.info(to, line + " Use /tpaccept or /tpdeny. It expires in " + seconds + " seconds.");
                }
                core.debug("tpa " + type + " " + from.getName() + " -> " + who);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- answer

    /** The pending request the player means (by name, or the newest), without removing it. */
    private Optional<TpaManager.Request> find(Player me, String[] args) {
        List<TpaManager.Request> open = manager.pendingFor(me.getUniqueId());
        if (args.length == 0) {
            return open.isEmpty() ? Optional.empty() : Optional.of(open.get(open.size() - 1));
        }
        String wanted = args[0].toLowerCase(Locale.ROOT);
        TpaManager.Request found = null;
        for (TpaManager.Request r : open) {
            Player from = Bukkit.getPlayer(r.sender());
            if (from != null && from.getName().toLowerCase(Locale.ROOT).equals(wanted)) {
                found = r;
            }
        }
        return Optional.ofNullable(found);
    }

    private boolean accept(CommandSender sender, Command command, String label, String[] args) {
        Player me = Cmd.player(msg, sender);
        if (me == null) {
            return true;
        }
        Optional<TpaManager.Request> peek = find(me, args);
        if (peek.isEmpty()) {
            msg.error(me, args.length == 0 ? "You have no pending teleport requests."
                : "You have no pending request from '" + args[0] + "'.");
            return true;
        }
        TpaManager.Request request = peek.get();
        Player from = Bukkit.getPlayer(request.sender());
        if (from == null || !from.isOnline()) {
            manager.take(me.getUniqueId(), request.sender());
            msg.error(me, "That player is no longer online.");
            return true;
        }
        boolean toTarget = request.type() == TpaManager.Type.TO_TARGET;
        Player mover = toTarget ? from : me;
        Player anchor = toTarget ? me : from;
        // Refuse (and keep the request) when the mover cannot teleport right now: nothing is consumed.
        String block = moverBlock(mover, anchor);
        if (block != null) {
            msg.error(me, mover.equals(me) ? Character.toUpperCase(block.charAt(0)) + block.substring(1)
                : from.getName() + " cannot teleport right now: " + block);
            return true;
        }
        if (manager.take(me.getUniqueId(), request.sender()).isEmpty()) {
            msg.error(me, "That request has expired.");
            return true;
        }
        msg.ok(me, "Teleport request from " + from.getName() + " accepted.");
        if (!mover.equals(from)) {
            msg.ok(from, me.getName() + " accepted your request.");
        } else {
            msg.ok(from, me.getName() + " accepted your request. Hold still.");
        }
        UUID anchorId = anchor.getUniqueId();
        boolean started = teleports.start(mover, anchor.getName(), p -> follow(p, anchorId), () -> {
            if (!mover.hasPermission(Perms.BYPASS_COOLDOWN)) {
                cooldowns.set(mover.getUniqueId(), COOLDOWN_KEY, config.get().tpaTeleportCooldownSeconds() * 1000L);
            }
            msg.ok(mover, "Teleported to " + anchor.getName() + ".");
            Player a = Bukkit.getPlayer(anchorId);
            if (a != null && !a.equals(mover)) {
                msg.info(a, mover.getName() + " arrived.");
            }
        });
        if (!started) {
            msg.warn(me, "The teleport could not start. Ask for a new request when " + mover.getName() + " is ready.");
        }
        return true;
    }

    /** Null when the mover can go; otherwise why not. */
    private String moverBlock(Player mover, Player anchor) {
        if (teleports.isTeleporting(mover.getUniqueId())) {
            return "already teleporting.";
        }
        long wait = mover.hasPermission(Perms.BYPASS_COOLDOWN) ? 0
            : cooldowns.remaining(mover.getUniqueId(), COOLDOWN_KEY);
        if (wait > 0) {
            return "wait " + Msg.duration(wait) + " between request teleports.";
        }
        if (teleports.inDungeon(anchor)) {
            return "the destination is inside a dungeon.";
        }
        return teleports.denial(mover, anchor.getLocation());
    }

    /** Re-evaluated when the warm-up ends: the other player may have left or entered a dungeon. */
    private CompletableFuture<TeleportService.Outcome> follow(Player mover, UUID anchorId) {
        Player anchor = Bukkit.getPlayer(anchorId);
        if (anchor == null || !anchor.isOnline()) {
            return CompletableFuture.completedFuture(new TeleportService.Outcome.Fail("That player went offline."));
        }
        if (teleports.inDungeon(anchor)) {
            return CompletableFuture.completedFuture(new TeleportService.Outcome.Fail(
                "The destination is inside a dungeon."));
        }
        return CompletableFuture.completedFuture(new TeleportService.Outcome.Go(anchor.getLocation()));
    }

    private boolean deny(CommandSender sender, Command command, String label, String[] args) {
        Player me = Cmd.player(msg, sender);
        if (me == null) {
            return true;
        }
        Optional<TpaManager.Request> peek = find(me, args);
        if (peek.isEmpty()) {
            msg.error(me, args.length == 0 ? "You have no pending teleport requests."
                : "You have no pending request from '" + args[0] + "'.");
            return true;
        }
        manager.take(me.getUniqueId(), peek.get().sender());
        Player from = Bukkit.getPlayer(peek.get().sender());
        msg.ok(me, "Teleport request denied.");
        if (from != null) {
            msg.info(from, me.getName() + " denied your teleport request.");
        }
        return true;
    }

    private boolean cancel(CommandSender sender, Command command, String label, String[] args) {
        Player me = Cmd.player(msg, sender);
        if (me == null) {
            return true;
        }
        Optional<TpaManager.Request> request = manager.cancel(me.getUniqueId());
        if (request.isEmpty()) {
            if (!teleports.cancel(me.getUniqueId(), "Teleport cancelled.")) {
                msg.error(me, "You have no pending request to cancel.");
            }
            return true;
        }
        Player to = Bukkit.getPlayer(request.get().target());
        msg.ok(me, "Teleport request cancelled.");
        if (to != null) {
            msg.info(to, me.getName() + " cancelled their teleport request.");
        }
        return true;
    }

    private boolean toggle(CommandSender sender, Command command, String label, String[] args) {
        Player me = Cmd.player(msg, sender);
        if (me == null) {
            return true;
        }
        UtilityData data = data(me.getUniqueId());
        data.tpaDisabled = !data.tpaDisabled;
        core.profiles().markDirty(me.getUniqueId());
        if (data.tpaDisabled) {
            for (TpaManager.Request r : manager.pendingFor(me.getUniqueId())) {
                manager.take(me.getUniqueId(), r.sender());
            }
            msg.ok(me, "You no longer receive teleport requests. Use /tptoggle to receive them again.");
        } else {
            msg.ok(me, "You receive teleport requests again.");
        }
        return true;
    }

    // ---------------------------------------------------------------- housekeeping

    private void expire() {
        for (TpaManager.Request r : manager.purgeExpired()) {
            Player from = Bukkit.getPlayer(r.sender());
            Player to = Bukkit.getPlayer(r.target());
            if (from != null) {
                msg.info(from, "Your teleport request to " + (to == null ? "that player" : to.getName())
                    + " expired.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        for (TpaManager.Request r : manager.forget(leaving.getUniqueId())) {
            UUID otherId = r.sender().equals(leaving.getUniqueId()) ? r.target() : r.sender();
            Player other = Bukkit.getPlayer(otherId);
            if (other != null) {
                msg.info(other, leaving.getName() + " left, the teleport request was cancelled.");
            }
        }
    }
}
