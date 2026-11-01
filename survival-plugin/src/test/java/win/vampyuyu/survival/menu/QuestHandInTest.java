package win.vampyuyu.survival.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import win.vampyuyu.survival.SurvivalPlugin;
import win.vampyuyu.survival.api.QuestGateApi;
import win.vampyuyu.survival.core.SkyblockWorlds;

/** Ores that reached the inventory some other way (a Miner's chest, found earlier) go to an ore quest by right-click. */
public class QuestHandInTest {
    private SurvivalPlugin plugin;
    private PlayerMock player;
    private QuestService quests;
    private QuestDef haul;
    private QuestData data;

    @Before
    public void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
        player = server.addPlayer("Miner");
        player.teleport(Bukkit.getWorld(SkyblockWorlds.WORLD).getSpawnLocation());
        quests = (QuestService) plugin.core().service(QuestGateApi.class).orElseThrow();
        haul = QuestRepo.load(plugin.core().data()).tutorialQuest("q054").orElseThrow(); // Frostium Haul: 8
        data = plugin.core().profiles().section(player.getUniqueId(), "quests", QuestData.class, QuestData::new);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    private int frostium() {
        return Arrays.stream(player.getInventory().getStorageContents())
            .filter(s -> s != null && plugin.core().items().idOf(s).map("frostium"::equals).orElse(false))
            .mapToInt(ItemStack::getAmount).sum();
    }

    @Test
    public void onlyWhatTheActiveQuestNeedsIsTaken() {
        player.getInventory().addItem(plugin.core().items().create("frostium", 12));
        assertEquals("locked: nothing is taken", 0, quests.handIn(player, haul));
        assertEquals(12, frostium());

        data.completed.addAll(haul.requires());
        assertEquals(8, quests.handInAvailable(player, haul));
        assertEquals(8, quests.handIn(player, haul));
        assertTrue(data.completed.contains(haul.id()));
        assertEquals(4, frostium());
        assertEquals("done: nothing more is taken", 0, quests.handIn(player, haul));
        assertEquals(4, frostium());
    }

    @Test
    public void aCreativePlayerKeepsTheOresSinceNothingWouldCount() {
        data.completed.addAll(haul.requires());
        player.getInventory().addItem(plugin.core().items().create("frostium", 8));
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        assertEquals(0, quests.handIn(player, haul));
        assertEquals(8, frostium());
        assertFalse(data.completed.contains(haul.id()));
    }

    @Test
    public void rightClickInTheCategoryMenuHandsIn() {
        data.completed.addAll(haul.requires());
        player.getInventory().addItem(plugin.core().items().create("frostium", 8));
        player.performCommand("quests category variety");
        var view = player.getOpenInventory();
        int slot = -1;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            ItemStack icon = view.getTopInventory().getItem(i);
            if (icon != null && icon.hasItemMeta() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(icon.getItemMeta().displayName()).contains(haul.name())) {
                slot = i;
            }
        }
        assertTrue("the quest is on the first page", slot >= 0);
        player.simulateInventoryClick(view, ClickType.RIGHT, slot);
        assertEquals(0, frostium());
        assertTrue(data.completed.contains(haul.id()));
    }
}
