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
    private Tile distTile;
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
                if (isCloudFree(sourceFlagTile, x, y)) {
                    isCloudShadow[x - x0][y - y0] = getCloudShadow(sourceFlagTile, targetTile, x, y, x0, y0);
                    if (isCloudShadow[x - x0][y - y0]) {
                        setCloudShadow(targetTile, x, y);
                    }
                }
            }
        }
        // first 'post-correction': fill gaps surrounded by other cloud or cloud shadow pixels
//        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
//            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
//                if (isCloudFree(sourceFlagTile, x, y)) {
//                    final boolean pixelSurroundedByClouds = isSurroundedByCloud(sourceFlagTile, x, y);
//                    final boolean pixelSurroundedByCloudShadow =
//                            isPixelSurroundedByCloudShadow(targetRectangle, x, y, isCloudShadow);
//
//                    if (pixelSurroundedByClouds || pixelSurroundedByCloudShadow) {
//                        setCloudShadow(targetTile, x, y);
//                    }
//                }
//            }
//        }
//        // second post-correction, called 'belt' (why??): flag a pixel as cloud shadow if neighbour pixel is shadow
//        for (int y = y0; y < y0 + h; y++) {
//            for (int x = x0; x < x0 + w; x++) {
//                if (isCloudFree(sourceFlagTile, x, y)) {
//                    performCloudShadowBeltCorrection(targetTile, x, y, isCloudShadow);
//                }
//            }
//        }
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

    private double checkDistanceInDirection( double saaApparent, double aNorth, double sza, int x_, int y_, int x0, int y0,
                                             float Height, float Width, GeoPos geoPos){

        double buffer =2;
        //angles in the observation plane, based on pixel coordinates
        int y = y_ - y0;
        int x = x_ - x0;

        if (y==0) y+=1;
        if (y==Height) y-=1;
        if (x==0) x+=1;
        if (x==Width) x-=1;
        double a = -Math.toDegrees(Math.atan((Width - x) / (0. - y)));
        double b=-Math.toDegrees(Math.atan((Width - x) / (Height - y))) + 180.;
        double c = -Math.toDegrees(Math.atan((0. - x) / (Height - y))) + 180.;
        double d = 360. -Math.toDegrees(Math.atan((0. - x) / (0. - y)));

        //converting saaApparent into angle in observation plane, against angle towards North
        if (aNorth>360.) aNorth = aNorth-360.;
        saaApparent = saaApparent - aNorth;
        //alpha: angle of slope
        double alpha = Math.toRadians(saaApparent);
        double xnew=0;
        double ynew=0;
        if (saaApparent > d || saaApparent < a){
            ynew=0 +buffer;
            xnew = -1.*(ynew-y) * Math.tan(alpha) + x;
        }
        else if(saaApparent> a && saaApparent <b){
            xnew=Width-buffer;
            ynew = -1.*(xnew-x)/Math.tan(alpha) + y;
        }
        else if(saaApparent>b && saaApparent<c){
            ynew=Height-buffer;
            xnew = -1.*(ynew-y) * Math.tan(alpha) + x;
        }
        else if(saaApparent> c && saaApparent <d){
            xnew=0 +buffer;
            ynew = -1.*(xnew-x)/Math.tan(alpha) + y;
        }
//        if (xnew < 0) xnew=0.;
//        if(ynew<0) ynew=0.;
        xnew += x0;
        ynew += y0;

        PixelPos pixelPosBoundary = new PixelPos( (Math.floor(xnew)) + 0.5f, Math.floor(ynew) + 0.5f);

        final GeoPos geoPosBoundary = geoCoding.getGeoPos(pixelPosBoundary, null);

        return computeDistance(geoPosBoundary, geoPos);
    }

    private double computeNorthDirection(int x, int y, Tile sourceTile){
        final Rectangle sourceRectangle = sourceTile.getRectangle();
        final int h = sourceRectangle.height;
        final int w = sourceRectangle.width;
        double alpha = 43.60281897270362; //constant angle between AP, BP with P(x,y), A(x-4, y-10), B(x+4,y-10), in pixel coordinates

        if (x<4) x =4;
        if (x>w-4) x =w;
        if (y<10) y = 10;

        PixelPos pixelPosP = new PixelPos(x + 0.5f, y + 0.5f);
        PixelPos pixelPosA = new PixelPos(x + 0.5f -4.f, y + 0.5f -10.f);
        PixelPos pixelPosB = new PixelPos(x + 0.5f +4.f, y + 0.5f -10.f);

        final GeoPos geoPosP = geoCoding.getGeoPos(pixelPosP, null);
        final GeoPos geoPosA = geoCoding.getGeoPos(pixelPosA, null);
        final GeoPos geoPosB = geoCoding.getGeoPos(pixelPosB, null);

        return alpha * (geoPosA.getLon()-geoPosB.getLon())/(geoPosP.getLon()-geoPosB.getLon());
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
//        final double saaRad = Math.toRadians(saa);

        PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);

        final GeoPos geoPos = geoCoding.getGeoPos(pixelPos, null);
        double tanSza = Math.tan(Math.toRadians(90.0 - sza));

        final double saaApparent = IdepixOlciUtils.computeApparentSaa(sza, saa, oza, oaa);
        final double saaRadApparent = Math.toRadians(saaApparent);

