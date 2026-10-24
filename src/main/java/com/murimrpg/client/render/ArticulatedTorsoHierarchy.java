package com.murimrpg.client.render;

/**
 * Where the torso turns, and what it carries when it does.
 *
 * <p>createBodyLayer builds head, body, both upper arms and both upper legs as siblings under
 * root. Only the lower limbs and the elbow and knee connectors hang off anything. So the torso's
 * forward lean turns one lone box about {@link #NECK_Y}, the top of that box, and nothing else in
 * the character moves with it. Measured on the clips that ship today:
 *
 * <pre>
 *   lean          hip line: thigh front stands proud of the belly     shoulder surface moves
 *   walk 4.95     1.032 px                                            0.173 px
 *   run  9.45     1.969 px                                            0.328 px
 * </pre>
 *
 * <p>That is the doll look, and it is not a hole: the thighs simply stick out in front of a torso
 * whose bottom has swung backwards, and the lower back overhangs them behind. A connector cuboid
 * cannot fix a step, only a hole, so the fix is where the rotation happens.
 *
 * <p>A person leaning forward turns at the hip, not the neck. Turning the torso about
 * {@link #HIP_Y} keeps its bottom face on the thighs, so the hip step becomes exactly zero, and
 * moves the shoulders forward instead, which is what a forward lean looks like. The shoulders
 * moving is only an improvement if the arms move with them: with the arms still on root they
 * would be left 1.642 px behind at the run lean. So the two go together, as one hierarchy, and
 * the flag that turns it on turns on both.
 *
 * <p>What is left at the hip afterwards is the ordinary wedge any hinged pair of boxes opens,
 * {@code 2 * half_depth * sin(theta / 2)}: 0.329 px at the run lean, against the 1.969 px step it
 * replaces. Whether that needs a connector of its own is an F5 question, not one to answer here.
 *
 * <p>The legs stay on root in both layouts, so the feet stay under the player. The head stays on
 * root here too, and locomotion.torso-head-follow is the separate, separately defaulted-off flag
 * that hangs it off the neck instead; see HeadLookCompensation for why that needs this layout and
 * how the look rotation survives it. Nothing in this class is derived from any third-party model,
 * rig or animation.
 */
final class ArticulatedTorsoHierarchy {
    private ArticulatedTorsoHierarchy() { }

    /** Torso box, unchanged from vanilla and from the current build: 8 wide, 12 tall, 4 deep. */
    static final float TORSO_WIDTH = 8f;
    static final float TORSO_HEIGHT = 12f;
    static final float TORSO_DEPTH = 4f;
    static final float TORSO_HALF_DEPTH = TORSO_DEPTH / 2f;

    /** Root-space heights. The model's origin is the neck, and y grows downward. */
    static final float NECK_Y = 0f;
    static final float SHOULDER_Y = 2f;
    static final float HIP_Y = 12f;

    /** Root-space pivot offsets createBodyLayer already uses. */
    static final float ARM_OFFSET_X = 5f;
    static final float LEG_OFFSET_X = 1.9f;

    /** Thigh cross-section, for measuring the hip seam against the torso's. */
    static final float THIGH_HALF_DEPTH = 2f;

    /** Arm pivot expressed in a hip-pivoted torso's local space: 2 - 12. */
    static final float ARM_OFFSET_Y_FROM_HIP = SHOULDER_Y - HIP_Y;

    /** Torso box origin on Y for each layout, so both occupy root y 0..12 while standing. */
    static final float NECK_PIVOTED_BOX_ORIGIN_Y = 0f;
    static final float HIP_PIVOTED_BOX_ORIGIN_Y = -TORSO_HEIGHT;

    /** How far the torso surface at {@code worldY} travels forward at this lean, per layout. */
    static float forwardTravel(float worldY, float pivotY, double leanRadians) {
        return (float) (Math.abs(worldY - pivotY) * Math.sin(leanRadians));
    }

    /** The wedge a hinged pair of 4-deep boxes opens at their shared face. */
    static float hingeWedge(double leanRadians) {
        return (float) (2f * TORSO_HALF_DEPTH * Math.abs(Math.sin(leanRadians / 2)));
    }
}
