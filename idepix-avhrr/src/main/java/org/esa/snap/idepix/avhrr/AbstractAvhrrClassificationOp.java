package org.esa.snap.idepix.avhrr;

import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.idepix.core.util.*;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.gpf.pointop.PixelOperator;
import org.esa.snap.core.gpf.pointop.Sample;
import org.esa.snap.core.gpf.pointop.WritableSample;
import org.esa.snap.core.util.math.MathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;

/**
 * Basic operator for GlobAlbedo pixel classification
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Idepix.Avhrr.Abstract.Classification",
        version = "3.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2016 by Brockmann Consult",
        description = "Abstract basic operator for pixel classification from AVHRR L1b data.")
public abstract class AbstractAvhrrClassificationOp extends PixelOperator {

    @SourceProduct(alias = "l1b", description = "The source product.")
    Product sourceProduct;

    @SourceProduct(alias = "waterMask")
    Product waterMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(defaultValue = "false", label = " Copy input radiance bands (with albedo1/2 converted)")
    boolean copyRadiances = false;

    @Parameter(defaultValue = "false", label = " Copy geometry bands")
    boolean copyGeometries = false;

    @Parameter(defaultValue = "2", label = " Width of cloud buffer (# of pixels)")
    int cloudBufferWidth;

    @Parameter(defaultValue = "50", valueSet = {"50", "150"}, label = " Resolution of used land-water mask in m/pixel",
            description = "Resolution in m/pixel")
    int wmResolution;

    @Parameter(defaultValue = "true", label = " Consider water mask fraction")
    boolean useWaterMaskFraction = true;

    @Parameter(defaultValue = "false", label = " Flip source images (check before if needed!)")
    boolean flipSourceImages;

    @Parameter(defaultValue = "2.15",
            label = " Schiller NN cloud ambiguous lower boundary ",
            description = " Schiller NN cloud ambiguous lower boundary ")
    double avhrrNNCloudAmbiguousLowerBoundaryValue;

    @Parameter(defaultValue = "3.45",
            label = " Schiller NN cloud ambiguous/sure separation value ",
            description = " Schiller NN cloud ambiguous cloud ambiguous/sure separation value ")
    double avhrrNNCloudAmbiguousSureSeparationValue;

    @Parameter(defaultValue = "4.45",
            label = " Schiller NN cloud sure/snow separation value ",
            description = " Schiller NN cloud ambiguous cloud sure/snow separation value ")
    double avhrrNNCloudSureSnowSeparationValue;


    @Parameter(defaultValue = "20.0",
            label = " Reflectance 1 'brightness' threshold ",
            description = " Reflectance 1 'brightness' threshold ")
    double reflCh1Thresh;

    @Parameter(defaultValue = "20.0",
            label = " Reflectance 2 'brightness' threshold ",
            description = " Reflectance 2 'brightness' threshold ")
    double reflCh2Thresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 2/1 ratio threshold ",
            description = " Reflectance 2/1 ratio threshold ")
    double r2r1RatioThresh;

    @Parameter(defaultValue = "1.0",
            label = " Reflectance 3/1 ratio threshold ",
            description = " Reflectance 3/1 ratio threshold ")
    double r3r1RatioThresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 4 brightness temperature threshold (C)",
            description = " Channel 4 brightness temperature threshold (C)")
    double btCh4Thresh;

    @Parameter(defaultValue = "-30.0",
            label = " Channel 5 brightness temperature threshold (C)",
            description = " Channel 5 brightness temperature threshold (C)")
    double btCh5Thresh;

    ElevationModel getasseElevationModel;

    static final int ALBEDO_TO_RADIANCE = 0;
    static final int RADIANCE_TO_ALBEDO = 1;

    static final int BT_TO_RADIANCE = 0;
    static final int RADIANCE_TO_BT = 1;


    static final String AVHRRAC_NET_NAME = "6x3_114.1.net";

    ThreadLocal<SchillerNeuralNetWrapper> avhrrNeuralNet;

    AvhrrAuxdata.Line2ViewZenithTable vzaTable;
    AvhrrAuxdata.Rad2BTTable rad2BTTable;

    SunPosition sunPosition;

    String noaaId;


    public Product getSourceProduct() {
        return sourceProduct;
    }

    @Override
    protected void computePixel(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples) {
        runAvhrrAlgorithm(x, y, sourceSamples, targetSamples);
    }

    void readSchillerNets() {
        try (InputStream is = getClass().getResourceAsStream(AVHRRAC_NET_NAME)) {
            avhrrNeuralNet = SchillerNeuralNetWrapper.create(is);
        } catch (IOException e) {
            throw new OperatorException("Cannot read Schiller neural nets: " + e.getMessage());
        }
    }

    GeoPos computeSatPosition(int y) {
        return getGeoPos(sourceProduct.getSceneRasterWidth() / 2, y);
    }

    void computeSunPosition() {
        final Calendar calendar = AvhrrAcUtils.getProductDateAsCalendar(getProductDatestring());
        sunPosition = SunPositionCalculator.calculate(calendar);
    }

    private int getDoy() {
        return IdepixUtils.getDoyFromYYMMDD(getProductDatestring());
    }

    double getDistanceCorr() {
        return 1.0 + 0.033 * Math.cos(2.0 * Math.PI * getDoy() / 365.0);
    }

    GeoPos getGeoPos(int x, int y) {
        final GeoPos geoPos = new GeoPos();
        final GeoCoding geoCoding = sourceProduct.getSceneGeoCoding();
        final PixelPos pixelPos = new PixelPos(x, y);
        geoCoding.getGeoPos(pixelPos, geoPos);
        return geoPos;
    }

    double calculateReflectancePartChannel3b(double radianceCh3b,double btCh4, double btch5, double sza) {
        // follows GK formula
        int sensorId;
        double frequenz;
        double t_3b_B0;
        double r_3b_em;
        double b_0_3b;
        double emissivity_3b;
        double result;
        // different central wave numbers for AVHRR Channel3b correspond to the temperature ranges & to NOAA11 and NOAA14
        // NOAA 11: 180-225	2663.500, 225-275	2668.150, 275-320	2671.400, 270-310	2670.96
        // NOAA 14: 190-230	2638.652, 230-270	2642.807, 270-310	2645.899, 290-330	2647.169



        switch (noaaId) {
            case "7":
                // NOAA 7
                sensorId = 2;
                frequenz=AvhrrConstants.FREQUENZ_3B[sensorId];
                break;
            case "11":
                // NOAA 11
                sensorId = 0;
                frequenz=AvhrrConstants.FREQUENZ_3B[sensorId];
                break;
            case "14":
                // NOAA 14
                sensorId = 1;
                frequenz=AvhrrConstants.FREQUENZ_3B[sensorId];
                break;
            default:
                throw new OperatorException("Cannot parse source product name " + sourceProduct.getName() + " properly.");
        }

        if ((btCh4 - btch5) > 1.) {
            t_3b_B0 = AvhrrConstants.A0[sensorId]
                    + AvhrrConstants.B0[sensorId] * btCh4
                    + AvhrrConstants.C0[sensorId] * (btCh4 - btch5);
        } else {
            t_3b_B0 = btCh4;
        }

        if (btCh4  > 0.) {
            r_3b_em = (AvhrrConstants.c1 * Math.pow(frequenz, 3))
                    /(Math.exp((AvhrrConstants.c2 * frequenz)/
                    ((t_3b_B0- AvhrrConstants.a1_3b[sensorId])/(AvhrrConstants.a2_3b[sensorId])))-1.);
        } else {
            r_3b_em = 0;
        }

        if (btCh4  > 0.) {
            emissivity_3b = radianceCh3b/r_3b_em;
        } else {
            emissivity_3b = 0;
        }

        if (sza  < 90. && r_3b_em > 0. && radianceCh3b > 0.) {
            b_0_3b = 1000.0 * AvhrrConstants.SOLAR_3b/ AvhrrConstants.EW_3b[sensorId];
            result = Math.PI * (radianceCh3b - r_3b_em)/
                    (b_0_3b * Math.cos(sza * MathUtils.DTOR) * getDistanceCorr() - Math.PI * r_3b_em );
        } else  if (sza  > 90. && emissivity_3b > 0.) {
            result = 1. - emissivity_3b;
        } else {
            result = Double.NaN;
        }
        return result;
    }

    double convertBetweenAlbedoAndRadiance(double input, double sza, int mode, int bandIndex) {
        // follows GK formula
        float[] integrSolarSpectralIrrad = new float[2];     // F
        float[] spectralResponseWidth = new float[2];        // W
        switch (noaaId) {
            case "7":
                // NOAA 7, NOAA POD Guide 2001, p. 3-23
                integrSolarSpectralIrrad[0] = 177.5f;  // F
                integrSolarSpectralIrrad[1] = 261.9f;  // F
                spectralResponseWidth[0] = 0.108f;  // W
                spectralResponseWidth[1] = 0.249f;  // W
                break;
            case "11":  //
                // NOAA 11
                integrSolarSpectralIrrad[0] = 184.1f;
                integrSolarSpectralIrrad[1] = 241.1f;
                spectralResponseWidth[0] = 0.1130f;
                spectralResponseWidth[1] = 0.229f;
                break;
            case "14":
                // NOAA 14
                integrSolarSpectralIrrad[0] = 221.42f;
                integrSolarSpectralIrrad[1] = 252.29f;
                spectralResponseWidth[0] = 0.136f;
                spectralResponseWidth[1] = 0.245f;
            case "15":
                // NOAA 15 - NOAA_KLM_Users_Guide - APPENDIX D: Table D.1-7 p.10
                integrSolarSpectralIrrad[0] = 138.7f;
                integrSolarSpectralIrrad[1] = 235.4f;
                spectralResponseWidth[0] = 0.084f;
                spectralResponseWidth[1] = 0.228f;
            case "16":
                // NOAA 16 - NOAA_KLM_Users_Guide - APPENDIX D: Table D.2-8 p.83
                integrSolarSpectralIrrad[0] = 133.2f;
                integrSolarSpectralIrrad[1] = 243.1f;
                spectralResponseWidth[0] = 0.081f;
                spectralResponseWidth[1] = 0.235f;
            case "17":
                // NOAA 17 - NOAA_KLM_Users_Guide - APPENDIX D: Table D.3-6 p.118
                integrSolarSpectralIrrad[0] = 136.212f;
                integrSolarSpectralIrrad[1] = 240.558f;
                spectralResponseWidth[0] = 0.0830f;
                spectralResponseWidth[1] = 0.2332f;
                break;
            case "18":
                // NOAA 18 - NOAA_KLM_Users_Guide - APPENDIX D: Table D.4-6 p.186
                integrSolarSpectralIrrad[0] = 130.3f;
                integrSolarSpectralIrrad[1] = 246.0f;
                spectralResponseWidth[0] = 0.0793f;
                spectralResponseWidth[1] = 0.2465f;
                break;
            case "METOP-A":
                // METOP-A - NOAA_KLM_Users_Guide - APPENDIX D: Table D.5-6 p.490
                integrSolarSpectralIrrad[0] = 139.873215f;
                integrSolarSpectralIrrad[1] = 232.919556f;
                spectralResponseWidth[0] = 0.084877f;
                spectralResponseWidth[1] = 0.229421f;
                break;
            case "19":
                // NOAA 19 - NOAA_KLM_Users_Guide - APPENDIX D: Table D.6-6 p.779
                integrSolarSpectralIrrad[0] = 126.773f;
                integrSolarSpectralIrrad[1] = 225.698f;
                spectralResponseWidth[0] = 0.077580f;
                spectralResponseWidth[1] = 0.217591f;
                break;
            case "METOP-B":
                // METOP-B - NOAA_KLM_Users_Guide - APPENDIX D: Table D.7-6 p.1061
                integrSolarSpectralIrrad[0] = 140.3147f;
                integrSolarSpectralIrrad[1] = 245.0039f;
                spectralResponseWidth[0] = 0.086698f;
                spectralResponseWidth[1] = 0.242643f;
                break;
            default:
                throw new OperatorException("Cannot parse source product name " + sourceProduct.getName() + " properly.");
        }

        // GK: R=A (F/(100 PI W)  technical Albedo A  and  A_corr = R (100 PI W / (F * cos(sun_zenith) * abstandkorrektur))
        final double conversionFactor = integrSolarSpectralIrrad[bandIndex] /
                (100.0 * Math.PI * spectralResponseWidth[bandIndex]);
        double result;
        //input technical albedo output radiance
        if (mode == ALBEDO_TO_RADIANCE) {
            result = input * conversionFactor;
        // input radiance output corrected albedo => albedo_corr= technical_albedo/(cos(sun_zenith) * abstandkorrektur)
        } else if (mode == RADIANCE_TO_ALBEDO) {
            result = input / (conversionFactor * Math.cos(sza * MathUtils.DTOR) * getDistanceCorr());
        } else {
            throw new IllegalArgumentException("wrong mode " + mode + " for albedo/radiance conversion");
        }
        return result;

    }


    abstract void setClassifFlag(WritableSample[] targetSamples, AvhrrAlgorithm algorithm);

    abstract void runAvhrrAlgorithm(int x, int y, Sample[] sourceSamples, WritableSample[] targetSamples);

    abstract void setNoaaId();

    abstract String getProductDatestring();

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AbstractAvhrrClassificationOp.class);
        }
    }
}
