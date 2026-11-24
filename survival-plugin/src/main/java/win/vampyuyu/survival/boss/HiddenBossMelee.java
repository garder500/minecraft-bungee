package win.vampyuyu.survival.boss;

import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.ComplexLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * A boss that is hidden from the clients (the Voidwyrm and the Mycelial Queen: their vanilla dragon and spider would
 * still show through the custom model, the dragon because it ignores invisibility, the spider by its glowing eyes) cannot be
 * targeted by the client, so a melee swing that would have landed on one of its parts is turned into a real attack here.
 * Arrows and spells already hit the server-side hit box.
 */
final class HiddenBossMelee implements Listener {
    private static final double REACH = 6.0;
    private static final double RANGE_SQUARED = 60.0 * 60.0;

    private final BossManager manager;

    HiddenBossMelee(BossManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getAttackCooldown() < 0.9f) {
            return;
        }
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = player.getEyeLocation().getDirection();
        for (BossSession session : manager.active()) {
            LivingEntity boss = session.entity();
            if (boss == null || boss.isVisibleByDefault() || boss.getWorld() != player.getWorld()
                    || boss.getLocation().distanceSquared(player.getLocation()) > RANGE_SQUARED) {
                continue;
            }
            if (!(boss instanceof ComplexLivingEntity complex)) {
                // a hidden plain mob (the Mycelial Queen's spider: its eyes glow even when invisible) is hit on its box
                if (boss.getBoundingBox().expand(0.2).rayTrace(eye, direction, 3.5) != null) {
                    player.attack(boss);
                    return;
                }
                continue;
            }
            for (ComplexEntityPart part : complex.getParts()) {
                RayTraceResult hit = part.getBoundingBox().rayTrace(eye, direction, REACH);
                if (hit != null) {
                    player.attack(part);
                    return;
                }
            }
        }
    }
}
