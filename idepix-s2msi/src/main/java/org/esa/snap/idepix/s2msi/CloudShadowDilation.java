/*
 * Copyright (C) 2026 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.idepix.s2msi;

import java.awt.Rectangle;

/**
 * Defines the resolution-dependent star structuring element used to dilate cloud shadows.
 * The star contains every offset whose Manhattan distance from the centre does not exceed the radius.
 */
final class CloudShadowDilation {

    private CloudShadowDilation() {
    }

    static int getRadius(int resolution) {
        switch (resolution) {
            case 10:
                return 4;
            case 20:
                return 2;
            case 60:
                return 1;
            default:
                throw new IllegalArgumentException("Unsupported Sentinel-2 resolution: " + resolution);
        }
    }

    static int[][] createStarOffsets(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must not be negative: " + radius);
        }

        final int[][] offsets = new int[1 + 2 * radius * (radius + 1)][2];
        int offsetIndex = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (Math.abs(x) + Math.abs(y) <= radius) {
                    offsets[offsetIndex][0] = x;
                    offsets[offsetIndex][1] = y;
                    offsetIndex++;
                }
            }
        }
        return offsets;
    }

    static Rectangle extend(Rectangle rectangle, int radius, int sceneWidth, int sceneHeight) {
        final int minX = Math.max(0, rectangle.x - radius);
        final int minY = Math.max(0, rectangle.y - radius);
        final int maxX = Math.min(sceneWidth, rectangle.x + rectangle.width + radius);
        final int maxY = Math.min(sceneHeight, rectangle.y + rectangle.height + radius);
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    static void addStar(boolean[] target, int width, int height, int centerX, int centerY, int[][] offsets) {
        for (int[] offset : offsets) {
            final int x = centerX + offset[0];
            final int y = centerY + offset[1];
            if (x >= 0 && x < width && y >= 0 && y < height) {
                target[y * width + x] = true;
            }
        }
    }
}
