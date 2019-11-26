package org.esa.snap.idepix.core.util;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.ProductUtils;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class OperatorUtils {

    private OperatorUtils() {
    }

    /**
     * Creates a new product with the same size.
     * Copies geocoding and the start and stop time.
     */
    public static Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies geocoding and the start and stop time.
     */
    private static void copyProductBase(Product sourceProduct, Product targetProduct) {
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }
}
