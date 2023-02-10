package org.esa.snap.idepix.s2msi.operators;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.awt.Rectangle;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.*;

/**
 * Adds a cloud buffer to cloudy pixels and does a cloud discrimination on coastal areas and urban areas.
 */
@OperatorMetadata(alias = "Idepix.S2CloudPostProcess2",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne, Martin Boettcher",
        copyright = "(c) 2016-2023 by Brockmann Consult",
        description = "Performs post processing of cloudy pixels and adds optional cloud buffer.")
public class S2IdepixCloudPostProcess2Op extends Operator {

    @SourceProduct(alias = "classifiedProduct")
    private Product classifiedProduct;

    @Parameter(defaultValue = "true", label = "Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "true", label = "Compute cloud buffer for cloud ambiguous pixels too.")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    // variables set in initialize and used in computeTile
    private Band origClassifFlagBand;
    private Band b2Band;
    private Band b7Band;
    private Band b8Band;
    private Band b8aBand;
    private Band b11Band;

    private int landWaterContextSize;
    private int urbanContextSize = 11;
    private int cdiStddevContextSize = 7;
    private int contextSize;
    private int cloudBufferSize;
    
    private int landWaterContextRadius;
    private int urbanContextRadius = urbanContextSize / 2;
    private int cdiStddevContextRadius = cdiStddevContextSize / 2;
    private int contextRadius;

    private RectangleExtender pixelStateRectCalculator;
    private RectangleExtender urbanRectCalculator;
    private RectangleExtender cdiRectCalculator;
    private RectangleExtender cloudBufferRectCalculator;

    private static final float COAST_BUFFER_SIZE = 1000f;  // [m]
    private static final double CDI_THRESHOLD = -0.5;

    /**
     * Creates the output product with a pixel_classif_flags band, gets bands of the input product, and
     * determines sizes for buffers. It prepares rectangle calculators that limit the rectangle
     * to the image at the border even if source tile is extended.
     *
     * @throws OperatorException
     */
    @Override
    public void initialize() throws OperatorException {

        Product cloudBufferProduct = createTargetProduct(classifiedProduct,
                classifiedProduct.getName(),
                classifiedProduct.getProductType());
        ProductUtils.copyBand(IDEPIX_CLASSIF_FLAGS, classifiedProduct, cloudBufferProduct, false);
        setTargetProduct(cloudBufferProduct);

        origClassifFlagBand = classifiedProduct.getBand(IDEPIX_CLASSIF_FLAGS);
        b2Band = classifiedProduct.getBand("B2");
        b7Band = classifiedProduct.getBand("B7");
        b8Band = classifiedProduct.getBand("B8");
        b8aBand = classifiedProduct.getBand("B8A");
        b11Band = classifiedProduct.getBand("B11");

        // add previous state for debugging, comment out in operational version
        //ProductUtils.copyBand("pixel_classif_flags", classifiedProduct,
        //                      "flags_before_postprocessing", cloudBufferProduct, true);

        if (!computeCloudBuffer) {
            cloudBufferWidth = 0;
        }

        final int resolution = S2IdepixUtils.determineResolution(classifiedProduct);
        landWaterContextSize = (int) Math.floor((2 * COAST_BUFFER_SIZE) / resolution);   // TODO shall this be odd?
        landWaterContextRadius = landWaterContextSize / 2;
        contextSize = Math.max(landWaterContextSize, Math.max(urbanContextSize, cdiStddevContextSize));
        contextRadius = contextSize / 2;
        cloudBufferSize = 2 * cloudBufferWidth + 1;
        
        pixelStateRectCalculator = createRectCalculator(contextRadius + cloudBufferWidth);
        urbanRectCalculator = createRectCalculator(urbanContextRadius + cloudBufferWidth);
        cdiRectCalculator = createRectCalculator(cdiStddevContextRadius + cloudBufferWidth);
        cloudBufferRectCalculator = createRectCalculator(cloudBufferWidth);
    }

    /**
     * Corrects pixel classification and eliminates false cloud flags in coastal and in
     * urban regions, adds cloud buffer. Cloud flags in mixed land-water areas are tested
     * with a test based on B2, B8, B11. Cloud flags in regions with clear pixels nearby
     * are tested with a test based on the spatial variance of a cloud displacement index
     * using B7, B8, and B8A.
     *
     * This single-pass implementation uses several accumulators of the full tile width
     * and a few lines to memorise and pre-compute various filtered values. Example:
     * To determine whether there is a clear pixel in a 11x11 box around a pixel we use an
     * accu with 11 lines. The accu is initialised with "false". A "clear" contribution
     * writes a patch of 11x11 values "true" into the accu. This value will be used 5 lines
     * and 5 rows later to determine whether there are clear pixels nearby. This is the last
     * time the pixel may have got a clear contribution by some pixel passed in the
     * single-pass. (Later pixels are more than 5 pixels away.)
     * The accu lines are used in a rolling manner (with modulo for line selection). The
     * values used are cleared for re-use.
     *
     * There are accumulators for
     * * landNearby 33x33 (60m), 100x100 (20m), 200x200 (10m)
     * * waterNearby 33x33 (60m), 100x100 (20m), 200x200 (10m)
     * * clearNearby 11x11
     * * sum, square sum, and count of two band ratio expressions for the CDI
     * * cloudBuffer 5x5 (cloudBufferWidth=2)
     * For simplicity all the accus except cloudBuffer use the maximum number of lines that
     * occurs (contextSize). This does not change the logic, just the buffer line used.
     *
     * Cloud buffer shall be determined based on the corrected cloud flags. It memorises
     * the flag value to be written into the target image and delays its writing by cloud
     * buffer width lines and columns. The flag may get updated by cloud pixels showing up
     * one or two lines later.
     *
     * @param targetBand The target band, pixel_classif_flags.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancellation requests.
     * @throws OperatorException
     */
    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle pixelStateRectangle = pixelStateRectCalculator.extend(targetRectangle);
        final Rectangle urbanRectangle = urbanRectCalculator.extend(targetRectangle);
        final Rectangle cdiRectangle = cdiRectCalculator.extend(targetRectangle);
        final Rectangle cdiExtendedRectangle = new Rectangle(targetRectangle);
        cdiExtendedRectangle.grow(cdiStddevContextRadius + cloudBufferWidth, cdiStddevContextRadius + cloudBufferWidth);
        final Rectangle contextExtendedRectangle = new Rectangle(targetRectangle);
        contextExtendedRectangle.grow(contextRadius + cloudBufferWidth, contextRadius + cloudBufferWidth);
        final Rectangle cloudBufferRectangle = cloudBufferRectCalculator.extend(targetRectangle);

        // get source tiles with different extents
        final Tile sourceFlagTile = getSourceTile(origClassifFlagBand, pixelStateRectangle);
        final Tile b7Tile = getSourceTile(b7Band, cdiRectangle);
        final Tile b8Tile = getSourceTile(b8Band, cdiRectangle);
        final Tile b8aTile = getSourceTile(b8aBand, cdiRectangle);
        final Tile b2Tile = getSourceTile(b2Band, cloudBufferRectangle);
        final Tile b11Tile = getSourceTile(b11Band, cloudBufferRectangle);

        // allocate and initialise some lines of full width plus cloud buffer to collect pixel contributions
        // The lines will be re-used for later image lines in a rolling manner.
        final boolean[][] landNearbyAccu = new boolean[contextSize][cloudBufferRectangle.width];
        final boolean[][] waterNearbyAccu = new boolean[contextSize][cloudBufferRectangle.width];
        final boolean[][] clearNearbyAccu = new boolean[contextSize][cloudBufferRectangle.width];
        final double[][] m7 = new double[contextSize][cloudBufferRectangle.width];
        final double[][] c7 = new double[contextSize][cloudBufferRectangle.width];
        final double[][] m8 = new double[contextSize][cloudBufferRectangle.width];
        final double[][] c8 = new double[contextSize][cloudBufferRectangle.width];
        final short[][] n78 = new short[contextSize][cloudBufferRectangle.width];
        // the accu for pixelClassifFlags can be smaller because we only read it within the target tile
        final int[][] cloudBufferAccu = new int[cloudBufferSize][targetRectangle.width];

        // loop over extended source tile
        // y/x run from target tile y/x - context radius - cloud buffer width to ...
        for (int y = contextExtendedRectangle.y; y < contextExtendedRectangle.y + contextExtendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = contextExtendedRectangle.x; x < contextExtendedRectangle.x + contextExtendedRectangle.width; x++) {

                // write dilated land and water patch into accu, required for coastal cloud distinction
                if (pixelStateRectangle.contains(x, y)) {
                    collectLand(y, x, sourceFlagTile, cloudBufferRectangle, landNearbyAccu);
                    collectWater(y, x, sourceFlagTile, cloudBufferRectangle, waterNearbyAccu);
                }

                // write 11x11 "filtered" non-cloud patch into accu, required for urban cloud distinction
                if (urbanRectangle.contains(x, y)) {
                    collectClear(y, x, sourceFlagTile, cloudBufferRectangle, clearNearbyAccu);
                }

                // add stddev contributions to accu of sums, squares, counts, required for urban cloud distinction
                if (cdiExtendedRectangle.contains(x, y)) {
                    collectCdiSumsAndSquares(y, x, b7Tile, b8Tile, b8aTile, cdiRectangle, cloudBufferRectangle,
                                             m7, c7, m8, c8, n78);
                }

                // yt/xt is the target pixel where we have just seen the last pixel of its context
                final int yt = y - contextRadius;
                final int xt = x - contextRadius;
                if (cloudBufferRectangle.contains(xt, yt)) {
                    correctFlagsOfPixel(yt, xt,
                                        landNearbyAccu, waterNearbyAccu, clearNearbyAccu,
                                        m7, c7, m8, c8, n78,
                                        sourceFlagTile, b2Tile, b8Tile, b11Tile,
                                        targetRectangle, cloudBufferRectangle,
                                        cloudBufferAccu);
                }
                // yb/xb is the target pixel where we have seen just the last pixel of some possible cloud buffer
                final int yb = yt - cloudBufferWidth;
                final int xb = xt - cloudBufferWidth;
                if (targetRectangle.contains(xb, yb)) {
                    writeFlagsOfPixel(yb, xb, cloudBufferAccu, targetRectangle, targetTile);
                }
            }
        }
    }

    private void correctFlagsOfPixel(int yt, int xt,
                                     boolean[][] landAccu, boolean[][] waterAccu, boolean[][] clearNearbyAccu,
                                     double[][] m7, double[][] c7, double[][] m8, double[][] c8, short[][] n78,
                                     Tile sourceFlagTile, Tile b2Tile, Tile b8Tile, Tile b11Tile,
                                     Rectangle targetRectangle, Rectangle cloudBufferRectangle,
                                     int[][] cloudBufferAccu) {
        // land/water/urban accu pixel position
        final int jt = (yt - cloudBufferRectangle.y) % contextSize;
        final int it = xt - cloudBufferRectangle.x;
        // read pixel classif flags from source, apply corrections
        int pixelClassifFlags = sourceFlagTile.getSampleInt(xt, yt);
        if (isValid(pixelClassifFlags)) {
            pixelClassifFlags = coastalCloudDistinction(yt, xt, jt, it, landAccu, waterAccu,
                                                        sourceFlagTile, b2Tile, b8Tile, b11Tile,
                                                        pixelClassifFlags);
            pixelClassifFlags = urbanCloudDistinction(jt, it, clearNearbyAccu,
                                                      m7, m8, c7, c8, n78,
                                                      pixelClassifFlags);
            // patch cloud buffer into accu
            if (computeCloudBuffer && isCloudForBuffer(pixelClassifFlags)) {
                addCloudBufferInAccu(yt, xt, targetRectangle, cloudBufferWidth, cloudBufferAccu);
            }
        }
        // add cloud buffer flag from accu to pixelClassifFlags
        // memorize p.c.f. in accu for later updates of cloud buffer flag and final writing
        if (targetRectangle.contains(xt, yt)) {
            final int jb = (yt - targetRectangle.y) % cloudBufferSize;
            final int ib = xt - targetRectangle.x;
            // read cloud buffer from accu (for clouds in upper left direction seen before)
            if (isCloudBuffer(cloudBufferAccu[jb][ib]) && isClear(pixelClassifFlags)) {
                pixelClassifFlags |= (1 << IDEPIX_CLOUD_BUFFER);
            }
            // write corrected pixel classif flags to accu
            cloudBufferAccu[jb][ib] = pixelClassifFlags;
        }
        // reset accu for re-use
        landAccu[jt][it] = false;
        waterAccu[jt][it] = false;
        clearNearbyAccu[jt][it] = false;
        c7[jt][it] = 0.0;
        m7[jt][it] = 0.0;
        c8[jt][it] = 0.0;
        m8[jt][it] = 0.0;
        n78[jt][it] = 0;
    }

    private void writeFlagsOfPixel(int yb, int xb, int[][] cloudBufferAccu, Rectangle targetRectangle, Tile targetTile) {
        // transfer accu to target
        final int jb = (yb - targetRectangle.y) % cloudBufferSize;
        final int ib = xb - targetRectangle.x;
        targetTile.setSample(xb, yb, cloudBufferAccu[jb][ib]);
        // clear accu for re-use
        cloudBufferAccu[jb][ib] = 0;
    }

    private static int coastalCloudDistinction(int yt, int xt, int jt, int it,
                                               boolean[][] landAccu, boolean[][] waterAccu,
                                               Tile sourceFlagTile, Tile b2Tile, Tile b8Tile, Tile b11Tile,
                                               int pixelClassifFlags) {
        // not land but there is some land nearby, or not water and some water nearby
        final boolean isCoastal =
                (!isLand(sourceFlagTile, yt, xt) && landAccu[jt][it]) ||
                (!isWater(sourceFlagTile, yt, xt) && waterAccu[jt][it]);
        if (isCoastal && isValid(pixelClassifFlags)) {
            // another cloud test
            final float b2 = b2Tile.getSampleFloat(xt, yt);
            final float b8 = b8Tile.getSampleFloat(xt, yt);
            final float b11 = b11Tile.getSampleFloat(xt, yt);
            final float idx1 = b2 / b11;
            final float idx2 = b8 / b11;
            //final boolean notCoastal = idx1 > 0.7f || (idx1 > 0.6f && idx2 > 0.9f);
            // handles NaN as non-coastal
            final boolean isCoastal2 = idx1 <= 0.6f || (idx1 <= 0.7f && idx2 <= 0.9f);
            if (isCoastal2) {
                // clear cloud flags if cloud test fails
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD_AMBIGUOUS, false);
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD_SURE, false);
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD, false);
            } else {
                // align cloud flag with combination of ambiguous and sure
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD, isAmbigousOrSure(pixelClassifFlags));
            }
        }
        return pixelClassifFlags;
    }

    private static int urbanCloudDistinction(int jt, int it,
                                             boolean[][] clearNearbyAccu,
                                             double[][] m7, double[][] m8, double[][] c7, double[][] c8, short[][] n78,
                                             int pixelClassifFlags) {
        // some clear pixels nearby, and not cirrus or water
        if (isCloud(pixelClassifFlags) && isNotCirrusNotWater(pixelClassifFlags) && clearNearbyAccu[jt][it]) {
            // another non-cloud test
            final double variance7 = variance_of(c7[jt][it], m7[jt][it], n78[jt][it]);
            final double variance8 = variance_of(c8[jt][it], m8[jt][it], n78[jt][it]);
            final double cdiValue = (variance7 - variance8) / (variance7 + variance8);
            if (cdiValue >= CDI_THRESHOLD) {
                // clear cloud flags if CDI test succeeds
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD_AMBIGUOUS, false);
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD_SURE, false);
                pixelClassifFlags = BitSetter.setFlag(pixelClassifFlags, IDEPIX_CLOUD, false);
            }
        }
        return pixelClassifFlags;
    }

    private void collectLand(int y, int x,
                                     Tile sourceFlagTile,
                                     Rectangle cloudBufferRectangle,
                                     boolean[][] landAccu) {
        if (isLand(sourceFlagTile, y, x)) {
            fillPatchInAccu(y, x, cloudBufferRectangle, landWaterContextRadius, landAccu);
        }
    }

    private void collectWater(int y, int x,
                                     Tile sourceFlagTile,
                                     Rectangle cloudBufferRectangle,
                                     boolean[][] waterAccu) {
        if (isWater(sourceFlagTile, y, x)) {
            fillPatchInAccu(y, x, cloudBufferRectangle, landWaterContextRadius, waterAccu);
        }
    }

    private void collectClear(int y, int x,
                              Tile sourceFlagTile,
                              Rectangle cloudBufferRectangle,
                              boolean[][] clearNearbyAccu) {
        if (isClear(sourceFlagTile, y, x)) {
            fillPatchInAccu(y, x, cloudBufferRectangle, urbanContextRadius, clearNearbyAccu);
        }
    }

    private void collectCdiSumsAndSquares(int y, int x,
                                          Tile b7Tile, Tile b8Tile, Tile b8aTile,
                                          Rectangle cdiRectangle, Rectangle cloudBufferRectangle,
                                          double[][] m7, double[][] c7, double[][] m8, double[][] c8, short[][] n78) {
        // determine nearest position inside complete image, COPY-extend image
        final int xt =
                x < cdiRectangle.x ? cdiRectangle.x
                        : x >= cdiRectangle.x + cdiRectangle.width ? cdiRectangle.x + cdiRectangle.width - 1
                        : x;
        final int yt =
                y < cdiRectangle.y ? cdiRectangle.y
                        : y >= cdiRectangle.y + cdiRectangle.height ? cdiRectangle.y + cdiRectangle.height - 1
                        : y;
        // determine values
        final float b7 = b7Tile.getSampleFloat(xt, yt);
        final float b8 = b8Tile.getSampleFloat(xt, yt);
        final float b8a = b8aTile.getSampleFloat(xt, yt);
        if (!Float.isNaN(b7) && !Float.isNaN(b8) && b8a != 0.0f) {
            final float b7b8a = b7 / b8a;
            final float b8b8a = b8 / b8a;
            addStddevPatchInAccu(y, x, cloudBufferRectangle, cdiStddevContextRadius,
                                 b7b8a, b8b8a,
                                 m7, c7, m8, c8, n78);
        }
    }

    private void fillPatchInAccu(int y, int x, Rectangle cloudBufferRectangle, int halfWidth, boolean[][] accu) {
        // reduce patch to part overlapping with target image
        final int jMin = Math.max(y - cloudBufferRectangle.y - halfWidth, 0);
        final int jMax = Math.min(y - cloudBufferRectangle.y + 1 + halfWidth, cloudBufferRectangle.height);
        final int iMin = Math.max(x - cloudBufferRectangle.x - halfWidth, 0);
        final int iMax = Math.min(x - cloudBufferRectangle.x + 1 + halfWidth, cloudBufferRectangle.width);
        // fill patch with value
        for (int j = jMin; j < jMax; ++j) {
            final int jj = j % contextSize;
            for (int i = iMin; i < iMax; ++i) {
                accu[jj][i] = true;
            }
        }
    }

    private void addStddevPatchInAccu(int y, int x, Rectangle cloudBufferRectangle, int halfWidth,
                                      float b7b8a, float b8b8a,
                                      double[][] m7, double[][] c7, double[][] m8, double[][] c8, short[][] n78) {
        // reduce patch to part overlapping with target image
        final int jMin = Math.max(y - cloudBufferRectangle.y - halfWidth, 0);
        final int jMax = Math.min(y - cloudBufferRectangle.y + 1 + halfWidth, cloudBufferRectangle.height);
        final int iMin = Math.max(x - cloudBufferRectangle.x - halfWidth, 0);
        final int iMax = Math.min(x - cloudBufferRectangle.x + 1 + halfWidth, cloudBufferRectangle.width);
        // add to mean and to sum of squares within patch
        final float b7b8a2 = b7b8a * b7b8a;
        final float b8b8a2 = b8b8a * b8b8a;
        for (int j = jMin; j < jMax; ++j) {
            final int jj = j % contextSize;
            for (int i = iMin; i < iMax; ++i) {
                m7[jj][i] += b7b8a;
                c7[jj][i] += b7b8a2;
                m8[jj][i] += b8b8a;
                c8[jj][i] += b8b8a2;
                n78[jj][i] += 1;
            }
        }
    }

    private void addCloudBufferInAccu(int y, int x, Rectangle targetRectangle, int halfWidth,
                                      int[][] cloudBufferAccu) {
        // reduce patch to part overlapping with target image
        final int jMin = Math.max(y - targetRectangle.y - halfWidth, 0);
        final int jMax = Math.min(y - targetRectangle.y + 1 + halfWidth, targetRectangle.height);
        final int iMin = Math.max(x - targetRectangle.x - halfWidth, 0);
        final int iMax = Math.min(x - targetRectangle.x + 1 + halfWidth, targetRectangle.width);
        for (int j = jMin; j < jMax; ++j) {
            final int jj = j % cloudBufferSize;
            for (int i = iMin; i < iMax; ++i) {
                if (isClear(cloudBufferAccu[jj][i])) {
                    cloudBufferAccu[jj][i] |= (1 << IDEPIX_CLOUD_BUFFER);
                }
            }
        }
    }


    private RectangleExtender createRectCalculator(int width) {
        return new RectangleExtender(new Rectangle(classifiedProduct.getSceneRasterWidth(),
                                                   classifiedProduct.getSceneRasterHeight()),
                                     width, width);
    }

    private static Product createTargetProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        return targetProduct;
    }

    private static boolean isLand(Tile tile, int y, int x) {
        return tile.getSampleBit(x, y, IDEPIX_LAND);
    }

    private static boolean isWater(Tile tile, int y, int x) {
        return tile.getSampleBit(x, y, IDEPIX_WATER);
    }
    
    private static boolean isValid(int pixelClassifFlags) {
        return (pixelClassifFlags & 1 << IDEPIX_INVALID) == 0;
    }

    private static boolean isAmbigousOrSure(int pixelClassifFlags) {
        return (pixelClassifFlags & (1 << IDEPIX_CLOUD_AMBIGUOUS | 1 << IDEPIX_CLOUD_SURE)) != 0;
    }

    private static boolean isCloudBuffer(int pixelClassifFlags) {
        return (pixelClassifFlags & 1 << IDEPIX_CLOUD_BUFFER) != 0;
    }

    private static boolean isClear(int pixelClassifFlags) {
        return (pixelClassifFlags
                & (1 << IDEPIX_CLOUD_AMBIGUOUS | 1 << IDEPIX_CLOUD_SURE
                   | 1 << IDEPIX_CLOUD | 1 << IDEPIX_INVALID)) == 0;
    }

    private static boolean isCloud(int pixelClassifFlags) {
        return (pixelClassifFlags & (1 << IDEPIX_CLOUD_AMBIGUOUS | 1 << IDEPIX_CLOUD_SURE | 1 << IDEPIX_CLOUD)) != 0;
    }

    private static boolean isNotCirrusNotWater(int pixelClassifFlags) {
        return (pixelClassifFlags & (1 << IDEPIX_CIRRUS_AMBIGUOUS | 1 << IDEPIX_CIRRUS_SURE | 1 << IDEPIX_WATER)) == 0;
    }

    private static boolean isClear(Tile tile, int y, int x) {
        return (tile.getSampleInt(x, y)
                & (1 << IDEPIX_CLOUD_AMBIGUOUS | 1 << IDEPIX_CLOUD_SURE
                   | 1 << IDEPIX_CLOUD | 1 << IDEPIX_INVALID)) == 0;
    }    

    private boolean isCloudForBuffer(int pixelClassifFlags) {
        return 
                (pixelClassifFlags & 1 << IDEPIX_CLOUD_SURE) != 0 ||
                (computeCloudBufferForCloudAmbiguous && (pixelClassifFlags & 1 << IDEPIX_CLOUD_AMBIGUOUS) != 0);
    }

    private static double variance_of(double c, double m, int n) {
        //return n == 0 ? Double.NaN : n == 1 ? 0.0 : (c - m * m / n) / (n - 1);  // seems JNI uses different formula
        return n == 0 ? Double.NaN : n == 1 ? 0.0 : (c - m * m / n) / n;
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(S2IdepixCloudPostProcess2Op.class);
        }
    }
}
