package win.vampyuyu.survival.api;

import java.util.Optional;
import org.bukkit.entity.Player;

/** Bastions: instanced fights against a Guardian, opened with a key. Implemented by the {@code bastion} feature. */
public interface BastionApi {
    /** True when the custom item is the key of a Bastion (it cannot be used as a free summon in the open world). */
    boolean isKey(String itemId);

    /** The Bastion a key opens. */
    Optional<String> bastionOfKey(String itemId);

    /**
     * Opens a Bastion for the player's party (or alone) if they hold the key.
     *
     * @return the English refusal, or null when the Bastion is opening
     */
    String enter(Player leader, String bastionId);

    /** True when the block is the seal of a Realm (its right-click opens a Bastion, whatever the player holds). */
    default boolean isSeal(org.bukkit.block.Block block) {
        return false;
    }

    /**
     * True while the player is a member of a Bastion whose arena is built and still open: a death there respawns them in the
     * arena (they have lives), so the mode travel must not send them back to their island.
     */
    default boolean inArena(java.util.UUID player) {
        return false;
    }
}
