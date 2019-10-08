package org.esa.snap.idepix.viirs;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 19.10.2016
 * Time: 12:24
 *
 * @author olafd
 */
class ViirsAlgorithm {
    private static final double THRESH_BRIGHT = 0.15;

    private float waterFraction;
    private double[] refl;
    private double[] nnOutput;

    boolean isInvalid() {
        // todo: define if needed
        return false;
    }

//    public boolean isCoastline() {
//        // NOTE that this does not work if we have a PixelGeocoding. In that case, waterFraction
//        // is always 0 or 100!! (TS, OD, 20140502). If so, get a coastline in post processing approach.
//        return waterFraction < 100 && waterFraction > 0;
//    }

    boolean isLand() {
        return waterFraction == 0;
    }


    boolean isSnowIce() {
        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 4.15 && nnOutput[0] <= 5.0;
    }

    boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    boolean isCloudAmbiguous() {
        if (isCloudSure() || isSnowIce()) {   // this check has priority
            return false;
        }

        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 2.15 && nnOutput[0] <= 3.7;
    }

    boolean isCloudSure() {
        if (isSnowIce()) {   // this has priority
            return false;
        }

        // for VIIRS NN, nnOutput has one element:
        // nnOutput[0] =
        // 1 < x < 2.15 : clear
        // 2.15 < x < 3.7 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.7 < x < 4.15 : cloudy --> cloud sure
        // 4.2 < x : clear snow/ice
        // (separation numbers from HS, 20151122)
        return nnOutput[0] > 3.7 && nnOutput[0] <= 4.2;
    }

    boolean isCloudBuffer() {
        // is applied in post processing!
        return false;
    }

    boolean isCloudShadow() {
        // will be applied in post processing once we have an appropriate algorithm
        return false;
    }

    boolean isMixedPixel() {
        // todo
        return false;
    }

//    public boolean isGlintRisk() {
//         todo
//        return false;
//    }

    boolean isBright() {
        return brightValue() > THRESH_BRIGHT;
    }

    ///////////////////////////////////////////////////////////////////////

    float brightValue() {
        return (float) refl[4];   //  rhot_671 (671nm)
    }

    float ndsiValue() {
        return 0.5f; // not yet needed
    }

    ///////////////////////////////////////////////////////////////////////
    // setters
    void setWaterFraction(float waterFraction) {
        this.waterFraction = waterFraction;
    }

    void setRefl(double[] reflectance) {
        refl = reflectance;
    }

    void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

}
