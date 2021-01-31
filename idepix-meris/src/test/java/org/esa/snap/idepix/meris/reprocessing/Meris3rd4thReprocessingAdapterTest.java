package org.esa.snap.idepix.meris.reprocessing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.ProductReader;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.dataio.dimap.DimapProductConstants;
import org.esa.snap.core.dataio.dimap.DimapProductWriter;
import org.esa.snap.core.dataio.dimap.DimapProductWriterPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.util.BitSetter;
import org.esa.snap.core.util.DummyProductBuilder;
import org.esa.snap.dataio.envisat.EnvisatConstants;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Meris3rd4thReprocessingAdapterTest {
    // todo

//    private Product createSampleMerisProduct() throws ParseException {
//        final ProductReaderPlugIn readerPlugInMock = mock(ProductReaderPlugIn.class);
//        when(readerPlugInMock.getFormatNames()).thenReturn(new String[]{EnvisatConstants.ENVISAT_FORMAT_NAME});
//
//        final ProductReader readerMock = mock(ProductReader.class);
//        when(readerMock.getReaderPlugIn()).thenReturn(readerPlugInMock);
//
//        final Product source = new DummyProductBuilder()
//                .size(DummyProductBuilder.Size.SMALL)
//                .gc(DummyProductBuilder.GC.MAP)
//                .gp(DummyProductBuilder.GP.ANTI_MERIDIAN)
//                .sizeOcc(DummyProductBuilder.SizeOcc.SINGLE)
//                .gcOcc(DummyProductBuilder.GCOcc.UNIQUE)
//                .create();
//        source.setName("test");
//        source.setProductType("MER_RR__1P");
//        source.setProductReader(readerMock);
//        source.setStartTime(ProductData.UTC.parse("23-MAY-2010 09:59:12.278508"));
//        source.setEndTime(ProductData.UTC.parse("23-MAY-2010 10:02:32.200875"));
//        source.addBand("radiance_1", "20.0").setSpectralWavelength(412.691f);
//        source.addBand("radiance_2", "20.0").setSpectralWavelength(442.55902f);
//        source.addBand("radiance_3", "20.0").setSpectralWavelength(489.88202f);
//        source.addBand("radiance_4", "20.0").setSpectralWavelength(509.81903f);
//        source.addBand("radiance_5", "20.0").setSpectralWavelength(559.69403f);
//        source.addBand("radiance_6", "20.0").setSpectralWavelength(619.601f);
//        source.addBand("radiance_7", "20.0").setSpectralWavelength(664.57306f);
//        source.addBand("radiance_8", "20.0").setSpectralWavelength(680.82104f);
//        source.addBand("radiance_9", "20.0").setSpectralWavelength(708.32904f);
//        source.addBand("radiance_10", "20.0").setSpectralWavelength(753.37103f);
//        source.addBand("radiance_11", "20.0").setSpectralWavelength(761.50806f);
//        source.addBand("radiance_12", "20.0").setSpectralWavelength(778.40906f);
//        source.addBand("radiance_13", "20.0").setSpectralWavelength(864.87604f);
//        source.addBand("radiance_14", "20.0").setSpectralWavelength(884.94403f);
//        source.addBand("radiance_15", "20.0").setSpectralWavelength(900.00006f);
//        source.addBand("sun_zenith", "20.0");
//        source.addBand("sun_azimuth", "20.0");
//        source.addBand("view_zenith", "20.0");
//        source.addBand("view_azimuth", "20.0");
//        source.addBand("atm_press", "20.0");
//        source.addBand("dem_alt", "20.0");
//        source.addBand("ozone", "20.0");
//        Band flagBand = source.addBand("l1_flags", "-1", ProductData.TYPE_UINT8);
//        FlagCoding l1FlagCoding = new FlagCoding("l1_flags");
//        l1FlagCoding.addFlag("INVALID", 1, "INVALID");
//        l1FlagCoding.addFlag("LAND_OCEAN", 2, "LAND or OCEAN");
//        source.getFlagCodingGroup().add(l1FlagCoding);
//        flagBand.setSampleCoding(l1FlagCoding);
//        return source;
//    }

    @Test
    public final void testReadAndWritePixels() throws IOException {
//        final int[] testInt8s = new int[]{3, -6, 9, -12, 15, -18};
//        final int[] testInt16s = new int[]{11, -22, 33, -44, 55, -66};
        final int[] testInt32s = new int[]{111, -222, 333, -444, 555, -666};
        final int[] testUInt8s = new int[]{1, 2, 3, 4, 5, 6};
//        final int[] testUInt16s = new int[]{1001, 2002, 3003, 4004, 5005, 6006};
//        final int[] testUInt32s = new int[]{1111, 2222, 3333, 4444, 5555, 6666};
//        final float[] testFloat32s = new float[]{1.001f, 2.002f, 3.003f, 4.004f, 5.005f, 6.006f};
//        final double[] testFloat64s = new double[]{-15d, 200d, -30000d, 4440d, -5550d, -600000d};

        int[] trueInts = new int[6];
//        float[] trueFloats = new float[6];
//        double[] trueDoubles = new double[6];
        float[] trueScaledFloats = new float[6];
        float[] testScaledFloats;
//        double[] trueScaledDoubles = new double[6];
//        double[] testScaledDoubles;

        String name = "x";
        Product product = new Product(name, "NO_TYPE", 3, 2);

//        Band bandInt8 = new Band("bandInt8", ProductData.TYPE_INT8, 3, 2);
//        bandInt8.ensureRasterData();
//        bandInt8.setPixels(0, 0, 3, 2, testInt8s);
//        product.addBand(bandInt8);
//
//        Band bandInt16 = new Band("bandInt16", ProductData.TYPE_INT16, 3, 2);
//        bandInt16.ensureRasterData();
//        bandInt16.setPixels(0, 0, 3, 2, testInt16s);
//        product.addBand(bandInt16);

        Band bandInt32 = new Band("bandInt32", ProductData.TYPE_INT32, 3, 2);
        bandInt32.ensureRasterData();
        bandInt32.setPixels(0, 0, 3, 2, testInt32s);
        product.addBand(bandInt32);

        Band bandUInt8 = new Band("bandUInt8", ProductData.TYPE_UINT8, 3, 2);
        bandUInt8.ensureRasterData();
        bandUInt8.setPixels(0, 0, 3, 2, testUInt8s);
        product.addBand(bandUInt8);
//
//        Band bandUInt16 = new Band("bandUInt16", ProductData.TYPE_UINT16, 3, 2);
//        bandUInt16.ensureRasterData();
//        bandUInt16.setPixels(0, 0, 3, 2, testUInt16s);
//        product.addBand(bandUInt16);
//
//        Band bandUInt32 = new Band("bandUint32", ProductData.TYPE_UINT32, 3, 2);
//        bandUInt32.ensureRasterData();
//        bandUInt32.setPixels(0, 0, 3, 2, testUInt32s);
//        product.addBand(bandUInt32);
//
//        Band bandFloat32 = new Band("bandFloat32", ProductData.TYPE_FLOAT32, 3, 2);
//        bandFloat32.ensureRasterData();
//        bandFloat32.setPixels(0, 0, 3, 2, testFloat32s);
//        product.addBand(bandFloat32);
//
//        Band bandFloat64 = new Band("bandFloat64", ProductData.TYPE_FLOAT64, 3, 2);
//        bandFloat64.ensureRasterData();
//        bandFloat64.setPixels(0, 0, 3, 2, testFloat64s);
//        product.addBand(bandFloat64);

        final File outputDirectory = new File(".");
        final File file = new File(outputDirectory, name + DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION);
        ProductIO.writeProduct(product,
                file,
                DimapProductConstants.DIMAP_FORMAT_NAME,
                false,
                ProgressMonitor.NULL);

//        product = ProductIO.readProduct(file);
//
//        final DimapProductWriter dimapProductWriter = new DimapProductWriter(new DimapProductWriterPlugIn());
//        assertNull(product.getProductWriter());
//        product.setProductWriter(dimapProductWriter);
//        assertNotNull(product.getProductWriter());
//        dimapProductWriter.writeProductNodes(product, file);

//        bandInt8 = product.getBand("bandInt8");
//        bandInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testInt8s, trueInts));
//        bandInt8.setScalingFactor(0.1);
//        bandInt8.setScalingOffset(1.25);
//        testScaledFloats = new float[]{1.55f, 0.65f, 2.15f, 0.05f, 2.75f, -0.55f};
//        bandInt8.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
//        }
//
//        bandInt8.setScalingFactor(2);
//        bandInt8.setScalingOffset(2);
//        bandInt8.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandInt8.setScalingFactor(1);
//        bandInt8.setScalingOffset(0);
//        bandInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertEquals(true, Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandInt8.setScalingFactor(2);
//        bandInt8.setScalingOffset(2);
//        bandInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));
//
//        bandInt16 = product.getBand("bandInt16");
//        bandInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertEquals(true, Arrays.equals(testInt16s, trueInts));
//        bandInt16.setScalingFactor(0.1);
//        bandInt16.setScalingOffset(1.25);
//        testScaledFloats = new float[]{2.35f, -0.95f, 4.55f, -3.15f, 6.75f, -5.35f};
//        bandInt16.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
//        }
//
//        bandInt16.setScalingFactor(2);
//        bandInt16.setScalingOffset(2);
//        bandInt16.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandInt16.setScalingFactor(1);
//        bandInt16.setScalingOffset(0);
//        bandInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandInt16.setScalingFactor(2);
//        bandInt16.setScalingOffset(2);
//        bandInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));

        bandInt32 = product.getBand("bandInt32");
        bandInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
        assertTrue(Arrays.equals(testInt32s, trueInts));
//        bandInt32.setScalingFactor(0.1);
//        bandInt32.setScalingOffset(1.25);
//        testScaledFloats = new float[]{12.35f, -20.95f, 34.55f, -43.15f, 56.75f, -65.35f};
//        bandInt32.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
//        }
//
//        bandInt32.setScalingFactor(2);
//        bandInt32.setScalingOffset(2);
//        bandInt32.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandInt32.setScalingFactor(1);
//        bandInt32.setScalingOffset(0);
//        bandInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandInt32.setScalingFactor(2);
//        bandInt32.setScalingOffset(2);
//        bandInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));

        bandUInt8 = product.getBand("bandUInt8");
        bandUInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
        assertTrue(Arrays.equals(testUInt8s, trueInts));
        bandUInt8.setScalingFactor(0.1);
        bandUInt8.setScalingOffset(1.25);
        testScaledFloats = new float[]{1.35f, 1.45f, 1.55f, 1.65f, 1.75f, 1.85f};
        bandUInt8.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
        for (int i = 0; i < testScaledFloats.length; i++) {
            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
        }
//
//        bandUInt8.setScalingFactor(2);
//        bandUInt8.setScalingOffset(2);
//        bandUInt8.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandUInt8.setScalingFactor(1);
//        bandUInt8.setScalingOffset(0);
//        bandUInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandUInt8.setScalingFactor(2);
//        bandUInt8.setScalingOffset(2);
//        bandUInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));
//
//        bandUInt16 = product.getBand("bandUInt16");
//        bandUInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testUInt16s, trueInts));
//        bandUInt16.setScalingFactor(0.1);
//        bandUInt16.setScalingOffset(1.25);
//        testScaledFloats = new float[]{101.35f, 201.45f, 301.55f, 401.65f, 501.75f, 601.85f};
//        bandUInt16.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-5f);
//        }
//
//        bandUInt16.setScalingFactor(2);
//        bandUInt16.setScalingOffset(2);
//        bandUInt16.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandUInt16.setScalingFactor(1);
//        bandUInt16.setScalingOffset(0);
//        bandUInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandUInt16.setScalingFactor(2);
//        bandUInt16.setScalingOffset(2);
//        bandUInt16.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));
//
//        bandUInt32 = product.getBand("bandUInt32");
//        bandUInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testUInt32s, trueInts));
//        bandUInt32.setScalingFactor(0.1);
//        bandUInt32.setScalingOffset(1.25);
//        testScaledFloats = new float[]{112.35f, 223.45f, 334.55f, 445.65f, 556.75f, 667.85f};
//        bandUInt32.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
//        }
//
//        bandUInt32.setScalingFactor(2);
//        bandUInt32.setScalingOffset(2);
//        bandUInt32.writePixels(0, 0, 3, 2, new int[]{9, 8, 7, 6, 5, 4}, ProgressMonitor.NULL);
//        bandUInt32.setScalingFactor(1);
//        bandUInt32.setScalingOffset(0);
//        bandUInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{4, 3, 3, 2, 2, 1}, trueInts));
//        bandUInt32.setScalingFactor(2);
//        bandUInt32.setScalingOffset(2);
//        bandUInt32.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new int[]{10, 8, 8, 6, 6, 4}, trueInts));
//
//        bandFloat32 = product.getBand("bandFloat32");
//        bandFloat32.readPixels(0, 0, 3, 2, trueFloats, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testFloat32s, trueFloats));
//        bandFloat32.setScalingFactor(0.1);
//        bandFloat32.setScalingOffset(1.25);
//        testScaledFloats = new float[]{1.3501f, 1.4502f, 1.5503f, 1.6504f, 1.7505f, 1.8506f};
//        bandFloat32.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledFloats.length; i++) {
//            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
//        }
//
//        bandFloat32.setScalingFactor(0.2);
//        bandFloat32.setScalingOffset(3);
//        final float[] testFloats = new float[]{3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f};
//        bandFloat32.writePixels(0, 0, 3, 2, testFloats, ProgressMonitor.NULL);
//        bandFloat32.setScalingFactor(1);
//        bandFloat32.setScalingOffset(0);
//        bandFloat32.readPixels(0, 0, 3, 2, trueFloats, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new float[]{2.5f, 7.5f, 12.5f, 17.5f, 22.5f, 27.5f}, trueFloats));
//        bandFloat32.setScalingFactor(0.2);
//        bandFloat32.setScalingOffset(3);
//        bandFloat32.readPixels(0, 0, 3, 2, trueFloats, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testFloats, trueFloats));
//
//        bandFloat64 = product.getBand("bandFloat64");
//        bandFloat64.readPixels(0, 0, 3, 2, trueDoubles, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testFloat64s, trueDoubles));
//        bandFloat64.setScalingFactor(0.1);
//        bandFloat64.setScalingOffset(1.25);
//        testScaledDoubles = new double[]{-0.25d, 21.25d, -2998.75d, 445.25d, -553.75d, -59998.75d};
//        bandFloat64.readPixels(0, 0, 3, 2, trueScaledDoubles, ProgressMonitor.NULL);
//        for (int i = 0; i < testScaledDoubles.length; i++) {
//            assertEquals(testScaledDoubles[i], trueScaledDoubles[i], 1e-6f);
//        }
//
//        bandFloat64.setScalingFactor(0.2);
//        bandFloat64.setScalingOffset(3);
//        final double[] testDoubles = new double[]{3.5, 4.5, 5.5, 6.5, 7.5, 8.5};
//        bandFloat64.writePixels(0, 0, 3, 2, testDoubles, ProgressMonitor.NULL);
//        bandFloat64.setScalingFactor(1);
//        bandFloat64.setScalingOffset(0);
//        bandFloat64.readPixels(0, 0, 3, 2, trueDoubles, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(new double[]{2.5, 7.5, 12.5, 17.5, 22.5, 27.5}, trueDoubles));
//        bandFloat64.setScalingFactor(0.2);
//        bandFloat64.setScalingOffset(3);
//        bandFloat64.readPixels(0, 0, 3, 2, trueDoubles, ProgressMonitor.NULL);
//        assertTrue(Arrays.equals(testDoubles, trueDoubles));

        product.closeProductReader();
        product.dispose();
    }

    @Test
    public final void testWriteReadUInt8Pixels() throws IOException {
        final int[] testUInt8s = new int[]{1, 2, 3, 4, 5, 6};

        int[] trueInts = new int[6];
        float[] trueScaledFloats = new float[6];
        float[] testScaledFloats;

        String name = "x";
        Product product = new Product(name, "NO_TYPE", 3, 2);

        Band bandUInt8 = new Band("bandUInt8", ProductData.TYPE_UINT8, 3, 2);
        bandUInt8.ensureRasterData();
        bandUInt8.setPixels(0, 0, 3, 2, testUInt8s);
        product.addBand(bandUInt8);

        final File outputDirectory = new File(".");
        final File file = new File(outputDirectory, name + DimapProductConstants.DIMAP_HEADER_FILE_EXTENSION);
        ProductIO.writeProduct(product,
                file,
                DimapProductConstants.DIMAP_FORMAT_NAME,
                false,
                ProgressMonitor.NULL);

        product = ProductIO.readProduct(file);

        bandUInt8 = product.getBand("bandUInt8");
        bandUInt8.readPixels(0, 0, 3, 2, trueInts, ProgressMonitor.NULL);
        assertArrayEquals(testUInt8s, trueInts);
        bandUInt8.setScalingFactor(0.1);
        bandUInt8.setScalingOffset(1.25);
        testScaledFloats = new float[]{1.35f, 1.45f, 1.55f, 1.65f, 1.75f, 1.85f};
        bandUInt8.readPixels(0, 0, 3, 2, trueScaledFloats, ProgressMonitor.NULL);
        for (int i = 0; i < testScaledFloats.length; i++) {
            assertEquals(testScaledFloats[i], trueScaledFloats[i], 1e-6f);
        }

        product.closeProductReader();
        product.dispose();
    }

    @Test
    public void testConvertQualityToL1FlagValue() {
        // quality_flags --> l1_flags
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
        long qualityFlagValue = (int) (Math.pow(2.0, 24) + Math.pow(2.0, 21) + Math.pow(2.0, 30));
        int expectedL1FlagValue = 1 + 8 + 64;
        int l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);

        // quality flag: pixel is duplicated, sun glint risk, land, bright
        qualityFlagValue = (long) (Math.pow(2.0, 23) + Math.pow(2.0, 22) + Math.pow(2.0, 31) + Math.pow(2.0, 27));
        expectedL1FlagValue = 2 + 4 + 16 + 32;
        l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);

        // quality flag: pixel is invalid
        qualityFlagValue = (int) (Math.pow(2.0, 25));
        expectedL1FlagValue = 128;
        l1FlagValue = adapter.convertQualityToL1FlagValue(qualityFlagValue);
        assertEquals(expectedL1FlagValue, l1FlagValue);
    }

    @Test
    public void test32BitFlagMethods() {
        int[] indexes = new int[]{
                0, 1, 4, 5,
                7, 8, 14, 16,
                17, 19, 25, 26,
                28, 31
        };
        int[] results = new int[]{
                1, 2, 16, 32,
                128, 256, 16384, 65536,
                131072, 524288, 33554432, 67108864,
                268435456, -2147483648
        };

        for (int i = 0; i < indexes.length; i++) {
            final int index = indexes[i];
            final int result = results[i];
            int flags = BitSetter.setFlag(0, index);
            assertEquals("i = " + i, result, flags);
            assertTrue("i = " + i, BitSetter.isFlagSet(flags, index));
        }

        int flags = 0;
        for (int i = 0; i < indexes.length; i++) {
            flags = BitSetter.setFlag(flags, indexes[i]);
        }
        assertEquals(-1777647181, flags);
        for (int i = 0; i < 32; i++) {
            boolean expected = false;
            for (int j = 0; j < indexes.length; j++) {
                if (i == indexes[j]) {
                    expected = true;
                    break;
                }
            }
            assertEquals("i = " + i, expected, BitSetter.isFlagSet(flags, i));
        }
    }

}
