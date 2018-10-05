package org.esa.snap.idepix.meris.nc4;

import org.esa.snap.core.dataio.ProductIOPlugInManager;
import org.esa.snap.core.dataio.ProductWriterPlugIn;
import org.junit.Test;

import java.util.Iterator;

import static junit.framework.TestCase.assertEquals;

/**
 *
 * @author olafd
 */
public class IdepixMerisNc4WriterLoadedAsServiceTest {
    @Test
    public void testWriterIsLoaded() {
        int writerCount = 0;

        ProductIOPlugInManager plugInManager = ProductIOPlugInManager.getInstance();
        Iterator writerPlugIns = plugInManager.getWriterPlugIns("NetCDF4-IDEPIX-MERIS");

        while (writerPlugIns.hasNext()) {
            writerCount++;
            ProductWriterPlugIn plugIn = (ProductWriterPlugIn) writerPlugIns.next();
            System.out.println("writerPlugIn.Class = " + plugIn.getClass());
            System.out.println("writerPlugIn.Descr = " + plugIn.getDescription(null));
        }

        assertEquals(1, writerCount);

    }

}
