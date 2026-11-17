package win.vampyuyu.survival.showcase;

import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import win.vampyuyu.survival.SurvivalPlugin;

/**
 * Every item /sbgive can give (so every frame of /sbshowcase) must have a drawable model in the resource pack: its
 * {@code item_model} resolves to {@code assets/<ns>/items/<path>.json}, every model that definition references exists,
 * its parents exist and every texture exists. {@code minecraft:} references are vanilla and trusted. Without this a
 * frame shows the magenta and black missing-texture checker. The pack is read from {@code ../resource-pack/pack} or,
 * in the docker test run, from the read-only mount {@code /resource-pack/pack}.
 */
public class ShowcaseModelsTest {
    private ServerMock server;
    private SurvivalPlugin plugin;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SurvivalPlugin.class);
    }

    @After
    public void tearDown() {
        MockBukkit.unmock();
    }

    static Path pack() {
        for (String candidate : new String[] {"../resource-pack/pack", "/resource-pack/pack"}) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p.resolve("assets"))) {
                return p;
            }
        }
        throw new AssertionError("resource pack not found (../resource-pack/pack or /resource-pack/pack)");
    }

    private static String[] split(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? new String[] {"minecraft", key} : new String[] {key.substring(0, colon), key.substring(colon + 1)};
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** Every "model" string anywhere in an item definition (plain, composite, select, range_dispatch, condition...). */
    private static void modelRefs(JsonElement e, List<String> out) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (en.getKey().equals("model") && v.isJsonPrimitive()) {
                    out.add(v.getAsString());
                } else {
                    modelRefs(v, out);
                }
            }
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> modelRefs(x, out));
        }
    }

    /** Problems (empty when fine) of one model reference, recursing through parents. */
    static void checkModel(Path pack, String ref, List<String> problems, int depth) throws IOException {
        String[] k = split(ref);
        if (k[0].equals("minecraft") || depth > 8) {
            return;
        }
        Path file = pack.resolve("assets/" + k[0] + "/models/" + k[1] + ".json");
        if (!Files.isRegularFile(file)) {
            problems.add("missing model " + ref + " (" + file + ")");
            return;
        }
        JsonObject model = read(file);
        if (model.has("parent")) {
            checkModel(pack, model.get("parent").getAsString(), problems, depth + 1);
        }
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String tex = t.getValue().getAsString();
                if (tex.startsWith("#")) {
                    continue;
                }
                String[] tk = split(tex);
                if (!tk[0].equals("minecraft") && !Files.isRegularFile(pack.resolve("assets/" + tk[0] + "/textures/" + tk[1] + ".png"))) {
                    problems.add("model " + ref + ": missing texture " + tex);
                }
            }
        }
    }

    /** Problems of one {@code item_model} key; empty when it draws. */
    static List<String> check(Path pack, String itemModel) throws IOException {
        List<String> problems = new ArrayList<>();
        String[] k = split(itemModel);
        if (k[0].equals("minecraft")) {
            return problems;
        }
        Path def = pack.resolve("assets/" + k[0] + "/items/" + k[1] + ".json");
        if (!Files.isRegularFile(def)) {
            problems.add("no item definition " + def);
            return problems;
        }
        List<String> refs = new ArrayList<>();
        modelRefs(read(def), refs);
        if (refs.isEmpty()) {
            problems.add("item definition " + itemModel + " references no model");
        }
        for (String ref : refs) {
            checkModel(pack, ref, problems, 0);
        }
        return problems;
    }

    @Test
    public void everyShowcaseItemHasADrawableModel() throws IOException {
        Path pack = pack();
        ShowcaseLayout layout = ShowcaseFeature.layoutOf(plugin.core());
        List<String> failures = new ArrayList<>();
        for (ShowcaseLayout.Frame f : layout.frames()) {
            String model = plugin.core().items().def(f.id()).get().itemModel();
            if (model == null) {
                continue; // drawn as its vanilla base material
            }
            int column = f.x() - layout.sections().stream()
                .filter(s -> s.group().equals(f.group()) && f.x() >= s.x0() && f.x() <= s.x1()
                    && (s.north() == (f.z() == 0)))
                .findFirst().get().x0() + 1;
            int row = ShowcaseLayout.FRAME_TOP - f.y() + 1;
            for (String p : check(pack, model)) {
                failures.add(f.id() + " [" + f.group() + (f.z() == 0 ? " north" : " south") + " wall, row " + row + ", column " + column
                    + ", item_model " + model + "]: " + p);
            }
        }
        assertTrue("showcase items that would draw as the missing-texture checker:\n" + String.join("\n", failures), failures.isEmpty());
    }

    @Test
    public void theCheckCatchesBrokenModels() throws IOException {
        Path pack = pack();
        assertTrue(!check(pack, "vampyuyu:no/such_item").isEmpty());
        assertTrue(check(pack, "vampyuyu:boss/echo_key").isEmpty());
    }
}
