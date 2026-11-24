package org.esa.snap.idepix.s2msi;

import org.junit.Test;
import static org.junit.Assert.*;

public class S2IdepixPerformanceAdviceTest {
    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    public void disabledCacheWarnsForShadowButNotClassificationOnly() {
        assertEquals(1, S2IdepixPerformanceAdvice.warnings(20, 30_000_000, true, 0, 21 * GIB, 16, 16).size());
        assertTrue(S2IdepixPerformanceAdvice.warnings(20, 30_000_000, false, 0, 21 * GIB, 16, 16).isEmpty());
    }

    @Test
    public void smallCacheAdviceDependsOnSceneSizeAndResolution() {
        assertEquals(1, S2IdepixPerformanceAdvice.warnings(10, 120_000_000, true, 2 * GIB, 21 * GIB, 16, 16).size());
        assertTrue(S2IdepixPerformanceAdvice.warnings(10, 1_000_000, true, 2 * GIB, 21 * GIB, 16, 16).isEmpty());
        assertTrue(S2IdepixPerformanceAdvice.warnings(20, 30_000_000, true, 2 * GIB, 21 * GIB, 16, 16).isEmpty());
        assertTrue(S2IdepixPerformanceAdvice.warnings(60, 3_000_000, true, GIB, 21 * GIB, 16, 16).isEmpty());
    }

    @Test
    public void testedCacheHasHeadroomAndSingleThreadIsNotAnError() {
        assertTrue(S2IdepixPerformanceAdvice.warnings(10, 120_000_000, true, 8 * GIB, 21 * GIB, 16, 16).isEmpty());
        assertTrue(S2IdepixPerformanceAdvice.warnings(10, 120_000_000, true, 8 * GIB, 21 * GIB, 1, 16).isEmpty());
    }

    @Test
    public void warnsOnHeapPressureAndCpuOversubscription() {
        assertEquals(2, S2IdepixPerformanceAdvice.warnings(20, 30_000_000, true, 8 * GIB, 10 * GIB, 32, 16).size());
        assertEquals(1, S2IdepixPerformanceAdvice.warnings(20, 30_000_000, true, 8 * GIB, 8 * GIB, 16, 16).size());
    }
}
