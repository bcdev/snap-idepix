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

import org.junit.Test;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CloudShadowDilationTest {

    @Test
    public void testRadiusForProductResolution() {
        assertEquals(4, CloudShadowDilation.getRadius(10));
        assertEquals(2, CloudShadowDilation.getRadius(20));
        assertEquals(1, CloudShadowDilation.getRadius(60));
    }

    @Test
    public void testUnsupportedResolutionIsRejected() {
        try {
            CloudShadowDilation.getRadius(30);
            fail("Expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("30"));
        }
    }

    @Test
    public void testRadiusOneIsThreeByThreeCross() {
        final Set<String> offsets = toSet(CloudShadowDilation.createStarOffsets(1));

        assertEquals(5, offsets.size());
        assertTrue(offsets.contains(key(0, 0)));
        assertTrue(offsets.contains(key(-1, 0)));
        assertTrue(offsets.contains(key(1, 0)));
        assertTrue(offsets.contains(key(0, -1)));
        assertTrue(offsets.contains(key(0, 1)));
        assertFalse(offsets.contains(key(1, 1)));
    }

    @Test
    public void testRadiusTwoIsStarAndNotFilledSquare() {
        final Set<String> offsets = toSet(CloudShadowDilation.createStarOffsets(2));

        assertEquals(13, offsets.size());
        assertTrue(offsets.contains(key(0, 0)));
        assertTrue(offsets.contains(key(2, 0)));
        assertTrue(offsets.contains(key(0, -2)));
        assertTrue(offsets.contains(key(1, 1)));
        assertFalse(offsets.contains(key(2, 2)));
        assertFalse(offsets.contains(key(1, 2)));
        assertFalse(offsets.contains(key(-2, 1)));
    }

    @Test
    public void testRadiusFourIsFilledManhattanStar() {
        final int[][] offsets = CloudShadowDilation.createStarOffsets(4);
        final Set<String> uniqueOffsets = toSet(offsets);

        assertEquals(41, offsets.length);
        assertEquals(offsets.length, uniqueOffsets.size());
        assertTrue(uniqueOffsets.contains(key(-3, -1)));
        assertTrue(uniqueOffsets.contains(key(0, 4)));
        assertTrue(uniqueOffsets.contains(key(4, 0)));
        assertFalse(uniqueOffsets.contains(key(4, 1)));
        assertFalse(uniqueOffsets.contains(key(3, 4)));
    }

    @Test
    public void testRectangleExtensionIsClippedAtSceneBorders() {
        assertEquals(new Rectangle(6, 16, 13, 13),
                CloudShadowDilation.extend(new Rectangle(10, 20, 5, 5), 4, 100, 100));
        assertEquals(new Rectangle(0, 0, 7, 8),
                CloudShadowDilation.extend(new Rectangle(0, 0, 5, 6), 2, 100, 100));
        assertEquals(new Rectangle(93, 92, 7, 8),
                CloudShadowDilation.extend(new Rectangle(95, 94, 5, 6), 2, 100, 100));
    }

    @Test
    public void testAddStarWritesExpectedPixels() {
        final boolean[] target = new boolean[7 * 7];

        CloudShadowDilation.addStar(target, 7, 7, 3, 3,
                CloudShadowDilation.createStarOffsets(2));

        assertEquals(13, countSetPixels(target));
        assertTrue(target[3 * 7 + 3]);
        assertTrue(target[2 * 7 + 2]);
        assertTrue(target[3 * 7 + 5]);
        assertFalse(target[1 * 7 + 1]);
        assertFalse(target[1 * 7 + 2]);
    }

    @Test
    public void testAddStarClipsAtTargetBorder() {
        final boolean[] target = new boolean[5 * 5];

        CloudShadowDilation.addStar(target, 5, 5, 0, 0,
                CloudShadowDilation.createStarOffsets(2));

        assertEquals(6, countSetPixels(target));
        assertTrue(target[0]);
        assertTrue(target[2]);
        assertTrue(target[2 * 5]);
        assertTrue(target[1 * 5 + 1]);
        assertFalse(target[2 * 5 + 2]);
    }

    private static Set<String> toSet(int[][] offsets) {
        final Set<String> set = new HashSet<>();
        for (int[] offset : offsets) {
            set.add(key(offset[0], offset[1]));
        }
        return set;
    }

    private static String key(int x, int y) {
        return x + "," + y;
    }

    private static int countSetPixels(boolean[] values) {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }
}
