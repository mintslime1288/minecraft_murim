package com.murimrpg.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Focused GLB 2.0 CPU skinning loader for the licensed Meshy biped walking asset. */
public final class GlbAnimatedPlayerMesh {
    private static final int MAGIC = 0x46546C67, JSON = 0x4E4F534A, BIN = 0x004E4942;
    private final float[] positions, normals, uvs, weights;
    private final int[] joints, indices, skinJoints, parents;
    private final Node[] nodes;
    private final Matrix4f[] inverseBind;
    private final List<Track> tracks;
    private final int meshNode;
    private final float duration;

    private GlbAnimatedPlayerMesh(float[] positions, float[] normals, float[] uvs, int[] joints,
                                  float[] weights, int[] indices, int[] skinJoints, Matrix4f[] inverseBind,
                                  Node[] nodes, int[] parents, List<Track> tracks, int meshNode, float duration) {
        this.positions = positions; this.normals = normals; this.uvs = uvs; this.joints = joints;
        this.weights = weights; this.indices = indices; this.skinJoints = skinJoints;
        this.inverseBind = inverseBind; this.nodes = nodes; this.parents = parents;
        this.tracks = tracks; this.meshNode = meshNode; this.duration = duration;
    }

    public static Loaded load(InputStream input, String label) throws IOException {
        try {
            ByteBuffer glb = ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (glb.remaining() < 20 || glb.getInt() != MAGIC || glb.getInt() != 2) throw new IOException("Invalid GLB: " + label);
            glb.getInt();
            JsonObject root = null; byte[] binary = null;
            while (glb.remaining() >= 8) {
                int length = glb.getInt(), type = glb.getInt();
                if (length < 0 || length > glb.remaining()) throw new IOException("Invalid GLB chunk: " + label);
                byte[] data = new byte[length]; glb.get(data);
                if (type == JSON) root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8).trim()).getAsJsonObject();
                else if (type == BIN) binary = data;
            }
            if (root == null || binary == null) throw new IOException("Missing GLB JSON/BIN: " + label);
            JsonArray nodeJson = root.getAsJsonArray("nodes");
            Node[] nodes = new Node[nodeJson.size()]; int[] parents = new int[nodes.length]; Arrays.fill(parents, -1);
            int meshNode = -1;
            for (int i = 0; i < nodes.length; i++) {
                JsonObject value = nodeJson.get(i).getAsJsonObject();
                nodes[i] = Node.parse(value);
                if (value.has("mesh")) meshNode = i;
                if (value.has("children")) for (JsonElement child : value.getAsJsonArray("children")) parents[child.getAsInt()] = i;
            }
            if (meshNode < 0) throw new IOException("Animated GLB has no mesh node: " + label);
            JsonObject primitive = root.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");
            float[] positions = floats(root, binary, attributes.get("POSITION").getAsInt(), 3);
            float[] normals = floats(root, binary, attributes.get("NORMAL").getAsInt(), 3);
            float[] uvs = floats(root, binary, attributes.get("TEXCOORD_0").getAsInt(), 2);
            int[] joints = integers(root, binary, attributes.get("JOINTS_0").getAsInt(), 4);
            float[] weights = floats(root, binary, attributes.get("WEIGHTS_0").getAsInt(), 4);
            int[] indices = integers(root, binary, primitive.get("indices").getAsInt(), 1);
            JsonObject skin = root.getAsJsonArray("skins").get(0).getAsJsonObject();
            int[] skinJoints = skin.getAsJsonArray("joints").asList().stream().mapToInt(JsonElement::getAsInt).toArray();
            float[] inverseValues = floats(root, binary, skin.get("inverseBindMatrices").getAsInt(), 16);
            Matrix4f[] inverseBind = new Matrix4f[skinJoints.length];
            for (int i = 0; i < inverseBind.length; i++) inverseBind[i] = matrix(inverseValues, i * 16);
            List<Track> tracks = new ArrayList<>(); float duration = 0f;
            JsonObject animation = root.getAsJsonArray("animations").get(0).getAsJsonObject();
            JsonArray samplers = animation.getAsJsonArray("samplers");
            for (JsonElement channelElement : animation.getAsJsonArray("channels")) {
                JsonObject channel = channelElement.getAsJsonObject();
                if (!channel.has("target")) continue;
                JsonObject target = channel.getAsJsonObject("target");
                if (!target.has("node") || !target.has("path")) continue;
                JsonObject sampler = samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();
                String path = target.get("path").getAsString();
                int components = path.equals("rotation") ? 4 : 3;
                float[] times = floats(root, binary, sampler.get("input").getAsInt(), 1);
                float[] values = floats(root, binary, sampler.get("output").getAsInt(), components);
                tracks.add(new Track(target.get("node").getAsInt(), path, times, values,
                        sampler.has("interpolation") && sampler.get("interpolation").getAsString().equals("STEP")));
                if (times.length > 0) duration = Math.max(duration, times[times.length - 1]);
            }
            return new Loaded(new GlbAnimatedPlayerMesh(positions, normals, uvs, joints, weights, indices,
                    skinJoints, inverseBind, nodes, parents, tracks, meshNode, Math.max(.001f, duration)),
                    image(root, binary, primitive));
        } catch (RuntimeException error) { throw new IOException("Could not parse animated GLB: " + label, error); }
    }

    public void render(PoseStack.Pose pose, VertexConsumer out, int light, float walkPosition,
            float xScale, float yScale, float zScale) {
        renderInternal(pose, out, light, poseAt(timeForWalkPosition(walkPosition)).palette, null, 0f,
                xScale, yScale, zScale);
    }

    /**
     * Blends two clips with the same exported mesh and skeleton. The blend is used only during
     * the short walk/run transition; all other states keep their own renderer or vanilla fallback.
     */
    public void renderBlended(PoseStack.Pose pose, VertexConsumer out, int light, float walkPosition,
            GlbAnimatedPlayerMesh other, float otherWalkPosition, float otherWeight,
            float xScale, float yScale, float zScale) {
        if (other == null || other.vertexCount() != vertexCount() || other.jointCount() != jointCount()
                || other.triangleCount() != triangleCount()) {
            render(pose, out, light, walkPosition, xScale, yScale, zScale);
            return;
        }
        renderInternal(pose, out, light, poseAt(timeForWalkPosition(walkPosition)).palette,
                other.poseAt(other.timeForWalkPosition(otherWalkPosition)).palette,
                Math.clamp(otherWeight, 0f, 1f), xScale, yScale, zScale);
    }

    private float timeForWalkPosition(float walkPosition) {
        return Math.floorMod((long)(walkPosition * 1000f * duration / 9.424778f), (long)(duration * 1000f)) / 1000f;
    }

    private void renderInternal(PoseStack.Pose pose, VertexConsumer out, int light, Matrix4f[] palette,
            Matrix4f[] otherPalette, float otherWeight, float xScale, float yScale, float zScale) {
        Vector3f finalPosition = new Vector3f(), finalNormal = new Vector3f();
        Vector3f transformed = new Vector3f(), transformedNormal = new Vector3f();
        Vector3f otherPosition = new Vector3f(), otherNormal = new Vector3f();
        for (int triangle = 0; triangle + 2 < indices.length; triangle += 3) {
            emit(pose, out, light, palette, otherPalette, otherWeight, indices[triangle], finalPosition, finalNormal, otherPosition, otherNormal, transformed, transformedNormal, xScale, yScale, zScale);
            emit(pose, out, light, palette, otherPalette, otherWeight, indices[triangle + 1], finalPosition, finalNormal, otherPosition, otherNormal, transformed, transformedNormal, xScale, yScale, zScale);
            emit(pose, out, light, palette, otherPalette, otherWeight, indices[triangle + 2], finalPosition, finalNormal, otherPosition, otherNormal, transformed, transformedNormal, xScale, yScale, zScale);
            emit(pose, out, light, palette, otherPalette, otherWeight, indices[triangle + 2], finalPosition, finalNormal, otherPosition, otherNormal, transformed, transformedNormal, xScale, yScale, zScale);
        }
    }

    public int vertexCount() { return positions.length / 3; }
    public int triangleCount() { return indices.length / 3; }
    public int jointCount() { return skinJoints.length; }
    public float durationSeconds() { return duration; }
    public Bounds bounds() {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        for(int i=0;i<positions.length;i+=3){minX=Math.min(minX,positions[i]);maxX=Math.max(maxX,positions[i]);
            minY=Math.min(minY,positions[i+1]);maxY=Math.max(maxY,positions[i+1]);minZ=Math.min(minZ,positions[i+2]);maxZ=Math.max(maxZ,positions[i+2]);}
        return new Bounds(minX,minY,minZ,maxX,maxY,maxZ);
    }

    /**
     * Returns the real CPU-skinned bounds for a clip time. This is deliberately separate
     * from {@link #bounds()}, which only describes the unskinned POSITION accessor. A GLB
     * can have perfectly normal POSITION bounds while a bad bind-matrix conversion stretches
     * its limbs across the world.
     */
    public Bounds skinnedBounds(float timeSeconds) {
        PoseData pose = poseAt(timeSeconds);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        Vector3f position = new Vector3f(), scratch = new Vector3f();
        for (int vertex = 0; vertex < vertexCount(); vertex++) {
            skinPosition(pose.palette, vertex, position, scratch);
            if (!Float.isFinite(position.x) || !Float.isFinite(position.y) || !Float.isFinite(position.z)) {
                return new Bounds(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
            }
            minX = Math.min(minX, position.x); minY = Math.min(minY, position.y); minZ = Math.min(minZ, position.z);
            maxX = Math.max(maxX, position.x); maxY = Math.max(maxY, position.y); maxZ = Math.max(maxZ, position.z);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private PoseData poseAt(float timeSeconds) {
        float time = Math.max(0f, Math.min(duration, timeSeconds));
        Vector3f[] translations = new Vector3f[nodes.length]; Quaternionf[] rotations = new Quaternionf[nodes.length]; Vector3f[] scales = new Vector3f[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            translations[i] = new Vector3f(nodes[i].translation);
            rotations[i] = new Quaternionf(nodes[i].rotation);
            scales[i] = new Vector3f(nodes[i].scale);
            // Meshy's Blender export places both the mesh and armature under a 0.01
            // conversion node, but its inverse-bind matrices are already written in the
            // unscaled armature space. Applying that node here multiplies every joint by
            // 0.01 and then cancelling only the mesh node stretches the palette ~100x.
            if ("Armature".equals(nodes[i].name)) scales[i].set(1f, 1f, 1f);
        }
        for (Track track : tracks) track.apply(time, nodes[track.node].name, translations[track.node], rotations[track.node], scales[track.node]);
        Matrix4f[] globals = new Matrix4f[nodes.length];
        for (int i = 0; i < nodes.length; i++) global(i, translations, rotations, scales, globals);
        Matrix4f[] palette = new Matrix4f[skinJoints.length];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Matrix4f(globals[skinJoints[i]]).mul(inverseBind[i]);
        }
        return new PoseData(palette);
    }

    private Matrix4f global(int index, Vector3f[] t, Quaternionf[] r, Vector3f[] s, Matrix4f[] globals) {
        if (globals[index] != null) return globals[index];
        Matrix4f local = new Matrix4f().translate(t[index]).rotate(r[index]).scale(s[index]);
        globals[index] = parents[index] < 0 ? local : new Matrix4f(global(parents[index], t, r, s, globals)).mul(local);
        return globals[index];
    }

    private void emit(PoseStack.Pose pose, VertexConsumer out, int light, Matrix4f[] palette,
            Matrix4f[] otherPalette, float otherWeight, int vertex, Vector3f finalPosition, Vector3f finalNormal,
            Vector3f otherPosition, Vector3f otherNormal, Vector3f transformed, Vector3f transformedNormal,
            float xScale, float yScale, float zScale) {
        int u = vertex * 2;
        skinPosition(palette, vertex, finalPosition, transformed);
        skinNormal(palette, vertex, finalNormal, transformedNormal);
        if (otherPalette != null && otherWeight > 0f) {
            skinPosition(otherPalette, vertex, otherPosition, transformed);
            skinNormal(otherPalette, vertex, otherNormal, transformedNormal);
            finalPosition.lerp(otherPosition, otherWeight);
            finalNormal.lerp(otherNormal, otherWeight);
        }
        if (finalNormal.lengthSquared() > .000001f) finalNormal.normalize(); else finalNormal.set(0, 1, 0);
        out.addVertex(pose, finalPosition.x * xScale, finalPosition.y * yScale, finalPosition.z * zScale).setColor(255, 255, 255, 255)
                .setUv(uvs[u], 1f - uvs[u + 1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, finalNormal.x, finalNormal.y, finalNormal.z);
    }

    private void skinPosition(Matrix4f[] palette, int vertex, Vector3f target, Vector3f scratch) {
        int p = vertex * 3, j = vertex * 4;
        target.zero();
        for (int influence = 0; influence < 4; influence++) {
            float weight = weights[j + influence]; int joint = joints[j + influence];
            if (weight <= 0f || joint < 0 || joint >= palette.length) continue;
            scratch.set(positions[p], positions[p + 1], positions[p + 2]);
            palette[joint].transformPosition(scratch);
            target.fma(weight, scratch);
        }
    }

    private void skinNormal(Matrix4f[] palette, int vertex, Vector3f target, Vector3f transformed) {
        int p = vertex * 3, j = vertex * 4;
        target.zero();
        for (int influence = 0; influence < 4; influence++) {
            float weight = weights[j + influence]; int joint = joints[j + influence];
            if (weight <= 0f || joint < 0 || joint >= palette.length) continue;
            transformed.set(normals[p], normals[p + 1], normals[p + 2]);
            palette[joint].transformDirection(transformed);
            target.fma(weight, transformed);
        }
    }

    /** glTF MAT4 data is column-major; JOML's array setter accepts that layout directly. */
    private static Matrix4f matrix(float[] values, int offset) { return new Matrix4f().set(values, offset); }
    private static float[] floats(JsonObject root, byte[] bin, int index, int components) throws IOException { Accessor a = accessor(root,index,components); if(a.type!=5126) throw new IOException("Expected FLOAT accessor"); float[] out=new float[a.count*components]; ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN); for(int row=0;row<a.count;row++)for(int c=0;c<components;c++)out[row*components+c]=b.getFloat(a.offset+row*a.stride+c*4); return out; }
    private static int[] integers(JsonObject root, byte[] bin, int index, int components) throws IOException { Accessor a=accessor(root,index,components); ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN); int[] out=new int[a.count*components]; int size=switch(a.type){case 5121->1;case 5123->2;case 5125->4;default->throw new IOException("Unsupported integer accessor");}; for(int row=0;row<a.count;row++)for(int c=0;c<components;c++){int o=a.offset+row*a.stride+c*size;out[row*components+c]=size==1?b.get(o)&255:size==2?b.getShort(o)&65535:b.getInt(o);} return out; }
    private static Accessor accessor(JsonObject root,int index,int components)throws IOException{JsonObject a=root.getAsJsonArray("accessors").get(index).getAsJsonObject();JsonObject v=root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();int actual=switch(a.get("type").getAsString()){case"SCALAR"->1;case"VEC2"->2;case"VEC3"->3;case"VEC4"->4;case"MAT4"->16;default->0;};if(actual!=components)throw new IOException("Accessor component mismatch");int type=a.get("componentType").getAsInt(),size=switch(type){case 5121->1;case 5123->2;case 5125,5126->4;default->throw new IOException("Unsupported component type");};int stride=v.has("byteStride")?v.get("byteStride").getAsInt():size*components;int offset=(v.has("byteOffset")?v.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);return new Accessor(a.get("count").getAsInt(),type,offset,stride);}
    private static byte[] image(JsonObject root,byte[] bin,JsonObject primitive)throws IOException{JsonObject material=root.getAsJsonArray("materials").get(primitive.get("material").getAsInt()).getAsJsonObject();JsonObject pbr=material.getAsJsonObject("pbrMetallicRoughness");int texture=pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();int source=root.getAsJsonArray("textures").get(texture).getAsJsonObject().get("source").getAsInt();JsonObject image=root.getAsJsonArray("images").get(source).getAsJsonObject();JsonObject view=root.getAsJsonArray("bufferViews").get(image.get("bufferView").getAsInt()).getAsJsonObject();int offset=view.has("byteOffset")?view.get("byteOffset").getAsInt():0,length=view.get("byteLength").getAsInt();return Arrays.copyOfRange(bin,offset,offset+length);}

    private record Accessor(int count,int type,int offset,int stride){}
    private record Node(String name,Vector3f translation,Quaternionf rotation,Vector3f scale){static Node parse(JsonObject n){return new Node(n.has("name")?n.get("name").getAsString():"",vec3(n,"translation",0,0,0),quat(n),vec3(n,"scale",1,1,1));}private static Vector3f vec3(JsonObject n,String key,float x,float y,float z){if(!n.has(key))return new Vector3f(x,y,z);JsonArray a=n.getAsJsonArray(key);return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}private static Quaternionf quat(JsonObject n){if(!n.has("rotation"))return new Quaternionf();JsonArray a=n.getAsJsonArray("rotation");return new Quaternionf(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat(),a.get(3).getAsFloat());}}
    private record Track(int node,String path,float[] times,float[] values,boolean step){void apply(float time,String nodeName,Vector3f t,Quaternionf r,Vector3f s){int next=1;while(next<times.length&&times[next]<time)next++;int previous=Math.max(0,next-1);next=Math.min(next,times.length-1);float blend=step||times[next]<=times[previous]?0f:(time-times[previous])/(times[next]-times[previous]);int components=path.equals("rotation")?4:3,a=previous*components,b=next*components;if(path.equals("rotation")){Quaternionf from=new Quaternionf(values[a],values[a+1],values[a+2],values[a+3]),to=new Quaternionf(values[b],values[b+1],values[b+2],values[b+3]);r.set(from).slerp(to,blend);}else{Vector3f from=new Vector3f(values[a],values[a+1],values[a+2]),to=new Vector3f(values[b],values[b+1],values[b+2]);from.lerp(to,blend);if(path.equals("translation")){if(nodeName.equals("Hips")){/* Preserve bind translation: source root motion must not move an in-game player model. */ from.set(t);}t.set(from);}else if(path.equals("scale"))s.set(from);}}}
    public record Loaded(GlbAnimatedPlayerMesh mesh,byte[] baseColorImage){}
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
        public float width(){return maxX-minX;} public float height(){return maxY-minY;} public float depth(){return maxZ-minZ;}
    }
    private record PoseData(Matrix4f[] palette) { }
}
