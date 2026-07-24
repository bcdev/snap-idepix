package org.esa.snap.idepix.c3solcislstr;

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
import org.esa.snap.idepix.c3solcislstr.mc.Multivariate;
import org.esa.snap.idepix.c3solcislstr.mc.UniformVariate;
import org.esa.snap.idepix.c3solcislstr.mc.generators.LatinHypercube;
import org.esa.snap.idepix.c3solcislstr.mc.generators.Melg;
import org.esa.snap.idepix.c3solcislstr.mc.generators.Pcg;
import org.esa.snap.idepix.c3solcislstr.mc.generators.Sobol;
import org.esa.snap.idepix.c3solcislstr.mc.operators.CloudMaskMutationOp;
import org.esa.snap.idepix.c3solcislstr.mc.operators.InterpolationFunctionFactory;
import org.esa.snap.idepix.c3solcislstr.mc.variates.EmpiricVariate;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Rad2ReflConstants;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Sensor;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for OLCI/SLSTR synergy products.
 * Classification result can be randomly mutated.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel3.C3SOlciSlstrMutant",
        category = "Optical/Preprocessing/Masking",
        version = "3.0",
        authors = "Olaf Danne",
        internal = true,
        copyright = "(c) 2026 by Brockmann Consult",
        description = "Pixel identification and classification for C3S OLCI/SLSTR synergy products. " +
                "Classification result can be randomly mutated.")
public class C3SOlciSlstrMutantOp extends BasisOp {

    @SourceProduct(alias = "sourceProduct",
            label = "C3S OLCI/SLSTR Synergy product",
            description = "The C3S OLCI/SLSTR Synergy source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private boolean outputOlciRadiance;
    private boolean outputSlstrRadiance;
    private boolean outputOlciReflectance;
    private boolean outputSlstrReflectance;

    @Parameter(description = "The list of OLCI radiance bands to write to target product.",
            label = "Select OLCI TOA radiances to write to the target product",
            valueSet = {
                    "Oa01_radiance", "Oa02_radiance", "Oa03_radiance", "Oa04_radiance", "Oa05_radiance",
                    "Oa06_radiance", "Oa07_radiance", "Oa08_radiance", "Oa09_radiance", "Oa10_radiance",
                    "Oa11_radiance", "Oa12_radiance", "Oa13_radiance", "Oa14_radiance", "Oa15_radiance",
                    "Oa16_radiance", "Oa17_radiance", "Oa18_radiance", "Oa19_radiance", "Oa20_radiance",
                    "Oa21_radiance"
            },
            defaultValue = "")
    private String[] olciRadianceBandsToCopy;

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select OLCI TOA reflectances to write to the target product",
            valueSet = {"all",
                    "Oa01_reflectance", "Oa02_reflectance", "Oa03_reflectance", "Oa04_reflectance", "Oa05_reflectance",
                    "Oa06_reflectance", "Oa07_reflectance", "Oa08_reflectance", "Oa09_reflectance", "Oa10_reflectance",
                    "Oa11_reflectance", "Oa12_reflectance", "Oa13_reflectance", "Oa14_reflectance", "Oa15_reflectance",
                    "Oa16_reflectance", "Oa17_reflectance", "Oa18_reflectance", "Oa19_reflectance", "Oa20_reflectance",
                    "Oa21_reflectance"
            },
            defaultValue = "")
    private String[] olciReflectanceBandsToCopy;


    @Parameter(description = "The list of SLSTR radiance bands to write to target product.",
            label = "Select SLSTR TOA radiances to write to the target product",
            valueSet = {
                    "S1_radiance_an", "S2_radiance_an", "S3_radiance_an",
                    "S4_radiance_an", "S5_radiance_an", "S6_radiance_an"
            },
            defaultValue = "")
    private String[] slstrRadianceBandsToCopy;

    @Parameter(description = "The list of SLSTR radiance bands to write to target product.",
            label = "Select SLSTR TOA radiances to write to the target product",
            valueSet = {"all",
                    "S1_reflectance_an", "S2_reflectance_an", "S3_reflectance_an",
                    "S4_reflectance_an", "S5_reflectance_an", "S6_reflectance_an"
            },
            defaultValue = "")
    private String[] slstrReflectanceBandsToCopy;

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

    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;


    @Parameter(label = "Seed number",
            description = "A numeric value to seed the random number generator",
            defaultValue = "42")
    private long seed;

    @Parameter(label = "Selector",
            description = "A numeric value to select the random stream. If zero, no randomization is performed at all.",
            defaultValue = "0")
    private long selector;

    @Parameter(label = "Sampling type",
            description = "The sampling type.",
            defaultValue = "Sobol", valueSet = {"Latin hypercube", "Random", "Sobol"})
    private String samplingType;

    @Parameter(label = "Simulation count",
            description = "The number of simulations (only used for Latin hypercube sampling).",
            defaultValue = "10")
    private int simulationCount;

