package win.vampyuyu.survival.promotion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.api.PlayerClass;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The Master NPC of each class (GDD 11.5), placed by an admin in the Hub area with {@code /promotion npc add <class>}. Same
 * mechanics as the merchants: a registry file ({@code promotion/npcs.json}), entities that are not persistent and are rebuilt
 * from the registry when their chunk loads. Main thread only.
 */
final class PromotionNpcs implements Listener {
    /** One saved Master. */
    static final class Npc {
        String id = "";
        String vocation = "";
        String world = "";
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    /** The registry file. */
    static final class File {
        List<Npc> npcs = new ArrayList<>();
    }

    private static final String PATH = "promotion/npcs.json";
    private static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "promotion_master");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Core core;
    private final PromotionService service;
    private final Map<String, Npc> records = new java.util.LinkedHashMap<>();
    private final Map<String, UUID> live = new HashMap<>();
    private Scheduler.Handle task;

    PromotionNpcs(Core core, PromotionService service) {
        this.core = core;
        this.service = service;
    }

    void enable() {
        File file = core.store().read(PATH, File.class, File::new);
        for (Npc r : file.npcs) {
            if (r != null && r.id != null && !r.id.isBlank() && PlayerClass.parse(r.vocation) != null) {
                records.put(r.id, r);
            }
        }
        core.listen(this);
        core.scheduler().later(40, this::refreshAll);
        task = core.scheduler().repeating(200, 200, this::refreshAll);
    }

    void disable() {
        if (task != null) {
            task.cancel();
        }
        for (String id : new ArrayList<>(live.keySet())) {
            despawn(id);
        }
    }

    List<Npc> all() {
        return new ArrayList<>(records.values());
    }

    private void save() {
        File file = new File();
        file.npcs = new ArrayList<>(records.values());
        try {
            core.store().write(PATH, file);
        } catch (IOException e) {
            core.getPlugin().getLogger().log(Level.SEVERE, "Could not save the Master NPC registry", e);
        }
    }

    Npc create(PlayerClass vocation, Location at) {
        Npc r = new Npc();
        int n = records.size() + 1;
        while (records.containsKey("master-" + n)) {
            n++;
        }
        r.id = "master-" + n;
        r.vocation = vocation.name();
        r.world = at.getWorld().getName();
        r.x = at.getX();
        r.y = at.getY();
        r.z = at.getZ();
        r.yaw = at.getYaw();
        r.pitch = at.getPitch();
        records.put(r.id, r);
        save();
        spawn(r);
        return r;
    }

    /** Removes the Master nearest to the location, within the radius. */
    boolean removeNear(Location at, double radius) {
        Npc best = null;
        double bestSq = radius * radius;
        for (Npc r : records.values()) {
            if (!r.world.equals(at.getWorld().getName())) {
                continue;
            }
            double sq = Math.pow(r.x - at.getX(), 2) + Math.pow(r.y - at.getY(), 2) + Math.pow(r.z - at.getZ(), 2);
            if (sq <= bestSq) {
                bestSq = sq;
                best = r;
            }
        }
        if (best == null) {
            return false;
        }
        records.remove(best.id);
        despawn(best.id);
        save();
        return true;
    }

    // ---------------------------------------------------------------- entities

    private Location location(Npc r) {
        World w = Bukkit.getWorld(r.world);
        return w == null ? null : new Location(w, r.x, r.y, r.z, r.yaw, r.pitch);
    }

    void refreshAll() {
        for (Npc r : records.values()) {
            try {
                Entity existing = live.containsKey(r.id) ? Bukkit.getEntity(live.get(r.id)) : null;
                if (existing != null && existing.isValid()) {
                    continue;
                }
                live.remove(r.id);
                Location at = location(r);
                if (at != null && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                        && at.getWorld().getChunkAt(at.getBlockX() >> 4, at.getBlockZ() >> 4).isEntitiesLoaded()) {
                    spawn(r);
                }
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Master NPC refresh failed for " + r.id, e);
            }
        }
    }

    private void spawn(Npc r) {
        Location at = location(r);
        PlayerClass vocation = PlayerClass.parse(r.vocation);
        if (at == null || vocation == null || live.containsKey(r.id) && Bukkit.getEntity(live.get(r.id)) != null) {
            return;
        }
        try {
            Villager entity = at.getWorld().spawn(at, Villager.class, v -> configure(v, r, vocation));
            live.put(r.id, entity.getUniqueId());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not spawn Master NPC " + r.id, e);
        }
    }

    private void configure(Villager e, Npc r, PlayerClass vocation) {
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.customName(MINI.deserialize("<gold><bold>Master " + vocation.displayName()));
        e.setCustomNameVisible(true);
        e.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, r.id);
        attempt(() -> e.setSilent(true));
        attempt(() -> e.setRemoveWhenFarAway(false));
        attempt(() -> e.setCollidable(false));
        attempt(() -> e.setAI(false));
        attempt(() -> ((Mob) e).setAware(false));
        attempt(() -> e.setProfession(switch (vocation) {
            case VANGUARD -> Villager.Profession.ARMORER;
            case DUELIST -> Villager.Profession.WEAPONSMITH;
            case CHANNELER -> Villager.Profession.CLERIC;
            case RANGER -> Villager.Profession.FLETCHER;
        }));
        attempt(() -> e.setVillagerLevel(5));
    }

    private static void attempt(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // cosmetic only
        }
    }

    private void despawn(String id) {
        UUID uuid = live.remove(id);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.remove();
            }
        }
    }

    private static String tagOf(Entity e) {
        return e.getPersistentDataContainer().get(TAG, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            String id = tagOf(e);
            if (id != null && !e.getUniqueId().equals(live.get(id))) {
                e.remove();
            }
        }
        String world = event.getWorld().getName();
        for (Npc r : records.values()) {
            if (r.world.equals(world) && ((int) Math.floor(r.x)) >> 4 == event.getChunk().getX()
                    && ((int) Math.floor(r.z)) >> 4 == event.getChunk().getZ() && !live.containsKey(r.id)) {
                spawn(r);
            }
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            String id = tagOf(e);
            if (id != null && e.getUniqueId().equals(live.get(id))) {
                live.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        String id = tagOf(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Npc r = records.get(id);
        PlayerClass vocation = r == null ? null : PlayerClass.parse(r.vocation);
        if (vocation == null) {
            core.msg().error(player, "This Master is away right now.");
            return;
        }
        service.talk(player, vocation);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (tagOf(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (tagOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && tagOf(event.getTarget()) != null) {
            event.setCancelled(true);
        }
    }
}
