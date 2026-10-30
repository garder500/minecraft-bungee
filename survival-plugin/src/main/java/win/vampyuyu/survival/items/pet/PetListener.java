package win.vampyuyu.survival.items.pet;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import win.vampyuyu.survival.api.event.SkillXpEvent;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;
import win.vampyuyu.survival.items.combat.CombatModifiers;
import win.vampyuyu.survival.items.stats.StatsEngine;

/**
 * Pet lifecycle (join, quit, respawn, world change), pet experience from skill experience, egg and candy items, the
 * passive perks that are not plain stats (damage against some mobs, damage reduction, revive, potion effects) and
 * the follow task of the visual.
 */
public final class PetListener implements Listener, CombatModifiers {
    private final Core core;
    private final PetService service;
    private final StatsEngine engine;
    private final Map<UUID, Long> reviveReadyAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickGuard = new ConcurrentHashMap<>();
    private Scheduler.Handle followTask;
    private Scheduler.Handle perkTask;
    private long tick;

    public PetListener(Core core, PetService service, StatsEngine engine) {
        this.core = core;
        this.service = service;
        this.engine = engine;
    }

    public void start() {
        followTask = core.scheduler().repeating(2, service.settings().petUpdateTicks, () -> service.visuals().follow(tick += service.settings().petUpdateTicks));
        perkTask = core.scheduler().repeating(40, 20, this::perkTick);
        for (World world : Bukkit.getWorlds()) {
            service.visuals().purge(world);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            service.restore(player);
        }
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
        }
        if (perkTask != null) {
            perkTask.cancel();
        }
        service.visuals().despawnAll();
    }

    // ---------------------------------------------------------------- lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later(20, () -> {
            if (player.isOnline() && service.data(player.getUniqueId()).autoPet) {
                service.restore(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        service.forget(id);
        // The Phoenix cooldown survives a relog; only an expired entry is dropped.
        reviveReadyAt.computeIfPresent(id, (key, readyAt) -> keepRevive(readyAt, System.currentTimeMillis()) ? readyAt : null);
        clickGuard.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later(5, () -> {
            if (player.isOnline()) {
                service.restore(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.dismiss(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later(2, () -> {
            if (player.isOnline()) {
                service.dismiss(player.getUniqueId());
                service.restore(player);
            }
        });
    }

    // ---------------------------------------------------------------- experience

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkillXp(SkillXpEvent event) {
        double gained = event.getAmount();
        if (gained <= 0 || !Double.isFinite(gained)) {
            return;
        }
        Player player = event.getPlayer();
        Optional<PetService.Active> active = service.activeOf(player.getUniqueId());
        if (active.isEmpty()) {
            return;
        }
        PetData.Entry entry = active.get().entry();
        double xp = service.xpFor(entry, event.getSkill(), gained);
        if (xp > 0) {
            service.addXp(player.getUniqueId(), entry, xp);
        }
    }

    // ---------------------------------------------------------------- items

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir() || !service.gear().items().isCustom(stack)) {
            return;
        }
        boolean egg = service.gear().items().is(stack, "pet_egg");
        boolean candy = service.gear().items().is(stack, "pet_candy");
        if (!egg && !candy) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        long now = System.currentTimeMillis();
        Long last = clickGuard.put(player.getUniqueId(), now);
        if (last != null && now - last < 250) {
            return;
        }
        if (egg) {
            useEgg(player, stack);
        } else {
            useCandy(player, stack);
        }
    }

    private void useEgg(Player player, ItemStack stack) {
        Optional<PetData.Entry> read = service.readEgg(stack);
        if (read.isEmpty()) {
            core.msg().error(player, "This egg is empty.");
            return;
        }
        PetData.Entry entry = read.get();
        if (service.find(player.getUniqueId(), entry.id).isPresent()) {
            core.msg().error(player, "That pet is already in your collection.");
            return;
        }
        Optional<PetData.Entry> added = service.add(player.getUniqueId(), entry.type,
            service.rarityOf(entry), entry.xp, entry.held.isEmpty() ? null : entry.held);
        if (added.isEmpty()) {
            core.msg().error(player, "Your pet collection is full (" + service.settings().petMaxOwned + ").");
            return;
        }
        // Keep the stable id so the same egg cannot be duplicated into two collection entries.
        added.get().id = entry.id;
        consumeOne(player, stack);
        core.profiles().markDirty(player.getUniqueId());
        core.msg().ok(player, "Added to your pets. Open them with /pets.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    private void useCandy(Player player, ItemStack stack) {
        Optional<PetService.Active> active = service.activeOf(player.getUniqueId());
        if (active.isEmpty()) {
            core.msg().error(player, "Summon a pet first.");
            return;
        }
        double xp = service.settings().d("pets.candy-xp", 5000);
        consumeOne(player, stack);
        service.addXp(player.getUniqueId(), active.get().entry(), xp);
        core.msg().ok(player, "Your pet enjoyed the candy.");
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f);
    }

    private static void consumeOne(Player player, ItemStack stack) {
        if (stack.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    // ---------------------------------------------------------------- perks

    private Optional<PetService.Active> perkOwner(Player player) {
        if (!engine.active(player)) {
            return Optional.empty();
        }
        return service.activeOf(player.getUniqueId());
    }

    @Override
    public double outgoingBonus(Player attacker, LivingEntity target) {
        Optional<PetService.Active> active = perkOwner(attacker);
        if (active.isEmpty()) {
            return 0;
        }
        double bonus = 0;
        for (PetType.Perk perk : active.get().type().perks()) {
            if (perk.kind() == PetType.Kind.DAMAGE_VS && perk.targets().contains(target.getType())) {
                bonus += perk.value(active.get().level()) / 100.0;
            }
        }
        return bonus;
    }

    @Override
    public double incomingMultiplier(Player victim, EntityDamageEvent event) {
        Optional<PetService.Active> active = perkOwner(victim);
        if (active.isEmpty()) {
            return 1;
        }
        double reduction = 0;
        for (PetType.Perk perk : active.get().type().perks()) {
            if (perk.kind() == PetType.Kind.DAMAGE_REDUCTION) {
                reduction += perk.value(active.get().level());
            }
        }
        return Math.max(0.2, 1.0 - Math.min(80, reduction) / 100.0);
    }

    @Override
    public boolean preventDeath(Player victim, EntityDamageEvent event, double damage) {
        Optional<PetService.Active> active = perkOwner(victim);
        if (active.isEmpty() || event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.KILL
                || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) {
            return false;
        }
        for (PetType.Perk perk : active.get().type().perks()) {
            if (perk.kind() != PetType.Kind.REVIVE) {
                continue;
            }
            long now = System.currentTimeMillis();
            if (reviveReadyAt.getOrDefault(victim.getUniqueId(), 0L) > now) {
                return false;
            }
            reviveReadyAt.put(victim.getUniqueId(), now
                + (long) (service.settings().d("pets.revive-cooldown-seconds", 300) * 1000));
            var attribute = victim.getAttribute(Attribute.MAX_HEALTH);
            double max = attribute == null ? 20 : attribute.getValue();
            victim.setHealth(Math.max(1, Math.min(max, max * perk.base() / 100.0)));
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
            core.msg().ok(victim, "Your Phoenix brought you back from the ashes.");
            return true;
        }
        return false;
    }

    /** Applies the potion perks of every active pet (the effect is refreshed each second and expires quickly). */
    private void perkTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<PetService.Active> active = perkOwner(player);
            if (active.isEmpty() || player.isDead()) {
                continue;
            }
            for (PetType.Perk perk : active.get().type().perks()) {
                if (perk.kind() == PetType.Kind.POTION && perk.potion() != null) {
                    PotionEffectType type = perk.potion();
                    player.addPotionEffect(new PotionEffect(type, 60, 0, true, false, false));
                }
            }
        }
    }

    /** Pure: a revive cooldown entry is kept across a logout while it is still running. */
    public static boolean keepRevive(long readyAt, long now) {
        return readyAt > now;
    }

    /** Test hook: stores a revive cooldown. */
    public void reviveCooldown(UUID id, long readyAt) {
        reviveReadyAt.put(id, readyAt);
    }

    /** Test hook: when the pet may revive its owner again (epoch millis, 0 when ready). */
    public long reviveReadyAt(UUID id) {
        return reviveReadyAt.getOrDefault(id, 0L);
    }
}
