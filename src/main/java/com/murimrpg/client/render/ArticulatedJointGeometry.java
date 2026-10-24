package com.murimrpg.client.render;

/** Shared pixel dimensions for the elbow and knee seam connectors and their JVM regression test. */
final class ArticulatedJointGeometry {
    private ArticulatedJointGeometry() { }

    static final float LIMB_WIDTH = 4f;
    static final float LIMB_DEPTH = 4f;
    static final float LIMB_HALF_DEPTH = LIMB_DEPTH / 2f;
    static final float UPPER_ARM_LENGTH = 7f;
    static final float LOWER_ARM_LENGTH = 5f;
    static final float UPPER_LEG_LENGTH = 6f;
    static final float LOWER_LEG_LENGTH = 6f;
    static final float VANILLA_LIMB_LENGTH = 12f;
    static final float ELBOW_PIVOT_Y = 5f;
    static final float KNEE_PIVOT_Y = 6f;
    static final float RIGHT_ARM_ORIGIN_X = -3f;
    static final float LEFT_ARM_ORIGIN_X = -1f;
    static final float LEG_ORIGIN_X = -2f;
    static final float SEAM_INSET = 0.1f;
    /**
     * Half the connector's length along the limb.
     *
     * <p>Sized from the exact corner opening, not by eye. With the pivot at the origin, y along
     * the limb and z across its depth, the upper cuboid's bottom face is y = 0 and bending the
     * lower one by t carries its top face to (-z sin t, z cos t); the two meet on the pivot line
     * and splay apart, so the widest gap is between the far corners at z = +-LIMB_HALF_DEPTH and
     * measures 4 * LIMB_HALF_DEPTH * sin(t/2). At the configured knee ceiling of 52 degrees that
     * is 3.507 px. The previous 1.6 gave a 3.2 px connector, which closes the seam only to 47.2
     * degrees - the last five degrees of the knee's own configured range opened a hole up to
     * 0.31 px wide at the corner. 2.3 gives 4.6 px and closes it to 70.2 degrees.
     */
    static final float CONNECTOR_HALF_HEIGHT = 2.3f;
    static final float MAX_KNEE_DEGREES = 52f;
    static final float MAX_ELBOW_DEGREES = 30f;
    static final float CONNECTOR_WIDTH = LIMB_WIDTH - 2f * SEAM_INSET;
    static final float CONNECTOR_HEIGHT = 2f * CONNECTOR_HALF_HEIGHT;
    static final float CONNECTOR_DEPTH = LIMB_DEPTH - 2f * SEAM_INSET;
    static final float CONNECTOR_HALF_DEPTH = CONNECTOR_DEPTH / 2f;
    static final int RIGHT_ELBOW_TEX_U = 40, RIGHT_ELBOW_TEX_V = 22;
    static final int LEFT_ELBOW_TEX_U = 32, LEFT_ELBOW_TEX_V = 54;
    static final int RIGHT_KNEE_TEX_U = 0, RIGHT_KNEE_TEX_V = 21;
    static final int LEFT_KNEE_TEX_U = 16, LEFT_KNEE_TEX_V = 53;
}
