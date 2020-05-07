package org.esa.snap.idepix.spotvgt;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixUtils;
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

import java.awt.*;

/**
 * Operator used to consolidate IdePix classification flag for VGT, currently:
 * - basic flag consolidation following GK
 * - cloud shadow
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Vgt.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the VGT pixel classification over both land and water.")
public class VgtPostProcessOp extends Operator {

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "vgtCloud")
    private Product vgtCloudProduct;
    @SourceProduct(alias = "inlandWaterMaskCollocated", optional = true)
    private Product inlandWaterCollProduct;


    private Band origCloudFlagBand;
    private Band origSmFlagBand;
    private Band origInlandWaterFlagBand;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        Product finalVgtCloudProduct = vgtCloudProduct;

        Product postProcessedCloudProduct = createTargetProduct(vgtCloudProduct
        );

        origCloudFlagBand = finalVgtCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        origSmFlagBand = l1bProduct.getBand("SM");
        if (inlandWaterCollProduct != null) {
            origInlandWaterFlagBand = inlandWaterCollProduct.getBand("InlandWaterMaskArea");
        }
        if (computeCloudBuffer) {
            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                    l1bProduct.getSceneRasterHeight()),
                    cloudBufferWidth, cloudBufferWidth
            );
        }

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, finalVgtCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    private Product createTargetProduct(Product sourceProduct) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product("postProcessedCloud", "postProcessedCloud", sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        return targetProduct;
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();

        Rectangle srcRectangle = targetRectangle;
        if (computeCloudBuffer) {
            srcRectangle = rectCalculator.extend(targetRectangle);
        }

        final Tile cloudFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile smFlagTile = getSourceTile(origSmFlagBand, srcRectangle);
        Tile inlandWaterFlagTile = null;
        if (inlandWaterCollProduct != null) {
            inlandWaterFlagTile = getSourceTile(origInlandWaterFlagBand, srcRectangle);
        }


        boolean idepixLand;
        int inlandWater;

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isInvalid = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);
                    if (!isInvalid) {
                        combineFlags(x, y, cloudFlagTile, targetTile);
                        consolidateFlagging(x, y, smFlagTile, targetTile);

                        setCloudShadow(x, y, smFlagTile, targetTile);
                    }
                    if (inlandWaterCollProduct != null) {
                        idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
                        inlandWater = inlandWaterFlagTile.getSampleInt(x, y);
                        if (!idepixLand && inlandWater == 1) {
                            targetTile.setSample(x, y, VgtConstants.IDEPIX_INLAND_WATER, true);
                        }
                    }
                }
            }
        }

        // cloud buffer:
        if (computeCloudBuffer) {
            CloudBuffer.setCloudBuffer(targetTile, srcRectangle, cloudFlagTile, cloudBufferWidth);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    IdepixUtils.consolidateCloudAndBuffer(targetTile, x, y);
                }
            }
        }
    }

    private void setCloudShadow(int x, int y, Tile smFlagTile, Tile targetTile) {
        final boolean smCloud1 = smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_1);
        final boolean smCloud2 = smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_2);
        final boolean smCloudShadow = smCloud1 && !smCloud2; // see mask definition in SPOT VGT reader

        final boolean safeCloudFinal = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        final boolean isLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);

        final boolean isCloudShadow = smCloudShadow && !safeCloudFinal && isLand;
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, isCloudShadow);
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private void consolidateFlagging(int x, int y, Tile smFlagTile, Tile targetTile) {
        final boolean smCloud1 = smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_1);
        final boolean smCloud2 = smFlagTile.getSampleBit(x, y, VgtClassificationOp.SM_F_CLOUD_2);
        final boolean smClear = !smCloud1 && !smCloud2; // see mask definition in SPOT VGT reader

        final boolean idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
        final boolean idepixClearLand = targetTile.getSampleBit(x, y, VgtConstants.IDEPIX_CLEAR_LAND);
        final boolean idepixWater = targetTile.getSampleBit(x, y, VgtConstants.IDEPIX_WATER);
        final boolean idepixClearWater = targetTile.getSampleBit(x, y, VgtConstants.IDEPIX_CLEAR_WATER);
        final boolean idepixClearSnow = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE);
        final boolean idepixCloud = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        final boolean idepixInvalid = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);

        final boolean safeClearLand = smClear && idepixLand && idepixClearLand && !idepixClearSnow;
        final boolean safeClearWater = smClear && idepixWater && idepixClearWater && !idepixClearSnow;
        final boolean potentialCloudSnow = !safeClearLand && idepixLand;
        final boolean safeSnowIce = potentialCloudSnow && idepixClearSnow;
        // GK 20151201;
        final boolean smCloud = smCloud1 && smCloud2;
        final boolean safeCloud = idepixCloud || (potentialCloudSnow && (!safeSnowIce && !safeClearWater));
        final boolean safeClearWaterFinal = (((!safeClearLand && !safeSnowIce && !safeCloud && !smCloud) && idepixWater) || safeClearWater) && !idepixInvalid;
        final boolean safeClearLandFinal = (((!safeSnowIce && !idepixCloud && !smCloud && !safeClearWaterFinal) && idepixLand) || safeClearLand) && !idepixInvalid;
        ;
        final boolean safeCloudFinal = safeCloud && (!safeClearLandFinal && !safeClearWaterFinal) && !idepixInvalid;


        // GK 20151201;
        targetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_LAND, safeClearLandFinal);
        targetTile.setSample(x, y, VgtConstants.IDEPIX_CLEAR_WATER, safeClearWaterFinal);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, safeCloudFinal);
        if (safeCloudFinal) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
        }
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, safeSnowIce);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(VgtPostProcessOp.class);
        }
    }
}
