package win.vampyuyu.survival.boss;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.api.DungeonApi;
import win.vampyuyu.survival.api.IslandApi;
import win.vampyuyu.survival.api.Stat;
import win.vampyuyu.survival.api.StatsApi;
import win.vampyuyu.survival.core.Cooldowns;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Right-click summoning with the five summon items, and the vanilla mob drops that feed them. */
public final class SummonListener implements Listener {
    public static final String PERMISSION = "vampyuyu.sb.boss.summon";

    private final Core core;
    private final BossManager manager;
    private final SummonGate gate = new SummonGate();
    private final Cooldowns debounce = new Cooldowns();
    private final Random random = new Random();
    private volatile Map<String, BossDefinition> byItem = Map.of();

    public SummonListener(Core core, BossManager manager) {
        this.core = core;
        this.manager = manager;
        refresh();
    }

    /** Rebuilds the item to boss map after a reload. */
    public void refresh() {
        Map<String, BossDefinition> map = new HashMap<>();
        for (BossDefinition def : manager.config().bosses().values()) {
            if (def.summon() != null) {
                map.put(def.summon().item(), def);
            }
        }
        byItem = map;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<String> id = core.items().idOf(item);
        if (id.isEmpty()) {
            return;
        }
        BossDefinition def = byItem.get(id.get());
        if (def == null) {
            if (BossItems.byId().containsKey(id.get()) && BossItems.byId().get(id.get()).type().equals("SUMMON ITEM")) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        Player player = event.getPlayer();
        if (!debounce.tryUse(player.getUniqueId(), "summon", 400)) {
            return;
        }
        String denial = summon(player, def, hand);
        if (denial != null) {
            core.msg().error(player, denial);
        }
    }

    /** Tries a summon; returns the English refusal, or null when the boss appeared and the item was consumed. */
    @Nullable String summon(Player player, BossDefinition def, EquipmentSlot hand) {
        BossDefinition.Summon rules = def.summon();
        if (rules == null) {
            return "This boss cannot be summoned.";
        }
        if (!player.hasPermission(PERMISSION)) {
            return "You are not allowed to summon bosses.";
        }
        if (core.service(DungeonApi.class).map(d -> d.inDungeon(player)).orElse(false)) {
            return "You cannot summon this inside a dungeon.";
        }
        World world = player.getWorld();
        if (rules.worlds().isEmpty() ? !SkyblockWorlds.isSkyblock(world.getName()) : !rules.worlds().contains(world.getName())) {
            return "This item does not work in this world.";
        }
        Location spawn = player.getLocation();
        if (rules.mode().equals("altar")) {
            BossDefinition.Altar altar = rules.altar();
            if (altar == null || !world.getName().equals(altar.world())) {
                return "You must be at the altar to use this.";
            }
            Location centre = new Location(world, altar.x(), altar.y(), altar.z());
            if (Fx.flat(player.getLocation(), centre) > altar.radius()) {
                return "You are too far from the altar. Stand within " + (int) altar.radius() + " blocks of it.";
            }
            spawn = centre;
        } else {
            spawn = freeSpot(player);
        }
        if (rules.maxSpawnDistance() > 0 && Fx.flat(player.getLocation(), world.getSpawnLocation()) > rules.maxSpawnDistance()) {
            return "You are too far from the world spawn to summon this.";
        }
        if (rules.requireWater() && !player.isInWater()) {
            return "You must stand in water to use this.";
        }
        if (rules.requireIslandMember()) {
            Optional<IslandApi> islands = core.service(IslandApi.class);
            if (islands.isPresent()) {
                var info = islands.get().islandAt(player.getLocation());
                if (info.isPresent() && !info.get().members().contains(player.getUniqueId())) {
                    return "You can only summon this on your own island.";
                }
            }
        }
        if (manager.activeCount(def.id()) >= rules.maxConcurrent()) {
            return "That boss is already awake.";
        }
        Optional<String> wait = gate.acquire(player.getUniqueId(), def.id(), rules);
        if (wait.isPresent()) {
            return wait.get();
        }
        Optional<BossSession> session = manager.spawn(def, spawn, player, new BossSession.SpawnOptions(
            false, false, 0, 0, -1, null, rules.refundOnDespawn() ? rules.item() : null));
        if (session.isEmpty()) {
            gate.release(player.getUniqueId(), def.id());
            return "The summoning failed. Try somewhere else.";
        }
        consume(player, hand);
        String message = BossSession.plain(def.name());
        for (Player p : world.getPlayers()) {
            core.msg().mini(p, "<light_purple>" + player.getName().replace("<", "") + " <gray>awakened <white>" + message + "<gray>!");
        }
        return null;
    }

    private static void consume(Player player, EquipmentSlot hand) {
        ItemStack stack = player.getInventory().getItem(hand);
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItem(hand, stack);
        }
    }

    private static Location freeSpot(Player player) {
        Location eye = player.getLocation();
        org.bukkit.util.Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new org.bukkit.util.Vector(0, 0, 1);
        }
        Location target = eye.clone().add(dir.normalize().multiply(8));
        Location ground = Abilities.ground(player.getWorld(), target.clone().add(0, 4, 0));
        if (Math.abs(ground.getY() - eye.getY()) > 8) {
            ground = eye.clone();
        }
        return ground;
    }

    // ---------------------------------------------------------------- vanilla mob drops

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || Tags.isOurs(dead) || manager.config().mobDrops().isEmpty()) {
            return;
        }
        boolean spawner = false;
        try {
            spawner = dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER;
        } catch (RuntimeException ignored) {
            // unknown reason: treated as natural
        }
        for (BossConfig.MobDrop drop : manager.config().mobDrops()) {
            if (!drop.entities().contains(dead.getType()) || (spawner && !drop.allowSpawner())
                || (!drop.worlds().isEmpty() && !drop.worlds().contains(dead.getWorld().getName()))) {
                continue;
            }
            double mf = drop.magicFind() ? core.service(StatsApi.class)
                .map(s -> Math.min(manager.settings().magicFindCap(), s.stat(killer, Stat.MAGIC_FIND))).orElse(0.0) : 0;
            if (LootMath.roll(LootMath.chance(drop.chance(), 0, 1.0, mf, drop.magicFind()), random)
                && core.items().def(drop.item()).isPresent()) {
                manager.delivery().dropReserved(dead.getLocation(), core.items().create(drop.item()), killer.getUniqueId());
                core.msg().ok(killer, "A " + core.items().def(drop.item()).get().name() + " dropped!");
            }
        }
    }
}
