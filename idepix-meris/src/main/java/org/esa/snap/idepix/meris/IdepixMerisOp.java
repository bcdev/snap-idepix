package org.esa.snap.idepix.meris;

import eu.esa.opt.dataio.s3.meris.reprocessing.Meris3rd4thReprocessingAdapter;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.operators.CloudBufferOp;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for MERIS products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Meris",
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        version = "3.0",
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for MERIS.")
public class IdepixMerisOp extends BasisOp {

    @SourceProduct(alias = "sourceProduct",
            label = "MERIS L1b product",
            description = "The MERIS L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    private boolean outputRadiance = false;
    private boolean outputRad2Refl = false;

    @Parameter(description = "The list of radiance bands to write to target product.",
            label = "Select TOA radiances to write to the target product",
            valueSet = {
                    "radiance_1", "radiance_2", "radiance_3", "radiance_4", "radiance_5",
                    "radiance_6", "radiance_7", "radiance_8", "radiance_9", "radiance_10",
                    "radiance_11", "radiance_12", "radiance_13", "radiance_14", "radiance_15"
            })
    String[] radianceBandsToCopy;

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select TOA reflectances to write to the target product",
            valueSet = {
                    "reflectance_1", "reflectance_2", "reflectance_3", "reflectance_4", "reflectance_5",
                    "reflectance_6", "reflectance_7", "reflectance_8", "reflectance_9", "reflectance_10",
                    "reflectance_11", "reflectance_12", "reflectance_13", "reflectance_14", "reflectance_15"
            })
    String[] reflBandsToCopy;

