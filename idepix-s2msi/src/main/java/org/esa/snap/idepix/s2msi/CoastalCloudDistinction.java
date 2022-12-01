package org.esa.snap.idepix.s2msi;

import org.esa.snap.core.dataio.ProductSubsetDef;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.GeneralFilterBand;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.Kernel;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.VirtualBand;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.opengis.referencing.operation.MathTransform;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.IOException;

import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.*;

public class CoastalCloudDistinction {

    private static final float COAST_BUFFER_SIZE = 1000f;

    private final Band dilatedLandFlag;
    private final Band dilatedWaterFlag;
    private final Band idx1band;
    private final Band idx2band;
    private final int resolution;

    public CoastalCloudDistinction(Product s2ClassifProduct) {
        final Product ccdHelperProduct = createCcdHelperProduct(s2ClassifProduct);
        resolution = S2IdepixUtils.determineResolution(s2ClassifProduct);
        dilatedLandFlag = createDilatedLandFlag(ccdHelperProduct);
        dilatedWaterFlag = createDilatedWaterFlag(ccdHelperProduct);
        idx1band = getRatio2_11(ccdHelperProduct);
        idx2band = getRatio8_11(ccdHelperProduct);
    }

    private Product createCcdHelperProduct(Product s2ClassifProduct) {
        try {
            return s2ClassifProduct.createSubset(new ProductSubsetDef(), "ccd_helper", "");
        } catch (IOException e) {
            throw new OperatorException("Cannot create helper product for coastal cloud distinction." ,e);
        }
    }

    private Band createDilatedLandFlag(Product ucdHelperProduct) {
        return createDilatedIdepixFlag(ucdHelperProduct, "tmp_land_mask", IDEPIX_LAND_DESCR_TEXT, IDEPIX_LAND_NAME);
    }

    private Band createDilatedWaterFlag(Product ucdHelperProduct) {
        return createDilatedIdepixFlag(ucdHelperProduct, "tmp_water_mask", IDEPIX_WATER_DESCR_TEXT, IDEPIX_WATER_NAME);
    }

    private Band createDilatedIdepixFlag(Product ucdHelperProduct,
                                         String maskName,
                                         String descriptionName,
                                         String flagName) {
        final Mask flagMask = Mask.BandMathsType.create(maskName, descriptionName,
                ucdHelperProduct.getSceneRasterWidth(), ucdHelperProduct.getSceneRasterHeight(),
                String.format("%1$s.%2$s", IDEPIX_CLASSIF_FLAGS, flagName),
                new Color(178, 0, 178), 0.5f);
        flagMask.setOwner(ucdHelperProduct);

        return computeDilate(flagMask);
    }

    private Band computeDilate(Band band) {
        final int kernelSize = (int) Math.floor((2 * COAST_BUFFER_SIZE) / resolution);
        final Kernel kernel = new Kernel(kernelSize, kernelSize, new double[kernelSize * kernelSize]);
        final GeneralFilterBand filterBand = new GeneralFilterBand(
                "__dilate33_" + band.getName(), band, GeneralFilterBand.OpType.DILATION, kernel, 1
        );
        band.getProduct().addBand(filterBand);
        return filterBand;
    }

    public void correctCloudFlag(int x, int y, Tile classifFlagTile, Tile targetFlagTile) {
        final boolean landFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_LAND);
        final boolean waterFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_WATER);
        final boolean cloudAmbiguousFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD_AMBIGUOUS);
        final boolean cloudFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD);
        final boolean cloudSureFlag = classifFlagTile.getSampleBit(x, y, IDEPIX_CLOUD_SURE);
        targetFlagTile.setSample(x, y, classifFlagTile.getSampleInt(x, y));
        final boolean isCoastal = (dilatedLandFlag.getSampleInt(x, y) == 255 && !landFlag) ||
                (dilatedWaterFlag.getSampleInt(x, y) == 255 && !waterFlag);
        if (isCoastal) {
            final float idx1 = idx1band.getSampleFloat(x, y);
            final float idx2 = idx2band.getSampleFloat(x, y);
            final boolean notCoast = idx1 > 0.7 || (idx1 < 1 && idx1 > 0.6 && idx2 > 0.9);
            final boolean cloudSureCoastalFlag = cloudSureFlag && notCoast;
            final boolean cloudAmbiguousCoastalFlag = cloudAmbiguousFlag && notCoast;
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguousCoastalFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_SURE, cloudSureCoastalFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD, cloudAmbiguousCoastalFlag || cloudSureCoastalFlag);
        } else {
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguousFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD_SURE, cloudSureFlag);
            targetFlagTile.setSample(x, y, IDEPIX_CLOUD, cloudFlag);
        }
    }

    private Band getRatio2_11(Product product) {
        return createRatioBand(product.getBand("B2"), product.getBand("B11"), product);
    }

    private Band getRatio8_11(Product product) {
        return createRatioBand(product.getBand("B8"), product.getBand("B11"), product);
    }

     private static Band createRatioBand(Band numerator, Band denominator, Product owner) {
        final String name = String.format("__ratio_%s_%s", numerator.getName(), denominator.getName());
        final String expression = String.format("%s / %s", numerator.getName(), denominator.getName());
        return createVirtualBand(name, expression, owner);
    }

    private static VirtualBand createVirtualBand(String name, String expression, Product owner) {
        final VirtualBand virtBand = new VirtualBand(name,
                ProductData.TYPE_FLOAT32,
                owner.getSceneRasterWidth(),
                owner.getSceneRasterHeight(),
                expression);
        owner.addBand(virtBand);
        return virtBand;
    }

}
