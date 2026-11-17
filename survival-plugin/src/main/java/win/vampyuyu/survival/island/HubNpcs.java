package win.vampyuyu.survival.island;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The villagers standing on the hub pads (Merchant, Archivist, Guard, Contract Hunter and the three gatekeepers). The
 * behaviour is deliberately minimal in phase 1: a click runs a command or says a line. NPC memory is a separate
 * feature. The entities are not persistent: they are respawned from {@link HubPlan#PADS} while the hub is built and
 * every tagged entity this class does not own is removed. Main thread only.
 */
public final class HubNpcs implements Listener {
    /** What a click does. */
    public enum Action { COMMAND, ISLAND, SAY, RIFT }

    /** One NPC of the hub. {@code arg} is the command (without slash) or the line to say. */
    public record Role(String pad, String name, Villager.Profession profession, Action action, String arg) {}

    /** The NPCs, by pad id. */
    public static final List<Role> ROLES = List.of(
        new Role("merchant", "Merchant", Villager.Profession.LIBRARIAN, Action.COMMAND, "shop"),
        new Role("archivist", "Archivist", Villager.Profession.CARTOGRAPHER, Action.COMMAND, "quests"),
        new Role("guard", "Guard", Villager.Profession.WEAPONSMITH, Action.SAY,
            "Welcome to the Hub, survivor. Your island is a fragment of Aethelgard: /island takes you back to it, /hub brings you here. "
                + "The Crypt Depths are east, the Rift is west. The Archivist keeps the Echo Keys."),
        new Role("contract_hunter", "Contract Hunter", Villager.Profession.FLETCHER, Action.COMMAND, "contracts"),
        new Role("island_gate", "Island Gatekeeper", Villager.Profession.NONE, Action.ISLAND, ""),
        new Role("dungeon_gate", "Dungeon Gatekeeper", Villager.Profession.NONE, Action.COMMAND, "dungeon"),
        new Role("rift_gate", "Rift Gatekeeper", Villager.Profession.NONE, Action.RIFT,
            "The Rift is sealed. It opens only for a key cut from a shard that remembers the sky: the Archivist keeps one for "
                + "survivors who wear the Bronze armor."));

    /** The item that opens the Rift (summon item of the Echo of the Rift, see boss.sql). */
    public static final String ECHO_KEY = "echo_key";

    private static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "hub_npc");

    private final Core core;
    private final IslandService service;
    private final Map<String, UUID> live = new HashMap<>();
    /** The name plate (a text display above the villager) by pad id: the vanilla plate does not show in the pack. */
    private final Map<String, UUID> labels = new HashMap<>();
    private Scheduler.Handle task;

    public HubNpcs(Core core, IslandService service) {
        this.core = core;
        this.service = service;
    }

    /** The role standing on a pad, if any. */
    public static Optional<Role> role(String pad) {
        return ROLES.stream().filter(r -> r.pad().equals(pad)).findFirst();
    }

    public void enable() {
        core.listen(this);
        task = core.scheduler().repeating(100, 200, this::refresh);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (String pad : new ArrayList<>(live.keySet())) {
            despawn(pad);
        }
    }

    // ---------------------------------------------------------------- entities

    void refresh() {
        Map<String, Location> pads = service.hubPads();
        if (pads.isEmpty()) {
            return;
        }
        for (Role role : ROLES) {
            Location at = pads.get(role.pad());
            if (at == null) {
                continue;
            }
            try {
                Entity existing = live.containsKey(role.pad()) ? Bukkit.getEntity(live.get(role.pad())) : null;
                if (existing != null && existing.isValid()) {
                    Entity label = labels.containsKey(role.pad()) ? Bukkit.getEntity(labels.get(role.pad())) : null;
                    if (label == null || !label.isValid()) {
                        spawnLabel(role, existing.getLocation());
                    }
                    continue;
                }
                despawn(role.pad());
                live.remove(role.pad());
                int cx = at.getBlockX() >> 4;
                int cz = at.getBlockZ() >> 4;
                if (at.getWorld().isChunkLoaded(cx, cz) && at.getWorld().getChunkAt(cx, cz).isEntitiesLoaded()) {
                    spawn(role, at);
                }
            } catch (RuntimeException e) {
                core.getPlugin().getLogger().log(Level.WARNING, "Hub NPC refresh failed for " + role.pad(), e);
            }
        }
    }

    private void spawn(Role role, Location at) {
        Villager npc = at.getWorld().spawn(at, Villager.class, v -> {
            v.setPersistent(false);
            v.setInvulnerable(true);
            v.customName(Component.text(role.name(), NamedTextColor.GOLD));
            v.setCustomNameVisible(false); // replaced by the text display label (see spawnLabel)
            // island protection skips entities flagged like this (no "you cannot do that" on a click)
            v.setMetadata("NPC", new org.bukkit.metadata.FixedMetadataValue(core.getPlugin(), true));
            v.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, role.pad());
            attempt(() -> v.setSilent(true));
            attempt(() -> v.setRemoveWhenFarAway(false));
            attempt(() -> v.setCollidable(false));
            attempt(() -> v.setAI(false));
            attempt(() -> v.setAware(false));
            attempt(() -> v.setProfession(role.profession()));
            attempt(() -> v.setVillagerLevel(5));
        });
        live.put(role.pad(), npc.getUniqueId());
        spawnLabel(role, at);
    }

    /** The visible name of an NPC: a billboard text display just above its head, the same on every pad. */
    private void spawnLabel(Role role, Location at) {
        UUID old = labels.remove(role.pad());
        Entity previous = old == null ? null : Bukkit.getEntity(old);
        if (previous != null) {
            previous.remove();
        }
        Location above = at.clone().add(0, 2.35, 0);
        TextDisplay label = at.getWorld().spawn(above, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.text(Component.text(role.name(), NamedTextColor.GOLD));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setViewRange(1.5f);
            t.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, role.pad());
        });
        labels.put(role.pad(), label.getUniqueId());
    }

    private static void attempt(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // cosmetic only
        }
    }

    private void despawn(String pad) {
        for (UUID id : new UUID[] {live.remove(pad), labels.remove(pad)}) {
            Entity e = id == null ? null : Bukkit.getEntity(id);
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
            String pad = tagOf(e);
            if (pad != null && !e.getUniqueId().equals(live.get(pad)) && !e.getUniqueId().equals(labels.get(pad))) {
                e.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        String pad = tagOf(event.getRightClicked());
        if (pad == null) {
            return;
        }
        event.setCancelled(true); // never open the vanilla trade screen
        role(pad).ifPresent(role -> click(event.getPlayer(), role));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity && tagOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    void click(Player player, Role role) {
        // quest events A10-A11: "met the Guard / the Archivist / the Merchant" (the pad id is the key)
        fire(player, "hub_npc:" + role.pad());
        switch (role.action()) {
            case COMMAND -> player.performCommand(role.arg());
            case ISLAND -> {
                Result result = service.goHome(player);
                if (result.ok()) {
                    core.msg().ok(player, result.message());
                } else {
                    core.msg().error(player, result.message());
                }
            }
            case SAY -> core.msg().info(player, "[" + role.name() + "] " + role.arg());
            case RIFT -> {
                if (carriesEchoKey(player)) {
                    fire(player, "rift_enter");
                    core.msg().info(player, "[" + role.name() + "] The Rift knows your key. Stand on the glowing altar "
                        + "just inside the arch and right-click the Echo Key to wake the Echo of the Rift.");
                } else {
                    core.msg().info(player, "[" + role.name() + "] " + role.arg());
                }
            }
        }
    }

    private boolean carriesEchoKey(Player player) {
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && ECHO_KEY.equals(core.items().idOf(stack).orElse(null))) {
                return true;
            }
        }
        return false;
    }

    private static void fire(Player player, String key) {
        Bukkit.getPluginManager().callEvent(new win.vampyuyu.survival.api.event.QuarryProgressEvent(player, "EVENT", key, 1));
    }
}
