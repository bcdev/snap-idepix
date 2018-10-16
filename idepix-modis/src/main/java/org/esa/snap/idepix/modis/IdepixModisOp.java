package org.esa.snap.idepix.modis;

import org.esa.s3tbx.idepix.core.AlgorithmSelector;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.operators.BasisOp;
import org.esa.s3tbx.idepix.core.util.IdepixIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGeoCoding;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

import static org.esa.snap.idepix.modis.IdepixModisConstants.MODIS_WATER_MASK_BAND_NAME;

/**
 * Idepix operator for pixel identification and classification for MODIS
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Snap.Idepix.Modis",
        category = "Optical/Pre-Processing",
        version = "2.2",
        authors = "Olaf Danne, Marco Zuehlke",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for MODIS.")
public class IdepixModisOp extends BasisOp {

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select TOA reflectances to write to the target product",
            valueSet = {
                    "EV_1KM_RefSB_8", "EV_1KM_RefSB_9", "EV_1KM_RefSB_10", "EV_1KM_RefSB_11", "EV_1KM_RefSB_12",
                    "EV_1KM_RefSB_13lo", "EV_1KM_RefSB_13hi", "EV_1KM_RefSB_14lo", "EV_1KM_RefSB_14hi", "EV_1KM_RefSB_15",
                    "EV_1KM_RefSB_16", "EV_1KM_RefSB_17", "EV_1KM_RefSB_18", "EV_1KM_RefSB_19", "EV_1KM_RefSB_26",
                    "EV_250_Aggr1km_RefSB_1", "EV_250_Aggr1km_RefSB_2",
                    "EV_500_Aggr1km_RefSB_3", "EV_500_Aggr1km_RefSB_4", "EV_500_Aggr1km_RefSB_5",
                    "EV_500_Aggr1km_RefSB_6", "EV_500_Aggr1km_RefSB_7"
            },
            defaultValue = "")
    private String[] reflBandsToCopy;

    @Parameter(description = "The list of emissive bands to write to target product.",
            label = "Select Emissive bands to write to the target product",
            valueSet = {
                    "EV_1KM_Emissive_20", "EV_1KM_Emissive_21", "EV_1KM_Emissive_22", "EV_1KM_Emissive_23",
                    "EV_1KM_Emissive_24", "EV_1KM_Emissive_25", "EV_1KM_Emissive_27", "EV_1KM_Emissive_28",
                    "EV_1KM_Emissive_29", "EV_1KM_Emissive_30", "EV_1KM_Emissive_31", "EV_1KM_Emissive_32",
                    "EV_1KM_Emissive_33", "EV_1KM_Emissive_34", "EV_1KM_Emissive_35", "EV_1KM_Emissive_36"
            },
            defaultValue = "")
    private String[] emissiveBandsToCopy;

    @Parameter(defaultValue = "true",
            label = " Process only products with DayNightFlag = 'Day'")
    private boolean processDayProductsOnly;

    @Parameter(defaultValue = "CLOUD_CONSERVATIVE",
            valueSet = {"CLEAR_SKY_CONSERVATIVE", "CLOUD_CONSERVATIVE"},
            label = " Strength of cloud flagging",
            description = "Strength of cloud flagging. In case of 'CLOUD_CONSERVATIVE', more pixels might be flagged as cloud.")
    private String cloudFlaggingStrength;

    @Parameter(defaultValue = "1", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "150", valueSet = {"1000", "150", "50"},
            label = " Resolution of land-water mask (m/pixel)",
            description = "Resolution of used land-water mask in meters per pixel")
    private int waterMaskResolution;


    @Parameter(defaultValue = "1.035",     // this does not work over land!
            label = " NN cloud ambiguous lower boundary",
            description = " NN cloud ambiguous lower boundary")
    double nnCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.35",
            label = " NN cloud ambiguous/sure separation value",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    double nnCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.2",
            label = " NN cloud sure/snow separation value",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    double nnCloudSureSnowSeparationValue;

    //    @Parameter(defaultValue = "0.08",
//            label = " 'B_NIR' threshold at 859nm (MODIS)",
//            description = "'B_NIR' threshold: 'Cloud B_NIR' set if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double bNirThresh859 = 0.08;

    //    @Parameter(defaultValue = "0.15",
//            label = " 'Dark glint' threshold at 859nm for 'cloud sure' (MODIS)",
//            description = "'Dark glint' threshold: 'Cloud sure' possible only if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double glintThresh859forCloudSure = 0.15;

    //    @Parameter(defaultValue = "0.06",
//            label = " 'Dark glint' threshold at 859nm for 'cloud ambiguous' (MODIS)",
//            description = "'Dark glint' threshold: 'Cloud ambiguous' possible only if EV_250_Aggr1km_RefSB_2 > THRESH.")
    private double glintThresh859forCloudAmbiguous = 0.06;

    //    @Parameter(defaultValue = "true",
//            label = " Apply brightness test (MODIS)",
//            description = "Apply brightness test: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private boolean applyBrightnessTest = true;

    //    @Parameter(defaultValue = "true",
//            label = " Apply 'OR' logic in cloud test (MODIS)",
//            description = "Apply 'OR' logic instead of 'AND' logic in cloud test (MODIS).")
    private boolean applyOrLogicInCloudTest = true;

    //    @Parameter(defaultValue = "0.125",
//               label = " Brightness test 'cloud ambiguous' threshold (MODIS)",
//               description = "Brightness test 'cloud ambiguous' threshold: EV_250_Aggr1km_RefSB_1 > THRESH (MODIS).")
    private double brightnessThreshCloudAmbiguous = 0.125;


    @SourceProduct(alias = "sourceProduct", label = "Name (MODIS L1b product)", description = "The source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "modisWaterMask",
            label = "MODIS water mask product (optional)",
            description = "MODIS water mask product (must be MOD03 or MYD03)",
            optional = true)
    private Product modisWaterMaskProduct;

    private Product srtmWaterMaskProduct;
    private Product classifProduct;

    private boolean outputRad2Refl;
    private boolean outputEmissive;

    // former user options, now fix

//    private final double glintThresh859 = 0.15;
//    private boolean applyBrightnessTest = true;
//    private boolean applyOrLogicInCloudTest;


    @Override
    public void initialize() throws OperatorException {
        applyOrLogicInCloudTest = cloudFlaggingStrength.equals("CLOUD_CONSERVATIVE");

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.MODIS);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        if (processDayProductsOnly) {
            IdepixModisUtils.checkIfDayProduct(sourceProduct);
        }

        if (modisWaterMaskProduct != null) {
            IdepixModisUtils.validateModisWaterMaskProduct(sourceProduct,
                                                           modisWaterMaskProduct,
                                                           MODIS_WATER_MASK_BAND_NAME);
        }

        outputRad2Refl = reflBandsToCopy != null && reflBandsToCopy.length > 0;
        outputEmissive = emissiveBandsToCopy != null && emissiveBandsToCopy.length > 0;

        processModis();
    }

    private void processModis() {
        Map<String, Product> occciClassifInput = new HashMap<>(4);
        computeAlgorithmInputProducts(occciClassifInput);
        Map<String, Object> occciCloudClassificationParameters = createModisPixelClassificationParameters();

        // post processing input:
        // - cloud buffer
        // - cloud shadow todo (currently exisis only for Meris)
        Map<String, Object> postProcessParameters = new HashMap<>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        Map<String, Product> postProcessInput = new HashMap<>();
        postProcessInput.put("waterMask", srtmWaterMaskProduct);

        postProcessInput.put("refl", sourceProduct);
        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixModisClassificationOp.class),
                occciCloudClassificationParameters, occciClassifInput);

        postProcessInput.put("classif", classifProduct);

        Product postProcessProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixModisPostProcessOp.class),
                postProcessParameters, postProcessInput);

        ProductUtils.copyMetadata(sourceProduct, postProcessProduct);
        setTargetProduct(postProcessProduct);
        addBandsToTargetProduct(postProcessProduct);

        final TiePointGrid latTpg = getTargetProduct().getTiePointGrid("latitude");
        final TiePointGrid lonTpg = getTargetProduct().getTiePointGrid("longitude");
        final TiePointGeoCoding tiePointGeoCoding = new TiePointGeoCoding(latTpg, lonTpg);
        getTargetProduct().setSceneGeoCoding(tiePointGeoCoding);
    }

    private void computeAlgorithmInputProducts(Map<String, Product> modisClassifInput) {
        createWaterMaskProduct();
        modisClassifInput.put("refl", sourceProduct);
        modisClassifInput.put("srtmWaterMask", srtmWaterMaskProduct);
        modisClassifInput.put("modisWaterMask", modisWaterMaskProduct);
    }


    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<>();
        waterParameters.put("resolution", waterMaskResolution);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        srtmWaterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }

    private Map<String, Object> createModisPixelClassificationParameters() {
        Map<String, Object> occciCloudClassificationParameters = new HashMap<>(1);
        occciCloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        occciCloudClassificationParameters.put("wmResolution", waterMaskResolution);
        occciCloudClassificationParameters.put("applyBrightnessTest", applyBrightnessTest);
        occciCloudClassificationParameters.put("applyOrLogicInCloudTest", applyOrLogicInCloudTest);
        occciCloudClassificationParameters.put("nnCloudAmbiguousLowerBoundaryValue", nnCloudAmbiguousLowerBoundaryValue);
        occciCloudClassificationParameters.put("nnCloudAmbiguousSureSeparationValue", nnCloudAmbiguousSureSeparationValue);
        occciCloudClassificationParameters.put("nnCloudSureSnowSeparationValue", nnCloudSureSnowSeparationValue);

        occciCloudClassificationParameters.put("brightnessThreshCloudAmbiguous", brightnessThreshCloudAmbiguous);
        occciCloudClassificationParameters.put("glintThresh859forCloudSure", glintThresh859forCloudSure);
        occciCloudClassificationParameters.put("glintThresh859forCloudAmbiguous", glintThresh859forCloudAmbiguous);
        occciCloudClassificationParameters.put("bNirThresh859", bNirThresh859);

        return occciCloudClassificationParameters;
    }

    private void addBandsToTargetProduct(Product targetProduct) {
        if (outputRad2Refl) {
            copySourceBands(reflBandsToCopy, sourceProduct, targetProduct);
        }

        if (outputEmissive) {
            copySourceBands(emissiveBandsToCopy, sourceProduct, targetProduct);
        }
    }

    private static void copySourceBands(String[] bandsToCopy, Product sourceProduct, Product targetProduct) {
        for (String bandname : bandsToCopy) {
            if (!targetProduct.containsBand(bandname)) {
                ProductUtils.copyBand(bandname, sourceProduct, targetProduct, true);
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixModisOp.class);
        }
    }
}
