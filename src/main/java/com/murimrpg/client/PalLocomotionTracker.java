package com.murimrpg.client;

/** Entity-local, tick-based takeoff and landing edge tracker. */
final class PalLocomotionTracker {
    private boolean initialized;
    private boolean wasOnGround;
    private long takeoffUntilTick = Long.MIN_VALUE;
    private long landingUntilTick = Long.MIN_VALUE;
    private boolean moving;
    private boolean fastRunning;
    private final int minimumFallTicks;
    private long leftGroundTick;
    private long lastTick=Long.MIN_VALUE;
    private boolean jumped;

    PalLocomotionTracker() { this(0); }
    PalLocomotionTracker(int minimumFallTicks) { this.minimumFallTicks=Math.max(0,minimumFallTicks); }

    PalAnimationStateSelector.Locomotion select(boolean compatible, boolean onGround, boolean sprinting,
                                                 double horizontalSpeed, double verticalSpeed, long gameTick,
                                                 PalAnimationSettings settings) {
        if (!compatible || !Double.isFinite(horizontalSpeed) || !Double.isFinite(verticalSpeed)) {
            reset();
            return PalAnimationStateSelector.Locomotion.VANILLA;
        }
        if(lastTick!=Long.MIN_VALUE && (gameTick<lastTick||gameTick-lastTick>20)) reset();
        lastTick=gameTick;
        if (!initialized) {
            initialized = true;
            wasOnGround = onGround;
            leftGroundTick=gameTick;
        } else if (wasOnGround && !onGround) {
            leftGroundTick=gameTick;
            jumped=verticalSpeed>settings.riseVelocityThreshold();
            landingUntilTick=Long.MIN_VALUE;
            takeoffUntilTick = jumped||minimumFallTicks==0
                    ? gameTick + settings.takeoffDurationTicks() - 1L : Long.MIN_VALUE;
        } else if (!wasOnGround && onGround) {
            landingUntilTick = jumped||gameTick-leftGroundTick>=minimumFallTicks
                    ? gameTick + settings.landingDurationTicks() - 1L : Long.MIN_VALUE;
            jumped=false;
        }

        wasOnGround = onGround;
        boolean takeoffActive = !onGround && gameTick <= takeoffUntilTick;
        boolean landingActive = onGround && gameTick <= landingUntilTick;
        double movingThreshold = moving ? settings.movingExitThreshold() : settings.movingThreshold();
        double fastThreshold = fastRunning ? settings.fastRunExitThreshold() : settings.fastRunThreshold();
        boolean shortStep=!onGround&&!jumped&&gameTick-leftGroundTick<minimumFallTicks;
        var selected = PalAnimationStateSelector.locomotion(
                true, onGround||shortStep, sprinting, takeoffActive, landingActive,
                horizontalSpeed, verticalSpeed, movingThreshold, fastThreshold,
                settings.riseVelocityThreshold(), settings.apexVelocityThreshold());
        if (onGround && !landingActive) {
            moving = selected == PalAnimationStateSelector.Locomotion.WALK
                    || selected == PalAnimationStateSelector.Locomotion.RUN
                    || selected == PalAnimationStateSelector.Locomotion.FAST_RUN;
            fastRunning = selected == PalAnimationStateSelector.Locomotion.FAST_RUN;
        }
        return selected;
    }

    void reset() {
        initialized = false;
        wasOnGround = false;
        takeoffUntilTick = Long.MIN_VALUE;
        landingUntilTick = Long.MIN_VALUE;
        moving = false;
        fastRunning = false;
        jumped=false;
        lastTick=Long.MIN_VALUE;
    }
}
