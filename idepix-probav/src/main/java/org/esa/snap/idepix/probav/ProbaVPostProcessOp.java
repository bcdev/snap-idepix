package org.esa.snap.idepix.probav;

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
 * Operator used to consolidate IdePix classification flag for Proba-V, currently:
 * - basic flag consolidation following GK
 * - cloud shadow
 * - cloud buffer
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Probav.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the Proba-V pixel classification over both land and water.")
public class ProbaVPostProcessOp extends Operator {

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            label = " Width of cloud buffer (# of pixels)",
            description = " The width of the 'safety buffer' around a pixel identified as cloudy.")
    private int cloudBufferWidth;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "probavCloud")
    private Product probavCloudProduct;
    @SourceProduct(alias = "inlandWaterMaskCollocated", optional = true)
    private Product inlandWaterCollProduct;
    @SourceProduct(alias = "vitoCm", description = "Proba-V VITO cloud product.", optional = true)
    private Product vitoCloudProduct;

    @Parameter(defaultValue = "false",
            label = " Apply processing mode for C3S-Lot5 project",
            description = "If set, processing mode for C3S-Lot5 project is applied (uses specific tests)")
    private boolean isProcessingForC3SLot5;

    private Band origCloudFlagBand;
    private Band origSmFlagBand;
    private Band origInlandWaterFlagBand;
    private Band vitoCmBand;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        Product postProcessedCloudProduct = createTargetProduct(probavCloudProduct,
                "postProcessedCloud", "postProcessedCloud");

        origCloudFlagBand = probavCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        origSmFlagBand = l1bProduct.getBand("SM_FLAGS");
        if (inlandWaterCollProduct != null) {
            origInlandWaterFlagBand = inlandWaterCollProduct.getBand("InlandWaterMaskArea");
        }

        if (vitoCloudProduct != null && isProcessingForC3SLot5) {
            vitoCmBand = vitoCloudProduct.getBand("cloud_mask");
        }
        if (computeCloudBuffer) {
            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                    l1bProduct.getSceneRasterHeight()),
                    cloudBufferWidth, cloudBufferWidth
            );
        }

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, probavCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    private Product createTargetProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
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
        Tile vitoCmTile = null;
        if (vitoCloudProduct != null && isProcessingForC3SLot5) {
            vitoCmTile = getSourceTile(vitoCmBand, srcRectangle);
        }

        boolean idepixLand;
        int inlandWater;

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {

                boolean isInvalid = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);
                if (!isInvalid) {
                    combineFlags(x, y, cloudFlagTile, targetTile);

                    if (vitoCmTile != null && isProcessingForC3SLot5) {
                        consolidateFlaggingVitoCloudProduct(x, y, targetTile);
                    } else {
                        consolidateFlagging(x, y, smFlagTile, targetTile);
                    }
                    setCloudShadow(x, y, smFlagTile, targetTile);
                }

                if (inlandWaterCollProduct != null) {
                    idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
                    inlandWater = inlandWaterFlagTile.getSampleInt(x, y);
                    if (!idepixLand && (inlandWater == 1)) {
                        targetTile.setSample(x, y, ProbaVConstants.IDEPIX_INLAND_WATER, true);
                    }
                }
                consistencyCheck(x, y, targetTile);
                copyFlags(x, y, targetTile, cloudFlagTile);
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
        // as requested by JM, 20160302:
        final boolean smCloudShadow = smFlagTile.getSampleBit(x, y, ProbaVClassificationOp.SM_F_CLOUDSHADOW);
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

    private void copyFlags(int x, int y, Tile sourceFlagTile, Tile copyFlagTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        copyFlagTile.setSample(x, y, sourceFlags);
    }

    private void consolidateFlagging(int x, int y, Tile smFlagTile, Tile targetTile) {
        final boolean smClear = smFlagTile.getSampleBit(x, y, ProbaVClassificationOp.SM_F_CLEAR);
        final boolean idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
        final boolean idepixClearLand = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND);
        final boolean idepixWater = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_WATER);
        final boolean idepixClearWater = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER);
        final boolean idepixClearSnow = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE);
        final boolean idepixCloud = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        final boolean idepixInvalid = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);

        final boolean safeClearLand = smClear && idepixLand && idepixClearLand && !idepixClearSnow;
        final boolean safeClearWater = smClear && idepixWater && idepixClearWater && !idepixClearSnow;
        final boolean potentialCloudSnow = (!safeClearLand && idepixLand) || (!safeClearWater && idepixWater);
        final boolean safeSnowIce = potentialCloudSnow && idepixClearSnow;
        // GK 20151201;
        final boolean smCloud = smFlagTile.getSampleBit(x, y, ProbaVClassificationOp.SM_F_CLOUD);
        final boolean safeCloud = idepixCloud || (potentialCloudSnow && (!safeSnowIce && !safeClearWater));
        final boolean safeClearWaterFinal = (((!safeClearLand && !safeSnowIce && !safeCloud && !smCloud) && idepixWater) || safeClearWater) && !idepixInvalid;
        final boolean safeClearLandFinal = (((!safeSnowIce && !idepixCloud && !smCloud && !safeClearWaterFinal) && idepixLand) || safeClearLand) && !idepixInvalid;
        final boolean safeCloudFinal = safeCloud && (!safeClearLandFinal && !safeClearWaterFinal) && !idepixInvalid;
        ;

        // GK 20151201;
        targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, safeClearLandFinal);
        targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, safeClearWaterFinal);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, safeCloudFinal);
        if (safeCloudFinal) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
        }
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, safeSnowIce);

    }

    private void consolidateFlaggingVitoCloudProduct(int x, int y, Tile targetTile) {
        final boolean idepixLand = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_LAND);
        final boolean idepixClearLand = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND);
        final boolean idepixWater = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_WATER);
        final boolean idepixClearWater = targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER);
        final boolean idepixClearSnow = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE);
        final boolean idepixCloud = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
        final boolean idepixCloudAmbiguous = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS);
        final boolean idepixInvalid = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID);


        final boolean safeClearLand = idepixLand && idepixClearLand && !idepixClearSnow;
        final boolean safeClearWater = idepixWater && idepixClearWater && !idepixClearSnow;
        final boolean potentialCloudSnow = (!safeClearLand && idepixLand && !idepixInvalid) || (!safeClearWater && idepixWater && !idepixInvalid);
        final boolean safeSnowIce = potentialCloudSnow && idepixClearSnow;
        final boolean safeCloud = idepixCloud || (potentialCloudSnow && !safeSnowIce && !safeClearWater && !safeClearLand && !idepixInvalid);
        final boolean safeClearWaterFinal = safeClearWater || (!safeClearLand && !safeSnowIce && !safeCloud && idepixWater && !idepixInvalid);
        final boolean safeClearLandFinal = safeClearLand || (!safeClearWaterFinal && !safeSnowIce && !safeCloud && idepixLand && !idepixInvalid);
        final boolean safeCloudFinal = safeCloud && (!safeClearLandFinal && !safeClearWaterFinal && !safeSnowIce && !idepixInvalid);

        targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, safeClearLandFinal);
        targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, safeClearWaterFinal);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, safeCloudFinal);
        if (safeCloudFinal) {
            if (idepixCloudAmbiguous) {
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
            } else {
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            }
        }
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, safeSnowIce);

    }

    private void consistencyCheck(int x, int y, Tile targetTile) {
        if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
        }
        if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_SURE)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
        }
        if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
        }
        if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
        }
        if (targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
        }
        if (targetTile.getSampleBit(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER)) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
            targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
        }
        if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD)) {
            if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_SURE)) {
                targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
            } else {
                if (targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS)) {
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                } else {
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                    targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_LAND, false);
                    targetTile.setSample(x, y, ProbaVConstants.IDEPIX_CLEAR_WATER, false);
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, false);
                }
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ProbaVPostProcessOp.class);
        }
    }
}
