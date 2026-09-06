package com.murimrpg.client;

/** Entity-local, tick-based takeoff and landing edge tracker. */
final class PalLocomotionTracker {
    private boolean initialized;
    private boolean wasOnGround;
    private long takeoffUntilTick = Long.MIN_VALUE;
    private long landingUntilTick = Long.MIN_VALUE;

    PalAnimationStateSelector.Locomotion select(boolean compatible, boolean onGround, boolean sprinting,
                                                 double horizontalSpeed, double verticalSpeed, long gameTick,
                                                 PalAnimationSettings settings) {
        if (!compatible) {
            reset();
            return PalAnimationStateSelector.Locomotion.VANILLA;
        }
        if (!initialized) {
            initialized = true;
            wasOnGround = onGround;
        } else if (wasOnGround && !onGround) {
            takeoffUntilTick = gameTick + settings.takeoffDurationTicks() - 1L;
        } else if (!wasOnGround && onGround) {
            landingUntilTick = gameTick + settings.landingDurationTicks() - 1L;
        }

        wasOnGround = onGround;
        boolean takeoffActive = !onGround && gameTick <= takeoffUntilTick;
        boolean landingActive = onGround && gameTick <= landingUntilTick;
        return PalAnimationStateSelector.locomotion(
                true, onGround, sprinting, takeoffActive, landingActive,
                horizontalSpeed, verticalSpeed, settings.movingThreshold(), settings.fastRunThreshold(),
                settings.riseVelocityThreshold(), settings.apexVelocityThreshold());
    }

    void reset() {
        initialized = false;
        wasOnGround = false;
        takeoffUntilTick = Long.MIN_VALUE;
        landingUntilTick = Long.MIN_VALUE;
    }
}
