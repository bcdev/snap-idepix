package org.esa.snap.idepix.modis;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * IdePix MODIS consztants
 *
 * @author olafd
 */
class IdepixModisConstants {

    static final int MODIS_SRC_RAD_OFFSET = 22;

    private static final String MODIS_L1B_REFLECTANCE_1_BAND_NAME = "EV_250_Aggr1km_RefSB.1";           // 645
    private static final String MODIS_L1B_REFLECTANCE_2_BAND_NAME = "EV_250_Aggr1km_RefSB.2";           // 859
    private static final String MODIS_L1B_REFLECTANCE_3_BAND_NAME = "EV_500_Aggr1km_RefSB.3";           // 469
    private static final String MODIS_L1B_REFLECTANCE_4_BAND_NAME = "EV_500_Aggr1km_RefSB.4";           // 555
    private static final String MODIS_L1B_REFLECTANCE_5_BAND_NAME = "EV_500_Aggr1km_RefSB.5";           // 1240
    private static final String MODIS_L1B_REFLECTANCE_6_BAND_NAME = "EV_500_Aggr1km_RefSB.6";           // 1640
    private static final String MODIS_L1B_REFLECTANCE_7_BAND_NAME = "EV_500_Aggr1km_RefSB.7";           // 2130
    private static final String MODIS_L1B_REFLECTANCE_8_BAND_NAME = "EV_1KM_RefSB.8";                    // 412
    private static final String MODIS_L1B_REFLECTANCE_9_BAND_NAME = "EV_1KM_RefSB.9";                    // 443
    private static final String MODIS_L1B_REFLECTANCE_10_BAND_NAME = "EV_1KM_RefSB.10";                   // 488
    private static final String MODIS_L1B_REFLECTANCE_11_BAND_NAME = "EV_1KM_RefSB.11";                   // 531
    private static final String MODIS_L1B_REFLECTANCE_12_BAND_NAME = "EV_1KM_RefSB.12";                   // 547
    private static final String MODIS_L1B_REFLECTANCE_13_BAND_NAME = "EV_1KM_RefSB.13lo";                 // 667
    private static final String MODIS_L1B_REFLECTANCE_14_BAND_NAME = "EV_1KM_RefSB.13hi";                 // 667
    private static final String MODIS_L1B_REFLECTANCE_15_BAND_NAME = "EV_1KM_RefSB.14lo";                 // 678
    private static final String MODIS_L1B_REFLECTANCE_16_BAND_NAME = "EV_1KM_RefSB.14hi";                 // 678
    private static final String MODIS_L1B_REFLECTANCE_17_BAND_NAME = "EV_1KM_RefSB.15";                   // 748
    private static final String MODIS_L1B_REFLECTANCE_18_BAND_NAME = "EV_1KM_RefSB.16";                   // 869
    private static final String MODIS_L1B_REFLECTANCE_19_BAND_NAME = "EV_1KM_RefSB.17";                   // 869
    private static final String MODIS_L1B_REFLECTANCE_20_BAND_NAME = "EV_1KM_RefSB.18";                   // 869
    private static final String MODIS_L1B_REFLECTANCE_21_BAND_NAME = "EV_1KM_RefSB.19";                   // 869
    private static final String MODIS_L1B_REFLECTANCE_22_BAND_NAME = "EV_1KM_RefSB.26";                   // 869

    static final String[] MODIS_L1B_SPECTRAL_BAND_NAMES = {
            MODIS_L1B_REFLECTANCE_1_BAND_NAME,
            MODIS_L1B_REFLECTANCE_2_BAND_NAME,
            MODIS_L1B_REFLECTANCE_3_BAND_NAME,
            MODIS_L1B_REFLECTANCE_4_BAND_NAME,
            MODIS_L1B_REFLECTANCE_5_BAND_NAME,
            MODIS_L1B_REFLECTANCE_6_BAND_NAME,
            MODIS_L1B_REFLECTANCE_7_BAND_NAME,
            MODIS_L1B_REFLECTANCE_8_BAND_NAME,
            MODIS_L1B_REFLECTANCE_9_BAND_NAME,
            MODIS_L1B_REFLECTANCE_10_BAND_NAME,
            MODIS_L1B_REFLECTANCE_11_BAND_NAME,
            MODIS_L1B_REFLECTANCE_12_BAND_NAME,
            MODIS_L1B_REFLECTANCE_13_BAND_NAME,
            MODIS_L1B_REFLECTANCE_14_BAND_NAME,
            MODIS_L1B_REFLECTANCE_15_BAND_NAME,
            MODIS_L1B_REFLECTANCE_16_BAND_NAME,
            MODIS_L1B_REFLECTANCE_17_BAND_NAME,
            MODIS_L1B_REFLECTANCE_18_BAND_NAME,
            MODIS_L1B_REFLECTANCE_19_BAND_NAME,
            MODIS_L1B_REFLECTANCE_20_BAND_NAME,
            MODIS_L1B_REFLECTANCE_21_BAND_NAME,
            MODIS_L1B_REFLECTANCE_22_BAND_NAME,
    };
    static final int MODIS_L1B_NUM_SPECTRAL_BANDS = MODIS_L1B_SPECTRAL_BAND_NAMES.length;

