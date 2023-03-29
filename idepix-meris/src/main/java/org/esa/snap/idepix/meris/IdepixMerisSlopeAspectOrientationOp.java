package org.esa.snap.idepix.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.util.SlopeAspectOrientationUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.util.Map;

/**
 * Computes Slope, Aspect and Orientation for a MERIS L1b product.
 * Only 4RP, as this contains altitude band with original resolution. In 3RP we have only TPG.
 * See theory e.g. at
 * <a href="https://www.e-education.psu.edu/geog480/node/490">...</a>, or
 * <a href="https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm">...</a>
 *
 * @author Tonio Fincke, Olaf Danne, Dagmar Mueller
 */
@OperatorMetadata(alias = "Idepix.Meris.SlopeAspect",
        version = "2.0",
        internal = true,
        authors = "Tonio Fincke, Olaf Danne, Dagmar Mueller",
        copyright = "(c) 2018-2021 by Brockmann Consult",
        description = "Computes Slope, Aspect and Orientation for a Sentinel-3 OLCI product. " +
                "See theory e.g. at https://www.e-education.psu.edu/geog480/node/490, or " +
                "https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm")
public class IdepixMerisSlopeAspectOrientationOp extends Operator {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    private static final String ELEVATION_BAND_NAME = IdepixMerisConstants.MERIS_4RP_ALTITUDE_BAND_NAME;

    private double spatialResolution;

    private TiePointGrid latitudeTpg;
    private TiePointGrid longitudeTpg;
    private TiePointGrid sunAzimuthTpg;
    private TiePointGrid viewZenithTpg;
    private TiePointGrid viewAzimuthTpg;
    private Band elevationBand;
    private Band viewZenithBand;
    private Band viewAzimuthBand;
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

        if (!(l1bProduct.containsBand("altitude"))) {
            throw new OperatorException("Slope/Aspect/Orientation requires altitude band at original resolution. " +
                    "Use MERIS 4RP input product");
        }

        ensureSingleRasterSize(l1bProduct);
        GeoCoding sourceGeoCoding = l1bProduct.getSceneGeoCoding();
        if (sourceGeoCoding == null) {
            throw new OperatorException("Source product has no geo-coding");
        }

        spatialResolution = SlopeAspectOrientationUtils.computeSpatialResolution(l1bProduct, sourceGeoCoding);

