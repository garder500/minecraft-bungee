package win.vampyuyu.valoblock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The Armory window (VbArmoryLayout): a full pack background drawn by the title glyphs, class tabs down the left, the selected
 * class as 2 x 2 weapon cards, the Wards below, the title showing the Shards left. The tab a player looks at is remembered
 * per player so the window comes back on it after a purchase.
 */
public final class ArmoryMenu implements InventoryHolder {
  public static final int DEFAULT_TAB = 3;
  private static final Map<UUID, Integer> TABS = new ConcurrentHashMap<>();

  private Inventory inventory;

  @Override
  public Inventory getInventory() {
    return inventory;
  }

  public static int tab(UUID player) {
    return TABS.getOrDefault(player, DEFAULT_TAB);
  }

  public static void setTab(UUID player, int tab) {
    if (tab >= 0 && tab < VbArmoryLayout.TABS.length) TABS.put(player, tab);
  }

  public static void forget(UUID player) {
    TABS.remove(player);
  }

  /** Builds the 54-slot menu for this player's Shards and loadout. The ability cells of the agents slice are added by it afterwards. */
  public static Inventory build(VbMatch m, Loadout loadout, Player p) {
    ArmoryMenu holder = new ArmoryMenu();
    int credits = m.credits(p.getUniqueId());
    holder.inventory = Bukkit.createInventory(holder, 54, VbMenuSkin.armory(WeaponText.title(credits)));
    fill(holder.inventory, m, loadout, p);
    return holder.inventory;
  }

  static void fill(Inventory inv, VbMatch m, Loadout loadout, Player p) {
    UUID id = p.getUniqueId();
    int tab = tab(id);
    for (int i = 0; i < VbArmoryLayout.TABS.length; i++) {
      String name = VbArmoryLayout.TABS[i];
      int count = VbArmoryLayout.weaponsOf(i, VbData.weapons().all()).size();
      String sprite = "tab_" + name.toLowerCase(java.util.Locale.ROOT) + (i == tab ? "_sel" : "");
      inv.setItem(VbArmoryLayout.tab(i), AgentItems.cell(sprite, List.of(new AgentText.Line(name, i == tab ? AgentText.GOLD : "#eef0f4"), new AgentText.Line(count + (count == 1 ? " weapon" : " weapons"), AgentText.GRAY)), "tab:" + i, false));
    }
    List<VbWeapon> cards = VbArmoryLayout.weaponsOf(tab, VbData.weapons().all());
    for (int i = 0; i < cards.size(); i++) place(inv, VbArmoryLayout.cardSlots(i), cards.get(i), m, loadout, id);
    List<VbWeapon> shields = VbArmoryLayout.shields(VbData.weapons().all());
    for (int i = 0; i < shields.size(); i++) place(inv, VbArmoryLayout.shieldSlots(i), shields.get(i), m, loadout, id);
  }

  private static void place(Inventory inv, int[] slots, VbWeapon w, VbMatch m, Loadout loadout, UUID id) {
    Armory.Display d = Armory.display(m, loadout, id, w);
    int missing = Armory.missing(m, loadout, id, w);
    for (int s = 0; s < slots.length; s++) {
      ItemStack it = WeaponItems.card(w, d, missing, s == 0);
      inv.setItem(slots[s], it);
    }
  }

  /** The weapon shown in a slot of the window of a player looking at {@code tab}, or null. */
  public static VbWeapon at(int tab, int slot) {
    int card = VbArmoryLayout.cardAt(slot);
    if (card >= 0) {
      List<VbWeapon> list = VbArmoryLayout.weaponsOf(tab, VbData.weapons().all());
      return card < list.size() ? list.get(card) : null;
    }
    int shield = VbArmoryLayout.shieldAt(slot);
    if (shield >= 0) {
      List<VbWeapon> list = VbArmoryLayout.shields(VbData.weapons().all());
      return shield < list.size() ? list.get(shield) : null;
    }
    return null;
  }

  /** The weapon shown in a slot of this player's window, or null. */
  public static VbWeapon at(UUID player, int slot) {
    return at(tab(player), slot);
  }

  static boolean isMenu(Inventory inv) {
    return inv != null && inv.getHolder() instanceof ArmoryMenu;
  }
}
