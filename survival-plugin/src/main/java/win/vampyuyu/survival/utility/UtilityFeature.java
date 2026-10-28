package win.vampyuyu.survival.utility;

import java.nio.file.Path;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.Location;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.back.BackCommand;
import win.vampyuyu.survival.back.BackService;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.Msg;

/**
 * Quality-of-life layer: /back (unchanged state file, now behind the teleport rules), homes, /spawn, /tpa, /rtp,
 * portable blocks, /hat, /sit, /trash and small information commands. Profile section: {@code utility}.
 */
public final class UtilityFeature implements Feature {
    /** Holder of the current configuration so /utility reload takes effect everywhere. */
    private static final class Settings {
        volatile UtilityConfig current;
    }

    private TeleportService teleports;
    private SitManager sit;
    private TrashCommand trash;

    @Override
    public String id() {
        return "utility";
    }

    @Override
    public void enable(Core core) {
        Settings settings = new Settings();
        YamlConfiguration yaml = core.featureConfig("utility");
        settings.current = UtilityConfig.from(yaml);

        CombatTag combat = new CombatTag(System::currentTimeMillis);
        teleports = new TeleportService(core, () -> settings.current, combat);
        core.listen(teleports);

        // /back keeps its plugin.yml entry and its state file; only the teleport now goes through the gate.
        Path file = core.getDataFolder().toPath().resolve("back.json");
        BackService back = new BackService(file, System::currentTimeMillis, BackService.COOLDOWN_MS);
        back.load();
        BackCommand backCommand = new BackCommand(back, teleports);
        core.listen(backCommand);
        PluginCommand command = core.getPlugin().getCommand("back");
        if (command != null) {
            command.setExecutor(backCommand);
        }

        UtilityConfig cfg = settings.current;
        TpaManager manager = new TpaManager(System::currentTimeMillis, cfg.tpaExpireSeconds() * 1000L,
            cfg.tpaSendCooldownSeconds() * 1000L, cfg.tpaMaxPending());
        Cooldowns cooldowns = new Cooldowns();

        core.permissions().register(Perms.BYPASS_WARMUP, "Skip the teleport warm-up", PermissionDefault.OP);
        core.permissions().register(Perms.BYPASS_COOLDOWN, "Skip utility cooldowns (/rtp, /tpa)", PermissionDefault.OP);
        core.permissions().register(Perms.BYPASS_COMBAT, "Teleport while in combat", PermissionDefault.OP);

        new HomeCommands(core, teleports, () -> settings.current).register();
        new SpawnCommands(core, teleports, () -> settings.current).register();
        new TpaCommands(core, teleports, () -> settings.current, manager, cooldowns).register();
        new RtpCommand(core, teleports, () -> settings.current, cooldowns).register();
        new PortableCommands(core, teleports, yaml).register();
        new MiscCommands(core, () -> settings.current).register();
        sit = new SitManager(core, () -> settings.current, teleports);
        sit.register();
        trash = new TrashCommand(core, () -> settings.current);
        trash.register();
        core.listen(new DeathNotice(core.msg()));
        new UtilityHelp(core, () -> {
            UtilityConfig reloaded = UtilityConfig.from(core.featureConfig("utility"));
            settings.current = reloaded;
            manager.configure(reloaded.tpaExpireSeconds() * 1000L, reloaded.tpaSendCooldownSeconds() * 1000L,
                reloaded.tpaMaxPending());
        }).register();

        // Published last: other features may ask the teleport rules (for example to refuse a teleport in combat).
        core.register(TeleportService.class, teleports);
    }

    @Override
    public void disable() {
        if (trash != null) {
            trash.shutdown();
        }
        if (sit != null) {
            sit.shutdown();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
    }

    /** Tells the victim, and nobody else, where they died. */
    static final class DeathNotice implements Listener {
        private final Msg msg;

        DeathNotice(Msg msg) {
            this.msg = msg;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(PlayerDeathEvent event) {
            Player player = event.getPlayer();
            Location at = player.getLocation();
            if (at.getWorld() == null) {
                return;
            }
            String where = Cmd.worldName(at.getWorld());
            String hint = player.hasPermission("vampyuyu.back") && !win.vampyuyu.survival.core.SkyblockWorlds
                .isDungeon(at.getWorld().getName()) ? " Use /back to return." : "";
            msg.info(player, "You died at " + Cmd.coords(at) + " in " + where + "." + hint);
        }
    }
}
