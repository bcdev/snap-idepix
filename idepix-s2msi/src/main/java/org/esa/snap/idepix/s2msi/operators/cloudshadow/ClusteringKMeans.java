package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tonio Fincke
 * @author Michael Paperin
 */
class ClusteringKMeans {

    private static final int MAX_ITER_COUNT = 30;
    private static final int SEED = 544653563;

    static double[][] computedKMeansCluster(int clusterCount, double[]... images) {
        return new PrimitiveKMeans(clusterCount, images).cluster();
    }

    static double[][] computedKMeansClusterLegacy(int clusterCount, double[]... images) {
        AdaptedIsoClustering clusterer = new AdaptedIsoClustering(clusterCount, MAX_ITER_COUNT);
        List<Clusterable> list = new ArrayList<>();
        for (int xyPos = 0; xyPos < images[0].length; xyPos++) {
            double[] values = new double[images.length];
            for (int imageIndex = 0; imageIndex < images.length; imageIndex++) {
                values[imageIndex] = images[imageIndex][xyPos];
            }
            list.add(new DoublePoint(values));
        }
        List<CentroidCluster<Clusterable>> clusters = clusterer.cluster(list);
        double[][] clusterCentroidArray = new double[clusterCount][images.length];

        int countClusterNumber = 0;
        for (CentroidCluster<Clusterable> centroidCluster : clusters) {
            for (int i = 0; i < images.length; i++) {
                clusterCentroidArray[countClusterNumber][i] = centroidCluster.getCenter().getPoint()[i];
            }
            countClusterNumber++;
        }
        return clusterCentroidArray;
    }

    private static final class PrimitiveKMeans {
        private final int clusterCount;
        private final double[][] images;
        private final int pointCount;
        private final int dimension;
        private final JDKRandomGenerator random;

        private PrimitiveKMeans(int clusterCount, double[][] images) {
            this.clusterCount = clusterCount;
            this.images = images;
            this.pointCount = images[0].length;
            this.dimension = images.length;
            this.random = new JDKRandomGenerator();
            this.random.setSeed(SEED);
        }

        private double[][] cluster() {
            if (pointCount < clusterCount) {
                throw new IllegalArgumentException("Number of points must be at least the number of clusters");
            }

            double[][] centers = chooseInitialCenters();
            int[] assignments = assignPoints(centers);

            for (int iteration = 0; iteration < MAX_ITER_COUNT; iteration++) {
                boolean[] removed = new boolean[pointCount];
                boolean emptyCluster = false;
                double[][] newCenters = new double[clusterCount][dimension];
                for (int cluster = 0; cluster < clusterCount; cluster++) {
                    int members = memberCount(assignments, removed, cluster);
                    if (members == 0) {
                        int replacement = removePointFromLargestVarianceCluster(assignments, removed, centers);
                        copyPoint(replacement, newCenters[cluster]);
                        emptyCluster = true;
                    } else {
                        computeCentroid(assignments, removed, cluster, members, newCenters[cluster]);
                    }
                }

                int[] newAssignments = assignPoints(newCenters);
                int changes = 0;
                for (int point = 0; point < pointCount; point++) {
                    if (newAssignments[point] != assignments[point]) {
                        changes++;
                    }
                }
                centers = newCenters;
                assignments = newAssignments;
                if (changes == 0 && !emptyCluster) {
                    break;
                }
            }
            return centers;
        }

