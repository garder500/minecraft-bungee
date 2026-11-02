package win.vampyuyu.survival.api;

import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The eight Sky Gates of the Quai des Huit (GDD 17.1). A Gate is lit per player: the phases that open a Realm call
 * {@link #light} when the player unlocks it, and register where the Gate leads with {@link #setDestination}.
 * Until a destination is registered a lit Gate leads to a placeholder landing platform, so the travel can be
 * tested before the Realm exists.
 */
public interface SkyGateApi {
    /** True when this player's Gate to the Realm is lit. */
    boolean isLit(UUID player, Realm realm);

    /** The Realms this player has lit, in Realm order. */
    Set<Realm> litRealms(UUID player);

    /** Lights the Gate for this player (saved at once, shown to them if they are at the Quai). False if it already was. */
    boolean light(UUID player, Realm realm);

    /** Puts the Gate out again (admin tests, resets). False if it already was out. */
    boolean extinguish(UUID player, Realm realm);

    /**
     * Where the Gate of a Realm leads. The function runs on the main thread when the player steps through and may
     * return null to refuse for now. Pass null to go back to the placeholder landing.
     */
    void setDestination(Realm realm, Function<Player, Location> destination);

    /** Sends a player through a lit Gate with the travel transition. Same checks as walking into it. */
    boolean travel(Player player, Realm realm);

    /** The arrival point at the Quai (the end of the bridge from the hub), or null while the hub world is missing. */
    Location quaiSpawn();

    /**
     * A refusal rule for the Gates: it gets the player and the Realm and returns the reason a Gate stays shut, or null
     * to let the player through (the storms of the Celestial Breath close some routes). Several guards may be added.
     */
    default void addTravelGuard(java.util.function.BiFunction<Player, Realm, String> guard) {}
}
