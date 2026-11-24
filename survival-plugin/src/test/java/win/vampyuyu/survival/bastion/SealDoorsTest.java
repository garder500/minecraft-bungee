package win.vampyuyu.survival.bastion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.api.BastionApi;
import win.vampyuyu.survival.api.Realm;
import win.vampyuyu.survival.data.ContentDomains;
import win.vampyuyu.survival.data.Db;
import win.vampyuyu.survival.data.Seeds;
import win.vampyuyu.survival.realm.StormPlan;

/** The seals of the Realms: the mapping (data), the sites (plans) and the right-click. */
public class SealDoorsTest {
    private ServerMock server;
    private BastionConfig cfg;
    private final List<String> entered = new ArrayList<>();
    private final List<String> warned = new ArrayList<>();
    private String refusal;
    private SealDoors doors;

    @Before
    public void up() {
        server = MockBukkit.mock();
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION), null);
        cfg = BastionConfig.load(db);
        BastionApi api = new BastionApi() {
            @Override public boolean isKey(String itemId) { return false; }
            @Override public java.util.Optional<String> bastionOfKey(String itemId) { return java.util.Optional.empty(); }
            @Override public String enter(Player leader, String bastionId) { entered.add(bastionId); return refusal; }
        };
        doors = new SealDoors(() -> cfg, () -> api, (p, text) -> warned.add(text));
    }

    @After
    public void down() {
        MockBukkit.unmock();
    }

    private Block sealBlock(SealDoors.Site site) {
        return world(site.world()).getBlockAt(site.x(), site.y(), site.z());
    }

    /** A world tall enough for the Archipelago (the seal stands at y 141). */
    private World world(String name) {
        World world = server.getWorld(name);
        if (world == null) {
            org.mockbukkit.mockbukkit.world.WorldMock tall = new org.mockbukkit.mockbukkit.world.WorldMock(Material.AIR, -64, 400, 4);
            tall.setName(name);
            server.addWorld(tall);
            world = tall;
        }
        return world;
    }

    private SealDoors.Site site(Realm realm) {
        return SealDoors.sites().stream().filter(s -> s.realm() == realm).findFirst().orElseThrow();
    }

    private PlayerInteractEvent click(Player player, Block block, Action action, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, null, block, BlockFace.SOUTH, hand);
        doors.onInteract(event);
        return event;
    }

    @Test
    public void everyRealmHasASealAndASettingThatNamesAnExistingBastion() {
        assertEquals(List.of(), cfg.problems());
        Set<String> targets = new HashSet<>();
        for (Realm realm : Realm.values()) {
            String target = doors.bastionOf(realm);
            assertNotNull("bastion.seal." + realm.id(), target);
            assertTrue(target + " is a Bastion", cfg.get(target).isPresent());
            assertTrue("one Bastion per seal: " + target, targets.add(target));
            assertEquals(realm.id() + " has one seal", 1, SealDoors.sites().stream().filter(s -> s.realm() == realm).count());
        }
        assertEquals("the Realm's Guardian is the Bastion's Guardian name", "tempest_bastion", doors.bastionOf(Realm.ARCHIPELAGO));
        assertEquals("ashes_bastion", doors.bastionOf(Realm.ASHES));
        assertEquals("marsh_bastion", doors.bastionOf(Realm.MARSHES));
    }

    @Test
    public void aSettingThatNamesNoBastionIsReportedAndTheSealIsDead() {
        Db db = Db.memory();
        Seeds.sync(db, List.of(ContentDomains.BASTION), null);
        db.write(conn -> {
            Db.update(conn, "UPDATE setting SET value = 'nope_bastion' WHERE id = 'bastion.seal.grove'");
            return null;
        });
        BastionConfig bad = BastionConfig.load(db);
        assertFalse(bad.seals.containsKey("grove"));
        assertTrue(bad.problems().stream().anyMatch(p -> p.contains("bastion.seal.grove")));
    }

    @Test
    public void stormKeepsItsLodestoneAtTheSameCoordinates() {
        SealDoors.Site storm = site(Realm.ARCHIPELAGO);
        assertEquals("sb_storm", storm.world());
        assertEquals(StormPlan.SEAL[0], storm.x());
        assertEquals(StormPlan.ORIGIN_Y + StormPlan.SEAL[1], storm.y());
        assertEquals(StormPlan.SEAL[2] + 1, storm.z());
    }

    @Test
    public void rightClickingTheSealOpensItsBastion() {
        Player player = server.addPlayer("Seeker");
        for (Realm realm : Realm.values()) {
            entered.clear();
            PlayerInteractEvent event = click(player, sealBlock(site(realm)), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            assertTrue(realm + " cancels the click", event.isCancelled());
            assertEquals(realm + " enters its Bastion", List.of(doors.bastionOf(realm)), entered);
        }
        assertTrue("no refusal when the Bastion opens", warned.isEmpty());
    }

    @Test
    public void theBastionsOwnRefusalIsShownAsItIs() {
        refusal = "You need a Ashes Bastion Key to open the Ashes Bastion. The party leader spends it when the fight opens.";
        Player player = server.addPlayer("Seeker");
        click(player, sealBlock(site(Realm.ASHES)), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(List.of(refusal), warned);
    }

    @Test
    public void otherClicksAndOtherBlocksAreIgnored() {
        Player player = server.addPlayer("Seeker");
        SealDoors.Site ashes = site(Realm.ASHES);
        Block seal = sealBlock(ashes);
        assertFalse("off hand", click(player, seal, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND).isCancelled());
        assertFalse("left click", click(player, seal, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND).isCancelled());
        assertFalse("next block", click(player, seal.getRelative(BlockFace.UP), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND).isCancelled());
        assertFalse("next block", click(player, seal.getRelative(BlockFace.SOUTH), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND).isCancelled());
        World other = world("sb_elsewhere");
        assertFalse("same coordinates in another world",
            click(player, other.getBlockAt(ashes.x(), ashes.y(), ashes.z()), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND).isCancelled());
        assertTrue(entered.isEmpty());
        assertNull(doors.siteAt(other.getBlockAt(ashes.x(), ashes.y(), ashes.z())));
    }

    @Test
    public void aSealWithoutASettingDoesNotEnterAnything() {
        cfg.seals.remove("grove");
        Player player = server.addPlayer("Seeker");
        PlayerInteractEvent event = click(player, sealBlock(site(Realm.GROVE)), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertTrue(event.isCancelled());
        assertTrue(entered.isEmpty());
        assertEquals(1, warned.size());
    }
}
