/*
 * Copyright (C) 2022 Brockmann Consult GmbH (info@brockmann-consult.de)
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
 */

package org.esa.snap.idepix.aatsr;

import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.core.util.DummyProductBuilder;
import org.esa.snap.core.util.math.Range;
import org.junit.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import static org.junit.Assert.*;

public class IdepixAatsrOpTest {

    @Test
    public void testOperatorSpiIsLoaded() {
        OperatorSpiRegistry registry = GPF.getDefaultInstance().getOperatorSpiRegistry();
        OperatorSpi operatorSpi = registry.getOperatorSpi("Idepix.Aatsr");
        assertNotNull(operatorSpi);
        assertEquals("Idepix.Aatsr", operatorSpi.getOperatorAlias());
        assertNotNull(operatorSpi.getOperatorDescriptor());
        assertSame(operatorSpi.getOperatorClass(), operatorSpi.getOperatorDescriptor().getOperatorClass());
    }

    @Test
    public void testTargetProductSignature() {
        final Product aatsr = createDummyAatsrSource();
        final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();
        idepixAatsrOp.setSourceProduct(aatsr);

        idepixAatsrOp.setParameterDefaultValues();

        final Product targetProduct = idepixAatsrOp.getTargetProduct();
        assertEquals(aatsr.getName() + "_idepix", targetProduct.getName());
        assertEquals("AATSR_IDEPIX", targetProduct.getProductType());
        assertEquals(aatsr.getSceneRasterSize(), targetProduct.getSceneRasterSize());

    }

    @Test
    public void arangeTest() {
        double[] expected = {-22.5, -21.5, -20.5, -19.5, -18.5, -17.5, -16.5, -15.5, -14.5, -13.5, -12.5, -11.5, -10.5, -9.5, -8.5, -7.5, -6.5, -5.5, -4.5, -3.5, -2.5, -1.5, -0.5};
        final double[] arange = IdepixAatsrOp.arange(-22.5, 0.5, 1);
        assertArrayEquals(expected, arange, 1.0e-6);
    }

    @Test
    public void validate_ValidSourceProduct() {
        final Product aatsr = createDummyAatsrSource();

        try {
            final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();
            idepixAatsrOp.validate(aatsr);
        } catch (Throwable t) {
            fail("No exception expected here! Source should be valid.");
        }

    }

    @Test(expected = OperatorException.class)
    public void validate_InvalidSourceProduct_WrongType() {
        final Product aatsr = createDummyAatsrSource();
        aatsr.setProductType("differentType");

        final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();
        idepixAatsrOp.validate(aatsr);
    }

    @Test(expected = OperatorException.class)
    public void validate_InvalidSourceProduct_MissingBand() {
        final Product aatsr = createDummyAatsrSource();
        aatsr.removeBand(aatsr.getBand("cloud_in"));

        final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();
        idepixAatsrOp.validate(aatsr);
    }

    @Test
    public void detectFirstLastMaskedPixel() {
        final Product aatsr = createDummyAatsrSource();
        // Col 0 = 25-30
        // Col 560 = 10-12
        // Col 1300 = 300-400
        final Mask testMask = Mask.BandMathsType.create("TEST_MASK", "", aatsr.getSceneRasterWidth(), aatsr.getSceneRasterHeight(),
                                                        "(X==0.5 && Y>=25.5 && Y<=30.5) ||" +
                                                                "(X==560.5 && Y>=10.5 && Y<=12.5) ||" +
                                                                "(X==1300.5 && Y>=300.5 && Y<=400.5)",
                                                        Color.yellow, 0.5f);


        aatsr.addMask(testMask);
        int[] range;
        range = IdepixAatsrOp.detectMaskedPixelRangeInColumn(testMask, 0);
        assertEquals(25, range[0], 1.0e-6);
        assertEquals(30, range[1], 1.0e-6);

        range = IdepixAatsrOp.detectMaskedPixelRangeInColumn(testMask, 560);
        assertEquals(10, range[0], 1.0e-6);
        assertEquals(12, range[1], 1.0e-6);

        range = IdepixAatsrOp.detectMaskedPixelRangeInColumn(testMask, 1300);
        assertEquals(300, range[0], 1.0e-6);
        assertEquals(400, range[1], 1.0e-6);

    }

    @Test
    public void sliceRectangle_atZero() {
        final List<Rectangle> rectangles =
                IdepixAatsrOp.sliceRect(new Rectangle(0, 0, 512, 43138), 2000);

        assertEquals(22, rectangles.size());
        assertEquals(new Rectangle(0, 6000, 512, 2000), rectangles.get(3));
        assertEquals(new Rectangle(0, 42000, 512, 1138), rectangles.get(21));
    }

    @Test
    public void sliceRectangle_withYOffset() {
        final List<Rectangle> rectangles =
                IdepixAatsrOp.sliceRect(new Rectangle(0, 12356, 512, 20222), 2000);

        assertEquals(11, rectangles.size());
        assertEquals(new Rectangle(0, 12356, 512, 2000), rectangles.get(0));
        assertEquals(new Rectangle(0, 18356, 512, 2000), rectangles.get(3));
        assertEquals(new Rectangle(0, 32356, 512, 222), rectangles.get(10));
    }

    @Test
    public void createDistanceArray() {
        final int[] distanceArray = IdepixAatsrOp.createDistanceArray(25, 1000);
        assertEquals(51, distanceArray.length);
        assertEquals(-25000, distanceArray[0]);
        assertEquals(-24000, distanceArray[1]);
        assertEquals(-23000, distanceArray[2]);
        assertEquals(24000, distanceArray[distanceArray.length - 2]);
        assertEquals(25000, distanceArray[distanceArray.length - 1]);
    }

