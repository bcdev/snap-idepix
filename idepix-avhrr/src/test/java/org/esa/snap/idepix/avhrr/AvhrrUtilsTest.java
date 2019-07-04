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

package org.esa.snap.idepix.avhrr;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class AvhrrUtilsTest {

    private AvhrrAuxdata.Rad2BTTable rad2BTTable_14;

    @BeforeClass
    public static void beforeClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        rad2BTTable_14 = AvhrrAuxdata.getInstance().createRad2BTTable("14");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testConvertRadianceToBt() {
        // used for USGS
        final double radiance3 = 1.569;
        final double radiance4 = 124.9307;
        final double radiance5 = 141.6519;
        final double bt3 = AvhrrAcUtils.convertRadianceToBt("14", rad2BTTable_14, radiance3, 3, 0.0f);
        final double bt4 = AvhrrAcUtils.convertRadianceToBt("14", rad2BTTable_14, radiance4, 4, 0.0f);
        final double bt5 = AvhrrAcUtils.convertRadianceToBt("14", rad2BTTable_14, radiance5, 5, 0.0f);
        assertEquals(321.208, bt3, 1.E-3);
        assertEquals(307.448, bt4, 1.E-3);
        assertEquals(307.254, bt5, 1.E-3);
    }

    @Test
    public void testConvertBtToRadiance() {
        // used for timeline
        final double bt3 = 321.208;
        final double bt4 = 307.448;
        final double bt5 = 307.254;
        final double radiance3 = AvhrrAcUtils.convertBtToRadiance("14", rad2BTTable_14, bt3, 3, 0.0f);
        final double radiance4 = AvhrrAcUtils.convertBtToRadiance("14", rad2BTTable_14, bt4, 4, 0.0f);
        final double radiance5 = AvhrrAcUtils.convertBtToRadiance("14", rad2BTTable_14, bt5, 5, 0.0f);
        assertEquals(1.5, radiance3, 1.E-1);
        assertEquals(124.9, radiance4, 0.3);        // todo: check the small difference
        assertEquals(141.6, radiance5, 2.E-1);
    }

}