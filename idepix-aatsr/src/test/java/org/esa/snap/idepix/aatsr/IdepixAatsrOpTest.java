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
    public void validate_ValidSourceProduct() {
        final Product aatsr = createDummyAatsrSource();
        final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();

        try {
            idepixAatsrOp.validate(aatsr);
        } catch (Throwable t) {
            fail("No exception expected here! Source should be valid.");
        }

    }

    @Test(expected = OperatorException.class)
    public void validate_InvalidSourceProduct() {
        final Product aatsr = createDummyAatsrSource();
        final IdepixAatsrOp idepixAatsrOp = new IdepixAatsrOp();

        aatsr.setProductType("differentType");
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

    private Product createDummyAatsrSource() {
        final DummyProductBuilder pb = new DummyProductBuilder();
        pb.size(DummyProductBuilder.Size.MEDIUM);
        pb.gc(DummyProductBuilder.GC.PER_PIXEL);
        final Product product = pb.create();
        product.setProductType("ENV_AT_1_RBT");
        return product;
    }

}
