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
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ImageUtils;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.Range;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.IdepixFlagCoding;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.util.Arrays;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

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
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        // 2.1) copy source bands (todo - which source bands to include?)
        // 2.2) create flag band compatible with other IdePix processors but only
        final Band cloudFlagBand = targetProduct.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);
        FlagCoding flagCoding = IdepixFlagCoding.createDefaultFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        cloudFlagBand.setSampleCoding(flagCoding);
        IdepixFlagCoding.setupDefaultClassifBitmask(targetProduct);

    }

    @Override
    public void doExecute(ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Preparing cloud shadow detection.", 3);
        try {
            final int sceneWidth = sourceProduct.getSceneRasterWidth();
            final int sceneHeight = sourceProduct.getSceneRasterHeight();

            // 1) detect day time area where cloud shadow can occur
            final RasterDataNode sza = sourceProduct.getRasterDataNode("solar_zenith_tn");
            final Mask dayMask = sourceProduct.getMaskGroup().get("confidence_in_day");
            // test sun elevation and find first and last row with SZA<85°, daylight zone
            // day flag is necessary because there can be an area with SZA<85° but it is not marked as DAY.
            final Mask szaDayTest = Mask.BandMathsType.create("SZA_DAY_TEST", "", sceneWidth, sceneHeight,
                                                                "solar_zenith_tn < 85 && confidence_in_day",
                                                                Color.yellow, 0.5f);
            szaDayTest.setOwner(getSourceProduct());
            final Range dayRange = detectMaskedPixelRangeInColumn(szaDayTest, sceneWidth);

            // 2) create north-corrected orientation on a sub-sampled (100) grid then scale back
            // to original size using bicubic interpolation
            pm.worked(1);
            // 3) create cloudMaskImage and landMaskImage
            pm.worked(1);
            // 4) create startSearchMask using cloudMaskImage, landMaskImage and search radius
            pm.worked(1);
        } finally {
            pm.done();
        }
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

    static Range detectMaskedPixelRangeInColumn(Mask mask, int column) {
        final int rasterHeight = mask.getRasterHeight();
        final Raster data = mask.getSourceImage().getData(new Rectangle(column, 0, 1, rasterHeight));
        // masks have byte data type
        final byte[] dataBufferArray = (byte[]) ImageUtils.createDataBufferArray(data.getTransferType(), rasterHeight);
        data.getDataElements(column, 0, 1, rasterHeight, dataBufferArray);
        final Range range = new Range(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

        // todo - can be further optimised and parallelized
        for (int i = 0; i < dataBufferArray.length; i++) {
            if(Double.isInfinite(range.getMin()) ){
                if (dataBufferArray[i] == -1) {
                    range.setMin(i);
                }
            }
        }
        for (int i = dataBufferArray.length - 1; i >= 0; i--) {
            if(!Double.isInfinite(range.getMin()) && Double.isInfinite(range.getMax()) ){
                if (dataBufferArray[i] == -1) {
                    range.setMax(i);
                }
            }
        }
        return range;
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
