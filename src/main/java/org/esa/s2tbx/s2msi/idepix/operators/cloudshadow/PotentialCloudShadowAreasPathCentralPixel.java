package org.esa.s2tbx.s2msi.idepix.operators.cloudshadow;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.math.MathUtils;

import java.awt.*;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.Arrays;

/**
 * todo: add comment
 *
 */
public class PotentialCloudShadowAreasPathCentralPixel {

    private static final double MAXCLOUD_TOP = S2IdepixCloudShadowOp.maxcloudTop;
    private static final int MEAN_EARTH_RADIUS = 6372000;

    private PotentialCloudShadowAreasPathCentralPixel() {
    }

    public static int[][] makedCloudShadowArea(Product sourceProduct, Product targetProduct, Rectangle sourceRectangle,
                                               Rectangle targetRectangle, float[] sourceSunZenith, float[] sourceSunAzimuth,
                                               float[] sourceLatitude, float[] sourceLongitude, float[] sourceAltitude,
                                               int[] flagArray, int[] cloudShadowArray, int[] cloudIDArray,
                                               int[] cloudShadowIDArray, int counterTable) {

        int X0;
        int Y0;


        int X0TargetCenter;
        int Y0TargetCenter;
        int X0SourceCenter;
        int Y0SourceCenter;

        int X0Center;
        int Y0Center;
        int X1Center;
        int Y1Center;

        int productHeight = sourceProduct.getSceneRasterHeight();
        int productWidth = sourceProduct.getSceneRasterWidth();
        int targetHeight = targetRectangle.height;
        int targetWidth = targetRectangle.width;
        int sourceHeight = sourceRectangle.height;
        int sourceWidth = sourceRectangle.width;
        int xSourceLocation = sourceRectangle.x;
        int ySourceLocation = sourceRectangle.y;

        boolean caseKeyBorder;
        caseKeyBorder = !(xSourceLocation >= 0 && ySourceLocation >= 0 &&
                xSourceLocation + sourceWidth < productWidth && ySourceLocation + sourceHeight < productHeight);

        int[][] cloudShadowIdBorderRectangle = new int[counterTable][4];

        int[] path;
        int pathWidth;
        int pathHeight;
        int pathRectangleWidth;
        int pathRectangleHeight;

        int x0TargetProductEquivalent;
        int y0TargetProductEquivalent;
        int x1TargetProductEquivalent;
        int y1TargetProductEquivalent;

        double sunZenith;
        double sunAzimuth;
        double surfaceAltitude;

        double[] pathArray;

        int[] linePointsArray = new int[4];

        int[] indicesPointPathArray = new int[4];
        int[][] indicesPathArray = new int[1][1];
        indicesPathArray[0][0] = 0;
        int x0 = 0;
        int y0 = 0;
        int counter = 0;

        for (int bc = 0; bc < counterTable; bc++) {
            cloudShadowIdBorderRectangle[bc][0] = productWidth + 1;
            cloudShadowIdBorderRectangle[bc][1] = -1;
            cloudShadowIdBorderRectangle[bc][2] = productHeight + 1;
            cloudShadowIdBorderRectangle[bc][3] = -1;
        }

            X0TargetCenter = (int) (targetRectangle.width / 2.);
            Y0TargetCenter = (int) (targetRectangle.width / 2.);
            X0SourceCenter = X0TargetCenter + S2IdepixCloudShadowOp.searchBorderRadius;
            Y0SourceCenter = Y0TargetCenter + S2IdepixCloudShadowOp.searchBorderRadius;

        sunZenith = sourceSunZenith[Y0SourceCenter * sourceRectangle.width + X0SourceCenter] * MathUtils.DTOR;
        sunAzimuth = sourceSunAzimuth[Y0SourceCenter * sourceRectangle.width + X0SourceCenter] * MathUtils.DTOR;
        surfaceAltitude = sourceAltitude[Y0SourceCenter * sourceRectangle.width + X0SourceCenter];

        path = getPotentialMaxCloudShadowPosition(surfaceAltitude, sunZenith, sunAzimuth,
                                                  MAXCLOUD_TOP, X0SourceCenter, Y0SourceCenter, X0TargetCenter, Y0TargetCenter,
                                                  productHeight, productWidth, sourceHeight, sourceWidth, targetHeight, targetWidth,
                                                  S2IdepixCloudShadowOp.spatialResolution, caseKeyBorder);

        X0Center = path[0];
        Y0Center = path[1];
        X1Center = path[2];
        Y1Center = path[3];

        pathWidth = X1Center - X0Center;
        pathHeight = Y1Center - Y0Center;

        if (pathWidth < 0) {
            pathRectangleWidth = pathWidth - 1;
        } else {
            pathRectangleWidth = pathWidth + 1;
        }
        if (pathHeight < 0) {
            pathRectangleHeight = pathHeight - 1;
        } else {
            pathRectangleHeight = pathHeight + 1;
        }


        Arrays.fill(linePointsArray, 0);
        getLinePoints(X0Center, Y0Center, X1Center, Y1Center, sourceHeight, sourceWidth, targetHeight, targetWidth, pathWidth,
                pathHeight, linePointsArray, caseKeyBorder);

        x0TargetProductEquivalent = linePointsArray[0];
        y0TargetProductEquivalent = linePointsArray[1];
        x1TargetProductEquivalent = linePointsArray[2];
        y1TargetProductEquivalent = linePointsArray[3];

        int absPathRectangleWidth = Math.abs(pathRectangleWidth);
        int absPathRectangleHeight = Math.abs(pathRectangleHeight);
        int absPathRectangleLength = absPathRectangleHeight * absPathRectangleWidth;

        if (pathHeight != 0 && pathWidth != 0) {
            RenderedImage lineImage = PathRasterizer.rasterizeLine(x0TargetProductEquivalent, y0TargetProductEquivalent,
                    x1TargetProductEquivalent, y1TargetProductEquivalent, targetProduct);
            Rectangle pathAbsRectangle = new Rectangle(Math.min(x0TargetProductEquivalent, x1TargetProductEquivalent),
                    Math.min(y0TargetProductEquivalent, y1TargetProductEquivalent),
                    absPathRectangleWidth, absPathRectangleHeight);
            Raster pathRaster = lineImage.getData(pathAbsRectangle);
            pathArray = new double[absPathRectangleLength];
            pathRaster.getSamples(Math.min(x0TargetProductEquivalent, x1TargetProductEquivalent),
                    Math.min(y0TargetProductEquivalent, y1TargetProductEquivalent),
                    absPathRectangleWidth, absPathRectangleHeight, 0, pathArray);
            indicesPathArray = new int[absPathRectangleLength][2];
            counter = 0;
            for (int jj = 0; jj < absPathRectangleHeight; jj++) {
                for (int ii = 0; ii < absPathRectangleWidth; ii++) {
                    getIndicesPathArray(X0Center, Y0Center, X1Center, Y1Center, x0, y0, ii, jj,
                            absPathRectangleWidth - 1, absPathRectangleHeight - 1, indicesPointPathArray);
                    if ((int) pathArray[indicesPointPathArray[3] * absPathRectangleWidth + indicesPointPathArray[2]] > 0) {
                        indicesPathArray[counter][0] = indicesPointPathArray[0]; // xPath
                        indicesPathArray[counter][1] = indicesPointPathArray[1]; // yPath
                        counter++;
                    }
                }
            }
        } else if (pathHeight == 0 && pathWidth != 0) {
            indicesPathArray = new int[absPathRectangleWidth][2];
            counter = 0;
            for (int jj = 0; jj < absPathRectangleHeight; jj++) {
                for (int ii = 0; ii < absPathRectangleWidth; ii++) {
                    getIndicesPathArray(X0Center, Y0Center, X1Center, Y1Center, x0, y0, ii, jj,
                            absPathRectangleWidth - 1, absPathRectangleHeight - 1, indicesPointPathArray);
                    indicesPathArray[counter][0] = indicesPointPathArray[0]; // xPath
                    indicesPathArray[counter][1] = indicesPointPathArray[1]; // yPath
                    counter++;
                }
            }
        } else if (pathHeight != 0) {
            indicesPathArray = new int[absPathRectangleHeight][2];
            counter = 0;
            for (int jj = 0; jj < absPathRectangleHeight; jj++) {
                for (int ii = 0; ii < absPathRectangleWidth; ii++) {
                    getIndicesPathArray(X0Center, Y0Center, X1Center, Y1Center, x0, y0, ii, jj,
                            absPathRectangleWidth - 1, absPathRectangleHeight - 1, indicesPointPathArray);
                    indicesPathArray[counter][0] = indicesPointPathArray[0]; // xPath
                    indicesPathArray[counter][1] = indicesPointPathArray[1]; // yPath
                    counter++;
                }
            }
        }

        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {

                X0 = x; // position in the source array
                Y0 = y;


                if (flagArray[Y0 * sourceRectangle.width + X0] < PreparationMaskBand.CLOUD_FLAG) {
                    identifyPotentialCloudShadow(sourceRectangle, sourceLatitude, sourceLongitude,
                            sourceAltitude, flagArray, cloudShadowArray, X0, Y0,
                            sunZenith, cloudIDArray, cloudShadowIDArray, cloudShadowIdBorderRectangle,
                            indicesPathArray, counter);
                }
                //todo one- or two-pixel wide cloud shadow between clouds  (for all satellites)  cause -cleft between cloud
            }
        }
        return cloudShadowIdBorderRectangle;
    }


    private static void identifyPotentialCloudShadow(Rectangle sourceRectangle, float[] sourceLatitude,
                                              float[] sourceLongitude, float[] sourceAltitude,
                                              int[] flagArray, int[] cloudShadowArray,
                                              int x0, int y0, double sunZenith,
                                              int[] cloudIDArray, int[] cloudShadowIDArray,
                                              int[][] cloudShadowIdBorderRectangle,
                                              int[][] indicesPathArray, int counter) {

        double[] cloudExtent;
        double cloudBase;
        double cloudTop;
        double dist;
        double minAltitude;
        double cloudSearchPointHeight;
        double[] distAltArray;

        int minX0;
        int maxX0;
        int minY0;
        int maxY0;
        int temp;


        int xPath;
        int yPath;


        for (int n = 0; n < counter; n++) {

            xPath = x0 + indicesPathArray[n][0];
            yPath = y0 + indicesPathArray[n][1];

            if (yPath < sourceRectangle.height && xPath < sourceRectangle.width && yPath >= 0 && xPath >= 0) {
                if ((flagArray[yPath * sourceRectangle.width + xPath] >= PreparationMaskBand.CLOUD_FLAG &&
                        flagArray[yPath * sourceRectangle.width + xPath] < PreparationMaskBand.INVALID_FLAG)) {


                    cloudExtent = CloudVerticalExtent.getCloudVerticalExtentSentinal2();
                    cloudBase = cloudExtent[0];
                    cloudTop = cloudExtent[1];

                    distAltArray = computeDistance(x0, xPath, y0, yPath, sourceLongitude, sourceLatitude,
                            sourceAltitude, sourceRectangle.width);

                    dist = distAltArray[0];
                    minAltitude = distAltArray[1];

                    cloudSearchPointHeight = dist * Math.tan(((Math.PI / 2. - sunZenith)));
                    cloudSearchPointHeight = cloudSearchPointHeight + (sourceAltitude[y0 * sourceRectangle.width + x0] - minAltitude);


                    if (cloudBase <= cloudSearchPointHeight && cloudSearchPointHeight <= cloudTop) {
                        temp = y0 * sourceRectangle.width + x0;
                        cloudShadowArray[temp] = PreparationMaskBand.CLOUD_SHADOW_FLAG;
                        cloudShadowIDArray[temp] = cloudIDArray[yPath * sourceRectangle.width + xPath];

                        minX0 = Math.min(cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][0], x0);
                        maxX0 = Math.max(cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][1], x0);
                        minY0 = Math.min(cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][2], y0);
                        maxY0 = Math.max(cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][3], y0);

                        cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][0] = Math.min(minX0, maxX0);
                        cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][1] = Math.max(minX0, maxX0);
                        cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][2] = Math.min(minY0, maxY0);
                        cloudShadowIdBorderRectangle[cloudShadowIDArray[temp]][3] = Math.max(minY0, maxY0);
                    }
                }
            }
        }
    }

    private static double[] computeDistance(int PosX1, int PosX2, int PosY1, int PosY2, float[] sourceLongitude, float[]
            sourceLatitude, float[] sourceAltitude, int sourceWidth) {

        double k = Math.PI / 180.0;
        double geoPos1Lon = sourceLongitude[PosY1 * sourceWidth + PosX1];
        double geoPos1Lat = sourceLatitude[PosY1 * sourceWidth + PosX1];
        double geoPos2Lon = sourceLongitude[PosY2 * sourceWidth + PosX2];
        double geoPos2Lat = sourceLatitude[PosY2 * sourceWidth + PosX2];
        double minAltitude = (double) Math.min(sourceAltitude[PosY1 * sourceWidth + PosX1],
                sourceAltitude[PosY2 * sourceWidth + PosX2]);


        double cosPos1Lat = Math.cos(geoPos1Lat * k);
        double cosPos2Lat = Math.cos(geoPos2Lat * k);
        double sinPos1Lat = Math.sin(geoPos1Lat * k);
        double sinPos2Lat = Math.sin(geoPos2Lat * k);
        double delta = (geoPos2Lon - geoPos1Lon) * k;
        double cosDelta = Math.cos(delta);
        double sinDelta = Math.sin(delta);
        double y = Math.sqrt(Math.pow(cosPos2Lat * sinDelta, 2) + Math.pow(cosPos1Lat * sinPos2Lat - sinPos1Lat * cosPos2Lat * cosDelta, 2));
        double x = sinPos1Lat * sinPos2Lat + cosPos1Lat * cosPos2Lat * cosDelta;

        double ad = Math.atan2(y, x);
        //double ad_2 = Math.acos(sinPos1Lat * sinPos2Lat + cosPos1Lat * cosPos2Lat * cosDelta);


        double dist = ad * (MEAN_EARTH_RADIUS + minAltitude);  //[m]

        double[] distAltArray = new double[2];

        distAltArray[0] = dist;
        distAltArray[1] = minAltitude;

        return distAltArray;
    }

    private static int[] getPotentialMaxCloudShadowPosition(double surfaceAlt, double sza, double saa,
                                                            double maxAlt,
                                                            int x0SourceArray, int y0SourceArray,
                                                            int x0TargetArray, int y0TargetArray, int productHeight,
                                                            int productWidth, int sourceHeight, int sourceWidth,
                                                            int targetHeight, int targetWidth,
                                                            double spatialResolution, boolean caseKeyBorder) {


        int X0;
        int Y0;
        int X1;
        int Y1;
        double factor;
        int[] path = new int[4];
        double iteratedeltaProjX;
        double iteratedeltaProjY;

        double deltaProjX = ((maxAlt - surfaceAlt) * Math.tan(sza) * Math.cos(saa - Math.PI / 2.)) / spatialResolution;
        double deltaProjY = ((maxAlt - surfaceAlt) * Math.tan(sza) * Math.sin(saa - Math.PI / 2.)) / spatialResolution;


        int divisor = (int) (Math.max(Math.abs(deltaProjX), Math.abs(deltaProjX)) * 2 + 0.5);
        if (caseKeyBorder) {
            X0 = x0TargetArray;
            Y0 = y0TargetArray;
            X1 = X0 + (int) (deltaProjX + 0.5);
            Y1 = Y0 + (int) (deltaProjY + 0.5);

            if (X1 > productWidth || X1 > targetWidth || Y1 > productHeight || Y1 > targetHeight || X1 < 0 || Y1 < 0) {
                iteratedeltaProjX = deltaProjX / divisor;
                iteratedeltaProjY = deltaProjY / divisor;
                for (int kk = 0; kk < (int) (maxAlt) - 1; kk++) {
                    factor = divisor - kk;
                    X1 = X0 + (int) (factor * iteratedeltaProjX + 0.5);
                    Y1 = Y0 + (int) (factor * iteratedeltaProjY + 0.5);
                    if (X1 < productWidth && X1 < targetWidth && Y1 < productHeight && Y1 < targetHeight && X1 > 0 && Y1 > 0) {
                        break;
                    } else {
                        X1 = X0;
                        Y1 = Y0;
                    }
                }
            }
        } else {
            X0 = x0SourceArray;
            Y0 = y0SourceArray;
            X1 = X0 + (int) (deltaProjX + 0.5);
            Y1 = Y0 + (int) (deltaProjY + 0.5);


            if (X1 > productWidth || X1 > sourceWidth || Y1 > productHeight || Y1 > sourceHeight || X1 < 0 || Y1 < 0) {
                iteratedeltaProjX = deltaProjX / divisor;
                iteratedeltaProjY = deltaProjY / divisor;
                for (int kk = 0; kk < (int) (maxAlt) - 1; kk++) {
                    factor = divisor - kk;
                    X1 = X0 + (int) (factor * iteratedeltaProjX + 0.5);
                    Y1 = Y0 + (int) (factor * iteratedeltaProjY + 0.5);
                    if (X1 < productWidth && X1 < sourceWidth && Y1 < productHeight && Y1 < sourceHeight && X1 > 0 && Y1 > 0) {
                        break;
                    } else {
                        X1 = X0;
                        Y1 = Y0;
                    }
                }
            }
        }

        path[0] = X0;
        path[1] = Y0;
        path[2] = X1;
        path[3] = Y1;

        return path;
    }


    private static void getLinePoints(int x0, int y0, int x1, int y1, int sourceHeight, int sourceWidth,
                               int targetHeight, int targetWidth, int pathWidth, int pathHeight,
                               int[] linePointsArray, boolean caseKeyBorder) {

        int x0SourceProduct;
        int y0SourceProduct;
        int x1SourceProduct;
        int y1SourceProduct;
        if (caseKeyBorder) {
            if (x0 == x1 && y0 == y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = 0;
                y1SourceProduct = 0;
            } else if (x0 == x1 && y0 < y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = 0;
                y1SourceProduct = pathHeight;
            } else if (x0 == x1 && y0 > y1) {
                x0SourceProduct = 0;
                y0SourceProduct = targetHeight - 1;
                x1SourceProduct = 0;
                y1SourceProduct = targetHeight - 1 + pathHeight; // pathHeigth <0
            } else if (x0 < x1 && y0 == y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = pathWidth;
                y1SourceProduct = 0;
            } else if (x0 < x1 && y0 < y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = pathWidth;
                y1SourceProduct = pathHeight;
            } else if (x0 < x1 && y0 > y1) {
                x0SourceProduct = 0;
                y0SourceProduct = targetHeight - 1;
                x1SourceProduct = pathWidth;
                y1SourceProduct = targetHeight - 1 + pathHeight; // pathHeigth <0
            } else if (x0 > x1 && y0 == y1) {
                x0SourceProduct = targetWidth - 1;
                y0SourceProduct = 0;
                x1SourceProduct = targetWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = 0;
            } else if (x0 > x1 && y0 < y1) {
                x0SourceProduct = targetWidth - 1;
                y0SourceProduct = 0;
                x1SourceProduct = targetWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = pathHeight;
            } else {
                x0SourceProduct = targetWidth - 1;
                y0SourceProduct = targetHeight - 1;
                x1SourceProduct = targetWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = targetHeight - 1 + pathHeight; // pathHeigth <0
            }
        } else {
            if (x0 == x1 && y0 == y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = 0;
                y1SourceProduct = 0;
            } else if (x0 == x1 && y0 < y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = 0;
                y1SourceProduct = pathHeight;
            } else if (x0 == x1 && y0 > y1) {
                x0SourceProduct = 0;
                y0SourceProduct = sourceHeight - 1;
                x1SourceProduct = 0;
                y1SourceProduct = sourceHeight - 1 + pathHeight; // pathHeight <0
            } else if (x0 < x1 && y0 == y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = pathWidth;
                y1SourceProduct = 0;
            } else if (x0 < x1 && y0 < y1) {
                x0SourceProduct = 0;
                y0SourceProduct = 0;
                x1SourceProduct = pathWidth;
                y1SourceProduct = pathHeight;
            } else if (x0 < x1 && y0 > y1) {
                x0SourceProduct = 0;
                y0SourceProduct = sourceHeight - 1;
                x1SourceProduct = pathWidth;
                y1SourceProduct = sourceHeight - 1 + pathHeight; // pathHeigth <0
            } else if (x0 > x1 && y0 == y1) {
                x0SourceProduct = sourceWidth - 1;
                y0SourceProduct = 0;
                x1SourceProduct = sourceWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = 0;
            } else if (x0 > x1 && y0 < y1) {
                x0SourceProduct = sourceWidth - 1;
                y0SourceProduct = 0;
                x1SourceProduct = sourceWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = pathHeight;
            } else {
                x0SourceProduct = sourceWidth - 1;
                y0SourceProduct = sourceHeight - 1;
                x1SourceProduct = sourceWidth - 1 + pathWidth; // pathWidth <0
                y1SourceProduct = sourceHeight - 1 + pathHeight; // pathHeigth <0
            }
        }
        linePointsArray[0] = x0SourceProduct;
        linePointsArray[1] = y0SourceProduct;
        linePointsArray[2] = x1SourceProduct;
        linePointsArray[3] = y1SourceProduct;

    }


    private static int[] getIndicesPathArray(int x0Center, int y0Center, int x1Center, int y1Center,
                                      int x0, int y0, int ii, int jj, int pathWidth, int pathHeight,
                                      int[] indicesPathArray) {

        int xi;
        int yj;
        int xPath;
        int yPath;

        if (x0Center == x1Center && y0Center == y1Center) {
            xPath = x0;
            yPath = y0;
            xi = 0;
            yj = 0;
        } else if (x0Center == x1Center && y0Center < y1Center) {
            xPath = x0;
            yPath = y0 + jj;
            xi = 0;
            yj = jj;
        } else if (x0Center == x1Center && y0Center > y1Center) {
            xPath = x0;
            yPath = y0 - jj;
            xi = 0;
            yj = pathHeight - jj;
        } else if (x0Center < x1Center && y0Center == y1Center) {
            xPath = x0 + ii;
            yPath = y0;
            xi = ii;
            yj = 0;
        } else if (x0Center < x1Center && y0Center < y1Center) {
            xPath = x0 + ii;
            yPath = y0 + jj;
            xi = ii;
            yj = jj;
        } else if (x0Center < x1Center && y0Center > y1Center) {
            xPath = x0 + ii;
            yPath = y0 - jj;
            xi = ii;
            yj = pathHeight - jj;
        } else if (x0Center > x1Center && y0Center == y1Center) {
            xPath = x0 - ii;
            yPath = y0;
            xi = pathWidth - ii;
            yj = 0;
        } else if (x0Center > x1Center && y0Center < y1Center) {
            xPath = x0 - ii;
            yPath = y0 + jj;
            xi = pathWidth - ii;
            yj = jj;
        } else {
            xPath = x0 - ii;
            yPath = y0 - jj;
            xi = pathWidth - ii;
            yj = pathHeight - jj;
        }

        indicesPathArray[0] = xPath;
        indicesPathArray[1] = yPath;
        indicesPathArray[2] = xi;
        indicesPathArray[3] = yj;

        return indicesPathArray;
    }


}



