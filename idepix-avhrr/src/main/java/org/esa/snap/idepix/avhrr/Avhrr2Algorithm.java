package org.esa.snap.idepix.avhrr;

/**
 * IDEPIX instrument-specific pixel identification algorithm
 *
 * @author olafd
 */
public class Avhrr2Algorithm extends AvhrrAlgorithm {

    @Override
    public boolean isSnowIce() {

        // isSnowIce
        boolean isSnowIceOne = !isCloudTgct() && ndsi > -0.2;

        // forget all the old stuff, completely new test now (GK/JM, 20151028):
        boolean isSnowIceTwo;
        final double btCh3Celsius = btCh3 - 273.15;
        final double btCh4Celsius = btCh4 - 273.15;
        final double btCh5Celsius = btCh5 - 273.15;
        final double ratio21 = reflCh2 / reflCh1;
        final double diffbt53 = btCh5Celsius - btCh3Celsius;
        final double ratiobt53 = btCh5Celsius/btCh3Celsius;
        final double diffrefl1rt3 = reflCh1 - reflCh3;
        final double sumrefl2rt3 = reflCh2 + reflCh3;

        if (latitude > 62.0) {

            final boolean condBtCh4 = -15.0 < btCh4Celsius && btCh4Celsius < 1.35;
            final boolean condElevation = elevation > 300.0;

            final boolean cond1 = condBtCh4 && reflCh1 > 0.4 && 0.95 <= ratio21 && ratio21 < 1.15
                    && reflCh3 < 0.054 && condElevation && diffbt53 > -14.0 && ratiobt53 > 1.7;
            final boolean cond2 = condBtCh4 && reflCh1 > 0.4 && 0.8 <= ratio21 && ratio21 < 1.15
                    && reflCh3 < 0.054 && reflCh3 > 0.026 && condElevation && diffbt53 > -13.0 && ratiobt53 <= -0.98;
            final boolean cond3 = condBtCh4 && reflCh1 > 0.4 && 0.8 <= ratio21 && ratio21 < 1.15
                    && reflCh3 < 0.054 && condElevation && diffbt53 > -11.0 && ratiobt53 <= 1.7 && ratiobt53 > -0.98;
            final boolean cond4 = diffrefl1rt3/sumrefl2rt3 > 1.1 && diffbt53 > -8.0;

            final boolean snowIceCondition = (cond1 || cond2 ||cond3 || cond4);

            isSnowIceTwo = isLand() && snowIceCondition;

        } else if (latitude < -62.0) {

            isSnowIceTwo = -27.0 < btCh4Celsius && btCh4Celsius < 1.35 && reflCh1 > 0.75 &&
                    ratio21 > 0.85 && ratio21 < 1.15 && reflCh3 < 0.03 &&
                    diffbt53 > -13.0 || (reflCh1-reflCh3)/(reflCh2+reflCh3) > 1.05 && reflCh1 > 0.55;

        } else {

            isSnowIceTwo = isLand() && -15.0 < btCh4Celsius && btCh4Celsius < 1.35 && reflCh1 > 0.4 &&
                    ratio21 > 0.8 && ratio21 < 1.15 && reflCh3 < 0.054 && diffbt53 > -14.0 && elevation > 1000.0;
        }

        return isSnowIceOne && isSnowIceTwo;
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
        boolean isCloudFromOldSnowIce;
        isCloudFromOldSnowIce = (isCloudTgct() && ndsi < 0.2);
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

//        return isCloudSureSchiller || isCloudAdditional;
//        return isCloudSureSchiller || isCloudAdditional || isCloudFromOldSnowIce || isResidualCloud;
    }

    private boolean isResidualCloud() {

        if (isDesertArea()) {
            return (reflCh3 < 0.18 && reflCh1>0.15 && (btCh4 / btCh3) < 0.927 && btCh4<280)
                    || ((reflCh1 + reflCh2 + reflCh3) / 3 > 0.23 && btCh4 < 302 && (btCh4 / btCh3) < 0.95 && reflCh3 < 0.38 && reflCh3 > 0.219);
        } else {
            return (reflCh3 < 0.18 && reflCh1>0.15 && (btCh4 / btCh3) < 0.927)
                    || ((reflCh1 + reflCh2 + reflCh3) / 3 > 0.2 && btCh4 < 302 && (btCh4 / btCh3) < 0.95 && reflCh3 < 0.4);
        }
    }

