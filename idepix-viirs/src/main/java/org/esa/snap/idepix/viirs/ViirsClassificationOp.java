package org.esa.snap.idepix.viirs;

import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.IdepixIO;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.pointop.*;

import java.io.IOException;
import java.io.InputStream;

/**
 * VIIRS pixel classification operator.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "Idepix.Viirs.Classification",
        version = "3.1",
        authors = "Olaf Danne, Marco Zuehlke",
        copyright = "(c) 2016 - 2024 by Brockmann Consult",
        description = "VIIRS pixel classification operator. Supports Suomi NPP, NOAA20, NOAA21 products.",
        internal = true)
public class ViirsClassificationOp extends PixelOperator {

    @Parameter(defaultValue = "false",
            label = " Debug bands",
            description = "Write further useful bands to target product.")
    private boolean outputDebug = false;

    @Parameter(defaultValue = "1", label = " Width of cloud buffer (# of pixels)")
    private int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    private int waterMaskResolution;


    @SourceProduct(alias = "refl", description = "VIIRS L1C reflectance product")
    private Product reflProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    private static final String VIIRS_NET_NAME = "6x5x4x3x2_204.8.net";
    private ThreadLocal<SchillerNeuralNetWrapper> viirsNeuralNet;

    private String[] viirsSpectralBandNames;


    @Override
    protected void prepareInputs() throws OperatorException {
        viirsSpectralBandNames = IdepixIO.getViirsSpectralBandNames(reflProduct.getName());
        readSchillerNet();
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        ViirsAlgorithm algorithm = createViirsAlgorithm(x, y, sourceSamples, targetSamples);
        setClassifFlag(targetSamples, algorithm);
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        if (viirsSpectralBandNames != null) {
            for (int i = 0; i < viirsSpectralBandNames.length; i++) {
                if (getSourceProduct().containsBand(viirsSpectralBandNames[i])) {
                    sampleConfigurer.defineSample(i, viirsSpectralBandNames[i], getSourceProduct());
                } else {
                    sampleConfigurer.defineSample(i, viirsSpectralBandNames[i].replace(".", "_"),
                            getSourceProduct());
                }
            }
        } else {
            // should never happen
            throw new OperatorException("Source product has no valid VIIRS spectral bands - please check.");
        }

        sampleConfigurer.defineSample(viirsSpectralBandNames.length+1,
                                      IdepixConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        // the only standard band:
        sampleConfigurer.defineSample(0, IdepixConstants.CLASSIF_BAND_NAME);

        // debug bands:
        if (outputDebug) {
            sampleConfigurer.defineSample(1, ViirsConstants.BRIGHTNESS_BAND_NAME);
            sampleConfigurer.defineSample(2, ViirsConstants.NDSI_BAND_NAME);
            sampleConfigurer.defineSample(3, IdepixConstants.NN_OUTPUT_BAND_NAME);
        } else {
            sampleConfigurer.defineSample(1, IdepixConstants.NN_OUTPUT_BAND_NAME);
        }
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = ViirsUtils.createViirsFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        productConfigurer.copyGeoCoding();
        ViirsUtils.setupViirsClassifBitmask(getTargetProduct());

        // debug bands:
        if (outputDebug) {
            Band brightnessValueBand = productConfigurer.addBand(ViirsConstants.BRIGHTNESS_BAND_NAME, ProductData.TYPE_FLOAT32);
            brightnessValueBand.setDescription("Brightness value (uses EV_250_Aggr1km_RefSB_1) ");
            brightnessValueBand.setUnit("dl");

            Band ndsiValueBand = productConfigurer.addBand(ViirsConstants.NDSI_BAND_NAME, ProductData.TYPE_FLOAT32);
            ndsiValueBand.setDescription("NDSI value (uses EV_250_Aggr1km_RefSB_1, EV_500_Aggr1km_RefSB_7)");
            ndsiValueBand.setUnit("dl");

        }
        Band nnValueBand = productConfigurer.addBand(IdepixConstants.NN_OUTPUT_BAND_NAME, ProductData.TYPE_FLOAT32);
        nnValueBand.setDescription("Schiller NN output value");
        nnValueBand.setUnit("dl");
    }

    private void readSchillerNet() {
        try (InputStream isV = getClass().getResourceAsStream(VIIRS_NET_NAME)) {
            viirsNeuralNet = SchillerNeuralNetWrapper.create(isV);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Neural Nets: " + e.getMessage());
        }
    }

    private void setClassifFlag(WritableSample[] targetSamples, ViirsAlgorithm algorithm) {
        targetSamples[0].set(IdepixConstants.IDEPIX_INVALID, algorithm.isInvalid());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD, algorithm.isCloud());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(IdepixConstants.IDEPIX_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(ViirsConstants.IDEPIX_MIXED_PIXEL, algorithm.isMixedPixel());
        targetSamples[0].set(IdepixConstants.IDEPIX_LAND, algorithm.isLand());
        targetSamples[0].set(IdepixConstants.IDEPIX_BRIGHT, algorithm.isBright());

        if (outputDebug) {
            targetSamples[1].set(algorithm.brightValue());
            targetSamples[2].set(algorithm.ndsiValue());
        }
    }

    private ViirsAlgorithm createViirsAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        final double[] reflectance = new double[viirsSpectralBandNames.length];
        double[] neuralNetOutput;

        float waterFraction = Float.NaN;

        ViirsAlgorithm viirsAlgorithm = new ViirsAlgorithm();
        for (int i = 0; i < viirsSpectralBandNames.length; i++) {
            reflectance[i] = sourceSamples[i].getFloat();
        }
        viirsAlgorithm.setRefl(reflectance);
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            waterFraction =
                    sourceSamples[viirsSpectralBandNames.length + 1].getFloat();
        }
        viirsAlgorithm.setWaterFraction(waterFraction);

        double[] viirsNeuralNetInput = viirsNeuralNet.get().getInputVector();
        for (int i = 0; i < viirsNeuralNetInput.length; i++) {
            viirsNeuralNetInput[i] = Math.sqrt(sourceSamples[i].getFloat());
        }

        neuralNetOutput = viirsNeuralNet.get().getNeuralNet().calc(viirsNeuralNetInput);

        viirsAlgorithm.setNnOutput(neuralNetOutput);

        final int targetOffset = outputDebug ? 3 : 1;
        targetSamples[targetOffset].set(neuralNetOutput[0]);

        return viirsAlgorithm;
    }

    private GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = reflProduct.getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ViirsClassificationOp.class);
        }
    }

}
