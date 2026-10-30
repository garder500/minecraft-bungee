package win.vampyuyu.survival.minions;

import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Keyed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Cooldowns;

/** Bukkit events of the feature: placing, opening the menu, protecting the props, chunk and world lifecycle. */
final class MinionListener implements Listener {
    private final Core core;
    private final MinionManager manager;
    private final MinionGui gui;
    private final MinionRecipes recipes;
    private final Cooldowns clicks = new Cooldowns();

    MinionListener(Core core, MinionManager manager, MinionGui gui, MinionRecipes recipes) {
        this.core = core;
        this.manager = manager;
        this.gui = gui;
        this.recipes = recipes;
    }

    // ---------------------------------------------------------------- placing

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (MinionItems.parse(core.items(), item) == null || event.getClickedBlock() == null) {
            return;
        }
        // A minion item never does anything else, whatever the block under it is.
        boolean blocked = event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (blocked || !clicks.tryUse(p.getUniqueId(), "place", 250)) {
            return;
        }
        try {
            MinionManager.Result result = manager.place(p, item, event.getClickedBlock(), event.getBlockFace());
            if (!result.ok()) {
                core.msg().error(p, result.error());
                return;
            }
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItemInMainHand(item.getAmount() <= 0 ? null : item);
            Optional<MinionData> placed = manager.at(event.getClickedBlock().getRelative(event.getBlockFace()));
            placed.ifPresent(d -> {
                core.msg().ok(p, "Minion placed (" + manager.placedCount(p.getUniqueId()) + "/"
                    + manager.slotsFor(p.getUniqueId()) + " slots). Right-click it to open its menu.");
                core.scheduler().nextTick(() -> manager.refresh(d));
            });
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.SEVERE, "minions: placing failed", e);
            core.msg().error(p, "Something went wrong while placing the minion.");
        }
    }

    // ---------------------------------------------------------------- the menu

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Optional<String> id = MinionVisuals.minionIdOf(clicked);
        if (id.isEmpty() && !MinionVisuals.isMobProp(clicked)) {
            return;
        }
        event.setCancelled(true);
        if (id.isEmpty() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (!clicks.tryUse(p.getUniqueId(), "open", 300)) {
            return;
        }
        Optional<MinionData> minion = manager.store().get(id.get());
        if (minion.isEmpty()) {
            return;
        }
        if (!manager.canAccess(p, minion.get())) {
            core.msg().error(p, "This minion belongs to " + ownerName(minion.get()) + ".");
            return;
        }
        gui.open(p, minion.get());
    }

    private String ownerName(MinionData d) {
        try {
            return core.profiles().lastName(java.util.UUID.fromString(d.owner));
        } catch (RuntimeException e) {
            return "another player";
        }
    }

    // ---------------------------------------------------------------- props are untouchable

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();
        if (MinionVisuals.minionIdOf(e).isPresent() || MinionVisuals.isMobProp(e)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (MinionVisuals.isMobProp(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (MinionVisuals.isMobProp(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (MinionVisuals.minionIdOf(e).isPresent() || MinionVisuals.isMobProp(e)) {
                e.remove();
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.chunkUnloaded(event.getWorld().getName(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.worldUnloaded(event.getWorld().getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later(60, () -> {
            if (p.isOnline()) {
                manager.deliverOwed(p);
            }
        });
        try {
            p.discoverRecipes(recipes.keys());
        } catch (RuntimeException e) {
            core.debug("minions: could not unlock recipes: " + e);
        }
    }

    // ---------------------------------------------------------------- crafting

    /** A minion item (paper) or an enchanted resource must never be eaten by a vanilla recipe. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack s : event.getInventory().getMatrix()) {
            if (s != null && core.items().idOf(s).map(i -> i.startsWith("minion_")).orElse(false)) {
                event.getInventory().setResult(null);
                return;
            }
        }
        // A crafted minion gets its uid now, so a copy of it (crash that restores an older inventory) is caught by the
        // ledger once the original has been placed.
        if (event.getRecipe() instanceof Keyed keyed && recipes.isOurs(keyed.getKey())) {
            ItemStack result = event.getInventory().getResult();
            if (result != null && MinionItems.parse(core.items(), result) != null) {
                ItemStack stamped = result.clone();
                core.items().set(stamped, MinionItems.PDC_UID, MinionItems.newUid());
                event.getInventory().setResult(stamped);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !recipes.isOurs(keyed.getKey())
                || !(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        MinionItems.Parsed made = MinionItems.parse(core.items(), event.getRecipe().getResult());
        if (made != null) {
            manager.markCrafted(p.getUniqueId(), made.type(), made.tier());
        }
    }
}