        private double[][] chooseInitialCenters() {
            int darkestPointIndex = -1;
            double darkestPoint = Double.MAX_VALUE;
            for (int point = 0; point < pointCount; point++) {
                double value = 0.0;
                for (int band = 0; band < dimension; band++) {
                    value += Math.pow(images[band][point], 2);
                }
                if (value < darkestPoint) {
                    darkestPoint = value;
                    darkestPointIndex = point;
                }
            }

            int[] centerIndexes = new int[clusterCount];
            boolean[] taken = new boolean[pointCount];
            double[] minimumDistanceSquared = new double[pointCount];
            centerIndexes[0] = darkestPointIndex;
            taken[darkestPointIndex] = true;
            for (int point = 0; point < pointCount; point++) {
                if (point != darkestPointIndex) {
                    double distance = distanceBetweenPoints(darkestPointIndex, point);
                    minimumDistanceSquared[point] = distance * distance;
                }
            }

            int centersFound = 1;
            while (centersFound < clusterCount) {
                double distanceSquaredSum = 0.0;
                for (int point = 0; point < pointCount; point++) {
                    if (!taken[point]) {
                        distanceSquaredSum += minimumDistanceSquared[point];
                    }
                }
                double threshold = random.nextDouble() * distanceSquaredSum;
                double sum = 0.0;
                int nextPoint = -1;
                for (int point = 0; point < pointCount; point++) {
                    if (!taken[point]) {
                        sum += minimumDistanceSquared[point];
                        if (sum >= threshold) {
                            nextPoint = point;
                            break;
                        }
                    }
                }
                if (nextPoint < 0) {
                    for (int point = pointCount - 1; point >= 0; point--) {
                        if (!taken[point]) {
                            nextPoint = point;
                            break;
                        }
                    }
                }
                if (nextPoint < 0) {
                    break;
                }

                centerIndexes[centersFound++] = nextPoint;
                taken[nextPoint] = true;
                if (centersFound < clusterCount) {
                    for (int point = 0; point < pointCount; point++) {
                        if (!taken[point]) {
                            double distance = distanceBetweenPoints(nextPoint, point);
                            double squared = distance * distance;
                            if (squared < minimumDistanceSquared[point]) {
                                minimumDistanceSquared[point] = squared;
                            }
                        }
                    }
                }
            }

            double[][] centers = new double[clusterCount][dimension];
            for (int cluster = 0; cluster < clusterCount; cluster++) {
                copyPoint(centerIndexes[cluster], centers[cluster]);
            }
            return centers;
        }

        private int[] assignPoints(double[][] centers) {
            int[] assignments = new int[pointCount];
            for (int point = 0; point < pointCount; point++) {
                double minimumDistance = Double.MAX_VALUE;
                int nearest = 0;
                for (int cluster = 0; cluster < clusterCount; cluster++) {
                    double distance = distanceToCenter(point, centers[cluster]);
                    if (distance < minimumDistance) {
                        minimumDistance = distance;
                        nearest = cluster;
                    }
                }
                assignments[point] = nearest;
            }
            return assignments;
        }

        private int removePointFromLargestVarianceCluster(int[] assignments, boolean[] removed,
                                                          double[][] centers) {
            double maximumVariance = Double.NEGATIVE_INFINITY;
            int selectedCluster = -1;
            for (int cluster = 0; cluster < clusterCount; cluster++) {
                if (memberCount(assignments, removed, cluster) > 0) {
                    Variance variance = new Variance();
                    for (int point = 0; point < pointCount; point++) {
                        if (!removed[point] && assignments[point] == cluster) {
                            variance.increment(distanceToCenter(point, centers[cluster]));
                        }
                    }
                    double value = variance.getResult();
                    if (value > maximumVariance) {
                        maximumVariance = value;
                        selectedCluster = cluster;
                    }
                }
            }
            int selectedMember = random.nextInt(memberCount(assignments, removed, selectedCluster));
            for (int point = 0; point < pointCount; point++) {
                if (!removed[point] && assignments[point] == selectedCluster && selectedMember-- == 0) {
                    removed[point] = true;
                    return point;
                }
            }
            throw new IllegalStateException("Unable to replace empty cluster");
        }

        private int memberCount(int[] assignments, boolean[] removed, int cluster) {
            int count = 0;
            for (int point = 0; point < pointCount; point++) {
                if (!removed[point] && assignments[point] == cluster) {
                    count++;
                }
            }
            return count;
        }

        private void computeCentroid(int[] assignments, boolean[] removed, int cluster, int members,
                                     double[] centroid) {
            for (int point = 0; point < pointCount; point++) {
                if (!removed[point] && assignments[point] == cluster) {
                    for (int band = 0; band < dimension; band++) {
                        centroid[band] += images[band][point];
                    }
                }
            }
            for (int band = 0; band < dimension; band++) {
                centroid[band] /= members;
            }
        }

        private double distanceBetweenPoints(int first, int second) {
            double sum = 0.0;
            for (int band = 0; band < dimension; band++) {
                double difference = images[band][first] - images[band][second];
                sum += difference * difference;
            }
            return Math.sqrt(sum);
        }

        private double distanceToCenter(int point, double[] center) {
            double sum = 0.0;
            for (int band = 0; band < dimension; band++) {
                double difference = images[band][point] - center[band];
                sum += difference * difference;
            }
            return Math.sqrt(sum);
        }

        private void copyPoint(int point, double[] target) {
            for (int band = 0; band < dimension; band++) {
                target[band] = images[band][point];
            }
        }
    }
}

