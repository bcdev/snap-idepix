package org.esa.snap.idepix.s2msi.operators.cloudshadow;

/**
 * Independent evidence channels for a possible cloud-to-shadow match.
 *
 * <p>No weighting is imposed here. Keeping the channels separate lets later Step 3 experiments change or
 * disable one criterion without changing the per-cloud data model.</p>
 */
final class CloudShadowMatchScore {

    private static final CloudShadowMatchScore NOT_EVALUATED =
            new CloudShadowMatchScore(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

    private final double overlap;
    private final double corridorSupport;
    private final double darkness;
    private final double distance;
    private final double coherence;

    CloudShadowMatchScore(double overlap, double corridorSupport, double darkness, double distance,
                          double coherence) {
        this.overlap = overlap;
        this.corridorSupport = corridorSupport;
        this.darkness = darkness;
        this.distance = distance;
        this.coherence = coherence;
    }

    static CloudShadowMatchScore notEvaluated() {
        return NOT_EVALUATED;
    }

    double getOverlap() {
        return overlap;
    }

    double getCorridorSupport() {
        return corridorSupport;
    }

    double getDarkness() {
        return darkness;
    }

    double getDistance() {
        return distance;
    }

    double getCoherence() {
        return coherence;
    }

    boolean isEvaluated() {
        return !Double.isNaN(overlap);
    }
}
