package org.esa.snap.idepix.meris.reprocessing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.dimap.DimapProductConstants;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.dataio.envisat.EnvisatConstants;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Meris3rd4thReprocessingAdapterTest {

    @Test
    public final void testWriteReadUInt32Pixels() throws IOException {
        final int[] testUInt32s = new int[]{1, 2, 3, 4, 5, 6};

        int[] trueUInt32s = new int[6];
        float[] trueScaledFloats = new float[6];
        float[] testScaledFloats;

        String name = "x";
        Product product = new Product(name, "NO_TYPE", 3, 2);

        Band bandUInt32 = new Band("bandUInt32", ProductData.TYPE_UINT32, 3, 2);
        bandUInt32.ensureRasterData();
        product.addBand(bandUInt32);
        bandUInt32.setPixels(0, 0, 3, 2, testUInt32s);

        final File outputDirectory = new File(".");
        final File file = new File(outputDirectory, name + DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION);
        ProductIO.writeProduct(product,
                file,
                DimapProductConstants.DIMAP_FORMAT_NAME,
                false,
                ProgressMonitor.NULL);

        product = ProductIO.readProduct(file);

        bandUInt32 = product.getBand("bandUInt32");
        bandUInt32.readPixels(0, 0, 3, 2, trueUInt32s, ProgressMonitor.NULL);
        assertArrayEquals(testUInt32s, trueUInt32s);
        bandUInt32.setScalingFactor(0.1);
        bandUInt32.setScalingOffset(1.25);
        testScaledFloats = new float[]{1.35f, 1.45f, 1.55f, 1.65f, 1.75f, 1.85f};
        bandUInt32.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
        for (int i = 0; i < testScaledFloats.length; i++) {
            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
        }

        product.closeProductReader();
        product.dispose();
    }

    @Test
    public void testConvertQualityToL1FlagValue() {
        // mapping quality_flags --> l1_flags:
        // ##  cosmetic (2^24) --> COSMETIC (1)
        // ##  duplicated (2^23) --> DUPLICATED (2)
        // ##  sun_glint_risk (2^22) --> GLINT_RISK (4)
        // ##  dubious (2^21) --> SUSPECT (8)
        // ##  land (2^31) --> LAND_OCEAN (16)
        // ##  bright (2^27) --> BRIGHT (32)
        // ##  coastline (2^30) --> COASTLINE (64)
        // ##  invalid (2^25) --> INVALID (128)

        final Meris3rd4thReprocessingAdapter adapter = new Meris3rd4thReprocessingAdapter();

        // quality flag: pixel is cosmetic, dubious, coastline
        // NOTE: quality_flag is uint32 in the 4RP product, we treat it as a long here
        long qualityFlagValue = (long) (Math.pow(2.0, 24) + Math.pow(2.0, 21) + Math.pow(2.0, 30));
        int expectedL1FlagValue = 1 + 8 + 64;
        int l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);

        // quality flag: pixel is duplicated, sun glint risk, land, bright
        qualityFlagValue = (long) (Math.pow(2.0, 23) + Math.pow(2.0, 22) + Math.pow(2.0, 31) + Math.pow(2.0, 27));
        expectedL1FlagValue = 2 + 4 + 16 + 32;
        l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);

        // quality flag: pixel is invalid
        qualityFlagValue = (long) (Math.pow(2.0, 25));
        expectedL1FlagValue = 128;
        l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);
    }

    @Test
    public void testLowerVersionProductContainsAllBands() throws IOException {
        Product fourthReproProduct = createFourthReproTestProduct();

        final File outputDirectory = new File(".");
        final File fileFourthRepro = new File(outputDirectory, "ME_1_RRG_4RP_test" +
                DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION);
        ProductIO.writeProduct(fourthReproProduct,
                fileFourthRepro,
                DimapProductConstants.DIMAP_FORMAT_NAME,
                false,
                ProgressMonitor.NULL);

        Meris3rd4thReprocessingAdapter adapter = new Meris3rd4thReprocessingAdapter();
        final Product thirdReproProduct = adapter.convertToLowerVersion(fourthReproProduct);
        final File fileThirdRepro = new File(outputDirectory, "ME_1_RRG_3RP_test" +
                DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION);
        ProductIO.writeProduct(thirdReproProduct,
                fileThirdRepro,
                DimapProductConstants.DIMAP_FORMAT_NAME,
                false,
                ProgressMonitor.NULL);

//        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
//            final String fourthReproSpectralBandName = "M" + String.format("%02d", i + 1) + "_radiance";
//            final String thirdReproSpectralBandName = EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i];
//        }
    }

    private Product createFourthReproTestProduct() {
        Product product = new Product("ME_1_RRG_4RP_test", "ME_1_RRG", 3, 2);

        // add bands
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final String fourthReproSpectralBandName = "M" + String.format("%02d", i + 1) + "_radiance";
            Band fourthReproSpectralBand = new Band(fourthReproSpectralBandName, ProductData.TYPE_FLOAT32, 3, 2);
            fourthReproSpectralBand.ensureRasterData();
            product.addBand(fourthReproSpectralBand);
            float[] testFloat32s = new float[]{i * 1.f, i * 2.f, i * 3.f, i * 4.f, i * 5.f, i * 6.f};
            fourthReproSpectralBand.setPixels(0, 0, 3, 2, testFloat32s);

            Band solarFluxBand = new Band("solar_flux_band_" + (i + 1), ProductData.TYPE_FLOAT32, 3, 2);
            solarFluxBand.ensureRasterData();
            product.addBand(solarFluxBand);
            float[] testFloat32sSolarFlux = new float[]{1400 + i * 1.f, 1400 + i * 2.f, 1400 + i * 3.f,
                    1400 + i * 4.f, 1400 + i * 5.f, 1400 + i * 6.f};
            solarFluxBand.setPixels(0, 0, 3, 2, testFloat32sSolarFlux);
        }

        Band detectorIndexBand = new Band("detector_index", ProductData.TYPE_INT16, 3, 2);
        detectorIndexBand.ensureRasterData();
        product.addBand(detectorIndexBand);
        int[] testInt16sDetectorIndex = new int[]{111, 222, 333, 444, 555, 666};
        detectorIndexBand.setPixels(0, 0, 3, 2, testInt16sDetectorIndex);

        Band qualityFlagBand = new Band("quality_flags", ProductData.TYPE_UINT32, 3, 2);
        qualityFlagBand.ensureRasterData();
        product.addBand(qualityFlagBand);
        int[] testInt16sQualityFlag = new int[]{
                (int) (Math.pow(2.0, 24) + Math.pow(2.0, 21) + Math.pow(2.0, 30)),
                (int) (Math.pow(2.0, 23) + Math.pow(2.0, 22) + Math.pow(2.0, 31)),
                (int) (Math.pow(2.0, 22) + Math.pow(2.0, 27) + Math.pow(2.0, 30)),
                (int) (Math.pow(2.0, 24) + Math.pow(2.0, 31)),
                (int) (Math.pow(2.0, 24) + Math.pow(2.0, 27) + Math.pow(2.0, 31)),
                (int) (Math.pow(2.0, 25)),
        };
        qualityFlagBand.setPixels(0, 0, 3, 2, testInt16sQualityFlag);

        // add tie point grids:
        // TP_latitude --> latitude
        // TP_longitude --> longitude
        // TP_altitude --> dem_alt
        // SZA --> sun_zenith
        // OZA --> view_zenith
        // SAA --> sun_azimuth
        // OAA --> view_azimuth
        // horizontal_wind_vector_1 --> zonal_wind
        // horizontal_wind_vector_2 --> merid_wind
        // sea_level_pressure --> atm_press
        // total_ozone --> ozone
        // humidity_pressure_level_14 --> rel_hum  // relative humidity at 850 hPa

        float[] tpLatTestData = new float[]{31.f, 32.f, 33.f, 27.f, 28.f, 29.f};
        TiePointGrid tpLatTpg = new TiePointGrid("TP_latitude", 3, 2, 0, 0, 1, 1, tpLatTestData);
        product.addTiePointGrid(tpLatTpg);

        float[] tpLonTestData = new float[]{51.f, 52.f, 33.f, 51.f, 52.f, 53.f};
        TiePointGrid tpLonTpg = new TiePointGrid("TP_longitude", 3, 2, 0, 0, 1, 1, tpLonTestData);
        product.addTiePointGrid(tpLonTpg);

        float[] tpAltTestData = new float[]{234.f, 567.f, 789.f, 1133.f, 1244.f, 1355.f};
        TiePointGrid tpAltTpg = new TiePointGrid("TP_altitude", 3, 2, 0, 0, 1, 1, tpAltTestData);
        product.addTiePointGrid(tpAltTpg);

        float[] szaTestData = new float[]{41.f, 42.f, 43.f, 47.f, 48.f, 49.f};
        TiePointGrid szaTpg = new TiePointGrid("SZA", 3, 2, 0, 0, 1, 1, szaTestData);
        product.addTiePointGrid(szaTpg);

        float[] saaTestData = new float[]{-14.f, -24.f, -34.f, 74.f, 84.f, 89.f};
        TiePointGrid saaTpg = new TiePointGrid("SAA", 3, 2, 0, 0, 1, 1, saaTestData);
        product.addTiePointGrid(saaTpg);

        float[] ozaTestData = new float[]{11.f, 22.f, 33.f, 12.f, 23.f, 34.f};
        TiePointGrid ozaTpg = new TiePointGrid("OZA", 3, 2, 0, 0, 1, 1, ozaTestData);
        product.addTiePointGrid(ozaTpg);

        float[] oaaTestData = new float[]{-55.f, -56.f, -57.f, 58.f, 59.f, 60.f};
        TiePointGrid oaaTpg = new TiePointGrid("OAA", 3, 2, 0, 0, 1, 1, oaaTestData);
        product.addTiePointGrid(oaaTpg);

        float[] zonalWindTestData = new float[]{10.f, 11.f, 12.f, 16.f, 17.f, 18.f};
        TiePointGrid zonalWindTpg = new TiePointGrid("horizontal_wind_vector_1", 3, 2, 0, 0, 1, 1, zonalWindTestData);
        product.addTiePointGrid(zonalWindTpg);

        float[] meridionalWindTestData = new float[]{5.f, 6.f, 7.f, 2.f, 3.f, 4.f};
        TiePointGrid meridionalWindTpg = new TiePointGrid("horizontal_wind_vector_2", 3, 2, 0, 0, 1, 1, meridionalWindTestData);
        product.addTiePointGrid(meridionalWindTpg);

        float[] slpTestData = new float[]{1005.f, 1006.f, 1008.f, 1002.f, 1003.f, 1004.f};
        TiePointGrid slpTpg = new TiePointGrid("sea_level_pressure", 3, 2, 0, 0, 1, 1, slpTestData);
        product.addTiePointGrid(slpTpg);

        float[] ozoneTestData = new float[]{0.005f, 0.0055f, 0.006f, 0.0065f, 0.007f, 0.0075f};
        TiePointGrid ozoneTpg = new TiePointGrid("total_ozone", 3, 2, 0, 0, 1, 1, ozoneTestData);
        product.addTiePointGrid(ozoneTpg);

        float[] relHumTestData = new float[]{2.34f, 13.7f, 11.5f, 33.4f, 35.8f, 39.2f};
        TiePointGrid relHumTpg = new TiePointGrid("humidity_pressure_level_14", 3, 2, 0, 0, 1, 1, relHumTestData);
        product.addTiePointGrid(relHumTpg);

        product.setAutoGrouping("M*_radiance:solar_flux");

        // metadata
        final MetadataElement manifestElement = new MetadataElement("Manifest");
        product.getMetadataRoot().addElement(manifestElement);
        final MetadataElement metadataSectionElement = new MetadataElement("metadataSection");
        manifestElement.addElement(metadataSectionElement);
        final MetadataElement generalProductInformationElement = new MetadataElement("generalProductInformation");
        metadataSectionElement.addElement(generalProductInformationElement);
        generalProductInformationElement.addAttribute(new MetadataAttribute("productName",
                ProductData.createInstance(product.getName()), true));
        final MetadataElement acquisitionPeriodElement = new MetadataElement("acquisitionPeriod");
        metadataSectionElement.addElement(acquisitionPeriodElement);
        acquisitionPeriodElement.addAttribute(new MetadataAttribute("startTime",
                ProductData.createInstance("2011-07-02T14:08:01.955726Z"), true));
        acquisitionPeriodElement.addAttribute(new MetadataAttribute("stopTime",
                ProductData.createInstance("2011-07-02T14:51:57.552001Z"), true));
        final MetadataElement orbitReferenceElement = new MetadataElement("orbitReference");
        orbitReferenceElement.addAttribute(new MetadataAttribute("cycleNumber",
                ProductData.createInstance(new int[]{104}), true));
        final MetadataElement orbitNumberElement = new MetadataElement("orbitNumber");
        orbitNumberElement.addAttribute(new MetadataAttribute("orbitNumber",
                ProductData.createInstance(new int[]{48832}), true));
        orbitReferenceElement.addElement(orbitNumberElement);
        final MetadataElement relativeOrbitNumberElement = new MetadataElement("relativeOrbitNumber");
        relativeOrbitNumberElement.addAttribute(new MetadataAttribute("relativeOrbitNumber",
                ProductData.createInstance(new int[]{111}), true));
        orbitReferenceElement.addElement(relativeOrbitNumberElement);
        metadataSectionElement.addElement(orbitReferenceElement);

        return product;
    }
}
