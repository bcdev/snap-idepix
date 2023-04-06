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


import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.idepix.core.CloudShadowFronts;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.Bresenham;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.Rectangle;

/**
 * Specific cloud shadow algorithm for OLCI based on fronts, using cloud top height computation based on
 * OLCI per-pixel atmospheric temperature profile TPG taken from L1b product.
 * <p>
 * Other functionalities are the same as in {@link org.esa.snap.idepix.core.CloudShadowFronts}.
 *
 * @author olafd
 */
class IdepixOlciCloudShadowFronts {

    private final GeoCoding geoCoding;

    private final Tile szaTile;
    private final Tile saaTile;
    private final Tile ozaTile;
    private final Tile oaaTile;
    private final Tile ctpTile;
    private final Tile slpTile;
    private final Tile[] temperatureProfileTPGTiles;
    private final Tile altTile;
    private final Tile distTile;
    private final double cloudHeightMax;

    IdepixOlciCloudShadowFronts(GeoCoding geoCoding,
                                Tile szaTile, Tile saaTile,
                                Tile ozaTile, Tile oaaTile,
                                Tile ctpTile, Tile slpTile,
                                Tile[] temperatureProfileTPGTiles,
                                Tile altTile, Tile distTile,
                                double cloudHeightMax) {
        this.geoCoding = geoCoding;
        this.szaTile = szaTile;
        this.saaTile = saaTile;
        this.ozaTile = ozaTile;
        this.oaaTile = oaaTile;
        this.ctpTile = ctpTile;
        this.slpTile = slpTile;
        this.temperatureProfileTPGTiles = temperatureProfileTPGTiles;
        this.altTile = altTile;
        this.distTile = distTile;
        this.cloudHeightMax = cloudHeightMax;
    }

