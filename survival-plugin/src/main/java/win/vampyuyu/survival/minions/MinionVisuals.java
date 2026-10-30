package win.vampyuyu.survival.minions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;
import win.vampyuyu.survival.minions.MinionConfig.Skin;
import win.vampyuyu.survival.minions.MinionConfig.Type;

/**
 * The body of a minion: a small {@link Mannequin} (player model with a hat, a chestplate by tier and its tool in
 * hand) plus a floating {@link TextDisplay} label. Both are non persistent and tagged, and are spawned lazily when
 * the chunk is loaded: the saved data is the only truth, so a chunk save can never duplicate a minion.
 */
final class MinionVisuals {
    /** PDC key on the body and the label: the id of the minion they belong to. */
    static final NamespacedKey TAG = new NamespacedKey("vampyuyu", "minion_id");

    private record Handle(@Nullable Mannequin body, @Nullable TextDisplay label) {}

    private final MinionConfig config;
    private final Logger log;
    private final Map<String, Handle> handles = new HashMap<>();
    private boolean warned;

    MinionVisuals(MinionConfig config, Logger log) {
        this.config = config;
        this.log = log;
    }

    /** True when the minion's body and label are alive. */
    boolean present(String id) {
        Handle h = handles.get(id);
        return h != null && h.body() != null && h.body().isValid();
    }

    /** Spawns the body and label when they are missing (a no-op when they are alive). */
    void ensure(World world, MinionData d, Type type, Component label) {
        if (present(d.id)) {
            return;
        }
        remove(d.id);
        try {
            Location at = new Location(world, d.x + 0.5, d.y, d.z + 0.5, d.yaw, 0);
            Mannequin body = world.spawn(at, Mannequin.class, m -> {
                m.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, d.id);
                m.setPersistent(false);
                m.setImmovable(true);
                m.setInvulnerable(true);
                m.setAI(false);
                m.setSilent(true);
                m.setGravity(false);
                m.setCollidable(false);
                m.setCustomNameVisible(false);
                m.setDescription(Component.empty());
                m.setProfile(Mannequin.defaultProfile());
                AttributeInstance scale = m.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(config.scale);
                }
                dress(m, d, type);
            });
            double height = 1.8 * config.scale + 0.45;
            TextDisplay text = world.spawn(at.clone().add(0, height, 0), TextDisplay.class, t -> {
                t.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, d.id);
                t.setPersistent(false);
                t.setBillboard(Display.Billboard.CENTER);
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.setShadowed(true);
                t.setSeeThrough(false);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
                t.setViewRange(0.6f);
                t.text(label);
            });
            handles.put(d.id, new Handle(body, text));
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.log(Level.WARNING, "minions: could not spawn a minion body (" + e + "); minions keep working without it");
            }
        }
    }

    /** Puts the current hat, tier chestplate and tool on the body. */
    void dress(Mannequin body, MinionData d, Type type) {
        EntityEquipment eq = body.getEquipment();
        Skin skin = d.skin == null ? null : config.skins.get(d.skin);
        eq.setHelmet(new ItemStack(skin != null ? skin.head() : type.hat()));
        eq.setChestplate(new ItemStack(config.chestplate[MinionConfig.clampTier(d.tier) - 1]));
        eq.setItemInMainHand(new ItemStack(type.hand()));
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.HAND}) {
            eq.setDropChance(slot, 0f);
        }
    }

    /** Redresses the body after a tier or hat change. */
    void refresh(MinionData d, Type type) {
        Handle h = handles.get(d.id);
        if (h != null && h.body() != null && h.body().isValid()) {
            dress(h.body(), d, type);
        }
    }

    void setLabel(String id, Component text) {
        Handle h = handles.get(id);
        if (h != null && h.label() != null && h.label().isValid()) {
            h.label().text(text);
        }
    }

    void swing(String id) {
        Handle h = handles.get(id);
        if (h != null && h.body() != null && h.body().isValid()) {
            h.body().swingMainHand();
        }
    }

    /** Where the body stands (for effects), null when it is not spawned. */
    @Nullable Location locationOf(String id) {
        Handle h = handles.get(id);
        return h != null && h.body() != null ? h.body().getLocation() : null;
    }

    void remove(String id) {
        Handle h = handles.remove(id);
        if (h != null) {
            if (h.body() != null) {
                h.body().remove();
            }
            if (h.label() != null) {
                h.label().remove();
            }
        }
    }

    void removeAll() {
        for (String id : new java.util.ArrayList<>(handles.keySet())) {
            remove(id);
        }
    }

    /** The minion id a body or label belongs to. */
    static Optional<String> minionIdOf(@Nullable Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getPersistentDataContainer().get(TAG, PersistentDataType.STRING));
    }

    /** Whether an entity is a minion mob prop (see {@link Terrain#MOB_TAG}). */
    static boolean isMobProp(@Nullable Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(Terrain.MOB_TAG, PersistentDataType.STRING);
    }

    int count() {
        return handles.size();
    }
}
