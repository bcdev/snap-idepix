package org.esa.snap.idepix.avhrr;

import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification for AVHRR products
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Idepix.Noaa.Avhrr",
        internal = false,
        category = "Optical/Pre-Processing",
        version = "3.0",
        authors = "Olaf Danne, Grit Kirches",
        copyright = "(c) 2016, 2019 Brockmann Consult",
        description = "Pixel identification and classification for AVHRR.")
public class AvhrrOp extends BasisOp {

    private static final int LAND_WATER_MASK_RESOLUTION = 1000;
    private static final int SUBSAMPLING_FACTOR_X = 1;
    private static final int SUBSAMPLING_FACTOR_Y = 1;

    @SourceProduct(alias = "sourceProduct",
            label = "AVHRR2 product",
            description = "The AVHRR source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product waterMaskProduct;


    @Parameter(defaultValue = "false", label = " Copy input radiance/reflectance bands")
    private boolean copyRadiances = false;

    @Parameter(defaultValue = "false", label = " Copy geometry bands")
    private boolean copyGeometries = false;

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "2.15",
            label = " NN cloud ambiguous lower boundary ",
            description = " NN cloud ambiguous lower boundary ")
    private double avhrrNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " NN cloud ambiguous/sure separation value ",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value ")
    private double avhrrNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
            label = " NN cloud sure/snow separation value ",
            description = " NN cloud ambiguous cloud sure/snow separation value ")
    private double avhrrNNCloudSureSnowSeparationValue;

    private Map<String, Object> cloudClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.AVHRR);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        cloudClassificationParameters = createCloudClassificationParameters();
        if (sourceProduct.getDescription() != null &&
                sourceProduct.getDescription().equalsIgnoreCase(IdepixConstants.AVHRR_L1b_TIMELINE_DESCRIPTION)){
            processAvhrrTimeLine();
        } else {
            processAvhrr();
        }
    }

    private Map<String, Object> createCloudClassificationParameters() {
        Map<String, Object> cloudClassificationParameters = new HashMap<>(1);
        cloudClassificationParameters.put("copyRadiances", copyRadiances);
        cloudClassificationParameters.put("copyGeometries", copyGeometries);
        cloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        cloudClassificationParameters.put("avhrrNNCloudAmbiguousLowerBoundaryValue",
                                             avhrrNNCloudAmbiguousLowerBoundaryValue);
        cloudClassificationParameters.put("avhrrNNCloudAmbiguousSureSeparationValue",
                                             avhrrNNCloudAmbiguousSureSeparationValue);
        cloudClassificationParameters.put("avhrrNNCloudSureSnowSeparationValue",
                                             avhrrNNCloudSureSnowSeparationValue);

        return cloudClassificationParameters;
    }


    private void processAvhrrTimeLine() {
        AbstractAvhrrClassificationOp timelineClassificationOp = new AvhrrTimelineClassificationOp();

        timelineClassificationOp.setParameterDefaultValues();
        for (String key : cloudClassificationParameters.keySet()) {
            timelineClassificationOp.setParameter(key, cloudClassificationParameters.get(key));
        }
        timelineClassificationOp.setSourceProduct("l1b", sourceProduct);
        createWaterMaskProduct();
        timelineClassificationOp.setSourceProduct("waterMask", waterMaskProduct);

        classificationProduct = timelineClassificationOp.getTargetProduct();
        postProcess();

        targetProduct = IdepixIO.cloneProduct(classificationProduct, true);
        targetProduct.setName(sourceProduct.getName() + ".idepix");

        Band cloudFlagBand;
        cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());

    }
    private void processAvhrr() {
        AbstractAvhrrClassificationOp usgsClassificationOp = new AvhrrUSGSClassificationOp();

        usgsClassificationOp.setParameterDefaultValues();
        for (String key : cloudClassificationParameters.keySet()) {
            usgsClassificationOp.setParameter(key, cloudClassificationParameters.get(key));
        }
        usgsClassificationOp.setSourceProduct("l1b", sourceProduct);
        createWaterMaskProduct();
        usgsClassificationOp.setSourceProduct("waterMask", waterMaskProduct);

        classificationProduct = usgsClassificationOp.getTargetProduct();
        postProcess();

        targetProduct = IdepixIO.cloneProduct(classificationProduct, true);
        targetProduct.setName(sourceProduct.getName() + ".idepix");

        Band cloudFlagBand;
        cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());

    }
    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("avhrrCloud", classificationProduct);
        input.put("waterMask", waterMaskProduct);
        Map<String, Object> params = new HashMap<>();
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("computeCloudShadow", false);     // todo: we need algo
        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AvhrrPostProcessOp.class), params, input);
    }

    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<>();
        waterParameters.put("resolution", LAND_WATER_MASK_RESOLUTION);
        waterParameters.put("subSamplingFactorX", SUBSAMPLING_FACTOR_X);
        waterParameters.put("subSamplingFactorY", SUBSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrOp.class);
        }
    }
}
