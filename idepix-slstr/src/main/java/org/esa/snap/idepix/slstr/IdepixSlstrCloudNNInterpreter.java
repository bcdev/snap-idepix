package org.esa.snap.idepix.slstr;

import com.bc.ceres.binding.ValueRange;

/**
 * @author Marco Peters
 */
class IdepixSlstrCloudNNInterpreter {

//    Schiller:
//    target
//    1      class 6    clear snow/ice
//    2      class 1    opaque cloud
//    3      class 2    semi-transparent cloud
//    4      class 3    spatially mixed cloud
//    5      class 4    clear Land
//    6      class 5    clear water
//    und den cuts best
//    1.7    2.5    3.46    4.54    5.46

    private static final ValueRange CLEAR_SNOW_ICE_BOUNDS = new ValueRange(0.0, 1.68, true, false);
    private static final ValueRange OPAQUE_CLOUD_BOUNDS = new ValueRange(1.68, 2.37, true, false);
    private static final ValueRange SEMI_TRANS_CLOUD_BOUNDS = new ValueRange(2.37, 3.6, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_LAND = new ValueRange(3.6, 4.79, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_GLINT = new ValueRange(3.6, 3.6, true, false);
    private static final ValueRange SPATIAL_MIXED_BOUNDS_WATER_NOGLINT = new ValueRange(3.6, 4.2, true, false);
    private static final ValueRange CLEAR_LAND_BOUNDS = new ValueRange(4.79, 5.16, true, false);
    private static final ValueRange CLEAR_WATER_BOUNDS = new ValueRange(5.16, 6.0, true, false);

    private IdepixSlstrCloudNNInterpreter() {
    }

    // Here we might add the nn as parameter to decide which valueRanges to load
    static IdepixSlstrCloudNNInterpreter create() {
        return new IdepixSlstrCloudNNInterpreter();
    }

    boolean isCloudAmbiguous(double nnValue) {
        return SEMI_TRANS_CLOUD_BOUNDS.contains(nnValue) || SPATIAL_MIXED_BOUNDS_LAND.contains(nnValue);
    }

    boolean isCloudSure(double nnValue) {
        return OPAQUE_CLOUD_BOUNDS.contains(nnValue);
    }

    boolean isSnowIce(double nnValue) {
        return CLEAR_SNOW_ICE_BOUNDS.contains(nnValue);
    }

    boolean isGlint(double nnValue) {
        // always false with boundaries above. todo: discuss
        return SPATIAL_MIXED_BOUNDS_WATER_GLINT.contains(nnValue);
    }

    boolean isClearLand(double nnValue) {
        return CLEAR_LAND_BOUNDS.contains(nnValue);
    }

    boolean isClearWater(double nnValue) {
        return CLEAR_WATER_BOUNDS.contains(nnValue);
    }

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
