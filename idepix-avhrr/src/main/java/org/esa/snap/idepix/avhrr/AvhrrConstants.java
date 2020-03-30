package org.esa.snap.idepix.avhrr;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 28.11.2014
 * Time: 11:17
 *
 * @author olafd
 */
public class AvhrrConstants {

    public static final String AVHRR_AC_ALBEDO_1_BAND_NAME = "albedo_1";
    public static final String AVHRR_AC_ALBEDO_2_BAND_NAME = "albedo_2";

    public static final String[] AVHRR_AC_ALBEDO_BAND_NAMES = {
            AVHRR_AC_ALBEDO_1_BAND_NAME,
            AVHRR_AC_ALBEDO_2_BAND_NAME,
    };

    public static final String AVHRR_AC_RADIANCE_1_BAND_NAME = "radiance_1";
    public static final String AVHRR_AC_RADIANCE_2_BAND_NAME = "radiance_2";
    public static final String AVHRR_AC_RADIANCE_3_BAND_NAME = "radiance_3";
    public static final String AVHRR_AC_RADIANCE_4_BAND_NAME = "radiance_4";
    public static final String AVHRR_AC_RADIANCE_5_BAND_NAME = "radiance_5";

    public static final String[] AVHRR_AC_RADIANCE_BAND_NAMES = {
            AVHRR_AC_RADIANCE_1_BAND_NAME,
            AVHRR_AC_RADIANCE_2_BAND_NAME,
            AVHRR_AC_RADIANCE_3_BAND_NAME,
            AVHRR_AC_RADIANCE_4_BAND_NAME,
            AVHRR_AC_RADIANCE_5_BAND_NAME
    };

    public static final String AVHRR_AC_TL_ALBEDO_1_BAND_NAME = "albedo_1";
    public static final String AVHRR_AC_TL_ALBEDO_2_BAND_NAME = "albedo_2";

    public static final String[] AVHRR_AC_TL_ALBEDO_BAND_NAMES = {
            AVHRR_AC_TL_ALBEDO_1_BAND_NAME,
            AVHRR_AC_TL_ALBEDO_2_BAND_NAME,
    };

    static final int IDEPIX_INLAND_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 0;
    static final int IDEPIX_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 1;
    static final int IDEPIX_CLEAR_LAND = IdepixConstants.NUM_DEFAULT_FLAGS + 2;
    static final int IDEPIX_CLEAR_WATER = IdepixConstants.NUM_DEFAULT_FLAGS + 3;

    static final String IDEPIX_CLEAR_LAND_DESCR_TEXT = "Clear land pixels";
    static final String IDEPIX_CLEAR_WATER_DESCR_TEXT = "Clear water pixels";
    static final String IDEPIX_WATER_DESCR_TEXT = "Water pixels";
    static final String IDEPIX_INLAND_WATER_DESCR_TEXT = "inland water pixels";


    static final int SRC_USGS_SZA = 0;
    static final int SRC_USGS_LAT = 1;
    static final int SRC_USGS_LON = 2;
    static final int SRC_USGS_ALBEDO_1 = 3;
    static final int SRC_USGS_ALBEDO_2 = 4;
    static final int SRC_USGS_RADIANCE_3 = 5;
    static final int SRC_USGS_RADIANCE_4 = 6;
    static final int SRC_USGS_RADIANCE_5 = 7;
    static final int SRC_USGS_WATERFRACTION = 8;
    static final int SRC_USGS_DESERTMASK = 9;

    static final String[] SRC_TIMELINE_SPECTRAL_BAND_NAMES = {
            "avhrr_b1", "avhrr_b2", "avhrr_b3a", "avhrr_b3b", "avhrr_b4", "avhrr_b5"
    };

    static final String SRC_TIMELINE_SZA_BAND_NAME = "sun_zenith";
    static final String SRC_TIMELINE_SAA_BAND_NAME = "sun_azimuth";
    static final String SRC_TIMELINE_VZA_BAND_NAME = "sat_zenith";
    static final String SRC_TIMELINE_VAA_BAND_NAME = "sat_azimuth";
    static final String SRC_TIMELINE_ALTITUDE_BAND_NAME = "altitude";

