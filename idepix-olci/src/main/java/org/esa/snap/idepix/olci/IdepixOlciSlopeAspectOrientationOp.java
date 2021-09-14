package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Map;

/**
 * Computes Slope, Aspect and Orientation for a Sentinel-3 OLCI product.
 * See theory e.g. at
 * https://www.e-education.psu.edu/geog480/node/490, or
 * https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm
 *
 * @author Tonio Fincke, Olaf Danne, Dagmar Mueller
 */
@OperatorMetadata(alias = "Idepix.Olci.SlopeAspect",
        version = "2.0",
        internal = true,
        authors = "Tonio Fincke, Olaf Danne, Dagmar Mueller",
        copyright = "(c) 2018-2021 by Brockmann Consult",
        description = "Computes Slope, Aspect and Orientation for a Sentinel-3 OLCI product. " +
                "See theory e.g. at https://www.e-education.psu.edu/geog480/node/490, or " +
                "https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm")
public class IdepixOlciSlopeAspectOrientationOp extends Operator {

    @SourceProduct
    private Product sourceProduct;

    private final static float EARTH_MIN_ELEVATION = -428.0f;  // at shoreline of Dead Sea
    private final static float EARTH_MAX_ELEVATION = 8848.0f;  // Mt. Everest

    private static final String ELEVATION_BAND_NAME = IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME;

    private double spatialResolution;

    private Band latitudeBand;
    private Band longitudeBand;
    private Band elevationBand;
    private TiePointGrid viewZenithTiePointGrid;
    private TiePointGrid viewAzimuthTiePointGrid;
    private TiePointGrid sunAzimuthTiePointGrid;
    private Band slopeBand;
    private Band aspectBand;
    private Band orientationBand;
    private final static String TARGET_PRODUCT_NAME = "Slope-Aspect-Orientation";
    private final static String TARGET_PRODUCT_TYPE = "slope-aspect-orientation";
    final static String SLOPE_BAND_NAME = "slope";
    final static String ASPECT_BAND_NAME = "aspect";
    final static String ORIENTATION_BAND_NAME = "orientation";
    private final static String SLOPE_BAND_DESCRIPTION = "Slope of each pixel as angle";
    private final static String ASPECT_BAND_DESCRIPTION =
            "Aspect of each pixel as angle between raster -Y direction and steepest slope, clockwise";
    private final static String ORIENTATION_BAND_DESCRIPTION =
            "Orientation of each pixel as angle between east and raster X direction, clockwise";
    private final static String SLOPE_BAND_UNIT = "rad [0..pi/2]";
    private final static String ASPECT_BAND_UNIT = "rad [-pi..pi]";
    private final static String ORIENTATION_BAND_UNIT = "rad [-pi..pi]";

