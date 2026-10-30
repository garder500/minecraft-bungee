package win.vampyuyu.survival.economy;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import win.vampyuyu.survival.core.Core;

/**
 * "Click, then type" input for the economy menus: the next chat line of the player is taken as the answer instead of
 * being broadcast. One pending prompt per player, 30 seconds to answer, the word {@code cancel} (or a new prompt, a
 * timeout, a quit, a plugin disable) drops it. The chat event is async, so the answer is handed to the callback on the
 * main thread. {@code cancelled} only runs when the player types {@code cancel}: a timeout or a replacement never opens a
 * menu on a player who has moved on (they get a chat message at most).
 */
final class Prompts implements Listener {
    static final long TIMEOUT_TICKS = 600;

    private record Pending(long token, Consumer<String> answer, Runnable cancelled) {}

    private final Core core;
    /** Written on the main thread, peeked at from the async chat thread. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private long nextToken = 1;

    Prompts(Core core) {
        this.core = core;
    }

    void register() {
        core.listen(this);
    }

    /**
     * Asks the player to type something. {@code answer} runs on the main thread with the trimmed text; {@code cancelled}
     * (may be null) runs only when the player types {@code cancel} (typically to reopen the menu).
     */
    void ask(Player player, String question, Consumer<String> answer, Runnable cancelled) {
        UUID id = player.getUniqueId();
        pending.remove(id); // an older prompt is simply dropped: no callback, its menu stays closed
        long token = nextToken++;
        pending.put(id, new Pending(token, answer, cancelled));
        core.msg().info(player, question + " Type it in chat, or 'cancel' (" + TIMEOUT_TICKS / 20 + "s).");
        Sfx.click(player);
        core.scheduler().later(TIMEOUT_TICKS, () -> {
            Pending p = pending.get(id);
            if (p != null && p.token() == token) {
                pending.remove(id);
                Player online = org.bukkit.Bukkit.getPlayer(id);
                if (online != null) {
                    core.msg().warn(online, "Input timed out.");
                }
            }
        });
    }

    boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    /** Main thread: feeds a chat line to the player's prompt. Returns true when it was consumed. */
    boolean handle(UUID id, String text) {
        Pending p = pending.remove(id);
        if (p == null) {
            return false;
        }
        String line = text == null ? "" : text.trim();
        if (line.equalsIgnoreCase("cancel") || line.isEmpty()) {
            if (p.cancelled() != null) {
                p.cancelled().run();
            }
            return true;
        }
        p.answer().accept(line);
        return true;
    }

    /** Drops every pending prompt (plugin disable). */
    void clear() {
        pending.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!pending.containsKey(id)) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        core.scheduler().sync(() -> handle(id, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
