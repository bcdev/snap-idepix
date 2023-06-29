package org.esa.snap.idepix.olci;

import junit.framework.TestCase;
import org.esa.snap.core.gpf.Operator;

/**
 * TODO add API doc
 *
 * @author Martin Boettcher
 */
public class IdepixOlciClassificationOpTest extends TestCase {

    public void testReadNNThresholds() {
        final IdepixOlciClassificationOp operator = (IdepixOlciClassificationOp) new IdepixOlciClassificationOp.Spi().createOperator();
        operator.readNNThresholds();
        assertEquals(0.0, IdepixOlciCloudNNInterpreter.NNThreshold.CLEAR_SNOW_ICE_BOUNDS.range.getMin());
        assertEquals(4.79, IdepixOlciCloudNNInterpreter.NNThreshold.SPATIAL_MIXED_BOUNDS_LAND.range.getMax());
    }
}