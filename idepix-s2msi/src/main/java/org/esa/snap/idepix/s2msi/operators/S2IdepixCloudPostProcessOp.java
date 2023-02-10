package org.esa.snap.idepix.s2msi.operators;

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
import org.esa.snap.idepix.s2msi.CoastalCloudDistinction;
import org.esa.snap.idepix.s2msi.UrbanCloudDistinction;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;

import java.awt.Rectangle;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLASSIF_FLAGS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_BUFFER;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_SURE;

/**
 * Adds a cloud buffer to cloudy pixels and does a cloud discrimination on urban areas.
 */
@OperatorMetadata(alias = "Idepix.S2CloudPostProcess",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016-2021 by Brockmann Consult",
        description = "Performs post processing of cloudy pixels and adds optional cloud buffer.")
public class S2IdepixCloudPostProcessOp extends Operator {

    @SourceProduct(alias = "classifiedProduct")
    private Product classifiedProduct;

    @Parameter(defaultValue = "true", label = "Compute cloud buffer for cloud ambiguous pixels too.")
    private boolean computeCloudBufferForCloudAmbiguous;

    @Parameter(defaultValue = "2", label = "Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;


    private Band origClassifFlagBand;

    private RectangleExtender rectCalculator;
    private CoastalCloudDistinction coastalCloudDistinction;
    private UrbanCloudDistinction urbanCloudDistinction;


    @Override
    public void initialize() throws OperatorException {

        Product cloudBufferProduct = createTargetProduct(classifiedProduct,
                classifiedProduct.getName(),
                classifiedProduct.getProductType());

        rectCalculator = new RectangleExtender(new Rectangle(classifiedProduct.getSceneRasterWidth(),
                classifiedProduct.getSceneRasterHeight()),
                cloudBufferWidth, cloudBufferWidth);

        origClassifFlagBand = classifiedProduct.getBand(IDEPIX_CLASSIF_FLAGS);
        ProductUtils.copyBand(IDEPIX_CLASSIF_FLAGS, classifiedProduct, cloudBufferProduct, false);

        coastalCloudDistinction = new CoastalCloudDistinction(classifiedProduct);
        urbanCloudDistinction = new UrbanCloudDistinction(classifiedProduct);
        //urbanCloudDistinction.addDebugBandsToTargetProduct(cloudBufferProduct);

        setTargetProduct(cloudBufferProduct);
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
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRectangle = targetTile.getRectangle();
        final Rectangle srcRectangle = rectCalculator.extend(targetRectangle);

//        if (! "pixel_classif_flags".equals(targetBand.getName())) {
//            if ("__ucd_cdi__".equals(targetBand.getName())) {
//                for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
//                    for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {
//                        if (targetRectangle.contains(x, y)) {
//                            targetTile.setSample(x, y, urbanCloudDistinction.getCdiValue(x, y));
//                        }
//                    }
//                }
//            }
//            return;
//        }

        final Tile sourceFlagTile = getSourceTile(origClassifFlagBand, srcRectangle);

        for (int y = srcRectangle.y; y < srcRectangle.y + srcRectangle.height; y++) {
            checkForCancellation();
            for (int x = srcRectangle.x; x < srcRectangle.x + srcRectangle.width; x++) {

                Tile workTile;
                if (targetRectangle.contains(x, y)) {
                    workTile = S2IdepixUtils.combineFlags(x, y, sourceFlagTile, targetTile); // overlap area of extended source tile
                }else {
                    workTile = sourceFlagTile;
                }
                coastalCloudDistinction.correctCloudFlag(x, y, workTile, workTile);
                urbanCloudDistinction.correctCloudFlag(x, y, workTile, workTile);

                if (isCloudPixel(workTile, x, y)) {
                    S2IdepixCloudBuffer.computeSimpleCloudBuffer(x, y,
                            targetTile,
                            srcRectangle,
                            cloudBufferWidth,
                            IDEPIX_CLOUD_BUFFER);
                }
            }
        }

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                S2IdepixUtils.consolidateCloudAndBuffer(targetTile, x, y);
            }
        }
    }

    private boolean isCloudPixel(Tile targetTile, int x, int y) {
        boolean isCloud = targetTile.getSampleBit(x, y, IDEPIX_CLOUD_SURE);
        if (computeCloudBufferForCloudAmbiguous) {
            isCloud = isCloud || targetTile.getSampleBit(x, y, IDEPIX_CLOUD_AMBIGUOUS);
        }
        return isCloud;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixCloudPostProcessOp.class);
        }
    }
}