    @Test
    public void calcPathAndTheoreticalHeight() {
        // input values taken from Python
        float maxObjAlt = 6000;
        float minSurfAlt = -65;
        double orientation = 154.91488;
        float oza = 16.4364f;
        float saa = 313.2662f;
        float spatialRes = 1000;
        float sza = 83.9982f;
        float x_tx = 173500.0f;

        // expected values taken from Python 
        double[] illuPathHeight = {-40.56836, 53.990723, 148.30078, 186.63232, 281.31152, 375.73584, 469.8916, 413.7661, 508.5747, 603.1211, 697.3926, 735.772, 830.4507, 924.84717, 1018.9448, 962.89453, 1057.7168, 1152.2485, 1246.4727, 1284.9097, 1379.5894, 1473.9512, 1512.0195, 1606.8594, 1701.3726, 1795.5366, 1834.0474, 1928.7266, 2023.0447, 2061.1404, 2156.0017, 2250.4902, 2344.5764, 2288.1187, 2383.1836, 2477.8623, 2572.122, 2610.2534, 2705.1443, 2799.6, 2893.5803, 2837.186, 2932.3176, 3026.9954, 3121.175, 3159.3547, 3254.2874, 3348.6963, 3386.2214, 3481.4473, 3576.1243, 3670.1855, 3708.437, 3803.4297, 3897.7683, 3935.1997, 4030.5703, 4125.245, 4219.1143, 4161.652, 4257.481, 4352.5723, 4446.79, 4484.059, 4579.6772, 4674.348, 4767.853, 4710.1294, 4806.434, 4901.715, 4995.68, 5032.597, 5128.74, 5223.395, 5257.624, 5355.065, 5450.8574, 5543.962, 5579.5537, 5677.532, 5771.981, 5796.0537, 5898.027, 6000.0};
        int[][] illuPathSteps = {{-22, -55}, {-22, -54}, {-22, -53}, {-21, -53}, {-21, -52}, {-21, -51}, {-21, -50}, {-20, -51}, {-20, -50}, {-20, -49}, {-20, -48}, {-19, -48}, {-19, -47}, {-19, -46}, {-19, -45}, {-18, -46}, {-18, -45}, {-18, -44}, {-18, -43}, {-17, -43}, {-17, -42}, {-17, -41}, {-16, -41}, {-16, -40}, {-16, -39}, {-16, -38}, {-15, -38}, {-15, -37}, {-15, -36}, {-14, -36}, {-14, -35}, {-14, -34}, {-14, -33}, {-13, -34}, {-13, -33}, {-13, -32}, {-13, -31}, {-12, -31}, {-12, -30}, {-12, -29}, {-12, -28}, {-11, -29}, {-11, -28}, {-11, -27}, {-11, -26}, {-10, -26}, {-10, -25}, {-10, -24}, {-9, -24}, {-9, -23}, {-9, -22}, {-9, -21}, {-8, -21}, {-8, -20}, {-8, -19}, {-7, -19}, {-7, -18}, {-7, -17}, {-7, -16}, {-6, -17}, {-6, -16}, {-6, -15}, {-6, -14}, {-5, -14}, {-5, -13}, {-5, -12}, {-5, -11}, {-4, -12}, {-4, -11}, {-4, -10}, {-4, -9}, {-3, -9}, {-3, -8}, {-3, -7}, {-2, -7}, {-2, -6}, {-2, -5}, {-2, -4}, {-1, -4}, {-1, -3}, {-1, -2}, {0, -2}, {0, -1}, {0, 0}};
        double thresHeight = 101.97246511092132;

        final IdepixAatsrOp.PathAndHeightInfo pathAndHeightInfo = IdepixAatsrOp.calcPathAndTheoreticalHeight(sza, saa, oza, x_tx, orientation, (int) spatialRes, (int) maxObjAlt, minSurfAlt);

        assertArrayEquals(illuPathHeight, pathAndHeightInfo.illuPathHeight, 1.0e-2);
        for (int i = 0; i < illuPathSteps.length; i++) {
            int[] illuPathStep = illuPathSteps[i];
            assertArrayEquals(illuPathStep, pathAndHeightInfo.illuPathSteps[i]);
        }
        assertEquals(thresHeight, pathAndHeightInfo.threshHeight, 1.0e-2);
    }

    private Product createDummyAatsrSource() {
        final DummyProductBuilder pb = new DummyProductBuilder();
        pb.size(DummyProductBuilder.Size.MEDIUM);
        pb.gc(DummyProductBuilder.GC.PER_PIXEL);
        final Product product = pb.create();
        product.setProductType("ENV_AT_1_RBT");
        product.addBand("solar_zenith_tn", ProductData.TYPE_FLOAT32);
        product.addBand("solar_azimuth_tn", ProductData.TYPE_FLOAT32);
        product.addBand("sat_zenith_tn", ProductData.TYPE_FLOAT32);
        product.addBand("confidence_in", ProductData.TYPE_FLOAT32);
        product.addBand("elevation_in", ProductData.TYPE_FLOAT32);
        product.addBand("cloud_in", ProductData.TYPE_FLOAT32);
        product.addBand("latitude_tx", ProductData.TYPE_FLOAT32);
        product.addBand("longitude_tx", ProductData.TYPE_FLOAT32);
        product.addBand("x_tx", ProductData.TYPE_FLOAT32);
        return product;
    }

}
