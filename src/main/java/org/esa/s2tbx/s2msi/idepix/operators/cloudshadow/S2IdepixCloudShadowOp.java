package org.esa.s2tbx.s2msi.idepix.operators.cloudshadow;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.lang.StringUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ColorPaletteDef;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.ImageInfo;
import org.esa.snap.core.datamodel.IndexCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.BorderExtenderConstant;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Map;

/**
 * todo: add comment
 */
@OperatorMetadata(alias = "CCICloudShadow",
        category = "Optical",
        authors = "Grit Kirches, Michael Paperin, Olaf Danne",
        copyright = "(c) Brockmann Consult GmbH",
        version = "1.0",
        description = "Algorithm detecting cloud shadow...")
public class S2IdepixCloudShadowOp extends Operator {

    @SourceProduct(description = "The classification product.")
    private Product s2ClassifProduct;

    @SourceProduct(alias = "s2CloudBuffer", optional = true)
    private Product s2CloudBufferProduct;      // has only classifFlagBand with buffer added


    @TargetProduct
    private Product targetProduct;


    public final static String BAND_NAME_CLOUD_SHADOW = "FlagBand";

    private final static String BAND_NAME_TEST_A = "ShadowMask_TestA";
    private final static String BAND_NAME_TEST_B = "CloudID_TestB";
    private final static String BAND_NAME_TEST_C = "ShadowID_TestC";
    private final static String BAND_NAME_TEST_D = "LongShadowID_TestC";

    private final static int GRANULE_TEN_M = 122; // one ninetieth of a granule (10980 pixels)
    private final static int GRANULE_TWENTY_M = 610; // one ninth of a granule (5490 pixels)
    private final static int GRANULE_SIXTY_M = 305; // one sixth of a granule (1830 pixels)

    private Band sourceBandClusterA;

    private Band sourceBandFlag1;
    private Band sourceBandFlag2;

    private RasterDataNode sourceSunZenith;
    private RasterDataNode sourceSunAzimuth;
    private RasterDataNode sourceLongitude;
    private RasterDataNode sourceLatitude;
    private RasterDataNode sourceAltitude;


    private Band targetBandCloudShadow;
    private Band targetBandTestA;
    private Band targetBandTestB;
    private Band targetBandTestC;
    private Band targetBandTestD;


    static String productType;
    static String productName;
    static boolean productNameContainIdepix;
    static boolean productCentralComputation;

    static int searchBorderRadius;
    static int mincloudBase;
    static int maxcloudTop;
    static double scaleAltitude = 1.;
    static double spatialResolution;  //[m]
    static int SENSOR_BAND_CLUSTERING;
    static final int clusterCountDefault = 4;
    static int clusterCountDefine;
    static double OUTLIER_THRESHOLD = 0.94;
    static double Threshold_Whiteness_Darkness;
    static int CloudShadowFragmentationThreshold;
    static int GROWING_CLOUD;


