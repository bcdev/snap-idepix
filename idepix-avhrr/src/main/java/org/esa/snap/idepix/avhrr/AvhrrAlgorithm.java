package org.esa.snap.idepix.avhrr;

import org.esa.snap.idepix.core.util.IdepixIO;

/**
 * IDEPIX instrument-specific pixel identification algorithm for GlobAlbedo: abstract superclass
 *
 * @author olafd
 */
public class AvhrrAlgorithm implements AvhrrPixelProperties {

    private float waterFraction;
    private double[] radiance;
    private double[] nnOutput;

    private double avhrracSchillerNNCloudAmbiguousSureSeparationValue;
    private double avhrracSchillerNNCloudSureSnowSeparationValue;

    private double reflCh1;
    private double reflCh2;
    private double reflCh3;
    //    double btCh3;
    private double btCh4;
    private double btCh5;

    private double ndsi;

    private double latitude;
    private double longitude;
    private double elevation;

    @Override
    public boolean isInvalid() {
        return !IdepixIO.areAllReflectancesValid(radiance);
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isSnowIce() {

//        final double ndsi = (reflCh3 - reflCh1) / (reflCh3 + reflCh1);
        boolean isSnowIceOne = (notIsCloudTgct() && ndsi > 0.7) || (reflCh1 > 1.1 && ndsi > 0.4);

        if (!isSnowIceOne && nnOutput != null) {
            isSnowIceOne = nnOutput[0] > avhrracSchillerNNCloudSureSnowSeparationValue && nnOutput[0] <= 5.0;
        }

        boolean isSnowIceTwo;
        final double btCh4Celsius = btCh4 - 273.15;
        final double ratio21 = reflCh2 / reflCh1;
        final double diffrefl1rt3 = reflCh1 - reflCh3;
        final double sumrefl2rt3 = reflCh2 + reflCh3;

        if (latitude > 62.0) {

            final boolean condBtCh4 = -15.0 < btCh4Celsius && btCh4Celsius < 1.35;
//            final boolean condElevation = elevation > 300.0;
            final boolean condElevation = true;        // todo: discuss
            final boolean cond1 = condBtCh4 && reflCh1 > 0.4 && 0.95 <= ratio21 &&
                    ratio21 < 1.15 && reflCh3 < 0.054 && condElevation;
            final boolean cond2 = condBtCh4 && reflCh1 > 0.4 && 0.8 <= ratio21 && ratio21 < 1.15 &&
                    reflCh3 < 0.054 && reflCh3 > 0.026 && condElevation;
            final boolean cond3 = condBtCh4 && reflCh1 > 0.4 && 0.8 <= ratio21 && ratio21 < 1.15 &&
                    reflCh3 < 0.054 && condElevation;
            final boolean cond4 = diffrefl1rt3 / sumrefl2rt3 > 1.1;
            final boolean snowIceCondition = (cond1 || cond2 || cond3 || cond4);
            isSnowIceTwo = isLand() && snowIceCondition;

        } else if (latitude < -62.0) {

            isSnowIceTwo = -27.0 < btCh4Celsius && btCh4Celsius < 1.35 && reflCh1 > 0.75 && ratio21 > 0.85 &&
                    ratio21 < 1.15 && reflCh3 < 0.03 && (reflCh1 - reflCh3) / (reflCh2 + reflCh3) > 1.05 && reflCh1 > 0.55;

        } else {
            isSnowIceTwo = isLand() && -15.0 < btCh4Celsius && btCh4Celsius < 1.35 && reflCh1 > 0.4 &&
                    ratio21 > 0.8 && ratio21 < 1.15 && reflCh3 < 0.054 && elevation > 1000.0;
        }
        return (isSnowIceOne && isSnowIceTwo);
    }

    @Override
    public boolean isCloudAmbiguous() {
        return isCloudSure();
        // todo: discuss if we need specific ambiguous flag
        // CB: cloud sure gives enough clouds, no ambiguous needed, 20141111
    }

    @Override
    public boolean isCloudSure() {
        if (isSnowIce()) {   // this check has priority
            return false;
        }

        final boolean isCloudAdditional = isCloudSnowIceFromDecisionTree();
        if (isCloudAdditional) {
            return true;
        }
        final boolean isCloudSureSchiller = isCloudSureSchiller();
        if (isCloudSureSchiller) {
            return true;
        }

        // Test (GK/JM 20151029): use 'old' snow/ice test as additional cloud criterion:
        boolean isCloudFromOldSnowIce = notIsCloudTgct() && ndsi > 0.8;
        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (!isCloudFromOldSnowIce && nnOutput != null) {
            // separation numbers from HS, 20140923
            isCloudFromOldSnowIce = nnOutput[0] > avhrracSchillerNNCloudSureSnowSeparationValue && nnOutput[0] <= 5.0;
        }
        if (isCloudFromOldSnowIce) {
            return true;
        }

        return isResidualCloud();
    }

    private boolean isResidualCloud() {
        if (isDesertArea()) {
            return reflCh3 < 0.18 && reflCh1 > 0.15 && btCh4 < 280 && (reflCh1 + reflCh2 + reflCh3) / 3 > 0.23 &&
                    btCh4 < 302 && reflCh3 < 0.38 && reflCh3 > 0.219;
        } else {
            return reflCh3 < 0.18 && reflCh1 > 0.15 && (reflCh1 + reflCh2 + reflCh3) / 3 > 0.2 &&
                    btCh4 < 302 && reflCh3 < 0.4;
        }
    }

    private boolean isCloudSureSchiller() {
        boolean isCloudSureSchiller;
        // for AVHRR, nnOutput has one element:
        // nnOutput[0] =
        // 0 < x < 2.15 : clear
        // 2.15 < x < 3.45 : noncl / semitransparent cloud --> cloud ambiguous
        // 3.45 < x < 4.45 : cloudy --> cloud sure
        // 4.45 < x : clear snow/ice
        if (nnOutput != null) {
            isCloudSureSchiller = nnOutput[0] >= avhrracSchillerNNCloudAmbiguousSureSeparationValue &&
                    nnOutput[0] < avhrracSchillerNNCloudSureSnowSeparationValue;   // separation numbers from report HS, 0141112 for NN Nr.2
        } else {
            isCloudSureSchiller = false;
        }
        return isCloudSureSchiller;
    }

    private boolean isCloudSnowIceFromDecisionTree() {
        // 1. RGCT test:
        final double ndvi = (reflCh2 - reflCh1) / (reflCh2 + reflCh1);
        final double rgctThresh = getRgctThreshold(ndvi);

        final boolean isCloudRGCT = isLand() && reflCh1 > rgctThresh;

        // 2. RRCT test:
        final double rrctThresh = 1.1;
        final boolean isCloudRRCT = isLand() && !isDesertArea() && reflCh2 / reflCh1 < rrctThresh;

        // 3. C3AT test:
        final double c3atThresh = 0.15;
        final boolean isCloudC3AT = isLand() && reflCh3 / reflCh1 < 1.1 && reflCh3 > c3atThresh;

        // 4. TGCT test
        final boolean isCloudTGCT = btCh4 < AvhrrConstants.TGCT_THRESH;
        // 5. FMFT test
        final double fmftThresh = getFmftThreshold();
        final boolean isCloudFMFT = (btCh4 - btCh5) > fmftThresh;

        // 6. C3A test:
        final double c3aThresh = 1.1;
        final boolean isCloudC3A = isLand() && reflCh3 / reflCh1 < c3aThresh;

        boolean isCloudAdditional = false;
        if (isCloudRGCT && isCloudFMFT && isCloudC3A) {
            isCloudAdditional = true;
        }
        if (isDesertArea() && (isCloudFMFT || (Math.abs(latitude) < AvhrrConstants.LAT_MAX_THRESH && isCloudTGCT)) ||
                (!isDesertArea() && isCloudRRCT && isCloudFMFT) ||
                (!isDesertArea() && isCloudRRCT && (Math.abs(latitude) < AvhrrConstants.LAT_MAX_THRESH && isCloudTGCT)) ||
                (!isDesertArea() && isCloudRRCT && isCloudC3AT)) {
            isCloudAdditional = true;
        }
        return isCloudAdditional;
    }

    private boolean notIsCloudTgct() {
        return btCh4 >= AvhrrConstants.TGCT_THRESH;
    }

    private double getFmftThreshold() {
        int fmftThresholdIndex = (int) (btCh4 - 200.0);
        fmftThresholdIndex = Math.max(0, fmftThresholdIndex);
        fmftThresholdIndex = Math.min(120, fmftThresholdIndex);
        return AvhrrConstants.fmftTestThresholds[fmftThresholdIndex];
    }

    private boolean isDesertArea() {
        return isLand() &&
                ((latitude >= 10.0 && latitude < 35.0 && longitude >= -20.0 && longitude < 30.0) ||
                (latitude >= 5.0 && latitude < 50.0 && longitude >= 30.0 && longitude < 60.0) ||
                (latitude >= 25.0 && latitude < 50.0 && longitude >= 60.0 && longitude < 110.0) ||
                (latitude >= -31.0 && latitude < 19.0 && longitude >= 121.0 && longitude < 141.0) ||
                (latitude >= 35.0 && latitude < 50.0 && longitude >= 110.0 && longitude < 127.0));
    }

    private double getRgctThreshold(double ndvi) {
        double rgctThresh = Double.MAX_VALUE;
        if (ndvi < -0.05) {
            rgctThresh = 0.8;
        } else if (ndvi >= -0.05 && ndvi < 0.0) {
            rgctThresh = 0.6;
        } else if (ndvi >= 0.0 && ndvi < 0.05) {
            rgctThresh = 0.5;
        } else if (ndvi >= 0.05 && ndvi < 0.1) {
            rgctThresh = 0.4;
        } else if (ndvi >= 0.1 && ndvi < 0.15) {
            rgctThresh = 0.35;
        } else if (ndvi >= 0.15 && ndvi < 0.25) {
            rgctThresh = 0.3;
        } else if (ndvi >= 0.25) {
            rgctThresh = 0.25;
        }
        return rgctThresh;
    }

    @Override
    public boolean isCloudBuffer() {
        // is applied in post processing!
        return false;
    }

    @Override
    public boolean isCloudShadow() {
        // is applied in post processing!
        return false;
    }

    @Override
    public boolean isCoastline() {
        // NOTE that this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502). If so, get a coastline in post processing approach.
        return waterFraction < 100 && waterFraction > 0;
    }

    @Override
    public boolean isLand() {
        return waterFraction == 0;
    }

    @Override
    public boolean isGlintRisk() {
        return false;
    }


    void setReflCh1(double reflCh1) {
        this.reflCh1 = reflCh1;
    }

    void setReflCh2(double reflCh2) {
        this.reflCh2 = reflCh2;
    }

    void setReflCh3(double reflCh3) {
        this.reflCh3 = reflCh3;
    }

    void setBtCh4(double btCh4) {
        this.btCh4 = btCh4;
    }

    void setBtCh5(double btCh5) {
        this.btCh5 = btCh5;
    }

    void setRadiance(double[] rad) {
        this.radiance = rad;
    }

    void setNdsi(double ndsi) {
        this.ndsi = ndsi;
    }

    void setWaterFraction(float waterFraction) {
        this.waterFraction = waterFraction;
    }

    void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

    void setAmbiguousSureSeparationValue(double avhrracSchillerNNCloudAmbiguousSureSeparationValue) {
        this.avhrracSchillerNNCloudAmbiguousSureSeparationValue = avhrracSchillerNNCloudAmbiguousSureSeparationValue;
    }

    void setSureSnowSeparationValue(double avhrracSchillerNNCloudSureSnowSeparationValue) {
        this.avhrracSchillerNNCloudSureSnowSeparationValue = avhrracSchillerNNCloudSureSnowSeparationValue;
    }

    void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    void setElevation(double elevation) {
        this.elevation = elevation;
    }
}
