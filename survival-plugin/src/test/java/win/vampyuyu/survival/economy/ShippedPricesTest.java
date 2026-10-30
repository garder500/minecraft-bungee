package win.vampyuyu.survival.economy;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.junit.Test;

/** Checks the price tables shipped in economy.yml: valid items and no buy-low / sell-high loops. */
public class ShippedPricesTest {
    private static YamlConfiguration config;
    private static ShopCatalog catalog;

    @BeforeClass
    public static void load() {
        MockBukkit.mock(); // Material and YamlConfiguration need a server, also when this class runs alone
        config = YamlConfiguration.loadConfiguration(new InputStreamReader(
            ShippedPricesTest.class.getResourceAsStream("/economy.yml"), StandardCharsets.UTF_8));
        catalog = ShopCatalog.parse(config, Logger.getAnonymousLogger());
    }

    @AfterClass
    public static void unload() {
        MockBukkit.unmock();
    }

    private static long minBuy(String key) {
        long best = Long.MAX_VALUE;
        for (var shop : catalog.shops().values()) {
            for (var i : shop.items()) {
                if (i.key().equals(key) && i.buyCents() > 0) {
                    best = Math.min(best, i.buyCents());
                }
            }
        }
        return best;
    }

    private static long maxSell(String key) {
        return catalog.bestSell(key).orElse(0);
    }

    @Test
    public void tenMerchantsExist() {
        assertEquals(List.of("farmer", "miner", "lumberjack", "fisherman", "mob_hunter", "alchemist", "adventurer",
            "weaponsmith", "builder", "rancher"), new ArrayList<>(catalog.shops().keySet()));
        for (var shop : catalog.shops().values()) {
            assertTrue(shop.id() + " has items", shop.items().size() >= 15);
            assertFalse(shop.name().isBlank());
        }
    }

    @Test
    public void everyVanillaKeyIsARealItem() {
        for (var shop : catalog.shops().values()) {
            for (var i : shop.items()) {
                if (ItemKeys.looksVanilla(i.key())) {
                    Material m = Material.matchMaterial(i.key());
                    assertNotNull(shop.id() + ": unknown material " + i.key(), m);
                    assertTrue(shop.id() + ": not an item " + i.key(), m.isItem());
                    assertEquals(i.key(), m.name());
                }
            }
        }
    }

    @Test
    public void noItemIsListedInTwoShopsWithDifferentPrices() {
        for (var a : catalog.shops().values()) {
            for (var b : catalog.shops().values()) {
                if (a == b) {
                    continue;
                }
                for (var i : a.items()) {
                    b.item(i.key()).ifPresent(j -> {
                        assertEquals(i.key() + " buy", i.buyCents(), j.buyCents());
                        assertEquals(i.key() + " sell", i.sellCents(), j.sellCents());
                    });
                }
            }
        }
    }

    @Test
    public void cheapestPurchaseNeverBelowBestSale() {
        for (var shop : catalog.shops().values()) {
            for (var i : shop.items()) {
                long buy = minBuy(i.key());
                long sell = maxSell(i.key());
                if (buy != Long.MAX_VALUE && sell > 0) {
                    assertTrue(i.key() + ": buy " + buy + " must be above sell " + sell, buy > sell);
                }
            }
        }
    }

