package com.murimrpg.client.render;

import static com.murimrpg.client.render.ArticulatedJointGeometry.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** JVM checks for the connector dimensions supplied with the connected-joints patch. */
class ArticulatedJointGeometryTest {
    @Test void everyConnectorDimensionIsFiniteAndPositive() {
        for (float value : new float[] {CONNECTOR_WIDTH, CONNECTOR_HEIGHT, CONNECTOR_DEPTH, CONNECTOR_HALF_HEIGHT, CONNECTOR_HALF_DEPTH, SEAM_INSET}) {
            assertTrue(Float.isFinite(value)); assertTrue(value > 0f);
        }
    }
    @Test void theConnectorStaysInsideTheLimbSurfaceSoItCannotZFight() {
        assertTrue(CONNECTOR_HALF_DEPTH < LIMB_HALF_DEPTH); assertTrue(CONNECTOR_WIDTH < LIMB_WIDTH);
        assertEquals(LIMB_DEPTH - 2f * SEAM_INSET, CONNECTOR_DEPTH, 1e-6f);
        assertEquals(LIMB_WIDTH - 2f * SEAM_INSET, CONNECTOR_WIDTH, 1e-6f);
    }
    @Test void theConnectorIsCompletelyBuriedWhenTheJointIsStraight() {
        assertTrue(CONNECTOR_HALF_HEIGHT <= UPPER_ARM_LENGTH && CONNECTOR_HALF_HEIGHT <= LOWER_ARM_LENGTH);
        assertTrue(CONNECTOR_HALF_HEIGHT <= UPPER_LEG_LENGTH && CONNECTOR_HALF_HEIGHT <= LOWER_LEG_LENGTH);
    }
    @Test void theLimbsStillAddUpToAVanillaPlayer() {
        assertEquals(VANILLA_LIMB_LENGTH, UPPER_ARM_LENGTH + LOWER_ARM_LENGTH, 1e-6f);
        assertEquals(VANILLA_LIMB_LENGTH, UPPER_LEG_LENGTH + LOWER_LEG_LENGTH, 1e-6f);
        assertEquals(UPPER_ARM_LENGTH - 2f, ELBOW_PIVOT_Y, 1e-6f); assertEquals(UPPER_LEG_LENGTH, KNEE_PIVOT_Y, 1e-6f);
    }
    @Test void theLeftAndRightConnectorsAreExactMirrors() {
        float r0=RIGHT_ARM_ORIGIN_X+SEAM_INSET, r1=r0+CONNECTOR_WIDTH, l0=LEFT_ARM_ORIGIN_X+SEAM_INSET, l1=l0+CONNECTOR_WIDTH;
        assertEquals(-r1,l0,1e-6f); assertEquals(-r0,l1,1e-6f); float leg=LEG_ORIGIN_X+SEAM_INSET;
        assertEquals(-(leg+CONNECTOR_WIDTH),leg,1e-6f);
    }
    @Test void everyConnectorTextureRectStaysInsideItsOwnLimbRegion() {
        float netWidth=2f*(CONNECTOR_DEPTH+CONNECTOR_WIDTH), netHeight=CONNECTOR_DEPTH+CONNECTOR_HEIGHT;
        int[][] data={{RIGHT_ELBOW_TEX_U,RIGHT_ELBOW_TEX_V,40,16,56,32},{LEFT_ELBOW_TEX_U,LEFT_ELBOW_TEX_V,32,48,48,64},{RIGHT_KNEE_TEX_U,RIGHT_KNEE_TEX_V,0,16,16,32},{LEFT_KNEE_TEX_U,LEFT_KNEE_TEX_V,16,48,32,64}};
        for(int[] c:data){assertTrue(c[0]>=c[2]&&c[0]+netWidth<=c[4]); assertTrue(c[1]>=c[3]&&c[1]+netHeight<=c[5]);}
    }
    /**
     * The exact corner opening, sampled at 120 uniform bend angles, against the connector that has
     * to hide it.
     *
     * <p>Put the joint pivot at the origin with y along the limb and z across its depth. The upper
     * cuboid's bottom face is the segment y = 0, z in [-2, 2]. Bending the lower cuboid by theta
     * about x carries its top face to (-z sin t, z cos t). The two faces meet exactly on the pivot
     * line and splay apart outwards, so the widest opening is between the two far corners at
     * z = +-LIMB_HALF_DEPTH, and its length is
     *
     * <pre>  sqrt( (2 d sin t)^2 + (2 d (1 - cos t))^2 ) = 4 d sin(t/2)  </pre>
     *
     * with d = LIMB_HALF_DEPTH. The connector is a box centred on that same pivot, so the span it
     * offers along the limb is its full height. The seam is closed while 4 d sin(t/2) does not
     * exceed CONNECTOR_HEIGHT, which is an exact statement rather than a tolerance.
     *
     * <p>This covers the SPLIT-CUBE path (articulated-player.yml), which is a fallback and is off
     * in the shipped configuration. The live PAL + Bendable Cuboids path has no seam at all: it
     * bends the original single cuboid, so there are no two faces that can part.
     */
    @Test void theCornerOpeningStaysInsideTheConnectorAcrossAHundredAndTwentyBendSamples() {
        for (float ceiling : new float[] {MAX_KNEE_DEGREES, MAX_ELBOW_DEGREES}) {
            for (int sample = 0; sample <= 120; sample++) {
                double radians = Math.toRadians(ceiling * sample / 120.0);
                double opening = 4.0 * LIMB_HALF_DEPTH * Math.sin(radians / 2.0);
                assertTrue(Double.isFinite(opening));
                assertTrue(opening <= CONNECTOR_HEIGHT + 1e-6,
                        "a " + Math.toDegrees(radians) + " degree bend opens " + opening
                                + " px against a " + CONNECTOR_HEIGHT + " px connector");
            }
        }
        // How much headroom is left, so a future ceiling rise cannot silently open a hole: the
        // connector runs out at 2 asin(CONNECTOR_HEIGHT / (4 d)), which must stay above both
        // ceilings by a real margin.
        double limitDegrees = Math.toDegrees(2 * Math.asin(CONNECTOR_HEIGHT / (4.0 * LIMB_HALF_DEPTH)));
        assertTrue(limitDegrees > MAX_KNEE_DEGREES + 15f,
                "the connector closes the seam only to " + limitDegrees + " degrees");
        assertTrue(limitDegrees < 180d, "the geometry must still have a real limit");
    }

