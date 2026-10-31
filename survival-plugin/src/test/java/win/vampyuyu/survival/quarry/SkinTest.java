package win.vampyuyu.survival.quarry;

import static org.junit.Assert.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.core.SkyblockWorlds;

public class SkinTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private SkinService skins;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        skins = plugin.core().require(SkinService.class);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock player(String name) {
        PlayerMock p = server.addPlayer(name);
        p.teleport(new Location(Bukkit.getWorld(SkyblockWorlds.WORLD), 0, 70, 0));
        return p;
    }

    @Test
    public void everySkinHasAPackModelATokenAndARegisteredItem() {
        for (Skin skin : Skin.values()) {
            assertTrue(skin.tokenId(), plugin.core().items().def(skin.tokenId()).isPresent());
            assertEquals("vampyuyu:skin/" + skin.id, plugin.core().items().def(skin.tokenId()).get().itemModel());
        }
    }

    @Test
    public void aTokenUnlocksTheSkinOnceForTheAccount() {
        PlayerMock p = player("Collector");
        ItemStack token = plugin.core().items().create("skin_kitsune");
        p.getInventory().setItemInMainHand(token);
        var event = new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, token, null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(skins.has(p.getUniqueId(), Skin.KITSUNE));
        assertEquals("the token is used up", 0, token.getAmount());
        assertFalse(skins.has(p.getUniqueId(), Skin.WYRMFANG));
        assertFalse("a second unlock does nothing", skins.unlock(p.getUniqueId(), Skin.KITSUNE));
    }

    @Test
    public void aSkinChangesTheModelKeepsTheItemAndBindsIt() {
        PlayerMock p = player("Fencer");
        skins.unlock(p.getUniqueId(), Skin.TITAN_EDGE);
        ItemStack sword = plugin.core().items().create("bronze_cleaver");
        assertTrue(skins.isSword(sword));
        assertFalse(skins.isSword(new ItemStack(Material.IRON_PICKAXE)));
        skins.apply(sword, Skin.TITAN_EDGE);
        assertEquals("vampyuyu:skin/titan_edge", sword.getItemMeta().getItemModel().toString());
        assertEquals("the sword is still the same item", "bronze_cleaver", plugin.core().items().idOf(sword).orElseThrow());
        assertTrue("a skinned sword is bound", win.vampyuyu.survival.economy.MarketRulesAccess.bound(sword));
        skins.apply(sword, null);
        assertEquals("vampyuyu:weapon/bronze_cleaver", sword.getItemMeta().getItemModel().toString());
        assertFalse(win.vampyuyu.survival.economy.MarketRulesAccess.bound(sword));
    }

    @Test
    public void skinTokensAreBoundToTheirOwner() {
        ItemStack token = plugin.core().items().create("skin_wyrmfang");
        assertTrue(win.vampyuyu.survival.economy.MarketRulesAccess.bound(token));
    }
}
