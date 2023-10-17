package org.esa.snap.idepix.olci;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for OLCI products.
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
@OperatorMetadata(alias = "Idepix.Olci",
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        version = "3.1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI.")
public class IdepixOlciOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product sourceProduct;

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
            }
    )
    private String[] radianceBandsToCopy;

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select TOA reflectances to write to the target product",
            valueSet = {
                    "Oa01_reflectance", "Oa02_reflectance", "Oa03_reflectance", "Oa04_reflectance", "Oa05_reflectance",
                    "Oa06_reflectance", "Oa07_reflectance", "Oa08_reflectance", "Oa09_reflectance", "Oa10_reflectance",
                    "Oa11_reflectance", "Oa12_reflectance", "Oa13_reflectance", "Oa14_reflectance", "Oa15_reflectance",
                    "Oa16_reflectance", "Oa17_reflectance", "Oa18_reflectance", "Oa19_reflectance", "Oa20_reflectance",
                    "Oa21_reflectance"
            }
    )
    private String[] reflBandsToCopy;

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow index.",
            description = " If applied, compute cloud shadow index and write to the target product ")
    private boolean computeCSI;

    @Parameter(defaultValue = "false",
            label = " Compute OCIMP cloud shadow.",
            description = " If applied, compute OCIMP cloud shadow ")
    private boolean computeOcimpCloudShadow;

    @Parameter(defaultValue = "31",
            label = " Window size for OCIMP cloud shadow computation.",
            description = " The window size for OCIMP cloud shadow computation (must be positive and odd number ")
    private int ocimpCloudShadowWindowSize;

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(description = "Alternative pixel classification NN file. " +
            "If set, it MUST follow format and input/output used in default " +
            "'class-sequential-i21x42x8x4x2o1-5489.net'. " +
            "('11x10x4x3x2_207.9.net' has been default until June 2023.)",
            label = " Alternative NN file")
    private File alternativeNNFile;

    @Parameter(description = "Alternative pixel classification NN thresholds file. " +
            "If set, it MUST follow format used in default " +
            "'class-sequential-i21x42x8x4x2o1-5489-thresholds.json'. " +
            "('11x10x4x3x2_207.9-thresholds.json' has been default until June 2023.)",
            label = " Alternative NN thresholds file")
    private File alternativeNNThresholdsFile;

    @Parameter(defaultValue = "true", label = " Compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(label = " Extent of mountain shadow", defaultValue = "0.9", interval = "[0,1]",
            description = "Extent of mountain shadow detection")
    private double mntShadowExtent;

    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;

    @Parameter(description = "Path to alternative tensorflow neuronal net directory for CTP retrieval " +
            "Use this to replace the standard neuronal net 'nn_training_20190131_I7x30x30x30xO1'.",
            label = "Path to alternative NN for CTP retrieval")
    private String alternativeCtpNNDir;

    @Parameter(defaultValue = "false",
            label = " If cloud shadow is computed, write CTP value to the target product",
            description = " If cloud shadow is computed, write CTP value to the target product ")
    private boolean outputCtp;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            description = "Restrict NN test for sea/lake ice to ice climatology area. This parameter has changed its default value!",
            label = "Use sea/lake ice climatology as filter"
    )
    private boolean useLakeAndSeaIceClimatology;

    @Parameter(defaultValue = "false",
            label = " Use SRTM Land/Water mask",
            description = "If selected, SRTM Land/Water mask is used instead of L1b land flag. " +
                    "Slower, but in general more precise.")
    private boolean useSrtmLandWaterMask;


    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product rad2reflProduct;
    private Product ctpProduct;
    private Product o2CorrProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;

    private boolean considerCloudsOverSnow;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        final Geometry productGeometry = IdepixOlciUtils.computeProductGeometry(sourceProduct);
        if (productGeometry != null) {
            final Polygon arcticPolygon =
                    IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ARCTIC_POLYGON_COORDS);
            final Polygon antarcticaPolygon =
                    IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ANTARCTICA_POLYGON_COORDS);
            considerCloudsOverSnow =
                    productGeometry.intersects(arcticPolygon) || productGeometry.intersects(antarcticaPolygon);
            arcticPolygon.contains(productGeometry);
        } else {
            throw new OperatorException("Product geometry is null - cannot proceed.");
        }

        outputRadiance = radianceBandsToCopy != null && radianceBandsToCopy.length > 0;
        outputRad2Refl = reflBandsToCopy != null && reflBandsToCopy.length > 0;

        preProcess();

        setClassificationInputProducts();
        computeCloudProduct();

        Product olciIdepixProduct = classificationProduct;
        olciIdepixProduct.setName(sourceProduct.getName() + "_IDEPIX");
        olciIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance");

        ProductUtils.copyFlagBands(sourceProduct, olciIdepixProduct, true);

        if (computeCloudBuffer || computeMountainShadow || computeCloudShadow) {
            postProcess(olciIdepixProduct);
        }

        Product targetProduct = createTargetProduct(olciIdepixProduct);
        targetProduct.setAutoGrouping(olciIdepixProduct.getAutoGrouping());

        if (postProcessingProduct != null) {
            Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
            cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());
        }
        setTargetProduct(targetProduct);
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

        IdepixOlciUtils.setupOlciClassifBitmask(targetProduct);

        if (outputRadiance) {
            IdepixIO.addRadianceBands(sourceProduct, targetProduct, radianceBandsToCopy);
        }
        if (outputRad2Refl) {
            IdepixOlciUtils.addOlciRadiance2ReflectanceBands(rad2reflProduct, targetProduct, reflBandsToCopy);
        }

        if (outputSchillerNNValue) {
            ProductUtils.copyBand(IdepixConstants.NN_OUTPUT_BAND_NAME, idepixProduct, targetProduct, true);
        }

        if (computeCloudShadow && outputCtp) {
            ProductUtils.copyBand(IdepixConstants.CTP_OUTPUT_BAND_NAME, ctpProduct, targetProduct, true);
        }

        if (computeCSI) {
            ProductUtils.copyBand(IdepixOlciConstants.CSI_OUTPUT_BAND_NAME, postProcessingProduct, targetProduct, true);
        }

        if (computeOcimpCloudShadow) {
            ProductUtils.copyBand(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME, postProcessingProduct, targetProduct, true);
        }

        return targetProduct;
    }


    private void preProcess() {
        rad2reflProduct = IdepixOlciUtils.computeRadiance2ReflectanceProduct(sourceProduct);

        if (considerCloudsOverSnow) {
            Map<String, Product> o2corrSourceProducts = new HashMap<>();
            o2corrSourceProducts.put("l1bProduct", sourceProduct);
            final String o2CorrOpName = "OlciO2aHarmonisation";
            Map<String, Object> o2corrParms = new HashMap<>();
            o2corrParms.put("writeHarmonisedRadiances", false);
            if (computeCloudShadow) {
                o2corrParms.put("processOnlyBand13", false);
            }
            o2corrParms.put("processOnlyBand13", false); // test!
            o2CorrProduct = GPF.createProduct(o2CorrOpName, o2corrParms, o2corrSourceProducts);
        }

        if (computeCloudShadow) {
            ctpProduct = IdepixOlciUtils.computeCloudTopPressureProduct(sourceProduct,
                    o2CorrProduct,
                    alternativeCtpNNDir,
                    outputCtp);
        }

    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
        classificationParameters.put("outputSchillerNNValue", outputSchillerNNValue);
        classificationParameters.put("alternativeNNFile", alternativeNNFile);
        classificationParameters.put("alternativeNNThresholdsFile", alternativeNNThresholdsFile);
        classificationParameters.put("useSrtmLandWaterMask", useSrtmLandWaterMask);
        classificationParameters.put("useLakeAndSeaIceClimatology", useLakeAndSeaIceClimatology);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", sourceProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        if (considerCloudsOverSnow) {
            classificationInputProducts.put("o2Corr", o2CorrProduct);
        }
    }

    private void postProcess(Product olciIdepixProduct) {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", sourceProduct);
        input.put("rhotoa", rad2reflProduct);
        input.put("ctp", ctpProduct);
        input.put("olciCloud", olciIdepixProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudShadow", computeCloudShadow);
        params.put("computeMountainShadow", computeMountainShadow);
        params.put("mntShadowExtent", mntShadowExtent);
        params.put("computeCSI", computeCSI);
        params.put("computeOcimpCloudShadow", computeOcimpCloudShadow);
        params.put("ocimpCloudShadowWindowSize", ocimpCloudShadowWindowSize);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciPostProcessOp.class),
                params, input);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciOp.class);
        }
    }
}
