package org.esa.snap.idepix.s2msi.operators.mountainshadow;

import java.util.Arrays;

/**
 * Directional terrain-envelope cast shadows, inspired by the DemShading scanline approach.
 * This is a separate experimental calculator, not the public IdePix mountain-shadow flag.
 *
 * <p>Input is a row-major DEM on an unrotated, rectangular metric grid. Elevations and pixel
 * spacing must use the same units. Azimuth is clockwise from raster north (-Y), NOT necessarily
 * geographic north: callers must account for projection/grid convergence. Solar geometry is
 * constant for a calculation. The supplied DEM must include surrounding terrain if shadows
 * from outside the output area are to be represented.</p>
 *
 * <p>A major-axis sweep retains the maximum tilted elevation from the upstream terrain. The
 * upstream envelope is sampled at the fractional cross-axis position where the solar ray
 * intersects the preceding row or column. This avoids the regular banding caused by assigning
 * oblique rays to nearest-cell scanlines. Runtime is O(width*height), independent of shadow
 * length; auxiliary envelope storage is O(width+height), excluding input and output rasters.
 * No smoothing, artificial angle adjustment, or maximum shadow distance is applied.</p>
 */
public final class CastMountainShadow {

    private CastMountainShadow() {
    }

    /**
     * Returns nonnegative shadow depth (height of the upstream solar obstruction envelope
     * above terrain), zero for illuminated terrain, and NaN for missing/indeterminate terrain.
     * NoData gaps do not act as zero-height terrain and do not erase the known envelope.
     * After a gap, a known obstruction can still prove shadow; illumination cannot be proved.
     * The upstream DEM boundary is assumed unobstructed outside the supplied coverage.
     *
     * @param noData finite sentinel, or NaN when no finite sentinel is used; nonfinite input
     *               samples are always treated as missing
     * @param tolerance height tolerance below which a shadow is treated as zero
     * @param blockLength number of major-axis steps per processing block; does not alter results
     */
    public static float[] compute(float[] elevation, int width, int height,
                                  double pixelSizeX, double pixelSizeY,
                                  double gridSunAzimuthDegrees, double sunElevationDegrees,
                                  float noData, double tolerance, int blockLength) {
        if (width <= 0 || height <= 0 || elevation == null ||
                (long) width * height != elevation.length) {
            throw new IllegalArgumentException("DEM dimensions must match the elevation array");
        }
        if (!Double.isFinite(pixelSizeX) || !Double.isFinite(pixelSizeY) ||
                pixelSizeX <= 0 || pixelSizeY <= 0 ||
                !Double.isFinite(gridSunAzimuthDegrees) || !Double.isFinite(sunElevationDegrees) ||
                sunElevationDegrees <= 0 || sunElevationDegrees > 90 ||
                !Double.isFinite(tolerance) || tolerance < 0 || blockLength <= 0) {
            throw new IllegalArgumentException("Invalid pixel spacing, solar geometry, tolerance or block length");
        }
        float[] depth = new float[elevation.length];
        Arrays.fill(depth, Float.NaN);
        if (sunElevationDegrees == 90) {
            for (int i = 0; i < elevation.length; i++) {
                if (isValid(elevation[i], noData)) {
                    depth[i] = 0;
                }
            }
            return depth;
        }

        double azimuth = Math.toRadians(gridSunAzimuthDegrees % 360);
        // Direction in which shadows travel: away from the sun, in raster coordinates.
        double directionX = -Math.sin(azimuth);
        double directionY = Math.cos(azimuth);
        double cellDirectionX = directionX / pixelSizeX;
        double cellDirectionY = directionY / pixelSizeY;
        boolean majorX = Math.abs(cellDirectionX) >= Math.abs(cellDirectionY);
        int majorCount = majorX ? width : height;
        int minorCount = majorX ? height : width;
        double majorDirection = majorX ? cellDirectionX : cellDirectionY;
        double ratio = (majorX ? cellDirectionY : cellDirectionX) / majorDirection;
        double solarSlope = Math.tan(Math.toRadians(sunElevationDegrees));
        boolean forward = majorDirection > 0;
        // Moving one major-grid cell changes the minor coordinate by this fractional amount.
        double minorStep = ratio * (forward ? 1.0 : -1.0);
        double[] previousEnvelope = new double[minorCount];
        double[] currentEnvelope = new double[minorCount];
        Arrays.fill(previousEnvelope, Double.NEGATIVE_INFINITY);
        boolean[] previousMissing = new boolean[minorCount];
        boolean[] currentMissing = new boolean[minorCount];

        int processed = 0;
        while (processed < majorCount) {
            int blockEnd = processed + Math.min(blockLength, majorCount - processed);
            for (int step = processed; step < blockEnd; step++) {
                int major = forward ? step : majorCount - 1 - step;
                for (int minor = 0; minor < minorCount; minor++) {
                    int x = majorX ? major : minor;
                    int y = majorX ? minor : major;
                    int index = y * width + x;
                    UpstreamEnvelope upstream = interpolateUpstream(previousEnvelope, previousMissing,
                            minor - minorStep);
                    float z = elevation[index];
                    if (!isValid(z, noData)) {
                        // Keep a previously proven obstruction, but make illumination downstream indeterminate.
                        currentEnvelope[minor] = upstream.elevation;
                        currentMissing[minor] = true;
                        continue;
                    }
                    double distance = x * pixelSizeX * directionX + y * pixelSizeY * directionY;
                    double tiltedElevation = z + distance * solarSlope;
                    double obstruction = upstream.elevation - tiltedElevation;
                    if (obstruction > tolerance) {
                        depth[index] = (float) obstruction;
                    } else if (!upstream.hasMissingTerrain) {
                        depth[index] = 0;
                    }
                    currentEnvelope[minor] = Math.max(upstream.elevation, tiltedElevation);
                    currentMissing[minor] = upstream.hasMissingTerrain;
                }
                double[] envelopeSwap = previousEnvelope;
                previousEnvelope = currentEnvelope;
                currentEnvelope = envelopeSwap;
                boolean[] missingSwap = previousMissing;
                previousMissing = currentMissing;
                currentMissing = missingSwap;
            }
            processed = blockEnd;
        }
        return depth;
    }

