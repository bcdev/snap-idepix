package org.esa.snap.idepix.core.util;

import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.util.math.MathUtils;

public class SlopeAspectOrientationUtils {

    private final static float EARTH_MIN_ELEVATION = -428.0f;  // at shoreline of Dead Sea
    private final static float EARTH_MAX_ELEVATION = 8848.0f;  // Mt. Everest

    /**
     * Provides data from a 3x3 macropixel of a float source tile
     *
     * @param sourceTile -
     * @param y -
     * @param x -
     * @return float[9]
     */
    public static float[] get3x3MacropixelData(Tile sourceTile, int y, int x) {
        float[] macropixelData = new float[9];
        macropixelData[0] = sourceTile.getSampleFloat(x - 1, y - 1);
        macropixelData[1] = sourceTile.getSampleFloat(x, y - 1);
        macropixelData[2] = sourceTile.getSampleFloat(x + 1, y - 1);
        macropixelData[3] = sourceTile.getSampleFloat(x - 1, y);
        macropixelData[4] = sourceTile.getSampleFloat(x, y);
        macropixelData[5] = sourceTile.getSampleFloat(x + 1, y);
        macropixelData[6] = sourceTile.getSampleFloat(x - 1, y + 1);
        macropixelData[7] = sourceTile.getSampleFloat(x, y + 1);
        macropixelData[8] = sourceTile.getSampleFloat(x + 1, y + 1);

        return macropixelData;
    }

