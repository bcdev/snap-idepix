package org.esa.snap.idepix.meris;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix MERIS algorithm
 *
 * @author olafd
 */
public class IdepixMerisConstants {

    public static final int IDEPIX_MOUNTAIN_SHADOW = IdepixConstants.NUM_DEFAULT_FLAGS;  // first non-default flag
    static final String IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT = "Pixel is affected by a mountain/hill shadow";

    public static final int IDEPIX_GLINT_RISK = IdepixConstants.NUM_DEFAULT_FLAGS + 1;
    static final String IDEPIX_GLINT_RISK_DESCR_TEXT = "Glint risk pixel";

    /* Level 1 Flags Positions */
    static final int L1_F_LAND = 4;
    static final int L1_F_BRIGHT = 5;
    static final int L1_F_INVALID = 7;


    public static final String MERIS_LATITUDE_BAND_NAME = "latitude";
    public static final String MERIS_LONGITUDE_BAND_NAME = "longitude";

    public static final String MERIS_ALTITUDE_BAND_NAME = "dem_alt";
    public static final String MERIS_SUN_ZENITH_BAND_NAME = "sun_zenith";
    public static final String MERIS_SUN_AZIMUTH_BAND_NAME = "sun_azimuth";
    public static final String MERIS_VIEW_ZENITH_BAND_NAME = "view_zenith";
    public static final String MERIS_VIEW_AZIMUTH_BAND_NAME = "view_azimuth";

    public static final String MERIS_4RP_ALTITUDE_BAND_NAME = "altitude";
    public static final String MERIS_4RP_SUN_ZENITH_BAND_NAME = "SZA";
    public static final String MERIS_4RP_SUN_AZIMUTH_BAND_NAME = "SAA";
    public static final String MERIS_4RP_VIEW_ZENITH_BAND_NAME = "OZA";
    public static final String MERIS_4RP_VIEW_AZIMUTH_BAND_NAME = "OAA";

    public static final String MERIS_VIEW_ZENITH_INTERPOLATED_BAND_NAME = "OZA_interp";
    public static final String MERIS_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME = "OAA_interp";

    public static final double[] POLYNOM_FIT_INITIAL = new double[]{0., 0., 0.};

    // view angle interpolation at discontinuities:
    static final int MERIS_FR_FULL_PRODUCT_WIDTH = 4481;
    static final int MERIS_RR_FULL_PRODUCT_WIDTH = 1121;
    static final int MERIS_FR_DEFAULT_NX_CHANGE = 2241;
    static final int MERIS_RR_DEFAULT_NX_CHANGE = 561;
    static final int[] MERIS_DEFAULT_FR_NX_VZA = new int[]{2100, 2180, 2302, 2382};
    static final int[] MERIS_DEFAULT_RR_NX_VZA = new int[]{525, 545, 576, 596};
    static final int[] MERIS_DEFAULT_FR_NX_VAA = new int[]{1000, 2000, 2500, MERIS_FR_FULL_PRODUCT_WIDTH};
    static final int[] MERIS_DEFAULT_RR_NX_VAA = new int[]{250, 500, 625, MERIS_RR_FULL_PRODUCT_WIDTH};
}
