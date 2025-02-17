package org.esa.snap.idepix.s2msi;

import org.esa.snap.idepix.s2msi.util.S2IdepixConstants;
import org.esa.snap.idepix.s2msi.util.S2IdepixUtils;
import org.esa.snap.core.util.math.MathUtils;

/**
 * IDEPIX pixel identification algorithm for Sentinel-2 (MSI instrument)
 *
 * @author olafd
 */
public class S2IdepixAlgorithm {

    static final float UNCERTAINTY_VALUE = 0.5f;
    static final float LAND_THRESH = 0.9f;
    static final float WATER_THRESH = 0.9f;

    static final float BRIGHTWHITE_THRESH = 1.5f;
//    static final float NDSI_THRESH = 0.6f;
    static final float NDSI_THRESH = 0.15f;  // JW, 20250217
    //    static final float BRIGHT_THRESH = 0.25f;
    static final float BRIGHT_THRESH = 0.45f;      // JW, GK 20220825
    static final float BRIGHT_FOR_WHITE_THRESH = 0.8f;
    static final float WHITE_THRESH = 0.9f;
    static final float NDVI_THRESH = 0.5f;

    static final float B3B11_THRESH = 1.0f;

    static final float GCW_THRESH = -0.1f;
    static final float TCW_TC_THRESH = -0.08f;
    static final float TCW_NDWI_THRESH = 0.4f;
    static final float ELEVATION_THRESH = 2000.0f;

    private float[] refl;
    private double brr442Thresh;
    private double elevation;
    private boolean isLand;

    private double cwThresh;
    private double gclThresh;
    private double clThresh;
    private boolean isInvalid;

//    static final float TC1_THRESH = 0.36f;
    static final float TC1_THRESH = 0.2f;  // JW, 20250217

    private double lat;
    static final float ELEVATION_SNOW_THRESH = 3000.0f;
    static final float VISBRIGHT_THRESH = 0.12f;
    static final float TCL_TRESH = -0.085f;
    static final float CLA_THRESH = 0.004f;

    public boolean isBrightWhite() {
        return !isInvalid() && (whiteValue() + brightValue() > getBrightWhiteThreshold());
    }

    public boolean isCloud() {
        return isCloudSure() || isCloudAmbiguous();
    }

    public boolean isCloudSure() {
        final boolean gcw = tc4CirrusValue() < GCW_THRESH;
        final boolean tcw = tc4Value() < TCW_TC_THRESH && ndwiValue() < TCW_NDWI_THRESH;
        final boolean acw = isB3B11Water() && (gcw || tcw);
        final boolean gcl = !isB3B11Water() && tc4CirrusValue() < gclThresh && visbrightValue() > VISBRIGHT_THRESH;
        return !isInvalid() && !isClearSnow() && (acw || gcl);

    }

    public boolean isCloudAmbiguous() {
        final boolean tcl = !isB3B11Water() && tc4CirrusValue() < TCL_TRESH && visbrightValue() > VISBRIGHT_THRESH;
        return !isInvalid() && !isClearSnow() && !isCloudSure() && (tcl);
    }

    public boolean isCirrus() {
//      B10 > thresh + (thresh * exp(elevation/1000))
        final boolean cl = refl[10] > clThresh + (clThresh * Math.exp(elevation / 1000));
        final boolean cw = refl[10] > cwThresh + (cwThresh * Math.exp(elevation / 1000));
        return !isInvalid() && !isClearSnow() && (cw || cl);
    }

    public boolean isCirrusAmbiguous() {
        //      B10 > thresh + (thresh * exp(elevation/1000))
        final boolean cla = refl[10] > CLA_THRESH + (CLA_THRESH * Math.exp(elevation / 1000));
        return !isInvalid() && !isClearSnow() && (cla);
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
        return (!isCloudSure() && !isCloudAmbiguous() && !isCirrus() && !isCirrusAmbiguous() && landValue > LAND_THRESH);
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
        return (!isCloudSure() && !isCloudAmbiguous() && !isCirrus() && !isCirrusAmbiguous()
                && !isClearSnow() && !isBrightWhite() && waterValue > WATER_THRESH);
    }

    public boolean isClearSnow() {
        return (!isInvalid() && !(lat < 30 && lat > -30) &&
                ndsiValue() > getNdsiThreshold() &&
                !(isB3B11Water() && (tc1Value() < getTc1Threshold()))) ||
                (!isInvalid() && (lat < 30 && lat > -30) &&
                        elevation > getElevationSnowThreshold() &&
                        ndsiValue() > getNdsiThreshold() &&
                        !(isB3B11Water() && (tc1Value() < getTc1Threshold())));
    }

    public boolean isLand() {
        return isLand;
    }

    public boolean isWater() {
        return !isInvalid() && !isLand();
    }

    public boolean isBright() {
        return brightValue() > getBrightThreshold();
    }

    public boolean isWhite() {
        return whiteValue() > getWhiteThreshold();
    }

    public boolean isVegRisk() {
        return ndviValue() > getNdviThreshold();
    }

    public boolean isB3B11Water() {
        return b3b11Value() > B3B11_THRESH;
    }

    public boolean isInvalid() {
        return isInvalid;
    }

