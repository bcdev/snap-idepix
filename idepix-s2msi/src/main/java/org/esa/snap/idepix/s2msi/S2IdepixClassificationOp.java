package org.esa.snap.idepix.s2msi;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.esa.snap.watermask.operator.WatermaskClassifier;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.Map;

import static org.esa.snap.idepix.core.IdepixConstants.LAND_WATER_MASK_RESOLUTION;
import static org.esa.snap.idepix.core.IdepixConstants.OVERSAMPLING_FACTOR_X;
import static org.esa.snap.idepix.core.IdepixConstants.OVERSAMPLING_FACTOR_Y;

/**
 * Sentinel-2 (MSI) pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.S2.Classification",
        version = "3.1",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2008, 2012 by Brockmann Consult",
        description = "Operator for pixel classification from Sentinel-2 MSI data.")
public class S2IdepixClassificationOp extends Operator {

    public static final double DELTA_RHO_TOA_442_THRESHOLD = 0.03;
    public static final double RHO_TOA_442_THRESHOLD = 0.03;

    private static final float WATER_MASK_SOUTH_BOUND = -58.0f;

    private static final String VALID_PIXEL_EXPRESSION = "B1.raw > 0 " +
            "and B2.raw > 0 " +
            "and B3.raw > 0 " +
            "and B4.raw > 0 " +
            "and B5.raw > 0 " +
            "and B6.raw > 0 " +
            "and B7.raw > 0 " +
            "and B8.raw > 0 " +
            "and B8A.raw > 0 " +
            "and B9.raw > 0 " +
            "and B11.raw > 0 " +
            "and B12.raw > 0";

    @Parameter(defaultValue = "false",
            label = " Write feature values to the target product",
            description = " Write all feature values to the target product")
    private boolean copyFeatureValues;

    @Parameter(defaultValue = "0.007",
            label = " Threshold CW_THRESH",
            description = " Threshold CW_THRESH")
    private double cwThresh;

    @Parameter(defaultValue = "-0.11",
            label = " Threshold GCL_THRESH",
            description = " Threshold GCL_THRESH")
    private double gclThresh;

    @Parameter(defaultValue = "0.007",
            label = " Threshold CL_THRESH",
            description = " Threshold CL_THRESH")
    private double clThresh;

    @Parameter(defaultValue = "true", label = " Classify invalid pixels as land/water")
    private boolean classifyInvalid;

    @SourceProduct(alias = "l1c", description = "The MSI L1C source product.")
    Product sourceProduct;

    @SourceProduct(alias = "elevation", description = "The elevation product.")
    Product elevationProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    // hidden parameter, may be set to true for mosaics with larger invalid areas
    private boolean skipInvalidTiles;

    private Band[] s2MsiReflBands;
    Band classifFlagBand;

    Band szaBand;
    Band vzaBand;
    Band saaBand;
    Band vaaBand;

    Mask validPixelMask;

    // features:
    Band temperatureBand;
    Band brightBand;
    Band whiteBand;
    Band brightWhiteBand;
    Band spectralFlatnessBand;
    Band ndviBand;
    Band ndsiBand;
    Band glintRiskBand;
    Band radioLandBand;
    Band radioWaterBand;
    Band b3b11Band;
    Band tc1Band;
    Band tc4Band;
    Band tc4CirrusBand;
    Band ndwiBand;

    private WatermaskClassifier watermaskClassifier;

    @Override
    public void initialize() throws OperatorException {
        skipInvalidTiles = Boolean.getBoolean("snap.idepix.s2msi.skipInvalidTiles");

        validateInputBandsExist(S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES);
        validateInputBandsExist(S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES);

        setBands();

        validPixelMask = Mask.BandMathsType.create("__valid_pixel_mask", null,
                getSourceProduct().getSceneRasterWidth(),
                getSourceProduct().getSceneRasterHeight(),
                VALID_PIXEL_EXPRESSION,
                Color.GREEN, 0.0);
        validPixelMask.setOwner(getSourceProduct());

        boolean isHigherResolutionInput = sourceProduct.getBand("B2") != null
                && sourceProduct.getBand("B2").getGeoCoding().getMapCRS().getName().toString().contains("UTM")
                && sourceProduct.getBand("B2").getImageToModelTransform().getScaleX() < LAND_WATER_MASK_RESOLUTION;
        try {
            watermaskClassifier = new WatermaskClassifier(LAND_WATER_MASK_RESOLUTION,
                    isHigherResolutionInput ? 1 : OVERSAMPLING_FACTOR_X,
                    isHigherResolutionInput ? 1 : OVERSAMPLING_FACTOR_Y);
        } catch (IOException e) {
            throw new OperatorException("Could not initialise SRTM land-water mask", e);
        }

        createTargetProduct();
        extendTargetProduct();
    }

    private void validateInputBandsExist(String[] bandNames) {
        for (String bandName : bandNames) {
            if (!sourceProduct.containsBand(bandName)) {
                throw new OperatorException("Band " + bandName + " not found in source product");
            }
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        Tile[] s2ReflectanceTiles = new Tile[S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length];
        float[] s2MsiReflectance = new float[S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length; i++) {
            s2ReflectanceTiles[i] = getSourceTile(s2MsiReflBands[i], rectangle);
        }


        final Band cloudFlagTargetBand = targetProduct.getBand(S2IdepixConstants.IDEPIX_CLASSIF_FLAGS);
        final Tile cloudFlagTargetTile = targetTiles.get(cloudFlagTargetBand);

        final Tile szaTile = getSourceTile(szaBand, rectangle);
        final Tile vzaTile = getSourceTile(vzaBand, rectangle);
        final Tile saaTile = getSourceTile(saaBand, rectangle);
        final Tile vaaTile = getSourceTile(vaaBand, rectangle);

        final Band elevationBand = targetProduct.getBand(S2IdepixConstants.ELEVATION_BAND_NAME);
        final Tile elevationTile = getSourceTile(elevationBand, rectangle);
        final Tile validPixelTile = getSourceTile(validPixelMask, rectangle);


        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                checkForCancellation();
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                    // avoid application of algo for invalid inputs
                    // TODO check whether land/water flag shall be set for invalid pixels as well
                    if (skipInvalidTiles && ! validPixelTile.getSampleBoolean(x, y)) {
                        cloudFlagTargetTile.setSample(x, y, S2IdepixConstants.IDEPIX_INVALID, true);
                        continue;
                    }

                    // set up pixel properties for given instruments...
                    S2IdepixAlgorithm s2MsiAlgorithm = createS2MsiAlgorithm(s2ReflectanceTiles,
                            szaTile, vzaTile, saaTile, vaaTile,
                            elevationTile,
                            validPixelTile,
                            watermaskClassifier,
                            s2MsiReflectance,
                            y,
                            x);

                    setCloudFlag(cloudFlagTargetTile, y, x, s2MsiAlgorithm);

                    // for given instrument, compute more pixel properties and write to distinct band
                    for (Band band : targetProduct.getBands()) {
                        final Tile targetTile = targetTiles.get(band);
                        setPixelSamples(band, targetTile, y, x, s2MsiAlgorithm);
                    }
                }
            }

        } catch (Exception e) {
            throw new OperatorException("Failed to provide GA cloud screening:\n" + e.getMessage(), e);
        }
    }

    public void setBands() {
        s2MsiReflBands = new Band[S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length];
        for (int i = 0; i < S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length; i++) {
            s2MsiReflBands[i] = sourceProduct.getBand(S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES[i]);
        }

        szaBand = sourceProduct.getBand(S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[0]);
        vzaBand = sourceProduct.getBand(S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[1]);
        saaBand = sourceProduct.getBand(S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[2]);
        vaaBand = sourceProduct.getBand(S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[3]);
    }

    public void extendTargetProduct() throws OperatorException {
        for (String bandName : S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES) {
            if (!targetProduct.containsBand(bandName)) {
                final Band band = ProductUtils.copyBand(bandName, sourceProduct, targetProduct, true);
                band.setUnit("dl");
            }
        }

        for (String s2MsiAnnotationBandName : S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES) {
            ProductUtils.copyBand(s2MsiAnnotationBandName, sourceProduct, targetProduct, true);
        }

        Band b = ProductUtils.copyBand(S2IdepixConstants.ELEVATION_BAND_NAME, elevationProduct, targetProduct, true);
        b.setUnit("m");

        if (sourceProduct.containsBand("lat") && !targetProduct.containsBand("lat")) {
            Band latBand = ProductUtils.copyBand("lat", sourceProduct, targetProduct, true);
            latBand.setUnit("deg");
        }

        if (sourceProduct.containsBand("lon") && !targetProduct.containsBand("lon")) {
            Band lonBand = ProductUtils.copyBand("lon", sourceProduct, targetProduct, true);
            lonBand.setUnit("deg");
        }

    }

    private S2IdepixAlgorithm createS2MsiAlgorithm(Tile[] s2MsiReflectanceTiles,
                                                   Tile szaTile, Tile vzaTile, Tile saaTile, Tile vaaTile,
                                                   Tile elevationTile,
                                                   Tile validPixelTile,
                                                   WatermaskClassifier classifier, float[] s2MsiReflectances,
                                                   int y,
                                                   int x) {
        S2IdepixAlgorithm s2MsiAlgorithm = new S2IdepixAlgorithm();

        for (int i = 0; i < S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES.length; i++) {
            s2MsiReflectances[i] = s2MsiReflectanceTiles[i].getSampleFloat(x, y);
        }
        s2MsiAlgorithm.setRefl(s2MsiReflectances);

        final byte waterFraction = classifier.getWaterMaskFraction(sourceProduct.getSceneGeoCoding(), x, y);

        boolean isLand = isLandPixel(x, y, waterFraction, s2MsiAlgorithm);
        s2MsiAlgorithm.setIsLand(isLand);

        final double sza = szaTile.getSampleDouble(x, y);
        final double vza = vzaTile.getSampleDouble(x, y);
        final double saa = saaTile.getSampleDouble(x, y);
        final double vaa = vaaTile.getSampleDouble(x, y);
        s2MsiAlgorithm.setLat(getGeoPos(x, y).lat);
        final double elevation = elevationTile.getSampleDouble(x, y);
        s2MsiAlgorithm.setElevation(elevation);
        final double rhoToa442Thresh = calcRhoToa442ThresholdTerm(sza, vza, saa, vaa);
        s2MsiAlgorithm.setRhoToa442Thresh(rhoToa442Thresh);

        s2MsiAlgorithm.setCwThresh(cwThresh);
        s2MsiAlgorithm.setGclThresh(gclThresh);
        s2MsiAlgorithm.setClThresh(clThresh);

        final boolean isValid = validPixelTile.getSampleBoolean(x, y);
        s2MsiAlgorithm.setInvalid(!isValid);

        return s2MsiAlgorithm;
    }

    private boolean isLandPixel(int x, int y, int waterFraction, S2IdepixAlgorithm s2MsiAlgorithm) {
        if (getGeoPos(x, y).lat > WATER_MASK_SOUTH_BOUND) {
            // values bigger than 100 indicate no data
            if (waterFraction <= 100) {
                // todo: this does not work if we have a PixelGeocoding. In that case, waterFraction
                // is always 0 or 100!! (TS, OD, 20140502)
                return waterFraction == 0;
            } else {
                return s2MsiAlgorithm.aPrioriLandValue() > S2IdepixAlgorithm.LAND_THRESH;
            }
        } else {
            return s2MsiAlgorithm.aPrioriLandValue() > S2IdepixAlgorithm.LAND_THRESH;
        }
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = sourceProduct.getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    private double calcRhoToa442ThresholdTerm(double sza, double vza, double saa, double vaa) {
        final double cosThetaScatt = S2IdepixUtils.calcScatteringCos(sza, vza, saa, vaa);
        return RHO_TOA_442_THRESHOLD + DELTA_RHO_TOA_442_THRESHOLD * cosThetaScatt * cosThetaScatt;
    }

    void createTargetProduct() throws OperatorException {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), sceneWidth, sceneHeight);

        classifFlagBand = targetProduct.addBand(S2IdepixConstants.IDEPIX_CLASSIF_FLAGS, ProductData.TYPE_INT32);
        FlagCoding flagCoding = S2IdepixUtils.createIdepixFlagCoding(S2IdepixConstants.IDEPIX_CLASSIF_FLAGS);
        classifFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyMetadata(sourceProduct, targetProduct);

        if (copyFeatureValues) {
            brightBand = targetProduct.addBand("bright_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(brightBand, "Brightness", "dl", S2IdepixConstants.NO_DATA_VALUE, true);
            whiteBand = targetProduct.addBand("white_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(whiteBand, "Whiteness", "dl", S2IdepixConstants.NO_DATA_VALUE, true);
            brightWhiteBand = targetProduct.addBand("bright_white_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(brightWhiteBand, "Brightwhiteness", "dl", S2IdepixConstants.NO_DATA_VALUE,
                    true);
            spectralFlatnessBand = targetProduct.addBand("spectral_flatness_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(spectralFlatnessBand, "Spectral Flatness", "dl",
                    S2IdepixConstants.NO_DATA_VALUE, true);
            ndviBand = targetProduct.addBand("ndvi_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(ndviBand, "NDVI", "dl", S2IdepixConstants.NO_DATA_VALUE, true);
            ndsiBand = targetProduct.addBand("ndsi_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(ndsiBand, "NDSI", "dl", S2IdepixConstants.NO_DATA_VALUE, true);
            radioLandBand = targetProduct.addBand("radiometric_land_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(radioLandBand, "Radiometric Land Value", "", S2IdepixConstants.NO_DATA_VALUE,
                    true);
            radioWaterBand = targetProduct.addBand("radiometric_water_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(radioWaterBand, "Radiometric Water Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

            b3b11Band = targetProduct.addBand("b3b11_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(b3b11Band, "B3 B11 Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

            tc1Band = targetProduct.addBand("tc1_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(tc1Band, "TC1 Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

            tc4Band = targetProduct.addBand("tc4_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(tc4Band, "TC4 Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

            tc4CirrusBand = targetProduct.addBand("tc4cirrus_water_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(tc4CirrusBand, "TC4 Cirrus Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

            ndwiBand = targetProduct.addBand("ndwi_value", ProductData.TYPE_FLOAT32);
            S2IdepixUtils.setNewBandProperties(ndwiBand, "NDWI Value", "",
                    S2IdepixConstants.NO_DATA_VALUE, true);

        }

    }

    void setPixelSamples(Band band, Tile targetTile, int y, int x, S2IdepixAlgorithm s2Algorithm) {
        // for given instrument, compute more pixel properties and write to distinct band
        if (band == brightBand) {
            targetTile.setSample(x, y, s2Algorithm.brightValue());
        } else if (band == whiteBand) {
            targetTile.setSample(x, y, s2Algorithm.whiteValue());
        } else if (band == brightWhiteBand) {
            targetTile.setSample(x, y, s2Algorithm.brightValue() + s2Algorithm.whiteValue());
        } else if (band == temperatureBand) {
            targetTile.setSample(x, y, s2Algorithm.temperatureValue());
        } else if (band == spectralFlatnessBand) {
            targetTile.setSample(x, y, s2Algorithm.spectralFlatnessValue());
        } else if (band == ndviBand) {
            targetTile.setSample(x, y, s2Algorithm.ndviValue());
        } else if (band == ndsiBand) {
            targetTile.setSample(x, y, s2Algorithm.ndsiValue());
        } else if (band == glintRiskBand) {
            targetTile.setSample(x, y, s2Algorithm.glintRiskValue());
        } else if (band == radioLandBand) {
            targetTile.setSample(x, y, s2Algorithm.radiometricLandValue());
        } else if (band == radioWaterBand) {
            targetTile.setSample(x, y, s2Algorithm.radiometricWaterValue());
        } else if (band == b3b11Band) {
            targetTile.setSample(x, y, s2Algorithm.b3b11Value());
        } else if (band == tc1Band) {
            targetTile.setSample(x, y, s2Algorithm.tc1Value());
        } else if (band == tc4Band) {
            targetTile.setSample(x, y, s2Algorithm.tc4Value());
        } else if (band == tc4CirrusBand) {
            targetTile.setSample(x, y, s2Algorithm.tc4CirrusValue());
        } else if (band == ndwiBand) {
            targetTile.setSample(x, y, s2Algorithm.ndwiValue());
        }
    }

    void setCloudFlag(Tile targetTile, int y, int x, S2IdepixAlgorithm s2Algorithm) {
        // for given instrument, compute boolean pixel properties and write to cloud flag band
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_INVALID, s2Algorithm.isInvalid());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLOUD, s2Algorithm.isCloud());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLOUD_SURE, s2Algorithm.isCloudSure());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, s2Algorithm.isCloudAmbiguous());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CIRRUS_SURE, s2Algorithm.isCirrus());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CIRRUS_AMBIGUOUS, s2Algorithm.isCirrusAmbiguous());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLOUD_SHADOW, false); // not computed here
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLEAR_LAND, s2Algorithm.isClearLand());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLEAR_WATER, s2Algorithm.isClearWater());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_SNOW_ICE, s2Algorithm.isClearSnow());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_LAND, s2Algorithm.isLand());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_WATER, s2Algorithm.isWater());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_BRIGHT, s2Algorithm.isBright());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_WHITE, s2Algorithm.isWhite());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_BRIGHTWHITE, s2Algorithm.isBrightWhite());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_VEG_RISK, s2Algorithm.isVegRisk());
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_MOUNTAIN_SHADOW, false); //not computed here
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_POTENTIAL_SHADOW, false); //not computed here
        targetTile.setSample(x, y, S2IdepixConstants.IDEPIX_CLUSTERED_CLOUD_SHADOW, false); //not computed here
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(S2IdepixClassificationOp.class);
        }
    }
}
