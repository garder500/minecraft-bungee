package win.vampyuyu.valoblock;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The Armory chest menu: one column per class, cheapest at the top, title shows the Shards left. */
public final class ArmoryMenu implements InventoryHolder {
  private Inventory inventory;

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  /** Builds the 54-slot menu for this player's Shards and loadout. Row 6 (abilities) belongs to the agents slice. */
  public static Inventory build(VbMatch m, Loadout loadout, Player p) {
    ArmoryMenu holder = new ArmoryMenu();
    int credits = m.credits(p.getUniqueId());
    holder.inventory = Bukkit.createInventory(holder, 54, Component.text(WeaponText.title(credits)));
    fill(holder.inventory, m, loadout, p);
    return holder.inventory;
  }

  static void fill(Inventory inv, VbMatch m, Loadout loadout, Player p) {
    for (VbWeapon w : VbData.weapons().all()) {
      if (w.menuRow() < 0) continue;
      Armory.Display d = Armory.display(m, loadout, p.getUniqueId(), w);
      inv.setItem(w.menuRow() * 9 + w.menuCol(), WeaponItems.menu(w, d, Armory.missing(m, loadout, p.getUniqueId(), w)));
    }
  }

  /** The weapon shown in a slot, or null. */
  public static VbWeapon at(int slot) {
    for (VbWeapon w : VbData.weapons().all()) if (w.menuRow() >= 0 && w.menuRow() * 9 + w.menuCol() == slot) return w;
    return null;
  }


  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder() instanceof ArmoryMenu;
  }

}
