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

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.core.util.DummyProductBuilder;
import org.junit.Test;

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

    private Product createDummyAatsrSource() {
        final DummyProductBuilder pb = new DummyProductBuilder();
        pb.size(DummyProductBuilder.Size.MEDIUM);
        pb.gc(DummyProductBuilder.GC.PER_PIXEL);
        final Product product = pb.create();
        product.setProductType("ENV_AT_1_RBT");
        return product;
    }

}
