package win.vampyuyu.network;

import org.bukkit.entity.Player;

/**
 * Same-server hook for the Skyblock card of {@code /menu}, for a player who is ALREADY on the survival
 * server (where a BungeeCord {@code Connect survival} is a no-op and fires no join event).
 *
 * <p>Optional: the survival plugin MAY register an implementation in the Bukkit {@code ServicesManager}.
 * Because the two plugins share no build dependency, the registering side has to load this interface
 * through VampyuyuNetwork's class loader and wrap it in a {@link java.lang.reflect.Proxy}. The zero-code
 * alternative is the incoming plugin channel {@link SkyblockHandoff#CHANNEL}, which needs no reflection (see
 * {@code docs/skyblock-notes/netmenu.md}, "Same-server handoff"). The gateway only serves a plain Skyblock request
 * (it has no landing parameter); "my island" and "hub" requests use the channel.
 */
public interface SkyblockGateway {
    /**
     * Sends the player to Skyblock (hub, island or wherever the survival plugin's join router decides).
     * Called on the main server thread.
     *
     * @return true when the request was taken; false lets the network plugin try the next mechanism
     */
    boolean sendToSkyblock(Player player);
}
