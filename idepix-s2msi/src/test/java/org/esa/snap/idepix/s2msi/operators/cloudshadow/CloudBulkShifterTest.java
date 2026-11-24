package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import org.junit.Test;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CloudBulkShifterTest {

    @Test
    public void sparsePathScanMatchesLegacyTraversal() {
        final Rectangle sourceRectangle = new Rectangle(0, 0, 8, 7);
        final Rectangle targetRectangle = new Rectangle(2, 1, 5, 5);
        final Point2D[] path = {
                new Point2D.Double(0, 0),
                new Point2D.Double(1, 0),
                new Point2D.Double(1, 1),
                new Point2D.Double(2, 1),
                new Point2D.Double(3, 2)
        };
        final int[] originalFlags = createFlags(sourceRectangle.width, sourceRectangle.height);
        final float[][] sourceBands = {new float[originalFlags.length], new float[originalFlags.length]};
        for (int i = 0; i < sourceBands[1].length; i++) {
            sourceBands[1][i] = (float) (0.01 + i * 0.003);
        }

        for (float azimuth : new float[]{45.0f, 135.0f, 225.0f, 315.0f}) {
            final int[] expectedFlags = originalFlags.clone();
            final LegacyResult expected = legacyShift(sourceRectangle, targetRectangle, azimuth,
                    sourceBands[1], expectedFlags, path);

            final int[] actualFlags = originalFlags.clone();
            final CloudBulkShifter actual = new CloudBulkShifter();
            actual.shiftCloudBulkAlongCloudPathType(sourceRectangle, targetRectangle, azimuth,
                    sourceBands, actualFlags, path);

            assertArrayEquals(expectedFlags, actualFlags);
            for (int band = 0; band < 3; band++) {
                assertArrayEquals(expected.means[band], actual.getMeanReflectanceAlongPath()[band], 0.0);
            }
            assertEquals(expected.cloudLand, actual.getNCloudOverLand());
            assertEquals(expected.cloudWater, actual.getNCloudOverWater());
        }
    }

    @Test
    public void sparseFinalShiftMatchesLegacyTraversal() {
        final Rectangle sourceRectangle = new Rectangle(0, 0, 8, 7);
        final Rectangle targetRectangle = new Rectangle(2, 1, 5, 5);
        final Point2D[] path = {
                new Point2D.Double(0, 0),
                new Point2D.Double(1, 0),
                new Point2D.Double(1, 1),
                new Point2D.Double(2, 1),
                new Point2D.Double(3, 2)
        };

        for (float azimuth : new float[]{45.0f, 135.0f, 225.0f, 315.0f}) {
            final int[] expected = createFlags(sourceRectangle.width, sourceRectangle.height);
            legacySetTileShiftedCloudBulk(sourceRectangle, targetRectangle, azimuth, expected, path, 3);

            final int[] actual = createFlags(sourceRectangle.width, sourceRectangle.height);
            CloudBulkShifter.setTileShiftedCloudBulk(sourceRectangle, targetRectangle, azimuth, actual, path, 3);

            assertArrayEquals(expected, actual);
        }
    }

    private static int[] createFlags(int width, int height) {
        final int[] flags = new int[width * height];
        for (int index = 0; index < flags.length; index++) {
            flags[index] = index % 3 == 0 ? PreparationMaskBand.LAND_FLAG : PreparationMaskBand.WATER_FLAG;
            if (index % 11 == 0) {
                flags[index] += PreparationMaskBand.INVALID_FLAG;
            }
        }
        for (int index : new int[]{2, 10, 11, 19, 28, 35, 44, 53}) {
            flags[index] += PreparationMaskBand.CLOUD_FLAG;
        }
        return flags;
    }

    private static LegacyResult legacyShift(Rectangle sourceRectangle, Rectangle targetRectangle, float azimuth,
                                            float[] sourceBand, int[] flags, Point2D[] path) {
        final int width = sourceRectangle.width;
        final int height = sourceRectangle.height;
        final int[] offsets = offsets(sourceRectangle, targetRectangle, azimuth);
        final double[][] means = new double[3][path.length];
        final double[] sums = new double[3];
        final int[] counts = new int[3];
        int cloudLand = 0;
        int cloudWater = 0;

        for (int pathIndex = 1; pathIndex < path.length; pathIndex++) {
            for (int x = offsets[0]; x < width; x++) {
                for (int y = offsets[1]; y < height; y++) {
                    final int sourceIndex = y * width + x;
                    if ((flags[sourceIndex] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG) {
                        continue;
                    }
                    if ((flags[sourceIndex] & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG) {
                        cloudLand++;
                    }
                    if ((flags[sourceIndex] & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG) {
                        cloudWater++;
                    }
                    final int shiftedX = x + (int) path[pathIndex].getX();
                    final int shiftedY = y + (int) path[pathIndex].getY();
                    if (shiftedX < 0 || shiftedX >= width || shiftedY < 0 || shiftedY >= height) {
                        continue;
                    }
                    final int shiftedIndex = shiftedY * width + shiftedX;
                    if ((flags[shiftedIndex] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG &&
                            (flags[shiftedIndex] & PreparationMaskBand.INVALID_FLAG) != PreparationMaskBand.INVALID_FLAG &&
                            (flags[shiftedIndex] & PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) !=
                                    PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) {
                        flags[shiftedIndex] += PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG;
                        sums[0] += sourceBand[shiftedIndex];
                        counts[0]++;
                        if ((flags[shiftedIndex] & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG) {
                            sums[1] += sourceBand[shiftedIndex];
                            counts[1]++;
                        }
                        if ((flags[shiftedIndex] & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG) {
                            sums[2] += sourceBand[shiftedIndex];
                            counts[2]++;
                        }
                    }
                }
            }
            for (int band = 0; band < 3; band++) {
                means[band][pathIndex] = sums[band] / counts[band];
            }
        }
        return new LegacyResult(means, cloudLand, cloudWater);
    }

    private static void legacySetTileShiftedCloudBulk(Rectangle sourceRectangle, Rectangle targetRectangle,
                                                       float azimuth, int[] flags, Point2D[] path, int darkIndex) {
        final int width = sourceRectangle.width;
        final int height = sourceRectangle.height;
        final int[] offsets = offsets(sourceRectangle, targetRectangle, azimuth);
        for (int x = offsets[0]; x < width; x++) {
            for (int y = offsets[1]; y < height; y++) {
                final int sourceIndex = y * width + x;
                if ((flags[sourceIndex] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG) {
                    continue;
                }
                final int shiftedX = x + (int) path[darkIndex].getX();
                final int shiftedY = y + (int) path[darkIndex].getY();
                if (shiftedX >= 0 && shiftedX < width && shiftedY >= 0 && shiftedY < height) {
                    final int shiftedIndex = shiftedY * width + shiftedX;
                    if ((flags[shiftedIndex] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG &&
                            (flags[shiftedIndex] & PreparationMaskBand.INVALID_FLAG) != PreparationMaskBand.INVALID_FLAG &&
                            (flags[shiftedIndex] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) !=
                                    PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) {
                        flags[shiftedIndex] += PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG;
                    }
                }

                final int x1 = x + (int) path[1].getX();
                final int y1 = y + (int) path[1].getY();
                final int x2 = x + (int) path[2].getX();
                final int y2 = y + (int) path[2].getY();
                if (x1 < 0 || x1 >= width || y1 < 0 || y1 >= height ||
                        x2 < 0 || x2 >= width || y2 < 0 || y2 >= height ||
                        ((flags[y1 * width + x1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG &&
                                (flags[y2 * width + x2] & PreparationMaskBand.CLOUD_FLAG) ==
                                        PreparationMaskBand.CLOUD_FLAG)) {
                    continue;
                }
                for (int pathIndex = 1; pathIndex < path.length; pathIndex++) {
                    final int potentialX = x + (int) path[pathIndex].getX();
                    final int potentialY = y + (int) path[pathIndex].getY();
                    if (potentialX < 0 || potentialX >= width || potentialY < 0 || potentialY >= height) {
                        break;
                    }
                    final int potentialIndex = potentialY * width + potentialX;
                    if ((flags[potentialIndex] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG &&
                            (flags[potentialIndex] & PreparationMaskBand.INVALID_FLAG) != PreparationMaskBand.INVALID_FLAG &&
                            (flags[potentialIndex] & PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) !=
                                    PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) {
                        flags[potentialIndex] += PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG;
                    }
                }
            }
        }
    }

    private static int[] offsets(Rectangle sourceRectangle, Rectangle targetRectangle, float azimuth) {
        int xOffset = 0;
        int yOffset = 0;
        if (azimuth < 90.0f) {
            xOffset = targetRectangle.x - sourceRectangle.x;
        } else if (azimuth < 180.0f) {
            xOffset = targetRectangle.x - sourceRectangle.x;
            yOffset = targetRectangle.y - sourceRectangle.y;
        } else if (azimuth < 270.0f) {
            yOffset = targetRectangle.y - sourceRectangle.y;
        }
        return new int[]{xOffset, yOffset};
    }

    private static class LegacyResult {
        private final double[][] means;
        private final int cloudLand;
        private final int cloudWater;

        private LegacyResult(double[][] means, int cloudLand, int cloudWater) {
            this.means = means;
            this.cloudLand = cloudLand;
            this.cloudWater = cloudWater;
        }
    }
}
