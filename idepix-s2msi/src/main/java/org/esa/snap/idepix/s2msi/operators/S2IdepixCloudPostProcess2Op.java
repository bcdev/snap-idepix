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
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.awt.Rectangle;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.*;

/**
 * Adds a cloud buffer to cloudy pixels and does a cloud discrimination on urban areas.
 */
@OperatorMetadata(alias = "Idepix.S2CloudPostProcess2",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne, Martin Boettcher",
        copyright = "(c) 2016-2023 by Brockmann Consult",
        description = "Performs post processing of cloudy pixels and adds optional cloud buffer.")
public class S2IdepixCloudPostProcess2Op extends Operator {

    private static final int IDEPIX_CLOUD_AMBIGUOUS_BIT = 1 << IDEPIX_CLOUD_AMBIGUOUS;
    private static final int IDEPIX_CLOUD_SURE_BIT = 1 << IDEPIX_CLOUD_SURE;
    private static final int IDEPIX_CLOUD_BIT = 1 << IDEPIX_CLOUD;
    private static final int IDEPIX_CIRRUS_AMBIGUOUS_BIT = 1 << IDEPIX_CIRRUS_AMBIGUOUS;
    private static final int IDEPIX_CIRRUS_SURE_BIT = 1 << IDEPIX_CIRRUS_SURE;
    private static final int IDEPIX_WATER_BIT = 1 << IDEPIX_WATER;

    @SourceProduct(alias = "classifiedProduct")
    private Product classifiedProduct;

    @Parameter(defaultValue = "true", label = "Compute cloud buffer for cloud ambiguous pixels too.")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    private int landWaterContextSize;
    private int urbanContextSize = 11;
    private int cdiStddevContextSize = 7;
    private int contextSize;
    private int cloudBufferSize;
    
    private int landWaterContextRadius;
    private int urbanContextRadius = urbanContextSize / 2;
    private int cdiStddevContextRadius = cdiStddevContextSize / 2;
    private int contextRadius;

    private static final float COAST_BUFFER_SIZE = 1000f;
    private Band origClassifFlagBand;
    private Band b2Band;
    private Band b7Band;
    private Band b8Band;
    private Band b8aBand;
    private Band b11Band;
    private RectangleExtender pixelStateRectCalculator;
    private RectangleExtender urbanRectCalculator;
    private RectangleExtender cdiRectCalculator;
    private RectangleExtender contextRectCalculator;
    private RectangleExtender cloudBufferRectCalculator;

    private static final double CDI_THRESHOLD = -0.5;

    @Override
    public void initialize() throws OperatorException {

        final int resolution = S2IdepixUtils.determineResolution(classifiedProduct);
        landWaterContextSize = (int) Math.floor((2 * COAST_BUFFER_SIZE) / resolution);   // TODO is a +1 required here?
        landWaterContextRadius = landWaterContextSize / 2;
        contextSize = Math.max(landWaterContextSize, Math.max(urbanContextSize, cdiStddevContextSize));
        contextRadius = contextSize / 2;
        cloudBufferSize = 2 * cloudBufferWidth + 1;
        
        pixelStateRectCalculator = createRectCalculator(contextSize/2);
        urbanRectCalculator = createRectCalculator(urbanContextSize/2);
        cdiRectCalculator = createRectCalculator(cdiStddevContextSize/2);
        contextRectCalculator = createRectCalculator(contextSize/2);
        cloudBufferRectCalculator = createRectCalculator(cloudBufferWidth);

        Product cloudBufferProduct = createTargetProduct(classifiedProduct,
                classifiedProduct.getName(),
                classifiedProduct.getProductType());
        origClassifFlagBand = classifiedProduct.getBand(IDEPIX_CLASSIF_FLAGS);
        ProductUtils.copyBand(IDEPIX_CLASSIF_FLAGS, classifiedProduct, cloudBufferProduct, false);
        b2Band = classifiedProduct.getBand("B2");
        b7Band = classifiedProduct.getBand("B7");
        b8Band = classifiedProduct.getBand("B8");
        b8aBand = classifiedProduct.getBand("B8A");
        b11Band = classifiedProduct.getBand("B11");

        setTargetProduct(cloudBufferProduct);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle pixelStateRectangle = pixelStateRectCalculator.extend(targetRectangle);
        final Rectangle urbanRectangle = urbanRectCalculator.extend(targetRectangle);
        final Rectangle cdiRectangle = cdiRectCalculator.extend(targetRectangle);
        final Rectangle contextRectangle = contextRectCalculator.extend(targetRectangle);
        final Rectangle cloudBufferRectangle = cloudBufferRectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origClassifFlagBand, pixelStateRectangle);
        final Tile b7Tile = getSourceTile(b7Band, cdiRectangle);
        final Tile b8Tile = getSourceTile(b8Band, cdiRectangle);
        final Tile b8aTile = getSourceTile(b8aBand, cdiRectangle);
        final Tile b2Tile = getSourceTile(b2Band, targetRectangle);
        final Tile b11Tile = getSourceTile(b11Band, targetRectangle);

