package org.esa.snap.idepix.slstr;

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
@OperatorMetadata(alias = "Idepix.Slstr.Postprocess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2025 by Brockmann Consult",
        description = "Post-processes the SLSTR pixel classification. Actually just adds cloud buffer.")
public class IdepixSlstrPostProcessOp extends Operator {

    @Parameter(defaultValue = "true",
            label = " Compute a cloud buffer",
            description = " Compute a cloud buffer")
    private boolean computeCloudBuffer;

    @Parameter(defaultValue = "2", interval = "[0,100]",
            description = "The width of a cloud 'safety buffer' around a pixel which was classified as cloudy.",
            label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;

    @SourceProduct(alias = "slstrCloud")
    private Product slstrCloudProduct;

    private Band origCloudFlagBand;

    private RectangleExtender rectExtender;

    @Override
    public void initialize() throws OperatorException {
        Product postProcessedCloudProduct = IdepixIO.createCompatibleTargetProduct(slstrCloudProduct,
                "postProcessedCloud",
                "postProcessedCloud",
                true);

        origCloudFlagBand = slstrCloudProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);

        int extent = computeCloudBuffer ? cloudBufferWidth : 0;
        rectExtender = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight()), extent, extent);

        ProductUtils.copyBand(IdepixConstants.CLASSIF_BAND_NAME, slstrCloudProduct, postProcessedCloudProduct, false);
        setTargetProduct(postProcessedCloudProduct);
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

    }

    private void combineFlags(int x, int y, Tile sourceFlagTile, Tile targetTile) {
        int sourceFlags = sourceFlagTile.getSampleInt(x, y);
        int computedFlags = targetTile.getSampleInt(x, y);
        targetTile.setSample(x, y, sourceFlags | computedFlags);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixSlstrPostProcessOp.class);
        }
    }

}
