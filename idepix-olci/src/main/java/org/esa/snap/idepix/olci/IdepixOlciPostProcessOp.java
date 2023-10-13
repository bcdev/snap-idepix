package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.*;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.esa.snap.idepix.core.IdepixConstants.*;
import static org.esa.snap.idepix.olci.IdepixOlciConstants.CSI_FILTER_WINDOW_SIZE;

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

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow index.",
            description = " If applied, compute cloud shadow index and write to the target product ")
    private boolean computeCSI;

    @Parameter(defaultValue = "false",
            label = " Compute OCIMP cloud shadow.",
            description = " If applied, compute OCIMP cloud shadow ")
    private boolean computeOcimpCloudShadow;

    @Parameter(defaultValue = "11",
            label = " Window size for OCIMP cloud shadow computation.",
            description = " The window size for OCIMP cloud shadow computation (must be positive and odd number ")
    private int ocimpCloudShadowWindowSize;

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer",
            description = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true", label = " Compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(label = " Extent of mountain shadow", defaultValue = "0.9", interval = "[0,1]",
            description = "Extent of mountain shadow detection")
    private double mntShadowExtent;

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with latest 'fronts' algorithm. Requires CTP product.")
    private boolean computeCloudShadow;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "olciCloud")
    private Product olciCloudProduct;

    @SourceProduct(alias = "ctp", optional = true,
    description = "Must contain a band with the name 'ctp'.")
    private Product ctpProduct;

    @SourceProduct(alias = "rhotoa")
    private Product rad2reflProduct;

    private Band origCloudFlagBand;

    private Band[] olciReflBands;

    private Band ctpBand;
    private TiePointGrid szaTPG;
    private TiePointGrid saaTPG;
    private TiePointGrid ozaTPG;
    private TiePointGrid oaaTPG;
    private TiePointGrid slpTPG;
    private TiePointGrid[] temperatureProfileTPGs;
    private Band altBand;
    private Band mountainShadowFlagBand;

    private GeoCoding geoCoding;

    private RectangleExtender rectExtender;
    private RectangleExtender rectExtender2;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(olciCloudProduct,
                "postProcessedCloud",
                "postProcessedCloud",
                true);

        geoCoding = l1bProduct.getSceneGeoCoding();

        if (computeCloudShadow && (ctpProduct == null || !ctpProduct.containsBand("ctp"))) {
            throw new OperatorException("Cloud shadow computation needs a CTP product containing a band named 'ctp'.");
        }

        origCloudFlagBand = olciCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);

        szaTPG = l1bProduct.getTiePointGrid("SZA");
        saaTPG = l1bProduct.getTiePointGrid("SAA");
        ozaTPG = l1bProduct.getTiePointGrid("OZA");
        oaaTPG = l1bProduct.getTiePointGrid("OAA");
        slpTPG = l1bProduct.getTiePointGrid("sea_level_pressure");

        final Band latBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_LATITUDE_BAND_NAME);
        final Band lonBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_LONGITUDE_BAND_NAME);
        altBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME);

        temperatureProfileTPGs = new TiePointGrid[IdepixOlciConstants.referencePressureLevels.length];
        for (int i = 0; i < IdepixOlciConstants.referencePressureLevels.length; i++) {
            temperatureProfileTPGs[i] =
                    l1bProduct.getTiePointGrid("atmospheric_temperature_profile_pressure_level_" + (i + 1));
        }

        if (computeMountainShadow) {
            ensureBandsAreCopied(l1bProduct, olciCloudProduct, latBand.getName(), lonBand.getName(), altBand.getName());
            Map<String, Object> mntShadowParams = new HashMap<>();
            mntShadowParams.put("mntShadowExtent", mntShadowExtent);

            HashMap<String, Product> input = new HashMap<>();
            input.put("l1b", l1bProduct);
            final Product mountainShadowProduct = GPF.createProduct(
                    OperatorSpi.getOperatorAlias(IdepixOlciMountainShadowOp.class), mntShadowParams, input);
            mountainShadowFlagBand = mountainShadowProduct.getBand(
                    IdepixOlciMountainShadowOp.MOUNTAIN_SHADOW_FLAG_BAND_NAME);
        }

        if (computeCloudShadow) {
            ctpBand = ctpProduct.getBand("ctp");
        }

        int cloudShadowExtent = l1bProduct.getName().contains("FR____") ? 64 : 16;
        int extent = computeCloudShadow ? cloudShadowExtent : computeCloudBuffer ? cloudBufferWidth : 0;
        rectExtender = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), extent, extent);

        final int extent2 = (computeCSI  || computeOcimpCloudShadow) ? ocimpCloudShadowWindowSize : 0;
        rectExtender2 = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), extent2, extent2);

        setOlciReflBands();
        if (computeCSI) {
            postProcessedCloudProduct.addBand(IdepixOlciConstants.CSI_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        }
        if (computeOcimpCloudShadow) {
//            postProcessedCloudProduct.addBand(IdepixOlciConstants.OCIMP_CLOUD_SHADOW_BAND_NAME, ProductData.TYPE_INT8);
            postProcessedCloudProduct.addBand(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME, ProductData.TYPE_FLOAT32);
        }

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, olciCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    private void ensureBandsAreCopied(Product source, Product target, String... bandNames) {
        for (String bandName : bandNames) {
            if (!target.containsBand(bandName)) {
                ProductUtils.copyBand(bandName, source, target, true);
            }
        }
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();

        if (targetBand.getName().equals(IdepixConstants.CLASSIF_BAND_NAME)) {
            final Rectangle srcRectangle = rectExtender.extend(targetRectangle);
            final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);

            for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
                checkForCancellation();
                for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                    if (targetRectangle.contains(x, y)) {
                        boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                        combineFlags(x, y, sourceFlagTile, targetTile);
                        if (isCloud) {
                            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);   // necessary??
                        }
                    }
                }
            }

            if (computeCloudBuffer) {
                CloudBuffer.setCloudBuffer(targetTile, srcRectangle, sourceFlagTile, cloudBufferWidth);
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        IdepixUtils.consolidateCloudAndBuffer(targetTile, x, y);
                    }
                }
            }

            if (computeCloudShadow) {
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
                        altTile);
                cloudShadowFronts.computeCloudShadow(sourceFlagTile, targetTile);
            }

            if (computeMountainShadow) {
                final Tile mountainShadowFlagTile = getSourceTile(mountainShadowFlagBand, targetRectangle);
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        final boolean mountainShadow = mountainShadowFlagTile.getSampleInt(x, y) > 0;
                        targetTile.setSample(x, y, IdepixOlciConstants.IDEPIX_MOUNTAIN_SHADOW, mountainShadow);
                    }
                }
            }
        }

        if (computeCSI && targetBand.getName().equals(IdepixOlciConstants.CSI_OUTPUT_BAND_NAME)) {
            final Rectangle srcExtRectangle = rectExtender2.extend(targetRectangle);

            final Band olciQualityFlagBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
            final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, srcExtRectangle);

            Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
            for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], srcExtRectangle);
            }

            try {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        final double csi = computeCsi(x, y, srcExtRectangle, olciQualityFlagTile,
                                olciReflectanceTiles);
                        targetTile.setSample(x, y, csi);
                    }
                }
            } catch (Exception e) {
                throw new OperatorException("Failed to provide OLCI pixel classification:\n" + e.getMessage(), e);
            }

        }
