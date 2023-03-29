package org.esa.snap.idepix.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.fitting.PolynomialFitter;
import org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.awt.*;
import java.util.Map;

/**
 * Performs interpolation at view zenith and azimuth discontinuities for MERIS L1b input products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "MerisViewAngleInterpolation", version = "0.6",
        authors = "Olaf Danne, Dagmar Müller (Brockmann Consult)",
        internal = true,
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        copyright = "Copyright (C) 2023 by Brockmann Consult",
        description = "Performs interpolation at view zenith and azimuth discontinuities for MERIS L1b input products.")
public class IdepixMerisViewAngleInterpolationOp extends Operator {

    @SourceProduct(description = "Input product",
            label = "MERIS L1b product")
    private Product sourceProduct;

    private int nxChange = -1;
    private int[] nx_vza;
    private int[] nx_vaa;

    private Band vzaInterpolBand;
    private Band vaaInterpolBand;


    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.MERIS);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        setInterpolationIntervals();

        Product targetProduct = createTargetProduct();

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        try {
            final Tile vzaTile =
                    getSourceTile(sourceProduct.getTiePointGrid(IdepixMerisConstants.MERIS_VIEW_ZENITH_BAND_NAME),
                            targetRectangle);
            final Tile vaaTile =
                    getSourceTile(sourceProduct.getTiePointGrid(IdepixMerisConstants.MERIS_VIEW_AZIMUTH_BAND_NAME),
                            targetRectangle);

            final Tile vzaInterpolTile = targetTiles.get(vzaInterpolBand);
            final Tile vaaInterpolTile = targetTiles.get(vaaInterpolBand);

            float[] vzaOrigLine = new float[targetRectangle.width];
            float[] vaaOrigLine = new float[targetRectangle.width];

            PolynomialFitter curveFitter1 = new PolynomialFitter(new LevenbergMarquardtOptimizer());
            PolynomialFitter curveFitter2 = new PolynomialFitter(new LevenbergMarquardtOptimizer());

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                checkForCancellation();

                for (int x = 0; x < targetRectangle.width; x++) {
                    vzaOrigLine[x] = vzaTile.getSampleFloat(x, y);
                    vaaOrigLine[x] = vaaTile.getSampleFloat(x, y);
                }

                if (nxChange != -1 && nx_vza[1] > 0 && nx_vza[2] < targetRectangle.width &&
                        nx_vaa[1] > 0 && nx_vaa[2] < targetRectangle.width) {
                    // we need a sufficient product width to do interpolation...
                    float[] vzaInterpolLine = IdepixMerisUtils.interpolateViewAngles(curveFitter1, curveFitter2,
                            vzaOrigLine, nx_vza, nxChange);
                    float[] vaaInterpolLine = IdepixMerisUtils.interpolateViewAngles(curveFitter1, curveFitter2,
                            vaaOrigLine, nx_vaa, nxChange);
                    for (int x = 0; x < targetRectangle.width; x++) {
                        vzaInterpolTile.setSample(x, y, vzaInterpolLine[x]);
                        vaaInterpolTile.setSample(x, y, vaaInterpolLine[x]);
                    }
                } else {
                    for (int x = 0; x < targetRectangle.width; x++) {
                        vzaInterpolTile.setSample(x, y, vzaOrigLine[x]);
                        vaaInterpolTile.setSample(x, y, vaaOrigLine[x]);
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    private Product createTargetProduct() {
        final int w = sourceProduct.getSceneRasterWidth();
        final int h = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), w, h);
        targetProduct.setPreferredTileSize(w, 16);  // we need tiles over whole source width

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        vzaInterpolBand = createInterpolatedBand(targetProduct, IdepixMerisConstants.MERIS_VIEW_ZENITH_INTERPOLATED_BAND_NAME,
                "Interpolated OZA");
        vaaInterpolBand = createInterpolatedBand(targetProduct, IdepixMerisConstants.MERIS_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME,
                "Interpolated OAA");

        return targetProduct;
    }

    private Band createInterpolatedBand(Product targetProduct, String bandName, String description) {
        Band band = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
        band.setDescription(description);
        band.setUnit("deg");
        band.setNoDataValue(-9999.);
        band.setNoDataValueUsed(true);
        return band;
    }

    private void setInterpolationIntervals() {
        final int productWidth = sourceProduct.getSceneRasterWidth();
        final boolean isFullResolution = IdepixMerisUtils.isFullResolution(sourceProduct);
        if (isFullResolution && productWidth == IdepixMerisConstants.MERIS_FR_FULL_PRODUCT_WIDTH) {
            // full width, full resolution
            nxChange = IdepixMerisConstants.MERIS_FR_DEFAULT_NX_CHANGE;
            nx_vza = IdepixMerisConstants.MERIS_DEFAULT_FR_NX_VZA;
            nx_vaa =  IdepixMerisConstants.MERIS_DEFAULT_FR_NX_VAA;
        } else if (!isFullResolution && productWidth == IdepixMerisConstants.MERIS_RR_FULL_PRODUCT_WIDTH) {
            // full width, reduced resolution
            nxChange = IdepixMerisConstants.MERIS_RR_DEFAULT_NX_CHANGE;
            nx_vza = IdepixMerisConstants.MERIS_DEFAULT_RR_NX_VZA;
            nx_vaa =  IdepixMerisConstants.MERIS_DEFAULT_RR_NX_VAA;
        } else {
            // subset: find discontinuity
            final TiePointGrid vaaTpg = sourceProduct.getTiePointGrid(IdepixMerisConstants.MERIS_VIEW_AZIMUTH_BAND_NAME);
            final float[] vaaProfile = (float[]) vaaTpg.getRasterData().getElems();
            for (int i = 0; i < productWidth -2; i++) {
                if (vaaProfile[i+1] < 0.0 && vaaProfile[i] > 0.0) {
                    nxChange = i+2;
                    break;
                }
            }

            if (nxChange != -1) {
                nx_vza = new int[4];
                nx_vaa = new int[4];
                nx_vza[0] = (int) (0.97*nxChange);
                nx_vza[1] = (int) (0.99*nxChange);
                nx_vza[2] = Math.min(productWidth, (int) (1.01*nxChange));
                nx_vza[3] = Math.min(productWidth, (int) (1.03*nxChange));
                nx_vaa[0] = (int) (0.55*nxChange);
                nx_vaa[1] = (int) (0.94*nxChange);
                nx_vaa[2] = Math.min(productWidth, (int) (1.08*nxChange));
                nx_vaa[3] = productWidth;
            }

        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixMerisViewAngleInterpolationOp.class);
        }
    }
}