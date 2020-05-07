package org.esa.snap.idepix.probav;

import org.esa.snap.collocation.CollocateOp;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification for PROBA-V Synthesis products
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Probav",
        category = "Optical/Pre-Processing",
        version = "3.0",
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for PROBA-V.")
public class ProbaVOp extends BasisOp {

    @Parameter(defaultValue = "false",
            label = " Write TOA reflectances to the target product",
            description = " Write TOA reflectances to the target product")
    private boolean copyToaReflectances = false;

    @Parameter(defaultValue = "false",
            label = " Write input annotation bands to the target product",
            description = " Write input annotation bands to the target product")
    private boolean copyAnnotations;

    @Parameter(defaultValue = "false",
            label = " Write feature values to the target product",
            description = " Write all feature values to the target product")
    private boolean copyFeatureValues = false;

    @Parameter(defaultValue = "false",
            label = " Apply NN for cloud classification",
            description = " Apply NN for cloud classification")
    private boolean applySchillerNN;

    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary",
            description = " NN cloud ambiguous lower boundary")
    private double schillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "1.2",
            label = " NN cloud ambiguous upper boundary",
            description = " NN cloud ambiguous upper boundary")
    private double schillerNNCloudAmbiguousUpperBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    private double schillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    private double schillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead of SRTM mask",
            description = "Use land-water flag from L1b product instead of SRTM mask")
    private boolean useL1bLandWaterFlag;

    @Parameter(defaultValue = "false",
            label = " Apply processing mode for C3S-Lot5 project",
            description = "If set, processing mode for C3S-Lot5 project is applied (uses specific tests)")
    private boolean isProcessingForC3SLot5;


    @SourceProduct(alias = "sourceProduct",
            label = "Proba-V L1b product",
            description = "The Proba-V L1b source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "vitoCloudProduct",
            label = "Proba-V VITO cloud product",
            optional = true,
            description = "Proba-V VITO cloud product (optional)")
    private Product vitoCloudProduct;


    @SourceProduct(alias = "inlandWaterProduct",
            label = "External inland water product",
            optional = true,
            description = "External inland water product (optional)")
    private Product inlandWaterProduct;



    private Product cloudProduct;
    private Product inlandWaterMaskProduct;
    private Product postProcessingProduct;


    @Override
    public void initialize() throws OperatorException {
        sourceProduct = getSourceProduct("sourceProduct");
        vitoCloudProduct = getSourceProduct("vitoCloudProduct");
        inlandWaterProduct = getSourceProduct("inlandWaterProduct");

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.PROBAV);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        processProbav();
    }

    private void processProbav() {
        HashMap<String, Object> waterMaskParameters = new HashMap<>();
        waterMaskParameters.put("resolution", IdepixConstants.LAND_WATER_MASK_RESOLUTION);
        waterMaskParameters.put("subSamplingFactorX", IdepixConstants.OVERSAMPLING_FACTOR_X);
        waterMaskParameters.put("subSamplingFactorY", IdepixConstants.OVERSAMPLING_FACTOR_Y);
        Product waterMaskProduct = GPF.createProduct("LandWaterMask", waterMaskParameters, sourceProduct);

        // Cloud Classification
        Map<String, Product> cloudInput = new HashMap<>(4);
        cloudInput.put("l1b", sourceProduct);
        cloudInput.put("vitoCm", vitoCloudProduct);
        cloudInput.put("waterMask", waterMaskProduct);


        if (inlandWaterProduct != null) {
            inlandWaterMaskProduct = collocateInlandWaterProduct(sourceProduct, inlandWaterProduct);
        }
        if (inlandWaterMaskProduct != null) {
            //setSourceProduct("inlandWaterMaskCollocated", inlandWaterMaskProduct);
            getLogger().info("inland water mask " + inlandWaterMaskProduct.getName() + " applied");
        }

        cloudInput.put("inlandWaterMaskCollocated", inlandWaterMaskProduct);

        Map<String, Object> cloudClassificationParameters = createCloudClassificationParameters();

        cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ProbaVClassificationOp.class),
                                         cloudClassificationParameters, cloudInput);

        computePostProcessProduct();

        Product targetProduct = IdepixIO.cloneProduct(cloudProduct, true);
//        targetProduct.setPreferredTileSize(sourceProduct.getSceneRasterWidth(), 16); // test!

        Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());

        setTargetProduct(targetProduct);
    }

    private void computePostProcessProduct() {
        // Post Cloud Classification: flag consolidation, cloud shadow, cloud buffer
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("probavCloud", cloudProduct);
        input.put("inlandWaterMaskCollocated", inlandWaterMaskProduct);
        input.put("vitoCm", vitoCloudProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("isProcessingForC3SLot5", isProcessingForC3SLot5);
        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ProbaVPostProcessOp.class),
                                                            params, input);
    }

    private Map<String, Object> createCloudClassificationParameters() {
        Map<String, Object> cloudClassificationParameters = new HashMap<>(1);
        cloudClassificationParameters.put("copyToaReflectances", copyToaReflectances);
        cloudClassificationParameters.put("copyFeatureValues", copyFeatureValues);
        cloudClassificationParameters.put("useL1bLandWaterFlag", useL1bLandWaterFlag);
        cloudClassificationParameters.put("copyAnnotations", copyAnnotations);
        cloudClassificationParameters.put("applySchillerNN", applySchillerNN);
        cloudClassificationParameters.put("isProcessingForC3SLot5", isProcessingForC3SLot5);
        cloudClassificationParameters.put("schillerNNCloudAmbiguousLowerBoundaryValue",
                                            schillerNNCloudAmbiguousLowerBoundaryValue);
        cloudClassificationParameters.put("schillerNNCloudAmbiguousUpperBoundaryValue",
                                            schillerNNCloudAmbiguousUpperBoundaryValue);
        cloudClassificationParameters.put("schillerNNCloudAmbiguousSureSeparationValue",
                                            schillerNNCloudAmbiguousSureSeparationValue);
        cloudClassificationParameters.put("schillerNNCloudSureSnowSeparationValue",
                                            schillerNNCloudSureSnowSeparationValue);

        return cloudClassificationParameters;
    }

    private Product collocateInlandWaterProduct(Product sourceProduct, Product inlandWaterProduct) {
        CollocateOp collocateOp = new CollocateOp();
        collocateOp.setMasterProduct(sourceProduct);
        collocateOp.setSlaveProduct(inlandWaterProduct);
        collocateOp.setParameter("resamplingType", "NEAREST_NEIGHBOUR");
        collocateOp.setRenameMasterComponents(false);
        collocateOp.setRenameSlaveComponents(false);
        return collocateOp.getTargetProduct();
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ProbaVOp.class);
        }
    }
}
