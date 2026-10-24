package com.murimrpg.client.render.wuxia;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a binary glTF into a {@link WuxiaRig}, keeping every animation instead of only the first.
 *
 * <p>Separate from the older {@code GlbAnimatedPlayerMesh} on purpose. That class has its own
 * tests and its own single-clip contract; changing it to serve a six-clip asset would put a
 * working path at risk for no gain. This reader uses Gson and the JDK only, so the parse and the
 * rig it produces can be exercised without a Minecraft classpath.
 */
public final class WuxiaGlbReader {

    private static final int MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private WuxiaGlbReader() {
    }

    /** A parsed asset: the rig plus the embedded base colour image bytes. */
    public record Asset(WuxiaRig rig, byte[] baseColorImage, String imageMimeType,
                        int textureWidth, int textureHeight) { }

    public static Asset read(InputStream input, String label) throws IOException {
        try {
            return parse(ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN), label);
        } catch (RuntimeException error) {
            throw new IOException("Could not parse " + label, error);
        }
    }

    private static Asset parse(ByteBuffer glb, String label) throws IOException {
        if (glb.remaining() < 20 || glb.getInt() != MAGIC || glb.getInt() != 2) {
            throw new IOException("Not a glTF 2.0 binary file: " + label);
        }
        glb.getInt();
        JsonObject root = null;
        byte[] binary = null;
        while (glb.remaining() >= 8) {
            int length = glb.getInt();
            int type = glb.getInt();
            if (length < 0 || length > glb.remaining()) throw new IOException("Truncated chunk in " + label);
            byte[] data = new byte[length];
            glb.get(data);
            if (type == CHUNK_JSON) {
                root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8).trim()).getAsJsonObject();
            } else if (type == CHUNK_BIN) {
                binary = data;
            }
        }
        if (root == null || binary == null) throw new IOException("Missing JSON or BIN chunk in " + label);

        JsonArray nodeJson = root.getAsJsonArray("nodes");
        WuxiaRig.Node[] nodes = new WuxiaRig.Node[nodeJson.size()];
        int[] parents = new int[nodes.length];
        Arrays.fill(parents, -1);
        int meshNode = -1;
        for (int i = 0; i < nodes.length; i++) {
            JsonObject value = nodeJson.get(i).getAsJsonObject();
            nodes[i] = new WuxiaRig.Node(
                    value.has("name") ? value.get("name").getAsString() : "",
                    vector(value, "translation", 0f, 0f, 0f),
                    quaternion(value),
                    vector(value, "scale", 1f, 1f, 1f));
            if (value.has("mesh")) meshNode = i;
            if (value.has("children")) {
                for (JsonElement child : value.getAsJsonArray("children")) parents[child.getAsInt()] = i;
            }
        }
        if (meshNode < 0) throw new IOException("No mesh node in " + label);

        JsonObject primitive = root.getAsJsonArray("meshes").get(0).getAsJsonObject()
                .getAsJsonArray("primitives").get(0).getAsJsonObject();
        if (primitive.has("mode") && primitive.get("mode").getAsInt() != 4) {
            throw new IOException("Only triangle primitives are supported: " + label);
        }
        JsonObject attributes = primitive.getAsJsonObject("attributes");
        for (String required : new String[]{"POSITION", "NORMAL", "TEXCOORD_0", "JOINTS_0", "WEIGHTS_0"}) {
            if (!attributes.has(required)) throw new IOException("Missing " + required + " in " + label);
        }
        float[] positions = floats(root, binary, attributes.get("POSITION").getAsInt(), 3);
        float[] normals = floats(root, binary, attributes.get("NORMAL").getAsInt(), 3);
        float[] uvs = floats(root, binary, attributes.get("TEXCOORD_0").getAsInt(), 2);
        int[] vertexJoints = integers(root, binary, attributes.get("JOINTS_0").getAsInt(), 4);
        float[] vertexWeights = floats(root, binary, attributes.get("WEIGHTS_0").getAsInt(), 4);
        int[] indices = integers(root, binary, primitive.get("indices").getAsInt(), 1);

        if (!root.has("skins") || root.getAsJsonArray("skins").isEmpty()) {
            throw new IOException("Asset is not skinned: " + label);
        }
        JsonObject skin = root.getAsJsonArray("skins").get(0).getAsJsonObject();
        int[] joints = skin.getAsJsonArray("joints").asList().stream().mapToInt(JsonElement::getAsInt).toArray();
        float[] inverseValues = floats(root, binary, skin.get("inverseBindMatrices").getAsInt(), 16);
        if (inverseValues.length != joints.length * 16) {
            throw new IOException("inverseBindMatrices count does not match joint count in " + label);
        }
        float[][] inverseBind = new float[joints.length][];
        for (int i = 0; i < joints.length; i++) {
            inverseBind[i] = Arrays.copyOfRange(inverseValues, i * 16, i * 16 + 16);
        }

        Map<String, WuxiaRig.Clip> clips = new LinkedHashMap<>();
        if (root.has("animations")) {
            JsonArray animations = root.getAsJsonArray("animations");
            for (int a = 0; a < animations.size(); a++) {
                JsonObject animation = animations.get(a).getAsJsonObject();
                String name = animation.has("name") ? animation.get("name").getAsString() : "clip_" + a;
                JsonArray samplers = animation.getAsJsonArray("samplers");
                List<WuxiaRig.Channel> channels = new ArrayList<>();
                float duration = 0f;
                for (JsonElement element : animation.getAsJsonArray("channels")) {
                    JsonObject channel = element.getAsJsonObject();
                    if (!channel.has("target")) continue;
                    JsonObject target = channel.getAsJsonObject("target");
                    if (!target.has("node") || !target.has("path")) continue;
                    String path = target.get("path").getAsString();
                    if (path.equals("weights")) continue;
                    JsonObject sampler = samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();
                    String interpolation = sampler.has("interpolation")
                            ? sampler.get("interpolation").getAsString() : "LINEAR";
                    if (interpolation.equals("CUBICSPLINE")) {
                        throw new IOException("CUBICSPLINE sampler is not supported: " + label + " / " + name);
                    }
                    float[] times = floats(root, binary, sampler.get("input").getAsInt(), 1);
                    float[] values = floats(root, binary, sampler.get("output").getAsInt(),
                            path.equals("rotation") ? 4 : 3);
                    if (times.length == 0) continue;
                    channels.add(new WuxiaRig.Channel(target.get("node").getAsInt(), path, times, values,
                            interpolation.equals("STEP")));
                    duration = Math.max(duration, times[times.length - 1]);
                }
                clips.put(name, new WuxiaRig.Clip(name, Math.max(.001f, duration), List.copyOf(channels)));
            }
        }

        WuxiaRig rig = new WuxiaRig(nodes, parents, joints, inverseBind, positions, normals, uvs,
                vertexJoints, vertexWeights, indices, clips);
        return image(root, binary, primitive, rig);
    }

    private static Asset image(JsonObject root, byte[] binary, JsonObject primitive, WuxiaRig rig)
            throws IOException {
        JsonObject material = root.getAsJsonArray("materials")
                .get(primitive.get("material").getAsInt()).getAsJsonObject();
        JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
        int texture = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
        int source = root.getAsJsonArray("textures").get(texture).getAsJsonObject().get("source").getAsInt();
        JsonObject image = root.getAsJsonArray("images").get(source).getAsJsonObject();
        JsonObject view = root.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();
        int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int length = view.get("byteLength").getAsInt();
        byte[] bytes = Arrays.copyOfRange(binary, offset, offset + length);
        String mime = image.has("mimeType") ? image.get("mimeType").getAsString() : "image/png";
        int[] size = pngSize(bytes);
        return new Asset(rig, bytes, mime, size[0], size[1]);
    }

    /** Reads width and height out of a PNG IHDR so the texture cost can be reported before upload. */
    static int[] pngSize(byte[] png) {
        if (png.length < 24 || (png[0] & 255) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            return new int[]{0, 0};
        }
        ByteBuffer buffer = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
        return new int[]{buffer.getInt(16), buffer.getInt(20)};
    }

    private record Accessor(int count, int componentType, int offset, int stride) { }

    private static Accessor accessor(JsonObject root, int index, int components) throws IOException {
        JsonObject a = root.getAsJsonArray("accessors").get(index).getAsJsonObject();
        if (!a.has("bufferView")) throw new IOException("Sparse or view-less accessors are not supported");
        JsonObject view = root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int actual = switch (a.get("type").getAsString()) {
            case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; case "MAT4" -> 16;
            default -> 0;
        };
        if (actual != components) throw new IOException("Accessor component mismatch: expected " + components);
        int componentType = a.get("componentType").getAsInt();
        int size = switch (componentType) {
            case 5120, 5121 -> 1; case 5122, 5123 -> 2; case 5125, 5126 -> 4;
            default -> throw new IOException("Unsupported componentType " + componentType);
        };
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : size * components;
        int offset = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
                + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
        return new Accessor(a.get("count").getAsInt(), componentType, offset, stride);
    }

    private static float[] floats(JsonObject root, byte[] bin, int index, int components) throws IOException {
        Accessor a = accessor(root, index, components);
        if (a.componentType() != 5126) throw new IOException("Expected a FLOAT accessor at index " + index);
        float[] out = new float[a.count() * components];
        ByteBuffer buffer = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        for (int row = 0; row < a.count(); row++) {
            for (int c = 0; c < components; c++) {
                out[row * components + c] = buffer.getFloat(a.offset() + row * a.stride() + c * 4);
            }
        }
        return out;
    }

    private static int[] integers(JsonObject root, byte[] bin, int index, int components) throws IOException {
        Accessor a = accessor(root, index, components);
        int size = switch (a.componentType()) {
            case 5121 -> 1; case 5123 -> 2; case 5125 -> 4;
            default -> throw new IOException("Unsupported integer componentType " + a.componentType());
        };
        ByteBuffer buffer = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[a.count() * components];
        for (int row = 0; row < a.count(); row++) {
            for (int c = 0; c < components; c++) {
                int o = a.offset() + row * a.stride() + c * size;
                out[row * components + c] = size == 1 ? buffer.get(o) & 255
                        : size == 2 ? buffer.getShort(o) & 65535 : buffer.getInt(o);
            }
        }
        return out;
    }

    private static float[] vector(JsonObject node, String key, float x, float y, float z) {
        if (!node.has(key)) return new float[]{x, y, z};
        JsonArray a = node.getAsJsonArray(key);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] quaternion(JsonObject node) {
        if (!node.has("rotation")) return new float[]{0f, 0f, 0f, 1f};
        JsonArray a = node.getAsJsonArray("rotation");
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(),
                a.get(2).getAsFloat(), a.get(3).getAsFloat()};
    }
}
