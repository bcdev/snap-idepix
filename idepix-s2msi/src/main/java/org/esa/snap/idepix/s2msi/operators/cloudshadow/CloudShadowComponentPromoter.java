package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.util.List;
import java.util.Map;

/** Promotes complete clustered-shadow components selected by a per-cloud match. */
final class CloudShadowComponentPromoter {

    private CloudShadowComponentPromoter() {
    }

    static int promote(List<Integer> componentIds, Map<Integer, List<Integer>> components, int[] flagArray) {
        int promotedPixelCount = 0;
        for (int componentId : componentIds) {
            final List<Integer> positions = components.get(componentId);
            if (positions == null) {
                continue;
            }
            for (int position : positions) {
                final int flags = flagArray[position];
                if ((flags & PreparationMaskBand.CLOUD_SHADOW_COMB_FLAG) == 0 &&
                        (flags & PreparationMaskBand.CLOUD_FLAG) == 0 &&
                        (flags & PreparationMaskBand.INVALID_FLAG) == 0) {
                    flagArray[position] += PreparationMaskBand.CLOUD_SHADOW_COMB_FLAG;
                    promotedPixelCount++;
                }
            }
        }
        return promotedPixelCount;
    }
}
