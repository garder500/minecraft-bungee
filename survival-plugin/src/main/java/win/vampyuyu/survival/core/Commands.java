package win.vampyuyu.survival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

/**
 * Registers Bukkit commands at runtime through the server command map, so features never edit plugin.yml.
 * <pre>{@code
 * core.commands().command("island")
 *     .aliases("is")
 *     .description("Manage your island")
 *     .usage("/island [home|invite]")
 *     .permission("vampyuyu.sb.island.use", PermissionDefault.TRUE)
 *     .executor(executor)
 *     .tab(completer)      // optional
 *     .register();
 * }</pre>
 * Every command registered here is removed by {@link #unregisterAll()} when the plugin disables.
 */
public final class Commands {
    /** Fluent definition of a command. */
    public final class Spec {
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String description = "";
        private String usage = "/<command>";
        private String permission;
        private PermissionDefault permissionDefault = PermissionDefault.OP;
        private CommandExecutor executor;
        private TabCompleter tab;

        private Spec(String name) {
            this.name = name;
        }

        public Spec aliases(String... aliases) {
            this.aliases.addAll(List.of(aliases));
            return this;
        }

        public Spec description(String description) {
            this.description = description;
            return this;
        }

        public Spec usage(String usage) {
            this.usage = usage;
            return this;
        }

        /** Permission required to run it; registered at runtime with the given default. */
        public Spec permission(String permission, PermissionDefault def) {
            this.permission = permission;
            this.permissionDefault = def;
            return this;
        }

        public Spec executor(CommandExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Spec tab(TabCompleter tab) {
            this.tab = tab;
            return this;
        }

        /** Registers the command. Returns the command (the label may be prefixed if another plugin owns the name). */
        public Command register() {
            if (executor == null) {
                throw new IllegalStateException("Command " + name + " has no executor");
            }
            return Commands.this.add(this);
        }
    }

    private final class Registered extends Command {
        private final CommandExecutor executor;
        private final TabCompleter tab;

        Registered(Spec spec) {
            super(spec.name, spec.description, spec.usage, spec.aliases);
            this.executor = spec.executor;
            this.tab = spec.tab;
            setPermission(spec.permission);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!testPermission(sender)) {
                return true;
            }
            boolean ok = executor.onCommand(sender, this, label, args);
            if (!ok) {
                sender.sendMessage(getUsage().replace("<command>", label));
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (tab == null || !testPermissionSilent(sender)) {
                return List.of();
            }
            List<String> result = tab.onTabComplete(sender, this, alias, args);
            return result == null ? List.of() : result;
        }
    }

    private final Plugin plugin;
    private final Permissions permissions;
    private final Logger log;
    private final List<Registered> registered = new ArrayList<>();

    public Commands(Plugin plugin, Permissions permissions) {
        this.plugin = plugin;
        this.permissions = permissions;
        this.log = plugin.getLogger();
    }

    /** Starts the definition of a command. */
    public Spec command(String name) {
        return new Spec(name);
    }

    private synchronized Command add(Spec spec) {
        if (spec.permission != null) {
            permissions.register(spec.permission, "Use /" + spec.name, spec.permissionDefault);
        }
        Registered command = new Registered(spec);
        CommandMap map = Bukkit.getCommandMap();
        String fallback = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        if (!map.register(fallback, command)) {
            log.warning("Command /" + spec.name + " is already taken by another plugin; use /"
                + command.getLabel() + " instead");
        }
        registered.add(command);
        refreshClients();
        return command;
    }

    /** Removes every command registered through this instance (names, prefixed names and aliases). */
    public synchronized void unregisterAll() {
        unregisterFrom(0);
    }

    /** Removes the commands registered after the first {@code index} ones (rollback of a failed feature). */
    public synchronized void unregisterFrom(int index) {
        CommandMap map = Bukkit.getCommandMap();
        boolean any = registered.size() > index;
        while (registered.size() > index) {
            Registered command = registered.remove(registered.size() - 1);
            command.unregister(map);
            // Paper's known-commands map is a forwarding view: remove by key, values().removeIf is not supported.
            Map<String, Command> known = map.getKnownCommands();
            for (String key : new ArrayList<>(known.keySet())) {
                if (known.get(key) == command) {
                    known.remove(key);
                }
            }
        }
        if (any) {
            refreshClients();
        }
    }

    /** Number of commands currently registered (diagnostics, tests). */
    public synchronized int count() {
        return registered.size();
    }

    private void refreshClients() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }
}
