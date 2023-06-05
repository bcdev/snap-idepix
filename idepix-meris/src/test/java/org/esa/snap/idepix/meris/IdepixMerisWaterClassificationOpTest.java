package org.esa.snap.idepix.meris;

import junit.framework.TestCase;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.util.IdepixUtils;

/**
 * TODO add API doc
 *
 * @author Martin Boettcher
 */
public class IdepixMerisWaterClassificationOpTest extends TestCase {
    public void testAtan() {
        double ator = Math.PI / 180.0;
        for (double y = -10.0; y <= 10.0; y += 1.0) {
            for (double x = -10.0; y <= 10.0; y += 1.0) {
                double v1 = IdepixMerisWaterClassificationOp.azimuth1(x * ator, y * ator);
                double v2 = IdepixMerisWaterClassificationOp.azimuth(x * ator, y * ator);
                assertEquals(x+","+y, v1, v2, 1.0e-5);
            }
        }
    }

    public void testChiw() {
        double ator = Math.PI / 180.0;
        for (double u = -180.0; u <= 180.0; u += 45.0) {
            for (double v = -180.0; v <= 180.0; v += 45.0) {
                for (double s = -180.0; s <= 180.0; s += 15.0) {
                    double w1 = IdepixMerisWaterClassificationOp.computeChiW1(u * ator, v * ator, s);
                    double w2 = IdepixMerisWaterClassificationOp.computeChiW2(u * ator, v * ator, s);
                    assertEquals(u + "," + v + "," + s, w1, w2, 1.0e-5);
                }
            }
        }
    }

    public void testCos() {
        for (double oaa = -180.0; oaa <= 360.0; oaa += 45.0) {
            for (double saa = -180.0; saa <= 360.0; saa += 45.0) {
                final double deltaPhi;
                if (oaa < 0.0) {
                    deltaPhi = 360.0 - Math.abs(oaa) - saa;
                } else {
                    deltaPhi = saa - oaa;
                }
                double v1 = Math.cos(MathUtils.DTOR * deltaPhi);
                double v2 = Math.cos(MathUtils.DTOR * (saa - oaa));
                assertEquals(oaa + "," + saa, v1, v2, 1e-5);
            }
        }
    }

    public void testAzimuthDifference() {
        for (double oaa = -180.0; oaa <= 360.0; oaa += 45.0) {
            for (double saa = -180.0; saa <= 360.0; saa += 45.0) {
                double v1 = IdepixUtils.computeAzimuthDifference1(oaa, saa);
                double v2 = IdepixUtils.computeAzimuthDifference(oaa, saa);
                assertEquals(oaa + "," + saa, v1, v2, 1e-5);
            }
        }
    }

}