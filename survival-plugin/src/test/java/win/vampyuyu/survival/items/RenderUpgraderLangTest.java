package win.vampyuyu.survival.items;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.core.Core;
import win.vampyuyu.survival.core.CustomItemDef;
import win.vampyuyu.survival.core.Lang;
import win.vampyuyu.survival.core.Rarity;
import win.vampyuyu.survival.items.enchant.EnchantRegistry;
import win.vampyuyu.survival.items.gear.Gear;
import win.vampyuyu.survival.items.gear.ItemRender;
import win.vampyuyu.survival.items.gear.RenderUpgrader;
import win.vampyuyu.survival.items.reforge.ReforgeRegistry;

/** A material handed out before translations existed is redrawn once, and then stacks with the new ones. */
public class RenderUpgraderLangTest {
    private ServerMock server;
    private SurvivalPlugin plugin;
    private Core core;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        win.vampyuyu.survival.LegacyFeatureFixture.enable(plugin);
        core = plugin.core();
    }

    @After
    public void tearDown() {
        Lang.clear();
        win.vampyuyu.survival.LegacyFeatureFixture.disable(plugin);
        MockBukkit.unmock();
    }

    @Test
    public void anOldEnglishOnlyMaterialIsRedrawnAndStacksWithANewOne() {
        Lang.clear();
        core.items().register(CustomItemDef.builder("t_old_ore", "Old Ore", Rarity.COMMON, Material.RAW_COPPER)
            .lore("<gray>A plain material.").build());
        ItemStack old = core.items().create("t_old_ore", 3);
        RenderUpgrader upgrader = new RenderUpgrader(core,
            new Gear(core.items(), new EnchantRegistry(null), new ReforgeRegistry(null), ItemsSettings.defaults(), new ItemRender()));
        assertFalse("nothing is translated yet: the stack is up to date", upgrader.needsTranslation(old));

        Lang.publish(Map.of("fr", Map.of("item.t_old_ore.name", "Vieux minerai", "item.t_old_ore.lore.0", "<gray>Un simple matériau.")));
        ItemStack fresh = core.items().create("t_old_ore", 1);
        assertFalse("the old stack would not stack with a new one", old.isSimilar(fresh));
        assertTrue(upgrader.needsTranslation(old));
        assertFalse(upgrader.needsTranslation(fresh));

        PlayerMock player = server.addPlayer();
        player.getInventory().setItem(0, old);
        assertEquals(1, upgrader.upgrade(player));
        ItemStack redrawn = player.getInventory().getItem(0);
        assertEquals(3, redrawn.getAmount());
        assertTrue("redrawn, it stacks with a new one", redrawn.isSimilar(fresh));
        assertEquals("a second pass changes nothing", 0, upgrader.upgrade(player));
    }

    @Test
    public void aStackRenamedByAnotherFeatureIsLeftAlone() {
        Lang.clear();
        core.items().register(CustomItemDef.builder("t_named", "Plain Name", Rarity.COMMON, Material.RAW_COPPER).build());
        ItemStack stack = core.items().create("t_named", 1);
        stack.editMeta(meta -> meta.itemName(net.kyori.adventure.text.Component.text("Special Name")));
        Lang.publish(Map.of("fr", Map.of("item.t_named.name", "Nom simple")));
        RenderUpgrader upgrader = new RenderUpgrader(core,
            new Gear(core.items(), new EnchantRegistry(null), new ReforgeRegistry(null), ItemsSettings.defaults(), new ItemRender()));
        assertFalse(upgrader.needsTranslation(stack));
    }
}
