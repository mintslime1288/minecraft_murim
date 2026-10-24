package com.murimrpg.client.render;

import com.murimrpg.infrastructure.config.YamlDocument;

/** Original rotation-only refinements of the existing, project-owned motion clips. */
final class LocomotionStyle {
    private LocomotionStyle() { }

    record Settings(boolean runEnabled, float legGain, float kneeGain, float armGain,
                    float elbowBias, float leanBias, boolean torsoEnabled, float rollScale,
                    float yawGain, float yawLimit, boolean idleEnabled, float idleLean,
                    float idleElbow, float breathDegrees, float breathSeconds, float idleFadeSpeed) {
        static Settings load(YamlDocument yaml) {
            return new Settings(flag(yaml, "run.enabled"),
                    value(yaml, "run.leg-gain", 1.12f, 1f, 1.5f),
                    value(yaml, "run.knee-gain", 1.5f, 1f, 2f),
                    value(yaml, "run.arm-gain", 1.15f, 1f, 1.5f),
                    value(yaml, "run.elbow-bias-degrees", 40f, 0f, 45f),
                    value(yaml, "run.lean-bias-degrees", 4f, 0f, 12f),
                    flag(yaml, "torso.enabled"),
                    value(yaml, "torso.roll-scale", .15f, 0f, 1f),
                    value(yaml, "torso.arm-yaw-gain", .10f, 0f, .2f),
                    value(yaml, "torso.yaw-limit-degrees", 5f, 0f, 8f),
                    flag(yaml, "idle.enabled"),
                    value(yaml, "idle.lean-degrees", 2f, 0f, 4f),
                    value(yaml, "idle.elbow-degrees", 12f, 0f, 20f),
                    value(yaml, "idle.breath-degrees", .45f, 0f, 1f),
                    value(yaml, "idle.breath-seconds", 4f, 2f, 8f),
                    value(yaml, "idle.fade-speed", .2f, .05f, .5f));
        }
        private static boolean flag(YamlDocument yaml, String key) {
            return Boolean.TRUE.equals(yaml.get("motion-style." + key));
        }
        private static float value(YamlDocument yaml, String key, float fallback, float min, float max) {
            Object raw = yaml.get("motion-style." + key);
            float result = raw instanceof Number n ? n.floatValue() : fallback;
            if (!Float.isFinite(result) || result < min || result > max)
                throw new IllegalArgumentException("motion-style." + key + " must be within " + min + ".." + max);
            return result;
        }
    }

    /** Adjust an already speed-scaled rotation, preserving its phase and blend to the old pose. */
    static float running(float original, float gain, float biasDegrees, float speed, float forwardWeight) {
        float w = unit(forwardWeight);
        if (w == 0f) return original;
        return original + ((gain - 1f) * original + radians(biasDegrees) * unit(speed)) * w;
    }

    /**
     * Forward is model -Z. Right shoulder is x=-5, so z'=-x*sin(yaw) must be negative
     * when the right arm has the more negative (forward) pitch. Therefore right-left,
     * not left-right, is the signed yaw. No side-to-side translation is introduced.
     */
    static float shoulderYaw(float rightPitch, float leftPitch, float gain, float limitDegrees) {
        if (!Float.isFinite(rightPitch) || !Float.isFinite(leftPitch)
                || !Float.isFinite(gain) || !Float.isFinite(limitDegrees)) return 0f;
        float limit = radians(Math.clamp(limitDegrees, 0f, 8f));
        if (limit == 0f) return 0f;
        // Soft saturation avoids a flat shoulder pose around the ends of the arm swing.
        return limit * (float) Math.tanh((rightPitch - leftPitch) * Math.clamp(gain, 0f, .2f) / limit);
    }

    static float idleWeight(float rawSpeed, float fadeSpeed) {
        if (!Float.isFinite(rawSpeed) || !Float.isFinite(fadeSpeed) || fadeSpeed <= 0f) return 0f;
        float t = unit(rawSpeed / fadeSpeed);
        return 1f - t * t * (3f - 2f * t);
    }

    static float breath(float ageTicks, float periodSeconds, float degrees) {
        if (!Float.isFinite(ageTicks) || !Float.isFinite(periodSeconds) || periodSeconds <= 0f
                || !Float.isFinite(degrees)) return 0f;
        double cycle = ageTicks / (20d * periodSeconds);
        return (float) Math.sin((cycle - Math.floor(cycle)) * Math.PI * 2d) * radians(degrees);
    }

    private static float unit(float value) { return Float.isFinite(value) ? Math.clamp(value, 0f, 1f) : 0f; }
    static float radians(float degrees) { return (float) Math.toRadians(degrees); }
}
