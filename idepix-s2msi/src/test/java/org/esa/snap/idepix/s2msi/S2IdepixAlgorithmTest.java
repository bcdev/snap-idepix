package org.esa.snap.idepix.s2msi;


import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for class {@link S2IdepixUtils}.
 *
 * @author Olaf Danne
 */
public class S2IdepixAlgorithmTest {

//    public static final int IDEPIX_INVALID = 0;
//    public static final int IDEPIX_CLOUD = 1;
//    public static final int IDEPIX_CLOUD_AMBIGUOUS = 2;
//    public static final int IDEPIX_CLOUD_SURE = 3;
//    public static final int IDEPIX_CLOUD_BUFFER = 4;
//    public static final int IDEPIX_CLOUD_SHADOW = 5;
//    public static final int IDEPIX_SNOW_ICE = 6;
//    public static final int IDEPIX_BRIGHT = 7;
//    public static final int IDEPIX_WHITE = 8;
//    public static final int IDEPIX_COASTLINE = 9;
//    public static final int IDEPIX_LAND = 10;

    private static float eps = 1.E-4f;

    S2IdepixAlgorithm algo;

    float[] reflInvalid;
    float[] reflClear;
    float[] reflCloud;
    float[] reflCloudAmbiguous;
    float[] reflCloudSure;
    float[] reflSnowIce;
    float[] reflBright;
    float[] reflWhite;

    @Before
    public void setUp() throws Exception {
        algo = new S2IdepixAlgorithm();

        reflClear = new float[]{0.1413f, 0.1207f, 0.1191f, 0.1114f, 0.1559f, 0.2464f, 0.2963f, 0.2941f, 0.3436f, 0.0717f, 0.0062f, 0.2498f, 0.1356f};
        reflCloud = new float[]{0.8183f, 0.7886f, 0.753f, 0.7907f, 0.7965f, 0.8381f, 0.884f, 0.8293f, 0.8935f, 0.3962f, 0.0467f, 0.3719f, 0.265f};
        reflCloudAmbiguous = new float[]{0.164f, 0.1426f, 0.1383f, 0.1012f, 0.1483f, 0.3298f, 0.4005f, 0.3869f, 0.4387f, 0.095f, 0.0235f, 0.215f, 0.1025f};
        reflCloudSure = new float[]{0.8183f, 0.7886f, 0.753f,	0.7907f, 0.7965f, 0.8381f, 0.884f,	0.8293f, 0.8935f, 0.3962f, 0.0467f, 0.3719f, 0.265f};
        reflSnowIce = new float[]{}; // TODO find suitable product
        reflBright = new float[]{0.1853f, 0.1552f, 0.1491f, 0.1311f, 0.1786f, 0.3053f, 0.3741f, 0.3645f, 0.4304f, 0.0867f, 0.0054f, 0.2773f, 0.142f};
        reflWhite = new float[]{0.4539f, 0.4276f, 0.3962f, 0.4012f, 0.3964f, 0.4153f, 0.4372f, 0.4101f, 0.4431f, 0.1784f, 0.0207f, 0.2427f, 0.21f};
    }

    @Test
    public void testTc4CirrusValue() {
        algo.setRefl(reflClear);
        assertEquals(-0.0538f, algo.tc4CirrusValue(), eps);
        algo.setRefl(reflCloudSure);
        assertEquals(-0.3353f, algo.tc4CirrusValue(), eps);
        algo.setRefl(reflCloudAmbiguous);
        assertEquals(-0.0953f, algo.tc4CirrusValue(), eps);
        algo.setRefl(reflBright);
        assertEquals(-0.0715f, algo.tc4CirrusValue(), eps);
        algo.setRefl(reflWhite);
        assertEquals(-0.198f, algo.tc4CirrusValue(), eps);
    }

    @Test
    public void testTc4Value() {
        algo.setRefl(reflClear);
        assertEquals(-0.0476f, algo.tc4Value(), eps);
        algo.setRefl(reflCloudSure);
        assertEquals(-0.2886f, algo.tc4Value(), eps);
        algo.setRefl(reflCloudAmbiguous);
        assertEquals(-0.0718f, algo.tc4Value(), eps);
        algo.setRefl(reflBright);
        assertEquals(-0.0661f, algo.tc4Value(), eps);
        algo.setRefl(reflWhite);
        assertEquals(-0.1773f, algo.tc4Value(), eps);
    }

