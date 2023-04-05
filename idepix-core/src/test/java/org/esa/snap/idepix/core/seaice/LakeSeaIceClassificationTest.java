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

package org.esa.snap.idepix.core.seaice;

import org.esa.snap.core.datamodel.Product;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LakeSeaIceClassificationTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        LakeSeaIceAuxdata.install();
    }

    @Test
    public void testGetMonthlyLakeSeaIceMask() {
        LakeSeaIceClassification classification = new LakeSeaIceClassification(null,
                LakeSeaIceAuxdata.AUXDATA_DIRECTORY, Calendar.MARCH + 1);
        final Product monthlyMaskProduct = classification.getMonthlyMaskProduct();
        assertNotNull(monthlyMaskProduct);

        assertEquals(360, monthlyMaskProduct.getSceneRasterWidth());
        assertEquals(180, monthlyMaskProduct.getSceneRasterHeight());
        assertEquals("ice_climatology_03_max", monthlyMaskProduct.getName());
        assertEquals(1, monthlyMaskProduct.getNumBands());
        assertEquals("ice_climatology", monthlyMaskProduct.getBandAt(0).getName());

        // ice_climatology_03_max.dim has value 76.0 at pixel (16, 29):
        assertEquals(76.0f, classification.getMonthlyMaskValue(16, 29), 0.0f);
    }
}