package win.vampyuyu.valoblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The Bukkit side of {@link Outfit}: the marked equippable items and putting them on and taking them off. */
final class OutfitItems {
  static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "vb_outfit");

  private OutfitItems() {}

  static ItemStack piece(String agent, String agentName, Outfit.Piece piece) {
    ItemStack it = new ItemStack(Material.PAPER); // no attribute modifiers: zero armour points
    ItemMeta meta = it.getItemMeta();
    meta.displayName(Component.text(agentName + " " + piece.label, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
    meta.lore(java.util.List.of(Component.text("Worn for the match", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
    meta.setItemModel(new NamespacedKey("vampyuyu", "valoblock/agent_" + agent));
    var eq = meta.getEquippable();
    eq.setSlot(switch (piece) {
      case FEET -> EquipmentSlot.FEET;
      case LEGS -> EquipmentSlot.LEGS;
      case CHEST -> EquipmentSlot.CHEST;
      case HEAD -> EquipmentSlot.HEAD;
    });
    eq.setModel(new NamespacedKey("vampyuyu", Outfit.assetId(agent)));
    eq.setSwappable(false);
    eq.setDispensable(false);
    eq.setEquipOnInteract(false);
    eq.setDamageOnHurt(false);
    meta.setEquippable(eq);
    meta.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
    it.setItemMeta(meta);
    return it;
  }

  static boolean isOutfit(ItemStack it) {
    return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(MARK, PersistentDataType.BYTE);
  }

  /** Fills the empty armour slots with the agent's outfit. */
  static void dress(Player p, VbAgent agent) {
    PlayerInventory inv = p.getInventory();
    ItemStack[] armor = inv.getArmorContents();
    if (Outfit.fill(armor, piece -> piece(agent.id(), agent.name(), piece), it -> it == null || it.getType().isAir()) > 0) inv.setArmorContents(armor);
  }

  /** Takes every outfit piece off the player, wherever it is (armour, hotbar, offhand, cursor). */
  static void undress(Player p) {
    PlayerInventory inv = p.getInventory();
    ItemStack[] all = inv.getContents();
    if (Outfit.strip(all, OutfitItems::isOutfit) > 0) inv.setContents(all);
    if (isOutfit(p.getItemOnCursor())) p.setItemOnCursor(null);
  }
}