    @Parameter(label = "Cloud-to-clear NN threshold over land",
            description = "The NN value threshold to separate clouds from cloud-free land surface. If zero, a threshold value is generated randomly.",
            defaultValue = "0.0")
    private double cloudyClearThresholdLnd;

    @Parameter(label = "Cloud-to-clear NN threshold over water",
            description = "The NN value threshold to separate clouds from cloud-free water surface. If zero, a threshold value is generated randomly.",
            defaultValue = "0.0")
    private double cloudyClearThresholdWtr;

    @Parameter(label = "Randomly mutant cloud-clear",
            description = "If checked, the cloud mask is mutated randomly.",
            defaultValue = "true")
    private boolean randomlyMutantCloudyClear;


    private Product postProcessingProduct;

    private Product olciRad2reflProduct;
    private Product slstrRad2reflProduct;
    private Product ctpProduct;
    private Product o2CorrProduct;
    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;

    private static final String DATE_AND_TIME_OF_SOURCE = "";
    private static final String MELG = "MELG";

    private Pcg pcg;
    private Multivariate mv;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCISLSTR);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        outputOlciRadiance = olciRadianceBandsToCopy != null && olciRadianceBandsToCopy.length > 0;

        outputSlstrRadiance = slstrRadianceBandsToCopy != null && slstrRadianceBandsToCopy.length > 0;

        outputOlciReflectance = olciReflectanceBandsToCopy != null && olciReflectanceBandsToCopy.length > 0;

        outputSlstrReflectance = slstrReflectanceBandsToCopy != null && slstrReflectanceBandsToCopy.length > 0;

        if (olciReflectanceBandsToCopy != null && olciReflectanceBandsToCopy.length > 0 &&
                olciReflectanceBandsToCopy[0].equalsIgnoreCase("all")) {
            olciReflectanceBandsToCopy = Rad2ReflConstants.OLCI_REFL_BAND_NAMES;
        }

        if (slstrReflectanceBandsToCopy != null && slstrReflectanceBandsToCopy.length > 0 &&
                slstrReflectanceBandsToCopy[0].equalsIgnoreCase("all")) {
            slstrReflectanceBandsToCopy = Rad2ReflConstants.SLSTR_REFL_AN_BAND_NAMES;
        }

        preProcess();

        setClassificationInputProducts();
        Product olciSlstrIdepixProduct = computeClassificationProduct();

        // mutation part
        Product olciSlstrMutatedIdepixProduct;
        if (isMutant()) {
            pcg = new Pcg(seed, selector);
            mv = multivariate(samplingType);

            if (cloudyClearThresholdLnd == 0.0) {
                cloudyClearThresholdLnd = randomCloudyClearThreshold(mv.get(4), "threshold_pdf_lnd.dat");
            }
            if (cloudyClearThresholdWtr == 0.0) {
                cloudyClearThresholdWtr = randomCloudyClearThreshold(mv.get(5), "threshold_pdf_wtr.dat");
            }

            if (!olciSlstrIdepixProduct.containsBand(Rad2ReflConstants.OLCI_REFL_BAND_NAMES[2])) {
                ProductUtils.copyBand(Rad2ReflConstants.OLCI_REFL_BAND_NAMES[2],
                        olciRad2reflProduct, olciSlstrIdepixProduct, true);
            }
            if (!olciSlstrIdepixProduct.containsBand(Rad2ReflConstants.OLCI_REFL_BAND_NAMES[16])) {
                ProductUtils.copyBand(Rad2ReflConstants.OLCI_REFL_BAND_NAMES[16],
                        olciRad2reflProduct, olciSlstrIdepixProduct, true);
            }

            olciSlstrMutatedIdepixProduct = mutateCloudMask(olciSlstrIdepixProduct);
            computeCloudShadow = true;
        } else {
            olciSlstrMutatedIdepixProduct = olciSlstrIdepixProduct;
        }

        olciSlstrMutatedIdepixProduct.setName(sourceProduct.getName() + "_IDEPIX");
        olciSlstrMutatedIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance:S*_radiance_*:S*_reflectance_*");

        C3SOlciSlstrUtils.copySlstrCloudFlagBands(sourceProduct, olciSlstrMutatedIdepixProduct);

        // cloud buffer and shadow
        postProcess(olciSlstrMutatedIdepixProduct);

        targetProduct = createTargetProduct(olciSlstrMutatedIdepixProduct);
        targetProduct.setAutoGrouping(olciSlstrMutatedIdepixProduct.getAutoGrouping());

        if (postProcessingProduct != null) {
            Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
            cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());
        }
    }

    private Product createTargetProduct(Product idepixProduct) {
        Product targetProduct = new Product(idepixProduct.getName(),
                idepixProduct.getProductType(),
                idepixProduct.getSceneRasterWidth(),
                idepixProduct.getSceneRasterHeight());

        ProductUtils.copyMetadata(idepixProduct, targetProduct);
        ProductUtils.copyGeoCoding(idepixProduct, targetProduct);
        ProductUtils.copyFlagCodings(idepixProduct, targetProduct);
        ProductUtils.copyFlagBands(idepixProduct, targetProduct, true);
        ProductUtils.copyMasks(idepixProduct, targetProduct);
        ProductUtils.copyTiePointGrids(idepixProduct, targetProduct);

        ProductUtils.copyBand("altitude", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("SZA", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("SAA", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("OZA", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("OAA", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("solar_zenith_tn", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("solar_azimuth_tn", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("sat_zenith_tn", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("sat_azimuth_tn", idepixProduct, targetProduct, true);

        ProductUtils.copyBand("total_column_ozone_tx", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("total_column_water_vapour_tx", idepixProduct, targetProduct, true);
        ;
        ProductUtils.copyBand("surface_pressure_tx", idepixProduct, targetProduct, true);
        ProductUtils.copyBand("elevation_an", idepixProduct, targetProduct, true);
        ;

        targetProduct.setStartTime(idepixProduct.getStartTime());
        targetProduct.setEndTime(idepixProduct.getEndTime());

        C3SOlciSlstrUtils.setupOlciClassifBitmask(targetProduct);
        if (outputOlciRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, olciRadianceBandsToCopy);
        }

        if (outputSlstrRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, slstrRadianceBandsToCopy);
        }

        if (outputSlstrReflectance) {
            C3SOlciSlstrUtils.addSlstrRadiance2ReflectanceBands(slstrRad2reflProduct, targetProduct, slstrReflectanceBandsToCopy);
        }
        if (outputOlciReflectance) {
            C3SOlciSlstrUtils.addOlciRadiance2ReflectanceBands(olciRad2reflProduct, targetProduct, olciReflectanceBandsToCopy);
        }

        if (outputSchillerNNValue) {
            ProductUtils.copyBand(IdepixConstants.NN_OUTPUT_BAND_NAME, idepixProduct, targetProduct, true);
        }

        return targetProduct;
    }

    private void preProcess() {
        olciRad2reflProduct = C3SOlciSlstrUtils.computeRadiance2ReflectanceProduct(sourceProduct, Sensor.OLCI);
        slstrRad2reflProduct = C3SOlciSlstrUtils.computeRadiance2ReflectanceProduct(sourceProduct, Sensor.C3S_SYN_SLSTR);

        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", IdepixConstants.LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", IdepixConstants.OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", IdepixConstants.OVERSAMPLING_FACTOR_Y);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);

        if (computeCloudShadow) {
            Map<String, Product> o2corrSourceProducts = new HashMap<>();
            o2corrSourceProducts.put("l1bProduct", sourceProduct);
            final String o2CorrOpName = "OlciO2aHarmonisation";
            Map<String, Object> o2corrParms = new HashMap<>();
            o2corrParms.put("writeHarmonisedRadiances", false);
            o2corrParms.put("processOnlyBand13", false);
            o2CorrProduct = GPF.createProduct(o2CorrOpName, o2corrParms, o2corrSourceProducts);
            ctpProduct = C3SOlciSlstrUtils.computeCloudTopPressureProduct(sourceProduct, o2CorrProduct);
        }
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
        classificationParameters.put("outputSchillerNNValue", isMutant() ? true : outputSchillerNNValue);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("reflOlci", olciRad2reflProduct);
        classificationInputProducts.put("reflSlstr", slstrRad2reflProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
    }

    private Product computeClassificationProduct() {
        setClassificationParameters();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(C3SOlciSlstrClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void postProcess(Product olciIdepixProduct) {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("ctp", ctpProduct);
        input.put("olciSlstrCloud", olciIdepixProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudShadow", true);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(C3SOlciSlstrPostProcessOp.class),
                params, input);
    }

    private Product mutateCloudMask(Product product) {
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudMaskMutationOp.class), cloudMaskMutationParameterMap(), product);
    }

    @NotNull
    private Map<String, Object> cloudMaskMutationParameterMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put("rngType", MELG);
        map.put("seedNumber", pcg.nextLong());
        map.put("seedString", DATE_AND_TIME_OF_SOURCE);
        map.put("cloudyClearThresholdLnd", cloudyClearThresholdLnd);
        map.put("cloudyClearThresholdWtr", cloudyClearThresholdWtr);
        map.put("randomlyMutant", randomlyMutantCloudyClear);
        return map;
    }

    private Multivariate multivariate(String samplingType) {
        switch (samplingType) {
            case "Latin hypercube":
                return new LatinHypercube(6, simulationCount, selector, new Melg(seed));
            case "Sobol":
                return new Sobol(6).start(6 + selector + seed);
            default:
                return pcg;
        }
    }

    private static double randomCloudyClearThreshold(UniformVariate u, String resource) {
        return new EmpiricVariate(InterpolationFunctionFactory.create("InversePrimitiveStep", resource), u).nextDouble();
    }

    private boolean isMutant() {
        return selector > 0;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(C3SOlciSlstrMutantOp.class);
        }
    }
}
