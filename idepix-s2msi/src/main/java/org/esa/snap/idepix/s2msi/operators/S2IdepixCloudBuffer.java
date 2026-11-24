/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.snap.idepix.s2msi.operators;


import org.esa.snap.core.gpf.Tile;

import java.awt.*;

/**
 * cloud buffer algorithms
 */
public class S2IdepixCloudBuffer {

    public static void computeSimpleCloudBuffer(int x, int y,
                                                Tile targetTile,
                                                Rectangle extendedRectangle,
                                                int cloudBufferWidth,
                                                int cloudBufferFlagBit) {
        Rectangle rectangle = targetTile.getRectangle();
        int TOP_BORDER = Math.max(y - cloudBufferWidth, extendedRectangle.y);
        int BOTTOM_BORDER = Math.min(y + cloudBufferWidth, extendedRectangle.y + extendedRectangle.height - 1);

        for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
            final int horizontalExtent = getHorizontalExtent(j - y, cloudBufferWidth);
            final int leftBorder = Math.max(x - horizontalExtent, extendedRectangle.x);
            final int rightBorder = Math.min(x + horizontalExtent,
                                             extendedRectangle.x + extendedRectangle.width - 1);
            for (int i = leftBorder; i <= rightBorder; i++) {
                if (rectangle.contains(i, j) && extendedRectangle.contains(i, j)) {
                    targetTile.setSample(i, j, cloudBufferFlagBit, true);
                }
            }
        }
    }

    static int getHorizontalExtent(int yOffset, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must not be negative: " + radius);
        }
        final int absoluteYOffset = Math.abs(yOffset);
        if (absoluteYOffset > radius) {
            throw new IllegalArgumentException("Y offset exceeds radius: " + yOffset);
        }
        return radius - absoluteYOffset;
    }
}
