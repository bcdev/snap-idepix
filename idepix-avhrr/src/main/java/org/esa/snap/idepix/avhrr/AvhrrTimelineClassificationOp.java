package org.esa.snap.idepix.avhrr;

import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.core.dataop.dem.ElevationModelDescriptor;
import org.esa.snap.core.dataop.dem.ElevationModelRegistry;
import org.esa.snap.core.dataop.resamp.Resampling;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.gpf.pointop.*;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.esa.snap.idepix.core.IdepixConstants;
import org.esa.snap.idepix.core.util.SchillerNeuralNetWrapper;

import java.io.IOException;
import java.io.InputStream;

import static java.lang.Math.*;
import static java.lang.StrictMath.toRadians;

/**
 * Basic operator for AVHRR/2 Timeline (NOAA-7) pixel classification
 *
 * @author Olaf Danne
 * @author Grit Kirches
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Idepix.Avhrr.Timeline.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne, Grit Kirches",
        copyright = "(c) 2019 by Brockmann Consult",
        description = "Basic operator for pixel classification from AVHRR/2 Timeline data.")
public class AvhrrTimelineClassificationOp extends AbstractAvhrrClassificationOp {

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    private ElevationModel getasseElevationModel;

    @Override
    public void prepareInputs() throws OperatorException {
        setNoaaId();
        readSchillerNets();
        createTargetProduct();

        try {
            rad2BTTable = AvhrrAuxdata.getInstance().createRad2BTTable(noaaId);
        } catch (IOException e) {
            throw new OperatorException("Failed to get VZA from auxdata - cannot proceed: ", e);
        }

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor =
                ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.canBeDownloaded()) {
            throw new OperatorException("DEM cannot be downloaded: " + demName + ".");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);
    }

    void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(AVHRRAC_NET_NAME)) {
            avhrrNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }


    @Override
    void runAvhrrAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAlgorithm avhrrAlgorithm = new AvhrrAlgorithm();

        double sza = sourceSamples[AvhrrConstants.SRC_TIMELINE_SZA].getDouble();
        double vza = sourceSamples[AvhrrConstants.SRC_TIMELINE_VZA].getDouble();
        double saa = sourceSamples[AvhrrConstants.SRC_TIMELINE_SAA].getDouble();
        double vaa = sourceSamples[AvhrrConstants.SRC_TIMELINE_VAA].getDouble();

        double vaa_r = toRadians(vaa);
        double saa_r = toRadians(saa);

        final double relAzi = acos(cos(saa_r) * cos(vaa_r) + sin(saa_r) * sin(vaa_r));
        final double altitude = computeGetasseAltitude(x, y);

        final double albedo1 = sourceSamples[AvhrrConstants.SRC_TIMELINE_ALBEDO_1].getDouble();
        final double albedo2 = sourceSamples[AvhrrConstants.SRC_TIMELINE_ALBEDO_2].getDouble();
        final double bt3 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_3b].getDouble();    // brightness temp !!
        final double bt4 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_4].getDouble();
        final double bt5 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_5].getDouble();

        // GK, 20150325: convert albedo1, 2 to 'normalized' albedo:
        // norm_albedo_i = albedo_i / (d^2_sun * cos(theta_sun))    , effect is a few % only
        final double d = getDistanceCorr() * Math.cos(sza * MathUtils.DTOR);
        final double albedo1Norm = albedo1 / d;
        final double albedo2Norm = albedo2 / d;

        double[] avhrrRadiance = new double[AvhrrConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length];

        int targetSamplesIndex;
        if (albedo1 >= 0.0 && albedo2 >= 0.0 && !AvhrrAcUtils.anglesInvalid(sza, vza, saa, vaa)) {

            float waterFraction = Float.NaN;
            // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
            if (getGeoPos(x, y).lat > -58f) {
                waterFraction = sourceSamples[AvhrrConstants.SRC_TIMELINE_WATERFRACTION].getFloat();
            }

//            if (x == 260 && y == 575) {
//                System.out.println("x = " + x);
//            }

            avhrrAlgorithm.setLatitude(getGeoPos(x, y).lat);
            avhrrAlgorithm.setLongitude(getGeoPos(x, y).lon);
            avhrrAlgorithm.setSza(sza);

            avhrrRadiance[0] = convertBetweenAlbedoAndRadiance(albedo1, sza, ALBEDO_TO_RADIANCE, 0);
            avhrrRadiance[1] = convertBetweenAlbedoAndRadiance(albedo2, sza, ALBEDO_TO_RADIANCE, 1);
            avhrrRadiance[2] = AvhrrAcUtils.convertBtToRadiance(noaaId, rad2BTTable, bt3, 3, waterFraction);
            avhrrRadiance[3] = AvhrrAcUtils.convertBtToRadiance(noaaId, rad2BTTable, bt4, 4, waterFraction);
            avhrrRadiance[4] = AvhrrAcUtils.convertBtToRadiance(noaaId, rad2BTTable, bt5, 5, waterFraction);

            SchillerNeuralNetWrapper nnWrapper = avhrrNeuralNet.get();
            double[] inputVector = nnWrapper.getInputVector();
            inputVector[0] = sza;
            inputVector[1] = vza;
            inputVector[2] = relAzi;
            inputVector[3] = Math.sqrt(avhrrRadiance[0]);
            inputVector[4] = Math.sqrt(avhrrRadiance[1]);
            inputVector[5] = Math.sqrt(avhrrRadiance[3]);
            inputVector[6] = Math.sqrt(avhrrRadiance[4]);
            avhrrAlgorithm.setRadiance(avhrrRadiance);
            avhrrAlgorithm.setWaterFraction(waterFraction);

            double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

            avhrrAlgorithm.setNnOutput(nnOutput);
            avhrrAlgorithm.setAmbiguousLowerBoundaryValue(avhrrNNCloudAmbiguousLowerBoundaryValue);
            avhrrAlgorithm.setAmbiguousSureSeparationValue(avhrrNNCloudAmbiguousSureSeparationValue);
            avhrrAlgorithm.setSureSnowSeparationValue(avhrrNNCloudSureSnowSeparationValue);

            avhrrAlgorithm.setReflCh1(albedo1Norm / 100.0); // on [0,1]        --> put here albedo_norm now!!
            avhrrAlgorithm.setReflCh2(albedo2Norm / 100.0); // on [0,1]

            avhrrAlgorithm.setBtCh3(bt3);
            avhrrAlgorithm.setBtCh4(bt4);
            avhrrAlgorithm.setBtCh5(bt5);
            avhrrAlgorithm.setElevation(altitude);

            final double albedo3 = calculateReflectancePartChannel3b(avhrrRadiance[2], bt4, bt5, sza);
            avhrrAlgorithm.setReflCh3(albedo3); // on [0,1]

            setClassifFlag(targetSamples, avhrrAlgorithm);
        } else {
            targetSamplesIndex = 0;
            targetSamples[targetSamplesIndex].set(IdepixConstants.IDEPIX_INVALID, true);
        }
    }

    private double computeGetasseAltitude(float x, float y)  {
        final PixelPos pixelPos = new PixelPos(x + 0.5f, y + 0.5f);
        GeoPos geoPos = sourceProduct.getSceneGeoCoding().getGeoPos(pixelPos, null);
        double altitude;
        try {
            altitude = getasseElevationModel.getElevation(geoPos);
        } catch (Exception e) {
            // todo
            e.printStackTrace();
            altitude = 0.0;
        }
        return altitude;
    }

    @Override
    void setClassifFlag(WritableSample[] targetSamples, AvhrrAlgorithm algorithm) {
        targetSamples[0].set(IdepixConstants.IDEPIX_INVALID, algorithm.isInvalid());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD, algorithm.isCloud());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[0].set(IdepixConstants.IDEPIX_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[0].set(IdepixConstants.IDEPIX_SNOW_ICE, algorithm.isSnowIce());
        targetSamples[0].set(IdepixConstants.IDEPIX_COASTLINE, algorithm.isCoastline());
        targetSamples[0].set(IdepixConstants.IDEPIX_LAND, algorithm.isLand());
    }

    @Override
    String getProductDatestring() {
        // provides datestring as DDMMYY !!!
        // e.g. from C3S-L2A-FCDR-AVHRR_NOAA-19820502043955-fV0102 it is 020582
        final int productNameStartIndex = sourceProduct.getName().indexOf("NOAA-");
        final String yy = sourceProduct.getName().substring(productNameStartIndex + 6, productNameStartIndex + 8);
        final String mm = sourceProduct.getName().substring(productNameStartIndex + 8, productNameStartIndex + 10);
        final String dd = sourceProduct.getName().substring(productNameStartIndex + 10, productNameStartIndex + 12);
        return dd + mm + yy;
    }

    @Override
    void setNoaaId() {
        final String platformName =
                sourceProduct.getMetadataRoot().getElement("Global_Attributes").getAttributeString("platform");
        noaaId =  platformName.substring(5);
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SZA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SAA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_VZA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_VAA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[0]);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[1]);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[2]);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[3]);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[4]);

        sampleConfigurer.defineSample(index, IdepixConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        // the only standard band:
        sampleConfigurer.defineSample(index, IdepixConstants.CLASSIF_BAND_NAME);
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");
        FlagCoding flagCoding = AvhrrAcUtils.createAvhrrAcFlagCoding(IdepixConstants.CLASSIF_BAND_NAME);
        classifFlagBand.setSampleCoding(flagCoding);
        getTargetProduct().getFlagCodingGroup().add(flagCoding);

        productConfigurer.copyGeoCoding();
        AvhrrAcUtils.setupAvhrrAcClassifBitmask(getTargetProduct());

        // radiances:
        if (copyRadiances) {
            for (int i = 0; i < AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES.length; i++) {
                ProductUtils.copyBand(AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[i],
                                      sourceProduct, getTargetProduct(), true);
            }
        }

        // auxiliary bands:
        if (copyGeometries) {
            ProductUtils.copyBand(AvhrrConstants.SRC_TIMELINE_SZA_BAND_NAME, sourceProduct, getTargetProduct(), true);
            ProductUtils.copyBand(AvhrrConstants.SRC_TIMELINE_VZA_BAND_NAME, sourceProduct, getTargetProduct(), true);
            ProductUtils.copyBand(AvhrrConstants.SRC_TIMELINE_SAA_BAND_NAME, sourceProduct, getTargetProduct(), true);
            ProductUtils.copyBand(AvhrrConstants.SRC_TIMELINE_VAA_BAND_NAME, sourceProduct, getTargetProduct(), true);
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AvhrrTimelineClassificationOp.class);
        }
    }
}
