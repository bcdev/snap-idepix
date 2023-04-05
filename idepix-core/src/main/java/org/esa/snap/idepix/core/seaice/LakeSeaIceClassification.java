/*
 * Copyright (c) 2023.  Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 *
 */

package org.esa.snap.idepix.core.seaice;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;

import java.io.IOException;
import java.nio.file.Path;

public class LakeSeaIceClassification {

    private static final String DEFAULT_ICE_MAPS_DIR_NAME = "1c_icelake_icesea_mask_1grad";
    public static final String FILE_NAME_EXTENSION = ".nc";

    private Product monthlyMaskProduct;
    private Band monthlyMaskBand;

    public LakeSeaIceClassification(Product userMaskProduct, Path iceMapsAuxdataDir, int month) {
        try {
            provideMaskData(userMaskProduct, iceMapsAuxdataDir, month);
        } catch (IOException e) {
            throw new IllegalStateException("Not able to initialise lake-sea-ice mask", e);
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

    private void provideMaskData(Product userMaskProduct, Path iceMapsAuxdataDir, int month) throws IOException {
        if (userMaskProduct == null) {
            readDefaultMonthlyMaskProduct(iceMapsAuxdataDir, month);
        } else {
            monthlyMaskProduct = userMaskProduct;
        }
        monthlyMaskBand = monthlyMaskProduct.getBandAt(0);
        monthlyMaskBand.readRasterDataFully(ProgressMonitor.NULL);
    }

    private void readDefaultMonthlyMaskProduct(Path iceMapsAuxdataDir, int month) throws IOException {
        Path iceMapsDir = iceMapsAuxdataDir.resolve(DEFAULT_ICE_MAPS_DIR_NAME);

        final String seaIceMaskFileName = String.format("ice_climatology_%02d_max%s", month, FILE_NAME_EXTENSION);
        Path seaIceMaskFile = iceMapsDir.resolve(seaIceMaskFileName);

        monthlyMaskProduct = ProductIO.readProduct(seaIceMaskFile.toAbsolutePath().toString());
        monthlyMaskBand = monthlyMaskProduct.getBandAt(0);
        monthlyMaskBand.readRasterDataFully(ProgressMonitor.NULL);
    }
}
