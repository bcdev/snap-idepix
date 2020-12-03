package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.core.CloudShadowFronts;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;
import java.util.Map;

//import org.esa.s3tbx.idepix.core.IdepixConstants;
//import org.esa.s3tbx.idepix.core.operators.CloudBuffer;
//import org.esa.s3tbx.idepix.core.util.IdepixIO;
//import org.esa.s3tbx.idepix.core.util.IdepixUtils;

/**
 * Operator used to consolidate IdePix classification flag for OLCI:
 * - cloud buffer
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Olci.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the OLCI pixel classification over both land and water.")
public class IdepixOlciPostProcessOp extends Operator {

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer",
            description = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with latest 'fronts' algorithm. Requires CTP.")
    private boolean computeCloudShadow;

//    @Parameter(defaultValue = "true",
//            label = " Refine pixel classification near coastlines",
//            description = "Refine pixel classification near coastlines. ")
//    private boolean refineClassificationNearCoastlines;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "olciCloud")
    private Product olciCloudProduct;

    @SourceProduct(alias = "ctp", optional = true)
    private Product ctpProduct;

    private Band origCloudFlagBand;
    private Band targetFlagBand;

    private Band ctpBand;
    private TiePointGrid szaTPG;
    private TiePointGrid saaTPG;
    private TiePointGrid ozaTPG;
    private TiePointGrid oaaTPG;
    private TiePointGrid slpTPG;
    private TiePointGrid[] temperatureProfileTPGs;
    private Band altBand;

    private Band distanceBand;

    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    static int mincloudBase = 300;
    static int maxcloudTop = 12000;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(olciCloudProduct,
                "postProcessedCloud",
                "postProcessedCloud",
                true);

        geoCoding = l1bProduct.getSceneGeoCoding();

        origCloudFlagBand = olciCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        String[] a= origCloudFlagBand.getFlagCoding().getFlagNames();

//        ProductUtils.copyBand("distance", olciCloudProduct, postProcessedCloudProduct, true);
//        distanceBand = postProcessedCloudProduct.getBand("distance");

        distanceBand = postProcessedCloudProduct.addBand("distance", ProductData.TYPE_FLOAT32);
        distanceBand.setNoDataValue(Float.NaN);
        distanceBand.setNoDataValueUsed(true);
        distanceBand.setUnit("m");
        distanceBand.setDescription("Distance to scene border");

        szaTPG = l1bProduct.getTiePointGrid("SZA");
        saaTPG = l1bProduct.getTiePointGrid("SAA");
        ozaTPG = l1bProduct.getTiePointGrid("OZA");
        oaaTPG = l1bProduct.getTiePointGrid("OAA");
        slpTPG = l1bProduct.getTiePointGrid("sea_level_pressure");
        altBand = l1bProduct.getBand("altitude");

        temperatureProfileTPGs = new TiePointGrid[IdepixOlciConstants.referencePressureLevels.length];
        for (int i = 0; i < IdepixOlciConstants.referencePressureLevels.length; i++) {
            temperatureProfileTPGs[i] =
                    l1bProduct.getTiePointGrid("atmospheric_temperature_profile_pressure_level_" + (i + 1));
        }

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()),
                cloudBufferWidth, cloudBufferWidth
        );
        if (computeCloudShadow && ctpProduct != null) {
            ctpBand = ctpProduct.getBand("ctp");
            int extendedWidth;
            int extendedHeight;
            if (l1bProduct.getName().contains("FR____")) {
                extendedWidth = 64;     // todo: check these values
                extendedHeight = 64;
            } else {
                extendedWidth = 16;
                extendedHeight = 16;
            }
            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                    l1bProduct.getSceneRasterHeight()),
                    extendedWidth, extendedHeight
            );

            final GeoPos centerGeoPos =
                    getCenterGeoPos(l1bProduct.getSceneGeoCoding(), l1bProduct.getSceneRasterWidth(),
                            l1bProduct.getSceneRasterHeight());
//            maxcloudTop = setCloudTopHeight(centerGeoPos.getLat());
        }


        targetFlagBand = ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME,
                olciCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    private GeoPos getCenterGeoPos(GeoCoding geoCoding, int width, int height) {
        final PixelPos centerPixelPos = new PixelPos(0.5 * width + 0.5,
                0.5 * height + 0.5);
        return geoCoding.getGeoPos(centerPixelPos, null);
    }

    private int setCloudTopHeight(double lat) {
        return (int) Math.ceil(0.5 * Math.pow(90. - Math.abs(lat), 2.) + (90. - Math.abs(lat)) * 25 + 5000);
    }

//    @Override
//    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
//        Rectangle targetRectangle = targetTile.getRectangle();
//        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);
//
//        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
//        //Tile distanceTile = getSourceTile(distanceBand, targetRectangle);
//
//        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
//            checkForCancellation();
//            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {
//
//                if (targetRectangle.contains(x, y)) {
//                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
//                    combineFlags(x, y, sourceFlagTile, targetTile);
//                    if (isCloud) {
//                        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);   // necessary??
//                    }
//
//                    // refine classification near coastline is not necessayr, if coast line pixels are processed as land!
////                    boolean isCoastline = sourceFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_COASTLINE);
////
////                    if (refineClassificationNearCoastlines) {
////                        if (isCloud && isCoastline) { //MERIS has a test isNearCoastline
////                            refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, targetTile, srcRectangle);
////                        }
////                    }
////                    boolean isCloudAfterRefinement = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
////                    if (isCloudAfterRefinement) {
////                        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
////                    }
//                }
//            }
//        }
//
//        if (computeCloudBuffer) {
//            CloudBuffer.setCloudBuffer(targetTile, srcRectangle, sourceFlagTile, cloudBufferWidth);
//            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
//                checkForCancellation();
//                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
//                    IdepixUtils.consolidateCloudAndBuffer(targetTile, x, y);
//                }
//            }
//        }
//
//        if (computeCloudShadow && ctpProduct != null) {
//            Tile szaTile = getSourceTile(szaTPG, srcRectangle);
//            Tile saaTile = getSourceTile(saaTPG, srcRectangle);
//            Tile ozaTile = getSourceTile(ozaTPG, srcRectangle);
//            Tile oaaTile = getSourceTile(oaaTPG, srcRectangle);
//            Tile ctpTile = getSourceTile(ctpBand, srcRectangle);
//            Tile slpTile = getSourceTile(slpTPG, srcRectangle);
//            Tile altTile = getSourceTile(altBand, targetRectangle);
//            Tile distanceTile = getSourceTile(distanceBand, targetRectangle);
//
//            Tile[] temperatureProfileTPGTiles = new Tile[temperatureProfileTPGs.length];
//            for (int i = 0; i < temperatureProfileTPGTiles.length; i++) {
//                temperatureProfileTPGTiles[i] = getSourceTile(temperatureProfileTPGs[i], srcRectangle);
//            }
//
//            // CloudShadowFronts was modified for OLCI:
//            // - more advanced CTH computation
//            // - use of 'apparent sun azimuth angle
//            IdepixOlciCloudShadowFronts cloudShadowFronts = new IdepixOlciCloudShadowFronts(geoCoding,
//                    szaTile, saaTile,
//                    ozaTile, oaaTile,
//                    ctpTile, slpTile,
//                    temperatureProfileTPGTiles,
//                    altTile, distanceTile, maxcloudTop);
//            cloudShadowFronts.computeCloudShadow(sourceFlagTile, targetTile);
//        }
//    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);

        Tile targetFlagTile = targetTiles.get(targetFlagBand);
        Tile distanceTile = null;
        if (distanceBand != null) {
            distanceTile = targetTiles.get(distanceBand);
        }


        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    combineFlags(x, y, sourceFlagTile, targetFlagTile);
                    if (isCloud) {
                        targetFlagTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);   // necessary??
                    }
                }
            }
        }

        if (computeCloudBuffer) {
            CloudBuffer.setCloudBuffer(targetFlagTile, srcRectangle, sourceFlagTile, cloudBufferWidth);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    IdepixUtils.consolidateCloudAndBuffer(targetFlagTile, x, y);
                }
            }
        }

        if (computeCloudShadow && ctpProduct != null) {
            Tile szaTile = getSourceTile(szaTPG, srcRectangle);
            Tile saaTile = getSourceTile(saaTPG, srcRectangle);
            Tile ozaTile = getSourceTile(ozaTPG, srcRectangle);
            Tile oaaTile = getSourceTile(oaaTPG, srcRectangle);
            Tile ctpTile = getSourceTile(ctpBand, srcRectangle);
            Tile slpTile = getSourceTile(slpTPG, srcRectangle);
            Tile altTile = getSourceTile(altBand, targetRectangle);

            Tile[] temperatureProfileTPGTiles = new Tile[temperatureProfileTPGs.length];
            for (int i = 0; i < temperatureProfileTPGTiles.length; i++) {
                temperatureProfileTPGTiles[i] = getSourceTile(temperatureProfileTPGs[i], srcRectangle);
            }

            // CloudShadowFronts was modified for OLCI:
            // - more advanced CTH computation
            // - use of 'apparent sun azimuth angle
            IdepixOlciCloudShadowFronts cloudShadowFronts = new IdepixOlciCloudShadowFronts(geoCoding,
                    szaTile, saaTile,
                    ozaTile, oaaTile,
                    ctpTile, slpTile,
                    temperatureProfileTPGTiles,
                    altTile, distanceTile, maxcloudTop);
            cloudShadowFronts.computeCloudShadow(sourceFlagTile, targetFlagTile);
        }

    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    public static  boolean isPixelSurrounded_2Flags(int x, int y, Tile sourceFlagTile, int pixelFlag, int pixelFlagNOT,
                                             int buffer) {
        // check if pixel is surrounded by other pixels flagged as 'pixelFlag' and not 'pixelFlagNOT'
        int surroundingPixelCount = 0;
        Rectangle rectangle = sourceFlagTile.getRectangle();
        for (int i = x - buffer; i <= x + buffer; i++) {
            for (int j = y - buffer; j <= y + buffer; j++) {
                if (rectangle.contains(i, j) && sourceFlagTile.getSampleBit(i, j, pixelFlag) &&
                    !sourceFlagTile.getSampleBit(i, j, pixelFlagNOT)) {
                    surroundingPixelCount++;
                }
            }
        }
//        return (surroundingPixelCount * 1.0 / 9 >= 0.7);  // at least 6 pixel in a 3x3 box
//        return (surroundingPixelCount * 1.0 / ((2*buffer+1)*(2*buffer+1)) >= 0.7);
        return (surroundingPixelCount>0);
    }

    private boolean isCoastalZone(Tile sourceFlagTile, int x, int y, Integer bufferValue){
        int buffer = bufferValue != null ? bufferValue : 3; //default value 3 for dilation 7x7
        // check if in window is at least one, but not all of them.
        int pixelCount = 0;
        int sizeRectangle = 0;
        Rectangle rectangle = sourceFlagTile.getRectangle();
        for (int i = x - buffer; i <= x + buffer; i++) {
            for (int j = y - buffer; j <= y + buffer; j++) {
                if (rectangle.contains(i, j)){
                    sizeRectangle++;
                    if (sourceFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_LAND) &&
                            !sourceFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_FRESH_INLAND_WATER)) {
                        pixelCount++;
                    }
                }

            }
        }
        return (pixelCount>0 && pixelCount < sizeRectangle);
    }

    private float getDistanceToShoreORCloud(int x, int y, Tile sourceFlagTile, Integer bufferValue){
        int buffer = bufferValue != null ? bufferValue : 7; //
        // find minimum distance in window from LAND or CLOUD to central pixel, which is processable water.
        double maxDist = (double) buffer * Math.sqrt(2.);
        double pixelDist = maxDist;

        Rectangle rectangle = sourceFlagTile.getRectangle();
        boolean noLandCloudFound = true;
        for (int bf = 1; bf <= buffer; bf++) {
            for (int i = x - bf; i <= x + bf; i++) {
                int ix = i;
                int iy = y + bf;
                if (rectangle.contains(ix, iy)) { //oben
                    if (CloudOrLandTest(ix, iy, sourceFlagTile)){
                        double thisDist = Math.sqrt(Math.pow(x-ix,2) + Math.pow(y-iy, 2));
                        if (thisDist < pixelDist){
                            pixelDist = thisDist;
                        }
                    }
                }
                ix = i;
                iy = y - bf;
                if (rectangle.contains(ix, iy)) { //unten
                    if (CloudOrLandTest(ix, iy, sourceFlagTile)){
                        double thisDist = Math.sqrt(Math.pow(x-ix,2) + Math.pow(y-iy, 2));
                        if (thisDist < pixelDist){
                            pixelDist = thisDist;
                        }
                    }
                }
                ix = x - bf;
                iy = i;
                if (rectangle.contains(ix, iy)) { //links
                    if (CloudOrLandTest(ix, iy, sourceFlagTile)){
                        double thisDist = Math.sqrt(Math.pow(x-ix,2) + Math.pow(y-iy, 2));
                        if (thisDist < pixelDist){
                            pixelDist = thisDist;
                        }
                    }
                }
                ix = x + bf;
                iy = i;
                if (rectangle.contains(ix, iy)) { //rechts
                    if (CloudOrLandTest(ix, iy, sourceFlagTile)){
                        double thisDist = Math.sqrt(Math.pow(x-ix,2) + Math.pow(y-iy, 2));
                        if (thisDist < pixelDist){
                            pixelDist = thisDist;
                        }
                    }
                }
            }
            if (pixelDist < maxDist){
                break;
            }
        }
        return (float)pixelDist;
    }

    private boolean CloudOrLandTest(int ix, int iy, Tile sourceFlagTile){
        return sourceFlagTile.getSampleBit(ix, iy, IdepixConstants.IDEPIX_LAND) || sourceFlagTile.getSampleBit(ix, iy, IdepixConstants.IDEPIX_CLOUD);
    }

    private void refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile, Rectangle srcRectangle) {
        final int windowWidth = 2;
        final int LEFT_BORDER = Math.max(x - windowWidth, srcRectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, srcRectangle.x + srcRectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, srcRectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, srcRectangle.y + srcRectangle.height - 1);
        boolean removeCloudFlag = true;
        if (isPixelSurrounded_2Flags(x, y, sourceFlagTile, IdepixConstants.IDEPIX_CLOUD, IdepixOlciConstants.L1_F_COASTLINE, windowWidth)) {
            removeCloudFlag = false;
        }
//        else {
//            Rectangle targetTileRectangle = targetTile.getRectangle();
//            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
//                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
//                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD);
//                    boolean is_coastline = sourceFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_COASTLINE);
//                    if (is_cloud && targetTileRectangle.contains(i, j) && !is_coastline) {
//                        removeCloudFlag = false;
//                        break;
//                    }
//                }
//            }
//        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
        }
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciPostProcessOp.class);
        }
    }

}
