package org.esa.snap.idepix.spotvgt;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.pixel.AbstractPixelProperties;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * VGT pixel classification operator.
 * Pixels are classified from NN approach (following BEAM IdePix algorithm).
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Vgt.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix land pixel classification operator for VGT.")
public class VgtClassificationOp extends Operator {

    @Parameter(defaultValue = "true",
            label = " Write TOA reflectances to the target product",
            description = " Write TOA reflectances to the target product")
    private boolean copyToaReflectances = true;

    @Parameter(defaultValue = "false",
            label = " Write input annotation bands to the target product",
            description = " Write input annotation bands to the target product")
    private boolean copyAnnotations;

    @Parameter(defaultValue = "false",
            label = " Write feature values to the target product",
            description = " Write all feature values to the target product")
    private boolean copyFeatureValues = false;

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "false",
            label = " Use land-water flag from L1b product instead",
            description = "Use land-water flag from L1b product instead of SRTM mask")
    private boolean useL1bLandWaterFlag;

    @Parameter(defaultValue = "1.1",
            label = " NN cloud ambiguous lower boundary",
            description = " NN cloud ambiguous lower boundary")
    private double nnCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "2.7",
            label = " NN cloud ambiguous/sure separation value",
            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
    private double nnCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.6",
            label = " NN cloud sure/snow separation value",
            description = " NN cloud ambiguous cloud sure/snow separation value")
    private double nnCloudSureSnowSeparationValue;

    @Parameter(defaultValue = "false",
            label = " Apply processing mode for C3S-Lot5 project",
            description = "If set, processing mode for C3S-Lot5 project is applied (uses specific tests)")
    private boolean isProcessingForC3SLot5;



    // VGT bands:
    private Band[] vgtReflectanceBands;

    private Band temperatureBand;
    private Band brightBand;
    private Band whiteBand;
    private Band brightWhiteBand;
    private Band spectralFlatnessBand;
    private Band ndviBand;
    private Band ndsiBand;
    private Band glintRiskBand;
    private Band radioLandBand;

    private Band radioWaterBand;

    static final int SM_F_CLOUD_1 = 0;
    static final int SM_F_CLOUD_2 = 1;
