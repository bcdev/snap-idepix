/*
 * Copyright (C) 2022 Brockmann Consult GmbH (info@brockmann-consult.de)
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
 */
package org.esa.snap.idepix.aatsr;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

/**
 * The IdePix pixel classification operator for AATSR products (4th repro).
 *
 */
@OperatorMetadata(alias = "Idepix.Aatsr",
        category = "Optical/Preprocessing/Masking",
        version = "1.0",
        authors = "Dagmar Mueller, Marco Peters",
        copyright = "(c) 2022 by Brockmann Consult",
        description = "Pixel identification and classification for AATSR 4th repro data.")
public class IdepixAatsrOp extends Operator {

    @SourceProduct(label = "AATSR L1b product",
            description = "The AATSR L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Override
    public void initialize() throws OperatorException {
        validate(sourceProduct); // 1.1)
        // 1.2) validateParameters(); // if any

        // 2) create TargetProduct
        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_idepix");
        // 2.1) copy source bands (todo - which source bands to include?)
        // 2.2) create flag band compatible with other Idepix processors but only
        // 2.3) the cloud shadow flag and the used cloud flag are used?
        // 2.4) setup cloud shadow mask and the cloud mask
    }

    @Override
    public void doExecute(ProgressMonitor pm) throws OperatorException {
        super.doExecute(pm);
        // 1) create north-corrected orientation on a sub-sampled (100) grid then scale back
        // to original size using bicubic interpolation
        // 2) create cloudMaskImage and landMaskImage
        // 3) create startSearchMask using cloudMaskImage, landMaskImage and search radius
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        super.computeTile(targetBand, targetTile, pm);
        // 1) compute flag data
        //    use max distance from 2000-blocks for getting source data
        // 1.1) compute illuPathSteps, illuPathHeight, threshHeight
        // 1.2) compute cloud shadow
        // 2) set flags cloud_shadow and cloud
    }


    private Product createCompatibleProduct(Product sourceProduct, String name) {
        int sceneWidth = sourceProduct.getSceneRasterWidth();
        int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(name, "AATSR_IDEPIX", sceneWidth, sceneHeight);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        return targetProduct;
    }

    void validate(Product sourceProduct) throws OperatorException{
        // todo - test for the used data.
        if(!"ENV_AT_1_RBT".equals(sourceProduct.getProductType())) {
            throw new OperatorException("An AATSR product from the 4th reprocessing is needed as input");
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IdepixAatsrOp.class);
        }
    }
}
