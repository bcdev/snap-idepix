package org.esa.s3tbx.idepix.algorithms.olci;

import org.esa.s3tbx.idepix.core.AlgorithmSelector;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.util.IdepixIO;
import org.esa.s3tbx.idepix.operators.BasisOp;
import org.esa.s3tbx.idepix.operators.IdepixProducts;
import org.esa.s3tbx.processor.rad2refl.Sensor;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * The Idepix pixel classification operator for OLCI products.
 * <p>
 * Initial implementation:
 * - pure neural net approach which uses MERIS heritage bands only
 * - no auxdata available yet (such as 'L2 auxdata' for MERIS)
 * <p>
 * Currently resulting limitations:
 * - no cloud shadow flag
 * - glint flag over water just taken from 'sun_glint_risk' in L1 'quality_flags' band
 * <p>
 * Advanced algorithm to be defined which makes use of more bands.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel3.Olci",
        category = "Optical/Pre-Processing",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI.")
public class OlciOp extends BasisOp {
    @SourceProduct(alias = "sourceProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private boolean outputRadiance;
    private boolean outputRad2Refl;

    @Parameter(description = "The list of radiance bands to write to target product.",
            label = "Select TOA radiances to write to the target product",
            valueSet = {
                    "Oa01_radiance", "Oa02_radiance", "Oa03_radiance", "Oa04_radiance", "Oa05_radiance",
                    "Oa06_radiance", "Oa07_radiance", "Oa08_radiance", "Oa09_radiance", "Oa10_radiance",
                    "Oa11_radiance", "Oa12_radiance", "Oa13_radiance", "Oa14_radiance", "Oa15_radiance",
                    "Oa16_radiance", "Oa17_radiance", "Oa18_radiance", "Oa19_radiance", "Oa20_radiance",
                    "Oa21_radiance"
            },
            defaultValue = "")
    String[] radianceBandsToCopy;

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select TOA reflectances to write to the target product",
            valueSet = {
                    "Oa01_reflectance", "Oa02_reflectance", "Oa03_reflectance", "Oa04_reflectance", "Oa05_reflectance",
                    "Oa06_reflectance", "Oa07_reflectance", "Oa08_reflectance", "Oa09_reflectance", "Oa10_reflectance",
                    "Oa11_reflectance", "Oa12_reflectance", "Oa13_reflectance", "Oa14_reflectance", "Oa15_reflectance",
                    "Oa16_reflectance", "Oa17_reflectance", "Oa18_reflectance", "Oa19_reflectance", "Oa20_reflectance",
                    "Oa21_reflectance"
            },
//            defaultValue = "Oa08_reflectance,Oa10_reflectance")
            defaultValue = "")
    String[] reflBandsToCopy;

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Compute a cloud shadow (experimental option)",
            description = " If applied, a cloud shadow is computed. " +
                    "This requires the CTP operator (Python plugin based on CAWA) to be installed. " +
                    "Still experimental and incomplete. ")
    private boolean computeCloudShadow;

    // We only have the All NN (mp/20170324)
//    @Parameter(defaultValue = "true",
//            label = " Use 'all' NN instead of separate land and water NNs.",
//            description = " If applied, 'all' NN instead of separate land and water NNs is used. ")
//    @Parameter(defaultValue = "2.0",
//            label = " NN cloud ambiguous lower boundary (applied on WATER)",
//            description = " NN cloud ambiguous lower boundary (applied on WATER)")
//    double schillerWaterNNCloudAmbiguousLowerBoundaryValue;
//
//    @Parameter(defaultValue = "3.7",
//            label = " NN cloud ambiguous/sure separation value (applied on WATER)",
//            description = " NN cloud ambiguous cloud ambiguous/sure separation value (applied on WATER)")
//    double schillerWaterNNCloudAmbiguousSureSeparationValue;
//
//    @Parameter(defaultValue = "4.05",
//            label = " NN cloud sure/snow separation value (applied on WATER)",
//            description = " NN cloud ambiguous cloud sure/snow separation value (applied on WATER)")
//    double schillerWaterNNCloudSureSnowSeparationValue;
//
//    @Parameter(defaultValue = "1.1",
//            label = " NN cloud ambiguous lower boundary (applied on LAND)",
//            description = " NN cloud ambiguous lower boundary (applied on LAND)")
//    double schillerLandNNCloudAmbiguousLowerBoundaryValue;
//
//    @Parameter(defaultValue = "2.7",
//            label = " NN cloud ambiguous/sure separation value (applied on LAND)",
//            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
//    double schillerLandNNCloudAmbiguousSureSeparationValue;
//
//    @Parameter(defaultValue = "4.6",
//            label = " NN cloud sure/snow separation value (applied on LAND)",
//            description = " NN cloud ambiguous cloud sure/snow separation value")


//    private boolean useSchillerNNAll;