//        final double aNorth = computeNorthDirection(x, y, targetTile);

//        double cloudDistanceMax = checkDistanceInDirection(saaApparent, aNorth, sza, x, y, x0, y0, targetTile.getHeight(),
//                targetTile.getWidth(), geoPos);
//
//        if (Double.isNaN(cloudDistanceMax)){
//            cloudDistanceMax = checkDistanceInDirection(saaApparent, aNorth, sza, x, y, x0, y0, targetTile.getHeight(),
//                    sourceFlagTile.getWidth(), geoPos);
//        }
//
//        //cloudHeightMax as function of center pixel Lat.
//        double cloudDistanceMax2 = cloudHeightMax / tanSza;
//        if (cloudDistanceMax > cloudDistanceMax2) cloudDistanceMax = cloudDistanceMax2;
//        if (cloudDistanceMax*tanSza < 300.){
//            //lower boundary of clouds (300m minimum); if lower, no calculation of shadow from that position.
//            return false;
//        }

        double cloudDistanceMax = cloudHeightMax / tanSza;

        //done: at high latitudes and low sun, this can lead to endGeoPoint always outside the scene!
        //done:  That is the reason, why cloud shadow is sometimes not calculated!
        //done: does the saaRadApparent has to be changed with the North direction? No! lineWithAngle works on Lat Lon!

        GeoPos endGeoPoint = CloudShadowFronts.lineWithAngle(geoPos, cloudDistanceMax, saaRadApparent + Math.PI);
        PixelPos endPixPoint = geoCoding.getPixelPos(endGeoPoint, null);
