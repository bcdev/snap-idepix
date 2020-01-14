package org.esa.snap.idepix.avhrr;

import org.esa.snap.idepix.core.util.IdepixIO;

/**
 * IDEPIX instrument-specific pixel identification algorithm for AVHRR-x: abstract superclass
 *
 * @author olafd
 */
public abstract class AvhrrAlgorithm implements AvhrrPixelProperties {

    double[] nnOutput;

    double avhrracSchillerNNCloudSureSnowSeparationValue;

    double reflCh1;
    double reflCh2;
    double reflCh3;
    double btCh3;
    double btCh4;
    double btCh5;

    double rho3b;
    double emissivity3b;
    double ndsi;

    double latitude;
    double elevation;

    private double longitude;
    private float waterFraction;
    private double[] radiance;
    private double avhrracSchillerNNCloudAmbiguousSureSeparationValue;

    @Override
    public boolean isInvalid() {
        return !IdepixIO.areAllReflectancesValid(radiance);
    }

    @Override
    public boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        return isCloudSure(); // todo: discuss if we need specific ambiguous flag
        // CB: cloud sure gives enough clouds, no ambiguous needed, 20141111
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

    @Override
    public abstract boolean isSnowIce();

    @Override
    public abstract boolean isCloudSure();


    boolean isCloudSureSchiller() {
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

    boolean isCloudTgct() {
        return btCh4 < AvhrrConstants.TGCT_THRESH;
    }

    double getFmftThreshold() {
        int fmftThresholdIndex = (int) (btCh4 - 200.0);
        fmftThresholdIndex = Math.max(0, fmftThresholdIndex);
        fmftThresholdIndex = Math.min(120, fmftThresholdIndex);
        return AvhrrConstants.fmftTestThresholds[fmftThresholdIndex];
    }

    boolean isDesertArea() {
        return isLand() &&
                (latitude >= 10.0 && latitude < 35.0 && longitude >= -20.0 && longitude < 30.0) ||
                (latitude >= 5.0 && latitude < 50.0 && longitude >= 30.0 && longitude < 60.0) ||
                (latitude >= 25.0 && latitude < 50.0 && longitude >= 60.0 && longitude < 110.0) ||
                (latitude >= -31.0 && latitude < 19.0 && longitude >= 121.0 && longitude < 141.0) ||
                (latitude >= 35.0 && latitude < 50.0 && longitude >= 110.0 && longitude < 127.0);
    }

    double getRgctThreshold(double ndvi) {
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

    void setReflCh1(double reflCh1) {
        this.reflCh1 = reflCh1;
    }

    void setReflCh2(double reflCh2) {
        this.reflCh2 = reflCh2;
    }

    void setReflCh3(double reflCh3) {
        this.reflCh3 = reflCh3;
    }

    void setBtCh3(double btCh3) {
        this.btCh3 = btCh3;
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