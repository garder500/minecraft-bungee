package win.vampyuyu.survival.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** The dungeon bestiary and the wave plan of a fight room. Pure (no world access), testable. */
public final class MobRoster {
    private MobRoster() {}

    public static final MobSpec ZOMBIE_KNIGHT = new MobSpec("zombie_knight", "Zombie Knight", EntityType.ZOMBIE, 40, 1.0,
        1.0, Material.IRON_SWORD, true, false, 1, 40, "<red>");
    public static final MobSpec SKELETON_SNIPER = new MobSpec("skeleton_sniper", "Skeleton Sniper", EntityType.SKELETON,
        26, 1.2, 1.0, Material.BOW, false, false, 1, 30, "<gray>");
    public static final MobSpec CRYPTIC_WRAITH = new MobSpec("cryptic_wraith", "Cryptic Wraith", EntityType.STRAY, 34, 1.1,
        1.35, Material.STONE_SWORD, false, true, 2, 20, "<aqua>");
    public static final MobSpec STONE_GOLEM = new MobSpec("stone_golem", "Stone Golem", EntityType.IRON_GOLEM, 90, 1.0, 0.8,
        Material.AIR, false, false, 2, 12, "<dark_gray>");
    public static final MobSpec BONE_BRUTE = new MobSpec("bone_brute", "Bone Brute", EntityType.WITHER_SKELETON, 70, 1.3, 1.0,
        Material.STONE_AXE, false, false, 3, 20, "<dark_red>");
    public static final MobSpec CRYPT_WITCH = new MobSpec("crypt_witch", "Crypt Witch", EntityType.WITCH, 46, 1.0, 1.0,
        Material.AIR, false, false, 4, 10, "<dark_purple>");
    public static final MobSpec SHADOW_ASSASSIN = new MobSpec("shadow_assassin", "Shadow Assassin", EntityType.VINDICATOR, 60,
        1.0, 1.25, Material.IRON_AXE, true, false, 5, 14, "<dark_gray>");

    public static final MobSpec WARLORD = new MobSpec("bone_warlord", "Bone Warlord", EntityType.WITHER_SKELETON, 400, 1.6,
        1.05, Material.NETHERITE_SWORD, true, false, 1, 0, "<dark_red>");
    public static final MobSpec WATCHER_GUARD = new MobSpec("watcher_guard", "Watcher Guard", EntityType.ZOMBIE, 36, 0.9, 1.0,
        Material.STONE_SWORD, true, false, 1, 0, "<red>");
    public static final MobSpec CRYPT_LORD = new MobSpec("crypt_lord", "Crypt Lord", EntityType.WITHER_SKELETON, 2500, 2.0,
        1.1, Material.NETHERITE_SWORD, true, false, 1, 0, "<dark_red>");

    private static final List<MobSpec> NORMAL = List.of(ZOMBIE_KNIGHT, SKELETON_SNIPER, CRYPTIC_WRAITH, STONE_GOLEM,
        BONE_BRUTE, CRYPT_WITCH, SHADOW_ASSASSIN);

    /** Normal mobs available on a floor. */
    public static List<MobSpec> forFloor(int floor) {
        List<MobSpec> out = new ArrayList<>();
        for (MobSpec s : NORMAL) {
            if (floor >= s.minFloor()) {
                out.add(s);
            }
        }
        return out;
    }

    public static MobSpec byId(String id) {
        for (MobSpec s : NORMAL) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        for (MobSpec s : List.of(WARLORD, WATCHER_GUARD, CRYPT_LORD)) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static MobSpec pick(int floor, Random rng) {
        List<MobSpec> pool = forFloor(floor);
        int total = 0;
        for (MobSpec s : pool) {
            total += s.weight();
        }
        int r = rng.nextInt(total);
        for (MobSpec s : pool) {
            r -= s.weight();
            if (r < 0) {
                return s;
            }
        }
        return pool.get(0);
    }

    /**
     * Waves of a fight room. Total mobs never exceed {@code maxPerRoom}; a wave never has more than
     * {@code maxPerRoom / waves} mobs so the room stays readable.
     */
    public static List<List<MobSpec>> waves(int floor, int partySize, int maxPerRoom, Random rng) {
        int waves = floor >= 4 ? 3 : 2;
        int perWave = Math.max(2, Math.min(maxPerRoom / waves, 2 + floor + partySize));
        List<List<MobSpec>> out = new ArrayList<>();
        for (int w = 0; w < waves; w++) {
            List<MobSpec> wave = new ArrayList<>();
            for (int i = 0; i < perWave; i++) {
                wave.add(pick(floor, rng));
            }
            out.add(wave);
        }
        return out;
    }

    /** Guards of the Watcher in the entrance room. */
    public static int guards(int floor, int base, int perFloor, int partySize) {
        return Math.max(1, base + perFloor * (floor - 1) + (partySize > 2 ? 1 : 0));
    }

    /** Health multiplier for the party size. */
    public static double partyHealth(int partySize, double scale) {
        return 1.0 + scale * Math.max(0, partySize - 1);
    }

    public static double partyDamage(int partySize, double scale) {
        return 1.0 + scale * Math.max(0, partySize - 1);
    }
}
