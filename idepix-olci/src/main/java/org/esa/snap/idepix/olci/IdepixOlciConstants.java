package org.esa.snap.idepix.olci;

import org.esa.snap.idepix.core.IdepixConstants;

/**
 * Constants for IdePix OLCI algorithm
 *
 * @author olafd
 */
public class IdepixOlciConstants {

    /* Level 1 Quality Flags Positions */
    static final int L1_F_LAND = 31;
    static final int L1_F_COASTLINE = 30;
    static final int L1_F_FRESH_INLAND_WATER = 29;
    static final int L1_F_BRIGHT = 27;
    public static final int L1_F_INVALID = 25;
    static final int L1_F_GLINT = 22;

    public static final int IDEPIX_MOUNTAIN_SHADOW = IdepixConstants.NUM_DEFAULT_FLAGS;  // first non-default flag
    static final String IDEPIX_MOUNTAIN_SHADOW_DESCR_TEXT = "Pixel is affected by a mountain/hill shadow";

    public static final String OLCI_ALTITUDE_BAND_NAME = "altitude";
    public static final String OLCI_LATITUDE_BAND_NAME = "latitude";
    public static final String OLCI_LONGITUDE_BAND_NAME = "longitude";
    public static final String OLCI_SUN_ZENITH_BAND_NAME = "SZA";
    public static final String OLCI_SUN_AZIMUTH_BAND_NAME = "SAA";
    public static final String OLCI_VIEW_ZENITH_BAND_NAME = "OZA";
    public static final String OLCI_VIEW_ZENITH_INTERPOLATED_BAND_NAME = "OZA_interp";
    public static final String OLCI_VIEW_AZIMUTH_BAND_NAME = "OAA";
    public static final String OLCI_VIEW_AZIMUTH_INTERPOLATED_BAND_NAME = "OAA_interp";

    public static final String CSI_OUTPUT_BAND_NAME = "csi";
    public static final String OCIMP_CSI_FINAL_BAND_NAME = "csi_ocimp_final";
    public static final int CSI_FILTER_WINDOW_SIZE = 11;

    public static final double[] POLYNOM_FIT_INITIAL = new double[]{0., 0., 0.};

    static final String OLCI_QUALITY_FLAGS_BAND_NAME = "quality_flags";

    static final double[] referencePressureLevels = {
            1000., 950., 925., 900., 850., 800., 700.,
            600., 500., 400., 300., 250., 200., 150.,
            100., 70., 50., 30., 20., 10., 7., 5., 3., 2., 1.};

    // Antarctica polygon:
    // isLand && lat < 60S
    static final double[][] ANTARCTICA_POLYGON_COORDS = new double[][]{
            {-179.99, -60.0},
            {179.99, -60.0},
            {179.99, -89.99},
            {0.0, -89.99},
            {-179.99, -89.99},
            {-179.99, -60.0}
    };

    // Arctic Land Polygon
    static final double[][] ARCTIC_POLYGON_COORDS = new double[][]{
            {-179.17503822036088, 89.99},
            {-179.32503127120435, 74.97152959212823},
            {-162.32581884227693, 74.82153654128476},
            {-146.57654850371182, 74.97152959212823},
            {-132.42720404081047, 74.97152959212823},
            {-119.27781325019896, 74.82153654128476},
            {-111.7281630244106, 73.37160371646448},
            {-103.2285568099469, 74.02157360345288},
            {-96.4788695219904, 74.17156665429636},
            {-91.97907799668609, 74.37155738875433},
            {-88.27924940921365, 73.8715805526094},
            {-83.12948799692093, 74.02157360345288},
            {-77.67974048294126, 74.02157360345288},
            {-69.48012037016451, 73.8715805526094},
            {-59.180597545579076, 73.37160371646448},
            {-55.630762008950114, 67.87185851887031},
            {-52.58090330846608, 62.42211100489064},
            {-43.38132952339947, 56.32239360392256},
            {-35.83167929761112, 61.12217123091383},
            {-26.682103196159005, 65.9719465415196},
            {-15.432624382898211, 69.971761230679},
            {-3.3331849481910467, 74.071571287067},
            {8.716256802901626, 74.571548123212},
            {18.165819006040692, 74.871534224899},
            {29.26530476845801, 75.67149716273113},
            {40.364790530875325, 76.32146704971953},
            {51.46427629329264, 77.12142998755141},
            {63.66371109522879, 78.22137902707024},
            {72.96328024752438, 78.22137902707024},
            {82.91281928680837, 78.42136976152821},
            {92.06239538826048, 77.77139987453981},
            {98.16211278922856, 77.62140682369633},
            {101.2119714897126, 77.57140914008184},
            {105.56176996417344, 77.92139292538329},
            {109.56158465333283, 77.77139987453981},
            {119.31113295815885, 78.12138365984126},
            {129.91064188443124, 78.4713674451427},
            {140.36015775986016, 78.7713535468296},
            {150.6596805844456, 79.2713303829745},
            {161.40918256156147, 79.2713303829745},
            {170.4087656121701, 79.07133964851661},
            {179.5583417136222, 79.07133964851661},
            {179.40834866277874, 89.99},
            {0.0, 89.99},
            {-179.17503822036088, 89.99}
    };

    // view angle interpolation at discontinuities:
    static final int OLCI_FR_FULL_PRODUCT_WIDTH = 4865;
    static final int OLCI_RR_FULL_PRODUCT_WIDTH = 1217;
    static final int OLCI_FR_DEFAULT_NX_CHANGE = 3616;
    static final int OLCI_RR_DEFAULT_NX_CHANGE = 904;
    static final int[] OLCI_DEFAULT_FR_NX_VZA = new int[]{3500, 3580, 3649, 3729};
    static final int[] OLCI_DEFAULT_RR_NX_VZA = new int[]{875, 895, 912, 932};
    static final int[] OLCI_DEFAULT_FR_NX_VAA = new int[]{2000, 3400, 3900, OLCI_FR_FULL_PRODUCT_WIDTH};
    static final int[] OLCI_DEFAULT_RR_NX_VAA = new int[]{500, 850, 975, OLCI_RR_FULL_PRODUCT_WIDTH};
}
