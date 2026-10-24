package com.murimrpg.client;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.entity.Avatar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

/** PAL layer adapter. Samples all bones once per game timestamp, before any bone is submitted. */
final class PalNaturalMotionModifier extends AbstractModifier {
    private final Avatar avatar;
    private final Supplier<PalAnimationStateSelector.Locomotion> state;
    private final Supplier<Map<PalAnimationStateSelector.Locomotion,PalSmoothClip>> resources;
    private final PalNaturalSettings settings;
    private Map<PalAnimationStateSelector.Locomotion,PalSmoothClip> loaded;
    private PalNaturalMotion motion;
    private boolean ready, logged;
    private int lastDiagnosticTick=Integer.MIN_VALUE;
    private net.minecraft.world.level.Level lastLevel;
    private net.minecraft.world.phys.Vec3 lastPosition;

    PalNaturalMotionModifier(Avatar avatar, Supplier<PalAnimationStateSelector.Locomotion> state,
            PalNaturalSettings settings, Supplier<Map<PalAnimationStateSelector.Locomotion,PalSmoothClip>> resources) {
        this.avatar=avatar; this.state=state; this.settings=settings; this.resources=resources;
    }
    @Override public void setupAnim(AnimationData data) {
        super.setupAnim(data);
        var available=resources.get();
        if(available.isEmpty()) { ready=false; return; }
        if(available!=loaded) { loaded=available; motion=new PalNaturalMotion(settings,loaded); }
        var position=avatar.position();
        if(lastLevel!=avatar.level() || lastPosition!=null && lastPosition.distanceToSqr(position)>256) motion.reset();
        lastLevel=avatar.level(); lastPosition=position;
        var velocity=avatar.getDeltaMovement();
        ready=motion.update(avatar.tickCount+data.getPartialTick(),state.get(),velocity.x,velocity.z,avatar.yBodyRot);
        if(settings.diagnostics() && avatar==net.minecraft.client.Minecraft.getInstance().player
                && (lastDiagnosticTick==Integer.MIN_VALUE||avatar.tickCount-lastDiagnosticTick>=40)) {
            lastDiagnosticTick=avatar.tickCount;
            LoggerFactory.getLogger("MurimRPG-PAL").info(
                    "Natural motion v2: state={}, ready={}, speed={}, phase={}, cycleTicks={}, strideGain={}",
                    state.get(),ready,velocity.horizontalDistance(),motion.phase(),motion.cycleTicks(),
                    motion.strideGain(state.get()));
        }
    }
    @Override public void get3DTransform(@NotNull PlayerAnimBone bone) {
        super.get3DTransform(bone);
        int index=PalSmoothClip.boneIndex(bone.getName());
        if(!ready||index<0) return;
        int c=index*4;
        bone.rotation.set((float)motion.pose[c],(float)motion.pose[c+1],(float)motion.pose[c+2]);
        bone.bend=(float)motion.pose[c+3];
        if(!logged) {
            logged=true;
            LoggerFactory.getLogger("MurimRPG-PAL").info(
                    "Natural motion v2 sampled pose submitted: continuous phase, bounded cubic interpolation, rotation-only.");
        }
    }
}
