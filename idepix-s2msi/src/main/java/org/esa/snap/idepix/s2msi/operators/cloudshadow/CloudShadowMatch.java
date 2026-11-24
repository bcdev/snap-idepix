package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.util.Collections;
import java.util.List;

/** Selected cloud-path offset and its evidence for one cloud. */
final class CloudShadowMatch {

    enum Source {
        SCENE_PRIOR,
        LOCAL_SEARCH
    }

    private final int cloudId;
    private final int offset;
    private final double confidence;
    private final CloudShadowMatchScore score;
    private final Source source;
    private final List<Integer> shadowComponentIds;

    CloudShadowMatch(int cloudId, int offset, double confidence, CloudShadowMatchScore score, Source source) {
        this(cloudId, offset, confidence, score, source, Collections.emptyList());
    }

    CloudShadowMatch(int cloudId, int offset, double confidence, CloudShadowMatchScore score, Source source,
                     List<Integer> shadowComponentIds) {
        if (offset < 0) {
            throw new IllegalArgumentException("Cloud-shadow offset must not be negative.");
        }
        if (score == null || source == null || shadowComponentIds == null) {
            throw new IllegalArgumentException("Score, match source, and shadow components must not be null.");
        }
        this.cloudId = cloudId;
        this.offset = offset;
        this.confidence = confidence;
        this.score = score;
        this.source = source;
        this.shadowComponentIds = Collections.unmodifiableList(shadowComponentIds);
    }

    int getCloudId() {
        return cloudId;
    }

    int getOffset() {
        return offset;
    }

    double getConfidence() {
        return confidence;
    }

    CloudShadowMatchScore getScore() {
        return score;
    }

    Source getSource() {
        return source;
    }

    List<Integer> getShadowComponentIds() {
        return shadowComponentIds;
    }
}
