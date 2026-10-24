package com.murimrpg.client.render;

/** Splits a horizontal velocity into the player's own forward and right axes. */
record LocomotionDirection(float forward, float strafe, float gaitWeight, boolean reversed) {
    private static final float MINIMUM_SPEED = 1.0e-4f;

    static LocomotionDirection of(double velocityX, double velocityZ, float bodyYawDegrees,
                                  float strafeGaitWeight, float reverseDeadband, boolean previouslyReversed) {
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityZ) || !Float.isFinite(bodyYawDegrees)) {
            return new LocomotionDirection(0f, 0f, 0f, previouslyReversed);
        }
        double yaw = Math.toRadians(bodyYawDegrees);
        // Minecraft yaw 0 faces +Z and the view vector is (-sin(yaw), cos(yaw)).
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -forwardZ;
        double rightZ = forwardX;
        float forward = (float) (velocityX * forwardX + velocityZ * forwardZ);
        float strafe = (float) (velocityX * rightX + velocityZ * rightZ);
        float total = (float) Math.sqrt((double) forward * forward + (double) strafe * strafe);
        boolean reversed = Math.abs(forward) <= Math.max(reverseDeadband, 0f)
                ? previouslyReversed : forward < 0f;
        float sideways = Math.clamp(strafeGaitWeight, 0f, 1f);
        float weight = total < MINIMUM_SPEED ? 0f
                : Math.clamp((Math.abs(forward) + sideways * Math.abs(strafe)) / total, 0f, 1f);
        return new LocomotionDirection(forward, strafe, weight, reversed);
    }
}
