package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.esa.snap.idepix.olci.IdepixOlciConstants.CTP_TF_NN_NAME_DM;
import static org.esa.snap.idepix.olci.IdepixOlciConstants.CTP_TF_NN_NAME_RQ;

/**
 * CTP for OLCI based on Tensorflow neural nets.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Olci.Ctp",
        category = "Optical/Preprocessing",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "CTP for OLCI based on Tensorflow neural nets.")
public class CtpOp extends BasisOp {

    @SourceProduct(alias = "l1bProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product sourceProduct;

    @SourceProduct(alias = "o2CorrProduct",
            label = "OLCI O2 Correction product",
            optional = true,
            description = "OLCI O2 Correction product.")
    private Product o2CorrProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    @Parameter(defaultValue = CTP_TF_NN_NAME_RQ,
            valueSet = {CTP_TF_NN_NAME_RQ, CTP_TF_NN_NAME_DM},
            label = " Tensorflow Neural Net for CTP computation",
            description = "Directory of Tensorflow Neural Net, which is given in Saved Model Format.")
    private String ctpNNDir;

    private TiePointGrid szaBand;
    private TiePointGrid ozaBand;
    private TiePointGrid saaBand;
    private TiePointGrid oaaBand;

    private Band rad12Band;
    private Band solarFlux12Band;
    private Band tra13Band;
    private Band tra14Band;
    private Band tra15Band;

    private TensorflowNNCalculator nnCalculator;

    @Override
    public void initialize() throws OperatorException {

        final boolean inputProductIsValid = IdepixIO.validateInputProduct(sourceProduct, AlgorithmSelector.OLCI);
        if (!inputProductIsValid) {
            throw new OperatorException(IdepixConstants.INPUT_INCONSISTENCY_ERROR_MESSAGE);
        }

        String auxdataPath;
        try {
            auxdataPath = IdepixOlciUtils.installAuxdataNNCtp();
        } catch (IOException e) {
            e.printStackTrace();
            throw new OperatorException("Cannot install CTP NN auxdata:" + e.getMessage());
        }

        String modelDir = auxdataPath + File.separator + ctpNNDir;
        nnCalculator = new TensorflowNNCalculator(modelDir, "none", ctpNNDir);

        targetProduct = createTargetProduct();
    }

    @Override
    public void dispose() {
        super.dispose();
        nnCalculator.getModel().close();
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

            rad12Band = sourceProduct.getBand("Oa12_radiance");
            solarFlux12Band = sourceProduct.getBand("solar_flux_band_12");

            tra13Band = ctpNNDir.equals(CTP_TF_NN_NAME_RQ) ? o2CorrProduct.getBand("transDesmiled_13") :
                    o2CorrProduct.getBand("trans_13");
            tra14Band = ctpNNDir.equals(CTP_TF_NN_NAME_RQ) ? o2CorrProduct.getBand("transDesmiled_14") :
                    o2CorrProduct.getBand("trans_14");
            tra15Band = ctpNNDir.equals(CTP_TF_NN_NAME_RQ) ? o2CorrProduct.getBand("transDesmiled_15") :
                    o2CorrProduct.getBand("trans_15");
        } catch (Exception e) {
            throw new OperatorException(e.getMessage(), e);
        } finally {
            pm.done();
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRectangle = targetTile.getRectangle();

        final Tile szaTile = getSourceTile(szaBand, targetRectangle);
        final Tile ozaTile = getSourceTile(ozaBand, targetRectangle);
        final Tile saaTile = getSourceTile(saaBand, targetRectangle);
        final Tile oaaTile = getSourceTile(oaaBand, targetRectangle);
        final Tile rad12Tile = getSourceTile(rad12Band, targetRectangle);
        final Tile solarFlux12Tile = getSourceTile(solarFlux12Band, targetRectangle);
        final Tile tra13Tile = getSourceTile(tra13Band, targetRectangle);
        final Tile tra14Tile = getSourceTile(tra14Band, targetRectangle);
        final Tile tra15Tile = getSourceTile(tra15Band, targetRectangle);

        final Tile l1FlagsTile = getSourceTile(sourceProduct.getRasterDataNode("quality_flags"), targetRectangle);

        float[][] nnInputs = new float[targetRectangle.height * targetRectangle.width][];
        float[] dummyNnInput = new float[7];
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {

                final boolean pixelIsValid = !l1FlagsTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
                if (pixelIsValid) {
                    // Preparing input data...
                    final float sza = szaTile.getSampleFloat(x, y);
                    final float cosSza = (float) Math.cos(sza * MathUtils.DTOR);
                    final float oza = ozaTile.getSampleFloat(x, y);
                    final float cosOza = (float) Math.cos(oza * MathUtils.DTOR);
                    final float sinOza = (float) Math.sin(oza * MathUtils.DTOR);
                    final float saa = saaTile.getSampleFloat(x, y);
                    final float oaa = oaaTile.getSampleFloat(x, y);
                    final float aziDiff = (float) ((saa - oaa) * MathUtils.DTOR * sinOza);

                    final float rad12 = rad12Tile.getSampleFloat(x, y);
                    final float solarFlux12 = solarFlux12Tile.getSampleFloat(x, y);
                    final float refl12 = rad12 / solarFlux12;
                    final float tra13 = tra13Tile.getSampleFloat(x, y);
                    final float mLogTra13 = (float) -Math.log(tra13);
                    final float tra14 = tra14Tile.getSampleFloat(x, y);
                    final float mLogTra14 = (float) -Math.log(tra14);
                    final float tra15 = tra15Tile.getSampleFloat(x, y);
                    final float mLogTra15 = (float) -Math.log(tra15);

                    float[] nnInput = new float[]{cosSza, cosOza, aziDiff, refl12, mLogTra13, mLogTra14, mLogTra15};
                    nnInputs[(y - targetRectangle.y) * targetRectangle.width + (x - targetRectangle.x)] = nnInput;
                } else {
                    nnInputs[(y - targetRectangle.y) * targetRectangle.width + (x - targetRectangle.x)] = dummyNnInput;
                }
            }
        }

        // call tensorflow once with the complete tile stack
        final float[][] nnResult = nnCalculator.calculate(nnInputs);

        // convert output of tf into ctp and set value into target tile
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                final boolean pixelIsValid = !l1FlagsTile.getSampleBit(x, y, IdepixOlciConstants.L1_F_INVALID);
                if (pixelIsValid) {
                    final float nnResultPixel =
                            nnResult[(y - targetRectangle.y) * targetRectangle.width + (x - targetRectangle.x)][0];
                    targetTile.setSample(x, y, nnCalculator.convertNNResultToCtp(nnResultPixel));
                } else {
                    targetTile.setSample(x, y, Float.NaN);
                }
            }
        }
    }

    private void preProcess() {
        if (o2CorrProduct == null) {
            Map<String, Product> o2corrSourceProducts = new HashMap<>();
            Map<String, Object> o2corrParms = new HashMap<>();
            o2corrParms.put("processOnlyBand13", false);
            o2corrParms.put("writeHarmonisedRadiances", false);
            o2corrSourceProducts.put("l1bProduct", sourceProduct);
            final String o2CorrOpName = "OlciO2aHarmonisation";
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
