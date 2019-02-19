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

package org.esa.snap.idepix.olci;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdepixOlciUtilsTest {

    @Test
    public void testGetRefinedHeightFromCtp() {
        double[] temperatures = new double[]{
                285.57446,             // 1
                282.85202,             // 2
                281.8181,              // 3
                280.7165,              // 4
                278.78867,             // 5
                276.87848,             // 6
                272.55078,             // 7 
                265.57416,             // 8
                256.5705,              // 9
                244.82516,             // 10
                229.13414,             // 11
                218.55168,             // 12
                208.09369,             // 13
                214.1096,              // 14
                210.8931,              // 15
                211.13419,             // 16
                212.4217,              // 17
                211.85518,             // 18
                212.15376,             // 19
                215.58745,             // 20
                221.34615,             // 21
                231.9397,              // 22
                254.38956,             // 23
                270.59445,             // 24
                273.14984              // 25
        };
        double ctp = 622.6564;
        double slp = 1013.48065;
        double heightFromCtp = IdepixOlciUtils.getRefinedHeightFromCtp(ctp, slp, temperatures);
        assertEquals(3638.864, heightFromCtp, 1.E-2);

        temperatures = new double[]{
                279.76178,             // 1
                276.8786,              // 2
                275.2141,              // 3
                273.40378,             // 4
                270.1625,              // 5
                268.1065,              // 6
                261.449,               // 7
                256.47284,             // 8
                247.98018,             // 9
                237.19742,             // 10
                225.11032,             // 11
                218.98813,             // 12
                217.61,                // 13
                219.14716,             // 14
                216.29794,             // 15
                216.65158,             // 16
                214.7119,              // 17
                212.98112,             // 18
                214.11125,             // 19
                222.45465,             // 20
                229.25972,             // 21
                242.21924,             // 22
                261.0823,              // 23
                267.98877,             // 24
                268.43387              // 25
        };
        ctp = 969.0744;
        slp = 1016.1385;
        heightFromCtp = IdepixOlciUtils.getRefinedHeightFromCtp(ctp, slp, temperatures);
        assertEquals(384.2026, heightFromCtp, 1.E-2);
    }

}