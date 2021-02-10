package org.esa.snap.idepix.olci.mountainshadow;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.util.io.FileUtils;
import org.esa.snap.idepix.olci.IdepixOlciConstants;
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
public class SlopeAspectOrientation_IntegrationTest {

    private File targetDirectory;

    @Before
    public void setUp() {
        targetDirectory = new File("sao_test_out");
        if (!targetDirectory.mkdirs()) {
            fail("Unable to create test target directory");
        }
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new IdepixOlciSlopeAspectOrientationOp.Spi());
    }

    @After
    public void tearDown() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(new IdepixOlciSlopeAspectOrientationOp.Spi());
        if (targetDirectory.isDirectory()) {
            if (!FileUtils.deleteTree(targetDirectory)) {
                fail("Unable to delete test directory");
            }
        }
    }

    @Test
    public void testSlopeAspectOrientationOp() throws FactoryException, TransformException, IOException {
        final int width = 3;
        final int height = 3;
        final Product product = new Product("SAO_Test", "sao_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);

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

        final Band elevationBand = new Band(IdepixOlciConstants.OLCI_ALTITUDE_BAND_NAME, ProductData.TYPE_FLOAT32, width, height);
        float[] elevationData = new float[]{
                10.0f, 15.0f, 12.5f,
                12.0f, 14.0f, 13.0f,
                14.0f, 12.0f, 11.0f};
        elevationBand.setDataElems(elevationData);
        product.addBand(elevationBand);

        final Map<String, Object> parameters = new HashMap<>();
        final Product targetProduct = GPF.createProduct("Idepix.Olci.SlopeAspect", parameters, product);
        final String targetFilePath = targetDirectory.getPath() + File.separator + "sao_test.dim";
        ProductIO.writeProduct(targetProduct, targetFilePath, "BEAM-DIMAP");

        assertTrue(targetProduct.containsBand(IdepixOlciSlopeAspectOrientationOp.SLOPE_BAND_NAME));
        assertTrue(targetProduct.containsBand(IdepixOlciSlopeAspectOrientationOp.ASPECT_BAND_NAME));
        assertTrue( targetProduct.containsBand(IdepixOlciSlopeAspectOrientationOp.ORIENTATION_BAND_NAME));
    }

}
