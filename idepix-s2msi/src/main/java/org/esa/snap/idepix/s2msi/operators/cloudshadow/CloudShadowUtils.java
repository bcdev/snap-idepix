package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.util.ShapeRasterizer;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

/**
 * @author Tonio Fincke
 */
public class CloudShadowUtils {

    private static double QUARTER_DIVIDER = 0.7071067811865475;
    private static final int MEAN_EARTH_RADIUS = 6372000;

    private final static float EARTH_MIN_ELEVATION = -428.0f;  // at shoreline of Dead Sea
    private final static float EARTH_MAX_ELEVATION = 8848.0f;  // Mt. Everest

    /**
     * Checks if 3x3 box of elevations around given index is valid
     * (i.e. not NaN and all values inside real values possible on Earth)
     *
     * @param elevations - elevation sample array on tile
     * @param index0 - array index
     * @param tileWidth - tile width
     * @return boolean
     */
    public static boolean elevationBoxIsValid(float[] elevations, int index0, int tileWidth) {
        float[] elevBox = new float[8];
        elevBox[0] = elevations[Math.max(0, index0 - tileWidth - 1)];
        elevBox[1] = elevations[Math.max(0, index0 - tileWidth)];
        elevBox[2] = elevations[Math.max(0, index0 - tileWidth + 1)];
        elevBox[3] = elevations[Math.max(0, index0 - 1)];
        elevBox[4] = elevations[Math.min(tileWidth-1, index0 + 1)];
        elevBox[5] = elevations[Math.min(tileWidth-1, index0 + tileWidth - 1)];
        elevBox[6] = elevations[Math.min(tileWidth-1, index0 + tileWidth)];
        elevBox[7] = elevations[Math.min(tileWidth-1, index0 + tileWidth + 1)];

        for (float elev : elevBox) {
            if (Float.isNaN(elev) || elev < EARTH_MIN_ELEVATION || elev > EARTH_MAX_ELEVATION) {
                return false;
            }
        }
        return true;
    }

    static Point2D[] getRelativePath(double minSurfaceAltitude, double sza, double saa, double maxObjectAltitude,
                                     Rectangle sourceRectangle, Rectangle targetRectangle,
                                     int productHeight, int productWidth, double spatialResolution, boolean inverse,
                                     boolean setOffsetInTargetRectangle) {
        final double cosSaa = Math.cos(saa - Math.PI / 2.);
        final double sinSaa = Math.sin(saa - Math.PI / 2.);
        double deltaProjX = ((maxObjectAltitude - minSurfaceAltitude) * Math.tan(sza) * cosSaa) / spatialResolution;
        double deltaProjY = ((maxObjectAltitude - minSurfaceAltitude) * Math.tan(sza) * sinSaa) / spatialResolution;
        double x0;
        double y0;
        if (setOffsetInTargetRectangle) {
            x0 = cosSaa > 0 ? targetRectangle.x : targetRectangle.x + targetRectangle.getWidth() - 1;
            y0 = sinSaa > 0 ? targetRectangle.y : targetRectangle.y + targetRectangle.getHeight() - 1;
        } else {
            x0 = cosSaa > 0 ? sourceRectangle.x : sourceRectangle.x + sourceRectangle.getWidth() - 1;
            y0 = sinSaa > 0 ? sourceRectangle.y : sourceRectangle.y + sourceRectangle.getHeight() - 1;
        }
        double x1 = x0 + deltaProjX + 0.5;
        double y1 = y0 + deltaProjY + 0.5;
        double minX = Math.max(0, sourceRectangle.getX());
        double minY = Math.max(0, sourceRectangle.getY());
        double maxX = Math.min(productWidth - 1, sourceRectangle.getX() + sourceRectangle.getWidth() - 1);
        double maxY = Math.min(productHeight - 1, sourceRectangle.getY() + sourceRectangle.getHeight() - 1);
        if (sinSaa + QUARTER_DIVIDER < 1e-8) {
            //upper border
            if (y1 < minY) { //intersection exists
                if (x0 != x1) { //otherwise keep x1
                    double m = (y1 - y0) / (x1 - x0);
                    x1 = (x0 + (minY - y0) / m);
                }
                y1 = minY;
            }
        } else if (sinSaa - QUARTER_DIVIDER > 1e-8) {
            //lower border
            if (y1 > maxY) { //intersection exists
                if (x0 != x1) { //otherwise keep x1
                    double m = (y1 - y0) / (x1 - x0);
                    x1 = (x0 + (maxY - y0) / m);
                }
                y1 = maxY;
            }
        } else if (cosSaa + QUARTER_DIVIDER < 1e-8) {
            //left border
            if (x1 < minX) { //intersection exists
                if (y0 != y1) { //otherwise keep x1
                    double m = (y1 - y0) / (x1 - x0);
                    y1 = (y0 + m * (minX - x0));
                }
                x1 = minX;
            }
        } else {
            // right border
            if (x1 > maxX) { //intersection exists
                if (y0 != y1) { //otherwise keep x1
                    double m = (y1 - y0) / (x1 - x0);
                    y1 = (y0 + m * (maxX - x0));
                }
                x1 = maxX;
            }
        }
        Point2D endPoint = new Point2D.Double();
        if (inverse) {
            endPoint.setLocation(x0 - x1, y0 - y1);
        } else {
            endPoint.setLocation(x1 - x0, y1 - y0);
        }
        Point2D[] vertices = new Point2D[]{new Point2D.Double(0, 0), endPoint};
        final PotentialPathShapeRasterizer shapeRasterizer = new PotentialPathShapeRasterizer();
        return shapeRasterizer.rasterize(vertices);
    }

