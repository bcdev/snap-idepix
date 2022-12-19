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
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.idepix.core.seaice.SeaIceClassification;
import org.esa.snap.idepix.core.seaice.SeaIceClassifier;

import java.util.Arrays;
import java.util.OptionalDouble;

public class WaterSnowIceClassification {

    private static final double SEA_ICE_CLIM_THRESHOLD = 10.0;
    private static final int REFL01_400 = 0;
    private static final int REFL02_412 = 1;
    private static final int REFL03_442 = 2;
    private static final int REFL04_490 = 3;
    private static final int REFL05_510 = 4;
    public static final int REFL06_560 = 5;
    private static final int REFL18_885 = 17;
    private static final int REFL20_940 = 19;
    private static final int REFL21_1020 = 20;

    private static final double[] OLCI_WAVELENGTHS = new double[]{400.0, 412.5, 442.5, 490.0, 510.0, 560.0};
    // further wavelengths not needed: 620.0, 665.0, 673.75, 681.25, 708.75, 753.75, 761.25, 764.375, 767.5, 778.75, 865.0, 885.0, 900.0, 940.0, 1020.0};

    private static final double CURVATURE_THRESHOLD = -7.5e-5;

    private final SeaIceClassifier seaIceClimatology;
    private final double[][] wvlDiffPairs;
    private final double cosRotateAngle;
    private final double sinRotateAngle;

    public WaterSnowIceClassification() {
        this(null);
    }

    public WaterSnowIceClassification(SeaIceClassifier seaIceClimatology) {
        this.seaIceClimatology = seaIceClimatology;
        wvlDiffPairs = createDiffPairs(OLCI_WAVELENGTHS);
        double rotateAngle = Math.toRadians(-45.0);
        cosRotateAngle = Math.cos(rotateAngle);
        sinRotateAngle = Math.sin(rotateAngle);
    }

    public boolean classify(int x, int y, GeoPos pos, boolean isInvalid, boolean isCloud, double nnOutput, Tile[] olciReflectanceTiles) {
        boolean isSeaIceClassified = false;
        if (seaIceClimatology != null) {
            isSeaIceClassified = true;
//            isSeaIceClassified = isPixelClassifiedAsSeaIce(pos);
        }

        if (isSeaIceClassified && !isCloud) {
            return spectralSnowIceTest(x, y, olciReflectanceTiles, isInvalid);
        } else {
            return IdepixOlciCloudNNInterpreter.isSnowIce(nnOutput);
        }
    }

    private boolean spectralSnowIceTest(int x, int y, Tile[] olciReflectanceTiles, boolean isInvalid) {
        double b400 = olciReflectanceTiles[REFL01_400].getSampleDouble(x, y);
        double b412 = olciReflectanceTiles[REFL02_412].getSampleDouble(x, y);
        double b442 = olciReflectanceTiles[REFL03_442].getSampleDouble(x, y);
        double b490 = olciReflectanceTiles[REFL04_490].getSampleDouble(x, y);
        double b510 = olciReflectanceTiles[REFL05_510].getSampleDouble(x, y);
        double b560 = olciReflectanceTiles[REFL06_560].getSampleDouble(x, y);
        double b885 = olciReflectanceTiles[REFL18_885].getSampleDouble(x, y);
        double b940 = olciReflectanceTiles[REFL20_940].getSampleDouble(x, y);
        double b1020 = olciReflectanceTiles[REFL21_1020].getSampleDouble(x, y);
        double ndwi = (b560 - b885) / (b560 + b885);
        double ndsi = (b560 - b1020) / (b560 + b1020);
        double[] refToaB1B6 = new double[]{b400, b412, b442, b490, b510, b560};
        boolean isWater = !isInvalid & ndwi > 0;
        if(!isWater) {
            return false;
        }
        // Values empirically taken from plot
        boolean isIce = ndwi < (0.1 - 0.56) / (0.2 - 0.74) * (ndsi - 0.74) + 0.56;
        boolean isMixedTidal = isMixedTidal(refToaB1B6);
        return curvatureTest(b940, b1020, ndwi, ndsi, isWater, isIce, isMixedTidal);
    }

    private boolean curvatureTest(double b940, double b1020, double ndwi, double ndsi, boolean isWater, boolean isIce, boolean isMixedTidal) {
        boolean result = false;
        if (isIce && isWater) {
            double ndsiRotated = ndsi * cosRotateAngle - ndwi * sinRotateAngle;
            double ndwiRotated = ndsi * sinRotateAngle + ndwi * cosRotateAngle;
            // Values empirically taken from plot
            double minBound = 0.31120702820971125;
            double maxBound = 0.7563567684961906;

            double relDifB1020_B940 = (b1020 - b940) / b1020;
            if (ndsiRotated < minBound) {
                result = !isMixedTidal;
            } else if (ndsiRotated > maxBound) {
                result = relDifB1020_B940 < 0;
            } else {
                double yOut = 2.06475934 * Math.pow(ndsiRotated, 2) + -1.80010554 * ndsiRotated + 0.14796041;
                boolean tempId = ndwiRotated < yOut;
                if (tempId) {
                    result = relDifB1020_B940 < 0;
                } else {
                    result = !isMixedTidal;
                }
            }
        }
        return result;
    }

    private boolean isMixedTidal(double[] refToaB1B6) {
        OptionalDouble min = Arrays.stream(refToaB1B6).min();
        if (!min.isPresent()) {
            return false;
        }
        double minRefl = min.getAsDouble();
        double[] curvature = new double[4];
        double[][] reflDiffPairs = createDiffPairs(refToaB1B6);
        for (int i = 0; i < curvature.length; i++) {
            double[] wvlDiffPair = wvlDiffPairs[i];
            double[] reflDiffPair = reflDiffPairs[i];
            double s1 = (reflDiffPair[0] / wvlDiffPair[0]) / minRefl;
            double s2 = (reflDiffPair[1] / wvlDiffPair[1]) / minRefl;
            double sdl1 = OLCI_WAVELENGTHS[i] + wvlDiffPair[0] / 2;
            double sdl2 = OLCI_WAVELENGTHS[i + 1] + wvlDiffPair[1] / 2;
            curvature[i] = (s2 - s1) / (sdl1 - sdl2);
        }

        boolean allCurvNegative = Arrays.stream(curvature).allMatch(value -> value < 0);
        boolean isBelowThreshold = Arrays.stream(curvature, 1, curvature.length).anyMatch(value -> value < CURVATURE_THRESHOLD);
        return allCurvNegative && isBelowThreshold;
    }

    private boolean isPixelClassifiedAsSeaIce(GeoPos geoPos) {
        // check given pixel, but also neighbour cell from 1x1 deg sea ice climatology...
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                // for sea ice climatology indices, we need to shift lat/lon onto [0,180]/[0,360]...
                double lon = geoPos.lon + x;
                double lat = geoPos.lat + y;
                final SeaIceClassification classification = seaIceClimatology.getClassification(lat, lon);
                if (classification.getMax() >= SEA_ICE_CLIM_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    private double[][] createDiffPairs(double[] inputValues) {
        if (inputValues.length < 3) {
            throw new IllegalArgumentException("The number of elements of 'inputValues' must be at least 3");
        }

        double[][] diffPairs = new double[inputValues.length - 2][2];
        for (int i = 0; i < diffPairs.length - 1; i++) {
            diffPairs[i][0] = inputValues[i + 1] - inputValues[i];
            diffPairs[i][1] = inputValues[i + 2] - inputValues[i + 1];
        }
        return diffPairs;
    }
}
