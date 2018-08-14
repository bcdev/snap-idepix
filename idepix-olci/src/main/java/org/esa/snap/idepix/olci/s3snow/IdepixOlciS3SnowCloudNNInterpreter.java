package org.esa.snap.idepix.olci.s3snow;

import com.bc.ceres.binding.ValueRange;

/**
 * @author Marco Peters
 */
public class IdepixOlciS3SnowCloudNNInterpreter {

    private static final ValueRange CLEAR_SNOW_ICE_BOUNDS = new ValueRange(0.0, 1.1, true, false);
    private static final ValueRange OPAQUE_CLOUD_BOUNDS = new ValueRange(1.1, 2.75, true, false);
    private static final ValueRange SEMI_TRANS_CLOUD_BOUNDS = new ValueRange(2.75, 3.5, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_LAND = new ValueRange(3.5, 3.85, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_GLINT = new ValueRange(3.5, 3.5, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_NOGLINT = new ValueRange(3.5, 3.75, true, false);

    // currently not used
//    private static final ValueRange CLEAR_LAND_BOUNDS = new ValueRange(3.75, 5.3, true, false);
//    private static final ValueRange CLEAR_WATER_BOUNDS = new ValueRange(5.3, 6.00, true, true);

    private IdepixOlciS3SnowCloudNNInterpreter() {
    }

    // Here we might add the nn as parameter to decide which valueRanges to load
    public static IdepixOlciS3SnowCloudNNInterpreter create() {
        return new IdepixOlciS3SnowCloudNNInterpreter();
    }

    // currently not used
//    boolean isCloudAmbiguous(double nnValue) {
//        return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS.contains(nnValue);
//    }

    boolean isCloudAmbiguous(double nnValue, boolean isLand, boolean considerGlint) {
        if (isLand) {
            return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS_LAND.contains(nnValue);
        } else {
            if (considerGlint) {
                return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS_WATER_GLINT.contains(nnValue);
            } else {
                return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS_WATER_NOGLINT.contains(nnValue);
            }
        }
    }

    boolean isCloudSure(double nnValue) {
        return OPAQUE_CLOUD_BOUNDS.contains(nnValue);
    }

    boolean isSnowIce(double nnValue) {
        return CLEAR_SNOW_ICE_BOUNDS.contains(nnValue);

    }

}
