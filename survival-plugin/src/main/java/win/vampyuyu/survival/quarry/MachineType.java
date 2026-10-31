package win.vampyuyu.survival.quarry;

import java.util.Optional;
import org.bukkit.Material;

/** The machines of the Quarry. {@code itemId} is the custom item that places them, {@code block} the block they are. */
enum MachineType {
    COAL_GENERATOR("coal_generator", Material.STONECUTTER, "Coal Generator"),
    BATTERY("battery", Material.WAXED_COPPER_BLOCK, "Battery"),
    CABLE("cable", Material.REDSTONE_WIRE, "Copper Cable"),
    SOLAR_PANEL("solar_panel", Material.DAYLIGHT_DETECTOR, "Solar Panel"),
    MINER_1("miner_1", Material.LODESTONE, "Miner I"),
    MINER_2("miner_2", Material.LODESTONE, "Miner II"),
    /** Makes cobblestone above itself; the centre of every multi-block generator. No energy. */
    FOUNTAIN("fountain", Material.SMOOTH_STONE, "Fountain"),
    /** An infinite water source: fill a bucket from it. The water cell of the multi-block generators. */
    SPRING_STONE("spring_stone", Material.PRISMARINE_BRICKS, "Spring Stone");

    final String itemId;
    final Material block;
    final String title;

    MachineType(String itemId, Material block, String title) {
        this.itemId = itemId;
        this.block = block;
        this.title = title;
    }

    /** Fountains and Spring Stones are not part of the Flux networks. */
    boolean passive() {
        return this == FOUNTAIN || this == SPRING_STONE;
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
