package org.esa.snap.idepix.olci;

import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
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
import org.esa.snap.idepix.core.util.IdepixIO;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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
@OperatorMetadata(alias = "Idepix.Olci.Omaps.Minifiles",
        category = "Optical/Pre-Processing",
        version = "3.0.2",
        authors = "Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI.")
public class IdepixOlciOmapsMinifilesOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product l1bProduct;

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
    private String[] radianceBandsToCopy;

    @Parameter(description = "The list of reflectance bands to write to target product.",
            label = "Select TOA reflectances to write to the target product",
            valueSet = {
                    "Oa01_reflectance", "Oa02_reflectance", "Oa03_reflectance", "Oa04_reflectance", "Oa05_reflectance",
                    "Oa06_reflectance", "Oa07_reflectance", "Oa08_reflectance", "Oa09_reflectance", "Oa10_reflectance",
                    "Oa11_reflectance", "Oa12_reflectance", "Oa13_reflectance", "Oa14_reflectance", "Oa15_reflectance",
                    "Oa16_reflectance", "Oa17_reflectance", "Oa18_reflectance", "Oa19_reflectance", "Oa20_reflectance",
                    "Oa21_reflectance"
            },
            defaultValue = "")
    private String[] reflBandsToCopy;

    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with the algorithm from 'Fronts' project")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Use SRTM Land/Water mask",
            description = "If selected, SRTM Land/Water mask is used instead of L1b land flag. " +
                    "Slower, but in general more precise.")
    private boolean useSrtmLandWaterMask;


    private Product classificationProduct;
    private Product postProcessingProduct;

    private Product l1bClonedProduct;
    private Product rad2reflProduct;

    private Map<String, Product> classificationInputProducts;
    private Map<String, Object> classificationParameters;


    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(l1bProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        // neglect cloud shadow for a 3x3 or 5x5 macro pixel. Cloud buffer is enough...
        computeCloudShadow = false;

        // 1. create idepix branch 'omaps' --> DONE
        // 2. new operator 'IdepixOlciOmapsMinifilesOp --> DONE
        // 3. here: clone minifile source product, --> DOME
        // add solar fluxes extracted from nc file as below, add start/stop time from file name --> DONE
        l1bClonedProduct = cloneL1bProduct();
        final File inputFile = (File) l1bProduct.getProductReader().getInput();
        try {
            float[][] solarFluxArr = new float[0][];
            NetcdfFile netcdfInputFile = NetcdfFile.open(inputFile.getAbsolutePath());
            final List<Variable> variables = netcdfInputFile.getVariables();
            for (final Variable variable : variables) {
                final int bandsDimensionIndex = variable.findDimensionIndex("bands");
                final int detectorsDimensionIndex = variable.findDimensionIndex("detectors");
                if (bandsDimensionIndex != -1 && detectorsDimensionIndex != -1) {
                    if (variable.getShortName().equals("solar_flux")) {
                        solarFluxArr = (float[][]) variable.read().copyToNDJavaArray();
                    }
                }
            }
            addSolarFluxesToL1bProduct(solarFluxArr, l1bClonedProduct);
        } catch (IOException e) {
            e.printStackTrace();
        }

        outputRadiance = radianceBandsToCopy != null && radianceBandsToCopy.length > 0;
        outputRad2Refl = reflBandsToCopy != null && reflBandsToCopy.length > 0;

        preProcess();

        setClassificationInputProducts();
        computeCloudProduct();

        Product olciIdepixProduct = classificationProduct;
        olciIdepixProduct.setName(l1bClonedProduct.getName() + "_IDEPIX");
        olciIdepixProduct.setAutoGrouping("Oa*_radiance:Oa*_reflectance");

        ProductUtils.copyFlagBands(l1bClonedProduct, olciIdepixProduct, true);
//        setTargetProduct(olciIdepixProduct);  // test

        if (computeCloudBuffer || computeCloudShadow) {
            postProcess(olciIdepixProduct);
        }

        targetProduct = createTargetProduct(olciIdepixProduct);
        targetProduct.setAutoGrouping(olciIdepixProduct.getAutoGrouping());

        if (postProcessingProduct != null) {
            Band cloudFlagBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
            cloudFlagBand.setSourceImage(postProcessingProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME).getSourceImage());
        }

    }

    private void addSolarFluxesToL1bProduct(float[][] solarFluxArr,
                                            Product l1bClonedProduct) throws IOException {

        // solarFluxArr: 21 x 3700
        // detectorIndexArr: 5 x 5
        Band detectorIndexBand = l1bProduct.getBand("detector_index");
        detectorIndexBand.readRasterDataFully();
        final ProductData.Short detectorIndexBandRasterData = (ProductData.Short) detectorIndexBand.getRasterData();

        Band[] solar_flux_bands = new Band[Rad2ReflConstants.OLCI_RAD_BAND_NAMES.length];
        for (int i = 0; i < solar_flux_bands.length; i++) {
            solar_flux_bands[i] = l1bClonedProduct.addBand("solar_flux_band_" + (i+1), ProductData.TYPE_FLOAT32);
            solar_flux_bands[i].ensureRasterData();
            int index = 0;
            for (int j = 0; j < l1bClonedProduct.getSceneRasterWidth(); j++) {
                for (int k = 0; k < l1bClonedProduct.getSceneRasterHeight(); k++) {
                    solar_flux_bands[i].setPixelFloat(j, k, solarFluxArr[i][detectorIndexBandRasterData.getElemIntAt(index++)]);
                }
            }
        }
    }

    private Product cloneL1bProduct() {
        Product l1bClonedPeoduct = new Product(l1bProduct.getName(), "OL_1_",
                l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight());
        ProductUtils.copyMetadata(l1bProduct, l1bClonedPeoduct);
        ProductUtils.copyGeoCoding(l1bProduct, l1bClonedPeoduct);
        ProductUtils.copyFlagBands(l1bProduct, l1bClonedPeoduct, true);
        ProductUtils.copyFlagCodings(l1bProduct, l1bClonedPeoduct);
        ProductUtils.copyMasks(l1bProduct, l1bClonedPeoduct);
        ProductUtils.copyTimeInformation(l1bProduct, l1bClonedPeoduct);

        final ProductData.UTC startTime = IdepixOlciUtils.getStartTimeFromOlciFileName(l1bProduct.getName());
        l1bClonedPeoduct.setStartTime(startTime);
        final ProductData.UTC endTime = IdepixOlciUtils.getEndTimeFromOlciFileName(l1bProduct.getName());
        l1bClonedPeoduct.setEndTime(endTime);

        for (Band sourceBand : l1bProduct.getBands()) {
            if (!l1bClonedPeoduct.containsBand(sourceBand.getName())) {
                ProductUtils.copyBand(sourceBand.getName(), l1bProduct, l1bClonedPeoduct, true);
                ProductUtils.copyRasterDataNodeProperties(sourceBand, l1bClonedPeoduct.getBand(sourceBand.getName()));
            }
        }

        return l1bClonedPeoduct;
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
            IdepixIO.addRadianceBands(l1bClonedProduct, targetProduct, radianceBandsToCopy);
        }
        if (outputRad2Refl) {
            IdepixOlciUtils.addOlciRadiance2ReflectanceBands(rad2reflProduct, targetProduct, reflBandsToCopy);
        }

        return targetProduct;
    }


    private void preProcess() {
        rad2reflProduct = IdepixOlciUtils.computeRadiance2ReflectanceProduct(l1bClonedProduct);
    }

    private void setClassificationParameters() {
        classificationParameters = new HashMap<>();
        classificationParameters.put("copyAllTiePoints", true);
        classificationParameters.put("useSrtmLandWaterMask", useSrtmLandWaterMask);
    }

    private void computeCloudProduct() {
        setClassificationParameters();
        classificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciClassificationOp.class),
                classificationParameters, classificationInputProducts);
    }

    private void setClassificationInputProducts() {
        classificationInputProducts = new HashMap<>();
        classificationInputProducts.put("l1b", l1bProduct);
//        classificationInputProducts.put("iceMask", iceMaskProduct);
        classificationInputProducts.put("rhotoa", rad2reflProduct);
    }

    private void postProcess(Product olciIdepixProduct) {
        HashMap<String, Product> input = new HashMap<>();
        input.put("l1b", l1bClonedProduct);
        input.put("olciCloud", olciIdepixProduct);

        Map<String, Object> params = new HashMap<>();
        params.put("computeCloudBuffer", computeCloudBuffer);
        params.put("cloudBufferWidth", cloudBufferWidth);
        params.put("computeCloudShadow", computeCloudShadow);

        postProcessingProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IdepixOlciPostProcessOp.class),
                params, input);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciOmapsMinifilesOp.class);
        }
    }
}
