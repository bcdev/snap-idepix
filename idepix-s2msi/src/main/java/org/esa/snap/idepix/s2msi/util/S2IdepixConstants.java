package org.esa.snap.idepix.s2msi.util;

/**
 * IDEPIX constants
 *
 * @author Olaf Danne
 */
public class S2IdepixConstants {

    public static final String IDEPIX_CLASSIF_FLAGS = "pixel_classif_flags";

    public static final int IDEPIX_INVALID = 0;
    public static final String IDEPIX_INVALID_NAME = "IDEPIX_INVALID";
    public static final String IDEPIX_INVALID_DESCR_TEXT = "Invalid pixels";

    public static final int IDEPIX_CLOUD = 1;
    public static final String IDEPIX_CLOUD_NAME = "IDEPIX_CLOUD";
    public static final String IDEPIX_CLOUD_DESCR_TEXT = "Pixels which are either cloud_sure or cloud_ambiguous";

    public static final int IDEPIX_CLOUD_AMBIGUOUS = 2;
    public static final String IDEPIX_CLOUD_AMBIGUOUS_NAME = "IDEPIX_CLOUD_AMBIGUOUS";
    public static final String IDEPIX_CLOUD_AMBIGUOUS_DESCR_TEXT = "Semi transparent clouds, or clouds where the detection level is uncertain";

    public static final int IDEPIX_CLOUD_SURE = 3;
    public static final String IDEPIX_CLOUD_SURE_NAME = "IDEPIX_CLOUD_SURE";
    public static final String IDEPIX_CLOUD_SURE_DESCR_TEXT = "Fully opaque clouds with full confidence of their detection";

    public static final int IDEPIX_CLOUD_BUFFER = 4;
    public static final String IDEPIX_CLOUD_BUFFER_NAME = "IDEPIX_CLOUD_BUFFER";
    public static final String IDEPIX_CLOUD_BUFFER_DESCR_TEXT = "A buffer of n pixels around a cloud. n is a user supplied parameter. Applied to pixels masked as 'cloud'";

    public static final int IDEPIX_CLOUD_SHADOW = 5;
    public static final String IDEPIX_CLOUD_SHADOW_NAME = "IDEPIX_CLOUD_SHADOW";
    public static final String IDEPIX_CLOUD_SHADOW_DESCR_TEXT = "Pixel is affected by a cloud shadow (combination of shifted cloud mask in cloud gaps and dark clusters coinciding with a corrected shifted cloud mask)";

    public static final int IDEPIX_SNOW_ICE = 6;
    public static final String IDEPIX_SNOW_ICE_NAME = "IDEPIX_SNOW_ICE";
    public static final String IDEPIX_SNOW_ICE_DESCR_TEXT = "Clear snow/ice pixels";

    public static final int IDEPIX_BRIGHT = 7;
    public static final String IDEPIX_BRIGHT_NAME = "IDEPIX_BRIGHT";
    public static final String IDEPIX_BRIGHT_DESCR_TEXT = "Bright pixels";

    public static final int IDEPIX_WHITE = 8;
    public static final String IDEPIX_WHITE_NAME = "IDEPIX_WHITE";
    public static final String IDEPIX_WHITE_DESCR_TEXT = "White pixels";

    public static final int IDEPIX_COASTLINE = 9;
    public static final String IDEPIX_COASTLINE__NAME = "IDEPIX_COASTLINE";
    public static final String IDEPIX_COASTLINE_DESCR_TEXT = "Pixels at a coastline";

    public static final int IDEPIX_LAND = 10;
    public static final String IDEPIX_LAND_NAME = "IDEPIX_LAND";
    public static final String IDEPIX_LAND_DESCR_TEXT = "Land pixels";

    public static final int IDEPIX_CIRRUS_SURE = 11;
    public static final String IDEPIX_CIRRUS_SURE_NAME = "IDEPIX_CIRRUS_SURE";
    public static final String IDEPIX_CIRRUS_SURE_DESCR_TEXT = "Cirrus clouds with full confidence of their detection";

    public static final int IDEPIX_CIRRUS_AMBIGUOUS = 12;
    public static final String IDEPIX_CIRRUS_AMBIGUOUS_NAME = "IDEPIX_CIRRUS_AMBIGUOUS";
    public static final String IDEPIX_CIRRUS_AMBIGUOUS_DESCR_TEXT = "Cirrus clouds, or clouds where the detection level is uncertain";

