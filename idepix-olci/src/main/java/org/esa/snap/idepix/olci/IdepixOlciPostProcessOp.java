package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGrid;
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

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "olciCloud")
    private Product olciCloudProduct;

    @SourceProduct(alias = "ctp", optional = true,
    description = "Must contain a band with the name 'ctp'.")
    private Product ctpProduct;

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
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(olciCloudProduct,
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
        }

        int cloudShadowExtent = l1bProduct.getName().contains("FR____") ? 64 : 16;
        int extent = computeCloudShadow ? cloudShadowExtent : computeCloudBuffer ? cloudBufferWidth : 0;
        rectExtender = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), extent, extent);

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
        final Rectangle srcRectangle = rectExtender.extend(targetRectangle);

        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);

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