//    double schillerLandNNCloudSureSnowSeparationValue;

    private Product waterClassificationProduct;
    private Product landClassificationProduct;
    private Product mergedClassificationProduct;

    private Product rad2reflProduct;
    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> waterClassificationParameters;
    private Map<String, Object> landClassificationParameters;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        outputRadiance = radianceBandsToCopy != null && radianceBandsToCopy.length > 0;
        outputRad2Refl = reflBandsToCopy != null && reflBandsToCopy.length > 0;

        preProcess();

        setClassificationInputProducts();
        computeWaterCloudProduct();
        computeLandCloudProduct();
        mergeLandWater();

        Product olciIdepixProduct = mergedClassificationProduct;
        olciIdepixProduct.setName(sourceProduct.getName() + "_IDEPIX");
        olciIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance");
        copyOutputBands(olciIdepixProduct);
        ProductUtils.copyFlagBands(sourceProduct, olciIdepixProduct, true);   // we need the L1b flag!

        if (computeCloudShadow) {
            Map<String, Product> ctpSourceProducts = new HashMap<>();
            ctpSourceProducts.put("l1b", olciIdepixProduct);
            Product ctpProduct = GPF.createProduct("py_olci_ctp_op", GPF.NO_PARAMS, ctpSourceProducts);
            ProductUtils.copyBand("ctp", ctpProduct, olciIdepixProduct, true);
            olciIdepixProduct.getBand("ctp").setUnit("hPa");
            // todo: implement cloud shadow algorithm with CTP based on stuff used for MERIS
        }

        targetProduct = olciIdepixProduct;
    }

    private void preProcess() {
        rad2reflProduct = IdepixProducts.computeRadiance2ReflectanceProduct(sourceProduct, Sensor.OLCI);

        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", IdepixConstants.LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", IdepixConstants.OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", IdepixConstants.OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);
    }

    private void setLandClassificationParameters() {
        landClassificationParameters = new HashMap<>();
        landClassificationParameters.put("copyAllTiePoints", true);
        landClassificationParameters.put("outputSchillerNNValue", outputSchillerNNValue);
        // We only have the All NN (mp/20170324)
//        landClassificationParameters.put("useSchillerNNAll", true);
//        landClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
//                                         schillerLandNNCloudAmbiguousLowerBoundaryValue);
//        landClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
//                                         schillerLandNNCloudAmbiguousSureSeparationValue);
//        landClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
//                                         schillerLandNNCloudSureSnowSeparationValue);
    }

    private void setWaterClassificationParameters() {
        waterClassificationParameters = new HashMap<>();
        waterClassificationParameters.put("copyAllTiePoints", true);
        waterClassificationParameters.put("outputSchillerNNValue", outputSchillerNNValue);
        // We only have the All NN (mp/20170324)
//        waterClassificationParameters.put("useSchillerNNAll", useSchillerNNAll);
//        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousLowerBoundaryValue",
//                                          schillerWaterNNCloudAmbiguousLowerBoundaryValue);
//        waterClassificationParameters.put("ccSchillerNNCloudAmbiguousSureSeparationValue",
//                                          schillerWaterNNCloudAmbiguousSureSeparationValue);
//        waterClassificationParameters.put("ccSchillerNNCloudSureSnowSeparationValue",
//                                          schillerWaterNNCloudSureSnowSeparationValue);
    }

    private void computeWaterCloudProduct() {
        setWaterClassificationParameters();
        waterClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OlciWaterClassificationOp.class),
                                                       waterClassificationParameters, classificationInputProducts);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
    }

    private void computeLandCloudProduct() {
        setLandClassificationParameters();
        landClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OlciLandClassificationOp.class),
                                                      landClassificationParameters, classificationInputProducts);
    }

    private void mergeLandWater() {
        Map<String, Product> mergeInputProducts = new HashMap<>();
        mergeInputProducts.put("landClassif", landClassificationProduct);
        mergeInputProducts.put("waterClassif", waterClassificationProduct);

        Map<String, Object> mergeClassificationParameters = new HashMap<>();
        mergeClassificationParameters.put("copyAllTiePoints", true);
        mergeClassificationParameters.put("computeCloudBuffer", computeCloudBuffer);
        mergeClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        mergedClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OlciMergeLandWaterOp.class),
                                                        mergeClassificationParameters, mergeInputProducts);
    }

    private void copyOutputBands(Product targetProduct) {
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        OlciUtils.setupOlciClassifBitmask(targetProduct);
        if (outputRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, radianceBandsToCopy);
        }
        if (outputRad2Refl) {
            IdepixIO.addOlciRadiance2ReflectanceBands(rad2reflProduct, targetProduct, reflBandsToCopy);
        }
        if (computeCloudShadow) {
            IdepixIO.addCawaBands(sourceProduct, targetProduct);
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OlciOp.class);
        }
    }
}
