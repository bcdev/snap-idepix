package org.esa.snap.idepix.olci;

/**
 * Constants for Idepix OLCI algorithm
 *
 * @author olafd
 */
public class IdepixOlciConstants {

    /* Level 1 Quality Flags Positions */
    public static final int L1_F_LAND = 31;
    public static final int L1_F_COASTLINE = 30;
    //    public static final int L1_F_FRESH_INLAND_WATER = 29;
    public static final int L1_F_BRIGHT = 27;
    public static final int L1_F_INVALID = 25;
    public static final int L1_F_GLINT = 22;


    public static final String OLCI_QUALITY_FLAGS_BAND_NAME = "quality_flags";

    public static final double[] referencePressureLevels = {
            1000., 950., 925., 900., 850., 800., 700.,
            600., 500., 400., 300., 250., 200., 150.,
            100., 70., 50., 30., 20., 10., 7., 5., 3., 2., 1.};

    public static final double[][] GREENLAND_POLYGON_COORDS = new double[][]{
            {-59.75, 83.25},
            {-63.25, 81.75},
            {-66.75, 80.25},
            {-69.75, 79.75},
            {-73.25, 78.25},
            {-70.75, 75.75},
            {-66.25, 75.25},
            {-62.25, 74.75},
            {-59.25, 74.25},
            {-56.75, 71.75},
            {-56.25, 68.75},
            {-54.75, 66.25},
            {-52.75, 63.25},
            {-49.25, 60.25},
            {-46.25, 59.25},
            {-42.75, 59.25},
            {-40.75, 62.25},
            {-38.25, 64.25},
            {-34.25, 65.75},
            {-31.25, 67.25},
            {-27.25, 67.75},
            {-23.75, 68.75},
            {-20.75, 70.25},
            {-18.75, 73.25},
            {-16.75, 76.75},
            {-15.25, 79.25},
            {-11.75, 81.25},
            {-13.75, 83.25},
            {-18.25, 82.75},
            {-22.25, 83.75},
            {-26.75, 84.25},
            {-31.75, 84.25},
            {-35.75, 84.25},
            {-40.25, 84.25},
            {-44.75, 84.75},
            {-49.25, 84.75},
            {-53.25, 85.25},
            {-59.75, 83.25}
    };

    // Antarctica polygon:
    // isLand && lat < 60S
    public static final double[][] ANTARCTICA_POLYGON_COORDS = new double[][]{
            {-179.99, -60.0},
            {179.99, -60.0},
            {179.99, -89.99},
            {-179.99, -89.99},
            {-179.99, -60.0}
    };
}
