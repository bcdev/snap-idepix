package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.*;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.RectangleExtender;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

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

    @Parameter(defaultValue = "false",
            label = " If cloud shadow is computed, write CTH value to the target product",
            description = " If cloud shadow is computed, write cloud top height value to the target product ")
    private boolean outputCth;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "olciCloud")
    private Product olciCloudProduct;

    @SourceProduct(alias = "ctp", optional = true,
    description = "Must contain a band with the name 'ctp'.")
    private Product ctpProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    private Band origCloudFlagBand;

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

    @Override
    public void initialize() throws OperatorException {
        targetProduct = IdepixIO.createCompatibleTargetProduct(olciCloudProduct,
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
            if (outputCth) {
                targetProduct.addBand(IdepixConstants.CTH_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
            }
        }

        int cloudShadowExtent = l1bProduct.getName().contains("FR____") ? 64 : 16;
        int extent = computeCloudShadow ? cloudShadowExtent : computeCloudBuffer ? cloudBufferWidth : 0;
        rectExtender = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), extent, extent);

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, olciCloudProduct, targetProduct, false);
        setTargetProduct(targetProduct);
    }

    private void ensureBandsAreCopied(Product source, Product target, String... bandNames) {
        for (String bandName : bandNames) {
            if (!target.containsBand(bandName)) {
                ProductUtils.copyBand(bandName, source, target, true);
            }
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        final Rectangle srcRectangle = rectExtender.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);
        final Tile postProcessedCloudTile = targetTiles.get(targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME));

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                if (targetRectangle.contains(x, y)) {
                    boolean isCloud = sourceFlagTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD);
                    combineFlags(x, y, sourceFlagTile, postProcessedCloudTile);
                    if (isCloud) {
                        postProcessedCloudTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);   // necessary??
                    }
                }
            }
        }

        if (computeCloudBuffer) {
            CloudBuffer.setCloudBuffer(postProcessedCloudTile, srcRectangle, sourceFlagTile, cloudBufferWidth);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    IdepixUtils.consolidateCloudAndBuffer(postProcessedCloudTile, x, y);
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
            if (outputCth) {
                Tile cthTile = targetTiles.get(targetProduct.getBand(IdepixConstants.CTH_OUTPUT_BAND_NAME));
                double[] temperature = new double[temperatureProfileTPGTiles.length];
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    checkForCancellation();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        final float ctp = ctpTile.getSampleFloat(x, y);
                        final float slp = slpTile.getSampleFloat(x, y);
                        for (int i = 0; i < temperature.length; i++) {
                            temperature[i] = temperatureProfileTPGTiles[i].getSampleDouble(x, y);
                        }
                        final float cloudHeight = (float) IdepixOlciUtils.getRefinedHeightFromCtp(ctp, slp, temperature);
                        cthTile.setSample(x, y, cloudHeight);
                    }
                }
            }

            cloudShadowFronts.computeCloudShadow(sourceFlagTile, postProcessedCloudTile);
        }

        if (computeMountainShadow) {
            final Tile mountainShadowFlagTile = getSourceTile(mountainShadowFlagBand, targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    final boolean mountainShadow = mountainShadowFlagTile.getSampleInt(x, y) > 0;
                    postProcessedCloudTile.setSample(x, y, IdepixOlciConstants.IDEPIX_MOUNTAIN_SHADOW, mountainShadow);
                }
            }
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
