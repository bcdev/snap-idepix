package org.esa.snap.idepix.spotvgt;

import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.pixel.AbstractPixelProperties;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.IdepixUtils;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.util.math.MathUtils;

/**
 * IDEPIX pixel identification algorithm for VGT
 *
 * @author olafd
 */
public class VgtAlgorithm extends AbstractPixelProperties {

    private static final float LAND_THRESH = 0.9f;
    private static final float WATER_THRESH = 0.9f;

    private float[] refl;

    private static final float BRIGHTWHITE_THRESH = 0.65f;
    private static final float NDSI_THRESH = 0.50f;
//    private static final float PRESSURE_THRESH = 0.9f;
    private static final float CLOUD_THRESH = 1.65f;
    private static final float UNCERTAINTY_VALUE = 0.5f;
    private static final float BRIGHT_THRESH = 0.3f;
    private static final float WHITE_THRESH = 0.5f;
    private static final float BRIGHT_FOR_WHITE_THRESH = 0.2f;
//    private static final float NDVI_THRESH = 0.4f;
    private static final float REFL835_WATER_THRESH = 0.1f;
    private static final float REFL835_LAND_THRESH = 0.15f;

    private boolean smLand;
    private double[] nnOutput;

    private boolean isWater;
    private boolean usel1bLandWaterFlag;
    private boolean isCoastline;

    public boolean isInvalid() {
        return !IdepixIO.areAllReflectancesValid(refl);
    }

    public boolean isClearSnow() {
        return (!isInvalid() && isLand() && isBrightWhite() && ndsiValue() > getNdsiThreshold());
    }

    public boolean isClearLand() {
        if (isInvalid()) {
            return false;
        }
        float landValue;

        if (!MathUtils.equalValues(radiometricLandValue(), UNCERTAINTY_VALUE)) {
            landValue = radiometricLandValue();
        } else if (aPrioriLandValue() > UNCERTAINTY_VALUE) {
            landValue = aPrioriLandValue();
        } else {
            return false; // this means: if we have no information about land, we return isClearLand = false
        }
        return (!isWater && !isCloud() && landValue > LAND_THRESH);
    }

    public boolean isClearWater() {
        if (isInvalid()) {
            return false;
        }
        float waterValue;
        if (!MathUtils.equalValues(radiometricWaterValue(), UNCERTAINTY_VALUE)) {
            waterValue = radiometricWaterValue();
        } else if (aPrioriWaterValue() > UNCERTAINTY_VALUE) {
            waterValue = aPrioriWaterValue();
        } else {
            return false; // this means: if we have no information about water, we return isClearWater = false
        }
        return (isWater && !isCloud() && waterValue > WATER_THRESH);
    }



    public boolean isCloud() {
        if (!isInvalid()) {
            return ((whiteValue() + brightValue() + pressureValue() + temperatureValue() > CLOUD_THRESH) && !isClearSnow());
        }
        return false;
    }

    public boolean isLand() {
        final boolean isLand1 = !usel1bLandWaterFlag && !isWater;
        return !isInvalid() && (isLand1 || (aPrioriLandValue() > LAND_THRESH));
    }

    public boolean isWater() {
        return !isInvalid() && isWater;
    }

    @Override
    public boolean isL1Water() {
        return false;
    }

    public boolean isBrightWhite() {
        return !isInvalid() && (whiteValue() + brightValue() > getBrightWhiteThreshold());
    }

    public boolean isBright() {
        return (!isInvalid() && brightValue() > getBrightThreshold());
    }

    public boolean isWhite() {
        return (!isInvalid() && whiteValue() > getWhiteThreshold());
    }

//    public boolean isVegRisk() {
//        return (!isInvalid() && ndviValue() > getNdviThreshold());
//    }

//    public boolean isHigh() {
//        return (!isInvalid() && pressureValue() > getPressureThreshold());
//    }


//    public boolean isSeaIce() {
//         // no algorithm available for VGT only
//        return false;
//    }

