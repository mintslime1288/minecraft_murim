package com.murimrpg.client;

/** Keeps one punch hand stable for an entire swing and owns the short post-hit guard window. */
final class PalBoxingTracker {
    enum Pose { NONE, GUARD, PUNCH_LEFT, PUNCH_RIGHT }

    private boolean previousSwinging;
    private float previousPhase;
    private boolean rightHand = true;
    private int guardUntilTick = Integer.MIN_VALUE;

    Pose select(boolean compatible, boolean emptyHands, boolean swinging, float attackPhase,
                int tick, int guardHoldTicks, boolean randomHands, boolean randomRight) {
        if (!compatible || !emptyHands || !Float.isFinite(attackPhase)) {
            reset(swinging, attackPhase);
            guardUntilTick = Integer.MIN_VALUE;
            return Pose.NONE;
        }

        boolean newSwing = swinging && (!previousSwinging || attackPhase + 0.05F < previousPhase);
        if (newSwing) {
            rightHand = !randomHands || randomRight;
            guardUntilTick = tick + guardHoldTicks;
        }
        previousSwinging = swinging;
        previousPhase = attackPhase;

        if (swinging) return rightHand ? Pose.PUNCH_RIGHT : Pose.PUNCH_LEFT;
        return tick <= guardUntilTick ? Pose.GUARD : Pose.NONE;
    }

    private void reset(boolean swinging, float attackPhase) {
        previousSwinging = swinging;
        previousPhase = Float.isFinite(attackPhase) ? attackPhase : 0.0F;
    }
}
