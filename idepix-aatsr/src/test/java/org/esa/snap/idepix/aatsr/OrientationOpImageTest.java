/*
 * Copyright (c) 2022.  Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.idepix.aatsr;

import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.util.ImageUtils;
import org.junit.Test;

import java.awt.image.RenderedImage;

import static org.junit.Assert.assertEquals;

/**
 * @author Marco Peters
 */
public class OrientationOpImageTest {


    @Test
    public void testOrientationImage() {
        float[] lats = {
                56.0214f, 55.7504f, 55.4608f, 56.0214f,
                55.1538f, 55.7504f, 55.4608f, 55.1538f,
                57.3770f, 57.3770f, 57.1410f, 56.8849f,
                57.1410f, 56.6093f, 56.3146f, 56.8849f,
        };
        float[] lons = {
                -175.4993f, -177.0226f, -178.5242f, -175.4993f,
                +179.9959f, -177.0226f, -178.5242f, -179.9959f,
                -172.8034f, -174.4020f, -175.9795f, -174.4020f,
                -177.5348f, -179.0667f, -175.9795f, +179.4245f
        };

        final RenderedImage latImage = ImageUtils.createRenderedImage(4, 4, ProductData.createInstance(lats));
        final RenderedImage lonImage = ImageUtils.createRenderedImage(4, 4, ProductData.createInstance(lons));
        final OrientationOpImage orientation = new OrientationOpImage(latImage, lonImage);
        assertEquals(4, orientation.getWidth());
        assertEquals(4, orientation.getHeight());

        assertEquals(+162.342737, orientation.getData().getSampleDouble(0,0,0), 1.0e-6);
        assertEquals(-179.914133, orientation.getData().getSampleDouble(1,1,0), 1.0e-6);
        assertEquals(90.0, orientation.getData().getSampleDouble(2,2,0), 1.0e-6);
        assertEquals(-0.165765, orientation.getData().getSampleDouble(3,3,0), 1.0e-6);
    }

    @Test
    public void computeOrientation() {
        assertEquals(-45.004363, OrientationOpImage.computeOrientation(1, 2, 2, 3f), 1.0e-6);
        assertEquals(-26.917511, OrientationOpImage.computeOrientation(10f, 10f, 11f, 12f), 1.0e-6);
    }

}