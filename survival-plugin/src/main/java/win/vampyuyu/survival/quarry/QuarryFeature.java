package win.vampyuyu.survival.quarry;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.permissions.PermissionDefault;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.Feature;

/**
 * The Quarry: generator ore draw, custom ores, the Forge and the first gear. Everything is limited to the Skyblock
 * overworld and Nether. See docs/skyblock-notes/quarry.md.
 */
public final class QuarryFeature implements Feature {
    private Core core;
    private List<String> itemIds = List.of();
    private NamespacedKey recipeKey;
    private MachineStore machineStore;
    private win.vampyuyu.survival.core.Scheduler.Handle machineTask;

    @Override
    public String id() {
        return "quarry";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        QuarryConfig config = QuarryConfig.parse(core.featureConfig("quarry"), core.getPlugin().getLogger());
        itemIds = QuarryItems.register(core.items());
        QuarryService service = new QuarryService(core, config);
        ForgeGui gui = new ForgeGui(core, service);
        core.listen(new CobbleListener(core, service));
        core.listen(new ForgeListener(core, service, gui));
        core.listen(new WeaponListener(core));
        SkinService skins = new SkinService(core, service);
        SkinGui skinGui = new SkinGui(core, skins);
        core.listen(new SkinListener(core, skins));
        core.register(SkinService.class, skins);
        core.permissions().register("vampyuyu.sb.quarry.admin", "Administer the Quarry (skins)", PermissionDefault.OP);
        core.commands().command("skins")
            .description("Your sword skins")
            .usage("/skins [unlock <player> <skin>]")
            .permission("vampyuyu.sb.quarry.use", PermissionDefault.TRUE)
            .executor((CommandSender sender, org.bukkit.command.Command command, String label, String[] args) -> {
                if (args.length == 3 && args[0].equalsIgnoreCase("unlock")) {
                    if (!sender.hasPermission("vampyuyu.sb.quarry.admin")) {
                        core.msg().error(sender, "You cannot do that.");
                        return true;
                    }
                    Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                    var skin = Skin.byId(args[2].toLowerCase(java.util.Locale.ROOT));
                    if (target == null || skin.isEmpty()) {
                        core.msg().error(sender, "Usage: /skins unlock <online player> <skin id>");
                    } else {
                        core.msg().ok(sender, skins.unlock(target.getUniqueId(), skin.get()) ? "Skin unlocked." : "Already unlocked.");
                    }
                    return true;
                }
                if (sender instanceof Player player) {
                    if (CobbleListener.skyblock(player.getLocation().getBlock())) {
                        skinGui.open(player);
                    } else {
                        core.msg().warn(player, "Sword skins work in your Skyblock island.");
                    }
                } else {
                    core.msg().error(sender, "Only players can use /skins.");
                }
                return true;
            }).register();
        core.listen(new QuarryGuard(core));
        machineStore = new MachineStore(core.store(), core.getPlugin().getLogger());
        MachineService machines = new MachineService(service, machineStore);
        core.listen(new MachineListener(core, machines));
        machineTask = core.scheduler().repeating(20, 20, machines::tick);
        addForgeRecipe(config);
        core.commands().command("forge")
            .description("Open the Forge")
            .usage("/forge")
            .permission("vampyuyu.sb.quarry.use", PermissionDefault.TRUE)
            .executor((CommandSender sender, org.bukkit.command.Command command, String label, String[] args) -> {
                if (sender instanceof Player player) {
                    String world = player.getWorld().getName();
                    if (CobbleListener.skyblock(player.getLocation().getBlock())) {
                        gui.open(player);
                    } else {
                        core.msg().warn(player, "The Forge only works on your Skyblock island. World: " + world);
                    }
                } else {
                    core.msg().error(sender, "Only players can use /forge.");
                }
                return true;
            }).register();
        core.register(QuarryService.class, service);
        core.register(win.vampyuyu.survival.api.EndAccessApi.class, service);
        core.register(MachineService.class, machines);
    }

    private void addForgeRecipe(QuarryConfig config) {
        if (config.forgeBlockRecipe.isEmpty()) {
            return;
        }
        recipeKey = new NamespacedKey(core.getPlugin(), "forge_block");
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, core.items().create(QuarryItems.FORGE));
        int total = 0;
        for (var entry : config.forgeBlockRecipe.entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null || entry.getKey().startsWith("custom:")) {
                core.debug("quarry: forge recipe ingredient skipped: " + entry.getKey());
                continue;
            }
            total += entry.getValue();
            if (total > 9) {
                core.getPlugin().getLogger().warning("quarry.yml: forge.recipe has more than 9 ingredients; recipe not added");
                return;
            }
            recipe.addIngredient(entry.getValue(), material);
        }
        Bukkit.addRecipe(recipe);
    }

    @Override
    public void disable() {
        if (machineTask != null) {
            machineTask.cancel();
        }
        if (machineStore != null) {
            machineStore.flush();
        }
        if (recipeKey != null) {
            Bukkit.removeRecipe(recipeKey);
        }
        if (core != null) {
            itemIds.forEach(core.items()::unregister);
        }
    }
}
