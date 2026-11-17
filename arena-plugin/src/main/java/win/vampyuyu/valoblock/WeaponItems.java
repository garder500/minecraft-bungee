package win.vampyuyu.valoblock;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Builds and reads the ItemStacks of ValoBlock weapons: a vanilla base item, an item_model, PDC tags. */
public final class WeaponItems {
  public static final NamespacedKey ID = new NamespacedKey("vampyuyu", "vb_weapon");
  public static final NamespacedKey MAG = new NamespacedKey("vampyuyu", "vb_mag");
  public static final NamespacedKey ARMORY = new NamespacedKey("vampyuyu", "vb_armory");

  private WeaponItems() {}

  public static Component text(String s, String hex) {
    return Component.text(s, TextColor.fromHexString(hex)).decoration(TextDecoration.ITALIC, false);
  }

  private static Material base(VbWeapon w) {
    Material m = Material.matchMaterial(w.baseItem());
    return m == null ? Material.STICK : m;
  }

  /** The item model key: {@code vampyuyu:valoblock/<id>} (or {@code _locked}, the dimmed twin). */
  public static NamespacedKey model(VbWeapon w, boolean locked) {
    return new NamespacedKey("vampyuyu", "valoblock/" + w.id() + (locked ? "_locked" : ""));
  }

  /** The weapon as carried: the stack size shows the rounds left (1 while reloading or empty). */
  public static ItemStack carried(VbWeapon w, int mag) {
    ItemStack it = new ItemStack(base(w));
    ItemMeta meta = it.getItemMeta();
    meta.displayName(text(w.name(), WeaponText.GOLD));
    meta.setItemModel(model(w, false));
    meta.setMaxStackSize(99);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    meta.setUnbreakable(true);
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(ID, PersistentDataType.STRING, w.id());
    if (w.isGun()) pdc.set(MAG, PersistentDataType.INTEGER, mag);
    List<Component> lore = new ArrayList<>();
    lore.add(text(w.kind(), WeaponText.GRAY));
    if (w.isGun()) {
      if (ScopeInput.holdScope(w)) {
        lore.add(text(ScopeInput.hint(w), WeaponText.GRAY));
        lore.add(text("F reload · Q drop", WeaponText.GRAY));
      } else lore.add(text(ScopeInput.hint(w) + " · F reload · Q drop", WeaponText.GRAY));
    }
    else if (!w.isShield()) lore.add(text("Left-click to stab", WeaponText.GRAY));
    meta.lore(lore);
    it.setItemMeta(meta);
    if (ScopeInput.holdScope(w) && w.scopedSlow() > 0) { // the walking speed while the spyglass is up: no sprint, the design's factor
      it.setData(
          io.papermc.paper.datacomponent.DataComponentTypes.USE_EFFECTS,
          io.papermc.paper.datacomponent.item.UseEffects.useEffects().canSprint(false).interactVibrations(false).speedMultiplier((float) w.scopedSlow()).build());
    }
    it.setAmount(w.isGun() ? Math.max(1, Math.min(99, mag)) : 1);
    return it;
  }

  /** The look the pack draws for the held stack (custom_model_data string 0), "" for the plain held model. */
  public static String state(ItemStack it) {
    if (it == null || !it.hasItemMeta()) return "";
    var cmd = it.getItemMeta().getCustomModelDataComponent();
    return cmd.getStrings().isEmpty() ? "" : cmd.getStrings().get(0);
  }

  /** Sets (or with "" clears) the look of the stack: "fire", "reload", "inspect" or "aim" (see GunVisual). */
  public static void setState(ItemStack it, String state) {
    ItemMeta meta = it.getItemMeta();
    var cmd = meta.getCustomModelDataComponent();
    cmd.setStrings(state.isEmpty() ? List.of() : List.of(state));
    meta.setCustomModelDataComponent(cmd);
    it.setItemMeta(meta);
  }

  /**
   * The sniper scope: an invisible helmet whose equippable camera overlay is the pack's scope texture (the same
   * mechanism as the pumpkin blur), so the screen is covered while the player is aiming. It draws nothing on the head.
   */
  public static final NamespacedKey SCOPE = new NamespacedKey("vampyuyu", "vb_scope");

