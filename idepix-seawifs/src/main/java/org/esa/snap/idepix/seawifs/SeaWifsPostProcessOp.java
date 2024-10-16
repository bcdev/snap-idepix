package org.esa.snap.idepix.seawifs;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGeoCoding;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;

/**
 * SeaWiFS post processing operator, operating on tiles:
 * - cloud buffer
 * - ...
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Seawifs.Postprocess",
        version = "3.0",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the SeaWiFS pixel classification.",
        internal = true)
public class SeaWifsPostProcessOp extends BasisOp {

    @SourceProduct(alias = "refl", description = "SeaWiFS reflectance product")
    private Product reflProduct;

    @SourceProduct(alias = "classif", description = "SeaWiFS pixel classification product")
    private Product classifProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with latest 'fronts' algorithm")
    private boolean computeCloudShadow;

    private RectangleExtender rectCalculator;

    private Band landWaterBand;

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();

        rectCalculator = new RectangleExtender(new Rectangle(reflProduct.getSceneRasterWidth(),
                                                             reflProduct.getSceneRasterHeight()),
                                               cloudBufferWidth, cloudBufferWidth
        );

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Band classifFlagSourceBand = classifProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle extendedRectangle = rectCalculator.extend(targetRectangle);
        final Tile classifFlagSourceTile = getSourceTile(classifFlagSourceBand, extendedRectangle);
        final Tile waterFractionTile = getSourceTile(landWaterBand, extendedRectangle);

        for (int y = extendedRectangle.y; y < extendedRectangle.y + extendedRectangle.height; y++) {
            checkForCancellation();
            for (int x = extendedRectangle.x; x < extendedRectangle.x + extendedRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = classifFlagSourceTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    combineFlags(x, y, classifFlagSourceTile, targetTile);

                    if (!(classifProduct.getSceneGeoCoding() instanceof TiePointGeoCoding) &&
                            !(classifProduct.getSceneGeoCoding() instanceof CrsGeoCoding)) {
                        // in this case, coastline could not be determined per pixel earlier
                        if (isCoastline(x, y, classifFlagSourceTile, targetRectangle)) {
                            targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, true);
                        }
                    }

                    if (isNearCoastline(x, y, targetTile, waterFractionTile, targetRectangle)) {
                        refineSnowIceFlaggingForCoastlines(x, y, classifFlagSourceTile, targetTile);
                        if (isCloud) {
                            refineCloudFlaggingForCoastlines(x, y, classifFlagSourceTile, waterFractionTile, targetTile, targetRectangle);
                        }
                    }
                }
            }
        }

        // cloud buffer:
        CloudBuffer.setCloudBuffer(targetTile, extendedRectangle, classifFlagSourceTile, cloudBufferWidth);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                IdepixUtils.consolidateCloudAndBuffer(targetTile, x, y);
            }
        }
    }

    private boolean isCoastline(int x, int y, Tile sourceFlagTile, Rectangle rectangle) {
        // idea:
        // - consider 3x3 box
        // - consider the 8 pixels surrounding center
        // - assume center pixel as coastline if it is land and number of surrounding land/water pixels is
        //   almost the same: either 3/5, 4/4, or 5/3
        // --> very simple approach, works fairly well except for weird land edges
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);

        final boolean isLandCenter = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
        if (isLandCenter) {
            int landCount = 0;
            int count = 0;
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    final boolean isCenter = (i == x && j == y);
                    final boolean isLand = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_LAND);
                    if (!isCenter) {
                        count++;
                    }
                    if (isLand && !isCenter) {
                        landCount++;
                    }
                }
            }
            // also consider reduced boxes at product edge
            return (count >= 4 && landCount >= Math.max(count-6, 1) && landCount <= count - 3);
        }

        return false;
    }

    private boolean isNearCoastline(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);

        if (!(classifProduct.getSceneGeoCoding() instanceof TiePointGeoCoding) &&
                !(classifProduct.getSceneGeoCoding() instanceof CrsGeoCoding)) {
            final int waterFractionCenter = waterFractionTile.getSampleInt(x, y);
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    if (rectangle.contains(i, j)) {
                        if (waterFractionTile.getSampleInt(i, j) != waterFractionCenter) {
                            return true;
                        }
                    }
                }
            }
        } else {
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    if (rectangle.contains(i, j)) {
                        boolean isAlreadyCoastline = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_COASTLINE);
                        if (isAlreadyCoastline) {
                            return true;
                        }
                    }
                }
            }
        }


        return false;
    }

    private boolean refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile waterFractionTile,
                                                     Tile targetTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        boolean removeCloudFlag = true;
        if (isPixelSurrounded(x, y, sourceFlagTile, rectangle)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, sourceFlagTile, waterFractionTile, rectangle)) {
                        removeCloudFlag = false;
                        break;
                    }
                }
            }
        }

        if (removeCloudFlag) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            boolean is_land = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
            targetTile.setSample(x, y, SeaWifsConstants.IDEPIX_MIXED_PIXEL, !is_land);
        }
        // return whether this is still a cloud
        return !removeCloudFlag;
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE);
        if (isSnowIce) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
        }
    }

    private boolean isPixelSurrounded(int x, int y, Tile sourceFlagTile, Rectangle targetRectangle) {
        // check if pixel is surrounded by other pixels flagged as 'pixelFlag'
        int surroundingPixelCount = 0;
        for (int i = x - 1; i <= x + 1; i++) {
            for (int j = y - 1; j <= y + 1; j++) {
                if (sourceFlagTile.getRectangle().contains(i, j)) {
                    boolean is_flagged = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD);
                    if (is_flagged && targetRectangle.contains(i, j)) {
                        surroundingPixelCount++;
                    }
                }
            }
        }

        return (surroundingPixelCount * 1.0 / 9 >= 0.7);  // at least 6 pixel in a 3x3 box
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(reflProduct, classifProduct.getName(), classifProduct.getProductType());
        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, classifProduct, targetProduct, false);

        ProductUtils.copyFlagBands(reflProduct, targetProduct, true);
        ProductUtils.copyFlagCodings(reflProduct, targetProduct);
        ProductUtils.copyFlagCodings(classifProduct, targetProduct);
        ProductUtils.copyGeoCoding(reflProduct, targetProduct);

        SeaWifsUtils.setupSeawifsClassifBitmask(targetProduct);
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
            super(SeaWifsPostProcessOp.class);
        }
    }

}
