package win.vampyuyu.valoblock;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The Rift Seed as an item: hotbar slot 8 of its carrier, the pack's 3D model, a tooltip that explains how to plant. */
public final class SeedItems {
  public static final NamespacedKey SEED = new NamespacedKey("vampyuyu", "vb_seed");
  /** The item model of the pack (resource-pack/valoblock, art/valoblock_seed.py). */
  public static final NamespacedKey MODEL = new NamespacedKey("vampyuyu", "valoblock/rift_seed");
  /** Hotbar index of the Seed: "slot 8" (abilities are slots 4 to 7, the Armory is slot 9). */
  public static final int SLOT = 7;
  public static final String PINK = "#ff4a8a", LAVENDER = "#e0c8ff";

  private SeedItems() {}

  /** The name and tooltip lines, as plain data. */
  static List<WeaponText.Line> tooltip() {
    return List.of(
        new WeaponText.Line("Rift Seed", PINK),
        new WeaponText.Line("You carry the Seed. Plant it on a site.", LAVENDER),
        new WeaponText.Line("Hold right-click inside a site to plant", WeaponText.GRAY),
        new WeaponText.Line("Keep holding until the bar fills", WeaponText.GRAY),
        new WeaponText.Line("Q drops it for a teammate to pick up", WeaponText.GRAY));
  }

  public static ItemStack item() {
    ItemStack it = new ItemStack(Material.ECHO_SHARD);
    ItemMeta meta = it.getItemMeta();
    List<WeaponText.Line> lines = tooltip();
    meta.displayName(WeaponItems.text(lines.get(0).text(), lines.get(0).color()));
    meta.lore(lines.subList(1, lines.size()).stream().map(l -> (Component) WeaponItems.text(l.text(), l.color())).toList());
    meta.setItemModel(MODEL);
    meta.setMaxStackSize(1);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    meta.getPersistentDataContainer().set(SEED, PersistentDataType.BYTE, (byte) 1);
    it.setItemMeta(meta);
    return it;
  }

  public static boolean isSeed(ItemStack it) {
    return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(SEED, PersistentDataType.BYTE);
  }
}
