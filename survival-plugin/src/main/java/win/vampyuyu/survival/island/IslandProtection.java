package win.vampyuyu.survival.island;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.SkyblockWorlds;
import win.vampyuyu.survival.island.IslandAccess.Action;

/**
 * Island protection: blocks, containers, entities, vehicles, explosions, pistons, liquids, hoppers, dispensers,
 * fire, mob griefing, projectiles, spawning and PvP. Every handler first asks whether the position is in one of the
 * four island-feature worlds (dungeon and vanilla worlds are never touched) and ignores NPC entities from other
 * plugins (metadata "NPC").
 */
public final class IslandProtection implements Listener {
    private static final Set<String> MOB_BREAK_ALLOWED = Set.of("SHEEP", "VILLAGER", "FOX", "RABBIT", "BEE");
    private static final Set<CreatureSpawnEvent.SpawnReason> RESTRICTED_SPAWNS = Set.of(
        CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
        CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.JOCKEY,
        CreatureSpawnEvent.SpawnReason.REINFORCEMENTS, CreatureSpawnEvent.SpawnReason.PATROL,
        CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
        CreatureSpawnEvent.SpawnReason.TRAP, CreatureSpawnEvent.SpawnReason.MOUNT,
        CreatureSpawnEvent.SpawnReason.LIGHTNING, CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK,
        CreatureSpawnEvent.SpawnReason.INFECTION, CreatureSpawnEvent.SpawnReason.SLIME_SPLIT,
        CreatureSpawnEvent.SpawnReason.NETHER_PORTAL);

    private final IslandService service;
    private final IslandConfig cfg;
    private final Cooldowns notices = new Cooldowns();

    public IslandProtection(IslandService service) {
        this.service = service;
        this.cfg = service.config();
    }

    // ---------------------------------------------------------------- helpers

    private static boolean ours(Location location) {
        World w = location == null ? null : location.getWorld();
        return w != null && SkyblockWorlds.isSkyblock(w.getName()) && !SkyblockWorlds.isDungeon(w.getName());
    }

    private static boolean isNpc(Entity entity) {
        return entity != null && entity.hasMetadata("NPC");
    }

    /** True (and tells the player) when the player is NOT allowed. */
    private boolean denied(Player player, Location location, Action action) {
        if (!ours(location) || service.allowed(player, location, action)) {
            return false;
        }
        if (notices.tryUse(player.getUniqueId(), "deny", cfg.messageCooldownMs)) {
            service.core().msg().warn(player, service.denyReason(player, location));
        }
        return true;
    }

    private Island islandOfBlock(Block block) {
        return service.islandAtLocation(block.getLocation());
    }

    private static String worldName(Location location) {
        return location.getWorld().getName();
    }

