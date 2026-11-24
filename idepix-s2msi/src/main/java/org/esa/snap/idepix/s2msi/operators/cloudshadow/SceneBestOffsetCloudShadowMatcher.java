package org.esa.snap.idepix.s2msi.operators.cloudshadow;

/**
 * Compatibility matcher that assigns the existing scene-wide offset to every cloud.
 *
 * <p>This is the Step 3 fallback and preserves the pre-Step-3 result until local matching has enough evidence.</p>
 */
final class SceneBestOffsetCloudShadowMatcher implements CloudShadowMatcher {

    private final int sceneBestOffset;

    SceneBestOffsetCloudShadowMatcher(int sceneBestOffset) {
        if (sceneBestOffset < 0) {
            throw new IllegalArgumentException("Scene cloud-shadow offset must not be negative.");
        }
        this.sceneBestOffset = sceneBestOffset;
    }

    @Override
    public CloudShadowMatch match(CloudShadowObject cloud) {
        return new CloudShadowMatch(cloud.getId(), sceneBestOffset, 0.0,
                CloudShadowMatchScore.notEvaluated(), CloudShadowMatch.Source.SCENE_PRIOR);
    }
}
