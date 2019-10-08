package org.esa.snap.idepix.core.operators;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.util.ProductUtils;

// todo (mp/20160311) BrrBasisOp, MerisBasisOp and BasisOp are all more or less the same --> harmonize

public abstract class BasisOp extends Operator {

    /**
     * Creates a new product with the same size
     *
     * @param sourceProduct the source product
     * @param name the name of the newly created product
     * @param type the product type of the newly created product
     *
     * @return targetProduct
     */
    protected Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductTrunk(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies basic information for a MERIS product to the target product
     * To the basic information belong the tie-point grids and the geo-coding.
     *
     * @param sourceProduct the source product
     * @param targetProduct the target product to get the information from
     */
    private void copyProductTrunk(Product sourceProduct,
                                  Product targetProduct) {
        // copy all tie point grids to output product
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        // copy geo-coding to the output product
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(BasisOp.class);
        }
    }
}
