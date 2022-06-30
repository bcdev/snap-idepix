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

import org.esa.snap.core.image.ImageManager;
import org.esa.snap.core.util.math.MathUtils;

import javax.media.jai.OpImage;
import javax.media.jai.PlanarImage;
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;

/**
 * @author Marco Peters
 */
public class OrientationOpImage extends OpImage {

    public OrientationOpImage(RenderedImage lats, RenderedImage lons) {
        super(vectorize(lats, lons), ImageManager.createSingleBandedImageLayout(DataBuffer.TYPE_DOUBLE, lats.getWidth(), lats.getHeight(), lats.getTileWidth(), lats.getTileHeight()),
              null, false);
    }

    @Override
    protected void computeRect(PlanarImage[] planarImages, WritableRaster writableRaster, Rectangle destRect) {
        PlanarImage lats = planarImages[0];
        PlanarImage lons = planarImages[1];
        int x0 = destRect.x;
        int y0 = destRect.y;
        final int x1 = x0 + writableRaster.getWidth()-1;
        final int y1 = y0 + writableRaster.getHeight()-1;
        final Raster latsData = lats.getData(destRect);
        final Raster lonsData = lons.getData(destRect);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                final int x_a = MathUtils.crop(x - 1, x0, x1);
                final int x_b = MathUtils.crop(x + 1, x0, x1);
                final float lat1 = latsData.getSampleFloat(x_a, y, 0);
                final float lon1 = lonsData.getSampleFloat(x_a, y, 0);
                final float lat2 = latsData.getSampleFloat(x_b, y, 0);
                final float lon2 = lonsData.getSampleFloat(x_b, y, 0);
                final double orientation = computeOrientation(lat1, lon1, lat2, lon2);
                writableRaster.setSample(x, y, 0, orientation);
            }
        }
    }

    static double computeOrientation(float lat1, float lon1, float lat2, float lon2) {
        return Math.atan2(-(lat2 - lat1), (lon2 - lon1) * Math.cos(lat1 * Math.PI / 180.0)) * 180.0 / Math.PI;
    }

    @Override
    public Rectangle mapSourceRect(Rectangle rectangle, int i) {
        return new Rectangle(rectangle);
    }

    @Override
    public Rectangle mapDestRect(Rectangle rectangle, int i) {
        return new Rectangle(rectangle);
    }

}
