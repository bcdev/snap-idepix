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

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import org.esa.snap.core.datamodel.ProductData;
import org.junit.Test;

import java.text.ParseException;

import static org.junit.Assert.*;

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

    @Test
    public void testCoordinateIsInsideArctica() {
        final Polygon arcticPolygon =
                IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ARCTIC_POLYGON_COORDS);
        final GeometryFactory gf = new GeometryFactory();

        Coordinate insideCoord = new Coordinate(-45.0, 75.0);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, arcticPolygon, gf));

        insideCoord = new Coordinate(-90.0, 89.95);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, arcticPolygon, gf));

        insideCoord = new Coordinate(90.0, 89.95);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, arcticPolygon, gf));

        Coordinate outsideCoord = new Coordinate(10.0, 20.0);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(outsideCoord, arcticPolygon, gf));

        outsideCoord = new Coordinate(10.0, 89.9999);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(outsideCoord, arcticPolygon, gf));

        // close to polygon boundary:
        final Coordinate justInsideCoord = new Coordinate(-57.0, 71.0);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(justInsideCoord, arcticPolygon, gf));

        final Coordinate justOutsideCoord = new Coordinate(-58.0, 71.0);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(justOutsideCoord, arcticPolygon, gf));
    }

    @Test
    public void testCoordinateIsInsideAntarctica() {
        final Polygon antarcticaPolygon =
                IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ANTARCTICA_POLYGON_COORDS);
        final GeometryFactory gf = new GeometryFactory();

        Coordinate insideCoord = new Coordinate(-45.0, -75.0);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, antarcticaPolygon, gf));

        insideCoord = new Coordinate(-45.0, -89.9);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, antarcticaPolygon, gf));

        insideCoord = new Coordinate(45.0, -89.9);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(insideCoord, antarcticaPolygon, gf));

        Coordinate outsideCoord = new Coordinate(10.0, 20.0);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(outsideCoord, antarcticaPolygon, gf));

        outsideCoord = new Coordinate(10.0, -89.999);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(outsideCoord, antarcticaPolygon, gf));

        // close to polygon boundary:
        final Coordinate justInsideCoord = new Coordinate(-57.0, -61.0);
        assertTrue(IdepixOlciUtils.isCoordinateInsideGeometry(justInsideCoord, antarcticaPolygon, gf));

        final Coordinate justOutsideCoord = new Coordinate(-57.0, -59.0);
        assertFalse(IdepixOlciUtils.isCoordinateInsideGeometry(justOutsideCoord, antarcticaPolygon, gf));
    }

    @Test
    public void testGeometryIntersectsWithArctic() {
        final Polygon arcticPolygon =
                IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ARCTIC_POLYGON_COORDS);

        double[][] outsideGeomCoordArray = new double[][]{
                {-43.25, 55.75},
                {-36.25, 55.75},
                {-36.25, 50.25},
                {-42.75, 50.25},
                {-43.25, 55.75}
        };
        final Polygon outsidePolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(outsideGeomCoordArray);
        assertFalse(outsidePolygon.intersects(arcticPolygon));

        double[][] insideGeomCoordArray = new double[][]{
                {-40.25, 75.75},
                {-33.75, 76.25},
                {-33.75, 71.25},
                {-39.25, 71.25},
                {-40.25, 75.75}
        };
        final Polygon insidePolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(insideGeomCoordArray);
        assertTrue(insidePolygon.intersects(arcticPolygon));

        double[][] overlappingGeomCoordArray = new double[][]{
                {-40.25, 75.75},
                {-33.75, 76.25},
                {-36.25, 50.25},
                {-42.75, 50.25},
                {-40.25, 75.75}
        };
        final Polygon overlappingPolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(overlappingGeomCoordArray);
        assertTrue(overlappingPolygon.intersects(arcticPolygon));
    }

    @Test
    public void testGeometryIntersectsWithAntarctica() {
        final Polygon antarcticaPolygon =
                IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ANTARCTICA_POLYGON_COORDS);

        double[][] outsideGeomCoordArray = new double[][]{
                {-43.25, 55.75},
                {-36.25, 55.75},
                {-36.25, 50.25},
                {-42.75, 50.25},
                {-43.25, 55.75}
        };
        final Polygon outsidePolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(outsideGeomCoordArray);
        assertFalse(outsidePolygon.intersects(antarcticaPolygon));

        double[][] insideGeomCoordArray = new double[][]{
                {-40.25, -75.75},
                {-33.75, -76.25},
                {-33.75, -81.25},
                {-39.25, -81.25},
                {-40.25, -75.75}
        };
        final Polygon insidePolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(insideGeomCoordArray);
        assertTrue(insidePolygon.intersects(antarcticaPolygon));

        double[][] overlappingGeomCoordArray = new double[][]{
                {-40.25, -59.75},
                {-33.75, -59.75},
                {-33.75, -60.25},
                {-40.25, -60.25},
                {-40.25, -59.75}
        };
        final Polygon overlappingPolygon = IdepixOlciUtils.createPolygonFromCoordinateArray(overlappingGeomCoordArray);
        assertTrue(overlappingPolygon.intersects(antarcticaPolygon));
    }

    @Test
    public void testComputeApparentSaa() {
        double sza = 42.72302;
        double saa = 147.73538;
        double oza = 2.3104007;
        double oaa = -76.864624;
        double lat = 25.;
        assertEquals(149.43941, IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa, lat), 1.E-4);

        sza = 45.74776;
        saa = 140.51381;
        oza = 40.13199;
        oaa = 100.81727;
        assertEquals(85.56738, IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa, lat), 1.E-4);
    }

    @Test
    public void testGetMonthFromStartStopTime() throws ParseException {
        ProductData.UTC startStopTime = ProductData.UTC.parse("09-DEC-2019 20:12:13"); // dd-MMM-yyyy HH:mm:ss
        assertEquals(12, IdepixOlciUtils.getMonthFromStartStopTime(startStopTime));

        startStopTime = ProductData.UTC.parse("11-APR-2019 17:21:52.053827");
        assertEquals(4, IdepixOlciUtils.getMonthFromStartStopTime(startStopTime));
    }

}