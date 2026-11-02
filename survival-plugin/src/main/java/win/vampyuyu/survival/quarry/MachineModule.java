package win.vampyuyu.survival.quarry;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A Realm module (GDD 7.5): an item slotted into a Miner that trades speed for efficiency and adds one function. It is
 * a sidegrade: nothing here makes Flux for free, and a Miner holds a single module. Each Realm adds its own constants.
 */
enum MachineModule {
    /** Cracked Ashes, thermal: faster and hungrier, and the Miner smelts what it breaks. */
    EMBER("ember_module", "Ember Module", "ember"),
    /** Whispering Grove, biological: slower and thrifty, and the Miner sometimes gathers bone meal. */
    BLOOM("bloom_module", "Bloom Module", "bloom"),
    /** Ethereal Necropolis, spiritual: slower and hungrier, and the Miner sometimes distils a Memory Shard. */
    MEMORY("memory_module", "Memory Module", "memory"),
    /** Frozen Heights, advanced cooling: a cooled Miner runs faster for the same Flux per block, and gathers packed ice. */
    CRYO("cryo_module", "Cryo Module", "cryo"),
    /** Putrid Marshes, industrial: thrifty and a little slower, and the Miner sometimes recycles its residue into a second copy. */
    RECYCLER("recycler_module", "Recycler Module", "recycler"),
    /** Citadel of Golden Sands, temporal: follows the Time Tide and banks the cycles it cannot run (a buffer). */
    CHRONO("chrono_module", "Chrono Module", "chrono");

    final String itemId;
    final String title;
    /** Settings key: {@code quarry.machines.module.<key>.*}. */
    final String key;

    MachineModule(String itemId, String title, String key) {
        this.itemId = itemId;
        this.title = title;
        this.key = key;
    }

    static Optional<MachineModule> ofItem(String itemId) {
        for (MachineModule module : values()) {
            if (module.itemId.equals(itemId)) {
                return Optional.of(module);
            }
        }
        return Optional.empty();
    }

    /** The module stored on a machine, or null when none (or an unknown name). */
    static MachineModule of(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** What an Ember module's smelting makes of a mined block; the same item when it does not smelt. */
    static ItemStack smelt(ItemStack stack) {
        Material out = switch (stack.getType()) {
            case COBBLESTONE -> Material.STONE;
            case SAND, RED_SAND -> Material.GLASS;
            case NETHERRACK -> Material.NETHER_BRICK;
            default -> stack.getType();
        };
        if (out == stack.getType()) {
            return stack;
        }
        return stack.getAmount() == 1 ? new ItemStack(out) : new ItemStack(out, stack.getAmount());
    }

    /** Packed ice a Cryo module gathers with one cycle (0 most of the time). */
    static int cryoBonus(double chance, Random random) {
        return random.nextDouble() < chance ? 1 : 0;
    }

    /** Memory Shards a Memory module distils with one cycle (0 most of the time). */
    static int memoryShards(double chance, Random random) {
        return random.nextDouble() < chance ? 1 : 0;
    }

    /** True when a Recycler's cycle gives back a second copy of what it mined. */
    static boolean salvages(double chance, Random random) {
        return random.nextDouble() < chance;
    }

    /**
     * The Chrono Module's buffer (GDD 7.5, "buffers temporels"): a Miner that cannot run (no Flux, full chest, a stalled
     * tide) banks the cycles it lost, scaled by {@code bankFraction}, up to {@code cap}; a Miner that runs pays them back
     * as extra cycles, at most {@code release} per second, with no extra Flux. Returns the new buffer.
     */
    static double bank(double buffer, double lostCycles, double bankFraction, double cap) {
        return Math.min(Math.max(0, cap), Math.max(0, buffer) + Math.max(0, lostCycles) * Math.max(0, bankFraction));
    }

    /** Cycles a running Chrono Miner takes out of its buffer this second. */
    static double release(double buffer, double release) {
        return Math.min(Math.max(0, buffer), Math.max(0, release));
    }

    /** Bone meal a Bloom module gathers with one cycle (0 most of the time). */
    static int bloomBonus(double chance, Random random) {
        return random.nextDouble() < chance ? 1 : 0;
    }
}