        elevationBand = l1bProduct.getBand(ELEVATION_BAND_NAME);
        if (elevationBand == null) {
            throw new OperatorException("Elevation band required to compute slope or aspect");
        }
        latitudeTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_LATITUDE_BAND_NAME);
        longitudeTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_LONGITUDE_BAND_NAME);

        Product targetProduct = createTargetProduct();

        if (IdepixMerisUtils.isFullResolution(l1bProduct) || IdepixMerisUtils.isReducedResolution(l1bProduct)) {
            IdepixMerisViewAngleInterpolationOp viewAngleInterpolationOp = new IdepixMerisViewAngleInterpolationOp();
            viewAngleInterpolationOp.setParameterDefaultValues();
            viewAngleInterpolationOp.setSourceProduct(l1bProduct);
            Product viewAngleInterpolationProduct = viewAngleInterpolationOp.getTargetProduct();

            viewZenithBand = viewAngleInterpolationProduct.getBand(IdepixMerisConstants.MERIS_VIEW_ZENITH_INTERPOLATED_BAND_NAME);
            viewAzimuthBand = viewAngleInterpolationProduct.getBand(IdepixMerisConstants.MERIS_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME);
            ProductUtils.copyBand(IdepixMerisConstants.MERIS_VIEW_ZENITH_INTERPOLATED_BAND_NAME,
                    viewAngleInterpolationProduct, targetProduct, true);
            ProductUtils.copyBand(IdepixMerisConstants.MERIS_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME,
                    viewAngleInterpolationProduct, targetProduct, true);
        } else {
            viewZenithTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_VIEW_ZENITH_BAND_NAME);
            viewAzimuthTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_VIEW_AZIMUTH_BAND_NAME);
        }

        sunAzimuthTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_SUN_AZIMUTH_BAND_NAME);

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
        final Tile latitudeTile = getSourceTile(latitudeTpg, sourceRectangle, borderExtender);
        final Tile longitudeTile = getSourceTile(longitudeTpg, sourceRectangle, borderExtender);
        final Tile elevationTile = getSourceTile(elevationBand, sourceRectangle, borderExtender);
        Tile viewZenithAngleTile;
        Tile viewAzimuthAngleTile;
        if (IdepixMerisUtils.isFullResolution(l1bProduct) || IdepixMerisUtils.isReducedResolution(l1bProduct)) {
            viewZenithAngleTile = getSourceTile(viewZenithBand, sourceRectangle, borderExtender);
            viewAzimuthAngleTile = getSourceTile(viewAzimuthBand, sourceRectangle, borderExtender);
        } else {
            viewZenithAngleTile = getSourceTile(viewZenithTpg, sourceRectangle, borderExtender);
            viewAzimuthAngleTile = getSourceTile(viewAzimuthTpg, sourceRectangle, borderExtender);
        }
        final Tile sunAzimuthAngleTile = getSourceTile(sunAzimuthTpg, sourceRectangle, borderExtender);

        final Tile slopeTile = targetTiles.get(slopeBand);
        final Tile aspectTile = targetTiles.get(aspectBand);
        final Tile orientationTile = targetTiles.get(orientationBand);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                slopeTile.setSample(x, y, Float.NaN);
                aspectTile.setSample(x, y, Float.NaN);
                orientationTile.setSample(x, y, Float.NaN);
                final float[] elevationDataMacropixel = SlopeAspectOrientationUtils.get3x3MacropixelData(elevationTile, y, x);
                if (SlopeAspectOrientationUtils.is3x3ElevationDataValid(elevationDataMacropixel)) {
                    final float vza = viewZenithAngleTile.getSampleFloat(x, y);
                    final float vaa = viewAzimuthAngleTile.getSampleFloat(x, y);
                    final float saa = sunAzimuthAngleTile.getSampleFloat(x, y);
                    if (x == 2783 && y == 642) {
                        System.out.println("x, y = " + x + ", " + y);  // small subset, shadow
                    }
                    final float[] latitudeDataMacropixel =
                            SlopeAspectOrientationUtils.get3x3MacropixelData(latitudeTile, y, x);
                    final float[] longitudeDataMacropixel =
                            SlopeAspectOrientationUtils.get3x3MacropixelData(longitudeTile, y, x);
                    final float orientation = SlopeAspectOrientationUtils.computeOrientation3x3Box(latitudeDataMacropixel,
                            longitudeDataMacropixel);
                    orientationTile.setSample(x, y, orientation);
                    final float[] slopeAspect = SlopeAspectOrientationUtils.computeSlopeAspect3x3(elevationDataMacropixel,
                            orientation, vza, vaa, saa, spatialResolution);
                    slopeTile.setSample(x, y, slopeAspect[0]);
                    aspectTile.setSample(x, y, slopeAspect[1]);
                }
            }
        }
    }

    private static Rectangle getSourceRectangle(Rectangle targetRectangle) {
        return new Rectangle(targetRectangle.x - 1, targetRectangle.y - 1,
                targetRectangle.width + 2, targetRectangle.height + 2);
    }

    private Product createTargetProduct() {
        final int sceneWidth = l1bProduct.getSceneRasterWidth();
        final int sceneHeight = l1bProduct.getSceneRasterHeight();
        Product targetProduct = new Product(TARGET_PRODUCT_NAME, TARGET_PRODUCT_TYPE, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(l1bProduct, targetProduct);
        targetProduct.setStartTime(l1bProduct.getStartTime());
        targetProduct.setEndTime(l1bProduct.getEndTime());

        slopeBand = createBand(targetProduct, SLOPE_BAND_NAME, SLOPE_BAND_DESCRIPTION, SLOPE_BAND_UNIT);
        aspectBand = createBand(targetProduct, ASPECT_BAND_NAME, ASPECT_BAND_DESCRIPTION, ASPECT_BAND_UNIT);
        orientationBand = createBand(targetProduct, ORIENTATION_BAND_NAME, ORIENTATION_BAND_DESCRIPTION, ORIENTATION_BAND_UNIT);

        return targetProduct;
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixMerisSlopeAspectOrientationOp.class);
        }
    }
}
