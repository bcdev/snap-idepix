package org.esa.snap.idepix.core.seaice;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;

import java.io.File;
import java.io.IOException;

public class LakeSeaIceClassification {

    private static final String DEFAULT_ICE_MAPS_DIR_NAME = "1c_icelake_icesea_mask_1grad";

    private Product monthlyMaskProduct;
    private Band monthlyMaskBand;

    public LakeSeaIceClassification(Product userMaskProduct, String iceMapsAuxdataDir, int month) {
        try {
            provideMaskData(userMaskProduct, iceMapsAuxdataDir, month);
        } catch (IOException e) {
            e.printStackTrace();  // todo
        }
    }

    public float getMonthlyMaskValue(int x, int y) {
        final int rasterWidth = monthlyMaskBand.getRasterWidth();
        int rasterDataIndex = y * rasterWidth + x;
        return monthlyMaskBand.getRasterData().getElemFloatAt(rasterDataIndex);
    }

    Product getMonthlyMaskProduct() {
        return monthlyMaskProduct;
    }

    private void provideMaskData(Product userMaskProduct, String iceMapsAuxdataDir, int month) throws IOException {
        if (userMaskProduct == null) {
            // get mask data from default climatology. todo: make names more generic if needed
            readDefaultMonthlyMaskProduct(iceMapsAuxdataDir, month);
        } else {
            monthlyMaskProduct = userMaskProduct;
        }
        monthlyMaskBand = monthlyMaskProduct.getBandAt(0);
        monthlyMaskBand.readRasterDataFully(ProgressMonitor.NULL);
    }

    private void readDefaultMonthlyMaskProduct(String iceMapsAuxdataDir, int month) throws IOException {
        // todo: make names more generic if needed
        String iceMapsDir = iceMapsAuxdataDir + File.separator + DEFAULT_ICE_MAPS_DIR_NAME;

        final String seaiceMaskFileName = "ice_climatology_" + String.format("%02d", month) + "_max.dim";
        final File seaiceMaskFile = new File(iceMapsDir + File.separator + seaiceMaskFileName);

        monthlyMaskProduct = ProductIO.readProduct(seaiceMaskFile);
        monthlyMaskBand = monthlyMaskProduct.getBandAt(0);
        monthlyMaskBand.readRasterDataFully(ProgressMonitor.NULL);
    }
}
