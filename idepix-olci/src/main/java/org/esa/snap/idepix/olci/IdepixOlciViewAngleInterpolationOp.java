package org.esa.snap.idepix.olci;

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
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.awt.*;
import java.util.Map;

/**
 * Performs interpolation at view zenith and azimuth discontinuities for OLCI L1b input products.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "OlciViewAngleInterpolation", version = "0.6",
        authors = "Olaf Danne, Dagmar Müller (Brockmann Consult)",
        internal = true,
        category = "Optical/Preprocessing/Masking/IdePix (Clouds, Land, Water, ...)",
        copyright = "Copyright (C) 2021 by Brockmann Consult",
        description = "Performs interpolation at view zenith and azimuth discontinuities for OLCI L1b input products.")
public class IdepixOlciViewAngleInterpolationOp extends Operator {

    @SourceProduct(description = "Input product",
            label = "OLCI L1b product")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private int nxChange = -1;
    private int[] nx_vza;
    private int[] nx_vaa;

    private Band vzaInterpolBand;
    private Band vaaInterpolBand;


    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        setInterpolationIntervals();

        targetProduct = createTargetProduct();

        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        try {
            final Tile vzaTile = getSourceTile(sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_VIEW_ZENITH_BAND_NAME), targetRectangle);
            final Tile vaaTile = getSourceTile(sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_VIEW_AZIMUTH_BAND_NAME), targetRectangle);

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
                    float[] vzaInterpolLine = IdepixOlciUtils.interpolateViewAngles(curveFitter1, curveFitter2,
                            vzaOrigLine, nx_vza, nxChange);
                    float[] vaaInterpolLine = IdepixOlciUtils.interpolateViewAngles(curveFitter1, curveFitter2,
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

        vzaInterpolBand = createInterpolatedBand(targetProduct, IdepixOlciConstants.OLCI_VIEW_ZENITH_INTERPOLATED_BAND_NAME,
                "Interpolated OZA");
        vaaInterpolBand = createInterpolatedBand(targetProduct, IdepixOlciConstants.OLCI_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME,
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
        final boolean isFullResolution = IdepixOlciUtils.isFullResolution(sourceProduct);
        if (isFullResolution && productWidth == IdepixOlciConstants.OLCI_FR_FULL_PRODUCT_WIDTH) {
            // full width, full resolution
            nxChange = IdepixOlciConstants.OLCI_FR_DEFAULT_NX_CHANGE;
            nx_vza = IdepixOlciConstants.OLCI_DEFAULT_FR_NX_VZA;
            nx_vaa =  IdepixOlciConstants.OLCI_DEFAULT_FR_NX_VAA;
        } else if (!isFullResolution && productWidth == IdepixOlciConstants.OLCI_RR_FULL_PRODUCT_WIDTH) {
            // full width, reduced resolution
            nxChange = IdepixOlciConstants.OLCI_RR_DEFAULT_NX_CHANGE;
            nx_vza = IdepixOlciConstants.OLCI_DEFAULT_RR_NX_VZA;
            nx_vaa =  IdepixOlciConstants.OLCI_DEFAULT_RR_NX_VAA;
        } else {
            // subset: find discontinuity
            final TiePointGrid vaaTpg = sourceProduct.getTiePointGrid(IdepixOlciConstants.OLCI_VIEW_AZIMUTH_BAND_NAME);
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
//                if (isFullResolution) {
//                    nx_vza[0] = Math.max(0, nxChange - 116);
//                    nx_vza[1] = Math.max(0, nxChange - 36);
//                    nx_vza[2] = Math.min(productWidth, nxChange + 33);
//                    nx_vza[3] = Math.min(productWidth, nxChange + 113);
//                    nx_vaa[0] = Math.max(0, nxChange - 1616);
//                    nx_vaa[1] = Math.max(0, nxChange - 216);
//                    nx_vaa[2] = Math.min(productWidth, nxChange + 284);
//                    nx_vaa[3] = productWidth;
//                } else {
//                    nx_vza[0] = Math.max(0, nxChange - 29);
//                    nx_vza[1] = Math.max(0, nxChange - 9);
//                    nx_vza[2] = Math.min(productWidth, nxChange + 8);
//                    nx_vza[3] = Math.min(productWidth, nxChange + 28);
//                    nx_vaa[0] = Math.max(0, nxChange - 404);
//                    nx_vaa[1] = Math.max(0, nxChange - 54);
//                    nx_vaa[2] = Math.min(productWidth, nxChange + 71);
//                    nx_vaa[3] = productWidth;
//                }
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
            super(IdepixOlciViewAngleInterpolationOp.class);
        }
    }
}