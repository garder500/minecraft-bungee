package win.vampyuyu.survival.skills;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import win.vampyuyu.survival.api.Skill;

/** Fishing, enchanting and brewing events driven straight into the feature's listeners. */
public class ActivityEventsTest extends SkillsBase {
    private ActivitySources sources() {
        return new ActivitySources(core, service);
    }

    @Test
    public void fishCatchesGiveFishingExperienceAndCollections() {
        Item caught = world.dropItem(new Location(world, 0, 64, 0), new ItemStack(Material.SALMON, 2));
        sources().onFish(new PlayerFishEvent(player, caught, null, PlayerFishEvent.State.CAUGHT_FISH));
        assertEquals(20, service.xp(player.getUniqueId(), Skill.FISHING), 1e-9);
        assertEquals(2, service.data(player.getUniqueId()).collected("SALMON"));
        // a bite that was not a catch gives nothing
        sources().onFish(new PlayerFishEvent(player, caught, null, PlayerFishEvent.State.BITE));
        assertEquals(20, service.xp(player.getUniqueId(), Skill.FISHING), 1e-9);
    }

    @Test
    public void enchantingTableGivesExperienceFromCostAndLevels() {
        Block table = world.getBlockAt(0, 64, 0);
        table.setType(Material.ENCHANTING_TABLE);
        EnchantItemEvent event = new EnchantItemEvent(player, player.getOpenInventory(), table,
            new ItemStack(Material.DIAMOND_SWORD), 2, Map.of(Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 1),
            Enchantment.SHARPNESS, 3, 1);
        sources().onEnchant(event);
        // cost 2 * 3 + (3 + 1) * 4
        assertEquals(22, service.xp(player.getUniqueId(), Skill.ENCHANTING), 1e-9);
    }

    @Test
    public void brewingGivesAlchemyExperienceToTheLastPlayerWhoOpenedTheStand() {
        Block stand = world.getBlockAt(5, 64, 5);
        stand.setType(Material.BREWING_STAND);
        BrewerInventory inventory = (BrewerInventory) server.createInventory(null, InventoryType.BREWING);
        inventory.setItem(0, new ItemStack(Material.POTION));
        inventory.setItem(1, new ItemStack(Material.POTION));
        inventory.setIngredient(new ItemStack(Material.NETHER_WART));
        ItemStack changed = new ItemStack(Material.SPLASH_POTION);
        List<ItemStack> results = List.of(changed, changed, new ItemStack(Material.AIR));
        ActivitySources sources = sources();
        sources.onBrew(new BrewEvent(stand, inventory, results, 20));
        assertEquals("nobody opened the stand", 0, service.xp(player.getUniqueId(), Skill.ALCHEMY), 0);
        sources.rememberBrewer(stand, player.getUniqueId());
        sources.onBrew(new BrewEvent(stand, inventory, results, 20));
        assertEquals(2 * 10, service.xp(player.getUniqueId(), Skill.ALCHEMY), 1e-9);
    }
}
