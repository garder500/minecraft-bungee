package win.vampyuyu.survival.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Forgets per-session economy state (the buyback list) when a player leaves. */
final class SessionListener implements Listener {
    private final ShopService shops;

    SessionListener(ShopService shops) {
        this.shops = shops;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shops.forget(event.getPlayer().getUniqueId());
    }
}
