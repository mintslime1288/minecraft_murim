package com.murimrpg.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.murimrpg.client.MeshyWalkAnimation;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** Rotation-only reader for the MurimRPG-owned Blender walk export. */
final class AuthoredRigWalkAnimation {
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("murimrpg", "player_motion/murim_walk.json");
    private static final List<String> BONES = List.of("upper_arm.L", "upper_arm.R", "lower_arm.L", "lower_arm.R",
            "thigh.L", "thigh.R", "shin.L", "shin.R", "spine", "chest");
    private final Map<String, List<Vector3f>> tracks;
    private final int loopFrames;

    private AuthoredRigWalkAnimation(Map<String, List<Vector3f>> tracks, int loopFrames) {
        this.tracks = Map.copyOf(tracks);
        this.loopFrames = loopFrames;
    }

    static AuthoredRigWalkAnimation load(Minecraft client) throws IOException {
        try (var input = client.getResourceManager().open(SOURCE);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("schema").getAsInt() != 1 || !root.get("rotation_only").getAsBoolean()) {
                throw new IOException("Unsupported Murim authored walk schema");
            }
            JsonObject bones = root.getAsJsonObject("bones");
            Map<String, List<Vector3f>> tracks = new HashMap<>();
            int size = -1;
            for (String name : BONES) {
                JsonArray frames = bones.getAsJsonArray(name);
                if (frames == null || frames.size() < 2) throw new IOException("Missing authored walk bone: " + name);
                var values = new java.util.ArrayList<Vector3f>();
                for (var element : frames) {
                    JsonArray value = element.getAsJsonArray();
                    if (value.size() != 3) throw new IOException("Invalid authored rotation: " + name);
                    Vector3f rotation = new Vector3f(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat());
                    if (!Float.isFinite(rotation.x) || !Float.isFinite(rotation.y) || !Float.isFinite(rotation.z)) {
                        throw new IOException("Non-finite authored rotation: " + name);
                    }
                    values.add(rotation);
                }
                if (size >= 0 && size != values.size()) throw new IOException("Mismatched authored track length");
                size = values.size(); tracks.put(name, List.copyOf(values));
            }
            warnIfSeamNotClosed(tracks);
            return new AuthoredRigWalkAnimation(tracks, size - 1);
        }
    }

    /** Load-time only: a walk action whose first and last frame differ pops at the loop seam. */
    private static void warnIfSeamNotClosed(Map<String, List<Vector3f>> tracks) {
        for (var entry : tracks.entrySet()) {
            var frames = entry.getValue();
            if (frames.getFirst().distance(frames.getLast()) > 1.0e-4f) {
                org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated")
                        .warn("Authored walk bone {} does not close its loop; expect a seam pop", entry.getKey());
            }
        }
    }

    MeshyWalkAnimation.Pose sample(float phase) {
        float cycle = phase / ((float) Math.PI * 2f);
        cycle = cycle - (float) Math.floor(cycle);
        float exact = cycle * loopFrames;
        int from = Math.min(loopFrames - 1, (int) Math.floor(exact));
        // The track holds loopFrames + 1 samples, so from + 1 is always in range and the
        // final sample is used instead of silently jumping back to index 0 mid-frame.
        int to = from + 1;
        float amount = exact - from;
        return new MeshyWalkAnimation.Pose(sample("upper_arm.R", from, to, amount), sample("upper_arm.L", from, to, amount),
                sample("lower_arm.R", from, to, amount), sample("lower_arm.L", from, to, amount),
                sample("thigh.R", from, to, amount), sample("thigh.L", from, to, amount),
                sample("shin.R", from, to, amount), sample("shin.L", from, to, amount),
                new Vector3f(), new Vector3f(), sample("spine", from, to, amount).add(sample("chest", from, to, amount)));
    }

    private Vector3f sample(String bone, int from, int to, float amount) {
        return new Vector3f(tracks.get(bone).get(from)).lerp(tracks.get(bone).get(to), amount);
    }
}
