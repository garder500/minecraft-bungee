package win.vampyuyu.survival.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A boss rig baked from its Blockbench project (resource-pack/art/bbrig.py, written to boss/rigs/&lt;id&gt;.json in the jar by
 * boss_skins.py): one item model per bone and, per animation clip, a frame every {@code step} ticks giving each bone's pose
 * as [tx, ty, tz, qx, qy, qz, qw, k]: a model point v (in model units) goes to k * R * (v - C) + C + t, C the model centre.
 * Every bone is its own item display at the boss's position; {@link #transform} turns a pose into its transformation.
 */
final class BossRig {
    record Bone(String name, String model) {
    }

    /** A clip: frames[frame][bone] = pose. loop clips repeat, hold clips stay on their last frame (death). */
    record Clip(String name, boolean loop, boolean hold, int ticks, int step, float[][][] frames) {
        int frameCount() {
            return frames.length;
        }
    }

    private static final Map<String, Optional<BossRig>> CACHE = new ConcurrentHashMap<>();

    final List<Bone> bones;
    final Map<String, Clip> clips;

    BossRig(List<Bone> bones, Map<String, Clip> clips) {
        this.bones = List.copyOf(bones);
        this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
    }

    static Optional<BossRig> of(String bossId) {
        return CACHE.computeIfAbsent(bossId, id -> {
            try (InputStream in = BossRig.class.getResourceAsStream("/boss/rigs/" + id + ".json")) {
                if (in == null) {
                    return Optional.empty();
                }
                return Optional.of(parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    static BossRig parse(JsonObject root) {
        List<Bone> bones = new ArrayList<>();
        for (JsonElement b : root.getAsJsonArray("bones")) {
            JsonObject o = b.getAsJsonObject();
            bones.add(new Bone(o.get("name").getAsString(), o.get("model").getAsString()));
        }
        Map<String, Clip> clips = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            JsonArray frames = a.getAsJsonArray("frames");
            float[][][] f = new float[frames.size()][][];
            for (int i = 0; i < frames.size(); i++) {
                JsonArray perBone = frames.get(i).getAsJsonArray();
                f[i] = new float[perBone.size()][];
                for (int j = 0; j < perBone.size(); j++) {
                    JsonArray pose = perBone.get(j).getAsJsonArray();
                    f[i][j] = new float[8];
                    for (int k = 0; k < 8; k++) {
                        f[i][j][k] = pose.get(k).getAsFloat();
                    }
                }
            }
            clips.put(e.getKey(), new Clip(e.getKey(), a.get("loop").getAsBoolean(), a.has("hold") && a.get("hold").getAsBoolean(),
                a.get("ticks").getAsInt(), a.get("step").getAsInt(), f));
        }
        return new BossRig(bones, clips);
    }

    /** The rest pose of every bone (no clip). */
    static final float[] REST = {0, 0, 0, 0, 0, 0, 1, 1};

    /**
     * The display transformation of a bone pose for a boss model drawn at {@code scale} (16 model units = scale blocks).
     * The item display draws the model centred on its position, one model unit = 1/16 block, turned half a turn about y
     * (F), then applies translation + left * scale * right: so translation = scale/16 * F t + the lift that stands the
     * model's feet on the ground (0, scale/2, 0), left = F R F, scale = scale * k.
     */
    static Transformation transform(float[] pose, float scale) {
        float s = scale / 16f;
        // the item display draws its model turned half a turn about y (that is why a boss faces where its mob looks):
        // the pose, made in the model's own frame, is turned the same way, (x, y, z) -> (-x, y, -z)
        Vector3f translation = new Vector3f(-pose[0] * s, pose[1] * s + 0.5f * scale, -pose[2] * s);
        Quaternionf left = new Quaternionf(-pose[3], pose[4], -pose[5], pose[6]).normalize();
        float k = scale * pose[7];
        return new Transformation(translation, left, new Vector3f(k, k, k), new Quaternionf());
    }
}
