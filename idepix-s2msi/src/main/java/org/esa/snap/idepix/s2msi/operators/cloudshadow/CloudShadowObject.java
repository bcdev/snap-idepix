package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The cloud and geometrically possible shadow pixels belonging to one connected cloud.
 *
 * <p>The pixel positions are indexes into the current source rectangle. The potential-shadow offsets use
 * indexes into the cloud path and correspond one-to-one to {@link #getPotentialShadowPixels()}.</p>
 */
final class CloudShadowObject {

    private final int id;
    private final List<Integer> cloudPixels;
    private final List<Integer> potentialShadowPixels;
    private final List<Integer> potentialShadowOffsets;

    CloudShadowObject(int id, List<Integer> cloudPixels, List<Integer> potentialShadowPixels,
                      List<Integer> potentialShadowOffsets) {
        if (id <= 0) {
            throw new IllegalArgumentException("Cloud id must be positive.");
        }
        if (cloudPixels == null || potentialShadowPixels == null || potentialShadowOffsets == null) {
            throw new IllegalArgumentException("Cloud and shadow pixel collections must not be null.");
        }
        if (potentialShadowPixels.size() != potentialShadowOffsets.size()) {
            throw new IllegalArgumentException("Every potential-shadow pixel must have one cloud-path offset.");
        }
        this.id = id;
        this.cloudPixels = Collections.unmodifiableList(cloudPixels);
        this.potentialShadowPixels = Collections.unmodifiableList(potentialShadowPixels);
        this.potentialShadowOffsets = Collections.unmodifiableList(potentialShadowOffsets);
    }

    static List<CloudShadowObject> fromLegacyMaps(Map<Integer, List<Integer>> cloudPixelsById,
                                                   Map<Integer, List<Integer>> potentialShadowPixelsById,
                                                   Map<Integer, List<Integer>> potentialShadowOffsetsById) {
        final List<CloudShadowObject> clouds = new ArrayList<>(potentialShadowPixelsById.size());
        for (Map.Entry<Integer, List<Integer>> entry : potentialShadowPixelsById.entrySet()) {
            final int cloudId = entry.getKey();
            final List<Integer> cloudPixels = cloudPixelsById.get(cloudId);
            final List<Integer> offsets = potentialShadowOffsetsById.get(cloudId);
            if (cloudPixels == null) {
                throw new IllegalArgumentException("Potential shadow references unknown cloud id " + cloudId + '.');
            }
            if (offsets == null) {
                throw new IllegalArgumentException("Potential shadow offsets are missing for cloud id " + cloudId + '.');
            }
            clouds.add(new CloudShadowObject(cloudId, cloudPixels, entry.getValue(), offsets));
        }
        return clouds;
    }

    int getId() {
        return id;
    }

    List<Integer> getCloudPixels() {
        return cloudPixels;
    }

    List<Integer> getPotentialShadowPixels() {
        return potentialShadowPixels;
    }

    List<Integer> getPotentialShadowOffsets() {
        return potentialShadowOffsets;
    }
}