    static final int SRC_TIMELINE_SZA = 0;
    static final int SRC_TIMELINE_SAA = 1;
    static final int SRC_TIMELINE_VZA = 2;
    static final int SRC_TIMELINE_VAA = 3;
    static final int SRC_TIMELINE_ALBEDO_1 = 4;
    static final int SRC_TIMELINE_ALBEDO_2 = 5;
    static final int SRC_TIMELINE_ALBEDO_3a = 6;
    static final int SRC_TIMELINE_RAD_3b = 7;
    static final int SRC_TIMELINE_RAD_4 = 8;
    static final int SRC_TIMELINE_RAD_5 = 9;
    static final int SRC_TIMELINE_WATERFRACTION = 10;

    static final double SOLAR_3b = 4.448;

    // values of the following constants are for NOAA7, 11, 14, 15, 16, 17, 18, METOP-A, 19, METOP-B
    // todo: GK to provide correct values for NOAA > 15. For the moment we take the same as for NOAA-14
    static final double[] WAVENUMBER_3b = {2671.900, 2671.4, 2645.899, 2695.9743, 2700.1148, 2669.3554, 2659.7952, 2687.0000, 2670.0000, 2684.3200};
    static final double[] EW_3b = {278.85792, 278.85792, 284.69366, 284.69366, 284.69366, 284.69366, 284.69366, 284.69366, 284.69366, 284.69366};
    static final double[] A0 = {6.34384, 6.34384, 4.00162, 4.00162, 4.00162, 4.00162, 4.00162, 4.00162, 4.00162, 4.00162};
    static final double[] B0 = {2.68468, 2.68468, 0.98107, 0.98107, 0.98107, 0.98107, 0.98107, 0.98107, 0.98107, 0.98107};
    static final double[] C0 = {-1.70931, -1.70931, 1.9789, 1.9789, 1.9789, 1.9789, 1.9789, 1.9789, 1.9789, 1.9789};
    // up to NOAA 14    Te = (Te_star  - a1_3b)/a2_3b
    // from NOAA 15     Te = (Te_star * a1_3b + a2_3b
    // in Appendix D; Table D.1-11 for NOAA-15,
    // Table D.2-12 for NOAA-16,
    // Table D.3-7 for NOAA-17 and
    // Table D.4-7 for NOAA-18
    static final double[] a1_3b = {-1.738973, -1.738973, -1.88533, 1.621256, 1.592459, 1.70238, 1.698704, 2.06699, 1.67396, 1.763611};
    static final double[] a2_3b = { 1.003354,  1.003354, 1.003839, 0.998015, 0.998147, 0.997378, 0.99696, 0.996577, 0.997364, 0.997018};
    static final double c1 = 1.1910659 * 1.E-5; // mW/(m^2 sr cm^-4)
    static final double c2 = 1.438833;
    //Corrected values for NOAA-7 Nsp. Channel(mW/(m2-sr-cm-1))
    static final double[] Nsp = {0.0, -1.176, -1.346};

    // different central wave numbers for AVHRR Channel3b, 4, 5 correspond to the temperature ranges & to NOAA11 and NOAA14
    // NOAA 7_3b: 180-225	     NaN, 225-275	2670.300, 275-320	2671.900, 270-310	2671.700
    // NOAA 7_4:  180-225	 926.200, 225-275	 926.800, 275-320	 927.220, 270-310	 927.200
    // NOAA 7_5:  180-225	 840.100, 225-275	 840.500, 275-320	 840.872, 270-310	 840.800
    // NOAA 11_3b: 180-225	2663.500, 225-275	2668.150, 275-320	2671.400, 270-310	2670.960
    // NOAA 11_4:  180-225	 926.810, 225-275	 927.360, 275-320	 927.830, 270-310	 927.750
    // NOAA 11_5:  180-225	 841.400, 225-275	 841.810, 275-320	 842.200, 270-310	 842.140
    // NOAA 14_3b: 190-230	2638.652, 230-270	2642.807, 270-310	2645.899, 290-330	2647.169
    // NOAA 14_4:  190-230	928.2603, 230-270	928.8284, 270-310	929.3323, 290-330	929.5878
    // NOAA 14_5:  190-230	834.4496, 230-270	834.8066, 270-310	835.1647, 290-330	 835.374

//            A     B           D        nuLow       nuMid    nuHighland  nuHighwater
    //  3	1.00359	0.0	        -0.0031	2638.652	2642.807	2645.899	2647.169
    //  4	0.9237	0.0003833	3.72	928.2603	928.8284	929.3323	929.5878
    //  5	0.9619	0.0001742	2.0	    834.4496	834.8066	835.1647	835.374

