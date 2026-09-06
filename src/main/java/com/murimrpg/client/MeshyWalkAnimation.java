package com.murimrpg.client;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

/** Reads rotation-only locomotion clips and retargets them onto Minecraft's articulated skin model. */
public final class MeshyWalkAnimation {
    private static final int MAGIC=0x46546C67, JSON=0x4E4F534A, BIN=0x004E4942;
    public enum Clip { WALK, RUN, FAST_RUN }
    private static final Map<Clip, Identifier> SOURCES = Map.of(
            Clip.WALK, Identifier.fromNamespaceAndPath("murimrpg","models_glb/player/native_walk.glb"),
            Clip.RUN, Identifier.fromNamespaceAndPath("murimrpg","models_glb/player/native_run.glb"),
            Clip.FAST_RUN, Identifier.fromNamespaceAndPath("murimrpg","models_glb/player/native_fast_run.glb"));
    private static final EnumMap<Clip, MeshyWalkAnimation> CURRENT = new EnumMap<>(Clip.class);
    private final Map<String,RotationTrack> rotations;
    private final float duration;
    private final boolean meshyBasis;
    private MeshyWalkAnimation(Map<String,RotationTrack> rotations,float duration,boolean meshyBasis){
        this.rotations=Map.copyOf(rotations);this.duration=duration;this.meshyBasis=meshyBasis;
    }

    public static void load(Minecraft client){
        CURRENT.clear();
        for (var entry : SOURCES.entrySet()) {
            try(var input=client.getResourceManager().open(entry.getValue())){
                var animation=parse(input); CURRENT.put(entry.getKey(), animation);
                LoggerFactory.getLogger("MurimRPG-Walk").info("Loaded Meshy {} animation only: {} mapped bones, {} seconds.",
                        entry.getKey().name().toLowerCase(Locale.ROOT), animation.rotations.size(), animation.duration);
            }catch(Exception error){LoggerFactory.getLogger("MurimRPG-Walk").warn("Meshy {} animation unavailable", entry.getKey(), error);}
        }
    }
    public static MeshyWalkAnimation current(){return CURRENT.get(Clip.WALK);}
    public static boolean loaded(Clip clip) { return CURRENT.containsKey(clip); }

    public static Pose sample(Clip clip, float phase) {
        var animation = CURRENT.get(clip);
        return animation == null ? Pose.ZERO : animation.samplePhase(phase);
    }

    public Pose sample(float walkPosition,float speed){
        Pose pose = samplePhase(walkPosition);
        float normalized=Math.clamp((speed-.02f)/.33f,0f,1f), weight=normalized*normalized*(3f-2f*normalized);
        return pose.scaled(weight);
    }

