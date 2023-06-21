package org.esa.snap.idepix.olci;

import com.bc.ceres.binding.ValueRange;

/**
 * @author Marco Peters
 */
class IdepixOlciCloudNNInterpreter {

//    private static final ValueRange CLEAR_SNOW_ICE_BOUNDS = new ValueRange(0.0, 1.1, true, false);
//    private static final ValueRange OPAQUE_CLOUD_BOUNDS = new ValueRange(1.1, 2.75, true, false);
//    private static final ValueRange SEMI_TRANS_CLOUD_BOUNDS = new ValueRange(2.75, 3.5, true, false);
//    private static final ValueRange SPATIAL_MIXED_BOUNDS_LAND = new ValueRange(3.5, 3.85, true, false);
//    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_GLINT = new ValueRange(3.5, 3.5, true, false);
//    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_NOGLINT = new ValueRange(3.5, 3.75, true, false);

    // currently not used
//    private static final ValueRange CLEAR_LAND_BOUNDS = new ValueRange(3.75, 5.3, true, false);
//    private static final ValueRange CLEAR_WATER_BOUNDS = new ValueRange(5.3, 6.00, true, true);

    private IdepixOlciCloudNNInterpreter() {}

    // Here we might add the nn as parameter to decide which valueRanges to load
    static IdepixOlciCloudNNInterpreter create() {
        return new IdepixOlciCloudNNInterpreter();
    }

    // currently not used
//    boolean isCloudAmbiguous(double nnValue) {
//        return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS.contains(nnValue);
//    }

    boolean isCloudAmbiguous(double nnValue, boolean isLand, boolean considerGlint) {
        if (isLand) {
            return NNThreshold.SEMI_TRANS_CLOUD_BOUNDS.range.contains(nnValue) ||
                    NNThreshold.SPATIAL_MIXED_BOUNDS_LAND.range.contains(nnValue);
        } else {
            if (considerGlint) {
                return NNThreshold.SEMI_TRANS_CLOUD_BOUNDS.range.contains(nnValue) ||
                        NNThreshold.SPATIAL_MIXED_BOUNDS_WATER_GLINT.range.contains(nnValue);
            } else {
                return NNThreshold.SEMI_TRANS_CLOUD_BOUNDS.range.contains(nnValue) ||
                        NNThreshold.SPATIAL_MIXED_BOUNDS_WATER_NOGLINT.range.contains(nnValue);
            }
        }
    }

    boolean isCloudSure(double nnValue) {
        return NNThreshold.OPAQUE_CLOUD_BOUNDS.range.contains(nnValue);
    }

    boolean isSnowIce(double nnValue) {
        return NNThreshold.CLEAR_SNOW_ICE_BOUNDS.range.contains(nnValue);

    }

    // Not absolutely elegant as there is one instance of this enum while we could have several interpreters.
    // But this was the same with the former constants defined in this class.
    public enum NNThreshold {
        CLEAR_SNOW_ICE_BOUNDS,
        OPAQUE_CLOUD_BOUNDS,
        SEMI_TRANS_CLOUD_BOUNDS,
        SPATIAL_MIXED_BOUNDS_LAND,
        SPATIAL_MIXED_BOUNDS_WATER_GLINT,
        SPATIAL_MIXED_BOUNDS_WATER_NOGLINT,
        CLEAR_LAND_BOUNDS,
        CLEAR_WATER_BOUNDS;

        public ValueRange range;
    }
}