    public float b3b11Value() {
        return (refl[2] / refl[11]);
    }

    public float visbrightValue() {
        return (refl[1] + refl[2] + refl[3]) / 3;
    }

    public float tc1Value() {
        return (0.3029f * refl[1] + 0.2786f * refl[2] + 0.4733f * refl[3] + 0.5599f * refl[8] + 0.508f * refl[11] + 0.1872f * refl[12]);
    }

    public float tc4Value() {
        return (-0.8239f * refl[1] + 0.0849f * refl[2] + 0.4396f * refl[3] - 0.058f * refl[8] + 0.2013f * refl[11] - 0.2773f * refl[12]);
    }

    public float tc4CirrusValue() {
        return (-0.8239f * refl[1] + 0.0849f * refl[2] + 0.4396f * refl[3] - 0.058f * refl[8] + 0.2013f * refl[11] - 0.2773f * refl[12] - refl[10]);
    }

    public float ndwiValue() {
        return ((refl[8] - refl[11]) / (refl[8] + refl[11]));
    }

    public float spectralFlatnessValue() {
        final double slope0 = S2IdepixUtils.spectralSlope(refl[1], refl[0],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[1],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[0]);
        final double slope1 = S2IdepixUtils.spectralSlope(refl[2], refl[3],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[2],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[3]);
        final double slope2 = S2IdepixUtils.spectralSlope(refl[4], refl[6],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[4],
                S2IdepixConstants.S2_MSI_WAVELENGTHS[6]);

        final double flatness = 1.0f - Math.abs(1000.0 * (slope0 + slope1 + slope2) / 3.0);
        return (float) Math.max(0.0f, flatness);
    }

    public float whiteValue() {
        if (brightValue() > BRIGHT_FOR_WHITE_THRESH) {
            return spectralFlatnessValue();
        } else {
            return 0.0f;
        }
    }

    public float brightValue() {
//        if (refl[0] <= 0.0 || brr442Thresh <= 0.0) {
//            return S2IdepixConstants.NO_DATA_VALUE;
//        } else {
//            return (float) (refl[0] / (6.0 * brr442Thresh));
//        }

        // JW, GK 20220825:
        // use 0.3029*B2 + 0.2786*B3 + 0.4733*B4 + 0.5599*B8A + 0.508*B11 + 0.1872*B12
        if (refl[1] <= 0.0 || refl[2] <= 0.0 || refl[3] <= 0.0 || refl[8] <= 0.0 ||
                refl[11] <= 0.0 || refl[12] <= 0.0) {
            return S2IdepixConstants.NO_DATA_VALUE;
        } else {
            return (float) (0.3029 * refl[1] + 0.2786 * refl[2] + 0.4733 * refl[3] +
                    0.5599 * refl[8] + 0.508 * refl[11] + 0.1872 * refl[12]);
        }
    }

    public float ndsiValue() {
        return (refl[2] - refl[11]) / (refl[2] + refl[11]);
    }

    public float ndviValue() {
        double value = (refl[8] - refl[3]) / (refl[8] + refl[3]);
        value = 0.5 * (value + 1);
        value = Math.min(value, 1.0);
        value = Math.max(value, 0.0);
        return (float) value;
    }

    public float aPrioriLandValue() {
        return radiometricLandValue();
    }

    public float aPrioriWaterValue() {
        return radiometricWaterValue();
    }

    // SETTERS
    public void setRhoToa442Thresh(double brr442Thresh) {
        this.brr442Thresh = brr442Thresh;
    }

    public void setRefl(float[] refl) {
        this.refl = refl;
    }

    public void setIsLand(boolean isLand) {
        this.isLand = isLand;
    }

    public void setElevation(double elevation) {
        this.elevation = elevation;
    }

    public void setCwThresh(double cwThresh) {
        this.cwThresh = cwThresh;
    }

    public void setGclThresh(double gclThresh) {
        this.gclThresh = gclThresh;
    }

    public void setClThresh(double clThresh) {
        this.clThresh = clThresh;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    // GETTERS
    public float getNdsiThreshold() {
        return NDSI_THRESH;
    }

    public float getNdviThreshold() {
        return NDVI_THRESH;
    }

    public float getTc1Threshold() {
        return TC1_THRESH;
    }

    public float getBrightThreshold() {
        return BRIGHT_THRESH;
    }

    public float getWhiteThreshold() {
        return WHITE_THRESH;
    }

    public float temperatureValue() {
        return UNCERTAINTY_VALUE;
    }

    public float radiometricLandValue() {
        if (refl[7] >= refl[3]) {
            return 1.0f;
        } else {
            return UNCERTAINTY_VALUE;
        }
    }

    public float radiometricWaterValue() {
        if (refl[7] < refl[3]) {
            return 1.0f;
        } else {
            return UNCERTAINTY_VALUE;
        }
    }

    public float getBrightWhiteThreshold() {
        return BRIGHTWHITE_THRESH;
    }

    public float glintRiskValue() {
        return UNCERTAINTY_VALUE;
    }

    public void setInvalid(boolean isInvalid) {
        this.isInvalid = isInvalid;
    }

    public double getElevationSnowThreshold() {
        return ELEVATION_SNOW_THRESH;
    }

}
