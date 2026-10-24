package com.murimrpg.client.render;

/**
 * The head-local rotation that makes a head hanging off the torso still look exactly where the
 * mouse points.
 *
 * <p>Two things have to be true at once, and they pull in opposite directions. The head's pivot
 * has to ride the neck, because with locomotion.torso-hip-pivot on the neck travels 1.970 px
 * forward at the run lean while a head parented to root stays at the model origin - that gap is
 * the head appearing to hang in space behind a leaning body. And the head's world orientation has
 * to stay exactly the mouse yaw and pitch, because a runner keeps their eyes on the horizon and
 * because first-person aim and third-person look must agree. Parenting fixes the first and breaks
 * the second, so the second has to be put back by rotating the head the other way inside its new
 * parent. That is all this class computes.
 *
 * <h2>Where the rotation convention comes from</h2>
 * Not from a guess, and not from an older Minecraft version. It was read out of the Minecraft 26.2
 * classes in this project's own Loom cache
 * ({@code .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-&lt;hash&gt;/26.2}), three
 * independent places that agree:
 *
 * <ul>
 *   <li>{@code ModelPart.translateAndRotate} translates by x/16, y/16, z/16 and, when any of the
 *       three rotations is non-zero, multiplies the pose by
 *       {@code new Quaternionf().rotationZYX(zRot, yRot, xRot)}.</li>
 *   <li>{@code ModelPart.render} pushes the pose, calls its own {@code translateAndRotate},
 *       compiles its own cubes, then recurses into its children before popping. A child's
 *       rotation is therefore composed on the right of its parent's.</li>
 *   <li>{@code ModelPart.rotateBy} builds {@code Matrix3f.rotationZYX(zRot, yRot, xRot)},
 *       right-multiplies the incoming quaternion into it and reads the result back out with
 *       {@code getEulerAnglesZYX}. Mojang's own answer to "compose a rotation onto a part and turn
 *       it back into xRot/yRot/zRot" is an inverse pair in this same order.</li>
 * </ul>
 *
 * So a part's rotation matrix is {@code R = Rz(zRot) * Ry(yRot) * Rx(xRot)} acting on column
 * vectors, and a child's world rotation is {@code R_parent * R_child}. Independently of the
 * bytecode, the code that ships today corroborates it: the head is a root child with
 * {@code yRot = yaw} and {@code xRot = pitch}, which under this convention is
 * {@code Ry(yaw) * Rx(pitch)} - yaw about the model's own vertical axis and then pitch in the
 * yawed frame, which is the only composition under which a Minecraft head still turns sideways
 * while looking straight down. That behaviour is F5-confirmed every time anyone moves the mouse.
 *
 * <h2>What is solved</h2>
 * Required: {@code R_body * R_head = R_look}, so {@code R_head = R_body^-1 * R_look}. A rotation
 * matrix's inverse is its transpose, and {@code R_look} is the rotation the head already has
 * today, {@code Rz(0) * Ry(yaw) * Rx(pitch)}. The product is then read back into xRot/yRot/zRot
 * with the same extraction {@code getEulerAnglesZYX} performs, which makes compose and extract an
 * exact inverse pair.
 *
 * <p>Componentwise subtraction - pitch minus body.xRot, yaw minus body.yRot, minus body.zRot - was
 * measured and rejected, not assumed to be wrong. Euler angles do not subtract, and the residual
 * grows with head yaw because the body's lean is being removed in the wrong frame: 2.512 degrees
 * of world look error inside 15 degrees of head yaw at the run lean, 7.990 by 50 degrees, 18.894
 * at 180. The exact form above measures 0.0396 degrees worst case in float arithmetic over head
 * yaw -180..180, pitch -90..90 and every body rotation the clips and ceilings allow, which is
 * float rounding of sin/cos/atan2 and nothing else.
 *
 * <h2>Why the returned angles are not range-checked</h2>
 * Euler triples are not unique, and near a head yaw of 90 degrees the ZYX chart is ill-conditioned:
 * the same world rotation can come back as a large xRot and zRot instead of a small pair. The
 * rendered result is the composed matrix, which stays exact there - measured, see the tests - so
 * clamping these values would corrupt a correct pose rather than protect anything. Only finiteness
 * is checked, and a non-finite input leaves the caller's already-assigned raw look rotation alone.
 *
 * <p>Nothing here is derived from any third-party mod, library, rig or animation. No new Gradle
 * dependency is introduced: the 3x3 arithmetic is written out in full so it is testable on a plain
 * JVM with no Minecraft classes on the classpath.
 */
