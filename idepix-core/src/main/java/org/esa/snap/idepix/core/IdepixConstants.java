package org.esa.snap.idepix.core;

import java.util.regex.Pattern;

/**
 * IDEPIX constants
 *
 * @author Olaf Danne
 */
public class IdepixConstants {

    public static final String CLASSIF_BAND_NAME = "pixel_classif_flags";
    public static final String LAND_WATER_FRACTION_BAND_NAME = "land_water_fraction";

    public static final int IDEPIX_INVALID = 0;
    public static final int IDEPIX_CLOUD = 1;
    public static final int IDEPIX_CLOUD_AMBIGUOUS = 2;
    public static final int IDEPIX_CLOUD_SURE = 3;
    public static final int IDEPIX_CLOUD_BUFFER = 4;
    public static final int IDEPIX_CLOUD_SHADOW = 5;
    public static final int IDEPIX_SNOW_ICE = 6;
    public static final int IDEPIX_BRIGHT = 7;
    public static final int IDEPIX_WHITE = 8;
    public static final int IDEPIX_COASTLINE = 9;
    public static final int IDEPIX_LAND = 10;

    public static final int NUM_DEFAULT_FLAGS = 11;

    public static final String IDEPIX_CLOUD_DESCR_TEXT = "Pixels which are either cloud_sure or cloud_ambiguous";

    public static final int LAND_WATER_MASK_RESOLUTION = 50;
    public static final int OVERSAMPLING_FACTOR_X = 3;
    public static final int OVERSAMPLING_FACTOR_Y = 3;

    public static final String SPOT_VGT_PRODUCT_TYPE_PREFIX = "VGT";
    public static final String PROBAV_PRODUCT_TYPE_PREFIX = "PROBA-V";

    public static final String AVHRR_L1b_PRODUCT_TYPE = "AVHRR";
    public static final String AVHRR_L1b_USGS_PRODUCT_TYPE = "NOAA_POD_AVHRR_HRPT";

    public static final int NO_DATA_VALUE = -1;

    public static final String NN_OUTPUT_BAND_NAME = "nn_value";
    public static final String CTP_OUTPUT_BAND_NAME = "ctp";

    private static final String VGT_RADIANCE_0_BAND_NAME = "B0";
    private static final String VGT_RADIANCE_2_BAND_NAME = "B2";
    private static final String VGT_RADIANCE_3_BAND_NAME = "B3";
    private static final String VGT_RADIANCE_MIR_BAND_NAME = "MIR";
    /**
     * The names of the VGT spectral band names.
     */
    public static final String[] VGT_REFLECTANCE_BAND_NAMES = {
            VGT_RADIANCE_0_BAND_NAME, // 0
            VGT_RADIANCE_2_BAND_NAME, // 1
            VGT_RADIANCE_3_BAND_NAME, // 2
            VGT_RADIANCE_MIR_BAND_NAME // 3
    };

    public static final String[] VGT_ANNOTATION_BAND_NAMES = {
            "VZA",
            "SZA",
            "VAA",
            "SAA",
            "WVG",
            "OG",
            "AG"
    };

    public static final float[] VGT_WAVELENGTHS = {450.0f, 645.0f, 835.0f, 1670.0f};

    private static final String PROBAV_BLUE_BAND_NAME = "TOA_REFL_BLUE";
    private static final String PROBAV_RED_BAND_NAME = "TOA_REFL_RED";
    private static final String PROBAV_NIR_BAND_NAME = "TOA_REFL_NIR";
    private static final String PROBAV_SWIR_BAND_NAME = "TOA_REFL_SWIR";
    public static final String[] PROBAV_REFLECTANCE_BAND_NAMES = {
            PROBAV_BLUE_BAND_NAME,
            PROBAV_RED_BAND_NAME,
            PROBAV_NIR_BAND_NAME,
            PROBAV_SWIR_BAND_NAME
    };

    public static final String[] PROBAV_ANNOTATION_BAND_NAMES = {
            "VZA_SWIR",
            "VZA_VNIR",
            "SZA",
            "VAA_SWIR",
            "VAA_VNIR",
            "SAA",
            "NDVI"
    };

    public static final float[] PROBAV_WAVELENGTHS = {462.0f, 655.5f, 843.0f, 1599.0f};

    public static final String[] VIRRS_SNPP_BAND_NAMES =
            new String[]{"rhot_410", "rhot_443", "rhot_486", "rhot_551", "rhot_671",
                    "rhot_745", "rhot_862", "rhot_1238", "rhot_1601", "rhot_2257"};

    public static final String[] VIRRS_NOAA20_BAND_NAMES =
            new String[]{"rhot_411", "rhot_445", "rhot_489", "rhot_556", "rhot_667",
                    "rhot_746", "rhot_868", "rhot_1238", "rhot_1604", "rhot_2258"};

    // todo: check these
    public static final String[] VIRRS_NOAA21_BAND_NAMES =
            new String[]{"rhot_411", "rhot_445", "rhot_489", "rhot_556", "rhot_667",
                    "rhot_746", "rhot_868", "rhot_1238", "rhot_1604", "rhot_2258"};


    public static final String INPUT_INCONSISTENCY_ERROR_MESSAGE =
            "Selected cloud screening algorithm cannot be used with given input product. \n\n" +
                    "Supported sensors are: MERIS, SPOT VGT, MODIS, Landsat-8, SeaWiFS, Sentinel-2 MSI, " +
                    "Sentinel-3 OLCI, PROBA-V, VIIRS.";

    /**
     * A pattern which matches MERIS CC L1P product types
     *
     * @see java.util.regex.Matcher
     */
    public static final Pattern MERIS_CCL1P_TYPE_PATTERN = Pattern.compile("MER_..._CCL1P");

    static final String IDEPIX_INVALID_DESCR_TEXT = "Invalid pixels";
    static final String IDEPIX_CLOUD_AMBIGUOUS_DESCR_TEXT =
            "Semi transparent clouds, or clouds where the detection level is uncertain";
    static final String IDEPIX_CLOUD_SURE_DESCR_TEXT = "Fully opaque clouds with full confidence of their detection";
    static final String IDEPIX_CLOUD_BUFFER_DESCR_TEXT =
            "A buffer of n pixels around a cloud. n is a user supplied parameter. Applied to pixels masked as 'cloud'";
    static final String IDEPIX_CLOUD_SHADOW_DESCR_TEXT = "Pixel is affected by a cloud shadow";
    static final String IDEPIX_SNOW_ICE_DESCR_TEXT = "Clear snow/ice pixels";
    static final String IDEPIX_BRIGHT_DESCR_TEXT = "Bright pixels";
    static final String IDEPIX_WHITE_DESCR_TEXT = "White pixels";
    static final String IDEPIX_COASTLINE_DESCR_TEXT = "Pixels at a coastline";
    static final String IDEPIX_LAND_DESCR_TEXT = "Land pixels";


    private IdepixConstants() {
    }
}
