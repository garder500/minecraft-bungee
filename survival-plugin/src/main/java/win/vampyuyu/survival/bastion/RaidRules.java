package win.vampyuyu.survival.bastion;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The rules that only the Raid of the Devourer has (GDD 4.6, 8.7, 12.5): who may enter, how the arena falls in Phase III and
 * where a player who slips off is put back. Pure functions, the {@link BastionService} does the wiring.
 */
final class RaidRules {
    /** Radius of the core of the platform, the part that never falls: the last stand of Phase III. */
    static final int CORE_RADIUS = 9;

    private RaidRules() {}

    /** The required Bastions a player has not cleared yet, in the configured order (empty = may enter). */
    static List<String> missing(BastionConfig cfg, BastionData data) {
        List<String> out = new ArrayList<>();
        for (String id : cfg.raidRequires) {
            if (!data.cleared(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** The visible names of Bastion ids, for the refusal sent to the party. */
    static String names(BastionConfig cfg, List<String> ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            names.add(cfg.get(id).map(BastionConfig.Bastion::name).orElse(id));
        }
        return String.join(", ", names);
    }

    /** True when the arena of a run in {@code phase} must be falling. */
    static boolean collapsing(BastionConfig cfg, String bastionId, int phase) {
        return cfg.isRaid(bastionId) && phase >= cfg.raidCollapsePhase;
    }

    /** Radius that remains after one more ring fell: it stops at the core. */
    static int nextRadius(int current) {
        return Math.max(CORE_RADIUS, current - 1);
    }

    /** Ticks between two rings. */
    static long ringTicks(BastionConfig cfg) {
        return cfg.raidCollapseSeconds * 20L;
    }

    /** Where a player who left the platform is put back: the arrival while it stands, the centre once it fell. */
    static Location safeSpot(World world, int radius, int standing) {
        Location arrival = BastionArena.arrival(world, radius);
        if (standing < 0 || Math.abs(arrival.getZ() - 0.5) <= standing - 2) {
            return arrival;
        }
        return BastionArena.centre(world).add(0, 0, 3);
    }

    /** Coins for a member's first clear: only if the clear is new for them, and never more than the GDD cap. */
    static double firstClearCoins(BastionConfig cfg, boolean alreadyCleared) {
        return alreadyCleared ? 0 : Math.max(0, Math.min(5000, cfg.raidFirstClearCoins));
    }
}
