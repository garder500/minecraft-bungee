package win.vampyuyu.survival.boss;

import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;

/**
 * Every Bukkit event the boss framework reacts to: damage attribution and anti-cheese, cleanup of drops and
 * explosions, arena rules, loot protection and login delivery.
 */
public final class BossListener implements Listener {
    /** Permission that bypasses the arena build ban. */
    public static final String ADMIN = "vampyuyu.sb.boss.admin";

    private final Core core;
    private final BossManager manager;
    private final Cooldowns notices = new Cooldowns();

    public BossListener(Core core, BossManager manager) {
        this.core = core;
        this.manager = manager;
    }

    // ---------------------------------------------------------------- attribution

    /** The real attacker behind projectiles, TNT, clouds and pets; null when it is not a player. */
    static @Nullable Player attacker(@Nullable Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Entity shooter ? attacker(shooter) : null;
        }
        if (damager instanceof TNTPrimed tnt) {
            return attacker(tnt.getSource());
        }
        if (damager instanceof AreaEffectCloud cloud) {
            return cloud.getSource() instanceof Entity source ? attacker(source) : null;
        }
        if (damager instanceof Tameable pet && pet.getOwner() != null) {
            return Bukkit.getPlayer(pet.getOwner().getUniqueId());
        }
        return null;
    }

    private void notice(Player player, String key, String text) {
        if (notices.tryUse(player.getUniqueId(), key, 3000)) {
            core.msg().warn(player, text);
        }
    }

    // ---------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player) {
            return; // see onPlayerDamage: players are handled early so the items feature can mitigate the result
        }
        if (!Tags.isOurs(victim) && !(victim instanceof org.bukkit.entity.ComplexEntityPart)) {
            return;
        }
        BossSession boss = manager.sessionOfBoss(victim);
        if (boss != null) {
            bossHurt(event, boss);
            return;
        }
        if (victim instanceof org.bukkit.entity.ComplexEntityPart) {
            return;
        }
        if (Tags.bossId(victim) != null) {
            event.setCancelled(true); // orphan boss entity, purged on the next chunk load or tick
            return;
        }
        BossSession owner = manager.sessionOfAux(victim);
        if (owner == null) {
            return;
        }
        Player player = event instanceof EntityDamageByEntityEvent by ? attacker(by.getDamager()) : null;
        if (victim instanceof EnderCrystal) {
            event.setCancelled(true);
            if (player != null && Fx.targetable(player) && (owner.owner() == null || owner.owner().equals(player.getUniqueId()))) {
                owner.crystalHit(victim.getUniqueId(), player);
            }
            return;
        }
        if (event instanceof EntityDamageByEntityEvent by) {
            if (Tags.isOurs(by.getDamager()) || (player != null && owner.owner() != null && !owner.owner().equals(player.getUniqueId()))) {
                event.setCancelled(true);
            }
        }
    }

    private void bossHurt(EntityDamageEvent event, BossSession session) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            event.setCancelled(true); // fall, fire, void, suffocation, drowning, explosions: the boss ignores the world
            return;
        }
        Player player = attacker(by.getDamager());
        if (player == null || session.isEnded()) {
            event.setCancelled(true);
            return;
        }
        String denial = denial(player, session, by.getDamager());
        if (denial != null) {
            event.setCancelled(true);
            if (!denial.isEmpty()) {
                notice(player, "boss-deny", denial);
            }
            return;
        }
        if (session.isShielded()) {
            event.setCancelled(true);
            Fx.sound(session.world, session.location(), org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.8f);
            notice(player, "boss-shield", "The shield absorbs your attacks. Destroy the crystals!");
            return;
        }
        double raw = event.getDamage();
        double damage = raw * BossMath.defenseMultiplier(session.def.armor());
        double credited = session.applyHit(player, damage);
        event.setCancelled(false); // bosses are exempt from region protections (hub, claims)
        event.setDamage(0.0001);
        if (credited <= 0) {
            event.setCancelled(true);
        }
    }

    /** Why this hit is refused (empty string: refuse silently), or null when it counts. */
    @Nullable String denial(Player player, BossSession session, Entity damager) {
        if (player.hasMetadata("NPC") || !player.isOnline()) {
            return "";
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return "";
        }
        if (session.owner() != null && !session.owner().equals(player.getUniqueId())) {
            return "This boss belongs to someone else. You cannot hurt it.";
        }
        BossDefinition.Arena arena = session.def.arena();
        Location at = player.getLocation();
        if (!session.world.equals(at.getWorld()) || Fx.flat(at, session.anchor) > arena.radius()) {
            return "You are too far from the fight to hurt it.";
        }
        if (at.distance(session.location()) > arena.projectileRange()) {
            return "You are out of range of the boss.";
        }
        if (arena.noFly() && (player.isFlying() || player.isGliding())) {
            return "You cannot hurt " + BossSession.plain(session.def.name()) + " while flying.";
        }
        return null;
    }

    /**
     * Boss damage against players. Runs at LOW, BEFORE the items feature applies its scale, Defense and shields at
     * HIGH, so everything a boss deals (melee, projectiles, abilities) goes through the same mitigation formula.
     * Hits made by {@link BossSession#hit} already carry the ability multiplier and are left alone.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            playerHurt(event, player);
        }
    }

    private void playerHurt(EntityDamageEvent event, Player player) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        Entity damager = by.getDamager();
        if (damager instanceof org.bukkit.entity.ComplexEntityPart part) {
            damager = part.getParent();
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
            && Tags.isOurs(shooter)) {
            BossSession shooterSession = manager.sessionOfBoss(shooter);
            if (player.hasMetadata("NPC")) {
                event.setCancelled(true);
            } else if (shooterSession != null) {
                event.setDamage(Math.max(0.5, shooterSession.hitDamage(0.7)));
            }
            return;
        }
        if (!Tags.isOurs(damager)) {
            return;
        }
        if (player.hasMetadata("NPC")) {
            event.setCancelled(true);
            return;
        }
        BossSession session = manager.sessionOfBoss(damager);
        if (session == null || session.inAbilityHit()) {
            return; // an ability hit: the amount already includes its multiplier
        }
        if (damager instanceof EnderDragon || !hasAttackAttribute(damager)) {
            event.setDamage(Math.max(0.5, session.hitDamage(1.0)));
        }
    }

    private static boolean hasAttackAttribute(Entity e) {
        return e instanceof org.bukkit.attribute.Attributable a && a.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null;
    }

    // ---------------------------------------------------------------- deaths and cleanup

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        if (!Tags.isOurs(dead)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        BossSession session = manager.sessionOfBoss(dead);
        if (session != null) {
            session.end(BossSession.End.ENTITY_LOST, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!Tags.isOurs(event.getEntity())) {
            return;
        }
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        if (Tags.isOurs(target) || target.hasMetadata("NPC")) {
            event.setCancelled(true);
            return;
        }
        BossSession session = manager.sessionOfBoss(event.getEntity());
        if (session == null) {
            session = manager.sessionOfAux(event.getEntity());
        }
        if (session != null && session.owner() != null && !session.owner().equals(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSplit(SlimeSplitEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        Entity source = event.getIgnitingEntity();
        if (source != null && (Tags.isOurs(source) || (source instanceof Projectile p && p.getShooter() instanceof Entity e
            && Tags.isOurs(e)))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(EntityPortalEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (Tags.isOurs(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)
            && event.getItem().getPersistentDataContainer().has(Tags.LOOT_OWNER, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        Item item = event.getItem();
        if (item.getPersistentDataContainer().has(Tags.LOOT_OWNER, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- arena rules

    /**
     * The fight whose arena rules bind {@code player} at {@code at}. A fight started by a player only restrains that
     * player and the ones who hit the boss: a neighbour on the next island is never locked out by someone else's
     * summon. Public events (no summoner) restrain everybody.
     */
    private @Nullable BossSession arenaAt(Location at, Player player, boolean needsNoBuild, boolean needsNoTeleport) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        for (BossSession s : manager.active()) {
            if (s.isEnded() || !at.getWorld().equals(s.world) || !s.restrains(player)) {
                continue;
            }
            BossDefinition.Arena arena = s.def.arena();
            if ((needsNoBuild && !arena.noBuild()) || (needsNoTeleport && !arena.noTeleport())) {
                continue;
            }
            if (Fx.flat(at, s.anchor) <= arena.radius() + 4) {
                return s;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().hasPermission(ADMIN) && arenaAt(event.getBlock().getLocation(), event.getPlayer(), true, false) != null) {
            event.setCancelled(true);
            notice(event.getPlayer(), "boss-build", "You cannot build during a boss fight.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!event.getPlayer().hasPermission(ADMIN) && arenaAt(event.getBlock().getLocation(), event.getPlayer(), true, false) != null) {
            event.setCancelled(true);
            notice(event.getPlayer(), "boss-build", "You cannot break blocks during a boss fight.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        Player traveller = event.getPlayer();
        if (arenaAt(event.getFrom(), traveller, false, true) != null || arenaAt(event.getTo(), traveller, false, true) != null) {
            event.setCancelled(true);
            notice(event.getPlayer(), "boss-tp", "Something blocks your teleport during the boss fight.");
        }
    }

    // ---------------------------------------------------------------- players and worlds

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        manager.playerDied(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.playerQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        core.scheduler().later(60, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                manager.rewards().applyPending(p);
                int n = manager.delivery().claimPending(p);
                if (n > 0) {
                    core.msg().ok(p, "Boss loot was waiting for you: " + n + (n == 1 ? " item" : " items") + " delivered.");
                }
            }
        });
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.worldUnloading(event.getWorld());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!manager.settings().purgeOrphans()) {
            return;
        }
        List<Entity> tagged = event.getEntities().stream().filter(Tags::isOurs).toList();
        if (!tagged.isEmpty()) {
            core.scheduler().nextTick(() -> manager.purge(tagged.stream().filter(Entity::isValid).toList()));
        }
    }
}
