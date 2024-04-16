package org.esa.snap.idepix.olcislstr;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
import eu.esa.opt.processor.rad2refl.Rad2ReflConstants;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

import java.awt.*;
import java.io.InputStream;
import java.util.Map;

/**
 * OLCI/SLSTR synergy pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.OlciSlstr.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix pixel classification operator for OLCI/SLSTR synergy.")
public class OlciSlstrClassificationOp extends Operator {

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "reflOlci")
    private Product olciRad2reflProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;


    @TargetProduct(description = "The target product.")
    Product targetProduct;

    Band cloudFlagBand;

    private Band[] olciReflBands;

    private Band landWaterBand;

    public static final String OLCISLSTR_ALL_NET_NAME = "11x10x4x3x2_207.9.net";

    private static final double THRESH_LAND_MINBRIGHT1 = 0.3;
    private static final double THRESH_LAND_MINBRIGHT2 = 0.25;  // test OD 20170411

    ThreadLocal<SchillerNeuralNetWrapper> olciSlstrAllNeuralNet;
    private OlciSlstrCloudNNInterpreter nnInterpreter;


    @Override
    public void initialize() throws OperatorException {
        setBands();
        nnInterpreter = OlciSlstrCloudNNInterpreter.create();
        readSchillerNeuralNets();
        createTargetProduct();
    }

    private void readSchillerNeuralNets() {
        InputStream olciAllIS = getClass().getResourceAsStream(OLCISLSTR_ALL_NET_NAME);
        olciSlstrAllNeuralNet = SchillerNeuralNetWrapper.create(olciAllIS);
    }

    public void setBands() {
        // e.g. Oa07_reflectance
        olciReflBands = new Band[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].substring(0, suffixStart);
            olciReflBands[i] = olciRad2reflProduct.getBand(reflBandname + "_reflectance");
        }

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        // shall be the only target band!!
        cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = OlciSlstrUtils.createOlciFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
//        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (outputSchillerNNValue) {
            final Band nnValueBand = targetProduct.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
            nnValueBand.setNoDataValue(0.0f);
            nnValueBand.setNoDataValueUsed(true);
        }
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        final Band olciQualityFlagBand = sourceProduct.getBand(OlciSlstrConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
        final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, rectangle);

        final Band slstrCloudAnFlagBand = sourceProduct.getBand(OlciSlstrConstants.SLSTR_CLOUD_AN_FLAG_BAND_NAME);
        final Tile slstrCloudAnFlagTile = getSourceTile(slstrCloudAnFlagBand, rectangle);

        Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        float[] olciReflectance = new float[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], rectangle);
        }

        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        Band nnTargetBand;
        Tile nnTargetTile = null;
        if (outputSchillerNNValue) {
            nnTargetBand = targetProduct.getBand(IdepixConstants.NN_OUTPUT_BAND_NAME);
            nnTargetTile = targetTiles.get(nnTargetBand);
        }
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    initCloudFlag(targetTiles.get(cloudFlagTargetBand), y, x);
                    final int waterFraction = waterFractionTile.getSampleInt(x, y);
                    final boolean isL1bLand = olciQualityFlagTile.getSampleBit(x, y, OlciSlstrConstants.L1_F_LAND);
                    final boolean isLand =
                            IdepixUtils.isLandPixel(x, y, sourceProduct.getSceneGeoCoding(), isL1bLand, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, isLand);

                    for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                        olciReflectance[i] = olciReflectanceTiles[i].getSampleFloat(x, y);
                    }

                    final boolean l1Invalid = olciQualityFlagTile.getSampleBit(x, y, OlciSlstrConstants.L1_F_INVALID);
                    boolean reflectancesValid = IdepixIO.areAllReflectancesValid(olciReflectance);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid || !reflectancesValid);

                    final boolean isSlstrCloudAn137Thresh =
                            slstrCloudAnFlagTile.getSampleBit(x, y, OlciSlstrConstants.CLOUD_AN_F_137_THRESH);
                    final boolean isSlstrCloudAnGrossCloud =
                            slstrCloudAnFlagTile.getSampleBit(x, y, OlciSlstrConstants.CLOUD_AN_F_GROSS_CLOUD);

                    if (reflectancesValid) {
                        SchillerNeuralNetWrapper nnWrapper = olciSlstrAllNeuralNet.get();
                        double[] inputVector = nnWrapper.getInputVector();
                        // use OLCI net instead of OLCI/SLSTR net:
                        for (int i = 0; i < inputVector.length; i++) {
                            inputVector[i] = Math.sqrt(olciReflectance[i]);
                        }

                        final double nnOutput = nnWrapper.getNeuralNet().calc(inputVector)[0];

                        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

                            // CB 20170406: todo: needed here?
                            final boolean cloudSure = olciReflectance[2] > THRESH_LAND_MINBRIGHT1 &&
                                    nnInterpreter.isCloudSure(nnOutput);
                            final boolean cloudAmbiguous = olciReflectance[2] > THRESH_LAND_MINBRIGHT2 &&
                                    nnInterpreter.isCloudAmbiguous(nnOutput);

                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguous);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, cloudSure);
                            // request RQ, GK, 20220111:
                            final boolean isSynCloud = cloudAmbiguous || cloudSure || isSlstrCloudAn137Thresh ||
                                    isSlstrCloudAnGrossCloud;
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, cloudAmbiguous || cloudSure);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, isSynCloud);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, nnInterpreter.isSnowIce(nnOutput));
                        }

                        if (nnTargetTile != null) {
                            nnTargetTile.setSample(x, y, nnOutput);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide cloud screening:\n" + e.getMessage(), e);
        }
    }

    void initCloudFlag(Tile targetTile, int y, int x) {
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_BUFFER, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SHADOW, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_COASTLINE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, true);   // already checked
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(OlciSlstrClassificationOp.class);
        }
    }
}