    private Player shooter(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player p ? p : null;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    /** Blocks anybody may use when the island allows interaction: no state of the island changes. */
    private static final Set<Material> HARMLESS_USE = Set.of(Material.LEVER, Material.ENDER_CHEST,
        Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.STONECUTTER, Material.LOOM,
        Material.CARTOGRAPHY_TABLE, Material.SMITHING_TABLE, Material.GRINDSTONE, Material.BELL);

    /** The allow-list of {@code visitor_interact}: doors, trapdoors, gates, buttons, levers, plates and pure GUIs. */
    static boolean harmlessToUse(Material type) {
        String name = type.name();
        return HARMLESS_USE.contains(type) || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE");
    }

    /**
     * What an interaction with this block needs; null when the block does nothing when clicked (stone, dirt...), so
     * eating or throwing while pointing at it is never blocked. Everything that reacts to a click and is not in the
     * harmless allow-list (repeaters, comparators, note blocks, signs, cake, beds, anvils...) is a BUILD.
     */
    static Action blockAction(Block block) {
        Action explicit = explicitAction(block);
        if (explicit != null) {
            return explicit;
        }
        Material type = block.getType();
        if (harmlessToUse(type)) {
            return Action.INTERACT;
        }
        if (type.name().endsWith("_FENCE")) {
            return null; // listed as interactable only because of leads, which count as a build tool anyway
        }
        return type.isInteractable() ? Action.BUILD : null;
    }

    /** Blocks that need BUILD or CONTAINER whatever the held item is (crops, eggs, fire sources, storage...). */
    static Action explicitAction(Block block) {
        Material type = block.getType();
        String name = type.name();
        if (type == Material.FARMLAND || type == Material.TURTLE_EGG || type == Material.SNIFFER_EGG
                || type == Material.FLOWER_POT || name.startsWith("POTTED_") || type == Material.COMPOSTER
                || type == Material.RESPAWN_ANCHOR
                || type == Material.CAULDRON || name.endsWith("_CAULDRON") || type == Material.DRAGON_EGG
                || type == Material.TNT || type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES
                || type == Material.CAVE_VINES_PLANT || type == Material.GLOW_LICHEN || type == Material.SCULK_SENSOR
                || type == Material.CALIBRATED_SCULK_SENSOR || type == Material.SCULK_SHRIEKER
                || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE || type == Material.BEEHIVE
                || type == Material.BEE_NEST || type == Material.CANDLE || name.endsWith("_CANDLE")
                || type == Material.PUMPKIN) {
            return Action.BUILD;
        }
        if (type == Material.JUKEBOX || type == Material.LECTERN || type == Material.DECORATED_POT
                || type == Material.CHISELED_BOOKSHELF || type == Material.BREWING_STAND) {
            return Action.CONTAINER;
        }
        try {
            BlockState state = block.getState(false);
            if (state instanceof Container) {
                return Action.CONTAINER;
            }
        } catch (RuntimeException e) {
            // fall through
        }
        return null;
    }

    /** Held items that change the world when used on a block. */
    static boolean isBuildTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_SPAWN_EGG") || name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")
            || name.endsWith("_HOE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_BUCKET")
            || type == Material.ARMOR_STAND || type == Material.END_CRYSTAL || type == Material.FLINT_AND_STEEL
            || type == Material.FIRE_CHARGE || type == Material.BONE_MEAL || type == Material.SHEARS
            || type == Material.GLOW_INK_SAC || type == Material.INK_SAC || type == Material.HONEYCOMB
            || type == Material.BRUSH || type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME
            || type == Material.PAINTING || type == Material.LEAD || type == Material.TNT
            || type == Material.FIREWORK_ROCKET;
    }

    // ---------------------------------------------------------------- blocks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD)
                || denied(event.getPlayer(), event.getBlockAgainst().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (isNpc(event.getEntity())) {
            return;
        }
        if (denied(event.getPlayer(), event.getEntity().getLocation(), Action.ANIMAL)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (denied(event.getPlayer(), event.getHarvestedBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        switch (event.getAction()) {
            case PHYSICAL -> {
                Block block = event.getClickedBlock();
                if (block != null && ours(block.getLocation())) {
                    Action needed = explicitAction(block) == Action.BUILD ? Action.BUILD : Action.INTERACT;
                    if (service.allowed(player, block.getLocation(), needed)) {
                        return;
                    }
                    event.setUseInteractedBlock(Event.Result.DENY);
                    event.setCancelled(true);
                }
            }
            case RIGHT_CLICK_BLOCK, LEFT_CLICK_BLOCK -> {
                Block block = event.getClickedBlock();
                if (block == null || !ours(block.getLocation())) {
                    return;
                }
                Action needed;
                if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                    // punching a block: only matters for fire and dragon eggs, both are BUILD
                    needed = explicitAction(block) == Action.BUILD ? Action.BUILD : null;
                } else {
                    needed = blockAction(block);
                    if (isBuildTool(event.getItem()) && needed != Action.CONTAINER) {
                        needed = Action.BUILD;
                    }
                }
                if (needed == null) {
                    return;
                }
                if (denied(player, block.getLocation(), needed)) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                    if (isBuildTool(event.getItem())) {
                        event.setUseItemInHand(Event.Result.DENY);
                        event.setCancelled(true);
                    }
                    // otherwise only the block is off limits: eating, drawing a bow or throwing a pearl while
                    // pointing at a repeater or sign still works
                }
            }
            default -> {
                // air clicks: throwing items is not an island matter
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && denied(player, event.getEntity().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- entities

    private static boolean isStructure(Entity entity) {
        return entity instanceof ArmorStand || entity instanceof Hanging || entity.getType() == EntityType.END_CRYSTAL
            || entity.getType() == EntityType.LEASH_KNOT;
    }

    private static boolean isContainerEntity(Entity entity) {
        String name = entity.getType().name();
        return name.equals("CHEST_MINECART") || name.equals("HOPPER_MINECART") || name.endsWith("CHEST_BOAT")
            || name.equals("CHEST_RAFT");
    }

    private Action entityAction(Entity entity) {
        if (isContainerEntity(entity)) {
            return Action.CONTAINER;
        }
        if (isStructure(entity)) {
            return Action.BUILD;
        }
        if (entity instanceof Player || entity instanceof Monster) {
            return null;
        }
        if (entity instanceof Boat || entity instanceof Minecart || entity instanceof org.bukkit.entity.Villager
                || entity instanceof org.bukkit.entity.WanderingTrader) {
            return Action.INTERACT;
        }
        if (entity instanceof LivingEntity) {
            return Action.ANIMAL;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (isNpc(entity)) {
            return;
        }
        Action needed = entityAction(entity);
        if (needed == null) {
            return;
        }
        if (denied(event.getPlayer(), entity.getLocation(), needed)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!isNpc(event.getEntity()) && denied(event.getPlayer(), event.getEntity().getLocation(), Action.ANIMAL)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!isNpc(event.getRightClicked())
                && denied(event.getPlayer(), event.getRightClicked().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && denied(player, event.getEntity().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = shooter(event.getRemover());
        if (isNpc(event.getEntity())) {
            return;
        }
        if (player == null) {
            if (ours(event.getEntity().getLocation()) && event.getRemover() != null
                    && !(event.getRemover() instanceof Player)) {
                // mobs and explosions never break pictures or frames
                event.setCancelled(true);
            }
            return;
        }
        if (denied(player, event.getEntity().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = shooter(event.getAttacker());
        if (player != null && !isNpc(event.getVehicle()) && denied(player, event.getVehicle().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && !isNpc(event.getVehicle())
                && denied(player, event.getVehicle().getLocation(), Action.INTERACT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (isNpc(victim) || !ours(victim.getLocation())) {
            return;
        }
        Player attacker = shooter(event.getDamager());
        if (victim instanceof Player target) {
            // PvP: hub never, islands by their pvp setting (the victim's island decides)
            if (attacker == null || attacker.equals(target) || isNpc(attacker)) {
                return;
            }
            if (!pvpOn(victim.getLocation())) {
                event.setCancelled(true);
                if (notices.tryUse(attacker.getUniqueId(), "pvp", cfg.messageCooldownMs)) {
                    service.core().msg().warn(attacker, "PvP is disabled here.");
                }
            }
            return;
        }
        if (attacker == null) {
            // mobs and the environment hurting frames, stands, animals is allowed except for hanging items
            if (victim instanceof Hanging || victim instanceof ArmorStand) {
                event.setCancelled(true);
            }
            return;
        }
        Action needed = entityAction(victim);
        if (needed == null) {
            return;
        }
        if (needed == Action.INTERACT) {
            needed = Action.ANIMAL; // hitting a villager is hurting an animal-like mob
        }
        if (denied(attacker, victim.getLocation(), needed)) {
            event.setCancelled(true);
        }
    }

    /** Whether players may hurt each other where the victim stands (never in the hub). */
    private boolean pvpOn(Location where) {
        if (SkyblockWorlds.HUB.equals(worldName(where))) {
            return false;
        }
        Island island = service.islandAtLocation(where);
        return island != null && island.flag(IslandFlag.PVP);
    }

    // ---------------------------------------------------------------- signs, beds, items, fishing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (denied(event.getPlayer(), event.getBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event) {
        if (denied(event.getPlayer(), event.getSign().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (denied(event.getPlayer(), event.getBed().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    /**
     * May the player take this item entity? Their own drops always, anything on their own island or one they are
     * trusted on; on a stranger's island only when {@code protection.visitor-pickup} says so (banned players never).
     */
    boolean mayTake(Player player, Item item) {
        Location at = item.getLocation();
        if (!ours(at) || player.hasPermission(IslandService.BYPASS)) {
            return true;
        }
        UUID id = player.getUniqueId();
        if (id.equals(item.getThrower()) || id.equals(item.getOwner())) {
            return true;
        }
        if (SkyblockWorlds.HUB.equals(worldName(at))) {
            return true;
        }
        Island island = service.islandAtLocation(at);
        if (island == null) {
            return true;
        }
        IslandRole role = island.roleOf(id);
        if (role.isCoop() || role == IslandRole.TRUSTED) {
            return true;
        }
        return cfg.visitorPickup && role != IslandRole.BANNED;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !mayTake(player, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Location at = player.getLocation();
        if (cfg.visitorDrop || !ours(at) || player.hasPermission(IslandService.BYPASS)
                || SkyblockWorlds.HUB.equals(worldName(at))) {
            return;
        }
        Island island = service.islandAtLocation(at);
        if (island == null) {
            return;
        }
        IslandRole role = island.roleOf(player.getUniqueId());
        if (!role.isCoop() && role != IslandRole.TRUSTED) {
            event.setCancelled(true);
            if (notices.tryUse(player.getUniqueId(), "drop", cfg.messageCooldownMs)) {
                service.core().msg().warn(player, "You cannot drop items on someone else's island.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        Entity caught = event.getCaught();
        Player player = event.getPlayer();
        if (caught == null || isNpc(caught) || !ours(caught.getLocation())) {
            return;
        }
        boolean refuse;
        if (caught instanceof Item item) {
            refuse = !mayTake(player, item);
        } else if (caught instanceof Player victim) {
            refuse = !victim.equals(player) && !pvpOn(victim.getLocation());
        } else {
            Action needed = entityAction(caught);
            if (needed == Action.INTERACT) {
                needed = Action.ANIMAL;
            }
            refuse = needed != null && denied(player, caught.getLocation(), needed);
        }
        if (refuse) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- explosions, fire, griefing

    private void filterExplosion(Location origin, Iterator<Block> blocks, boolean allowed) {
        Island source = origin == null || !ours(origin) ? null : service.islandAtLocation(origin);
        boolean inIsland = source != null && allowed;
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (!ours(block.getLocation())) {
                continue;
            }
            if (!inIsland || islandOfBlock(block) != source) {
                blocks.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location origin = event.getLocation();
        if (!ours(origin)) {
            return;
        }
        Island island = service.islandAtLocation(origin);
        filterExplosion(origin, event.blockList().iterator(), island != null && island.flag(IslandFlag.TNT));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location origin = event.getBlock().getLocation();
        if (!ours(origin)) {
            return;
        }
        Island island = service.islandAtLocation(origin);
        filterExplosion(origin, event.blockList().iterator(), island != null && island.flag(IslandFlag.TNT));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Location at = event.getBlock().getLocation();
        if (!ours(at)) {
            return;
        }
        Island island = service.islandAtLocation(at);
        Player player = event.getPlayer();
        if (player != null) {
            return; // flint and steel is checked through the interact handler
        }
        if (island == null || !island.flag(IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
            return;
        }
        Block source = event.getIgnitingBlock();
        if (source != null && islandOfBlock(source) != island) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Location at = event.getBlock().getLocation();
        if (!ours(at)) {
            return;
        }
        Island island = service.islandAtLocation(at);
        if (island == null || !island.flag(IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!ours(event.getBlock().getLocation())) {
            return;
        }
        Island target = islandOfBlock(event.getBlock());
        Island source = islandOfBlock(event.getSource());
        if (target == null || source != target) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityForm(EntityBlockFormEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && denied(player, event.getBlock().getLocation(), Action.BUILD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Location at = event.getBlock().getLocation();
        if (!ours(at)) {
            return;
        }
        if (entity instanceof Player) {
            return;
        }
        if (entity instanceof FallingBlock) {
            if (islandOfBlock(event.getBlock()) == null) {
                event.setCancelled(true);
            }
            return;
        }
        if (entity instanceof LivingEntity && !MOB_BREAK_ALLOWED.contains(entity.getType().name())) {
            event.setCancelled(true); // endermen, silverfish, ravagers, withers, zombies breaking doors...
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Location at = event.getLocation();
        if (!ours(at)) {
            return;
        }
        Island source = service.islandAtLocation(at);
        if (source == null) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getBlocks()) {
            if (service.islandAtLocation(state.getLocation()) != source) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block block = event.getHitBlock();
        if (block == null || !ours(block.getLocation())) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player player) {
            Action needed = explicitAction(block);
            if (needed == null) {
                if (!isTrigger(block)) {
                    return;
                }
                needed = Action.INTERACT;
            }
            if (!service.allowed(player, block.getLocation(), needed)) {
                projectile.remove();
            }
            return;
        }
        Island here = islandOfBlock(block);
        Island from = ours(projectile.getLocation()) ? service.islandAtLocation(projectile.getLocation()) : null;
        if (here == null || here != from) {
            projectile.remove();
        }
    }

    private static boolean isTrigger(Block block) {
        String name = block.getType().name();
        return name.contains("BUTTON") || name.contains("PRESSURE_PLATE") || name.equals("TARGET") || name.equals("BELL")
            || name.contains("TRIPWIRE") || name.equals("POINTED_DRIPSTONE") || name.startsWith("CHORUS");
    }

    // ---------------------------------------------------------------- crossing the border

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquid(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (!ours(from.getLocation())) {
            return;
        }
        Island a = islandOfBlock(from);
        Island b = islandOfBlock(event.getToBlock());
        if (a != b) {
            event.setCancelled(true);
        }
    }

    private boolean crosses(Block piston, List<Block> moved, BlockFace direction) {
        Island home = islandOfBlock(piston);
        if (home == null) {
            return true;
        }
        for (Block block : moved) {
            if (islandOfBlock(block) != home || islandOfBlock(block.getRelative(direction)) != home) {
                return true;
            }
        }
        return islandOfBlock(piston.getRelative(direction)) != home;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (ours(event.getBlock().getLocation()) && crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (ours(event.getBlock().getLocation()) && crosses(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        Location source = event.getSource().getLocation();
        Location destination = event.getDestination().getLocation();
        if (source == null || destination == null || (!ours(source) && !ours(destination))) {
            return;
        }
        if (!ours(source) || !ours(destination) || service.islandAtLocation(source) != service.islandAtLocation(destination)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (!ours(block.getLocation())) {
            return;
        }
        Island home = islandOfBlock(block);
        if (home == null) {
            event.setCancelled(true);
            return;
        }
        if (block.getBlockData() instanceof Directional directional) {
            Block target = block.getRelative(directional.getFacing());
            if (islandOfBlock(target) != home || islandOfBlock(target.getRelative(directional.getFacing())) != home) {
                event.setCancelled(true);
            }
        }
    }

    // ---------------------------------------------------------------- spawning and hunger

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        Location at = event.getLocation();
        if (!ours(at)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (isNpc(entity) || !RESTRICTED_SPAWNS.contains(event.getSpawnReason())) {
            return;
        }
        if (SkyblockWorlds.HUB.equals(worldName(at))) {
            event.setCancelled(true);
            return;
        }
        Island island = service.islandAtLocation(at);
        if (island == null) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Monster && !island.flag(IslandFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        } else if (entity instanceof Animals && !island.flag(IslandFlag.ANIMALS_SPAWNING)) {
            event.setCancelled(true);
        } else if (!(entity instanceof Monster) && !(entity instanceof Animals) && entity.getType().name().contains("SLIME")
                && !island.flag(IslandFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && SkyblockWorlds.HUB.equals(player.getWorld().getName())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    /** Lower-case id of a material (tests). */
    static String id(Material m) {
        return m.name().toLowerCase(Locale.ROOT);
    }
}
