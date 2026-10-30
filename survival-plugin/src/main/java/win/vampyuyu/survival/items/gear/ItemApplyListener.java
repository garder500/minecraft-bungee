package win.vampyuyu.survival.items.gear;

import java.util.Optional;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.items.ItemsSettings;
import win.vampyuyu.survival.items.reforge.Reforge;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Applying upgrade items by clicking them onto another item in the player's own inventory: recombobulator, hot potato
 * books, singularities, drill fuel and reforge stones. The cursor item is consumed (one per use) in the same event
 * that changes the target, so nothing can be duplicated or lost halfway.
 */
public final class ItemApplyListener implements Listener {
    private final Core core;
    private final Gear gear;
    private final StatsEngine engine;

    public ItemApplyListener(Core core, Gear gear, StatsEngine engine) {
        this.core = core;
        this.gear = gear;
        this.engine = engine;
    }

    /** Result of trying to apply an upgrade to an item (pure data, tested without a server). */
    public record Outcome(boolean ok, String message) {
        static Outcome fail(String message) { return new Outcome(false, message); }

        static Outcome ok(String message) { return new Outcome(true, message); }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getClick() != ClickType.LEFT
                || event.getClickedInventory() == null || !(event.getClickedInventory() instanceof PlayerInventory)
                || event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }
        Optional<String> apply = gear.items().getString(cursor, Keys.APPLY);
        if (apply.isEmpty() || apply.get().equals("pet_candy")) {
            return;
        }
        Optional<GearInfo> cursorInfo = gear.read(cursor);
        Optional<GearInfo> targetInfo = gear.read(target);
        if (cursorInfo.isEmpty() || targetInfo.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        ItemStack modified = target.clone();
        Outcome outcome = apply(apply.get(), cursorInfo.get(), targetInfo.get(), cursor, modified, engine.settings());
        if (!outcome.ok()) {
            core.msg().error(player, outcome.message());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        gear.rebuild(modified);
        ItemStack left = cursor.clone();
        left.setAmount(cursor.getAmount() - 1);
        event.setCurrentItem(modified);
        event.setCursor(left.getAmount() <= 0 ? null : left);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
        core.msg().ok(player, outcome.message());
        engine.markDirty(player.getUniqueId(), true);
    }

    /** Applies the upgrade to {@code target} (modified in place). The caller consumes the cursor item. */
    public Outcome apply(String action, GearInfo source, GearInfo targetInfo, ItemStack cursor, ItemStack target,
            ItemsSettings settings) {
        ItemKind kind = targetInfo.kind();
        String name = ItemRender.displayName(targetInfo, gear.reforges());
        if (action.equals("recombobulate")) {
            if (kind == ItemKind.OTHER || kind == ItemKind.BOOK || kind == ItemKind.PET) {
                return Outcome.fail("This item cannot be recombobulated.");
            }
            if (targetInfo.recombobulated()) {
                return Outcome.fail("This item is already recombobulated.");
            }
            if (!Gear.canRecombobulate(targetInfo.def().rarity())) {
                return Outcome.fail("This item is already at the highest rarity that can be reached.");
            }
            gear.setLong(target, Keys.RECOMB, 1);
            return Outcome.ok("Recombobulated " + name + ".");
        }
        if (action.equals("hot_potato")) {
            if (!kind.weapon() && !kind.armor()) {
                return Outcome.fail("Hot Potato Books work on weapons and armor only.");
            }
            int max = settings.i("hot-potato.max", 10);
            if (targetInfo.potatoBooks() >= max) {
                return Outcome.fail("This item already has " + max + " Hot Potato Books.");
            }
            gear.setLong(target, Keys.HOT_POTATO, targetInfo.potatoBooks() + 1L);
            return Outcome.ok("Applied a Hot Potato Book (" + (targetInfo.potatoBooks() + 1) + "/" + max + ").");
        }
        if (action.equals("singularity")) {
            boolean fits = switch (source.id()) {
                case "wood_singularity" -> kind == ItemKind.AXE;
                case "stone_singularity" -> kind == ItemKind.PICKAXE || kind == ItemKind.DRILL || kind == ItemKind.SHOVEL;
                case "hay_singularity" -> kind == ItemKind.HOE;
                default -> false;
            };
            if (!fits) {
                return Outcome.fail("This singularity does not fit that tool.");
            }
            if (targetInfo.singularity() > 0) {
                return Outcome.fail("This item already has a singularity.");
            }
            gear.setLong(target, Keys.SINGULARITY, 1);
            return Outcome.ok("The singularity merged with " + name + ".");
        }
        if (action.startsWith("fuel:")) {
            long max = targetInfo.def().pdcLongs().getOrDefault(Keys.FUEL_MAX, 0L);
            if (max <= 0) {
                return Outcome.fail("Only drills can be refuelled.");
            }
            long fuel = gear.items().getLong(target, Keys.FUEL).orElse(max);
            if (fuel >= max) {
                return Outcome.fail("The drill is already full.");
            }
            long add;
            try {
                add = Long.parseLong(action.substring(5));
            } catch (NumberFormatException e) {
                return Outcome.fail("Broken fuel item.");
            }
            long now = Math.min(max, fuel + add);
            gear.items().set(target, Keys.FUEL, now);
            return Outcome.ok("Refuelled to " + now + "/" + max + ".");
        }
        if (action.equals("reforge")) {
            String stoneId = gear.items().getString(cursor, Keys.STONE).orElse(null);
            Optional<Reforge> reforge = gear.reforges().byStone(stoneId);
            if (reforge.isEmpty()) {
                return Outcome.fail("This stone has no effect.");
            }
            if (!reforge.get().appliesTo(kind)) {
                return Outcome.fail("The " + reforge.get().name() + " reforge does not fit this kind of item.");
            }
            if (reforge.get().id().equals(targetInfo.reforge())) {
                return Outcome.fail("This item already has that reforge.");
            }
            gear.setReforge(target, reforge.get().id());
            return Outcome.ok("Reforged to " + reforge.get().name() + ".");
        }
        return Outcome.fail("Nothing happens.");
    }
}
