package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.junit.Test;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.ProductUtils;
import org.geotools.referencing.CRS;

import java.util.Map;

import static org.junit.Assert.*;

public class CloudShadowResamplingTest {

    @Test
    public void usesMeanForContinuousBandsAndOrForClassificationFlags() {
        final Map<String, Object> parameters = S2IdepixCloudShadowOp.createCloudShadowResamplingParameters();

        assertEquals("Nearest", parameters.get("upsampling"));
        assertEquals("Mean", parameters.get("downsampling"));
        assertEquals("FlagOr", parameters.get("flagDownsampling"));
        assertEquals(60, parameters.get("targetResolution"));
        assertEquals(4, parameters.size());
    }

    @Test
    public void preservesReaderValidityDependenciesAndMaskedMeanWithoutModifyingInput() throws Exception {
        Product original = new Product("L1C", "S2", 12, 12);
        original.setSceneGeoCoding(new CrsGeoCoding(CRS.decode("EPSG:32632"), 12, 12,
                699960, 5300040, 10, 10, 0, 0));
        original.setPreferredTileSize(7, 5);
        Band reflectance = original.addBand("B3", ProductData.TYPE_FLOAT32);
        Band detector = original.addBand("B_detector_footprint_B3", ProductData.TYPE_INT32);
        Band noData = original.addBand("B_nodata_B3", ProductData.TYPE_INT32);
        // Include a recursive validity dependency and an unrelated band that must not be copied.
        Band quality = original.addBand("quality", ProductData.TYPE_INT32);
        original.addBand("unneeded", ProductData.TYPE_FLOAT32);
        String validity = "B3.raw >= -0.1 && B_detector_footprint_B3.raw > 0 && B_nodata_B3 == 0";
        reflectance.setValidPixelExpression(validity);
        detector.setValidPixelExpression("quality == 0");
        float[] values = new float[144];
        int[] detectors = new int[144];
        int[] noDatas = new int[144];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % 5 == 0 ? 999 : i * 0.01f;
            detectors[i] = i % 5 == 0 ? 0 : 1;
            noDatas[i] = i % 7 == 0 ? 1 : 0;
        }
        reflectance.setRasterData(ProductData.createInstance(values));
        detector.setRasterData(ProductData.createInstance(detectors));
        noData.setRasterData(ProductData.createInstance(noDatas));
        quality.setRasterData(ProductData.createInstance(new int[144]));
        Product intermediate = new Product("classification", "S2", 12, 12);
        ProductUtils.copyGeoCoding(original, intermediate);
        intermediate.setPreferredTileSize(original.getPreferredTileSize());
        ProductUtils.copyBand("B3", original, intermediate, true);

        Product repaired = S2IdepixCloudShadowOp.createCloudShadowResamplingSource(intermediate, original, "B3");
        assertEquals(4, repaired.getNumBands());
        assertNull(repaired.getBand("unneeded"));
        assertNotNull(repaired.getBand("quality"));
        assertEquals(validity, repaired.getBand("B3").getValidPixelExpression());
        assertEquals("quality == 0", repaired.getBand("B_detector_footprint_B3").getValidPixelExpression());
        assertEquals(original.getPreferredTileSize(), repaired.getPreferredTileSize());
        assertEquals(1, intermediate.getNumBands());
        assertEquals(5, original.getNumBands());
        assertEquals(validity, reflectance.getValidPixelExpression());
        Product expected = GPF.createProduct("Resample", S2IdepixCloudShadowOp.createCloudShadowResamplingParameters(), original);
        Product actual = GPF.createProduct("Resample", S2IdepixCloudShadowOp.createCloudShadowResamplingParameters(), repaired);
        int w = actual.getSceneRasterWidth();
        int h = actual.getSceneRasterHeight();
        float[] expectedPixels = expected.getBand("B3").readPixels(0, 0, w, h, new float[w * h]);
        assertArrayEquals(expectedPixels, actual.getBand("B3").readPixels(0, 0, w, h, new float[w * h]), 0.0f);
        // This assertion makes sure a repair that simply drops validity checks cannot pass.
        for (float value : expectedPixels) {
            assertTrue(Float.isNaN(value) || value < 2.0f);
        }
    }

    @Test(expected = OperatorException.class)
    public void missingValidityDependencyFailsRatherThanDiscardingMask() {
        Product source = new Product("flags", "S2", 6, 6);
        source.addBand("B3", ProductData.TYPE_FLOAT32).setValidPixelExpression("missing > 0");
        S2IdepixCloudShadowOp.createCloudShadowResamplingSource(source, source, "B3");
    }

    @Test
    public void standardFlagOrAndSharedMaterializationMatchAt10mAnd20m() throws Exception {
        for (int resolution : new int[]{10, 20}) {
            Product source = new Product("classification", "S2", 31, 19);
            source.setSceneGeoCoding(new CrsGeoCoding(CRS.decode("EPSG:32632"), 31, 19,
                    699960, 4000020, resolution, resolution, 0, 0));
            source.setPreferredTileSize(7, 5);
            Band flags = source.addBand("pixel_classif_flags", ProductData.TYPE_INT32);
            FlagCoding coding = new FlagCoding("flags");
            coding.addFlag("cloud", 1, "cloud");
            source.getFlagCodingGroup().add(coding);
            flags.setSampleCoding(coding);
            flags.setNoDataValue(-999);
            flags.setNoDataValueUsed(false);
            int[] values = new int[31 * 19];
            for (int i = 0; i < values.length; i++) {
                values[i] = i % 11 == 0 ? -999 : 1 << (i % 14);
            }
            flags.setRasterData(ProductData.createInstance(values));
            Product expected = GPF.createProduct("Resample",
                    S2IdepixCloudShadowOp.createCloudShadowResamplingParameters(), source);
            Product view = S2IdepixCloudShadowOp.createCloudShadowResamplingSource(source, source,
                    "pixel_classif_flags");
            assertTrue(view.getBand("pixel_classif_flags").isFlagBand());
            Product resampled = GPF.createProduct("Resample",
                    S2IdepixCloudShadowOp.createCloudShadowResamplingParameters(), view);
            Product materialized = S2IdepixCloudShadowOp.materializeCloudShadowClassificationProduct(resampled);
            int w = expected.getSceneRasterWidth();
            int h = expected.getSceneRasterHeight();
            assertArrayEquals(expected.getBand("pixel_classif_flags").readPixels(0, 0, w, h, new int[w * h]),
                    materialized.getBand("pixel_classif_flags").readPixels(0, 0, w, h, new int[w * h]));
            assertEquals(expected.getSceneGeoCoding().getImageToMapTransform(),
                    materialized.getSceneGeoCoding().getImageToMapTransform());
        }
    }
}
