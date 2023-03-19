package org.esa.snap.idepix.c3solcislstr;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.idepix.c3solcislstr.rad2refl.Rad2ReflConstants;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
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
@OperatorMetadata(alias = "Idepix.C3SOlciSlstr.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "IdePix pixel classification operator for OLCI/SLSTR synergy.")
public class C3SOlciSlstrClassificationOp extends Operator {

    @Parameter(defaultValue = "false",
            label = " Write NN value to the target product.",
            description = " If applied, write Schiller NN value to the target product ")
    private boolean outputSchillerNNValue;

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "reflOlci")
    private Product olciRad2reflProduct;

    @SourceProduct(alias = "reflSlstr")
    private Product slstrRad2reflProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;


    @TargetProduct(description = "The target product.")
    Product targetProduct;

    Band cloudFlagBand;

    private Band[] olciReflBands;
    private Band[] slstrReflBands;

    private Band landWaterBand;
    private Band altitudeBand;
    private Band szaBand;
    private Band saaBand;
    private Band ozaBand;
    private Band oaaBand;


    public static final String OLCISLSTR_ALL_NET_NAME = "11x10x4x3x2_207.9.net";

    private static final double THRESH_LAND_MINBRIGHT1 = 0.3;
    private static final double THRESH_LAND_MINBRIGHT2 = 0.25;  // test OD 20170411
    private static final double THRESH_LAND_BRIGHT_35RATIO_LOW = 0.82; // test Ishida, H. and Nakajima, T. Y. (2009) DOI: 10.1029/2008JD010710
    private static final double THRESH_LAND_BRIGHT_35RATIO_UP = 0.94;
    public static final double DELTA_RHO_TOA_442_THRESHOLD = 0.03;
    public static final double RHO_TOA_442_THRESHOLD = 0.03;

    public static final double BRIGHT_THRESH = 0.35;//0.25
    public static final double BRIGHT_FOR_WHITE_THRESH = 0.7; //0.8
    public static final double WHITE_THRESH = 0.9;

    ThreadLocal<SchillerNeuralNetWrapper> olciSlstrAllNeuralNet;
    private C3SOlciSlstrCloudNNInterpreter nnInterpreter;


    @Override
    public void initialize() throws OperatorException {
        setBands();
        nnInterpreter = C3SOlciSlstrCloudNNInterpreter.create();
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
        slstrReflBands = new Band[Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length; i++) {
            final int suffixStart = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].indexOf("_");
            final String reflBandname = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].substring(0, suffixStart);
            final int suffixEnd = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].lastIndexOf("_");
            final String reflBandnameSuffix = Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES[i].substring(suffixEnd);
            slstrReflBands[i] = slstrRad2reflProduct.getBand(reflBandname + "_reflectance" + reflBandnameSuffix);
        }
        landWaterBand = waterMaskProduct.getBand("land_water_fraction");
        altitudeBand = sourceProduct.getBand("altitude");
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);
        ProductUtils.copyBand("altitude", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("solar_zenith_tn", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("solar_azimuth_tn", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("sat_zenith_tn", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("sat_azimuth_tn", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("total_column_ozone_tx", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("total_column_water_vapour_tx", sourceProduct, targetProduct,true);;
        ProductUtils.copyBand("surface_pressure_tx", sourceProduct, targetProduct, true);
        ProductUtils.copyBand("elevation_an", sourceProduct, targetProduct, true);
        ProductUtils.copyBand(C3SOlciSlstrConstants.OLCI_QUALITY_FLAGS_BAND_NAME, sourceProduct, targetProduct, true);
        // shall be the only target band!!
        cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        szaBand = targetProduct.addBand("SZA", ProductData.TYPE_FLOAT64);
        saaBand = targetProduct.addBand("SAA", ProductData.TYPE_FLOAT64);
        ozaBand = targetProduct.addBand("OZA", ProductData.TYPE_FLOAT64);
        oaaBand = targetProduct.addBand("OAA", ProductData.TYPE_FLOAT64);
        FlagCoding flagCoding = C3SOlciSlstrUtils.createOlciFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (outputSchillerNNValue) {
            final Band nnValueBand = targetProduct.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
            nnValueBand.setNoDataValue(0.0f);
            nnValueBand.setNoDataValueUsed(true);
        }
    }


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile waterFractionTile = getSourceTile(landWaterBand, rectangle);
        final Tile szaTile = getSourceTile(sourceProduct.getRasterDataNode("SZA"), rectangle);
        final Tile saaTile = getSourceTile(sourceProduct.getRasterDataNode("SAA"), rectangle);
        final Tile ozaTile = getSourceTile(sourceProduct.getRasterDataNode("OZA"), rectangle);
        final Tile oaaTile = getSourceTile(sourceProduct.getRasterDataNode("OAA"), rectangle);

        final Band olciQualityFlagBand = sourceProduct.getBand(C3SOlciSlstrConstants.OLCI_QUALITY_FLAGS_BAND_NAME);
        final Tile olciQualityFlagTile = getSourceTile(olciQualityFlagBand, rectangle);

        final Band slstrCloudAnFlagBand = sourceProduct.getBand(C3SOlciSlstrConstants.SLSTR_CLOUD_AN_FLAG_BAND_NAME);
        final Tile slstrCloudAnFlagTile = getSourceTile(slstrCloudAnFlagBand, rectangle);


        Tile[] olciReflectanceTiles = new Tile[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        double[] olciReflectance = new double[Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
            olciReflectanceTiles[i] = getSourceTile(olciReflBands[i], rectangle);
        }
        Tile[] slstrReflectanceTiles = new Tile[Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length];
        double[] slstrReflectance = new double[Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length];
        for (int i = 0; i < Rad2ReflConstants.C3S_SYN_SLSTR_REFL_BAND_NAMES.length; i++) {
            slstrReflectanceTiles[i] = getSourceTile(slstrReflBands[i], rectangle);
        }
        final Band cloudFlagTargetBand = targetProduct.getBand(IdepixConstants.CLASSIF_BAND_NAME);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        Band szaTargetBand = targetProduct.getBand("SZA");
        Band saaTargetBand = targetProduct.getBand("SAA");
        Band ozaTargetBand = targetProduct.getBand("OZA");
        Band oaaTargetBand = targetProduct.getBand("OAA");

        Tile szaTargetTile = targetTiles.get(szaTargetBand);
        Tile saaTargetTile = targetTiles.get(saaTargetBand);
        Tile ozaTargetTile = targetTiles.get(ozaTargetBand);
        Tile oaaTargetTile = targetTiles.get(oaaTargetBand);

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
                    final double sza = szaTile.getSampleDouble(x, y);
                    final double saa = saaTile.getSampleDouble(x, y);
                    final double oza = ozaTile.getSampleDouble(x, y);
                    final double oaa = oaaTile.getSampleDouble(x, y);
                    szaTargetTile.setSample(x, y, sza);
                    saaTargetTile.setSample(x, y, saa);
                    ozaTargetTile.setSample(x, y, oza);
                    oaaTargetTile.setSample(x, y, oaa);
                    final int waterFraction = waterFractionTile.getSampleInt(x, y);
                    final boolean isL1bLand = olciQualityFlagTile.getSampleBit(x, y, C3SOlciSlstrConstants.L1_F_LAND);
                    //final boolean isLand =
                    //        IdepixUtils.isLandPixel(x, y, sourceProduct.getSceneGeoCoding(), isL1bLand, waterFraction);
                    final boolean isLand = isOlciSlstrLandPixel(x, y, isL1bLand, waterFraction);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, isLand);

                    for (int i = 0; i < Rad2ReflConstants.OLCI_REFL_BAND_NAMES.length; i++) {
                        olciReflectance[i] = olciReflectanceTiles[i].getSampleFloat(x, y);
                    }

                    final boolean l1Invalid = olciQualityFlagTile.getSampleBit(x, y, C3SOlciSlstrConstants.L1_F_INVALID);
                    boolean reflectancesValid = IdepixIO.areAllReflectancesValid(olciReflectance);
                    cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_INVALID, l1Invalid || !reflectancesValid);

                    final boolean isSlstrCloudAn137Thresh =
                            slstrCloudAnFlagTile.getSampleBit(x, y, C3SOlciSlstrConstants.CLOUD_AN_F_137_THRESH);
                    final boolean isSlstrCloudAnGrossCloud =
                            slstrCloudAnFlagTile.getSampleBit(x, y, C3SOlciSlstrConstants.CLOUD_AN_F_GROSS_CLOUD);

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
                            final boolean cloudSureNN = olciReflectance[2] > THRESH_LAND_MINBRIGHT1 &&
                                    nnInterpreter.isCloudSure(nnOutput);
                            final boolean cloudAmbiguousNN = olciReflectance[2] > THRESH_LAND_MINBRIGHT2 &&
                                    nnInterpreter.isCloudAmbiguous(nnOutput);


                            //Ratio S3-867nm/S5-1.64µm thresholds 0.82<=Ratio<=0.92 true = desert, false = cloud -
                            // Ishida, H. and Nakajima, T. Y. (2009)  doi: 10.1029/2008JD010710.
                            //"S3_reflectance_an", "S5_reflectance_an",
                            boolean isSlstrBright35Ratio = ((slstrReflectance[2] / slstrReflectance[4]) >= THRESH_LAND_BRIGHT_35RATIO_LOW) &&
                                    ((slstrReflectance[2] / slstrReflectance[4]) <= THRESH_LAND_BRIGHT_35RATIO_UP) && isLand;

                            final double rhoToa442Thresh = calcRhoToa442ThresholdTerm(sza, oza, saa, oaa);
                            double brightValue = calcbrightValue(olciReflectance[2],  rhoToa442Thresh);
                            double whiteValue = calcwhiteValue(brightValue, olciReflectance);
                            final boolean isBright = brightValue > BRIGHT_THRESH;
                            final boolean isWhite = whiteValue > WHITE_THRESH;

                            // Krijger, J. M. et al. (2011) doi: 10.5194/amt-4-2213-2011.
                            // W43 = (S3/0.795)/S2
                            // W25 = S1/S5
                            // Ice/Snow W43 ≥ 0.77 + (1/(W25 − 0.08)).
                            //S1_reflectance_an", "S2_reflectance_an", "S3_reflectance_an", "S5_reflectance_an",
                            double valueW43 =(slstrReflectance[2]/0.795) / slstrReflectance[1];
                            double valueW25 =slstrReflectance[0]/slstrReflectance[4];

                            boolean isSlstrIceSnow = valueW43 >= 0.77 + (1/(valueW25 - 0.08));

                            final boolean isSnowIce = nnInterpreter.isSnowIce(nnOutput) || isSlstrIceSnow;
                            // request RQ, GK, 20220111:
                            final boolean isSynCloud = (cloudSureNN || isSlstrCloudAn137Thresh || isSlstrCloudAnGrossCloud);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, cloudAmbiguousNN && !isSlstrBright35Ratio && !isSnowIce);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD_SURE, isSynCloud && !isSlstrBright35Ratio && !isSnowIce);

                            final boolean cloudSure = cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_SURE);
                            final boolean cloudAmbiguous = cloudFlagTargetTile.getSampleBit(x, y, IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS);

                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_CLOUD, cloudAmbiguous || cloudSure);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_SNOW_ICE, isSnowIce);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, isBright);
                            cloudFlagTargetTile.setSample(x, y, IdepixConstants.IDEPIX_WHITE, isWhite);
                        }

                        if (nnTargetTile != null) {
                            nnTargetTile.setSample(x, y, nnOutput);
                        }
                        if (nnTargetTile != null) {
                            nnTargetTile.setSample(x, y, nnOutput);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    private double calcRhoToa442ThresholdTerm(double sza, double vza, double saa, double vaa) {
        final double cosThetaScatt = C3SOlciSlstrUtils.calcScatteringCos(sza, vza, saa, vaa);
        return RHO_TOA_442_THRESHOLD + DELTA_RHO_TOA_442_THRESHOLD * cosThetaScatt * cosThetaScatt;
    }

    public double spectralFlatnessValue(double[] refl) {
            final double slope0 = C3SOlciSlstrUtils.spectralSlope(refl[3], refl[2],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[3],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[2]);
            final double slope1 = C3SOlciSlstrUtils.spectralSlope(refl[5], refl[7],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[5],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[7]);
            final double slope2 = C3SOlciSlstrUtils.spectralSlope(refl[10], refl[15],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[10],
                    C3SOlciSlstrConstants.OLCI_WAVELENGTHS[15]);

        final double flatness = 1.0f - Math.abs(1000.0 * (slope0 + slope1 + slope2) / 3.0);
        return Math.max(0.0, flatness);
    }

    public double calcbrightValue( double refl, double brr442Thresh) {
        if (refl <= 0.0 || brr442Thresh <= 0.0) {
            return C3SOlciSlstrConstants.NO_DATA_VALUE;
        } else {
            return (refl / (7.0 * brr442Thresh));
        }
    }

    public double calcwhiteValue(double brightValue, double[] refl) {
        if (brightValue > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue(refl);
        } else {
            return 0.0;
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
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_WHITE, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_BRIGHT, false);
        targetTile.setSample(x, y, IdepixConstants.IDEPIX_LAND, true);   // already checked
    }

    private boolean isOlciSlstrLandPixel(int x, int y, boolean isL1Land, int waterFraction) {
        if (waterFraction < 0) {
            return isL1Land;
        } else {
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (IdepixUtils.getGeoPos(getSourceProduct().getSceneGeoCoding(), x, y).lat > -58f) {
                // values bigger than 100 indicate no data
                if (waterFraction <= 100) {
                    // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                    // is always 0 or 100!! (TS, OD, 20140502)
                    return waterFraction == 0;
                } else {
                    return isL1Land;
                }
            } else {
                return isL1Land;
            }
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(C3SOlciSlstrClassificationOp.class);
        }
    }
}
