package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CloudShadowComponentPromoterTest {

    @Test
    public void promotesWholeSelectedComponentButNotCloudOrInvalidPixels() {
        final int[] flags = new int[8];
        flags[1] = PreparationMaskBand.CLOUD_SHADOW_FLAG;
        flags[2] = PreparationMaskBand.CLOUD_SHADOW_FLAG;
        flags[3] = PreparationMaskBand.CLOUD_SHADOW_FLAG | PreparationMaskBand.CLOUD_FLAG;
        flags[4] = PreparationMaskBand.CLOUD_SHADOW_FLAG | PreparationMaskBand.INVALID_FLAG;
        flags[6] = PreparationMaskBand.CLOUD_SHADOW_FLAG;
        final Map<Integer, List<Integer>> components = new HashMap<>();
        components.put(7, Arrays.asList(1, 2, 3, 4));
        components.put(8, Collections.singletonList(6));

        final int promotedPixels = CloudShadowComponentPromoter.promote(
                Collections.singletonList(7), components, flags);

        assertEquals(2, promotedPixels);
        assertTrue(hasCombinedFlag(flags[1]));
        assertTrue(hasCombinedFlag(flags[2]));
        assertFalse(hasCombinedFlag(flags[3]));
        assertFalse(hasCombinedFlag(flags[4]));
        assertFalse(hasCombinedFlag(flags[6]));
    }

    private static boolean hasCombinedFlag(int flags) {
        return (flags & PreparationMaskBand.CLOUD_SHADOW_COMB_FLAG) ==
                PreparationMaskBand.CLOUD_SHADOW_COMB_FLAG;
    }
}