    @Override
    public void initialize() throws OperatorException {
        sourceProduct = getSourceProduct();
        ensureSingleRasterSize(sourceProduct);
        GeoCoding sourceGeoCoding = sourceProduct.getSceneGeoCoding();
        if (sourceGeoCoding == null) {
            throw new OperatorException("Source product has no geo-coding");
        }
        if (sourceGeoCoding instanceof CrsGeoCoding) {
            final MathTransform i2m = sourceGeoCoding.getImageToMapTransform();
            if (i2m instanceof AffineTransform) {
                spatialResolution = ((AffineTransform) i2m).getScaleX();
            } else {
                spatialResolution = computeSpatialResolution(sourceProduct, sourceGeoCoding);
            }
        } else {
            spatialResolution = computeSpatialResolution(sourceProduct, sourceGeoCoding);
        }
        elevationBand = sourceProduct.getBand(ELEVATION_BAND_NAME);
        if (elevationBand == null) {
            throw new OperatorException("Elevation band required to compute slope or aspect");
        }
        latitudeBand = sourceProduct.getBand(IdepixOlciConstants.OLCI_LATITUDE_BAND_NAME);
        longitudeBand = sourceProduct.getBand(IdepixOlciConstants.OLCI_LONGITUDE_BAND_NAME);
        viewZenithTiePointGrid = sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_VIEW_ZENITH_BAND_NAME);
        viewAzimuthTiePointGrid = sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_VIEW_AZIMUTH_BAND_NAME);
        sunAzimuthTiePointGrid = sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_SUN_AZIMUTH_BAND_NAME);

        Product targetProduct = createTargetProduct();
        setTargetProduct(targetProduct);
    }

    private Band createBand(Product targetProduct, String bandName, String description, String unit) {
        Band band = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(-9999.);
        band.setNoDataValueUsed(true);
        return band;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        final Rectangle sourceRectangle = getSourceRectangle(targetRectangle);
        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
        final Tile latitudeTile = getSourceTile(latitudeBand, sourceRectangle, borderExtender);
        final Tile longitudeTile = getSourceTile(longitudeBand, sourceRectangle, borderExtender);
        final Tile elevationTile = getSourceTile(elevationBand, sourceRectangle, borderExtender);
        final Tile viewZenithAngleTile = getSourceTile(viewZenithTiePointGrid, sourceRectangle, borderExtender);
        final Tile viewAzimuthAngleTile = getSourceTile(viewAzimuthTiePointGrid, sourceRectangle, borderExtender);
        final Tile sunAzimuthAngleTile = getSourceTile(sunAzimuthTiePointGrid, sourceRectangle, borderExtender);

        final Tile slopeTile = targetTiles.get(slopeBand);
        final Tile aspectTile = targetTiles.get(aspectBand);
        final Tile orientationTile = targetTiles.get(orientationBand);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                slopeTile.setSample(x, y, Float.NaN);
                aspectTile.setSample(x, y, Float.NaN);
                orientationTile.setSample(x, y, Float.NaN);
                final float[] elevationDataMacropixel = get3x3MacropixelData(elevationTile, y, x);
                if (is3x3ElevationDataValid(elevationDataMacropixel)) {
                    final float vza = viewZenithAngleTile.getSampleFloat(x, y);
                    final float vaa = viewAzimuthAngleTile.getSampleFloat(x, y);
                    final float saa = sunAzimuthAngleTile.getSampleFloat(x, y);
                    final float[] latitudeDataMacropixel = get3x3MacropixelData(latitudeTile, y, x);
                    final float[] longitudeDataMacropixel = get3x3MacropixelData(longitudeTile, y, x);
                    final float orientation = computeOrientation(latitudeDataMacropixel, longitudeDataMacropixel);
                    orientationTile.setSample(x, y, orientation);
                    final float[] slopeAspect = computeSlopeAspect(elevationDataMacropixel, orientation, vza, vaa, saa,
                            spatialResolution);
                    slopeTile.setSample(x, y, slopeAspect[0]);
                    aspectTile.setSample(x, y, slopeAspect[1]);
                }
            }
        }
    }

    private float[] get3x3MacropixelData(Tile sourceTile, int y, int x) {
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
    static boolean is3x3ElevationDataValid(float[] elevationData) {
        for (final float elev : elevationData) {
            if (elev == 0.0f || Float.isNaN(elev) || elev < EARTH_MIN_ELEVATION || elev > EARTH_MAX_ELEVATION) {
                return false;
            }
        }
        return true;
    }

    /* package local for testing */
    static float[] computeSlopeAspect(float[] elev, float orientation, float vza, float vaa, float saa, double spatialResolution) {
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
        if (saa > 270. || saa < 90){ //Sun from North (mostly southern hemisphere)
            aspect -=Math.PI;
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

    /* package local for testing */
    static float computeOrientation(float[] latData, float[] lonData) {
        final float lat1 = latData[3];
        final float lat2 = latData[5];
        final float lon1 = lonData[3];
        final float lon2 = lonData[5];

        return computeOrientation(lat1, lat2, lon1, lon2);
    }

    /**
     * Provides orientation ('bearing') between two points.
     * See theory e.g. at
     * https://www.igismap.com/formula-to-find-bearing-or-heading-angle-between-two-points-latitude-longitude/
     *
     * @param lat1 - first latitude
     * @param lat2 - second latitude
     * @param lon1 - first longitude
     * @param lon2 - second longitude
     *
     * @return float
     */
    static float computeOrientation(float lat1, float lat2, float lon1, float lon2) {
//        final float lat1Rad = lat1 * MathUtils.DTOR_F;
//        final float deltaLon = lon2 - lon1;
        // we use this formula, as in S2-MSI:
//        return (float) Math.atan2(-(lat2 - lat1), deltaLon * Math.cos(lat1Rad));

        // DM: formulas from theory, see above
        double X = Math.cos(lat2*MathUtils.DTOR)*Math.sin((lon2-lon1)*MathUtils.DTOR);
        double Y = Math.cos(lat1*MathUtils.DTOR)*Math.sin(lat2*MathUtils.DTOR) - Math.sin(lat1*MathUtils.DTOR)*X;
        return (float) (Math.atan2(X, Y));

    }

    /**
     * Computes product spatial resolution from great circle distances at the product edges.
     * To be used as fallback if we have no CRS geocoding.
     *
     * @param sourceProduct   - the source product
     * @param sourceGeoCoding - the source scene geocoding
     * @return spatial resolution in metres
     */
    static double computeSpatialResolution(Product sourceProduct, GeoCoding sourceGeoCoding) {
        final double width = sourceProduct.getSceneRasterWidth();
        final double height = sourceProduct.getSceneRasterHeight();

        final GeoPos leftPos = sourceGeoCoding.getGeoPos(new PixelPos(0, height / 2), null);
        final GeoPos rightPos = sourceGeoCoding.getGeoPos(new PixelPos(width - 1, height / 2), null);
        final double distance1 =
                computeDistance(leftPos.getLat(), leftPos.getLon(), rightPos.getLat(), rightPos.getLon());

        final GeoPos upperPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, 0), null);
        final GeoPos lowerPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, height - 1), null);
        final double distance2 =
                computeDistance(upperPos.getLat(), upperPos.getLon(), lowerPos.getLat(), lowerPos.getLon());

        final double distance = 0.5 * (distance1 + distance2);

        return 1000.0 * distance / (width - 1);
    }

    /**
     * Calculate the great-circle distance between two points on Earth using Haversine formula.
     * See e.g. https://www.movable-type.co.uk/scripts/latlong.html
     *
     * @param lat1 - first point latitude
     * @param lon1 - first point longitude
     * @param lat2 - second point latitude
     * @param lon2 - second point longitude
     * @return distance in km
     */
    static double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        final double deltaLatR = (lat1 - lat2) * MathUtils.DTOR;
        final double deltaLonR = (lon1 - lon2) * MathUtils.DTOR;

        final double a = Math.sin(deltaLatR / 2.0) * Math.sin(deltaLatR / 2.0) +
                Math.cos(lat1 * MathUtils.DTOR) * Math.cos(lat2 * MathUtils.DTOR) *
                        Math.sin(deltaLonR / 2.0) * Math.sin(deltaLonR / 2.0);

        final double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        final double R = 6371.0;  // Earth radius in km

        return R * c;
    }

    private static Rectangle getSourceRectangle(Rectangle targetRectangle) {
        return new Rectangle(targetRectangle.x - 1, targetRectangle.y - 1,
                targetRectangle.width + 2, targetRectangle.height + 2);
    }

    private Product createTargetProduct() {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(TARGET_PRODUCT_NAME, TARGET_PRODUCT_TYPE, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        slopeBand = createBand(targetProduct, SLOPE_BAND_NAME, SLOPE_BAND_DESCRIPTION, SLOPE_BAND_UNIT);
        aspectBand = createBand(targetProduct, ASPECT_BAND_NAME, ASPECT_BAND_DESCRIPTION, ASPECT_BAND_UNIT);
        orientationBand = createBand(targetProduct, ORIENTATION_BAND_NAME, ORIENTATION_BAND_DESCRIPTION, ORIENTATION_BAND_UNIT);

        return targetProduct;
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixOlciSlopeAspectOrientationOp.class);
        }
    }
}