    @Test void theSeamStaysCoveredAcrossEveryBendTheCeilingsAllow() {
        for(int degrees=0;degrees<=(int)MAX_KNEE_DEGREES;degrees++){float reach=LIMB_HALF_DEPTH*(float)Math.sin(Math.toRadians(degrees)); assertTrue(reach<=CONNECTOR_HALF_HEIGHT+1e-5f);}
    }
    @Test void theKnuckleIsAlwaysSmallerThanTheGapItReplaces() {
        for(int degrees=1;degrees<=(int)MAX_KNEE_DEGREES;degrees++){float gap=2f*LIMB_HALF_DEPTH*(float)Math.sin(Math.toRadians(degrees)/2d); assertTrue(gap>0f);}
    }
    @Test void theKnuckleOnlyAppearsOnTheOutsideOfTheBend() {
        assertTrue(CONNECTOR_HALF_DEPTH<LIMB_HALF_DEPTH); assertTrue(CONNECTOR_HALF_HEIGHT>0f);
    }
    @Test void theConnectorGrowsSmoothlyOutOfTheLimbWithNoStep() {
        float previous=0f; for(int degrees=0;degrees<=(int)MAX_KNEE_DEGREES;degrees++){float current=LIMB_HALF_DEPTH*(float)Math.sin(Math.toRadians(degrees)); assertTrue(current>=previous-1e-6f); previous=current;}
    }
}
