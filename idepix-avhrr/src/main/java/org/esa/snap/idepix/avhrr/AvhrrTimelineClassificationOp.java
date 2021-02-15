package org.esa.snap.idepix.avhrr;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
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

    private static final int CLASSIF_SAMPLE_INDEX = 0;
    private static final int NDSI_SAMPLE_INDEX = 1;
    private static final int SOURCE_DESERT_SAMPLE_INDEX = AvhrrConstants.SRC_TIMELINE_END_INDEX + 1;
    private static final int SOURCE_INLAND_WATER_SAMPLE_INDEX = AvhrrConstants.SRC_TIMELINE_END_INDEX + 2;

    private String sensor;
    private boolean isAvhrrB3aInactive;

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "desertMask", optional = true)
    private Product desertProduct;

    @SourceProduct(alias = "desertMaskCollocated", optional = true)
    private Product desertMaskProduct;

    @SourceProduct(alias = "waterMask")
    private Product waterMaskProduct;

    @SourceProduct(alias = "inlandWaterMaskCollocated", optional = true)
    private Product inlandWaterCollProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;


    @Override
    public void prepareInputs() throws OperatorException {
        setNoaaId();
        noaaId = "14";  // test, as long we do not have NOAA >= 15
        readSchillerNets();

        try {
            rad2BTTable = AvhrrAuxdata.getInstance().createRad2BTTable(noaaId);
        } catch (IOException e) {
            throw new OperatorException("Failed to get Rad2BTTable from auxdata - cannot proceed: ", e);
        }

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor =
                ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.canBeDownloaded()) {
            throw new OperatorException("DEM cannot be downloaded: " + demName + ".");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        sensor = sourceProduct.getMetadataRoot().getElement("Global_Attributes").getAttributeString("sensor");
        if (!sensor.equals("AVHRR/2") && !sensor.equals("AVHRR/3")) {
            throw new OperatorException("Sensor '" + sensor + "' is not supported in Timeline product. " +
                    "Must be 'AVHRR/2' or 'AVHRR/3'.");
        }

        // for AVHRR/3, check if avhrr_b3a is active
        // this depends on NOAA_XX and period, see table 2 in
        // https://www.atmos-chem-phys.net/13/5351/2013/acp-13-5351-2013.pdf:
        isAvhrrB3aInactive = sensor.equals("AVHRR/2") ||
                sourceProduct.getBand("avhrr_b3a").getStx().getSampleCount() == 0;

    }

    @Override
    void runAvhrrAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        AvhrrAlgorithm algorithmToUse =
                (sensor.equals("AVHRR/2") || isAvhrrB3aInactive) ? new Avhrr2Algorithm() : new Avhrr3Algorithm();

        final double sza = sourceSamples[AvhrrConstants.SRC_TIMELINE_SZA].getDouble();
        final double vza = sourceSamples[AvhrrConstants.SRC_TIMELINE_VZA].getDouble();
        final double saa = sourceSamples[AvhrrConstants.SRC_TIMELINE_SAA].getDouble();
        final double vaa = sourceSamples[AvhrrConstants.SRC_TIMELINE_VAA].getDouble();
        final int clm = sourceSamples[AvhrrConstants.SRC_TIMELINE_CLM].getInt();

        final double albedo1 = sourceSamples[AvhrrConstants.SRC_TIMELINE_ALBEDO_1].getDouble();
        final double albedo2 = sourceSamples[AvhrrConstants.SRC_TIMELINE_ALBEDO_2].getDouble();

        float waterFraction = Float.NaN;
        // the water mask ends at 59 Degree south, stop earlier to avoid artefacts
        if (getGeoPos(x, y).lat > -58f) {
            waterFraction = sourceSamples[AvhrrConstants.SRC_TIMELINE_WATERFRACTION].getFloat();
        }
        algorithmToUse.setWaterFraction(waterFraction);
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_LAND, algorithmToUse.isLand());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_COASTLINE, algorithmToUse.isCoastline());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(AvhrrConstants.IDEPIX_WATER, !algorithmToUse.isLand());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(AvhrrConstants.IDEPIX_CLEAR_WATER, false);


        if (inlandWaterCollProduct != null) {
            boolean idepixLandPrior = algorithmToUse.isLand();
            int inlandWater = sourceSamples[SOURCE_INLAND_WATER_SAMPLE_INDEX].getInt();
            if (!idepixLandPrior && (inlandWater == 1)) {
                targetSamples[CLASSIF_SAMPLE_INDEX].set(AvhrrConstants.IDEPIX_INLAND_WATER, true);
            }
        }
        if (albedo1 >= 0.0 && albedo2 >= 0.0 && AvhrrAcUtils.anglesValid(sza, vza, saa, vaa)) {
            if (algorithmToUse.isLand()) {
                final double vaa_r = toRadians(vaa);
                final double saa_r = toRadians(saa);

                final double relAzi = acos(cos(saa_r) * cos(vaa_r) + sin(saa_r) * sin(vaa_r));
                final double altitude = computeGetasseAltitude(x, y);

                //        final double bt3 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_3b].getDouble();    // brightness temp !!
                final double bt4 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_4].getDouble();
                final double bt5 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_5].getDouble();

                // GK, 20150325: convert albedo1, 2 to 'normalized' albedo:
                // norm_albedo_i = albedo_i / (d^2_sun * cos(theta_sun))    , effect is a few % only
                final double d = getDistanceCorr() * Math.cos(sza * MathUtils.DTOR);
                final double albedo1Norm = albedo1 / d;
                final double albedo2Norm = albedo2 / d;

                double[] avhrrRadiance = new double[AvhrrConstants.AVHRR_AC_RADIANCE_BAND_NAMES.length];

                avhrrRadiance[0] = convertBetweenAlbedoAndRadiance(albedo1, sza, ALBEDO_TO_RADIANCE, 0);
                avhrrRadiance[1] = convertBetweenAlbedoAndRadiance(albedo2, sza, ALBEDO_TO_RADIANCE, 1);
                double ndsi;
                algorithmToUse.setLatitude(getGeoPos(x, y).lat);
                algorithmToUse.setLongitude(getGeoPos(x, y).lon);
                if (desertMaskProduct != null) {
                    algorithmToUse.setDesert(sourceSamples[SOURCE_DESERT_SAMPLE_INDEX].getBoolean());
                }
                if (sensor.equals("AVHRR/2") || isAvhrrB3aInactive) {
                    final double bt3 = sourceSamples[AvhrrConstants.SRC_TIMELINE_RAD_3b].getDouble();    // brightness temp !!
                    avhrrRadiance[2] = AvhrrAcUtils.convertBtToRadiance(noaaId, rad2BTTable, bt3, 3, waterFraction);
                    final double albedo3b = calculateReflectancePartChannel3b(avhrrRadiance[2], bt4, bt5, sza);
                    algorithmToUse.setReflCh3(albedo3b); // on [0,1]
                    algorithmToUse.setElevation(altitude);
                    algorithmToUse.setBtCh3(bt3);
                    ndsi = ((albedo1Norm / 100.) - albedo3b) / (albedo3b + (albedo1Norm / 100.));
                } else {
                    final double albedo3a = sourceSamples[AvhrrConstants.SRC_TIMELINE_ALBEDO_3a].getDouble();
                    avhrrRadiance[2] = convertBetweenAlbedoAndRadiance(albedo3a, sza, ALBEDO_TO_RADIANCE, 2);
                    final double albedo3Norm = albedo3a / d;
                    final double albedo3Norm100 = albedo3Norm / 100.0;
                    algorithmToUse.setReflCh3(albedo3Norm100); // on [0,1]
                    ndsi = ((albedo1Norm / 100.) - albedo3Norm100) / (albedo3Norm100 + (albedo1Norm / 100.));
                }
                algorithmToUse.setNdsi(ndsi);

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

                double[] nnOutput = nnWrapper.getNeuralNet().calc(inputVector);

                algorithmToUse.setRadiance(avhrrRadiance);
                algorithmToUse.setNnOutput(nnOutput);
                algorithmToUse.setAmbiguousSureSeparationValue(avhrrNNCloudAmbiguousSureSeparationValue);
                algorithmToUse.setSureSnowSeparationValue(avhrrNNCloudSureSnowSeparationValue);
                algorithmToUse.setReflCh1(albedo1Norm / 100.0); // on [0,1]        --> put here albedo_norm now!!
                algorithmToUse.setReflCh2(albedo2Norm / 100.0); // on [0,1]
                algorithmToUse.setBtCh4(bt4);
                algorithmToUse.setBtCh5(bt5);
                algorithmToUse.setElevation(altitude);
                algorithmToUse.setCLM(clm);
                setClassifFlag(targetSamples, algorithmToUse);
                targetSamples[NDSI_SAMPLE_INDEX].set(ndsi);
            }
        } else {
            targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_INVALID, true);
        }
    }

    @Override
    void setClassifFlag(WritableSample[] targetSamples, AvhrrAlgorithm algorithm) {
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_INVALID, algorithm.isInvalid());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_CLOUD, algorithm.isCloud());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_CLOUD_AMBIGUOUS, algorithm.isCloudAmbiguous());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_CLOUD_SURE, algorithm.isCloudSure());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_CLOUD_BUFFER, algorithm.isCloudBuffer());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_CLOUD_SHADOW, algorithm.isCloudShadow());
        targetSamples[CLASSIF_SAMPLE_INDEX].set(IdepixConstants.IDEPIX_SNOW_ICE, algorithm.isSnowIce());
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
        noaaId = platformName.substring(5);
    }

    @Override
    protected void configureSourceSamples(SourceSampleConfigurer sampleConfigurer) throws OperatorException {
        int index = 0;
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SZA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SAA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_VZA_BAND_NAME);
        sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_VAA_BAND_NAME);
        for (int i = 0; i < AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES.length; i++) {
            sampleConfigurer.defineSample(index++, AvhrrConstants.SRC_TIMELINE_SPECTRAL_BAND_NAMES[i]);
        }
        sampleConfigurer.defineSample(index, IdepixConstants.LAND_WATER_FRACTION_BAND_NAME, waterMaskProduct);
        if (desertMaskProduct != null) {
            sampleConfigurer.defineSample(SOURCE_DESERT_SAMPLE_INDEX, "DesertMaskArea", desertMaskProduct);
        }
        if (inlandWaterCollProduct != null) {
            sampleConfigurer.defineSample(SOURCE_INLAND_WATER_SAMPLE_INDEX, "InlandWaterMaskArea", inlandWaterCollProduct);
        }
        sampleConfigurer.defineSample(AvhrrConstants.SRC_TIMELINE_CLM, AvhrrConstants.SRC_TIMELINE_CLM_BAND_NAME);
    }

    @Override
    protected void configureTargetSamples(TargetSampleConfigurer sampleConfigurer) throws OperatorException {
        // the only standard band:
        sampleConfigurer.defineSample(CLASSIF_SAMPLE_INDEX, IdepixConstants.CLASSIF_BAND_NAME);
        sampleConfigurer.defineSample(NDSI_SAMPLE_INDEX, "NDSI");
    }

    @Override
    protected void configureTargetProduct(ProductConfigurer productConfigurer) {
        productConfigurer.copyTimeCoding();
        productConfigurer.copyTiePointGrids();
        Band classifFlagBand = productConfigurer.addBand(IdepixConstants.CLASSIF_BAND_NAME, ProductData.TYPE_INT16);

        Band ndsiBand = productConfigurer.addBand("NDSI", ProductData.TYPE_FLOAT32);
        ndsiBand.setDescription("normalized difference snow index");
        ndsiBand.setNoDataValue(Double.NaN);
        ndsiBand.setNoDataValueUsed(true);
        ndsiBand.setUnit("dl");


        classifFlagBand.setDescription("Pixel classification flag");
        classifFlagBand.setUnit("dl");

        FlagCoding flagCoding = AvhrrAcUtils.createAvhrrAcFlagCoding();
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

    void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(AVHRRAC_NET_NAME)) {
            avhrrNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
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
