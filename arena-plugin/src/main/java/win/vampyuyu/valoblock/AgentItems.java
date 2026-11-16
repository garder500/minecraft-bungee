package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** ItemStacks of the agents slice: menu entries, ability items, the "agent select" reopener. */
public final class AgentItems {
  public static final NamespacedKey ACTION = new NamespacedKey("vampyuyu", "vb_agent_action");
  public static final NamespacedKey ABILITY = new NamespacedKey("vampyuyu", "vb_ability");

  private AgentItems() {}

  public static NamespacedKey model(String name) {
    return new NamespacedKey("vampyuyu", "valoblock/" + name);
  }

  /** A menu cell: a pack sprite, a tooltip, an action tag ("pick:cinder", "role:Seer", "lock", "random", "none"). */
  public static ItemStack cell(String sprite, List<AgentText.Line> lines, String action, boolean glint) {
    ItemStack it = new ItemStack(Material.PAPER);
    ItemMeta meta = it.getItemMeta();
    meta.displayName(WeaponItems.text(lines.get(0).text(), lines.get(0).color()));
    List<Component> lore = new ArrayList<>();
    for (AgentText.Line l : lines.subList(1, lines.size())) lore.add(WeaponItems.text(l.text(), l.color()));
    meta.lore(lore);
    meta.setItemModel(model(sprite));
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    if (glint) meta.setEnchantmentGlintOverride(true);
    meta.getPersistentDataContainer().set(ACTION, PersistentDataType.STRING, action);
    it.setItemMeta(meta);
    return it;
  }

  /** The action tag of a menu cell, or null. */
  public static String action(ItemStack it) {
    if (it == null || !it.hasItemMeta()) return null;
    return it.getItemMeta().getPersistentDataContainer().get(ACTION, PersistentDataType.STRING);
  }

  /** An ability on the hotbar: its vanilla icon, the stack size is the charges (1 at least). */
  public static ItemStack ability(VbAgent a, VbAbility ab, AgentKit kit) {
    Material mat = Material.matchMaterial(ab.item());
    ItemStack it = new ItemStack(mat == null ? Material.STICK : mat);
    ItemMeta meta = it.getItemMeta();
    boolean ready = kit.canUse(ab.slot()) == AgentKit.Result.OK;
    meta.displayName(WeaponItems.text("[" + ab.slot() + "] " + ab.name() + (ready ? "" : " (not ready)"), ready ? a.trim() : "#777777"));
    List<Component> lore = new ArrayList<>();
    for (AgentText.Line l : AgentText.abilityLines(a, ab, kit)) lore.add(WeaponItems.text(l.text(), l.color()));
    meta.lore(lore);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    if (ab.kind() == VbAbility.Kind.ULTIMATE && ready) meta.setEnchantmentGlintOverride(true);
    meta.getPersistentDataContainer().set(ABILITY, PersistentDataType.INTEGER, ab.slot());
    it.setItemMeta(meta);
    it.setAmount(Math.max(1, Math.min(ab.kind() == VbAbility.Kind.ULTIMATE ? 1 : 99, kit.charges(ab.slot()))));
    return it;
  }

  /** The slot of an ability item, or -1. */
  public static int slotOf(ItemStack it) {
    if (it == null || !it.hasItemMeta()) return -1;
    Integer s = it.getItemMeta().getPersistentDataContainer().get(ABILITY, PersistentDataType.INTEGER);
    return s == null ? -1 : s;
  }

  /** Hotbar item that reopens the agent select menu while it is still open. */
  public static ItemStack selectItem() {
    ItemStack it = cell("lock_in", List.of(new AgentText.Line("Agent select", AgentText.GREEN), new AgentText.Line("Right-click to open the menu", AgentText.GRAY)), "reopen", false);
    it.setType(Material.NETHER_STAR);
    return it;
  }
}
