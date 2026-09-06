package com.murimrpg.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Small, read-only GLB 2.0 loader for static, triangle-based props.
 *
 * <p>This deliberately supports the subset exported by the MurimRPG Meshy pipeline:
 * one mesh, POSITION/NORMAL/TEXCOORD_0 and indexed triangles. It keeps the client
 * renderer free of a broad third-party runtime and rejects unsupported data safely.</p>
 */
public final class GlbStaticMesh {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;

    private final float[] positions;
    private final float[] normals;
    private final float[] uvs;
    private final int[] indices;
    private final float centerX;
    private final float centerZ;
    private final float minY;
    private final float height;

    private GlbStaticMesh(float[] positions, float[] normals, float[] uvs, int[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.uvs = uvs;
        this.indices = indices;
        float minimumX = Float.POSITIVE_INFINITY;
        float maximumX = Float.NEGATIVE_INFINITY;
        float minimumZ = Float.POSITIVE_INFINITY;
        float maximumZ = Float.NEGATIVE_INFINITY;
        float minimumY = Float.POSITIVE_INFINITY;
        float maximumY = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < positions.length; index += 3) {
            minimumX = Math.min(minimumX, positions[index]);
            maximumX = Math.max(maximumX, positions[index]);
            minimumY = Math.min(minimumY, positions[index + 1]);
            maximumY = Math.max(maximumY, positions[index + 1]);
            minimumZ = Math.min(minimumZ, positions[index + 2]);
            maximumZ = Math.max(maximumZ, positions[index + 2]);
        }
        this.centerX = (minimumX + maximumX) * .5f;
        this.centerZ = (minimumZ + maximumZ) * .5f;
        this.minY = minimumY;
        this.height = Math.max(.0001f, maximumY - minimumY);
    }

    public static LoadedGlb load(ResourceManager resources, Identifier location) throws IOException {
        try (InputStream input = resources.open(location)) {
            return load(input, location.toString());
        }
    }

    static LoadedGlb load(InputStream input, String label) throws IOException {
        try {
            ByteBuffer glb = ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (glb.remaining() < 20 || glb.getInt() != GLB_MAGIC || glb.getInt() != 2) {
                throw new IOException("Unsupported GLB header: " + label);
            }
            glb.getInt(); // Declared file length; ByteBuffer bounds are the reliable limit.
            JsonObject root = null;
            byte[] binary = null;
            while (glb.remaining() >= 8) {
                int length = glb.getInt();
                int type = glb.getInt();
                if (length < 0 || length > glb.remaining()) throw new IOException("Invalid GLB chunk length: " + label);
                byte[] data = new byte[length];
                glb.get(data);
                if (type == JSON_CHUNK) root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8).trim()).getAsJsonObject();
                else if (type == BIN_CHUNK) binary = data;
            }
            if (root == null || binary == null) throw new IOException("GLB needs JSON and binary chunks: " + label);

