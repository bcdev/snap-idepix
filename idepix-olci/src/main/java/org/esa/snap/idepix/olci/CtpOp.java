package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.idepix.core.AlgorithmSelector;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.operators.BasisOp;
import org.esa.s3tbx.idepix.core.util.IdepixIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * The Idepix pixel classification operator for OLCI products.
 * <p>
 * Initial implementation:
 * - pure neural net approach which uses MERIS heritage bands only
 * - no auxdata available yet (such as 'L2 auxdata' for MERIS)
 * <p>
 * Currently resulting limitations:
 * - no cloud shadow flag
 * - glint flag over water just taken from 'sun_glint_risk' in L1 'quality_flags' band
 * <p>
 * Advanced algorithm to be defined which makes use of more bands.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Snap.Idepix.Olci.Ctp",
        category = "Optical/Pre-Processing",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Pixel identification and classification for OLCI.")
public class CtpOp extends BasisOp {

    @SourceProduct(alias = "sourceProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "rad2reflProduct",
            label = "OLCI TOA Reflectance product",
            optional = true,
            description = "OLCI TOA Reflectance product generated with Rad2ReflOp.")
    private Product rad2reflProduct;

    @SourceProduct(alias = "o2CorrProduct",
            label = "OLCI O2 Correction product",
            optional = true,
            description = "OLCI O2 Correction product.")
    private Product o2CorrProduct;


    @TargetProduct(description = "The target product.")
    private Product targetProduct;


    // todo
//    @Parameter(description = "Path to an alternative Tensorflow neuronal net. Use this to replace the standard " +
//            "set of neuronal nets with the ones in the given directory.",
//            label = "Alternative NN Path")
//    private String alternativeNNPath;

    private static final String DEFAULT_TENSORFLOW_NN_DIR_NAME = "nn_training_20190131_I7x24x24x24xO1";

    private TiePointGrid szaBand;
    private TiePointGrid ozaBand;
    private TiePointGrid saaBand;
    private TiePointGrid oaaBand;

    private Band refl12Band;
    private Band tra13Band;
    private Band tra14Band;
    private Band tra15Band;

    private String nnDirName;


    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        nnDirName = DEFAULT_TENSORFLOW_NN_DIR_NAME;

        targetProduct = createTargetProduct();
    }

    @Override
    public void doExecute(ProgressMonitor pm) throws OperatorException {
        try {
            pm.beginTask("Executing CTP processing...", 0);
            preProcess();

            szaBand = sourceProduct.getTiePointGrid("SZA");
            ozaBand = sourceProduct.getTiePointGrid("OZA");
            saaBand = sourceProduct.getTiePointGrid("SAA");
            oaaBand = sourceProduct.getTiePointGrid("OAA");

            refl12Band = rad2reflProduct.getBand("Oa12_reflectance");

            tra13Band = o2CorrProduct.getBand("trans_13");
            tra14Band = o2CorrProduct.getBand("trans_14");
            tra15Band = o2CorrProduct.getBand("trans_15");

        } catch (Exception e) {
            throw new OperatorException(e.getMessage(), e);
        } finally {
            pm.done();
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRectangle = targetTile.getRectangle();
        final String targetBandName = targetBand.getName();

        final Tile szaTile = getSourceTile(szaBand, targetRectangle);
        final Tile ozaTile = getSourceTile(ozaBand, targetRectangle);
        final Tile saaTile = getSourceTile(saaBand, targetRectangle);
        final Tile oaaTile = getSourceTile(oaaBand, targetRectangle);
        final Tile refl12Tile = getSourceTile(refl12Band, targetRectangle);
        final Tile tra13Tile = getSourceTile(tra13Band, targetRectangle);
        final Tile tra14Tile = getSourceTile(tra14Band, targetRectangle);
        final Tile tra15Tile = getSourceTile(tra15Band, targetRectangle);

        final Tile l1FlagsTile = getSourceTile(sourceProduct.getRasterDataNode("quality_flags"), targetRectangle);

        TensorflowNNCalculator nnCalculator = new TensorflowNNCalculator(nnDirName, "none", null);

        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {

                if (x == 1800 && y == 600) {
                    System.out.println("x = " + x);
                }

                final boolean pixelIsValid = !l1FlagsTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
                if (pixelIsValid) {
                    // Preparing input data...
                    final float sza = szaTile.getSampleFloat(x, y);
                    final float cosSza = (float) Math.cos(sza*MathUtils.DTOR);
                    final float oza = ozaTile.getSampleFloat(x, y);
                    final float cosOza = (float) Math.cos(oza*MathUtils.DTOR);
                    final float sinOza = (float) Math.sin(oza * MathUtils.DTOR);
                    final float saa = saaTile.getSampleFloat(x, y);
                    final float oaa = oaaTile.getSampleFloat(x, y);
                    final float aziDiff = (float) ((saa - oaa)*MathUtils.DTOR * sinOza);

                    final float refl12 = refl12Tile.getSampleFloat(x, y);
                    final float tra13 = tra13Tile.getSampleFloat(x, y);
                    final float mLogTra13 = (float) -Math.log(tra13);
                    final float tra14 = tra14Tile.getSampleFloat(x, y);
                    final float mLogTra14 = (float) -Math.log(tra14);
                    final float tra15 = tra15Tile.getSampleFloat(x, y);
                    final float mLogTra15 = (float) -Math.log(tra15);

                    float[] nnInput = new float[]{cosSza, cosOza, aziDiff, refl12, mLogTra13, mLogTra14, mLogTra15};
                    nnCalculator.setNnTensorInput(nnInput);
                    final float[][] nnResult = nnCalculator.getNNResult();
                    final float ctp = TensorflowNNCalculator.convertNNResultToCtp(nnResult[0][0]);

                    if (targetBandName.equals("ctp")) {
                        targetTile.setSample(x, y, ctp);
                    } else {
                        throw new OperatorException("Unexpected target band name: '" +
                                                            targetBandName + "' - exiting.");
                    }
                } else {
                    targetTile.setSample(x, y, Float.NaN);
                }
            }
        }

    }

    private void preProcess() {
        if (rad2reflProduct == null) {
            rad2reflProduct = IdepixOlciUtils.computeRadiance2ReflectanceProduct(sourceProduct);
        }

        if (o2CorrProduct == null) {
            Map<String, Product> o2corrSourceProducts = new HashMap<>();
            Map<String, Object> o2corrParms = new HashMap<>();
            o2corrParms.put("processOnlyBand13", false);
            o2corrParms.put("writeCorrectedRadiances", false);
            o2corrSourceProducts.put("l1bProduct", sourceProduct);
            final String o2CorrOpName = "O2CorrOlci";
            o2CorrProduct = GPF.createProduct(o2CorrOpName, o2corrParms, o2corrSourceProducts);
        }
    }

    private Product createTargetProduct() {
        Product targetProduct = new Product(sourceProduct.getName(),
                                            sourceProduct.getProductType(),
                                            sourceProduct.getSceneRasterWidth(),
                                            sourceProduct.getSceneRasterHeight());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        final Band ctpBand = targetProduct.addBand("ctp", ProductData.TYPE_FLOAT32);
        ctpBand.setNoDataValue(Float.NaN);
        ctpBand.setNoDataValueUsed(true);
        ctpBand.setUnit("hPa");
        ctpBand.setDescription("Cloud Top Pressure");

        return targetProduct;
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CtpOp.class);
        }
    }
}