//        if (computeOcimpCloudShadow && targetBand.getName().equals(IdepixOlciConstants.OCIMP_CLOUD_SHADOW_BAND_NAME)) {
        if (computeOcimpCloudShadow && targetBand.getName().equals(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME)) {
            // todo: lots of code duplication, improve
            final Rectangle srcExtRectangle = rectExtender2.extend(targetRectangle);

            final Band olciQualityFlagBand = l1bProduct.getBand(IdepixOlciConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
            final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, srcExtRectangle);

            final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcExtRectangle);

            Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
            for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], srcExtRectangle);
            }

            try {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
//                        final boolean isOcimpCloudShadow = computeCloudShadowOcimp(x, y, srcExtRectangle,
                        final double ocimpCsiFinal = computeCloudShadowOcimp(x, y, srcExtRectangle,
                                olciQualityFlagTile,
                                sourceFlagTile,
                                olciReflectanceTiles[0], ocimpCloudShadowWindowSize);
//                        targetTile.setSample(x, y, isOcimpCloudShadow ? 1 : 0);
                        targetTile.setSample(x, y, ocimpCsiFinal);
                    }
                }
            } catch (Exception e) {
                throw new OperatorException("Failed to provide OLCI pixel classification:\n" + e.getMessage(), e);
            }
        }
    }

    /**
     * Provides cloud shadow index. For test purposes towards OCIMP cloud shadow implementation.
     *
     * @param x -
     * @param y -
     * @param extendedRectangle -
     * @param olciQualityFlagTile -
     * @param olciReflectanceTiles -
     *
     * @return csi
     */
    static double computeCsi(int x, int y,
                             Rectangle extendedRectangle,
                             Tile olciQualityFlagTile,
                             Tile[] olciReflectanceTiles) {

        boolean isInvalid = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
        if (isInvalid) {
            return Double.NaN;
        }

        //        Step1: Calculate mean reflectance of VIS bands between 400nm and 600nm
        //                MeanVIS = (Oa01_reflectance + Oa02_reflectance + Oa03_reflectance + Oa04_reflectance + Oa05_reflectance + Oa06_reflectance)/6
        //
        int LEFT_BORDER = Math.max(x - CSI_FILTER_WINDOW_SIZE / 2, extendedRectangle.x);
        int RIGHT_BORDER = Math.min(x + CSI_FILTER_WINDOW_SIZE / 2, extendedRectangle.x + extendedRectangle.width - 1);
        int TOP_BORDER = Math.max(y - CSI_FILTER_WINDOW_SIZE / 2, extendedRectangle.y);
        int BOTTOM_BORDER = Math.min(y + CSI_FILTER_WINDOW_SIZE / 2, extendedRectangle.y + extendedRectangle.height - 1);

        double[][] meanVisArr = new double[CSI_FILTER_WINDOW_SIZE][CSI_FILTER_WINDOW_SIZE];
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (extendedRectangle.contains(i, j)) {
                    isInvalid = olciQualityFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_INVALID);
                    if (!isInvalid) {
                        final int ix = i - x + CSI_FILTER_WINDOW_SIZE / 2;
                        final int iy = j - y + CSI_FILTER_WINDOW_SIZE / 2;
                        meanVisArr[ix][iy] = 0.0;
                        for (int k = 0; k < 6; k++) {
                            meanVisArr[ix][iy] += olciReflectanceTiles[k].getSampleDouble(i, j);
                        }
                        meanVisArr[ix][iy] /= 6.0;
                    }
                }
            }
        }

        //        Step2: Use 11x11 mean filter:
        //        MeanVIS_filtered = spatial_mean_filetring(MeanVIS)
        //
        double meanVisFiltered = getMean(LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                extendedRectangle, olciQualityFlagTile, x, y, CSI_FILTER_WINDOW_SIZE, meanVisArr);

        //        Step3: calculate CSI (Cloud and Shadow Index)
        //        CSI = MeanVIS/MeanVIS_filtered
        return meanVisArr[CSI_FILTER_WINDOW_SIZE / 2][CSI_FILTER_WINDOW_SIZE / 2] / meanVisFiltered;
    }

    /**
     * Cloud shadow implementation foreseen for OCIMP project.
     * To be transformed into a C function within Eumetsat IPF processing workflow.
     * Just for test purpose here, maybe remove later.
     *
     * @param x -
     * @param y -
     * @param extendedRectangle -
     * @param olciQualityFlagTile -
     * @param cloudFlagTile -
     * @param oa01ReflectanceTile -
     * @param csiFilterWindowSize -
     *
     * @return boolean isCloudShadow
     */
    static double computeCloudShadowOcimp(int x, int y,
                                           Rectangle extendedRectangle,
                                           Tile olciQualityFlagTile,
                                           Tile cloudFlagTile,
                                           Tile oa01ReflectanceTile,
                                           int csiFilterWindowSize) {

        // Do not further investigate invalid pixels
        boolean isInvalid = olciQualityFlagTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
        if (isInvalid) {
//            return false;
            return Double.NaN;
        }

//        Step 1 : Mask using cloud, snow and land flags
//                Oa01_masked initial = if (WQSF_lsb_WATER or WQSF_lsb_INLAND_WATER) and not
//                (WQSF_lsb_INVALID or  WQSF_lsb_CLOUD or WQSF_lsb_CLOUD_AMBIGUOUS or WQSF_lsb_SNOW_ICE)
//                then Oa01_reflectance else NaN

        int LEFT_BORDER = Math.max(x - csiFilterWindowSize / 2, extendedRectangle.x);
        int RIGHT_BORDER = Math.min(x + csiFilterWindowSize / 2, extendedRectangle.x + extendedRectangle.width - 1);
        int TOP_BORDER = Math.max(y - csiFilterWindowSize / 2, extendedRectangle.y);
        int BOTTOM_BORDER = Math.min(y + csiFilterWindowSize / 2, extendedRectangle.y + extendedRectangle.height - 1);

        double[][] oa01MaskedArr = new double[csiFilterWindowSize][csiFilterWindowSize];
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (extendedRectangle.contains(i, j)) {
                    isInvalid = olciQualityFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_INVALID);
                    final boolean isLand = olciQualityFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_LAND);
                    final boolean isCloud = cloudFlagTile.getSampleBit(i, j, IDEPIX_CLOUD);
                    final boolean isCloudAmbiguous = cloudFlagTile.getSampleBit(i, j, IDEPIX_CLOUD_AMBIGUOUS);
                    final boolean isSnowIce = cloudFlagTile.getSampleBit(i, j, IDEPIX_SNOW_ICE);
                    final int ix = i - x + csiFilterWindowSize / 2;
                    final int jy = j - y + csiFilterWindowSize / 2;
                    if (isInvalid || isLand || isCloud || isCloudAmbiguous || isSnowIce) {
                        oa01MaskedArr[ix][jy] = Double.NaN;
                    } else {
                        oa01MaskedArr[ix][jy] = oa01ReflectanceTile.getSampleDouble(i, j);
                    }
                }
            }
        }

        //        Step2: Use MxM mean filter:
        //        Oa01_filtered_initial = spatial_mean_filetring(Oa01_masked_initial)
        //
        double oa01FilteredInitial = getMean(LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                extendedRectangle, olciQualityFlagTile, x, y, csiFilterWindowSize, oa01MaskedArr);

        //        Step3: calculate CSI (Cloud and Shadow Index)
        //        CSI_initial = Oa01_masked_initial / Oa01_filtered_initial
        final double oa01Centre = oa01MaskedArr[csiFilterWindowSize / 2][csiFilterWindowSize / 2];
        final double csiInitial = oa01Centre / oa01FilteredInitial;

        //        Step4: set PCA (Potential Cloud Area) using threshold
        //        PCA_mask = True if CSI_initial > 1.02 else False
        final boolean isPca = csiInitial > 1.02;

        //        Step5: Mask using cloud, snow and land flags
        //                Oa01_masked = if (WQSF_lsb_WATER or WQSF_lsb_INLAND_WATER) and not
        //                (WQSF_lsb_INVALID or  WQSF_lsb_CLOUD or WQSF_lsb_CLOUD_AMBIGUOUS
        //                or WQSF_lsb_SNOW_ICE or PCA_mask)
        //                then Oa01_reflectance else NaN
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (extendedRectangle.contains(i, j)) {
                    final int ix = i - x + csiFilterWindowSize / 2;
                    final int jy = j - y + csiFilterWindowSize / 2;
                    if (isPca) {
                        oa01MaskedArr[ix][jy] = Double.NaN;
                    }
                }
            }
        }

        //        Step6: Use MxM mean filter:
        //        Oa01_filtered = spatial_mean_filetring(Oa01_masked)
        //
        final double oa01FilteredFinal = getMean(LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                extendedRectangle, olciQualityFlagTile, x, y, csiFilterWindowSize, oa01MaskedArr);

        //        Step7: calculate CSI (Cloud and Shadow Index)
        //        CSI = Oa01_masked / Oa01_filtered
        final double csiFinal = oa01Centre / oa01FilteredFinal;

        //        Step8: return CSM (Cloud Shadow Mask) using threshold
        //        CSM = True if CSI < 0.98 else False
//        return csiFinal < 0.98;
        return csiFinal;
    }

    private static double getMean(int left_border, int right_border, int top_border, int bottom_border,
                                  Rectangle extendedRectangle,
                                  Tile flagTile, int x, int y, int windowSize,
                                  double[][] dataArr) {
        double meanValue = 0.0;
        int validCount = 0;
        for (int i = left_border; i <= right_border; i++) {
            for (int j = top_border; j <= bottom_border; j++) {
                if (extendedRectangle.contains(i, j)) {
                    boolean isInvalid = flagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_INVALID);
                    if (!isInvalid) {
                        validCount++;
                        final int ix = i - x + windowSize / 2;
                        final int iy = j - y + windowSize / 2;
                        meanValue += dataArr[ix][iy];
                    }
                }
            }
        }
        meanValue /= validCount;
        return meanValue;
    }

    private void setOlciReflBands() {
        olciReflBands = new Band[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].substring(0, suffixStart);
            olciReflBands[i] = rad2reflProduct.getBand(reflBandname + "_reflectance");
        }
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
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
