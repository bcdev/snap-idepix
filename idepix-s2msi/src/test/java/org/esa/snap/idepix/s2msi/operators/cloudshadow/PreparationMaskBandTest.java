package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.internal.TileImpl;
import org.junit.Test;

import java.awt.Rectangle;
import java.net.URL;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_BUFFER;
import static org.esa.snap.idepix.s2msi.util.S2IdepixConstants.IDEPIX_CLOUD_SURE;

/**
 * @author Tonio Fincke
 */
public class PreparationMaskBandTest {

    private final static int[] EXPECTED_FLAG_ARRAY = new int[]{
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6
    };

    @Test
    public void prepareMaskBand() throws Exception {
        final URL s2ProductURL = PreparationMaskBandTest.class.getResource("s2_classif.dim");
        final Product s2Product = ProductIO.readProduct(s2ProductURL.getFile());

        int[] flagArray = new int[21 * 17];
        final Rectangle sourceRectangle = new Rectangle(21, 17);
        final Band classifBand = s2Product.getBand("pixel_classif_flags");
        final Tile classifSourceTile = new TileImpl(classifBand, classifBand.getSourceImage().getData());
        final FlagDetector flagDetector = new FlagDetector(classifSourceTile, sourceRectangle);
        PreparationMaskBand.prepareMaskBand(21, 17, sourceRectangle, flagArray, flagDetector);

        assertArrayEquals(EXPECTED_FLAG_ARRAY, flagArray);
    }

    @Test
    public void sureAmbiguousAndCombinedCloudAreAlwaysShadowCasting() {
        assertTrue(FlagDetector.isCloudSample(1 << IDEPIX_CLOUD_SURE, false));
        assertTrue(FlagDetector.isCloudSample(1 << IDEPIX_CLOUD_AMBIGUOUS, false));
        assertTrue(FlagDetector.isCloudSample(1 << IDEPIX_CLOUD, false));
    }

    @Test
    public void cloudBufferIsShadowCastingOnlyWhenEnabled() {
        final int bufferOnly = 1 << IDEPIX_CLOUD_BUFFER;

        assertFalse(FlagDetector.isCloudSample(bufferOnly, false));
        assertTrue(FlagDetector.isCloudSample(bufferOnly, true));
    }

}
