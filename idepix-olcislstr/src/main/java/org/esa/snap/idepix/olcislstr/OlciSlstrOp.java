package org.esa.snap.idepix.olcislstr;

import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.operators.BasisOp;
import eu.esa.opt.processor.rad2refl.Sensor;
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

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for OLCI/SLSTR synergy products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel3.OlciSlstr",
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        version = "3.0",
        authors = "Olaf Danne",
        internal = true,
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI/SLSTR synergy products.")
public class OlciSlstrOp extends BasisOp {

    @SourceProduct(alias = "sourceProduct",
            label = "OLCI/SLSTR Synergy product",
            description = "The OLCI/SLSTR Synergy source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private boolean outputOlciRadiance;
    private boolean outputSlstrRadiance;

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

    @Parameter(description = "The list of SLSTR radiance bands to write to target product.",
            label = "Select SLSTR TOA radiances to write to the target product",
            valueSet = {
                    "S1_radiance_an", "S2_radiance_an", "S3_radiance_an",
                    "S4_radiance_an", "S5_radiance_an", "S6_radiance_an",
                    "S4_radiance_bn", "S5_radiance_bn", "S6_radiance_bn",
                    "S4_radiance_cn", "S5_radiance_cn", "S6_radiance_cn"
            },
            defaultValue = "")
    private String[] slstrRadianceBandsToCopy;

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


    private Product postProcessingProduct;

    private Product olciRad2reflProduct;
    private Product ctpProduct;
    private Product o2CorrProduct;
    private Product waterMaskProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCISLSTR);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        outputOlciRadiance = olciRadianceBandsToCopy != null && olciRadianceBandsToCopy.length > 0;

        outputSlstrRadiance = slstrRadianceBandsToCopy != null && slstrRadianceBandsToCopy.length > 0;

        preProcess();

        setClassificationInputProducts();
        Product olciSlstrIdepixProduct = computeClassificationProduct();

        olciSlstrIdepixProduct.setName(sourceProduct.getName() + "_IDEPIX");
        olciSlstrIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance:S*_radiance:S*_reflectance");

        OlciSlstrUtils.copySlstrCloudFlagBands(sourceProduct, olciSlstrIdepixProduct);

        if (computeCloudBuffer || computeCloudShadow) {
            postProcess(olciSlstrIdepixProduct);
        }

        targetProduct = createTargetProduct(olciSlstrIdepixProduct);
        targetProduct.setAutoGrouping(olciSlstrIdepixProduct.getAutoGrouping());

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

//        ProductUtils.copyMetadata(idepixProduct, targetProduct);
        ProductUtils.copyGeoCoding(idepixProduct, targetProduct);
        ProductUtils.copyFlagCodings(idepixProduct, targetProduct);
        ProductUtils.copyFlagBands(idepixProduct, targetProduct, true);
        ProductUtils.copyMasks(idepixProduct, targetProduct);
        ProductUtils.copyTiePointGrids(idepixProduct, targetProduct);
        targetProduct.setStartTime(idepixProduct.getStartTime());
        targetProduct.setEndTime(idepixProduct.getEndTime());

        OlciSlstrUtils.setupOlciClassifBitmask(targetProduct);

        if (outputOlciRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, olciRadianceBandsToCopy);
        }

        if (outputSlstrRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, slstrRadianceBandsToCopy);
        }

        if (outputSchillerNNValue) {
            ProductUtils.copyBand(IdepixConstants.NN_OUTPUT_BAND_NAME, idepixProduct, targetProduct, true);
        }

        return targetProduct;
    }

    private void preProcess() {
        olciRad2reflProduct = OlciSlstrUtils.computeRadiance2ReflectanceProduct(sourceProduct, Sensor.OLCI);

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

            ctpProduct = OlciSlstrUtils.computeCloudTopPressureProduct(sourceProduct, o2CorrProduct);
        }
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
        classificationParameters.put("outputSchillerNNValue", outputSchillerNNValue);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("reflOlci", olciRad2reflProduct);
        classificationInputProducts.put("waterMask", waterMaskProduct);
    }

    private Product computeClassificationProduct() {
        setClassificationParameters();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(OlciSlstrClassificationOp.class),
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
        params.put("computeCloudShadow", computeCloudShadow);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(OlciSlstrPostProcessOp.class),
                                                  params, input);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OlciSlstrOp.class);
        }
    }
}
