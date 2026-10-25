package win.vampyuyu.network;

import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A private channel between one player and the staff using the panel. While it is open, what the player
 * types goes only to the panel (nobody else sees it) and what staff write appears only for that player.
 * The player is told when it opens and leaves with /exitchat. It also ends when the player disconnects.
 * Staff text is shown as plain text: no colour codes or formatting are ever parsed from it. The channel
 * outranks arena and game chat on purpose (staff stepping in is deliberate), but not a menu that is
 * waiting for typed input: those handlers run first and cancel the message.
 */
final class StaffChannel implements Listener {
    private static final int MAX_TEXT = 256;
    private static final int KEEP = 200;
    private static final int MAX_CHANNELS = 50;

    record Message(long id, String at, String from, String text) {}

    private static final class Channel {
        final String player;
        final String staff;
        final Deque<Message> inbox = new LinkedList<>();
        long last;
        boolean open = true;

        Channel(String player, String staff) {
            this.player = player;
            this.staff = staff;
        }

        synchronized Message add(String from, String text) {
            Message m = new Message(++last, Instant.now().toString(), from, text);
            inbox.addLast(m);
            while (inbox.size() > KEEP) inbox.removeFirst();
            return m;
        }

        synchronized List<Message> since(long id) {
            List<Message> out = new ArrayList<>();
            for (Message m : inbox) if (m.id() > id) out.add(m);
            return out;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    StaffChannel(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin == null) return; // unit tests build the channel without a server
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand exit = plugin.getCommand("exitchat");
        if (exit != null) exit.setExecutor(this::exitCommand);
    }

    private static String clean(String text, int max) {
        String t = text.replaceAll("[\\p{Cntrl}&&[^ ]]", " ").strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String staffName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_. -]{1,32}")) throw new IllegalArgumentException("Invalid staff name");
        return name;
    }

    /** One call for every operation the panel needs: open, send, close or read (with the last id it saw). */
    Map<String, Object> handle(JsonObject in, Player player, UUID id) {
        try {
            String action = in.get("action").getAsString();
            if (player == null && (action.equals("open") || action.equals("send"))) throw new IllegalArgumentException("Player must be online on this server");
            switch (action) {
                case "open" -> open(player, staffName(in.get("staff").getAsString()));
                case "send" -> send(player, staffName(in.get("staff").getAsString()), clean(in.get("message").getAsString(), MAX_TEXT));
                case "close" -> close(id, "Staff closed the channel.", true);
                case "read" -> { }
                default -> throw new IllegalArgumentException("Unsupported chat action");
            }
            long since = in.has("since") ? in.get("since").getAsLong() : 0;
            return snapshot(id, player, since);
        } catch (NullPointerException | ClassCastException | UnsupportedOperationException | NumberFormatException e) {
            throw new IllegalArgumentException("Invalid chat request");
        }
    }

    private Map<String, Object> snapshot(UUID id, Player player, long since) {
        Channel c = channels.get(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("open", c != null && c.open);
        out.put("exists", c != null);
        out.put("online", player != null);
        out.put("messages", c == null ? List.of() : c.since(since));
        out.put("last", c == null ? 0 : c.last);
        return out;
    }

    private void open(Player player, String staff) {
        Channel existing = channels.get(player.getUniqueId());
        if (existing != null && existing.open) return;
        if (channels.size() >= MAX_CHANNELS) channels.values().removeIf(c -> !c.open);
        if (channels.size() >= MAX_CHANNELS) throw new IllegalStateException("Too many open channels");
        Channel c = new Channel(player.getName(), staff);
        channels.put(player.getUniqueId(), c);
        c.add("system", "Channel opened by " + staff + ".");
        player.sendMessage(Component.text("[Staff] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(staff + " opened a private channel with you. What you type now goes only to staff. Type /exitchat to go back to the public chat.", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
    }

    private void send(Player player, String staff, String text) {
        Channel c = channels.get(player.getUniqueId());
        if (c == null || !c.open) throw new IllegalStateException("The channel is closed");
        if (text.isBlank()) throw new IllegalArgumentException("Message is empty");
        c.add("staff", text);
        player.sendMessage(Component.text("[Staff · " + staff + "] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(text, NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.2f);
    }

    private void close(UUID id, String reason, boolean tellPlayer) {
        Channel c = channels.get(id);
        if (c == null || !c.open) return;
        c.open = false;
        c.add("system", reason);
        Player player = tellPlayer ? plugin.getServer().getPlayer(id) : null;
        if (player != null) player.sendMessage(Component.text("[Staff] The private channel is closed. Your messages are public again.", NamedTextColor.LIGHT_PURPLE));
    }

    private boolean exitCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Channel c = channels.get(player.getUniqueId());
        if (c == null || !c.open) {
            player.sendMessage(Component.text("You are not in a private channel.", NamedTextColor.GRAY));
            return true;
        }
        close(player.getUniqueId(), player.getName() + " left the channel.", true);
        return true;
    }

    // After the menus that wait for typed input (LOWEST) have taken their message, before arena chat (HIGHEST).
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        Channel c = channels.get(event.getPlayer().getUniqueId());
        if (c == null || !c.open) return;
        event.setCancelled(true);
        String text = clean(PlainTextComponentSerializer.plainText().serialize(event.message()), MAX_TEXT);
        if (text.isBlank()) return;
        c.add("player", text);
        event.getPlayer().sendMessage(Component.text("[You → Staff] ", NamedTextColor.GRAY).append(Component.text(text, NamedTextColor.WHITE)));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        close(event.getPlayer().getUniqueId(), event.getPlayer().getName() + " left the server.", false);
    }
}
