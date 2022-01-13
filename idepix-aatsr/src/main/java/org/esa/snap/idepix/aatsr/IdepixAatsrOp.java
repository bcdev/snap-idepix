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

import org.esa.s3tbx.dataio.s3.meris.reprocessing.Meris3rd4thReprocessingAdapter;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;

import org.esa.snap.idepix.core.AlgorithmSelector;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.operators.BasisOp;
import org.esa.snap.idepix.core.operators.CloudBufferOp;
import org.esa.snap.idepix.core.util.IdepixIO;

import java.util.HashMap;
import java.util.Map;

/**
 * The IdePix pixel classification operator for AATSR products.
 *
 */
@OperatorMetadata(alias = "Idepix.Aatsr",
        category = "Optical/Preprocessing/Masking",
        version = "1.0",
        authors = "",
        copyright = "(c) 2022 by Brockmann Consult",
        description = "Pixel identification and classification for AATSR.")
public class IdepixAatsrOp extends BasisOp {

    @SourceProduct(label = "AATSR L1b product",
            description = "The AATSR L1b source product.")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    // overall parameters

    @Override
    public void initialize() throws OperatorException {
        validate(sourceProduct);

    }

    private void validate(Product sourceProduct) throws OperatorException{

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
