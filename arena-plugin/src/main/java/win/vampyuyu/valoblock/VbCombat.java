package win.vampyuyu.valoblock;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import win.vampyuyu.arena.NexusArenaEngine;

/** What an ability does to health: always through the match (shield first, kills credited), then mirrored to the health bar. Server thread. */
final class VbCombat {
  private final NexusArenaEngine plugin;

  VbCombat(NexusArenaEngine plugin) {
    this.plugin = plugin;
  }

  /** Damage from an ability. Returns true when the victim died (and is put in spectator). */
  boolean damage(VbMatch m, Player victim, UUID attacker, int amount) {
    boolean died = m.damage(victim.getUniqueId(), attacker, amount);
    mirror(victim, m);
    if (died) victim.setGameMode(GameMode.SPECTATOR);
    return died;
  }

  /** Health taken without a kill, never below 1. */
  int drain(VbMatch m, Player victim, int amount) {
    int lost = m.drain(victim.getUniqueId(), amount);
    if (lost > 0) mirror(victim, m);
    return lost;
  }

  int heal(VbMatch m, Player p, int amount) {
    int gained = m.heal(p.getUniqueId(), amount);
    if (gained > 0) mirror(p, m);
    return gained;
  }

  void mirror(Player v, VbMatch m) {
    double max = v.getAttribute(Attribute.MAX_HEALTH).getValue();
    int hp = m.health(v.getUniqueId());
    v.setHealth(Math.max(1.0, Math.min(max, max * hp / VbData.rules().maxHealth())));
  }

  /** Online human by id, or null. */
  static Player player(UUID id) {
    return Bukkit.getPlayer(id);
  }
}
