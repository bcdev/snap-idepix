package org.esa.snap.idepix.s2msi.operators.cloudshadow;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.Arrays;

class CloudBulkShifter {

    private double[] sumValue;
    private int[] N;
    private int NCloudLand;
    private int NCloudWater;

    private double[][] meanValuesPath;

    void shiftCloudBulkAlongCloudPathType(Rectangle sourceRectangle, Rectangle targetRectangle,
                                                 float sourceSunAzimuth, float[][] sourceBands,
                                                 int[] flagArray, Point2D[] cloudPath) {
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;

        //search rectangle: tile + extension in certain directions
        int xOffset = 0;
        int yOffset = 0;
        if (sourceSunAzimuth < 90) {
            xOffset = targetRectangle.x - sourceRectangle.x;
        } else if (sourceSunAzimuth < 180) {
            xOffset = targetRectangle.x - sourceRectangle.x;
            yOffset = targetRectangle.y - sourceRectangle.y;
        } else if (sourceSunAzimuth < 270) {
            yOffset = targetRectangle.y - sourceRectangle.y;
        }

        meanValuesPath = new double[3][cloudPath.length];

        sumValue = new double[3]; // Positions: 0: all, 1: only land, 2: only water
        N = new int[3];
        NCloudLand = 0;
        NCloudWater = 0;

        final CloudPixels cloudPixels = collectCloudPixels(flagArray, sourceWidth, sourceHeight,
                xOffset, yOffset);
        final int pathStepCount = cloudPath.length - 1;

        // These counters were historically incremented during every path step. Compute
        // them once and retain the same values without repeatedly visiting non-cloud pixels.
        NCloudLand = cloudPixels.landCount * pathStepCount;
        NCloudWater = cloudPixels.waterCount * pathStepCount;

        for (int path_i = 1; path_i < cloudPath.length; path_i++) {
            // cloudPixelIndices retains the original x-major/y-minor traversal order,
            // so floating-point accumulation and first-hit duplicate handling are unchanged.
            for (int cloudPixelIndex : cloudPixels.indices) {
                accumulateShiftedCloudPixel(cloudPixelIndex, sourceHeight, sourceWidth, cloudPath, path_i,
                        flagArray, sourceBands[1]);
            }
            for (int j = 0; j < 3; j++) {
                meanValuesPath[j][path_i] = sumValue[j] / N[j];
            }
        }
    }

