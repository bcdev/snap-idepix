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
}
