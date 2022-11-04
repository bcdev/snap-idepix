package org.esa.snap.idepix.s2msi.util;


import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.internal.TileCacheOp;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;

import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Random;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.*;

/**
 * @author Olaf Danne
 */
public class S2IdepixUtils {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("idepix");

    private S2IdepixUtils() {
    }

    public static Product cloneProduct(Product sourceProduct, boolean copySourceBands) {
        return cloneProduct(sourceProduct, sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight(), copySourceBands);
    }

    public static boolean isIdepixSpectralBand(Band b) {
        return b.getName().startsWith("B");
    }

    public static boolean validateInputProduct(Product inputProduct, AlgorithmSelector algorithm) {
        return isInputValid(inputProduct) && isInputConsistent(inputProduct, algorithm);
    }

    public static boolean isInputValid(Product inputProduct) {
        if (!isValidSentinel2(inputProduct)) {
            logErrorMessage("Input sensor must be Sentinel-2 MSI!");
        }
        return true;
    }

    public static boolean isValidSentinel2(Product sourceProduct) {
        for (String bandName : S2_MSI_REFLECTANCE_BAND_NAMES) {
            if (!sourceProduct.containsBand(bandName)) {
                return false;
            }
        }
        return true;
    }

    public static Product computeTileCacheProduct(Product inputProduct, int cacheSize) {
        if (Boolean.getBoolean("snap.gpf.disableTileCache")) {
            TileCacheOp tileCacheOp = new TileCacheOp();
            tileCacheOp.setSourceProduct("source", inputProduct);
            tileCacheOp.setParameterDefaultValues();
            tileCacheOp.setParameter("cacheSize", cacheSize);
            inputProduct = tileCacheOp.getTargetProduct();
        }
        return inputProduct;
    }


    public static double determineResolution(Product product) {
        int width = product.getSceneRasterWidth();
        int height = product.getSceneRasterHeight();
        GeoPos geoPos1 = product.getSceneGeoCoding().getGeoPos(new PixelPos(width / 2, 0), null);
        GeoPos geoPos2 = product.getSceneGeoCoding().getGeoPos(new PixelPos(width / 2, height - 1), null);
        double deltaLatInMeters = (geoPos1.lat - geoPos2.lat) / (height-1) / 180.0 * 6367500 * Math.PI;
        double deltaLonInMeters = (geoPos1.lon - geoPos2.lon) / (height-1) / 180.0 * 6367500 * Math.PI * Math.cos((geoPos1.lat + geoPos2.lat) / 2 / 180 * Math.PI);
        double resolution = (int) Math.round(Math.sqrt(deltaLatInMeters * deltaLatInMeters + deltaLonInMeters * deltaLonInMeters));
        SystemUtils.LOG.info("Determined resolution as " + resolution + " m");
        return resolution;
    }

    public static void getPixels(GeoCoding sceneGeoCoding,
                                 final int x1, final int y1, final int w, final int h,
                                 final float[] latPixels, final float[] lonPixels) {
        PixelPos pixelPos = new PixelPos();
        GeoPos geoPos = new GeoPos();
        int i = 0;
        for (int y = y1; y < y1 + h; ++y) {
            for (int x = x1; x < x1 + w; ++x) {
                pixelPos.setLocation(x + 0.5f, y + 0.5f);
                sceneGeoCoding.getGeoPos(pixelPos, geoPos);
                lonPixels[i] = (float) geoPos.lon;
                latPixels[i++] = (float) geoPos.lat;
            }
        }
    }

    public static void logErrorMessage(String msg) {
        if (System.getProperty("gpfMode") != null && "GUI".equals(System.getProperty("gpfMode"))) {
            JOptionPane.showOptionDialog(null, msg, "IDEPIX - Error Message", JOptionPane.DEFAULT_OPTION,
                                         JOptionPane.ERROR_MESSAGE, null, null, null);
        } else {
            info(msg);
        }
    }

    public static void info(final String msg) {
        logger.info(msg);
        System.out.println(msg);
    }