    private Pose samplePhase(float phase) {
        float time=Math.floorMod((long)(phase*1000f*duration/9.424778f),Math.max(1L,(long)(duration*1000f)))/1000f;
        // Meshy uses a different local basis and a more detailed skeleton. Retarget only
        // the stable upper-limb bend, with tiny torso influence; hands, feet and root
        // translation are intentionally ignored to avoid twisting and foot sliding.
        return new Pose(calibrated("RightArm",time), calibrated("LeftArm",time),
                calibrated("RightForeArm", time), calibrated("LeftForeArm", time),
                calibrated("RightUpLeg",time), calibrated("LeftUpLeg",time),
                calibrated("RightLeg", time), calibrated("LeftLeg", time),
                calibrated("RightFoot", time), calibrated("LeftFoot", time),
                calibratedBody(time));
    }
    private Vector3f calibrated(String name, float time) {
        Vector3f raw = rotation(name, time);
        float swing;
        if (meshyBasis) {
            swing = switch (name) {
                case "RightArm", "RightForeArm", "LeftArm", "LeftForeArm" -> raw.z * .68f;
                case "RightUpLeg", "RightLeg", "LeftUpLeg", "LeftLeg", "RightFoot", "LeftFoot" -> raw.y * .72f;
                default -> 0f;
            };
        } else {
            // Blender source is authored directly in MurimRPG/Minecraft limb space.
            swing = raw.x;
        }
        return finite(new Vector3f(swing, 0f, 0f), .55f);
    }
    private Vector3f calibratedBody(float time) {
        Vector3f upper = rotation("Spine02", time);
        Vector3f lower = rotation("Spine01", time);
        if (meshyBasis) return finite(upper.mul(.26f).add(lower.mul(.12f)), .30f);
        Vector3f combined = upper.mul(.55f).add(lower.mul(.45f));
        return finite(new Vector3f(combined.x, combined.z, combined.y), .30f);
    }
    private Vector3f rotation(String name,float time){
        RotationTrack track=rotations.get(name);if(track==null)return new Vector3f();
        Quaternionf animated=track.sample(time),delta=new Quaternionf(track.rest).conjugate().mul(animated).normalize();
        return delta.getEulerAnglesXYZ(new Vector3f());
    }
    private static Vector3f finite(Vector3f value, float limit) {
        if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z)) return new Vector3f();
        value.x = Math.clamp(value.x, -limit, limit);
        value.y = Math.clamp(value.y, -limit, limit);
        value.z = Math.clamp(value.z, -limit, limit);
        return value;
    }

    static MeshyWalkAnimation parse(InputStream input)throws IOException{
        ByteBuffer glb=ByteBuffer.wrap(input.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        if(glb.remaining()<20||glb.getInt()!=MAGIC||glb.getInt()!=2)throw new IOException("Invalid animated GLB");glb.getInt();
        JsonObject root=null;byte[] binary=null;
        while(glb.remaining()>=8){int length=glb.getInt(),type=glb.getInt();if(length<0||length>glb.remaining())throw new IOException("Invalid GLB chunk");byte[] data=new byte[length];glb.get(data);if(type==JSON)root=JsonParser.parseString(new String(data,StandardCharsets.UTF_8).trim()).getAsJsonObject();else if(type==BIN)binary=data;}
        if(root==null||binary==null)throw new IOException("Missing GLB chunks");
        JsonArray nodes=root.getAsJsonArray("nodes"),samplers=root.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("samplers"),channels=root.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("channels");
        Map<String,RotationTrack> tracks=new HashMap<>();float duration=0f;boolean meshyBasis=false;
        for(JsonElement nodeElement:nodes){JsonObject node=nodeElement.getAsJsonObject();if(node.has("name")&&"Armature".equals(node.get("name").getAsString())&&node.has("scale")){JsonArray scale=node.getAsJsonArray("scale");if(scale.size()>=1&&scale.get(0).getAsFloat()<.1f)meshyBasis=true;}}
        for(JsonElement element:channels){JsonObject channel=element.getAsJsonObject(),target=channel.getAsJsonObject("target");if(!target.has("node")||!"rotation".equals(target.get("path").getAsString()))continue;int nodeIndex=target.get("node").getAsInt();JsonObject node=nodes.get(nodeIndex).getAsJsonObject(),sampler=samplers.get(channel.get("sampler").getAsInt()).getAsJsonObject();float[] times=floats(root,binary,sampler.get("input").getAsInt(),1),values=floats(root,binary,sampler.get("output").getAsInt(),4);Quaternionf rest=quat(node);String name=node.get("name").getAsString();tracks.put(name,new RotationTrack(rest,times,values,sampler.has("interpolation")&&"STEP".equals(sampler.get("interpolation").getAsString())));if(times.length>0)duration=Math.max(duration,times[times.length-1]);}
        for(String required:List.of("RightArm","LeftArm","RightForeArm","LeftForeArm","RightUpLeg","LeftUpLeg","RightLeg","LeftLeg","Spine02"))if(!tracks.containsKey(required))throw new IOException("Missing Meshy animation bone: "+required);
        return new MeshyWalkAnimation(tracks,Math.max(.001f,duration),meshyBasis);
    }
    private static Quaternionf quat(JsonObject node){if(!node.has("rotation"))return new Quaternionf();JsonArray a=node.getAsJsonArray("rotation");return new Quaternionf(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat(),a.get(3).getAsFloat());}
    private static float[] floats(JsonObject root,byte[] binary,int index,int components)throws IOException{JsonObject a=root.getAsJsonArray("accessors").get(index).getAsJsonObject(),view=root.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();if(a.get("componentType").getAsInt()!=5126)throw new IOException("Animation accessor is not FLOAT");int offset=(view.has("byteOffset")?view.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0),stride=view.has("byteStride")?view.get("byteStride").getAsInt():components*4,count=a.get("count").getAsInt();float[] result=new float[count*components];ByteBuffer b=ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);for(int row=0;row<count;row++)for(int c=0;c<components;c++)result[row*components+c]=b.getFloat(offset+row*stride+c*4);return result;}
    private record RotationTrack(Quaternionf rest,float[] times,float[] values,boolean step){Quaternionf sample(float time){int next=1;while(next<times.length&&times[next]<time)next++;int previous=Math.max(0,next-1);next=Math.min(next,times.length-1);float blend=step||times[next]<=times[previous]?0f:(time-times[previous])/(times[next]-times[previous]);int a=previous*4,b=next*4;return new Quaternionf(values[a],values[a+1],values[a+2],values[a+3]).slerp(new Quaternionf(values[b],values[b+1],values[b+2],values[b+3]),blend);}}
    public record Pose(Vector3f rightArm,Vector3f leftArm,Vector3f rightForeArm,Vector3f leftForeArm,
                       Vector3f rightLeg,Vector3f leftLeg,Vector3f rightLowerLeg,Vector3f leftLowerLeg,
                       Vector3f rightFoot,Vector3f leftFoot,Vector3f body){
        public static final Pose ZERO = new Pose(new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(),
                new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f());
        public Pose scaled(float amount) { return new Pose(new Vector3f(rightArm).mul(amount), new Vector3f(leftArm).mul(amount),
                new Vector3f(rightForeArm).mul(amount), new Vector3f(leftForeArm).mul(amount),
                new Vector3f(rightLeg).mul(amount), new Vector3f(leftLeg).mul(amount),
                new Vector3f(rightLowerLeg).mul(amount), new Vector3f(leftLowerLeg).mul(amount),
                new Vector3f(rightFoot).mul(amount), new Vector3f(leftFoot).mul(amount), new Vector3f(body).mul(amount)); }
        static Pose blend(Pose walk, Pose run, float walkWeight, float runWeight) {
            float total = Math.min(1f, walkWeight + runWeight);
            if (total <= .0001f) return ZERO;
            float runMix = runWeight / Math.max(.0001f, walkWeight + runWeight);
            return new Pose(mix(walk.rightArm,run.rightArm,runMix,total), mix(walk.leftArm,run.leftArm,runMix,total),
                    mix(walk.rightForeArm,run.rightForeArm,runMix,total), mix(walk.leftForeArm,run.leftForeArm,runMix,total),
                    mix(walk.rightLeg,run.rightLeg,runMix,total), mix(walk.leftLeg,run.leftLeg,runMix,total),
                    mix(walk.rightLowerLeg,run.rightLowerLeg,runMix,total), mix(walk.leftLowerLeg,run.leftLowerLeg,runMix,total),
                    mix(walk.rightFoot,run.rightFoot,runMix,total), mix(walk.leftFoot,run.leftFoot,runMix,total),
                    mix(walk.body,run.body,runMix,total));
        }
        private static Vector3f mix(Vector3f a, Vector3f b, float mix, float weight) {
            return new Vector3f(a).lerp(b, mix).mul(weight);
        }
    }
}
