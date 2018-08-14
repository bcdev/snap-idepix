package org.esa.snap.idepix.olci.s3snow;

import org.esa.s3tbx.idepix.core.AlgorithmSelector;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.operators.BasisOp;
import org.esa.s3tbx.idepix.core.util.IdepixIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.VirtualBand;
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
 * Specific plugin version for S3-SNOW project.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Snap.Idepix.Olci.S3Snow",
        category = "Optical/Pre-Processing",
        version = "0.82",
        authors = "Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI. Specific version for S3-SNOW project.")
public class IdepixOlciS3SnowOp extends BasisOp {

    @Parameter(defaultValue = "band_1",
            label = " Name of DEM band (if optional DEM product is provided)",
            description = "Name of DEM band in DEM product (if optionally provided)")
    private String demBandName;


    @SourceProduct(alias = "sourceProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "demProduct",
            optional = true,
            label = "DEM product for O2 correction",
            description = "DEM product.")
    private Product demProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;


    private String[] reflBandsToCopy = {"Oa21_reflectance"};  // needed for 'cloud over snow' band computation

    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product rad2reflProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;
    private Product o2CorrProduct;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        preProcess();

        setClassificationInputProducts();
        computeCloudProduct();

        Product olciIdepixProduct = classificationProduct;
        olciIdepixProduct.setName(sourceProduct.getName() + "_IDEPIX");
        olciIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance");

        ProductUtils.copyFlagBands(sourceProduct, olciIdepixProduct, true);

        postProcess(olciIdepixProduct);

        targetProduct = createTargetProduct(olciIdepixProduct);
        targetProduct.setAutoGrouping(olciIdepixProduct.getAutoGrouping());

        ProductUtils.copyBand("trans_13", o2CorrProduct, targetProduct, true);
        ProductUtils.copyBand("press_13", o2CorrProduct, targetProduct, true);
        ProductUtils.copyBand("surface_13", o2CorrProduct, targetProduct, true);
        ProductUtils.copyBand("altitude", sourceProduct, targetProduct, true);
        addSurfacePressureBand();
        addCloudOverSnowBand();

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
        targetProduct.setStartTime(idepixProduct.getStartTime());
        targetProduct.setEndTime(idepixProduct.getEndTime());

        IdepixOlciS3SnowUtils.setupOlciClassifBitmask(targetProduct);
        IdepixOlciS3SnowUtils.addOlciRadiance2ReflectanceBands(rad2reflProduct, targetProduct, reflBandsToCopy);

        return targetProduct;
    }


    private void preProcess() {
        rad2reflProduct = IdepixOlciS3SnowUtils.computeRadiance2ReflectanceProduct(sourceProduct);

        Map<String, Product> o2corrSourceProducts = new HashMap<>();
        Map<String, Object> o2corrParms = new HashMap<>();
        o2corrSourceProducts.put("l1bProduct", sourceProduct);
        if (demProduct != null) {
            o2corrSourceProducts.put("DEM", demProduct);
            o2corrParms.put("demAltitudeBandName", demBandName);
        }
        final String o2CorrOpName = "O2CorrOlci";
        o2CorrProduct = GPF.createProduct(o2CorrOpName, o2corrParms, o2corrSourceProducts);
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciS3SnowClassificationOp.class),
                                                  classificationParameters, classificationInputProducts);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
    }

    private void postProcess(Product olciIdepixProduct) {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("olciCloud", olciIdepixProduct);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciS3SnowPostProcessOp.class),
                                                  GPF.NO_PARAMS, input);
    }

    private void addSurfacePressureBand() {
        String presExpr = "(1013.25 * exp(-altitude/8400))";
        final Band surfPresBand = new VirtualBand("surface_pressure",
                                                  ProductData.TYPE_FLOAT32,
                                                  targetProduct.getSceneRasterWidth(),
                                                  targetProduct.getSceneRasterHeight(),
                                                  presExpr);
        surfPresBand.setDescription("estimated sea level pressure (p0=1013.25hPa, hScale=8.4km)");
        surfPresBand.setNoDataValue(0);
        surfPresBand.setNoDataValueUsed(true);
        surfPresBand.setUnit("hPa");
        targetProduct.addBand(surfPresBand);
    }

    private void addCloudOverSnowBand() {
        String expr = "pixel_classif_flags.IDEPIX_LAND && Oa21_reflectance > 0.5 && surface_13 - trans_13 < 0.01";
        final Band surfPresBand = new VirtualBand("cloud_over_snow",
                                                  ProductData.TYPE_FLOAT32,
                                                  targetProduct.getSceneRasterWidth(),
                                                  targetProduct.getSceneRasterHeight(),
                                                  expr);
        surfPresBand.setDescription("Pixel identified as likely cloud over a snow/ice surface");
        surfPresBand.setNoDataValue(0);
        surfPresBand.setNoDataValueUsed(true);
        surfPresBand.setUnit("dl");
        targetProduct.addBand(surfPresBand);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciS3SnowOp.class);
        }
    }
}
