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


//import org.esa.s3tbx.idepix.core.CloudShadowFronts;
//import org.esa.s3tbx.idepix.core.IdepixConstants;
//import org.esa.s3tbx.idepix.core.util.Bresenham;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.CloudShadowFronts;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.Bresenham;

import java.awt.*;

/**
 * Specific cloud shadow algorithm for OLCI based on fronts, using cloud top height computation based on
 * OLCI per-pixel atmospheric temperature profile TPG taken from L1b product.
 * <p>
 * Other functionalities are the same as in {@link org.esa.snap.idepix.core.CloudShadowFronts}.
 *
 * @author olafd
 */
class IdepixOlciCloudShadowFronts {

    private static final int MEAN_EARTH_RADIUS = 6372000;

    private final GeoCoding geoCoding;

    private final Tile szaTile;
    private final Tile saaTile;
    private final Tile ozaTile;
    private final Tile oaaTile;
    private final Tile ctpTile;
    private final Tile slpTile;
    private final Tile[] temperatureProfileTPGTiles;
    private final Tile altTile;

    IdepixOlciCloudShadowFronts(GeoCoding geoCoding,
                                Tile szaTile, Tile saaTile,
                                Tile ozaTile, Tile oaaTile,
                                Tile ctpTile, Tile slpTile,
                                Tile[] temperatureProfileTPGTiles,
                                Tile altTile) {
        this.geoCoding = geoCoding;
        this.szaTile = szaTile;
        this.saaTile = saaTile;
        this.ozaTile = ozaTile;
        this.oaaTile = oaaTile;
        this.ctpTile = ctpTile;
        this.slpTile = slpTile;
        this.temperatureProfileTPGTiles = temperatureProfileTPGTiles;
        this.altTile = altTile;
    }

    void computeCloudShadow(Tile sourceTile, Tile targetTile) {
        final Rectangle targetRectangle = targetTile.getRectangle();
        final int h = targetRectangle.height;
        final int w = targetRectangle.width;
        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final boolean[][] cloudShadow = new boolean[h][w];

        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (isCloudFree(sourceTile, x, y)) {
                    if (isCloudShadow(sourceTile, targetTile, x, y)) {
                        setCloudShadow(targetTile, x, y);
                        cloudShadow[y - y0][x - x0] = true;
                    } else {
                        cloudShadow[y - y0][x - x0] = false;
                    }
                }
            }
        }
        // first 'post-correction': fill gaps surrounded by other cloud or cloud shadow pixels
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (isCloudFree(sourceTile, x, y)) {
                    if (isSurroundedByCloud(sourceTile, x, y) || isPixelSurroundedByCloudShadow(targetRectangle, x, y, cloudShadow)) {
                        setCloudShadow(targetTile, x, y);
                    }
                }
            }
        }
        // second post-correction, called 'belt'
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (isCloudFree(sourceTile, x, y)) {
                    // flag a pixel as cloud shadow if neighbour pixel is shadow
                    for (int j = y - 1; j <= y + 1; j++) {
                        for (int i = x - 1; i <= x + 1; i++) {
                            if (targetRectangle.contains(i, j) && cloudShadow[j - y0][i - x0]) {
                                setCloudShadow(targetTile, x, y);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    ///////////////////// end of public ///////////////////////////////////////////////////////

    private boolean isCloudForShadow(Tile sourceTile, Tile targetTile, int x, int y) {
        if (!targetTile.getRectangle().contains(x, y)) {
            return sourceTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        } else {
            return targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        }
    }

    private boolean isCloudFree(Tile sourceTile, int x, int y) {
        return !sourceTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
    }

    private boolean isSurroundedByCloud(Tile sourceTile, int x, int y) {
        final Rectangle rectangle = sourceTile.getRectangle();
        int count = 0;
        for (int j = y - 1; j <= y + 1; j++) {
            for (int i = x - 1; i <= x + 1; i++) {
                if (rectangle.contains(i, j) && sourceTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD)) {
                    count++;
                    if (count >= 6) {  // at least 6 cloudy pixels within a 3x3 box
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setCloudShadow(Tile targetTile, int x, int y) {
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, true);
    }

    private boolean isPixelSurroundedByCloudShadow(Rectangle targetRectangle, int x, int y, boolean[][] cloudShadow) {
        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;

        int count = 0;
        for (int j = y - 1; j <= y + 1; j++) {
            for (int i = x - 1; i <= x + 1; i++) {
                if (targetRectangle.contains(i, j)) {
                    if (cloudShadow[j - y0][i - x0]) {
                        count++;
                        if (count >= 6) {  // at least 6 cloudy pixels within a 3x3 box
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isCloudShadow(Tile sourceTile, Tile targetTile, int x, int y) {

        final Rectangle sourceRectangle = sourceTile.getRectangle();
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
//        final double saaRad = Math.toRadians(saa);

        PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);

        final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);
        double tanSza = Math.tan(Math.toRadians(90.0 - sza));
        final double cloudHeightMax = 12_000;
        final double cloudDistanceMax = cloudHeightMax / tanSza;

        final double saaApparent = IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa, geoPos.getLat());
        final double saaRadApparent = Math.toRadians(saaApparent);


        GeoPos endGeoPoint = CloudShadowFronts.lineWithAngle(geoPos, cloudDistanceMax, saaRadApparent + Math.PI);
        PixelPos endPixPoint = geoCoding.getPixelPos(endGeoPoint, null);
        if (endPixPoint.x == -1 || endPixPoint.y == -1) {
            return false;
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
                if (isCloudForShadow(sourceTile, targetTile, xCurrent, yCurrent)) {
                    pixelPos.setLocation(xCurrent + 0.5f, yCurrent + 0.5f);
                    geoCoding.getGeoPos(pixelPos, geoPosCurrent);
                    final double cloudSearchHeight = (computeDistance(geoPos, geoPosCurrent) * tanSza) + alt;
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

    private double computeDistance(GeoPos geoPos1, GeoPos geoPos2) {
        final double lon1 = geoPos1.getLon();
        final double lon2 = geoPos2.getLon();
        final double lat1 = geoPos1.getLat();
        final double lat2 = geoPos2.getLat();

        final double cosLat1 = Math.cos(MathUtils.DTOR * lat1);
        final double cosLat2 = Math.cos(MathUtils.DTOR * lat2);
        final double sinLat1 = Math.sin(MathUtils.DTOR * lat1);
        final double sinLat2 = Math.sin(MathUtils.DTOR * lat2);

        final double delta = MathUtils.DTOR * (lon2 - lon1);
        final double cosDelta = Math.cos(delta);
        final double sinDelta = Math.sin(delta);

        final double a = cosLat2 * sinDelta;
        final double b = cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDelta;
        final double y = Math.sqrt(a * a + b * b);
        final double x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDelta;

        return Math.atan2(y, x) * MEAN_EARTH_RADIUS;
    }
}
