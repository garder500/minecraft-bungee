package win.vampyuyu.valoblock;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

/**
 * Bookkeeping of the ValoBlock resource pack (the second pack, sent over the base pack while a player is in a match):
 * who was sent it, who loaded it, and which client answers mean "this player cannot play". No Bukkit objects but the
 * status enum, so it is unit-tested.
 */
final class PackTracker {
  /** The pack's id: stable per revision, different from the base pack's so both stay on the client. */
  static UUID packId(String sha1) {
    checkSha1(sha1);
    return UUID.nameUUIDFromBytes(("vampyuyu-valoblock:" + sha1).getBytes(StandardCharsets.UTF_8));
  }

  static String packUrl(String baseUrl, String sha1) {
    checkSha1(sha1);
    String base = baseUrl.trim();
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    if (!base.startsWith("http://") && !base.startsWith("https://"))
      throw new IllegalArgumentException("the pack base URL must be http(s)");
    return base + "/pack/" + sha1 + ".zip";
  }

  static void checkSha1(String sha1) {
    if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("invalid SHA-1");
  }

  /** What a status answer means. */
  enum Verdict {
    /** Progress, nothing to do. */
    WAIT,
    /** The pack is loaded: the player can play. */
    READY,
    /** The player declined or the download failed: they leave the match. */
    REFUSED
  }

  /** Whether this client answer is a refusal or a failure (declined, download failed, bad URL, reload failed, dropped). */
  static boolean ejects(Status s) {
    return switch (s) {
      case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> true;
      default -> false;
    };
  }

  private final Set<UUID> sent = ConcurrentHashMap.newKeySet();
  private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

  /** The pack is about to be sent to the player; false when they already hold it (no second offer). */
  boolean offer(UUID player) {
    return sent.add(player);
  }

  /** Records a client answer for a player the pack was sent to; answers from anyone else are ignored. */
  Verdict status(UUID player, Status s) {
    if (!sent.contains(player)) return Verdict.WAIT;
    if (s == Status.SUCCESSFULLY_LOADED) {
      loaded.add(player);
      return Verdict.READY;
    }
    if (ejects(s)) {
      loaded.remove(player);
      return Verdict.REFUSED;
    }
    return Verdict.WAIT;
  }

  /** The pack was sent to this player (and not taken back). */
  boolean holds(UUID player) {
    return sent.contains(player);
  }

  boolean loaded(UUID player) {
    return loaded.contains(player);
  }

  /** The player left the match, quit or changed server: forget them. True when the pack had been sent (so it must be removed). */
  boolean release(UUID player) {
    loaded.remove(player);
    return sent.remove(player);
  }
}
