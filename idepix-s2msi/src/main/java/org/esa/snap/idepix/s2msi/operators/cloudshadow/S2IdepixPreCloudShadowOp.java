package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.datamodel.StxFactory;
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
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.BorderExtenderConstant;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tonio Fincke, Dagmar Müller
 */
@OperatorMetadata(alias = "Idepix.S2.CloudShadow.Preprocess",
        category = "Optical",
        authors = "Grit Kirches, Michael Paperin, Olaf Danne, Tonio Fincke, Dagmar Müller",
        copyright = "(c) Brockmann Consult GmbH",
        version = "2.0",
        internal = true,
        description = "Preprocessing for algorithm detecting cloud shadow...")

public class S2IdepixPreCloudShadowOp extends Operator {

    @SourceProduct(description = "The classification product from which to take the classification band.")
    private Product s2ClassifProduct;

    @SourceProduct(description = "The product from which to take other bands than the classification band.")
    private Product s2BandsProduct;

    @TargetProduct
    private Product targetProduct;

    // hidden parameter, may be set to true for mosaics with larger invalid areas
    private boolean skipInvalidTiles;

    @Parameter(notNull=true)
    private float sunZenithMean;

    @Parameter(notNull=true)
    private float sunAzimuthMean;

    private final static double MAX_CLOUD_HEIGHT = 8000.;

    private Band sourceBandClusterA;
    private Band sourceBandClusterB;

    private Band sourceBandFlag1;

    static int mincloudBase = 100;
    static int maxcloudTop = 10000;
    //for calculating a single cloud path
    private float minAltitude = 0;

    //map for the different tiles: meanReflectance per offset.
    private final Map<Integer, double[][]> meanReflPerTile = new HashMap<>();
    private final Map<Integer, Integer> NCloudOverLand = new HashMap<>();
    private final Map<Integer, Integer> NCloudOverWater = new HashMap<>();
    private final Map<Integer, Integer> NValidPixelTile = new HashMap<>();

    static double spatialResolution;  //[m]
    static int clusterCountDefine = 4;
    static double OUTLIER_THRESHOLD = 0.94;
    static int searchBorderRadius;
    private static final String sourceBandNameClusterA = "B8A";
    private static final String sourceBandNameClusterB = "B3";
    private static final String sourceFlagName1 = "pixel_classif_flags";
    private final static String BAND_NAME_CLOUD_SHADOW = "FlagBand";

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
    // todo: why is the cloud buffer description not used?
    private static final String F_CLOUD_BUFFER_DESCR_TEXT = "Cloud buffer";
    private static final String F_SHIFTED_CLOUD_SHADOW_GAPS_DESCR_TEXT = "shifted cloud mask in cloud gap";
    private static final String F_RECOMMENDED_CLOUD_SHADOW_DESCR_TEXT = "combination of shifted cloud mask in cloud gap + cloud-shadow_comb, or if bestOffset=0: clustered";

    public static final int F_WATER = 0;
    public static final int F_LAND = 1;
    public static final int F_CLOUD = 2;
    public static final int F_HAZE = 3;
    public static final int F_CLOUD_SHADOW = 4;
    public static final int F_MOUNTAIN_SHADOW = 5;
    public static final int F_INVALID = 6;
    public static final int F_CLOUD_BUFFER = 7;
    public static final int F_POTENTIAL_CLOUD_SHADOW = 8;
    public static final int F_SHIFTED_CLOUD_SHADOW = 9;
    public static final int F_CLOUD_SHADOW_COMB = 10;
    public static final int F_SHIFTED_CLOUD_SHADOW_GAPS = 11;
    public static final int F_RECOMMENDED_CLOUD_SHADOW = 12;

    @Override
    public void initialize() throws OperatorException {
        skipInvalidTiles = Boolean.getBoolean("snap.idepix.s2msi.skipInvalidTiles");
        targetProduct = new Product(s2BandsProduct.getName(), s2BandsProduct.getProductType(),
                s2BandsProduct.getSceneRasterWidth(), s2BandsProduct.getSceneRasterHeight());

        ProductUtils.copyGeoCoding(s2BandsProduct, targetProduct);

        sourceBandClusterA = s2BandsProduct.getBand(sourceBandNameClusterA);
        sourceBandClusterB = s2BandsProduct.getBand(sourceBandNameClusterB);

        final GeoPos centerGeoPos =
                S2IdepixUtils.getCenterGeoPos(s2BandsProduct);
        maxcloudTop = setCloudTopHeight(centerGeoPos.getLat());

        //create a single potential cloud path for the granule.
        minAltitude = 0;
        sourceBandFlag1 = s2ClassifProduct.getBand(sourceFlagName1);

        Band targetBandCloudShadow = targetProduct.addBand(BAND_NAME_CLOUD_SHADOW, ProductData.TYPE_INT32);
        attachFlagCoding(targetBandCloudShadow);
        setupBitmasks(targetProduct);

        spatialResolution = determineSourceResolution();

        int cacheSize = Integer.parseInt(System.getProperty("snap.idepix.s2msi.tilecache2", "600"));
        targetProduct = S2IdepixUtils.computeTileCacheProduct(targetProduct, cacheSize);
    }

    private double determineSourceResolution() throws OperatorException {
        final GeoCoding sceneGeoCoding = getSourceProduct().getSceneGeoCoding();
        if (sceneGeoCoding instanceof CrsGeoCoding) {
            final MathTransform imageToMapTransform = sceneGeoCoding.getImageToMapTransform();
            if (imageToMapTransform instanceof AffineTransform) {
                return ((AffineTransform) imageToMapTransform).getScaleX();
            }
        } else {
            return S2IdepixUtils.determineResolution(getSourceProduct());
        }
        throw new OperatorException("Invalid product");
    }

