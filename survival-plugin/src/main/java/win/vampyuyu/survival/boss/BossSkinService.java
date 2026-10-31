package win.vampyuyu.survival.boss;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The custom looks of the bosses. The vanilla mob stays (it keeps the hit box, the movement and the abilities) but is
 * invisible, and an item display with the boss's model from the resource pack follows it every tick. Only our bosses
 * are touched, so the vanilla mobs of the other modes keep their look. See resource-pack/art/boss_skins.py.
 */
final class BossSkinService {
    /** One boss look: the pack item model and the display scale (the model is about 2 blocks tall at scale 1). */
    record Look(String model, float scale, boolean hide, float yawOffset) {
        Look(String model, float scale) {
            this(model, scale, false, 180f);
        }
    }

    static final Map<String, Look> LOOKS = Map.of(
        // the Ender Dragon ignores invisibility: it is hidden from every client instead, and HiddenBossMelee gives the
        // swings that would have hit it back to the players
        "voidwyrm", new Look("vampyuyu:boss/skin/voidwyrm", 4.2f, true, 0f),
        "magma_warden", new Look("vampyuyu:boss/skin/magma_warden", 2.4f),
        "frost_revenant", new Look("vampyuyu:boss/skin/frost_revenant", 3.6f),
        "sylvan_matriarch", new Look("vampyuyu:boss/skin/sylvan_matriarch", 2.8f),
        "abyssal_leviathan", new Look("vampyuyu:boss/skin/abyssal_leviathan", 2.8f),
        "crypt_lord", new Look("vampyuyu:boss/skin/crypt_lord", 2.4f));

    private static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "boss_skin");

    private final Core core;
    private final BossManager manager;
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();
    private Scheduler.Handle task;

    BossSkinService(Core core, BossManager manager) {
        this.core = core;
        this.manager = manager;
    }

    void start() {
        task = core.scheduler().repeating(1, 1, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        displays.values().forEach(Entity::remove);
        displays.clear();
    }

    int showing() {
        return displays.size();
    }

    private void tick() {
        for (BossSession session : manager.active()) {
            LivingEntity boss = session.entity();
            Look look = LOOKS.get(session.definition().id());
            if (boss == null || !boss.isValid() || look == null) {
                continue;
            }
            ItemDisplay display = displays.get(boss.getUniqueId());
            if (display == null || !display.isValid()) {
                display = create(boss, look);
                displays.put(boss.getUniqueId(), display);
                if (look.hide()) {
                    boss.setVisibleByDefault(false);
                } else {
                    boss.setInvisible(true);
                }
                if (boss.getEquipment() != null) {
                    boss.getEquipment().clear(); // an invisible zombie would still show its sword and armour
                }
            }
            var at = boss.getLocation();
            at.setYaw(boss.getBodyYaw() + look.yawOffset());
            at.setPitch(0f);
            display.teleport(at);
        }
        Iterator<Map.Entry<UUID, ItemDisplay>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity boss = core.getPlugin().getServer().getEntity(entry.getKey());
            if (boss == null || !boss.isValid()) {
                entry.getValue().remove();
                it.remove();
            }
        }
    }

    private ItemDisplay create(LivingEntity boss, Look look) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(look.model()));
        stack.setItemMeta(meta);
        return boss.getWorld().spawn(boss.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // the model's centre is the display's position: lift it so its feet stand where the mob's feet are
            d.setTransformation(new Transformation(new Vector3f(0f, 0.5f * look.scale(), 0f), new AxisAngle4f(),
                new Vector3f(look.scale(), look.scale(), look.scale()), new AxisAngle4f()));
            d.setTeleportDuration(2);
            d.setViewRange(4f);
            d.setBrightness(new Display.Brightness(12, 12));
            d.setPersistent(false);
            d.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
        });
    }
}