    /**
     * Checks if 3x3 box of elevations around reference pixel is valid
     * (i.e. not NaN and all values inside real values possible on Earth)
     *
     * @param elevationData - 3x3 box of elevations
     * @return boolean
     */
    public static boolean is3x3ElevationDataValid(float[] elevationData) {
        for (final float elev : elevationData) {
            if (elev == 0.0f || Float.isNaN(elev) || elev < EARTH_MIN_ELEVATION || elev > EARTH_MAX_ELEVATION) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes slope and aspect for a 3x3 altitude array
     *
     * @param elev - 3x3 elevation array
     * @param orientation - orientation angle of box
     * @param vza - view zenith angle
     * @param vaa - view azimuth angle
     * @param saa - sun azimuth angle
     * @param spatialResolution - spatial resolution in m
     * @return float[]{slope, aspect}
     */
    public static float[] computeSlopeAspect3x3(float[] elev, float orientation, float vza, float vaa, float saa,
                                                double spatialResolution) {
        //DM: geometric correction of resolution necessary for observations not in nadir view.
        // generates steeper slopes. Needs vza, vaa, angle between y-pixel axis and north direction (orientation).
        //DM: orientation in rad!
        float b = (elev[2] + 2 * elev[5] + elev[8] - elev[0] - 2 * elev[3] - elev[6]) / 8f; //direction x
        float c = (elev[0] + 2 * elev[1] + elev[2] - elev[6] - 2 * elev[7] - elev[8]) / 8f; //direction y
        double vaa_orientation = (360.0 - (vaa + orientation / MathUtils.DTOR)) * MathUtils.DTOR;
        double spatialRes = spatialResolution / Math.cos(vza * MathUtils.DTOR);
        float slope = (float) Math.atan(Math.sqrt(Math.pow(b / (spatialRes * Math.sin(vaa_orientation)), 2) +
                Math.pow(c / (spatialRes * Math.cos(vaa_orientation)), 2)));
        float aspect = (float) Math.atan2(-b, -c);
        if (saa > 270. || saa < 90) { //Sun from North (mostly southern hemisphere)
            aspect -= Math.PI;
        }
        if (aspect < 0.0f) {
            // map from [-180, 180] into [0, 360], see e.g. https://www.e-education.psu.edu/geog480/node/490
            aspect += 2.0 * Math.PI;
        }
        if (slope <= 0.0) {
            aspect = Float.NaN;
        }

        return new float[]{slope, aspect};
    }

    /**
     * Computes the orientation of a 3x3 lat/lon box, i.e. uses 3rd and 5th point.
     *
     * @param latData - 3x3 lat values
     * @param lonData - 3x3 lon values
     * @return float
     */
    public static float computeOrientation3x3Box(float[] latData, float[] lonData) {
        final float lat1 = latData[3];
        final float lat2 = latData[5];
        final float lon1 = lonData[3];
        final float lon2 = lonData[5];

        return computeOrientation(lat1, lat2, lon1, lon2);
    }

    /**
     * Provides orientation ('bearing') between two points.
     * See theory e.g. at
     * <a href="https://www.igismap.com/formula-to-find-bearing-or-heading-angle-between-two-points-latitude-longitude/">...</a>
     *
     * @param lat1 - first latitude
     * @param lat2 - second latitude
     * @param lon1 - first longitude
     * @param lon2 - second longitude
     * @return float
     */
    public static float computeOrientation(float lat1, float lat2, float lon1, float lon2) {
//        final float lat1Rad = lat1 * MathUtils.DTOR_F;
//        final float deltaLon = lon2 - lon1;
        // we use this formula, as in S2-MSI:
//        return (float) Math.atan2(-(lat2 - lat1), deltaLon * Math.cos(lat1Rad));

        // DM: formulas from theory, see above
        double X = Math.cos(lat2 * MathUtils.DTOR) * Math.sin((lon2 - lon1) * MathUtils.DTOR);
        double Y = Math.cos(lat1 * MathUtils.DTOR) * Math.sin(lat2 * MathUtils.DTOR) - Math.sin(lat1 * MathUtils.DTOR) * X;
        return (float) (Math.atan2(X, Y));

    }

    /**
     * Computes product spatial resolution from great circle distances at the product edges.
     * To be used as fallback if we have no CRS geocoding.
     *
     * @param l1bProduct      - the source product
     * @param sourceGeoCoding - the source scene geocoding
     * @return spatial resolution in metres
     */
    public static double computeSpatialResolution(Product l1bProduct, GeoCoding sourceGeoCoding) {
        final double width = l1bProduct.getSceneRasterWidth();
        final double height = l1bProduct.getSceneRasterHeight();

        final GeoPos leftPos = sourceGeoCoding.getGeoPos(new PixelPos(0, height / 2), null);
        final GeoPos rightPos = sourceGeoCoding.getGeoPos(new PixelPos(width - 1, height / 2), null);
        final double distance1 =
                computeDistance(leftPos.getLat(), leftPos.getLon(), rightPos.getLat(), rightPos.getLon());
        final double xRes = 1000.0 * distance1 / (width - 1);

        final GeoPos upperPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, 0), null);
        final GeoPos lowerPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, height - 1), null);
        final double distance2 =
                computeDistance(upperPos.getLat(), upperPos.getLon(), lowerPos.getLat(), lowerPos.getLon());
        final double yRes = 1000.0 * distance2 / (height - 1);

        return 0.5 * (xRes + yRes);
    }

    /**
     * Calculate the great-circle distance between two points on Earth using Haversine formula.
     * See e.g. <a href="https://www.movable-type.co.uk/scripts/latlong.html">...</a>
     *
     * @param lat1 - first point latitude
     * @param lon1 - first point longitude
     * @param lat2 - second point latitude
     * @param lon2 - second point longitude
     * @return distance in km
     */
    public static double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        final double deltaLatR = (lat1 - lat2) * MathUtils.DTOR;
        final double deltaLonR = (lon1 - lon2) * MathUtils.DTOR;

        final double a = Math.sin(deltaLatR / 2.0) * Math.sin(deltaLatR / 2.0) +
                Math.cos(lat1 * MathUtils.DTOR) * Math.cos(lat2 * MathUtils.DTOR) *
                        Math.sin(deltaLonR / 2.0) * Math.sin(deltaLonR / 2.0);

        final double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        final double R = 6371.0;  // Earth radius in km

        return R * c;
    }

}
