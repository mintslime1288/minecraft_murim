package com.murimrpg.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.Set;

/** Own interpolation of MurimRPG's existing numeric, rotation-only poses; no imported animation. */
final class PalSmoothClip {
    static final String[] BONES = {"torso", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final String[] JSON_BONES = {"torso", "rightArm", "leftArm", "rightLeg", "leftLeg"};
    private static final String[] AXES = {"pitch", "yaw", "roll", "bend"};
    private static final Set<String> FORBIDDEN = Set.of("x","y","z","scale","scaleX","scaleY","scaleZ");
    static final int CHANNELS = 20;
    /** Fore-aft foot offset of one leg, in pixels, from a hip angle and the knee bend under it. */
    private static final int SEGMENT_PIXELS = 6;
    private static final int STRIDE_SAMPLES = 256;
    static final int RIGHT_LEG_PITCH = 12, RIGHT_LEG_BEND = 15;
    final double length;
    final boolean loop;
    final double[] ticks;
    /** Cycle average of every channel, so a swing can be scaled about its own centre. */
    final double[] mean = new double[CHANNELS];
    /**
     * The phase, 0..1, at which the right foot is furthest FORWARD - the moment of contact.
     *
     * <p>Measured off the clip rather than configured, because it is a property of the authored
     * curve: shifting a keyframe moves the contact and the weight accent has to move with it. The
     * left foot's contact is half a cycle away by the mirror symmetry the loader already enforces.
     */
    final double contactPhase;
    private final double[][] values;
    private final double[][] slopes;

    PalSmoothClip(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        int formatVersion = root.has("version") ? root.get("version").getAsInt() : 1;
        if (formatVersion < 1 || formatVersion > 3)
            throw new IllegalArgumentException("Unsupported PAL clip format version");
        JsonObject emote = root.getAsJsonObject("emote");
        if (emote == null || !emote.has("degrees") || emote.get("degrees").getAsBoolean())
            throw new IllegalArgumentException("Natural clips must use radians");
        length = emote.get("endTick").getAsDouble();
        loop = emote.get("isLoop").getAsBoolean();
        var moves = emote.getAsJsonArray("moves");
        if (!Double.isFinite(length) || length <= 0 || moves.size() < 2 || moves.size() > 4096)
            throw new IllegalArgumentException("Invalid clip duration/keyframe count");
        ticks = new double[moves.size()]; values = new double[moves.size()][CHANNELS];
        slopes = new double[moves.size()][CHANNELS];
        for (int i=0;i<ticks.length;i++) {
            var move = moves.get(i).getAsJsonObject();
            ticks[i] = move.get("tick").getAsDouble();
            if (!Double.isFinite(ticks[i]) || (i>0 && ticks[i]<=ticks[i-1]))
                throw new IllegalArgumentException("Non-increasing keyframe time");
            for (var entry : move.entrySet()) if (entry.getValue().isJsonObject()) {
                for (String key : entry.getValue().getAsJsonObject().keySet())
                    if (FORBIDDEN.contains(key)) throw new IllegalArgumentException("Non-rotation track: " + key);
            }
            for(int b=0;b<JSON_BONES.length;b++) {
                var bone=move.getAsJsonObject(JSON_BONES[b]);
                if(bone==null) throw new IllegalArgumentException("Missing bone " + JSON_BONES[b]);
                for(int a=0;a<4;a++) {
                    double v=bone.has(AXES[a])?bone.get(AXES[a]).getAsDouble():0;
                    // Player Animation Library v1/v2 aliases torso to body and negates
                    // the body's pitch while loading.  Preserve that published format
                    // convention before routing the result to PAL's visible torso bone.
                    if (formatVersion < 3 && b == 0 && a == 0) v = -v;
                    if(!Double.isFinite(v)||Math.abs(v)>Math.PI) throw new IllegalArgumentException("Unsafe rotation");
                    if(a==3 && ((b==1||b==2)&&v>0 || (b==3||b==4)&&v<0))
                        throw new IllegalArgumentException("Reversed hinge");
                    values[i][b*4+a]=v;
                }
            }
        }
        int last=ticks.length-1;
        if(ticks[0]!=0 || ticks[last]!=length) throw new IllegalArgumentException("Missing clip endpoints");
        for(int c=0;c<CHANNELS;c++) {
            if(loop && Math.abs(values[0][c]-values[last][c])>1e-6)
                throw new IllegalArgumentException("Discontinuous loop");
            for(int i=0;i<=last;i++) {
                if(!loop && (i==0||i==last)) { slopes[i][c]=0; continue; }
                int before=i==0?last-1:i-1, after=i==last?1:i+1;
                double h0=i==0?length-ticks[before]:ticks[i]-ticks[before];
                double h1=i==last?ticks[after]:ticks[after]-ticks[i];
                double d0=(values[i][c]-values[before][c])/h0;
                double d1=(values[after][c]-values[i][c])/h1;
                // Monotone Hermite slopes: continuous velocity without hinge overshoot.
                slopes[i][c]=d0*d1<=0?0:(3*(h0+h1))/((2*h1+h0)/d0+(h1+2*h0)/d1);
            }
        }
        double[] scratch=new double[CHANNELS];
        for(int i=0;i<STRIDE_SAMPLES;i++) {
            sample(length*i/(double)STRIDE_SAMPLES,scratch);
            for(int c=0;c<CHANNELS;c++) mean[c]+=scratch[c];
        }
        for(int c=0;c<CHANNELS;c++) mean[c]/=STRIDE_SAMPLES;
        double best=Double.MAX_VALUE, at=0;
        for(int i=0;i<STRIDE_SAMPLES;i++) {
            sample(length*i/(double)STRIDE_SAMPLES,scratch);
            // -z is forward for this rig, so the most forward foot is the smallest offset.
            double offset=SEGMENT_PIXELS*Math.sin(scratch[RIGHT_LEG_PITCH])
                    +SEGMENT_PIXELS*Math.sin(scratch[RIGHT_LEG_PITCH]+scratch[RIGHT_LEG_BEND]);
            if(offset<best) { best=offset; at=i/(double)STRIDE_SAMPLES; }
        }
        contactPhase=at;
    }

    /** A limb swing may be widened, but never past what a hip or a shoulder can actually do. */
    static double clampSwing(double radians,double maximum) {
        if(!Double.isFinite(radians)) return 0;
        double limit=Double.isFinite(maximum)&&maximum>0?maximum:Math.PI;
        return Math.clamp(radians,-limit,limit);
    }

    /**
     * How far the foot travels fore and aft in one step, in blocks, if the hip swing is scaled by
     * {@code gain} about its own cycle average.
     *
     * <p>This is the number the whole stride question turns on. A rotation-only rig has no IK and
     * no foot lock, so the only thing that can stop the feet skating is the foot's own travel
     * matching the ground the player covers in the same step. The leg is a 12 pixel cuboid bent at
     * its middle by Bendable Cuboids, so the foot hangs off two 6 pixel segments: the thigh at the
     * hip angle and the shin at the hip angle plus the bend. The bend is deliberately NOT scaled -
     * a knee is a hinge with a sign, and widening it is how a knee ends up inverted.
     */
    double footTravelBlocks(double gain,double maximumSwing) {
        double[] scratch=new double[CHANNELS];
        double low=Double.MAX_VALUE, high=-Double.MAX_VALUE;
        for(int i=0;i<STRIDE_SAMPLES;i++) {
            sample(length*i/(double)STRIDE_SAMPLES,scratch);
            double hip=clampSwing(mean[RIGHT_LEG_PITCH]
                    +(scratch[RIGHT_LEG_PITCH]-mean[RIGHT_LEG_PITCH])*gain,maximumSwing);
            double offset=SEGMENT_PIXELS*Math.sin(hip)
                    +SEGMENT_PIXELS*Math.sin(hip+scratch[RIGHT_LEG_BEND]);
            low=Math.min(low,offset); high=Math.max(high,offset);
        }
        return (high-low)/16d;
    }

    /**
     * The swing gain whose foot travel matches {@code wantedBlocks}, or 1 when none is needed.
     *
     * <p>Solved from the clip's own geometry rather than configured, so re-authoring the clip with
     * a longer stride automatically drives this back toward 1 instead of leaving a stale constant
     * multiplying an already-correct pose.
     */
    double strideGain(double wantedBlocks,double maximumGain,double maximumSwing) {
        if(!Double.isFinite(wantedBlocks)||wantedBlocks<=0||!(maximumGain>1)) return 1;
        if(footTravelBlocks(1,maximumSwing)>=wantedBlocks) return 1;
        if(footTravelBlocks(maximumGain,maximumSwing)<=wantedBlocks) return maximumGain;
        double low=1, high=maximumGain;
        for(int i=0;i<40;i++) {
            double middle=(low+high)/2;
            if(footTravelBlocks(middle,maximumSwing)<wantedBlocks) low=middle; else high=middle;
        }
        return (low+high)/2;
    }

    void sample(double time, double[] output) {
        double t=loop?((time%length)+length)%length:Math.clamp(time,0,length);
        int i=0; while(i<ticks.length-2 && ticks[i+1]<t) i++;
        double h=ticks[i+1]-ticks[i], u=(t-ticks[i])/h, u2=u*u, u3=u2*u;
        for(int c=0;c<CHANNELS;c++) output[c]=(2*u3-3*u2+1)*values[i][c]
                +(u3-2*u2+u)*h*slopes[i][c]+(-2*u3+3*u2)*values[i+1][c]
                +(u3-u2)*h*slopes[i+1][c];
    }
    static int boneIndex(String name) {
        for(int i=0;i<BONES.length;i++) if(BONES[i].equals(name)) return i;
        return -1;
    }
}