    static double[] computeDistance(int index0, int indexPath, float[] sourceLongitude, float[] sourceLatitude,
                                    float[] sourceAltitude) {
        double k = Math.PI / 180.0;
        double geoPos1Lon = sourceLongitude[index0];
        double geoPos1Lat = sourceLatitude[index0];
        double geoPos2Lon = sourceLongitude[indexPath];
        double geoPos2Lat = sourceLatitude[indexPath];
        double minAltitude = (double) Math.min(sourceAltitude[index0], sourceAltitude[indexPath]);
        if (minAltitude < 0 || Double.isNaN(minAltitude)) {
            minAltitude = 0.0;
        }

        double cosPos1Lat = Math.cos(geoPos1Lat * k);
        double cosPos2Lat = Math.cos(geoPos2Lat * k);
        double sinPos1Lat = Math.sin(geoPos1Lat * k);
        double sinPos2Lat = Math.sin(geoPos2Lat * k);
        double delta = (geoPos2Lon - geoPos1Lon) * k;
        double cosDelta = Math.cos(delta);
        double sinDelta = Math.sin(delta);
        double y = Math.sqrt(Math.pow(cosPos2Lat * sinDelta, 2) +
                Math.pow(cosPos1Lat * sinPos2Lat - sinPos1Lat * cosPos2Lat * cosDelta, 2));
        double x = sinPos1Lat * sinPos2Lat + cosPos1Lat * cosPos2Lat * cosDelta;
        double ad = Math.atan2(y, x);
        double[] distAltArray = new double[3];
        distAltArray[0] = ad * (MEAN_EARTH_RADIUS + minAltitude);
        distAltArray[1] = minAltitude;
        return distAltArray;
    }

    /*
        package local for testing
         */
    @SuppressWarnings("WeakerAccess")
    static Rectangle getSourceRectangle(Product sourceProduct, Rectangle targetRectangle, Point2D[] relativePath) {
        final int productWidth = sourceProduct.getSceneRasterWidth();
        final int productHeight = sourceProduct.getSceneRasterHeight();
        final int relativeX = (int) relativePath[relativePath.length - 1].getX();
        final int relativeY = (int) relativePath[relativePath.length - 1].getY();

        // borders are now extended in both directions left-right, top-down.
        // so it needs a reduction in x0,y0 and addition in x1,y1
        int x0 = Math.max(0, targetRectangle.x + Math.min(0, -1 * Math.abs(relativeX)));
        int y0 = Math.max(0, targetRectangle.y + Math.min(0, -1 * Math.abs(relativeY)));
        int x1 = Math.min(productWidth, targetRectangle.x + targetRectangle.width + Math.max(0, Math.abs(relativeX)));
        int y1 = Math.min(productHeight, targetRectangle.y + targetRectangle.height + Math.max(0, Math.abs(relativeY)));
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    static boolean isCompletelyInvalid(Tile sourceTileFlag1) {
        int invalid_byte = (int) Math.pow(2, S2IdepixConstants.IDEPIX_INVALID);
        int[] classifData = sourceTileFlag1.getSamplesInt();
        for (int i=0; i<classifData.length; ++i) {
            if ((classifData[i] & invalid_byte) == 0) {
                return false;
            }
        }
        return true;
    }
}
