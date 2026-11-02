package win.vampyuyu.survival.station;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.api.StationApi;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;
import win.vampyuyu.survival.core.SkyblockWorlds;

/**
 * Alembic, Crucible, Ranch and the Contract Hunter's contracts (GDD 10.1, 17, 22). Needs no other feature; it uses the
 * quarry's generator tier, the economy, the resonance Shards and the boss kills when they are loaded. See
 * docs/skyblock-notes/stations.md.
 */
public final class StationFeature implements Feature {
    private final List<NamespacedKey> recipes = new ArrayList<>();
    private StationService service;

    @Override
    public String id() {
        return "stations";
    }

    @Override
    public void enable(Core core) {
        StationConfig config = StationConfig.load(core.data());
        config.problems().forEach(p -> core.getPlugin().getLogger().warning("station data: " + p));
        StationItems.register(core.items());
        service = new StationService(core, config);
        StationGui gui = new StationGui(core, service);
        core.listen(new StationListener(core, service, gui));
        command(core, "alembic", "Open the Alembic of your island", p -> gui.alembic(p), true);
        command(core, "crucible", "Open the Crucible of your island", p -> gui.crucible(p), true);
        command(core, "ranch", "Open the Ranch of your island", p -> gui.ranch(p), true);
        command(core, "contracts", "The Contract Hunter's board", p -> gui.contracts(p), false);
        addRecipes(core);
        core.register(StationApi.class, service);
        core.register(StationService.class, service);
    }

    /** {@code needsIsland}: the three stations are used on the island; the contract board works anywhere (the Hub NPC runs it). */
    private void command(Core core, String name, String description, java.util.function.Consumer<Player> open, boolean needsIsland) {
        core.commands().command(name).description(description).usage("/" + name)
            .permission("vampyuyu.sb.station.use", PermissionDefault.TRUE)
            .executor((CommandSender sender, org.bukkit.command.Command command, String label, String[] args) -> {
                if (!(sender instanceof Player player)) {
                    core.msg().error(sender, "Players only.");
                } else if (!service.config().enabled) {
                    core.msg().warn(player, "The stations are switched off.");
                } else if (needsIsland && !SkyblockWorlds.WORLD.equals(player.getWorld().getName())
                        && !SkyblockWorlds.NETHER.equals(player.getWorld().getName())) {
                    core.msg().warn(player, "Go to your island first: the " + name + " works there.");
                } else {
                    open.accept(player);
                }
                return true;
            }).register();
    }

    /** Crafting-table recipes of the three stations (vanilla ingredients; the stations only work on an island). */
    private void addRecipes(Core core) {
        shapeless(core, StationItems.ALEMBIC, Material.BREWING_STAND, Material.COPPER_INGOT, 3, Material.GLASS_BOTTLE, 3);
        shapeless(core, StationItems.CRUCIBLE, Material.CAULDRON, Material.IRON_INGOT, 4, Material.BLAZE_POWDER, 2);
        shapeless(core, StationItems.RANCH, Material.COMPOSTER, Material.HAY_BLOCK, 2, Material.LEATHER, 4);
    }

    private void shapeless(Core core, String item, Material base, Material a, int na, Material b, int nb) {
        NamespacedKey key = new NamespacedKey(core.getPlugin(), "station_" + item);
        ShapelessRecipe recipe = new ShapelessRecipe(key, core.items().create(item));
        recipe.addIngredient(base);
        recipe.addIngredient(na, a);
        recipe.addIngredient(nb, b);
        Bukkit.addRecipe(recipe);
        recipes.add(key);
    }

    @Override
    public void disable() {
        recipes.forEach(Bukkit::removeRecipe);
        recipes.clear();
        service = null;
    }

    /** For tests. */
    public StationService service() { return service; }
}