    public static float spectralSlope(float ch1, float ch2, float wl1, float wl2) {
        return (ch2 - ch1) / (wl2 - wl1);
    }


    public static boolean areAllReflectancesValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (Float.isNaN(aReflectance) || aReflectance <= 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static void setNewBandProperties(Band band, String description, String unit, double noDataValue,
                                            boolean useNoDataValue) {
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(noDataValue);
        band.setNoDataValueUsed(useNoDataValue);
    }

    public static FlagCoding createIdepixFlagCoding(String flagIdentifier) {
        FlagCoding flagCoding = new FlagCoding(flagIdentifier);
        flagCoding.addFlag(IDEPIX_INVALID_NAME, BitSetter.setFlag(0, IDEPIX_INVALID), IDEPIX_INVALID_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLOUD_NAME, BitSetter.setFlag(0, IDEPIX_CLOUD), IDEPIX_CLOUD_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLOUD_AMBIGUOUS_NAME, BitSetter.setFlag(0, IDEPIX_CLOUD_AMBIGUOUS), IDEPIX_CLOUD_AMBIGUOUS_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLOUD_SURE_NAME, BitSetter.setFlag(0, IDEPIX_CLOUD_SURE), IDEPIX_CLOUD_SURE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLOUD_BUFFER_NAME, BitSetter.setFlag(0, IDEPIX_CLOUD_BUFFER), IDEPIX_CLOUD_BUFFER_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLOUD_SHADOW_NAME, BitSetter.setFlag(0, IDEPIX_CLOUD_SHADOW), IDEPIX_CLOUD_SHADOW_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_SNOW_ICE_NAME, BitSetter.setFlag(0, IDEPIX_SNOW_ICE), IDEPIX_SNOW_ICE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_BRIGHT_NAME, BitSetter.setFlag(0, IDEPIX_BRIGHT), IDEPIX_BRIGHT_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_WHITE_NAME, BitSetter.setFlag(0, IDEPIX_WHITE), IDEPIX_WHITE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_COASTLINE__NAME, BitSetter.setFlag(0, IDEPIX_COASTLINE), IDEPIX_COASTLINE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_LAND_NAME, BitSetter.setFlag(0, IDEPIX_LAND), IDEPIX_LAND_DESCR_TEXT);

        flagCoding.addFlag(IDEPIX_CIRRUS_SURE_NAME, BitSetter.setFlag(0, IDEPIX_CIRRUS_SURE), IDEPIX_CIRRUS_SURE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CIRRUS_AMBIGUOUS_NAME, BitSetter.setFlag(0, IDEPIX_CIRRUS_AMBIGUOUS), IDEPIX_CIRRUS_AMBIGUOUS_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLEAR_LAND_NAME, BitSetter.setFlag(0, IDEPIX_CLEAR_LAND), IDEPIX_CLEAR_LAND_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLEAR_WATER_NAME, BitSetter.setFlag(0, IDEPIX_CLEAR_WATER), IDEPIX_CLEAR_WATER_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_WATER_NAME, BitSetter.setFlag(0, IDEPIX_WATER), IDEPIX_WATER_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_BRIGHTWHITE_NAME, BitSetter.setFlag(0, IDEPIX_BRIGHTWHITE), IDEPIX_BRIGHTWHITE_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_VEG_RISK_NAME, BitSetter.setFlag(0, IDEPIX_VEG_RISK), IDEPIX_VEG_RISK_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_MOUNTAIN_SHADOW_NAME, BitSetter.setFlag(0, IDEPIX_MOUNTAIN_SHADOW), IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_POTENTIAL_SHADOW_NAME, BitSetter.setFlag(0, IDEPIX_POTENTIAL_SHADOW), IDEPIX_POTENTIAL_SHADOW_DESCR_TEXT);
        flagCoding.addFlag(IDEPIX_CLUSTERED_CLOUD_SHADOW_NAME, BitSetter.setFlag(0, IDEPIX_CLUSTERED_CLOUD_SHADOW), IDEPIX_CLUSTERED_CLOUD_SHADOW_DESCR_TEXT);
        return flagCoding;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static int setupIdepixCloudscreeningBitmasks(Product gaCloudProduct) {

        int index = 0;
        int w = gaCloudProduct.getSceneRasterWidth();
        int h = gaCloudProduct.getSceneRasterHeight();
        Mask mask;
        Random r = new Random(1234567);

        mask = Mask.BandMathsType.create(IDEPIX_INVALID_NAME,
                                         IDEPIX_INVALID_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_INVALID_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLOUD_NAME,
                                         IDEPIX_CLOUD_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLOUD_NAME),
                                         new Color(178, 178, 0), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLOUD_AMBIGUOUS_NAME,
                                         IDEPIX_CLOUD_AMBIGUOUS_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLOUD_AMBIGUOUS_NAME),
                                         new Color(255, 219, 156), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLOUD_SURE_NAME,
                                         IDEPIX_CLOUD_SURE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLOUD_SURE_NAME),
                                         new Color(224, 224, 30), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLOUD_BUFFER_NAME,
                                         IDEPIX_CLOUD_BUFFER_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLOUD_BUFFER_NAME),
                                         Color.red, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLOUD_SHADOW_NAME,
                                         IDEPIX_CLOUD_SHADOW_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLOUD_SHADOW_NAME),
                                         Color.cyan, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_SNOW_ICE_NAME,
                                         IDEPIX_SNOW_ICE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_SNOW_ICE_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_BRIGHT_NAME,
                                         IDEPIX_BRIGHT_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_BRIGHT_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_WHITE_NAME,
                                         IDEPIX_WHITE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_WHITE_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_COASTLINE__NAME,
                                         IDEPIX_COASTLINE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_COASTLINE__NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_LAND_NAME,
                                         IDEPIX_CLEAR_LAND_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_LAND_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);

        mask = Mask.BandMathsType.create(IDEPIX_CIRRUS_SURE_NAME,
                                         IDEPIX_CIRRUS_SURE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CIRRUS_SURE_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CIRRUS_AMBIGUOUS_NAME,
                                         IDEPIX_CIRRUS_AMBIGUOUS_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CIRRUS_AMBIGUOUS_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLEAR_LAND_NAME,
                                         IDEPIX_CLEAR_LAND_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLEAR_LAND_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLEAR_WATER_NAME,
                                         IDEPIX_CLEAR_WATER_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLEAR_WATER_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_WATER_NAME,
                                         IDEPIX_WATER_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_WATER_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_BRIGHTWHITE_NAME,
                                         IDEPIX_BRIGHTWHITE_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_BRIGHTWHITE_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_VEG_RISK_NAME,
                                         IDEPIX_VEG_RISK_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_VEG_RISK_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_MOUNTAIN_SHADOW_NAME,
                                         IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_MOUNTAIN_SHADOW_NAME),
                                         getRandomColour(r), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_POTENTIAL_SHADOW_NAME,
                                         IDEPIX_POTENTIAL_SHADOW_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_POTENTIAL_SHADOW_NAME),
                                         new Color(255, 200, 0), 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);
        mask = Mask.BandMathsType.create(IDEPIX_CLUSTERED_CLOUD_SHADOW_NAME,
                                         IDEPIX_CLUSTERED_CLOUD_SHADOW_DESCR_TEXT, w, h,
                                         String.format("%s.%s", IDEPIX_CLASSIF_FLAGS, IDEPIX_CLUSTERED_CLOUD_SHADOW_NAME),
                                         Color.red, 0.5f);
        gaCloudProduct.getMaskGroup().add(index++, mask);

        return index;
    }


    public static double convertGeophysicalToMathematicalAngle(double inAngle) {
        if (0.0 <= inAngle && inAngle < 90.0) {
            return (90.0 - inAngle);
        } else if (90.0 <= inAngle && inAngle < 360.0) {
            return (90.0 - inAngle + 360.0);
        } else {
            // invalid
            return Double.NaN;
        }
    }

    public static boolean isNoReflectanceValid(float[] reflectance) {
        for (float aReflectance : reflectance) {
            if (!Float.isNaN(aReflectance) && aReflectance > 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static void consolidateCloudAndBuffer(Tile targetTile, int x, int y) {
        if (targetTile.getSampleBit(x, y, IDEPIX_CLOUD_SURE) ||
                targetTile.getSampleBit(x, y, IDEPIX_CLOUD_AMBIGUOUS)) {
            targetTile.setSample(x, y, IDEPIX_CLOUD_BUFFER, false);
        }
    }


    public static Tile combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
        return targetTile;
    }

    public static double calcScatteringCos(double sza, double vza, double saa, double vaa) {
        final double sins = (float) Math.sin(sza * MathUtils.DTOR);
        final double sinv = (float) Math.sin(vza * MathUtils.DTOR);
        final double coss = (float) Math.cos(sza * MathUtils.DTOR);
        final double cosv = (float) Math.cos(vza * MathUtils.DTOR);

        // Compute the geometric conditions
        final double cosphi = Math.cos((vaa - saa) * MathUtils.DTOR);

        // cos of scattering angle
        return -coss * cosv - sins * sinv * cosphi;
    }

    private static Color getRandomColour(Random random) {
        int rColor = random.nextInt(256);
        int gColor = random.nextInt(256);
        int bColor = random.nextInt(256);
        return new Color(rColor, gColor, bColor);
    }

    private static Product cloneProduct(Product sourceProduct, int width, int height, boolean copySourceBands) {
        Product clonedProduct = new Product(sourceProduct.getName(),
                                            sourceProduct.getProductType(),
                                            width,
                                            height);

        ProductUtils.copyMetadata(sourceProduct, clonedProduct);
        ProductUtils.copyGeoCoding(sourceProduct, clonedProduct);
        ProductUtils.copyFlagCodings(sourceProduct, clonedProduct);
        ProductUtils.copyFlagBands(sourceProduct, clonedProduct, true);
        ProductUtils.copyMasks(sourceProduct, clonedProduct);
        clonedProduct.setStartTime(sourceProduct.getStartTime());
        clonedProduct.setEndTime(sourceProduct.getEndTime());

        if (copySourceBands) {
            // copy all bands from source product
            for (Band b : sourceProduct.getBands()) {
                if (!clonedProduct.containsBand(b.getName())) {
                    ProductUtils.copyBand(b.getName(), sourceProduct, clonedProduct, true);
                    if (isIdepixSpectralBand(b)) {
                        ProductUtils.copyRasterDataNodeProperties(b, clonedProduct.getBand(b.getName()));
                    }
                }
            }

            for (int i = 0; i < sourceProduct.getNumTiePointGrids(); i++) {
                TiePointGrid srcTPG = sourceProduct.getTiePointGridAt(i);
                if (!clonedProduct.containsTiePointGrid(srcTPG.getName())) {
                    clonedProduct.addTiePointGrid(srcTPG.cloneTiePointGrid());
                }
            }
        }

        return clonedProduct;
    }

    private static boolean isInputConsistent(Product sourceProduct, AlgorithmSelector algorithm) {
        if (AlgorithmSelector.MSI == algorithm) {
            return (isValidSentinel2(sourceProduct));
        }  else {
            throw new OperatorException("Algorithm " + algorithm.toString() + "not supported.");
        }
    }

    public static int calculateTileId(Rectangle targetRectangle, Product targetProduct) {
        Dimension tileSize = targetProduct.getPreferredTileSize();
        int tileX = (int) (targetRectangle.getX() / tileSize.getWidth());
        int tileY = (int) (targetRectangle.getY() / tileSize.getHeight());
        int numXTiles = targetProduct.getSceneRasterWidth() / (int) tileSize.getWidth();
        return (tileY * numXTiles) + tileX;
    }

    public static GeoPos getCenterGeoPos(Product product) {
        final PixelPos centerPixelPos = new PixelPos(0.5 * product.getSceneRasterWidth() + 0.5,
                                                     0.5 * product.getSceneRasterHeight() + 0.5);
        return product.getSceneGeoCoding().getGeoPos(centerPixelPos, null);
    }
}
