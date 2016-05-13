package org.esa.s2tbx.s2msi.idepix.algorithms.sentinel2;

import org.esa.s2tbx.s2msi.idepix.util.AlgorithmSelector;
import org.esa.s2tbx.s2msi.idepix.util.IdepixConstants;
import org.esa.s2tbx.s2msi.idepix.util.IdepixUtils;
import org.esa.s2tbx.s2msi.idepix.operators.Sentinel2CloudBufferOp;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;

import java.util.HashMap;
import java.util.Map;

/**
 * Idepix operator for pixel identification and classification for Sentinel-2 (MSI instrument)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel2",
        category = "Optical/Pre-Processing",
        version = "2.2",
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for Sentinel-2.")
public class Sentinel2Op extends Operator {

    @Parameter(defaultValue = "true",
            label = " Write TOA Reflectances to the target product",
            description = " Write TOA Reflectances to the target product")
    private boolean copyToaReflectances;

    @Parameter(defaultValue = "true",
            label = " Write Feature Values to the target product",
            description = " Write all Feature Values to the target product")
    private boolean copyFeatureValues;

    @Parameter(defaultValue = "1.95",
            label = " NN cloud ambiguous lower boundary",
            description = " NN cloud ambiguous lower boundary")
    private double nnCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " NN cloud ambiguous/sure separation value",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    private double nnCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.3",
            label = " NN cloud sure/snow separation value",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    private double nnCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Apply NN for pixel classification purely (not combined with feature value approach)",
            description = " Apply NN for pixelclassification purely (not combined with feature value  approach)")
    private boolean applyNNPure;

    @Parameter(defaultValue = "false",
            label = " Ignore NN and only use feature value approach for pixel classification (if set, overrides previous option)",
            description = " Ignore NN and only use feature value approach for pixel classification (if set, overrides previous option)")
    private boolean ignoreNN;

    @Parameter(defaultValue = "true",
            label = " Write NN output value to the target product",
            description = " Write NN output value to the target product")
    private boolean copyNNValue = true;

//    @Parameter(defaultValue = "true",
//            label = " Refine pixel classification near coastlines",
//            description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines = false; // todo later


    //    @Parameter(defaultValue = "true", label = " Compute cloud shadow")
    private boolean computeCloudShadow = false; // todo later

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;


    @SourceProduct(alias = "l1cProduct",
            label = "Sentinel-2 MSI L1C product",
            description = "The Sentinel-2 MSI L1C product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private Product postProcessingProduct;
    private Product s2CloudProduct;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.MSI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        if (IdepixUtils.isValidSentinel2(sourceProduct)) {
            processSentinel2();
        }
    }

    private void processSentinel2() {
        Map<String, Product> input = new HashMap<>(4);
        input.put("l1c", sourceProduct);

        final Map<String, Object> pixelClassificationParameters = createPixelClassificationParameters();

        s2CloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Sentinel2ClassificationOp.class),
                                           pixelClassificationParameters, input);

        if (refineClassificationNearCoastlines || computeCloudShadow || computeCloudBuffer) {
            // Post Cloud Classification: coastline refinement, cloud shadow, cloud buffer
            computePostProcessProduct();

            targetProduct = IdepixUtils.cloneProduct(s2CloudProduct, true);

            Band cloudFlagBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLASSIF_FLAGS);
            cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixUtils.IDEPIX_CLASSIF_FLAGS).getSourceImage());
        } else {
            targetProduct = s2CloudProduct;
        }

        // new bit masks:
        IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

        setTargetProduct(targetProduct);
    }

    private void computePostProcessProduct() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1c", sourceProduct);
        input.put("s2Cloud", s2CloudProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("gaComputeCloudBuffer", computeCloudBuffer);
        params.put("gaComputeCloudShadow", computeCloudShadow);
        params.put("gaRefineClassificationNearCoastlines", refineClassificationNearCoastlines);
        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Sentinel2PostProcessOp.class),
                                                            params, input);

        if (computeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", cloudBufferWidth);
            postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Sentinel2CloudBufferOp.class),
                                                        params, input);
        } else {
            postProcessingProduct = classifiedProduct;
        }
    }

    private Map<String, Object> createPixelClassificationParameters() {
        Map<String, Object> gaCloudClassificationParameters = new HashMap<>(1);
        gaCloudClassificationParameters.put("copyToaReflectances", copyToaReflectances);
        gaCloudClassificationParameters.put("copyFeatureValues", copyFeatureValues);
        gaCloudClassificationParameters.put("applyNNPure", applyNNPure);
        gaCloudClassificationParameters.put("ignoreNN", ignoreNN);
        gaCloudClassificationParameters.put("nnCloudAmbiguousLowerBoundaryValue", nnCloudAmbiguousLowerBoundaryValue);
        gaCloudClassificationParameters.put("nnCloudAmbiguousSureSeparationValue", nnCloudAmbiguousSureSeparationValue);
        gaCloudClassificationParameters.put("nnCloudSureSnowSeparationValue", nnCloudSureSnowSeparationValue);
        gaCloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);

        return gaCloudClassificationParameters;
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(Sentinel2Op.class);
        }
    }
}
