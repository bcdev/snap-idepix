package org.esa.snap.idepix.viirs;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix VIIRS algorithm
 *
 * @author olafd
 */
class ViirsConstants {


    private final static String VIIRS_REFLECTANCE_1_BAND_NAME = "rhot_410";
    private final static String VIIRS_REFLECTANCE_2_BAND_NAME = "rhot_443";
    private final static String VIIRS_REFLECTANCE_3_BAND_NAME = "rhot_486";
    private final static String VIIRS_REFLECTANCE_4_BAND_NAME = "rhot_551";
    private final static String VIIRS_REFLECTANCE_5_BAND_NAME = "rhot_671";
    private final static String VIIRS_REFLECTANCE_6_BAND_NAME = "rhot_745";
    private final static String VIIRS_REFLECTANCE_7_BAND_NAME = "rhot_862";
    private final static String VIIRS_REFLECTANCE_8_BAND_NAME = "rhot_1238";
    private final static String VIIRS_REFLECTANCE_9_BAND_NAME = "rhot_1601";
    private final static String VIIRS_REFLECTANCE_10_BAND_NAME = "rhot_2257";
    static String[] VIIRS_SPECTRAL_BAND_NAMES = {
            VIIRS_REFLECTANCE_1_BAND_NAME,
            VIIRS_REFLECTANCE_2_BAND_NAME,
            VIIRS_REFLECTANCE_3_BAND_NAME,
            VIIRS_REFLECTANCE_4_BAND_NAME,
            VIIRS_REFLECTANCE_5_BAND_NAME,
            VIIRS_REFLECTANCE_6_BAND_NAME,
            VIIRS_REFLECTANCE_7_BAND_NAME,
            VIIRS_REFLECTANCE_8_BAND_NAME,
            VIIRS_REFLECTANCE_9_BAND_NAME,
            VIIRS_REFLECTANCE_10_BAND_NAME,
    };
    final static int VIIRS_L1B_NUM_SPECTRAL_BANDS = VIIRS_SPECTRAL_BAND_NAMES.length;

    static final int IDEPIX_MIXED_PIXEL = IdepixConstants.NUM_DEFAULT_FLAGS + 1;

    static final String IDEPIX_MIXED_PIXEL_DESCR_TEXT = "Mixed pixel";

    // debug bands:
    static final String BRIGHTNESS_BAND_NAME = "brightness_value";
    static final String NDSI_BAND_NAME = "ndsi_value";
}
