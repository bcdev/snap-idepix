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
    private final double cloudHeightMax;

    IdepixOlciCloudShadowFronts(GeoCoding geoCoding,
                                Tile szaTile, Tile saaTile,
                                Tile ozaTile, Tile oaaTile,
                                Tile ctpTile, Tile slpTile,
                                Tile[] temperatureProfileTPGTiles,
                                Tile altTile,
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
                    isCloudShadow[x - x0][y - y0] = getCloudShadow(sourceFlagTile, targetTile, x, y);
                    if (isCloudShadow[x - x0][y - y0]) {
                        setCloudShadow(targetTile, x, y);
                    }
                }
            }
        }
        // first 'post-correction': fill gaps surrounded by other cloud or cloud shadow pixels
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                if (isCloudFree(sourceFlagTile, x, y)) {
                    final boolean pixelSurroundedByClouds = isSurroundedByCloud(sourceFlagTile, x, y);
                    final boolean pixelSurroundedByCloudShadow =
                            isPixelSurroundedByCloudShadow(targetRectangle, x, y, isCloudShadow);

                    if (pixelSurroundedByClouds || pixelSurroundedByCloudShadow) {
                        setCloudShadow(targetTile, x, y);
                    }
                }
            }
        }
        // second post-correction, called 'belt' (why??): flag a pixel as cloud shadow if neighbour pixel is shadow
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (isCloudFree(sourceFlagTile, x, y)) {
                    performCloudShadowBeltCorrection(targetTile, x, y, isCloudShadow);
                }
            }
        }
    }

    ///////////////////// end of public ///////////////////////////////////////////////////////

    private static boolean isPixelSurrounded(int x, int y, Tile sourceFlagTile) {
        // check if pixel is surrounded by other pixels flagged as 'pixelFlag'
        int surroundingPixelCount = 0;
        Rectangle rectangle = sourceFlagTile.getRectangle();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (rectangle.contains(i, j) && sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD)) {
                    surroundingPixelCount++;
                }
            }
        }
        return (surroundingPixelCount * 1.0 / 9 >= 0.7);  // at least 6 pixel in a 3x3 box
    }

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
        return !sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);
    }

    private boolean isSurroundedByCloud(Tile sourceFlagTile, int x, int y) {
        return isPixelSurrounded(x, y, sourceFlagTile);
    }

    private void setCloudShadow(Tile targetTile, int x, int y) {
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, true);
    }

    private boolean isPixelSurroundedByCloudShadow(Rectangle targetRectangle, int x, int y, boolean[][] isCloudShadow) {
        // check if pixel is surrounded by other cloud shadow pixels
        int surroundingPixelCount = 0;
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (targetRectangle.contains(i, j)) {
                    if (isCloudShadow[i - targetRectangle.x][j - targetRectangle.y]) {
                        surroundingPixelCount++;
                    }
                }
            }
        }
        return surroundingPixelCount * 1.0 / 9 >= 0.7; // at least 6 pixel in a 3x3 box
    }

    private void performCloudShadowBeltCorrection(Tile targetTile, int x, int y, boolean[][] isCloudShadow) {
        // flag a pixel as cloud shadow if neighbour pixel is shadow
        final Rectangle targetRectangle = targetTile.getRectangle();
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (targetRectangle.contains(i, j) && isCloudShadow[i - targetRectangle.x][j - targetRectangle.y]) {
                    setCloudShadow(targetTile, x, y);
                    break;
                }
            }
        }
    }

    private double checkDistanceInDirection(double saaApparent, double aNorth, int x, int y,
                                            float height, float width, GeoPos geoPos) {

        double buffer = 2;
        //angles in the observation plane, based on pixel coordinates
        double a = -Math.toDegrees(Math.atan((width - x) / (0. - y)));
        double b = -Math.toDegrees(Math.atan((width - x) / (height - y))) + 180.;
        double c = -Math.toDegrees(Math.atan((0. - x) / (height - y))) + 180.;
        double d = 360. - Math.toDegrees(Math.atan((0. - x) / (0. - y)));

        //converting saaApparent into angle in observation plane, against angle towards North
        saaApparent = saaApparent - aNorth;
        //alpha: angle of slope
        double alpha = Math.toRadians(saaApparent - 90.);
        double xnew = 0;
        double ynew = 0;
        if (saaApparent > d || saaApparent < a) {
            ynew = 0 + buffer;
            xnew = (ynew - y) / Math.tan(alpha) + x;
        } else if (saaApparent > a && saaApparent < b) {
            xnew = width - buffer;
            ynew = Math.tan(alpha) * (xnew - x) + y;
        } else if (saaApparent > b && saaApparent < c) {
            ynew = height - buffer;
            xnew = (ynew - y) / Math.tan(alpha) + x;
        } else if (saaApparent > c && saaApparent < d) {
            xnew = 0 + buffer;
            ynew = Math.tan(alpha) * (xnew - x) + y;
        }

        PixelPos pixelpPos2 = new PixelPos((Math.floor(xnew)) + 0.5f, Math.floor(ynew) + 0.5f);
        final GeoPos geoPos2 = geoCoding.getGeoPos(pixelpPos2, null);
        return IdepixUtils.computeDistanceOnEarth(geoPos2, geoPos);
    }

    private double computeNorthDirection(int x, int y, Tile sourceTile) {
        final Rectangle sourceRectangle = sourceTile.getRectangle();
        final int w = sourceRectangle.width;
        double alpha = 43.60281897270362; //constant angle between AP, BP with P(x,y), A(x-4, y-10), B(x+4,y-10), in pixel coordinates

        if (x < 4) x = 4;
        if (x > w - 4) x = w;
        if (y < 10) y = 10;

        PixelPos pixelPosP = new PixelPos(x + 0.5f, y + 0.5f);
        PixelPos pixelPosA = new PixelPos(x + 0.5f - 4.f, y + 0.5f - 10.f);
        PixelPos pixelPosB = new PixelPos(x + 0.5f + 4.f, y + 0.5f - 10.f);

        final GeoPos geoPosP = geoCoding.getGeoPos(pixelPosP, null);
        final GeoPos geoPosA = geoCoding.getGeoPos(pixelPosA, null);
        final GeoPos geoPosB = geoCoding.getGeoPos(pixelPosB, null);

        return alpha * (geoPosA.getLon() - geoPosB.getLon()) / (geoPosP.getLon() - geoPosB.getLon());
    }

    private boolean getCloudShadow(Tile sourceFlagTile, Tile targetTile, int x, int y) {

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


        double tanSza = Math.tan(Math.toRadians(90.0 - sza));

        final double saaApparent = IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa);
        final double saaRadApparent = Math.toRadians(saaApparent);

        PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
        final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);

        final double aNorth = computeNorthDirection(x, y, sourceFlagTile);
        double cloudDistanceMax = checkDistanceInDirection(saaApparent, aNorth, x, y, sourceFlagTile.getHeight(),
                sourceFlagTile.getWidth(), geoPos);

        //cloudHeightMax as function of center pixel Lat.
        double cloudDistanceMax2 = cloudHeightMax / tanSza;
        if (cloudDistanceMax > cloudDistanceMax2) cloudDistanceMax = cloudDistanceMax2;
        if (cloudDistanceMax*tanSza < 300.){
            //lower boundary of clouds (300m minimum); if lower, no calculation of shadow from that position.
            return false;
        }


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
