package win.vampyuyu.survival.menu;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import win.vampyuyu.survival.menu.SoulLogic.SoulSpot;

/** The entity side of a Fairy Soul, behind an interface so the scanning logic can be tested without a real server. */
interface SoulDisplays {
    /** One spawned soul. */
    interface Handle {
        boolean valid();

        void remove();

        void show(Player player);

        void hide(Player player);

        Location location();
    }

    /** True when the soul's chunk is loaded (souls are never spawned into unloaded chunks). */
    boolean chunkLoaded(World world, SoulSpot spot);

    /** Spawns the soul, hidden from everybody. */
    Handle spawn(World world, SoulSpot spot);

    /** Real implementation: a non-persistent glowing {@link ItemDisplay}. */
    final class Bukkit implements SoulDisplays {
        private final Plugin plugin;
        private final NamespacedKey tag;
        private final java.util.function.Supplier<MenuConfig> config;

        Bukkit(Plugin plugin, NamespacedKey tag, java.util.function.Supplier<MenuConfig> config) {
            this.plugin = plugin;
            this.tag = tag;
            this.config = config;
        }

        @Override
        public boolean chunkLoaded(World world, SoulSpot spot) {
            return world.isChunkLoaded((int) Math.floor(spot.x()) >> 4, (int) Math.floor(spot.z()) >> 4);
        }

        @Override
        public Handle spawn(World world, SoulSpot spot) {
            Color glow = parseColor(config.get().fairyGlow);
            ItemStack stack = new ItemStack(Material.PINK_DYE);
            stack.editMeta(meta -> meta.setItemModel(NamespacedKey.fromString("vampyuyu:menu/fairy_soul")));
            ItemDisplay display = world.spawn(new Location(world, spot.x(), spot.y(), spot.z()), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.setItemStack(stack);
                e.setBillboard(Display.Billboard.CENTER);
                e.setGlowing(true);
                e.setGlowColorOverride(glow);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(0.6f);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
                e.setInvulnerable(true);
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
            return new Handle() {
                @Override public boolean valid() { return display.isValid(); }
                @Override public void remove() { display.remove(); }
                @Override public void show(Player player) { player.showEntity(plugin, display); }
                @Override public void hide(Player player) { player.hideEntity(plugin, display); }
                @Override public Location location() { return display.getLocation(); }
            };
        }

        static Color parseColor(String hex) {
            try {
                String clean = hex.startsWith("#") ? hex.substring(1) : hex;
                int rgb = Integer.parseInt(clean, 16);
                return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            } catch (RuntimeException e) {
                return Color.fromRGB(0xFF, 0x7A, 0xD9);
            }
        }
    }
}
