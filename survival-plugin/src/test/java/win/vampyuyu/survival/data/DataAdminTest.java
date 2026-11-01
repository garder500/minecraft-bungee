package win.vampyuyu.survival.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Staff editing of the content database, on an in-memory copy of the jar's defaults. */
public class DataAdminTest {
    private static int count(Db db, String sql) {
        return db.read(c -> Db.query(c, sql, rs -> rs.getInt(1)).get(0));
    }

    private static String text(Db db, String sql) {
        return db.read(c -> Db.query(c, sql, rs -> rs.getString(1)).get(0));
    }

    private static Db seeded() {
        Db db = Db.memory();
        Seeds.sync(db, ContentDomains.ALL, null);
        return db;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private static Map<String, Object> quest(String name, Object amount) {
        return Map.of("pool", "plan", "position", 9000, "name", name, "type", "BREAK_BLOCK", "amount", amount);
    }

    @Test
    public void listsDomainsAndColumnsFromTheSchema() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            List<Map<String, Object>> domains = admin.domains();
            assertEquals(ContentDomains.ALL.size(), domains.size());
            Map<String, Object> schema = admin.schema("quests", "quest");
            List<Map<String, Object>> cols = list(schema.get("columns"));
            Map<String, Object> amount = cols.stream().filter(c -> c.get("name").equals("amount")).findFirst().orElseThrow();
            assertEquals("int", amount.get("kind"));
            assertEquals(true, amount.get("notnull"));
            assertTrue(list(List.of(amount)).get(0).get("checks").toString().contains("amount >= 1"));
            Map<String, Object> main = list(admin.schema("quests", "quest_category").get("columns")).stream()
                .filter(c -> c.get("name").equals("main")).findFirst().orElseThrow();
            assertEquals("bool", main.get("kind"));
            Map<String, Object> pool = cols.stream().filter(c -> c.get("name").equals("pool")).findFirst().orElseThrow();
            assertEquals(List.of("plan", "daily"), pool.get("choices"));
            assertEquals(3, list(schema.get("children")).size());
            assertEquals("quest_id", list(schema.get("children")).get(0).get("fk"));
            assertEquals("origin", schema.get("originColumn"));
            assertThrows(DataAdmin.Invalid.class, () -> admin.schema("quests", "nope"));
        }
    }

    @Test
    public void listsRowsWithPagingFilterAndOrigin() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            Map<String, Object> page = admin.rows("quests", "quest", null, null, 0, 10);
            assertEquals(10, list(page.get("rows")).size());
            assertTrue((int) page.get("total") >= 250);
            assertEquals("quests only lists the plan pool, not the daily one", count(db, "SELECT COUNT(*) FROM quest WHERE pool = 'plan'"), page.get("total"));
            Map<String, Object> found = admin.rows("quests", "quest", "q046", null, 0, 10);
            assertEquals(1, list(found.get("rows")).size());
            assertEquals(0, (int) admin.rows("quests", "quest", null, "panel", 0, 10).get("total"));
            assertEquals("wildcards in the filter are plain text", 0, (int) admin.rows("quests", "quest", "%", null, 0, 10).get("total"));
        }
    }

    @Test
    public void putsARootWithItsChildrenAndAuditsIt() {
        try (Db db = seeded()) {
            List<String> changed = new ArrayList<>();
            DataAdmin admin = new DataAdmin(db, changed::add);
            Map<String, Object> out = admin.put("quests", "quest", "q046", quest("Edited", 77), Map.of(
                "quest_requirement", List.of(Map.of("requires", "q001"), Map.of("requires", "q002")),
                "quest_reward_xp", List.of(Map.of("skill", "mining", "amount", 12.5))), "alice");
            assertEquals("panel", out.get("origin"));
            assertEquals(77, map(out.get("row")).get("amount"));
            assertEquals(List.of("q001", "q002"), list(map(out.get("children")).get("quest_requirement")).stream().map(r -> r.get("requires")).toList());
            assertEquals(0, list(map(out.get("children")).get("quest_reward_item")).size());
            assertEquals(List.of("quests"), changed);
            assertEquals(1, count(db, "SELECT COUNT(*) FROM quest WHERE origin = 'panel'"));
            assertEquals("the children were replaced, not appended", 2, count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));
            assertEquals(1, count(db, "SELECT COUNT(*) FROM data_audit WHERE actor = 'alice' AND action = 'put' AND row_key = 'q046'"));
            String after = text(db, "SELECT after FROM data_audit WHERE action = 'put'");
            assertTrue(after, after.contains("\"name\":\"Edited\"") && after.contains("q002"));
            assertNotNull(text(db, "SELECT before FROM data_audit WHERE action = 'put'"));

            // a new row
            admin.put("quests", "quest", "q_new", quest("Brand new", 3), null, "alice");
            assertEquals(1, count(db, "SELECT COUNT(*) FROM data_audit WHERE row_key = 'q_new' AND before IS NULL AND after IS NOT NULL"));
            assertEquals(2, count(db, "SELECT COUNT(*) FROM quest WHERE origin = 'panel'"));
        }
    }

    @Test
    public void aRefusedWriteNamesTheColumnAndChangesNothing() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            int audits = count(db, "SELECT COUNT(*) FROM data_audit");
            DataAdmin.Invalid check = assertThrows(DataAdmin.Invalid.class,
                () -> admin.put("quests", "quest", "q046", quest("x", 0), null, "alice"));
            assertEquals("amount", check.column());
            assertTrue(check.getMessage(), check.getMessage().contains("amount"));
            assertFalse(check.getMessage(), check.getMessage().contains("SQLITE"));

            DataAdmin.Invalid type = assertThrows(DataAdmin.Invalid.class,
                () -> admin.put("quests", "quest", "q046", quest("x", "many"), null, "alice"));
            assertEquals("amount", type.column());
            assertTrue(type.getMessage(), type.getMessage().contains("whole number"));

            DataAdmin.Invalid missing = assertThrows(DataAdmin.Invalid.class,
                () -> admin.put("quests", "quest", "q_x", Map.of("pool", "plan", "position", 1, "type", "KILL", "amount", 1), null, "alice"));
            assertEquals("name", missing.column());

            DataAdmin.Invalid dup = assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", "q046", quest("x", 5),
                Map.of("quest_requirement", List.of(Map.of("requires", "q001"), Map.of("requires", "q001"))), "alice"));
            assertTrue(dup.getMessage(), dup.getMessage().contains("quest_requirement"));
            assertEquals("the failed child rolled the root back too", 8, count(db, "SELECT amount FROM quest WHERE id = 'q046'"));

            assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", "q046", Map.of("nope", 1), null, "alice"));
            assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", "q046", quest("x", 5), Map.of("ghost", List.of()), "alice"));
            assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", "q046", Map.of("id", "other", "name", "x"), null, "alice"));
            assertEquals(audits, count(db, "SELECT COUNT(*) FROM data_audit"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM quest WHERE origin = 'panel'"));
        }
    }

    @Test
    public void aDomainCannotWriteAnotherDomainsRows() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            String daily = text(db, "SELECT id FROM quest WHERE pool = 'daily' LIMIT 1");
            DataAdmin.Invalid other = assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", daily, quest("x", 1), null, "alice"));
            assertTrue(other.getMessage(), other.getMessage().contains("another data set"));
            Map<String, Object> moved = new java.util.HashMap<>(quest("x", 1));
            moved.put("pool", "daily");
            DataAdmin.Invalid pool = assertThrows(DataAdmin.Invalid.class, () -> admin.put("quests", "quest", "q046", moved, null, "alice"));
            assertEquals("pool", pool.column());
            assertEquals("plan", text(db, "SELECT pool FROM quest WHERE id = 'q046'"));
            assertThrows(DataAdmin.Invalid.class, () -> admin.put("economy", "setting", "skills.x", Map.of("type", "int", "value", "1"), null, "alice"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM setting WHERE id = 'skills.x'"));
            assertThrows(DataAdmin.Invalid.class, () -> admin.delete("quests", "quest", daily, "alice"));
        }
    }

    @Test
    public void aPanelRowSurvivesANewSeedAndResetBringsTheDefaultBack() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            admin.put("quests", "quest", "q046", quest("Mine", 99), Map.of(), "bob");
            db.write(c -> Db.update(c, "DELETE FROM seed_state")); // as if the jar had a new seed
            Seeds.sync(db, ContentDomains.ALL, null);
            assertEquals(99, count(db, "SELECT amount FROM quest WHERE id = 'q046'"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));

            Map<String, Object> back = admin.reset("quests", "quest", "q046", "bob");
            assertEquals("seed", back.get("origin"));
            assertEquals(8, map(back.get("row")).get("amount"));
            assertEquals(1, list(map(back.get("children")).get("quest_requirement")).size());
            assertEquals(1, count(db, "SELECT COUNT(*) FROM data_audit WHERE action = 'reset' AND actor = 'bob'"));

            admin.put("quests", "quest", "q_only_panel", quest("No default", 1), null, "bob");
            DataAdmin.Invalid none = assertThrows(DataAdmin.Invalid.class, () -> admin.reset("quests", "quest", "q_only_panel", "bob"));
            assertEquals(404, none.status());
        }
    }

    @Test
    public void deleteRemovesTheRowAndItsChildrenAndIsAudited() {
        try (Db db = seeded()) {
            List<String> changed = new ArrayList<>();
            DataAdmin admin = new DataAdmin(db, changed::add);
            assertEquals(1, count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));
            admin.delete("quests", "quest", "q046", "carol");
            assertEquals(0, count(db, "SELECT COUNT(*) FROM quest WHERE id = 'q046'"));
            assertEquals(0, count(db, "SELECT COUNT(*) FROM quest_requirement WHERE quest_id = 'q046'"));
            assertEquals(List.of("quests"), changed);
            assertEquals(404, assertThrows(DataAdmin.Invalid.class, () -> admin.delete("quests", "quest", "q046", "carol")).status());

            Map<String, Object> audit = admin.audit("quest", null, "carol", 0, 10);
            assertEquals(1, audit.get("total"));
            Map<String, Object> row = list(audit.get("rows")).get(0);
            assertEquals("delete", row.get("action"));
            assertNotNull(row.get("before"));
            assertNull(row.get("after"));
        }
    }

    @Test
    public void auditListsNewestFirstAndFilters() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            admin.put("quests", "quest", "q_a", quest("A", 1), null, "alice");
            admin.put("quests", "quest", "q_b", quest("B", 1), null, "bob");
            List<Map<String, Object>> all = list(admin.audit(null, null, null, 0, 10).get("rows"));
            assertEquals("q_b", all.get(0).get("key"));
            assertEquals(1, list(admin.audit(null, "q_a", null, 0, 10).get("rows")).size());
            assertEquals(1, list(admin.audit(null, null, "bob", 0, 10).get("rows")).size());
            assertEquals(1, list(admin.audit(null, null, null, 1, 1).get("rows")).size());
        }
    }

    @Test
    public void everyRootOfEveryDomainDescribesItself() {
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            for (Seeds.Domain d : ContentDomains.ALL) {
                for (Seeds.Root r : d.roots()) {
                    Map<String, Object> schema = admin.schema(d.name(), r.table());
                    assertFalse(d.name() + "." + r.table(), list(schema.get("columns")).isEmpty());
                    Map<String, Object> rows = admin.rows(d.name(), r.table(), null, null, 0, 1);
                    for (Map<String, Object> row : list(rows.get("rows"))) {
                        String key = String.valueOf(row.get(r.key()));
                        assertNotNull(admin.get(d.name(), r.table(), key).get("row"));
                    }
                }
            }
        }
    }

    @Test
    public void putThenGetRoundTripsAnEverySeedRowUnchanged() {
        // saving a row exactly as the editor loaded it must not fail or change a value
        try (Db db = seeded()) {
            DataAdmin admin = new DataAdmin(db, null);
            for (Seeds.Domain d : ContentDomains.ALL) {
                for (Seeds.Root r : d.roots()) {
                    Map<String, Object> rows = admin.rows(d.name(), r.table(), null, null, 0, 1);
                    for (Map<String, Object> row : list(rows.get("rows"))) {
                        String key = String.valueOf(row.get(r.key()));
                        Map<String, Object> got = admin.get(d.name(), r.table(), key);
                        Map<String, List<Map<String, Object>>> kids = new java.util.LinkedHashMap<>();
                        map(got.get("children")).forEach((t, v) -> kids.put(t, list(v)));
                        Map<String, Object> saved = admin.put(d.name(), r.table(), key, map(got.get("row")), kids, "t");
                        assertEquals(d.name() + "." + r.table(), map(got.get("row")), withSeed(map(saved.get("row"))));
                        assertEquals("panel", saved.get("origin"));
                    }
                }
            }
        }
    }

    private static Map<String, Object> withSeed(Map<String, Object> row) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
        if (copy.containsKey("origin")) copy.put("origin", "seed");
        return copy;
    }

    @Test
    public void theHttpRoutesSpeakJsonAndNeedAnActorForChanges() {
        try (Db db = seeded(); DataHttpApi api = routerOnly(db)) {
            assertEquals(200, api.route("GET", "/skyblock/data", Map.of(), "").status());
            assertEquals(200, api.route("GET", "/skyblock/data/quests/quest/rows", Map.of("q", "q046"), "").status());
            assertEquals(200, api.route("GET", "/skyblock/data/quests/quest/row", Map.of("key", "q046"), "").status());
            assertThrows(DataAdmin.Invalid.class, () -> api.route("DELETE", "/skyblock/data/quests/quest/row", Map.of("key", "q046"), ""));
            String body = "{\"row\":{\"pool\":\"plan\",\"position\":1,\"name\":\"n\",\"type\":\"KILL\",\"amount\":2},\"children\":{}}";
            assertEquals(200, api.route("PUT", "/skyblock/data/quests/quest/row", Map.of("key", "weird/key:*", "actor", "a"), body).status());
            assertEquals(2, map(((Map<?, ?>) api.route("GET", "/skyblock/data/quests/quest/row", Map.of("key", "weird/key:*"), "").body()).get("row")).get("amount"));
            assertEquals(200, api.route("POST", "/skyblock/data/quests/quest/reset", Map.of("key", "q046", "actor", "a"), "").status());
            assertEquals(Map.of("a", "b c", "d", ""), DataHttpApi.query("a=b%20c&d="));
            assertEquals(404, assertThrows(DataAdmin.Invalid.class, () -> api.route("GET", "/skyblock/data/quests/quest/other", Map.of(), "")).status());
        }
    }

    /** The router needs no socket: a stand-in built through the package-private route(). */
    private static DataHttpApi routerOnly(Db db) {
        try {
            return new DataHttpApi(new DataAdmin(db, null), "t".repeat(40), 0, java.util.logging.Logger.getLogger("test"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