    private static final String MODIS_L1B_EMISSIVITY_1_BAND_NAME = "EV_1KM_Emissive.20";                    // 3750
    private static final String MODIS_L1B_EMISSIVITY_2_BAND_NAME = "EV_1KM_Emissive.21";                    // 3959
    private static final String MODIS_L1B_EMISSIVITY_3_BAND_NAME = "EV_1KM_Emissive.22";                    // 3959
    private static final String MODIS_L1B_EMISSIVITY_4_BAND_NAME = "EV_1KM_Emissive.23";                    // 4050
    private static final String MODIS_L1B_EMISSIVITY_5_BAND_NAME = "EV_1KM_Emissive.24";                    // 4465
    private static final String MODIS_L1B_EMISSIVITY_6_BAND_NAME = "EV_1KM_Emissive.25";                    // 4515
    private static final String MODIS_L1B_EMISSIVITY_7_BAND_NAME = "EV_1KM_Emissive.27";                    // 6715
    private static final String MODIS_L1B_EMISSIVITY_8_BAND_NAME = "EV_1KM_Emissive.28";                    // 7325
    private static final String MODIS_L1B_EMISSIVITY_9_BAND_NAME = "EV_1KM_Emissive.29";                    // 8550
    private static final String MODIS_L1B_EMISSIVITY_10_BAND_NAME = "EV_1KM_Emissive.30";                    // 9730
    private static final String MODIS_L1B_EMISSIVITY_11_BAND_NAME = "EV_1KM_Emissive.31";                    // 11030
    private static final String MODIS_L1B_EMISSIVITY_12_BAND_NAME = "EV_1KM_Emissive.32";                    // 12020
    private static final String MODIS_L1B_EMISSIVITY_13_BAND_NAME = "EV_1KM_Emissive.33";                    // 13335
    private static final String MODIS_L1B_EMISSIVITY_14_BAND_NAME = "EV_1KM_Emissive.34";                    // 13635
    private static final String MODIS_L1B_EMISSIVITY_15_BAND_NAME = "EV_1KM_Emissive.35";                    // 13935
    private static final String MODIS_L1B_EMISSIVITY_16_BAND_NAME = "EV_1KM_Emissive.36";                    // 14235

    static final String[] MODIS_L1B_EMISSIVE_BAND_NAMES = {
            MODIS_L1B_EMISSIVITY_1_BAND_NAME,
            MODIS_L1B_EMISSIVITY_2_BAND_NAME,
            MODIS_L1B_EMISSIVITY_3_BAND_NAME,
            MODIS_L1B_EMISSIVITY_4_BAND_NAME,
            MODIS_L1B_EMISSIVITY_5_BAND_NAME,
            MODIS_L1B_EMISSIVITY_6_BAND_NAME,
            MODIS_L1B_EMISSIVITY_7_BAND_NAME,
            MODIS_L1B_EMISSIVITY_8_BAND_NAME,
            MODIS_L1B_EMISSIVITY_9_BAND_NAME,
            MODIS_L1B_EMISSIVITY_10_BAND_NAME,
            MODIS_L1B_EMISSIVITY_11_BAND_NAME,
            MODIS_L1B_EMISSIVITY_12_BAND_NAME,
            MODIS_L1B_EMISSIVITY_13_BAND_NAME,
            MODIS_L1B_EMISSIVITY_14_BAND_NAME,
            MODIS_L1B_EMISSIVITY_15_BAND_NAME,
            MODIS_L1B_EMISSIVITY_16_BAND_NAME,
    };
    static final int MODIS_L1B_NUM_EMISSIVE_BANDS = MODIS_L1B_EMISSIVE_BAND_NAMES.length;

    static final int IDEPIX_MIXED_PIXEL = IdepixConstants.NUM_DEFAULT_FLAGS;
    static final int IDEPIX_CLOUD_B_NIR= IdepixConstants.NUM_DEFAULT_FLAGS + 1;

    static final String IDEPIX_MIXED_PIXEL_DESCR_TEXT = "Mixed pixel";
    static final String IDEPIX_CLOUD_B_NIR_DESCR_TEXT = "Cloudy pixel (from 'b_nir test')";

//    static final String MODIS_WATER_MASK_BAND_NAME = "Land_SeaMask";
    static final String MODIS_WATER_MASK_BAND_NAME = "Land_SeaMask_MODIS_Swath_Type_GEO/Data_Fields";
}