    void computeCloudShadow(Tile sourceFlagTile, Tile targetTile) {
        final Rectangle targetRectangle = targetTile.getRectangle();
        final int h = targetRectangle.height;
        final int w = targetRectangle.width;
        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        boolean[][] isCloudShadow = new boolean[w][h];
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (isCloudFree(sourceFlagTile, x, y) && isNotInvalid(sourceFlagTile, x, y)) {
                    isCloudShadow[x - x0][y - y0] = getCloudShadow(sourceFlagTile, targetTile, x, y, x0, y0);
                    if (isCloudShadow[x - x0][y - y0]) {
                        setCloudShadow(targetTile, x, y);
                    }
                }
            }
        }
    }

    ///////////////////// end of public ///////////////////////////////////////////////////////

    private boolean isCloudForShadow(Tile sourceFlagTile, Tile targetTile, int x, int y) {
        if (!targetTile.getRectangle().contains(x, y)) {
            return sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        } else {
            return targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        }
    }

    private boolean isCloudFree(Tile sourceFlagTile, int x, int y) {
        return !sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
    }

    private boolean isNotInvalid(Tile sourceFlagTile, int x, int y) {
        // todo rename
        return !sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);
    }

    private void setCloudShadow(Tile targetTile, int x, int y) {
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, true);
    }

    private boolean getCloudShadow(Tile sourceFlagTile, Tile targetTile, int x, int y, int x0, int y0) {

        final Rectangle sourceRectangle = sourceFlagTile.getRectangle();
        final double sza = szaTile.getSampleDouble(x, y);
        final double saa = saaTile.getSampleDouble(x, y);
        final double oza = ozaTile.getSampleDouble(x, y);
        final double oaa = oaaTile.getSampleDouble(x, y);
        double alt = 0;
        if (altTile != null) {
            alt = altTile.getSampleDouble(x, y);
            if (alt < 0) {
                alt = 0; // do NOT use bathimetry
            }
        }

        PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);

        final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);
        double tanSza = Math.tan(Math.toRadians(90.0 - sza));

        final double saaApparent = IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa);
        final double saaRadApparent = Math.toRadians(saaApparent);

        double cloudDistanceMax = cloudHeightMax / tanSza;


        GeoPos endGeoPoint = CloudShadowFronts.lineWithAngle(geoPos, cloudDistanceMax, saaRadApparent + Math.PI);
        PixelPos endPixPoint = geoCoding.getPixelPos(endGeoPoint, null);
        if (!endPixPoint.isValid()) {
            double cloudDistanceMin = 300. / tanSza;
            //compute distance with scene intersection, approximation
            final int width = sourceFlagTile.getWidth();
            final int height = sourceFlagTile.getHeight();
            double beta_North_rad = 0.;
            if (x - x0 > 4 && x - x0 < width - 4) {
                //estimation of North direction against -y-axis (y increases downwards)
                if (saa > 90. && saa < 270.) { //approximation for a decision on the pixel grid, Sun in South
                    if (y - y0 > 10) {
                        PixelPos pixelPos1 = new PixelPos(x + 0.5f - 4.f, y + 0.5f - 10.f);
                        PixelPos pixelPos2 = new PixelPos(x + 0.5f + 4.f, y + 0.5f - 10.f);
                        GeoPos geoPos1 = geoCoding.getGeoPos(pixelPos1, null);
                        GeoPos geoPos2 = geoCoding.getGeoPos(pixelPos2, null);
                        double XN = (geoPos.lon - geoPos2.lon) / (geoPos1.lon - geoPos2.lon) * (-8.) + 4.;
                        beta_North_rad = Math.atan(XN / 10.);
                    } else {
                        return false;
                    }
                } else if (saa < 90. || saa > 270.) {//approximation for a decision on the pixel grid, Sun in North
                    if (y - y0 < height - 10) {
                        PixelPos pixelPos1 = new PixelPos(x + 0.5f - 4.f, y + 0.5f + 10.f);
                        PixelPos pixelPos2 = new PixelPos(x + 0.5f + 4.f, y + 0.5f + 10.f);
                        GeoPos geoPos1 = geoCoding.getGeoPos(pixelPos1, null);
                        GeoPos geoPos2 = geoCoding.getGeoPos(pixelPos2, null);
                        double XN = (geoPos.lon - geoPos2.lon) / (geoPos1.lon - geoPos2.lon) * (-8.) + 4.;
                        beta_North_rad = Math.atan(-XN / 10.);
                    } else {
                        return false;
                    }
                }

                double resolution = 300.;
                double X_max = x + cloudDistanceMax / resolution * Math.sin(saaRadApparent + beta_North_rad);
                double Y_max = y + cloudDistanceMax / resolution * (-1.) * Math.cos(saaRadApparent + beta_North_rad);

                double x_test;
                double y_test;
                double newDistance = 0.0;
                if (X_max < 0) { //at x_test=0
                    x_test = 0.;
                    y_test = (Y_max - (y - y0)) / (X_max - (x - x0)) * (x_test - (x - x0)) + (y - y0);
                    if (y_test >= 0 && y_test < height)
                        newDistance = Math.sqrt(Math.pow((y_test - (y - y0)), 2.) + Math.pow((x_test - (x - x0)), 2.));
                }
                if (X_max > width) {
                    x_test = width - 1.;
                    y_test = (Y_max - (y - y0)) / (X_max - (x - x0)) * (x_test - (x - x0)) + (y - y0);
                    if (y_test >= 0 && y_test < height)
                        newDistance = Math.sqrt(Math.pow((y_test - (y - y0)), 2.) + Math.pow((x_test - (x - x0)), 2.));
                }
                if (Y_max < 0) {
                    y_test = 0.;
                    x_test = (X_max - (x - x0)) / (Y_max - (y - x0)) * (y_test - (y - x0)) + (x - x0);
                    if (x_test >= 0 && x_test < width)
                        newDistance = Math.sqrt(Math.pow((y_test - (y - y0)), 2.) + Math.pow((x_test - (x - x0)), 2.));
                }
                if (Y_max > height) {
                    y_test = height - 1.;
                    x_test = (X_max - (x - x0)) / (Y_max - (y - y0)) * (y_test - (y - y0)) + (x - x0);
                    if (x_test >= 0 && x_test < width)
                        newDistance = Math.sqrt(Math.pow((y_test - (y - y0)), 2.) + Math.pow((x_test - (x - x0)), 2.));
                }

                distTile.setSample(x, y, newDistance * resolution);

                if (newDistance * resolution < cloudDistanceMin) {
                    return false;
                }else {
                    endGeoPoint = CloudShadowFronts.lineWithAngle(geoPos, newDistance, saaRadApparent + Math.PI);
                    endPixPoint = geoCoding.getPixelPos(endGeoPoint, null);
                    return endPixPoint.isValid();
                }
            } else {
                return false;
            }
        }

        final int endPointX = (int) Math.round(endPixPoint.x);
        final int endPointY = (int) Math.round(endPixPoint.y);
        java.util.List<PixelPos> pathPixels = Bresenham.getPathPixels(x, y, endPointX, endPointY, sourceRectangle);

        double[] temperature = new double[temperatureProfileTPGTiles.length];

        GeoPos geoPosCurrent = new GeoPos();
        for (PixelPos pathPixel : pathPixels) {

            final int xCurrent = (int) pathPixel.getX();
            final int yCurrent = (int) pathPixel.getY();

            if (sourceRectangle.contains(xCurrent, yCurrent)) {
                if (isCloudForShadow(sourceFlagTile, targetTile, xCurrent, yCurrent)) {
                    pixelPos.setLocation(xCurrent + 0.5f, yCurrent + 0.5f);
                    geoCoding.getGeoPos(pixelPos, geoPosCurrent);
                    final double cloudSearchHeight = (IdepixUtils.computeDistanceOnEarth(geoPos, geoPosCurrent) * tanSza) + alt;
                    final float ctp = ctpTile.getSampleFloat(xCurrent, yCurrent);
                    final float slp = slpTile.getSampleFloat(xCurrent, yCurrent);
                    for (int i = 0; i < temperature.length; i++) {
                        temperature[i] = temperatureProfileTPGTiles[i].getSampleDouble(xCurrent, yCurrent);
                    }
                    final float cloudHeight = (float) IdepixOlciUtils.getRefinedHeightFromCtp(ctp, slp, temperature);
                    if (cloudSearchHeight <= cloudHeight + 300) {
                        // cloud thickness should also be at least 300m (OD, 2012/08/02)
                        float cloudBase = cloudHeight - 300.0f;
                        // cloud base should be at least at 300m
                        cloudBase = (float) Math.max(300.0, cloudBase);
                        if (cloudSearchHeight >= cloudBase - 300) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

}
