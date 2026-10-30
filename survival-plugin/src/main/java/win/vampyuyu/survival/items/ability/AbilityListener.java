package win.vampyuyu.survival.items.ability;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.GearInfo;
import win.vampyuyu.survival.items.stats.StatsEngine;

/** Click and fishing triggers of item abilities (shoot and break triggers are called by the combat and break code). */
public final class AbilityListener implements Listener {
    private final AbilityRegistry registry;
    private final StatsEngine engine;
    private final Gear gear;

    public AbilityListener(AbilityRegistry registry, StatsEngine engine, Gear gear) {
        this.registry = registry;
        this.engine = engine;
        this.gear = gear;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        if (!engine.active(player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return;
        }
        Optional<GearInfo> read = gear.read(hand);
        if (read.isEmpty()) {
            return;
        }
        GearInfo info = read.get();
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (right && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }
        for (Ability ability : registry.forItem(info)) {
            Ability.Trigger trigger = ability.trigger();
            boolean match = switch (trigger) {
                case RIGHT_CLICK -> right && !player.isSneaking();
                case SNEAK_RIGHT_CLICK -> right && player.isSneaking();
                case LEFT_CLICK -> left;
                default -> false;
            };
            if (match && registry.use(player, hand, info, ability, event)) {
                if (right) {
                    event.setCancelled(true);
                }
                return;
            }
        }
    }

    /** Reel in abilities (Grappling Hook, Rod of Legends). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!engine.active(player)) {
            return;
        }
        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.REEL_IN && state != PlayerFishEvent.State.IN_GROUND
                && state != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<GearInfo> read = gear.read(hand);
        if (read.isEmpty()) {
            return;
        }
        for (Ability ability : registry.forItem(read.get())) {
            if (ability.trigger() == Ability.Trigger.FISH) {
                registry.use(player, hand, read.get(), ability, event);
            }
        }
    }

    /** The hook entity of a fishing event, if any. */
    public static Optional<FishHook> hook(Event event) {
        return event instanceof PlayerFishEvent fish ? Optional.of(fish.getHook()) : Optional.empty();
    }

    /** Hooked entity of a fishing event, if any. */
    public static Optional<LivingEntity> caught(Event event) {
        if (event instanceof PlayerFishEvent fish) {
            Entity entity = fish.getCaught();
            if (entity instanceof LivingEntity living) {
                return Optional.of(living);
            }
        }
        return Optional.empty();
    }

    /** Location of the hook, or null. */
    public static Location hookLocation(Event event) {
        return hook(event).map(FishHook::getLocation).orElse(null);
    }
}