            JsonObject primitive = root.getAsJsonArray("meshes").get(0).getAsJsonObject()
                    .getAsJsonArray("primitives").get(0).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            float[] positions = readFloats(root, binary, attributes.get("POSITION").getAsInt(), 3);
            float[] normals = attributes.has("NORMAL") ? readFloats(root, binary, attributes.get("NORMAL").getAsInt(), 3) : null;
            float[] uvs = attributes.has("TEXCOORD_0") ? readFloats(root, binary, attributes.get("TEXCOORD_0").getAsInt(), 2) : null;
            int[] indices = primitive.has("indices") ? readIndices(root, binary, primitive.get("indices").getAsInt()) : sequentialIndices(positions.length / 3);
            if (normals == null) normals = defaultNormals(positions.length / 3);
            if (uvs == null) uvs = new float[(positions.length / 3) * 2];
            if (positions.length / 3 != normals.length / 3 || positions.length / 3 != uvs.length / 2) {
                throw new IOException("GLB attribute counts do not match: " + label);
            }
            return new LoadedGlb(new GlbStaticMesh(positions, normals, uvs, indices), baseColorImage(root, binary, primitive));
        } catch (RuntimeException error) {
            throw new IOException("Could not parse GLB: " + label, error);
        }
    }

    public void render(PoseStack.Pose pose, VertexConsumer vertices, int packedLight) {
        for (int index : indices) {
            if (index < 0 || index * 3 + 2 >= positions.length) continue;
            int position = index * 3;
            int uv = index * 2;
            vertices.addVertex(pose, positions[position], positions[position + 1], positions[position + 2])
                    .setColor(255, 255, 255, 255)
                    // glTF UVs use a bottom-left origin; Minecraft texture sampling uses a
                    // top-left origin. The V flip is essential for a packed Meshy texture atlas.
                    .setUv(uvs[uv], 1.0f - uvs[uv + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, normals[position], normals[position + 1], normals[position + 2]);
        }
    }

    /**
     * Renders a vertically-grained material around a log-shaped mesh. Some AI-generated GLB
     * exports contain a packed UV atlas that is valid only for their preview renderer; using
     * that atlas in Minecraft creates disconnected texture fragments. This deterministic,
     * cylindrical mapping keeps the supplied high-detail geometry while giving it one continuous
     * bark surface. It is deliberately used only by the training-log renderer.
     */
    public void renderWithCylindricalBark(PoseStack.Pose pose, VertexConsumer vertices, int packedLight) {
        for (int index : indices) {
            if (index < 0 || index * 3 + 2 >= positions.length) continue;
            int position = index * 3;
            float x = positions[position];
            float y = positions[position + 1];
            float z = positions[position + 2];
            float u = (float) (Math.atan2(z - centerZ, x - centerX) / (Math.PI * 2.0) + .5);
            float v = 1.0f - (y - minY) / height;
            vertices.addVertex(pose, x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, normals[position], normals[position + 1], normals[position + 2]);
        }
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    private static float[] readFloats(JsonObject root, byte[] binary, int accessorIndex, int components) throws IOException {
        Accessor accessor = accessor(root, accessorIndex, components);
        if (accessor.componentType != 5126) throw new IOException("Only FLOAT vertex attributes are supported");
        float[] values = new float[accessor.count * components];
        ByteBuffer buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int row = 0; row < accessor.count; row++) {
            int base = accessor.offset + row * accessor.stride;
            for (int component = 0; component < components; component++) values[row * components + component] = buffer.getFloat(base + component * 4);
        }
        return values;
    }

    private static int[] readIndices(JsonObject root, byte[] binary, int accessorIndex) throws IOException {
        Accessor accessor = accessor(root, accessorIndex, 1);
        int[] values = new int[accessor.count];
        ByteBuffer buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        int size = switch (accessor.componentType) { case 5121 -> 1; case 5123 -> 2; case 5125 -> 4; default -> throw new IOException("Unsupported GLB index type"); };
        for (int row = 0; row < accessor.count; row++) {
            int offset = accessor.offset + row * accessor.stride;
            values[row] = switch (size) { case 1 -> buffer.get(offset) & 0xFF; case 2 -> buffer.getShort(offset) & 0xFFFF; default -> buffer.getInt(offset); };
        }
        return values;
    }

    private static Accessor accessor(JsonObject root, int index, int components) throws IOException {
        JsonObject accessor = root.getAsJsonArray("accessors").get(index).getAsJsonObject();
        JsonObject view = root.getAsJsonArray("bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        String type = accessor.get("type").getAsString();
        int actualComponents = switch (type) { case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; default -> throw new IOException("Unsupported GLB accessor type: " + type); };
        if (actualComponents != components) throw new IOException("Unexpected GLB accessor component count");
        int componentSize = switch (accessor.get("componentType").getAsInt()) { case 5121 -> 1; case 5123 -> 2; case 5125, 5126 -> 4; default -> throw new IOException("Unsupported GLB component type"); };
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : componentSize * components;
        int offset = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0) + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
        return new Accessor(accessor.get("count").getAsInt(), accessor.get("componentType").getAsInt(), offset, stride);
    }

    private static byte[] baseColorImage(JsonObject root, byte[] binary, JsonObject primitive) throws IOException {
        if (!primitive.has("material")) return null;
        JsonObject material = root.getAsJsonArray("materials").get(primitive.get("material").getAsInt()).getAsJsonObject();
        if (!material.has("pbrMetallicRoughness")) return null;
        JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
        if (!pbr.has("baseColorTexture")) return null;
        int textureIndex = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
        int imageIndex = root.getAsJsonArray("textures").get(textureIndex).getAsJsonObject().get("source").getAsInt();
        JsonObject image = root.getAsJsonArray("images").get(imageIndex).getAsJsonObject();
        if (!image.has("bufferView")) return null;
        JsonObject view = root.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();
        int offset = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int length = view.get("byteLength").getAsInt();
        if (offset < 0 || length < 1 || offset + length > binary.length) throw new IOException("Invalid embedded GLB image range");
        return Arrays.copyOfRange(binary, offset, offset + length);
    }

    private static int[] sequentialIndices(int vertexCount) { int[] result = new int[vertexCount]; for (int i = 0; i < result.length; i++) result[i] = i; return result; }
    private static float[] defaultNormals(int vertexCount) { float[] result = new float[vertexCount * 3]; for (int i = 0; i < vertexCount; i++) result[i * 3 + 1] = 1f; return result; }

    private record Accessor(int count, int componentType, int offset, int stride) { }
    public record LoadedGlb(GlbStaticMesh mesh, byte[] baseColorImage) { }
}
