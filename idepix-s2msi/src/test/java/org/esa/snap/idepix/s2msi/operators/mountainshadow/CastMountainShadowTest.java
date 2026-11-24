package org.esa.snap.idepix.s2msi.operators.mountainshadow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class CastMountainShadowTest {

    private static float[] compute(float[] z, int w, int h, double azimuth, int block) {
        return CastMountainShadow.compute(z, w, h, 10, 10, azimuth, 45, Float.NaN, 1e-6, block);
    }

    @Test
    public void highestRidgeCastsOntoFlatValley() {
        assertArrayEquals(new float[]{0, 20, 10, 0, 0},
                compute(new float[]{30, 0, 0, 0, 0}, 5, 1, 270, 2), 1e-5f);
    }

    @Test
    public void cardinalDirectionsHaveCorrectOrientation() {
        float[] horizontal = {0, 0, 0, 0, 30};
        assertArrayEquals(new float[]{0, 0, 10, 20, 0}, compute(horizontal, 5, 1, 90, 1), 1e-5f);
        assertArrayEquals(new float[]{0, 20, 10, 0, 0},
                compute(new float[]{30, 0, 0, 0, 0}, 1, 5, 0, 1), 1e-5f);
        assertArrayEquals(new float[]{0, 0, 10, 20, 0}, compute(horizontal, 1, 5, 180, 3), 1e-5f);
    }

    @Test
    public void flatAndNegativeElevationTerrainIsNotShadowed() {
        float[] z = new float[77];
        Arrays.fill(z, -100);
        for (int azimuth = 0; azimuth < 360; azimuth += 7) {
            assertArrayEquals(new float[77], compute(z, 11, 7, azimuth, 3), 0);
        }
    }

    @Test
    public void diagonalUsesPhysicalDistance() {
        float[] z = new float[9];
        z[0] = 40;
        float[] depth = compute(z, 3, 3, 315, 1);
        assertEquals(40 - Math.sqrt(200), depth[4], 1e-4);
        assertEquals(40 - Math.sqrt(800), depth[8], 1e-4);
        assertEquals(0, depth[1], 0);
    }

    @Test
    public void blocksDoNotChangeCoverageOrShadowEnvelope() {
        float[] z = new float[143];
        Random random = new Random(17);
        for (int i = 0; i < z.length; i++) {
            z[i] = random.nextFloat() * 400 - 200;
        }
        for (int azimuth = 0; azimuth < 360; azimuth += 11) {
            float[] reference = compute(z, 13, 11, azimuth, 100);
            for (int block : new int[]{1, 2, 3, 7, 11, 13}) {
                assertArrayEquals(reference, compute(z, 13, 11, azimuth, block), 0);
            }
            for (float value : reference) {
                assertTrue(Float.isFinite(value));
            }
        }
    }

    @Test
    public void missingTerrainIsNotAnArtificialObstacleOrProofOfIllumination() {
        float[] z = {30, Float.NaN, 0, 0, 0};
        float[] depth = compute(z, 5, 1, 270, 1);
        assertEquals(0, depth[0], 0);
        assertTrue(Float.isNaN(depth[1]));
        assertEquals(10, depth[2], 1e-5);
        assertTrue(Float.isNaN(depth[3]));
        assertTrue(Float.isNaN(depth[4]));
        assertArrayEquals(depth, CastMountainShadow.compute(new float[]{30, -9999, 0, 0, 0},
                5, 1, 10, 10, 270, 45, -9999, 1e-6, 2), 0);
    }

    @Test
    public void rectangularCellsUseTheirOwnSpacing() {
        assertArrayEquals(new float[]{0, 10, 0}, CastMountainShadow.compute(new float[]{30, 0, 0},
                3, 1, 20, 10, 270, 45, Float.NaN, 1e-6, 2), 1e-5f);
    }

    @Test
    public void zenithSunAndToleranceAreExplicit() {
        assertArrayEquals(new float[]{0, 0, Float.NaN}, CastMountainShadow.compute(new float[]{100, 0, Float.NaN},
                3, 1, 10, 10, 0, 90, Float.NaN, 0, 1), 0);
        assertArrayEquals(new float[]{0, 0}, CastMountainShadow.compute(new float[]{10.01f, 0},
                2, 1, 10, 10, 270, 45, Float.NaN, 0.1, 1), 0);
    }

    @Test
    public void cropsExpandedResultBackToInputGrid() {
        float[] expanded = {
                0, 1, 2, 3, 4,
                5, 6, 7, 8, 9,
                10, 11, 12, 13, 14,
                15, 16, 17, 18, 19
        };
        assertArrayEquals(new float[]{6, 7, 8, 11, 12, 13},
                S2IdepixCastMountainShadowOp.crop(expanded, 5, 3, 2, 1), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedDimensions() {
        compute(new float[2], 2, 2, 0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSunBelowHorizon() {
        CastMountainShadow.compute(new float[1], 1, 1, 10, 10, 0, 0, Float.NaN, 0, 1);
    }
}