    @Parameter(defaultValue = "false",
            label = "Write NN value to the target product.",
            description = "If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = "NN cloud ambiguous lower boundary (applied on WATER)",
            description = "NN cloud ambiguous lower boundary (applied on WATER)")
    double schillerWaterNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = "NN cloud ambiguous/sure separation value (applied on WATER)",
            description = "NN cloud ambiguous cloud ambiguous/sure separation value (applied on WATER)")
    double schillerWaterNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = "NN cloud sure/snow separation value (applied on WATER)",
            description = "NN cloud ambiguous cloud sure/snow separation value (applied on WATER)")
    double schillerWaterNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "1.1",
            label = "NN cloud ambiguous lower boundary (applied on LAND)",
            description = "NN cloud ambiguous lower boundary (applied on LAND)")
    double schillerLandNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = "NN cloud ambiguous/sure separation value (applied on LAND)",
            description = "NN cloud ambiguous cloud ambiguous/sure separation value")
    double schillerLandNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = "NN cloud sure/snow separation value (applied on LAND)",
            description = "NN cloud ambiguous cloud sure/snow separation value")
    double schillerLandNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "true", label = "Compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(label = "Extent of mountain shadow", defaultValue = "0.9", interval = "[0,1]",
            description = "Extent of mountain shadow detection")
    private double mntShadowExtent;

    @Parameter(defaultValue = "true",
            label = "Compute cloud shadow",
            description = "Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true", label = "Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private Product waterClassificationProduct;
    private Product landClassificationProduct;
    private Product mergedClassificationProduct;
    private Product postProcessingProduct;

    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product waterMaskProduct;

    private Product inputProductToProcess;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> waterClassificationParameters;
    private Map<String, Object> landClassificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.MERIS);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        final boolean isMeris4thReprocessingProduct =
                IdepixIO.isMeris4thReprocessingL1bProduct(sourceProduct.getProductType());
        computeMountainShadow = computeMountainShadow && isMeris4thReprocessingProduct;
        if (isMeris4thReprocessingProduct) {
            // adapt to 3rd reprocessing...
            Meris3rd4thReprocessingAdapter reprocessingAdapter = new Meris3rd4thReprocessingAdapter();
            inputProductToProcess = reprocessingAdapter.convertToLowerVersion(sourceProduct);
            if (computeMountainShadow) {
                // altitude TPG is too coarse in this case!
                ProductUtils.copyBand(IdepixMerisConstants.MERIS_4RP_ALTITUDE_BAND_NAME, sourceProduct,
                        inputProductToProcess, true);
            }
        } else {
            inputProductToProcess = sourceProduct;
        }

        outputRadiance = radianceBandsToCopy != null && radianceBandsToCopy.length > 0;
        outputRad2Refl = reflBandsToCopy != null && reflBandsToCopy.length > 0;

        preProcess();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();
        postProcess();

        targetProduct = postProcessingProduct;

        targetProduct = IdepixIO.cloneProduct(mergedClassificationProduct, true);
        targetProduct.setAutoGrouping("radiance:reflectance");

        Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());

        copyOutputBands();
        ProductUtils.copyFlagBands(inputProductToProcess, targetProduct, true);   // we need the L1b flag!
    }

    private void preProcess() {
        rad2reflProduct = IdepixMerisUtils.computeRadiance2ReflectanceProduct(inputProductToProcess);
        ctpProduct = IdepixMerisUtils.computeCloudTopPressureProduct(inputProductToProcess);

        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", IdepixConstants.LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", IdepixConstants.OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", IdepixConstants.OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, inputProductToProcess);
    }

    private void setLandClassificationParameters() {
        landClassificationParameters = new HashMap<>();
        landClassificationParameters.put("copyAllTiePoints", true);
        landClassificationParameters.put("outputSchillerNNValue",
                outputSchillerNNValue);
        landClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                schillerLandNNCloudAmbiguousLowerBoundaryValue);
        landClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                schillerLandNNCloudAmbiguousSureSeparationValue);
        landClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                schillerLandNNCloudSureSnowSeparationValue);
    }

    private void setWaterClassificationParameters() {
        waterClassificationParameters = new HashMap<>();
        waterClassificationParameters.put("copyAllTiePoints", true);
        waterClassificationParameters.put("outputSchillerNNValue",
                outputSchillerNNValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
                schillerWaterNNCloudAmbiguousLowerBoundaryValue);
        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
                schillerWaterNNCloudAmbiguousSureSeparationValue);
        waterClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
                schillerWaterNNCloudSureSnowSeparationValue);
    }

    private void computeWaterCloudProduct() {
        setWaterClassificationParameters();
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", inputProductToProcess);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);

        waterClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixMerisWaterClassificationOp.class),
                waterClassificationParameters, classificationInputProducts);
    }

    private void computeLandCloudProduct() {
        setLandClassificationParameters();
        landClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixMerisLandClassificationOp.class),
                landClassificationParameters, classificationInputProducts);
    }

    private void mergeLandWater() {
        Map<String, Product> mergeInputProducts = new HashMap<>();
        mergeInputProducts.put("landClassif", landClassificationProduct);
        mergeInputProducts.put("waterClassif", waterClassificationProduct);

        Map<String, Object> mergeClassificationParameters = new HashMap<>();
        mergeClassificationParameters.put("copyAllTiePoints", true);
        mergedClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixMerisMergeLandWaterOp.class),
                mergeClassificationParameters, mergeInputProducts);
    }

    private void postProcess() {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", inputProductToProcess);
        input.put("merisCloud", mergedClassificationProduct);
        input.put("ctp", ctpProduct);
        input.put("waterMask", waterMaskProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudShadow", computeCloudShadow);
        params.put("computeMountainShadow", computeMountainShadow);
        params.put("mntShadowExtent", mntShadowExtent);
        params.put("refineClassificationNearCoastlines", true);  // always an improvement

        final Product classifiedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixMerisPostProcessOp.class),
                params, input);

        if (computeCloudBuffer) {
            input = new HashMap<>();
            input.put("classifiedProduct", classifiedProduct);
            params = new HashMap<>();
            params.put("cloudBufferWidth", cloudBufferWidth);
            postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudBufferOp.class),
                    params, input);
        } else {
            postProcessingProduct = classifiedProduct;
        }
    }

    private void copyOutputBands() {
        ProductUtils.copyMetadata(inputProductToProcess, targetProduct);
        IdepixMerisUtils.setupMerisClassifBitmask(targetProduct);
        if (outputRadiance) {
            IdepixIO.addRadianceBands(inputProductToProcess, targetProduct, radianceBandsToCopy);
        }
        if (outputRad2Refl) {
            IdepixMerisUtils.addMerisRadiance2ReflectanceBands(rad2reflProduct, targetProduct, reflBandsToCopy);
        }

        for (Band b : targetProduct.getBands()) {
            if (b.getName().contains("radiance") || b.getName().contains("reflectance")) {
                b.setValidPixelExpression("!l1_flags.INVALID");
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixMerisOp.class);
        }
    }
}
