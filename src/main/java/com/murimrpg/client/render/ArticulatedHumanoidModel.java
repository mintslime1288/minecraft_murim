package com.murimrpg.client.render;

import com.murimrpg.client.MeshyWalkAnimation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Native ModelPart implementation of an articulated Minecraft player.  It deliberately
 * stays in Mojang's 16-pixel model coordinate system, avoiding custom-geometry queue
 * transforms entirely.  Upper/lower arms and legs are separate hierarchical parts.
 */
final class ArticulatedHumanoidModel extends Model<AvatarRenderState> {
    private final ModelPart head, body, rightUpperArm, leftUpperArm, rightLowerArm, leftLowerArm;
    private final ModelPart rightUpperLeg, leftUpperLeg, rightLowerLeg, leftLowerLeg;
    private final ArticulatedPlayerSettings settings;
    private final AuthoredRigWalkAnimation walkAnimation;
    private final AuthoredRigWalkAnimation runAnimation;
    private final AuthoredRigWalkAnimation jumpAnimation;
    private boolean running;
    private boolean fastRunning;
    private ArticulatedJumpTracker.Phase jumpPhase = ArticulatedJumpTracker.Phase.GROUND;
    private float landingProgress;
    /** Hard ceiling regardless of YAML; a joint past this is a retarget failure, not a pose. */
    private static final float MAX_SAFE_RADIANS = 1.6f;
    private boolean poseValid;

    ArticulatedHumanoidModel(ArticulatedPlayerSettings settings, AuthoredRigWalkAnimation walkAnimation,
                             AuthoredRigWalkAnimation runAnimation, AuthoredRigWalkAnimation jumpAnimation) {
        super(createBodyLayer().bakeRoot(), RenderTypes::entityCutout);
        this.settings = settings;
        this.walkAnimation = walkAnimation;
        this.runAnimation = runAnimation;
        this.jumpAnimation = jumpAnimation;
        head = root.getChild("head"); body = root.getChild("body");
        rightUpperArm = root.getChild("right_upper_arm"); leftUpperArm = root.getChild("left_upper_arm");
        rightLowerArm = rightUpperArm.getChild("right_lower_arm"); leftLowerArm = leftUpperArm.getChild("left_lower_arm");
        rightUpperLeg = root.getChild("right_upper_leg"); leftUpperLeg = root.getChild("left_upper_leg");
        rightLowerLeg = rightUpperLeg.getChild("right_lower_leg"); leftLowerLeg = leftUpperLeg.getChild("left_lower_leg");
    }

    void setLocomotion(boolean running, boolean fastRunning) {
        this.running = running;
        this.fastRunning = fastRunning;
    }

    void setJumpPhase(ArticulatedJumpTracker.Phase jumpPhase, float landingProgress) {
        this.jumpPhase = jumpPhase;
        this.landingProgress = Float.isFinite(landingProgress) ? Math.clamp(landingProgress, 0f, 1f) : 0f;
    }

