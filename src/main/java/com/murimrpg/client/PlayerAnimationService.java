package com.murimrpg.client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.slf4j.LoggerFactory;

/**
 * Applies a procedural, rotation-only walk to Minecraft's ordinary player mesh.
 * No GLB pose, root motion, model scale, or player position is used here.
 */
public final class PlayerAnimationService {
    private final ClientVisualSettings visuals;
    private final Path configDirectory;
    private final Map<Integer, WalkCycleState> walkCycles = new HashMap<>();
    private volatile NaturalWalkSettings settings;

    public PlayerAnimationService(ClientVisualSettings visuals, Path configDirectory) {
        this.visuals = visuals;
        this.configDirectory = configDirectory;
        this.settings = NaturalWalkSettings.load(configDirectory);
    }

    public void loadAnimations(Minecraft client) {
        LoggerFactory.getLogger("MurimRPG-Walk").info("Native procedural walk controller ready; GLB clips are not used.");
    }

    public void reload() {
        settings = NaturalWalkSettings.load(configDirectory);
        LoggerFactory.getLogger("MurimRPG-Walk").info("Reloaded native procedural natural-walk.yml");
    }

    public void apply(Player player, AvatarRenderState renderState, ModelPart rightArm, ModelPart leftArm,
                      ModelPart rightLeg, ModelPart leftLeg, ModelPart body) {
        var config = settings;
        PlayerAnimationState snapshot = PlayerAnimationState.from(player, renderState);
        var state = PlayerLocomotionAnimator.state(snapshot, player.onGround(), player.isInWater(),
                player.onClimbable(), player.getAbilities().flying, player.isSleeping(), renderState.deathTime != 0);
        float renderSpeed = renderState.walkAnimationSpeed;
        double horizontalVelocity = player.getDeltaMovement().horizontalDistance();
        // HumanoidRenderState.walkAnimationSpeed can be zero on multiplayer
        // player models. Map the local horizontal velocity into the same useful
        // range as vanilla's gait value when that happens.
        float effectiveSpeed = renderSpeed > .03f
                ? renderSpeed
                : Math.clamp((float) horizontalVelocity, 0f, 1f);
        NaturalWalkDiagnostics.record(player.getId(), state.name(), effectiveSpeed, renderState.walkAnimationPos);
        if (!NaturalWalkDiagnostics.enabled() || !config.enabled()) return;

        // Sprinting, jumping, attack, item use, swimming and every other state
        // remain fully vanilla. IDLE is retained only long enough to fade the
        // previous safe walk pose back to zero without a visual pop.
        boolean walkEligible = state == PlayerLocomotionAnimator.State.WALK;
        boolean canFadeToIdle = walkEligible || state == PlayerLocomotionAnimator.State.IDLE;
        if (!canFadeToIdle) {
            walkCycles.remove(player.getId());
            return;
        }

        float quality = switch (visuals.quality()) { case LOW -> .55f; case NORMAL -> .80f; case HIGH -> 1f; };
        var cycle = walkCycles.computeIfAbsent(player.getId(), ignored -> new WalkCycleState());
        var pose = cycle.update(renderState.ageInTicks, effectiveSpeed, walkEligible, config).scaled(quality);
        if (!walkEligible && cycle.inactive()) {
            walkCycles.remove(player.getId());
            return;
        }

        // The target is a bounded rotation, not an additive second animation. This
        // keeps the feet under the player and avoids the horizontal-leg failure that
        // occurred when Meshy skeleton rotations were retargeted directly.
        applyProceduralLeg(rightLeg, pose.rightLeg(), config.maximumLegRadians());
        applyProceduralLeg(leftLeg, pose.leftLeg(), config.maximumLegRadians());
        body.xRot = blend(body.xRot, clamp(pose.torsoPitch(), config.maximumBodyRadians()), .78f);
        body.yRot = blend(body.yRot, clamp(pose.shoulderYaw(), config.maximumShoulderYawRadians()), .72f);
        body.zRot = blend(body.zRot, clamp(pose.bodyRoll(), config.maximumBodyRadians()), .72f);

        // Never modify a hand that is responsible for an item pose. Empty hands use
        // a smaller counter-swing than the legs, with only a tiny roll at the shoulder.
        boolean rightHandEmpty = player.getMainArm() == HumanoidArm.RIGHT
                ? player.getMainHandItem().isEmpty() : player.getOffhandItem().isEmpty();
        boolean leftHandEmpty = player.getMainArm() == HumanoidArm.LEFT
                ? player.getMainHandItem().isEmpty() : player.getOffhandItem().isEmpty();
        if (rightHandEmpty) applyProceduralArm(rightArm, pose.rightArm(), pose.armRoll(), config.maximumArmRadians());
        if (leftHandEmpty) applyProceduralArm(leftArm, pose.leftArm(), -pose.armRoll(), config.maximumArmRadians());
    }

    private static void applyProceduralArm(ModelPart part, float target, float roll, float limit) {
        part.xRot = blend(part.xRot, clamp(target, limit), .82f);
        part.zRot = blend(part.zRot, clamp(roll, limit) * .35f, .70f);
    }

    private static void applyProceduralLeg(ModelPart part, float target, float limit) {
        part.xRot = blend(part.xRot, clamp(target, limit), .88f);
    }

    private static float blend(float vanilla, float target, float amount) {
        return vanilla + (target - vanilla) * Math.clamp(amount, 0f, 1f);
    }
    private static float clamp(float value, float limit) { return Math.clamp(value, -limit, limit); }
}
