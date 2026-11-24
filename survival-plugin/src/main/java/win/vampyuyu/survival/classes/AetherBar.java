package win.vampyuyu.survival.classes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Shows the Aether reserve on the experience bar of Skyblock players: the number is the current Aether, the bar how full
 * the reserve is. Only the client display changes ({@link Player#sendExperienceChange}): the real levels stay on the
 * server, and they are shown again while a menu is open (the anvil spends them) and outside the Skyblock worlds.
 */
final class AetherBar {
    private final ClassService service;
    private final Set<UUID> shown = new HashSet<>();

    AetherBar(ClassService service) {
        this.service = service;
    }

    /** Level and progress shown for {@code current} Aether out of {@code max}. */
    static int level(double current) {
        return (int) Math.max(0, Math.floor(current + 1e-6));
    }

    static float progress(double current, double max) {
        if (!(max > 0)) {
            return 0f;
        }
        return (float) Math.max(0, Math.min(0.999, current / max));
    }

    static boolean eligible(String world, GameMode mode, InventoryType open) {
        return SkyblockWorlds.isSkyblock(world) && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
            && (open == InventoryType.CRAFTING || open == InventoryType.CREATIVE);
    }

    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (eligible(p.getWorld().getName(), p.getGameMode(), p.getOpenInventory().getType())) {
                p.sendExperienceChange(progress(service.aether(p), service.maxAether(p)), level(service.aether(p)));
                shown.add(p.getUniqueId());
            } else if (shown.remove(p.getUniqueId())) {
                restore(p);
            }
        }
        shown.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    void restore(Player p) {
        p.sendExperienceChange(p.getExp(), p.getLevel());
    }

    void restoreAll() {
        for (UUID id : shown) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p);
            }
        }
        shown.clear();
    }
}