    static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4), PartPose.ZERO);
        // Seven-pixel upper and five-pixel lower sections meet exactly at elbow height.
        var rua = root.addOrReplaceChild("right_upper_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 7, 4), PartPose.offset(-5, 2, 0));
        rua.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(40, 23).addBox(-3, 0, -2, 4, 5, 4), PartPose.offset(0, 5, 0));
        var lua = root.addOrReplaceChild("left_upper_arm", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(-1, -2, -2, 4, 7, 4), PartPose.offset(5, 2, 0));
        lua.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(32, 55).mirror().addBox(-1, 0, -2, 4, 5, 4), PartPose.offset(0, 5, 0));
        var rul = root.addOrReplaceChild("right_upper_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(-1.9f, 12, 0));
        rul.addOrReplaceChild("right_lower_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        var lul = root.addOrReplaceChild("left_upper_leg", CubeListBuilder.create().texOffs(16, 48).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(1.9f, 12, 0));
        lul.addOrReplaceChild("left_lower_leg", CubeListBuilder.create().texOffs(16, 54).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** False when the last setupAnim produced a non-finite or out-of-range joint. */
    boolean poseValid() { return poseValid; }

    @Override public void setupAnim(AvatarRenderState state) {
        resetPose();
        poseValid = true;
        head.yRot = state.yRot * ((float)Math.PI / 180f);
        head.xRot = state.xRot * ((float)Math.PI / 180f);
        float speed = Math.clamp(state.walkAnimationSpeed, 0f, 1f);
        // walkAnimationPos is vanilla's accumulated limb-swing distance, not radians of a
        // gait cycle. Vanilla scales it before driving a full cycle, so the authored loop
        // must use the same scale or it plays at the wrong cadence and the feet skate.
        float phase = state.walkAnimationPos * settings.limbSwingScale();
        if (!Float.isFinite(phase) || !Float.isFinite(speed)) {
            poseValid = false;
            return;
        }
        if (jumpPhase != ArticulatedJumpTracker.Phase.GROUND) {
            int frame = switch (jumpPhase) {
                case RISE -> 1;
                case APEX -> 2;
                case FALL -> 3;
                case LAND -> 4;
                case GROUND -> 0;
            };
            if (jumpPhase == ArticulatedJumpTracker.Phase.LAND) {
                // Impact is immediate; only recovery eases back toward the live gait.
                applyLocomotionPose(phase, speed);
                float[] recovered = snapshot();
                applyJumpPose(jumpAnimation.sampleFrame(frame));
                blendToward(recovered, smoothstep(landingProgress));
            } else {
                applyJumpPose(jumpAnimation.sampleFrame(frame));
            }
            return;
        }
        applyLocomotionPose(phase, speed);
    }

    private void applyLocomotionPose(float phase, float speed) {
        var authored = (running ? runAnimation : walkAnimation).sample(phase).scaled(speed);
        float strideLimit = radians(running ? settings.runStrideDegrees() : settings.walkStrideDegrees());
        float kneeLimit = radians(running ? settings.runKneeDegrees() : settings.walkKneeDegrees());
        float elbowLimit = radians(running ? settings.runElbowDegrees() : settings.walkElbowDegrees());
        float bodyLimit = radians(settings.bodyTurnDegrees());
        rightUpperLeg.xRot = limited(authored.rightLeg().x, settings.upperLegGain(), strideLimit);
        leftUpperLeg.xRot = limited(authored.leftLeg().x, settings.upperLegGain(), strideLimit);
        rightLowerLeg.xRot = limited(authored.rightLowerLeg().x, settings.lowerLegGain(), kneeLimit);
        leftLowerLeg.xRot = limited(authored.leftLowerLeg().x, settings.lowerLegGain(), kneeLimit);
        rightUpperArm.xRot = limited(authored.rightArm().x, settings.upperArmGain(), strideLimit);
        leftUpperArm.xRot = limited(authored.leftArm().x, settings.upperArmGain(), strideLimit);
        rightLowerArm.xRot = limited(authored.rightForeArm().x, settings.lowerArmGain(), elbowLimit);
        leftLowerArm.xRot = limited(authored.leftForeArm().x, settings.lowerArmGain(), elbowLimit);
        body.xRot = limited(authored.body().x, 1f, bodyLimit);
        body.yRot = limited(authored.body().y, 1f, bodyLimit);
        body.zRot = limited(authored.body().z, 1f, bodyLimit);
    }

    /** Captures the eleven driven joints so LAND can recover into the live gait. */
    private float[] snapshot() {
        return new float[] { rightUpperLeg.xRot, leftUpperLeg.xRot, rightLowerLeg.xRot, leftLowerLeg.xRot,
                rightUpperArm.xRot, leftUpperArm.xRot, rightLowerArm.xRot, leftLowerArm.xRot,
                body.xRot, body.yRot, body.zRot };
    }

    private void blendToward(float[] target, float amount) {
        rightUpperLeg.xRot = lerp(rightUpperLeg.xRot, target[0], amount);
        leftUpperLeg.xRot = lerp(leftUpperLeg.xRot, target[1], amount);
        rightLowerLeg.xRot = lerp(rightLowerLeg.xRot, target[2], amount);
        leftLowerLeg.xRot = lerp(leftLowerLeg.xRot, target[3], amount);
        rightUpperArm.xRot = lerp(rightUpperArm.xRot, target[4], amount);
        leftUpperArm.xRot = lerp(leftUpperArm.xRot, target[5], amount);
        rightLowerArm.xRot = lerp(rightLowerArm.xRot, target[6], amount);
        leftLowerArm.xRot = lerp(leftLowerArm.xRot, target[7], amount);
        body.xRot = lerp(body.xRot, target[8], amount);
        body.yRot = lerp(body.yRot, target[9], amount);
        body.zRot = lerp(body.zRot, target[10], amount);
    }

    private float lerp(float from, float to, float amount) {
        float result = from + (to - from) * amount;
        if (!Float.isFinite(result) || Math.abs(result) > MAX_SAFE_RADIANS) {
            poseValid = false;
            return 0f;
        }
        return result;
    }

    private static float smoothstep(float amount) {
        float value = Math.clamp(amount, 0f, 1f);
        return value * value * (3f - 2f * value);
    }

    private void applyJumpPose(MeshyWalkAnimation.Pose authored) {
        float strideLimit = radians(settings.jumpStrideDegrees());
        float kneeLimit = radians(settings.jumpKneeDegrees());
        float elbowLimit = radians(settings.jumpElbowDegrees());
        float bodyLimit = radians(settings.jumpBodyDegrees());
        rightUpperLeg.xRot = limited(authored.rightLeg().x, settings.upperLegGain(), strideLimit);
        leftUpperLeg.xRot = limited(authored.leftLeg().x, settings.upperLegGain(), strideLimit);
        rightLowerLeg.xRot = limited(authored.rightLowerLeg().x, settings.lowerLegGain(), kneeLimit);
        leftLowerLeg.xRot = limited(authored.leftLowerLeg().x, settings.lowerLegGain(), kneeLimit);
        rightUpperArm.xRot = limited(authored.rightArm().x, settings.upperArmGain(), strideLimit);
        leftUpperArm.xRot = limited(authored.leftArm().x, settings.upperArmGain(), strideLimit);
        rightLowerArm.xRot = limited(authored.rightForeArm().x, settings.lowerArmGain(), elbowLimit);
        leftLowerArm.xRot = limited(authored.leftForeArm().x, settings.lowerArmGain(), elbowLimit);
        body.xRot = limited(authored.body().x, 1f, bodyLimit);
        body.yRot = limited(authored.body().y, 1f, bodyLimit);
        body.zRot = limited(authored.body().z, 1f, bodyLimit);
    }

    private float limited(float value, float gain, float limit) {
        if (!Float.isFinite(value)) {
            poseValid = false;
            return 0f;
        }
        float result = Math.clamp(value * gain, -limit, limit);
        if (!Float.isFinite(result) || Math.abs(result) > MAX_SAFE_RADIANS) {
            poseValid = false;
            return 0f;
        }
        return result;
    }

    private static float radians(float degrees) {
        return degrees * ((float)Math.PI / 180f);
    }

}
