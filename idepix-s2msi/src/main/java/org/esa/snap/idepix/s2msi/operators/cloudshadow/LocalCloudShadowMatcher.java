package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Searches a bounded set of cloud-path offsets for a projection that intersects the clustered shadow belonging to
 * one cloud's potential-shadow corridor.
 */
final class LocalCloudShadowMatcher implements CloudShadowMatcher {

    static final int MIN_SEARCH_RADIUS = 4;
    static final int MAX_SEARCH_RADIUS = 32;
    private static final int MAX_SAMPLED_CLOUD_PIXELS = 1024;

    private final int sceneBestOffset;
    private final int searchRadius;
    private final int width;
    private final int height;
    private final int[] flagArray;
    private final int[] shadowComponentIdByPixel;
    private final float[][] sourceBands;
    private final Point2D[] cloudPath;
    private final int[] potentialPixelGeneration;
    private int generation;

    LocalCloudShadowMatcher(int sceneBestOffset, int width, int height, int[] flagArray,
                            int[] shadowComponentIdByPixel, float[][] sourceBands, Point2D[] cloudPath) {
        this(sceneBestOffset, defaultSearchRadius(sceneBestOffset), width, height, flagArray,
                shadowComponentIdByPixel, sourceBands, cloudPath);
    }

    LocalCloudShadowMatcher(int sceneBestOffset, int searchRadius, int width, int height, int[] flagArray,
                            int[] shadowComponentIdByPixel, float[][] sourceBands, Point2D[] cloudPath) {
        if (sceneBestOffset < 0 || searchRadius < 0) {
            throw new IllegalArgumentException("Offsets and search radius must not be negative.");
        }
        if (flagArray.length != width * height || shadowComponentIdByPixel.length != flagArray.length) {
            throw new IllegalArgumentException("Flag and component arrays must match the raster dimensions.");
        }
        this.sceneBestOffset = sceneBestOffset;
        this.searchRadius = searchRadius;
        this.width = width;
        this.height = height;
        this.flagArray = flagArray;
        this.shadowComponentIdByPixel = shadowComponentIdByPixel;
        this.sourceBands = sourceBands;
        this.cloudPath = cloudPath;
        this.potentialPixelGeneration = new int[flagArray.length];
    }

    @Override
    public CloudShadowMatch match(CloudShadowObject cloud) {
        if (sceneBestOffset <= 0 || sceneBestOffset >= cloudPath.length) {
            return scenePrior(cloud);
        }

        generation++;
        if (generation == Integer.MAX_VALUE) {
            java.util.Arrays.fill(potentialPixelGeneration, 0);
            generation = 1;
        }
        final Set<Integer> candidateOffsets = new LinkedHashSet<>();
        candidateOffsets.add(sceneBestOffset);
        final List<Integer> positions = cloud.getPotentialShadowPixels();
        final List<Integer> offsets = cloud.getPotentialShadowOffsets();
        final int minimumOffset = Math.max(1, sceneBestOffset - searchRadius);
        final int maximumOffset = Math.min(cloudPath.length - 1, sceneBestOffset + searchRadius);
        for (int i = 0; i < positions.size(); i++) {
            final int pixel = positions.get(i);
            potentialPixelGeneration[pixel] = generation;
            if (isClusteredShadow(pixel)) {
                final int offset = offsets.get(i);
                if (offset >= minimumOffset && offset <= maximumOffset) {
                    candidateOffsets.add(offset);
                }
            }
        }

        final Candidate sceneCandidate = evaluate(cloud, sceneBestOffset);
        Candidate bestCandidate = sceneCandidate;
        for (int candidateOffset : candidateOffsets) {
            if (candidateOffset != sceneBestOffset) {
                final Candidate candidate = evaluate(cloud, candidateOffset);
                if (candidate.isBetterThan(bestCandidate, sceneBestOffset)) {
                    bestCandidate = candidate;
                }
            }
        }

        // A local result may only add evidence: it must create more exact clustered-shadow intersections than
        // the scene prior. Existing globally accepted shadows are retained by the legacy combination path.
        if (bestCandidate.offset == sceneBestOffset || bestCandidate.overlapCount <= sceneCandidate.overlapCount ||
                bestCandidate.overlapCount == 0 || bestCandidate.corridorSupport == 0.0 ||
                bestCandidate.componentIds.isEmpty()) {
            return scenePrior(cloud);
        }

        final double confidence = Math.min(1.0,
                (bestCandidate.overlapCount - sceneCandidate.overlapCount) /
                        (double) Math.max(1, bestCandidate.validProjectionCount));
        final CloudShadowMatchScore score = new CloudShadowMatchScore(
                bestCandidate.overlap, bestCandidate.corridorSupport, bestCandidate.darkness,
                bestCandidate.distance, bestCandidate.coherence);
        return new CloudShadowMatch(cloud.getId(), bestCandidate.offset, confidence, score,
                CloudShadowMatch.Source.LOCAL_SEARCH, new ArrayList<>(bestCandidate.componentIds));
    }