    public static final int IDEPIX_CLEAR_LAND = 13;
    public static final String IDEPIX_CLEAR_LAND_NAME = "IDEPIX_CLEAR_LAND";
    public static final String IDEPIX_CLEAR_LAND_DESCR_TEXT = "Clear land pixels";

    public static final int IDEPIX_CLEAR_WATER = 14;
    public static final String IDEPIX_CLEAR_WATER_NAME = "IDEPIX_CLEAR_WATER";
    public static final String IDEPIX_CLEAR_WATER_DESCR_TEXT = "Clear water pixels";

    public static final int IDEPIX_WATER = 15;
    public static final String IDEPIX_WATER_NAME = "IDEPIX_WATER";
    public static final String IDEPIX_WATER_DESCR_TEXT = "Water pixels";

    public static final int IDEPIX_BRIGHTWHITE = 16;
    public static final String IDEPIX_BRIGHTWHITE_NAME = IDEPIX_BRIGHT_NAME + "WHITE";
    public static final String IDEPIX_BRIGHTWHITE_DESCR_TEXT = "'Brightwhite' pixels";

    public static final int IDEPIX_VEG_RISK = 17;
    public static final String IDEPIX_VEG_RISK_NAME = "IDEPIX_VEG_RISK";
    public static final String IDEPIX_VEG_RISK_DESCR_TEXT = "Pixels with vegetation risk";

    public static final int IDEPIX_MOUNTAIN_SHADOW = 18;
    public static final String IDEPIX_MOUNTAIN_SHADOW_NAME = "IDEPIX_MOUNTAIN_SHADOW";
    public static final String IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT = "Pixel is affected by mountain shadow";

    public static final int IDEPIX_POTENTIAL_SHADOW = 19;
    public static final String IDEPIX_POTENTIAL_SHADOW_NAME = "IDEPIX_POTENTIAL_SHADOW";
    public static final String IDEPIX_POTENTIAL_SHADOW_DESCR_TEXT = "Potentially a cloud shadow pixel";

    public static final int IDEPIX_CLUSTERED_CLOUD_SHADOW = 20;
    public static final String IDEPIX_CLUSTERED_CLOUD_SHADOW_NAME = "IDEPIX_CLUSTERED_CLOUD_SHADOW";
    public static final String IDEPIX_CLUSTERED_CLOUD_SHADOW_DESCR_TEXT= "Cloud shadow identified by clustering algorithm";


    public static final int NO_DATA_VALUE = -1;

    public static final String ELEVATION_BAND_NAME = "elevation";
    public static final String LATITUDE_BAND_NAME = "lat";

    public static final String[] S2_MSI_REFLECTANCE_BAND_NAMES = {
            "B1",
            "B2",
            "B3",
            "B4",
            "B5",
            "B6",
            "B7",
            "B8",
            "B8A",
            "B9",
            "B10",
            "B11",
            "B12"
    };

    public static final String SUN_ZENITH_BAND_NAME = "sun_zenith";
    public static final String SUN_AZIMUTH_BAND_NAME = "sun_azimuth";

    public static final String[] S2_MSI_ANNOTATION_BAND_NAMES = {
            SUN_ZENITH_BAND_NAME,
            "view_zenith_mean",
            SUN_AZIMUTH_BAND_NAME,
            "view_azimuth_mean",
    };

    public static final float[] S2_MSI_WAVELENGTHS = {
            443.0f,     // B1
            490.0f,     // B2
            560.0f,     // B3
            665.0f,     // B4
            705.0f,     // B5
            740.0f,     // B6
            783.0f,     // B7
            842.0f,     // B8
            865.0f,     // B8A
            945.0f,     // B9
            1375.0f,    // B10
            1610.0f,    // B11
            2190.0f     // B12
    };

    public static final double[] S2_SOLAR_IRRADIANCES = {
            1913.57,     // B1
            1941.63,     // B2
            1822.61,     // B3
            1512.79,     // B4
            1425.56,     // B5
            1288.32,     // B6
            1163.19,     // B7
            1036.39,     // B8
            955.19,      // B8A
            813.04,      // B9
            367.15,      // B10
            245.59,      // B11
            85.25        // B12
    };

    public static final String INPUT_INCONSISTENCY_ERROR_MESSAGE =
            "Selected cloud screening algorithm cannot be used with given input product. \n\n" +
                    "Input product must be S2 MSI L1C product.";

}