    private void accumulateShiftedCloudPixel(int index0, int height, int width, Point2D[] cloudPath,
                                             int pathIndex, int[] flagArray, float[] sourceBand) {
        final int y0 = index0 / width;
        final int x0 = index0 - y0 * width;
        final int x1 = x0 + (int) cloudPath[pathIndex].getX();
        final int y1 = y0 + (int) cloudPath[pathIndex].getY();
        if (x1 >= width || y1 >= height || x1 < 0 || y1 < 0) {
            return;
        }
        final int index1 = y1 * width + x1;
        if ((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) != PreparationMaskBand.CLOUD_FLAG &&
                (flagArray[index1] & PreparationMaskBand.INVALID_FLAG) != PreparationMaskBand.INVALID_FLAG &&
                (flagArray[index1] & PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) !=
                        PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) {
            flagArray[index1] += PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG;
            sumValue[0] += sourceBand[index1];
            N[0]++;

            if ((flagArray[index1] & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG) {
                sumValue[1] += sourceBand[index1];
                N[1]++;
            }
            if ((flagArray[index1] & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG) {
                sumValue[2] += sourceBand[index1];
                N[2]++;
            }
        }
    }

    static void setTileShiftedCloudBulk(Rectangle sourceRectangle,
                                        Rectangle targetRectangle,
                                        float sourceSunAzimuth,
                                        int[] flagArray, Point2D[] cloudPath, int darkIndex) {
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;

        //search rectangle: tile + extension in certain directions
        int xOffset = 0;
        int yOffset = 0;
        if (sourceSunAzimuth < 90) {
            xOffset = targetRectangle.x - sourceRectangle.x;
        } else if (sourceSunAzimuth < 180) {
            xOffset = targetRectangle.x - sourceRectangle.x;
            yOffset = targetRectangle.y - sourceRectangle.y;
        } else if (sourceSunAzimuth < 270) {
            yOffset = targetRectangle.y - sourceRectangle.y;
        }
        final int[] cloudPixelIndices = collectCloudPixels(flagArray, sourceWidth, sourceHeight,
                xOffset, yOffset).indices;
        for (int cloudPixelIndex : cloudPixelIndices) {
            final int y0 = cloudPixelIndex / sourceWidth;
            final int x0 = cloudPixelIndex - y0 * sourceWidth;
            setShiftedCloudBULK(x0, y0, sourceHeight, sourceWidth, cloudPath, flagArray, darkIndex);
            setPotentialCloudShadowMask(x0, y0, sourceHeight, sourceWidth, cloudPath, flagArray);
        }
    }

    private static CloudPixels collectCloudPixels(int[] flagArray, int width, int height,
                                                  int xOffset, int yOffset) {
        int[] indices = new int[Math.min(1024, Math.max(1, (width - xOffset) * (height - yOffset)))];
        int count = 0;
        int landCount = 0;
        int waterCount = 0;
        for (int x = xOffset; x < width; x++) {
            for (int y = yOffset; y < height; y++) {
                final int index = y * width + x;
                if ((flagArray[index] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) {
                    if (count == indices.length) {
                        indices = Arrays.copyOf(indices, indices.length * 2);
                    }
                    indices[count++] = index;
                    if ((flagArray[index] & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG) {
                        landCount++;
                    }
                    if ((flagArray[index] & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG) {
                        waterCount++;
                    }
                }
            }
        }
        return new CloudPixels(Arrays.copyOf(indices, count), landCount, waterCount);
    }

    private static class CloudPixels {
        private final int[] indices;
        private final int landCount;
        private final int waterCount;

        private CloudPixels(int[] indices, int landCount, int waterCount) {
            this.indices = indices;
            this.landCount = landCount;
            this.waterCount = waterCount;
        }
    }

    private static void setShiftedCloudBULK(int x0, int y0, int height, int width, Point2D[] cloudPath,
                                            int[] flagArray, int darkIndex) {
        int index0 = y0 * width + x0;
        //start from a cloud pixel, otherwise stop.
        if (!((flagArray[index0] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG)) {
            return;
        }


        int x1 = x0 + (int) cloudPath[darkIndex].getX();
        int y1 = y0 + (int) cloudPath[darkIndex].getY();
        if (x1 >= width || y1 >= height || x1 < 0 || y1 < 0) {
            //break; only necessary in the for-loop, which is no longer used.
            return;
        }
        int index1 = y1 * width + x1;

        if (!((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) &&
                !((flagArray[index1] & PreparationMaskBand.INVALID_FLAG) == PreparationMaskBand.INVALID_FLAG)) {


            if (!((flagArray[index1] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG)) {
                flagArray[index1] += PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG;
            }
        }
    }


    private static void setPotentialCloudShadowMask(int x0, int y0, int height, int width, Point2D[] cloudPath,
                                                    int[] flagArray) {
        int index0 = y0 * width + x0;
        //start from a cloud pixel, otherwise stop.
        if (!((flagArray[index0] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG)) {
            return;
        }
        int x1 = x0 + (int) cloudPath[1].getX();
        int y1 = y0 + (int) cloudPath[1].getY();
        int x2 = x0 + (int) cloudPath[2].getX();
        int y2 = y0 + (int) cloudPath[2].getY();
        // cloud edge is used at least 2 pixels deep, otherwise gaps occur due to orientation of cloud edge and cloud path.
        // (Moire-Effect)
        if (x1 >= width || y1 >= height || x1 < 0 || y1 < 0 || x2 >= width || y2 >= height || x2 < 0 || y2 < 0 ||
                ((flagArray[y1 * width + x1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG &&
                        (flagArray[y2 * width + x2] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG)) {
            return;
        }
        for (int i = 1; i < cloudPath.length; i++) {
            x1 = x0 + (int) cloudPath[i].getX();
            y1 = y0 + (int) cloudPath[i].getY();
            if (x1 >= width || y1 >= height || x1 < 0 || y1 < 0) {
                break;
            }
            int index1 = y1 * width + x1;
            if (!((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) &&
                    !((flagArray[index1] & PreparationMaskBand.INVALID_FLAG) == PreparationMaskBand.INVALID_FLAG)) {

                if (!((flagArray[index1] & PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) == PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG)) {
                    flagArray[index1] += PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG;
                }
            }
        }
    }

    double[][] getMeanReflectanceAlongPath() {
        return meanValuesPath;
    }

    int getNCloudOverWater() {
        return NCloudWater;
    }

    int getNCloudOverLand() {
        return NCloudLand;
    }

}
