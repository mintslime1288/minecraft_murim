package com.murimrpg.client;

import java.util.Map;
import static com.murimrpg.client.PalAnimationStateSelector.Locomotion;

/** Entity-local simulation clock. Bone reads never advance time or mutate the pose. */
final class PalNaturalMotion {
    private final PalNaturalSettings settings;
    private final Map<Locomotion, PalSmoothClip> clips;
    final double[] pose = new double[PalSmoothClip.CHANNELS];
    private final double[] target = new double[PalSmoothClip.CHANNELS];
    private final double[] from = new double[PalSmoothClip.CHANNELS];
    private final double[] takeoff = new double[PalSmoothClip.CHANNELS];
    private final double[] gait = new double[PalSmoothClip.CHANNELS];
    private final double[] idleStance = new double[PalSmoothClip.CHANNELS];
    /** Scratch for the chain's second and third clip reads; never escapes a single sampleGait. */
    private final double[] chestTap = new double[PalSmoothClip.CHANNELS];
    private final double[] armTap = new double[PalSmoothClip.CHANNELS];
    private final java.util.EnumMap<Locomotion,Double> strideGain=new java.util.EnumMap<>(Locomotion.class);
    private double lastTime=Double.NaN, stateStart, transitionStart, transitionLength, phase, rate;
    private double forward=1, strafe, takeoffTime;
    private Locomotion state, lastGait=Locomotion.WALK;
    private boolean movingTakeoff;

    PalNaturalMotion(PalNaturalSettings settings, Map<Locomotion,PalSmoothClip> clips) {
        this.settings=settings; this.clips=clips;
        clips.get(Locomotion.IDLE).sample(0,idleStance);
        // Solved once per clip set, never per frame: the gain is a property of the clip and of the
        // ground a step covers, and neither changes while the client runs.
        for(var gait:new Locomotion[]{Locomotion.WALK,Locomotion.RUN,Locomotion.FAST_RUN}) {
            strideGain.put(gait,solveStrideGain(gait));
        }
    }

    /**
     * How much the authored limb swing has to be widened for the foot to cover the ground the step
     * covers.
     *
     * <p>Only meaningful with the distance-driven clock: that is what makes one step's ground
     * exactly half a cycle distance. Under the legacy fixed period the ground per step depends on
     * how fast the player happens to be moving, so there is no single number to match and the gain
     * stays at 1.
     */
    private double solveStrideGain(Locomotion gait) {
        if(!settings.strideMatch()||!settings.distanceCadence()) return 1;
        var clip=clips.get(gait);
        if(clip==null) return 1;
        double wanted=cycleBlocks(gait)/2*settings.strideMatchStrength();
        return clip.strideGain(wanted,settings.maximumStrideGain(),settings.maximumLimbSwing());
    }
    /** The solved gain for a gait, 1 when stride matching is off. Read by the diagnostic and tests. */
    double strideGain(Locomotion gait) { return strideGain.getOrDefault(gait,1d); }

