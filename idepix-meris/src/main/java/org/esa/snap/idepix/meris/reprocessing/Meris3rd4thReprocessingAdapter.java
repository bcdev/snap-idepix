package org.esa.snap.idepix.meris.reprocessing;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dataio.envisat.EnvisatConstants;

import javax.media.jai.RenderedOp;
import javax.media.jai.operator.MeanDescriptor;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Meris3rd4thReprocessingAdapter implements ReprocessingAdapter {

    private Map<Integer, Integer> qualityToL1FlagMap = new HashMap<>();

    public Meris3rd4thReprocessingAdapter() {
        setupQualityToL1FlagMap();
    }

    @Override
    public Product convertToLowerVersion(Product inputProduct) {
        Product thirdReproProduct = new Product(inputProduct.getName(),
                inputProduct.getProductType(),
                inputProduct.getSceneRasterWidth(),
                inputProduct.getSceneRasterHeight());

        // adapt band names:
        // ## M%02d_radiance --> radiance_%d
        // ## detector_index --> detector_index
        adaptBandNamesToThirdRepro(inputProduct, thirdReproProduct);

        // adapt tie point grids:
        // TP_latitude --> latitude
        // TP_longitude --> longitude
        // TP_altitude --> dem_alt  // todo: clarify this!
        // n.a. --> dem_rough  // todo: clarify this!
        // n.a. --> lat_corr  // todo: clarify this!
        // n.a. --> lon_corr  // todo: clarify this!
        // SZA --> sun_zenith
        // OZA --> view_zenith
        // SAA --> sun_azimuth
        // OAA --> view_azimuth
        // horiz_wind_vector_1 --> zonal_wind
        // horiz_wind_vector_2 --> merid_wind
        // sea_level_pressure --> atm_press
        // total_ozone --> ozone  // todo: check units!
        // n.a. --> rel_hum  // todo: clarify this!
        adaptTiePointGridsToThirdRepro(inputProduct, thirdReproProduct);

        // adapt flag band and coding:
        // quality_flags --> l1_flags
        // ##  cosmetic (2^24) --> COSMETIC (1)
        // ##  duplicated (2^23) --> DUPLICATED (2)
        // ##  sun_glint_risk (2^22) --> GLINT_RISK (4)
        // ##  dubious (2^21) --> SUSPECT (8)
        // ##  land (2^31) --> LAND_OCEAN (16)
        // ##  bright (2^27) --> BRIGHT (32)
        // ##  coastline (2^30) --> COASTLINE (64)
        // ##  invalid (2^2^25) --> INVALID (128)
        try {
            adaptFlagBandToThirdRepro(inputProduct, thirdReproProduct);
        } catch (IOException e) {
            // todo
            e.printStackTrace();
        }

        // adapt metadata:
        // Idepix MERIS does not use any product metadata
        // --> for the moment, copy just a few elements from element metadataSection:
        // ## acquisition period
        // ## platform
        // ## generalProductInformation
        // ## orbitReference
        // ## qualityInformation
        // ## frameSet
        // ## merisProductInformation
        // todo: discuss what else might be useful
        fillMetadataInThirdRepro(inputProduct, thirdReproProduct);


        return thirdReproProduct;
    }

    /* package local for testing */
    int convertQualityToL1FlagValue(long qualityFlagValue) {
        int l1FlagValue = 0;
        for (int i = 0; i < Integer.SIZE; i++) {
            if (qualityToL1FlagMap.containsKey(i) && BitSetter.isFlagSet(qualityFlagValue, i)) {
                l1FlagValue += qualityToL1FlagMap.get(i);
            }
        }
        return l1FlagValue;
    }

    private void fillMetadataInThirdRepro(Product inputProduct, Product thirdReproProduct) {

    }

    private void adaptFlagBandToThirdRepro(Product inputProduct, Product thirdReproProduct) throws IOException {
        // test
//        ProductUtils.copyFlagBands(inputProduct, thirdReproProduct, true);
//        ProductUtils.copyFlagCodings(inputProduct, thirdReproProduct);

        Band l1FlagBand = createL1bFlagBand(thirdReproProduct);
//        initL1bFlagBandData(l1FlagBand);

        final Band qualityFlagBand = inputProduct.getBand("quality_flags");
        final int width = inputProduct.getSceneRasterWidth();
        final int height = inputProduct.getSceneRasterHeight();
        int[] qualityFlagData = new int[width * height];
        int[] l1FlagData = new int[width * height];
        // todo: qualityFlagData must be long!
        qualityFlagBand.readPixels(0, 0, width, height, qualityFlagData, ProgressMonitor.NULL);
        for (int i = 0; i < width * height; i++) {
            l1FlagData[i] = convertQualityToL1FlagValue(qualityFlagData[i]);
        }

        l1FlagBand.ensureRasterData();
        l1FlagBand.setPixels(0, 0, width, height, l1FlagData);
        l1FlagBand.setSourceImage(l1FlagBand.getSourceImage());
        thirdReproProduct.addBand(l1FlagBand);
    }

    private static void initL1bFlagBandData(Band dataBand) {
        final int width = dataBand.getRasterWidth();
        final int height = dataBand.getRasterHeight();
        final int[] data = new int[width * height];
        Arrays.fill(data, 1);
        dataBand.setPixels(0, 0, width, height, data);
    }

    private void adaptTiePointGridsToThirdRepro(Product inputProduct, Product thirdReproProduct) {
        // adapt tie point grids:
        // TP_latitude --> latitude
        // TP_longitude --> longitude
        // TP_altitude --> dem_alt  // todo: clarify this!
        // n.a. --> dem_rough  // todo: clarify this!
        // n.a. --> lat_corr  // todo: clarify this!
        // n.a. --> lon_corr  // todo: clarify this!
        // SZA --> sun_zenith
        // OZA --> view_zenith
        // SAA --> sun_azimuth
        // OAA --> view_azimuth
        // horizontal_wind_vector_1 --> zonal_wind
        // horizontal_wind_vector_2 --> merid_wind
        // sea_level_pressure --> atm_press
        // total_ozone --> ozone  // todo: check units!
        // n.a. --> rel_hum  // todo: clarify this!

        final TiePointGrid latitudeTargetTPG =
                ProductUtils.copyTiePointGrid("TP_latitude", inputProduct, thirdReproProduct);
        latitudeTargetTPG.setName("latitude");
        latitudeTargetTPG.setDescription("Latitude of the tie points (WGS-84), positive N");
        latitudeTargetTPG.setUnit("deg");

        final TiePointGrid longitudeTargetTPG =
                ProductUtils.copyTiePointGrid("TP_longitude", inputProduct, thirdReproProduct);
        longitudeTargetTPG.setName("longitude");
        longitudeTargetTPG.setDescription("Longitude of the tie points (WGS-84), Greenwich origin, positive E");
        longitudeTargetTPG.setUnit("deg");

        final TiePointGrid altitudeTargetTPG =
                ProductUtils.copyTiePointGrid("TP_altitude", inputProduct, thirdReproProduct);
        altitudeTargetTPG.setName("dem_alt");
        altitudeTargetTPG.setDescription("Digital elevation model altitude");

        final TiePointGrid szaTargetTPG =
                ProductUtils.copyTiePointGrid("SZA", inputProduct, thirdReproProduct);
        szaTargetTPG.setName("sun_zenith");
        szaTargetTPG.setDescription("Viewing zenith angles");
        szaTargetTPG.setUnit("deg");

        final TiePointGrid saaTargetTPG =
                ProductUtils.copyTiePointGrid("SAA", inputProduct, thirdReproProduct);
        saaTargetTPG.setName("sun_azimuth");
        saaTargetTPG.setDescription("Sun azimuth angles");
        saaTargetTPG.setUnit("deg");
        // SAA: 4th --> 3rd: if (saa <= 0.0) then saa += 360.0
        final float[] saaTiePoints = saaTargetTPG.getTiePoints();
        for (int i = 0; i < saaTiePoints.length; i++) {
            if (saaTiePoints[i] <= 0.0) {
                saaTiePoints[i] += 360.0;
            }
        }

        final TiePointGrid vzaTargetTPG =
                ProductUtils.copyTiePointGrid("OZA", inputProduct, thirdReproProduct);
        vzaTargetTPG.setName("view_zenith");

        final TiePointGrid vaaTargetTPG =
                ProductUtils.copyTiePointGrid("OAA", inputProduct, thirdReproProduct);
        vaaTargetTPG.setName("view_azimuth");
        vaaTargetTPG.setDescription("Viewing azimuth angles");
        vaaTargetTPG.setUnit("deg");
        // OAA: 4th --> 3rd: if (oaa <= 0.0) then oaa += 360.0
        final float[] vaaTiePoints = vaaTargetTPG.getTiePoints();
        for (int i = 0; i < vaaTiePoints.length; i++) {
            if (vaaTiePoints[i] <= 0.0) {
                vaaTiePoints[i] += 360.0;
            }
        }

        final TiePointGrid zonalWindTPG =
                ProductUtils.copyTiePointGrid("horizontal_wind_vector_1", inputProduct, thirdReproProduct);
        zonalWindTPG.setName("zonal_wind");
        zonalWindTPG.setDescription("Zonal wind");

        final TiePointGrid meridionalWindTPG =
                ProductUtils.copyTiePointGrid("horizontal_wind_vector_2", inputProduct, thirdReproProduct);
        meridionalWindTPG.setName("merid_wind");
        meridionalWindTPG.setDescription("Meridional wind");

        final TiePointGrid atmPressTPG =
                ProductUtils.copyTiePointGrid("sea_level_pressure", inputProduct, thirdReproProduct);
        atmPressTPG.setName("atm_press");

        final TiePointGrid ozoneTPG =
                ProductUtils.copyTiePointGrid("total_ozone", inputProduct, thirdReproProduct);
        ozoneTPG.setName("ozone");
        ozoneTPG.setDescription("Total ozone");
        ozoneTPG.setUnit("DU");
        double conversionFactor = 1.0 / 2.1415e-5;  // convert from kg/m2 to DU, https://sacs.aeronomie.be/info/dobson.php
        final float[] ozoneTiePoints = ozoneTPG.getTiePoints();
        for (int i = 0; i < ozoneTiePoints.length; i++) {
            ozoneTiePoints[i] *= conversionFactor;
        }
    }

    private void adaptBandNamesToThirdRepro(Product inputProduct, Product thirdReproProduct) {
        // adapt band names:
        // ## M%02d_radiance --> radiance_%d
        // ## detector_index --> detector_index
        Band detectorIndexTargetBand =
                ProductUtils.copyBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME,
                        inputProduct, thirdReproProduct, true);
        final Band detectorIndexSourceBand = inputProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME);
        detectorIndexTargetBand.setDescription(detectorIndexSourceBand.getDescription());
        detectorIndexTargetBand.setNoDataValueUsed(false);
        detectorIndexTargetBand.setNoDataValue(0.0);
        detectorIndexTargetBand.setValidPixelExpression(null);

        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final String inputBandName = "M" + String.format("%02d", i + 1) + "_radiance";
            final String thirdReproBandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
            final Band inputRadianceBand = inputProduct.getBand(inputBandName);
            final Band targetRadianceBand = ProductUtils.copyBand(inputBandName, inputProduct,
                    thirdReproBandName, thirdReproProduct, true);
            copyGeneralBandProperties(inputRadianceBand, targetRadianceBand);
            copySpectralBandProperties(inputRadianceBand, targetRadianceBand);

            // set solar flux:
            final Band solarFluxBand = inputProduct.getBand("solar_flux_band_" + (i + 1));
            // todo: solar flux is not needed for Idepix, but later for general adapter. Discuss how to set.
            targetRadianceBand.setSolarFlux(getMeanSolarFluxFrom4thReprocessing(solarFluxBand));

            // set valid pixel expression:
