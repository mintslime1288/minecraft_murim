package com.murimrpg.client.render;

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
    private boolean running;
    private boolean fastRunning;
    /** Hard ceiling regardless of YAML; a joint past this is a retarget failure, not a pose. */
    private static final float MAX_SAFE_RADIANS = 1.6f;
    private boolean poseValid;

    ArticulatedHumanoidModel(ArticulatedPlayerSettings settings, AuthoredRigWalkAnimation walkAnimation) {
        super(createBodyLayer().bakeRoot(), RenderTypes::entityCutout);
        this.settings = settings;
        this.walkAnimation = walkAnimation;
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
        var authored = walkAnimation.sample(phase).scaled(speed);
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
