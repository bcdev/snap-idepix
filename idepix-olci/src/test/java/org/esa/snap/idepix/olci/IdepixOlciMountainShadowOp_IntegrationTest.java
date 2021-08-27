package org.esa.snap.idepix.olci;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.util.io.FileUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.olci.IdepixOlciConstants;
import org.esa.snap.idepix.olci.IdepixOlciMountainShadowOp;
import org.esa.snap.idepix.olci.IdepixOlciSlopeAspectOrientationOp;
import org.geotools.referencing.CRS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Tonio Fincke, Olaf Danne
 */
public class IdepixOlciMountainShadowOp_IntegrationTest {

    private File targetDirectory;

    @Before
    public void setUp() {
        targetDirectory = new File("ms_test_out");
        if (!targetDirectory.mkdirs()) {
            fail("Unable to create test target directory");
        }
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new IdepixOlciMountainShadowOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new IdepixOlciSlopeAspectOrientationOp.Spi());
    }

    @After
    public void tearDown() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(new IdepixOlciMountainShadowOp.Spi());
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(new IdepixOlciSlopeAspectOrientationOp.Spi());
        if (targetDirectory.isDirectory()) {
            if (!FileUtils.deleteTree(targetDirectory)) {
                fail("Unable to delete test directory");
            }
        }
    }

    @Test
    public void testMountainShadowOp_MissingSAO() throws FactoryException, TransformException, IOException {
        final int width = 3;
        final int height = 3;
        final Product product = new Product("MS_Test", "ms_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);
        final Band elevationBand = new Band(IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] elevationData = new float[]{
                10.0f, 15.0f, 12.5f,
                12.0f, 14.0f, 13.0f,
                14.0f, 12.0f, 11.0f};
        elevationBand.setDataElems(elevationData);
        product.addBand(elevationBand);
        final Band szaBand = new Band(IdepixOlciConstants.OLCI_SUN_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] szaData = new float[]{
                85.001f, 85.002f, 85.004f,
                85.011f, 85.012f, 85.014f,
                85.031f, 85.032f, 85.034f};
        szaBand.setDataElems(szaData);
        product.addBand(szaBand);
        final Band saaBand = new Band(IdepixOlciConstants.OLCI_SUN_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] saaData = new float[]{
                150.001f, 150.002f, 150.004f,
                150.011f, 150.012f, 150.014f,
                150.031f, 150.032f, 150.034f};
        saaBand.setDataElems(saaData);
        product.addBand(saaBand);

        final Band ozaBand = new Band(IdepixOlciConstants.OLCI_VIEW_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] ozaData = new float[]{
                85.002f, 85.003f, 85.004f,
                85.012f, 85.013f, 85.014f,
                85.032f, 85.033f, 85.034f};
        ozaBand.setDataElems(ozaData);
        product.addBand(ozaBand);
        final Band oaaBand = new Band(IdepixOlciConstants.OLCI_VIEW_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] oaaData = new float[]{
                150.002f, 150.003f, 150.004f,
                150.012f, 150.013f, 150.014f,
                150.032f, 150.033f, 150.034f};
        oaaBand.setDataElems(oaaData);
        product.addBand(oaaBand);

        final Band latitudeBand = new Band(IdepixOlciConstants.OLCI_LATITUDE_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] latitudeData = new float[]{
                10.0f, 15.0f, 12.5f,
                12.0f, 14.0f, 13.0f,
                14.0f, 12.0f, 11.0f};
        latitudeBand.setDataElems(latitudeData);
        product.addBand(latitudeBand);

        final Band longitudeBand = new Band(IdepixOlciConstants.OLCI_LONGITUDE_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] longitudeData = new float[]{
                10.0f, 15.0f, 12.5f,
                12.0f, 14.0f, 13.0f,
                14.0f, 12.0f, 11.0f};
        longitudeBand.setDataElems(longitudeData);
        product.addBand(longitudeBand);

        final Band classifBand = new Band(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT32, width, height);
        int[] classifData = new int[]{
                0, 0, 0,
                0, 0, 0,
                0, 0, 0};
        classifBand.setDataElems(classifData);
        product.addBand(classifBand);

        final Map<String, Object> parameters = new HashMap<>();
        final Product targetProduct = GPF.createProduct("Idepix.Olci.MountainShadow", parameters, product);
        final String targetFilePath = targetDirectory.getPath() + File.separator + "ms_test.dim";
        ProductIO.writeProduct(targetProduct, targetFilePath, "BEAM-DIMAP");

        assertTrue(targetProduct.containsBand(IdepixOlciMountainShadowOp.MOUNTAIN_SHADOW_FLAG_BAND_NAME));
    }

    @Test
    public void testMountainShadowOp_ExistingSAO() throws FactoryException, TransformException, IOException {
        final int width = 3;
        final int height = 3;
        final Product product = new Product("MS_Test", "ms_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);
        final Band slopeBand = new Band(IdepixOlciSlopeAspectOrientationOp.SLOPE_BAND_NAME,
                ProductData.TYPE_FLOAT32, width, height);
        float[] slopeData = new float[]{
                0.32035214f, 0.11440312f, 0.22131443f,
                0.22345093f, 0.14396477f, 0.124354996f,
                0.049958397f, 0.0f, 0.14048971f};
        slopeBand.setDataElems(slopeData);
        product.addBand(slopeBand);
        final Band aspectBand = new Band(IdepixOlciSlopeAspectOrientationOp.ASPECT_BAND_NAME,
                ProductData.TYPE_FLOAT32, width, height);
        float[] aspectData = new float[]{
                -1.62733972f, 1.96140337f, 1.57079637f,
                -2.12064958f, 3.01189017f, 1.57079637f,
                -0.f, -3.14159274f, 2.3561945f};
        aspectBand.setDataElems(aspectData);
        product.addBand(aspectBand);
        final Band orientationBand = new Band(IdepixOlciSlopeAspectOrientationOp.ORIENTATION_BAND_NAME,
                ProductData.TYPE_FLOAT32, width, height);
        float[] orientationData = new float[]{
                0.021341953f, 0.02063076f, 0.021341952f,
                0.02134193f, 0.02134193f, 0.02134193f,
                0.021341879f, 0.02063069f, 0.021341879f};
        orientationBand.setDataElems(orientationData);
        product.addBand(orientationBand);
        final Band szaBand = new Band(IdepixOlciConstants.OLCI_SUN_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] szaData = new float[]{
                85.002f, 85.003f, 85.004f,
                85.012f, 85.013f, 85.014f,
                85.032f, 85.033f, 85.034f};
        szaBand.setDataElems(szaData);
        product.addBand(szaBand);
        final Band saaBand = new Band(IdepixOlciConstants.OLCI_SUN_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] saaData = new float[]{
                150.002f, 150.003f, 150.004f,
                150.012f, 150.013f, 150.014f,
                150.032f, 150.033f, 150.034f};
        saaBand.setDataElems(saaData);
        product.addBand(saaBand);

        final Band ozaBand = new Band(IdepixOlciConstants.OLCI_VIEW_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] ozaData = new float[]{
                85.002f, 85.003f, 85.004f,
                85.012f, 85.013f, 85.014f,
                85.032f, 85.033f, 85.034f};
        ozaBand.setDataElems(ozaData);
        product.addBand(ozaBand);
        final Band oaaBand = new Band(IdepixOlciConstants.OLCI_VIEW_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] oaaData = new float[]{
                150.002f, 150.003f, 150.004f,
                150.012f, 150.013f, 150.014f,
                150.032f, 150.033f, 150.034f};
        oaaBand.setDataElems(oaaData);
        product.addBand(oaaBand);

        final Band elevationBand = new Band(IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] elevationData = new float[]{
                15.0f, 17.5f, 12.5f,
                14.0f, 16.0f, 13.0f,
                12.0f, 14.0f, 11.0f};
        elevationBand.setDataElems(elevationData);
        product.addBand(elevationBand);

        final Band classifBand = new Band(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT32, width, height);
        int[] classifData = new int[]{
                0, 0, 0,
                0, 0, 0,
                0, 0, 0};
        classifBand.setDataElems(classifData);
        product.addBand(classifBand);

        final Map<String, Object> parameters = new HashMap<>();
        final Product targetProduct = GPF.createProduct("Idepix.Olci.MountainShadow", parameters, product);
        final String targetFilePath = targetDirectory.getPath() + File.separator + "ms_test.dim";
        ProductIO.writeProduct(targetProduct, targetFilePath, "BEAM-DIMAP");

        assertTrue(targetProduct.containsBand(IdepixOlciMountainShadowOp.MOUNTAIN_SHADOW_FLAG_BAND_NAME));
    }

}