//    public static final int SM_F_ICE_SNOW = 2;
    private static final int SM_F_LAND = 3;
    private static final int SM_F_MIR_GOOD = 4;
    private static final int SM_F_B3_GOOD = 5;
    private static final int SM_F_B2_GOOD = 6;
    private static final int SM_F_B0_GOOD = 7;

    @SourceProduct(alias = "l1b", description = "The source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @SourceProduct(alias = "inlandWaterMaskCollocated", description = "External inland water product(optional)", optional = true)
    private Product inlandWaterMaskProduct;


    private static final String VGT_NET_NAME = "3x2x2_341.8.net";
    private ThreadLocal<SchillerNeuralNetWrapper> vgtNeuralNet;

    private Band landWaterBand;

    private static final byte WATERMASK_FRACTION_THRESH = 23;   // for 3x3 subsampling, this means 2 subpixels water

    @Override
    public void initialize() throws OperatorException {
        setBands();
        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
        readSchillerNeuralNets();
        createTargetProduct();
        extendTargetProduct();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // VGT variables
        final Band smFlagBand = sourceProduct.getBand("SM");
        final Tile smFlagTile = getSourceTile(smFlagBand, rectangle);

        Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        Tile[] vgtReflectanceTiles = new Tile[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        float[] vgtReflectance = new float[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectanceTiles[i] = getSourceTile(vgtReflectanceBands[i], rectangle);
        }

        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        Band nnTargetBand;
        Tile nnTargetTile = null;
        if (outputSchillerNNValue) {
            nnTargetBand = targetProduct.getBand("vgt_nn_value");
            nnTargetTile = targetTiles.get(nnTargetBand);
        }

        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    byte waterMaskFraction = WatermaskClassifier.INVALID_VALUE;
                    if (!useL1bLandWaterFlag) {
                        waterMaskFraction = (byte) waterFractionTile.getSampleInt(x, y);
                    }

                    // set up pixel properties for given instruments...
                    VgtAlgorithm vgtAlgorithm = createVgtAlgorithm(smFlagTile, vgtReflectanceTiles,
                                                                   vgtReflectance,
                                                                   waterMaskFraction,
                                                                   y, x);

                    setCloudFlag(cloudFlagTargetTile, y, x, vgtAlgorithm);
                   // apply improvement from NN approach...
                    final double[] nnOutput = vgtAlgorithm.getNnOutput();
                    final boolean smCloud = smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_1) &&smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_2);
                    if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                        if (nnOutput[0] > nnCloudAmbiguousLowerBoundaryValue &&
                                nnOutput[0] <= nnCloudAmbiguousSureSeparationValue && smCloud) {
                            // this would be as 'CLOUD_AMBIGUOUS'...
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_LAND, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_WATER, false);
                        }
                        if (nnOutput[0] > nnCloudAmbiguousSureSeparationValue &&
                                nnOutput[0] <= nnCloudSureSnowSeparationValue && smCloud) {
                            // this would be as 'CLOUD_SURE'...
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_LAND, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_WATER, false);
                        }
                        if (nnOutput[0] > nnCloudSureSnowSeparationValue && cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE)) {
                            // this would be as 'SNOW/ICE'...
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_LAND, false);
                            cloudFlagTargetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_WATER, false);
                        } else {
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                        }
                    }
                    if (outputSchillerNNValue && nnTargetTile != null) {
                        nnTargetTile.setSample(x, y, nnOutput[0]);
                    }

                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, y, x, vgtAlgorithm);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT32);
        FlagCoding flagCoding = VgtUtils.createVgtFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (copyFeatureValues) {
            brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(brightBand, "Brightness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(whiteBand, "Whiteness", "dl", IdepixConstants.NO_DATA_VALUE, true);
            brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", IdepixConstants.NO_DATA_VALUE,
                                          true);
            temperatureBand = targetProduct.addBand("temperature_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(temperatureBand, "Temperature", "K", IdepixConstants.NO_DATA_VALUE, true);
            spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl",
                                          IdepixConstants.NO_DATA_VALUE, true);
            ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(ndviBand, "NDVI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(ndsiBand, "NDSI", "dl", IdepixConstants.NO_DATA_VALUE, true);
            glintRiskBand = targetProduct.addBand("glint_risk_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(glintRiskBand, "GLINT_RISK", "dl", IdepixConstants.NO_DATA_VALUE, true);
            radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", IdepixConstants.NO_DATA_VALUE,
                                          true);
            radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            IdepixIO.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "",
                                          IdepixConstants.NO_DATA_VALUE, true);
        }
    }


    private void readSchillerNeuralNets() {
        try (InputStream vgtLandIS = getClass().getResourceAsStream(VGT_NET_NAME)) {
            vgtNeuralNet = SchillerNeuralNetWrapper.create(vgtLandIS);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Neural Nets: " + e.getMessage());
        }
    }


    private void setBands() {
        vgtReflectanceBands = new Band[IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectanceBands[i] = sourceProduct.getBand(IdepixConstants.VGT_REFLECTANCE_BAND_NAMES[i]);
        }
    }

    private void setCloudFlag(Tile targetTile, int y, int x, VgtAlgorithm vgtAlgorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, vgtAlgorithm.isInvalid());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, vgtAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, vgtAlgorithm.isCloud());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_LAND, vgtAlgorithm.isClearLand());
        targetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_WATER, vgtAlgorithm.isClearWater());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, vgtAlgorithm.isCoastline());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, vgtAlgorithm.isClearSnow());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, vgtAlgorithm.isLand());
        targetTile.setSample(x, y, VgtConstants.IDEPIX_WATER, vgtAlgorithm.isWater());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, vgtAlgorithm.isBright());
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_WHITE, vgtAlgorithm.isWhite());
    }

    private void setPixelSamples(Band band, Tile targetTile, int y, int x,
                         VgtAlgorithm vgtAlgorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            targetTile.setSample(x, y, vgtAlgorithm.brightValue());
        } else if (band == whiteBand) {
            targetTile.setSample(x, y, vgtAlgorithm.whiteValue());
        } else if (band == brightWhiteBand) {
            targetTile.setSample(x, y, vgtAlgorithm.brightValue() + vgtAlgorithm.whiteValue());
        } else if (band == temperatureBand) {
            targetTile.setSample(x, y, vgtAlgorithm.temperatureValue());
        } else if (band == spectralFlatnessBand) {
            targetTile.setSample(x, y, vgtAlgorithm.spectralFlatnessValue());
        } else if (band == ndviBand) {
            targetTile.setSample(x, y, vgtAlgorithm.ndviValue());
        } else if (band == ndsiBand) {
            targetTile.setSample(x, y, vgtAlgorithm.ndsiValue());
        } else if (band == glintRiskBand) {
            targetTile.setSample(x, y, vgtAlgorithm.glintRiskValue());
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, vgtAlgorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, vgtAlgorithm.radiometricWaterValue());
        }
    }


    private void extendTargetProduct() throws OperatorException {
        if (copyToaReflectances) {
            copyVgtReflectances();
            ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        }

        if (copyAnnotations) {
            copyVgtAnnotations();
        }

        if (outputSchillerNNValue) {
            targetProduct.addBand("vgt_nn_value", ProductData.TYPE_FLOAT32);
        }
    }

    private void copyVgtAnnotations() {
        for (String bandName : IdepixConstants.VGT_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
        }
    }

    private void copyVgtReflectances() {
        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            // write the original reflectance bands:
            ProductUtils.copyBand(IdepixConstants.VGT_REFLECTANCE_BAND_NAMES[i], sourceProduct,
                                  targetProduct, true);
        }
    }

    private VgtAlgorithm createVgtAlgorithm(Tile smFlagTile, Tile[] vgtReflectanceTiles,
                                            float[] vgtReflectance,
                                            byte watermaskFraction,
                                            int y, int x) {

        VgtAlgorithm vgtAlgorithm = new VgtAlgorithm();

        vgtAlgorithm.setProcessingForC3SLot5(isProcessingForC3SLot5);

        final boolean isUndefined = smFlagTile.getSampleInt(x, y) == 0;    // GK, 20200219
        vgtAlgorithm.setIsUndefined(isUndefined);

        final boolean isBlueGood = smFlagTile.getSampleBit(x, y, SM_F_B0_GOOD);
        final boolean isRedGood = smFlagTile.getSampleBit(x, y, SM_F_B2_GOOD);
        final boolean isNirGood = smFlagTile.getSampleBit(x, y, SM_F_B3_GOOD);
        final boolean isSwirGood = smFlagTile.getSampleBit(x, y, SM_F_MIR_GOOD);
        vgtAlgorithm.setIsBlueGood(isBlueGood);
        vgtAlgorithm.setIsRedGood(isRedGood);
        vgtAlgorithm.setIsNirGood(isNirGood);
        vgtAlgorithm.setIsSwirGood(isSwirGood);

        for (int i = 0; i < IdepixConstants.VGT_REFLECTANCE_BAND_NAMES.length; i++) {
            vgtReflectance[i] = vgtReflectanceTiles[i].getSampleFloat(x, y);
        }

        if (!isProcessingForC3SLot5) {
            checkVgtReflectanceQuality(vgtReflectance, smFlagTile, x, y);
        }
        float[] vgtReflectanceSaturationCorrected = IdepixUtils.correctSaturatedReflectances(vgtReflectance);
        vgtAlgorithm.setRefl(vgtReflectanceSaturationCorrected);

        SchillerNeuralNetWrapper nnWrapper = vgtNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(vgtReflectanceSaturationCorrected[i]);
        }
        vgtAlgorithm.setNnOutput(nnWrapper.getNeuralNet().calc(inputVector));

        boolean isLand;
        if (useL1bLandWaterFlag) {
            isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND);
            vgtAlgorithm.setSmLand(isLand);
            vgtAlgorithm.setIsWater(!isLand);
            //  vgtAlgorithm.setL1bLand(isLand);
            vgtAlgorithm.setIsCoastline(false);
        } else {
            isLand = smFlagTile.getSampleBit(x, y, SM_F_LAND) &&
                    watermaskFraction < WATERMASK_FRACTION_THRESH;
            vgtAlgorithm.setSmLand(isLand);
            //vgtAlgorithm.setL1bLand(isLand);
            setIsWaterByFraction(watermaskFraction, vgtAlgorithm);
            final boolean isCoastline = isCoastlinePixel(x, y, watermaskFraction);
            vgtAlgorithm.setIsCoastline(isCoastline);
        }

        return vgtAlgorithm;
    }

    private void setIsWaterByFraction(byte watermaskFraction, AbstractPixelProperties pixelProperties) {
        boolean isWater;
        if (watermaskFraction == WatermaskClassifier.INVALID_VALUE) {
            // fallback
            isWater = pixelProperties.isL1Water();
        } else {
            isWater = watermaskFraction >= WATERMASK_FRACTION_THRESH;
        }
        pixelProperties.setIsWater(isWater);
    }

    private boolean isCoastlinePixel(int x, int y, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        // values bigger than 100 indicate no data
        // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return getGeoPos(x, y).lat > -58f && waterFraction < 100 && waterFraction > 0;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private void checkVgtReflectanceQuality(float[] vgtReflectance, Tile smFlagTile, int x, int y) {
        final boolean isBlueGood = smFlagTile.getSampleBit(x, y, SM_F_B0_GOOD);
        final boolean isRedGood = smFlagTile.getSampleBit(x, y, SM_F_B2_GOOD);
        final boolean isNirGood =  smFlagTile.getSampleBit(x, y, SM_F_B3_GOOD);
        final boolean isSwirGood = smFlagTile.getSampleBit(x, y, SM_F_MIR_GOOD) || vgtReflectance[3] <= 0.65;

        if (!isBlueGood || !isRedGood || !isNirGood || !isSwirGood) {
            for (int i = 0; i < vgtReflectance.length; i++) {
                vgtReflectance[i] = Float.NaN;
            }
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(VgtClassificationOp.class);
        }
    }
}
