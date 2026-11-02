package org.esa.snap.idepix.s2msi;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class S2IdepixClassificationOpTest {

    @Test
    public void copiedReflectanceIncludesValidityDependencies() throws Exception {
        Product source = new Product("source", "test", 2, 2);
        Band b2 = source.addBand("B2", ProductData.TYPE_FLOAT32);
        source.addBand("B_detector_footprint_B2", ProductData.TYPE_INT8);
        source.addBand("B_nodata_B2", ProductData.TYPE_INT8);
        b2.setValidPixelExpression("B2.raw >= -0.1 && B_detector_footprint_B2.raw > 0 && B_nodata_B2 == 0");

        Product target = new Product("target", "test", 2, 2);
        S2IdepixClassificationOp.copyBandWithValidityDependencies(b2, source, target);

        assertNotNull(target.getBand("B2"));
        assertNotNull(target.getBand("B_detector_footprint_B2"));
        assertNotNull(target.getBand("B_nodata_B2"));
        assertNotNull(target.parseExpression(target.getBand("B2").getValidPixelExpression()));
        assertTrue(target.getBand("B2").getValidPixelExpression().contains("B_detector_footprint_B2.raw"));
    }
}