    /** {small, big, count}: count smalls craft one big (or one big uncrafts into count smalls). */
    private static final Object[][] COMPRESSION = {
        {"WHEAT", "HAY_BLOCK", 9}, {"COAL", "COAL_BLOCK", 9}, {"IRON_INGOT", "IRON_BLOCK", 9}, {"IRON_NUGGET", "IRON_INGOT", 9},
        {"GOLD_INGOT", "GOLD_BLOCK", 9}, {"GOLD_NUGGET", "GOLD_INGOT", 9}, {"COPPER_INGOT", "COPPER_BLOCK", 9},
        {"REDSTONE", "REDSTONE_BLOCK", 9}, {"LAPIS_LAZULI", "LAPIS_BLOCK", 9}, {"DIAMOND", "DIAMOND_BLOCK", 9},
        {"EMERALD", "EMERALD_BLOCK", 9}, {"QUARTZ", "QUARTZ_BLOCK", 4}, {"GLOWSTONE_DUST", "GLOWSTONE", 4},
        {"SLIME_BALL", "SLIME_BLOCK", 9}, {"DRIED_KELP", "DRIED_KELP_BLOCK", 9}, {"MELON_SLICE", "MELON", 9},
        {"CLAY_BALL", "TERRACOTTA", 4}, {"BRICK", "BRICKS", 4}, {"SAND", "SANDSTONE", 4}, {"RAW_IRON", "IRON_INGOT", 1},
        {"RAW_GOLD", "GOLD_INGOT", 1}, {"RAW_COPPER", "COPPER_INGOT", 1}, {"NETHERITE_SCRAP", "NETHERITE_INGOT", 4},
        {"BLAZE_ROD", "BLAZE_POWDER", 1}, {"OAK_LOG", "OAK_PLANKS", 4}, {"SPRUCE_LOG", "SPRUCE_PLANKS", 4},
        {"BIRCH_LOG", "BIRCH_PLANKS", 4}, {"JUNGLE_LOG", "JUNGLE_PLANKS", 4}, {"ACACIA_LOG", "ACACIA_PLANKS", 4},
        {"DARK_OAK_LOG", "DARK_OAK_PLANKS", 4}, {"OAK_PLANKS", "STICK", 1}, {"STONE_BRICKS", "SMOOTH_STONE", 1},
        {"BONE", "BONE_MEAL", 3}, {"WHEAT", "BREAD", 3}, {"POTATO", "BAKED_POTATO", 1},
        {"COD", "COOKED_COD", 1}, {"SALMON", "COOKED_SALMON", 1}, {"BEEF", "COOKED_BEEF", 1}, {"PORKCHOP", "COOKED_PORKCHOP", 1},
        {"CHICKEN", "COOKED_CHICKEN", 1}, {"MUTTON", "COOKED_MUTTON", 1}, {"RABBIT", "COOKED_RABBIT", 1},
        {"SUGAR_CANE", "SUGAR", 1}, {"COBBLESTONE", "STONE", 1}, {"SAND", "GLASS", 1}, {"BAMBOO", "STICK", 1},
    };

    @Test
    public void craftingLoopsAreNotProfitable() {
        for (Object[] pair : COMPRESSION) {
            String small = (String) pair[0];
            String big = (String) pair[1];
            long n = ((Integer) pair[2]).longValue();
            // Buy n smalls, craft one big, sell it.
            if (minBuy(small) != Long.MAX_VALUE && maxSell(big) > 0) {
                assertTrue(small + "->" + big + " loop: cost " + n * minBuy(small) + " vs sale " + maxSell(big),
                    n * minBuy(small) >= maxSell(big));
            }
            // Buy one big, uncraft into n smalls, sell them (only where the reverse recipe exists).
            boolean reversible = n > 1 && (big.endsWith("_BLOCK") || big.equals("IRON_INGOT") || big.equals("GOLD_INGOT")
                || big.equals("MELON") || big.equals("GLOWSTONE") || big.equals("SLIME_BLOCK"));
            if (reversible && minBuy(big) != Long.MAX_VALUE && maxSell(small) > 0) {
                assertTrue(big + "->" + small + " loop: cost " + minBuy(big) + " vs sale " + n * maxSell(small),
                    minBuy(big) >= n * maxSell(small));
            }
        }
    }

    @Test
    public void configDefaultsAreSane() {
        assertTrue(config.getDouble("pressure.soft-cap") > 0);
        assertTrue(config.getDouble("pressure.min-multiplier") > 0);
        assertTrue(config.getInt("buyback.seconds") > 0);
    }

    /** Compacted minion items (160 raw -> enchanted, 160 enchanted -> super in minions.yml) must never out-earn the raw goods. */
    @Test
    public void compactedMinionItemsAreSellOnlyAndBelowTheCompactionRatio() {
        int checked = 0;
        for (var shop : catalog.shops().values()) {
            for (var i : shop.items()) {
                String key = i.key();
                boolean ench = key.startsWith("minion_enchanted_");
                boolean sup = key.startsWith("minion_super_");
                if (!ench && !sup) {
                    continue;
                }
                checked++;
                assertEquals(key + " must be sell only", 0, i.buyCents());
                String base = key.substring(ench ? "minion_enchanted_".length() : "minion_super_".length());
                String raw = base.toUpperCase(java.util.Locale.ROOT);
                long rawSell = maxSell(raw);
                assertTrue(shop.id() + " sells " + key + " but no shop buys " + raw, rawSell > 0);
                long enchSell = maxSell("minion_enchanted_" + base);
                if (ench) {
                    assertTrue(key + " above 150 x raw", i.sellCents() <= 150 * rawSell);
                    assertTrue(key + " not above the raw buy price x 160",
                        minBuy(raw) == Long.MAX_VALUE || i.sellCents() <= 160 * minBuy(raw));
                } else {
                    assertTrue(key + " has no enchanted form sold", enchSell > 0);
                    assertTrue(key + " above 150 x enchanted", i.sellCents() <= 150 * enchSell);
                }
            }
        }
        assertTrue("the shipped shops list compacted items", checked >= 40);
    }
}
