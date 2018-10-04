package org.esa.s2tbx.s2msi.idepix.operators.cloudshadow;

import org.esa.s2tbx.s2msi.idepix.operators.cloudshadow.fft.PhaseFilter;
import org.esa.snap.core.util.SystemUtils;
import org.jblas.ComplexDoubleMatrix;
import org.jblas.DoubleMatrix;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;

class CloudShadowFlaggerShiftInCloudGaps {

    private static Logger logger = SystemUtils.LOG;

    private int[] flagArray;

    void setShiftedCloudInCloudGaps(Rectangle sourceRectangle, int[] flagArray, Map<Integer, List<Integer>> cloudList,
                                           double[] cloudTestArray, double spatialResolution) {
        int sourceWidth = sourceRectangle.width;
        int sourceHeight = sourceRectangle.height;
        this.flagArray = flagArray;
        if (cloudList.size() > 0) {
            DoubleMatrix cloudFlag = DoubleMatrix.zeros(sourceWidth, sourceHeight);
            for (int key : cloudList.keySet()) {
                List<Integer> positions = cloudList.get(key);
                for (int index : positions) {
                    cloudFlag.put(index, 1.);
                }
            }
            ComplexDoubleMatrix complexCloudFlag = new ComplexDoubleMatrix(cloudFlag);

            //at the moment: fixed kernel width.
            double kernelRadius = 1000.;
            double kernelInnerRadius = 0.8 * kernelRadius;
            double[] spacing = new double[2];
            spacing[0] = spatialResolution;
            spacing[1] = spatialResolution;

            int blockSize = 2 * (int) Math.ceil(kernelRadius / spacing[0]) + 1;
            int overlap = (int) Math.ceil(kernelRadius / spacing[0]);
            PhaseFilter CloudCoverage = new PhaseFilter(complexCloudFlag, blockSize, overlap, kernelRadius, kernelInnerRadius, spacing);

            DoubleMatrix cloudTestMatrix = CloudCoverage.convolutionSimpleGapFinder();
            double[] test = cloudTestMatrix.toArray();

            System.arraycopy(test, 0, cloudTestArray, 0, cloudTestMatrix.length);

            logger.fine("Gap finder finished.");

            //Find continuous areas in shifted cloud shadow.
            int shifted[] = new int[flagArray.length];
            for (int i = 0; i < flagArray.length; i++) {
                if ((flagArray[i] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) {
                    shifted[i] = 1;
                }
            }
            FindContinuousAreas testContinuousShadow = new FindContinuousAreas(shifted);
            int[] shadowIDArray = new int[sourceWidth * sourceHeight];
            Map<Integer, List<Integer>> shiftedShadowTileID =
                    testContinuousShadow.computeAreaID(sourceWidth, sourceHeight, shadowIDArray, false);

            setCoincidingShiftedCloudShadowWithCloudGaps(shiftedShadowTileID, cloudTestArray);

        }

    }


    private void setCoincidingShiftedCloudShadowWithCloudGaps(Map<Integer, List<Integer>> shiftedShadowTileID, double[] cloudGaps) {
        //if a continuous shifted shadow coincides with a cloud gap, keep it.
        List<Integer> coincideKey = new ArrayList<>();
        if (shiftedShadowTileID.size() > 0) {
            for (int key : shiftedShadowTileID.keySet()) {
                List<Integer> positions = shiftedShadowTileID.get(key);
                for (Integer position : positions) {
                    int ind = position;
                    if (((flagArray[ind] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_FLAG)
                            && (cloudGaps[ind] < -0.1)) {
                        coincideKey.add(key);
                        break;
                    }
                }
            }
            if (coincideKey.size() > 0) {
                for (int key : coincideKey) {
                    List<Integer> positions = shiftedShadowTileID.get(key);
                    for (int index1 : positions) {
                        if (!((flagArray[index1] & PreparationMaskBand.SHIFTED_CLOUD_SHADOW_GAPS_FLAG) == PreparationMaskBand.SHIFTED_CLOUD_SHADOW_GAPS_FLAG) &&
                                !((flagArray[index1] & PreparationMaskBand.CLOUD_FLAG) == PreparationMaskBand.CLOUD_FLAG) &&
                                !((flagArray[index1] & PreparationMaskBand.INVALID_FLAG) == PreparationMaskBand.INVALID_FLAG)) {
                            flagArray[index1] += PreparationMaskBand.SHIFTED_CLOUD_SHADOW_GAPS_FLAG;
                        }
                    }

                }
            }
        }
    }

}
