package org.esa.snap.idepix.s2msi;

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
import org.esa.snap.dem.gpf.AddElevationOp;
import org.esa.snap.idepix.s2msi.operators.S2IdepixCloudPostProcessOp;
import org.esa.snap.idepix.s2msi.util.AlgorithmSelector;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.util.HashMap;
import java.util.Map;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES;

/**
 * IdePix operator for pixel identification and classification for Sentinel-2 (MSI instrument)
 *
 * @author Tonio Fincke
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Idepix.S2",
        category = "Optical/Pre-Processing",
        version = "8.0.2.4-ucd-beta",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2019 by Brockmann Consult",
        description = "Pixel identification and classification for Sentinel-2 MSI.")
public class S2IdepixOp extends Operator {


    @Parameter(defaultValue = "true",
            label = " Write TOA reflectances to the target product",
            description = " Write TOA reflectances to the target product")
    private boolean copyToaReflectances;

    @Parameter(defaultValue = "false",
            label = " Write feature values to the target product",
            description = " Write all feature values to the target product")
    private boolean copyFeatureValues;

    // NN stuff is deactivated unless we have a better net

    //    @Parameter(defaultValue = "1.95",
//            label = " NN cloud ambiguous lower boundary",
//            description = " NN cloud ambiguous lower boundary")
//    private double nnCloudAmbiguousLowerBoundaryValue;
    private double nnCloudAmbiguousLowerBoundaryValue = 1.95;

    //    @Parameter(defaultValue = "3.45",
//            label = " NN cloud ambiguous/sure separation value",
//            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
//    private double nnCloudAmbiguousSureSeparationValue;
    private double nnCloudAmbiguousSureSeparationValue = 3.45;

    //    @Parameter(defaultValue = "4.3",
//            label = " NN cloud sure/snow separation value",
//            description = " NN cloud ambiguous cloud sure/snow separation value")
//    private double nnCloudSureSnowSeparationValue;
    private double nnCloudSureSnowSeparationValue = 4.3;

    //    @Parameter(defaultValue = "false",
//            label = " Apply NN for pixel classification purely (not combined with feature value approach)",
//            description = " Apply NN for pixelclassification purely (not combined with feature value  approach)")
//    private boolean applyNNPure;
    private boolean applyNNPure = false;

    //    @Parameter(defaultValue = "false",
//            label = " Ignore NN and only use feature value approach for pixel classification (if set, overrides previous option)",
//            description = " Ignore NN and only use feature value approach for pixel classification (if set, overrides previous option)")
//    private boolean ignoreNN;
    boolean ignoreNN = true;       // currently bad results. Wait for better S2 NN.

    //    @Parameter(defaultValue = "true",
//            label = " Write NN output value to the target product",
//            description = " Write NN output value to the target product")
//    private boolean copyNNValue = true;
    private boolean copyNNValue = false;

    @Parameter(defaultValue = "true", label = " Compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(defaultValue = "false", label = " Compute cloud shadow")
    private boolean computeCloudShadow = false;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer also for cloud ambiguous pixels")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

    // temporarily disabled the following threshold options. TODO: clarify their meaning! They aren't explained anywhere.
//    @Parameter(defaultValue = "0.01",
//            label = " Threshold CW_THRESH",
//            description = " Threshold CW_THRESH")
//    private double cwThresh;
    private double cwThresh = 0.01;

    //    @Parameter(defaultValue = "-0.11",
//            label = " Threshold GCL_THRESH",
//            description = " Threshold GCL_THRESH")
//    private double gclThresh;
    private double gclThresh = -0.11;

    //    @Parameter(defaultValue = "0.01",
//            label = " Threshold CL_THRESH",
//            description = " Threshold CL_THRESH")
//    private double clThresh;
    private double clThresh = 0.01;

    @Parameter(description = "The digital elevation model.", defaultValue = "SRTM 3Sec", label = "Digital Elevation Model")
    private String demName = "SRTM 3Sec";


    @SourceProduct(alias = "l1cProduct",
            label = "Sentinel-2 MSI L1C product",
            description = "The Sentinel-2 MSI L1C product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = S2IdepixUtils.validateInputProduct(sourceProduct, AlgorithmSelector.MSI);
        if (!inputProductIsValid) {
            throw new OperatorException(S2IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }
        sourceProduct.setPreferredTileSize(610, 610);
        if (S2IdepixUtils.isValidSentinel2(sourceProduct)) {
            processSentinel2();
        }
    }

    private void processSentinel2() {

        Product elevationProduct;
        if (sourceProduct.containsBand(S2IdepixConstants.ELEVATION_BAND_NAME)) {
            elevationProduct = sourceProduct;
        } else {
            AddElevationOp elevationOp = new AddElevationOp();
            elevationOp.setParameterDefaultValues();
            elevationOp.setParameter("demName", demName);
            elevationOp.setSourceProduct(sourceProduct);
            elevationProduct = elevationOp.getTargetProduct();
        }

        Map<String, Product> inputProducts = new HashMap<>(4);
        inputProducts.put("l1c", sourceProduct);
        inputProducts.put("elevation", elevationProduct);

        Product s2ClassifProduct = createS2ClassificationProduct(inputProducts);

        // Post Cloud Classification: cloud shadow, cloud buffer, mountain shadow
        Product postProcessingProduct = computePostProcessProduct(sourceProduct, s2ClassifProduct);

        targetProduct = S2IdepixUtils.cloneProduct(s2ClassifProduct, true);
        if (!copyToaReflectances) {
            removeReflectances(targetProduct);
        }

        Band cloudFlagBand = targetProduct.getBand(S2IdepixConstants.IDEPIX_CLASSIF_FLAGS);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(S2IdepixConstants.IDEPIX_CLASSIF_FLAGS).getSourceImage());

        // new bit masks:
        S2IdepixUtils.setupIdepixCloudscreeningBitmasks(targetProduct);

        setTargetProduct(targetProduct);
    }

    private static void removeReflectances(Product product) {
        for (String reflectanceBandName : S2_MSI_REFLECTANCE_BAND_NAMES) {
            if (product.containsBand(reflectanceBandName)) {
                final Band b = product.getBand(reflectanceBandName);
                product.removeBand(product.getBand(reflectanceBandName));
            }
        }
    }

    private Product createS2ClassificationProduct(Map<String, Product> inputProducts) {
        Map<String, Object> classificationParameter = new HashMap<>(20);
        classificationParameter.put("copyFeatureValues", copyFeatureValues);
        classificationParameter.put("applyNNPure", applyNNPure);
        classificationParameter.put("ignoreNN", ignoreNN);
        classificationParameter.put("nnCloudAmbiguousLowerBoundaryValue", nnCloudAmbiguousLowerBoundaryValue);
        classificationParameter.put("nnCloudAmbiguousSureSeparationValue", nnCloudAmbiguousSureSeparationValue);
        classificationParameter.put("nnCloudSureSnowSeparationValue", nnCloudSureSnowSeparationValue);
        classificationParameter.put("cloudBufferWidth", cloudBufferWidth);
        classificationParameter.put("cwThresh", cwThresh);
        classificationParameter.put("gclThresh", gclThresh);
        classificationParameter.put("clThresh", clThresh);
        classificationParameter.put("demName", demName);

        return GPF.createProduct(OperatorSpi.getOperatorAlias(S2IdepixClassificationOp.class),
                                 classificationParameter, inputProducts);
    }

    private Product computePostProcessProduct(Product l1cProduct, Product classificationProduct) {

        // todo: Shouldn't this be actually within the if clause?
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1c", l1cProduct);
        input.put("s2Cloud", classificationProduct);
        input.put("classifiedProduct", classificationProduct);
        Map<String, Object> paramsBuffer = new HashMap<>();
        paramsBuffer.put("cloudBufferWidth", cloudBufferWidth);
        paramsBuffer.put("computeCloudBufferForCloudAmbiguous", computeCloudBufferForCloudAmbiguous);
        Product cloudBufferProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(S2IdepixCloudPostProcessOp.class),
                                                       paramsBuffer, input);

        Product postProduct = cloudBufferProduct;

        if (computeCloudShadow || computeMountainShadow || computeCloudBuffer) {
            HashMap<String, Product> inputShadow = new HashMap<>();
            inputShadow.put("l1c", l1cProduct);
            inputShadow.put("s2Classif", classificationProduct);
            inputShadow.put("s2CloudBuffer", cloudBufferProduct);
            Map<String, Object> params = new HashMap<>();
            params.put("computeMountainShadow", computeMountainShadow);
            params.put("computeCloudShadow", computeCloudShadow);
            params.put("computeCloudBuffer", computeCloudBuffer);
            params.put("cloudBufferWidth", cloudBufferWidth);
            params.put("computeCloudBufferForCloudAmbiguous", computeCloudBufferForCloudAmbiguous);
            params.put("mode", "LandWater");
            params.put("demName", demName);
            postProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(S2IdepixPostProcessOp.class),
                                                      params, inputShadow);
        }

        return postProduct;
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixOp.class);
        }
    }
}
