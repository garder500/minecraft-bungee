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
}
