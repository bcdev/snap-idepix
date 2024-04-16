package org.esa.snap.idepix.olci;

import com.bc.ceres.binding.ValueRange;
import com.bc.ceres.core.ProgressMonitor;
import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Calendar;
import java.util.Map;

import static org.esa.snap.idepix.core.IdepixConstants.*;
import static org.esa.snap.idepix.olci.IdepixOlciCloudNNInterpreter.NNThreshold;

/**
 * OLCI pixel classification operator.
 * Processes both land and water, so we get rid of separate land/water merge operator.
 * Land pixels are classified from NN approach + rho_toa threshold.
 * Water pixels are classified from NN approach + OC-CCI algorithm (sea ice, glint).
 * <p>
 * Current classification in more detail (see email OD --> group, 20171115):
 * * - OLCI water:
 * ## cloud_sure: nn_value in [1.1, 2.75] AND rho_toa[band_17] > 0.2
 * ## cloud_ambiguous:
 * +++ nn_value in [2.75, 3.5] für 'Glint-Pixel' gemäß L1-glint-flag AND rho_toa[band_17] > 0.08
 * +++ nn_value in [2.75, 3.75] für no-glint-Pixel AND rho_toa[band_17] > 0.08
 * <p>
 * - OLCI land:
 * ## cloud_sure: nn_value in [1.1, 2.75] AND rho_toa[band_3] > 0.3
 * ## cloud_ambiguous: nn_value in [2.75, 3.85] für no-glint-Pixel AND rho_toa[band_3] > 0.25
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Olci.Classification",
        version = "3.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix land pixel classification operator for OLCI.")
public class IdepixOlciClassificationOp extends Operator {

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
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

    @Parameter(defaultValue = "true",
            description = "Restrict NN test for sea/lake ice to ice climatology area.",
            label = "Use sea/lake ice climatology as filter")
    private boolean useLakeAndSeaIceClimatology;

    @Parameter(defaultValue = "false",
            label = " Use SRTM Land/Water mask",
            description = "If selected, SRTM Land/Water mask is used instead of L1b land flag. " +
                    "Slower, but in general more precise.")
    private boolean useSrtmLandWaterMask;


    @SourceProduct(alias = "l1b", description = "The L1b product.")
    private Product l1bProduct;

    @SourceProduct(alias = "iceMask",
            label = "Ice mask product", optional = true,
            description = "User defined ice mask product. If not provided, default climatology is used.")
    private Product iceMaskProduct;

    @SourceProduct(alias = "rhotoa")
    private Product rad2reflProduct;

    @SourceProduct(alias = "o2Corr", optional = true)
    private Product o2CorrProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    private Band[] olciReflBands;

    private Band surface13Band;
    private Band trans13Band;

    private static final String OLCI_2018_NET_NAME = "11x10x4x3x2_207.9.net";
    private static final String OLCI_2018_NN_THRESHOLDS_FILE = "11x10x4x3x2_207.9-thresholds.json";
    private static final String OLCI_202306_NET_NAME = "class-sequential-i21x42x8x4x2o1-5489.net";
    private static final String OLCI_202306_NN_THRESHOLDS_FILE = "class-sequential-i21x42x8x4x2o1-5489-thresholds.json";

    private static final double THRESH_LAND_MINBRIGHT1 = 0.3;
    private static final double THRESH_LAND_MINBRIGHT2 = 0.25;  // test OD 20170411

    private static final double THRESH_WATER_MINBRIGHT1 = 0.2;
    private static final double THRESH_WATER_MINBRIGHT2 = 0.08; // CB 20170411

    private ThreadLocal<SchillerNeuralNetWrapper> olciAllNeuralNet;

    private IdepixOlciCloudNNInterpreter nnInterpreter;

    private static final double SEA_ICE_CLIM_THRESHOLD = 10.0;
    private GeometryFactory gf;
    private Polygon arcticPolygon;
    private Polygon antarcticaPolygon;
    private WatermaskClassifier watermaskClassifier;

    private LakeSeaIceClassification lakeSeaIceClassification;


    @Override
    public void initialize() throws OperatorException {
        setBands();
        readSchillerNeuralNets();
        readNNThresholds();
        nnInterpreter = IdepixOlciCloudNNInterpreter.create();
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

        if (o2CorrProduct != null) {
            surface13Band = o2CorrProduct.getBand("surface_13");
            trans13Band = o2CorrProduct.getBand("trans_13");
            gf = new GeometryFactory();
            arcticPolygon =
                    IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ARCTIC_POLYGON_COORDS);
            antarcticaPolygon =
                    IdepixOlciUtils.createPolygonFromCoordinateArray(IdepixOlciConstants.ANTARCTICA_POLYGON_COORDS);
        }
    }

    private void readSchillerNeuralNets() {
        InputStream olciAllInputStream;
        try {
            olciAllInputStream = getNNInputStream();
        } catch (IOException e) {
            throw new OperatorException("Cannot read specified alternative Neural Net - please check!", e);
        }
        olciAllNeuralNet = SchillerNeuralNetWrapper.create(olciAllInputStream);
    }

    void readNNThresholds() {
        try (Reader r = alternativeNNThresholdsFile == null ||
                OLCI_202306_NN_THRESHOLDS_FILE.equals(alternativeNNThresholdsFile.getName())
                ? new InputStreamReader(getClass().getResourceAsStream(OLCI_202306_NN_THRESHOLDS_FILE))
                : OLCI_2018_NN_THRESHOLDS_FILE.equals(alternativeNNThresholdsFile.getName())
                ? new InputStreamReader(getClass().getResourceAsStream(OLCI_2018_NN_THRESHOLDS_FILE))
                : new FileReader(alternativeNNThresholdsFile)) {
            Map<String, Object> m = (JSONObject) JSONValue.parse(r);
            for (NNThreshold t : NNThreshold.values()) {
                if (m.containsKey(t.name())) {
                    t.range = new ValueRange((Double) ((JSONArray) m.get(t.name())).get(0),
                                             (Double) ((JSONArray) m.get(t.name())).get(1),
                                             true,
                                             false);
                } else {
                    t.range = new ValueRange(0.0, 0.0,true, false);
                }
            }
        } catch (FileNotFoundException e) {
            throw new OperatorException("cannot find NN thresholds file " + alternativeNNThresholdsFile, e);
        } catch (Exception e) {
            throw new OperatorException("error reading NN thresholds file " + alternativeNNThresholdsFile, e);
        }
    }

    private InputStream getNNInputStream() throws IOException {
        if (alternativeNNFile == null || OLCI_202306_NET_NAME.equals(alternativeNNFile.getName())) {
            return getClass().getResourceAsStream(OLCI_202306_NET_NAME);
        } else if (OLCI_2018_NET_NAME.equals(alternativeNNFile.getName())) {
            return getClass().getResourceAsStream(OLCI_2018_NET_NAME);
        } else {
            return Files.newInputStream(alternativeNNFile.toPath());
        }
    }

    private void initLakeSeaIceClassification() {
        final ProductData.UTC startTime = l1bProduct.getStartTime();
        final int monthIndex = startTime.getAsCalendar().get(Calendar.MONTH);
        lakeSeaIceClassification = new LakeSeaIceClassification(iceMaskProduct, LakeSeaIceAuxdata.AUXDATA_DIRECTORY, monthIndex + 1);
    }

    private void setBands() {
        olciReflBands = new Band[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].substring(0, suffixStart);
            olciReflBands[i] = rad2reflProduct.getBand(reflBandname + "_reflectance");
        }
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = l1bProduct.getSceneRasterWidth();
        int sceneHeight = l1bProduct.getSceneRasterHeight();

        targetProduct = new Product(l1bProduct.getName(), l1bProduct.getProductType(), sceneWidth, sceneHeight);

        final Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixOlciUtils.createOlciFlagCoding();
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

        Tile surface13Tile = null;
        Tile trans13Tile = null;
        if (surface13Band != null && trans13Band != null) {
            surface13Tile = getSourceTile(surface13Band, rectangle);
            trans13Tile = getSourceTile(trans13Band, rectangle);
        }

        final Band olciQualityFlagBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
        final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, rectangle);

        Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], rectangle);
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

                    initCloudFlag(olciQualityFlagTile, targetTiles.get(cloudFlagTargetBand), olciReflectanceTiles, y, x);
                    final boolean isBright = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_BRIGHT);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, isBright);
                    final boolean isCoastlineFromAppliedMask = classifyCoastline(olciQualityFlagTile, x, y, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, isCoastlineFromAppliedMask);

                    final boolean isLandFromAppliedMask = isOlciLandPixel(x, y, olciQualityFlagTile, waterFraction);
                    final boolean isInlandWaterFromAppliedMask = isOlciInlandWaterPixel(x, y, olciQualityFlagTile, waterFraction);
                    //todo: for CGLOPS, coastlines are added to LAND to exclude them from L2 processing
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, isLandFromAppliedMask ||
                            isCoastlineFromAppliedMask);

                    // todo: for cglops, coastlines are treated as LAND
                    if ((isLandFromAppliedMask && !isInlandWaterFromAppliedMask) || isCoastlineFromAppliedMask) {
                        classifyOverLand(olciReflectanceTiles, cloudFlagTargetTile, nnTargetTile,
                                surface13Tile, trans13Tile, x, y);
                    } else {
                        classifyOverWater(olciQualityFlagTile, olciReflectanceTiles,
                                          cloudFlagTargetTile, nnTargetTile, x, y, isInlandWaterFromAppliedMask);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean classifyCoastline(Tile olciQualityFlagTile, int x, int y, int waterFraction) {
        return waterFraction < 0 ?
                olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_COASTLINE) :
                isCoastlinePixel(x, y, waterFraction);
    }

    private void classifyOverWater(Tile olciQualityFlagTile, Tile[] olciReflectanceTiles,
                                   Tile cloudFlagTargetTile, Tile nnTargetTile, int x, int y, boolean isInlandWater) {


        double nnOutput = getOlciNNOutput(x, y, olciReflectanceTiles);
        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

            final boolean isGlint = isGlintPixel(x, y, olciQualityFlagTile);
            // CB 20170406:
            final boolean cloudSure = olciReflectanceTiles[16].getSampleFloat(x, y) > THRESH_WATER_MINBRIGHT1 &&
                    nnInterpreter.isCloudSure(nnOutput);
            final boolean cloudAmbiguous = olciReflectanceTiles[16].getSampleFloat(x, y) > THRESH_WATER_MINBRIGHT2 &&
                    nnInterpreter.isCloudAmbiguous(nnOutput, false, isGlint);

            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguous);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, cloudSure);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, cloudAmbiguous || cloudSure);

            final GeoPos geoPos = IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y);
            final boolean checkForSeaIce = !useLakeAndSeaIceClimatology || isPixelClassifiedAsLakeSeaIce(geoPos);
            if (checkForSeaIce && nnInterpreter.isSnowIce(nnOutput)) {
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            }

            if (isInlandWater && cloudAmbiguous) {
                final double NDVI = getNDVI(x, y, olciReflectanceTiles);
                if (NDVI > 0.07) {
                    //catches mixed pixels at coast lines.
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                    // todo: for CGLOPS it is OK, if these mixed pixels are classified as LAND!
                    cloudFlagTargetTile.setSample(x, y, IDEPIX_LAND, true);

                }
                if (olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_BRIGHT)) {
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
                }
            }
        }
        if (outputSchillerNNValue) {
            nnTargetTile.setSample(x, y, nnOutput);
        }
    }

    private void classifyOverLand(Tile[] olciReflectanceTiles,
                                  Tile cloudFlagTargetTile, Tile nnTargetTile,
                                  Tile surface13Tile, Tile trans13Tile,
                                  int x, int y) {

        final double nnOutput = getOlciNNOutput(x, y, olciReflectanceTiles);

        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            final float olciReflectance3 = olciReflectanceTiles[2].getSampleFloat(x, y);

            // CB 20170406:
            boolean isCloudSure = olciReflectance3 > THRESH_LAND_MINBRIGHT1 &&
                    nnInterpreter.isCloudSure(nnOutput);
            boolean isCloudAmbiguous = olciReflectance3 > THRESH_LAND_MINBRIGHT2 &&
                    nnInterpreter.isCloudAmbiguous(nnOutput, true, false);
            boolean isCloud = isCloudAmbiguous || isCloudSure;

            boolean isSnowIce = nnInterpreter.isSnowIce(nnOutput);

            // cloud over snow from harmonisation approach:
            // pixel_classif_flags.IDEPIX_LAND && ((Oa21_reflectance > 0.5 && surface_13 - trans_13 < 0.01) || Oa21_reflectance > 0.76)
            double surface13;
            double trans13;
            if (surface13Tile != null && trans13Tile != null) {
                GeoPos geoPos = IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y);
                Coordinate coord = new Coordinate(geoPos.getLon(), geoPos.getLat());
                boolean isInsideGreenland = IdepixOlciUtils.isCoordinateInsideGeometry(coord, arcticPolygon, gf);
                boolean isInsideAntarctica = IdepixOlciUtils.isCoordinateInsideGeometry(coord, antarcticaPolygon, gf);
                if (isInsideGreenland || isInsideAntarctica) {
                    surface13 = surface13Tile.getSampleDouble(x, y);
                    trans13 = trans13Tile.getSampleDouble(x, y);
                    float olciReflectance21 = olciReflectanceTiles[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length - 1].getSampleFloat(x, y);
                    boolean isCloudOverSnow = (olciReflectance21 > 0.5 && surface13 - trans13 < 0.01) || olciReflectance21 > 0.76f;
                    if (isCloudOverSnow) {
                        isCloudSure = true;
                        isCloud = true;
                        isSnowIce = false;
                    } else {
                        if (isCloud) {
                            // this overrules the NN which likely classified snow/ice as cloud
                            isSnowIce = true;
                            isCloud = false;
                            isCloudSure = false;
                            isCloudAmbiguous = false;
                        }
                    }
                }
            }

            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, isCloudAmbiguous);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, isCloudSure);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, isCloud);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, isSnowIce);
        }

        if (nnTargetTile != null) {
            nnTargetTile.setSample(x, y, nnOutput);
        }
    }

    private boolean isOlciLandPixel(int x, int y, Tile olciL1bFlagTile, int waterFraction) {
        if (waterFraction < 0) {
            boolean landFlag = olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND);
            boolean inlandWaterFlag = olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER);
            return landFlag && !inlandWaterFlag;
        } else {
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y).lat > -58f) {
                // values bigger than 100 indicate no data
                if (waterFraction <= 100) {
                    // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                    // is always 0 or 100!! (TS, OD, 20140502)
                    return waterFraction == 0;
                } else {
                    return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND);
                }
            } else {
                return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND);
            }
        }
    }

    private boolean isOlciInlandWaterPixel(int x, int y, Tile olciL1bFlagTile, int waterFraction) {
        if (waterFraction < 0) {
            // SRTM has not been used! Rely on OLCI flags!
            return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND) &&
                    olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER);
        } else {
            // SRTM water mask is used.
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (IdepixUtils.getGeoPos(l1bProduct.getSceneGeoCoding(), x, y).lat > -58f) {
                // values bigger than 100 indicate no data
                if (waterFraction <= 100) {
                    // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                    // is always 0 or 100!! (TS, OD, 20140502)
                    return waterFraction > 0 && olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND);
                } else {
                    return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND) &&
                            olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER);
                }
            } else {
                return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND) &&
                        olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER);
            }
        }
    }

    private double getOlciNNOutput(int x, int y, Tile[] rhoToaTiles) {
        SchillerNeuralNetWrapper nnWrapper;
        try {
            nnWrapper = olciAllNeuralNet.get();
        } catch (Exception e) {
            throw new OperatorException("Cannot get values from Neural Net file - check format! " + e.getMessage());
        }
        double[] nnInput = nnWrapper.getInputVector();
        for (int i = 0; i < nnInput.length; i++) {
            nnInput[i] = Math.sqrt(rhoToaTiles[i].getSampleFloat(x, y));
        }
        return nnWrapper.getNeuralNet().calc(nnInput)[0];
    }

    private double getNDVI(int x, int y, Tile[] rhoToaTiles) {
        double rho17 = rhoToaTiles[16].getSampleDouble(x, y);
        double rho8 = rhoToaTiles[7].getSampleDouble(x, y);
        return (rho17 - rho8) / (rho17 + rho8);
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

    private boolean isGlintPixel(int x, int y, Tile l1FlagsTile) {
        return l1FlagsTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_GLINT);
    }

    private void initCloudFlag(Tile olciL1bFlagTile, Tile targetTile, Tile[] olciReflectanceTiles, int y, int x) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        float[] olciReflectances = new float[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            olciReflectances[i] = olciReflectanceTiles[i].getSampleFloat(x, y);
        }

        final boolean l1Invalid = olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
        final boolean reflectancesValid = IdepixIO.areAllReflectancesValid(olciReflectances);

        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid || !reflectancesValid);
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

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciClassificationOp.class);
        }
    }

}
