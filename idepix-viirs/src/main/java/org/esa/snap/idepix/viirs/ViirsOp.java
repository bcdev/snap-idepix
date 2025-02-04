package org.esa.snap.idepix.viirs;

import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * IdePix operator for pixel identification and classification for VIIRS
 *
 * @author olafd
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Idepix.Viirs",
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        version = "3.1",
        authors = "Olaf Danne, Marco Zuehlke",
        copyright = "(c) 2016 - 2024 by Brockmann Consult",
        description = "Pixel identification and classification for VIIRS. Supports Suomi NPP, NOAA20, NOAA21 products.")
public class ViirsOp extends Operator{

    @Parameter(defaultValue = "false",
            label = " Write TOA reflectances to the target product",
            description = "Write TOA reflectances to the target product.")
    private boolean outputViirsRhoToa = false;

//    @Parameter(defaultValue = "true",
//            label = " Debug bands",
//            description = "Write further useful bands to target product.")
//    private boolean outputDebug = true;
    private boolean outputDebug = false;

    @Parameter(defaultValue = "1", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    private int waterMaskResolution;


    @SourceProduct(alias = "sourceProduct", label = "Name (VIIRS L1C product)",
            description = "The L1C source product (SNPP, NOAA20, or NOAA21)")
    private Product sourceProduct;

    private Product classifProduct;
    private Product waterMaskProduct;
    private Map<String, Object> classificationParameters;

    @Override
    public void initialize() throws OperatorException {
        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.VIIRS);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        processViirs(createViirsClassificationParameters());
    }

    private void processViirs(Map<String, Object> viirsClassificationParameters) {
        Map<String, Product> viirsClassifInput = new HashMap<>(4);
        computeAlgorithmInputProducts(viirsClassifInput);

        // post processing input:
        // - cloud buffer
        // - cloud shadow todo (currently exists only for Meris)
        Map<String, Object> postProcessParameters = new HashMap<>();
        postProcessParameters.put("cloudBufferWidth", cloudBufferWidth);
        Map<String, Product> postProcessInput = new HashMap<>();
        postProcessInput.put("waterMask", waterMaskProduct);

        postProcessInput.put("refl", sourceProduct);
        classifProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ViirsClassificationOp.class),
                                           viirsClassificationParameters, viirsClassifInput);

        postProcessInput.put("classif", classifProduct);

        Product postProcessProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ViirsPostProcessOp.class),
                                                       postProcessParameters, postProcessInput);

        ProductUtils.copyMetadata(sourceProduct, postProcessProduct);
        setTargetProduct(postProcessProduct);
        addBandsToTargetProduct(postProcessProduct);
    }

    private void computeAlgorithmInputProducts(Map<String, Product> viirsClassifInput) {
        createWaterMaskProduct();
        viirsClassifInput.put("waterMask", waterMaskProduct);
        viirsClassifInput.put("refl", sourceProduct);
    }

    private void createWaterMaskProduct() {
        HashMap<String, Object> waterParameters = new HashMap<>();
        waterParameters.put("resolution", waterMaskResolution);
        waterParameters.put("subSamplingFactorX", 3);
        waterParameters.put("subSamplingFactorY", 3);
        waterMaskProduct = GPF.createProduct("LandWaterMask", waterParameters, sourceProduct);
    }

    private Map<String, Object> createViirsClassificationParameters() {
        Map<String, Object> viirsCloudClassificationParameters = new HashMap<>(1);
        viirsCloudClassificationParameters.put("cloudBufferWidth", cloudBufferWidth);
        viirsCloudClassificationParameters.put("waterMaskResolution", waterMaskResolution);
        viirsCloudClassificationParameters.put("outputDebug", outputDebug);

        return viirsCloudClassificationParameters;
    }

    private void addBandsToTargetProduct(Product targetProduct) {
        if (outputViirsRhoToa) {
            copySourceBands(sourceProduct, targetProduct, "rhot");
        }
        if (outputDebug) {
            copySourceBands(classifProduct, targetProduct, "_value");
        }
    }

    private static void copySourceBands(Product rad2reflProduct, Product targetProduct, String bandNameSubstring) {
        for (String bandname : rad2reflProduct.getBandNames()) {
            if (bandname.contains(bandNameSubstring) && !targetProduct.containsBand(bandname)) {
                ProductUtils.copyBand(bandname, rad2reflProduct, targetProduct, true);
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ViirsOp.class);
        }
    }
}
