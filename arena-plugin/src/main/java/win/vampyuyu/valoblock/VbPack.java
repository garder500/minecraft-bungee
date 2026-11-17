package win.vampyuyu.valoblock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import win.vampyuyu.arena.ArenaSession;
import win.vampyuyu.arena.NexusArenaEngine;

/**
 * The ValoBlock resource pack (guns, abilities, agent faces, scope), sent as a second pack over the base pack when a
 * player joins a match and removed when they leave it, the match ends, they quit or change server. It is served by the
 * panel next to the base pack ({@code /pack/<sha1>.zip}). A player who declines it or fails the download is taken out
 * of the match and sent back with a message. Not configured (no SHA-1 file, no base URL): matches play without it.
 */
public final class VbPack implements Listener {
  private final NexusArenaEngine plugin;
  private final UUID id;
  private final String url;
  private final byte[] hash;
  private final boolean required;
  private final PackTracker tracker = new PackTracker();

  private VbPack(NexusArenaEngine plugin, String baseUrl, String sha1, boolean required) {
    this.plugin = plugin;
    this.id = PackTracker.packId(sha1);
    this.url = PackTracker.packUrl(baseUrl, sha1);
    this.hash = HexFormat.of().parseHex(sha1);
    this.required = required;
  }

  /**
   * VALOBLOCK_PACK_SHA1_FILE (default /opt/vampyuyu-valoblock.sha1, written by the image build), served from
   * VALOBLOCK_PACK_BASE_URL or else MENU_PACK_BASE_URL (the panel serves both). VALOBLOCK_PACK_REQUIRED=false makes
   * it tolerated (default true: a client that declines or fails the pack is taken out of the match with a message).
   */
  public static VbPack load(NexusArenaEngine plugin) {
    String base = System.getenv("VALOBLOCK_PACK_BASE_URL");
    if (base == null || base.isBlank()) base = System.getenv("MENU_PACK_BASE_URL");
    Path file = Path.of(System.getenv().getOrDefault("VALOBLOCK_PACK_SHA1_FILE", "/opt/vampyuyu-valoblock.sha1"));
    if (base == null || base.isBlank() || !Files.isRegularFile(file)) {
      plugin.getLogger().info("ValoBlock resource pack not configured; matches play without it");
      return null;
    }
    try {
      String sha1 = Files.readString(file, StandardCharsets.UTF_8).trim();
      boolean required = !"false".equalsIgnoreCase(System.getenv().getOrDefault("VALOBLOCK_PACK_REQUIRED", "true").trim());
      VbPack pack = new VbPack(plugin, base, sha1, required);
      plugin.getServer().getPluginManager().registerEvents(pack, plugin);
      plugin.getLogger().info("ValoBlock resource pack " + (required ? "required" : "offered") + " from " + pack.url);
      return pack;
    } catch (IOException | IllegalArgumentException e) {
      plugin.getLogger().warning("ValoBlock resource pack disabled: " + e.getMessage());
      return null;
    }
  }

  /**
   * Sends the pack a second after the player is in the arena: the base pack's own re-offer after a world change
   * (network plugin) uses setResourcePack, which would wipe a pack added before it.
   */
  public void sendSoon(Player p) {
    UUID u = p.getUniqueId();
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      Player now = Bukkit.getPlayer(u);
      if (now != null && now.isOnline() && plugin.arenas.session(u) != null) send(now);
    }, SEND_DELAY_TICKS);
  }

  static final long SEND_DELAY_TICKS = 20;

  /** Sends the pack on top of the base pack (addResourcePack keeps the packs the client already holds). */
  public void send(Player p) {
    if (!tracker.offer(p.getUniqueId())) return;
    p.addResourcePack(
        id,
        url,
        hash,
        required ? "ValoBlock guns and agents (required to play)" : "ValoBlock guns and agents",
        false); // never the wire "required" flag: a vanilla client then disconnects itself on a decline and the match
                // handler (message, leave the arena) would never run. The match ejects a refusal when `required`.
  }

  /** Takes the pack back (leaving the match, the match ending). Safe to call twice. */
  public void remove(Player p) {
    if (tracker.release(p.getUniqueId())) p.removeResourcePack(id);
  }

  public void removeAll(Collection<UUID> players) {
    for (UUID u : players) {
      Player p = Bukkit.getPlayer(u);
      if (p != null) remove(p);
      else tracker.release(u);
    }
  }

  /** The player holds the ValoBlock pack and the client confirmed it loaded (menus only open after this). */
  public boolean loaded(UUID player) {
    return tracker.loaded(player);
  }

  public boolean holds(UUID player) {
    return tracker.holds(player);
  }

  @EventHandler
  public void status(PlayerResourcePackStatusEvent e) {
    if (!id.equals(e.getID())) return;
    Player p = e.getPlayer();
    if (tracker.status(p.getUniqueId(), e.getStatus()) != PackTracker.Verdict.REFUSED) return;
    refused(p, e.getStatus());
  }

  /** The player cannot play without the pack: out of the match (and back to where they came from), with the reason. */
  private void refused(Player p, PlayerResourcePackStatusEvent.Status why) {
    UUID u = p.getUniqueId();
    tracker.release(u);
    ArenaSession s = plugin.arenas.session(u);
    if (s == null) return;
    boolean declined = why == PlayerResourcePackStatusEvent.Status.DECLINED;
    p.sendMessage(
        Component.text(
            declined
                ? "ValoBlock needs its resource pack (guns, agents, scope). You declined it, so you were taken out of the match."
                : "The ValoBlock resource pack did not download (" + why.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    + "), so you were taken out of the match. Join again to retry.",
            NamedTextColor.RED));
    plugin.arenas.leaveArena(p).whenComplete((v, err) -> {
      if (err != null) plugin.getLogger().warning("ValoBlock pack refused, leaving failed: " + NexusArenaEngine.reason(err));
    });
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    tracker.release(e.getPlayer().getUniqueId()); // the connection drops every pack with it; nothing to send
  }
}
