package win.vampyuyu.survival.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Scheduler;

/**
 * The custom looks of the bosses. The vanilla mob stays (it keeps the hit box, the movement and the abilities) but is
 * invisible, and an item display with the boss's model from the resource pack follows it every tick. Only our bosses
 * are touched, so the vanilla mobs of the other modes keep their look. See resource-pack/art/boss_skins.py.
 *
 * A boss with a rig (BossRig: its Blockbench bones and animation clips) gets one item display per bone instead, all at the
 * mob's position; every {@code step} ticks each one gets the pose of the clip its BossAnimator picks (idle, walk, attack,
 * hurt, an ability, phase, death), and the client interpolates between two poses. A killed boss keeps its displays in
 * {@link #dying} until its death clip has played out.
 */
final class BossSkinService {
    /** One boss look: the pack item model and the display scale (the model is about 2 blocks tall at scale 1). */
    /**
     * {@code yawOffset}: the model's face looks along the display's yaw, so a mob boss needs 0 (it looks where its body
     * looks). The vanilla dragon's yaw points backwards (measured: 180 degrees from its heading), so the Echo needs 180.
     * Both were swapped before 2026-11-20: every boss walked and flew with its back first.
     */
    record Look(String model, float scale, boolean hide, float yawOffset) {
        Look(String model, float scale) {
            this(model, scale, false, 0f);
        }
    }

    static final Map<String, Look> LOOKS = Map.of(
        // the Ender Dragon ignores invisibility: it is hidden from every client instead, and HiddenBossMelee gives the
        // swings that would have hit it back to the players
        "voidwyrm", new Look("vampyuyu:boss/skin/voidwyrm", 4.2f, true, 180f),
        "magma_warden", new Look("vampyuyu:boss/skin/magma_warden", 2.4f),
        "frost_revenant", new Look("vampyuyu:boss/skin/frost_revenant", 3.6f),
        // the spider's eyes glow through invisibility: it is hidden too
        "sylvan_matriarch", new Look("vampyuyu:boss/skin/sylvan_matriarch", 2.8f, true, 0f),
        "abyssal_leviathan", new Look("vampyuyu:boss/skin/abyssal_leviathan", 2.8f),
        "crypt_lord", new Look("vampyuyu:boss/skin/crypt_lord", 2.4f));

    private static final NamespacedKey MARK = new NamespacedKey("vampyuyu", "boss_skin");

    private final Core core;
    private final BossManager manager;
    /** What follows one boss: its displays (one, or one per bone of its rig), the rig and its animator. */
    private static final class Visual {
        final List<ItemDisplay> displays = new ArrayList<>();
        BossRig rig;
        BossAnimator animator;
        float scale;
        org.bukkit.Location last;
        long deathEnd;
        /** The ground height of a hopping mob (slime, magma cube): its model walks, it does not leap with the mob. */
        double groundY = Double.NaN;

        boolean valid() {
            return !displays.isEmpty() && displays.stream().allMatch(Entity::isValid);
        }

        void remove() {
            displays.forEach(Entity::remove);
            displays.clear();
        }
    }

    private final Map<UUID, Visual> displays = new HashMap<>();
    private final List<Visual> dying = new ArrayList<>();
    private long now;
    /**
     * The boss's name and health over its model. The mob's own name tag sits at the vanilla mob's height, which is inside
     * the model (at the Colossus's ankles, mid-neck on the Leviathan): it is hidden and copied here, over the model's head.
     */
    private final Map<UUID, org.bukkit.entity.TextDisplay> tags = new HashMap<>();
    private Scheduler.Handle task;

    BossSkinService(Core core, BossManager manager) {
        this.core = core;
        this.manager = manager;
    }

    void start() {
        task = core.scheduler().repeating(1, 1, this::tick);
        BossCues.listen(this::cue, this::play);
    }

    /** A cue from the fight (BossCues). The death cue moves the boss's displays to {@link #dying}. */
    void cue(UUID boss, String name) {
        Visual v = displays.get(boss);
        if (v == null || v.animator == null) {
            return;
        }
        if (v.animator.cue(name, now) && name.equals("death")) {
            // the visual stays with its boss and holds the last pose of the clip; once the mob is gone (killed, or
            // despawned) tick() moves it to dying, which keeps it until the clip has played out
            v.deathEnd = now + v.animator.deathLeft(now) + 10;
        }
    }

    /** Plays a clip on every active rigged boss (the /boss anim admin command); returns how many started it. */
    int play(String name) {
        int n = 0;
        for (Map.Entry<UUID, Visual> e : List.copyOf(displays.entrySet())) {
            Visual v = e.getValue();
            if (v.animator != null && v.animator.clipFor(name) != null) {
                cue(e.getKey(), name);
                n++;
            }
        }
        return n;
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        BossCues.listen(null, null);
        displays.values().forEach(Visual::remove);
        displays.clear();
        dying.forEach(Visual::remove);
        dying.clear();
        tags.values().forEach(Entity::remove);
        tags.clear();
    }

    int showing() {
        return displays.size();
    }