        correctCoastalAndUrbanClouds(contextRectangle, pixelStateRectangle, urbanRectangle, cdiRectangle, targetRectangle, 
                                     b7Tile, b2Tile, b8Tile, b8aTile, b11Tile, sourceFlagTile, targetTile);

        // cloud buffer is based on the corrected clouds
        addCloudBuffer(targetRectangle, cloudBufferRectangle, sourceFlagTile, targetTile);
    }

    private void correctCoastalAndUrbanClouds(Rectangle contextRectangle, Rectangle pixelStateRectangle, Rectangle urbanRectangle, Rectangle cdiRectangle, Rectangle targetRectangle, Tile b7Tile, Tile b2Tile, Tile b8Tile, Tile b8aTile, Tile b11Tile, Tile sourceFlagTile, Tile targetTile) {
        // allocate and initialise some lines of full width to collect pixel contributions
        // The lines will be re-used for later image lines in a rolling manner.
        final boolean[][] landAccu = new boolean[contextSize][targetRectangle.width];
        final boolean[][] waterAccu = new boolean[contextSize][targetRectangle.width];
        final boolean[][] urbanSomeClearAccu = new boolean[contextSize][targetRectangle.width];
        final double m7[][] = new double[contextSize][targetRectangle.width];
        final double c7[][] = new double[contextSize][targetRectangle.width];
        final double m8[][] = new double[contextSize][targetRectangle.width];
        final double c8[][] = new double[contextSize][targetRectangle.width];
        final short n78[][] = new short[contextSize][targetRectangle.width];

        // loop over extended source tile
        for (int y = contextRectangle.y; y < contextRectangle.y + contextRectangle.height; y++) {
            checkForCancellation();
            for (int x = contextRectangle.x; x < contextRectangle.x + contextRectangle.width; x++) {

                // write dilated land and water patch into accu, required for coastal cloud distinction
                if (pixelStateRectangle.contains(x, y)) {  
                    if (isLand(sourceFlagTile, y, x)) {
                        fillPatchInAccu(y, x, targetRectangle, landWaterContextRadius, contextSize, true, landAccu);
                    }
                    if (isWater(sourceFlagTile, y, x)) {
                        fillPatchInAccu(y, x, targetRectangle, landWaterContextRadius, contextSize, true, waterAccu);
                    }
                }

                // write 11x11 "filtered" non-cloud patch into accu, required for urban cloud distinction
                if (urbanRectangle.contains(x, y)) {
                    if (!isCloud(sourceFlagTile, y, x)) {
                        fillPatchInAccu(y, x, targetRectangle, urbanContextRadius, contextSize, true, urbanSomeClearAccu);
                    }
                }

                // add stddev contributions to accu of sums, squares, counts, required for urban cloud distinction
                if (cdiRectangle.contains(x, y)) {
                    final float b7 = b7Tile.getSampleFloat(x, y);
                    final float b8 = b8Tile.getSampleFloat(x, y);
                    final float b8a = b8aTile.getSampleFloat(x, y);
                    if (!Float.isNaN(b7) && !Float.isNaN(b8) && b8a != 0.0f) {
                        final float b7b8a = b7 / b8a;
                        final float b8b8a = b8 / b8a;
                        final float b7b8a2 = b7b8a * b7b8a;
                        final float b8b8a2 = b8b8a * b8b8a;
                        addStddevPatchInAccu(y, x, targetRectangle, cdiStddevContextRadius, b8b8a, b7b8a2, b8b8a2, b7b8a, m7, c7, m8, c8, n78);
                    }
                }

                // evaluate accu if we have enough context
                // target pixel yt/xt
                final int yt = y - contextRadius;
                final int xt = x - contextRadius;
                // accu position jt/it
                final int jt = (yt - targetRectangle.y) % contextSize;
                final int it = xt - targetRectangle.x;
                if (targetRectangle.contains(xt, yt)) {
                    // read pixel classif flags from source, apply corrections, write it to target
                    int pixelClassifFlags = sourceFlagTile.getSampleInt(xt, yt);
                    pixelClassifFlags = coastalCloudDistinction(yt, xt, jt, it, landAccu, waterAccu, sourceFlagTile, b2Tile, b8Tile, b11Tile, pixelClassifFlags);
                    pixelClassifFlags = urbanCloudDistinction(jt, it, urbanSomeClearAccu, m7, m8, c7, c8, n78, pixelClassifFlags);
                    targetTile.setSample(xt, yt, pixelClassifFlags);
                }
            }
        }
    }

    private void fillPatchInAccu(int y, int x, Rectangle targetRectangle, int halfWidth, int contextSize, boolean value, boolean[][] accu) {
        // reduce patch to part overlapping with target image
        final int jMin = Math.max(y - targetRectangle.y - halfWidth, 0);
        final int jMax = Math.min(y - targetRectangle.y + 1 + halfWidth, targetRectangle.height);
        final int iMin = Math.max(x - targetRectangle.x - halfWidth, 0);
        final int iMax = Math.min(x - targetRectangle.x + 1 + halfWidth, targetRectangle.width);
        // fill patch with value
        for (int j = jMin; j < jMax; ++j) {
            final int jj = j % contextSize;
            for (int i = iMin; i < iMax; ++i) {
                accu[jj][i] = value;
            }
        }
    }

    private void addStddevPatchInAccu(int y, int x, Rectangle targetRectangle, int halfWidth,
                                      float b8b8a, float b7b8a2, float b8b8a2, float b7b8a, 
                                      double[][] m7, double[][] c7, double[][] m8, double[][] c8, short[][] n78) {
        // reduce patch to part overlapping with target image
        final int jMin = Math.max(y - targetRectangle.y - halfWidth, 0);
        final int jMax = Math.min(y - targetRectangle.y + 1 + halfWidth, targetRectangle.height);
        final int iMin = Math.max(x - targetRectangle.x - halfWidth, 0);
        final int iMax = Math.min(x - targetRectangle.x + 1 + halfWidth, targetRectangle.width);
        // add to mean and to sum of squares within patch
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

    private static int coastalCloudDistinction(int yt, int xt, int jt, int it,
                                               boolean[][] landAccu, boolean[][] waterAccu,
                                               Tile sourceFlagTile, Tile b2Tile, Tile b8Tile, Tile b11Tile,
                                               int pixelClassifFlags) {
        // not land but there is some land nearby, or not water and some water nearby
        final boolean isCoastal =
                (!isLand(sourceFlagTile, yt, xt) && landAccu[jt][it]) ||
                (!isWater(sourceFlagTile, yt, xt) && waterAccu[jt][it]);
        if (isCoastal) {
            // another cloud test
            final float b2 = b2Tile.getSampleFloat(xt, yt);
            final float b8 = b8Tile.getSampleFloat(xt, yt);
            final float b11 = b11Tile.getSampleFloat(xt, yt);
            final float idx1 = b2 / b11;
            final float idx2 = b8 / b11;
            final boolean notCoastalCloud = idx1 > 0.7f || (idx1 > 0.6f && idx2 > 0.9f);
            if (notCoastalCloud) {
                // clear cloud flags if cloud test fails
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD_AMBIGUOUS);
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD_SURE);
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD);
            } else if ((pixelClassifFlags & ((1 << IDEPIX_CLOUD_AMBIGUOUS) | (1 << IDEPIX_CLOUD_SURE))) == 0) {
                // align cloud flag with combination of ambiguous and sure if none of them is set
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD);
            } else {
                // align cloud flag with combination of ambiguous and sure if one of them is set
                pixelClassifFlags |= IDEPIX_CLOUD_BIT;
            }
        }
        // reset accu for re-use
        landAccu[jt][it] = false;
        waterAccu[jt][it] = false;
        return pixelClassifFlags;
    }

    private static int urbanCloudDistinction(int jt, int it, boolean[][] urbanSomeClearAccu, double[][] m7, double[][] m8, double[][] c7, double[][] c8, short[][] n78, int pixelClassifFlags) {
        // some clear pixels nearby, and not cirrus or water
        final boolean notInTheMiddleOfACloud = urbanSomeClearAccu[jt][it];
        if (notInTheMiddleOfACloud
                && (pixelClassifFlags & (IDEPIX_CIRRUS_AMBIGUOUS_BIT | IDEPIX_CIRRUS_SURE_BIT | IDEPIX_WATER_BIT)) == 0
                && (pixelClassifFlags & (IDEPIX_CLOUD_AMBIGUOUS_BIT | IDEPIX_CLOUD_SURE_BIT | IDEPIX_CLOUD_BIT)) != 0) {
            // another non-cloud test
            final double variance7 = variance_of(c7[jt][it], m7[jt][it], n78[jt][it]);
            final double variance8 = variance_of(c8[jt][it], m8[jt][it], n78[jt][it]);
            final double cdiValue = (variance7 - variance8) / (variance7 + variance8);
            if (cdiValue >= CDI_THRESHOLD) {
                // clear cloud flags if CDI test succeeds
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD_AMBIGUOUS);
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD_SURE);
                pixelClassifFlags = removeBit(pixelClassifFlags, IDEPIX_CLOUD);
            }
        }
        // clear accu for re-use
        urbanSomeClearAccu[jt][it] = false;
        return pixelClassifFlags;
    }

    private void addCloudBuffer(Rectangle targetRectangle, Rectangle cloudBufferRectangle, Tile sourceFlagTile, Tile targetTile) {
        final boolean[][] cloudBufferAccu = new boolean[cloudBufferSize][targetRectangle.width];
        for (int y = cloudBufferRectangle.y; y < cloudBufferRectangle.y + cloudBufferRectangle.height; y++) {
            checkForCancellation();
            for (int x = cloudBufferRectangle.x; x < cloudBufferRectangle.x + cloudBufferRectangle.width; x++) {
                if (isCloudForBuffer(targetRectangle.contains(x, y) ? targetTile : sourceFlagTile, x, y)) {
                    fillPatchInAccu(y, x, targetRectangle, cloudBufferWidth, cloudBufferSize, true, cloudBufferAccu);
                }
                // target pixel yt/xt
                final int yt = y - cloudBufferWidth;
                final int xt = x - cloudBufferWidth;
                // accu position jt/it
                final int jt = (yt - targetRectangle.y) % cloudBufferSize;
                final int it = xt - targetRectangle.x;
                // evaluate accu if we have enough context
                // set cloud buffer flag if it is not cloud but there is a cloud nearby
                if (targetRectangle.contains(xt, yt)
                        && cloudBufferAccu[jt][it]
                        && (targetTile.getSampleInt(xt, yt) & ((1 << IDEPIX_CLOUD_AMBIGUOUS) | (1 << IDEPIX_CLOUD_SURE) | (1 << IDEPIX_CLOUD))) == 0) {
                    targetTile.setSample(xt, yt, IDEPIX_CLOUD_BUFFER, true);
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

    private static int removeBit(int x, int i) {
        final int mask = -1 << i;
        return ((x ^ (x >>> 1)) & mask) ^ x;
    }

    private static boolean isLand(Tile tile, int y, int x) {
        return tile.getSampleBit(x, y, IDEPIX_LAND);
    }

    private static boolean isWater(Tile tile, int y, int x) {
        return tile.getSampleBit(x, y, IDEPIX_WATER);
    }

    private static boolean isCloud(Tile tile, int y, int x) {
        return (tile.getSampleInt(x, y) & ((1 << IDEPIX_CLOUD_AMBIGUOUS) | (1 << IDEPIX_CLOUD_SURE) | (1 << IDEPIX_CLOUD))) != 0;
    }

    private static double variance_of(double c, double m, int n) {
        return n == 0 ? Double.NaN : n == 1 ? 0.0 : (c - m * m / n) / (n - 1);
    }

    private boolean isCloudForBuffer(Tile targetTile, int x, int y) {
        return targetTile.getSampleBit(x, y, IDEPIX_CLOUD_SURE) ||
                (computeCloudBufferForCloudAmbiguous && targetTile.getSampleBit(x, y, IDEPIX_CLOUD_AMBIGUOUS));
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(S2IdepixCloudPostProcess2Op.class);
        }
    }
}
