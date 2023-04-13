package org.esa.snap.idepix.meris;

import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.pointop.*;
import org.esa.snap.core.util.math.MathUtils;

import java.util.HashMap;

/**
 * Computes mountain/hill shadow for a Sentinel-3 MERIS product using slope, aspect and orientation.
 * See theory e.g. at
 * <a href="https://www.e-education.psu.edu/geog480/node/490">...</a>, or
 * <a href="https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm">...</a>
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "Idepix.Meris.MountainShadow",
        version = "2.0",
        internal = true,
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2018-2021 by Brockmann Consult",
        description = "Computes mountain/hill shadow for a Sentinel-3 OLCI product using slope, aspect and orientation.")
public class IdepixMerisMountainShadowOp extends PixelOperator {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @Parameter(label = " Extent of mountain shadow", defaultValue = "0.9", interval = "[0,1]",
            description = "Extent of mountain shadow detection")
    private double mntShadowExtent;

    private final static int SZA_INDEX = 0;
    private final static int SAA_INDEX = 1;
    private final static int OZA_INDEX = 2;
    private final static int OAA_INDEX = 3;
    private final static int SLOPE_INDEX = 4;
    private final static int ASPECT_INDEX = 5;
    private final static int ORIENTATION_INDEX = 6;


    private final static int MOUNTAIN_SHADOW_FLAG_BAND_INDEX = 0;

    public final static String MOUNTAIN_SHADOW_FLAG_BAND_NAME = "mountainShadowFlag";

    private Product saoProduct;

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", l1bProduct);
        saoProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixMerisSlopeAspectOrientationOp.class),
                GPF.NO_PARAMS, input);
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);
        productConfigurer.addBand(MOUNTAIN_SHADOW_FLAG_BAND_NAME, ProductData.TYPE_INT8);
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(SZA_INDEX, IdepixMerisConstants.MERIS_SUN_ZENITH_BAND_NAME, l1bProduct);
        sampleConfigurer.defineSample(SAA_INDEX, IdepixMerisConstants.MERIS_SUN_AZIMUTH_BAND_NAME, l1bProduct);
        sampleConfigurer.defineSample(OZA_INDEX, IdepixMerisConstants.MERIS_VIEW_ZENITH_INTERPOLATED_BAND_NAME,
                saoProduct);
        sampleConfigurer.defineSample(OAA_INDEX, IdepixMerisConstants.MERIS_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME,
                saoProduct);
        sampleConfigurer.defineSample(SLOPE_INDEX, IdepixMerisSlopeAspectOrientationOp.SLOPE_BAND_NAME, saoProduct);
        sampleConfigurer.defineSample(ASPECT_INDEX, IdepixMerisSlopeAspectOrientationOp.ASPECT_BAND_NAME, saoProduct);
        sampleConfigurer.defineSample(ORIENTATION_INDEX, IdepixMerisSlopeAspectOrientationOp.ORIENTATION_BAND_NAME,
                saoProduct);
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(MOUNTAIN_SHADOW_FLAG_BAND_INDEX, MOUNTAIN_SHADOW_FLAG_BAND_NAME);
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        final float slope = sourceSamples[SLOPE_INDEX].getFloat();
        final float aspect = sourceSamples[ASPECT_INDEX].getFloat();
        if (!Float.isNaN(slope) &&
                !Float.isNaN(aspect)) {
            final float sza = sourceSamples[SZA_INDEX].getFloat();
            final float saa = sourceSamples[SAA_INDEX].getFloat();
            final float oza = sourceSamples[OZA_INDEX].getFloat();
            final float oaa = sourceSamples[OAA_INDEX].getFloat();
            final float orientation = sourceSamples[ORIENTATION_INDEX].getFloat();

            final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
            final GeoPos geoPos = l1bProduct.getSceneGeoCoding().getGeoPos(pixelPos, null);
            final double saaApparent = IdepixMerisUtils.computeApparentSaa(sza, saa, oza, oaa, geoPos.getLat());

            targetSamples[MOUNTAIN_SHADOW_FLAG_BAND_INDEX].set(isMountainShadow(sza, (float) saaApparent,
                    slope, aspect, orientation, mntShadowExtent));
        }
    }

    /* package local for testing */
    static boolean isMountainShadow(float sza, float saa, float slope, float aspect, float orientation, double mntShadowExtent) {
        final double cosBeta = computeCosBeta(sza, saa, slope, aspect, orientation);
        return cosBeta < (-1 * (1 - mntShadowExtent));
    }

    /* package local for testing */
    static double computeCosBeta(float sza, float saa, float slope, float aspect, float orientation) {
        return Math.cos(sza * MathUtils.DTOR) * Math.cos(slope) + Math.sin(sza * MathUtils.DTOR) * Math.sin(slope) *
                Math.cos(saa * MathUtils.DTOR - (aspect + orientation));
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixMerisMountainShadowOp.class);
        }
    }
}
