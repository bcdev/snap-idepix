package org.esa.snap.idepix.s2msi.util;

import org.esa.snap.core.datamodel.FlagCoding;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class S2IdepixFlagCodingTest {

    @Test
    public void exposesMountainShadowFlag() {
        FlagCoding coding = S2IdepixUtils.createIdepixFlagCoding("pixel_classif_flags");

        assertNotNull(coding.getFlag(S2IdepixConstants.IDEPIX_MOUNTAIN_SHADOW_NAME));
        assertEquals(1 << S2IdepixConstants.IDEPIX_MOUNTAIN_SHADOW,
                coding.getFlagMask(S2IdepixConstants.IDEPIX_MOUNTAIN_SHADOW_NAME));
    }
}
