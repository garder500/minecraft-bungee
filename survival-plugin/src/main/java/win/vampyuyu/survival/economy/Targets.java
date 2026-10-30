package win.vampyuyu.survival.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Core;

/** Resolves a typed player name to someone who has a Skyblock profile (online or known offline). */
final class Targets {
    /** A resolved player. {@code online} is null when the player is offline. */
    record Target(UUID id, String name, Player online) {}

    private Targets() {}

    static Optional<Target> resolve(Core core, String name) {
        if (name == null || name.isBlank() || name.length() > 32) {
            return Optional.empty();
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName(), online));
        }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off == null || !core.store().exists("players/" + off.getUniqueId() + ".json")) {
            return Optional.empty();
        }
        return Optional.of(new Target(off.getUniqueId(), off.getName() == null ? name : off.getName(), null));
    }

    static List<String> onlineNames(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(player.getName());
            }
        }
        return out;
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