    public boolean isGlintRisk() {
        return false;
    }

    float brightValue() {
        double value;

        // do not make a difference any more
        // (changed for LC VGT processing because of clouds in rivers with new water mask, 20130227)
        value = (refl[0] + refl[1]) / 2.0f;

        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    float temperatureValue() {
        return UNCERTAINTY_VALUE;
    }

    float spectralFlatnessValue() {
        final double slope0 = IdepixUtils.spectralSlope(refl[0], refl[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[0],
                                                        IdepixConstants.VGT_WAVELENGTHS[1]);
        final double slope1 = IdepixUtils.spectralSlope(refl[1], refl[2],
                                                        IdepixConstants.VGT_WAVELENGTHS[1],
                                                        IdepixConstants.VGT_WAVELENGTHS[2]);
        final double flatness = 1.0f - Math.abs(2000.0 * (slope0 + slope1) / 2.0);
        return (float) Math.max(0.0f, flatness);
    }

    float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0f;
        }
    }

    float ndsiValue() {
        // NDSI (RED-SWIR)/(RED+ SWIR)
        double value = (refl[2] - refl[3]) / (refl[2] + refl[3]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    float ndviValue() {
        double value = (refl[2] - refl[1]) / (refl[2] + refl[1]);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    private float pressureValue() {
        return UNCERTAINTY_VALUE;
    }

    float glintRiskValue() {
        return IdepixUtils.spectralSlope(refl[0], refl[1], IdepixConstants.VGT_WAVELENGTHS[0],
                                         IdepixConstants.VGT_WAVELENGTHS[1]);
    }

    private float aPrioriLandValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    private float aPrioriWaterValue() {
        if (isInvalid()) {
            return UNCERTAINTY_VALUE;
        } else if (!smLand) {
            return 1.0f;
        } else {
            return 0.0f;
        }
    }

    float radiometricLandValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[2] > refl[1] && refl[2] > REFL835_LAND_THRESH) {
            return 1.0f;
        } else if (refl[2] > REFL835_LAND_THRESH) {
            return 0.75f;
        } else {
            return 0.25f;
        }
    }

    float radiometricWaterValue() {
        if (isInvalid() || isCloud()) {
            return UNCERTAINTY_VALUE;
        } else if (refl[0] > refl[1] && refl[1] > refl[2] && refl[2] < REFL835_WATER_THRESH) {
            return 1.0f;
        } else {
            return 0.25f;
        }
    }

    private float getBrightWhiteThreshold() {
        return BRIGHTWHITE_THRESH;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private float getNdsiThreshold() {
        return NDSI_THRESH;  //To change body of implemented methods use File | Settings | File Templates.
    }

//    public float getNdviThreshold() {
//        return NDVI_THRESH;
//    }

    private float getBrightThreshold() {
        return BRIGHT_THRESH;
    }

    private float getWhiteThreshold() {
        return WHITE_THRESH;
    }

//    public float getPressureThreshold() {
//        return PRESSURE_THRESH;
//    }

    // setters for VGT specific quantities

    void setSmLand(boolean smLand) {
        this.smLand = smLand;
    }

    void setIsCoastline(boolean isCoastline) {
        this.isCoastline = isCoastline;
    }

    public void setIsWater(boolean isWater) {
        this.isWater = !isInvalid() && isWater;
    }

    void setRefl(float[] refl) {
        if (refl.length != IdepixConstants.VGT_WAVELENGTHS.length) {
            throw new OperatorException("VGT pixel processing: Invalid number of wavelengths [" + refl.length +
                                                "] - must be " + IdepixConstants.VGT_WAVELENGTHS.length);
        }
        this.refl = refl;
    }

    void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

    double[] getNnOutput() {
        return nnOutput;
    }

    boolean isCoastline() {
        return isCoastline;
    }
}
