package org.esa.snap.idepix.landsat8;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;

import javax.media.jai.RenderedOp;
import javax.media.jai.operator.MultiplyDescriptor;
import java.awt.image.RenderedImage;

/**
 * Operator to generate Landsat8 CLOST product:
 * multiplication of aerosol * blue * pan * cirrus
 * Target product will contain just one band with this image
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.L8.Clost",
                  version = "3.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2016 by Brockmann Consult",
                  description = "Landsat 8 CLOST: provides product from blue, aerosol, pan and cirrus images.")
public class ClostOp extends Operator {

    public static final String CLOST_BAND_NAME = "CLOST";

    @SourceProduct(alias = "l8source", description = "The source product.")
    Product sourceProduct;

    @Override
    public void initialize() throws OperatorException {
        final Band blueBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_BLUE_BAND_NAME);
        final Band cirrusBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_CIRRUS_BAND_NAME);
        final Band aerosolBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_COASTAL_AEROSOL_BAND_NAME);
        final Band panBand = sourceProduct.getBand(Landsat8Constants.LANDSAT8_PANCHROMATIC_BAND_NAME);

        // MPa: try with clost band:
        // get clost image: blue * aerosol * pan * cirrus
        RenderedImage blueImage = blueBand.getGeophysicalImage();
        RenderedImage aerosolImage = aerosolBand.getGeophysicalImage();
        RenderedOp blueAerosolImage = MultiplyDescriptor.create(blueImage, aerosolImage, null);
        RenderedImage panImage = panBand.getGeophysicalImage();
        RenderedOp blueAerosolPanImage = MultiplyDescriptor.create(blueAerosolImage, panImage, null);
        RenderedImage cirrusImage = cirrusBand.getGeophysicalImage();
        RenderedOp blueAerosolPanCirrusImage = MultiplyDescriptor.create(blueAerosolPanImage, cirrusImage, null);

        final Product clostProduct = createClostProduct(blueAerosolPanCirrusImage);
        setTargetProduct(clostProduct);
    }

    private Product createClostProduct(RenderedOp blueAerosolPanCirrusImage) {

        Product product = new Product(sourceProduct.getName() + "_clost",
                                      sourceProduct.getProductType() + " (clost)",
                                      blueAerosolPanCirrusImage.getWidth(),
                                      blueAerosolPanCirrusImage.getHeight());

        product.setSceneGeoCoding(sourceProduct.getSceneGeoCoding());
        product.setDescription("Product holding Clost Image");

        Band band = product.addBand(CLOST_BAND_NAME, ProductData.TYPE_FLOAT32);
        band.setSourceImage(blueAerosolPanCirrusImage);
        band.setUnit("dl");
        band.setDescription("CLOST Image: aerosol * blue * pan * cirrus ");

        return product;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ClostOp.class);
        }
    }

}
