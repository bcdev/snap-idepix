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

    @Parameter(defaultValue = "31",
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

    private Band pixelClassifFlagBand;

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

        pixelClassifFlagBand = olciCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);

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

        final int extent2 = (computeCSI || computeOcimpCloudShadow) ? ocimpCloudShadowWindowSize : 0;
        rectExtender2 = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                            l1bProduct.getSceneRasterHeight()), extent2, extent2);

        setOlciReflBands();
        if (computeCSI) {
            postProcessedCloudProduct.addBand(IdepixOlciConstants.CSI_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        }
        if (computeOcimpCloudShadow) {
            postProcessedCloudProduct.addBand(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME, ProductData.TYPE_FLOAT32);

            ProductUtils.copyBand(olciReflBands[0].getName(), rad2reflProduct, postProcessedCloudProduct, true);
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
            final Tile sourceFlagTile = getSourceTile(pixelClassifFlagBand, srcRectangle);

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
            // experimental part for Idepix, requested by JW , Oct 2023
            final Rectangle srcExtRectangle = rectExtender2.extend(targetRectangle);

            final Tile pixelClassifFlagTile = getSourceTile(pixelClassifFlagBand, srcExtRectangle);

            Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
            for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], srcExtRectangle);
            }

            try {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        final double csi = computeCsiIdepixExperimental(x, y, srcExtRectangle,
                                                                        pixelClassifFlagTile, olciReflectanceTiles);
                        targetTile.setSample(x, y, csi);
                    }
                }
            } catch (Exception e) {
                throw new OperatorException("Failed to provide OLCI cloud shadow index:\n" + e.getMessage(), e);
            }

        }

        if (computeOcimpCloudShadow && targetBand.getName().equals(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME)) {
            // breadboard implementation for IPF interface in Ocimp.
            // Cloud shadow retrieval to be provided as pluggable C function for Eumetsat, Nov 2023.
            final Rectangle srcExtRectangle = rectExtender2.extend(targetRectangle);

            final Tile sourceFlagTile = getSourceTile(pixelClassifFlagBand, srcExtRectangle);
            final Tile oa01ReflectanceTile = getSourceTile(olciReflBands[0], srcExtRectangle);

            try {
                if (targetBand.getName().equals(IdepixOlciConstants.OCIMP_CSI_FINAL_BAND_NAME)) {
                    for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                        checkForCancellation();
                        for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                            final double ocimpCsiFinal = getOcimpCsiFinal(srcExtRectangle, sourceFlagTile,
                                                                          oa01ReflectanceTile, y, x);
                            // We write cloud shadow index to target product. This allows further tuning in SNAP.
                            // Cloud shadow can be obtained from:
                            // isCloudShadowOcimp = (csi < CSI_THRESH)  with default CSI_THRESH = 0.98

                            // for IPF interface we will need (DD):
//                            We agreed to have a ‘cloud_shadow’ function that will be called in the IPF.
//                            The function will be call only on applicable pixels : (WATER | INLAND_WATER ) & NOT(INVALD or HIGH_GLINT)
//                                    -	Input provided to the function call:
//                            float rhotoa4cs[NEEDED_WL];  /* TOA Rho at required wavelength for cloud shadow processing. Type of input could also be double */
//                            -	Output:
//                            The function should return an integer (0: no CS | 1: CS)
//
//                            The cloud shadow code should also have a variable allowing to set the window size of the spatial-spectral method. By default this variable can be set to 33.

                            targetTile.setSample(x, y, ocimpCsiFinal);
                        }
                    }
                }
            } catch (Exception e) {
                throw new OperatorException("Failed to provide OLCI OCIMP CSI:\n" + e.getMessage(), e);
            }
        }
    }

    /**
     * Wrapper method for two-step CSI retrieval for Ocimp
     *
     * @param srcExtRectangle      - rectangle of extended source tile
     * @param pixelClassifFlagTile - tile with Idepix flag from main classification step
     * @param oa01ReflectanceTile  - tile with OLCI Oa01 reflectance
     * @param y                    - y coord (MxM window center)
     * @param x                    - x coord (MxM window center)
     * @return double csi
     */
    private double getOcimpCsiFinal(Rectangle srcExtRectangle, Tile pixelClassifFlagTile, Tile oa01ReflectanceTile, int y, int x) {
        final double csi = computeCsi(x, y, srcExtRectangle,
                                      pixelClassifFlagTile, oa01ReflectanceTile, false, ocimpCloudShadowWindowSize);
        final boolean isPca = computePca(csi);

        return computeCsi(x, y, srcExtRectangle,
                          pixelClassifFlagTile, oa01ReflectanceTile, isPca, ocimpCloudShadowWindowSize);
    }

    /**
     * Provides cloud shadow index. For test purposes for cloud shadow implementation, using refl01-06.
     *
     * @param x                    - x coord (MxM window center)
     * @param y                    - y coord (MxM window center)
     * @param extendedRectangle    - rectangle of extended source tile
     * @param pixelClassifFlagTile - tile with Idepix flag from main classification step
     * @param olciReflectanceTiles - tiles with OLCI Oa01-06 reflectances
     * @return csi
     */
    static double computeCsiIdepixExperimental(int x, int y,
                                               Rectangle extendedRectangle,
                                               Tile pixelClassifFlagTile,
                                               Tile[] olciReflectanceTiles) {

        boolean isInvalid = pixelClassifFlagTile.getSampleBit(x, y, IDEPIX_INVALID);
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

        double[][] oa01Masked = new double[CSI_FILTER_WINDOW_SIZE][CSI_FILTER_WINDOW_SIZE];
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (extendedRectangle.contains(i, j)) {
                    isInvalid = pixelClassifFlagTile.getSampleBit(i, j, IdepixOlciConstants.L1_F_INVALID);
                    if (!isInvalid) {
                        final int ix = i - x + CSI_FILTER_WINDOW_SIZE / 2;
                        final int iy = j - y + CSI_FILTER_WINDOW_SIZE / 2;
                        oa01Masked[ix][iy] = 0.0;
                        for (int k = 0; k < 6; k++) {
                            oa01Masked[ix][iy] += olciReflectanceTiles[k].getSampleDouble(i, j);
                        }
                        oa01Masked[ix][iy] /= 6.0;
                    }
                }
            }
        }

        //        Step2/6: Use MxM mean filter:
        //        Oa01_filtered_initial = spatial_mean_filetring(Oa01_masked_initial)
        //
        final double oa01Centre = oa01Masked[CSI_FILTER_WINDOW_SIZE / 2][CSI_FILTER_WINDOW_SIZE / 2];
        final boolean filterReflectance = false;  // todo: clarify
        double oa01Filtered = (filterReflectance && Double.isNaN(oa01Centre)) ? Double.NaN :
                getMeanReflectance(x, y, LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                                   extendedRectangle, filterReflectance,
                                   pixelClassifFlagTile, CSI_FILTER_WINDOW_SIZE, oa01Masked);

        //        Step3/7: calculate CSI (Cloud and Shadow Index)
        //        CSI_initial = Oa01_masked_initial / Oa01_filtered_initial
        return oa01Centre / oa01Filtered;
    }

    /**
     * Provides cloud shadow index. OCIMP approach, using two-step filtering with Oa01 ond potential cloud area (pca)
     *
     * @param x                    - x coord (MxM window center)
     * @param y                    - y coord (MxM window center)
     * @param extendedRectangle    - rectangle of extended source tile
     * @param pixelClassifFlagTile - tile with Idepix flag from main classification step
     * @param oa01ReflectanceTile  - tile with Oa01 reflectance
     * @param isPca                - if true, pixel is potential cloud area
     * @param csiFilterWindowSize  - filter window size (must be sufficiently large, default 31, maximum accepted by Eumetsat)
     * @return - double csi
     */
    static double computeCsi(int x, int y,
                             Rectangle extendedRectangle,
                             Tile pixelClassifFlagTile,
                             Tile oa01ReflectanceTile,
                             boolean isPca,
                             int csiFilterWindowSize) {

        // Do not further investigate invalid pixels
        boolean isInvalid = pixelClassifFlagTile.getSampleBit(x, y, IDEPIX_INVALID);
        if (isInvalid) {
            return Double.NaN;
        }

//        Step 1/5 : Mask using cloud, snow and land flags
//                Oa01_masked = if (WQSF_lsb_WATER or WQSF_lsb_INLAND_WATER) and not
//                (WQSF_lsb_INVALID or  WQSF_lsb_CLOUD or WQSF_lsb_CLOUD_AMBIGUOUS or WQSF_lsb_SNOW_ICE [or PCA_mask])
//                then Oa01_reflectance else NaN

        int LEFT_BORDER = Math.max(x - csiFilterWindowSize / 2, extendedRectangle.x);
        int RIGHT_BORDER = Math.min(x + csiFilterWindowSize / 2, extendedRectangle.x + extendedRectangle.width - 1);
        int TOP_BORDER = Math.max(y - csiFilterWindowSize / 2, extendedRectangle.y);
        int BOTTOM_BORDER = Math.min(y + csiFilterWindowSize / 2, extendedRectangle.y + extendedRectangle.height - 1);

        double[][] oa01Masked = getMaskedRefl(x, y,
                                              LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                                              extendedRectangle,
                                              pixelClassifFlagTile, oa01ReflectanceTile, isPca, csiFilterWindowSize);

        //        Step2/6: Use MxM mean filter:
        //        Oa01_filtered_initial = spatial_mean_filetring(Oa01_masked_initial)
        //
        final double oa01Centre = oa01Masked[csiFilterWindowSize / 2][csiFilterWindowSize / 2];
        double oa01Filtered = Double.isNaN(oa01Centre) ? Double.NaN :
                getMeanReflectance(x, y, LEFT_BORDER, RIGHT_BORDER, TOP_BORDER, BOTTOM_BORDER,
                                   extendedRectangle, true,
                                   pixelClassifFlagTile, csiFilterWindowSize, oa01Masked);

        //        Step3/7: calculate CSI (Cloud and Shadow Index)
        //        CSI_initial = Oa01_masked_initial / Oa01_filtered_initial
        return oa01Centre / oa01Filtered;
    }

    /**
     * Provides potential cloud area from csi threshold
     *
     * @param csi - cloud shadow index
     * @return boolean pca
     */
    static boolean computePca(double csi) {
        return csi > 1.02;
    }

    /**
     * Provides masked reflectance array.
     *
     * @param x                    - x coord (MxM window center)
     * @param y                    - y coord (MxM window center)
     * @param left_border          - x coord of MxM window left border
     * @param right_border         - x coord of MxM window right border
     * @param top_border           - y coord of MxM window top border
     * @param bottom_border        - y coord of MxM window bottom border
     * @param extendedRectangle    - rectangle of extended source tile
     * @param reflectanceTile      - tile with given reflectance
     * @param pixelClassifFlagTile - tile with Idepix flag from main classification step
     * @param isPca                - if true, pixel is potential cloud area
     * @param csiFilterWindowSize  - filter window size (must be sufficiently large, default 31, maximum accepted by Eumetsat)
     *
     * @return double[][] masked refl
     */
    private static double[][] getMaskedRefl(int x, int y,
                                            int left_border, int right_border, int top_border, int bottom_border,
                                            Rectangle extendedRectangle,
                                            Tile pixelClassifFlagTile,
                                            Tile reflectanceTile,
                                            boolean isPca, int csiFilterWindowSize) {
        boolean isInvalid;
        double[][] oa01MaskedInitial = new double[csiFilterWindowSize][csiFilterWindowSize];
        for (int i = left_border; i <= right_border; i++) {
            for (int j = top_border; j <= bottom_border; j++) {
                if (extendedRectangle.contains(i, j)) {
                    isInvalid = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_INVALID);
                    final boolean isLand = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_LAND);
                    final boolean isCloud = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_CLOUD);
                    final boolean isCloudAmbiguous = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_CLOUD_AMBIGUOUS);
                    final boolean isSnowIce = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_SNOW_ICE);
                    final int ix = i - x + csiFilterWindowSize / 2;
                    final int jy = j - y + csiFilterWindowSize / 2;
                    if (isInvalid || isLand || isCloud || isCloudAmbiguous || isSnowIce || isPca) {
                        oa01MaskedInitial[ix][jy] = Double.NaN;
                    } else {
                        oa01MaskedInitial[ix][jy] = reflectanceTile.getSampleDouble(i, j);
                    }
                }
            }
        }
        return oa01MaskedInitial;
    }

    /**
     * Provides mean reflectance of MxM window.
     *
     * @param x                    - x coord (MxM window center)
     * @param y                    - y coord (MxM window center)
     * @param left_border          - x coord of MxM window left border
     * @param right_border         - x coord of MxM window right border
     * @param top_border           - y coord of MxM window top border
     * @param bottom_border        - y coord of MxM window bottom border
     * @param extendedRectangle    - rectangle of extended source tile
     * @param filterReflectance    - if applied, only filtered pixels are considered
     * @param pixelClassifFlagTile - tile with Idepix flag from main classification step
     * @param windowSize  - filter window size (must be sufficiently large, default 31, maximum accepted by Eumetsat)
     * @param reflArr - input reflectance array
     * @return double mean refl
     */
    private static double getMeanReflectance(int x, int y,
                                             int left_border, int right_border, int top_border, int bottom_border,
                                             Rectangle extendedRectangle,
                                             boolean filterReflectance,
                                             Tile pixelClassifFlagTile, int windowSize,
                                             double[][] reflArr) {
        double meanValue = 0.0;
        int validCount = 0;
        for (int i = left_border; i <= right_border; i++) {
            for (int j = top_border; j <= bottom_border; j++) {
                if (extendedRectangle.contains(i, j)) {
                    final boolean isInvalid = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_INVALID);
                    final boolean isLand = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_LAND);
                    final boolean isCloud = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_CLOUD);
                    final boolean isCloudAmbiguous = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_CLOUD_AMBIGUOUS);
                    final boolean isSnowIce = pixelClassifFlagTile.getSampleBit(i, j, IDEPIX_SNOW_ICE);
                    final boolean filterFromFlag = isInvalid || isLand || isCloud || isCloudAmbiguous || isSnowIce;
                    if (!filterReflectance || !filterFromFlag) {
                        validCount++;
                        final int ix = i - x + windowSize / 2;
                        final int iy = j - y + windowSize / 2;
                        meanValue += reflArr[ix][iy];
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
