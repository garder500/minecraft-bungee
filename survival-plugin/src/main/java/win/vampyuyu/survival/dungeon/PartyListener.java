package win.vampyuyu.survival.dungeon;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sticky party chat and name refresh on login. */
public final class PartyListener implements Listener {
    private final DungeonService svc;

    public PartyListener(DungeonService svc) {
        this.svc = svc;
    }

    /** Priority LOW: before the chat formatters, and menu prompts at LOWEST still win. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!svc.parties.partyChat(p.getUniqueId())) {
            return;
        }
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.setCancelled(true);
        // PartyService is synchronized and only builds messages, the sends are thread safe on Paper
        if (!svc.parties.chat(p.getUniqueId(), text)) {
            svc.parties.togglePartyChat(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        svc.parties.rename(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
