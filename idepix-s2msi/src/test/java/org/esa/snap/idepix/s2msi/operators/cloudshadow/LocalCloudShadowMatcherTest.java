package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.junit.Test;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalCloudShadowMatcherTest {

    private static final int WIDTH = 12;
    private static final int HEIGHT = 5;

    @Test
    public void selectsLocalOffsetWhenScenePriorMissesClusteredShadow() {
        final int[] flags = clearLandFlags();
        final int cloudPixel1 = index(1, 2);
        final int cloudPixel2 = index(1, 3);
        flags[cloudPixel1] += PreparationMaskBand.CLOUD_FLAG;
        flags[cloudPixel2] += PreparationMaskBand.CLOUD_FLAG;
        final int shadowPixel1 = index(5, 2);
        final int shadowPixel2 = index(5, 3);
        flags[shadowPixel1] += PreparationMaskBand.CLOUD_SHADOW_FLAG;
        flags[shadowPixel2] += PreparationMaskBand.CLOUD_SHADOW_FLAG;
        final int[] componentIds = new int[flags.length];
        componentIds[shadowPixel1] = 9;
        componentIds[shadowPixel2] = 9;
        final CloudShadowObject cloud = new CloudShadowObject(3, Arrays.asList(cloudPixel1, cloudPixel2),
                Arrays.asList(shadowPixel1, shadowPixel2), Arrays.asList(4, 4));

        final CloudShadowMatch match = matcher(7, flags, componentIds).match(cloud);

        assertEquals(CloudShadowMatch.Source.LOCAL_SEARCH, match.getSource());
        assertEquals(4, match.getOffset());
        assertEquals(Collections.singletonList(9), match.getShadowComponentIds());
        assertTrue(match.getConfidence() > 0.0);
        assertTrue(match.getScore().isEvaluated());
        assertEquals(1.0, match.getScore().getOverlap(), 0.0);
    }

    @Test
    public void retainsScenePriorWhenItAlreadyIntersectsClusteredShadow() {
        final int[] flags = clearLandFlags();
        final int cloudPixel = index(1, 2);
        final int shadowPixel = index(8, 2);
        flags[cloudPixel] += PreparationMaskBand.CLOUD_FLAG;
        flags[shadowPixel] += PreparationMaskBand.CLOUD_SHADOW_FLAG;
        final int[] componentIds = new int[flags.length];
        componentIds[shadowPixel] = 5;
        final CloudShadowObject cloud = new CloudShadowObject(1, Collections.singletonList(cloudPixel),
                Collections.singletonList(shadowPixel), Collections.singletonList(7));

        final CloudShadowMatch match = matcher(7, flags, componentIds).match(cloud);

        assertEquals(CloudShadowMatch.Source.SCENE_PRIOR, match.getSource());
        assertEquals(7, match.getOffset());
        assertTrue(match.getShadowComponentIds().isEmpty());
    }

    @Test
    public void rejectsDarkClusterOutsideBoundedSearch() {
        final int[] flags = clearLandFlags();
        final int cloudPixel = index(1, 2);
        final int shadowPixel = index(3, 2);
        flags[cloudPixel] += PreparationMaskBand.CLOUD_FLAG;
        flags[shadowPixel] += PreparationMaskBand.CLOUD_SHADOW_FLAG;
        final int[] componentIds = new int[flags.length];
        componentIds[shadowPixel] = 4;
        final CloudShadowObject cloud = new CloudShadowObject(1, Collections.singletonList(cloudPixel),
                Collections.singletonList(shadowPixel), Collections.singletonList(2));

        final CloudShadowMatch match = matcher(7, 2, flags, componentIds).match(cloud);

        assertEquals(CloudShadowMatch.Source.SCENE_PRIOR, match.getSource());
        assertEquals(7, match.getOffset());
    }

    private static LocalCloudShadowMatcher matcher(int sceneOffset, int[] flags, int[] componentIds) {
        return matcher(sceneOffset, 4, flags, componentIds);
    }

    private static LocalCloudShadowMatcher matcher(int sceneOffset, int radius, int[] flags, int[] componentIds) {
        final float[][] bands = {new float[flags.length], new float[flags.length]};
        Arrays.fill(bands[0], 0.1f);
        Arrays.fill(bands[1], 0.1f);
        return new LocalCloudShadowMatcher(sceneOffset, radius, WIDTH, HEIGHT, flags, componentIds, bands, path());
    }

    private static int[] clearLandFlags() {
        final int[] flags = new int[WIDTH * HEIGHT];
        Arrays.fill(flags, PreparationMaskBand.LAND_FLAG);
        return flags;
    }

    private static Point2D[] path() {
        final Point2D[] path = new Point2D[10];
        for (int i = 0; i < path.length; i++) {
            path[i] = new Point2D.Double(i, 0);
        }
        return path;
    }

    private static int index(int x, int y) {
        return y * WIDTH + x;
    }
}
