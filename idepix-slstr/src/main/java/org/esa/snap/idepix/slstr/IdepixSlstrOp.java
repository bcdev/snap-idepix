package org.esa.snap.idepix.slstr;

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
import org.esa.snap.core.util.ProductUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for OLCI/SLSTR synergy products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel3.Slstr",
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        version = "3.0",
        authors = "Olaf Danne",
        internal = true,
        copyright = "(c) 2025 by Brockmann Consult",
        description = "Pixel identification and classification for SLSTR synergy products.")
public class IdepixSlstrOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "SLSTR L1b product",
            description = "The SLSTR L1b source product.")
    private Product sourceProduct;

    private boolean outputRadiance;
    private boolean outputRad2Refl;

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

    @Parameter(defaultValue = "false",
            description = "Restrict NN test for sea/lake ice to ice climatology area. This parameter has changed its default value!",
            label = "Use sea/lake ice climatology as filter"
    )
    private boolean useLakeAndSeaIceClimatology;

//    @Parameter(defaultValue = "false",
//            label = " Use SRTM Land/Water mask",
//            description = "If selected, SRTM Land/Water mask is used instead of L1b land flag. " +
//                    "Slower, but in general more precise.")
    private boolean useSrtmLandWaterMask = true;

    private Product l1bProductToProcess;

    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product rad2reflProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;

    private boolean considerCloudsOverSnow;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        final Geometry productGeometry = IdepixSlstrUtils.computeProductGeometry(sourceProduct);
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
            IdepixIO.addRadianceBands(l1bProductToProcess, targetProduct, radianceBandsToCopy);
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

        return targetProduct;
    }


    private void preProcess() {

        if (considerCloudsOverSnow || computeCloudShadow || useO2HarmonizedRadiancesForNN) {
            Map<String, Product> o2corrSourceProducts = new HashMap<>();
            Map<String, Object> o2corrParms = new HashMap<>();
            o2corrParms.put("processOnlyBand13", false);
            o2corrParms.put("writeHarmonisedRadiances", useO2HarmonizedRadiancesForNN);
            o2corrSourceProducts.put("l1bProduct", sourceProduct);
            final String o2CorrOpName = "OlciO2aHarmonisation";
            o2CorrProduct = GPF.createProduct(o2CorrOpName, o2corrParms, o2corrSourceProducts);

            if (computeCloudShadow) {
                ctpProduct = IdepixOlciUtils.computeCloudTopPressureProduct(sourceProduct,
                        o2CorrProduct,
                        alternativeCtpNNDir,
                        outputCtp,
                        useO2HarmonizedRadiancesForNN);
            }
        }

        if (useO2HarmonizedRadiancesForNN) {
            Map<String, Product> l1bO2MergeSourceProducts = new HashMap<>();
            Map<String, Object> emptyParms = new HashMap<>();
            l1bO2MergeSourceProducts.put("l1bProduct", sourceProduct);
            l1bO2MergeSourceProducts.put("o2harmoProduct", o2CorrProduct);
            l1bProductToProcess = GPF.createProduct("IdepixOlciMergeO2Harmonize", emptyParms, l1bO2MergeSourceProducts);
        } else {
            l1bProductToProcess = sourceProduct;
        }

        rad2reflProduct = IdepixOlciUtils.computeRadiance2ReflectanceProduct(l1bProductToProcess);
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
        classificationParameters.put("outputSchillerNNValue", outputSchillerNNValue);
        classificationParameters.put("alternativeNNFile", alternativeNNFile);
        classificationParameters.put("alternativeNNThresholdsFile", alternativeNNThresholdsFile);
        classificationParameters.put("useSrtmLandWaterMask", useSrtmLandWaterMask);
        classificationParameters.put("useLakeAndSeaIceClimatology", useLakeAndSeaIceClimatology);
        classificationParameters.put("useO2HarmonizedRadiancesForNN", useO2HarmonizedRadiancesForNN);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", l1bProductToProcess);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
        if (considerCloudsOverSnow) {
            classificationInputProducts.put("o2Corr", o2CorrProduct);
        }
    }

    private void postProcess(Product olciIdepixProduct) {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", l1bProductToProcess);
        input.put("ctp", ctpProduct);
        input.put("olciCloud", olciIdepixProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudShadow", computeCloudShadow);
        params.put("computeMountainShadow", computeMountainShadow);
        params.put("mntShadowExtent", mntShadowExtent);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciPostProcessOp.class),
                params, input);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixSlstrOp.class);
        }
    }
}
