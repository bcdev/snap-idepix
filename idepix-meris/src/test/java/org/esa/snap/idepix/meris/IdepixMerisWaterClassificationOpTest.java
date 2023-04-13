/*
 * Copyright (c) 2023.  Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 *
 */

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
    public void testChiw() {
        double ator = Math.PI / 180.0;
        for (double u = -180.0; u <= 180.0; u += 45.0) {
            for (double v = -180.0; v <= 180.0; v += 45.0) {
                for (double s = -180.0; s <= 180.0; s += 15.0) {
                    double expected = computeChiWReference(u * ator, v * ator, s);
                    double result = IdepixMerisWaterClassificationOp.computeChiW(u * ator, v * ator, s);
                    assertEquals(u + "," + v + "," + s, result, expected, 1.0e-5);
                }
            }
        }
    }

    public void testCos() {
        for (double oaa = -180.0; oaa <= 360.0; oaa += 60.0) {
            for (double saa = -180.0; saa <= 360.0; saa += 60.0) {
                final double deltaPhi;
                if (oaa < 0.0) {
                    deltaPhi = 360.0 - Math.abs(oaa) - saa;
                } else {
                    deltaPhi = saa - oaa;
                }
                double expected = Math.cos(MathUtils.DTOR * deltaPhi);
                double result = Math.cos(MathUtils.DTOR * (saa - oaa));
                assertEquals(oaa + "," + saa, expected, result, 1e-5);
            }
        }
    }

    public void testAzimuthDifference() {
        for (double oaa = -180.0; oaa <= 360.0; oaa += 45.0) {
            for (double saa = -180.0; saa <= 360.0; saa += 45.0) {
                double expected = computeAzimuthDifferenceReference(oaa, saa);
                double result = IdepixUtils.computeAzimuthDifference(oaa, saa);
                assertEquals(oaa + "," + saa, expected, result, 1e-5);
            }
        }
    }

    public static double computeAzimuthDifferenceReference(final double vaa, final double saa) {
        return MathUtils.RTOD * Math.acos(Math.cos(MathUtils.DTOR * (vaa - saa)));
    }

    private static double computeChiWReference(double windU, double windV, double saa) {
        final double phiw = azimuth(windU, windV);
        /* and "scattering" angle */
        final double arg = MathUtils.DTOR * (saa - phiw);
        return MathUtils.RTOD * (Math.acos(Math.cos(arg)));
    }

    private static double azimuth(double y, double x) {
        return MathUtils.RTOD * Math.atan2(y, x);
    }

}