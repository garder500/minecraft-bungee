package win.vampyuyu.survival.quarry;

import java.util.Optional;
import org.bukkit.Material;

/** The machines of the Quarry. {@code itemId} is the custom item that places them, {@code block} the block they are. */
enum MachineType {
    COAL_GENERATOR("coal_generator", Material.STONECUTTER, "Coal Generator"),
    BATTERY("battery", Material.WAXED_COPPER_BLOCK, "Battery"),
    CABLE("cable", Material.LIGHTNING_ROD, "Copper Cable"),
    SOLAR_PANEL("solar_panel", Material.DAYLIGHT_DETECTOR, "Solar Panel"),
    MINER_1("miner_1", Material.LODESTONE, "Miner I"),
    MINER_2("miner_2", Material.LODESTONE, "Miner II");

    final String itemId;
    final Material block;
    final String title;

    MachineType(String itemId, Material block, String title) {
        this.itemId = itemId;
        this.block = block;
        this.title = title;
    }

    boolean miner() {
        return this == MINER_1 || this == MINER_2;
    }

    static Optional<MachineType> ofItem(String itemId) {
        for (MachineType type : values()) {
            if (type.itemId.equals(itemId)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