final class HeadLookCompensation {
    private HeadLookCompensation() { }

    /**
     * Where the head pivot sits in a hip-pivoted torso's own space. The torso pivots at
     * {@link ArticulatedTorsoHierarchy#HIP_Y} and its box runs from -TORSO_HEIGHT to 0, so the
     * neck is one torso height above the pivot. Root-space check: 12 + (-12) = 0, exactly the
     * {@link ArticulatedTorsoHierarchy#NECK_Y} the head sits at today.
     */
    static final float NECK_OFFSET_Y = -ArticulatedTorsoHierarchy.TORSO_HEIGHT;

    /** Number of floats {@link #solve} writes: xRot, yRot, zRot, in that order. */
    static final int ANGLE_COUNT = 3;

    /**
     * @param out receives the head-local xRot, yRot, zRot; untouched unless true is returned
     * @return false when an input was not finite or the extraction produced a non-finite angle,
     *         in which case the caller must keep whatever rotation the head already had
     */
    static boolean solve(float bodyX, float bodyY, float bodyZ, float lookPitch, float lookYaw, float[] out) {
        if (out == null || out.length < ANGLE_COUNT) return false;
        if (!Float.isFinite(bodyX) || !Float.isFinite(bodyY) || !Float.isFinite(bodyZ)
                || !Float.isFinite(lookPitch) || !Float.isFinite(lookYaw)) {
            return false;
        }
        // A torso at rest is exactly zero on all three axes, because the authored clips are scaled
        // by the gait speed and the torso easing settles to exactly zero. Short-circuiting it is
        // not an optimisation: it makes a standing player's head bit-identical to the rotation that
        // ships today rather than a round trip through sin, cos and atan2.
        if (bodyX == 0f && bodyY == 0f && bodyZ == 0f) {
            out[0] = lookPitch;
            out[1] = lookYaw;
            out[2] = 0f;
            return true;
        }
        float[] body = rotationZYX(bodyZ, bodyY, bodyX);
        float[] look = rotationZYX(0f, lookYaw, lookPitch);
        float[] local = transposedTimes(body, look);
        float xRot = (float) Math.atan2(local[7], local[8]);
        float yRot = (float) Math.atan2(-local[6], Math.sqrt((double) local[7] * local[7] + (double) local[8] * local[8]));
        float zRot = (float) Math.atan2(local[3], local[0]);
        if (!Float.isFinite(xRot) || !Float.isFinite(yRot) || !Float.isFinite(zRot)) return false;
        out[0] = xRot;
        out[1] = yRot;
        out[2] = zRot;
        return true;
    }

    /**
     * {@code Rz(z) * Ry(y) * Rx(x)}, row-major, index {@code row * 3 + column}. This is the matrix
     * ModelPart builds for a part's own rotation; see the class note for where that was read.
     */
    static float[] rotationZYX(float z, float y, float x) {
        float cz = (float) Math.cos(z), sz = (float) Math.sin(z);
        float cy = (float) Math.cos(y), sy = (float) Math.sin(y);
        float cx = (float) Math.cos(x), sx = (float) Math.sin(x);
        return new float[] {
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy,     cy * sx,                cy * cx,
        };
    }

    /** {@code a^T * b} for two row-major 3x3 matrices. For a rotation, the transpose is the inverse. */
    static float[] transposedTimes(float[] a, float[] b) {
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                float sum = 0f;
                for (int k = 0; k < 3; k++) sum += a[k * 3 + row] * b[k * 3 + column];
                result[row * 3 + column] = sum;
            }
        }
        return result;
    }
}
