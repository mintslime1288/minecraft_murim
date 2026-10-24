package com.murimrpg.client.render.wuxia;

/**
 * Column-major 4x4 matrix and quaternion helpers for the Wuxia rig.
 *
 * <p>Deliberately dependency-free. The rig, the clip selector and the hand anchor are all pure
 * classes so they can be unit tested without a Minecraft or JOML classpath; only
 * {@code WuxiaPlayerRenderer} converts the results into JOML and Mojang types.
 *
 * <p>Layout is glTF's and JOML's: {@code m[column * 4 + row]}, so a translation lives in
 * elements 12, 13, 14.
 */
public final class WuxiaMath {

    private WuxiaMath() {
    }

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    /** {@code out = a * b}, applying b first. {@code out} may alias neither a nor b. */
    public static float[] multiply(float[] a, float[] b, float[] out) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return out;
    }

    public static float[] multiply(float[] a, float[] b) {
        return multiply(a, b, new float[16]);
    }

    /** Builds {@code T * R * S} from a glTF node's translation, xyzw rotation and scale. */
    public static float[] fromTrs(float[] translation, float[] rotation, float[] scale) {
        float x = rotation[0], y = rotation[1], z = rotation[2], w = rotation[3];
        float n = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (n > 1.0e-8f) { x /= n; y /= n; z /= n; w /= n; } else { x = y = z = 0f; w = 1f; }
        float[] m = new float[16];
        float r00 = 1 - 2 * (y * y + z * z), r01 = 2 * (x * y - z * w), r02 = 2 * (x * z + y * w);
        float r10 = 2 * (x * y + z * w), r11 = 1 - 2 * (x * x + z * z), r12 = 2 * (y * z - x * w);
        float r20 = 2 * (x * z - y * w), r21 = 2 * (y * z + x * w), r22 = 1 - 2 * (x * x + y * y);
        m[0] = r00 * scale[0]; m[1] = r10 * scale[0]; m[2] = r20 * scale[0]; m[3] = 0f;
        m[4] = r01 * scale[1]; m[5] = r11 * scale[1]; m[6] = r21 * scale[1]; m[7] = 0f;
        m[8] = r02 * scale[2]; m[9] = r12 * scale[2]; m[10] = r22 * scale[2]; m[11] = 0f;
        m[12] = translation[0]; m[13] = translation[1]; m[14] = translation[2]; m[15] = 1f;
        return m;
    }

    public static void transformPoint(float[] m, float px, float py, float pz, float[] out) {
        out[0] = m[0] * px + m[4] * py + m[8] * pz + m[12];
        out[1] = m[1] * px + m[5] * py + m[9] * pz + m[13];
        out[2] = m[2] * px + m[6] * py + m[10] * pz + m[14];
    }

    public static void transformDirection(float[] m, float dx, float dy, float dz, float[] out) {
        out[0] = m[0] * dx + m[4] * dy + m[8] * dz;
        out[1] = m[1] * dx + m[5] * dy + m[9] * dz;
        out[2] = m[2] * dx + m[6] * dy + m[10] * dz;
    }

    /** Shortest-arc quaternion interpolation, matching glTF's required rotation blending. */
    public static float[] slerp(float[] a, float[] b, float t, float[] out) {
        float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float sign = dot < 0f ? -1f : 1f;
        dot *= sign;
        if (dot > 0.9995f) {
            for (int i = 0; i < 4; i++) out[i] = a[i] + t * (sign * b[i] - a[i]);
        } else {
            float theta = (float) Math.acos(Math.min(1f, dot));
            float sin = (float) Math.sin(theta);
            float wa = (float) Math.sin((1f - t) * theta) / sin;
            float wb = (float) Math.sin(t * theta) / sin;
            for (int i = 0; i < 4; i++) out[i] = wa * a[i] + wb * sign * b[i];
        }
        float n = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2] + out[3] * out[3]);
        if (n > 1.0e-8f) for (int i = 0; i < 4; i++) out[i] /= n;
        else { out[0] = out[1] = out[2] = 0f; out[3] = 1f; }
        return out;
    }

    public static float[] lerp3(float[] a, float[] b, float t, float[] out) {
        for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * t;
        return out;
    }

    /** Wraps a phase into [0, 1). Negative input wraps forward, so reversing never jumps. */
    public static float wrapPhase(float phase) {
        float wrapped = phase % 1f;
        return wrapped < 0f ? wrapped + 1f : wrapped;
    }

    /** Smoothstep, used for every cross-fade so no transition starts or ends with a velocity step. */
    public static float smooth(float t) {
        float c = t < 0f ? 0f : t > 1f ? 1f : t;
        return c * c * (3f - 2f * c);
    }
}
