package org.esa.snap.idepix.s2msi;

import org.esa.snap.core.util.SystemUtils;

import javax.media.jai.JAI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Non-blocking advice only: never changes the application's shared processing settings. */
final class S2IdepixPerformanceAdvice {
    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;

    private S2IdepixPerformanceAdvice() { }

    static void log(int resolution, int width, int height, boolean cloudShadow) {
        JAI jai = JAI.getDefaultInstance();
        long cache = jai.getTileCache().getMemoryCapacity();
        long heap = Runtime.getRuntime().maxMemory();
        int threads = jai.getTileScheduler().getParallelism();
        int processors = Runtime.getRuntime().availableProcessors();
        int flagWorkerLimit = Math.max(1, Integer.getInteger("snap.parallelism", processors));
        SystemUtils.LOG.info(String.format(Locale.ROOT,
                "IdePix S2 processing settings: input=%dx%d at %d m, cloudShadow=%s, " +
                        "tileCache=%.0f MiB, maxHeap=%.0f MiB, tileThreads=%d, availableProcessors=%d, " +
                        "internal flag-worker limit=%d. See IdePix S2 Help: Performance settings.",
                width, height, resolution, cloudShadow, cache / (double) MIB, heap / (double) MIB,
                threads, processors, flagWorkerLimit));
        for (String warning : warnings(resolution, (long) width * height, cloudShadow,
                cache, heap, threads, processors)) {
            SystemUtils.LOG.warning("IdePix S2 performance advice: " + warning);
        }
        if (threads == 1 && processors > 1) {
            SystemUtils.LOG.info("IdePix S2 performance advice: tile scheduling uses one thread. " +
                    "More threads may help a single job, but fewer threads may be intentional for batch jobs " +
                    "or memory-constrained machines. No settings have been changed.");
        }
    }

    static List<String> warnings(int resolution, long pixels, boolean cloudShadow,
                                 long cache, long heap, int threads, int processors) {
        List<String> advice = new ArrayList<>();
        if (cloudShadow && cache <= 0) {
            advice.add("The tile cache is disabled. Repeated upstream computation can make shadow " +
                    "processing substantially slower. Enable a cache if sufficient Java heap is available.");
        } else if (cloudShadow) {
            // Conservative advisory thresholds, not correctness requirements or universal optimums.
            long threshold = resolution <= 10 ? 4 * GIB : resolution <= 20 ? 2 * GIB : GIB;
            long largeScene = resolution <= 20 ? 20_000_000L : 1_000_000L;
            if (pixels >= largeScene && cache < threshold) {
                advice.add("The tile cache is small for a large " + resolution + " m shadow-processing input. " +
                        "Consider a larger cache if the Java heap has room. Advisory thresholds are heuristics: " +
                        "8 GiB helped tested full-scene 10 m products on a 32 GB-class PC, but is not a universal " +
                        "minimum. Use GPT -c or Desktop performance settings; see IdePix S2 Help.");
            }
        }
        if (heap > 0 && cache > 0 && cache >= heap * 0.75) {
            advice.add("The configured tile cache is at least 75% of the maximum Java heap. " +
                    "Leave heap space for source data and operators; cache memory is part of the heap, " +
                    "not extra RAM. Adjust cache/heap to the machine and concurrent jobs.");
        }
        if (processors > 0 && threads > processors) {
            advice.add("Tile scheduling requests more threads than processors available to this JVM. " +
                    "This can increase contention; consider fewer threads, especially for concurrent jobs.");
        }
        return advice;
    }
}
