package com.murimrpg.client;

/** Pure state selection kept independent from rendering for regression testing. */
public final class PalAnimationStateSelector {
    public enum Locomotion {
        VANILLA, IDLE, WALK, RUN, FAST_RUN,
        JUMP_TAKEOFF, JUMP_RISE, JUMP_APEX, JUMP_FALL, JUMP_LAND
    }
    public enum Combat { NONE, PUNCH, SWORD_SWING }

    private PalAnimationStateSelector() { }

    public static Locomotion locomotion(boolean compatible, boolean onGround, boolean sprinting,
                                        boolean takeoffActive, boolean landingActive,
                                        double horizontalSpeed, double verticalSpeed,
                                        double movingThreshold, double fastRunThreshold,
                                        double riseVelocityThreshold, double apexVelocityThreshold) {
        if (!compatible || !Double.isFinite(horizontalSpeed) || !Double.isFinite(verticalSpeed)) {
            return Locomotion.VANILLA;
        }
        if (!onGround) {
            if (takeoffActive) return Locomotion.JUMP_TAKEOFF;
            if (verticalSpeed > riseVelocityThreshold) return Locomotion.JUMP_RISE;
            if (verticalSpeed < -apexVelocityThreshold) return Locomotion.JUMP_FALL;
            return Locomotion.JUMP_APEX;
        }
        if (landingActive) return Locomotion.JUMP_LAND;
        if (horizontalSpeed < movingThreshold) return Locomotion.IDLE;
        if (!sprinting) return Locomotion.WALK;
        return horizontalSpeed >= fastRunThreshold ? Locomotion.FAST_RUN : Locomotion.RUN;
    }

    public static boolean isAirborne(Locomotion state) {
        return state == Locomotion.JUMP_TAKEOFF || state == Locomotion.JUMP_RISE
                || state == Locomotion.JUMP_APEX || state == Locomotion.JUMP_FALL;
    }

    public static Combat combat(boolean compatible, boolean swinging, boolean swordHeld) {
        if (!compatible || !swinging) return Combat.NONE;
        return swordHeld ? Combat.SWORD_SWING : Combat.PUNCH;
    }
}
