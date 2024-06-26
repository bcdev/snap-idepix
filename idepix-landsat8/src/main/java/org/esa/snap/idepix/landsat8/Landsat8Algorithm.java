package org.esa.snap.idepix.landsat8;

/**
 * IDEPIX instrument-specific pixel identification algorithm for Landsat 8
 *
 * @author olafd
 */
public class Landsat8Algorithm implements Landsat8PixelProperties {

    private static final int NN_CATEGORY_CLEAR_SKY = 1;
    private static final int NN_CATEGORY_NON_CLEAR_SKY = 3;
    private static final int NN_CATEGORY_CLOUD = 4;
    private static final int NN_CATEGORY_CLEAR_SKY_SNOW_ICE = 5;

    private float[] l8SpectralBandData;

    private int brightnessBandLand;
    private float brightnessThreshLand;
    private int brightnessBand1Water;
    private float brightnessWeightBand1Water;
    private int brightnessBand2Water;
    private float brightnessWeightBand2Water;
    private float brightnessThreshWater;
    private int whitenessBand1Land;
    private int whitenessBand2Land;
    private float whitenessThreshLand;
    private int whitenessBand1Water;
    private int whitenessBand2Water;
    private float whitenessThreshWater;

    private boolean isLand;
    private boolean isInvalid;
    private boolean applyShimezCloudTest;
    private float shimezDiffThresh;
    private float shimezMeanThresh;
    private float hotThresh;
    private double clostThresh;
    private boolean applyClostCloudTest;
    private float clostValue;
    private float otsuValue;
    private boolean applyOtsuCloudTest;
    private double[] nnResult;

    private double nnCloudAmbiguousLowerBoundaryValue;
    private double nnCloudAmbiguousSureSeparationValue;
    private double nnCloudSureSnowSeparationValue;

    @Override
    public boolean isInvalid() {
        return isInvalid;
    }

    @Override
    public boolean isCloud() {
        return isCloudSure();
    }

    @Override
    public boolean isCloudAmbiguous() {
        // todo: discuss logic, then apply separation values from new NNs, 20151119:
        // for the moment, just return the 'non clear sky' from the new NN, no other tests
        return !isInvalid() && !isCloudSure() && getNnClassification()[0] == Landsat8Algorithm.NN_CATEGORY_NON_CLEAR_SKY;
    }

    @Override
    public boolean isCloudSure() {
        // todo: discuss logic of cloudSure
        // todo: also discuss if Michael's tests shall be fully replaced by Schiller NN when available
        final boolean isCloudShimez = applyShimezCloudTest && isCloudShimez();
//        final boolean isCloudHot = applyHotCloudTest && isCloudHot();
        final boolean isCloudClost = applyClostCloudTest && isCloudClost();
        final boolean isCloudOtsu = applyOtsuCloudTest && isCloudOtsu();
//        return !isInvalid() && isBright() && isWhite() && (isCloudShimez || isCloudHot);
        boolean nnCloud = nnResult[0] == NN_CATEGORY_CLOUD;

        // current logic: cloudSure if Shimez, Clost, Otsu or NN over water
        return !isInvalid() && (isCloudShimez || isCloudClost || isCloudOtsu || (nnCloud && !isLand()));
    }

    @Override
    public boolean isCloudBuffer() {
        return false;   // in post-processing
    }

    @Override
    public boolean isCloudShadow() {
        return false;  // in post-processing when defined
    }

    @Override
    public boolean isSnowIce() {
        return nnResult[0] == NN_CATEGORY_CLEAR_SKY_SNOW_ICE;
    }

    @Override
    public boolean isGlintRisk() {
        return false;  // no way to compute?!
    }

    @Override
    public boolean isCoastline() {
        return false;
    }

    @Override
    public boolean isLand() {
        return isLand;
    }

