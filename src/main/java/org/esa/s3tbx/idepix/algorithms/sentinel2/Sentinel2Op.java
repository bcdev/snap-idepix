package org.esa.s3tbx.idepix.algorithms.sentinel2;

import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

/**
 * The Idepix pixel classification for Sentinel-2 MSI products
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Sentinel2",
        category = "Optical/Pre-Processing",
        version = "1.0",
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Pixel identification and classification for Sentinel-2.")
public class Sentinel2Op extends Operator {
    @Override
    public void initialize() throws OperatorException {
        // todo - new implementation for Sentinel-2 MSI instrument
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(Sentinel2Op.class);
        }
    }
}