    @Override
    public void initialize() throws OperatorException {

        productType = s2ClassifProduct.getProductType();
        productName = s2ClassifProduct.getName();
        String content = "idepix";
        productNameContainIdepix = StringUtils.containsIgnoreCase(productName, content);

        System.out.println("Product_Type:  " + productType);
        System.out.println("Product_Name:  " + productName);
        System.out.println("productNameContainIdepix:  " + productNameContainIdepix);

        targetProduct = new Product(productName, productType,
                                    s2ClassifProduct.getSceneRasterWidth(),
                                    s2ClassifProduct.getSceneRasterHeight());

        ProductUtils.copyGeoCoding(s2ClassifProduct, targetProduct);

        String sourceBandNameClusterA;
        String sourceFlagName1;
        String sourceSunZenithName;
        String sourceSunAzimuthName;
        String sourceLongitudeName;
        String sourceLatitudeName;
        String sourceAltitudeName;
        if ("pyS2Ac".equals(productType)) {
            sourceBandNameClusterA = "B8a_ac";
            sourceSunZenithName = "sun_zenith";
            sourceSunAzimuthName = "sun_azimuth";
            sourceLongitudeName = "lon";
            sourceLatitudeName = "lat";
            sourceAltitudeName = "elevation";
            sourceFlagName1 = "pixel_classif_flags";
            scaleAltitude = 0.001; // altitude in[km] required
            mincloudBase = 100; // [m]
            maxcloudTop = 10000; // [m]
            SENSOR_BAND_CLUSTERING = 2;
            GROWING_CLOUD = 1;
            clusterCountDefine = clusterCountDefault;
            Threshold_Whiteness_Darkness = -1000;
            CloudShadowFragmentationThreshold = 500000;

        } else if ("S2_MSI_Level-1C".equals(productType) || ("CF-1.4".equals(productType) && productNameContainIdepix)) {
            sourceBandNameClusterA = "B8A";
            sourceSunZenithName = "sun_zenith";
            sourceSunAzimuthName = "sun_azimuth";
            sourceLongitudeName = "lon";
            sourceLatitudeName = "lat";
            sourceAltitudeName = "elevation";
            sourceFlagName1 = "pixel_classif_flags";
            scaleAltitude = 0.001; // altitude in[km] required
            mincloudBase = 100; // [m]
            maxcloudTop = 10000; // [m]
            SENSOR_BAND_CLUSTERING = 2;
            GROWING_CLOUD = 1;
            clusterCountDefine = clusterCountDefault;
            Threshold_Whiteness_Darkness = -1000;
            CloudShadowFragmentationThreshold = 500000;
        } else {
            throw new OperatorException("Product type not supported!");
        }
        spatialResolution = determineSourceResolution();
        final int granuleSize = getGranuleSize(spatialResolution);
        final int searchBorderRadius = getSearchBorderRadius(spatialResolution);

        // todo: discuss
        if (s2ClassifProduct.getSceneRasterWidth() > granuleSize || s2ClassifProduct.getSceneRasterHeight() > granuleSize) {
            final int preferredTileWidth = Math.min(s2ClassifProduct.getSceneRasterWidth(), granuleSize);
            final int preferredTileHeight = Math.min(s2ClassifProduct.getSceneRasterHeight(), granuleSize);
            targetProduct.setPreferredTileSize(preferredTileWidth, preferredTileHeight); //1500
            S2IdepixCloudShadowOp.searchBorderRadius = searchBorderRadius; //[pixel] 400 - Sentinel 2
            productCentralComputation = true;
        } else {
            targetProduct.setPreferredTileSize(s2ClassifProduct.getSceneRasterWidth(), s2ClassifProduct.getSceneRasterHeight());
            S2IdepixCloudShadowOp.searchBorderRadius = 0;
            productCentralComputation = false;
        }

        System.out.printf("searchBorderRadius:  %d  \n", S2IdepixCloudShadowOp.searchBorderRadius);

        sourceBandClusterA = s2ClassifProduct.getBand(sourceBandNameClusterA);

        sourceSunZenith = s2ClassifProduct.getBand(sourceSunZenithName);
        sourceSunAzimuth = s2ClassifProduct.getBand(sourceSunAzimuthName);
        sourceLatitude = s2ClassifProduct.getBand(sourceLatitudeName);
        sourceLongitude = s2ClassifProduct.getBand(sourceLongitudeName);
        sourceAltitude = s2ClassifProduct.getBand(sourceAltitudeName);

        sourceBandFlag1 = s2ClassifProduct.getBand(sourceFlagName1);
        if (s2CloudBufferProduct != null) {
            sourceBandFlag2 = s2CloudBufferProduct.getBand(sourceFlagName1);
        }


        targetBandCloudShadow = targetProduct.addBand(BAND_NAME_CLOUD_SHADOW, ProductData.TYPE_INT32);
        attachIndexCoding(targetBandCloudShadow);
        targetBandTestA = targetProduct.addBand(BAND_NAME_TEST_A, ProductData.TYPE_INT32);
        targetBandTestB = targetProduct.addBand(BAND_NAME_TEST_B, ProductData.TYPE_INT32);
        targetBandTestC = targetProduct.addBand(BAND_NAME_TEST_C, ProductData.TYPE_INT32);
        targetBandTestD = targetProduct.addBand(BAND_NAME_TEST_D, ProductData.TYPE_INT32);

    }

