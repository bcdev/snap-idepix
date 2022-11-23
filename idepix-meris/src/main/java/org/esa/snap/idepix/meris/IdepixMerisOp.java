package org.esa.snap.idepix.meris;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
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
import org.esa.snap.idepix.meris.reprocessing.Meris3rd4thReprocessingAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for MERIS products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Meris",
        category = "Optical/Pre-Processing",
        version = "3.1",
        authors = "Olaf Danne",
        copyright = "(c) 2016-2021 by Brockmann Consult",
        description = "Pixel identification and classification for MERIS.")
public class IdepixMerisOp extends BasisOp {

    @SourceProduct(alias = "sourceProduct",
            label = "MERIS L1b product",
            description = "The MERIS L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "2.0",
            label = " NN cloud ambiguous lower boundary (applied on WATER)",
            description = " NN cloud ambiguous lower boundary (applied on WATER)")
    double schillerWaterNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.7",
            label = " NN cloud ambiguous/sure separation value (applied on WATER)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value (applied on WATER)")
    double schillerWaterNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.05",
            label = " NN cloud sure/snow separation value (applied on WATER)",
            description = " NN cloud ambiguous cloud sure/snow separation value (applied on WATER)")
    double schillerWaterNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary (applied on LAND)",
            description = " NN cloud ambiguous lower boundary (applied on LAND)")
    double schillerLandNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value (applied on LAND)",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    double schillerLandNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value (applied on LAND)",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    double schillerLandNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
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

    private final String[] radianceBandsToCopy = {"M13_radiance", "M14_radiance", "M15_radiance"};
    private final String[] fluxBandsToCopy = {"solar_flux_band_13", "solar_flux_band_14", "solar_flux_band_15"};


    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.MERIS);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        if (IdepixIO.isMeris4thReprocessingL1bProduct(sourceProduct.getProductType())) {
            // adapt to 3rd reprocessing...
            Meris3rd4thReprocessingAdapter reprocessingAdapter = new Meris3rd4thReprocessingAdapter();
            inputProductToProcess = reprocessingAdapter.convertToLowerVersion(sourceProduct);

            // TPs needed in Idepix product for WV CCI Phase 2 (keep their original names, stay in line with OLCI):
            ProductUtils.copyTiePointGrid("total_columnar_water_vapour", sourceProduct, inputProductToProcess);
            ProductUtils.copyTiePointGrid("sea_level_pressure", sourceProduct, inputProductToProcess);
            ProductUtils.copyTiePointGrid("horizontal_wind_vector_1", sourceProduct, inputProductToProcess);
            ProductUtils.copyTiePointGrid("horizontal_wind_vector_2", sourceProduct, inputProductToProcess);
            for (int i = 1; i <= IdepixMerisConstants.MERIS_NUM_TEMPERATURE_PROFILES; i++) {
                ProductUtils.copyTiePointGrid("atmospheric_temperature_profile_pressure_level_" + i,
                        sourceProduct, inputProductToProcess);
            }
        } else {
            inputProductToProcess = sourceProduct;
        }

        preProcess();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();
        postProcess();

        targetProduct = postProcessingProduct;

        targetProduct = IdepixIO.cloneProduct(mergedClassificationProduct, true);
//        targetProduct.setAutoGrouping("radiance:reflectance");

        Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());

        copyOutputBands();
        if (!IdepixIO.isMeris4thReprocessingL1bProduct(sourceProduct.getProductType())) {
            ProductUtils.copyFlagBands(inputProductToProcess, targetProduct, true);   // we need the L1b flag!
        }
    }

    private void preProcess() {
        rad2reflProduct = IdepixMerisUtils.computeRadiance2ReflectanceProduct(inputProductToProcess);
        if (computeCloudShadow) {
            ctpProduct = IdepixMerisUtils.computeCloudTopPressureProduct(inputProductToProcess);
        }

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
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        IdepixMerisUtils.setupMerisClassifBitmask(targetProduct);
        IdepixIO.addRadianceBands(sourceProduct, targetProduct, radianceBandsToCopy);
        IdepixIO.addSolarFluxBands(sourceProduct, targetProduct, fluxBandsToCopy);
        ProductUtils.copyBand("altitude", sourceProduct, targetProduct, true);
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
