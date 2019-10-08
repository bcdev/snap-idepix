package org.esa.snap.idepix.seawifs;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 18.10.2016
 * Time: 14:42
 *
 * @author olafd
 */
public class SeaWifsAlgorithm {

    // as long as we have no Schiller, CLOUD thresholds experimentally selected just from A2009125001500.L1B_LAC:
    private static final double THRESH_BRIGHT_CLOUD_AMBIGUOUS = 0.07;
    private static final double THRESH_BRIGHT_CLOUD_SURE = 0.15;

    private float waterFraction;
    private double[] refl;
    private double[] nnOutput;

    boolean isInvalid() {
        // todo: define if needed
        return false;
    }

    boolean isCoastline() {
        // NOTE that this does not work if we have a PixelGeocoding. In that case, waterFraction
        // is always 0 or 100!! (TS, OD, 20140502). If so, get a coastline in post processing approach.
        return waterFraction < 100 && waterFraction > 0;
    }

    boolean isLand() {
        return waterFraction == 0;
    }


    boolean isSnowIce() {
        // we don't have anything for SeaWiFS...
        return false;
    }

    boolean isCloud() {
        return isCloudAmbiguous() || isCloudSure();
    }

    boolean isCloudAmbiguous() {
        if (isLand() || isCloudSure()) {   // this check has priority
            return false;
        }

        // for SeaWiFS, nnOutput has one element:
        // nnOutput[0] =
        // 0 : cloudy
        // 1 : semitransparent cloud
        // 2 : clear water
        if (nnOutput != null) {
//            return nnOutput[0] >= 0.48 && nnOutput[0] < 1.47;    // separation numbers from report HS, 20140822
            return nnOutput[0] >= 0.48 && nnOutput[0] < 0.48;      // CB: cloud sure gives enough clouds, no ambiguous needed, 20141111
        } else {
            // fallback
            return (brightValue() > THRESH_BRIGHT_CLOUD_AMBIGUOUS);
        }
    }

    boolean isCloudSure() {
        if (isLand() || isSnowIce()) {   // this check has priority
            return false;
        }

        // for SeaWiFS, nnOutput has one element:
        // nnOutput[0] =
        // 0 : cloudy
        // 1 : semitransparent cloud
        // 2 : clear water
        if (nnOutput != null) {
            return nnOutput[0] >= 0.0 && nnOutput[0] < 0.48;   // separation numbers from report HS, 20140822
        } else {
            // fallback
            return (brightValue() > THRESH_BRIGHT_CLOUD_SURE);
        }
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
        // unmixing using MERIS bands 7, 9, 10, 12
        return false;
    }

    public boolean isGlintRisk() {
        // todo
        // depends on geometry, windspeed and rho_toa_865
        // MODIS: we have rho_toa_865, wind components are required!
        return false;
    }

    boolean isBright() {
        return false;   // todo
    }

    public float whiteValue(int numeratorIndex, int denominatorIndex) {
        // not used yet
        return 0.0f;
    }

    public float ndsiValue() {
        return 0;
    }

    void setWaterFraction(float waterFraction) {
        this.waterFraction = waterFraction;
    }

    void setRefl(double[] reflectance) {
        refl = reflectance;
    }

    void setNnOutput(double[] nnOutput) {
        this.nnOutput = nnOutput;
    }

    private float brightValue() {
        // use L_865
        return (float) refl[7];
    }

}
