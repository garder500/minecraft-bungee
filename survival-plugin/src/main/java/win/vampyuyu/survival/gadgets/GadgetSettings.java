package win.vampyuyu.survival.gadgets;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import win.vampyuyu.survival.data.Settings;

/** Snapshot of the {@code gadgets.*} settings; every value has a default so a partial table still works. */
public record GadgetSettings(
        int skyhookRange, long skyhookCooldownMs, double skyhookSpeed, long skyhookFallGraceMs,
        int anchorWarmupTicks, long anchorCooldownMs,
        int voidNetCharges, int voidNetIslandDepth, int voidNetMargin, double voidNetItemRadius,
        int totemSeconds, int totemRadius, int totemIntervalTicks, double totemChance,
        Set<Material> satchelOverflow) {

    public static GadgetSettings from(Settings s) {
        Set<Material> overflow = EnumSet.noneOf(Material.class);
        for (String name : s.getList("satchel.overflow")) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m != null) {
                overflow.add(m);
            }
        }
        return new GadgetSettings(
            Math.max(1, s.getInt("skyhook.range", 20)),
            Math.max(0, s.getInt("skyhook.cooldown-seconds", 4)) * 1000L,
            Math.max(0.1, s.getDouble("skyhook.speed", 0.12)),
            Math.max(0, s.getInt("skyhook.fall-grace-seconds", 3)) * 1000L,
            Math.max(0, s.getInt("cloud-anchor.warmup-seconds", 2)) * 20,
            Math.max(0, s.getInt("cloud-anchor.cooldown-seconds", 60)) * 1000L,
            Math.max(1, s.getInt("void-net.charges", 5)),
            Math.max(0, s.getInt("void-net.island-depth", 12)),
            Math.max(0, s.getInt("void-net.margin", 10)),
            Math.max(1, s.getDouble("void-net.item-radius", 8)),
            Math.max(1, s.getInt("rain-totem.seconds", 60)),
            Math.max(1, s.getInt("rain-totem.radius", 7)),
            Math.max(1, s.getInt("rain-totem.interval-ticks", 40)),
            Math.max(0, Math.min(1, s.getDouble("rain-totem.growth-chance", 0.5))),
            overflow);
    }
}
