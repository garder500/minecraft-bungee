package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Per-viewer glowing, the technique of the Werewolf vote outline: an invisible glowing mannequin stands
 * where the revealed player is (it follows every tick), hidden by default and shown only to the viewers
 * (the revealing team), so only they see the outline through walls.
 */
final class Reveals {
  private static final class R {
    Mannequin body;
    UUID target;
    final Set<UUID> viewers = new HashSet<>();
    int until;
  }

  private final Plugin plugin;
  private final Map<String, R> active = new HashMap<>();
  private int now;

  Reveals(Plugin plugin) {
    this.plugin = plugin;
  }

  /** Reveals {@code target} to the viewers for {@code ticks} (extends a reveal already running for that squad). */
  void reveal(Player target, int squad, Collection<UUID> viewers, int ticks) {
    String key = target.getUniqueId() + "/" + squad;
    R r = active.get(key);
    if (r == null) {
      r = new R();
      r.target = target.getUniqueId();
      r.body = outline(target.getLocation());
      active.put(key, r);
    }
    r.until = Math.max(r.until, now + ticks);
    for (UUID v : viewers) {
      if (!r.viewers.add(v)) continue;
      Player p = Bukkit.getPlayer(v);
      if (p != null && !v.equals(target.getUniqueId())) p.showEntity(plugin, r.body);
    }
  }

  /** Follows the targets and drops what ran out. */
  void tick() {
    now++;
    for (var e : new ArrayList<>(active.entrySet())) {
      R r = e.getValue();
      Player t = Bukkit.getPlayer(r.target);
      if (t == null || r.until <= now || !r.body.isValid() || t.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
        r.body.remove();
        active.remove(e.getKey());
        continue;
      }
      Location l = t.getLocation();
      r.body.teleport(l);
    }
  }

  int count() {
    return active.size();
  }

  void clear() {
    for (R r : active.values()) r.body.remove();
    active.clear();
  }

  private static Mannequin outline(Location at) {
    return at.getWorld().spawn(at, Mannequin.class, m -> {
      m.setVisibleByDefault(false);
      m.setPersistent(false);
      m.setInvulnerable(true);
      m.setGravity(false);
      m.setAI(false);
      m.setSilent(true);
      m.setImmovable(true);
      m.setCollidable(false);
      m.setDescription(Component.empty());
      m.setInvisible(true);
      m.setGlowing(true);
    });
  }

  /** Whether this entity is one of ours (for tests and cleanup). */
  boolean owns(Entity e) {
    for (R r : active.values()) if (r.body == e) return true;
    return false;
  }
}
