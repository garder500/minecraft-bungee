package win.vampyuyu.survival.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import win.vampyuyu.survival.core.Msg;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Small helpers shared by the command classes. */
final class Cmd {
    private Cmd() {}

    /** The sender as a player, or null after telling a console user that this is a player command. */
    static Player player(Msg msg, CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        msg.error(sender, "Only players can use this command.");
        return null;
    }

    /** Options starting with the typed prefix (case insensitive). */
    static List<String> match(Collection<String> options, String typed) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    /** Human readable world name; dungeon runs all read "a dungeon". */
    static String worldName(String world) {
        if (world == null) {
            return "an unknown world";
        }
        if (SkyblockWorlds.isDungeon(world)) {
            return "a dungeon";
        }
        return switch (world) {
            case SkyblockWorlds.HUB -> "the Hub";
            case SkyblockWorlds.WORLD -> "the Skyblock world";
            case SkyblockWorlds.NETHER -> "the Skyblock Nether";
            case SkyblockWorlds.END -> "the Skyblock End";
            default -> world;
        };
    }

    static String worldName(World world) {
        return worldName(world == null ? null : world.getName());
    }

    static String coords(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }
}