    private static UpstreamEnvelope interpolateUpstream(double[] envelope, boolean[] missingTerrain,
                                                         double fractionalMinor) {
        // Trigonometric values at cardinal azimuths are not exactly zero. Accept numerical noise at
        // the raster boundary, while retaining the physical unobstructed condition outside it.
        final double boundaryTolerance = 1.0e-12;
        if (fractionalMinor < -boundaryTolerance ||
                fractionalMinor > envelope.length - 1 + boundaryTolerance) {
            return UpstreamEnvelope.UNOBSTRUCTED;
        }
        fractionalMinor = Math.max(0.0, Math.min(envelope.length - 1, fractionalMinor));
        int lower = (int) Math.floor(fractionalMinor);
        int upper = Math.min(lower + 1, envelope.length - 1);
        double fraction = fractionalMinor - lower;
        if (lower == upper || fraction == 0.0) {
            return new UpstreamEnvelope(envelope[lower], missingTerrain[lower]);
        }
        double lowerElevation = envelope[lower];
        double upperElevation = envelope[upper];
        // A ray entering the product between two sampled rays has no upstream terrain until both
        // neighbouring rays have entered the DEM. This avoids inventing an obstruction at boundaries.
        if (lowerElevation == Double.NEGATIVE_INFINITY || upperElevation == Double.NEGATIVE_INFINITY) {
            return UpstreamEnvelope.UNOBSTRUCTED;
        }
        return new UpstreamEnvelope(lowerElevation + fraction * (upperElevation - lowerElevation),
                missingTerrain[lower] || missingTerrain[upper]);
    }

    private static final class UpstreamEnvelope {
        private static final UpstreamEnvelope UNOBSTRUCTED =
                new UpstreamEnvelope(Double.NEGATIVE_INFINITY, false);

        private final double elevation;
        private final boolean hasMissingTerrain;

        private UpstreamEnvelope(double elevation, boolean hasMissingTerrain) {
            this.elevation = elevation;
            this.hasMissingTerrain = hasMissingTerrain;
        }
    }

    private static boolean isValid(float elevation, float noData) {
        return Float.isFinite(elevation) && elevation != noData;
    }
}
