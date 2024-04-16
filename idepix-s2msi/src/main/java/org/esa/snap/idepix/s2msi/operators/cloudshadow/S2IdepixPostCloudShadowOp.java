package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.lang3.ArrayUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import javax.media.jai.BorderExtenderConstant;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Tonio Fincke, Dagmar Müller
 */
@OperatorMetadata(alias = "Idepix.S2.CloudShadow.Postprocess",
        category = "Optical",
        authors = "Grit Kirches, Michael Paperin, Olaf Danne, Tonio Fincke, Dagmar Müller",
        copyright = "(c) Brockmann Consult GmbH",
        version = "2.0",
        internal = true,
        description = "Post-processing for algorithm detecting cloud shadow...")

public class S2IdepixPostCloudShadowOp extends Operator {

    @SourceProduct(description = "The classification product from which to take the classification band.")
    private Product s2ClassifProduct;

    @SourceProduct(description = "The product from which to take other bands than the classification band.")
    private Product s2BandsProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The mode by which clouds are detected. There are three options: Land/Water, Multiple Bands" +
            "or Single Band", valueSet = {"LandWater", "MultiBand", "SingleBand"}, defaultValue = "LandWater")
    private String mode;

    @Parameter(description = "Whether to also compute mountain shadow", defaultValue = "true")
    private boolean computeMountainShadow;

    @Parameter(description = "Offset along cloud path to minimum reflectance (over all tiles)", defaultValue = "0")
    private int bestOffset;

    // hidden parameter, may be set to true for mosaics with larger invalid areas
    private boolean skipInvalidTiles;

    @Parameter(notNull = true)
    private float sunZenithMean;

    @Parameter(notNull = true)
    private float sunAzimuthMean;

    private Band sourceBandClusterA;
    private Band sourceBandClusterB;

    private Band sourceBandFlag1;

    private RasterDataNode sourceAltitude;

    private boolean debug = false;

    private Band targetBandCloudShadow;
    private Band targetBandCloudID;
    private Band targetBandTileID;
    private Band targetBandShadowID;
    private Band targetBandCloudTest;

    private static int maxcloudTop = 10000;
    //for calculating a single cloud path
    private float minAltitude = 0;

    private static double spatialResolution;  //[m]
    static int clusterCountDefine = 4;
    private static final String sourceBandNameClusterA = "B8A";
    private static final String sourceBandNameClusterB = "B3";
    private static final String sourceFlagName1 = "pixel_classif_flags";
    private final static String BAND_NAME_CLOUD_SHADOW = "FlagBand";
    private final static String BAND_NAME_CLOUD_ID = "cloud_ids";
    private final static String BAND_NAME_TILE_ID = "tile_ids";
    private final static String BAND_NAME_SHADOW_ID = "shadow_ids";
    private final static String BAND_NAME_CLOUD_TEST = "cloud_test";
    private Mode analysisMode;

    private static final String F_INVALID_DESCR_TEXT = "Invalid pixels";
    private static final String F_CLOUD_DESCR_TEXT = "Cloud pixels";
    private static final String F_MOUNTAIN_SHADOW_DESCR_TEXT = "Mountain shadow pixels";
    private static final String F_CLOUD_SHADOW_DESCR_TEXT = "Cloud shadow pixels";
    private static final String F_LAND_DESCR_TEXT = "Land pixels";
    private static final String F_WATER_DESCR_TEXT = "Water pixels";
    private static final String F_HAZE_DESCR_TEXT = "Potential haze/semitransparent cloud pixels";
    private static final String F_POTENTIAL_CLOUD_SHADOW_DESCR_TEXT = "Potential cloud shadow pixels";
    private static final String F_SHIFTED_CLOUD_SHADOW_DESCR_TEXT = "Shifted cloud mask as shadow pixels";
    private static final String F_CLOUD_SHADOW_COMB_DESCR_TEXT = "cloud mask (combination)";
    private static final String F_CLOUD_BUFFER_DESCR_TEXT = "Cloud buffer";
    private static final String F_SHIFTED_CLOUD_SHADOW_GAPS_DESCR_TEXT = "shifted cloud mask in cloud gap";
    private static final String F_RECOMMENDED_CLOUD_SHADOW_DESCR_TEXT = "combination of shifted cloud mask in cloud gap + cloud-shadow_comb, or if bestOffset=0: clustered";

    private static final int F_WATER = 0;
    private static final int F_LAND = 1;
    private static final int F_CLOUD = 2;
    private static final int F_HAZE = 3;
    private static final int F_CLOUD_SHADOW = 4;
    private static final int F_MOUNTAIN_SHADOW = 5;
    private static final int F_INVALID = 6;
    private static final int F_CLOUD_BUFFER = 7;
    private static final int F_POTENTIAL_CLOUD_SHADOW = 8;
    private static final int F_SHIFTED_CLOUD_SHADOW = 9;
    private static final int F_CLOUD_SHADOW_COMB = 10;
    private static final int F_SHIFTED_CLOUD_SHADOW_GAPS = 11;
    private static final int F_RECOMMENDED_CLOUD_SHADOW = 12;

    @Override
    public void initialize() throws OperatorException {
        skipInvalidTiles = Boolean.getBoolean(S2IdepixUtils.INVALID_TILES_PROPERTIES);

        targetProduct = new Product(s2BandsProduct.getName(), s2BandsProduct.getProductType(),
                s2BandsProduct.getSceneRasterWidth(), s2BandsProduct.getSceneRasterHeight());
        ProductUtils.copyGeoCoding(s2BandsProduct, targetProduct);
        targetBandCloudShadow = targetProduct.addBand(BAND_NAME_CLOUD_SHADOW, ProductData.TYPE_INT32);
        if (debug) {
            targetBandCloudID = targetProduct.addBand(BAND_NAME_CLOUD_ID, ProductData.TYPE_INT32);
            targetBandTileID = targetProduct.addBand(BAND_NAME_TILE_ID, ProductData.TYPE_INT8);
            targetBandShadowID = targetProduct.addBand(BAND_NAME_SHADOW_ID, ProductData.TYPE_INT32);
            targetBandCloudTest = targetProduct.addBand(BAND_NAME_CLOUD_TEST, ProductData.TYPE_FLOAT64);
        }
        attachFlagCoding(targetBandCloudShadow);
        setupBitmasks(targetProduct);

        sourceBandClusterA = s2BandsProduct.getBand(sourceBandNameClusterA);
        sourceBandClusterB = s2BandsProduct.getBand(sourceBandNameClusterB);

        sourceAltitude = s2BandsProduct.getBand(S2IdepixConstants.ELEVATION_BAND_NAME);

        final GeoPos centerGeoPos = S2IdepixUtils.getCenterGeoPos(targetProduct);
        maxcloudTop = setCloudTopHeigh(centerGeoPos.getLat());

        //create a single potential cloud path for the granule.
        // sunZenithMean, sunAzimuthMean is the value at the central pixel.
        minAltitude = 0;

        sourceBandFlag1 = s2ClassifProduct.getBand(sourceFlagName1);

        spatialResolution = S2IdepixUtils.determineResolution(getSourceProduct());
        switch (mode) {
            case "LandWater":
                analysisMode = Mode.LAND_WATER;
                break;
            case "MultiBand":
                analysisMode = Mode.MULTI_BAND;
                break;
            case "SingleBand":
                analysisMode = Mode.SINGLE_BAND;
                break;
            default:
                throw new OperatorException("Invalid analysis mode. Must be LandWater, MultiBand or SingleBand.");
        }
    }

    private int setCloudTopHeigh(double lat) {
        return (int) Math.ceil(0.5 * Math.pow(90. - Math.abs(lat), 2.) + (90. - Math.abs(lat)) * 25 + 5000);
    }

    private void attachFlagCoding(Band targetBandCloudShadow) {
        FlagCoding cloudCoding = new FlagCoding("cloudCoding");
        cloudCoding.addFlag("water", BitSetter.setFlag(0, F_WATER), F_WATER_DESCR_TEXT);
        cloudCoding.addFlag("land", BitSetter.setFlag(0, F_LAND), F_LAND_DESCR_TEXT);
        cloudCoding.addFlag("cloud", BitSetter.setFlag(0, F_CLOUD), F_CLOUD_DESCR_TEXT);
        cloudCoding.addFlag("pot_haze", BitSetter.setFlag(0, F_HAZE), F_HAZE_DESCR_TEXT);
        cloudCoding.addFlag("cloudShadow", BitSetter.setFlag(0, F_CLOUD_SHADOW), F_CLOUD_SHADOW_DESCR_TEXT);
        cloudCoding.addFlag("mountain_shadow", BitSetter.setFlag(0, F_MOUNTAIN_SHADOW), F_MOUNTAIN_SHADOW_DESCR_TEXT);
        cloudCoding.addFlag("invalid", BitSetter.setFlag(0, F_INVALID), F_INVALID_DESCR_TEXT);
        cloudCoding.addFlag("potential_cloud_shadow", BitSetter.setFlag(0, F_POTENTIAL_CLOUD_SHADOW),
                F_POTENTIAL_CLOUD_SHADOW_DESCR_TEXT);
        cloudCoding.addFlag("shifted_cloud_shadow", BitSetter.setFlag(0, F_SHIFTED_CLOUD_SHADOW),
                F_SHIFTED_CLOUD_SHADOW_DESCR_TEXT);
        cloudCoding.addFlag("cloud_shadow_comb", BitSetter.setFlag(0, F_CLOUD_SHADOW_COMB),
                F_CLOUD_SHADOW_COMB_DESCR_TEXT);
        cloudCoding.addFlag("cloud_buffer", BitSetter.setFlag(0, F_CLOUD_BUFFER),
                F_CLOUD_BUFFER_DESCR_TEXT);
        cloudCoding.addFlag("shifted_cloud_shadow_gaps", BitSetter.setFlag(0, F_SHIFTED_CLOUD_SHADOW_GAPS),
                F_SHIFTED_CLOUD_SHADOW_GAPS_DESCR_TEXT);
        cloudCoding.addFlag("recommended_cloud_shadow", BitSetter.setFlag(0, F_RECOMMENDED_CLOUD_SHADOW),
                F_RECOMMENDED_CLOUD_SHADOW_DESCR_TEXT);
        targetBandCloudShadow.setSampleCoding(cloudCoding);
        targetBandCloudShadow.getProduct().getFlagCodingGroup().add(cloudCoding);
    }

    private static void setupBitmasks(Product targetProduct) {
        int index = 0;
        int w = targetProduct.getSceneRasterWidth();
        int h = targetProduct.getSceneRasterHeight();
        Mask mask;
        mask = Mask.BandMathsType.create("invalid", F_INVALID_DESCR_TEXT, w, h,
                "FlagBand.invalid", Color.DARK_GRAY, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("land", F_LAND_DESCR_TEXT, w, h, "FlagBand.land", Color.GREEN, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("water", F_WATER_DESCR_TEXT, w, h, "FlagBand.water", Color.BLUE, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cloud", F_CLOUD_DESCR_TEXT, w, h, "FlagBand.cloud", Color.YELLOW, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cloud_buffer", F_CLOUD_BUFFER_DESCR_TEXT, w, h,
                "FlagBand.cloud_buffer", Color.ORANGE, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("haze/semitransparent cloud", F_HAZE_DESCR_TEXT, w, h,
                "FlagBand.pot_haze", Color.CYAN, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cloud_shadow", F_CLOUD_SHADOW_DESCR_TEXT, w, h,
                "FlagBand.cloudShadow", Color.RED, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("mountain_shadow", F_MOUNTAIN_SHADOW_DESCR_TEXT, w, h,
                "FlagBand.mountain_shadow", Color.PINK, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("potential_cloud_shadow", F_POTENTIAL_CLOUD_SHADOW_DESCR_TEXT, w, h,
                "FlagBand.potential_cloud_shadow", Color.ORANGE, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("shifted_cloud_shadow", F_SHIFTED_CLOUD_SHADOW_DESCR_TEXT, w, h,
                "FlagBand.shifted_cloud_shadow", Color.MAGENTA, 0.5f);
        targetProduct.getMaskGroup().add(index, mask);
        mask = Mask.BandMathsType.create("cloud_shadow_comb", F_CLOUD_SHADOW_COMB_DESCR_TEXT, w, h,
                "FlagBand.cloud_shadow_comb", Color.BLUE, 0.5f);
        targetProduct.getMaskGroup().add(index, mask);
        mask = Mask.BandMathsType.create("shifted_cloud_shadow_gaps", F_SHIFTED_CLOUD_SHADOW_GAPS_DESCR_TEXT, w, h,
                "FlagBand.shifted_cloud_shadow_gaps", Color.BLUE, 0.5f);
        targetProduct.getMaskGroup().add(index, mask);
        mask = Mask.BandMathsType.create("recommended_cloud_shadow", F_RECOMMENDED_CLOUD_SHADOW_DESCR_TEXT, w, h,
                "FlagBand.recommended_cloud_shadow", Color.BLUE, 0.5f);
        targetProduct.getMaskGroup().add(index, mask);
    }

    private static void fillTile(int[] inputData, Rectangle targetRectangle, Rectangle sourceRectangle, Tile targetTile) {
        for (int targetY = targetRectangle.y; targetY < targetRectangle.y + targetRectangle.height; targetY++) {
            int sourceY = targetY - sourceRectangle.y;
            for (int targetX = targetRectangle.x; targetX < targetRectangle.x + targetRectangle.width; targetX++) {
                int sourceX = targetX - sourceRectangle.x;
                int sourceIndex = sourceY * sourceRectangle.width + sourceX;
                targetTile.setSample(targetX, targetY, inputData[sourceIndex]);
            }
        }
    }

    private static void fillTile(double[] inputData, Rectangle targetRectangle, Rectangle sourceRectangle, Tile targetTile) {
        for (int targetY = targetRectangle.y; targetY < targetRectangle.y + targetRectangle.height; targetY++) {
            int sourceY = targetY - sourceRectangle.y;
            for (int targetX = targetRectangle.x; targetX < targetRectangle.x + targetRectangle.width; targetX++) {
                int sourceX = targetX - sourceRectangle.x;
                int sourceIndex = sourceY * sourceRectangle.width + sourceX;
                targetTile.setSample(targetX, targetY, inputData[sourceIndex]);
            }
        }
    }

    private float[] getSamples(RasterDataNode rasterDataNode, Rectangle rectangle) {
        Tile tile = getSourceTile(rasterDataNode, rectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        return tile.getSamplesFloat();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        final float[] targetAltitude = getSamples(sourceAltitude, targetRectangle);
        final List<Float> altitudes = Arrays.asList(ArrayUtils.toObject(targetAltitude));
        final Point2D[] cloudShadowRelativePath = CloudShadowUtils.getRelativePath(
                minAltitude, sunZenithMean * MathUtils.DTOR, sunAzimuthMean * MathUtils.DTOR, maxcloudTop,
                targetRectangle, targetRectangle, getSourceProduct().getSceneRasterHeight(),
                getSourceProduct().getSceneRasterWidth(), spatialResolution, true, false);
        final Rectangle sourceRectangle = CloudShadowUtils.getSourceRectangle(getSourceProduct(), targetRectangle, cloudShadowRelativePath);

        Tile sourceTileFlag1 = getSourceTile(sourceBandFlag1, sourceRectangle,
                new BorderExtenderConstant(new double[]{Double.NaN}));
        if (skipInvalidTiles && CloudShadowUtils.isCompletelyInvalid(sourceTileFlag1)) {
            return;
        }
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;
        int sourceLength = sourceRectangle.width * sourceRectangle.height;

        Tile targetTileCloudShadow = targetTiles.get(targetBandCloudShadow);

        final int[] flagArray = new int[sourceLength];
        //will be filled in SegmentationCloudClass Arrays.fill(cloudIdArray, ....);
        final int[] cloudIDArray = new int[sourceLength];
        final int[] shadowIDArray = new int[sourceLength];
        final double[] cloudTestArray = new double[sourceLength];

        final float[] altitude = getSamples(sourceAltitude, sourceRectangle);
        final float[][] clusterData = {getSamples(sourceBandClusterA, sourceRectangle),
                getSamples(sourceBandClusterB, sourceRectangle)};

        float[] sourceLatitudes = new float[sourceLength];
        float[] sourceLongitudes = new float[sourceLength];
        if (getSourceProduct().getSceneGeoCoding() instanceof CrsGeoCoding) {
            ((CrsGeoCoding) getSourceProduct().getSceneGeoCoding()).
                    getPixels((int) sourceRectangle.getMinX(),
                            (int) sourceRectangle.getMinY(),
                            (int) sourceRectangle.getWidth(),
                            (int) sourceRectangle.getHeight(),
                            sourceLatitudes,
                            sourceLongitudes);
        } else {
            S2IdepixUtils.
                    getPixels(getSourceProduct().getSceneGeoCoding(),
                            (int) sourceRectangle.getMinX(),
                            (int) sourceRectangle.getMinY(),
                            (int) sourceRectangle.getWidth(),
                            (int) sourceRectangle.getHeight(),
                            sourceLatitudes,
                            sourceLongitudes);
        }

        FlagDetector flagDetector = new FlagDetector(sourceTileFlag1, sourceRectangle);

        PreparationMaskBand.prepareMaskBand(targetProduct.getSceneRasterWidth(), targetProduct.getSceneRasterHeight(),
                sourceRectangle, flagArray, flagDetector);

        if (computeMountainShadow) {
            float maxAltitude =
                    Collections.max(altitudes, new S2IdepixPostCloudShadowOp.MountainShadowMaxFloatComparator());
            if (Float.isNaN(maxAltitude)) {
                maxAltitude = 0;
            }
            MountainShadowFlagger.flagMountainShadowArea(sourceRectangle, sunZenithMean, altitude, flagArray,
                    minAltitude, maxAltitude, cloudShadowRelativePath);
        }

        final FindContinuousAreas cloudIdentifier = new FindContinuousAreas(flagArray);
        Map<Integer, List<Integer>> cloudList =
                cloudIdentifier.computeAreaID(sourceWidth, sourceHeight, cloudIDArray, true);

        if (cloudList.size() > 0) {
            /*
            /   Clustering can be separated: potential cloud shadow over water, and over land.
            /   potentialShadowPositions: Collection of List of integers, which hold the index of potential cloud shadow pixels for each cloudID.
            /   offsetAtPotentialShadowPositions: Collection of List of integers, holding the step along the cloud shadow path in the potential cloud shadow. Useful to determine distances of clusters.
            */
            final IdentifiedPcs identifiedPcs = PotentialCloudShadowAreaIdentifier.identifyPotentialCloudShadowsPLUS(
                    sourceRectangle, targetRectangle, sunZenithMean, sunAzimuthMean, sourceLatitudes, sourceLongitudes,
                    altitude, flagArray, cloudIDArray, cloudShadowRelativePath);
            final Map<Integer, List<Integer>> potentialShadowPositions = identifiedPcs.indexToPositions;
            final Map<Integer, List<Integer>> offsetAtPotentialShadow = identifiedPcs.offsetAtPositions;

            getLogger().fine("potential shadow is ready");
            // shifting by offset, but looking into water, land and all pixel.
            // best offset is determined before (either from water, land, or all pixels).
            if (bestOffset > 0 && bestOffset < cloudShadowRelativePath.length) {
                CloudBulkShifter.setTileShiftedCloudBulk(sourceRectangle, targetRectangle,
                        sunAzimuthMean, flagArray, cloudShadowRelativePath, bestOffset);
                //offset: 0: all, 1: over land, 2: over water.
            }
            //combining information. clustered shadow is analysed for continuous areas.
            // shifting the shadow is done before and a correction is included, if bestOffset > 0
            final CloudShadowFlaggerCombination cloudShadowFlagger = new CloudShadowFlaggerCombination();
            cloudShadowFlagger.flagCloudShadowAreas(clusterData, flagArray, potentialShadowPositions,
                    offsetAtPotentialShadow, cloudList, bestOffset, analysisMode, sourceWidth, sourceHeight,
                    shadowIDArray, cloudShadowRelativePath);

            // shifted cloud mask in cloud gaps.
            // the sourceRectangle has to be large enough, larger than the spatial filter with 1000m radius!
            double kernelRadius = 1000.;
            int blockSize = 2 * (int) Math.ceil(kernelRadius / spatialResolution) + 1;
            if (bestOffset > 0 && blockSize < Math.min(sourceHeight, sourceWidth)) {
                final CloudShadowFlaggerShiftInCloudGaps test = new CloudShadowFlaggerShiftInCloudGaps();
                test.setShiftedCloudInCloudGaps(sourceRectangle, flagArray, cloudList, cloudTestArray, spatialResolution);

            }
            RecommendedCloudShadowFlagger.setRecommendedCloudShadowFlag(bestOffset, flagArray, sourceRectangle);
        }
        fillTile(flagArray, targetRectangle, sourceRectangle, targetTileCloudShadow);
        if (debug) {
            Tile targetTileCloudID = targetTiles.get(targetBandCloudID);
            Tile targetTileTileID = targetTiles.get(targetBandTileID);
            Tile targetTileShadowID = targetTiles.get(targetBandShadowID);
            Tile targetTileCloudTest = targetTiles.get(targetBandCloudTest);
            final int[] tileIDArray = new int[sourceLength];
            Arrays.fill(tileIDArray, S2IdepixUtils.calculateTileId(targetRectangle, targetProduct));
            fillTile(cloudIDArray, targetRectangle, sourceRectangle, targetTileCloudID);
            fillTile(tileIDArray, targetRectangle, sourceRectangle, targetTileTileID);
            fillTile(shadowIDArray, targetRectangle, sourceRectangle, targetTileShadowID);
            fillTile(cloudTestArray, targetRectangle, sourceRectangle, targetTileCloudTest);
        }
    }

    private static class MountainShadowMaxFloatComparator implements Comparator<Float> {

        @Override
        public int compare(Float o1, Float o2) {
            if (Float.isNaN(o1) && Float.isNaN(o2)) {
                return 0;
            } else if (Float.isNaN(o1)) {
                return -1;
            } else if (Float.isNaN(o2)) {
                return 1;
            } else if (o1 < o2) {
                return -1;
            } else if (o1 > o2) {
                return 1;
            }
            return 0;
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixPostCloudShadowOp.class);
        }
    }

}
