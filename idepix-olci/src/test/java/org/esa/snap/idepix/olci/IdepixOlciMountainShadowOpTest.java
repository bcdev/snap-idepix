package org.esa.snap.idepix.olci;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tonio Fincke, Olaf Danne
 */
public class IdepixOlciMountainShadowOpTest {

    @Test
    public void testIsMountainShadow() {
        //todo 20210910 DM: aspect is a function of SAA (sun from northern direction on Southern hemisphere!).
        // Test for southern hemisphere.

        // simple illustrative test case: slope of 45 deg to the south, swath direction south-north (orientation = 0),
        // local noon (saa = 180):
        final float slope = (float) (Math.PI / 4.0);
        final float aspect = (float) (Math.PI * 2.0);
        final float orientation = 0.0f;
        float saa = 180.0f;  // local noon

        // sun in zenith: certainly no shadow...
        float sza = 0.0f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));

        // low sun angle: no shadow...
        sza = 30.0f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));

        // the change at sza = 45deg...
        sza = 44.9f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));
        sza = 45.0f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));
        // get out of the shadow again with slight change in saa...
        saa = 179.0f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));
        saa = 181.0f;
        assertFalse(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));

        // low sun angle: shadow...
        saa = 180.0f;
        sza = 60.0f;
        assertTrue(IdepixOlciMountainShadowOp.isMountainShadow(sza, saa, slope, aspect, orientation, 0.9));

    }

    @Test
    public void testComputeCosBeta() {
        // Test taken from S2-MSI. Should still run green for OLCI...
        float[] slope = new float[]{
                0.21798114f, 0.32035214f, 0.11440312f, 0.22131443f,
                0.10711748f, 0.22345093f, 0.14396477f, 0.124354996f,
                0.070593186f, 0.070593186f, 0.11134102f, 0.11134102f,
                0.11134102f, 0.049958397f, 0.0f, 0.14048971f};
        float[] aspect = {
                -1.28474486f, -1.62733972f, 1.96140337f, 1.57079637f,
                -0.95054686f, -2.12064958f, 3.01189017f, 1.57079637f,
                0.78539819f, -2.3561945f, -2.67794514f, 2.67794514f,
                1.10714877f, -0.f, -3.14159274f, 2.3561945f};
        float[] orientation = {
                0.021341953f, 0.021341953f, 0.02063076f, 0.021341952f,
                0.02134193f, 0.02134193f, 0.02134193f, 0.02134193f,
                0.021341905f, 0.021341905f, 0.042664386f, 0.021341903f,
                0.02134188f, 0.021341879f, 0.02063069f, 0.021341879f};
        float[] sza = {
                85.001f, 85.002f, 85.003f, 85.004f,
                85.011f, 85.012f, 85.013f, 85.014f,
                85.021f, 85.022f, 85.023f, 85.024f,
                85.031f, 85.032f, 85.033f, 85.034f};
        float[] saa = {
                150.001f, 150.002f, 150.003f, 150.004f,
                150.011f, 150.012f, 150.013f, 150.014f,
                150.021f, 150.022f, 150.023f, 150.024f,
                150.031f, 150.032f, 150.033f, 150.034f
        };
        double[] expectedCosBeta = {
                -0.07404259, -0.0644948, 0.1780186, 0.19830476,
                -0.01139193, 0.08591616, 0.21682262, 0.15026763,
                0.06981512, 0.10331751, 0.14325161, 0.19653183,
                0.09500943, 0.04393285, 0.08658208, 0.22118078};
        double[] cosBeta = new double[16];
        for (int i = 0; i < 16; i++) {
            cosBeta[i] = IdepixOlciMountainShadowOp.computeCosBeta(sza[i], saa[i], slope[i], aspect[i], orientation[i]);
        }
        assertArrayEquals(expectedCosBeta, cosBeta, 1e-7);
    }

}