    double determineSearchBorderRadius(double spatialResolution, double maxSunZenith) {
        final double maxCloudDistance = MAX_CLOUD_HEIGHT / Math.tan(Math.toRadians(90. - maxSunZenith));
        return maxCloudDistance / spatialResolution;
    }

    private float[] getSamples(RasterDataNode rasterDataNode, Rectangle rectangle) {
        Tile tile = getSourceTile(rasterDataNode, rectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        return tile.getSamplesFloat();
    }

    private float getRasterNodeValueAtCenter(RasterDataNode var, int width, int height) {
        float centralValue = var.getSampleFloat((int) (0.5 * width), (int) (0.5 * height));
        if (Float.isNaN(centralValue)) {
            centralValue = (float) new StxFactory().create(var, ProgressMonitor.NULL).getMedian();
        }
        return centralValue;
    }

    private int setCloudTopHeight(double lat) {
        return (int) Math.ceil(0.5 * Math.pow(90. - Math.abs(lat), 2.) + (90. - Math.abs(lat)) * 25 + 5000);
    }

    /*
    package local for testing
     */
    @SuppressWarnings("WeakerAccess")
    Rectangle getSourceRectangle(Rectangle targetRectangle, Point2D[] relativePath) {
        final int productWidth = getSourceProduct().getSceneRasterWidth();
        final int productHeight = getSourceProduct().getSceneRasterHeight();
        final int relativeX = (int) relativePath[relativePath.length - 1].getX();
        final int relativeY = (int) relativePath[relativePath.length - 1].getY();

        // borders are now extended in both directions left-right, top-down.
        // so it needs a reduction in x0,y0 and addition in x1,y1
        int x0 = Math.max(0, targetRectangle.x + Math.min(0, -1 * Math.abs(relativeX)));
        int y0 = Math.max(0, targetRectangle.y + Math.min(0, -1 * Math.abs(relativeY)));
        int x1 = Math.min(productWidth, targetRectangle.x + targetRectangle.width + Math.max(0, Math.abs(relativeX)));
        int y1 = Math.min(productHeight, targetRectangle.y + targetRectangle.height + Math.max(0, Math.abs(relativeY)));
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        //here: cloud path is calculated for center pixel sunZenith and sunAzimuth. sunAzimuth is corrected with view geometry.
        final Point2D[] cloudShadowRelativePath = CloudShadowUtils.getRelativePath(
                minAltitude, sunZenithMean * MathUtils.DTOR, sunAzimuthMean * MathUtils.DTOR, maxcloudTop,
                targetRectangle, targetRectangle, getSourceProduct().getSceneRasterHeight(),
                getSourceProduct().getSceneRasterWidth(), spatialResolution, true, false);

        final Rectangle sourceRectangle = getSourceRectangle(targetRectangle, cloudShadowRelativePath);
        Tile sourceTileFlag1 = getSourceTile(sourceBandFlag1, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        if (skipInvalidTiles && S2IdepixPostCloudShadowOp.isCompletelyInvalid(sourceTileFlag1)) {
            return;
        }

        int sourceLength = sourceRectangle.width * sourceRectangle.height;

        final int[] flagArray = new int[sourceLength];

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

        PreparationMaskBand.prepareMaskBand(s2ClassifProduct.getSceneRasterWidth(),
                s2ClassifProduct.getSceneRasterHeight(), sourceRectangle, flagArray, flagDetector);

        final CloudBulkShifter cloudBulkShifter = new CloudBulkShifter();
        cloudBulkShifter.shiftCloudBulkAlongCloudPathType(sourceRectangle, targetRectangle, sunAzimuthMean,
                clusterData, flagArray, cloudShadowRelativePath);
        int tileId = S2IdepixUtils.calculateTileId(targetRectangle, targetProduct);
        meanReflPerTile.put(tileId, cloudBulkShifter.getMeanReflectanceAlongPath());
        NCloudOverLand.put(tileId, cloudBulkShifter.getNCloudOverLand());
        NCloudOverWater.put(tileId, cloudBulkShifter.getNCloudOverWater());
        NValidPixelTile.put(tileId, cloudBulkShifter.getNValidPixel());

    }

    Map<Integer, double[][]> getMeanReflPerTile() {
        return meanReflPerTile;
    }

    Map<Integer, Integer> getNCloudOverLandPerTile() {
        return NCloudOverLand;
    }

    Map<Integer, Integer> getNCloudOverWaterPerTile() {
        return NCloudOverWater;
    }

    Map<Integer, Integer> getNValidPixelTile() {
        return NValidPixelTile;
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
        mask = Mask.BandMathsType.create("land", F_LAND_DESCR_TEXT, w, h,
                "FlagBand.land", Color.GREEN, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("water", F_WATER_DESCR_TEXT, w, h,
                "FlagBand.water", Color.BLUE, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("cloud", F_CLOUD_DESCR_TEXT, w, h,
                "FlagBand.cloud", Color.YELLOW, 0.5f);
        targetProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create("haze/semitransparent cloud", F_HAZE_DESCR_TEXT, w, h,
                " FlagBand.pot_haze", Color.CYAN, 0.5f);
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

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixPreCloudShadowOp.class);
        }
    }

}
