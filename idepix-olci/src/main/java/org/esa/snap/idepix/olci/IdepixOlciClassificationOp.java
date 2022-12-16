package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
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
import org.esa.snap.idepix.core.seaice.SeaIceClassifier;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.snap.watermask.operator.WatermaskClassifier;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Map;

import static org.esa.snap.idepix.core.IdepixConstants.*;

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
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix land pixel classification operator for OLCI.")
public class IdepixOlciClassificationOp extends Operator {

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @Parameter(defaultValue = "false",
            description = "Check for sea/lake ice also outside Sea Ice Climatology area.",
            label = "Check for sea/lake ice also outside Sea Ice Climatology area"
    )
    private boolean ignoreSeaIceClimatology;

    @Parameter(defaultValue = "false",
            label = " Use SRTM Land/Water mask",
            description = "If selected, SRTM Land/Water mask is used instead of L1b land flag. " +
                    "Slower, but in general more precise.")
    private boolean useSrtmLandWaterMask;


    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "rhotoa")
    private Product rad2reflProduct;

    @SourceProduct(alias = "o2Corr", optional = true)
    private Product o2CorrProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;


    private Band[] olciReflBands;

    private Band surface13Band;
    private Band trans13Band;

    private static final String OLCI_ALL_NET_NAME = "11x10x4x3x2_207.9.net";

    private static final double THRESH_LAND_MINBRIGHT1 = 0.3;
    private static final double THRESH_LAND_MINBRIGHT2 = 0.25;  // test OD 20170411

    private static final double THRESH_WATER_MINBRIGHT1 = 0.2;
    private static final double THRESH_WATER_MINBRIGHT2 = 0.08; // CB 20170411

    private ThreadLocal<SchillerNeuralNetWrapper> olciAllNeuralNet;

    private GeometryFactory gf;
    private Polygon arcticPolygon;
    private Polygon antarcticaPolygon;
    private WatermaskClassifier watermaskClassifier;
    private WaterSnowIceClassification waterSnowIceClassification;
    private WaterSnowIceClassification landSnowIceClassification;


    @Override
    public void initialize() throws OperatorException {
        setBands();
        readSchillerNeuralNets();
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

        waterSnowIceClassification = initSnowIceClassifier();
        landSnowIceClassification = new WaterSnowIceClassification();

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
        InputStream olciAllIS = getClass().getResourceAsStream(OLCI_ALL_NET_NAME);
        olciAllNeuralNet = SchillerNeuralNetWrapper.create(olciAllIS);
    }

    private WaterSnowIceClassification initSnowIceClassifier() {
        if(ignoreSeaIceClimatology) {
            return new WaterSnowIceClassification();
        } else {
            return new WaterSnowIceClassification(createSeaIceClassifier());
        }

    }

    private SeaIceClassifier createSeaIceClassifier() {
        final ProductData.UTC startTime = getSourceProduct().getStartTime();
        final int monthIndex = startTime.getAsCalendar().get(Calendar.MONTH);
        try {
            return new SeaIceClassifier(monthIndex + 1);
        } catch (IOException e) {
            throw new OperatorException("Could not initialise climatology for snow/ice classification", e);
        }
    }

    private void setBands() {
        olciReflBands = new Band[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandName = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].substring(0, suffixStart);
            olciReflBands[i] = rad2reflProduct.getBand(reflBandName + "_reflectance");
        }
    }

    private void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        final Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixOlciUtils.createOlciFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

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

        final Band olciQualityFlagBand = sourceProduct.getBand(IdepixOlciConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
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
            GeoCoding geoCoding = sourceProduct.getSceneGeoCoding();
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    int waterFraction = useSrtmLandWaterMask ? watermaskClassifier.getWaterMaskFraction(geoCoding, x, y) : -1;

                    initCloudFlag(olciQualityFlagTile, targetTiles.get(cloudFlagTargetBand), olciReflectanceTiles, y, x);
                    final boolean isBright = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_BRIGHT);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, isBright);
                    final boolean isCoastlineFromAppliedMask = classifyCoastline(olciQualityFlagTile, y, x, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, isCoastlineFromAppliedMask);

                    final boolean isLandFromAppliedMask = isOlciLandPixel(x, y, olciQualityFlagTile, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, isLandFromAppliedMask);
                    if (isLandFromAppliedMask && !isOlciInlandWaterPixel(x, y, olciQualityFlagTile)) {
                        classifyOverLand(olciQualityFlagTile, olciReflectanceTiles, cloudFlagTargetTile, nnTargetTile,
                                         surface13Tile, trans13Tile, y, x);
                    } else {
                        classifyOverWater(olciQualityFlagTile, olciReflectanceTiles,
                                          cloudFlagTargetTile, nnTargetTile, y, x);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean classifyCoastline(Tile olciQualityFlagTile, int y, int x, int waterFraction) {
        return waterFraction < 0 ?
                olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_COASTLINE) :
                isCoastlinePixel(x, y, waterFraction);
    }

    private void classifyOverWater(Tile olciQualityFlagTile, Tile[] olciReflectanceTiles,
                                   Tile cloudFlagTargetTile, Tile nnTargetTile, int y, int x) {


        double nnOutput = getOlciNNOutput(x, y, olciReflectanceTiles)[0];
        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

            final boolean isGlint = isGlintPixel(x, y, olciQualityFlagTile);
            // CB 20170406:
            final boolean cloudSure = olciReflectanceTiles[16].getSampleFloat(x, y) > THRESH_WATER_MINBRIGHT1 &&
                    IdepixOlciCloudNNInterpreter.isCloudSure(nnOutput);
            final boolean cloudAmbiguous = olciReflectanceTiles[16].getSampleFloat(x, y) > THRESH_WATER_MINBRIGHT2 &&
                    IdepixOlciCloudNNInterpreter.isCloudAmbiguous(nnOutput, false, isGlint);

            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguous);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, cloudSure);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, cloudAmbiguous || cloudSure);

            final GeoPos geoPos = IdepixUtils.getGeoPos(getSourceProduct().getSceneGeoCoding(), x, y);
            // 'sea' ice can be also ice over inland water!
            boolean isInvalid = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);

            boolean isSeaIce = waterSnowIceClassification.classify(x, y, geoPos, isInvalid, cloudAmbiguous || cloudSure,
                    nnOutput, olciReflectanceTiles);

            if (isSeaIce) {
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            }
        }

        if (outputSchillerNNValue) {
            nnTargetTile.setSample(x, y, nnOutput);
        }
    }

    private void classifyOverLand(Tile olciQualityFlagTile, Tile[] olciReflectanceTiles,
                                  Tile cloudFlagTargetTile, Tile nnTargetTile,
                                  Tile surface13Tile, Tile trans13Tile,
                                  int y, int x) {
        float[] olciReflectances = new float[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            olciReflectances[i] = olciReflectanceTiles[i].getSampleFloat(x, y);
        }
        final float olciReflectance21 = olciReflectances[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length - 1];

        SchillerNeuralNetWrapper nnWrapper = olciAllNeuralNet.get();
        double[] inputVector = nnWrapper.getInputVector();
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = Math.sqrt(olciReflectances[i]);
        }

        final double nnOutput = nnWrapper.getNeuralNet().calc(inputVector)[0];

        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

            // CB 20170406:
            boolean isCloudSure = olciReflectances[2] > THRESH_LAND_MINBRIGHT1 &&
                    IdepixOlciCloudNNInterpreter.isCloudSure(nnOutput);
            boolean isCloudAmbiguous = olciReflectances[2] > THRESH_LAND_MINBRIGHT2 &&
                    IdepixOlciCloudNNInterpreter.isCloudAmbiguous(nnOutput, true, false);
            boolean isCloud = isCloudAmbiguous || isCloudSure;

