package org.esa.s3tbx.idepix.algorithms.probav;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.lang.ArrayUtils;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.pixel.AbstractPixelProperties;
import org.esa.s3tbx.idepix.core.util.IdepixUtils;
import org.esa.s3tbx.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.core.dataop.dem.ElevationModelDescriptor;
import org.esa.snap.core.dataop.dem.ElevationModelRegistry;
import org.esa.snap.core.dataop.resamp.Resampling;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.watermask.operator.WatermaskClassifier;

import javax.media.jai.Histogram;
import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 17.08.2016
 * Time: 18:19
 *
 * @author olafd
 */
public class ProbaVClassificationOp extends Operator {

    @Parameter(defaultValue = "true",
            label = " Write TOA Reflectances to the target product",
            description = " Write TOA Reflectances to the target product")
    private boolean copyToaReflectances = true;

    @Parameter(defaultValue = "false",
            label = " Write Feature Values to the target product",
            description = " Write all Feature Values to the target product")
    private boolean copyFeatureValues = false;

    @Parameter(defaultValue = "false",
            label = " Write input annotation bands to the target product",
            description = " Write input annotation bands to the target product")
    private boolean copyAnnotations;

    @Parameter(defaultValue = "false",
            label = " Apply NN for cloud classification",
            description = " Apply NN for cloud classification")
    private boolean applySchillerNN;


    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary",
            description = " NN cloud ambiguous lower boundary")
    private double gaSchillerNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    private double gaSchillerNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    private double gaSchillerNNCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead",
            description = "Use land-water flag from L1b product instead of SRTM mask")
    private boolean useL1bLandWaterFlag;

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    // Proba-V bands:
    private Band[] probavReflectanceBands;

    private Band landWaterBand;

    protected static final int SM_F_CLEAR = 0;
    protected static final int SM_F_UNDEFINED = 1;
    protected static final int SM_F_CLOUD = 2;
    protected static final int SM_F_SNOWICE = 3;
    protected static final int SM_F_CLOUDSHADOW = 4;
    protected static final int SM_F_LAND = 5;
    protected static final int SM_F_SWIR_GOOD = 6;
    protected static final int SM_F_NIR_GOOD = 7;
    protected static final int SM_F_RED_GOOD = 8;
    protected static final int SM_F_BLUE_GOOD = 9;

    static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water

    ElevationModel getasseElevationModel;

    private int[] listOfPixelTimes;

    Band cloudFlagBand;
    Band temperatureBand;
    Band brightBand;
    Band whiteBand;
    Band brightWhiteBand;
    Band spectralFlatnessBand;
    Band ndviBand;
    Band ndsiBand;
    Band glintRiskBand;
    Band radioLandBand;

    Band radioWaterBand;


    public static final String SCHILLER_VGT_NET_NAME = "3x2x2_341.8.net";
    ThreadLocal<SchillerNeuralNetWrapper> vgtNeuralNet;


    @Override
    public void initialize() throws OperatorException {
        setBands();
        readSchillerNeuralNets();
        createTargetProduct();
        extendTargetProduct();

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(
                demName);
        if (demDescriptor == null || !demDescriptor.canBeDownloaded()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        // extract the pixel sampling times
        // todo: clarify with GK/JM how to further use this information
        if (isProbaVDailySynthesisProduct(sourceProduct.getName())) {
            final Band timeBand = sourceProduct.getBand("TIME");
            final RenderedImage timeImage = timeBand.getSourceImage().getImage(0);
            listOfPixelTimes = getListOfPixelTimes(timeImage);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // PROBA-V variables
        final Band smFlagBand = sourceProduct.getBand("SM_FLAGS");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        Tile[] probavReflectanceTiles = new Tile[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        float[] probavReflectance = new float[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectanceTiles[i] = getSourceTile(probavReflectanceBands[i], rectangle);
        }

        GeoPos geoPos = null;
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixUtils.IDEPIX_CLASSIF_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        final Band nnTargetBand = targetProduct.getBand("probav_nn_value");
        final Tile nnTargetTile = targetTiles.get(nnTargetBand);

        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    if (!useL1bLandWaterFlag) {
                        waterMaskFraction = (byte) waterFractionTile.getSampleInt(x, y);
                    }

                    // set up pixel properties for given instruments...
                    ProbaVAlgorithm probaVAlgorithm = createProbavAlgorithm(smFlagTile, probavReflectanceTiles,
                                                                                    probavReflectance,
                                                                                    waterMaskFraction,
                                                                                    y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, probaVAlgorithm);

                    // apply improvement from NN approach...
                    final double[] nnOutput = probaVAlgorithm.getNnOutput();
                    if (applySchillerNN) {
                        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.F_INVALID)) {
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, false);
                            if (nnOutput[0] > gaSchillerNNCloudAmbiguousLowerBoundaryValue &&
                                    nnOutput[0] <= gaSchillerNNCloudAmbiguousSureSeparationValue) {
                                // this would be as 'CLOUD_AMBIGUOUS'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_AMBIGUOUS, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                            if (nnOutput[0] > gaSchillerNNCloudAmbiguousSureSeparationValue &&
                                    nnOutput[0] <= gaSchillerNNCloudSureSnowSeparationValue) {
                                // this would be as 'CLOUD_SURE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLOUD, true);
                            }
                            if (nnOutput[0] > gaSchillerNNCloudSureSnowSeparationValue) {
                                // this would be as 'SNOW/ICE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, true);
                            }
                        }
                        nnTargetTile.setSample(x, y, nnOutput[0]);
                    }

                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, y, x, probaVAlgorithm);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }


    private void setBands() {
        probavReflectanceBands = new Band[IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES[i]);
        }

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    private void extendTargetProduct() throws OperatorException {
        if (copyToaReflectances) {
            copyProbavReflectances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (copyAnnotations) {
            copyProbavAnnotations();
        }

        if (applySchillerNN) {
            targetProduct.addBand("probav_nn_value", ProductData.TYPE_FLOAT32);
        }
    }

    // package local for testing
    static int[] getListOfPixelTimes(RenderedImage timeImage) {

        // Set up the parameters for the Histogram object.
        // bin the histogram into the minutes of one day.
        // we consider daily products only todo: clarify
        int[] bins = {1440};
        double[] low = {0.0};
        double[] high = {1440.0};

        // Create the parameter block.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(timeImage);           // Specify the source image
        pb.add(null);                      // No ROI
        pb.add(1);                         // Sampling
        pb.add(1);                         // periods
        pb.add(bins);                      // bins
        pb.add(low);                       // low
        pb.add(high);                      // high

        // Perform the histogram operation.
        final RenderedOp histoImage = JAI.create("histogram", pb, null);

        // Retrieve the histogram data.
        Histogram hist = (Histogram) histoImage.getProperty("histogram");
        final int[][] histBins = hist.getBins();
        java.util.List<Integer> histBinList = new ArrayList();
        for (int i = 1; i < histBins[0].length; i++) {   // skip the 0 (no data value)
            if (histBins[0][i] > 0) {
                histBinList.add(i);
            }
        }
        return ArrayUtils.toPrimitive(histBinList.toArray(new Integer[histBinList.size()]));
    }

    private static boolean isProbaVDailySynthesisProduct(String productName) {
        return productName.toUpperCase().startsWith("PROBAV_S1_") &&
                productName.toUpperCase().endsWith(".HDF5");
    }

    private void copyProbavAnnotations() {
        for (String bandName : IdepixConstants.PROBAV_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
        }
    }

    private void copyProbavReflectances() {
        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            // write the original reflectance bands:
            ProductUtils.copyBand(IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private ProbaVAlgorithm createProbavAlgorithm(Tile smFlagTile, Tile[] probavReflectanceTiles,
                                                      float[] probavReflectance,
                                                      byte watermaskFraction,
                                                      int y, int x) {

        ProbaVAlgorithm probaVAlgorithm = new ProbaVAlgorithm();

        for (int i = 0; i < IdepixConstants.PROBAV_REFLECTANCE_BAND_NAMES.length; i++) {
            probavReflectance[i] = probavReflectanceTiles[i].getSampleFloat(x, y);
        }

        final double altitude = computeGetasseAltitude(x, y);
        probaVAlgorithm.setElevation(altitude);

        checkProbavReflectanceQuality(probaVAlgorithm, probavReflectance, smFlagTile, x, y);
        probaVAlgorithm.setRefl(probavReflectance);

        SchillerNeuralNetWrapper nnWrapper = vgtNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(probavReflectance[i]);
        }
        probaVAlgorithm.setNnOutput(nnWrapper.getNeuralNet().calc(inputVector));

        if (useL1bLandWaterFlag) {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
            probaVAlgorithm.setL1bLand(isLand);
            probaVAlgorithm.setIsWater(!isLand);
        } else {
            final boolean isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            probaVAlgorithm.setL1bLand(isLand);
            setIsWaterByFraction(watermaskFraction, probaVAlgorithm);
        }

        return probaVAlgorithm;
    }

    private void readSchillerNeuralNets() {
        try (InputStream vgtLandIS = getClass().getResourceAsStream(SCHILLER_VGT_NET_NAME)) {
            vgtNeuralNet = SchillerNeuralNetWrapper.create(vgtLandIS);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Neural Nets: " + e.getMessage());
        }
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        cloudFlagBand = targetProduct.addBand(IdepixUtils.IDEPIX_CLASSIF_FLAGS, ProductData.TYPE_INT32);
        FlagCoding flagCoding = ProbaVUtils.createProbavFlagCoding(IdepixUtils.IDEPIX_CLASSIF_FLAGS);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (copyFeatureValues) {
            brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", IdepixConstants.NO_DATA_VALUE,
                                             true);
            temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(temperatureBand, "Temperature", "K", IdepixConstants.NO_DATA_VALUE, true);
            spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl",
                                             IdepixConstants.NO_DATA_VALUE, true);
            ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            glintRiskBand = targetProduct.addBand("glint_risk_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(glintRiskBand, "GLINT_RISK", "dl", IdepixConstants.NO_DATA_VALUE, true);
            radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", IdepixConstants.NO_DATA_VALUE,
                                             true);
            radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            IdepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "",
                                             IdepixConstants.NO_DATA_VALUE, true);
        }

        // new bit masks:
        ProbaVUtils.setupProbavBitmasks(targetProduct);

    }

    void setPixelSamples(Band band, Tile targetTile, int y, int x,
                         ProbaVAlgorithm probaVAlgorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            targetTile.setSample(x, y, probaVAlgorithm.brightValue());
        } else if (band == whiteBand) {
            targetTile.setSample(x, y, probaVAlgorithm.whiteValue());
        } else if (band == brightWhiteBand) {
            targetTile.setSample(x, y, probaVAlgorithm.brightValue() + probaVAlgorithm.whiteValue());
        } else if (band == temperatureBand) {
            targetTile.setSample(x, y, probaVAlgorithm.temperatureValue());
        } else if (band == spectralFlatnessBand) {
            targetTile.setSample(x, y, probaVAlgorithm.spectralFlatnessValue());
        } else if (band == ndviBand) {
            targetTile.setSample(x, y, probaVAlgorithm.ndviValue());
        } else if (band == ndsiBand) {
            targetTile.setSample(x, y, probaVAlgorithm.ndsiValue());
        } else if (band == glintRiskBand) {
            targetTile.setSample(x, y, probaVAlgorithm.glintRiskValue());
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, probaVAlgorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, probaVAlgorithm.radiometricWaterValue());
        }
    }

    void setCloudFlag(Tile targetTile, int y, int x, ProbaVAlgorithm probaVAlgorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, IdepixConstants.F_INVALID, probaVAlgorithm.isInvalid());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD, probaVAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SURE, probaVAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.F_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_LAND, probaVAlgorithm.isClearLand());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_WATER, probaVAlgorithm.isClearWater());
        targetTile.setSample(x, y, IdepixConstants.F_CLEAR_SNOW, probaVAlgorithm.isClearSnow());
        targetTile.setSample(x, y, IdepixConstants.F_LAND, probaVAlgorithm.isLand());
        targetTile.setSample(x, y, IdepixConstants.F_WATER, probaVAlgorithm.isWater());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHT, probaVAlgorithm.isBright());
        targetTile.setSample(x, y, IdepixConstants.F_WHITE, probaVAlgorithm.isWhite());
        targetTile.setSample(x, y, IdepixConstants.F_BRIGHTWHITE, probaVAlgorithm.isBrightWhite());
        targetTile.setSample(x, y, IdepixConstants.F_HIGH, probaVAlgorithm.isHigh());
        targetTile.setSample(x, y, IdepixConstants.F_VEG_RISK, probaVAlgorithm.isVegRisk());
        targetTile.setSample(x, y, IdepixConstants.F_SEAICE, probaVAlgorithm.isSeaIce());
    }


    void setIsWater(byte watermask, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermask == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermask == WatermaskClassifier.WATER_VALUE;
        }
        pixelProperties.setIsWater(isWater);
    }

    void setIsWaterByFraction(byte watermaskFraction, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermaskFraction == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermaskFraction >= WATERMASK_FRACTION_THRESH;
        }
        pixelProperties.setIsWater(isWater);
    }

    private double computeGetasseAltitude(float x, float y) {
        final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
        GeoPos geoPos = sourceProduct.getSceneGeoCoding().getGeoPos(pixelPos, null);
        double altitude;
        try {
            altitude = getasseElevationModel.getElevation(geoPos);
        } catch (Exception e) {
            // todo
            e.printStackTrace();
            altitude = 0.0;
        }
        return altitude;
    }

    private void checkProbavReflectanceQuality(ProbaVAlgorithm gaAlgorithm,
                                               float[] probavReflectance,
                                               Tile smFlagTile,
                                               int x, int y) {
        final boolean isBlueGood = smFlagTile.getSampleBit(x, y, SM_F_BLUE_GOOD);
        final boolean isRedGood = smFlagTile.getSampleBit(x, y, SM_F_RED_GOOD);
        final boolean isNirGood = smFlagTile.getSampleBit(x, y, SM_F_NIR_GOOD);
        final boolean isSwirGood = smFlagTile.getSampleBit(x, y, SM_F_SWIR_GOOD);
        final boolean isProcessingLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
        gaAlgorithm.setIsBlueGood(isBlueGood);
        gaAlgorithm.setIsRedGood(isRedGood);
        gaAlgorithm.setIsNirGood(isNirGood);
        gaAlgorithm.setIsSwirGood(isSwirGood);
        gaAlgorithm.setProcessingLand(isProcessingLand);

        if (!isBlueGood || !isRedGood || !isNirGood || !isSwirGood || !isProcessingLand) {
            for (int i = 0; i < probavReflectance.length; i++) {
                probavReflectance[i] = Float.NaN;
            }
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ProbaVClassificationOp.class);
        }
    }
}
