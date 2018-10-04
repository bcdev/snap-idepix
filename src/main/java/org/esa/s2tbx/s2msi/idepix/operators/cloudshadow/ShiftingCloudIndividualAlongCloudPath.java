package org.esa.s2tbx.s2msi.idepix.operators.cloudshadow;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ShiftingCloudIndividualAlongCloudPath {

    private static final double MAXCLOUD_TOP = S2IdepixPreCloudShadowOp.maxcloudTop;
    private static final double MINCLOUD_BASE = S2IdepixPreCloudShadowOp.mincloudBase;

    private double[] sumValue;
    private int[] N;

    private double[][] meanValuesPath;

    public void ShiftingCloudIndividualAlongCloudPath(Rectangle sourceRectangle,
                                                      Rectangle targetRectangle,
                                                      float[][] sourceBands,
                                                      int[] flagArray, Point2D[] cloudPath, Map<Integer, List<Integer>> cloudList) {
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;


        for (int key : cloudList.keySet()) {
            List<Integer> cloud = cloudList.get(key);

            meanValuesPath = new double[3][cloudPath.length];
            //int[] NPath = new int[cloudPath.length];

            sumValue = new double[3]; // Positions: 0: all, 1: only land, 2: only water
            N = new int[3];

            for (int path_i = 1; path_i < cloudPath.length; path_i++) {

                for (int index : cloud) {
                    int[] x = revertIndexToXY(index, sourceWidth);
                    simpleShiftedCloudMask_and_meanRefl_alongPath(x[0], x[1], sourceHeight, sourceWidth, cloudPath, path_i, flagArray, sourceBands[1]);

                }


                for (int j = 0; j < 3; j++) {
                    if (N[j] > 0) meanValuesPath[j][path_i] = sumValue[j] / N[j];
                    //NPath[path_i] = N[0];
                }
            }

            //Find the relative Minimum
            // over all pixel
            int offset = findOverallMinimumReflectanceSimple(meanValuesPath[0]);

            setTileShiftedCloudIndividual(sourceRectangle, flagArray, cloudPath, offset, cloud);

        }

    }

    private int[] revertIndexToXY(int index, int width) {
        //int index0 = y0 * width + x0;

        int y = Math.floorDiv(index, width);
        int x = index - y * width;

        return new int[]{x, y};
    }

    private int findOverallMinimumReflectanceSimple(double[] meanRefl) {

        boolean exclude = false;

        List<Integer> relativeMinimum = indecesRelativMaxInArray(meanRefl, false);
        //System.out.println(relativeMinimum.toArray());
        if (relativeMinimum.contains(0)) relativeMinimum.remove(relativeMinimum.indexOf(0));
        if (relativeMinimum.contains(meanRefl.length - 1))
            relativeMinimum.remove(relativeMinimum.indexOf(meanRefl.length - 1));

        if (relativeMinimum.size() == 0) exclude = true;

        int offset = 0;

        if (relativeMinimum.size() > 0 && !exclude) offset = relativeMinimum.get(0);

        return offset;
    }

    private List<Integer> indecesRelativMaxInArray(double[] x, boolean findMax) {
        int lx = x.length;

        List<Integer> ID = new ArrayList<>();

        boolean valid = true;
        int i = 0;
        while (i < lx && valid) {
            if (Double.isNaN(x[i])) valid = false;
            i++;
        }

        if (valid) {
            double fac = 1.;
            if (!findMax) fac = -1.;

            if (fac * x[0] > fac * x[1]) ID.add(0);
            if (fac * x[lx - 1] > fac * x[lx - 2]) ID.add(lx - 1);

            for (i = 1; i < lx - 1; i++) {
                if (fac * x[i] > fac * x[i - 1] && fac * x[i] > fac * x[i + 1]) ID.add(i);
            }
        } else {
            ID.add(0);
            ID.add(lx - 1);
        }

        return ID;
    }

    private void setTileShiftedCloudIndividual(Rectangle sourceRectangle,
                                               int[] flagArray, Point2D[] cloudPath, int darkIndex, List<Integer> cloud) {
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;


        for (int index0 : cloud) {

            //start from a cloud pixel, otherwise stop.
            if (!((flagArray[index0] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG)) {
                return;
            }

            int[] x = revertIndexToXY(index0, sourceWidth);

            int x1 = x[0] + (int) cloudPath[darkIndex].getX();
            int y1 = x[1] + (int) cloudPath[darkIndex].getY();
            if (x1 >= sourceWidth || y1 >= sourceHeight || x1 < 0 || y1 < 0) {
                //break; only necessary in the for-loop, which is no longer used.
                return;
            }
            int index1 = y1 * sourceWidth + x1;

            if (!((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) &&
                    !((flagArray[index1] & PreparationMaskBand.INVALID_FLAG) == PreparationMaskBand.INVALID_FLAG)) {


                if (!((flagArray[index1] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG)) {
                    flagArray[index1] += PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG;
                }
            }

        }
    }


    private void simpleShiftedCloudMask_and_meanRefl_alongPath(int x0, int y0, int height, int width, Point2D[] cloudPath, int end_path_i,
                                                               int[] flagArray, float[] sourceBand) {
        int index0 = y0 * width + x0;
        //start from a cloud pixel, otherwise stop.
        if (!((flagArray[index0] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG)) {
            return;
        }

        for (int i = end_path_i; i < end_path_i + 1; i++) {


            int x1 = x0 + (int) cloudPath[i].getX();
            int y1 = y0 + (int) cloudPath[i].getY();
            if (x1 >= width || y1 >= height || x1 < 0 || y1 < 0) {
                break;
            }
            int index1 = y1 * width + x1;

            if (!((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) &&
                    !((flagArray[index1] & PreparationMaskBand.INVALID_FLAG) == PreparationMaskBand.INVALID_FLAG)) {

                if (!((flagArray[index1] & PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG) == PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG)) {
                    flagArray[index1] += PreparationMaskBand.POTENTIAL_CLOUD_SHADOW_FLAG;
                }

                this.sumValue[0] += sourceBand[index1];
                this.N[0] += 1;

                if (((flagArray[index1] & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG)) {
                    this.sumValue[1] += sourceBand[index1];
                    this.N[1] += 1;
                }
                if (((flagArray[index1] & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG)) {
                    this.sumValue[2] += sourceBand[index1];
                    this.N[2] += 1;

                }
            }
        }
    }

    /*private static void setShiftedCloudBULK(int x0, int y0, int height, int width, Point2D[] cloudPath,
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


            if(!((flagArray[index1] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG)) {
                flagArray[index1] += PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG;
            }
        }
    }*/


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

    public double[][] getMeanReflectanceAlongPath() {
        return meanValuesPath;
    }


    private class PotentialShadowAnalyzerMode {

        private final float[][] sourceBands;
        int counterA;
        int counterB;
        private double[][] arrayBands;
        private int[][] arrayIndexes;
        private double[] minArrayBands;
        private int[] flagArray;
        double[] mean;
        //private final float[] landThreshholds;
        //private final float[] waterThreshholds;
        //private final double landMean;
        //private final double waterMean;

        PotentialShadowAnalyzerMode(float[][] sourceBands, int[] flagArray) {
            if (sourceBands.length != 2) {
                throw new IllegalArgumentException("Two bands required for land water analysis mode");
            }
            this.sourceBands = sourceBands;
            this.flagArray = flagArray;
            //landThreshholds = getThresholds(sourceBands, false, true);
            //waterThreshholds = getThresholds(sourceBands, true, false);
            //landMean = nonCloudMeans(sourceBands, flagArray, false, true)[0];
            //waterMean = nonCloudMeans(sourceBands, flagArray, true, false)[1];
        }

        public void initArrays(int size) {
            arrayBands = new double[2][size];
            arrayIndexes = new int[2][size];
            minArrayBands = new double[2];
            mean = new double[2];
            counterA = 0;
            counterB = 0;
            for (int i = 0; i < 2; i++) {
                Arrays.fill(arrayBands[i], Double.NaN);
                Arrays.fill(arrayIndexes[i], -1);
                minArrayBands[i] = Double.MAX_VALUE;
                mean[i] = 0.;
            }
        }

        public void doIterationStep(int index) {
            // from LandWaterAnalyzerMode
            // land and water pixels are handled independently.
            // might not be necessary in this case
            // two bands are analysed. geo-positioning is not identical in different S2-bands!
            final int flag = flagArray[index];
            arrayBands[0][counterA] = sourceBands[0][index];
            arrayBands[1][counterA] = sourceBands[1][index];

            if (arrayBands[0][counterA] >= 1e-8 && !Double.isNaN(arrayBands[0][counterA]) &&
                    (((flag & PreparationMaskBand.LAND_FLAG) == PreparationMaskBand.LAND_FLAG) ||
                            (flag & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG)) {
                arrayIndexes[0][counterA] = index;

                if (arrayBands[0][counterA] < minArrayBands[0]) {
                    minArrayBands[0] = arrayBands[0][counterA];
                }
                counterA++;
            }
        /*else if (arrayBands[1][counterB] >= 1e-8 && !Double.isNaN(arrayBands[1][counterB]) &&
                (flag & PreparationMaskBand.WATER_FLAG) == PreparationMaskBand.WATER_FLAG) {
            arrayIndexes[1][counterB] = index;

            if (arrayBands[1][counterB] < minArrayBands[1]) {
                minArrayBands[1] = arrayBands[1][counterB];
            }
            counterB++;
        }*/
        }

        public double[] calculateMean() {
            for (int i = 0; i < counterA; i++) {
                mean[0] += arrayBands[0][i];
                mean[1] += arrayBands[1][i];
            }

            for (int j = 0; j < arrayBands.length; j++) {
                mean[j] /= counterA;
            }
            return mean;
        }

    }


}