//        if (endPixPoint.x == -1 || endPixPoint.y == -1) {
//            return false;
//        }
        if (!endPixPoint.isValid()) {
            double cloudDistanceMin = 300. / tanSza;
            //compute distance with scene intersection, approximation
            final int width = sourceFlagTile.getWidth();
            final int height = sourceFlagTile.getHeight();
            double beta_North_rad=0.;
            if(x-x0>4 && x-x0<width-4){
                //estimation of North direction against -y-axis (y increases downwards)
                if( saa > 90. && saa <270.){ //approximation for a decision on the pixel grid, Sun in South
                    if(y-y0>10){
                        PixelPos pixelPos1 = new PixelPos(x + 0.5f -4.f, y + 0.5f -10.f);
                        PixelPos pixelPos2 = new PixelPos(x + 0.5f +4.f, y + 0.5f -10.f);
                        GeoPos geoPos1 = geoCoding.getGeoPos(pixelPos1, null);
                        GeoPos geoPos2 = geoCoding.getGeoPos(pixelPos2, null);
                        double XN = (geoPos.lon - geoPos2.lon) / (geoPos1.lon - geoPos2.lon)*(-8.) +4.;
                        beta_North_rad = Math.atan(XN/10.);
                    }
                    else{
                        return false;
                    }
                }
                else if(saa<90. || saa > 270.){//approximation for a decision on the pixel grid, Sun in North
                    if(y-y0<height-10){
                        PixelPos pixelPos1 = new PixelPos(x + 0.5f -4.f, y + 0.5f +10.f);
                        PixelPos pixelPos2 = new PixelPos(x + 0.5f +4.f, y + 0.5f +10.f);
                        GeoPos geoPos1 = geoCoding.getGeoPos(pixelPos1, null);
                        GeoPos geoPos2 = geoCoding.getGeoPos(pixelPos2, null);
                        double XN = (geoPos.lon - geoPos2.lon) / (geoPos1.lon - geoPos2.lon)*(-8.) +4.;
                        beta_North_rad = Math.atan(-XN/10.);
                    }
                    else{
                        return false;
                    }
                }

                double resolution = 300.;
                double X_max = x + cloudDistanceMax/resolution * Math.sin(saaRadApparent+beta_North_rad);
                double Y_max = y + cloudDistanceMax/resolution * (-1.)* Math.cos(saaRadApparent+beta_North_rad);

                double x_test=0.;
                double y_test=0.;
                double newDistance=0.;
                if(X_max <0){ //at x_test=0
                    x_test = 0.;
                    y_test = (Y_max-(y-y0))/(X_max-(x-x0))*(x_test-(x-x0)) + (y-y0);
                    if(y_test>=0 && y_test<height) newDistance = Math.sqrt(Math.pow((y_test-(y-y0)),2.)+Math.pow((x_test-(x-x0)),2.));
                }
                if(X_max> width){
                    x_test = width-1.;
                    y_test = (Y_max-(y-y0))/(X_max-(x-x0))*(x_test-(x-x0)) + (y-y0);
                    if(y_test>=0 && y_test<height) newDistance = Math.sqrt(Math.pow((y_test-(y-y0)),2.)+Math.pow((x_test-(x-x0)),2.));
                }
                if(Y_max < 0){
                    y_test=0.;
                    x_test = (X_max-(x-x0))/(Y_max-(y-x0))*(y_test-(y-x0)) + (x-x0);
                    if(x_test>=0 && x_test<width) newDistance = Math.sqrt(Math.pow((y_test-(y-y0)),2.)+Math.pow((x_test-(x-x0)),2.));
                }
                if(Y_max>height){
                    y_test= height -1.;
                    x_test = (X_max-(x-x0))/(Y_max-(y-y0))*(y_test-(y-y0)) + (x-x0);
                    if(x_test>=0 && x_test<width) newDistance = Math.sqrt(Math.pow((y_test-(y-y0)),2.)+Math.pow((x_test-(x-x0)),2.));
                }

                distTile.setSample(x,y, newDistance*resolution);

                if(newDistance*resolution < cloudDistanceMin) return false;
                else{
                    endGeoPoint = CloudShadowFronts.lineWithAngle(geoPos, newDistance, saaRadApparent + Math.PI);
                    endPixPoint = geoCoding.getPixelPos(endGeoPoint, null);
                    if (!endPixPoint.isValid()) {
                        return false;
                    }
                    else return true;
                }
            }
            else {
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
                    final double cloudSearchHeight = (computeDistance(geoPos, geoPosCurrent) * tanSza) + alt;
                    final float ctp = ctpTile.getSampleFloat(xCurrent, yCurrent);
                    final float slp = slpTile.getSampleFloat(xCurrent, yCurrent);
                    for (int i = 0; i < temperature.length; i++) {
                        temperature[i] = temperatureProfileTPGTiles[i].getSampleDouble(xCurrent, yCurrent);
                    }
                    final float cloudHeight = (float) IdepixOlciUtils.getRefinedHeightFromCtp(ctp, slp, temperature);
//                    final float cloudHeight = (float) IdepixOlciUtils.getHeightFromCtp_MERISStyle(ctp, slp);
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
        final float lon1 = (float) geoPos1.getLon();
        final float lon2 = (float) geoPos2.getLon();
        final float lat1 = (float) geoPos1.getLat();
        final float lat2 = (float) geoPos2.getLat();

        final double cosLat1 = Math.cos(MathUtils.DTOR * lat1);
        final double cosLat2 = Math.cos(MathUtils.DTOR * lat2);
        final double sinLat1 = Math.sin(MathUtils.DTOR * lat1);
        final double sinLat2 = Math.sin(MathUtils.DTOR * lat2);

        final double delta = MathUtils.DTOR * (lon2 - lon1);
        final double cosDelta = Math.cos(delta);
        final double sinDelta = Math.sin(delta);

        final double y = Math.sqrt(Math.pow(cosLat2 * sinDelta, 2) + Math.pow(cosLat1 * sinLat2 - sinLat1 * cosLat2 * cosDelta, 2));
        final double x = sinLat1 * sinLat2 + cosLat1 * cosLat2 * cosDelta;

        final double ad = Math.atan2(y, x);

        return ad * MEAN_EARTH_RADIUS;
    }
}