    @Test
    public void testNdwiValue() {
        algo.setRefl(reflClear);
        assertEquals(0.1581f, algo.ndwiValue(), eps);
        algo.setRefl(reflCloudSure);
        assertEquals(0.4122f, algo.ndwiValue(), eps);
        algo.setRefl(reflCloudAmbiguous);
        assertEquals(0.3422f, algo.ndwiValue(), eps);
        algo.setRefl(reflBright);
        assertEquals(0.2163f, algo.ndwiValue(), eps);
        algo.setRefl(reflWhite);
        assertEquals(0.2922f, algo.ndwiValue(), eps);
    }

    @Test
    public void testB3B11Value() {

    }

    @Test
    public void testVisBrightValue() {

    }

    @Test
    public void testIsCloudSure() {

    }

    @Test
    public void testIsCloudAmbiguous() {

    }

    @Test
    public void testIsCloud() {

    }

    @Test
    public void testIsClear() {

    }

    @Test
    public void testIsBright() {

    }

    @Test
    public void testIsWhite() {

    }

    @Test
    public void testIsSnowIce() {

    }



    @Test
    public void testAreAllReflectancesValid() {
        float[] reflOrig = new float[]{12.3f, 12.3f, 12.3f, 12.3f};
        assertTrue(S2IdepixUtils.areAllReflectancesValid(reflOrig));

        reflOrig = new float[]{Float.NaN, 12.3f, Float.NaN, 12.3f};
        assertFalse(S2IdepixUtils.areAllReflectancesValid(reflOrig));
    }

    @Test
    public void testIsNoReflectanceValid() {
        float[] reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, 12.3f};
        assertFalse(S2IdepixUtils.isNoReflectanceValid(reflOrig));

        reflOrig = new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        assertTrue(S2IdepixUtils.isNoReflectanceValid(reflOrig));
    }

    @Test
    public void testSpectralSlope() {
        float wvl1 = 450.0f;
        float wvl2 = 460.0f;
        float refl1 = 50.0f;
        float refl2 = 100.0f;
        assertEquals(5.0f, S2IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2), 1.0e-6f);

        refl1 = 500.0f;
        assertEquals(-40.0f, S2IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2), 1.0e-6f);

        wvl2 = 450.0f;
        refl1 = 50.0f;
        final float slope = S2IdepixUtils.spectralSlope(refl1, refl2, wvl1, wvl2);
        assertTrue(Float.isInfinite(slope));
    }

    @Test
    public void testSetNewBandProperties() {
        Band band1 = new Band("test", ProductData.TYPE_FLOAT32, 10, 10);
        S2IdepixUtils.setNewBandProperties(band1, "bla", "km", -999.0, false);
        assertEquals("bla", band1.getDescription());
        assertEquals("km", band1.getUnit());
        assertEquals(-999.0, band1.getNoDataValue(), 1.0e-8);
        assertFalse(band1.isNoDataValueUsed());

        Band band2 = new Band("test2", ProductData.TYPE_INT32, 10, 10);
        S2IdepixUtils.setNewBandProperties(band2, "blubb", "ton", -1, true);
        assertEquals("blubb", band2.getDescription());
        assertEquals("ton", band2.getUnit());
        assertEquals(-1.0, band2.getNoDataValue(), 1.0e-8);
        assertTrue(band2.isNoDataValueUsed());
    }

    @Test
    public void testConvertGeophysicalToMathematicalAngle() {
        double geoAngle = S2IdepixUtils.convertGeophysicalToMathematicalAngle(31.0);
        assertEquals(59.0, geoAngle, 1.0);
        geoAngle = S2IdepixUtils.convertGeophysicalToMathematicalAngle(134.0);
        assertEquals(316.0, geoAngle, 1.0);
        geoAngle = S2IdepixUtils.convertGeophysicalToMathematicalAngle(213.0);
        assertEquals(237.0, geoAngle, 1.0);
        geoAngle = S2IdepixUtils.convertGeophysicalToMathematicalAngle(301.0);
        assertEquals(149.0, geoAngle, 1.0);
        geoAngle = S2IdepixUtils.convertGeophysicalToMathematicalAngle(3100.0);
        assertTrue(Double.isNaN(geoAngle));
    }

}