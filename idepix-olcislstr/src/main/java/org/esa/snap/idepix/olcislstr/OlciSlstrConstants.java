package org.esa.snap.idepix.olcislstr;

/**
 * Constants for IdePix OLCI algorithm
 *
 * @author olafd
 */
class OlciSlstrConstants {

    /* Level 1 Quality Flags Positions */
    static final int L1_F_LAND = 31;
    static final int L1_F_INVALID = 25;
//    static final int L1_F_GLINT = 22;

    static final String OLCI_QUALITY_FLAGS_BAND_NAME = "quality_flags";

    final static String[] SLSTR_REFL_AN_BAND_NAMES = new String[]{
            "S1_reflectance_an", "S2_reflectance_an", "S3_reflectance_an",
            "S4_reflectance_an", "S5_reflectance_an", "S6_reflectance_an"
    };

    // todo: RENOVATION: code duplication, move to core
    static final double[] referencePressureLevels = {
            1000., 950., 925., 900., 850., 800., 700.,
            600., 500., 400., 300., 250., 200., 150.,
            100., 70., 50., 30., 20., 10., 7., 5., 3., 2., 1.};

    public static final String OLCI_ALTITUDE_BAND_NAME = "altitude";
}