    private boolean isCloudSnowIceFromDecisionTree() {
        // first apply additional tests (GK, 20150313):

        // 1. RGCT test:
        final double ndvi = (reflCh2 - reflCh1) / (reflCh2 + reflCh1);
        final double rgctThresh = getRgctThreshold(ndvi);
//        final boolean isCloudRGCT = isLand() && reflCh1/100.0 > rgctThresh;
        final boolean isCloudRGCT = isLand() && reflCh1 > rgctThresh;  // reflCh1 should not be divided by 100?!

        // 2. RRCT test:
        final double rrctThresh = 1.1;
        final boolean isCloudRRCT = isLand() && !isDesertArea() && reflCh2 / reflCh1 < rrctThresh;

        // 3. C3AT test:
        final double c3atThresh = 0.06;
        final boolean isCloudC3AT = isLand() && !isDesertArea() && rho3b > c3atThresh;

        // 4. TGCT test
        final boolean isCloudTGCT = btCh4 < AvhrrConstants.TGCT_THRESH;

        // 5. FMFT test
        final double fmftThresh = getFmftThreshold();
        final boolean isCloudFMFT = (btCh4 - btCh5) > fmftThresh;

        // 6. TMFT test
        final double bt34 = btCh3 - btCh4;
        final double tmftMinThresh = getTmftMinThreshold(bt34);
        final double tmftMaxThresh = getTmftMaxThreshold(bt34);
        final boolean isClearTMFT = bt34 > tmftMinThresh && bt34 < tmftMaxThresh;

        // 7. Emissivity test
        final boolean isCloudEmissivity = emissivity3b > AvhrrConstants.EMISSIVITY_THRESH;

        // now use combinations:
        //
        // if (RGCT AND FMFT) then cloud
        // if [ NOT RGCT BUT desert AND (FMFT OR (TGCT AND lat<latMaxThresh)) ] then cloud
        // if [ NOT RGCT BUT (RRCT AND FMFT) ] then cloud
        // if [ NOT RGCT BUT (RRCT AND C3AT) ] then cloud
        // if TMFT: clear pixels must not become cloud in any case!
        // cloud ONLY if cloudEmissivity
        //
        // apply Schiller AFTER these test for pixels not yet cloud!!
        boolean isCloudAdditional = false;
        if (!isClearTMFT && isCloudEmissivity) {
            // first branch of condition tree:
            if (isCloudRGCT && isCloudFMFT) {
                isCloudAdditional = true;
            }
            // second branch of condition tree:
            if (isDesertArea() && (isCloudFMFT || (Math.abs(latitude) < AvhrrConstants.LAT_MAX_THRESH && isCloudTGCT)) ||
                    (!isDesertArea() && isCloudRRCT && isCloudFMFT) ||
                    (!isDesertArea() && isCloudRRCT && (Math.abs(latitude) < AvhrrConstants.LAT_MAX_THRESH && isCloudTGCT)) ||
                    (!isDesertArea() && isCloudRRCT && isCloudC3AT)) {
                isCloudAdditional = true;
            }
        }
        return isCloudAdditional;
    }

    private double getTmftMinThreshold(double bt34) {
        int tmftMinThresholdIndexRow = (int) ((btCh4 - 190.0) / 10.0);
        tmftMinThresholdIndexRow = Math.max(0, tmftMinThresholdIndexRow);
        tmftMinThresholdIndexRow = Math.min(13, tmftMinThresholdIndexRow);
        int tmftMinThresholdIndexColumn = (int) ((bt34 - 7.5) / 15.0) + 1;
        tmftMinThresholdIndexColumn = Math.max(0, tmftMinThresholdIndexColumn);
        tmftMinThresholdIndexColumn = Math.min(3, tmftMinThresholdIndexColumn);

        final int tmftMinThresholdIndex = 4 * tmftMinThresholdIndexRow + tmftMinThresholdIndexColumn;

        return AvhrrConstants.tmftTestMinThresholds[tmftMinThresholdIndex];
    }

    private double getTmftMaxThreshold(double bt34) {
        int tmftMaxThresholdIndexRow = (int) ((btCh4 - 190.0) / 10.0);
        tmftMaxThresholdIndexRow = Math.max(0, tmftMaxThresholdIndexRow);
        tmftMaxThresholdIndexRow = Math.min(13, tmftMaxThresholdIndexRow);
        int tmftMaxThresholdIndexColumn = (int) ((bt34 - 7.5) / 15.0) + 1;
        tmftMaxThresholdIndexColumn = Math.max(0, tmftMaxThresholdIndexColumn);
        tmftMaxThresholdIndexColumn = Math.min(3, tmftMaxThresholdIndexColumn);

        final int tmftMaxThresholdIndex = 4 * tmftMaxThresholdIndexRow + tmftMaxThresholdIndexColumn;

        return AvhrrConstants.tmftTestMaxThresholds[tmftMaxThresholdIndex];
    }
}