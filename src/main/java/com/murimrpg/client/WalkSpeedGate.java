package com.murimrpg.client;

/** Hysteresis gate that prevents low-speed walk/idle flicker. */
final class WalkSpeedGate {
    private boolean walking;

    boolean update(float speed, float enterSpeed, float exitSpeed) {
        if (!Float.isFinite(speed) || enterSpeed < exitSpeed || exitSpeed < 0f) {
            throw new IllegalArgumentException("Walk speed gate requires finite speed and enter >= exit >= 0");
        }
        if (walking) {
            if (speed < exitSpeed) walking = false;
        } else if (speed > enterSpeed) {
            walking = true;
        }
        return walking;
    }

    void reset() { walking = false; }
}
