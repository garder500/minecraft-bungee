package win.vampyuyu.survival.economy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * Persistent NPC merchants. The registry lives in {@code economy/npcs.json}; the entities are NOT persistent
 * (the server never saves them), they are rebuilt from the registry whenever the chunk's entities load and removed
 * again on unload and on disable, so there are no stale copies. Every entity is tagged in its PDC, any tagged entity
 * we do not own is removed. Main thread only.
 */
final class NpcManager implements Listener {
    /** One saved NPC. */
    static final class Npc {
        String id = "";
        String shop = "";
        String name = "";
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

    private static final String PATH = "economy/npcs.json";
    private static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "economy_npc");
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Core core;
    private final ShopService shops;
    private final ShopUi ui;
    private final Map<String, Npc> records = new java.util.LinkedHashMap<>();
    private final Map<String, UUID> live = new HashMap<>();
    private Scheduler.Handle task;

    NpcManager(Core core, ShopService shops, ShopUi ui) {
        this.core = core;
        this.shops = shops;
        this.ui = ui;
    }

    void enable() {
        File file = core.store().read(PATH, File.class, File::new);
        for (Npc r : file.npcs) {
            if (r != null && r.id != null && !r.id.isBlank()) {
                records.put(r.id, r);
            }
        }
        core.listen(this);
        if (!records.isEmpty()) {
            core.scheduler().later(40, this::refreshAll);
            task = core.scheduler().repeating(200, 200, this::refreshAll);
        }
    }

    void disable() {
        if (task != null) {
            task.cancel();
        }
        despawnAll();
    }

    // ---------------------------------------------------------------- registry

    List<Npc> all() {
        return new ArrayList<>(records.values());
    }

    Optional<Npc> get(String id) {
        return Optional.ofNullable(records.get(id));
    }

    private void save() {
        File file = new File();
        file.npcs = new ArrayList<>(records.values());
        try {
            core.store().write(PATH, file);
        } catch (IOException e) {
            core.getPlugin().getLogger().log(Level.SEVERE, "Could not save the NPC registry", e);
        }
    }

    String nextId() {
        int n = records.size() + 1;
        while (records.containsKey("npc-" + n)) {
            n++;
        }
        return "npc-" + n;
    }

    Npc create(String shopId, String name, Location at) {
        Npc r = new Npc();
        r.id = nextId();
        r.shop = shopId;
        r.name = name == null ? "" : name;
        place(r, at);
        records.put(r.id, r);
        save();
        if (task == null) {
            task = core.scheduler().repeating(200, 200, this::refreshAll);
        }
        spawn(r);
        return r;
    }

    private static void place(Npc r, Location at) {
        r.world = at.getWorld().getName();
        r.x = at.getX();
        r.y = at.getY();
        r.z = at.getZ();
        r.yaw = at.getYaw();
        r.pitch = at.getPitch();
    }

    boolean remove(String id) {
        Npc r = records.remove(id);
        if (r == null) {
            return false;
        }
        despawn(id);
        save();
        return true;
    }

    void move(Npc r, Location at) {
        place(r, at);
        save();
        despawn(r.id);
        spawn(r);
    }

    void update(Npc r) {
        save();
        despawn(r.id);
        spawn(r);
    }

    /** Nearest NPC within {@code radius} blocks of the location. */
    Optional<Npc> nearest(Location at, double radius) {
        Npc best = null;
        double bestSq = radius * radius;
        for (Npc r : records.values()) {
            if (!r.world.equals(at.getWorld().getName())) {
                continue;
            }
            double dx = r.x - at.getX();
            double dy = r.y - at.getY();
            double dz = r.z - at.getZ();
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq <= bestSq) {
                bestSq = sq;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    int liveCount() {
        return live.size();
    }

    // ---------------------------------------------------------------- entities

    private Location location(Npc r) {
        World w = Bukkit.getWorld(r.world);
        return w == null ? null : new Location(w, r.x, r.y, r.z, r.yaw, r.pitch);
    }

    private boolean entitiesReady(Location at) {
        World w = at.getWorld();
        int cx = at.getBlockX() >> 4;
        int cz = at.getBlockZ() >> 4;
        return w.isChunkLoaded(cx, cz) && w.getChunkAt(cx, cz).isEntitiesLoaded();
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
                if (at != null && entitiesReady(at)) {
                    spawn(r);
                }
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "NPC refresh failed for " + r.id, e);
            }
        }
    }

    private void spawn(Npc r) {
        Location at = location(r);
        if (at == null || live.containsKey(r.id) && Bukkit.getEntity(live.get(r.id)) != null) {
            return;
        }
        var shop = shops.catalog().shop(r.shop);
        Component name = MINI.deserialize(!r.name.isBlank() ? r.name
            : shop.map(ShopCatalog.ShopDef::npcName).orElse("<red>Unknown merchant"));
        EntityType type = shop.map(s -> typeOf(s.npcModel())).orElse(EntityType.VILLAGER);
        try {
            Class<? extends Entity> cls = type.getEntityClass();
            if (cls == null) {
                return;
            }
            Entity entity = at.getWorld().spawn(at, cls, e -> configure(e, r, shop.orElse(null), name));
            live.put(r.id, entity.getUniqueId());
        } catch (RuntimeException e) {
            core.getPlugin().getLogger().log(Level.WARNING, "Could not spawn NPC " + r.id, e);
        }
    }

    static EntityType typeOf(String raw) {
        try {
            EntityType t = EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
            if (t.isSpawnable() && t.getEntityClass() != null && t != EntityType.PLAYER) {
                return t;
            }
        } catch (IllegalArgumentException ignored) {
            // MANNEQUIN does not exist on every server version; fall through
        }
        return EntityType.VILLAGER;
    }

    private void configure(Entity e, Npc r, ShopCatalog.ShopDef shop, Component name) {
        e.setPersistent(false);
        e.setInvulnerable(true);
        e.customName(name);
        e.setCustomNameVisible(true);
        e.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, r.id);
        // The rest is behaviour polish; a server that lacks one of these must still get a working merchant.
        attempt(() -> e.setSilent(true));
        if (e instanceof LivingEntity living) {
            attempt(() -> living.setRemoveWhenFarAway(false));
            attempt(() -> living.setCollidable(false));
            attempt(() -> living.setCanPickupItems(false));
            attempt(() -> living.setAI(false));
        }
        if (e instanceof Mob mob) {
            attempt(() -> mob.setAware(false));
        }
        if (e instanceof Villager villager) {
            attempt(() -> {
                String prof = shop == null ? "none" : shop.profession().toLowerCase(Locale.ROOT);
                Villager.Profession p = org.bukkit.Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof));
                villager.setProfession(p == null ? Villager.Profession.NONE : p);
                villager.setVillagerLevel(5);
            });
        }
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

    void despawnAll() {
        for (String id : new ArrayList<>(live.keySet())) {
            despawn(id);
        }
        live.clear();
    }

    private String tagOf(Entity e) {
        return e.getPersistentDataContainer().get(TAG, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        // Remove tagged leftovers we do not own, then spawn the NPCs that belong to this chunk.
        for (Entity e : event.getEntities()) {
            String id = tagOf(e);
            if (id != null && !e.getUniqueId().equals(live.get(id))) {
                e.remove();
            }
        }
        if (records.isEmpty()) {
            return;
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
        var shop = r == null ? Optional.<ShopCatalog.ShopDef>empty() : shops.catalog().shop(r.shop);
        if (shop.isEmpty()) {
            core.msg().error(player, "This merchant is closed right now.");
            return;
        }
        ui.open(player, shop.get());
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