    private void tick() {
        now++;
        for (BossSession session : manager.active()) {
            LivingEntity boss = session.entity();
            Look look = LOOKS.get(session.definition().id());
            if (boss == null || !boss.isValid() || look == null) {
                continue;
            }
            Visual visual = displays.get(boss.getUniqueId());
            if (visual == null || !visual.valid()) {
                if (visual != null) {
                    visual.remove();
                }
                visual = create(boss, look, session.definition().id());
                displays.put(boss.getUniqueId(), visual);
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
            if (boss instanceof org.bukkit.entity.Slime) {
                if (boss.isOnGround() || Double.isNaN(visual.groundY) || at.getY() < visual.groundY) {
                    visual.groundY = at.getY();
                }
                at.setY(visual.groundY);   // the mob hops, the colossus on top of it stays on its feet
            }
            for (ItemDisplay d : visual.displays) {
                d.teleport(at);
            }
            animate(visual, at);
            org.bukkit.entity.TextDisplay tag = tags.get(boss.getUniqueId());
            if (tag == null || !tag.isValid()) {
                tag = createTag(boss);
                tags.put(boss.getUniqueId(), tag);
            }
            boss.setCustomNameVisible(false);
            if (boss.customName() != null && !boss.customName().equals(tag.text())) {
                tag.text(boss.customName());
            }
            tag.teleport(boss.getLocation().add(0, tagHeight(look), 0));
        }
        dying.removeIf(v -> {
            if (now >= v.deathEnd || !v.valid()) {
                v.remove();
                return true;
            }
            animate(v, null);
            return false;
        });
        tags.entrySet().removeIf(entry -> {
            Entity boss = core.getPlugin().getServer().getEntity(entry.getKey());
            if (boss == null || !boss.isValid()) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
        Iterator<Map.Entry<UUID, Visual>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity boss = core.getPlugin().getServer().getEntity(entry.getKey());
            if (boss == null || !boss.isValid()) {
                Visual v = entry.getValue();
                it.remove();
                if (v.animator != null && v.animator.clipFor("death") != null && !v.animator.dying()) {
                    v.animator.cue("death", now);   // gone without the kill cue (despawn, entity lost): still fall
                    v.deathEnd = now + v.animator.deathLeft(now) + 10;
                    dying.add(v);
                } else if (v.animator != null && v.animator.dying()) {
                    v.deathEnd = Math.max(v.deathEnd, now + v.animator.deathLeft(now) + 10);
                    dying.add(v);
                } else {
                    v.remove();
                }
            }
        }
    }

    /** Every step ticks: the base clip follows the movement, each bone display gets its pose, interpolated by the client. */
    private void animate(Visual v, org.bukkit.Location at) {
        if (v.animator == null) {
            return;
        }
        if (at != null) {
            if (v.last != null && v.last.getWorld() == at.getWorld()) {
                double dx = at.getX() - v.last.getX(), dz = at.getZ() - v.last.getZ();
                v.animator.moving(dx * dx + dz * dz > 0.0016, now);   // over 0.04 block a tick
            }
            v.last = at.clone();
        }
        BossRig.Clip any = v.rig.clips.values().iterator().next();
        int step = Math.max(1, any.step());
        if (now % step != 0) {
            return;
        }
        float[][] poses = v.animator.poses(now);
        for (int i = 0; i < v.displays.size() && i < poses.length; i++) {
            ItemDisplay d = v.displays.get(i);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(step);
            d.setTransformation(BossRig.transform(poses[i], v.scale));
        }
    }

    /** Height of the name over the mob's feet: the models are about two blocks tall at scale 1 (32 units). */
    static double tagHeight(Look look) {
        return 2.0 * look.scale() + 0.25;
    }

    private org.bukkit.entity.TextDisplay createTag(LivingEntity boss) {
        return boss.getWorld().spawn(boss.getLocation(), org.bukkit.entity.TextDisplay.class, t -> {
            t.setBillboard(Display.Billboard.CENTER);
            if (boss.customName() != null) {
                t.text(boss.customName());
            }
            t.setTeleportDuration(2);
            t.setViewRange(2f);
            t.setShadowed(true);
            t.setPersistent(false);
            t.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private Visual create(LivingEntity boss, Look look, String bossId) {
        Visual v = new Visual();
        v.scale = look.scale();
        BossRig rig = BossRig.of(bossId).orElse(null);
        if (rig == null) {
            v.displays.add(display(boss, look, look.model()));
            return v;
        }
        v.rig = rig;
        v.animator = new BossAnimator(rig, now);
        for (BossRig.Bone bone : rig.bones) {
            v.displays.add(display(boss, look, bone.model()));
        }
        return v;
    }

    private ItemDisplay display(LivingEntity boss, Look look, String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(model));
        stack.setItemMeta(meta);
        return boss.getWorld().spawn(boss.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            // the model's centre is the display's position: lift it so its feet stand where the mob's feet are
            d.setTransformation(BossRig.transform(BossRig.REST, look.scale()));
            d.setTeleportDuration(2);
            d.setViewRange(4f);
            d.setBrightness(new Display.Brightness(12, 12));
            d.setPersistent(false);
            d.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1);
        });
    }
}
