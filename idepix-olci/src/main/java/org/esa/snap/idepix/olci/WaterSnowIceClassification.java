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

package org.esa.snap.idepix.olci;

import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.idepix.core.seaice.SeaIceClassification;
import org.esa.snap.idepix.core.seaice.SeaIceClassifier;

public class WaterSnowIceClassification {

    private static final double MAX_LON = 360.0;
    private static final double MIN_LON = 0.0;
    private static final double MAX_LAT = 180.0;
    private static final double MIN_LAT = 0.0;
    private static final double SEA_ICE_CLIM_THRESHOLD = 10.0;

    private final SeaIceClassifier seaIceClimatology;

    public WaterSnowIceClassification() {
        this(null);
    }

    public WaterSnowIceClassification(SeaIceClassifier seaIceClimatology) {
        this.seaIceClimatology = seaIceClimatology;
    }

    public boolean classify(GeoPos pos, boolean isCloud, double nnOutput) {
        boolean isSeaIceClassified = false;
        if (seaIceClimatology != null) {
            isSeaIceClassified = isPixelClassifiedAsSeaIce(pos);
        }

        if (isSeaIceClassified && !isCloud) {
            return spectralSnowIceTest();
        } else {
            return IdepixOlciCloudNNInterpreter.isSnowIce(nnOutput);
        }
    }

    private boolean spectralSnowIceTest() {
        // TODO
        double ndwi = 0; // (b560 – b885)/(b560+b885)

        return false;
    }

    private boolean isPixelClassifiedAsSeaIce(GeoPos geoPos) {
        // check given pixel, but also neighbour cell from 1x1 deg sea ice climatology...
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                // for sea ice climatology indices, we need to shift lat/lon onto [0,180]/[0,360]...
                double lon = geoPos.lon + 180.0 + x * 1.0;
                double lat = 90.0 - geoPos.lat + y * 1.0;
                lon = Math.min(Math.max(lon, MIN_LON), MAX_LON);
                lat = Math.min(Math.max(lat, MIN_LAT), MAX_LAT);
                final SeaIceClassification classification = seaIceClimatology.getClassification(lat, lon);
                if (classification.max >= SEA_ICE_CLIM_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }
}
