package win.vampyuyu.survival.dungeon.run;

import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import win.vampyuyu.survival.api.event.BossKillEvent;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.dungeon.DungeonItems;

/**
 * Every Bukkit event that touches a run: protection of the dungeon blocks, damage and death rules, the teleport and
 * command lock, puzzle and door interaction. Stateless apart from the manager reference.
 */
public final class RunListener implements Listener {
    public static final String ADMIN = "vampyuyu.sb.dungeon.admin";
    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = Set.of(CreatureSpawnEvent.SpawnReason.NATURAL,
        CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
        CreatureSpawnEvent.SpawnReason.JOCKEY, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
        CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.TRAP,
        CreatureSpawnEvent.SpawnReason.BREEDING, CreatureSpawnEvent.SpawnReason.EGG, CreatureSpawnEvent.SpawnReason.LIGHTNING,
        CreatureSpawnEvent.SpawnReason.SLIME_SPLIT, CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM,
        CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN, CreatureSpawnEvent.SpawnReason.BUILD_WITHER,
        CreatureSpawnEvent.SpawnReason.SPAWNER_EGG, CreatureSpawnEvent.SpawnReason.DROWNED,
        CreatureSpawnEvent.SpawnReason.INFECTION, CreatureSpawnEvent.SpawnReason.CURED);

    private final RunContext ctx;
    private final RunManager manager;
    private final Cooldowns cooldowns = new Cooldowns();

