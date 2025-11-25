package org.esa.snap.idepix.slstr;

import com.bc.ceres.binding.ValueRange;
import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.seaice.LakeSeaIceAuxdata;
import org.esa.snap.idepix.core.seaice.LakeSeaIceClassification;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.snap.watermask.operator.WatermaskClassifier;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;

import static org.esa.snap.idepix.core.IdepixConstants.*;
import static org.esa.snap.idepix.slstr.IdepixSlstrCloudNNInterpreter.NNThreshold;

/**
 * SLSTR pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Slstr.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2025 by Brockmann Consult",
        description = "IdePix pixel classification operator for SLSTR synergy.")
public class IdepixSlstrClassificationOp extends Operator {

    @Parameter(description = "The list of SLSTR bands to use as input for the classification NN.",
            label = "Select SLSTR bands for the classification NN",
            valueSet = {
                    "S1_reflectance_an", "S2_reflectance_an", "S3_reflectance_an",
                    "S4_reflectance_an", "S5_reflectance_an", "S6_reflectance_an",
                    "S1_reflectance_ao", "S2_reflectance_ao", "S3_reflectance_ao",
                    "S4_reflectance_ao", "S5_reflectance_ao", "S6_reflectance_ao",
                    "S4_reflectance_bn", "S5_reflectance_bn", "S6_reflectance_bn",
                    "S4_reflectance_bo", "S5_reflectance_bo", "S6_reflectance_bo",
                    "S7_bt_in", "S8_bt_in", "S9_bt_in",
                    "S7_bt_io", "S8_bt_io", "S9_bt_io"
            },
            defaultValue = "")
    private String[] slstrBandsForNN;

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(description = "Alternative pixel classification NN file. " +
            "If set, it MUST follow format and input/output used in default " +
            "'class-sequential-i9x32x16x8x4x2o1-5489.net.md'.",
            label = " Alternative NN file")
    private File alternativeNNFile;

    @Parameter(description = "Alternative pixel classification NN thresholds file. " +
            "If set, it MUST follow format used in default " +
            "'class-sequential-i9x32x16x8x4x2o1-5489-thresholds'.",
            label = " Alternative NN thresholds file")
    private File alternativeNNThresholdsFile;

    @Parameter(defaultValue = "true",
            description = "Restrict NN test for sea/lake ice to ice climatology area.",
            label = "Use sea/lake ice climatology as filter")
    private boolean useLakeAndSeaIceClimatology;

    @SourceProduct(alias = "l1b", description = "The L1b product.")
    private Product l1bProduct;

    @SourceProduct(alias = "iceMask",
            label = "Ice mask product", optional = true,
            description = "User defined ice mask product. If not provided, default climatology is used.")
    private Product iceMaskProduct;

    @SourceProduct(alias = "reflSlstr")
    private Product slstrRad2reflProduct;


    @TargetProduct(description = "The target product.")
    Product targetProduct;

    private static final String SLSTR_NET_NAME = "class-sequential-i9x32x16x8x4x2o1-5489.net.md";
    private static final String SLSTR_NN_THRESHOLDS_FILE = "class-sequential-i9x32x16x8x4x2o1-5489-thresholds.json";

    private Band[] slstrL1bBands;

    private final boolean useSrtmLandWaterMask = true;

    private ThreadLocal<SchillerNeuralNetWrapper> slstrNeuralNet;

    private IdepixSlstrCloudNNInterpreter nnInterpreter;

    private static final double SEA_ICE_CLIM_THRESHOLD = 10.0;
    private WatermaskClassifier watermaskClassifier;

    private LakeSeaIceClassification lakeSeaIceClassification;

    private File nnToUse;


    @Override
    public void initialize() throws OperatorException {
        setBands();

        readSchillerNeuralNets();
        readNNThresholds();
        nnInterpreter = IdepixSlstrCloudNNInterpreter.create();
        createTargetProduct();
        if (useSrtmLandWaterMask) {
            try {
                watermaskClassifier = new WatermaskClassifier(LAND_WATER_MASK_RESOLUTION,
                        OVERSAMPLING_FACTOR_X,
                        OVERSAMPLING_FACTOR_Y);
            } catch (IOException e) {
                throw new OperatorException("Could not initialise SRTM land-water mask", e);
            }
        }

        initLakeSeaIceClassification();
    }

    private void readSchillerNeuralNets() {
        InputStream slstrInputStream;
        try {
            slstrInputStream = getNNInputStream();
        } catch (IOException e) {
            throw new OperatorException("Cannot read specified alternative Neural Net - please check!", e);
        }
        slstrNeuralNet = SchillerNeuralNetWrapper.create(slstrInputStream);
    }

    void readNNThresholds() {
        try (Reader r = alternativeNNThresholdsFile == null ||
                SLSTR_NN_THRESHOLDS_FILE.equals(alternativeNNThresholdsFile.getName())
                ? new InputStreamReader(getClass().getResourceAsStream(SLSTR_NN_THRESHOLDS_FILE))
                : SLSTR_NN_THRESHOLDS_FILE.equals(alternativeNNThresholdsFile.getName())
                ? new InputStreamReader(getClass().getResourceAsStream(SLSTR_NN_THRESHOLDS_FILE))
                : new FileReader(alternativeNNThresholdsFile)) {
            Map<String, Object> m = (JSONObject) JSONValue.parse(r);
            for (NNThreshold t : NNThreshold.values()) {
                if (m.containsKey(t.name())) {
                    t.range = new ValueRange((Double) ((JSONArray) m.get(t.name())).get(0),
                                             (Double) ((JSONArray) m.get(t.name())).get(1),
                                             true,
                                             false);
                } else {
                    t.range = new ValueRange(0.0, 0.0, true, false);
                }
            }
        } catch (FileNotFoundException e) {
            throw new OperatorException("cannot find NN thresholds file " + alternativeNNThresholdsFile, e);
        } catch (Exception e) {
            throw new OperatorException("error reading NN thresholds file " + alternativeNNThresholdsFile, e);
        }
    }

    private InputStream getNNInputStream() throws IOException {
        if (alternativeNNFile == null || SLSTR_NET_NAME.equals(alternativeNNFile.getName())) {
            nnToUse = new File(SLSTR_NET_NAME);
            return getClass().getResourceAsStream(nnToUse.getName());
        } else if (SLSTR_NET_NAME.equals(alternativeNNFile.getName())) {
            nnToUse = new File(SLSTR_NET_NAME);
            return getClass().getResourceAsStream(nnToUse.getName());
        } else {
            nnToUse = alternativeNNFile;
            return Files.newInputStream(nnToUse.toPath());
        }
    }

    private void initLakeSeaIceClassification() {
        final ProductData.UTC startTime = l1bProduct.getStartTime();
        final int monthIndex = startTime.getAsCalendar().get(Calendar.MONTH);
        lakeSeaIceClassification = new LakeSeaIceClassification(iceMaskProduct, LakeSeaIceAuxdata.AUXDATA_DIRECTORY, monthIndex + 1);
    }

    private void setBands() {
        slstrL1bBands = IdepixSlstrUtils.getL1bBandsForClassification(slstrBandsForNN, l1bProduct, slstrRad2reflProduct);
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = l1bProduct.getSceneRasterWidth();
        int sceneHeight = l1bProduct.getSceneRasterHeight();

        targetProduct = new Product(l1bProduct.getName(), l1bProduct.getProductType(), sceneWidth, sceneHeight);

        final Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixSlstrUtils.createSlstrFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(l1bProduct, targetProduct);

        ProductUtils.copyGeoCoding(l1bProduct, targetProduct);
        targetProduct.setStartTime(l1bProduct.getStartTime());
        targetProduct.setEndTime(l1bProduct.getEndTime());
        ProductUtils.copyMetadata(l1bProduct, targetProduct);

        if (outputSchillerNNValue) {
            targetProduct.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        Tile[] slstrL1bTiles = new Tile[slstrL1bBands.length];
        for (int i = 0; i < slstrL1bBands.length; i++) {
            slstrL1bTiles[i] = getSourceTile(slstrL1bBands[i], rectangle);
        }

        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        Tile nnTargetTile = null;
        if (outputSchillerNNValue) {
            nnTargetTile = targetTiles.get(targetProduct.getBand(IdepixConstants.NN_OUTPUT_BAND_NAME));
        }
        try {
            GeoCoding geoCoding = l1bProduct.getSceneGeoCoding();
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    int waterFraction = -1;
                    if (useSrtmLandWaterMask) {
                        waterFraction = watermaskClassifier.getWaterMaskFraction(geoCoding, x, y);
                    }

                    initCloudFlag(targetTiles.get(cloudFlagTargetBand), slstrL1bTiles, y, x);
                    final boolean isBright = false;  // todo: find a criterion
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, isBright);
                    final boolean isCoastlineFromAppliedMask = classifyCoastline(x, y, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, isCoastlineFromAppliedMask);

                    final boolean isLandFromAppliedMask = isSlstrLandPixel(x, y, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, isLandFromAppliedMask ||
                            isCoastlineFromAppliedMask);

                    if (isLandFromAppliedMask || isCoastlineFromAppliedMask) {
                        classifyOverLand(slstrL1bTiles, cloudFlagTargetTile, nnTargetTile, x, y);
                    } else {
                        classifyOverWater(slstrL1bTiles, cloudFlagTargetTile, nnTargetTile, x, y);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide Idepix SLSTR pixel classification:\n" + e.getMessage(), e);
        }
    }

    private boolean classifyCoastline(int x, int y, int waterFraction) {
        return waterFraction >= 0 && isCoastlinePixel(x, y, waterFraction);
    }

    private void classifyOverWater(Tile[] slstrL1bTiles,
                                   Tile cloudFlagTargetTile, Tile nnTargetTile, int x, int y) {

        // basically the same as over land
        final double nnOutput = classifyOverLand(slstrL1bTiles, cloudFlagTargetTile, nnTargetTile, x, y);

        // additional check for ice
        final GeoPos geoPos = IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y);
        final boolean checkForSeaIce = !useLakeAndSeaIceClimatology || isPixelClassifiedAsLakeSeaIce(geoPos);
        if (checkForSeaIce && nnInterpreter.isSnowIce(nnOutput)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
        }
    }

    private double classifyOverLand(Tile[] slstrL1bTiles,
                                  Tile cloudFlagTargetTile, Tile nnTargetTile,
                                  int x, int y) {

        final double nnOutput = getSlstrNNOutput(x, y, slstrL1bTiles);

        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

            boolean isCloudSure =  nnInterpreter.isCloudSure(nnOutput);
            boolean isCloudAmbiguous = nnInterpreter.isCloudAmbiguous(nnOutput, true, false);
            boolean isCloud = isCloudAmbiguous || isCloudSure;
            boolean isSnowIce = nnInterpreter.isSnowIce(nnOutput);

            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, isCloudAmbiguous);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, isCloudSure);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, isCloud);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, isSnowIce);
        }

        if (nnTargetTile != null) {
            nnTargetTile.setSample(x, y, nnOutput);
        }

        return nnOutput;
    }

    private boolean isSlstrLandPixel(int x, int y, int waterFraction) {
        if (waterFraction < 0) {
            return true;
        } else {
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y).lat > -58f) {
                // values bigger than 100 indicate no data
                if (waterFraction <= 100) {
                    // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                    // is always 0 or 100!! (TS, OD, 20140502)
                    return waterFraction == 0;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private double getSlstrNNOutput(int x, int y, Tile[] l1bTiles) {
        SchillerNeuralNetWrapper nnWrapper;
        try {
            nnWrapper = slstrNeuralNet.get();
        } catch (Exception e) {
            throw new OperatorException("Cannot get values from Neural Net file - check format! " + e.getMessage());
        }
        double[] nnInput = nnWrapper.getInputVector();

        if (nnInput.length != l1bTiles.length) {
            final String message = "Invalid selection of bands for NN classifiation: " +
                    "Selected Neural Net '" + nnToUse + "' expects " +
                    nnInput.length + " input bands, but only " + l1bTiles.length + " were selected. Please check!";
            throw new OperatorException(message);
        }

        for (int i = 0; i < nnInput.length; i++) {
            final float l1bValue = l1bTiles[i].getSampleFloat(x, y);
            if (l1bValue > 100.0) {
                // certainly a BT
                nnInput[i] = l1bValue;
            } else {
                // certainly a reflectance
                nnInput[i] = Math.sqrt(l1bValue);
            }
        }

        final double nnResult = nnWrapper.getNeuralNet().calc(nnInput)[0];
//        if (x == 2222 && y == 625) {
//            // cloud over land
//            System.out.println("Cloud over land: x,y = " + x + "," + y);
//            System.out.println("nnResult = " + nnResult);
//            for (int i = 0; i < nnInput.length; i++) {
//                System.out.println("nnInput[" + i + "] = " + nnInput[i]);
//            }
//        }
//        if (x == 300 && y == 2323) {
//            // cloud over water
//            System.out.println("Cloud over water: x,y = " + x + "," + y);
//            System.out.println("nnResult = " + nnResult);
//            for (int i = 0; i < nnInput.length; i++) {
//                System.out.println("nnInput[" + i + "] = " + nnInput[i]);
//            }
//        }
//        if (x == 1350 && y == 2200) {
//            // no cloud over land
//            System.out.println("NO cloud over land: x,y = " + x + "," + y);
//            System.out.println("nnResult = " + nnResult);
//            for (int i = 0; i < nnInput.length; i++) {
//                System.out.println("nnInput[" + i + "] = " + nnInput[i]);
//            }
//        }
//        if (x == 1500 && y == 1800) {
//            // no cloud over water
//            System.out.println("NO cloud over water: x,y = " + x + "," + y);
//            System.out.println("nnResult = " + nnResult);
//            for (int i = 0; i < nnInput.length; i++) {
//                System.out.println("nnInput[" + i + "] = " + nnInput[i]);
//            }
//        }

        return nnResult;

    }

    private boolean isPixelClassifiedAsLakeSeaIce(GeoPos geoPos) {
        final int lakeSeaIceMaskX = (int) (180.0 + geoPos.lon);
        final int lakeSeaIceMaskY = (int) (90.0 - geoPos.lat);
        final float monthlyMaskValue = lakeSeaIceClassification.getMonthlyMaskValue(lakeSeaIceMaskX, lakeSeaIceMaskY);
        return monthlyMaskValue >= SEA_ICE_CLIM_THRESHOLD;
    }

    private boolean isCoastlinePixel(int x, int y, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        // values bigger than 100 indicate no data
        // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y).lat > -58f &&
                waterFraction < 100 && waterFraction > 0;
    }

    private void initCloudFlag(Tile targetTile, Tile[] slstrl1bTiles, int y, int x) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        float[] slstrL1bValues = new float[slstrl1bTiles.length];
        for (int i = 0; i < slstrl1bTiles.length; i++) {
            slstrL1bValues[i] = slstrl1bTiles[i].getSampleFloat(x, y);
        }

        final boolean l1Invalid = false;  // todo: define how to set this
        // can be applied to both refls or BTs
        final boolean reflectancesValid = IdepixIO.areAllReflectancesValid(slstrL1bValues);

        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid || !reflectancesValid);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, false);
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IdepixSlstrClassificationOp.class);
        }
    }
}
