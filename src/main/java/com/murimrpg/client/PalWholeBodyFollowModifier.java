package com.murimrpg.client;

import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes PAL's sibling torso/arm ModelParts read as a connected upper-body chain.
 * It changes rotations only: no root motion, position, scale, camera or gameplay state.
 */
final class PalWholeBodyFollowModifier extends AbstractModifier {
    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-PAL");
    private static final AtomicBoolean APPLIED_CONFIRMED = new AtomicBoolean();
    static final float MAX_PITCH = (float) Math.toRadians(8.0);
    static final float MAX_YAW = (float) Math.toRadians(3.0);
    static final float MAX_ROLL = (float) Math.toRadians(2.0);

    private final Supplier<PalAnimationStateSelector.Locomotion> state;
    private final boolean enabled;
    private final float pitchFollow;
    private final float yawFollow;
    private final float rollFollow;
    private boolean seeded;
    private float torsoPitch;
    private float torsoYaw;
    private float torsoRoll;

    PalWholeBodyFollowModifier(Supplier<PalAnimationStateSelector.Locomotion> state,
                               PalAnimationSettings settings) {
        this.state = state;
        this.enabled = settings.fullBodyFollowEnabled();
        this.pitchFollow = (float) settings.shoulderPitchFollow();
        this.yawFollow = (float) settings.shoulderYawFollow();
        this.rollFollow = (float) settings.shoulderRollFollow();
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone bone) {
        super.get3DTransform(bone);
        if (!enabled || !isGrounded(state.get())) {
            seeded = false;
            return;
        }
        if ("torso".equals(bone.getName())) {
            if (finite(bone.rotation.x, bone.rotation.y, bone.rotation.z)) {
                torsoPitch = bone.rotation.x;
                torsoYaw = bone.rotation.y;
                torsoRoll = bone.rotation.z;
                seeded = true;
            } else {
                seeded = false;
            }
            return;
        }
        if (!seeded || !("right_arm".equals(bone.getName()) || "left_arm".equals(bone.getName()))) return;

        bone.rotation.x += correction(torsoPitch, pitchFollow, MAX_PITCH);
        bone.rotation.y += correction(torsoYaw, yawFollow, MAX_YAW);
        bone.rotation.z += correction(torsoRoll, rollFollow, MAX_ROLL);
        if (APPLIED_CONFIRMED.compareAndSet(false, true)) {
            LOGGER.info("PAL whole-body shoulder follow applied in final animation layer.");
        }
    }

    static float correction(float torsoRotation, float follow, float limit) {
        if (!Float.isFinite(torsoRotation) || !Float.isFinite(follow) || !Float.isFinite(limit)
                || follow < 0f || follow > 1f || limit < 0f) return 0f;
        return Math.clamp(torsoRotation * follow, -limit, limit);
    }

    private static boolean isGrounded(PalAnimationStateSelector.Locomotion motion) {
        return motion == PalAnimationStateSelector.Locomotion.IDLE
                || motion == PalAnimationStateSelector.Locomotion.WALK
                || motion == PalAnimationStateSelector.Locomotion.RUN
                || motion == PalAnimationStateSelector.Locomotion.FAST_RUN;
    }

    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }
}
