package org.esa.snap.idepix.olci;

import com.bc.ceres.core.ProgressMonitor;
import eu.esa.snap.core.datamodel.group.BandGroup;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

/**
 * todo
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "IdepixOlciMergeO2Harmonize",
        category = "Raster",
        description = "Allows copying raster data from any number of source products to a specified 'master' product.",
        authors = "SNAP team",
        internal = true,
        version = "1.0",
        copyright = "(c) 2025 by Brockmann Consult")
public class IdepixOlciMergeO2HarmoniseOp extends Operator {

    @SourceProduct(alias = "l1bProduct",
            label = "OLCI L1b product",
            description = "The OLCI L1b source product.")
    private Product l1bProduct;

    @SourceProduct(alias = "o2harmoProduct",
            label = "OLCI O2A harmonisation product",
            description = "The OLCI O2A harmonisation product.")
    private Product o2harmoProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands", label = "Source Bands")
    private String[] sourceBandNames;

    @Parameter(defaultValue = "1.0E-5f",
            description = "Defines the maximum lat/lon error in degree between the products.")
    private float geographicError;

    private final String[] excludeBandNames = {"Oa13_radiance", "Oa14_radiance", "Oa15_radiance"};
    private final String[] excludeBandNamePrefixes = {"lambda0", "FWHM"};
    private final String[] excludeBandNameSufffixes = {"unc"};

    @Override
    public void initialize() throws OperatorException {
        targetProduct = new Product(l1bProduct.getName(),
                l1bProduct.getProductType(),
                l1bProduct.getSceneRasterWidth(),
                l1bProduct.getSceneRasterHeight());

        if (!l1bProduct.isCompatibleProduct(o2harmoProduct, geographicError)) {
            throw new OperatorException("O2A harmonisation product is not compatible to L1b product.");
        }

        ProductUtils.copyProductNodes(l1bProduct, targetProduct);

        for (Band band : l1bProduct.getBands()) {
            // copy only bands required by OLCI Idepix
            if (!targetProduct.containsRasterDataNode(band.getName())) {
                boolean doCopy = true;
                for (String excludeBandName : excludeBandNames) {
                    if (band.getName().equals(excludeBandName)) {
                        doCopy = false;
                        break;
                    }
                }
                if (doCopy) {
                    for (String excludeBandNamePrefix : excludeBandNamePrefixes) {
                        if (band.getName().startsWith(excludeBandNamePrefix)) {
                            doCopy = false;
                            break;
                        }
                    }
                }
                if (doCopy) {
                    for (String excludeBandNameSufffix : excludeBandNameSufffixes) {
                        if (band.getName().endsWith(excludeBandNameSufffix)) {
                            doCopy = false;
                            break;
                        }
                    }
                }
                if (doCopy) {
                    ProductUtils.copyBand(band.getName(), l1bProduct, targetProduct, true);
                }
            }
        }

        for (Band band : o2harmoProduct.getBands()) {
            if (!targetProduct.containsRasterDataNode(band.getName())) {
                if (!band.getName().startsWith("radiance")) {
                    ProductUtils.copyBand(band.getName(), o2harmoProduct, targetProduct, true);
                } else {
                    final int length = band.getName().length();
                    final String bandIndex = band.getName().substring(length - 2, length);
                    System.out.println("bandIndex = " + bandIndex);
                    final String l1bRadBandName = "Oa" + bandIndex + "_radiance";
                    ProductUtils.copyBand(band.getName(), o2harmoProduct, l1bRadBandName, targetProduct, true);
                    ProductUtils.copySpectralBandProperties(l1bProduct.getBand(l1bRadBandName),
                            targetProduct.getBand(l1bRadBandName));
                }
            }
        }

        mergeAutoGrouping(l1bProduct);
        ProductUtils.copyMasks(l1bProduct, targetProduct);
        ProductUtils.copyOverlayMasks(l1bProduct, targetProduct);

    }

    private void mergeAutoGrouping(Product srcProduct) {
        final BandGroup srcAutoGrouping = srcProduct.getAutoGrouping();
        if (srcAutoGrouping != null && !srcAutoGrouping.isEmpty()) {
            final BandGroup targetAutoGrouping = targetProduct.getAutoGrouping();
            if (targetAutoGrouping == null) {
                targetProduct.setAutoGrouping(srcAutoGrouping);
            } else {
                for (String[] grouping : srcAutoGrouping) {
                    if (!targetAutoGrouping.contains(grouping)) {
                        targetProduct.setAutoGrouping(targetAutoGrouping + ":" + srcAutoGrouping);
                    }
                }
            }
        }
    }


    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        getLogger().warning("Wrongly configured operator. Tiles should not be requested.");
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixOlciMergeO2HarmoniseOp.class);
        }
    }
}

