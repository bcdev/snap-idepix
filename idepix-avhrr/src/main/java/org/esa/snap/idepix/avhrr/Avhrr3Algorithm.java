package org.esa.snap.idepix.avhrr;

/**
 * IDEPIX instrument-specific pixel identification algorithm
 *
 * @author olafd
 */
public class Avhrr3Algorithm extends AvhrrAlgorithm {

    @Override
    public boolean isSnowIce() {


//        final double ndsi = (reflCh1 - reflCh3) / (reflCh3 + reflCh1);
        boolean isSnowIceOne = (!isCloudTgct() && ndsi > 0.5) && (reflCh1 > 0.45 && ndsi > 0.4);


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
        return (isSnowIceOne || isSnowIceTwo);
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
        boolean isCloudFromOldSnowIce = !isCloudTgct() && ndsi > 0.8 && isLand();
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

}
