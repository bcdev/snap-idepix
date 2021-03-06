package org.esa.snap.idepix.olci.mountainshadow;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.pointop.*;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.olci.IdepixOlciConstants;

/**
 * Computes mountain/hill shadow for a Sentinel-3 OLCI product using slope, aspect and orientation.
 * See theory e.g. at
 * https://www.e-education.psu.edu/geog480/node/490, or
 * https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "Idepix.Olci.MountainShadow",
        version = "2.0",
        internal = true,
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2018-2021 by Brockmann Consult",
        description = "Computes mountain/hill shadow for a Sentinel-3 OLCI product using slope, aspect and orientation.\n" +
                " See theory e.g. at \n" +
                " https://www.e-education.psu.edu/geog480/node/490, or\n" +
                " https://desktop.arcgis.com/en/arcmap/10.3/tools/spatial-analyst-toolbox/how-hillshade-works.htm")
public class IdepixOlciMountainShadowOp extends PixelOperator {

    @SourceProduct
    private Product sourceProduct;

    private final static int SZA_INDEX = 0;
    private final static int SAA_INDEX = 1;
    private final static int SLOPE_INDEX = 2;
    private final static int ASPECT_INDEX = 3;
    private final static int ORIENTATION_INDEX = 4;
    private final static int CLASSIF_INDEX = 5;
    private final static int ELEVATION_INDEX = 6;

    private final static int MOUNTAIN_SHADOW_FLAG_BAND_INDEX = 0;

    private final static double SHADOW_THRESHOLD = 0;

    public final static String MOUNTAIN_SHADOW_FLAG_BAND_NAME = "mountainShadowFlag";

    private Product saoProduct;

    @Override
    protected void prepareInputs() throws OperatorException {
        super.prepareInputs();
        sourceProduct = getSourceProduct();
        if (sourceProduct.getBand(IdepixOlciSlopeAspectOrientationOp.SLOPE_BAND_NAME) == null ||
                sourceProduct.getBand(IdepixOlciSlopeAspectOrientationOp.ASPECT_BAND_NAME) == null ||
                sourceProduct.getBand(IdepixOlciSlopeAspectOrientationOp.ORIENTATION_BAND_NAME) == null) {
            saoProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciSlopeAspectOrientationOp.class),
                    GPF.NO_PARAMS, sourceProduct);
        } else {
            saoProduct = sourceProduct;
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        super.configureTargetProduct(productConfigurer);
        productConfigurer.addBand(MOUNTAIN_SHADOW_FLAG_BAND_NAME, ProductData.TYPE_INT8);
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        sampleConfigurer.defineSample(SZA_INDEX, IdepixOlciConstants.OLCI_SUN_ZENITH_BAND_NAME, sourceProduct);
        sampleConfigurer.defineSample(SAA_INDEX, IdepixOlciConstants.OLCI_SUN_AZIMUTH_BAND_NAME, sourceProduct);
        sampleConfigurer.defineSample(SLOPE_INDEX, IdepixOlciSlopeAspectOrientationOp.SLOPE_BAND_NAME, saoProduct);
        sampleConfigurer.defineSample(ASPECT_INDEX, IdepixOlciSlopeAspectOrientationOp.ASPECT_BAND_NAME, saoProduct);
        sampleConfigurer.defineSample(ORIENTATION_INDEX, IdepixOlciSlopeAspectOrientationOp.ORIENTATION_BAND_NAME, saoProduct);
        sampleConfigurer.defineSample(CLASSIF_INDEX, IdepixConstants.CLASSIF_BAND_NAME, sourceProduct);
        sampleConfigurer.defineSample(ELEVATION_INDEX, IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME, sourceProduct);
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
            final float orientation = sourceSamples[ORIENTATION_INDEX].getFloat();
            targetSamples[MOUNTAIN_SHADOW_FLAG_BAND_INDEX].set(isMountainShadow(sza, saa, slope, aspect, orientation));
        }
    }

    /* package local for testing */
    static boolean isMountainShadow(float sza, float saa, float slope, float aspect, float orientation) {
        final double cosBeta = computeCosBeta(sza, saa, slope, aspect, orientation);
        return cosBeta < SHADOW_THRESHOLD;
    }

    /* package local for testing */
    static double computeCosBeta(float sza, float saa, float slope, float aspect, float orientation) {
        return Math.cos(sza * MathUtils.DTOR) * Math.cos(slope) + Math.sin(sza * MathUtils.DTOR) * Math.sin(slope) *
                Math.cos(saa * MathUtils.DTOR - (aspect + orientation));
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixOlciMountainShadowOp.class);
        }
    }
}
