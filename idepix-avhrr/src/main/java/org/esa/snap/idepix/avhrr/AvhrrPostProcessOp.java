package org.esa.snap.idepix.avhrr;

import com.bc.ceres.core.ProgressMonitor;
import com.google.common.primitives.Doubles;
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
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.OperatorUtils;

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

    private static final double RUTCLR_THRESH = 9.0;   // %
    private static final double TUTCLR_THRESH = 3.0;  // Kelvin

    private boolean computeCloudShadow = false;   // todo: we have no info for this (pressure, height, temperature)

    @Parameter(defaultValue = "false",
            label = " Apply spatial uniformity tests",
            description = "Apply spatial uniformity tests (actually works for AVHRR TIMELINE products only) . ")
    private boolean applyUniformityTests;


    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "avhrrCloud")
    private Product avhrrCloudProduct;
    @SourceProduct(alias = "waterMask", optional = true)
    private Product waterMaskProduct;

    private Band landWaterBand;
    private Band origCloudFlagBand;
    private Band reflCh1Band;
    private Band btCh4Band;

    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {

        if (!computeCloudShadow) {
            setTargetProduct(avhrrCloudProduct);
        } else {
            Product postProcessedCloudProduct = OperatorUtils.createCompatibleProduct(avhrrCloudProduct,
                                                                                      "postProcessedCloud", "postProcessedCloud");

            geoCoding = l1bProduct.getSceneGeoCoding();

            origCloudFlagBand = avhrrCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);

            reflCh1Band = l1bProduct.getBand("avhrr_b1");
            btCh4Band = l1bProduct.getBand("avhrr_b4");

            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                                 l1bProduct.getSceneRasterHeight()),
                                                   1, 1);

            ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, avhrrCloudProduct, postProcessedCloudProduct, false);
            setTargetProduct(postProcessedCloudProduct);
        }
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);

        final Tile reflCh1Tile = applyUniformityTests ? getSourceTile(reflCh1Band, srcRectangle) : null;
        final Tile btCh4Tile = applyUniformityTests ? getSourceTile(btCh4Band, srcRectangle) : null;

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                combineFlags(x, y, sourceFlagTile, targetTile);
            }
        }

        if (applyUniformityTests) {
            applyUniformityTest(targetTile, srcRectangle, sourceFlagTile, btCh4Tile, TUTCLR_THRESH);
            applyUniformityTest(targetTile, srcRectangle, sourceFlagTile, reflCh1Tile, RUTCLR_THRESH);
        }

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                boolean isCloud = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                if (isCloud) {
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                }
            }
        }
    }

    private void applyUniformityTest(Tile targetTile, Rectangle srcRectangle,
                                     Tile sourceFlagTile, Tile spectralTile, double spectralThresh) {

        // walk over tile in steps of 2 pixels:
        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y += 2) {
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x += 2) {
                final int RIGHT_BORDER = Math.min(x + 1, srcRectangle.x + srcRectangle.width - 1);
                int BOTTOM_BORDER = Math.min(y + 1, srcRectangle.y + srcRectangle.height - 1);

                // determine the land pixels in a 2x2 window
                boolean[] isLand2x2 = new boolean[4];
                int index = 0;
                for (int i = x; i <= RIGHT_BORDER; i++) {
                    for (int j = y; j <= BOTTOM_BORDER; j++) {
                        isLand2x2[index++] = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_LAND);
                    }
                }

                // if ALL pixels are land in the 2x2 window, apply uniformity test:
                if (isLand2x2[0] && isLand2x2[1] && isLand2x2[2] && isLand2x2[3]) {
                    // determine 2x2 min and max of incoming refl1 (RUT test) or bt4 (TUT test):
                    double[] spectralValues = new double[4];
                    index = 0;
                    for (int i = x; i <= RIGHT_BORDER; i++) {
                        for (int j = y; j <= BOTTOM_BORDER; j++) {
                            spectralValues[index++] = spectralTile.getSampleDouble(i, j);
                        }
                    }
                    final double utClrMin = Doubles.min(spectralValues);
                    final double utClrMax = Doubles.max(spectralValues);
                    final boolean isUtClr = utClrMax - utClrMin < spectralThresh;

                    if (isUtClr) {
                        // if uniformity is given, set all 2x2 pixels as clear:
                        for (int i = x; i <= RIGHT_BORDER; i++) {
                            for (int j = y; j <= BOTTOM_BORDER; j++) {
                                targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD, false);
                                targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                                targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                            }
                        }
                    } else {
                        // if one of the 4 pixels is cloudy, set all 2x2 pixels as cloud (sure)
                        boolean[] isCloud2x2 = new boolean[4];
                        index = 0;
                        for (int i = x; i <= RIGHT_BORDER; i++) {
                            for (int j = y; j <= BOTTOM_BORDER; j++) {
                                isCloud2x2[index++] = sourceFlagTile.getSampleBit(i, j, IdepixConstants.IDEPIX_CLOUD);
                            }
                        }
                        if (isCloud2x2[0] || isCloud2x2[1] || isCloud2x2[2] || isCloud2x2[3]) {
                            for (int i = x; i <= RIGHT_BORDER; i++) {
                                for (int j = y; j <= BOTTOM_BORDER; j++) {
                                    targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD, true);
                                    targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD_SURE, true);
                                    targetTile.setSample(i, j, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                                }
                            }

                        }
                    }
                }
            }
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
        if (getGeoPos(x, y).lat > -58f) {
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

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
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
