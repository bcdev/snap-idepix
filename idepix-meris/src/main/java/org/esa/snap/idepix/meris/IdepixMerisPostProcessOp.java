package org.esa.snap.idepix.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.*;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.dataio.envisat.EnvisatConstants;
import org.esa.snap.idepix.core.CloudShadowFronts;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Operator used to consolidate IdePix classification flag for MERIS:
 * - coastline refinement
 * - cloud shadow (from Fronts)
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Meris.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Refines the MERIS pixel classification over both land and water.")
public class IdepixMerisPostProcessOp extends Operator {

    @Parameter(defaultValue = "true", label = " Compute mountain shadow")
    private boolean computeMountainShadow;

    @Parameter(label = " Extent of mountain shadow", defaultValue = "0.9", interval = "[0,1]",
            description = "Extent of mountain shadow detection")
    private double mntShadowExtent;

    @Parameter(defaultValue = "true",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with latest 'fronts' algorithm")
    private boolean computeCloudShadow;

    @Parameter(defaultValue = "true",
            label = " Refine pixel classification near coastlines",
            description = "Refine pixel classification near coastlines. ")
    private boolean refineClassificationNearCoastlines;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "merisCloud")
    private Product merisCloudProduct;
    @SourceProduct(alias = "ctp", optional = true)
    private Product ctpProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    private Band waterFractionBand;
    private Band origCloudFlagBand;
    private Band ctpBand;
    private TiePointGrid szaTpg;
    private TiePointGrid saaTpg;
    private TiePointGrid altTpg;
    private GeoCoding geoCoding;
    private Band mountainShadowFlagBand;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(merisCloudProduct,
                                                                                   "postProcessedCloud",
                                                                                   "postProcessedCloud",
                                                                                   true);

        waterFractionBand = waterMaskProduct.getBand("land_water_fraction");

        geoCoding = l1bProduct.getSceneGeoCoding();

        origCloudFlagBand = merisCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        szaTpg = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        saaTpg = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME);
        altTpg = l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME);

        final TiePointGrid latTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_LATITUDE_BAND_NAME);
        final TiePointGrid lonTpg = l1bProduct.getTiePointGrid(IdepixMerisConstants.MERIS_LONGITUDE_BAND_NAME);

        if (ctpProduct != null) {
            ctpBand = ctpProduct.getBand("cloud_top_press");
        }

        int extendedWidth;
        int extendedHeight;
        if (l1bProduct.getProductType().startsWith("MER_F")) {
            extendedWidth = 64;
            extendedHeight = 64;
        } else {
            extendedWidth = 16;
            extendedHeight = 16;
        }

        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                                                             l1bProduct.getSceneRasterHeight()),
                                               extendedWidth, extendedHeight
        );

        if (computeMountainShadow) {
            ensureBandsAreCopied(l1bProduct, merisCloudProduct, latTpg.getName(), lonTpg.getName(), altTpg.getName());
            Map<String, Object> mntShadowParams = new HashMap<>();
            mntShadowParams.put("mntShadowExtent", mntShadowExtent);

            HashMap<String, Product> input = new HashMap<>();
            input.put("l1b", l1bProduct);
            final Product mountainShadowProduct = GPF.createProduct(
                    OperatorSpi.getOperatorAlias(IdepixMerisMountainShadowOp.class), mntShadowParams, input);
            mountainShadowFlagBand = mountainShadowProduct.getBand(
                    IdepixMerisMountainShadowOp.MOUNTAIN_SHADOW_FLAG_BAND_NAME);
        }

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, merisCloudProduct, postProcessedCloudProduct, false);
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
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        Tile szaTile = getSourceTile(szaTpg, srcRectangle);
        Tile saaTile = getSourceTile(saaTpg, srcRectangle);
        Tile altTile = getSourceTile(altTpg, targetRectangle);
        Tile ctpTile =  (ctpBand != null) ? getSourceTile(ctpBand, srcRectangle) : null;
        Tile waterFractionTile = getSourceTile(waterFractionBand, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    combineFlags(x, y, sourceFlagTile, targetTile);

                    if (refineClassificationNearCoastlines) {
                        if (isNearCoastline(x, y, waterFractionTile, srcRectangle)) {
                            targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, true);
                            // this causes problems for 'coastlines' over frozen inland lakes (OD 20200421)
                            // todo: this is a conflict between master and CGLOPS
//                            refineSnowIceFlaggingForCoastlines(x, y, sourceFlagTile, targetTile);
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

        if (computeCloudShadow) {
            CloudShadowFronts cloudShadowFronts = new CloudShadowFronts(
                    geoCoding,
                    srcRectangle,
                    targetRectangle,
                    szaTile, saaTile, ctpTile, altTile) {

                @Override
                protected boolean isCloudForShadow(int x, int y) {
                    final boolean is_cloud_current;
                    if (!targetTile.getRectangle().contains(x, y)) {
                        is_cloud_current = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    } else {
                        is_cloud_current = targetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    }
                    if (is_cloud_current) {
                        return !isNearCoastline(x, y, waterFractionTile, srcRectangle);
                    }
                    return false;
                }

                @Override
                protected boolean isCloudFree(int x, int y) {
                    return !sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                }

                @Override
                protected boolean isSurroundedByCloud(int x, int y) {
                    return isPixelSurrounded(x, y, sourceFlagTile, IdepixConstants.IDEPIX_CLOUD);
                }

                @Override
                protected void setCloudShadow(int x, int y) {
                    targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, true);
                }
            };
            cloudShadowFronts.computeCloudShadow();
        }

        if (computeMountainShadow) {
            final Tile mountainShadowFlagTile = getSourceTile(mountainShadowFlagBand, targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    final boolean mountainShadow = mountainShadowFlagTile.getSampleInt(x, y) > 0;
                    targetTile.setSample(x, y, IdepixMerisConstants.IDEPIX_MOUNTAIN_SHADOW, mountainShadow);
                }
            }
        }
    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
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
                        GeoPos geoPos = IdepixUtils.getGeoPos(geoCoding, x, y);
                        if (IdepixMerisUtils.isCoastlinePixel(geoPos, waterFractionCenter)) {
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
            super(IdepixMerisPostProcessOp.class);
        }
    }
}
