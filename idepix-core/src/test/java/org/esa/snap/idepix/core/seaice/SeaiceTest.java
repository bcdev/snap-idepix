package org.esa.snap.idepix.core.seaice;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SeaiceTest {

    @Test
    public void testGetMonthlyLakeSeaIceMask() {
        String auxdataIceMapsPath = null;
        try {
            auxdataIceMapsPath = IdepixIO.installAuxdataIceMaps();
        } catch (IOException e) {
           fail(e.getMessage());
        }

        LakeSeaIceClassification lakeSeaIceClassification = new LakeSeaIceClassification(null, auxdataIceMapsPath,
                Calendar.MARCH + 1);
        final Product monthlyMaskProduct = lakeSeaIceClassification.getMonthlyMaskProduct();
        assertNotNull(monthlyMaskProduct);

        assertEquals(360, monthlyMaskProduct.getSceneRasterWidth());
        assertEquals(180, monthlyMaskProduct.getSceneRasterHeight());
        assertEquals("ice_climatology_03_max", monthlyMaskProduct.getName());
        assertEquals(1, monthlyMaskProduct.getNumBands());
        assertEquals("ice_climatology", monthlyMaskProduct.getBandAt(0).getName());

        // ice_climatology_03_max.dim has value 76.0 at pixel (16, 29):
        assertEquals(76.0f, lakeSeaIceClassification.getMonthlyMaskValue(16, 29), 0.0f);
    }
}