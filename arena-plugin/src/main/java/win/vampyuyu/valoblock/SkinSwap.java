package win.vampyuyu.valoblock;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Agent skins through {@code Player#setPlayerProfile}. The player's own textures property is saved on the
 * player's persistent data first (it survives a crash), then put back at match end, on quit, on join and when
 * the plugin starts. An agent whose json has no signed property (empty value or signature) is skipped.
 * TODO: the eight agent files ship with an empty skin; paste a signed textures property into each.
 */
final class SkinSwap {
  private static final NamespacedKey SAVED = new NamespacedKey("vampyuyu", "vb_skin_saved");
  private static final NamespacedKey VALUE = new NamespacedKey("vampyuyu", "vb_skin_value");
  private static final NamespacedKey SIGNATURE = new NamespacedKey("vampyuyu", "vb_skin_signature");

  private SkinSwap() {}

  /** Whether the player wears an agent skin we put on (and must give back). */
  static boolean swapped(Player p) {
    return p.getPersistentDataContainer().has(SAVED, PersistentDataType.BYTE);
  }

  /** Applies the agent's skin; false when the agent has none (nothing changed). */
  static boolean apply(Player p, VbAgent agent) {
    if (!agent.hasSkin()) return false;
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    PlayerProfile profile = p.getPlayerProfile();
    if (!swapped(p)) { // keep the first original, never the skin of another agent
      ProfileProperty own = null;
      for (ProfileProperty prop : profile.getProperties()) if (prop.getName().equals("textures")) own = prop;
      pdc.set(VALUE, PersistentDataType.STRING, own == null ? "" : own.getValue());
      pdc.set(SIGNATURE, PersistentDataType.STRING, own == null || own.getSignature() == null ? "" : own.getSignature());
      pdc.set(SAVED, PersistentDataType.BYTE, (byte) 1);
    }
    profile.removeProperty("textures");
    profile.setProperty(new ProfileProperty("textures", agent.skinValue(), agent.skinSignature()));
    p.setPlayerProfile(profile);
    return true;
  }

  /** Gives the player's own skin back (a no-op when nothing was swapped). */
  static void restore(Player p) {
    PersistentDataContainer pdc = p.getPersistentDataContainer();
    if (!swapped(p)) return;
    String value = pdc.getOrDefault(VALUE, PersistentDataType.STRING, "");
    String sig = pdc.getOrDefault(SIGNATURE, PersistentDataType.STRING, "");
    PlayerProfile profile = p.getPlayerProfile();
    profile.removeProperty("textures");
    if (!value.isEmpty()) profile.setProperty(new ProfileProperty("textures", value, sig.isEmpty() ? null : sig));
    p.setPlayerProfile(profile);
    pdc.remove(SAVED);
    pdc.remove(VALUE);
    pdc.remove(SIGNATURE);
  }
}