    @Override
    public boolean isBright() {
        if (isLand()) {
            final Integer landBandIndex = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBandLand);
            return !isInvalid() && (l8SpectralBandData[landBandIndex] > brightnessThreshLand);
        } else {
            final Integer waterBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBand1Water);
            final Integer waterBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(brightnessBand2Water);
            final float brightnessWaterValue = brightnessWeightBand1Water * l8SpectralBandData[waterBand1Index] +
                    brightnessWeightBand2Water * l8SpectralBandData[waterBand2Index];
            return !isInvalid() && (brightnessWaterValue > brightnessThreshWater);
        }
    }

    @Override
    public boolean isWhite() {
        if (isLand()) {
            final Integer whitenessBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand1Land);
            final Integer whitenessBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand2Land);
            final float whiteness = l8SpectralBandData[whitenessBand1Index] / l8SpectralBandData[whitenessBand2Index];
            return !isInvalid() && (whiteness < whitenessThreshLand);
        } else {
            final Integer whitenessBand1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand1Water);
            final Integer whitenessBand2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(whitenessBand2Water);
            final float whiteness = l8SpectralBandData[whitenessBand1Index] / l8SpectralBandData[whitenessBand2Index];
            return !isInvalid() && (whiteness < whitenessThreshWater);
        }
    }


    /**
     * Setter for neural net result
     *
     * @param nnResult - the neural net result
     */
    void setNnResult(double[] nnResult) {
        this.nnResult = nnResult;
    }

    /**
     * Getter for neural net result
     *
     * @return - the neural net result
     */
    double[] getNnResult() {
        return nnResult;
    }

    /**
     * Getter for pixel classification from neural net
     *
     * @return - the classification result (int array of length of neural net result)
     */
    private int[] getNnClassification() {
        return classifyNNResult(nnResult);
        // todo: discuss logic, then apply separation values from new NNs, 20151119
    }

    /**
     * Performs SHIMEZ cloud test
     *
     * @return boolean
     */
    boolean isCloudShimez() {
        // make sure we have reflectances here!!

        // this is the latest correction from MPa , 20150330:
//        abs(blue/red-1)<A &&
//                abs(blue/green-1)<A &&
//                abs(red/green-1)<A &&
//                (red+green+blue)/3 > 0.35
//                  A = 0.1 over the day
//                  A = 0.2 if twilight

        final double blueGreenRatio = l8SpectralBandData[1] / l8SpectralBandData[2];
        final double redGreenRatio = l8SpectralBandData[3] / l8SpectralBandData[2];
        final double mean = (l8SpectralBandData[1] + l8SpectralBandData[2] + l8SpectralBandData[3]) / 3.0;

        return Math.abs(blueGreenRatio - 1.0) < shimezDiffThresh &&
                Math.abs(redGreenRatio - 1.0) < shimezDiffThresh &&
                mean > shimezMeanThresh;
    }

    /**
     * Performs HOT cloud test
     *
     * @return boolean
     */
    boolean isCloudHot() {
        final double hot = l8SpectralBandData[1] - 0.5 * l8SpectralBandData[3];
        return hot > hotThresh;
    }

    /**
     * Performs CLOST cloud test
     *
     * @return boolean
     */
    boolean isCloudClost() {
        if (applyOtsuCloudTest) {
            return clostValue > clostThresh;
        } else {
            final double clost = l8SpectralBandData[0] * l8SpectralBandData[1] * l8SpectralBandData[7] * l8SpectralBandData[8];
            return clost > clostThresh;
        }
    }

    /**
     * Performs OTSU cloud test
     *
     * @return boolean
     */
    boolean isCloudOtsu() {
        // todo
        return otsuValue > 128;
    }


    // currently not used (20160303)