//            targetRadianceBand.setValidPixelExpression("!l1_flags.INVALID");
        }
    }

    private static void copyGeneralBandProperties(Band sourceBand, Band targetBand) {
        targetBand.setUnit(sourceBand.getUnit());
    }

    private static void copySpectralBandProperties(Band sourceBand, Band targetBand) {
        targetBand.setDescription("TOA radiance band " + sourceBand.getSpectralBandIndex());
        targetBand.setSpectralBandIndex(sourceBand.getSpectralBandIndex());
        targetBand.setSpectralWavelength(sourceBand.getSpectralWavelength());
        targetBand.setSpectralBandwidth(sourceBand.getSpectralBandwidth());
        targetBand.setNoDataValueUsed(false);
        targetBand.setNoDataValue(0.0);
    }

    private static float getMeanSolarFluxFrom4thReprocessing(Band solarFluxBand) {
        final MultiLevelImage sourceImage = solarFluxBand.getSourceImage();
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(sourceImage);// The source image
        pb.add(null);    // null ROI means whole image
        pb.add(1);       // check every pixel horizontally
        pb.add(1);       // check every pixel vertically

        // Perform the mean operation on the source image.
        final RenderedOp meanImage = MeanDescriptor.create(sourceImage, null, 1, 1, null);
        double[] mean = (double[]) meanImage.getProperty("mean");

        return (float) mean[0];
    }

    public static Band createL1bFlagBand(Product thirdReproProduct) {

        Band l1FlagBand = new Band("l1_flags", ProductData.TYPE_INT32,
                thirdReproProduct.getSceneRasterWidth(), thirdReproProduct.getSceneRasterHeight());
        l1FlagBand.setDescription("Level 1b classification and quality flags");

        FlagCoding l1FlagCoding = new FlagCoding("l1_flags");
        l1FlagCoding.addFlag("COSMETIC", BitSetter.setFlag(0, 0), "Pixel is cosmetic");
        l1FlagCoding.addFlag("DUPLICATED", BitSetter.setFlag(0, 1), "Pixel has been duplicated (filled in)");
        l1FlagCoding.addFlag("GLINT_RISK", BitSetter.setFlag(0, 2), "Pixel has glint risk");
        l1FlagCoding.addFlag("SUSPECT", BitSetter.setFlag(0, 3), "Pixel is suspect");
        l1FlagCoding.addFlag("LAND_OCEAN", BitSetter.setFlag(0, 4), "Pixel is over land, not ocean");
        l1FlagCoding.addFlag("BRIGHT", BitSetter.setFlag(0, 5), "Pixel is bright");
        l1FlagCoding.addFlag("COASTLINE", BitSetter.setFlag(0, 6), "Pixel is part of a coastline");
        l1FlagCoding.addFlag("INVALID", BitSetter.setFlag(0, 7), "Pixel is invalid");

        l1FlagBand.setSampleCoding(l1FlagCoding);
        thirdReproProduct.getFlagCodingGroup().add(l1FlagCoding);

        return l1FlagBand;
    }

    private void setupQualityToL1FlagMap() {
        // quality_flags --> l1_flags
        // ##  cosmetic (2^24) --> COSMETIC (1)
        // ##  duplicated (2^23) --> DUPLICATED (2)
        // ##  sun_glint_risk (2^22) --> GLINT_RISK (4)
        // ##  dubious (2^21) --> SUSPECT (8)
        // ##  land (2^31) --> LAND_OCEAN (16)
        // ##  bright (2^27) --> BRIGHT (32)
        // ##  coastline (2^30) --> COASTLINE (64)
        // ##  invalid (2^25) --> INVALID (128)
        qualityToL1FlagMap.put(24, 1);
        qualityToL1FlagMap.put(23, 2);
        qualityToL1FlagMap.put(22, 4);
        qualityToL1FlagMap.put(21, 8);
        qualityToL1FlagMap.put(31, 16);
        qualityToL1FlagMap.put(27, 32);
        qualityToL1FlagMap.put(30, 64);
        qualityToL1FlagMap.put(25, 128);
    }


    @Override
    public Product convertToHigherVersion(Product inputProduct) {
        // todo: implement
        return null;
    }
}
