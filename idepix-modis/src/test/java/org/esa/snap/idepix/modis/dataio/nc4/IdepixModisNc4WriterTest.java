package org.esa.snap.idepix.modis.dataio.nc4;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IdepixModisNc4WriterTest {

    public static void main(String[] args) throws Exception {
        final String idepixMerisFilename = "subset_IDEPIX_MODIS.nc";
        final String idepixMerisFilePath = IdepixModisNc4WriterTest.class.getResource(idepixMerisFilename).getPath();
        final Product idepixMerisProduct = loadIdepixMerisProduct(idepixMerisFilePath);
        final String idepixMerisNc4Filename = "subset_IDEPIX_MODIS_nc4.nc";
        final String idepixMerisNc4FilePath = new File(idepixMerisFilePath).getParent() + File.separator + idepixMerisNc4Filename;
        Logger.getGlobal().log(Level.INFO, "Writing IDEPIX-MODIS NetCDF4 file '" + idepixMerisNc4FilePath + "'...");
        ProductIO.writeProduct(idepixMerisProduct, idepixMerisNc4FilePath, "NetCDF4-IDEPIX-MODIS");
    }

    private static Product loadIdepixMerisProduct(String idepixMerisFilePath) {
        Product tcwvProduct = null;
        try {
            Logger.getGlobal().log(Level.INFO, "Reading IDEPIX-MODIS file '" + idepixMerisFilePath + "'...");
            tcwvProduct = ProductIO.readProduct(new File(idepixMerisFilePath));
        } catch (IOException e) {
            Logger.getGlobal().log(Level.WARNING, "Warning: cannot open or read IDEPIX-MODIS file.");
        }
        return tcwvProduct;
    }
}
