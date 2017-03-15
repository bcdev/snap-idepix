package org.esa.s3tbx.idepix.algorithms.olci;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.s3tbx.idepix.core.IdepixConstants;
import org.esa.s3tbx.idepix.core.util.IdepixIO;
import org.esa.s3tbx.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.s3tbx.processor.rad2refl.Rad2ReflConstants;
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
 * OLCI pixel classification operator.
 * Only land pixels are classified from NN approach (following MERIS approach for BEAM Cawa algorithm).
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Olci.Land",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Idepix land pixel classification operator for OLCI.")
public class OlciLandClassificationOp extends Operator {
    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

//    @Parameter(defaultValue = "2.0",
//            label = " NN cloud ambiguous lower boundary",
//            description = " NN cloud ambiguous lower boundary")
//    double schillerNNCloudAmbiguousLowerBoundaryValue;
//
//    @Parameter(defaultValue = "3.7",
//            label = " NN cloud ambiguous/sure separation value",
//            description = " NN cloud ambiguous cloud ambiguous/sure separation value")
//    double schillerNNCloudAmbiguousSureSeparationValue;
//
//    @Parameter(defaultValue = "4.05",
//            label = " NN cloud sure/snow separation value",
//            description = " NN cloud ambiguous cloud sure/snow separation value")
//    double schillerNNCloudSureSnowSeparationValue;

    // Schiller's best values for OLCI land net 11x5x3_159.4.net, 20170314
//    private double schillerNNOpaqueCloudToSnowIceSeparationValue = 1.65;
//    private double schillerNNSnowIceToCloudAmbiguousSeparationValue = 2.35;
//    private double schillerNNCloudAmbiguousUpperBoundaryValue = 4.4;

    // Schiller's best values for OLCI all net 11x5x3_159.4.net, 20170314
    private double schillerNNOpaqueCloudToSnowIceSeparationValue = 1.6;
    private double schillerNNSnowIceToCloudAmbiguousSeparationValue = 2.45;
    private double schillerNNCloudAmbiguousUpperBoundaryValue = 4.45;


    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rad2reflProduct;
    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    Band cloudFlagBand;

    private Band[] olciReflBands;
    private Band landWaterBand;

//    public static final String OLCI_LAND_NET_NAME = "11x8x5x3_1062.5_land.net";

    // NEW net really for OLCI (for all 21 inputs, currently just for water)
    public static final String OLCI_LAND_NET_NAME = "11x5x3_159.4.net";
    public static final String OLCI_ALL_NET_NAME = "13x6x3_246.2.net";

    ThreadLocal<SchillerNeuralNetWrapper> olciLandNeuralNet;
    ThreadLocal<SchillerNeuralNetWrapper> olciAllNeuralNet;

    @Override
    public void initialize() throws OperatorException {
        setBands();

        readSchillerNeuralNets();
        createTargetProduct();

        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
    }

    private void readSchillerNeuralNets() {
        InputStream olciLandIS = getClass().getResourceAsStream(OLCI_LAND_NET_NAME);
        InputStream olciAllIS = getClass().getResourceAsStream(OLCI_ALL_NET_NAME);
        olciLandNeuralNet = SchillerNeuralNetWrapper.create(olciLandIS);
        olciAllNeuralNet = SchillerNeuralNetWrapper.create(olciAllIS);
    }

    public void setBands() {
        olciReflBands = new Band[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.OLCI_REFL_BAND_NAMES[i].substring(0, suffixStart);
            olciReflBands[i] = rad2reflProduct.getBand(reflBandname + "_reflectance");
        }
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        // shall be the only target band!!
        cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = OlciUtils.createOlciFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (outputSchillerNNValue) {
            targetProduct.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        }
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        // MERIS variables
        final Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);

        final Band olciQualityFlagBand = sourceProduct.getBand(OlciConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
        final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, rectangle);

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
                    final int waterFraction = waterFractionTile.getSampleInt(x, y);
                    initCloudFlag(olciQualityFlagTile, targetTiles.get(cloudFlagTargetBand), olciReflectance, y, x);
                    if (!isLandPixel(x, y, olciQualityFlagTile, waterFraction)) {
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, false);
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                        cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);
                        if (nnTargetTile != null) {
                            nnTargetTile.setSample(x, y, Float.NaN);
                        }
                    } else {
                        // only use Schiller NN approach...
                        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                            olciReflectance[i] = olciReflectanceTiles[i].getSampleFloat(x, y);
                        }

//                        SchillerNeuralNetWrapper nnWrapper = olciLandNeuralNet.get();
                        SchillerNeuralNetWrapper nnWrapper = olciAllNeuralNet.get();
                        double[] inputVector = nnWrapper.getInputVector();
//                        for (int i = 0; i < inputVector.length; i++) {
//                            final int olciEquivalentWvlIndex = Rad2ReflConstants.OLCI_MERIS_EQUIVALENT_WVL_INDICES[i];
//                            inputVector[i] = Math.sqrt(olciReflectance[olciEquivalentWvlIndex]);
//                        }
                        // we have a real OLCI net for all inputs now:
                        for (int i = 0; i < inputVector.length; i++) {
                            inputVector[i] = Math.sqrt(olciReflectance[i]);
                        }

                        final double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

                        if (!cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_INVALID)) {
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, false);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, false);

//                            if (nnOutput[0] > schillerNNCloudAmbiguousLowerBoundaryValue &&
//                                    nnOutput[0] <= schillerNNCloudAmbiguousSureSeparationValue) {
                            // new OLCI net, 20170307:
                            if (nnOutput[0] > schillerNNSnowIceToCloudAmbiguousSeparationValue &&
                                    nnOutput[0] <= schillerNNCloudAmbiguousUpperBoundaryValue) {
                                // this would be as 'CLOUD_AMBIGUOUS'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
                            }
//                            if (nnOutput[0] > schillerNNCloudAmbiguousSureSeparationValue &&
//                                    nnOutput[0] <= schillerNNCloudSureSnowSeparationValue) {
                            // new OLCI net, 20170307:
                            if (nnOutput[0] < schillerNNOpaqueCloudToSnowIceSeparationValue) {
                                // this would be as 'CLOUD_SURE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, true);
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, true);
                            }
//                            if (nnOutput[0] > schillerNNCloudSureSnowSeparationValue) {
                            // new OLCI net, 20170307:
                            if (nnOutput[0] > schillerNNOpaqueCloudToSnowIceSeparationValue &&
                                    nnOutput[0] <= schillerNNSnowIceToCloudAmbiguousSeparationValue) {
                                // this would be as 'SNOW/ICE'...
                                cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, true);
                            }
                        }

                        if (nnTargetTile != null) {
                            nnTargetTile.setSample(x, y, nnOutput[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private boolean isLandPixel(int x, int y, Tile olciL1bFlagTile, int waterFraction) {
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return olciL1bFlagTile.getSampleBit(x, y, OlciConstants.L1_F_LAND);
            }
        } else {
            return olciL1bFlagTile.getSampleBit(x, y, OlciConstants.L1_F_LAND);
        }
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = getSourceProduct().getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    void initCloudFlag(Tile olciL1bFlagTile, Tile targetTile, float[] olciReflectances, int y, int x) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        final boolean l1Invalid = olciL1bFlagTile.getSampleBit(x, y, OlciConstants.L1_F_INVALID);
        final boolean reflectancesValid = IdepixIO.areAllReflectancesValid(olciReflectances);

        targetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid || !reflectancesValid);
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
            super(OlciLandClassificationOp.class);
        }
    }
}