    public RunListener(RunContext ctx, RunManager manager) {
        this.ctx = ctx;
        this.manager = manager;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean dungeonWorld(org.bukkit.World w) {
        return w != null && SkyblockWorlds.isDungeon(w.getName());
    }

    private DungeonRun runOf(Player p) {
        return manager.runOf(p.getUniqueId());
    }

    /** The run of a participant that is still taking part, else null. */
    private DungeonRun active(Player p) {
        DungeonRun r = manager.runOf(p.getUniqueId());
        return r != null && r.isParticipant(p.getUniqueId()) ? r : null;
    }

    private boolean admin(Player p) {
        return p.hasPermission(ADMIN);
    }

    private static Player playerOf(Entity e) {
        if (e instanceof Player p) {
            return p;
        }
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** The dungeon monster behind a damager (a projectile resolves to its shooter), or null. */
    private LivingEntity mobOf(Entity damager) {
        Entity e = damager;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity s) {
            e = s;
        }
        return e instanceof LivingEntity le && !(le instanceof Player) && ctx.keys.runOf(le) != null ? le : null;
    }

    // ------------------------------------------------------------------ join and quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        manager.playerGone(p.getUniqueId());
        ctx.parties.forget(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // a player that logs in inside a dungeon world without a run (crash, quit mid-run) is sent out
        ctx.scheduler().later(5, () -> {
            if (p.isOnline() && RunManager.inDungeonWorld(p) && active(p) == null) {
                if (p.getGameMode() == GameMode.SPECTATOR && !admin(p)) {
                    p.setGameMode(GameMode.SURVIVAL);
                }
                RunManager.sendOut(ctx, p);
            }
        });
    }

    // ------------------------------------------------------------------ damage and death

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!dungeonWorld(victim.getWorld())) {
            return;
        }
        Player attacker = playerOf(event.getDamager());
        LivingEntity mob = mobOf(event.getDamager());
        // NPCs and props cannot be hurt
        if (ctx.keys.npcOf(victim) != null || (victim instanceof org.bukkit.entity.ArmorStand && ctx.keys.runOf(victim) != null)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof Player vp) {
            if (attacker != null && attacker != vp) {
                event.setCancelled(true); // no friendly fire
                return;
            }
            if (mob != null) {
                Double mult = ctx.keys.damageOf(mob);
                if (mult != null) {
                    event.setDamage(event.getDamage() * mult);
                }
            }
            return;
        }
        if (ctx.keys.runOf(victim) != null && victim instanceof LivingEntity) {
            if (mob != null) {
                event.setCancelled(true); // monsters never hurt each other
                return;
            }
            if (attacker != null) {
                RunPlayer rp = runOf(attacker) == null ? null : runOf(attacker).player(attacker.getUniqueId());
                if (rp != null && !rp.alive()) {
                    event.setCancelled(true);
                    return;
                }
            }
            Double factor = ctx.keys.factorOf(victim);
            if (factor != null && factor > 1) {
                event.setDamage(event.getDamage() / factor);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || !dungeonWorld(p.getWorld())) {
            return;
        }
        DungeonRun run = active(p);
        if (run == null) {
            return;
        }
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null) {
            return;
        }
        if (!run.state.active() && run.state != RunState.COUNTDOWN) {
            event.setCancelled(true); // the run is over: nothing can hurt anymore
            return;
        }
        if (rp.ghost || rp.eliminated || run.state == RunState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        if (rp.invulnerableUntil > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            run.deaths.fell(p, rp);
            return;
        }
        if (event.getFinalDamage() >= p.getHealth() + p.getAbsorptionAmount()) {
            if (run.deaths.onLethal(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player) {
            return;
        }
        String runId = ctx.keys.runOf(e);
        if (runId == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        DungeonRun run = manager.run(runId);
        if (run != null) {
            run.fights.mobDied(e, e.getKiller());
        }
    }

    /** Fallback for deaths that bypass the damage event (commands, other plugins): never lose items or levels. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        if (!dungeonWorld(p.getWorld()) || active(p) == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        DungeonRun run = active(p);
        if (run == null || run.world == null || run.state.over()) {
            return;
        }
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null) {
            return;
        }
        event.setRespawnLocation(rp.lastSafe != null ? rp.lastSafe : run.loc(run.built.spawn()));
        ctx.scheduler().nextTick(() -> {
            if (p.isOnline() && run.isParticipant(p.getUniqueId())) {
                run.deaths.onLethal(p);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && dungeonWorld(p.getWorld()) && active(p) != null) {
            event.setCancelled(true);
            if (p.getFoodLevel() < 20) {
                p.setFoodLevel(20);
            }
        }
    }

    // ------------------------------------------------------------------ monsters

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (ctx.keys.runOf(event.getEntity()) != null && event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (dungeonWorld(event.getLocation().getWorld()) && NATURAL.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (dungeonWorld(event.getBlock().getWorld()) && !(admin(event.getPlayer()) && event.getPlayer().getGameMode() == GameMode.CREATIVE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (dungeonWorld(event.getBlock().getWorld()) && !(admin(event.getPlayer()) && event.getPlayer().getGameMode() == GameMode.CREATIVE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (dungeonWorld(event.getEntity().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (dungeonWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ interaction

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        // consumables work anywhere inside a run
        ItemStack held = event.getItem();
        if (held != null && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && ctx.core.items().isCustom(held)) {
            if (useItem(event, p, held)) {
                return;
            }
        }
        if (!dungeonWorld(p.getWorld())) {
            return;
        }
        DungeonRun run = active(p);
        if (run == null) {
            if (!admin(p)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || !rp.alive()) {
            event.setCancelled(true);
            return;
        }
        Block b = event.getClickedBlock();
        if (b == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (Tag.PRESSURE_PLATES.isTagged(b.getType())) {
                for (RoomRt rt : run.rooms.values()) {
                    if (rt.puzzle != null && !rt.puzzle.solved && rt.puzzle.step(p, b)) {
                        return;
                    }
                }
            } else if (b.getType() == Material.FARMLAND || b.getType() == Material.TURTLE_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (run.state == RunState.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        for (RoomRt rt : run.rooms.values()) {
            if (rt.puzzle != null && rt.puzzle.click(p, b)) {
                event.setCancelled(true);
                return;
            }
        }
        if (b.getType() == Material.LEVER && run.fights.clickLever(p, b)) {
            return; // let the lever flip, it is the secret
        }
        if (run.fights.clickChest(p, b) || run.fights.clickDoor(p, b)) {
            event.setCancelled(true);
            return;
        }
        // everything else (containers, workstations, doors, beds...) stays shut; buttons and levers keep working
        if (b.getType() != Material.LEVER && !Tag.BUTTONS.isTagged(b.getType())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** Spirit leaf and healing flask. Returns true when the click was handled. */
    private boolean useItem(PlayerInteractEvent event, Player p, ItemStack held) {
        var items = ctx.core.items();
        boolean leaf = items.is(held, DungeonItems.SPIRIT_LEAF);
        boolean flask = items.is(held, DungeonItems.HEALING_FLASK);
        if (!leaf && !flask) {
            return false;
        }
        event.setCancelled(true);
        DungeonRun run = active(p);
        if (run == null || run.state.over()) {
            ctx.core.msg().warn(p, "This item only works inside a dungeon run.");
            return true;
        }
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || !rp.alive()) {
            return true;
        }
        if (leaf) {
            String name = run.deaths.reviveWithItem(p);
            if (name == null) {
                run.say(p, "<gray>Nobody in your party needs reviving.");
                return true;
            }
            held.setAmount(held.getAmount() - 1);
            return true;
        }
        if (!cooldowns.tryUse(p.getUniqueId(), "flask", ctx.cfg.flaskCooldownSeconds * 1000L)) {
            run.say(p, "<gray>The flask is still empty for " + (cooldowns.remaining(p.getUniqueId(), "flask") / 1000 + 1) + "s.");
            return true;
        }
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || p.getHealth() >= max.getValue()) {
            cooldowns.clear(p.getUniqueId(), "flask");
            run.say(p, "<gray>You are already at full health.");
            return true;
        }
        held.setAmount(held.getAmount() - 1);
        p.setHealth(Math.min(max.getValue(), p.getHealth() + max.getValue() * ctx.cfg.flaskHealPercent / 100.0));
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        if (!dungeonWorld(p.getWorld()) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity target = event.getRightClicked();
        DungeonRun run = active(p);
        if (run == null) {
            if (!admin(p)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        RunPlayer rp = run.player(p.getUniqueId());
        if (rp == null || !rp.alive()) {
            return;
        }
        String npc = ctx.keys.npcOf(target);
        if (npc == null) {
            return;
        }
        if (npc.equals("merchant")) {
            Shop.open(run, p);
            return;
        }
        for (RoomRt rt : run.rooms.values()) {
            if (rt.puzzle != null && rt.puzzle.clickEntity(p, target)) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ movement rules

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (ctx.teleporting() || admin(p)) {
            return;
        }
        DungeonRun run = active(p);
        boolean toDungeon = dungeonWorld(event.getTo().getWorld());
        if (run != null && !run.state.over()) {
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && event.getTo().getWorld() == run.world) {
                return;
            }
            event.setCancelled(true);
            run.say(p, "<red>You cannot teleport inside the dungeon. <gray>Use /dungeon leave to abandon the run.");
            return;
        }
        if (toDungeon && (run == null || run.world != event.getTo().getWorld())) {
            event.setCancelled(true); // only the feature itself brings people in
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p
                && dungeonWorld(p.getWorld()) && !admin(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChorus(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        if (event.getItem().getType() == Material.CHORUS_FRUIT && dungeonWorld(p.getWorld()) && !admin(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (event.isFlying() && dungeonWorld(p.getWorld()) && p.getGameMode() != GameMode.CREATIVE
                && p.getGameMode() != GameMode.SPECTATOR && active(p) != null) {
            event.setCancelled(true);
            p.setAllowFlight(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player p && dungeonWorld(p.getWorld()) && !admin(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        DungeonRun run = active(p);
        if (run == null || run.state.over() || admin(p)) {
            return;
        }
        String line = event.getMessage();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String label = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            String ns = label.substring(0, label.indexOf(':'));
            label = label.substring(label.indexOf(':') + 1);
            if (!ns.equals(ctx.core.getPlugin().getName().toLowerCase(Locale.ROOT))) {
                label = "?" + label; // another plugin's namespaced alias: never allowed
            }
        }
        if (!ctx.cfg.allowedCommands.contains(label)) {
            event.setCancelled(true);
            run.say(p, "<red>You cannot use that command inside the dungeon. <gray>Use /dungeon leave first.");
        }
    }

    // ------------------------------------------------------------------ boss feature

    @EventHandler
    public void onBossKill(BossKillEvent event) {
        if (!BossDirector.BOSS_ID.equals(event.getBossId())) {
            return;
        }
        var w = event.getLocation().getWorld();
        if (!dungeonWorld(w)) {
            return;
        }
        DungeonRun run = manager.runInWorld(w);
        if (run != null) {
            run.boss.externalKilled();
        }
    }
}
