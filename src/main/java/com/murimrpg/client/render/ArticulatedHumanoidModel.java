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
    private boolean reversed;
    private float gaitWeight = 1f;
    private final ArticulatedGaitPhase gaitPhase = new ArticulatedGaitPhase();
    private final ArticulatedGaitBlend gaitBlend = new ArticulatedGaitBlend();
    private final ProceduralTorsoFollow torsoFollow = new ProceduralTorsoFollow();
    private final RunArmInertia armInertia = new RunArmInertia();
    /** Attack state, pushed in by the renderer each frame from AvatarRenderState. */
    private float attackTime;
    private boolean attackRightHanded = true;
    private final BoxingHandSelector boxingHands = new BoxingHandSelector();
    private boolean bothHandsEmpty;

    void setBothHandsEmpty(boolean empty) { bothHandsEmpty = empty; }
    private boolean attackArmed;
    private boolean attackSwingActive;
    private boolean loggedStanceSwing;
    private boolean loggedStanceRaised;
    /** Reused so the per-frame combat solve allocates nothing. */
    private final float[] combatDelta = new float[ArticulatedCombatPose.VALUE_COUNT];
    private final float[] combatHeld = new float[ArticulatedCombatPose.VALUE_COUNT];
    private float previousAttackTime;
    private float restartWeight;
    /**
     * How much of the upper body the strike owns this frame, and the value held across a combo
     * restart so the handover releases with the pose it belongs to instead of snapping.
     */
    private float strikeOwnership;
    private float heldOwnership;
    private float lastCombatAge = Float.NaN;
    /** The fighting guard that outlives the swing. Local player only, one per model. */
    private final BarehandCombatStance.Tracker stance = new BarehandCombatStance.Tracker();
    private final float[] stanceDelta = new float[BarehandCombatStance.VALUE_COUNT];
    private boolean groundedFrame;
    private float gaitSpeed;
    private float forwardRunWeight;
    private float motionAge;
    private float rawMotionSpeed;
    /** True only when the head hangs off the torso; see headOnTorso. */
    private final boolean headFollowsTorso;
    /** Reused so the per-frame head solve allocates nothing. */
    private final float[] headLook = new float[HeadLookCompensation.ANGLE_COUNT];
    private float runWeight;
    private ArticulatedJumpTracker.Phase jumpPhase = ArticulatedJumpTracker.Phase.GROUND;
    private float landingProgress;
    private ArticulatedJumpTracker.Phase priorJumpPhase = ArticulatedJumpTracker.Phase.GROUND;
    private float phaseProgress = 1f;
    private float airborneProgress = 1f;
    private float[] takeOffPose;
    private boolean wasAirborneFrame;
    /**
     * Final finite-rotation guard after each axis has already been clamped to its own YAML-backed
     * anatomical limit. The articulated elbow may legitimately combine a 30 degree locomotion bend
     * with the 135 degree guard ceiling, so the old 1.6 radian (91.7 degree) catch-all rejected the
     * configured fighting stance and forced a vanilla fallback. 170 degrees admits that documented
     * 165 degree worst case while still rejecting a wrapped or runaway rotation near PI.
     */
    static final float MAX_SAFE_RADIANS = (float) Math.toRadians(170f);
    private boolean poseValid;

    ArticulatedHumanoidModel(ArticulatedPlayerSettings settings, AuthoredRigWalkAnimation walkAnimation,
                             AuthoredRigWalkAnimation runAnimation, AuthoredRigWalkAnimation jumpAnimation) {
        super(createBodyLayer(settings.torsoHipPivot(), headOnTorso(settings)).bakeRoot(), RenderTypes::entityCutout);
        this.settings = settings;
        this.walkAnimation = walkAnimation;
        this.runAnimation = runAnimation;
        this.jumpAnimation = jumpAnimation;
        this.headFollowsTorso = headOnTorso(settings);
        body = root.getChild("body");
        // The head hangs off the torso only when both flags agree, so the lookup has to follow the
        // same decision the layer builder made or getChild would throw on a name that is not there.
        head = (headFollowsTorso ? body : root).getChild("head");
        // With locomotion.torso-hip-pivot on, the arms hang from the torso instead of from root,
        // so the shoulders keep them when the torso leans. Everything else is looked up the same.
        ModelPart armHost = settings.torsoHipPivot() ? body : root;
        rightUpperArm = armHost.getChild("right_upper_arm"); leftUpperArm = armHost.getChild("left_upper_arm");
        rightLowerArm = rightUpperArm.getChild("right_lower_arm"); leftLowerArm = leftUpperArm.getChild("left_lower_arm");
        rightUpperLeg = root.getChild("right_upper_leg"); leftUpperLeg = root.getChild("left_upper_leg");
        rightLowerLeg = rightUpperLeg.getChild("right_lower_leg"); leftLowerLeg = leftUpperLeg.getChild("left_lower_leg");
        rightUpperArm.getChild("right_elbow_joint"); leftUpperArm.getChild("left_elbow_joint");
        rightUpperLeg.getChild("right_knee_joint"); leftUpperLeg.getChild("left_knee_joint");
    }

    /**
     * locomotion.torso-head-follow on its own does nothing. The neck anchor the head needs to ride
     * only exists in the hip-pivoted layout; with the torso still turning about the neck the head
     * pivot and the neck are the same point already, and parenting the head there would make the
     * lean rotate the head instead of carrying it. So torso-hip-pivot false falls back to the
     * root-head layout that ships today even when torso-head-follow is true.
     */
    private static boolean headOnTorso(ArticulatedPlayerSettings settings) {
        return settings.torsoHeadFollow() && settings.torsoHipPivot();
    }

    void setLocomotion(boolean running, boolean fastRunning, boolean reversed, float gaitWeight) {
        this.running = running;
        this.fastRunning = fastRunning;
        this.reversed = reversed;
        this.gaitWeight = Float.isFinite(gaitWeight) ? Math.clamp(gaitWeight, 0f, 1f) : 1f;
    }

    void setForwardRunWeight(float weight) {
        forwardRunWeight = normalised(weight, 0f);
    }

    void resetArmInertia() { armInertia.reset(); }

    /**
     * @param attackTime   AvatarRenderState.attackTime, which runs 0..1 while swinging and is 0 at rest
     * @param rightHanded  attackArm == RIGHT; the render state already resolves main hand and
     *                     off hand, so nothing here has to know which hand the player prefers
     * @param armed        the attacking hand is holding something. SwingAnimationType cannot be
     *                     used for this: ItemStack.getSwingAnimation falls back to
     *                     SwingAnimation.DEFAULT, which is WHACK, so an empty hand reports WHACK too
     */
    void setAttack(float attackTime, boolean rightHanded, boolean armed, boolean swingActive) {
        this.attackTime = Float.isFinite(attackTime) ? attackTime : 0f;
        boolean selectedRight = boxingHands.advance(this.attackTime, swingActive,
                settings.combatMotion().randomHands() && !armed && bothHandsEmpty, rightHanded,
                () -> java.util.concurrent.ThreadLocalRandom.current().nextBoolean());
        if (selectedRight != this.attackRightHanded) {
            // The held delta is expressed in attack/off-arm roles. Keep it on the same
            // physical limbs when a chained punch chooses the other hand.
            float x = combatHeld[ArticulatedCombatPose.ATTACK_UPPER_X];
            combatHeld[ArticulatedCombatPose.ATTACK_UPPER_X] = combatHeld[ArticulatedCombatPose.OFF_UPPER_X];
            combatHeld[ArticulatedCombatPose.OFF_UPPER_X] = x;
            x = combatHeld[ArticulatedCombatPose.ATTACK_LOWER_X];
            combatHeld[ArticulatedCombatPose.ATTACK_LOWER_X] = combatHeld[ArticulatedCombatPose.OFF_LOWER_X];
            combatHeld[ArticulatedCombatPose.OFF_LOWER_X] = x;
        }
        this.attackRightHanded = selectedRight;
        this.attackArmed = armed;
        this.attackSwingActive = swingActive;
    }

    void resetCombat() {
        boxingHands.reset();
        java.util.Arrays.fill(combatHeld, 0f);
        previousAttackTime = 0f;
        restartWeight = 0f;
        lastCombatAge = Float.NaN;
        attackSwingActive = false;
        strikeOwnership = 0f;
        heldOwnership = 0f;
        stance.reset();
    }

    /** The angle Mojang's one-piece arm has to take for a held item to land on this hand. */
    float handDirection(boolean rightHanded) {
        return ArticulatedCombatPose.handDirection(
                rightHanded ? rightUpperArm.xRot : leftUpperArm.xRot,
                rightHanded ? rightLowerArm.xRot : leftLowerArm.xRot);
    }

    float handShortfall(boolean rightHanded) {
        return ArticulatedCombatPose.handShortfall(
                rightHanded ? rightUpperArm.xRot : leftUpperArm.xRot,
                rightHanded ? rightLowerArm.xRot : leftLowerArm.xRot);
    }

    float shoulderRoll(boolean rightHanded) {
        return rightHanded ? rightUpperArm.zRot : leftUpperArm.zRot;
    }

    void setJumpPhase(ArticulatedJumpTracker.Phase jumpPhase, float landingProgress,
                      ArticulatedJumpTracker.Phase priorJumpPhase, float phaseProgress, float airborneProgress) {
        this.jumpPhase = jumpPhase;
        this.landingProgress = normalised(landingProgress, 0f);
        this.priorJumpPhase = priorJumpPhase;
        this.phaseProgress = normalised(phaseProgress, 1f);
        this.airborneProgress = normalised(airborneProgress, 1f);
    }

    private static float normalised(float value, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : fallback;
    }

    /**
     * @param hipPivotedTorso false builds the layout that ships today: the torso turns about the
     *        neck and the arms hang from root. True turns the torso about the hip and hangs the
     *        arms from the torso, which is where a leaning person's shoulders carry them. Both
     *        layouts place every part in exactly the same world position while standing; see
     *        ArticulatedTorsoHierarchy for the measurements that motivate the second one.
     */
    static LayerDefinition createBodyLayer(boolean hipPivotedTorso, boolean headOnTorso) {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        // Same box and the same texOffs either way: only the pivot moves, and the cube net is
        // derived from the dimensions, so the skin lands on identical pixels in both layouts.
        var torso = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
                        .addBox(-4, hipPivotedTorso ? ArticulatedTorsoHierarchy.HIP_PIVOTED_BOX_ORIGIN_Y
                                : ArticulatedTorsoHierarchy.NECK_PIVOTED_BOX_ORIGIN_Y, -2, 8, 12, 4),
                hipPivotedTorso ? PartPose.offset(0, ArticulatedTorsoHierarchy.HIP_Y, 0) : PartPose.ZERO);
        // Unchanged box and texOffs; only the parent and the pivot offset differ. On the torso the
        // offset is one torso height above the hip, which is root y 12 - 12 = 0: the same point the
        // head occupies today, so a standing player is identical either way.
        (headOnTorso ? torso : root).addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                headOnTorso ? PartPose.offset(0, HeadLookCompensation.NECK_OFFSET_Y, 0) : PartPose.ZERO);
        // Arms hang from the torso only in the hip-pivoted layout; the legs stay on root in both,
        // so the feet stay under the player.
        var armHost = hipPivotedTorso ? torso : root;
        float armPivotY = hipPivotedTorso ? ArticulatedTorsoHierarchy.ARM_OFFSET_Y_FROM_HIP
                : ArticulatedTorsoHierarchy.SHOULDER_Y;
        // Seven-pixel upper and five-pixel lower sections meet exactly at elbow height.
        var rua = armHost.addOrReplaceChild("right_upper_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 7, 4), PartPose.offset(-5, armPivotY, 0));
        rua.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(40, 23).addBox(-3, 0, -2, 4, 5, 4), PartPose.offset(0, 5, 0));
        rua.addOrReplaceChild("right_elbow_joint", jointConnector(ArticulatedJointGeometry.RIGHT_ELBOW_TEX_U,
                ArticulatedJointGeometry.RIGHT_ELBOW_TEX_V, ArticulatedJointGeometry.RIGHT_ARM_ORIGIN_X, false),
                PartPose.offset(0, ArticulatedJointGeometry.ELBOW_PIVOT_Y, 0));
        var lua = armHost.addOrReplaceChild("left_upper_arm", CubeListBuilder.create().texOffs(32, 48).mirror().addBox(-1, -2, -2, 4, 7, 4), PartPose.offset(5, armPivotY, 0));
        lua.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(32, 55).mirror().addBox(-1, 0, -2, 4, 5, 4), PartPose.offset(0, 5, 0));
        lua.addOrReplaceChild("left_elbow_joint", jointConnector(ArticulatedJointGeometry.LEFT_ELBOW_TEX_U,
                ArticulatedJointGeometry.LEFT_ELBOW_TEX_V, ArticulatedJointGeometry.LEFT_ARM_ORIGIN_X, true),
                PartPose.offset(0, ArticulatedJointGeometry.ELBOW_PIVOT_Y, 0));
        var rul = root.addOrReplaceChild("right_upper_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(-1.9f, 12, 0));
        rul.addOrReplaceChild("right_lower_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        rul.addOrReplaceChild("right_knee_joint", jointConnector(ArticulatedJointGeometry.RIGHT_KNEE_TEX_U,
                ArticulatedJointGeometry.RIGHT_KNEE_TEX_V, ArticulatedJointGeometry.LEG_ORIGIN_X, false),
                PartPose.offset(0, ArticulatedJointGeometry.KNEE_PIVOT_Y, 0));
        var lul = root.addOrReplaceChild("left_upper_leg", CubeListBuilder.create().texOffs(16, 48).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(1.9f, 12, 0));
        lul.addOrReplaceChild("left_lower_leg", CubeListBuilder.create().texOffs(16, 54).mirror().addBox(-2, 0, -2, 4, 6, 4), PartPose.offset(0, 6, 0));
        lul.addOrReplaceChild("left_knee_joint", jointConnector(ArticulatedJointGeometry.LEFT_KNEE_TEX_U,
                ArticulatedJointGeometry.LEFT_KNEE_TEX_V, ArticulatedJointGeometry.LEG_ORIGIN_X, true),
                PartPose.offset(0, ArticulatedJointGeometry.KNEE_PIVOT_Y, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A small parent-held cuboid that fills the wedge a bent rigid-cuboid joint otherwise opens. */
    private static CubeListBuilder jointConnector(int textureU, int textureV, float limbOriginX, boolean mirrored) {
        var cube = CubeListBuilder.create().texOffs(textureU, textureV);
        if (mirrored) cube = cube.mirror();
        return cube.addBox(limbOriginX + ArticulatedJointGeometry.SEAM_INSET,
                -ArticulatedJointGeometry.CONNECTOR_HALF_HEIGHT,
                -ArticulatedJointGeometry.CONNECTOR_HALF_DEPTH,
                ArticulatedJointGeometry.CONNECTOR_WIDTH,
                ArticulatedJointGeometry.CONNECTOR_HEIGHT,
                ArticulatedJointGeometry.CONNECTOR_DEPTH);
    }

    /** False when the last setupAnim produced a non-finite or out-of-range joint. */
    boolean poseValid() { return poseValid; }

    @Override public void setupAnim(AvatarRenderState state) {
        resetPose();
        poseValid = true;
        float lookYaw = state.yRot * ((float)Math.PI / 180f);
        float lookPitch = state.xRot * ((float)Math.PI / 180f);
        head.yRot = lookYaw;
        head.xRot = lookPitch;
        animateBody(state);
        // Outside animateBody on purpose: the jump branches return early, and the guard's clock
        // still has to tick down while the player is in the air so it cannot resume stale on
        // landing. It runs before applyHeadLook for the same reason everything else does - the
        // head look is solved from body's FINAL rotation, so the world look survives the torso
        // turning into the guard.
        applyStance(state.ageInTicks);
        applyHeadLook(lookPitch, lookYaw);
    }

    /**
     * Puts the head's world rotation back to exactly the mouse look after it was parented to the
     * leaning torso. Off by default and a no-op in the root-head layout, where the head already
     * carries the look rotation and nothing has composed anything onto it.
     *
     * <p>The result is deliberately not run through {@link #limited}: near a head yaw of 90 degrees
     * the exact answer can be a large xRot and zRot that compose to the correct orientation, so a
     * ceiling would reject a correct pose. A refusal from the solver leaves the raw look rotation
     * assigned above in place, which is what the shipping layout renders.
     */
    private void applyHeadLook(float lookPitch, float lookYaw) {
        if (!headFollowsTorso) return;
        if (!HeadLookCompensation.solve(body.xRot, body.yRot, body.zRot, lookPitch, lookYaw, headLook)) return;
        head.xRot = headLook[0];
        head.yRot = headLook[1];
        head.zRot = headLook[2];
    }

    private void animateBody(AvatarRenderState state) {
        motionAge = state.ageInTicks;
        rawMotionSpeed = state.walkAnimationSpeed;
        // Sideways travel has no authored clip, so the gait fades toward the neutral pose
        // rather than pretending a confident forward stride.
        float speed = Math.clamp(state.walkAnimationSpeed, 0f, 1f) * gaitWeight;
        gaitSpeed = speed;
        groundedFrame = jumpPhase == ArticulatedJumpTracker.Phase.GROUND;
        // walkAnimationPos is vanilla's accumulated limb-swing distance, not radians of a
        // gait cycle, and it only ever grows. Accumulating a signed delta keeps the same
        // cadence while letting a reversal play the authored loop backwards without a jump.
        // Walk and run differ by up to 18 degrees at the same cycle position, so the clip
        // is cross-faded rather than swapped on the frame isSprinting() toggles.
        runWeight = gaitBlend.advance(state.ageInTicks, running, settings.gaitBlendTicks());
        float limbSwingScale = settings.limbSwingScale()
                + (settings.runLimbSwingScale() - settings.limbSwingScale()) * runWeight;
        float phase = gaitPhase.advance(state.walkAnimationPos, limbSwingScale, reversed);
        if (!Float.isFinite(phase) || !Float.isFinite(speed)) {
            poseValid = false;
            return;
        }
        if (jumpPhase != ArticulatedJumpTracker.Phase.GROUND) {
            armInertia.reset();
            // Take-off, the airborne poses and the landing recovery each own the torso
            // outright. Dropping the eased value here keeps a stale rotation from leaking
            // into them, and makes the first grounded frame afterwards seed on the authored
            // pose instead of drifting into it.
            torsoFollow.reset();
            boolean nowAirborne = airborne(jumpPhase);
            if (nowAirborne && !wasAirborneFrame) {
                // Preserve the exact current stride at take-off, then release it into the
                // authored airborne pose. This prevents a running stride from snapping
                // into the authored symmetric jump frame in one render tick.
                applyLocomotionPose(phase, speed);
                takeOffPose = snapshot();
            } else if (!nowAirborne) {
                takeOffPose = null;
            }
            wasAirborneFrame = nowAirborne;
            int frame = frameFor(jumpPhase);
            if (jumpPhase == ArticulatedJumpTracker.Phase.LAND) {
                // Impact is immediate; only recovery eases back toward the live gait.
                applyLocomotionPose(phase, speed);
                float[] recovered = snapshot();
                applyJumpPose(jumpAnimation.sampleFrame(frame));
                blendToward(recovered, smoothstep(landingProgress));
                return;
            }
            if (airborne(priorJumpPhase) && phaseProgress < 1f) {
                applyJumpPose(jumpAnimation.sampleFrame(frameFor(priorJumpPhase)));
                float[] previous = snapshot();
                applyJumpPose(jumpAnimation.sampleFrame(frame));
                blendToward(previous, 1f - smoothstep(phaseProgress));
            } else {
                applyJumpPose(jumpAnimation.sampleFrame(frame));
            }
            if (takeOffPose != null && airborneProgress < 1f) {
                blendToward(takeOffPose, 1f - smoothstep(airborneProgress));
            }
            return;
        }
        wasAirborneFrame = false;
        takeOffPose = null;
        applyLocomotionPose(phase, speed);
        // Ground running only, and only here: the jump branches above have already returned, and
        // applyLocomotionPose is deliberately left untouched because take-off and landing call it
        // too. Placed before the easing so the torso follow lags the accent rather than the accent
        // overwriting an eased value, which is also what makes the head trail it: the head look is
        // solved from body's final rotation, so it needs no rotation of its own.
        applyContactAccent(phase, speed);
        // Rotation-only easing of the torso, and of nothing else. The eased value is always a
        // convex combination of values the two calls above produced, so it cannot pass the YAML
        // ceiling, cannot widen the gap between body and shoulders beyond what they already ask
        // for, and reaches exactly zero at a standstill because the authored torso is exactly zero
        // there and the accent is exactly zero there. locomotion.torso-follow-ticks 0.0 skips it.
        if (torsoFollow.advance(state.ageInTicks, settings.torsoFollowTicks(), body.xRot, body.yRot, body.zRot)) {
            body.xRot = torsoFollow.x();
            body.yRot = torsoFollow.y();
            body.zRot = torsoFollow.z();
        }
        // Arms already inherit the eased torso. Only their relative shoulder pitch lags;
        // elbows stay attached and the final head-look solve still sees the unchanged torso.
        float armWeight = settings.torsoHipPivot()
                ? RunArmInertia.weight(runWeight, speed, forwardRunWeight, reversed) : 0f;
        if (armInertia.advance(state.ageInTicks, settings.runArmFollowTicks(),
                radians(settings.runArmLagDegrees()), armWeight, rightUpperArm.xRot, leftUpperArm.xRot)) {
            float limit = radians(Math.max(settings.walkStrideDegrees(), settings.runStrideDegrees()));
            rightUpperArm.xRot = limited(armInertia.right(), 1f, limit);
            leftUpperArm.xRot = limited(armInertia.left(), 1f, limit);
        }
        // Derive shoulder yaw from the actual final arm pitches, after arm lag, so the
        // shoulder travelling forward agrees with the arm visible in this very frame.
        applyShoulderYaw();
        // Last, because applyShoulderYaw OVERWRITES body.yRot rather than adding to it, and
        // because the strike is deliberately not run through the torso easing: a swing lasts about
        // six ticks and torso-follow-ticks 2.0 would delay it by a quarter of that and cut its
        // amplitude to roughly 0.7. The curve is already continuous in value and slope, so there
        // is nothing for the easing to protect against. applyHeadLook still runs after this and
        // still solves from body's final rotation, so the world look survives the torso turning.
        applyCombatPose(state.ageInTicks);
    }

    /**
     * The upper-body attack, ADDED to the pose the locomotion layers produced. The legs are not in
     * the delta at all, so running while punching keeps the stride instead of freezing into a
     * standing pose.
     */
    private void applyCombatPose(float ageInTicks) {
        var combat = settings.combatMotion();
        if (!combat.enabled()) { resetCombat(); return; }
        boolean armed = attackArmed;
        var move = armed ? combat.sword() : combat.barehand();
        boolean moveEnabled = armed ? combat.swordEnabled() : combat.barehandEnabled();
        boolean restarted = ArticulatedCombatPose.swingRestarted(previousAttackTime, attackTime);
        previousAttackTime = attackTime;
        // A tick-based, bounded release of the pose held at the moment a second attack restarted
        // the swing clock. It is the only state here, it decays to zero, and it never grows.
        float delta = Float.isNaN(lastCombatAge) || !Float.isFinite(ageInTicks)
                ? 0f : ageInTicks - lastCombatAge;
        if (Float.isFinite(ageInTicks)) lastCombatAge = ageInTicks;
        if (!(delta >= 0f) || delta > 8f) delta = 0f;
        float release = combat.blendTicks() > 0f ? delta / combat.blendTicks() : 1f;
        restartWeight = Math.clamp(restartWeight - release, 0f, 1f);

        float attackElbow = attackRightHanded ? rightLowerArm.xRot : leftLowerArm.xRot;
        boolean solved = moveEnabled && ArticulatedCombatPose.solve(move, attackTime,
                attackRightHanded, 1f, attackElbow, combatDelta);
        if (!solved) java.util.Arrays.fill(combatDelta, 0f);
        // The off arm keeps the guard instead of counter-swinging out of it. The guard weight read
        // here is the one the stance layer produced on the previous frame, because the stance runs
        // after this in the same frame; that is less than one tick of lag and is bounded by the
        // blend windows, and with the guard block off it is exactly zero and nothing changes.
        if (solved) {
            ArticulatedCombatPose.yieldOffArm(combatDelta, stance.easedWeight(), move.offArmGuardYield());
        }
        float ownership = solved ? ArticulatedCombatPose.guardHandover(move, attackTime) : 0f;
        // combatHeld already carries the delta applied on the frame before the restart, because
        // it is only refreshed while nothing is being released. So there is nothing to snapshot.
        if (restarted) restartWeight = 1f;
        for (int slot = 0; slot < ArticulatedCombatPose.VALUE_COUNT; slot++) {
            combatDelta[slot] += (combatHeld[slot] - combatDelta[slot]) * restartWeight;
        }
        // The handover weight is released on exactly the same clock as the pose it belongs to, so a
        // combo cannot leave the guard half out of the way after the strike it belonged to is gone.
        ownership += (heldOwnership - ownership) * restartWeight;
        strikeOwnership = Math.clamp(ownership, 0f, 1f);
        if (restartWeight <= 0f) {
            System.arraycopy(combatDelta, 0, combatHeld, 0, combatHeld.length);
            heldOwnership = strikeOwnership;
        }

        boolean quiet = true;
        for (float value : combatDelta) if (value != 0f) { quiet = false; break; }
        if (quiet) return;

        ModelPart attackUpper = attackRightHanded ? rightUpperArm : leftUpperArm;
        ModelPart attackLower = attackRightHanded ? rightLowerArm : leftLowerArm;
        ModelPart offUpper = attackRightHanded ? leftUpperArm : rightUpperArm;
        ModelPart offLower = attackRightHanded ? leftLowerArm : rightLowerArm;
        float armCeiling = radians(Math.max(settings.walkStrideDegrees(), settings.runStrideDegrees())
                + ArticulatedCombatPose.MAXIMUM_ARM_DEGREES);
        float elbowCeiling = radians(Math.max(settings.walkElbowDegrees(), settings.runElbowDegrees())
                + ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES);
        body.xRot = limited(body.xRot + combatDelta[ArticulatedCombatPose.BODY_X], 1f,
                radians(ArticulatedCombatPose.MAXIMUM_BODY_PITCH_DEGREES + settings.jumpBodyDegrees()));
        body.yRot = limited(body.yRot + combatDelta[ArticulatedCombatPose.BODY_Y], 1f,
                radians(ArticulatedCombatPose.MAXIMUM_BODY_YAW_DEGREES));
        // The run contact accent owns this axis too, so the ceiling has to admit both of them.
        body.zRot = limited(body.zRot + combatDelta[ArticulatedCombatPose.BODY_Z], 1f,
                radians(ArticulatedCombatPose.MAXIMUM_BODY_ROLL_DEGREES + settings.runContactRollDegrees()));
        attackUpper.xRot = limited(attackUpper.xRot + combatDelta[ArticulatedCombatPose.ATTACK_UPPER_X], 1f, armCeiling);
        // Model forward is -Z: negative yaw brings the right fist (x < 0) toward centre.
        if (!armed) attackUpper.yRot += (attackRightHanded ? -1f : 1f)
                * radians(combat.inwardYawDegrees()) * strikeOwnership;
        attackUpper.zRot = limited(attackUpper.zRot + combatDelta[ArticulatedCombatPose.ATTACK_UPPER_Z], 1f,
                radians(ArticulatedCombatPose.MAXIMUM_ARM_DEGREES));
        attackLower.xRot = limited(attackLower.xRot + combatDelta[ArticulatedCombatPose.ATTACK_LOWER_X], 1f, elbowCeiling);
        offUpper.xRot = limited(offUpper.xRot + combatDelta[ArticulatedCombatPose.OFF_UPPER_X], 1f, armCeiling);
        offLower.xRot = limited(offLower.xRot + combatDelta[ArticulatedCombatPose.OFF_LOWER_X], 1f, elbowCeiling);
    }

    /**
     * The fighting guard: raised by a bare-handed swing, held for a few seconds, then released
     * back into whatever the locomotion layers are doing.
     *
     * <p>With combat-motion.barehand.enabled false there is no strike at all and this still works,
     * which is the point of the first F5 stage: the punch can be switched off and the guard judged
     * on its own. With the strike on, the two do not simply add: the strike's ownership is taken
     * back out of the guard here, so the punching arm is not folded into a 130 degree guard elbow
     * while it is punching, and the strike's recovery arrives at the guard instead of at neutral.
     */
    private void applyStance(float ageInTicks) {
        var combat = settings.combatMotion();
        var block = combat.stance();
        if (!combat.enabled() || !block.enabled()) { stance.reset(); return; }
        boolean barehand = !attackArmed;
        // Everything unsupported has already been refused by shouldReplace, so what is left to
        // decide here is the ground: a guard held rigid through a jump reads as a frozen doll.
        boolean supported = groundedFrame;
        if (combat.logOnce() && attackSwingActive && !loggedStanceSwing) {
            loggedStanceSwing = true;
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                    "Barehand stance swing observed: attackTime={}, barehand={}, grounded={}",
                    attackTime, barehand, supported);
        }
        stance.latchSide(attackRightHanded);
        float walkish = 1f + (block.walkWeight() - 1f) * BarehandCombatStance.unit(gaitSpeed * 2f);
        float target = supported
                ? walkish + (block.sprintWeight() - walkish) * BarehandCombatStance.unit(runWeight)
                : block.airborneWeight();
        float weight = stance.advance(ageInTicks, attackTime, attackSwingActive, barehand, supported,
                block.holdTicks(), block.blendInTicks(), block.blendOutTicks(), target);
        if (combat.logOnce() && weight > 0f && !loggedStanceRaised) {
            loggedStanceRaised = true;
            org.slf4j.LoggerFactory.getLogger("MurimRPG-Articulated").info(
                    "Barehand stance raised: weight={}, holdTicksLeft={}, target={}",
                    weight, stance.holdTicksLeft(), target);
        }
        // The ready legs are for standing still only. Moving, the gait owns the lower body.
        float groundedIdle = supported ? 1f - BarehandCombatStance.unit(gaitSpeed * 4f) : 0f;
        if (!BarehandCombatStance.pose(block, weight, groundedIdle, stance.rearIsRight(),
                ageInTicks, stanceDelta)) {
            return;
        }
        boolean rearRight = stance.rearIsRight();
        // The strike gets right of way over the guard for as long as it owns the upper body, and
        // hands it back before the swing clock reaches 1 - so the punch is a punch, and its
        // recovery lands on the guard rather than on the neutral pose. Which arm gives way is the
        // one actually attacking, which is normally the latched rear hand but need not be if the
        // off hand swings while a guard from the other hand is still up.
        boolean attackIsRear = rearRight == attackRightHanded;
        BarehandCombatStance.yieldToStrike(stanceDelta, strikeOwnership,
                BarehandCombatStance.STRIKE_BODY_SHARE,
                attackIsRear ? BarehandCombatStance.STRIKE_FRONT_SHARE : 1f,
                attackIsRear ? 1f : BarehandCombatStance.STRIKE_FRONT_SHARE);
        ModelPart rearUpper = rearRight ? rightUpperArm : leftUpperArm;
        ModelPart rearLower = rearRight ? rightLowerArm : leftLowerArm;
        ModelPart frontUpper = rearRight ? leftUpperArm : rightUpperArm;
        ModelPart frontLower = rearRight ? leftLowerArm : rightLowerArm;
        ModelPart rearLeg = rearRight ? rightUpperLeg : leftUpperLeg;
        ModelPart frontLeg = rearRight ? leftUpperLeg : rightUpperLeg;
        ModelPart rearKnee = rearRight ? rightLowerLeg : leftLowerLeg;
        ModelPart frontKnee = rearRight ? leftLowerLeg : rightLowerLeg;
        float armCeiling = radians(Math.max(settings.walkStrideDegrees(), settings.runStrideDegrees())
                + BarehandCombatStance.MAXIMUM_UPPER_ARM_DEGREES);
        float elbowCeiling = radians(Math.max(settings.walkElbowDegrees(), settings.runElbowDegrees())
                + BarehandCombatStance.MAXIMUM_ELBOW_DEGREES);
        float legCeiling = radians(Math.max(settings.walkStrideDegrees(), settings.runStrideDegrees())
                + BarehandCombatStance.MAXIMUM_LEG_DEGREES);
        float kneeCeiling = radians(Math.max(settings.walkKneeDegrees(), settings.runKneeDegrees())
                + BarehandCombatStance.MAXIMUM_KNEE_DEGREES);
        body.xRot = limited(body.xRot + stanceDelta[BarehandCombatStance.BODY_X], 1f,
                radians(BarehandCombatStance.MAXIMUM_BODY_PITCH_DEGREES + settings.jumpBodyDegrees()));
        body.yRot = limited(body.yRot + stanceDelta[BarehandCombatStance.BODY_Y], 1f,
                radians(ArticulatedCombatPose.MAXIMUM_BODY_YAW_DEGREES));
        frontUpper.xRot = limited(frontUpper.xRot + stanceDelta[BarehandCombatStance.FRONT_UPPER_X], 1f, armCeiling);
        frontUpper.zRot = limited(frontUpper.zRot + stanceDelta[BarehandCombatStance.FRONT_UPPER_Z], 1f,
                radians(BarehandCombatStance.MAXIMUM_ARM_ROLL_DEGREES));
        frontLower.xRot = limited(frontLower.xRot + stanceDelta[BarehandCombatStance.FRONT_LOWER_X], 1f, elbowCeiling);
        rearUpper.xRot = limited(rearUpper.xRot + stanceDelta[BarehandCombatStance.REAR_UPPER_X], 1f, armCeiling);
        rearUpper.zRot = limited(rearUpper.zRot + stanceDelta[BarehandCombatStance.REAR_UPPER_Z], 1f,
                radians(BarehandCombatStance.MAXIMUM_ARM_ROLL_DEGREES));
        rearLower.xRot = limited(rearLower.xRot + stanceDelta[BarehandCombatStance.REAR_LOWER_X], 1f, elbowCeiling);
        frontLeg.xRot = limited(frontLeg.xRot + stanceDelta[BarehandCombatStance.FRONT_LEG_X], 1f, legCeiling);
        rearLeg.xRot = limited(rearLeg.xRot + stanceDelta[BarehandCombatStance.REAR_LEG_X], 1f, legCeiling);
        frontKnee.xRot = limited(frontKnee.xRot + stanceDelta[BarehandCombatStance.FRONT_KNEE_X], 1f, kneeCeiling);
        rearKnee.xRot = limited(rearKnee.xRot + stanceDelta[BarehandCombatStance.REAR_KNEE_X], 1f, kneeCeiling);
    }

    /**
     * A short roll accent as each foot takes the weight. Roll and nothing else: the pitch axis is
     * the forward lean this must not deepen and has 0.050 degrees of headroom under
     * run.body-degrees anyway, and the yaw axis is pinned at exactly zero everywhere because
     * body.yRot's sign has never been verified in game.
     *
     * <p>Off unless every condition for a grounded forward run holds. run.contact-roll-degrees 0.0
     * is off; runWeight 0 is a walk, and gaitBlend already eases it, so the accent fades in and out
     * with the run clip itself; speed 0 is a standstill and also carries the strafe weight; and a
     * reversed gait gets nothing, because a foot strike read off a loop played backwards is not one.
     *
     * <p>The sum goes through {@link #limited} against the same run ceiling the authored torso uses,
     * so the ceiling invariant is unchanged. At the maximum the YAML allows the sum is 3.000 + 2.000
     * against a 9.5 ceiling, so in practice the clamp never fires and never distorts the shape.
     */
    private void applyContactAccent(float phase, float speed) {
        if (reversed || runWeight <= 0f || speed <= 0f) return;
        float degrees = settings.runContactRollDegrees();
        if (!(degrees > 0f)) return;
        float accent = RunContactAccent.rollRadians(phase, radians(degrees) * runWeight * speed);
        if (styleTorso()) accent *= settings.motionStyle().rollScale();
        if (accent == 0f) return;
        body.zRot = limited(body.zRot + accent, 1f, radians(settings.runBodyDegrees()));
    }

    private static int frameFor(ArticulatedJumpTracker.Phase phase) {
        return switch (phase) {
            case RISE -> 1;
            case APEX -> 2;
            case FALL -> 3;
            case LAND -> 4;
            case GROUND -> 0;
        };
    }

    private static boolean airborne(ArticulatedJumpTracker.Phase phase) {
        return phase == ArticulatedJumpTracker.Phase.RISE
                || phase == ArticulatedJumpTracker.Phase.APEX
                || phase == ArticulatedJumpTracker.Phase.FALL;
    }

    private void applyLocomotionPose(float phase, float speed) {
        applyBaseLocomotionPose(phase, speed);
        var style = settings.motionStyle();
        if (!style.idleEnabled() || !headFollowsTorso) return;
        // Raw movement speed, not strafe-reduced gait weight: sideways travel is not idle.
        float weight = LocomotionStyle.idleWeight(rawMotionSpeed, style.idleFadeSpeed());
        if (weight <= 0f) return;
        float breath = LocomotionStyle.breath(motionAge, style.breathSeconds(), style.breathDegrees());
        body.xRot = lerp(body.xRot, radians(style.idleLean()) + breath, weight);
        rightLowerArm.xRot = lerp(rightLowerArm.xRot, -radians(style.idleElbow()) - breath, weight);
        leftLowerArm.xRot = lerp(leftLowerArm.xRot, -radians(style.idleElbow()) - breath, weight);
        // Both feet and all pivots remain in the baseline standing pose.
    }

    private void applyBaseLocomotionPose(float phase, float speed) {
        if (runWeight <= 0f) {
            applyClip(walkAnimation, phase, speed, false);
            return;
        }
        if (runWeight >= 1f) {
            applyClip(runAnimation, phase, speed, true);
            return;
        }
        applyClip(walkAnimation, phase, speed, false);
        float[] walking = snapshot();
        applyClip(runAnimation, phase, speed, true);
        blendToward(walking, 1f - runWeight);
    }

    private void applyClip(AuthoredRigWalkAnimation clip, float phase, float speed, boolean run) {
        var authored = clip.sample(phase).scaled(speed);
        var style = settings.motionStyle();
        if (run && style.runEnabled()) {
            float weight = reversed ? 0f : forwardRunWeight;
            authored.rightLeg().x = LocomotionStyle.running(authored.rightLeg().x, style.legGain(), 0f, speed, weight);
            authored.leftLeg().x = LocomotionStyle.running(authored.leftLeg().x, style.legGain(), 0f, speed, weight);
            authored.rightLowerLeg().x = LocomotionStyle.running(authored.rightLowerLeg().x, style.kneeGain(), 0f, speed, weight);
            authored.leftLowerLeg().x = LocomotionStyle.running(authored.leftLowerLeg().x, style.kneeGain(), 0f, speed, weight);
            authored.rightArm().x = LocomotionStyle.running(authored.rightArm().x, style.armGain(), 0f, speed, weight);
            authored.leftArm().x = LocomotionStyle.running(authored.leftArm().x, style.armGain(), 0f, speed, weight);
            authored.rightForeArm().x = LocomotionStyle.running(authored.rightForeArm().x, 1f, -style.elbowBias(), speed, weight);
            authored.leftForeArm().x = LocomotionStyle.running(authored.leftForeArm().x, 1f, -style.elbowBias(), speed, weight);
            if (headFollowsTorso)
                authored.body().x = LocomotionStyle.running(authored.body().x, 1f, style.leanBias(), speed, weight);
        }
        float strideLimit = radians(run ? settings.runStrideDegrees() : settings.walkStrideDegrees());
        float kneeLimit = radians(run ? settings.runKneeDegrees() : settings.walkKneeDegrees());
        float elbowLimit = radians(run ? settings.runElbowDegrees() : settings.walkElbowDegrees());
        // The run clip authors 8.5 to 9.2 degrees of forward lean. A single walk-sized
        // ceiling truncated it to a constant 4.5, leaving the run torso identical to walk.
        float bodyLimit = radians(run ? settings.runBodyDegrees() : settings.bodyTurnDegrees());
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
        if (styleTorso()) body.zRot *= style.rollScale();
        applyShoulderYaw();
    }

    private boolean styleTorso() { return headFollowsTorso && settings.motionStyle().torsoEnabled(); }

    private void applyShoulderYaw() {
        if (!styleTorso()) return;
        var style = settings.motionStyle();
        body.yRot = LocomotionStyle.shoulderYaw(rightUpperArm.xRot, leftUpperArm.xRot,
                style.yawGain(), style.yawLimit());
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
