package org.esa.snap.idepix.olcislstr;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.CloudBuffer;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;

import java.awt.*;

/**
 * Post-processes the OLCI/SLSTR pixel classification. Actually just adds cloud buffer.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.OlciSlstr.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2017 by Brockmann Consult",
        description = "Post-processes the OLCI/SLSTR pixel classification. Actually just adds cloud buffer.")
public class OlciSlstrPostProcessOp extends Operator {

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer",
            description = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "false",
            label = " Compute cloud shadow",
            description = " Compute cloud shadow with latest 'fronts' algorithm. Requires CTP.")
    private boolean computeCloudShadow;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "olciSlstrCloud")
    private Product olciSlstrCloudProduct;

    @SourceProduct(alias = "ctp", optional = true)
    private Product ctpProduct;

    private Band origCloudFlagBand;

    private Band ctpBand;
    private RasterDataNode szaTPG;
    private RasterDataNode saaTPG;
    private RasterDataNode ozaTPG;
    private RasterDataNode oaaTPG;
    private RasterDataNode slpTPG;
    private RasterDataNode[] temperatureProfileTPGs;
    private Band altBand;

    private GeoCoding geoCoding;

    private RectangleExtender rectCalculator;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(olciSlstrCloudProduct,
                                                                                   "postProcessedCloud",
                                                                                   "postProcessedCloud",
                                                                                   true);

        geoCoding = l1bProduct.getSceneGeoCoding();

        origCloudFlagBand = olciSlstrCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);

        szaTPG = l1bProduct.getRasterDataNode("SZA");
        saaTPG = l1bProduct.getRasterDataNode("SAA");
        ozaTPG = l1bProduct.getRasterDataNode("OZA");
        oaaTPG = l1bProduct.getRasterDataNode("OAA");
        slpTPG = l1bProduct.getRasterDataNode("sea_level_pressure");

        altBand = l1bProduct.getBand(OlciSlstrConstants.OLCI_ALTITUDE_BAND_NAME);

        temperatureProfileTPGs = new RasterDataNode[OlciSlstrConstants.referencePressureLevels.length];
        for (int i = 0; i < OlciSlstrConstants.referencePressureLevels.length; i++) {
            temperatureProfileTPGs[i] =
                    l1bProduct.getRasterDataNode("atmospheric_temperature_profile_pressure_level_" + (i + 1));
        }


        rectCalculator = new RectangleExtender(new Rectangle(olciSlstrCloudProduct.getSceneRasterWidth(),
                                                             olciSlstrCloudProduct.getSceneRasterHeight()),
                                               cloudBufferWidth, cloudBufferWidth
        );

        if (computeCloudShadow && ctpProduct != null) {
            ctpBand = ctpProduct.getBand("ctp");
            int extendedWidth;
            int extendedHeight;
            if (l1bProduct.getName().contains("FR____")) {
                extendedWidth = 64;     // todo: check these values
                extendedHeight = 64;
            } else {
                extendedWidth = 16;
                extendedHeight = 16;
            }
            rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                    l1bProduct.getSceneRasterHeight()),
                    extendedWidth, extendedHeight
            );
        }

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, olciSlstrCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
    }

    @Override
    public void computeTile(Band targetBand, final Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);
        final Tile sourceFlagTile = getSourceTile(origCloudFlagBand, srcRectangle);

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                combineFlags(x, y, sourceFlagTile, targetTile);
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

        if (computeCloudShadow && ctpProduct != null) {
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
            OlciSlstrCloudShadowFronts cloudShadowFronts = new OlciSlstrCloudShadowFronts(geoCoding,
                    szaTile, saaTile,
                    ozaTile, oaaTile,
                    ctpTile, slpTile,
                    temperatureProfileTPGTiles,
                    altTile);
            cloudShadowFronts.computeCloudShadow(sourceFlagTile, targetTile);
        }

    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(OlciSlstrPostProcessOp.class);
        }
    }

}
