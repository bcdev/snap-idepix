package org.esa.snap.idepix.avhrr;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.CloudShadowFronts;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.OperatorUtils;
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

import java.awt.*;

/**
 * Operator used to consolidate cloud flag for AVHRR AC:
 * - coastline refinement
 * - cloud buffer (LC algo as default)
 * - cloud shadow (from Fronts)
 *
 * @author olafd
 * @since IdePix 2.1
 */
@OperatorMetadata(alias = "Idepix.Avhrr.Postprocess",
                  version = "3.0",
                  internal = true,
                  authors = "Marco Peters, Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2016 by Brockmann Consult",
                  description = "Refines the AVHRR pixel classification.")
public class AvhrrPostProcessOp extends Operator {
    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "true", label = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    //    @Parameter(defaultValue = "true",
//            label = " Compute cloud shadow",
//            description = " Compute cloud shadow with latest 'fronts' algorithm")
    private boolean computeCloudShadow = false;   // todo: we have no info at all for this (pressure, height, temperature)

    @Parameter(defaultValue = "true",
               label = " Refine pixel classification near coastlines",
               description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "avhrrCloud")
    private Product avhrrCloudProduct;
    @SourceProduct(alias = "waterMask", optional=true)
    private Product waterMaskProduct;

    private Band landWaterBand;
    private Band origCloudFlagBand;
    private Band rt3Band;
    private Band bt4Band;
    private Band refl1Band;
    private Band refl2Band;
    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        if (!computeCloudBuffer && !computeCloudShadow && !refineClassificationNearCoastlines) {
            setTargetProduct(avhrrCloudProduct);
        } else {
            Product postProcessedCloudProduct = OperatorUtils.createCompatibleProduct(avhrrCloudProduct,
                                                                    "postProcessedCloud", "postProcessedCloud");

            // BEAM: todo reactivate this once we have our SRTM mask in SNAP
            landWaterBand = waterMaskProduct.getBand("land_water_fraction");

            // meanwhile use the 'Land-Sea-Mask' operator by Array (Jun Lu, Luis Veci):
//            landWaterBand = waterMaskProduct.getBand(AvhrrConstants.AVHRR_AC_ALBEDO_1_BAND_NAME);

            geoCoding = l1bProduct.getSceneGeoCoding();

            origCloudFlagBand = avhrrCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
            rt3Band = avhrrCloudProduct.getBand("rt_3");
            bt4Band = avhrrCloudProduct.getBand("bt_4");
            refl1Band = avhrrCloudProduct.getBand("refl_1");
            refl2Band = avhrrCloudProduct.getBand("refl_2");


            int extendedWidth = 64;
            int extendedHeight = 64; // todo: what do we need?

            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                                 l1bProduct.getSceneRasterHeight()),
                                                   extendedWidth, extendedHeight
            );

            ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, avhrrCloudProduct, postProcessedCloudProduct, false);
            setTargetProduct(postProcessedCloudProduct);
        }
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile waterFractionTile = getSourceTile(landWaterBand, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    combineFlags(x, y, sourceFlagTile, targetTile);

                    if (refineClassificationNearCoastlines) {
                        if (isNearCoastline(x, y, waterFractionTile, srcRectangle)) {
                            targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, true);
                            refineSnowIceFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
                            if (isCloud) {
                                refineCloudFlaggingForCoastlines(x, y, sourceFlagTile, waterFractionTile, targetTile, srcRectangle);
                            }
                        }
                    }
                    boolean isCloudAfterRefinement = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    if (isCloudAfterRefinement) {
                        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
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
            // todo: we need something modified, as we have no CTP
        }
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    private boolean isCoastlinePixel(int x, int y, Tile waterFractionTile) {
        boolean isCoastline = false;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (IdepixUtils.getGeoPos(geoCoding, x, y).lat > -58f) {
            final int waterFraction = waterFractionTile.getSampleInt(x, y);
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                isCoastline = waterFraction < 100 && waterFraction > 0;
            }
        }
        return isCoastline;
    }

    private boolean isNearCoastline(int x, int y, Tile waterFractionTile, Rectangle rectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, rectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, rectangle.x + rectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, rectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, rectangle.y + rectangle.height - 1);
        final int waterFractionCenter = waterFractionTile.getSampleInt(x, y);
        for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
            for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                if (rectangle.contains(i, j)) {
                    if (!(l1bProduct.getSceneGeoCoding() instanceof TiePointGeoCoding) &&
                            !(l1bProduct.getSceneGeoCoding() instanceof CrsGeoCoding)) {
                        if (waterFractionTile.getSampleInt(i, j) != waterFractionCenter) {
                            return true;
                        }
                    } else {
                        if (isCoastlinePixel(i, j, waterFractionTile)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void refineCloudFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile waterFractionTile, Tile targetTile, Rectangle srcRectangle) {
        final int windowWidth = 1;
        final int LEFT_BORDER = Math.max(x - windowWidth, srcRectangle.x);
        final int RIGHT_BORDER = Math.min(x + windowWidth, srcRectangle.x + srcRectangle.width - 1);
        final int TOP_BORDER = Math.max(y - windowWidth, srcRectangle.y);
        final int BOTTOM_BORDER = Math.min(y + windowWidth, srcRectangle.y + srcRectangle.height - 1);
        boolean removeCloudFlag = true;
        if (CloudShadowFronts.isPixelSurrounded(x, y, sourceFlagTile, IdepixConstants.IDEPIX_CLOUD)) {
            removeCloudFlag = false;
        } else {
            Rectangle targetTileRectangle = targetTile.getRectangle();
            for (int i = LEFT_BORDER; i <= RIGHT_BORDER; i++) {
                for (int j = TOP_BORDER; j <= BOTTOM_BORDER; j++) {
                    boolean is_cloud = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD);
                    if (is_cloud && targetTileRectangle.contains(i, j) && !isNearCoastline(i, j, waterFractionTile, srcRectangle)) {
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
        }
    }

    private void refineSnowIceFlaggingForCoastlines(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        final boolean isSnowIce = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_SNOW_ICE);
        if (isSnowIce) {
            targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
        }
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrPostProcessOp.class);
        }
    }
}
