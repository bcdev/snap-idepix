package org.esa.snap.idepix.meris.reprocessing;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.dataio.dimap.DimapProductConstants;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
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

}
