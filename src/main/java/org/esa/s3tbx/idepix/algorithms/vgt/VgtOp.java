package org.esa.s3tbx.idepix.algorithms.vgt;

import org.esa.s3tbx.idepix.operators.BasisOp;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;

/**
 * The Idepix pixel classification for SPOT-VGT products
 *
 * @author olafd
 */
public class VgtOp extends BasisOp {
    @Override
    public void initialize() throws OperatorException {
        // todo - take from GlobAlbedoOp in BEAM Idepix
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(VgtOp.class);
        }
    }
}