  public static ItemStack scope() {
    ItemStack it = new ItemStack(Material.PAPER);
    ItemMeta meta = it.getItemMeta();
    meta.displayName(text("Scope", WeaponText.GRAY));
    meta.setItemModel(new NamespacedKey("vampyuyu", "blank"));
    var eq = meta.getEquippable();
    eq.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);
    eq.setCameraOverlay(new NamespacedKey("vampyuyu", "misc/scope"));
    eq.setSwappable(false);
    meta.setEquippable(eq);
    meta.getPersistentDataContainer().set(SCOPE, PersistentDataType.BYTE, (byte) 1);
    it.setItemMeta(meta);
    return it;
  }

  /** True while the player holds right-click with a hold-scope weapon (the client shows the spyglass overlay). */
  public static boolean holdScoped(org.bukkit.entity.Player p) {
    if (!p.hasActiveItem() || p.getActiveItemHand() != org.bukkit.inventory.EquipmentSlot.HAND) return false;
    String id = id(p.getInventory().getItemInMainHand());
    return id != null && ScopeInput.holdScope(VbData.weapons().get(id));
  }

  public static boolean isScope(ItemStack it) {
    return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(SCOPE, PersistentDataType.BYTE);
  }

  /** The card model of a weapon in the Armory: the frame says the state (owned green, too expensive dimmed). */
  public static NamespacedKey cardModel(VbWeapon w, Armory.Display display) {
    String suffix = switch (display) {
      case OWNED -> "_owned";
      case LOCKED -> "_locked";
      case AFFORDABLE -> "";
    };
    return new NamespacedKey("vampyuyu", "valoblock/card_" + w.id() + suffix);
  }

  /**
   * One of the four slots of an Armory card. The first ({@code art}) wears the 2 x 2 card model; the other three are
   * invisible twins with the same tooltip and weapon tag, so a click or a hover anywhere on the card does the same.
   */
  public static ItemStack card(VbWeapon w, Armory.Display display, int missing, boolean art) {
    ItemStack it = new ItemStack(base(w));
    ItemMeta meta = it.getItemMeta();
    List<WeaponText.Line> lines = WeaponText.tooltip(w, display, missing);
    meta.displayName(text(lines.get(0).text(), lines.get(0).color()));
    List<Component> lore = new ArrayList<>();
    for (WeaponText.Line l : lines.subList(1, lines.size())) lore.add(text(l.text(), l.color()));
    meta.lore(lore);
    meta.setItemModel(art ? cardModel(w, display) : AgentItems.model("blank"));
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, w.id());
    it.setItemMeta(meta);
    return it;
  }

  /** The shield worn as a chestplate. */
  public static ItemStack worn(VbWeapon shield) {
    ItemStack it = carried(shield, 0);
    return it;
  }

  /** The weapon id of a stack, or null. */
  public static String id(ItemStack it) {
    if (it == null || !it.hasItemMeta()) return null;
    return it.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING);
  }

  public static int mag(ItemStack it, int def) {
    if (it == null || !it.hasItemMeta()) return def;
    Integer m = it.getItemMeta().getPersistentDataContainer().get(MAG, PersistentDataType.INTEGER);
    return m == null ? def : m;
  }

  /** The Armory hotbar item. */
  public static ItemStack armoryItem() {
    ItemStack it = new ItemStack(Material.CHEST);
    ItemMeta meta = it.getItemMeta();
    meta.displayName(text("Armory", WeaponText.GOLD));
    meta.lore(List.of(text("Right-click in the buy phase to open", WeaponText.GRAY)));
    meta.getPersistentDataContainer().set(ARMORY, PersistentDataType.BYTE, (byte) 1);
    it.setItemMeta(meta);
    return it;
  }

  public static boolean isArmory(ItemStack it) {
    return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(ARMORY, PersistentDataType.BYTE);
  }

  /** Whether the stack is any ValoBlock item (weapon, shield, armory). */
  public static boolean isVb(ItemStack it) {
    return id(it) != null || isArmory(it) || isScope(it);
  }

  /** Dust colour of the tracer by metal. */
  public static Color tracer(String metal) {
    return switch (metal) {
      case "bronze" -> Color.fromRGB(0xC98442);
      case "sun" -> Color.fromRGB(0xF29A3A);
      case "frost" -> Color.fromRGB(0x7FC8E8);
      case "void" -> Color.fromRGB(0x8A4AFF);
      case "bone" -> Color.fromRGB(0xD8D4E8);
      case "marsh" -> Color.fromRGB(0x8A9A2E);
      case "storm" -> Color.fromRGB(0x7A8EE0);
      default -> Color.fromRGB(0xFFE9A0);
    };
  }
}
