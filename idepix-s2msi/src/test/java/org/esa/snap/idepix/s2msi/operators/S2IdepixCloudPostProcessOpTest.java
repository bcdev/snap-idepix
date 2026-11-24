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
 */

package org.esa.snap.idepix.s2msi.operators;

import org.junit.Test;

import java.awt.Rectangle;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_BUFFER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class S2IdepixCloudPostProcessOpTest {

    @Test
    public void testWidthTwoCloudBufferIsFiveByFiveDiamond() {
        final int[][] accumulator = new int[5][5];

        S2IdepixCloudPostProcessOp.addCloudBufferInAccu(2, 2,
                                                        new Rectangle(0, 0, 5, 5), 2, accumulator);

        assertEquals(13, countBufferedPixels(accumulator));
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                assertEquals(Math.abs(x - 2) + Math.abs(y - 2) <= 2, isBuffered(accumulator[y][x]));
            }
        }
    }

    @Test
    public void testCloudBufferDiamondIsClippedAtTargetBorder() {
        final int[][] accumulator = new int[5][5];

        S2IdepixCloudPostProcessOp.addCloudBufferInAccu(0, 0,
                                                        new Rectangle(0, 0, 5, 5), 2, accumulator);

        assertEquals(6, countBufferedPixels(accumulator));
        assertTrue(isBuffered(accumulator[0][0]));
        assertTrue(isBuffered(accumulator[0][2]));
        assertTrue(isBuffered(accumulator[1][1]));
        assertTrue(isBuffered(accumulator[2][0]));
        assertFalse(isBuffered(accumulator[1][2]));
        assertFalse(isBuffered(accumulator[2][2]));
    }

    @Test
    public void testHorizontalExtentDefinesManhattanRows() {
        assertEquals(0, S2IdepixCloudBuffer.getHorizontalExtent(-2, 2));
        assertEquals(1, S2IdepixCloudBuffer.getHorizontalExtent(-1, 2));
        assertEquals(2, S2IdepixCloudBuffer.getHorizontalExtent(0, 2));
        assertEquals(1, S2IdepixCloudBuffer.getHorizontalExtent(1, 2));
        assertEquals(0, S2IdepixCloudBuffer.getHorizontalExtent(2, 2));
    }

    private static int countBufferedPixels(int[][] accumulator) {
        int count = 0;
        for (int[] row : accumulator) {
            for (int flags : row) {
                if (isBuffered(flags)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean isBuffered(int flags) {
        return (flags & 1 << IDEPIX_CLOUD_BUFFER) != 0;
    }
}