    private double determineSourceResolution() throws OperatorException {
        final GeoCoding sceneGeoCoding = getSourceProduct().getSceneGeoCoding();
        if (sceneGeoCoding instanceof CrsGeoCoding) {
            final MathTransform imageToMapTransform = sceneGeoCoding.getImageToMapTransform();
            if (imageToMapTransform instanceof AffineTransform) {
                return ((AffineTransform) imageToMapTransform).getScaleX();
            }
        }
        throw new OperatorException("Invalid product: ");
    }

    private int getGranuleSize(double spatialResolution) {
        //todo determine granule size more gracefully
        if (spatialResolution - 10. < 1e-8) {
            return GRANULE_TEN_M;
        } else if (spatialResolution - 20. < 1e-8) {
            return GRANULE_TWENTY_M;
        }
        return GRANULE_SIXTY_M;
    }

    private int getSearchBorderRadius(double spatialResolution) {
        //todo determine search border radius more gracefully
        if (spatialResolution - 10. < 1e-8) {
            return 900;
        } else if (spatialResolution - 20. < 1e-8) {
            return 400;
        }
        return 200;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        Rectangle sourceRectangle = new Rectangle(targetRectangle);
        sourceRectangle.grow(searchBorderRadius, searchBorderRadius);

        Tile sourceTileSunZenith = getSourceTile(sourceSunZenith, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        Tile sourceTileSunAzimuth = getSourceTile(sourceSunAzimuth, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        Tile sourceTileAltitude = getSourceTile(sourceAltitude, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));

        Tile sourceTileFlag1 = getSourceTile(sourceBandFlag1, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        Tile sourceTileFlag2 = null;
        if (sourceBandFlag2 != null) {
            sourceTileFlag2 = getSourceTile(sourceBandFlag2, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));
        }
        Tile sourceTileClusterA = getSourceTile(sourceBandClusterA, sourceRectangle, new BorderExtenderConstant(new double[]{Double.NaN}));

        Tile targetTileCloudShadow = targetTiles.get(targetBandCloudShadow);
        Tile targetTileTestA = targetTiles.get(targetBandTestA);
        Tile targetTileTestB = targetTiles.get(targetBandTestB);
        Tile targetTileTestC = targetTiles.get(targetBandTestC);
        Tile targetTileTestD = targetTiles.get(targetBandTestD);

        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;
        int sourceLength = sourceRectangle.width * sourceRectangle.height;

        final int[] flagArray = new int[sourceLength];
        final int[] cloudShadowArray = new int[sourceLength];
        //will be filled in SegmentationCloudClass Arrays.fill(cloudIdArray, ....);
        final int[] cloudIDArray = new int[sourceLength];
        final int[] cloudShadowIDArray = new int[sourceLength];
        final int[] cloudLongShadowIDArray = new int[sourceLength];

        final float[] sourceSunZenith = sourceTileSunZenith.getSamplesFloat();
        final float[] sourceSunAzimuth = sourceTileSunAzimuth.getSamplesFloat();
        final float[] sourceAltitude = sourceTileAltitude.getSamplesFloat();
        final float[] sourceClusterA = sourceTileClusterA.getSamplesFloat();
        final float[] sourceClusterB = sourceTileClusterA.getSamplesFloat();

        float[] sourceLatitudes;
        float[] sourceLongitudes;
        if (sourceLatitude != null && sourceLongitude != null) {
            Tile sourceTileLatitude = getSourceTile(sourceLatitude, sourceRectangle,
                                                    new BorderExtenderConstant(new double[]{Double.NaN}));
            Tile sourceTileLongitude = getSourceTile(sourceLongitude, sourceRectangle,
                                                     new BorderExtenderConstant(new double[]{Double.NaN}));
            sourceLatitudes = sourceTileLatitude.getSamplesFloat();
            sourceLongitudes = sourceTileLongitude.getSamplesFloat();
        } else if (getSourceProduct().getSceneGeoCoding() instanceof CrsGeoCoding) {
            sourceLatitudes = new float[(int) (sourceRectangle.getWidth() * sourceRectangle.getHeight())];
            sourceLongitudes = new float[(int) (sourceRectangle.getWidth() * sourceRectangle.getHeight())];
            ((CrsGeoCoding) getSourceProduct().getSceneGeoCoding()).getPixels((int) sourceRectangle.getMinX(),
                                                                              (int) sourceRectangle.getMinY(),
                                                                              (int) sourceRectangle.getWidth(),
                                                                              (int) sourceRectangle.getHeight(),
                                                                              sourceLatitudes,
                                                                              sourceLongitudes);
        } else {
            throw new OperatorException("Could not determine geographic position");
        }

        FlagDetector flagDetector = new FlagDetectorSentinel2(sourceTileFlag1, sourceTileFlag2, sourceRectangle);

        PreparationMaskBand.prepareMaskBand(
                s2ClassifProduct.getSceneRasterWidth(),
                s2ClassifProduct.getSceneRasterHeight(),
                sourceRectangle,
                flagArray,
                flagDetector);

        int counterTable = SegmentationCloud.computeCloudID(sourceWidth, sourceHeight, flagArray, cloudIDArray);

        //todo assessment of the order of processing steps
            /*double solarFluxNir = sourceBandHazeNir.getSolarFlux();
            HazeDetection detectHaze = new HazeDetection();
            detectHaze.calculatePotentialHazeAreas(sourceRectangle, sourceTileHazeBlue,
                    sourceTileHazeRed,
                    sourceTileHazeNir,
                    sourceWidth,
                    sourceHeight,
                    flagArray,
                    solarFluxNir);   */

        //makeFilledBand(flagArray, targetTileCloudShadow.getRectangle(), targetTileCloudShadow, searchBorderRadius);

        if (counterTable != 0) {
            int[][] cloudShadowIdBorderRectangle;


            if (productCentralComputation) {
                cloudShadowIdBorderRectangle =
                        PotentialCloudShadowAreasPathCentralPixel.
                                makedCloudShadowArea(s2ClassifProduct, targetProduct, sourceRectangle,
                                                     targetRectangle, sourceSunZenith, sourceSunAzimuth,
                                                     sourceLatitudes, sourceLongitudes, sourceAltitude,
                                                     flagArray, cloudShadowArray,
                                                     cloudIDArray, cloudShadowIDArray, counterTable);
            } else {
                cloudShadowIdBorderRectangle =
                        PotentialCloudShadowAreas.
                                makedCloudShadowArea(s2ClassifProduct, targetProduct, sourceRectangle,
                                                     targetRectangle, sourceSunZenith, sourceSunAzimuth,
                                                     sourceLatitudes, sourceLongitudes, sourceAltitude,
                                                     flagArray, cloudShadowArray,
                                                     cloudIDArray, cloudShadowIDArray, counterTable);
            }


            AnalyzeCloudShadowIDAreas.identifyCloudShadowArea(s2ClassifProduct, sourceRectangle,
                                                              sourceClusterA, sourceClusterB,
                                                              flagArray, cloudShadowIDArray,
                                                              cloudLongShadowIDArray,
                                                              cloudShadowIdBorderRectangle,
                                                              counterTable);


            GrowingCloudShadow.computeCloudShadowBorder(sourceWidth, sourceHeight, flagArray);

            makeFilledBand(flagArray, targetTileCloudShadow.getRectangle(), targetTileCloudShadow, searchBorderRadius);
            makeFilledBand(cloudShadowArray, targetTileTestA.getRectangle(), targetTileTestA, searchBorderRadius);
            makeFilledBand(cloudIDArray, targetTileTestB.getRectangle(), targetTileTestB, searchBorderRadius);
            makeFilledBand(cloudShadowIDArray, targetTileTestC.getRectangle(), targetTileTestC, searchBorderRadius);
            makeFilledBand(cloudLongShadowIDArray, targetTileTestD.getRectangle(), targetTileTestD, searchBorderRadius);
        }
    }

    private void attachIndexCoding(Band targetBandCloudShadow) {
        IndexCoding cloudCoding = new IndexCoding("cloudCoding");
        cloudCoding.addIndex("nothing", 0, "");
        cloudCoding.addIndex("ocean", 1, "");
        cloudCoding.addIndex("land", 10, "");
        cloudCoding.addIndex("cloudShadow", 100, "");
        cloudCoding.addIndex("ocean_cloud_shadow", 101, "");
        cloudCoding.addIndex("land_cloud_shadow", 110, "");
        cloudCoding.addIndex("cloud", 1000, "");
        cloudCoding.addIndex("ocean_cloud", 1001, "");
        cloudCoding.addIndex("land_cloud", 1010, "");
        cloudCoding.addIndex("water_pot_haze", 5001, "");
        cloudCoding.addIndex("land_pot_haze", 5010, "");
        cloudCoding.addIndex("invalid", 10000, "");
        targetBandCloudShadow.setSampleCoding(cloudCoding);
        targetBandCloudShadow.getProduct().getIndexCodingGroup().add(cloudCoding);

        final int sampleCount = cloudCoding.getSampleCount();
        ColorPaletteDef.Point[] points = new ColorPaletteDef.Point[sampleCount];
        points[0] = new ColorPaletteDef.Point(0, Color.WHITE);
        points[1] = new ColorPaletteDef.Point(1, Color.BLUE);
        points[2] = new ColorPaletteDef.Point(10, Color.GREEN);
        points[3] = new ColorPaletteDef.Point(100, Color.DARK_GRAY);
        points[4] = new ColorPaletteDef.Point(101, Color.DARK_GRAY);
        points[5] = new ColorPaletteDef.Point(110, Color.DARK_GRAY);
        points[6] = new ColorPaletteDef.Point(1000, Color.YELLOW);
        points[7] = new ColorPaletteDef.Point(1001, Color.YELLOW);
        points[8] = new ColorPaletteDef.Point(1010, Color.YELLOW);
        points[9] = new ColorPaletteDef.Point(5001, Color.CYAN);
        points[10] = new ColorPaletteDef.Point(5010, Color.LIGHT_GRAY);
        points[11] = new ColorPaletteDef.Point(10000, Color.RED);
        targetBandCloudShadow.setImageInfo(new ImageInfo(new ColorPaletteDef(points, points.length)));
    }

    private static void makeFilledBand(int[] inputData, Rectangle targetRectangle, Tile targetTileOutputBand, int mkr) {

        int xLocation = targetRectangle.x;
        int yLocation = targetRectangle.y;
        int inputDataWidth = targetRectangle.width + 2 * mkr;
        int inputDataHeight = targetRectangle.height + 2 * mkr;

        //System.out.printf("rectangle_target_input_data:  %d  %d  \n", inputDataWidth, inputDataHeight);

        for (int y = mkr; y < inputDataHeight - mkr; y++) {
            for (int x = mkr; x < inputDataWidth - mkr; x++) {
                targetTileOutputBand.setSample(x - mkr + xLocation, y - mkr + yLocation, inputData[y * (inputDataWidth) + x]);
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixCloudShadowOp.class);
        }
    }

}
