package com.murimrpg.client.render.wuxia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A skinned glTF rig holding every animation in the file, not just the first one.
 *
 * <p>This is the piece the previous player GLB path was missing. {@code GlbAnimatedPlayerMesh}
 * reads {@code animations[0]} and therefore sees only one clip; the supplied rigged asset packs
 * six. Nothing here touches Minecraft, so the clip selection, root-motion policy and skinning
 * can be verified in a unit test rather than by looking at the game.
 *
 * <h2>Root motion</h2>
 * The source clips are Mixamo captures: the hip node carries the character forward across the
 * world. A player model must never do that, because the server owns the player's position. The
 * {@link RootPolicy} is applied per clip:
 *
 * <ul>
 *   <li>{@link RootPolicy#LOCK_HORIZONTAL} drops the hip's X and Z channels and keeps Y. This is
 *       correct for grounded clips: the horizontal drift disappears while the vertical bob that
 *       keeps the feet on the floor survives.
 *   <li>{@link RootPolicy#LOCK_ALL} drops all three. This is for clips whose vertical travel the
 *       game already performs itself, such as a jump, where keeping Y would lift the model twice.
 *   <li>{@link RootPolicy#FREE} keeps the authored motion and is only for measurement.
 * </ul>
 *
 * <p>Dropping Y as well on a grounded clip is a mistake that is easy to make and hard to see: it
 * leaves the model hovering, because these clips author the stance height in the hip channel.
 */
public final class WuxiaRig {

    /** What to do with the hip node's authored translation. */
    public enum RootPolicy { FREE, LOCK_HORIZONTAL, LOCK_ALL }

    /** One animation channel: a node, a property and its keyframes. */
    public record Channel(int node, String path, float[] times, float[] values, boolean step) { }

    /** One named animation. */
    public record Clip(String name, float durationSeconds, List<Channel> channels) { }

    /** A glTF node's rest transform. */
    public record Node(String name, float[] translation, float[] rotation, float[] scale) { }

    private final Node[] nodes;
    private final int[] parents;
    private final int[] joints;
    private final float[][] inverseBind;
    private final float[] positions;
    private final float[] normals;
    private final float[] uvs;
    private final int[] vertexJoints;
    private final float[] vertexWeights;
    private final int[] indices;
    private final Map<String, Clip> clips;
    private final int hipNode;

    WuxiaRig(Node[] nodes, int[] parents, int[] joints, float[][] inverseBind, float[] positions,
             float[] normals, float[] uvs, int[] vertexJoints, float[] vertexWeights, int[] indices,
             Map<String, Clip> clips) {
        this.nodes = nodes;
        this.parents = parents;
        this.joints = joints;
        this.inverseBind = inverseBind;
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.vertexJoints = vertexJoints;
        this.vertexWeights = vertexWeights;
        this.indices = indices;
        this.clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
        this.hipNode = joints.length > 0 ? joints[0] : -1;
    }

    public int vertexCount() { return positions.length / 3; }
    public int triangleCount() { return indices.length / 3; }
    public int jointCount() { return joints.length; }
    public int[] indices() { return indices; }
    public float[] uvs() { return uvs; }
    public Map<String, Clip> clips() { return clips; }
    public boolean hasClip(String name) { return clips.containsKey(name); }

    /** The node the skin lists first, which for a Mixamo rig is the hips. */
    public String rootJointName() { return hipNode < 0 ? "" : nodes[hipNode].name(); }

    public List<String> jointNames() {
        List<String> out = new ArrayList<>(joints.length);
        for (int joint : joints) out.add(nodes[joint].name());
        return out;
    }

    /** Index into the joint palette for a joint node name, or -1. */
    public int jointIndex(String name) {
        for (int i = 0; i < joints.length; i++) if (nodes[joints[i]].name().equals(name)) return i;
        return -1;
    }

    /**
     * Builds the skinning palette for one clip at one time.
     *
     * <p>Every node's rest transform is kept exactly as the file declares it. In particular the
     * asset's root conversion node carries a 0.01 scale that converts centimetre rig units to
     * metres, and the inverse bind matrices are written to match it. Cancelling that scale -
     * which an older loader does by name for a differently exported asset - multiplies the whole
     * palette by 100 and throws the model across the world.
     */
    public float[][] palette(String clipName, float timeSeconds, RootPolicy policy) {
        Clip clip = clipName == null ? null : clips.get(clipName);
        float[][] translation = new float[nodes.length][];
        float[][] rotation = new float[nodes.length][];
        float[][] scale = new float[nodes.length][];
        for (int i = 0; i < nodes.length; i++) {
            translation[i] = nodes[i].translation().clone();
            rotation[i] = nodes[i].rotation().clone();
            scale[i] = nodes[i].scale().clone();
        }
        if (clip != null) {
            float time = Math.max(0f, Math.min(clip.durationSeconds(), timeSeconds));
            float[] scratch = new float[4];
            for (Channel channel : clip.channels()) {
                sample(channel, time, scratch);
                switch (channel.path()) {
                    case "rotation" -> System.arraycopy(scratch, 0, rotation[channel.node()], 0, 4);
                    case "scale" -> System.arraycopy(scratch, 0, scale[channel.node()], 0, 3);
                    case "translation" -> applyTranslation(channel.node(), scratch, translation[channel.node()], policy);
                    default -> { }
                }
            }
        }
        float[][] globals = new float[nodes.length][];
        for (int i = 0; i < nodes.length; i++) global(i, translation, rotation, scale, globals);
        float[][] palette = new float[joints.length][];
        for (int i = 0; i < joints.length; i++) {
            palette[i] = WuxiaMath.multiply(globals[joints[i]], inverseBind[i]);
        }
        return palette;
    }

    private void applyTranslation(int node, float[] sampled, float[] target, RootPolicy policy) {
        if (node != hipNode || policy == RootPolicy.FREE) {
            System.arraycopy(sampled, 0, target, 0, 3);
            return;
        }
        if (policy == RootPolicy.LOCK_HORIZONTAL) target[1] = sampled[1];
        // LOCK_ALL keeps the whole rest translation, so the hips never leave their bind height.
    }

    private void sample(Channel channel, float time, float[] out) {
        float[] times = channel.times();
        int components = channel.path().equals("rotation") ? 4 : 3;
        int high = 1;
        while (high < times.length && times[high] < time) high++;
        high = Math.min(high, times.length - 1);
        int low = Math.max(0, high - 1);
        float blend = channel.step() || times[high] <= times[low]
                ? 0f : (time - times[low]) / (times[high] - times[low]);
        blend = Math.max(0f, Math.min(1f, blend));
        float[] a = new float[components];
        float[] b = new float[components];
        System.arraycopy(channel.values(), low * components, a, 0, components);
        System.arraycopy(channel.values(), high * components, b, 0, components);
        if (components == 4) WuxiaMath.slerp(a, b, blend, out);
        else WuxiaMath.lerp3(a, b, blend, out);
    }

    private float[] global(int index, float[][] t, float[][] r, float[][] s, float[][] globals) {
        if (globals[index] != null) return globals[index];
        float[] local = WuxiaMath.fromTrs(t[index], r[index], s[index]);
        globals[index] = parents[index] < 0
                ? local : WuxiaMath.multiply(global(parents[index], t, r, s, globals), local);
        return globals[index];
    }

    /** Linear-blend skinning of one vertex into {@code out}. */
    public void skinPosition(float[][] palette, int vertex, float[] out) {
        blend(palette, vertex, out, positions, true);
    }

    /** Linear-blend skinning of one normal into {@code out}; not normalised. */
    public void skinNormal(float[][] palette, int vertex, float[] out) {
        blend(palette, vertex, out, normals, false);
    }

    private void blend(float[][] palette, int vertex, float[] out, float[] source, boolean point) {
        int p = vertex * 3, j = vertex * 4;
        out[0] = out[1] = out[2] = 0f;
        float[] scratch = new float[3];
        for (int influence = 0; influence < 4; influence++) {
            float weight = vertexWeights[j + influence];
            int joint = vertexJoints[j + influence];
            if (weight <= 0f || joint < 0 || joint >= palette.length) continue;
            if (point) WuxiaMath.transformPoint(palette[joint], source[p], source[p + 1], source[p + 2], scratch);
            else WuxiaMath.transformDirection(palette[joint], source[p], source[p + 1], source[p + 2], scratch);
            out[0] += weight * scratch[0];
            out[1] += weight * scratch[1];
            out[2] += weight * scratch[2];
        }
    }

    /** Axis-aligned bounds of the mesh after real CPU skinning, in metres. */
    public Bounds skinnedBounds(String clip, float timeSeconds, RootPolicy policy) {
        float[][] palette = palette(clip, timeSeconds, policy);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        float[] point = new float[3];
        int vertices = vertexCount();
        for (int vertex = 0; vertex < vertices; vertex++) {
            skinPosition(palette, vertex, point);
            if (!Float.isFinite(point[0]) || !Float.isFinite(point[1]) || !Float.isFinite(point[2])) {
                return new Bounds(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
            }
            minX = Math.min(minX, point[0]); maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]);
            minZ = Math.min(minZ, point[2]); maxZ = Math.max(maxZ, point[2]);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** The world matrix of one joint at a clip time, for anchoring a held item to the hand. */
    public float[] jointMatrix(String clip, float timeSeconds, RootPolicy policy, String jointName) {
        int index = jointIndex(jointName);
        if (index < 0) return null;
        // palette = global * inverseBind, so global = palette * bindPose. Recomputing the global
        // directly is cheaper and avoids inverting anything.
        float[][] palette = palette(clip, timeSeconds, policy);
        float[] bind = invertRigid(inverseBind[index]);
        return bind == null ? null : WuxiaMath.multiply(palette[index], bind);
    }

    /**
     * Inverse of a matrix that is a rotation, a uniform-ish scale and a translation. glTF inverse
     * bind matrices are always of this form, so a general inverse is not needed.
     */
    static float[] invertRigid(float[] m) {
        float sx = length(m[0], m[1], m[2]);
        float sy = length(m[4], m[5], m[6]);
        float sz = length(m[8], m[9], m[10]);
        if (sx < 1.0e-9f || sy < 1.0e-9f || sz < 1.0e-9f) return null;
        float[] out = new float[16];
        // Transpose the rotation and divide out each axis scale twice: once to normalise the
        // source axis, once to invert the scale itself.
        float[] inv = {1f / (sx * sx), 1f / (sy * sy), 1f / (sz * sz)};
        out[0] = m[0] * inv[0]; out[4] = m[1] * inv[1]; out[8] = m[2] * inv[2];
        out[1] = m[4] * inv[0]; out[5] = m[5] * inv[1]; out[9] = m[6] * inv[2];
        out[2] = m[8] * inv[0]; out[6] = m[9] * inv[1]; out[10] = m[10] * inv[2];
        out[12] = -(out[0] * m[12] + out[4] * m[13] + out[8] * m[14]);
        out[13] = -(out[1] * m[12] + out[5] * m[13] + out[9] * m[14]);
        out[14] = -(out[2] * m[12] + out[6] * m[13] + out[10] * m[14]);
        out[15] = 1f;
        return out;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float width() { return maxX - minX; }
        public float height() { return maxY - minY; }
        public float depth() { return maxZ - minZ; }
        public boolean finite() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ);
        }
        @Override public String toString() {
            return String.format(java.util.Locale.ROOT, "%.4f x %.4f x %.4f (floor %+.4f)",
                    width(), height(), depth(), minY);
        }
    }
}
