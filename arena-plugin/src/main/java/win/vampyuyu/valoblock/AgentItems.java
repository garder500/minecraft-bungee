package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
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
    for (AgentText.Line l : lines.subList(1, lines.size())) TipWrap.add(lore, l.text(), l.color());
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

  /**
   * An ability on the hotbar: the pack's own icon for it (item model from {@link AbilityItemState}), named after the ability, the
   * stack size is the charges left, a dimmed icon when none is left, a lit one for a charged ultimate. The item carries a
   * use_cooldown component with its own group, so {@link #cooldown} can show the vanilla cooldown overlay on it.
   */
  public static ItemStack ability(VbAgent a, VbAbility ab, AgentKit kit) {
    AbilityItemState st = AbilityItemState.of(a, ab, kit);
    ItemStack it = new ItemStack(Material.PAPER);
    ItemMeta meta = it.getItemMeta();
    meta.displayName(WeaponItems.text(st.title(), st.titleColor()));
    List<Component> lore = new ArrayList<>();
    for (AgentText.Line l : st.lore()) TipWrap.add(lore, l.text(), l.color());
    meta.lore(lore);
    meta.setItemModel(model(st.model()));
    UseCooldownComponent cd = meta.getUseCooldown();
    cd.setCooldownSeconds(1f);
    cd.setCooldownGroup(new NamespacedKey("vampyuyu", st.cooldownGroup()));
    meta.setUseCooldown(cd);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    meta.getPersistentDataContainer().set(ABILITY, PersistentDataType.INTEGER, ab.slot());
    it.setItemMeta(meta);
    it.setAmount(st.count());
    return it;
  }

  /** Shows the vanilla cooldown overlay on the ability's hotbar item (its group, whatever its icon variant). */
  public static void cooldown(Player p, VbAgent a, VbAbility ab, int ticks) {
    p.setCooldown(new NamespacedKey("vampyuyu", AbilityItemState.cooldownGroup(a.id(), ab.id())), ticks);
  }

  /** The slot of an ability item, or -1. */
  public static int slotOf(ItemStack it) {
    if (it == null || !it.hasItemMeta()) return -1;
    Integer s = it.getItemMeta().getPersistentDataContainer().get(ABILITY, PersistentDataType.INTEGER);
    return s == null ? -1 : s;
  }

  /** Hotbar item that reopens the agent select menu while it is still open. */
  public static ItemStack selectItem() {
    ItemStack it = cell("lock_in", List.of(new AgentText.Line("Choose your agent", AgentText.GREEN), new AgentText.Line("Right-click to open the menu", AgentText.GRAY)), "reopen", false);
    it.setType(Material.NETHER_STAR);
    return it;
  }
}
