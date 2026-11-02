package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CloudShadowObjectTest {

    @Test
    public void buildsOrderedCloudObjectsFromExistingMaps() {
        final List<Integer> cloud7 = Arrays.asList(1, 2, 3);
        final List<Integer> cloud11 = Arrays.asList(8, 9);
        final List<Integer> shadow7 = Arrays.asList(20, 21);
        final List<Integer> offsets7 = Arrays.asList(4, 5);
        final Map<Integer, List<Integer>> clouds = new LinkedHashMap<>();
        clouds.put(7, cloud7);
        clouds.put(11, cloud11);
        final Map<Integer, List<Integer>> shadows = new LinkedHashMap<>();
        shadows.put(7, shadow7);
        final Map<Integer, List<Integer>> offsets = new LinkedHashMap<>();
        offsets.put(7, offsets7);

        final List<CloudShadowObject> objects = CloudShadowObject.fromLegacyMaps(clouds, shadows, offsets);

        assertEquals(1, objects.size());
        assertEquals(7, objects.get(0).getId());
        assertEquals(cloud7, objects.get(0).getCloudPixels());
        assertEquals(shadow7, objects.get(0).getPotentialShadowPixels());
        assertEquals(offsets7, objects.get(0).getPotentialShadowOffsets());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedPotentialPixelAndOffsetCounts() {
        new CloudShadowObject(1, Collections.singletonList(3), Arrays.asList(8, 9),
                Collections.singletonList(2));
    }

    @Test
    public void sceneMatcherPreservesGlobalOffsetAsUnevaluatedFallback() {
        final CloudShadowObject cloud = new CloudShadowObject(3, Collections.singletonList(5),
                Collections.singletonList(9), Collections.singletonList(4));

        final CloudShadowMatch match = new SceneBestOffsetCloudShadowMatcher(12).match(cloud);

        assertEquals(3, match.getCloudId());
        assertEquals(12, match.getOffset());
        assertEquals(0.0, match.getConfidence(), 0.0);
        assertEquals(CloudShadowMatch.Source.SCENE_PRIOR, match.getSource());
        assertFalse(match.getScore().isEvaluated());
    }
}
