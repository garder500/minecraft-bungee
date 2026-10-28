package win.vampyuyu.survival.core;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

/**
 * Runtime permission registration (no plugin.yml edit). Naming: {@code vampyuyu.sb.<feature>.<action>}.
 * Everything registered here is removed when the plugin disables.
 */
public final class Permissions {
    private final Set<String> mine = new LinkedHashSet<>();

    /**
     * Registers a permission. Registering an already known name is a no-op (returns the existing one), so
     * features can call it from {@code enable()} without checking.
     */
    public synchronized Permission register(String name, String description, PermissionDefault def) {
        Permission existing = Bukkit.getPluginManager().getPermission(name);
        if (existing != null) {
            return existing;
        }
        Permission permission = new Permission(name, description, def);
        Bukkit.getPluginManager().addPermission(permission);
        mine.add(name);
        return permission;
    }

    /** Number of permissions registered through this instance (rollback marks). */
    public synchronized int size() {
        return mine.size();
    }

    /** Removes the permissions registered after the first {@code index} ones. */
    public synchronized void unregisterFrom(int index) {
        java.util.Iterator<String> it = mine.iterator();
        for (int i = 0; it.hasNext(); i++) {
            String name = it.next();
            if (i >= index) {
                Bukkit.getPluginManager().removePermission(name);
                it.remove();
            }
        }
    }

    /** Removes every permission registered through this instance. */
    public synchronized void unregisterAll() {
        for (String name : mine) {
            Bukkit.getPluginManager().removePermission(name);
        }
        mine.clear();
    }
}