//    public boolean isDarkGlintTest1() {
//        Integer darkGlintTest1Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(darkGlintThresholdTest1Wvl);
//        return l8SpectralBandData[darkGlintTest1Index] > darkGlintThresholdTest1;
//    }
//
//    public boolean isDarkGlintTest2() {
//        Integer darkGlintTest2Index = Landsat8Constants.LANDSAT8_SPECTRAL_WAVELENGTH_MAP.get(darkGlintThresholdTest2Wvl);
//        return l8SpectralBandData[darkGlintTest2Index] > darkGlintThresholdTest2;
//
//    }

    ///////////////// further setter methods ////////////////////////////////////////

    void setL8SpectralBandData(float[] l8SpectralBandData) {
        this.l8SpectralBandData = l8SpectralBandData;
    }

    void setIsLand(boolean isLand) {
        this.isLand = isLand;
    }

    void setInvalid(boolean isInvalid) {
        this.isInvalid = isInvalid;
    }

    void setBrightnessBandLand(int brightnessBandLand) {
        this.brightnessBandLand = brightnessBandLand;
    }

    void setBrightnessThreshLand(float brightnessThreshLand) {
        this.brightnessThreshLand = brightnessThreshLand;
    }

    void setBrightnessBand1Water(int brightnessBand1Water) {
        this.brightnessBand1Water = brightnessBand1Water;
    }

    void setBrightnessWeightBand1Water(float brightnessWeightBand1Water) {
        this.brightnessWeightBand1Water = brightnessWeightBand1Water;
    }

    void setBrightnessBand2Water(int brightnessBand2Water) {
        this.brightnessBand2Water = brightnessBand2Water;
    }

    void setBrightnessWeightBand2Water(float brightnessWeightBand2Water) {
        this.brightnessWeightBand2Water = brightnessWeightBand2Water;
    }

    void setBrightnessThreshWater(float brightnessThreshWater) {
        this.brightnessThreshWater = brightnessThreshWater;
    }

    void setWhitenessBand1Land(int whitenessBand1Land) {
        this.whitenessBand1Land = whitenessBand1Land;
    }

    void setWhitenessBand2Land(int whitenessBand2Land) {
        this.whitenessBand2Land = whitenessBand2Land;
    }

    void setWhitenessThreshLand(float whitenessThreshLand) {
        this.whitenessThreshLand = whitenessThreshLand;
    }

    void setWhitenessThreshWater(float whitenessThreshWater) {
        this.whitenessThreshWater = whitenessThreshWater;
    }

    void setWhitenessBand1Water(int whitenessBand1Water) {
        this.whitenessBand1Water = whitenessBand1Water;
    }

    void setWhitenessBand2Water(int whitenessBand2Water) {
        this.whitenessBand2Water = whitenessBand2Water;
    }


    void setApplyShimezCloudTest(boolean applyShimezCloudTest) {
        this.applyShimezCloudTest = applyShimezCloudTest;
    }

    void setShimezDiffThresh(float shimezDiffThresh) {
        this.shimezDiffThresh = shimezDiffThresh;
    }

    void setShimezMeanThresh(float shimezMeanThresh) {
        this.shimezMeanThresh = shimezMeanThresh;
    }

    void setHotThresh(float hotThresh) {
        this.hotThresh = hotThresh;
    }

    void setClostThresh(double clostThresh) {
        this.clostThresh = clostThresh;
    }

    void setApplyClostCloudTest(boolean applyClostCloudTest) {
        this.applyClostCloudTest = applyClostCloudTest;
    }

    void setClostValue(float clostValue) {
        this.clostValue = clostValue;
    }

    void setOtsuValue(float otsuValue) {
        this.otsuValue = otsuValue;
    }

    void setApplyOtsuCloudTest(boolean applyOtsuCloudTest) {
        this.applyOtsuCloudTest = applyOtsuCloudTest;
    }

    private int[] classifyNNResult(double[] netResult) {
        double netResultValue = netResult[0];
        int[] nnClassification = new int[netResult.length];
        if (netResultValue < nnCloudAmbiguousLowerBoundaryValue) {
            nnClassification[0] = Landsat8Algorithm.NN_CATEGORY_CLEAR_SKY;
        } else if (netResultValue >= nnCloudAmbiguousLowerBoundaryValue &&
                netResultValue < nnCloudAmbiguousSureSeparationValue) {
            nnClassification[0] = Landsat8Algorithm.NN_CATEGORY_NON_CLEAR_SKY;
        } else if (netResultValue >= nnCloudAmbiguousSureSeparationValue &&
                netResultValue < nnCloudSureSnowSeparationValue) {
            nnClassification[0] = Landsat8Algorithm.NN_CATEGORY_CLOUD;
        } else {
            nnClassification[0] = Landsat8Algorithm.NN_CATEGORY_CLEAR_SKY_SNOW_ICE;
        }
        return nnClassification;
    }

    void setNnCloudAmbiguousLowerBoundaryValue(double nnCloudAmbiguousLowerBoundaryValue) {
        this.nnCloudAmbiguousLowerBoundaryValue = nnCloudAmbiguousLowerBoundaryValue;
    }

    void setNnCloudAmbiguousSureSeparationValue(double nnCloudAmbiguousSureSeparationValue) {
        this.nnCloudAmbiguousSureSeparationValue = nnCloudAmbiguousSureSeparationValue;
    }

    void setNnCloudSureSnowSeparationValue(double nnCloudSureSnowSeparationValue) {
        this.nnCloudSureSnowSeparationValue = nnCloudSureSnowSeparationValue;
    }
}