    private Candidate evaluate(CloudShadowObject cloud, int offset) {
        final int offsetX = (int) cloudPath[offset].getX();
        final int offsetY = (int) cloudPath[offset].getY();
        final List<Integer> cloudPixels = cloud.getCloudPixels();
        final int stride = Math.max(1, (cloudPixels.size() + MAX_SAMPLED_CLOUD_PIXELS - 1) /
                MAX_SAMPLED_CLOUD_PIXELS);
        int validProjectionCount = 0;
        int corridorCount = 0;
        int overlapCount = 0;
        double darknessSum = 0.0;
        final Set<Integer> componentIds = new LinkedHashSet<>();

        for (int i = 0; i < cloudPixels.size(); i += stride) {
            final int cloudPixel = cloudPixels.get(i);
            final int x = cloudPixel % width + offsetX;
            final int y = cloudPixel / width + offsetY;
            if (x < 0 || x >= width || y < 0 || y >= height) {
                continue;
            }
            final int projectedPixel = y * width + x;
            final int flags = flagArray[projectedPixel];
            if (hasFlag(flags, PreparationMaskBand.CLOUD_FLAG) ||
                    hasFlag(flags, PreparationMaskBand.INVALID_FLAG)) {
                continue;
            }
            validProjectionCount++;
            if (potentialPixelGeneration[projectedPixel] != generation) {
                continue;
            }
            corridorCount++;
            if (isClusteredShadow(projectedPixel)) {
                overlapCount++;
                final int componentId = shadowComponentIdByPixel[projectedPixel];
                if (componentId > 0) {
                    componentIds.add(componentId);
                }
                darknessSum += darkness(projectedPixel);
            }
        }

        final double overlap = validProjectionCount == 0 ? 0.0 : overlapCount / (double) validProjectionCount;
        final double corridorSupport = validProjectionCount == 0 ? 0.0 :
                corridorCount / (double) validProjectionCount;
        final double darkness = overlapCount == 0 ? Double.NaN : darknessSum / overlapCount;
        final double distance = searchRadius == 0 ? 1.0 :
                1.0 - Math.min(1.0, Math.abs(offset - sceneBestOffset) / (double) searchRadius);
        final double coherence = componentIds.isEmpty() ? 0.0 : 1.0 / componentIds.size();
        return new Candidate(offset, validProjectionCount, overlapCount, overlap, corridorSupport, darkness,
                distance, coherence, componentIds);
    }

    private double darkness(int pixel) {
        double sum = 0.0;
        int count = 0;
        for (float[] sourceBand : sourceBands) {
            final float reflectance = sourceBand[pixel];
            if (!Float.isNaN(reflectance)) {
                sum += 1.0 - Math.max(0.0, Math.min(1.0, reflectance));
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private boolean isClusteredShadow(int pixel) {
        return hasFlag(flagArray[pixel], PreparationMaskBand.CLOUD_SHADOW_FLAG);
    }

    private static boolean hasFlag(int value, int flag) {
        return (value & flag) == flag;
    }

    private CloudShadowMatch scenePrior(CloudShadowObject cloud) {
        return new SceneBestOffsetCloudShadowMatcher(sceneBestOffset).match(cloud);
    }

    private static int defaultSearchRadius(int sceneBestOffset) {
        return Math.max(MIN_SEARCH_RADIUS, Math.min(MAX_SEARCH_RADIUS, sceneBestOffset / 2));
    }

    private static final class Candidate {
        private final int offset;
        private final int validProjectionCount;
        private final int overlapCount;
        private final double overlap;
        private final double corridorSupport;
        private final double darkness;
        private final double distance;
        private final double coherence;
        private final Set<Integer> componentIds;

        private Candidate(int offset, int validProjectionCount, int overlapCount, double overlap,
                          double corridorSupport, double darkness, double distance, double coherence,
                          Set<Integer> componentIds) {
            this.offset = offset;
            this.validProjectionCount = validProjectionCount;
            this.overlapCount = overlapCount;
            this.overlap = overlap;
            this.corridorSupport = corridorSupport;
            this.darkness = darkness;
            this.distance = distance;
            this.coherence = coherence;
            this.componentIds = componentIds;
        }

        private boolean isBetterThan(Candidate other, int priorOffset) {
            if (overlapCount != other.overlapCount) {
                return overlapCount > other.overlapCount;
            }
            if (Double.compare(overlap, other.overlap) != 0) {
                return overlap > other.overlap;
            }
            return Math.abs(offset - priorOffset) < Math.abs(other.offset - priorOffset);
        }
    }
}