    //  3	1.67396	0.997364	0.0	    0.0	        2670.0	    0.0	        0.0
    //  4	0.53959	0.998534	0.0	    0.0	        928.9	    0.0	        0.0
    //  5	0.36064	0.998913	0.0	    0.0	        831.9	    0.0	        0.0

//     Walton, C. C., Sullivan, J. T., Rao, C. R. N., & Weinreb, M. P. (1998). Corrections for detector nonlinearities
//     and calibration inconsistencies of the infrared channels of the advanced very high resolution radiometer.
//     Journal of Geophysical Research: Oceans, 103(C2), 3323–3337. https://doi.org/10.1029/97JC02018

//                  A	    B	        D
//    NOAA 7	3	1.00000	0.0000000	0.0000
//    NOAA 7	4	0.89780	0.0004819	5.2500
//    NOAA 7	5	0.93680	0.0002425	3.9300
//    NOAA 9	4	0.88640	0.0006033	5.2400
//    NOAA 9	5	0.95310	0.0002198	2.4200
//    NOAA 10	4	0.88430	0.0005882	5.7600
//    NOAA 11	3	1.00000	0.0000000	0.0000
//    NOAA 11	4	0.84120	0.0008739	7.2100
//    NOAA 11	5	0.94600	0.0002504	2.9200
//    NOAA 12	4	0.88930	0.0005968	5.1100
//    NOAA 12	5	0.96300	0.0001775	1.9100
//    NOAA 14 	3	1.00359	0.0000000	-0.0031
//    NOAA 14 	4	0.92370	0.0003833	3.7200
//    NOAA 14	5	0.96190	0.0001742	2.0000



    public static final double TGCT_THRESH = 244.0; // 244K over land - 270K over water

    public static final double EMISSIVITY_THRESH = 0.022;
    public static final double LAT_MAX_THRESH = 60.0;

    public static double[] fmftTestThresholds = new double[]{
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.01, 0.03, 0.05, 0.08, 0.11, 0.14, 0.18, 0.23, 0.28,
            0.34, 0.41, 0.48, 0.57, 0.66, 0.76, 0.87, 1.0, 1.13, 1.27,
            1.42, 1.59, 1.76, 1.94, 2.14, 2.34, 2.55, 2.77, 3.0, 3.24,
            3.48, 3.73, 3.99, 4.26, 4.52, 4.80, 5.0, 5.35, 5.64, 5.92,
            6.20, 6.48, 6.76, 7.03, 7.30, 7.8, 7.8, 7.8, 7.8, 7.8,
            7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8, 7.8,
            7.8
    };

    public static double[] tmftTestMaxThresholds = new double[]{
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.635, 2.505, 3.395, 3.5,
            2.615, 2.655, 2.685, 2.505,
            1.865, 1.835, 1.845, 1.915,
            1.815, 1.785, 1.815, 1.795,
            1.885, 1.885, 1.875, 1.875,
            2.135, 2.115, 2.095, 2.105,
            6.825, 7.445, 8.305, 7.125,
            19.055, 18.485, 17.795, 17.025,
            20.625, 19.775, 19.355, 19.895,
            18.115, 15.935, 20.395, 16.025,
            18.115, 15.935, 20.395, 16.025
    };

    public static double[] tmftTestMinThresholds = new double[]{
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            0.145, -0.165, -0.075, -0.075,
            -0.805, -0.975, -0.795, -1.045,
            -1.195, -1.065, -1.125, -1.175,
            -1.225, -1.285, -1.285, -1.285,
            -2.425, -1.325, -2.105, -1.975,
            -1.685, -1.595, -1.535, -2.045,
            -4.205, -4.145, -3.645, -3.585,
            -2.425, -1.715, -2.275, -2.105,
            0.585, -0.585, 0.825, 0.345,
            0.655, 1.905, 0.475, 1.385,
            0.655, 1.905, 0.475, 1.385
    };
}