    boolean update(double time, Locomotion next, double velocityX, double velocityZ, double yawDegrees) {
        if(next==null || next==Locomotion.VANILLA || !Double.isFinite(time)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityZ) || !Double.isFinite(yawDegrees)) {
            reset(); return false;
        }
        if(!Double.isNaN(lastTime) && time==lastTime && next==state) return true;
        if(!Double.isNaN(lastTime) && (time<lastTime || time-lastTime>20)) reset();
        boolean first=Double.isNaN(lastTime);
        double dt=first?0:Math.max(0,time-lastTime);
        double speed=Math.hypot(velocityX,velocityZ);
        double yaw=Math.toRadians(yawDegrees);
        double wantedForward=speed<1e-5?forward:(-Math.sin(yaw)*velocityX+Math.cos(yaw)*velocityZ)/speed;
        double wantedStrafe=speed<1e-5?0:(-Math.cos(yaw)*velocityX-Math.sin(yaw)*velocityZ)/speed;
        double decay=Math.exp(-dt/settings.responseTicks());
        if(first) { forward=wantedForward; strafe=wantedStrafe; }
        else { forward=wantedForward+(forward-wantedForward)*decay; strafe=wantedStrafe+(strafe-wantedStrafe)*decay; }
        // A shared cycle survives WALK/RUN switches; period changes do not reset the support foot.
        double desiredRate=next==Locomotion.IDLE?0:cycleRate(grounded(next)?next:lastGait,speed);
        if(first) rate=desiredRate;
        phase += desiredRate*dt+(rate-desiredRate)*settings.responseTicks()*(1-decay);
        rate=desiredRate+(rate-desiredRate)*decay;
        phase-=Math.floor(phase);
        if(first || state!=next) {
            if(!first) System.arraycopy(pose,0,from,0,pose.length);
            if(grounded(state) && PalAnimationStateSelector.isAirborne(next)) {
                System.arraycopy(pose,0,takeoff,0,pose.length);
                takeoffTime=time; movingTakeoff=state!=Locomotion.IDLE;
            }
            state=next; stateStart=time; transitionStart=time;
            transitionLength=next==Locomotion.JUMP_LAND?settings.landingBlendTicks():
                    PalAnimationStateSelector.isAirborne(next)?settings.airBlendTicks():settings.blendTicks();
        }
        if(next==Locomotion.WALK||next==Locomotion.RUN||next==Locomotion.FAST_RUN) lastGait=next;
        if(grounded(next)) sampleGait(next, time, target);
        else {
            clips.get(next).sample(time-stateStart,target);
            if(movingTakeoff && PalAnimationStateSelector.isAirborne(next)) {
                double carry=Math.exp(-(time-takeoffTime)/settings.airCarryTicks());
                // Carry the actual left/right difference, not a fixed symmetric jump pose.
                for(int a=0;a<=3;a+=3) {
                    carryPair(target,takeoff,12+a,16+a,carry);
                    carryPair(target,takeoff,4+a,8+a,carry);
                }
            }
            if(next==Locomotion.JUMP_LAND && speed>1e-5) {
                sampleGait(lastGait,time,gait);
                double recovery=smooth((time-stateStart)/clips.get(next).length);
                for(int c=0;c<target.length;c++) target[c]=lerp(target[c],gait[c],recovery);
            }
        }
        // The standing orientation is added to the TARGET, in every state alike, and never to the
        // blended result. Adding it after the blend would add it to a `from` snapshot that already
        // carried it, so each state change would stack another offset - the one-joint-applied-
        // twice fault. Applying it in every state also means no transition can pop it in or out.
        target[1]=PalBodyChain.standingYaw(target[1],settings.bodyChain());
        double blend=first?1:smooth((time-transitionStart)/transitionLength);
        for(int c=0;c<pose.length;c++) pose[c]=lerp(from[c],target[c],blend);
        // Interpolation and carry must never reverse a hinge or produce an unbounded angle.
        pose[7]=Math.min(0,pose[7]); pose[11]=Math.min(0,pose[11]);
        pose[15]=Math.max(0,pose[15]); pose[19]=Math.max(0,pose[19]);
        for(double v:pose) if(!Double.isFinite(v)||Math.abs(v)>Math.PI) { reset(); return false; }
        lastTime=time;
        return true;
    }

    private void sampleGait(Locomotion motion,double time,double[] out) {
        var clip=clips.get(motion);
        var chain=settings.bodyChain();
        clip.sample(motion==Locomotion.IDLE?time:phase*clip.length,out);
        if(motion!=Locomotion.IDLE && chain.enabled()) {
            // One curve read at three places in the chain: the feet are where the phase says, the
            // chest is behind them and the shoulders behind the chest. Each group keeps its own
            // authored waveform, so the mirror and the hinges are untouched and only the timing
            // between groups moves.
            clip.sample(PalBodyChain.chestPhase(phase,chain)*clip.length,chestTap);
            clip.sample(PalBodyChain.armPhase(phase,chain)*clip.length,armTap);
            System.arraycopy(chestTap,0,out,0,4);
            System.arraycopy(armTap,4,out,4,8);
            PalBodyChain.applyContact(out,phase,clip.contactPhase,chain);
        }
        out[2]*=settings.torsoRollWeight();
        if(motion==Locomotion.IDLE) {
            out[7]*=settings.idleArmRelax(); out[11]*=settings.idleArmRelax();
            // Keep the loaded stance under the same feet while upper-body breathing continues.
            System.arraycopy(idleStance,12,out,12,8);
            return;
        }
        double f=Math.clamp(forward,-1,1), s=Math.clamp(strafe,-1,1);
        double gain=strideGain(motion);
        // Reverse fore/aft swing without reversing the knee hinge; side travel uses a small roll arc.
        // The gain widens the swing about the clip's own centre, so the stance's forward bias and
        // the left/right phase are untouched and only the reach changes. The lateral arc is taken
        // from the UNwidened swing on purpose: it is a stylistic accent, not a stride.
        for(int b=1;b<5;b++) {
            int i=b*4;
            double swing=out[i];
            if(gain!=1) out[i]=PalSmoothClip.clampSwing(clip.mean[i]+(out[i]-clip.mean[i])*gain,
                    settings.maximumLimbSwing());
            out[i]*=f;
            if(b>=3) out[i+2]+=s*swing*settings.strafeWeight();
        }
        if(motion==Locomotion.RUN||motion==Locomotion.FAST_RUN) {
            double lean=settings.extraRunLean()*Math.max(0,f);
            out[0]-=lean; out[4]-=lean; out[8]-=lean;
        }
    }
    private static void carryPair(double[] out,double[] source,int right,int left,double weight) {
        double difference=(source[right]-source[left])*.5*weight;
        out[right]+=difference; out[left]-=difference;
    }
    /**
     * Cycles per tick.
     *
     * <p>A gait cycle is a distance, not a duration. Mojang drives its own limb swing from
     * {@code WalkAnimationState.position}, which accumulates travelled distance, so the vanilla
     * cadence rises and falls with the player's real speed and one step always covers the same
     * ground. Reading a fixed tick period instead makes the legs turn at a constant rate whatever
     * the player is doing, which is what leaves the feet skating and the rhythm unresponsive.
     *
     * <p>Dividing the speed the caller already measured by a distance per cycle restores that
     * coupling: the result is cycles per tick, so multiplying by a tick delta stays frame-rate
     * independent, it is continuous in speed, and it reaches exactly zero at a standstill - a
     * player walking into a wall plants the feet instead of running on the spot. The cap only
     * exists so an impossible speed (a launch, a riptide, a rubber-banded correction) cannot spin
     * the legs; it is never reached at any normal travel speed.
     */
    private double cycleRate(Locomotion motion,double speed) {
        if(!settings.distanceCadence()) return 1/period(motion);
        if(!Double.isFinite(speed)||speed<=0) return 0;
        return Math.min(speed/cycleBlocks(motion),1/settings.minimumCycleTicks());
    }
    private double cycleBlocks(Locomotion motion) {
        return switch(motion) {
            case RUN -> settings.runCycleBlocks(); case FAST_RUN -> settings.fastCycleBlocks();
            default -> settings.walkCycleBlocks();
        };
    }
    private double period(Locomotion motion) {
        return switch(motion) {
            case RUN -> settings.runTicks(); case FAST_RUN -> settings.fastTicks();
            default -> settings.walkTicks();
        };
    }
    /** The cadence this frame, in ticks per complete cycle, for the rate-limited diagnostic only. */
    double cycleTicks() { return rate>1e-9?1/rate:Double.POSITIVE_INFINITY; }
    static boolean grounded(Locomotion state) {
        return state==Locomotion.IDLE||state==Locomotion.WALK||state==Locomotion.RUN||state==Locomotion.FAST_RUN;
    }
    static double smooth(double value) { double t=Math.clamp(value,0,1); return t*t*(3-2*t); }
    private static double lerp(double a,double b,double t) { return a+(b-a)*t; }
    double phase() { return phase; }
    void reset() { lastTime=Double.NaN; state=null; phase=0; rate=0; movingTakeoff=false; }
}
