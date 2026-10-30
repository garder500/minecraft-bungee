package win.vampyuyu.survival.island;

import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Where a portal leads. Only sb_world to sb_nether (Nether portal) and sb_world to sb_end (End portal) exist, in the
 * SAME grid cell, and back; everything else is refused. Pure.
 */
public final class PortalMapper {
    /** A destination: the world, and either a fixed offset from the island centre or the island's overworld home. */
    public record Route(String world, int[] offset, boolean home) {}

    private PortalMapper() {}

    /** The route for a player portal, or null to cancel the teleport. */
    public static Route route(String fromWorld, TeleportCause cause, boolean netherOn, boolean endOn) {
        if (fromWorld == null) {
            return null;
        }
        if (cause == TeleportCause.NETHER_PORTAL && netherOn) {
            if (SkyblockWorlds.WORLD.equals(fromWorld)) {
                return new Route(SkyblockWorlds.NETHER, IslandTemplates.NETHER_PORTAL, false);
            }
            if (SkyblockWorlds.NETHER.equals(fromWorld)) {
                return new Route(SkyblockWorlds.WORLD, null, true);
            }
        }
        if (cause == TeleportCause.END_PORTAL && endOn) {
            if (SkyblockWorlds.WORLD.equals(fromWorld)) {
                return new Route(SkyblockWorlds.END, IslandTemplates.END_SPAWN, false);
            }
            if (SkyblockWorlds.END.equals(fromWorld)) {
                return new Route(SkyblockWorlds.WORLD, null, true);
            }
        }
        return null;
    }
}
