package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;

/**
 * The {@code combat-motion} block: one master switch, one switch per move, one for the hand anchor,
 * and the angles and phase boundaries each move uses. Everything is off by default, and every value
 * is validated, so a bad number disables the combat layer for the run instead of taking the client
 * down with it - {@link #loadOrDisabled} is what makes that true.
 *
 * <p>Read once when the Minecraft client starts, like every other block in the file, so a change
 * needs a client restart.
 */
record ArticulatedCombatSettings(boolean enabled, boolean barehandEnabled, boolean swordEnabled,
                                 boolean handAnchorEnabled, boolean logOnce, float blendTicks,
                                 Move barehand, Move sword,
                                 BarehandCombatStance.Settings stance, String stanceFailure,
                                 boolean randomHands, float inwardYawDegrees) {

    /**
     * One attack's shape. Fractions are of the swing, angles are degrees.
     *
     * <p>{@code armLag} and {@code elbowLag} are places in the kinetic chain, not delays in time:
     * the torso reads the drive curve directly, the upper arm reads it warped by {@code armLag} and
     * the forearm by {@code armLag + elbowLag}. {@code guardYield} is how much of the fighting
     * guard this strike is allowed to push aside while it runs, and {@code offArmGuardYield} how
     * much of its own counter-swing it gives up so the off hand can stay in that guard.
     */
    record Move(float anticipation, float acceleration, float contact, float windUp, float settle,
                float elbowLag, float bodyYawDegrees, float bodyPitchDegrees, float upperArmDegrees,
                float elbowGatherDegrees, float elbowStrikeDegrees, float armRollDegrees,
                float counterArmDegrees, float counterElbowDegrees,
                float armLag, float bodyRollDegrees, float guardYield, float offArmGuardYield) { }

    static final ArticulatedCombatSettings DISABLED = new ArticulatedCombatSettings(
            false, false, false, false, false, 2f,
            new Move(.20f, .46f, .58f, .30f, .10f, .09f, 16f, 3f, 76f, 58f, 12f, 0f, 4f, 8f,
                    .05f, 2f, 1f, 1f),
            new Move(.24f, .50f, .62f, .50f, .10f, .10f, 20f, 4f, 60f, 74f, 26f, 12f, 26f, 14f,
                    .06f, 3f, 1f, .75f),
            BarehandCombatStance.DISABLED, null, false, 0f);

    /**
     * @return the block as configured, or {@link #DISABLED} when any value is out of range. The
     *         reason is returned in {@code failure} so the caller can log it exactly once instead
     *         of throwing, because a mistyped combat angle must not stop the client from starting.
     */
    static ArticulatedCombatSettings loadOrDisabled(YamlDocument yaml, String[] failure) {
        try {
            return load(yaml);
        } catch (RuntimeException error) {
            if (failure != null && failure.length > 0) failure[0] = String.valueOf(error.getMessage());
            return DISABLED;
        }
    }

    private static ArticulatedCombatSettings load(YamlDocument yaml) {
        // The stance block validates on its own so a bad angle there cannot take the strike layer
        // down with it, and the reason is carried out for a single log line.
        String[] stanceFailure = new String[1];
        var stance = BarehandCombatStance.loadOrDisabled(yaml, stanceFailure);
        return new ArticulatedCombatSettings(
                flag(yaml, "enabled"), flag(yaml, "barehand.enabled"), flag(yaml, "sword.enabled"),
                flag(yaml, "hand-anchor.enabled"), flag(yaml, "diagnostics.log-once"),
                value(yaml, "blend-ticks", 2f, 0f, 8f),
                move(yaml, "barehand", DISABLED.barehand()),
                move(yaml, "sword", DISABLED.sword()),
                stance, stanceFailure[0], flag(yaml, "barehand.random-hands"),
                value(yaml, "barehand.inward-yaw-degrees", 0f, 0f, 20f));
    }

    private static Move move(YamlDocument yaml, String block, Move fallback) {
        float anticipation = value(yaml, block + ".anticipation", fallback.anticipation(), .02f, .5f);
        float acceleration = value(yaml, block + ".acceleration", fallback.acceleration(), .04f, .8f);
        float contact = value(yaml, block + ".contact", fallback.contact(), .06f, .95f);
        if (!(anticipation < acceleration && acceleration < contact)) {
            throw new IllegalArgumentException("combat-motion." + block
                    + " phases must increase: anticipation < acceleration < contact");
        }
        return new Move(anticipation, acceleration, contact,
                value(yaml, block + ".wind-up", fallback.windUp(), 0f, 1f),
                value(yaml, block + ".settle", fallback.settle(), 0f, .5f),
                value(yaml, block + ".elbow-lag", fallback.elbowLag(), 0f, .4f),
                value(yaml, block + ".body-yaw-degrees", fallback.bodyYawDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_BODY_YAW_DEGREES),
                value(yaml, block + ".body-pitch-degrees", fallback.bodyPitchDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_BODY_PITCH_DEGREES),
                value(yaml, block + ".upper-arm-degrees", fallback.upperArmDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_ARM_DEGREES),
                value(yaml, block + ".elbow-gather-degrees", fallback.elbowGatherDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES),
                value(yaml, block + ".elbow-strike-degrees", fallback.elbowStrikeDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES),
                value(yaml, block + ".arm-roll-degrees", fallback.armRollDegrees(), 0f, 30f),
                value(yaml, block + ".counter-arm-degrees", fallback.counterArmDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_ARM_DEGREES),
                value(yaml, block + ".counter-elbow-degrees", fallback.counterElbowDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_ELBOW_DEGREES),
                value(yaml, block + ".arm-lag", fallback.armLag(), 0f,
                        ArticulatedCombatPose.MAXIMUM_CHAIN_LAG),
                value(yaml, block + ".body-roll-degrees", fallback.bodyRollDegrees(), 0f,
                        ArticulatedCombatPose.MAXIMUM_BODY_ROLL_DEGREES),
                value(yaml, block + ".guard-yield", fallback.guardYield(), 0f, 1f),
                value(yaml, block + ".off-arm-guard-yield", fallback.offArmGuardYield(), 0f, 1f));
    }

    private static boolean flag(YamlDocument yaml, String key) {
        return Boolean.TRUE.equals(yaml.get("combat-motion." + key));
    }

    private static float value(YamlDocument yaml, String key, float fallback, float minimum, float maximum) {
        Object raw = yaml.get("combat-motion." + key);
        float result = raw instanceof Number number ? number.floatValue() : fallback;
        if (!Float.isFinite(result) || result < minimum || result > maximum) {
            throw new IllegalArgumentException("articulated-player.yml.combat-motion." + key
                    + " must be within " + minimum + ".." + maximum);
        }
        return result;
    }
}