//            boolean isSnowIce = IdepixOlciCloudNNInterpreter.isSnowIce(nnOutput);
            final GeoPos geoPos = IdepixUtils.getGeoPos(getSourceProduct().getSceneGeoCoding(), x, y);
            boolean isInvalid = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);

            boolean isSnowIce = landSnowIceClassification.classify(x, y, geoPos, isInvalid, isCloud,
                    nnOutput, olciReflectanceTiles);

            double surface13;
            double trans13;
            if (surface13Tile != null && trans13Tile != null) {
                final Coordinate coord = new Coordinate(geoPos.getLon(), geoPos.getLat());
                final boolean isInsideGreenland =
                        IdepixOlciUtils.isCoordinateInsideGeometry(coord, arcticPolygon, gf);
                final boolean isInsideAntarctica =
                        IdepixOlciUtils.isCoordinateInsideGeometry(coord, antarcticaPolygon, gf);
                if (isInsideGreenland || isInsideAntarctica) {
                    surface13 = surface13Tile.getSampleDouble(x, y);
                    trans13 = trans13Tile.getSampleDouble(x, y);
                    final boolean isCloudOverSnow =
                            (olciReflectance21 > 0.5 && surface13 - trans13 < 0.01) || olciReflectance21 > 0.76f;
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
            return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_LAND);
        } else {
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (IdepixUtils.getGeoPos(getSourceProduct().getSceneGeoCoding(), x, y).lat > -58f) {
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

    private boolean isOlciInlandWaterPixel(int x, int y, Tile olciL1bFlagTile) {
        return olciL1bFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER);
    }

    private double[] getOlciNNOutput(int x, int y, Tile[] rhoToaTiles) {
        SchillerNeuralNetWrapper nnWrapper = olciAllNeuralNet.get();
        double[] nnInput = nnWrapper.getInputVector();
        for (int i = 0; i < nnInput.length; i++) {
            nnInput[i] = Math.sqrt(rhoToaTiles[i].getSampleFloat(x, y));
        }
        return nnWrapper.getNeuralNet().calc(nnInput);
    }

    private boolean isCoastlinePixel(int x, int y, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        // values bigger than 100 indicate no data
        // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502)
        return IdepixUtils.getGeoPos(getSourceProduct().getSceneGeoCoding(), x, y).lat > -58f &&
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
